/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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
import org.mitre.mpf.interop.JsonStreamingInputObject;
import org.mitre.mpf.interop.JsonStreamingJobRequest;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.businessrules.StreamingJobRequestBo;
import org.mitre.mpf.wfm.data.IdGenerator;
import org.mitre.mpf.wfm.data.InProgressStreamingJobsService;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateDao;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateStreamingJobRequestDaoImpl;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobRequest;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobStatus;
import org.mitre.mpf.wfm.data.entities.transients.TransientPipeline;
import org.mitre.mpf.wfm.data.entities.transients.TransientStream;
import org.mitre.mpf.wfm.data.entities.transients.TransientStreamingJob;
import org.mitre.mpf.wfm.enums.StreamingJobStatusType;
import org.mitre.mpf.wfm.event.JobCompleteNotification;
import org.mitre.mpf.wfm.event.JobProgress;
import org.mitre.mpf.wfm.event.NotificationConsumer;
import org.mitre.mpf.wfm.exceptions.*;
import org.mitre.mpf.wfm.service.JobStatusBroadcaster;
import org.mitre.mpf.wfm.service.PipelineService;
import org.mitre.mpf.wfm.service.StreamingJobMessageSender;
import org.mitre.mpf.wfm.util.CallbackUtils;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

@Component
public class StreamingJobRequestBoImpl implements StreamingJobRequestBo {

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

    private static final Logger log = LoggerFactory.getLogger(StreamingJobRequestBoImpl.class);
    public static final String REF = "streamingJobRequestBoImpl";

    @Autowired
    private PipelineService pipelineService;

    @Autowired
    private PropertiesUtil propertiesUtil;

    @Autowired
    private JsonUtils jsonUtils;

    @Autowired
    private InProgressStreamingJobsService inProgressJobs;

    @Autowired
    @Qualifier(HibernateStreamingJobRequestDaoImpl.REF)
    private HibernateDao<StreamingJobRequest> streamingJobRequestDao;

    @Autowired
    private JobProgress jobProgressStore;

    @Autowired
    private StreamingJobMessageSender streamingJobMessageSender;

    @Autowired
    private CallbackUtils callbackUtils;

    @Autowired
    private JobStatusBroadcaster jobStatusBroadcaster;


    /**
     * Create and initialize a JSON representation of a streaming job request given the raw parameters.
     * This version of the method allows for callbacks to be defined, use null to disable. This method also
     * does value checks, and sets additional parameters for the streaming job given the current state of streaming
     * job request parameters.
     *
     * @param externalId
     * @param pipelineName
     * @param stream
     * @param algorithmProperties
     * @param jobProperties
     * @param buildOutput                  if true, output objects will be stored and this method will assign the output object directory
     * @param priority
     * @param stallTimeout
     * @param healthReportCallbackUri      callback for health reports to be sent using HTTP method POST, pass null to disable health reports
     * @param summaryReportCallbackUri     callback for summary reports to be sent using HTTP method POST, pass null to disable summary reports
     * @return JSON representation of the streaming job request
     */
    @Override
    public JsonStreamingJobRequest createRequest(String externalId, String pipelineName, JsonStreamingInputObject stream,
                                                 Map<String, Map<String, String>> algorithmProperties,
                                                 Map<String, String> jobProperties, boolean buildOutput, int priority,
                                                 long stallTimeout,
                                                 String healthReportCallbackUri, String summaryReportCallbackUri) {
        log.debug("[streaming createRequest] externalId:" + externalId + ", pipeline:" + pipelineName + ", buildOutput:" + buildOutput + ", priority:" + priority +
                ", healthReportCallbackUri:" + healthReportCallbackUri + ", summaryReportCallbackUri:" + summaryReportCallbackUri);
        String jsonHealthReportCallbackUri = StringUtils.trimToNull(healthReportCallbackUri);
        String jsonSummaryReportCallbackUri = StringUtils.trimToNull(summaryReportCallbackUri);

        String outputObjectPath = ""; // initialize output output object to empty string, the path will be set after the streaming job is submitted
        JsonStreamingJobRequest jsonStreamingJobRequest = new JsonStreamingJobRequest(TextUtils.trim(externalId), buildOutput, outputObjectPath,
                pipelineService.createJsonPipeline(pipelineName), priority, stream,
                stallTimeout,
                jsonHealthReportCallbackUri, jsonSummaryReportCallbackUri,
                algorithmProperties, jobProperties);

        return jsonStreamingJobRequest;
    }

