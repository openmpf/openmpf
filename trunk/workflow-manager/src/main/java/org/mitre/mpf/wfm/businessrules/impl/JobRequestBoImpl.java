/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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
import org.apache.camel.EndpointInject;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.rest.api.JobCreationRequest;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.businessrules.JobRequestBo;
import org.mitre.mpf.wfm.camel.routes.MediaRetrieverRouteBuilder;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.access.JobRequestDao;
import org.mitre.mpf.wfm.data.access.MarkupResultDao;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateMarkupResultDaoImpl;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.data.entities.transients.SystemPropertiesSnapshot;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.transients.TransientPipeline;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.exceptions.InvalidPropertyWfmProcessingException;
import org.mitre.mpf.wfm.pipeline.PipelineService;
import org.mitre.mpf.wfm.service.JobStatusBroadcaster;
import org.mitre.mpf.wfm.service.S3StorageBackend;
import org.mitre.mpf.wfm.service.StorageException;
import org.mitre.mpf.wfm.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.joining;

@Component
public class JobRequestBoImpl implements JobRequestBo {

    private static final Logger log = LoggerFactory.getLogger(JobRequestBoImpl.class);
    public static final String REF = "jobRequestBoImpl";

    @Autowired
    private PipelineService pipelineService;

    @Autowired
    private PropertiesUtil propertiesUtil;

    @Autowired
    private AggregateJobPropertiesUtil aggregateJobPropertiesUtil;

    @Autowired
    private JsonUtils jsonUtils;

    @Autowired
    private JmsUtils jmsUtils;

    @Autowired
    private InProgressBatchJobsService inProgressJobs;

    @Autowired
    private JobRequestDao jobRequestDao;

    @Autowired
    @Qualifier(HibernateMarkupResultDaoImpl.REF)
    private MarkupResultDao markupResultDao;

    @Autowired
    private JobStatusBroadcaster jobStatusBroadcaster;

    @EndpointInject(uri = MediaRetrieverRouteBuilder.ENTRY_POINT)
    private ProducerTemplate jobRequestProducerTemplate;



    @Override
    public JobRequest run(JobCreationRequest jobCreationRequest) {
        List<Media> media = jobCreationRequest.getMedia()
                .stream()
                .map(m -> inProgressJobs.initMedia(m.getMediaUri(), m.getProperties()))
                .collect(ImmutableList.toImmutableList());

        int priority = Optional.ofNullable(jobCreationRequest.getPriority())
                .orElseGet(propertiesUtil::getJmsPriority);

        boolean buildOutput = Optional.ofNullable(jobCreationRequest.getBuildOutput())
                .orElseGet(propertiesUtil::isOutputObjectsEnabled);



        JobRequest jobRequestEntity = initialize(
                new JobRequest(),
                jobCreationRequest.getPipelineName(),
                media,
                jobCreationRequest.getJobProperties(),
                jobCreationRequest.getAlgorithmProperties(),
                jobCreationRequest.getExternalId(),
                buildOutput,
                priority,
                jobCreationRequest.getCallbackURL(),
                jobCreationRequest.getCallbackMethod());

        submit(jobRequestEntity);
        return jobRequestEntity;
    }



    @Override
    public JobRequest resubmit(long jobId, int priority) {
        JobRequest jobRequestEntity = jobRequestDao.findById(jobId);
        if (jobRequestEntity == null) {
            throw new WfmProcessingException("There was no job with id: " + jobId);
        }
        if (!jobRequestEntity.getStatus().isTerminal()) {
            throw new WfmProcessingException(String.format(
                    "The job with id %d is in the non-terminal state of '%s'. Only jobs in a terminal state may be resubmitted.",
                    jobId, jobRequestEntity.getStatus().name()));
        }

        BatchJob originalJob = jsonUtils.deserialize(jobRequestEntity.getInputObject(), BatchJob.class);

        List<Media> media = originalJob.getMedia()
                .stream()
                .map(m -> inProgressJobs.initMedia(m.getUri(), m.getMediaSpecificProperties()))
                .collect(ImmutableList.toImmutableList());


        jobRequestEntity = initialize(jobRequestEntity,
                    originalJob.getTransientPipeline().getName(),
                    media,
                    originalJob.getJobProperties(),
                    originalJob.getOverriddenAlgorithmProperties(),
                    originalJob.getExternalId().orElse(null),
                    originalJob.isOutputEnabled(),
                    priority > 0 ? priority : originalJob.getPriority(),
                    originalJob.getCallbackUrl().orElse(null),
                    originalJob.getCallbackMethod().orElse(null));

        // Clean up old job
        markupResultDao.deleteByJobId(jobId);
        FileSystemUtils.deleteRecursively(propertiesUtil.getJobArtifactsDirectory(jobId));
        FileSystemUtils.deleteRecursively(propertiesUtil.getJobOutputObjectsDirectory(jobId));
        FileSystemUtils.deleteRecursively(propertiesUtil.getJobMarkupDirectory(jobId));

        submit(jobRequestEntity);
        return jobRequestEntity;
    }


