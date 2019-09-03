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

package org.mitre.mpf.wfm.camel;

import org.apache.camel.Exchange;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.mitre.mpf.interop.*;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.ActionType;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.access.JobRequestDao;
import org.mitre.mpf.wfm.data.access.MarkupResultDao;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateMarkupResultDaoImpl;
import org.mitre.mpf.wfm.data.entities.persistent.*;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.event.JobCompleteNotification;
import org.mitre.mpf.wfm.event.JobProgress;
import org.mitre.mpf.wfm.event.NotificationConsumer;
import org.mitre.mpf.wfm.service.JobStatusBroadcaster;
import org.mitre.mpf.wfm.service.StorageService;
import org.mitre.mpf.wfm.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

@Component(JobCompleteProcessorImpl.REF)
public class JobCompleteProcessorImpl extends WfmProcessor implements JobCompleteProcessor {
    private static final Logger log = LoggerFactory.getLogger(JobCompleteProcessor.class);
    public static final String REF = "jobCompleteProcessorImpl";

    private final Set<NotificationConsumer<JobCompleteNotification>> consumers = new ConcurrentSkipListSet<>();

    @Autowired
    private JobRequestDao jobRequestDao;

    @Autowired
    @Qualifier(HibernateMarkupResultDaoImpl.REF)
    private MarkupResultDao markupResultDao;

    @Autowired
    private PropertiesUtil propertiesUtil;

    @Autowired
    private JsonUtils jsonUtils;

    @Autowired
    private InProgressBatchJobsService inProgressBatchJobs;

    @Autowired
    private JobProgress jobProgressStore;

    @Autowired
    private StorageService storageService;

    @Autowired
    private JobStatusBroadcaster jobStatusBroadcaster;

    @Autowired
    private AggregateJobPropertiesUtil aggregateJobPropertiesUtil;


    @Override
    public void wfmProcess(Exchange exchange) throws WfmProcessingException {
        Long jobId = exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class);
        assert jobId != null : String.format("The header '%s' (value=%s) was not set or is not a Long.", MpfHeaders.JOB_ID, exchange.getIn().getHeader(MpfHeaders.JOB_ID));

        if(jobId == Long.MIN_VALUE) {
            // If we receive a very large negative Job ID, it means an exception was encountered during processing of a job,
            // and none of the provided error handling logic could fix it. Further processing should not be performed.
            log.warn("[Job {}:*:*] An error prevents a job from completing successfully. Please review the logs for additional information.", jobId);
        } else {
            String statusString = exchange.getIn().getHeader(MpfHeaders.JOB_STATUS, String.class);
            Mutable<BatchJobStatusType> jobStatus = new MutableObject<>(BatchJobStatusType.parse(statusString, BatchJobStatusType.UNKNOWN));

            BatchJob job = inProgressBatchJobs.getJob(jobId);
            try {
                // NOTE: jobStatus is mutable - it __may__ be modified in createOutputObject!
                createOutputObject(job, jobStatus);
            } catch (Exception exception) {
                log.warn("Failed to create the output object for Job {} due to an exception.", jobId, exception);
                jobStatus.setValue(BatchJobStatusType.ERROR);
            }

            IoUtils.deleteEmptyDirectoriesRecursively(propertiesUtil.getJobMarkupDirectory(jobId).toPath());
            IoUtils.deleteEmptyDirectoriesRecursively(propertiesUtil.getJobArtifactsDirectory(jobId).toPath());
            IoUtils.deleteEmptyDirectoriesRecursively(propertiesUtil.getJobOutputObjectsDirectory(jobId).toPath());

            try {
                jmsUtils.destroyCancellationRoutes(jobId);
            }
            catch (Exception exception) {
                log.warn("Failed to destroy the cancellation routes associated with {}. If this job is resubmitted, it will likely not complete again!",
                         jobId, exception);
            }

            setJobCompletionStatus(jobId, jobStatus.getValue(), job);

            try {
                callback(job);
            } catch (Exception exception) {
                log.warn("Failed to make callback (if appropriate) for Job #{}.", jobId);
            }

            // Tear down the job.
            try {
                destroy(job);
            } catch (Exception exception) {
                log.warn("Failed to clean up Job {} due to an exception. Data for this job will remain in the transient data store, but the status of the job has not been affected by this failure.", jobId, exception);
            }

            log.info("Notifying {} completion consumer(s).", consumers.size());
            for (NotificationConsumer<JobCompleteNotification> consumer : consumers) {
                try {
                    log.info("Notifying completion consumer {}.", consumer.getId());
                    consumer.onNotification(this, new JobCompleteNotification(exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class)));
                } catch (Exception exception) {
                    log.warn("Completion consumer {} threw an exception.", consumer.getId(), exception);
                }
            }

