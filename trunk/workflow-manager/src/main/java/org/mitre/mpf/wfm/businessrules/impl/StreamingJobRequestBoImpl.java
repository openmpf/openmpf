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
import org.mitre.mpf.interop.*;
import org.mitre.mpf.interop.util.TimeUtils;
import org.mitre.mpf.mvc.controller.AtmosphereController;
import org.mitre.mpf.mvc.model.JobStatusMessage;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.businessrules.StreamingJobRequestBo;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.RedisImpl;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateDao;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateStreamingJobRequestDaoImpl;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobRequest;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.enums.JobStatus;
import org.mitre.mpf.wfm.event.JobCompleteNotification;
import org.mitre.mpf.wfm.event.JobProgress;
import org.mitre.mpf.wfm.event.NotificationConsumer;
import org.mitre.mpf.wfm.exceptions.JobAlreadyCancellingWfmProcessingException;
import org.mitre.mpf.wfm.exceptions.JobCancellationInvalidJobIdWfmProcessingException;
import org.mitre.mpf.wfm.exceptions.JobCancellationInvalidOutputObjectDirectoryWfmProcessingException;
import org.mitre.mpf.wfm.exceptions.JobCancellationOutputObjectDirectoryCleanupWarningWfmProcessingException;
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
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

@Component
public class StreamingJobRequestBoImpl implements StreamingJobRequestBo {

    private static final String INVALID_PIPELINE_MESSAGE = "INVALID_PIPELINE_MESSAGE";
    private static final String CREATE_TRANSIENT_JOB_FAILED_MESSAGE = "CREATE_TRANSIENT_JOB_FAILED_MESSAGE";

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
    @Qualifier(RedisImpl.REF)
    private Redis redis;

    @Autowired
    @Qualifier(HibernateStreamingJobRequestDaoImpl.REF)
    private HibernateDao<StreamingJobRequest> streamingJobRequestDao;

    @Autowired
    private JobProgress jobProgressStore;

    @Autowired
    private StreamingJobMessageSender streamingJobMessageSender;

    @Autowired
    private CallbackUtils callbackUtils;

    /**
     * Converts a pipeline represented in JSON to a {@link TransientPipeline} instance.
     */
    private TransientPipeline buildPipeline(JsonPipeline jsonPipeline) {

        if (jsonPipeline == null) {
            return null;
        }

        String name = jsonPipeline.getName();
        String description = jsonPipeline.getDescription();
        TransientPipeline transientPipeline = new TransientPipeline(name, description);

        // Iterate through the pipeline's stages and add them to the pipeline protocol buffer.
        for (JsonStage stage : jsonPipeline.getStages()) {
            transientPipeline.getStages().add(buildStage(stage));
        }

        return transientPipeline;
    }

    /**
     * Maps a stage (represented in JSON) to a {@link TransientStage} instance.
     */
    private TransientStage buildStage(JsonStage stage) {
        String name = stage.getName();
        String description = stage.getDescription();
        String operation = stage.getActionType();

        TransientStage transientStage = new TransientStage(name, description, ActionType.valueOf(TextUtils.trimAndUpper(operation)));

        // Iterate through the stage's actions and add them to the stage protocol buffer.
        for (JsonAction action : stage.getActions()) {
            transientStage.getActions().add(buildAction(action));
        }

        return transientStage;
    }