    /**
     * Create a new StreamingJobRequest using the provided JSON streaming job request, persist it in the database for long-term storage,
     * and send the streaming job request to the components using the ActiveMQ routes.
     * Upon return, the streaming job will be persisted in the long-term database.
     *
     * @param jsonStreamingJobRequest JSON representation of the job request
     * @return initialized streaming job request
     * @throws WfmProcessingException
     */
    @Override
    public StreamingJobRequest run(JsonStreamingJobRequest jsonStreamingJobRequest) throws WfmProcessingException {
        StreamingJobRequest streamingJobRequestEntity = initialize(jsonStreamingJobRequest);
        return runInternal(streamingJobRequestEntity, jsonStreamingJobRequest, (jsonStreamingJobRequest == null) ? propertiesUtil.getJmsPriority() : jsonStreamingJobRequest.getPriority());
    }

    /**
     * Create a new StreamingJobRequest using the provided JSON job request and persist it in the database for long-term storage.
     * Upon return, the streaming job will be assigned a unique jobId and it will be persisted in the long-term database.
     *
     * @param jsonStreamingJobRequest JSON representation of the job request
     * @return initialized job request, including assignment of a unique jobId
     * @throws WfmProcessingException
     */
    @Override
    public StreamingJobRequest initialize(JsonStreamingJobRequest jsonStreamingJobRequest) throws WfmProcessingException {

        // construct a new streaming job request that is persisted in the long term database, and has a unique jobId
        StreamingJobRequest streamingJobRequestEntity = initializeInternal(new StreamingJobRequest(), jsonStreamingJobRequest);

        // If creation of output objects is enabled, now that a unique jobId has been defined for this streaming job,
        // create the output object directory for the streaming job and store the absolute path to that directory
        // (as a String) in the streaming job request and persist the update in the long-term database.
        if (jsonStreamingJobRequest.isOutputObjectEnabled()) {
            long jobId = streamingJobRequestEntity.getId();
            try {
                // create the output file system to be used for this streaming job
                File outputObjectDir = propertiesUtil.createOutputObjectsDirectory(jobId);
                String outputObjectPath = outputObjectDir.getAbsolutePath();

                jsonStreamingJobRequest.setOutputObjectDirectory(outputObjectPath);

                streamingJobRequestEntity.setOutputObjectDirectory(outputObjectPath);
                streamingJobRequestEntity.setOutputObjectVersion(propertiesUtil.getOutputObjectVersion());

                // update the streaming job request in the long-term database so that it includes the
                // output object directory and object version information
                streamingJobRequestDao.persist(streamingJobRequestEntity);
            } catch (IOException wpe) {
                String errorMessage =
                        "Failed to create the output object file directory for streaming job " + jobId
                                + " due to IO exception.";
                log.error(errorMessage);
                throw new WfmProcessingException(errorMessage, wpe);
            }
        }

        return streamingJobRequestEntity;
    }

    /**
     * Marks a streaming job as CANCELLING in both the TransientStreamingJob and in the long-term database.
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
     * Finish initializing the StreamingJobRequest and persist it in the database for long-term storage.
     * Upon return, the streaming job will be persisted in the long-term database.
     *
     * @param streamingJobRequest     partially initialized streamingJobRequest
     * @param jsonStreamingJobRequest JSON version of the streaming job request that will be serialized into the streamingJobRequests input object
     * @return fully initialized streamingJobRequest
     * @throws WfmProcessingException
     */
    private StreamingJobRequest initializeInternal(StreamingJobRequest streamingJobRequest, JsonStreamingJobRequest jsonStreamingJobRequest) throws WfmProcessingException {
        streamingJobRequest.setPriority(jsonStreamingJobRequest.getPriority());
        streamingJobRequest.setStatus(StreamingJobStatusType.INITIALIZING);
        streamingJobRequest.setTimeReceived(Instant.now());
        streamingJobRequest.setInputObject(jsonUtils.serialize(jsonStreamingJobRequest));
        streamingJobRequest.setPipeline(jsonStreamingJobRequest.getPipeline() == null ? null : TextUtils.trimAndUpper(jsonStreamingJobRequest.getPipeline().getName()));

        // Set output object version and path to null. These will be set later if creation of output objects is enabled
        // and after the job has been persisted to the long term database (when it actually is assigned a unique jobId).
        streamingJobRequest.setOutputObjectDirectory(null);
        streamingJobRequest.setOutputObjectVersion(null);

        // set remaining items that need to be persisted
        streamingJobRequest.setExternalId(jsonStreamingJobRequest.getExternalId());
        streamingJobRequest.setHealthReportCallbackUri(jsonStreamingJobRequest.getHealthReportCallbackUri());
        streamingJobRequest.setSummaryReportCallbackUri(jsonStreamingJobRequest.getSummaryReportCallbackUri());
        streamingJobRequest.setStreamUri(jsonStreamingJobRequest.getStream().getStreamUri());

        // store the streaming job request in the long-term database.  Once the streaming job has been
        // successfully persisted to the long-term database, the streaming jobs unique jobId will be valid.
        return streamingJobRequestDao.persist(streamingJobRequest);
    }

