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
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentSkipListSet;

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
import org.mitre.mpf.wfm.data.entities.transients.TransientAction;
import org.mitre.mpf.wfm.data.entities.transients.TransientPipeline;
import org.mitre.mpf.wfm.data.entities.transients.TransientStage;
import org.mitre.mpf.wfm.data.entities.transients.TransientStream;
import org.mitre.mpf.wfm.data.entities.transients.TransientStreamingJob;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.enums.JobStatus;
import org.mitre.mpf.wfm.event.JobCompleteNotification;
import org.mitre.mpf.wfm.event.JobProgress;
import org.mitre.mpf.wfm.event.NotificationConsumer;
import org.mitre.mpf.wfm.service.PipelineService;
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
import java.util.*;

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
     * Ceate and initialize a JSON representation of a streaming job request given the raw parameters.
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
     * @param healthReportCallbackUri      callback for health reports, pass null to disable health reports
     * @param summaryReportCallbackUri     callback for summary reports, pass null to disable summary reports
     * @param newTrackAlertCallbackUri     callback for new track alerts, pass null to disable new track alerts
     * @param callbackMethod               callback method should be GET or POST.  If null, default will be GET
     * @return JSON representation of the streaming job request
     */
    @Override
    public JsonStreamingJobRequest createRequest(String externalId, String pipelineName, JsonStreamingInputObject stream,
                                                 Map<String, Map<String, String>> algorithmProperties,
                                                 Map<String, String> jobProperties, boolean buildOutput, int priority,
                                                 long stallTimeout,
                                                 String healthReportCallbackUri, String summaryReportCallbackUri, String newTrackAlertCallbackUri, String callbackMethod) {
        log.debug("[streaming createRequest] externalId:" + externalId + ", pipeline:" + pipelineName + ", buildOutput:" + buildOutput + ", priority:" + priority +
                ", healthReportCallbackUri:" + healthReportCallbackUri + ", summaryReportCallbackUri:" + summaryReportCallbackUri +
                ", newTrackAlertCallbackUri:" + newTrackAlertCallbackUri + ", callbackMethod:" + callbackMethod);
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
        if (newTrackAlertCallbackUri != null && TextUtils.trim(newTrackAlertCallbackUri) != null) {
            jsonNewTrackAlertCallbackUri = TextUtils.trim(newTrackAlertCallbackUri);
        }
        if (callbackMethod != null && TextUtils.trim(callbackMethod) != null && !TextUtils.trim(callbackMethod).equals("GET")) { //only GET or POST allowed
            jsonCallbackMethod = "POST";
        }

        String outputObjectPath = ""; // initialize output output object to empty string, the path will be set after the streaming job is submitted
        JsonStreamingJobRequest jsonStreamingJobRequest = new JsonStreamingJobRequest(TextUtils.trim(externalId), buildOutput, outputObjectPath,
                pipelineService.createJsonPipeline(pipelineName), priority, stream,
                stallTimeout,
                jsonHealthReportCallbackUri, jsonSummaryReportCallbackUri, jsonNewTrackAlertCallbackUri, jsonCallbackMethod,
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
     * Cancel a streaming job.
     * Mark the job as cancelled in both REDIS and in the long-term database.
     * // TODO The streaming job cancel request must also be sent to the components via the Master Node Manager
     *
     * @param jobId     The OpenMPF-assigned identifier for the streaming job. The job must be a streaming job.
     * @param doCleanup if true, delete the streaming job files from disk after canceling the streaming job
     * @return true if the streaming job was successfully cancelled, false otherwise
     * @throws WfmProcessingException
     */
    @Override
    public synchronized boolean cancel(long jobId, boolean doCleanup) throws WfmProcessingException {
        log.debug("[Job {}:*:*] Received request to cancel this streaming job.", jobId);
        StreamingJobRequest streamingJobRequest = streamingJobRequestDao.findById(jobId);
        if (streamingJobRequest == null) {
            throw new WfmProcessingException(String.format("A streaming job with the id %d is not known to the system.", jobId));
        }

        assert streamingJobRequest.getStatus() != null : "Streaming jobs must not have a null status.";

        if (streamingJobRequest.getStatus().isTerminal() || streamingJobRequest.getStatus() == JobStatus.CANCELLING) {
            log.warn("[Job {}:*:*] This streaming job is in the state of '{}' and cannot be cancelled at this time.", jobId, streamingJobRequest.getStatus().name());
            return false;
        } else {
            log.info("[Job {}:*:*] Cancelling streaming job.", jobId);

            // If doCleanup is true, then the caller has requested that the output object directory be deleted as part
            // of the job cancellation.
            if (doCleanup) {
                // delete the output object directory as part of the job cancellation
                String outputObjectDirectory = streamingJobRequest.getOutputObjectDirectory();
                if (outputObjectDirectory == null) {
                    log.warn("[Job {}:*:*] doCleanup is enabled but the streaming job output object directory is null. Can't cleanup the output object directory for this job.", jobId);
                } else {
                    // before we start deleting any file system, double check that this is the root directory of the
                    // streaming job's output object directory.
                    File outputObjectDirectoryFile = new File(outputObjectDirectory);
                    if (outputObjectDirectoryFile.exists() && outputObjectDirectoryFile.isDirectory() && outputObjectDirectoryFile.isAbsolute()) {
                        Path outputObjectDirectoryFileRootPath = Paths.get(outputObjectDirectoryFile.toURI());
                        // TODO some extra validation should be added here for security to ensure that the output object directory identified is of valid syntax (i.e. error out if equal to "/", etc)
                        // TODO need to define naming convention for VidoeWriter output files.
                        // TODO extra validation should be added here for security to ensure that only VideoWriter output files are deleted.
                        // TODO the software that actually deletes the output object file system is commented out until the security filters can be enforced
                        // TODO what extra handling needs to be added if the cleanup fails?  How to notify the user?
                        log.warn("[Job {}:*:*] doCleanup is enabled but cleanup of the output object directory associated with this streaming job isn't yet implemented, pending the addition of the directory/file validation filters.", jobId);
                        try {
                            // Walk through the files in the streaming job directory, deleting all files under the outputObjectDirectoryFileRootPath.
                            // We intentionally don't enable follow links option in the walk, there shouldn't be any symbolic links in the output object file system.
                            log.warn("[Job {}:*:*] doCleanup is enabled. Deleteing all streaming job files under {}",jobId,outputObjectDirectoryFileRootPath);
                            Files.walk(outputObjectDirectoryFileRootPath)
                                .sorted(Comparator.reverseOrder())
                                .map(Path::toFile)
                                .peek(System.out::println)
                                .forEach(File::delete);
                        } catch (IOException ioe) {
                            String errorMessage =
                                "Failed to cleanup the output object file directory for streaming job "
                                    + jobId
                                    + " due to IO exception.";
                            log.error(errorMessage);
                            throw new WfmProcessingException(errorMessage, ioe);
                        }

                    } else {
                        log.warn("[Job {}:*:*] doCleanup is enabled but the output object directory associated with this streaming job isn't a viable directory. Can't cleanup the output object directory for this job.", jobId);
                    }
                }
            }

            // Mark the streaming job as cancelled in Redis so that future steps in the workflow will know not to continue processing.
            if (redis.cancel(jobId)) {
                try {
                    // Try to move any pending work items on the queues to the appropriate cancellation queues.
                    // If this operation fails, any remaining pending items will continue to process, but
                    // the future splitters should not create any new work items. In short, if this fails,
                    // the system should not be affected, but the streaming job may not complete any faster.
                    // TODO tell the master node manager to cancel the streaming job
                    log.warn("[Job {}:*:*] Cancellation of streaming job via master node manager not yet implemented.", jobId);
                } catch (Exception exception) {
                    log.warn("[Job {}:*:*] Failed to remove the pending work elements in the message broker for this streaming job. The job must complete the pending work elements before it will cancel the job.", jobId, exception);
                }
                streamingJobRequest.setStatus(JobStatus.CANCELLING);
                streamingJobRequestDao.persist(streamingJobRequest);
            } else {
                // Warn of the race condition where Redis and the persistent database reflect different states.
                log.warn("[Job {}:*:*] The streaming job is not in progress and cannot be cancelled at this time.", jobId);
            }
            return true;
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
        streamingJobRequest.setNewTrackAlertCallbackUri(jsonStreamingJobRequest.getNewTrackAlertCallbackUri());
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
        log.info(this.getClass().getName() + ".runInternal: TODO notification of new streaming job " + streamingJobRequestEntity.getId() + " to Components via Master Node Manager");

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
                jsonStreamingJobRequest.getSummaryReportCallbackUri(),
                jsonStreamingJobRequest.getNewTrackAlertCallbackUri(),
                jsonStreamingJobRequest.getCallbackMethod());

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
    public synchronized void jobCompleted(long jobId, JobStatus jobStatus) throws WfmProcessingException {
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
                    log.warn("Completion streaming job consumer '{}' threw an exception.", consumer, exception);
                }
            }
        }

        AtmosphereController.broadcast(new JobStatusMessage(jobId, 100, jobStatus, new Date()));
        jobProgressStore.setJobProgress(jobId, 100.0f);
        log.info("[Streaming Job {}:*:*] Streaming Job complete!", jobId);

    }

    /**
     * Call the summary report callback.
     *
     * @param jobId unique id for this streaming job
     * @throws WfmProcessingException
     */
    private void summaryReportCallback(long jobId) throws WfmProcessingException {
        final String jsonSummaryReportCallbackURL = redis.getSummaryReportCallbackURI(jobId);
        final String jsonCallbackMethod = redis.getCallbackMethod(jobId);
        if (jsonSummaryReportCallbackURL != null && jsonCallbackMethod != null && (jsonCallbackMethod.equals("POST") || jsonCallbackMethod.equals("GET"))) {
            log.info("Starting " + jsonCallbackMethod + " summary report callback to " + jsonSummaryReportCallbackURL);
            try {
                JsonCallbackBody jsonBody = new JsonCallbackBody(jobId, redis.getExternalId(jobId));
                new Thread(new CallbackThread(jsonSummaryReportCallbackURL, jsonCallbackMethod, jsonBody)).start();
            } catch (IOException ioe) {
                log.warn("Failed to issue {} callback to '{}' due to an I/O exception.", jsonCallbackMethod, jsonSummaryReportCallbackURL, ioe);
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

        log.debug("StreamingJobRequest.destroy: destruction of stream data NYI for Streaming Job {} ", jobId);

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
     * Thread to handle the Callback to a URL given a HTTP method.
     */
    public class CallbackThread implements Runnable {
        private String callbackURL;
        private String callbackMethod;
        private HttpUriRequest req;

        public CallbackThread(String callbackURL, String callbackMethod, JsonCallbackBody body) throws UnsupportedEncodingException {
            this.callbackURL = callbackURL;
            this.callbackMethod = callbackMethod;

            if (callbackMethod.equals("GET")) {
                String jsonCallbackURL2 = callbackURL;
                if (jsonCallbackURL2.contains("?")) {
                    jsonCallbackURL2 += "&";
                } else {
                    jsonCallbackURL2 += "?";
                }
                jsonCallbackURL2 += "jobid=" + body.getJobId();
                if (body.getExternalId() != null) {
                    jsonCallbackURL2 += "&externalid=" + body.getExternalId();
                }
                req = new HttpGet(jsonCallbackURL2);
            } else { // this is for a POST
                HttpPost post = new HttpPost(callbackURL);
                post.addHeader("Content-Type", "application/json");
                try {
                    post.setEntity(new StringEntity(jsonUtils.serializeAsTextString(body)));
                    req = post;
                } catch (WfmProcessingException e) {
                    log.error("Cannont serialize CallbackBody");
                }
            }
        }

        @Override
        public void run() {
            final HttpClient httpClient = HttpClientBuilder.create().build();
            try {
                HttpResponse response = httpClient.execute(req);
                log.info("{} Callback issued to '{}' (Response={}).", callbackMethod, callbackURL, response);
            } catch (Exception exception) {
                log.warn("Failed to issue {} callback to '{}' due to an I/O exception.", callbackMethod, callbackURL, exception);
            }
        }
    }
}
