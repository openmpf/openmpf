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

package org.mitre.mpf.wfm.camel.operations.detection.artifactextraction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultMessage;
import org.mitre.mpf.interop.JsonDetectionOutputObject;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.ActionType;
import org.mitre.mpf.wfm.camel.WfmLocalSplitter;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.TrackCache;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.ArtifactExtractionPolicy;
import org.mitre.mpf.wfm.enums.ArtifactExtractionStatus;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.service.TaskMergingManager;
import org.mitre.mpf.wfm.util.TopQualitySelectionUtil;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component(ArtifactExtractionSplitterImpl.REF)
public class ArtifactExtractionSplitterImpl extends WfmLocalSplitter {
    public static final String REF = "detectionExtractionSplitter";

    private static final Logger LOG = LoggerFactory.getLogger(ArtifactExtractionSplitterImpl.class);

    private final InProgressBatchJobsService _inProgressBatchJobs;

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;

    private final TaskMergingManager _taskMergingManager;

    @Inject
    ArtifactExtractionSplitterImpl(
            InProgressBatchJobsService inProgressBatchJobs,
            AggregateJobPropertiesUtil aggregateJobPropertiesUtil,
            TaskMergingManager taskMergingManager) {
        super(inProgressBatchJobs);
        _inProgressBatchJobs = inProgressBatchJobs;
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
        _taskMergingManager = taskMergingManager;
    }

    @Override
    public String getSplitterName() {
        return REF;
    }

    @Override
    public List<Message> wfmSplit(Exchange exchange) {
        var trackCache = exchange.getIn().getBody(TrackCache.class);
        BatchJob job = _inProgressBatchJobs.getJob(trackCache.getJobId());

        if (job.isCancelled()) {
            LOG.warn("Artifact extraction will not be performed because this job has been cancelled.");
            return Collections.emptyList();
        }

        int taskIndex = trackCache.getTaskIndex();
        JobPipelineElements pipelineElements = job.getPipelineElements();
        int numPipelineTasks = pipelineElements.getTaskCount();
        int lastTaskIndex = numPipelineTasks - 1;
        ActionType finalActionType = pipelineElements.getAlgorithm(lastTaskIndex, 0).actionType();
        if (finalActionType == ActionType.MARKUP) {
            lastTaskIndex = lastTaskIndex - 1;
        }
        boolean notLastTask = (taskIndex < lastTaskIndex);

        List<Message> messages = new ArrayList<>();
        for (Media media : job.getMedia()) {
            if (!media.matchesType(MediaType.VIDEO, MediaType.IMAGE)) {
                continue;
            }

            // If the user has requested output objects for the last task only, and this is
            // not the last task, then skip extraction for this media. Also return an empty
            // list if this is the second to last task, but the action type of the last task
            // is MARKUP.
            // The OUTPUT_LAST_TASK_ONLY property only makes sense to be set at the job or
            // media level.
            boolean lastTaskOnly = Boolean.parseBoolean(
                    _aggregateJobPropertiesUtil.getValue(MpfConstants.OUTPUT_LAST_TASK_ONLY_PROPERTY, job, media));
            if (lastTaskOnly && notLastTask) {
                LOG.info("ARTIFACT EXTRACTION IS SKIPPED for pipeline task {} and media {}" +
                                " due to {} property.",
                        pipelineElements.getTask(taskIndex).name(), media.getId(),
                        MpfConstants.OUTPUT_LAST_TASK_ONLY_PROPERTY);
                continue;
            }

            // If the user has requested that this task be merged with one that follows, then skip artifact extraction.
            // Artifact extraction will be performed for the next task this one is merged with.
            if (_taskMergingManager.isMergeTarget(job, media, taskIndex)) {
                LOG.info("ARTIFACT EXTRACTION IS SKIPPED for pipeline task {} and media {}" +
                                " due to being merged with a following task.",
                        pipelineElements.getTask(taskIndex).name(), media.getId());
                continue;
            }

            for (int actionIndex = 0; actionIndex < pipelineElements.getTask(taskIndex).actions().size();
                 actionIndex++) {

                Action action = pipelineElements.getAction(taskIndex, actionIndex);
                ArtifactExtractionPolicy extractionPolicy = getExtractionPolicy(job, media, action);
                LOG.debug("Artifact extraction policy = {}", extractionPolicy);
                if (extractionPolicy == ArtifactExtractionPolicy.NONE) {
                    continue;
                }

                boolean cropping = Boolean.parseBoolean(_aggregateJobPropertiesUtil
                                   .getValue(MpfConstants.ARTIFACT_EXTRACTION_POLICY_CROPPING, job, media, action));
                boolean isRotationFillBlack = isRotationFillBlack(job, media, action);
                var request = new ArtifactExtractionRequest(
                        job.getId(),
                        media.getId(),
                        media.getProcessingPath().toString(),
                        media.getType().orElseThrow(),
                        media.getMetadata(),
                        taskIndex,
                        actionIndex,
                        cropping,
                        isRotationFillBlack,
                        trackCache);

                var tracks = trackCache.getTracks(media.getId(), actionIndex);

                LOG.debug("Action {} has {} tracks", actionIndex, tracks.size());
                var updatedTracks = processTracks(
                        request, tracks, job, media, action, actionIndex, extractionPolicy);
                trackCache.updateTracks(media.getId(), actionIndex, updatedTracks);

                Message message = new DefaultMessage(exchange.getContext());
                message.setBody(request);
                messages.add(message);
            }
        }
        return messages;
    }

