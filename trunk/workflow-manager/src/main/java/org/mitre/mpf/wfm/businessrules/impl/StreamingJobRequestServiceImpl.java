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


import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.interop.JsonSegmentSummaryReport;
import org.mitre.mpf.rest.api.JobCreationStreamData;
import org.mitre.mpf.rest.api.StreamingJobCreationRequest;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.businessrules.StreamingJobRequestService;
import org.mitre.mpf.wfm.data.IdGenerator;
import org.mitre.mpf.wfm.data.InProgressStreamingJobsService;
import org.mitre.mpf.wfm.data.access.StreamingJobRequestDao;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobRequest;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobStatus;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.mitre.mpf.wfm.data.entities.persistent.MediaStreamInfo;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJob;
import org.mitre.mpf.wfm.enums.StreamingJobStatusType;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.event.JobCompleteNotification;
import org.mitre.mpf.wfm.event.JobProgress;
import org.mitre.mpf.wfm.event.NotificationConsumer;
import org.mitre.mpf.wfm.exceptions.JobAlreadyCancellingWfmProcessingException;
import org.mitre.mpf.wfm.exceptions.JobCancellationInvalidJobIdWfmProcessingException;
import org.mitre.mpf.wfm.exceptions.JobCancellationInvalidOutputObjectDirectoryWfmProcessingException;
import org.mitre.mpf.wfm.exceptions.JobCancellationOutputObjectDirectoryCleanupWarningWfmProcessingException;
import org.mitre.mpf.wfm.service.pipeline.PipelineService;
import org.mitre.mpf.wfm.service.JobStatusBroadcaster;
import org.mitre.mpf.wfm.service.StreamingJobMessageSender;
import org.mitre.mpf.wfm.util.CallbackUtils;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

@Component
public class StreamingJobRequestServiceImpl implements StreamingJobRequestService {

    private Set<NotificationConsumer<JobCompleteNotification>> consumers = new ConcurrentSkipListSet<>();


// TODO: Enable this code if and when we handle streaming job resubmission.
//    /**
//     * Internal enumeration which is used to indicate whether a resubmitted streaming job should use the original
//     * priority value or a new value which has been provided.
//     */
//    private static enum PriorityPolicy {
//        /**
//         * Resubmits the streaming job with a different priority.
//         */
//        PROVIDED,
//
//        /**
//         * DEFAULT: Resubmits the streaming job using the same priority as its first run.
//         */
//        EXISTING;
//
//        /**
//         * The default action is to re-use the original priority.
//         */
//        public static final PriorityPolicy DEFAULT = EXISTING;
//    }

    private static final Logger log = LoggerFactory.getLogger(StreamingJobRequestServiceImpl.class);

    @Autowired
    private PipelineService pipelineService;

    @Autowired
    private PropertiesUtil propertiesUtil;

    @Autowired
    private JsonUtils jsonUtils;

    @Autowired
    private InProgressStreamingJobsService inProgressJobs;

    @Autowired
    private StreamingJobRequestDao streamingJobRequestDao;

    @Autowired
    private JobProgress jobProgressStore;

    @Autowired
    private StreamingJobMessageSender streamingJobMessageSender;

    @Autowired
    private CallbackUtils callbackUtils;

    @Autowired
    private JobStatusBroadcaster jobStatusBroadcaster;



