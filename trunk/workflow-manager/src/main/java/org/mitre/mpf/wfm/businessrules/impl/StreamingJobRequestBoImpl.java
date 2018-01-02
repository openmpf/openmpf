/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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


import java.io.UnsupportedEncodingException;

import java.net.URI;
import java.time.DateTimeException;
import java.util.List;
import java.util.HashMap;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentSkipListSet;

import java.math.BigInteger;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;

import org.mitre.mpf.interop.JsonAction;
import org.mitre.mpf.interop.JsonCallbackBody;
import org.mitre.mpf.interop.JsonPipeline;
import org.mitre.mpf.interop.JsonStage;
import org.mitre.mpf.interop.JsonStreamingJobRequest;
import org.mitre.mpf.interop.JsonStreamingInputObject;

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
import org.mitre.mpf.wfm.util.JmsUtils;
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
import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;
import java.util.Set;
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
    private JmsUtils jmsUtils;

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
        String jsonHealthReportCallbackUri = "";
        String jsonSummaryReportCallbackUri = "";
        String jsonNewTrackAlertCallbackUri = "";
        String jsonCallbackMethod = "GET";
        if (healthReportCallbackUri != null && TextUtils.trim(healthReportCallbackUri) != null) {
            jsonHealthReportCallbackUri = TextUtils.trim(healthReportCallbackUri);
        }
        if (summaryReportCallbackUri != null && TextUtils.trim(summaryReportCallbackUri) != null) {
            jsonSummaryReportCallbackUri = TextUtils.trim(summaryReportCallbackUri);
        }

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
                File outputObjectsDirName = propertiesUtil.createOutputObjectsDirectory(jobId);
                streamingJobRequestEntity.setOutputObjectDirectory(outputObjectsDirName.getAbsolutePath());
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
     * // TODO The streaming job cancel request must also be sent to the components via the Master Node Manager
     * // TODO Once the cancel request is more fully handled by the Master Node Manager, throw of JobCancellationInvalidOutputObjectDirectoryWfmProcessingException and JobCancellationOutputObjectDirectoryCleanupWarningWfmProcessingException will be moved to some other method
     * @param jobId     The OpenMPF-assigned identifier for the streaming job. The job must be a streaming job.
     * @param doCleanup if true, delete the streaming job files from disk as part of cancelling the streaming job.
     * @exception JobAlreadyCancellingWfmProcessingException may be thrown if the streaming job has already been cancelled or
     * if the streaming jobs status is already terminal. JobCancellationInvalidJobIdWfmProcessingException may be thrown if the
     * streaming job can't be cancelled due to an error with identification of the streaming job using the specified jobId.
     * JobCancellationOutputObjectDirectoryCleanupWarningWfmProcessingException may be thrown
     * if the streaming job has been cancelled, but the jobs output object directory couldn't be deleted when doCleanup is enabled.
     * JobCancellationInvalidOutputObjectDirectoryWfmProcessingException may be thrown if the streaming job has been cancelled, but an error
     * was detected in specification of the jobs output object directory.
     * The exception message will provide a summary of the warning or error that occurred.
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
                throw new JobAlreadyCancellingWfmProcessingException("Streaming job " + jobId +" already has state '" + streamingJobRequest.getStatus().name() + "' and cannot be cancelled at this time.");
            } else {
                log.info("[Job {}:*:*] Cancelling streaming job.", jobId);

                // Mark the streaming job as cancelled in Redis
                if (redis.cancel(jobId)) {

                    // Try to move any pending work items on the queues to the appropriate cancellation queues.
                    // If this operation fails, any remaining pending items will continue to process, but
                    // the future splitters should not create any new work items. In short, if this fails,
                    // the system should not be affected, but the streaming job may not complete any faster.
                    // TODO tell the master node manager to cancel the streaming job
                    log.warn(
                        "[Streaming Job {}:*:*] Cancellation of streaming job via master node manager not yet implemented.",
                        jobId);

                    // set job status as cancelling, and persist that changed state in the database
                    streamingJobRequest.setStatus(JobStatus.CANCELLING);
                    streamingJobRequestDao.persist(streamingJobRequest);

                    // TODO this doCleanup section should be moved to after the Node Manager has notified the WFM that the streaming job has been cancelled (issue #334)
                    // If doCleanup is true, then the caller has requested that the output object directory be deleted as part
                    // of the job cancellation. Note that the node manager may not yet have cancelled this streaming job, so these files may still be in use
                    // when they are deleted in the code below. If this occurs, then the output object directory may not be deleted as requested and a
                    // WfmProcessingException will be thrown so that the caller will be aware of this condition.
                    if (doCleanup) {
                        // delete the output object directory as part of the job cancellation.
                        String outputObjectDirectory = streamingJobRequest.getOutputObjectDirectory();
                        // the output object directory is determined when the streaming job is created. It should not be null or empty.
                        // The check for the output object directory being null or empty was added just to handle an unknown or unexpected case.
                        if (outputObjectDirectory == null) {
                            // it is an error if the streaming job has been marked for cancellation, doCleanup is enabled, but the output object directory is unknown. Log the error and throw an exception.
                            String errorMessage = "Streaming job " + jobId
                                + " is cancelling, but the path to the output object directory is null. Can't cleanup the output object directory for this job.";
                            throw new JobCancellationInvalidOutputObjectDirectoryWfmProcessingException(errorMessage);
                        } else if (outputObjectDirectory.isEmpty()) {
                            // it is an error if the streaming job has been marked for cancellation, doCleanup is enabled, but the path to the output object directory is empty. Log the error and throw an exception.
                            String errorMessage = "Streaming job " + jobId
                                + " is cancelling, but the path to the output object directory is empty. Can't cleanup the output object directory for this job.";
                            throw new JobCancellationInvalidOutputObjectDirectoryWfmProcessingException(errorMessage);
                        } else {
                            // delete the streaming job's output object directory.
                            File outputObjectDirectoryFile = new File(outputObjectDirectory);
                            if (!outputObjectDirectoryFile.exists()) {
                                // it is not an error if the streaming job has been marked for cancellation, doCleanup is enabled, but the output object directory doesn't exist. Just log that fact and continue.
                                log.info(
                                    "[Streaming Job {}:*:*] streaming job is cancelling. Output object directory '{}' not deleted because it doesn't exist.",
                                    jobId, outputObjectDirectory);
                            } else if ( outputObjectDirectoryFile.isDirectory() ) {
                                // as requested, delete the output object directory for this streaming job.
                                log.info("[Streaming Job {}:*:*] streaming job is cancelling. Output object directory '{}' is being deleted as requested within the cancel request.",
                                    jobId, outputObjectDirectory);
                                try {

                                    // First, make sure that the output object directory specified doesn't end with a symbolic link.
                                    // FileUtils.deleteDirectory doesn't follow symbolic links at the end of a path, so if the output object directory for this
                                    // streaming job is a symbolic link it won't be successfully deleted. Note that symbolic links in the middle of the path are ok.
                                    if ( !FileUtils.isSymlink(outputObjectDirectoryFile) ) {

                                        // use FileUtils to recursively delete the output object file directory
                                        FileUtils.deleteDirectory(outputObjectDirectoryFile);

                                        // Found instances where the output object directory for a streaming job marked for cancellation wasn't successfully deleted, but no exception was thrown.
                                        // Adding an extra check for that here.
                                        if ( (new File(outputObjectDirectory)).getAbsoluteFile().exists() ) {
                                            // the output object directory was not successfully deleted, throw a warning exception
                                            String warningMessage = "Streaming job " + jobId
                                                + " is cancelling, but failed to fully cleanup the output object directory. '"
                                                + outputObjectDirectory + "' still exists.";
                                            throw new JobCancellationOutputObjectDirectoryCleanupWarningWfmProcessingException(warningMessage);
                                        } else {
                                            // the output object directory was successfully deleted, log this fact
                                            log.info(
                                                "[Streaming Job {}:*:*] Streaming job is cancelling. Output object directory '{}' was successfully deleted as requested within the cancel request.",
                                                jobId, outputObjectDirectory);
                                        }
                                    } else {
                                        // Marking this as a WFM error. If the output object directory was properly created by OpenMPF, it would not be a symbolic link.
                                        String errorMessage = "Streaming job " + jobId + " is cancelling, but the output object directory '"
                                            + outputObjectDirectory + "' is a symbolic link, not a directory. The output object directory was not deleted.";
                                        throw new JobCancellationInvalidOutputObjectDirectoryWfmProcessingException(errorMessage);
                                    }

                                } catch (IOException | IllegalArgumentException e) {
                                    // Marking this as a warning. The warning include the path to the output object directory, so the client can delete it if necessary.
                                    String warningMessage = "Streaming job " + jobId + " is cancelling, but failed to fully cleanup the output object directory '"
                                        + outputObjectDirectory + "' due to exception: " + e.getMessage();
                                    throw new JobCancellationOutputObjectDirectoryCleanupWarningWfmProcessingException(warningMessage, e);
                                }
                            } else {
                                // it is an error if the streaming job has been cancelled, doCleanup is enabled, but the output object directory specified is not really a directory.
                                String errorMessage = "Streaming Job " + jobId
                                    + " requested to be cancelled with doCleanup enabled, but output object directory '"
                                    + outputObjectDirectory
                                    + "' isn't a viable directory. Can't delete output object directory.";
                                throw new JobCancellationInvalidOutputObjectDirectoryWfmProcessingException(errorMessage);
                            }
                        }
                    }

                } else {
                    // Couldn't find the requested job as a batch or as a streaming job in REDIS.
                    // Generate an error for the race condition where Redis and the persistent database reflect different states.
                    throw new JobCancellationInvalidJobIdWfmProcessingException("Streaming job with jobId " + jobId + " is not found, it cannot be cancelled at this time.");
                }

            }
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

    // TODO finish implementation of completed method, need to add a call to this method when a streaming job terminates

    /**
     * Complete a streaming job by updating the job in the persistent database(s), make any final callbacks for the job.
     *
     * @param jobId unique id for a streaming job
     * @throws WfmProcessingException
     */
    @Override
    public synchronized void jobCompleted(long jobId, JobStatus jobStatus) throws WfmProcessingException {
        // TODO: cleanup the summary reports and other output files
        markJobCompleted(jobId, jobStatus);

        try {
            // call streaming job summary report callback one final time
            summaryReportCallback(jobId);
        } catch (Exception exception) {
            log.warn("Failed to make callback (if appropriate) for Streaming Job #{}.", jobId);
        }

        // Tear down the streaming job.
        try {
            destroy(jobId);
        } catch (Exception exception) {
            log.warn("Failed to clean up Streaming Job {} due to an exception. Data for this streaming job will remain in the transient data store, but the status of the streaming job has not been affected by this failure.", jobId, exception);
        }

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

        AtmosphereController.broadcast(new JobStatusMessage(jobId, 100, jobStatus, new Date()));
        jobProgressStore.setJobProgress(jobId, 100.0f);
        log.info("[Streaming Job {}:*:*] Streaming Job complete!", jobId);

    }

    @Override
    public void handleJobStatusChange(long jobId, JobStatus status, long timestamp) {
    	// TODO: Replace logging with implementation of handleJobStatusChange
    	log.info("handleJobStatusChange(jobId = {}, status = {}, time = {})", jobId, status, millisToDateTime(timestamp));
    }

    @Override
    public void handleNewActivityAlert(long jobId, long frameId, long timestamp) {
        // TODO: Replace logging with implementation of handleNewActivityAlert
        log.info("handleNewActivityAlert(jobId = {}, frameId = {}, time = {})", jobId, frameId, millisToDateTime(timestamp));
    }

    @Override
    public void handleNewSummaryReport(long jobId, Object summaryReport) {
        // TODO: Replace logging with implementation of handleNewSummaryReport
        log.info("handleNewSummaryReport(jobId = {}, summaryReport = {})", jobId, summaryReport);
    }

    private static LocalDateTime millisToDateTime(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

    /**
     * Call the summary report callback.
     *
     * @param jobId unique id for this streaming job
     * @throws WfmProcessingException
     */
    private void summaryReportCallback(long jobId) throws WfmProcessingException {
        final String jsonSummaryReportCallbackUri = redis.getSummaryReportCallbackURI(jobId);
        final String jsonCallbackMethod = redis.getCallbackMethod(jobId);
        if (jsonSummaryReportCallbackUri != null) {
            // The caller requested summary reports.
            if (jsonCallbackMethod != null && (jsonCallbackMethod.equals("POST") || jsonCallbackMethod.equals("GET"))) {
                log.info("Starting " + jsonCallbackMethod + " summary report callback to " + jsonSummaryReportCallbackUri);
                try {
                    JsonCallbackBody jsonBody = new JsonCallbackBody(jobId, redis.getExternalId(jobId));
                    new Thread(new CallbackThread(jsonSummaryReportCallbackUri, jsonCallbackMethod, jsonBody)).start();
                } catch (IOException ioe) {
                    log.warn("Failed to issue {} callback to '{}' due to an I/O exception.", jsonCallbackMethod, jsonSummaryReportCallbackUri, ioe);
                }
            } else {
                throw new WfmProcessingException("Error: summary report callbacks are requested, but callback method is null or invalid");
            }
        }
    }

    /**
     * Immediately send a health report including the specified streaming jobs to the common health report callback.
     * The health report will be sent in a newly started thread.
     * @param jobIds Unique ids for the streaming jobs to be reported on. Should not be null or empty.
     * @param healthReportCallbackUri the same health report callback URI that was specified as common to these streaming jobs. Must not be null.
     * @throws WfmProcessingException may be thrown if healthReportCallbackUri is null
     */
    private void sendHealthReports(List<Long> jobIds, String healthReportCallbackUri) throws WfmProcessingException {

        if ( healthReportCallbackUri == null ) {
            throw new WfmProcessingException("Error, healthReportCallbackUri can't be null.");
        } else {
            // At least one health report needs to be sent to the specified healthReportCallbackUri
            log.info("Starting health report callback send to single health report callback URI " + healthReportCallbackUri + " for Streaming jobs " + jobIds);
            // send the health report to the health report callback URI in a newly started thread
            new Thread(new HealthReportCallbackThread(redis,jsonUtils,jobIds,healthReportCallbackUri)).start();
        }
    }

    /**
     * Send a health report to the health report callbacks associated with the streaming jobs.
     * Note that OpenMPF supports sending periodic health reports that contain health for all streaming jobs who have
     * defined the same HealthReportCallbackUri. This method will filter out jobIds for any jobs which are not current streaming jobs,
     * plus will optionally filter out inactive (i.e. TERMINATED) streaming jobs.
     * Note that out-of-cycle health reports that may have been sent due to a change in job status will not
     * delay sending of the periodic (i.e. scheduled) health report.
     * @param jobIds unique ids for the streaming jobs to be reported on. Must not be null or empty.
     * @param isActive If true, then streaming jobs which have JobStatus of TERMINATED will be
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
            // Reduce the List of jobIds to ony include streaming jobIds that are in REDIS. Optionally reduce that set to only include non-TERMINATED jobs.
            List<Long> currentActiveJobIds = redis.getCurrentStreamingJobs(jobIds, isActive );
            log.info("StreamingJobRequestBoImpl.sendHealthReports: got list of all streaming currentActiveJobIds=" + currentActiveJobIds);

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
                    healthReportCallbackUri -> sendHealthReports(healthReportCallbackUriToActiveJobIdListMap.get(healthReportCallbackUri), healthReportCallbackUri));
            }
        }
    }

    // TODO finalize what needs to be done to mark a streaming job as completed in the WFM, method copied over from StreamingJobCompleteProcessorImpl.java
    private void markJobCompleted(long jobId, JobStatus jobStatus) {
        log.debug("Marking Streaming Job {} as completed with status '{}'.", jobId, jobStatus);

        StreamingJobRequest streamingJobRequest = streamingJobRequestDao.findById(jobId);
        assert streamingJobRequest != null : String.format("A streaming job request entity must exist with the ID %d", jobId);

        streamingJobRequest.setTimeCompleted(new Date());
        streamingJobRequest.setStatus(jobStatus);
        streamingJobRequestDao.persist(streamingJobRequest);
    }

    // TODO finalize what needs to be done to destroy a streaming job in the WFM, method copied over from StreamingJobCompleteProcessorImpl.java
    private void destroy(long jobId) throws WfmProcessingException {
        TransientStreamingJob transientStreamingJob = redis.getStreamingJob(jobId);

        log.debug("StreamingJobRequest.destroy: destruction of stream data NYI for Streaming Job {}.", jobId);

// keep this commented section here for reference until streaming job processing is finalized
//		for(TransientMedia transientMedia : transientStreamingJob.getMedia()) {
//			if(transientMedia.getUriScheme().isRemote() && transientMedia.getLocalPath() != null) {
//				try {
//					Files.deleteIfExists(Paths.get(transientMedia.getLocalPath()));
//				} catch(Exception exception) {
//					log.warn("[{}|*|*] Failed to delete locally cached file '{}' due to an exception. This file must be manually deleted.", transientJob.getId(), transientMedia.getLocalPath());
//				}
//			}
//		}
        // end of keep this commented section here for reference until streaming job processing is finalized

        redis.clearJob(jobId);
    }

    @Override
    public void subscribe(NotificationConsumer<JobCompleteNotification> consumer) {
        consumers.add(consumer);
    }

    @Override
    public void unsubscribe(NotificationConsumer<JobCompleteNotification> consumer) {
        consumers.remove(consumer);
    }

    /**
     * Thread to handle a general Callback to a URI given a HTTP method.
     */
    public class CallbackThread implements Runnable {
        private String callbackUri;
        private String callbackMethod;
        private HttpUriRequest req;

       public CallbackThread(String callbackUri, String callbackMethod, JsonCallbackBody body) throws UnsupportedEncodingException {
            this.callbackUri = callbackUri;
            this.callbackMethod = callbackMethod;

            if (callbackMethod.equals("GET")) {
                String jsonCallbackUri2 = callbackUri;
                if (jsonCallbackUri2.contains("?")) {
                    jsonCallbackUri2 += "&";
                } else {
                    jsonCallbackUri2 += "?";
                }
                jsonCallbackUri2 += "jobid=" + body.getJobId();
                if (body.getExternalId() != null) {
                    jsonCallbackUri2 += "&externalid=" + body.getExternalId();
                }
                req = new HttpGet(jsonCallbackUri2);
            } else { // this is for a POST
                HttpPost post = new HttpPost(callbackUri);
                post.addHeader("Content-Type", "application/json");
                try {
                    post.setEntity(new StringEntity(jsonUtils.serializeAsTextString(body)));
                    req = post;
                } catch (WfmProcessingException e) {
                    log.error("Cannot serialize CallbackBody.",e);
                }
            }
        }

        @Override
        public void run() {
            final HttpClient httpClient = HttpClientBuilder.create().build();
            try {
                HttpResponse response = httpClient.execute(req);
                log.info("{} Callback issued to '{}' (Response={}).", callbackMethod, callbackUri, response);
            } catch (Exception exception) {
                log.warn("Failed to issue {} callback to '{}' due to an I/O exception.", callbackMethod, callbackUri, exception);
            }
        }
    }

}