    private JobRequest initialize(
            JobRequest jobRequestEntity,
            String pipelineName,
            Collection<Media> media,
            Map<String, String> jobProperties,
            Map<String, ? extends Map<String, String>> overriddenAlgoProps,
            String externalId,
            boolean buildOutput,
            int priority,
            String callbackUrl,
            String callbackMethod) {

        var pipeline = pipelineService.createTransientBatchPipeline(pipelineName);
        // Capture the current state of the detection system properties at the time when this job is created.
        // Since the detection system properties may be changed by an administrator, we must ensure that the job
        // uses a consistent set of detection system properties through all stages of the job's pipeline.
        var systemPropertiesSnapshot = propertiesUtil.createSystemPropertiesSnapshot();

        callbackUrl = StringUtils.trimToNull(callbackUrl);
        callbackMethod = TextUtils.trimToNullAndUpper(callbackMethod);
        if (callbackUrl != null && !Objects.equals("GET", callbackMethod)) {
            callbackMethod = "POST";
        }


        BatchJobStatusType jobStatus = validateJobRequest(
                pipeline,
                media,
                jobProperties,
                overriddenAlgoProps,
                systemPropertiesSnapshot);

        jobRequestEntity.setPriority(priority);
        jobRequestEntity.setStatus(BatchJobStatusType.INITIALIZED);
        jobRequestEntity.setTimeReceived(Instant.now());
        jobRequestEntity.setPipeline(pipeline.getName());
        jobRequestEntity.setOutputObjectPath(null);
        jobRequestEntity.setOutputObjectVersion(null);

        jobRequestEntity = jobRequestDao.persist(jobRequestEntity);

        try {
            jobStatusBroadcaster.broadcast(jobRequestEntity.getId(), 0, BatchJobStatusType.INITIALIZED);

            BatchJob job = inProgressJobs.addJob(
                    jobRequestEntity.getId(),
                    externalId,
                    systemPropertiesSnapshot,
                    pipeline,
                    jobRequestEntity.getPriority(),
                    buildOutput,
                    callbackUrl,
                    callbackMethod,
                    media,
                    jobProperties,
                    overriddenAlgoProps);


            inProgressJobs.setJobStatus(job.getId(), jobStatus);

            jobRequestEntity.setInputObject(jsonUtils.serialize(job));
            jobRequestEntity.setStatus(jobStatus);
            jobRequestEntity = jobRequestDao.persist(jobRequestEntity);

            jobStatusBroadcaster.broadcast(jobRequestEntity.getId(), 0, jobStatus);


            return jobRequestEntity;
        }
        catch (WfmProcessingException e) {
            jobRequestEntity.setStatus(BatchJobStatusType.JOB_CREATION_ERROR);
            jobRequestDao.persist(jobRequestEntity);
            throw e;
        }
    }


    private void submit(JobRequest jobRequestEntity) {
        var headers = Map.<String, Object>of(
                MpfHeaders.JOB_ID, jobRequestEntity.getId(),
                MpfHeaders.JMS_PRIORITY, Math.max(0, Math.min(9, jobRequestEntity.getPriority())));

        log.info("[Job {}|*|*] Job has started and is running at priority {}.",
                 jobRequestEntity.getId(), headers.get(MpfHeaders.JMS_PRIORITY));

        jobRequestProducerTemplate.sendBodyAndHeaders(
                MediaRetrieverRouteBuilder.ENTRY_POINT,
                ExchangePattern.InOnly,
                null,
                headers);
    }