    /**
     * Send the streaming job request to the components via the Master Node Manager.
     *
     * @param streamingJobRequestEntity the streaming job request as persisted in the long term database
     * @param jsonStreamingJobRequest   JSON representation of the streaming job request
     * @param priority
     * @return
     * @throws WfmProcessingException Exception thrown if there is a failure while creating transient objects
     */
    private StreamingJobRequest runInternal(StreamingJobRequest streamingJobRequestEntity, JsonStreamingJobRequest jsonStreamingJobRequest, int priority) throws WfmProcessingException {
        long jobId = streamingJobRequestEntity.getId();
        log.info("[Streaming Job {}|*|*] is running at priority {}.", streamingJobRequestEntity.getId(), priority);

        String errorMessage = null;
        Exception errorException = null; // If an exception error is caught, save it so it can be provided as root cause for the WfmProcessingException
        try {
            TransientPipeline transientPipeline = TransientPipeline.from(jsonStreamingJobRequest.getPipeline());
            TransientStreamingJob transientStreamingJob = buildStreamingJob(jobId, streamingJobRequestEntity, transientPipeline, jsonStreamingJobRequest);
            streamingJobMessageSender.launchJob(transientStreamingJob);

        } catch (InvalidPipelineObjectWfmProcessingException ipe) {
            errorMessage = "Streaming Job #" + jobId + " did not specify a valid pipeline.";
            log.error(errorMessage, ipe);
            errorException = ipe; // Save as root cause of error
        } catch (Exception e) {
            errorMessage = "Failed to parse the input object for Streaming Job #" + jobId
                + " due to an exception.";
            log.error(errorMessage, e);
            errorException = e; // Save as root cause of error
        }

        // Mark any exception from building the transient objects as a failure by recording the error in the persistent database and throwing an exception.
        // Note that it doesn't matter which exception occurred, the streaming job has to be marked as failed in the long-term database.
        if ( errorMessage != null ) {
            try {
                // make an effort to mark the streaming job as failed in the long-term database
                streamingJobRequestEntity.setStatus(StreamingJobStatusType.JOB_CREATION_ERROR, errorMessage);
                streamingJobRequestEntity.setTimeCompleted(Instant.now());
                streamingJobRequestEntity = streamingJobRequestDao.persist(streamingJobRequestEntity);
            } catch (Exception persistException) {
                log.warn("Failed to mark Streaming Job #{} as failed due to an exception. It will remain it its current state until manually changed.", streamingJobRequestEntity, persistException);
            } // end of persist Exception catch

            // Throw an exception providing with it the root cause, indicating a failure to create the transient objects
            throw new WfmProcessingException("Failed to create transient job: " + errorMessage, errorException);
        } // end of Exception catch

        return streamingJobRequestEntity;
    }

