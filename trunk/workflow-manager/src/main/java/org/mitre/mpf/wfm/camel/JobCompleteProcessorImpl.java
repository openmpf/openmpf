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

package org.mitre.mpf.wfm.camel;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListSet;

import org.apache.camel.Exchange;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;
import org.mitre.mpf.interop.JsonAction;
import org.mitre.mpf.interop.JsonActionOutputObject;
import org.mitre.mpf.interop.JsonActionTiming;
import org.mitre.mpf.interop.JsonDetectionOutputObject;
import org.mitre.mpf.interop.JsonDetectionProcessingError;
import org.mitre.mpf.interop.JsonMarkupOutputObject;
import org.mitre.mpf.interop.JsonMediaOutputObject;
import org.mitre.mpf.interop.JsonMediaRange;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.interop.JsonPipeline;
import org.mitre.mpf.interop.JsonTask;
import org.mitre.mpf.interop.JsonTiming;
import org.mitre.mpf.interop.JsonTrackOutputObject;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.ActionProperty;
import org.mitre.mpf.rest.api.pipelines.ActionType;
import org.mitre.mpf.rest.api.pipelines.Pipeline;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.access.JobRequestDao;
import org.mitre.mpf.wfm.data.access.MarkupResultDao;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.data.entities.transients.TrackCounter;
import org.mitre.mpf.wfm.enums.ArtifactExtractionStatus;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.event.JobCompleteNotification;
import org.mitre.mpf.wfm.event.JobProgress;
import org.mitre.mpf.wfm.event.NotificationConsumer;
import org.mitre.mpf.wfm.service.CensorPropertiesService;
import org.mitre.mpf.wfm.service.JobCompleteCallbackService;
import org.mitre.mpf.wfm.service.JobStatusBroadcaster;
import org.mitre.mpf.wfm.service.StorageService;
import org.mitre.mpf.wfm.service.TaskMergingManager;
import org.mitre.mpf.wfm.service.TiesDbBeforeJobCheckService;
import org.mitre.mpf.wfm.service.TiesDbService;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.CallbackStatus;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.JmsUtils;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.TextUtils;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;

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
    private JmsUtils jmsUtils;

    @Autowired
    private TiesDbService tiesDbService;

    @Autowired
    private TiesDbBeforeJobCheckService tiesDbBeforeJobCheckService;

    @Autowired
    private TrackOutputHelper trackOutputHelper;

    @Autowired
    private JobCompleteCallbackService jobCompleteCallbackService;

    @Autowired
    private TaskMergingManager taskMergingManager;

    @Override
    public void wfmProcess(Exchange exchange) throws WfmProcessingException {
        Long jobId = exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class);
        assert jobId != null : String.format("The header '%s' (value=%s) was not set or is not a Long.",
                MpfHeaders.JOB_ID, exchange.getIn().getHeader(MpfHeaders.JOB_ID));

        BatchJob job = inProgressBatchJobs.getJob(jobId);
        var outputObjectFromTiesDbUri = exchange.getIn().getHeader(
                MpfHeaders.OUTPUT_OBJECT_URI_FROM_TIES_DB, String.class);
        var skippedJobDueToTiesDbEntry = outputObjectFromTiesDbUri != null;

        if (!skippedJobDueToTiesDbEntry) {
            storageService.storeDerivativeMedia(job);
        }

        JobRequest jobRequest = jobRequestDao.findById(jobId);
        jobRequest.setTimeCompleted(Instant.now());

        var completionStatus = job.getStatus().onComplete();

        URI outputObjectUri = null;
        boolean outputObjectExists = false;
        var outputSha = new MutableObject<String>();
        var trackCounter = new TrackCounter();
        try {
            if (skippedJobDueToTiesDbEntry) {
                outputObjectUri = tiesDbBeforeJobCheckService.updateOutputObject(
                        job, URI.create(outputObjectFromTiesDbUri), jobRequest);
            }
            else {
                outputObjectUri = createOutputObject(
                        job,
                        jobRequest.getTimeReceived(),
                        jobRequest.getTimeCompleted(),
                        completionStatus,
                        outputSha,
                        trackCounter); // this may update the job status
            }
            outputObjectExists = true;
            jobRequest.setOutputObjectPath(outputObjectUri.toString());
            jobRequest.setOutputObjectVersion(propertiesUtil.getOutputObjectVersion());
        } catch (Exception exception) {
            var message = "Failed to create the output object due to: " + exception;
            log.error(message, exception);
            inProgressBatchJobs.addFatalError(jobId, IssueCodes.OTHER, message);
        }

        if (skippedJobDueToTiesDbEntry) {
            completionStatus = job.getStatus();
        }
        else {
            completionStatus = job.getStatus().onComplete();
            tiesDbService.prepareAssertions(
                    job,
                    completionStatus,
                    jobRequest.getTimeCompleted(),
                    outputObjectUri,
                    outputSha.getValue(),
                    trackCounter,
                    getTiming(job));
        }

        jobProgressStore.setJobProgress(jobId, 100);
        inProgressBatchJobs.setJobStatus(jobId, completionStatus);
        jobRequest.setStatus(completionStatus);
        jobRequest.setJob(jsonUtils.serialize(job));
        checkCallbacks(job, jobRequest);
        if (skippedJobDueToTiesDbEntry) {
            jobStatusBroadcaster.tiesDbStatusChanged(jobId, jobRequest.getTiesDbStatus());
        }
        else {
            checkTiesDbRequired(job, jobRequest);
        }

        jobRequestDao.persist(jobRequest);
        jobStatusBroadcaster.broadcast(
            job.getId(), 100, job.getStatus(), jobRequest.getTimeCompleted(), outputObjectExists);

        IoUtils.deleteEmptyDirectoriesRecursively(propertiesUtil.getJobMarkupDirectory(jobId).toPath());
        IoUtils.deleteEmptyDirectoriesRecursively(propertiesUtil.getJobArtifactsDirectory(jobId).toPath());
        IoUtils.deleteEmptyDirectoriesRecursively(propertiesUtil.getJobOutputObjectsDirectory(jobId).toPath());

        try {
            jmsUtils.destroyDetectionCancellationRoutes(jobId);
        }
        catch (Exception exception) {
            log.warn(String.format("Failed to destroy the cancellation routes associated with job %d." +
                            " If this job is resubmitted, it will likely not complete again!", jobId), exception);
        }

        var tiesDbFuture = skippedJobDueToTiesDbEntry
                ? ThreadUtil.completedFuture(null)
                : tiesDbService.postAssertions(job);

        if (job.getCallbackUrl().isPresent()) {
            final var finalOutputUri = outputObjectUri;
            tiesDbFuture
                    .exceptionally(x -> null)
                    .thenCompose(x -> sendCallbackAsync(job, finalOutputUri))
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
    }

    private void checkTiesDbRequired(BatchJob job, JobRequest jobRequest) {
        boolean requiresTiesDb = job.getMedia()
                .stream()
                .anyMatch(m -> !m.getTiesDbInfo().isEmpty());

        if (requiresTiesDb) {
            inProgressBatchJobs.setCallbacksInProgress(job.getId());
            jobStatusBroadcaster.tiesDbStatusChanged(job.getId(), CallbackStatus.inProgress());
        }
        else if (jobRequest.getTiesDbStatus() == null
                || !jobRequest.getTiesDbStatus().startsWith("ERROR")) {
            var newStatus = CallbackStatus.notRequested();
            jobRequest.setTiesDbStatus(newStatus);
            jobStatusBroadcaster.tiesDbStatusChanged(job.getId(), newStatus);
        }
    }


    private void completeJob(BatchJob job) {
        try {
            inProgressBatchJobs.clearJob(job.getId());
            jobProgressStore.removeJob(job.getId());
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
        log.info("Job complete with status: {}", job.getStatus());
    }


    private CompletableFuture<Void> sendCallbackAsync(BatchJob job, URI outputObjectUri) {
        return jobCompleteCallbackService.sendCallback(job, outputObjectUri)
                .thenAccept(r -> jobRequestDao.setCallbackSuccessful(job.getId()))
                .exceptionally(err -> handleFailedCallback(job, err));
    }


    private Void handleFailedCallback(BatchJob job, Throwable callbackError) {
        callbackError = ThreadUtil.unwrap(callbackError);

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
                completionStatus.toString(),
                getTiming(job));

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
            var mediaOutputObject = new JsonMediaOutputObject(
                    media.getId(),
                    media.getParentId(),
                    media.getPersistentUri().fullString(),
                    null,
                    media.getType().map(Enum::toString).orElse(null),
                    media.getMimeType().orElse(null),
                    media.getLength().orElse(0),
                    media.getSha256().orElse(null),
                    media.isFailed() ? "ERROR" : "COMPLETE",
                    media.getMediaSelectorsOutputUri().map(Object::toString).orElse(null));

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
            addTracksForMedia(job, media, mediaOutputObject, trackCounter);
            addDetectionProcessingErrors(job, mediaOutputObject);
            jsonOutputObject.getMedia().add(mediaOutputObject);
            mediaIndex++;
        }

        // this may update the job status
        URI outputObjectUri = storageService.store(jsonOutputObject, outputSha);
        inProgressBatchJobs.reportJobResultsAvailable(jobId, jsonOutputObject);
        return outputObjectUri;
    }


    private void addTracksForMedia(
            BatchJob job,
            Media media,
            JsonMediaOutputObject mediaOutputObject,
            TrackCounter trackCounter) {
        var prevUnmergedAction = job.getPipelineElements().getAction(0, 0);
        var noOutputActions = HashMultimap.<String, Action>create();
        var annotatorsMap = new HashMap<String, List<String>>();

        for (int taskIndex = (media.getCreationTask() + 1); taskIndex < job.getPipelineElements().getTaskCount(); taskIndex++) {
            Task task = job.getPipelineElements().getTask(taskIndex);
            var annotators = getAnnotators(job, media, taskIndex);

            for (int actionIndex = 0; actionIndex < task.actions().size(); actionIndex++) {
                String actionName = task.actions().get(actionIndex);
                Action action = job.getPipelineElements().getAction(actionName);

                if (!aggregateJobPropertiesUtil.actionAppliesToMedia(job, media, action)) {
                    continue;
                }

                // store the current action name -> annotators lookup
                annotatorsMap.put(actionName, annotators);

                var trackInfo = trackOutputHelper.getTrackInfo(job, media, taskIndex, actionIndex);
                if (!trackInfo.hadAnyTracks()) {
                    var noTracksAction = trackInfo.isMergeSource()
                            ? prevUnmergedAction
                            : action;
                    // Always include detection actions in the output object,
                    // even if they do not generate any results.
                    noOutputActions.put(JsonActionOutputObject.NO_TRACKS_TYPE, noTracksAction);
                }
                else if (trackInfo.isSuppressed()) {
                    noOutputActions.put(JsonActionOutputObject.TRACKS_SUPPRESSED_TYPE, action);
                }
                else if (trackInfo.isMergeTarget()) {
                    // This task will be merged with one that follows.
                    noOutputActions.put(JsonActionOutputObject.TRACKS_MERGED_TYPE, action);
                }
                else {
                    trackCounter.add(media, trackInfo.tracksGroupedByAction().size());
                    addJsonTracks(
                            mediaOutputObject, job, media, action,
                            trackInfo.tracksGroupedByAction(),
                            annotatorsMap);
                }

                if (!trackInfo.isMergeSource()) {
                    // NOTE: If and when we support parallel actions in tasks other then the final
                    // one in a pipeline, this code will need to be updated.
                    prevUnmergedAction = action;
                }
            }
        }
        addMissingTrackInfo(noOutputActions, mediaOutputObject);
    }

    private List<String> getAnnotators(BatchJob job, Media media, int taskIndex) {
        List<String> annotators = new ArrayList<>();

        boolean mergeTarget = taskMergingManager.isMergeTarget(job, media, taskIndex);
        if(mergeTarget) {
            // search subsequent tasks to determine if they are annotators for this task if it's a merge target
            for (int nextTaskIndex = (taskIndex + 1); nextTaskIndex < job.getPipelineElements().getTaskCount(); nextTaskIndex++) {
                Task nextTask = job.getPipelineElements().getTask(nextTaskIndex);

                // for each task, iterate though the actions list and check to see if the action is an annotator
                for (int actionIndex = 0; actionIndex < nextTask.actions().size(); actionIndex++) {
                    String actionName = nextTask.actions().get(actionIndex);
                    // Action action = job.getPipelineElements().getAction(actionName);
                    boolean mergeSource = taskMergingManager.isMergeSource(job, media, nextTaskIndex, actionIndex);

                    // add the action to the list if it's a merge source
                    if (mergeSource) {
                        annotators.add(actionName);
                    }
                }
            }
        }

        return annotators;
    }


    private void addJsonTracks(
            JsonMediaOutputObject mediaOutputObject,
            BatchJob job,
            Media media,
            Action action,
            Multimap<String, Track> tracksGroupedByMergedAction, 
            HashMap<String, List<String>> annotatorsMap) {
        for (var entry : tracksGroupedByMergedAction.asMap().entrySet()) {
            var mergedAction = job.getPipelineElements().getAction(entry.getKey());
            var mergedAlgo = job.getPipelineElements().getAlgorithm(mergedAction.algorithm());
            var jsonAction = new JsonActionOutputObject(
                    mergedAction.name(), mergedAlgo.name());
            int trackIndex = 0;
            for (var track : entry.getValue()) {
                var jsonTrackOutputObject = createTrackOutputObject(
                        track, trackIndex++, mergedAlgo.trackType(), action, media, job);
                jsonAction.getTracks().add(jsonTrackOutputObject);
            }

            if(annotatorsMap.containsKey(mergedAction.name())) {
                jsonAction.getAnnotators().addAll(annotatorsMap.get(mergedAction.name()));
            }

            mediaOutputObject.getTrackTypes()
                    .computeIfAbsent(mergedAlgo.trackType(), k -> new TreeSet<>())
                    .add(jsonAction);
        }
    }


    private static void addDetectionProcessingErrors(
            BatchJob job, JsonMediaOutputObject mediaOutputObject) {
        for (var error : job.getDetectionProcessingErrors()) {
            if (error.getMediaId() != mediaOutputObject.getMediaId()) {
                continue;
            }
            var jsonError = new JsonDetectionProcessingError(
                    error.getStartFrame(),
                    error.getStopFrame(),
                    error.getStartTime(),
                    error.getStopTime(),
                    error.getErrorCode(),
                    error.getErrorMessage());
            var action = job.getPipelineElements().getAction(
                    error.getTaskIndex(), error.getActionIndex());
            mediaOutputObject.getDetectionProcessingErrors()
                .computeIfAbsent(action.name(), k -> new TreeSet<>())
                .add(jsonError);
        }
        if (!mediaOutputObject.getDetectionProcessingErrors().isEmpty()
                && !"COMPLETE".equalsIgnoreCase(mediaOutputObject.getStatus())) {
            mediaOutputObject.setStatus("INCOMPLETE");
        }
    }


    private JsonTrackOutputObject createTrackOutputObject(Track track, int trackIndex, String type,
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
            TextUtils.getTrackUuid(media.getSha256().orElse(""),
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
        JsonPipeline jsonPipeline = new JsonPipeline(pipeline.name(), pipeline.description());
        var censorOperator = censorPropertiesService.createCensorOperator();

        for (String taskName : pipeline.tasks()) {
            Task task = pipelineElements.getTask(taskName);
            JsonTask jsonTask = new JsonTask(getActionType(pipelineElements, task).name(), taskName,
                                             task.description());

            for (String actionName : task.actions()) {
                Action action = pipelineElements.getAction(actionName);
                JsonAction jsonAction = new JsonAction(action.algorithm(), actionName,
                                                       action.description());
                for (ActionProperty property : action.properties()) {
                    jsonAction.getProperties().put(
                            property.name(),
                            censorOperator.apply(property.name(), property.value()));
                }
                jsonTask.getActions().add(jsonAction);
            }

            jsonPipeline.getTasks().add(jsonTask);
        }
        return jsonPipeline;
    }

    private static ActionType getActionType(JobPipelineElements pipeline, Task task) {
        Action action = pipeline.getAction(task.actions().get(0));
        return pipeline.getAlgorithm(action.algorithm()).actionType();
    }


    private static final String[] NO_TRACK_REASON_PRIORITY = {
            JsonActionOutputObject.NO_TRACKS_TYPE,
            JsonActionOutputObject.TRACKS_SUPPRESSED_TYPE,
            JsonActionOutputObject.TRACKS_MERGED_TYPE };

    private static void addMissingTrackInfo(
            Multimap<String, Action> noOutputActions, JsonMediaOutputObject mediaOutputObject) {
        for (var reason : NO_TRACK_REASON_PRIORITY) {
            for (var action : noOutputActions.get(reason)) {
                var actionMissing = mediaOutputObject
                        .getTrackTypes()
                        .values()
                        .stream()
                        .flatMap(Collection::stream)
                        .noneMatch(ja -> action.name().equals(ja.getAction()));
                if (actionMissing) {
                    Optional.ofNullable(mediaOutputObject.getTrackTypes()
                        .computeIfAbsent(reason, k -> {
                            if (JsonActionOutputObject.TRACKS_MERGED_TYPE.equals(k)) {
                                return null;
                            } else {
                                return new TreeSet<>();
                            }
                        }))
                        .ifPresent(set -> set.add(new JsonActionOutputObject(action.name(), action.algorithm())));
                }
            }
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

    private static JsonTiming getTiming(BatchJob job) {
        var actionTimes = job.getPipelineElements()
                .getTaskStreamInOrder()
                .flatMap(job.getPipelineElements()::getActionStreamInOrder)
                // Use distinct to prevent duplicate entries in the unlikely case of the same
                // action being used by more than one stage.
                .distinct()
                .map(a -> new JsonActionTiming(a.name(), job.getProcessingTime(a)))
                .toList();
        return new JsonTiming(job.getTotalProcessingTime(), actionTimes);
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
