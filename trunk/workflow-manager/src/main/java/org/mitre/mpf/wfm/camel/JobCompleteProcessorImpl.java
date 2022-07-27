/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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

import com.google.common.collect.ImmutableMap;
import org.apache.camel.Exchange;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.mitre.mpf.interop.*;
import org.mitre.mpf.rest.api.pipelines.*;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.access.JobRequestDao;
import org.mitre.mpf.wfm.data.access.MarkupResultDao;
import org.mitre.mpf.wfm.data.entities.persistent.*;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.data.entities.transients.TrackCounter;
import org.mitre.mpf.wfm.enums.*;
import org.mitre.mpf.wfm.event.JobCompleteNotification;
import org.mitre.mpf.wfm.event.JobProgress;
import org.mitre.mpf.wfm.event.NotificationConsumer;
import org.mitre.mpf.wfm.service.CensorPropertiesService;
import org.mitre.mpf.wfm.service.JobStatusBroadcaster;
import org.mitre.mpf.wfm.service.StorageService;
import org.mitre.mpf.wfm.service.TiesDbService;
import org.mitre.mpf.wfm.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.*;

@Component(JobCompleteProcessorImpl.REF)
public class JobCompleteProcessorImpl extends WfmProcessor implements JobCompleteProcessor {
    private static final Logger log = LoggerFactory.getLogger(JobCompleteProcessor.class);
    public static final String REF = "jobCompleteProcessorImpl";

    private final Set<NotificationConsumer<JobCompleteNotification>> consumers = new ConcurrentSkipListSet<>();

    @Autowired
    private JobRequestDao jobRequestDao;

    @Autowired
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
    private CensorPropertiesService censorPropertiesService;

    @Autowired
    private AggregateJobPropertiesUtil aggregateJobPropertiesUtil;

    @Autowired
    private CallbackUtils callbackUtils;

    @Autowired
    private JmsUtils jmsUtils;

    @Autowired
    private TiesDbService tiesDbService;

    @Override
    public void wfmProcess(Exchange exchange) throws WfmProcessingException {
        Long jobId = exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class);
        assert jobId != null : String.format("The header '%s' (value=%s) was not set or is not a Long.",
                MpfHeaders.JOB_ID, exchange.getIn().getHeader(MpfHeaders.JOB_ID));

        BatchJob job = inProgressBatchJobs.getJob(jobId);

        storageService.storeDerivativeMedia(job);

        JobRequest jobRequest = jobRequestDao.findById(jobId);
        jobRequest.setTimeCompleted(Instant.now());

        var completionStatus = job.getStatus().onComplete();

        URI outputObjectUri = null;
        var outputSha = new MutableObject<String>();
        var trackCounter = new TrackCounter();
        try {
            outputObjectUri = createOutputObject(
                    job,
                    jobRequest.getTimeReceived(),
                    jobRequest.getTimeCompleted(),
                    completionStatus,
                    outputSha,
                    trackCounter); // this may update the job status
            jobRequest.setOutputObjectPath(outputObjectUri.toString());
            jobRequest.setOutputObjectVersion(propertiesUtil.getOutputObjectVersion());
        } catch (Exception exception) {
            var message = "Failed to create the output object due to: " + exception;
                     log.error(message, exception);
            inProgressBatchJobs.addFatalError(jobId, IssueCodes.OTHER, message);
                                            }
        completionStatus = job.getStatus().onComplete();

        jobProgressStore.setJobProgress(jobId, 100);
            inProgressBatchJobs.setJobStatus(jobId, completionStatus);
        jobRequest.setStatus(completionStatus);
        jobRequest.setJob(jsonUtils.serialize(job));
        checkCallbacks(job, jobRequest);

        jobRequestDao.persist(jobRequest);
        jobStatusBroadcaster.broadcast(job.getId(), 100, job.getStatus());

        IoUtils.deleteEmptyDirectoriesRecursively(propertiesUtil.getJobMarkupDirectory(jobId).toPath());
        IoUtils.deleteEmptyDirectoriesRecursively(propertiesUtil.getJobArtifactsDirectory(jobId).toPath());
        IoUtils.deleteEmptyDirectoriesRecursively(propertiesUtil.getJobOutputObjectsDirectory(jobId).toPath());