    @Override
    public StreamingJobRequest run(StreamingJobCreationRequest jobCreationRequest) {
        validateRequest(jobCreationRequest);

        // Create pipeline before persisting job so any pipeline errors will get reported before the job is
        // persisted.
        JobPipelineElements pipeline
                = pipelineService.getStreamingPipelineElements(jobCreationRequest.getPipelineName());

        StreamingJobRequest jobRequestEntity = createJobRequestEntity(jobCreationRequest, pipeline);

        try {
            boolean enableOutput = Optional.ofNullable(jobCreationRequest.getEnableOutputToDisk())
                    .orElseGet(propertiesUtil::isOutputObjectsEnabled);
            if (enableOutput) {
                File outputObjectDir = propertiesUtil.createOutputObjectsDirectory(jobRequestEntity.getId());
                jobRequestEntity.setOutputObjectDirectory(outputObjectDir.getAbsolutePath());
                jobRequestEntity.setOutputObjectVersion(propertiesUtil.getOutputObjectVersion());
            }


            StreamingJob job = createJob(
                    jobCreationRequest, jobRequestEntity, pipeline, enableOutput);

            jobRequestEntity.setJob(jsonUtils.serialize(job));

            jobRequestEntity = streamingJobRequestDao.persist(jobRequestEntity);

            job.getHealthReportCallbackURI()
                    .ifPresent(uri -> callbackUtils.sendHealthReportCallback(uri, List.of(job)));

            streamingJobMessageSender.launchJob(job);


            return jobRequestEntity;
        }
        catch (Exception e) {
            String errorMsg = "An error occurred during job creation: " + e.getMessage();
            log.error(errorMsg, e);
            jobRequestEntity.setStatus(StreamingJobStatusType.JOB_CREATION_ERROR, errorMsg);
            jobRequestEntity.setTimeCompleted(Instant.now());
            streamingJobRequestDao.persist(jobRequestEntity);
            throw e;
        }
    }


    private StreamingJobRequest createJobRequestEntity(StreamingJobCreationRequest jobCreationRequest,
                                                       JobPipelineElements pipeline) {
        int priority = Optional.ofNullable(jobCreationRequest.getPriority())
                .orElseGet(propertiesUtil::getJmsPriority);

        var jobRequestEntity = new StreamingJobRequest();
        jobRequestEntity.setPriority(priority);
        jobRequestEntity.setStatus(StreamingJobStatusType.INITIALIZING);
        jobRequestEntity.setTimeReceived(Instant.now());
        jobRequestEntity.setPipeline(pipeline.getName());
        jobRequestEntity.setExternalId(StringUtils.trimToNull(jobCreationRequest.getExternalId()));

        jobRequestEntity.setHealthReportCallbackUri(
                StringUtils.trimToNull(jobCreationRequest.getHealthReportCallbackUri()));

        jobRequestEntity.setSummaryReportCallbackUri(
                StringUtils.trimToNull(jobCreationRequest.getSummaryReportCallbackUri()));

        jobRequestEntity.setStreamUri(jobCreationRequest.getStream().getStreamUri());

        return streamingJobRequestDao.persist(jobRequestEntity);
    }


    private StreamingJob createJob(StreamingJobCreationRequest jobCreationRequest,
                                   StreamingJobRequest jobRequestEntity,
                                   JobPipelineElements pipeline,
                                   boolean enableOutput) {
        var stream = new MediaStreamInfo(
                IdGenerator.next(),
                jobCreationRequest.getStream().getStreamUri(),
                jobCreationRequest.getStream().getSegmentSize(),
                jobCreationRequest.getStream().getMediaProperties());

        return inProgressJobs.addJob(
                jobRequestEntity.getId(),
                jobRequestEntity.getExternalId(),
                pipeline,
                stream,
                jobRequestEntity.getPriority(),
                jobCreationRequest.getStallTimeout(),
                enableOutput,
                jobRequestEntity.getOutputObjectDirectory(),
                jobRequestEntity.getHealthReportCallbackUri(),
                jobRequestEntity.getSummaryReportCallbackUri(),
                jobCreationRequest.getJobProperties(),
                jobCreationRequest.getAlgorithmProperties());
    }



    private static final Set<UriScheme> SUPPORTED_URI_SCHEMES = EnumSet.of(
            UriScheme.RTSP, UriScheme.HTTP, UriScheme.HTTPS);


    private static void validateRequest(StreamingJobCreationRequest jobCreationRequest) {
        JobCreationStreamData stream = jobCreationRequest.getStream();
        if (stream == null || StringUtils.isBlank(stream.getStreamUri())) {
            throw new WfmProcessingException("A stream URI was not provided");
        }
        if (stream.getSegmentSize() < 10) {
            throw new WfmProcessingException("The segment size must be at least 10.");
        }

        try {
            var uriScheme = UriScheme.get(new URI(stream.getStreamUri()));
            if (!SUPPORTED_URI_SCHEMES.contains(uriScheme)) {
                throw new WfmProcessingException(
                        "The scheme of the provided URI is not supported. Only rtsp, http, and https are supported.");
            }
        }
        catch (URISyntaxException e) {
            throw new WfmProcessingException("Invalid URI: " + e.getMessage(), e);

        }
    }




