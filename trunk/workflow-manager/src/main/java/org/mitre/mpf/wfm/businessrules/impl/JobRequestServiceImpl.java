/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
 *                                                                            *
 * Licensed under the Apache License, Version 2.0 (the "License");            *
 * you may not use this file except in compliance with the License.           *
 * You may obtain a copy of the License at                                    *
 *                                                                            *
 *    http://www.apache.org/licenses/LICENSE-2.0                              *
 *                                                                            *
 * Unless required by applicable law or agreed to in writing, software        *
 * distributed under the License is distributed on an "AS IS" BASIS,          *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.   *
 * See the License for the specific language governing permissions and        *
 * limitations under the License.                                             *
 ******************************************************************************/

package org.mitre.mpf.wfm.businessrules.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.mvc.util.CloseableMdc;
import org.mitre.mpf.rest.api.JobCreationMediaRange;
import org.mitre.mpf.rest.api.JobCreationMediaSelector;
import org.mitre.mpf.rest.api.JobCreationRequest;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.businessrules.JobRequestService;
import org.mitre.mpf.wfm.camel.operations.detection.transformation.DetectionTransformationException;
import org.mitre.mpf.wfm.camel.operations.detection.transformation.DetectionTransformationProcessor;
import org.mitre.mpf.wfm.camel.routes.JobRouterRouteBuilder;
import org.mitre.mpf.wfm.camel.routes.MediaRetrieverRouteBuilder;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.access.JobRequestDao;
import org.mitre.mpf.wfm.data.access.MarkupResultDao;
import org.mitre.mpf.wfm.data.entities.persistent.*;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.segmenting.TriggerProcessor;
import org.mitre.mpf.wfm.service.ConstraintValidationService;
import org.mitre.mpf.wfm.service.MediaSelectorsSegmenter;
import org.mitre.mpf.wfm.service.S3StorageBackend;
import org.mitre.mpf.wfm.service.StorageException;
import org.mitre.mpf.wfm.service.TiesDbBeforeJobCheckService;
import org.mitre.mpf.wfm.service.pipeline.PipelineService;
import org.mitre.mpf.wfm.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import javax.inject.Inject;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;

import static java.util.stream.Collectors.joining;

@Component
public class JobRequestServiceImpl implements JobRequestService {

    private static final Logger LOG = LoggerFactory.getLogger(JobRequestServiceImpl.class);

    private final PropertiesUtil _propertiesUtil;

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;

    private final PipelineService _pipelineService;

    private final JsonUtils _jsonUtils;

    private final JmsUtils _jmsUtils;

    private final InProgressBatchJobsService _inProgressJobs;

    private final JobRequestDao _jobRequestDao;

    private final MarkupResultDao _markupResultDao;

    private final TiesDbBeforeJobCheckService _tiesDbBeforeJobCheckService;

    private final ProducerTemplate _jobRequestProducerTemplate;

    private final ConstraintValidationService _validator;

    @Inject
    public JobRequestServiceImpl(
            PropertiesUtil propertiesUtil,
            AggregateJobPropertiesUtil aggregateJobPropertiesUtil,
            PipelineService pipelineService,
            JsonUtils jsonUtils,
            JmsUtils jmsUtils,
            InProgressBatchJobsService inProgressJobs,
            JobRequestDao jobRequestDao,
            MarkupResultDao markupResultDao,
            TiesDbBeforeJobCheckService tiesDbBeforeJobCheckService,
            ProducerTemplate jobRequestProducerTemplate,
            ConstraintValidationService validator) {
        _pipelineService = pipelineService;
        _propertiesUtil = propertiesUtil;
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
        _jsonUtils = jsonUtils;
        _jmsUtils = jmsUtils;
        _inProgressJobs = inProgressJobs;
        _jobRequestDao = jobRequestDao;
        _markupResultDao = markupResultDao;
        _tiesDbBeforeJobCheckService = tiesDbBeforeJobCheckService;
        _jobRequestProducerTemplate = jobRequestProducerTemplate;
        _validator = validator;
    }