    /**
     * Maps an action (represented in JSON) to a {@link TransientAction} instance.
     */
    private TransientAction buildAction(JsonAction action) {
        String name = action.getName();
        String description = action.getDescription();
        String algorithm = action.getAlgorithm();

        TransientAction transientAction = new TransientAction(name, description, algorithm);

        // Finally, iterate through all of the properties in this action and copy them to the protocol buffer.
        for (Map.Entry<String, String> property : action.getProperties().entrySet()) {
            if (StringUtils.isNotBlank(property.getKey()) && StringUtils.isNotBlank(property.getValue())) {
                transientAction.getProperties().put(property.getKey().toUpperCase(), property.getValue());
            }
        }

        return transientAction;
    }

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
     * Marks a streaming job as CANCELLING in both REDIS and in the long-term database.
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
        } else {

            assert streamingJobRequest.getStatus()
                != null : "Streaming jobs must not have a null status.";

            if (streamingJobRequest.getStatus().isTerminal() || streamingJobRequest.getStatus() == JobStatus.CANCELLING) {
                String errorMessage = "Streaming job " + jobId +" already has state '" + streamingJobRequest.getStatus().name() + "' and cannot be cancelled at this time.";
                if (doCleanup) {
                    cleanup(jobId, streamingJobRequest.getOutputObjectDirectory());
                    errorMessage += " Attempted cleanup anyway.";
                }
                throw new JobAlreadyCancellingWfmProcessingException(errorMessage);
            } else {
                log.info("[Streaming Job {}:*:*] Cancelling streaming job.", jobId);

                // Mark the streaming job as cancelled in Redis
                if (redis.cancel(jobId)) {
                    redis.setJobStatus(jobId, JobStatus.CANCELLING);
                    redis.setDoCleanup(jobId, doCleanup);

                    // Try to move any pending work items on the queues to the appropriate cancellation queues.
                    // If this operation fails, any remaining pending items will continue to process, but
                    // the future splitters should not create any new work items. In short, if this fails,
                    // the system should not be affected, but the streaming job may not complete any faster.

                    streamingJobMessageSender.stopJob(jobId);

                    // set job status as cancelling, and persist that changed state in the database
                    streamingJobRequest.setStatus(JobStatus.CANCELLING);
                    streamingJobRequestDao.persist(streamingJobRequest);
                } else {
                    // Couldn't find the requested job as a batch or as a streaming job in REDIS.
                    // Generate an error for the race condition where Redis and the persistent database reflect different states.
                    throw new JobCancellationInvalidJobIdWfmProcessingException("Streaming job with jobId " + jobId + " is not found, it cannot be cancelled at this time.");
                }

            }
        }

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
    public synchronized void cleanup(long jobId, String outputObjectDirPath) throws WfmProcessingException {

        if (outputObjectDirPath == null || outputObjectDirPath.isEmpty()) {
            String errorMessage = "Streaming job " + jobId
                    + " output object directory is invalid: '" + outputObjectDirPath
                    + "'. Can't delete output object directory.";
            throw new JobCancellationInvalidOutputObjectDirectoryWfmProcessingException(errorMessage);
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

        try {
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

            // the output object directory was successfully deleted, log this fact
            log.info("[Streaming Job {}:*:*] Output object directory '{}' was successfully deleted as specified by the cancel request.",
                    jobId, outputObjectDirPath);

        } catch (IOException | IllegalArgumentException e) {
            // Marking this as a warning. The warning include the path to the output object directory, so the client can delete it if necessary.
            String warningMessage = "Streaming job " + jobId
                    + " failed to cleanup the output object directory '"
                    + outputObjectDirPath + "' due to exception: " + e.getMessage();
            throw new JobCancellationOutputObjectDirectoryCleanupWarningWfmProcessingException(warningMessage, e);
        }
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
        streamingJobRequest.setStatus(JobStatus.INITIALIZED);
        streamingJobRequest.setTimeReceived(new Date());
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

        // First, create the Transient objects (i.e. storage in REDIS). This code was formerly in the job creation processor (Camel) software
        // get the unique job id that has been assigned for this streaming job.
        long jobId = streamingJobRequestEntity.getId();
        log.info("[Streaming Job {}|*|*] is running at priority {}.", streamingJobRequestEntity.getId(), priority);

        try {

            // persist the pipeline and streaming job in REDIS
            TransientPipeline transientPipeline = buildPipeline(jsonStreamingJobRequest.getPipeline());
            TransientStreamingJob transientStreamingJob = buildStreamingJob(jobId, streamingJobRequestEntity, transientPipeline, jsonStreamingJobRequest);
            streamingJobMessageSender.launchJob(transientStreamingJob);

        } catch (Exception e) {
            // mark any exception as a failure by recording the error in the persistent database and throwing an exception
            try {
                // make an effort to mark the streaming job as failed
                if (INVALID_PIPELINE_MESSAGE.equals(e.getMessage())) {
                    log.warn("Streaming Job #{} did not specify a valid pipeline.", jobId);
                } else {
                    log.warn("Failed to parse the input object for Streaming Job #{} due to an exception.", streamingJobRequestEntity.getId(), e);
                }
                streamingJobRequestEntity.setStatus(JobStatus.JOB_CREATION_ERROR);
                streamingJobRequestEntity.setTimeCompleted(new Date());
                streamingJobRequestEntity = streamingJobRequestDao.persist(streamingJobRequestEntity);
            } catch (Exception persistException) {
                log.warn("Failed to mark Streaming Job #{} as failed due to an exception. It will remain it its current state until manually changed.", streamingJobRequestEntity, persistException);
            } // end of persist Exception catch

            // throw an exception, failure to create the transient objects
            throw new WfmProcessingException(CREATE_TRANSIENT_JOB_FAILED_MESSAGE);
        } // end of Exception catch

        // TODO send the streaming job to the master node manager
        log.info(this.getClass().getName() + ".runInternal: TODO notification of new streaming job " + streamingJobRequestEntity.getId() + " to Components via Master Node Manager.");

        return streamingJobRequestEntity;
    }

    /**
     * Build a TransientStreamingJob that is persisted in REDIS.
     *
     * @param jobId                     unique id that is assigned to this job
     * @param streamingJobRequestEntity the streaming job request as persisted in the long term database (mysql)
     * @param transientPipeline         pipeline that has been created for this streaming job that has been persisted in REDIS
     * @param jsonStreamingJobRequest   JSON representation of the streaming job request
     * @return TransientStreamingJob that is persisted in REDIS
     * @throws WfmProcessingException
     */
    private TransientStreamingJob buildStreamingJob(long jobId, StreamingJobRequest streamingJobRequestEntity, TransientPipeline transientPipeline,
                                                    JsonStreamingJobRequest jsonStreamingJobRequest) throws WfmProcessingException {
        TransientStreamingJob transientStreamingJob = new TransientStreamingJob(
                streamingJobRequestEntity.getId(),
                jsonStreamingJobRequest.getExternalId(), transientPipeline,
                jsonStreamingJobRequest.getPriority(),
                jsonStreamingJobRequest.getStallTimeout(),
                jsonStreamingJobRequest.isOutputObjectEnabled(),
                jsonStreamingJobRequest.getOutputObjectDirectory(),
                false,
                jsonStreamingJobRequest.getHealthReportCallbackUri(),
                jsonStreamingJobRequest.getSummaryReportCallbackUri());

        transientStreamingJob.getOverriddenJobProperties()
                .putAll(jsonStreamingJobRequest.getJobProperties());

        // algorithm properties should override any previously set properties (note: priority scheme enforced in DetectionSplitter)
        transientStreamingJob.getOverriddenAlgorithmProperties()
                .putAll(jsonStreamingJobRequest.getAlgorithmProperties());

        // add the transient stream to this transient streaming job
        transientStreamingJob.setStream(buildTransientStream(jsonStreamingJobRequest.getStream()));

        // add the transient streaming job to REDIS
        redis.persistJob(transientStreamingJob);

        if (transientPipeline == null) {
            redis.setJobStatus(jobId, JobStatus.IN_PROGRESS_ERRORS);
            throw new WfmProcessingException(INVALID_PIPELINE_MESSAGE);
        }

        // Everything has been good so far. Update the job status using running status for a streaming job
        streamingJobRequestEntity.setStatus(JobStatus.IN_PROGRESS);
        redis.setJobStatus(jobId, JobStatus.IN_PROGRESS);
        streamingJobRequestEntity = streamingJobRequestDao.persist(streamingJobRequestEntity);

        return transientStreamingJob;
    }

    private TransientStream buildTransientStream(JsonStreamingInputObject json_input_stream) throws WfmProcessingException {
        TransientStream transientStream = new TransientStream(redis.getNextSequenceValue(),
                json_input_stream.getStreamUri());
        transientStream.setSegmentSize(json_input_stream.getSegmentSize());
        transientStream.setMediaProperties(json_input_stream.getMediaProperties());
        return transientStream;
    }

    @Override
    public void handleJobStatusChange(long jobId, JobStatus status, long timestamp) {
        Date date = new Date(timestamp);

        StreamingJobRequest streamingJobRequest = streamingJobRequestDao.findById(jobId);
        if (status.isTerminal()) {
            streamingJobRequest.setTimeCompleted(date);
        }
        streamingJobRequest.setStatus(status);
        streamingJobRequestDao.persist(streamingJobRequest);

        redis.setJobStatus(jobId, status);

        String healthReportCallbackUri = redis.getHealthReportCallbackURI(jobId);
        if (healthReportCallbackUri != null) {
            callbackUtils.doHealthReportCallback(Collections.singletonList(jobId), healthReportCallbackUri);
        }

        if (status.isTerminal()) {
            if (redis.getDoCleanup(jobId)) {
                cleanup(jobId, streamingJobRequest.getOutputObjectDirectory());
            }

            redis.clearJob(jobId);

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

            AtmosphereController.broadcast(new JobStatusMessage(jobId, 100, status, date));
            jobProgressStore.setJobProgress(jobId, 100.0f);

            log.info("[Streaming Job {}:*:*] Streaming Job complete!", jobId);
        }
    }

    @Override
    public void handleNewActivityAlert(long jobId, long frameId, long timestamp) {
        redis.setStreamingActivity(jobId, frameId, TimeUtils.millisToDateTime(timestamp));

        String healthReportCallbackUri = redis.getHealthReportCallbackURI(jobId);
        if (healthReportCallbackUri != null) {
            callbackUtils.doHealthReportCallback(Collections.singletonList(jobId), healthReportCallbackUri);
        }
    }

    @Override
    public void handleNewSummaryReport(JsonSegmentSummaryReport summaryReport) {
        // TODO: write system test

        LocalDateTime time = LocalDateTime.now();
        summaryReport.setReportDate(time);

        // TODO: check if output enabled and just get directory, not whole transient job
        TransientStreamingJob transientStreamingJob = redis.getStreamingJob(summaryReport.getJobId());
        if (transientStreamingJob != null) {
            if (transientStreamingJob.isOutputEnabled()) {
                try {
                    String outputPath = transientStreamingJob.getOutputObjectDirectory();
                    File outputFile = propertiesUtil.createStreamingOutputObjectsFile(time, new File(outputPath));
                    jsonUtils.serialize(summaryReport, outputFile);
                } catch(Exception e) {
                    log.error("Failed to write the JSON summary report for job '{}' due to an exception.", summaryReport.getJobId(), e);
                }
            }
        }

        String summaryReportCallbackUri = redis.getSummaryReportCallbackURI(summaryReport.getJobId());
        if (summaryReportCallbackUri != null) {
            callbackUtils.doSummaryReportCallback(summaryReport, summaryReportCallbackUri);
        }
    }

    /**
     * Send health reports to the health report callbacks associated with the streaming jobs.
     * Note that OpenMPF supports sending periodic health reports that contain health for all streaming jobs who have
     * defined the same HealthReportCallbackUri. This method will filter out jobIds for any jobs which are not current streaming jobs,
     * plus will optionally filter out streaming jobs that have been terminated.
     * Note that out-of-cycle health reports that may have been sent due to a change in job status will not
     * delay sending of the periodic (i.e. scheduled) health report.
     * @param jobIds unique ids for the streaming jobs to be reported on. Must not be null or empty.
     * @param isActive If true, then streaming jobs which have terminal JobStatus will be
     * filtered out. Otherwise, all current streaming jobs will be processed.
     * @throws WfmProcessingException thrown if an error occurs
     */
    public void sendHealthReports(List<Long> jobIds, boolean isActive) throws WfmProcessingException {
        if ( jobIds == null ) {
            throw new WfmProcessingException("Error: jobIds must not be null.");
        } else if ( jobIds.isEmpty() ) {
            throw new WfmProcessingException("Error: jobIds must not be empty.");
        } else {
            // While we are receiving the list of all job ids known to the system, some of these jobs may not be current streaming jobs in REDIS.
            // Reduce the List of jobIds to ony include streaming jobIds that are in REDIS. Optionally reduce that set to only include non-terminal jobs.
            List<Long> currentActiveJobIds = redis.getCurrentStreamingJobs(jobIds, isActive );

            // If there are no active jobs, no health reports will be sent.
            if ( currentActiveJobIds != null && !currentActiveJobIds.isEmpty() ) {

                // Get the list of health report callback URIs associated with the specified active jobs. Note that
                // this usage will return unique healthReportCallbackUris. Doing this so streaming jobs which specify
                // the same healthReportCallbackUri will only POST the health report once to the single healthReportCallbackUri. Note that
                // POST method is always used for sending health reports, GET method is not supported. The health report that is sent
                // will contain health for all streaming jobs with the same healthReportCallbackUri.
                // healthReportCallbackUriToJobIdListMap: key is the healthReportCallbackUri, value is the List of active jobIds that specified that healthReportCallbackUri.
                Map<String, List<Long>> healthReportCallbackUriToActiveJobIdListMap = redis.getHealthReportCallbackURIAsMap(currentActiveJobIds);

                // For each healthReportCallbackUri, send a health report containing health information for each streaming job
                // that specified the same healthReportCallbackUri. Note that sendToHealthReportCallback method won't be called
                // if healthReportCallbackUriToJobIdListMap is empty. This would be a possibility if no streaming jobs have
                // requested that a health report be sent.
                healthReportCallbackUriToActiveJobIdListMap.keySet().stream().forEach(
                    healthReportCallbackUri -> callbackUtils.doHealthReportCallback(
                            healthReportCallbackUriToActiveJobIdListMap.get(healthReportCallbackUri), healthReportCallbackUri));
            }
        }
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