    /**
     * Builds a TransientStreamingJob.
     *
     * @param jobId                     unique id that is assigned to this job
     * @param streamingJobRequestEntity the streaming job request as persisted in the long term database (mysql)
     * @param transientPipeline         pipeline that has been created for this streaming job
     * @param jsonStreamingJobRequest   JSON representation of the streaming job request
     * @return TransientStreamingJob
     * @throws InvalidPipelineObjectWfmProcessingException InvalidPipelineObjectWfmProcessingException is thrown if the requested pipeline is invalid.
     */
    private TransientStreamingJob buildStreamingJob(long jobId, StreamingJobRequest streamingJobRequestEntity, TransientPipeline transientPipeline,
                                                    JsonStreamingJobRequest jsonStreamingJobRequest) throws InvalidPipelineObjectWfmProcessingException {
        TransientStreamingJob transientStreamingJob = inProgressJobs.addJob(
                streamingJobRequestEntity.getId(),
                jsonStreamingJobRequest.getExternalId(),
                transientPipeline,
                buildTransientStream(jsonStreamingJobRequest.getStream()),
                jsonStreamingJobRequest.getPriority(),
                jsonStreamingJobRequest.getStallTimeout(),
                jsonStreamingJobRequest.isOutputObjectEnabled(),
                jsonStreamingJobRequest.getOutputObjectDirectory(),
                jsonStreamingJobRequest.getHealthReportCallbackUri(),
                jsonStreamingJobRequest.getSummaryReportCallbackUri(),
                jsonStreamingJobRequest.getJobProperties(),
                jsonStreamingJobRequest.getAlgorithmProperties());

        if (transientPipeline == null) {
            String errorMessage = "Pipeline built from " + jsonStreamingJobRequest.getPipeline() + " is invalid.";
            inProgressJobs.setJobStatus(jobId, StreamingJobStatusType.JOB_CREATION_ERROR, errorMessage);
            throw new InvalidPipelineObjectWfmProcessingException(errorMessage);
        }

        // Report the current job status (INITIALIZING). The job will report IN_PROGRESS once the WFM receives a
        // status message from the component process.
        handleJobStatusChange(streamingJobRequestEntity.getId(),
                new StreamingJobStatus(streamingJobRequestEntity.getStatus()),
                System.currentTimeMillis());

        return transientStreamingJob;
    }

    private static TransientStream buildTransientStream(JsonStreamingInputObject jsonInputStream) {
        return new TransientStream(
                IdGenerator.next(),
                jsonInputStream.getStreamUri(),
                jsonInputStream.getSegmentSize(),
                jsonInputStream.getMediaProperties());
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

        TransientStreamingJob transientJob = inProgressJobs.getJob(jobId);

        transientJob.getHealthReportCallbackURI()
                .ifPresent(uri -> callbackUtils.sendHealthReportCallback(uri, Collections.singletonList(transientJob)));


        StreamingJobRequest streamingJobRequest = streamingJobRequestDao.findById(jobId);
        if (status.isTerminal()) {
            streamingJobRequest.setTimeCompleted(Instant.ofEpochMilli(timestamp));
        }
        streamingJobRequest.setStatus(status.getType());

        if (status.getDetail() != null) {
            streamingJobRequest.setStatusDetail(status.getDetail());
        }

        streamingJobRequestDao.persist(streamingJobRequest);

        if (status.isTerminal()) {
            if (transientJob.isCleanupEnabled()) {
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
            jobProgressStore.setJobProgress(jobId, 100.0f);

            log.info("[Streaming Job {}:*:*] Streaming Job complete!", jobId);
        }
    }

    @Override
    public void handleNewActivityAlert(long jobId, long frameId, long timestamp) {
        inProgressJobs.setLastJobActivity(jobId, frameId, Instant.ofEpochMilli(timestamp));
        TransientStreamingJob job = inProgressJobs.getJob(jobId);
        job.getHealthReportCallbackURI()
                .ifPresent(uri -> callbackUtils.sendHealthReportCallback(uri, Collections.singletonList(job)));
    }

    @Override
    public void handleNewSummaryReport(JsonSegmentSummaryReport summaryReport) {
        TransientStreamingJob transientStreamingJob = inProgressJobs.getJob(summaryReport.getJobId());

        transientStreamingJob.getExternalId()
                .ifPresent(summaryReport::setExternalId);

        transientStreamingJob.getSummaryReportCallbackURI()
                .ifPresent(uri -> callbackUtils.sendSummaryReportCallback(summaryReport, uri));

        if (transientStreamingJob.isOutputEnabled()) {
            try {
                String outputPath = transientStreamingJob.getOutputObjectDirectory();
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