    @Override
    public CreationResult run(JobCreationRequest jobCreationRequest) {
        _validator.validate(jobCreationRequest, "JobCreationRequest");

        List<Media> media = jobCreationRequest.media()
                .stream()
                .map(m -> _inProgressJobs.initMedia(
                        m.mediaUri(),
                        m.properties(),
                        m.metadata(),
                        convertRanges(m.frameRanges()),
                        convertRanges(m.timeRanges()),
                        convertSelectors(m.mediaSelectors()),
                        m.mediaSelectorsOutputAction().orElse(null)))
                .collect(ImmutableList.toImmutableList());

        int priority = Optional.ofNullable(jobCreationRequest.priority())
                .orElseGet(_propertiesUtil::getJmsPriority);

        JobPipelineElements pipelineElements;
        if (jobCreationRequest.pipelineDefinition() == null) {
            pipelineElements = _pipelineService.getBatchPipelineElements(
                    jobCreationRequest.pipelineName());
        }
        else if (jobCreationRequest.pipelineName() == null
                || jobCreationRequest.pipelineName().isBlank()) {
            pipelineElements = _pipelineService.getBatchPipelineElements(
                    jobCreationRequest.pipelineDefinition());
        }
        else {
            // Allow setting for both the piplineName and pipelineDefinition.
             pipelineElements = _pipelineService.getBatchPipelineElements(
                    jobCreationRequest.pipelineName(),
                    jobCreationRequest.pipelineDefinition());
        }

        var systemPropertiesSnapshot = _propertiesUtil.createSystemPropertiesSnapshot();

        JobRequest jobRequestEntity = initialize(
                new JobRequest(),
                pipelineElements,
                media,
                jobCreationRequest.jobProperties(),
                jobCreationRequest.algorithmProperties(),
                jobCreationRequest.externalId(),
                priority,
                jobCreationRequest.callbackURL(),
                jobCreationRequest.callbackMethod(),
                systemPropertiesSnapshot);

        long jobId = jobRequestEntity.getId();
        try (var mdc = CloseableMdc.job(jobId)) {
            _jobRequestDao.newJobCreated();
            var tiesDbCheckResult = _tiesDbBeforeJobCheckService.checkTiesDbBeforeJob(jobId);

            if (tiesDbCheckResult.checkInfo().isPresent()) {
                LOG.info("Skipping job processing because compatible job found in TiesDb.");
                var headers = Map.<String, Object>of(
                        MpfHeaders.JOB_ID, jobId,
                        MpfHeaders.JMS_PRIORITY, getPriority(jobRequestEntity),
                        MpfHeaders.JOB_COMPLETE, true,
                        MpfHeaders.OUTPUT_OBJECT_URI_FROM_TIES_DB,
                        tiesDbCheckResult.checkInfo().get().outputObjectUri().toString());
                _jobRequestProducerTemplate.sendBodyAndHeaders(
                        JobRouterRouteBuilder.ENTRY_POINT,
                        ExchangePattern.InOnly,
                        null,
                        headers);
            }
            else {
                submit(jobRequestEntity);
            }
            return new CreationResult(jobId, tiesDbCheckResult);
        }
    }


    @Override
    public JobRequest resubmit(long jobId, int priority) {
        JobRequest jobRequestEntity = _jobRequestDao.findById(jobId);
        if (jobRequestEntity == null) {
            throw new WfmProcessingException("There was no job with id: " + jobId);
        }
        if (!jobRequestEntity.getStatus().isTerminal()) {
            throw new WfmProcessingException(String.format(
                    "The job with id %d is in the non-terminal state of '%s'. Only jobs in a terminal state may be resubmitted.",
                    jobId, jobRequestEntity.getStatus().name()));
        }

        BatchJob originalJob = _jsonUtils.deserialize(jobRequestEntity.getJob(), BatchJob.class);

        List<Media> media = originalJob.getMedia()
                .stream()
                .filter(Predicate.not(Media::isDerivative))
                .map(m -> _inProgressJobs.initMedia(
                        m.getUri(),
                        m.getMediaSpecificProperties(),
                        m.getProvidedMetadata(),
                        m.getFrameRanges(),
                        m.getTimeRanges(),
                        m.getMediaSelectors(),
                        m.getMediaSelectorsOutputAction().orElse(null)))
                .collect(ImmutableList.toImmutableList());


        jobRequestEntity = initialize(jobRequestEntity,
                    originalJob.getPipelineElements(),
                    media,
                    originalJob.getJobProperties(),
                    originalJob.getOverriddenAlgorithmProperties(),
                    originalJob.getExternalId().orElse(null),
                    priority > 0 ? priority : originalJob.getPriority(),
                    originalJob.getCallbackUrl().orElse(null),
                    originalJob.getCallbackMethod().orElse(null),
                    _propertiesUtil.createSystemPropertiesSnapshot());

        // Clean up old job
        _markupResultDao.deleteByJobId(jobId);
        FileSystemUtils.deleteRecursively(_propertiesUtil.getJobArtifactsDirectory(jobId));
        FileSystemUtils.deleteRecursively(_propertiesUtil.getJobOutputObjectsDirectory(jobId));
        FileSystemUtils.deleteRecursively(_propertiesUtil.getJobMarkupDirectory(jobId));
        FileSystemUtils.deleteRecursively(_propertiesUtil.getJobDerivativeMediaDirectory(jobId));

        submit(jobRequestEntity);
        return jobRequestEntity;
    }