    /**
     * Marks a streaming job as CANCELLING in both the StreamingJob and in the long-term database.
     * @param jobId     The OpenMPF-assigned identifier for the streaming job. The job must be a streaming job.
     * @param doCleanup if true, delete the streaming job files from disk as part of cancelling the streaming job.
     * @exception JobAlreadyCancellingWfmProcessingException may be thrown if the streaming job has already been cancelled or
     * if the streaming jobs status is already terminal.
     * @exception JobCancellationInvalidJobIdWfmProcessingException may be thrown if the
     * streaming job can't be cancelled due to an error with identification of the streaming job using the specified jobId.
     */
    @Override
    public synchronized void cancel(long jobId, boolean doCleanup) throws WfmProcessingException {

        log.debug("[Streaming Job {}:*:*] Received request to cancel this streaming job.", jobId);
        StreamingJobRequest streamingJobRequest = streamingJobRequestDao.findById(jobId);
        if (streamingJobRequest == null) {
            throw new JobCancellationInvalidJobIdWfmProcessingException("A streaming job with the id " + jobId + " is not known to the system.");
        }

        if (streamingJobRequest.getStatus() == null) {
            throw new WfmProcessingException("Streaming job " + jobId + " must not have a null status.");
        }

        if (streamingJobRequest.getStatus().isTerminal() || streamingJobRequest.getStatus() == StreamingJobStatusType.CANCELLING) {
            String errorMessage = "Streaming job " + jobId + " already has state '" + streamingJobRequest.getStatus().name() + "' and cannot be cancelled at this time.";
            if (doCleanup) {
                errorMessage += " Attempted cleanup anyway.";
                try {
                    cleanup(jobId, streamingJobRequest.getOutputObjectDirectory());
                } catch (WfmProcessingException e) {
                    log.warn(e.getMessage());
                }
            }
            throw new JobAlreadyCancellingWfmProcessingException(errorMessage);
        }

        log.info("[Streaming Job {}:*:*] Cancelling streaming job.", jobId);
        inProgressJobs.cancelJob(jobId, doCleanup);

        // set job status as cancelling, and persist that changed state in the database
        streamingJobRequest.setStatus(StreamingJobStatusType.CANCELLING);
        streamingJobRequestDao.persist(streamingJobRequest);

        streamingJobMessageSender.stopJob(jobId);

        handleJobStatusChange(jobId, new StreamingJobStatus(StreamingJobStatusType.CANCELLING),
                System.currentTimeMillis());
    }