            jobStatusBroadcaster.broadcast(jobId, 100, jobStatus.getValue(), Instant.now());
            jobProgressStore.removeJob(jobId);
            log.info("[Job {}:*:*] Job complete!", jobId);
        }
    }

    private void callback(BatchJob batchJob) throws WfmProcessingException {
        long jobId = batchJob.getId();
        String jsonCallbackURL = batchJob.getCallbackUrl().orElse(null);
        if (jsonCallbackURL == null) {
            return;
        }
        String jsonCallbackMethod = batchJob.getCallbackMethod()
                .filter(cbm -> cbm.equalsIgnoreCase("POST") || cbm.equalsIgnoreCase("GET"))
                .orElse(null);
        if (jsonCallbackMethod == null) {
            return;
        }


        log.debug("Starting {} callback to {} for job id {}.", jsonCallbackMethod, jsonCallbackURL, jobId);
        try {
            JsonCallbackBody jsonBody = new JsonCallbackBody(jobId, batchJob.getExternalId().orElse(null));
            new Thread(new CallbackThread(jsonCallbackURL, jsonCallbackMethod, jsonBody)).start();
        } catch (IOException ioe) {
            log.warn(String.format("Failed to issue %s callback to '%s' for job id %s.",
                                   jsonCallbackMethod, jsonCallbackURL, jobId), ioe);
        }
    }


    private void setJobCompletionStatus(long jobId, BatchJobStatusType jobStatus, BatchJob job) {
        inProgressBatchJobs.setJobStatus(jobId, jobStatus);

        JobRequest jobRequest = jobRequestDao.findById(jobId);
        assert jobRequest != null : String.format("A job request entity must exist with the ID %d", jobId);

        jobRequest.setTimeCompleted(Instant.now());
        jobRequest.setStatus(jobStatus);
        jobRequest.setJob(jsonUtils.serialize(job));
        jobRequestDao.persist(jobRequest);
    }

    @Override
    public void createOutputObject(BatchJob job, Mutable<BatchJobStatusType> jobStatus) throws IOException {
        long jobId = job.getId();
        JobRequest jobRequest = jobRequestDao.findById(jobId);

        if(job.isCancelled()) {
            jobStatus.setValue(BatchJobStatusType.CANCELLED);
        }

        JsonOutputObject jsonOutputObject = new JsonOutputObject(
                jobRequest.getId(),
                UUID.randomUUID().toString(),
                jsonUtils.convert(job.getPipelineElements()),
                job.getPriority(),
                propertiesUtil.getSiteId(),
                job.getExternalId().orElse(null),
                jobRequest.getTimeReceived(),
                jobRequest.getTimeCompleted(),
                jobStatus.getValue().toString());

        if (job.getJobProperties() != null) {
            jsonOutputObject.getJobProperties().putAll(job.getJobProperties());
        }

        if (job.getOverriddenAlgorithmProperties() != null) {
            jsonOutputObject.getAlgorithmProperties().putAll(job.getOverriddenAlgorithmProperties());
        }
        jsonOutputObject.getJobWarnings().addAll(job.getWarnings());
        jsonOutputObject.getJobErrors().addAll(job.getErrors());

        boolean hasDetectionProcessingError = false;

        int mediaIndex = 0;
        for (Media media : job.getMedia()) {
            StringBuilder stateKeyBuilder = new StringBuilder("+");

            JsonMediaOutputObject mediaOutputObject = new JsonMediaOutputObject(media.getId(), media.getUri(), media.getType(),
                                                                                media.getLength(), media.getSha256(), media.getMessage(),
                                                                                media.isFailed() ? "ERROR" : "COMPLETE");

            mediaOutputObject.getMediaMetadata().putAll(media.getMetadata());
            mediaOutputObject.getMediaProperties().putAll(media.getMediaSpecificProperties());

            MarkupResult markupResult = markupResultDao.findByJobIdAndMediaIndex(jobId, mediaIndex);
            if(markupResult != null) {
                mediaOutputObject.setMarkupResult(new JsonMarkupOutputObject(markupResult.getId(), markupResult.getMarkupUri(), markupResult.getMarkupStatus().name(), markupResult.getMessage()));
            }


            Set<Integer> suppressedTasks = getSuppressedTasks(media, job);

            for (int taskIndex = 0; taskIndex < job.getPipelineElements().getTaskCount(); taskIndex++) {
                Task task = job.getPipelineElements().getTask(taskIndex);
                for (int actionIndex = 0; actionIndex < task.getActions().size(); actionIndex++) {
                    Action action = job.getPipelineElements().getAction(taskIndex, actionIndex);
                    String stateKey = String.format("%s#%s", stateKeyBuilder.toString(), action.getName());

                    for (DetectionProcessingError detectionProcessingError : getDetectionProcessingErrors(
                                job, media.getId(), taskIndex, actionIndex)) {
                        hasDetectionProcessingError = !MpfConstants.REQUEST_CANCELLED.equals(detectionProcessingError.getError());
                        JsonDetectionProcessingError jsonDetectionProcessingError
                                = new JsonDetectionProcessingError(
                                        detectionProcessingError.getStartOffset(),
                                        detectionProcessingError.getEndOffset(),
                                        detectionProcessingError.getError());
                        if (!mediaOutputObject.getDetectionProcessingErrors().containsKey(stateKey)) {
                            mediaOutputObject.getDetectionProcessingErrors().put(stateKey, new TreeSet<>());
                        }
                        mediaOutputObject.getDetectionProcessingErrors().get(stateKey).add(jsonDetectionProcessingError);
                        if (!StringUtils.equalsIgnoreCase(mediaOutputObject.getStatus(), "COMPLETE")) {
                            mediaOutputObject.setStatus("INCOMPLETE");
                            if (StringUtils.equalsIgnoreCase(jsonOutputObject.getStatus(), "COMPLETE")) {
                                jsonOutputObject.setStatus("INCOMPLETE");
                            }
                        }
                    }

                    Collection<Track> tracks = inProgressBatchJobs.getTracks(jobId, media.getId(),
                                                                             taskIndex, actionIndex);
                    if(tracks.isEmpty()) {
                        // Always include detection actions in the output object, even if they do not generate any results.
                        addMissingTrackInfo(JsonActionOutputObject.NO_TRACKS_TYPE, stateKey, mediaOutputObject);
                    }
                    else if (suppressedTasks.contains(taskIndex)) {
                        addMissingTrackInfo(JsonActionOutputObject.TRACKS_SUPPRESSED_TYPE, stateKey,
                                            mediaOutputObject);
                    }
                    else {
                        for (Track track : tracks) {
                            JsonTrackOutputObject jsonTrackOutputObject
                                    = createTrackOutputObject(track, stateKey, action, media,
                                                              job);

                            String type = jsonTrackOutputObject.getType();
                            if (!mediaOutputObject.getTypes().containsKey(type)) {
                                mediaOutputObject.getTypes().put(type, new TreeSet<>());
                            }

                            SortedSet<JsonActionOutputObject> actionSet = mediaOutputObject.getTypes().get(type);
                            boolean stateFound = false;
                            for (JsonActionOutputObject jsonAction : actionSet) {
                                if (stateKey.equals(jsonAction.getSource())) {
                                    stateFound = true;
                                    jsonAction.getTracks().add(jsonTrackOutputObject);
                                    break;
                                }
                            }
                            if (!stateFound) {
                                JsonActionOutputObject jsonAction = new JsonActionOutputObject(stateKey);
                                actionSet.add(jsonAction);
                                jsonAction.getTracks().add(jsonTrackOutputObject);
                            }
                        }
                    }

                    if (actionIndex == task.getActions().size() - 1) {
                        stateKeyBuilder.append('#').append(action.getName());
                    }
                }
            }
            jsonOutputObject.getMedia().add(mediaOutputObject);
            mediaIndex++;
        }

        if (hasDetectionProcessingError) {
            jsonOutputObject.getJobErrors()
                    .add("Some components had errors for some media. Refer to the detectionProcessingErrors fields.");
        }

        URI outputLocation = storageService.store(jsonOutputObject);
        jobRequest.setOutputObjectPath(outputLocation.toString());
        jobRequest.setOutputObjectVersion(propertiesUtil.getOutputObjectVersion());
        checkErrorMessages(jsonOutputObject, jobStatus);
        jobRequestDao.persist(jobRequest);
    }


    private static List<DetectionProcessingError> getDetectionProcessingErrors(
            BatchJob job, long mediaId, int taskIndex, int actionIndex) {
        return job.getDetectionProcessingErrors()
                .stream()
                .filter(d -> d.getMediaId() == mediaId && d.getTaskIndex() == taskIndex
                        && d.getActionIndex() == actionIndex)
                .collect(toList());
    }


    private JsonTrackOutputObject createTrackOutputObject(Track track, String stateKey,
                                                          Action action,
                                                          Media media,
                                                          BatchJob job) {
        JsonDetectionOutputObject exemplar = createDetectionOutputObject(track.getExemplar());

        String exemplarsOnlyProp = aggregateJobPropertiesUtil.getValue(
                MpfConstants.OUTPUT_EXEMPLARS_ONLY_PROPERTY, job, media, action);
        boolean exemplarsOnly = Boolean.parseBoolean(exemplarsOnlyProp);

        List<JsonDetectionOutputObject> detections;
        if (exemplarsOnly) {
            detections = List.of(exemplar);
        }
        else {
            detections = track.getDetections().stream()
                    .map(JobCompleteProcessorImpl::createDetectionOutputObject)
                    .collect(toList());
        }

        return new JsonTrackOutputObject(
                TextUtils.getTrackUuid(media.getSha256(),
                                       track.getExemplar().getMediaOffsetFrame(),
                                       track.getExemplar().getX(),
                                       track.getExemplar().getY(),
                                       track.getExemplar().getWidth(),
                                       track.getExemplar().getHeight(),
                                       track.getType()),
                track.getStartOffsetFrameInclusive(),
                track.getEndOffsetFrameInclusive(),
                track.getStartOffsetTimeInclusive(),
                track.getEndOffsetTimeInclusive(),
                track.getType(),
                stateKey,
                track.getConfidence(),
                track.getTrackProperties(),
                exemplar,
                detections);
    }


    private static JsonDetectionOutputObject createDetectionOutputObject(Detection detection) {
        return new JsonDetectionOutputObject(
                detection.getX(),
                detection.getY(),
                detection.getWidth(),
                detection.getHeight(),
                detection.getConfidence(),
                detection.getDetectionProperties(),
                detection.getMediaOffsetFrame(),
                detection.getMediaOffsetTime(),
                detection.getArtifactExtractionStatus().name(),
                detection.getArtifactPath());
    }


    private static void checkErrorMessages(JsonOutputObject outputObject, Mutable<BatchJobStatusType> jobStatus) {
        if (jobStatus.getValue() == BatchJobStatusType.COMPLETE_WITH_ERRORS
                || jobStatus.getValue() == BatchJobStatusType.ERROR) {
            return;
        }
        if (!outputObject.getJobErrors().isEmpty()) {
            jobStatus.setValue(BatchJobStatusType.COMPLETE_WITH_ERRORS);
            return;
        }
        if (!outputObject.getJobWarnings().isEmpty()) {
            jobStatus.setValue(BatchJobStatusType.COMPLETE_WITH_WARNINGS);
        }
    }


    private static boolean isOutputLastTaskOnly(Media media, BatchJob job) {
        // Action properties and algorithm properties are not checked because it doesn't make sense to apply
        // OUTPUT_LAST_STAGE_ONLY to a single task.
        String mediaProperty = media.getMediaSpecificProperty(MpfConstants.OUTPUT_LAST_STAGE_ONLY_PROPERTY);
        if (mediaProperty != null) {
            return Boolean.parseBoolean(mediaProperty);
        }

        String jobProperty = job.getJobProperties()
                .get(MpfConstants.OUTPUT_LAST_STAGE_ONLY_PROPERTY);
        if (jobProperty != null) {
            return Boolean.parseBoolean(jobProperty);
        }

        return job.getSystemPropertiesSnapshot().isOutputObjectLastStageOnly();
    }


    private static Set<Integer> getSuppressedTasks(Media media, BatchJob job) {
        if (!isOutputLastTaskOnly(media, job)) {
            return Set.of();
        }

        List<String> taskNames = job.getPipelineElements().getPipeline().getTasks();
        int lastDetectionTask = 0;
        for (int i = taskNames.size() - 1; i >= 0; i--) {
            ActionType actionType = job.getPipelineElements().getAlgorithm(i, 0).getActionType();
            if (actionType == ActionType.DETECTION) {
                lastDetectionTask = i;
                break;
            }
        }
        return IntStream.range(0, lastDetectionTask)
                .boxed()
                .collect(toSet());
    }


    private static void addMissingTrackInfo(String missingTrackKey, String stateKey,
                                            JsonMediaOutputObject mediaOutputObject) {
        Set<JsonActionOutputObject> trackSet = mediaOutputObject.getTypes().computeIfAbsent(
                missingTrackKey, k -> new TreeSet<>());
        boolean stateMissing = trackSet.stream().noneMatch(a -> stateKey.equals(a.getSource()));
        if (stateMissing) {
            trackSet.add(new JsonActionOutputObject(stateKey));
        }
    }


    @Autowired
    private JmsUtils jmsUtils;

    private void destroy(BatchJob batchJob) throws WfmProcessingException {
        for(Media media : batchJob.getMedia()) {
            if(media.getUriScheme().isRemote() && media.getLocalPath() != null) {
                try {
                    Files.deleteIfExists(media.getLocalPath());
                } catch(IOException exception) {
                    log.warn("[{}|*|*] Failed to delete locally cached file '{}' due to an exception. This file must be manually deleted.",
                             batchJob.getId(), media.getLocalPath());
                }
            }
        }
        inProgressBatchJobs.clearJob(batchJob.getId());
    }

    @Override
    public void subscribe(NotificationConsumer<JobCompleteNotification> consumer) {
        log.info("Subscribing completion consumer {}.", consumer.getId());
        consumers.add(consumer);
    }

    @Override
    public void unsubscribe(NotificationConsumer<JobCompleteNotification> consumer) {
        log.info("Unsubscribing completion consumer {}.", consumer.getId());
        consumers.remove(consumer);
    }

    /**
     * Thread to handle the Callback to a URL given a HTTP method
     */
    public class CallbackThread implements Runnable {
        private long jobId;
        private String callbackURL;
        private String callbackMethod;
        private HttpUriRequest req;

        public CallbackThread(String callbackURL,String callbackMethod,JsonCallbackBody body) throws UnsupportedEncodingException {
            jobId = body.getJobId();
            this.callbackURL = callbackURL;
            this.callbackMethod = callbackMethod;

            if(callbackMethod.equals("GET")) {
                String jsonCallbackURL2 = callbackURL;
                if(jsonCallbackURL2.contains("?")){
                    jsonCallbackURL2 +="&";
                }else{
                    jsonCallbackURL2 +="?";
                }
                jsonCallbackURL2 +="jobid="+body.getJobId();
                if(body.getExternalId() != null){
                    jsonCallbackURL2 += "&externalid="+body.getExternalId();
                }
                req = new HttpGet(jsonCallbackURL2);
            }else { // this is for a POST
                HttpPost post = new HttpPost(callbackURL);
                post.addHeader("Content-Type", "application/json");
                try {
                    post.setEntity(new StringEntity(jsonUtils.serializeAsTextString(body)));
                    req = post;
                } catch (WfmProcessingException e) {
                    log.error("Cannot serialize CallbackBody for job id " + jobId, e);
                }
            }
        }

        @Override
        public void run() {
            final HttpClient httpClient = HttpClientBuilder.create().build();
            try {
                HttpResponse response = httpClient.execute(req);
                log.info("{} callback issued to '{}' for job id {}. (Response={})",
                         callbackMethod, callbackURL, jobId, response);
            } catch (Exception exception) {
                log.warn(String.format("Failed to issue %s callback to '%s' for job id %s.",
                                       callbackMethod, callbackURL, jobId), exception);
            }
        }
    }
}