    private static ImmutableSet<MediaRange> convertRanges(
            Collection<JobCreationMediaRange> ranges) {
        return ranges
                .stream()
                .map(r -> new MediaRange(r.start(), r.stop()))
                .collect(ImmutableSet.toImmutableSet());
    }

    private static ImmutableList<MediaSelector> convertSelectors(
                Collection<JobCreationMediaSelector> selectors) {
        return selectors.stream()
                .map(s -> new MediaSelector(
                        s.expression(), s.type(),
                        s.selectionProperties(), s.resultDetectionProperty()))
                .collect(ImmutableList.toImmutableList());
    }

    private JobRequest initialize(
            JobRequest jobRequestEntity,
            JobPipelineElements pipelineElements,
            Collection<Media> media,
            Map<String, String> jobProperties,
            Map<String, ? extends Map<String, String>> overriddenAlgoProps,
            String externalId,
            int priority,
            String callbackUrl,
            String callbackMethod,
            SystemPropertiesSnapshot systemPropertiesSnapshot) {

        callbackUrl = StringUtils.trimToNull(callbackUrl);
        callbackMethod = TextUtils.trimToNullAndUpper(callbackMethod);
        if (callbackUrl != null && !Objects.equals("GET", callbackMethod)) {
            callbackMethod = "POST";
        }


        BatchJobStatusType jobStatus = validateJobRequest(
                pipelineElements,
                media,
                jobProperties,
                overriddenAlgoProps,
                systemPropertiesSnapshot);

        var jobId = jobRequestEntity.getId() > 0
                ? jobRequestEntity.getId()
                : _jobRequestDao.getNextId();

        try (var mdc = CloseableMdc.job(jobId)) {
            BatchJob job = _inProgressJobs.addJob(
                    jobId,
                    externalId,
                    systemPropertiesSnapshot,
                    pipelineElements,
                    priority,
                    callbackUrl,
                    callbackMethod,
                    media,
                    jobProperties,
                    overriddenAlgoProps);

            try {
                jobRequestEntity.setId(jobId);
                jobRequestEntity.setPriority(priority);
                jobRequestEntity.setStatus(jobStatus);
                jobRequestEntity.setTimeReceived(Instant.now());
                jobRequestEntity.setPipeline(pipelineElements.getName());
                jobRequestEntity.setTimeCompleted(null);
                jobRequestEntity.setOutputObjectPath(null);
                jobRequestEntity.setOutputObjectVersion(null);
                jobRequestEntity.setTiesDbStatus(null);
                jobRequestEntity.setCallbackStatus(null);


                jobRequestEntity.setJob(_jsonUtils.serialize(job));

                jobRequestEntity = _jobRequestDao.persist(jobRequestEntity);
                _inProgressJobs.setJobStatus(job.getId(),  jobStatus);
                return jobRequestEntity;
            }
            catch (Exception e) {
                _inProgressJobs.clearOnInitializationError(jobId);
                throw e;
            }
        }
    }


    private void submit(JobRequest jobRequestEntity) {
        var headers = Map.<String, Object>of(
                MpfHeaders.JOB_ID, jobRequestEntity.getId(),
                MpfHeaders.JMS_PRIORITY, getPriority(jobRequestEntity));

        LOG.info("Job has started and is running at priority {}.",
                 headers.get(MpfHeaders.JMS_PRIORITY));

        _jobRequestProducerTemplate.sendBodyAndHeaders(
                MediaRetrieverRouteBuilder.ENTRY_POINT,
                ExchangePattern.InOnly,
                null,
                headers);
    }

    private static int getPriority(JobRequest jobRequestEntity) {
        return Math.max(0, Math.min(9, jobRequestEntity.getPriority()));
    }