    /**
     * Deletes files generated by a streaming job.
     * @param jobId The OpenMPF-assigned identifier for the streaming job. The job must be a streaming job.
     * @param outputObjectDirPath location where the job's generated files are stored
     * @exception JobCancellationOutputObjectDirectoryCleanupWarningWfmProcessingException may be thrown
     * if the streaming job has been cancelled, but the jobs output object directory couldn't be deleted when doCleanup is enabled.
     * @exception JobCancellationInvalidOutputObjectDirectoryWfmProcessingException may be thrown if the streaming job has been cancelled, but an error
     * was detected in specification of the jobs output object directory.
     */
    @Override
    public synchronized void cleanup(long jobId, String outputObjectDirPath) throws WfmProcessingException {
        try {

            if (outputObjectDirPath == null || outputObjectDirPath.isEmpty()) {
                String warningMessage = "Streaming job " + jobId
                        + " has no output object directory to cleanup."
                        + " Output to disk was not enabled when the job was created.";
                throw new JobCancellationOutputObjectDirectoryCleanupWarningWfmProcessingException(warningMessage);
            }

            File outputObjectDir = new File(outputObjectDirPath);
            if (!outputObjectDir.exists()) {
                // It is not an error if the output object directory doesn't exist. Just log that fact and return.
                log.info("[Streaming Job {}:*:*] Output object directory '{}' not deleted because it doesn't exist.",
                        jobId, outputObjectDirPath);
                return;
            }

            if (!outputObjectDir.isDirectory()) {
                String errorMessage = "Streaming job " + jobId
                        + " output object directory '" + outputObjectDirPath + "' isn't a directory."
                        + " Can't delete output object directory.";
                throw new JobCancellationInvalidOutputObjectDirectoryWfmProcessingException(errorMessage);
            }

            // As requested, delete the output object directory for this streaming job.
            log.debug("[Streaming Job {}:*:*] Output object directory '{}' is being deleted as specified by the cancel request.",
                    jobId, outputObjectDirPath);

            // First, make sure that the output object directory specified doesn't end with a symbolic link.
            // FileUtils.deleteDirectory doesn't follow symbolic links at the end of a path, so if the output object directory for this
            // streaming job is a symbolic link it won't be successfully deleted. Note that symbolic links in the middle of the path are ok.
            if (FileUtils.isSymlink(outputObjectDir)) {
                // If the output object directory was properly created by OpenMPF, it would not be a symbolic link.
                String errorMessage = "Streaming job " + jobId
                        + " output object directory '" + outputObjectDirPath + "' is a symbolic link, not a directory."
                        + " Did not delete output object directory.";
                throw new JobCancellationInvalidOutputObjectDirectoryWfmProcessingException(errorMessage);
            }

            // Use FileUtils to recursively delete the output object file directory.
            FileUtils.deleteDirectory(outputObjectDir);

            // It's possible that the output object directory wasn't successfully deleted, but no exception is thrown. Check for that.
            if (outputObjectDir.getAbsoluteFile().exists()) {
                String warningMessage = "Streaming job " + jobId
                        + " failed to fully cleanup the output object directory. '"
                        + outputObjectDirPath + "' still exists.";
                throw new JobCancellationOutputObjectDirectoryCleanupWarningWfmProcessingException(warningMessage);
            }

            log.info("[Streaming Job {}:*:*] Output object directory '{}' was successfully deleted as specified by the cancel request.",
                    jobId, outputObjectDirPath);

        } catch (IOException | IllegalArgumentException e) {
            // Marking this as a warning. The warning includes the path to the output object directory, so the client can delete it if necessary.
            String warningMessage = "Streaming job " + jobId
                    + " failed to cleanup the output object directory '"
                    + outputObjectDirPath + "' due to exception: " + e.getMessage();
            throw new JobCancellationOutputObjectDirectoryCleanupWarningWfmProcessingException(warningMessage, e);
        }

        /*
        // TODO: Forward the warning/error along in the mpfResponse if and when we implement a separate REST endpoint for cleanup.
        } catch (JobAlreadyCancellingWfmProcessingException | JobCancellationOutputObjectDirectoryCleanupWarningWfmProcessingException we) {
            // If one of these warning exceptions were caught, log the warning.
            log.warn(we.getMessage());
            cleanupResponse = new StreamingJobCancelResponse(jobId,
                    streamingJobRequest.getOutputObjectDirectory(), doCleanup,
                    MpfResponse.RESPONSE_CODE_WARNING, we.getMessage());

        } catch (JobCancellationInvalidOutputObjectDirectoryWfmProcessingException idee) {
            // If the output object directory couldn't be deleted due to an error, log the error. and forward the error along in the mpfResponse.
            log.error(idee.getMessage());
            cleanupResponse = new StreamingJobCancelResponse(jobId,
                    streamingJobRequest.getOutputObjectDirectory(), doCleanup,
                    MpfResponse.RESPONSE_CODE_ERROR, idee.getMessage());
        }
        */
    }