    private BatchJobStatusType validateJobRequest(
            TransientPipeline pipeline,
            Collection<Media> media,
            Map<String, String> jobProperties,
            Map<String, ? extends Map<String, String>> overriddenAlgoProps,
            SystemPropertiesSnapshot systemPropertiesSnapshot) {

        checkProperties(pipeline, media, jobProperties, overriddenAlgoProps, systemPropertiesSnapshot);

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
                    .map(m -> m.getUri() + ": " + m.getMessage())
                    .collect(joining("\n"));

            throw new WfmProcessingException("Could not start job because all media have errors: " + mediaErrors);
        }

        return failedMediaCount == 0
                ? BatchJobStatusType.IN_PROGRESS
                : BatchJobStatusType.IN_PROGRESS_ERRORS;
    }


    private void checkProperties(
            TransientPipeline pipeline,
            Iterable<Media> jobMedia,
            Map<String, String> jobProperties,
            Map<String, ? extends Map<String, String>> overriddenAlgoProps,
            SystemPropertiesSnapshot systemPropertiesSnapshot) {
        try {
            for (Action action : pipeline.getActions()) {
                for (Media media : jobMedia) {
                    Function<String, String> combinedProperties = aggregateJobPropertiesUtil.getCombinedProperties(
                            action,
                            pipeline,
                            media,
                            jobProperties,
                            overriddenAlgoProps,
                            systemPropertiesSnapshot);

                    S3StorageBackend.requiresS3MediaDownload(combinedProperties);
                    S3StorageBackend.requiresS3ResultUpload(combinedProperties);
                }
            }
        }
        catch (StorageException e) {
            throw new InvalidPropertyWfmProcessingException("Property validation failed due to: " + e, e);
        }
    }


    /**
     * Will cancel a batch job.
     * This method will mark the job as cancelled in both the TransientJob and in the long-term database.
     * The job cancel request will also be sent along to the components via ActiveMQ using the
     * JobCreatorRouteBuilder.ENTRY_POINT.
     *
     * @param jobId
     * @return true if the job was successfully cancelled, false otherwise
     * @throws WfmProcessingException
     */
    @Override
    public synchronized boolean cancel(long jobId) throws WfmProcessingException {
        log.debug("[Job {}:*:*] Received request to cancel this job.", jobId);
        JobRequest jobRequest = jobRequestDao.findById(jobId);
        if (jobRequest == null) {
            throw new WfmProcessingException(String.format("A job with the id %d is not known to the system.", jobId));
        }

        if (jobRequest.getStatus() == null) {
            throw new WfmProcessingException(String.format("Job %d must not have a null status.", jobId));
        }

        if (jobRequest.getStatus().isTerminal() || jobRequest.getStatus() == BatchJobStatusType.CANCELLING) {
            log.warn("[Job {}:*:*] This job is in the state of '{}' and cannot be cancelled at this time.", jobId, jobRequest.getStatus().name());
            return false;
        } else {
            log.info("[Job {}:*:*] Cancelling job.", jobId);


            if (inProgressJobs.cancelJob(jobId)) {
                try {
                    // Try to move any pending work items on the queues to the appropriate cancellation queues.
                    // If this operation fails, any remaining pending items will continue to process, but
                    // the future splitters should not create any new work items. In short, if this fails,
                    // the system should not be affected, but the job may not complete any faster.
                    jmsUtils.cancel(jobId);
                } catch (Exception exception) {
                    log.warn("[Job {}:*:*] Failed to remove the pending work elements in the message broker for this job. The job must complete the pending work elements before it will cancel the job.", jobId, exception);
                }
                jobRequest.setStatus(BatchJobStatusType.CANCELLING);
                jobRequestDao.persist(jobRequest);
            } else {
                log.warn("[Job {}:*:*] The job is not in progress and cannot be cancelled at this time.", jobId);
            }
            return true;
        }
    }
}