    private BatchJobStatusType validateJobRequest(
            JobPipelineElements pipeline,
            Collection<Media> media,
            Map<String, String> jobProperties,
            Map<String, ? extends Map<String, String>> overriddenAlgoProps,
            SystemPropertiesSnapshot systemPropertiesSnapshot) {

        checkProperties(pipeline, media, jobProperties, overriddenAlgoProps, systemPropertiesSnapshot);
        MediaSelectorsSegmenter.validateSelectors(media, pipeline);

        if (media.isEmpty()) {
            throw new WfmProcessingException(
                    "The job request must contain at least one piece of media, but it didn't contain any.");
        }

        long failedMediaCount = media
                .stream()
                .filter(Media::isFailed)
                .count();

        if (failedMediaCount == media.size()) {
            String mediaErrors = media.stream()
                    .map(m -> m.getUri() + ": " + m.getErrorMessage())
                    .collect(joining("\n"));

            throw new WfmProcessingException("Could not start job because all media have errors: " + mediaErrors);
        }

        return failedMediaCount == 0
                ? BatchJobStatusType.IN_PROGRESS
                : BatchJobStatusType.IN_PROGRESS_ERRORS;
    }


    private void checkProperties(
            JobPipelineElements pipeline,
            Iterable<Media> jobMedia,
            Map<String, String> jobProperties,
            Map<String, ? extends Map<String, String>> overriddenAlgoProps,
            SystemPropertiesSnapshot systemPropertiesSnapshot) {
        try {
            for (Action action : pipeline.getAllActions()) {
                for (Media media : jobMedia) {
                    var combinedProperties = _aggregateJobPropertiesUtil.getCombinedProperties(
                            action,
                            pipeline,
                            media,
                            jobProperties,
                            overriddenAlgoProps,
                            systemPropertiesSnapshot);

                    S3StorageBackend.validateS3Properties(combinedProperties);
                    DetectionTransformationProcessor.validatePaddingProperties(combinedProperties);
                    TriggerProcessor.validateTrigger(combinedProperties);
                }
            }
        }
        catch (StorageException | DetectionTransformationException e) {
            throw new WfmProcessingException("Property validation failed due to: " + e, e);
        }
    }


    /**
     * Will cancel a batch job.
     * This method will mark the job as cancelled in both the BatchJob and in the long-term database.
     * The job cancel request will also be sent along to the components via ActiveMQ using the
     * JobCreatorRouteBuilder.ENTRY_POINT.
     *
     * @param jobId
     * @return true if the job was successfully cancelled, false otherwise
     * @throws WfmProcessingException
     */
    @Override
    public synchronized boolean cancel(long jobId) throws WfmProcessingException {
        LOG.debug("Received request to cancel this job.");
        JobRequest jobRequest = _jobRequestDao.findById(jobId);
        if (jobRequest == null) {
            throw new WfmProcessingException(String.format("A job with the id %d is not known to the system.", jobId));
        }

        if (jobRequest.getStatus() == null) {
            throw new WfmProcessingException(String.format("Job %d must not have a null status.", jobId));
        }

        if (jobRequest.getStatus().isTerminal() || jobRequest.getStatus() == BatchJobStatusType.CANCELLING) {
            LOG.warn("This job is in the state of '{}' and cannot be cancelled at this time.",
                     jobRequest.getStatus().name());
            return false;
        } else {
            LOG.info("Cancelling job.");


            if (_inProgressJobs.cancelJob(jobId)) {
                try {
                    // Try to move any pending work items on the queues to the appropriate cancellation queues.
                    // If this operation fails, any remaining pending items will continue to process, but
                    // the future splitters should not create any new work items. In short, if this fails,
                    // the system should not be affected, but the job may not complete any faster.
                    _jmsUtils.cancelDetectionJob(jobId);
                } catch (Exception exception) {
                    LOG.warn("Failed to remove the pending work elements in the message broker " +
                                 "for this job. The job must complete the pending work " +
                                 "elements before it will cancel the job.", exception);
                }
                jobRequest.setStatus(BatchJobStatusType.CANCELLING);
                jobRequest.setJob(_jsonUtils.serialize(_inProgressJobs.getJob(jobId)));
                _jobRequestDao.persist(jobRequest);
            } else {
                LOG.warn("The job is not in progress and cannot be cancelled at this time.");
            }
            return true;
        }
    }
}