    /**
     * Complete a streaming job by updating the job in the persistent database(s), make any final callbacks for the job.
     * Use this version of the method when detail info is not required to accompany streaming job status.
     *
     * @param jobId unique id for a streaming job
     * @throws WfmProcessingException
     */
    @Override
    public void handleJobStatusChange(long jobId, StreamingJobStatus status, long timestamp) {
        inProgressJobs.setJobStatus(jobId, status);

        StreamingJob job = inProgressJobs.getJob(jobId);

        job.getHealthReportCallbackURI()
                .ifPresent(uri -> callbackUtils.sendHealthReportCallback(uri, List.of(job)));


        StreamingJobRequest streamingJobRequest = streamingJobRequestDao.findById(jobId);
        if (status.isTerminal()) {
            streamingJobRequest.setTimeCompleted(Instant.ofEpochMilli(timestamp));
            streamingJobRequest.setJob(jsonUtils.serialize(job));
        }
        streamingJobRequest.setStatus(status.getType());

        if (status.getDetail() != null) {
            streamingJobRequest.setStatusDetail(status.getDetail());
        }

        streamingJobRequestDao.persist(streamingJobRequest);

        if (status.isTerminal()) {
            if (job.isCleanupEnabled()) {
                try {
                    cleanup(jobId, streamingJobRequest.getOutputObjectDirectory());
                } catch (WfmProcessingException e) {
                    log.warn(e.getMessage());
                }
            }

            inProgressJobs.clearJob(jobId);

            JobCompleteNotification jobCompleteNotification = new JobCompleteNotification(jobId);

            for (NotificationConsumer<JobCompleteNotification> consumer : consumers) {
                if (!jobCompleteNotification.isConsumed()) {
                    try {
                        consumer.onNotification(this, new JobCompleteNotification(jobId));
                    } catch (Exception exception) {
                        log.warn("Completion Streaming Job {} consumer '{}' threw an exception.", jobId, consumer, exception);
                    }
                }
            }

            jobStatusBroadcaster.broadcast(jobId, 100, status.getType(), Instant.ofEpochMilli(timestamp));
            jobProgressStore.removeJob(jobId);

            log.info("[Streaming Job {}:*:*] Streaming Job complete!", jobId);
        }
    }

    @Override
    public void handleNewActivityAlert(long jobId, long frameId, long timestamp) {
        inProgressJobs.setLastJobActivity(jobId, frameId, Instant.ofEpochMilli(timestamp));
        StreamingJob job = inProgressJobs.getJob(jobId);
        job.getHealthReportCallbackURI()
                .ifPresent(uri -> callbackUtils.sendHealthReportCallback(uri, List.of(job)));
    }

    @Override
    public void handleNewSummaryReport(JsonSegmentSummaryReport summaryReport) {
        StreamingJob job = inProgressJobs.getJob(summaryReport.getJobId());

        job.getExternalId()
                .ifPresent(summaryReport::setExternalId);

        job.getSummaryReportCallbackURI()
                .ifPresent(uri -> callbackUtils.sendSummaryReportCallback(summaryReport, uri));

        if (job.isOutputEnabled()) {
            try {
                String outputPath = job.getOutputObjectDirectory();
                File outputFile = propertiesUtil.createStreamingOutputObjectsFile(summaryReport.getReportDate(),
                                                                                  new File(outputPath));
                jsonUtils.serialize(summaryReport, outputFile);
            }
            catch (IOException e) {
                String message = String.format("Failed to write the JSON summary report for job '%s' due to: %s",
                                               summaryReport.getJobId(), e.getMessage());
                throw new UncheckedIOException(message, e);
            }
        }
    }


    /**
     * Send health reports to the health report callbacks associated with the active streaming jobs.
     * Note that OpenMPF supports sending periodic health reports that contain health for all streaming jobs who have
     * defined the same HealthReportCallbackUri.
     * Note that out-of-cycle health reports that may have been sent due to a change in job status will not
     * delay sending of the periodic (i.e. scheduled) health report.
     */
    @Override
    public void sendHealthReports() {
        inProgressJobs.getJobsGroupedByHealthReportUri()
                .forEach(callbackUtils::sendHealthReportCallback);
    }

    @Override
    public void subscribe(NotificationConsumer<JobCompleteNotification> consumer) {
        consumers.add(consumer);
    }

    @Override
    public void unsubscribe(NotificationConsumer<JobCompleteNotification> consumer) {
        consumers.remove(consumer);
    }
}