    private void putInExtractableDetectionsMap(Integer trackIndex, SortedSet<JsonDetectionOutputObject> detections,
                                     SortedMap<Integer, Map<Integer, JsonDetectionOutputObject>> map) {
        for (JsonDetectionOutputObject detection : detections) {
            Integer frameNumber = detection.getOffsetFrame();
            map.computeIfAbsent(frameNumber, k -> new TreeMap<>());
            map.get(frameNumber).put(trackIndex, detection);
        }
    }

    private SortedSet<Track> processTracks(
            ArtifactExtractionRequest request, SortedSet<Track> tracks, BatchJob job, Media media,
            Action action, int actionIndex, ArtifactExtractionPolicy policy) {
        if (policy == ArtifactExtractionPolicy.VISUAL_TYPES_ONLY) {
            var algo = job.getPipelineElements().getAlgorithm(action.algorithm());
            if (_aggregateJobPropertiesUtil.isNonVisualObjectType(algo.trackType())) {
                return tracks;
            }
        }

        int trackIndex = 0;
        SortedMap<Integer, Map<Integer, JsonDetectionOutputObject>> extractableDetectionsMap = request.getExtractionsMap();
        for (Track track : tracks) {

            switch (policy) {
                case ALL_DETECTIONS: {
                    SortedSet<JsonDetectionOutputObject> extractableDetections = track.getDetections().stream()
                            .map(d -> createExtractableDetection(d))
                            .collect(Collectors.toCollection(TreeSet::new));

                    track.setArtifactExtractionTrackIndex(trackIndex);
                    putInExtractableDetectionsMap(trackIndex, extractableDetections, extractableDetectionsMap);
                    break;
                }
                case VISUAL_TYPES_ONLY:
                case ALL_TYPES: {
                    SortedSet<JsonDetectionOutputObject> extractableDetections = processExtractionsInTrack(job, track, media, action,
                            actionIndex);

                    track.setArtifactExtractionTrackIndex(trackIndex);
                    putInExtractableDetectionsMap(trackIndex, extractableDetections, extractableDetectionsMap);
                    break;
                }
                default:
            }

            // If we are not going to crop extracted artifacts, then all we need to do is collect frame numbers; the trackIndex is
            // a don't care. This simplifies the process of setting the artifact extraction status later.
            if (request.getCroppingFlag()) trackIndex++;
        }
        return tracks;
    }