        try {
            jmsUtils.destroyCancellationRoutes(jobId);
        }
        catch (Exception exception) {
            log.warn(String.format("Failed to destroy the cancellation routes associated with job %d." +
                            " If this job is resubmitted, it will likely not complete again!", jobId), exception);
        }

        var tiesDbFuture = tiesDbService.addAssertions(
                job,
                completionStatus,
                jobRequest.getTimeCompleted(),
                outputObjectUri,
                outputSha.getValue(),
                trackCounter);

        if (job.getCallbackUrl().isPresent()) {
            final var finalOutputUri = outputObjectUri;
            tiesDbFuture
                    .whenCompleteAsync(
                            (x, err) -> sendCallbackAsync(job, finalOutputUri).join())
                    .whenCompleteAsync((x, err) -> completeJob(job));
        }
        else {
            tiesDbFuture.whenCompleteAsync((x, err) -> completeJob(job));
        }
    }


    private void checkCallbacks(BatchJob job, JobRequest jobRequest) {
        if (job.getCallbackUrl().isPresent()) {
            inProgressBatchJobs.setCallbacksInProgress(job.getId());
            jobStatusBroadcaster.callbackStatusChanged(job.getId(), CallbackStatus.inProgress());
        }
        else {
            var newStatus = CallbackStatus.notRequested();
            jobRequest.setCallbackStatus(newStatus);
            jobStatusBroadcaster.callbackStatusChanged(job.getId(), newStatus);
        }

        boolean requiresTiesDb = JobPartsIter.stream(job)
                .map(jp -> aggregateJobPropertiesUtil.getValue(MpfConstants.TIES_DB_URL, jp))
                .anyMatch(url -> url != null && !url.isBlank());
        if (requiresTiesDb) {
            inProgressBatchJobs.setCallbacksInProgress(job.getId());
            jobStatusBroadcaster.tiesDbStatusChanged(job.getId(), CallbackStatus.inProgress());
        }
        else {
            var newStatus = CallbackStatus.notRequested();
            jobRequest.setTiesDbStatus(newStatus);
            jobStatusBroadcaster.tiesDbStatusChanged(job.getId(), newStatus);
        }
    }


    private void completeJob(BatchJob job) {
        try {
            inProgressBatchJobs.clearJob(job.getId());
        } catch (Exception exception) {
            log.warn(String.format(
                    "Failed to clean up job %d due to an exception. Data for this job will remain in the transient " +
                        "data store, but the status of the job has not been affected by this failure.", job.getId()),
                     exception);
        }

        log.info("Notifying {} completion consumer(s).", consumers.size());
        for (NotificationConsumer<JobCompleteNotification> consumer : consumers) {
            try {
                log.info("Notifying completion consumer {}.", consumer.getId());
                consumer.onNotification(this, new JobCompleteNotification(job.getId()));
            } catch (Exception exception) {
                log.warn(String.format("Completion consumer %s threw an exception.", consumer.getId()), exception);
            }
        }

        jobProgressStore.removeJob(job.getId());
        log.info("Job complete with status: {}", job.getStatus());
    }


    private CompletableFuture<Void> sendCallbackAsync(BatchJob job, URI outputObjectUri) {
        String callbackMethod = job.getCallbackMethod().orElse("POST");

        String callbackUrl = job.getCallbackUrl()
                // This will never throw because we already checked that the URL is present.
                .orElseThrow();

        log.info("Starting {} callback to {} for job id {}.", callbackMethod, callbackUrl, job.getId());
        try {
            HttpUriRequest request = createCallbackRequest(callbackMethod, callbackUrl,
                                                           job, outputObjectUri);
            return callbackUtils.executeRequest(request, propertiesUtil.getHttpCallbackRetryCount())
                    .thenAccept(resp -> checkResponse(job.getId(), resp))
                    .exceptionally(err -> handleFailedCallback(job, err));
        }
        catch (Exception e) {
            handleFailedCallback(job, e);
            return ThreadUtil.completedFuture(null);
        }
    }

    private void checkResponse(long jobId, HttpResponse response) {
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode < 200 || statusCode > 299) {
            throw new IllegalStateException(
                    "The remote server responded with a non-200 status code of: " + statusCode);
        }
        jobRequestDao.setCallbackSuccessful(jobId);
    }


    private HttpUriRequest createCallbackRequest(
            String jsonCallbackMethod, String jsonCallbackURL, BatchJob job, URI outputObjectUri)
                throws URISyntaxException {

        RequestConfig requestConfig = RequestConfig.custom()
                .setSocketTimeout(propertiesUtil.getHttpCallbackTimeoutMs())
                .setConnectTimeout(propertiesUtil.getHttpCallbackTimeoutMs())
                .build();

        String exportedJobId = propertiesUtil.getExportedJobId(job.getId());

        if ("GET".equals(jsonCallbackMethod)) {
            URIBuilder callbackUriWithParamsBuilder = new URIBuilder(jsonCallbackURL)
                    .setParameter("jobid", exportedJobId);

            job.getExternalId()
                    .ifPresent(id -> callbackUriWithParamsBuilder.setParameter("externalid", id));

            if (outputObjectUri != null) {
                callbackUriWithParamsBuilder.setParameter("outputobjecturi", outputObjectUri.toString());
            }
            var getRequest = new HttpGet(callbackUriWithParamsBuilder.build());
            getRequest.setConfig(requestConfig);
            return getRequest;
        }

        var postRequest = new HttpPost(jsonCallbackURL);
        postRequest.addHeader("Content-Type", "application/json");
        postRequest.setConfig(requestConfig);

        String outputObjectUriString = outputObjectUri == null
                ? null
                : outputObjectUri.toString();

        JsonCallbackBody jsonBody = new JsonCallbackBody(
                exportedJobId, job.getExternalId().orElse(null), outputObjectUriString);

        postRequest.setEntity(new StringEntity(jsonUtils.serializeAsTextString(jsonBody),
                                               ContentType.APPLICATION_JSON));
        return postRequest;
    }


    private Void handleFailedCallback(BatchJob job, Throwable callbackError) {
        if (callbackError instanceof CompletionException && callbackError.getCause() != null) {
            callbackError = callbackError.getCause();
        }

        String callbackMethod = job.getCallbackMethod().orElse("POST");
        String callbackUrl = job.getCallbackUrl()
                // This will never throw because we already checked that the URL is present.
                .orElseThrow();

        var errorMessage = String.format(
                "Sending HTTP %s callback to \"%s\" failed due to: %s",
                callbackMethod, callbackUrl, callbackError);
        log.error(errorMessage, callbackError);
        jobRequestDao.setCallbackError(job.getId(), errorMessage);

        return null;
    }


    @Override
    public URI createOutputObject(BatchJob job,
                                  Instant timeReceived,
                                  Instant timeCompleted,
                                  BatchJobStatusType completionStatus,
                                  Mutable<String> outputSha,
                                  TrackCounter trackCounter) throws IOException {
        long jobId = job.getId();
        String exportedJobId = propertiesUtil.getExportedJobId(jobId);
        JsonOutputObject jsonOutputObject = new JsonOutputObject(
                exportedJobId,
                UUID.randomUUID().toString(),
                convertPipeline(job.getPipelineElements()),
                job.getPriority(),
                propertiesUtil.getSiteId(),
                propertiesUtil.getSemanticVersion(),
                job.getExternalId().orElse(null),
                timeReceived,
                timeCompleted,
                completionStatus.toString());

        censorPropertiesService.copyAndCensorProperties(
                job.getJobProperties(), jsonOutputObject.getJobProperties());

        censorPropertiesService.copyAndCensorProperties(
                getEnvironmentProperties(), jsonOutputObject.getEnvironmentVariableProperties());

        for (Map.Entry<String, ImmutableMap<String, String>> algoPropsEntry
                : job.getOverriddenAlgorithmProperties().entrySet()) {
            jsonOutputObject.getAlgorithmProperties().put(
                    algoPropsEntry.getKey(),
                    censorPropertiesService.copyAndCensorProperties(algoPropsEntry.getValue()));
        }

        job.getWarnings().forEach(jsonOutputObject::addWarnings);
        job.getErrors().forEach(jsonOutputObject::addErrors);
        inProgressBatchJobs.getMergedDetectionErrors(job.getId())
                .asMap()
                .forEach(jsonOutputObject::addErrors);

        int mediaIndex = 0;
        for (Media media : job.getMedia()) {
            StringBuilder stateKeyBuilder = new StringBuilder("+");

            JsonMediaOutputObject mediaOutputObject = new JsonMediaOutputObject(
                    media.getId(), media.getParentId(), media.getPersistentUri(),
                    media.getType() != null ? media.getType().toString() : null,
                    media.getMimeType(), media.getLength(), media.getSha256(), media.isFailed() ? "ERROR" : "COMPLETE");

            for (var frameRange : media.getFrameRanges()) {
                mediaOutputObject.getFrameRanges().add(new JsonMediaRange(
                        frameRange.getStartInclusive(), frameRange.getEndInclusive()));
            }

            for (var timeRange : media.getTimeRanges()) {
                mediaOutputObject.getTimeRanges().add(new JsonMediaRange(
                        timeRange.getStartInclusive(), timeRange.getEndInclusive()));
            }

            mediaOutputObject.getMediaMetadata().putAll(media.getMetadata());
            censorPropertiesService.copyAndCensorProperties(
                    media.getMediaSpecificProperties(),
                    mediaOutputObject.getMediaProperties());

            markupResultDao.findByJobIdAndMediaIndex(jobId, mediaIndex)
                    .ifPresent(mr -> mediaOutputObject.setMarkupResult(
                            new JsonMarkupOutputObject(
                                    mr.getId(),
                                    mr.getMarkupUri(),
                                    mr.getMarkupStatus().name(),
                                    mr.getMessage())));

            Set<Integer> tasksToSuppress = getTasksToSuppress(media, job);
            Map<Integer, Integer> tasksToMerge = aggregateJobPropertiesUtil.getTasksToMerge(media, job);

            String prevUnmergedTaskType = null;
            String prevUnmergedAlgorithm = null;

            for (int taskIndex = (media.getCreationTask() + 1); taskIndex < job.getPipelineElements().getTaskCount(); taskIndex++) {
                Task task = job.getPipelineElements().getTask(taskIndex);

                for (int actionIndex = 0; actionIndex < task.getActions().size(); actionIndex++) {
                    String actionName = task.getActions().get(actionIndex);
                    Action action = job.getPipelineElements().getAction(actionName);

                    if (!aggregateJobPropertiesUtil.actionAppliesToMedia(job, media, action)) {
                        continue;
                    }

                    String stateKey = String.format("%s#%s", stateKeyBuilder, action.getName());

                    for (DetectionProcessingError detectionProcessingError : getDetectionProcessingErrors(
                             job, media.getId(), taskIndex, actionIndex)) {
                        JsonDetectionProcessingError jsonDetectionProcessingError =
                                 new JsonDetectionProcessingError(
                                         detectionProcessingError.getStartFrame(),
                                         detectionProcessingError.getStopFrame(),
                                         detectionProcessingError.getStartTime(),
                                         detectionProcessingError.getStopTime(),
                                         detectionProcessingError.getErrorCode(),
                                         detectionProcessingError.getErrorMessage());
                        if (!mediaOutputObject.getDetectionProcessingErrors().containsKey(stateKey)) {
                            mediaOutputObject.getDetectionProcessingErrors().put(stateKey, new TreeSet<>());
                        }
                        mediaOutputObject.getDetectionProcessingErrors().get(stateKey).add(jsonDetectionProcessingError);
                        if (!StringUtils.equalsIgnoreCase(mediaOutputObject.getStatus(), "COMPLETE")) {
                            mediaOutputObject.setStatus("INCOMPLETE");
                        }
                    }

                    SortedSet<Track> tracks = inProgressBatchJobs.getTracks(jobId, media.getId(),
                                                                            taskIndex, actionIndex);
                    if (tracks.isEmpty()) {
                        trackCounter.set(media.getId(), taskIndex, actionIndex,
                                         JsonActionOutputObject.NO_TRACKS_TYPE, 0);
                    }
                    else {
                        var type = tracks.iterator().next().getType();
                        trackCounter.set(media.getId(), taskIndex, actionIndex, type, tracks.size());
                    }

                    if (tracks.isEmpty()) {
                        // Always include detection actions in the output object,
                        // even if they do not generate any results.
                        if (tasksToMerge.containsKey(taskIndex)) {
                            addMissingTrackInfo(JsonActionOutputObject.NO_TRACKS_TYPE, stateKey,
                                                prevUnmergedAlgorithm, mediaOutputObject);
                        }
                        else {
                            addMissingTrackInfo(JsonActionOutputObject.NO_TRACKS_TYPE, stateKey,
                                                action.getAlgorithm(), mediaOutputObject);
                        }
                    }
                    else if (tasksToSuppress.contains(taskIndex)) {
                        addMissingTrackInfo(JsonActionOutputObject.TRACKS_SUPPRESSED_TYPE, stateKey,
                                action.getAlgorithm(), mediaOutputObject);
                    }
                    else if (tasksToMerge.containsValue(taskIndex)) {
                        // This task will be merged with one that follows.
                        addMissingTrackInfo(JsonActionOutputObject.TRACKS_MERGED_TYPE, stateKey,
                                action.getAlgorithm(), mediaOutputObject);
                    }
                    else {
                        int trackIndex = 0;
                        for (Track track : tracks) {
                            // tasksToMerge will never contain task 0, so the initial null values of
                            // prevUnmergedTaskType and prevUnmergedAlgorithm are never used.
                            String type = tasksToMerge.containsKey(taskIndex) ? prevUnmergedTaskType :
                                    track.getType();
                            String algo = tasksToMerge.containsKey(taskIndex) ? prevUnmergedAlgorithm :
                                    action.getAlgorithm();

                            JsonTrackOutputObject jsonTrackOutputObject
                                    = createTrackOutputObject(track, trackIndex, stateKey, type, action, media, job);

                            if (!mediaOutputObject.getDetectionTypes().containsKey(type)) {
                                mediaOutputObject.getDetectionTypes().put(type, new TreeSet<>());
                            }

                            SortedSet<JsonActionOutputObject> actionSet =
                                    mediaOutputObject.getDetectionTypes().get(type);
                            boolean stateFound = false;
                            for (JsonActionOutputObject jsonAction : actionSet) {
                                if (stateKey.equals(jsonAction.getSource())) {
                                    stateFound = true;
                                    jsonAction.getTracks().add(jsonTrackOutputObject);
                                    break;
                                }
                            }
                            if (!stateFound) {
                                JsonActionOutputObject jsonAction = new JsonActionOutputObject(stateKey, algo);
                                actionSet.add(jsonAction);
                                jsonAction.getTracks().add(jsonTrackOutputObject);
                            }
                            trackIndex++;
                        }
                    }

                    if (!tasksToMerge.containsKey(taskIndex)) {
                        // NOTE: If and when we support parallel actions in tasks other then the final one in a
                        // pipeline, this code will need to be updated.
                        prevUnmergedTaskType = tracks.isEmpty() ? JsonActionOutputObject.NO_TRACKS_TYPE :
                                tracks.stream().findFirst().get().getType(); // all tracks from same task have same type
                        prevUnmergedAlgorithm = action.getAlgorithm();
                    }

                    if (actionIndex == task.getActions().size() - 1) {
                        stateKeyBuilder.append('#').append(action.getName());
                    }
                }
            }
            jsonOutputObject.getMedia().add(mediaOutputObject);
            mediaIndex++;
        }

        // this may update the job status
        return storageService.store(jsonOutputObject, outputSha);
    }


    private static List<DetectionProcessingError> getDetectionProcessingErrors(
        BatchJob job, long mediaId, int taskIndex, int actionIndex) {
        return job.getDetectionProcessingErrors()
                .stream()
                .filter(d -> d.getMediaId() == mediaId && d.getTaskIndex() == taskIndex
                        && d.getActionIndex() == actionIndex)
                .collect(toList());
    }


    private JsonTrackOutputObject createTrackOutputObject(Track track, int trackIndex, String stateKey, String type,
                                                          Action action, Media media, BatchJob job) {
        JsonDetectionOutputObject exemplar = createDetectionOutputObject(track.getExemplar());

        String artifactsAndExemplarsOnlyProp = aggregateJobPropertiesUtil.getValue(
            MpfConstants.OUTPUT_ARTIFACTS_AND_EXEMPLARS_ONLY_PROPERTY, job, media, action);
        boolean artifactsAndExemplarsOnly = Boolean.parseBoolean(artifactsAndExemplarsOnlyProp);

        List<JsonDetectionOutputObject> detections;
        if (artifactsAndExemplarsOnly) {
            // This property requires that the exemplar AND all extracted frames be
            // available in the output object. Otherwise, the user might never know that
            // there were other artifacts extracted.
            detections = track.getDetections().stream()
                         .filter(d -> (d.getArtifactExtractionStatus() == ArtifactExtractionStatus.COMPLETED))
                         .map(this::createDetectionOutputObject)
                         .collect(toList());
            detections.add(exemplar);
        }
        else {
            detections = track.getDetections().stream()
                         .map(this::createDetectionOutputObject)
                         .collect(toList());
        }

        return new JsonTrackOutputObject(
            trackIndex,
            TextUtils.getTrackUuid(media.getSha256(),
                                   track.getExemplar().getMediaOffsetFrame(),
                                   track.getExemplar().getX(),
                                   track.getExemplar().getY(),
                                   track.getExemplar().getWidth(),
                                   track.getExemplar().getHeight(),
                                   type),
            track.getStartOffsetFrameInclusive(),
            track.getEndOffsetFrameInclusive(),
            track.getStartOffsetTimeInclusive(),
            track.getEndOffsetTimeInclusive(),
            type,
            stateKey,
            track.getConfidence(),
            censorPropertiesService.copyAndCensorProperties(track.getTrackProperties()),
            exemplar,
            detections);
    }


    private JsonDetectionOutputObject createDetectionOutputObject(Detection detection) {
        return new JsonDetectionOutputObject(
            detection.getX(),
            detection.getY(),
            detection.getWidth(),
            detection.getHeight(),
            detection.getConfidence(),
            censorPropertiesService.copyAndCensorProperties(detection.getDetectionProperties(),
                                                            new TreeMap<>()),
            detection.getMediaOffsetFrame(),
            detection.getMediaOffsetTime(),
            detection.getArtifactExtractionStatus().name(),
            detection.getArtifactPath());
    }


    private JsonPipeline convertPipeline(JobPipelineElements pipelineElements) {
        Pipeline pipeline = pipelineElements.getPipeline();
        JsonPipeline jsonPipeline = new JsonPipeline(pipeline.getName(), pipeline.getDescription());
        var censorOperator = censorPropertiesService.createCensorOperator();

        for (String taskName : pipeline.getTasks()) {
            Task task = pipelineElements.getTask(taskName);
            JsonTask jsonTask = new JsonTask(getActionType(pipelineElements, task).name(), taskName,
                                             task.getDescription());

            for (String actionName : task.getActions()) {
                Action action = pipelineElements.getAction(actionName);
                JsonAction jsonAction = new JsonAction(action.getAlgorithm(), actionName,
                                                       action.getDescription());
                for (ActionProperty property : action.getProperties()) {
                    jsonAction.getProperties().put(
                            property.getName(),
                            censorOperator.apply(property.getName(), property.getValue()));
                }
                jsonTask.getActions().add(jsonAction);
            }

            jsonPipeline.getTasks().add(jsonTask);
        }
        return jsonPipeline;
    }

    private static ActionType getActionType(JobPipelineElements pipeline, Task task) {
        Action action = pipeline.getAction(task.getActions().get(0));
        return pipeline.getAlgorithm(action.getAlgorithm()).getActionType();
    }


    private Set<Integer> getTasksToSuppress(Media media, BatchJob job) {
        if (!aggregateJobPropertiesUtil.isOutputLastTaskOnly(media, job)) {
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


    private static void addMissingTrackInfo(String missingTrackKey, String stateKey, String algorithm,
                                            JsonMediaOutputObject mediaOutputObject) {
        Set<JsonActionOutputObject> trackSet = mediaOutputObject.getDetectionTypes().computeIfAbsent(
            missingTrackKey, k -> new TreeSet<>());
        boolean stateMissing = trackSet.stream().noneMatch(a -> stateKey.equals(a.getSource()));
        if (stateMissing) {
            trackSet.add(new JsonActionOutputObject(stateKey, algorithm));
        }
    }


    private static Map<String, String> getEnvironmentProperties() {
        var propertyPrefix = "MPF_PROP_";
        return System.getenv()
                .entrySet()
                .stream()
                .filter(e -> e.getKey().length() > propertyPrefix.length()
                        && e.getKey().startsWith(propertyPrefix))
                .collect(toMap(e -> e.getKey().substring(propertyPrefix.length()),
                               Map.Entry::getValue));
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
}