    private SortedSet<JsonDetectionOutputObject> processExtractionsInTrack(BatchJob job, Track track, Media media,
            Action action, int actionIndex) {

        LOG.debug("Preparing extractions for action {}", actionIndex);
        List<Detection> sortedDetections = new ArrayList<>(track.getDetections());
        SortedSet<Integer> framesToExtract = new TreeSet<>();

        String exemplarPlusCountProp = _aggregateJobPropertiesUtil
                .getValue(MpfConstants.ARTIFACT_EXTRACTION_POLICY_EXEMPLAR_FRAME_PLUS_PROPERTY, job, media, action);
        int exemplarPlusCount = job.getSystemPropertiesSnapshot().getArtifactExtractionPolicyExemplarFramePlus();
        // Check that the string is parsable in case the user entered a bad integer
        // value
        try {
            exemplarPlusCount = Integer.parseInt(exemplarPlusCountProp);
        } catch (NumberFormatException e) {
            LOG.warn("Attempted to parse {} value of '{}' but encountered an exception. Defaulting to '{}'.",
                    MpfConstants.ARTIFACT_EXTRACTION_POLICY_EXEMPLAR_FRAME_PLUS_PROPERTY, exemplarPlusCountProp,
                    exemplarPlusCount);
        }

        if (exemplarPlusCount >= 0) {
            Detection exemplar = track.getExemplar();
            int exemplarFrame = exemplar.getMediaOffsetFrame();
            LOG.debug("Will extract exemplar frame {}", exemplarFrame);
            framesToExtract.add(exemplarFrame);
            int exemplarIndex = sortedDetections.indexOf(exemplar);
            while (exemplarPlusCount > 0) {
                // before frame: Find the detections in this track that are previous to the
                // exemplar.
                int beforeIndex = exemplarIndex - exemplarPlusCount;
                if (beforeIndex >= 0) {
                    int beforeFrame = sortedDetections.get(beforeIndex).getMediaOffsetFrame();
                    LOG.debug("Will extract before-frame {}", beforeFrame);
                    framesToExtract.add(beforeFrame);
                }
                // after frame: Find the detections in this track that are after the exemplar.
                int afterIndex = exemplarIndex + exemplarPlusCount;
                if (afterIndex < sortedDetections.size()) {
                    int afterFrame = sortedDetections.get(afterIndex).getMediaOffsetFrame();
                    LOG.debug("Will extract after-frame {}", afterFrame);
                    framesToExtract.add(afterFrame);
                }
                exemplarPlusCount--;
            }
        }

        int firstDetectionFrame = track.getDetections().first().getMediaOffsetFrame();
        int lastDetectionFrame = track.getDetections().last().getMediaOffsetFrame();
        if (Boolean.parseBoolean(_aggregateJobPropertiesUtil
                .getValue(MpfConstants.ARTIFACT_EXTRACTION_POLICY_FIRST_FRAME_PROPERTY, job, media, action))) {
            LOG.debug("Will extract first frame {}", firstDetectionFrame);
            framesToExtract.add(firstDetectionFrame);
        }

        if (Boolean.parseBoolean(_aggregateJobPropertiesUtil
                .getValue(MpfConstants.ARTIFACT_EXTRACTION_POLICY_LAST_FRAME_PROPERTY, job, media, action))) {
            LOG.debug("Will extract last frame {}", lastDetectionFrame);
            framesToExtract.add(lastDetectionFrame);
        }

        if (Boolean.parseBoolean(_aggregateJobPropertiesUtil
                .getValue(MpfConstants.ARTIFACT_EXTRACTION_POLICY_MIDDLE_FRAME_PROPERTY, job, media, action))) {
            // The goal here is to find the detection in the track that is closest to the
            // "middle" frame. The middle frame is the frame that is equally distant from the start and stop
            // frames, but that frame does not necessarily contain a detection in this track, so we
            // search for the detection in the track that is closest to that middle frame.
            double middleIndex = (firstDetectionFrame + lastDetectionFrame) / 2.0;
            double smallestDistance = Double.MAX_VALUE;
            int middleFrame = 0;
            for (Detection detection : track.getDetections()) {
                double distance = detection.getMediaOffsetFrame() - middleIndex;
                double absDistance = Math.abs(distance);
                if (absDistance < smallestDistance) {
                    smallestDistance = absDistance;
                    middleFrame = detection.getMediaOffsetFrame();
                }
                if (distance >= 0.0) {
                    // Detections are sorted in increasing order, so if the difference has
                    // turned positive, we have passed the minimum and don't need to look further.
                    break;
                }
            }
            LOG.debug("Will extract middle frame {}", middleFrame);
            framesToExtract.add(middleFrame);
        }

        int topQualityCount = job.getSystemPropertiesSnapshot().getArtifactExtractionPolicyTopQualityCount();
        String topQualityCountProp = _aggregateJobPropertiesUtil
                    .getValue(MpfConstants.ARTIFACT_EXTRACTION_POLICY_TOP_QUALITY_COUNT_PROPERTY, job, media, action);
        try {
            topQualityCount = Integer.parseInt(topQualityCountProp);
        } catch (NumberFormatException e) {
            LOG.warn("Attempted to parse {} value of '{}' but encountered an exception. Defaulting to '{}'.",
                MpfConstants.ARTIFACT_EXTRACTION_POLICY_TOP_QUALITY_COUNT_PROPERTY, topQualityCountProp,
                topQualityCount);
        }
        if (topQualityCount > 0) {
            String topQualitySelectionProp = _aggregateJobPropertiesUtil.getQualitySelectionProp(job, media, action);
            var topQualityDetections = TopQualitySelectionUtil.getTopQualityDetections(
                                                      sortedDetections, topQualityCount, topQualitySelectionProp);
            for (var detection : topQualityDetections) {
                LOG.debug("Will extract frame #{} with confidence = {}",
                    detection.getMediaOffsetFrame(),
                    detection.getConfidence());
                framesToExtract.add(detection.getMediaOffsetFrame());
            }
        }

        String bestDetectionPropNames = _aggregateJobPropertiesUtil
                .getValue(MpfConstants.ARTIFACT_EXTRACTION_POLICY_DETECTION_PROP_NAMES, job, media, action);
        var propNameList = TextUtils.parseListFromString(bestDetectionPropNames);
        propNameList = TextUtils.trimAndUpper(propNameList, Collectors.toList());
        for (Detection detection : track.getDetections()) {
            for (String p : propNameList) {
                if (detection.getDetectionProperties().containsKey(p)) {
                    LOG.info("Will extract detection in frame {} with property {}", detection.getMediaOffsetFrame(), p);
                    framesToExtract.add(detection.getMediaOffsetFrame());
                    break;
                }
            }
        }

        // For each frame to be extracted, set the artifact extraction status in the original detection and convert it to a
        // JsonDetectionOutputObject
        SortedSet<JsonDetectionOutputObject> detections = track.getDetections().stream()
                .filter(d -> framesToExtract.contains(d.getMediaOffsetFrame()))
                .map(d -> createExtractableDetection(d))
                .collect(Collectors.toCollection(TreeSet::new));
        return detections;

    }

    private static JsonDetectionOutputObject createDetectionOutputObject(Detection detection) {
        return new JsonDetectionOutputObject(detection.getX(),
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

    private static JsonDetectionOutputObject createExtractableDetection(Detection detection) {
        detection.setArtifactExtractionStatus(ArtifactExtractionStatus.REQUESTED);
        return createDetectionOutputObject(detection);
    }

    private ArtifactExtractionPolicy getExtractionPolicy(BatchJob job, Media media, Action action) {
        Function<String, String> combinedProperties = _aggregateJobPropertiesUtil.getCombinedProperties(job, media,
                action);
        String extractionPolicyString = combinedProperties.apply(MpfConstants.ARTIFACT_EXTRACTION_POLICY_PROPERTY);

        ArtifactExtractionPolicy defaultPolicy = job.getSystemPropertiesSnapshot().getDefaultArtifactExtractionPolicy();
        return ArtifactExtractionPolicy.parse(extractionPolicyString, defaultPolicy);
    }


    private boolean isRotationFillBlack(BatchJob job, Media media, Action action) {
        String fillColor = _aggregateJobPropertiesUtil.getValue(
                "ROTATION_FILL_COLOR", job, media, action);
        return !"WHITE".equalsIgnoreCase(fillColor);
    }
}
