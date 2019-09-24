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

package org.mitre.mpf.wfm.camel.operations.detection.artifactextraction;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultMessage;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.ActionType;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.wfm.camel.WfmSplitter;
import org.mitre.mpf.wfm.camel.operations.detection.trackmerging.TrackMergingContext;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.ArtifactExtractionPolicy;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.*;
import java.util.function.Function;

@Component(ArtifactExtractionSplitterImpl.REF)
public class ArtifactExtractionSplitterImpl extends WfmSplitter {
    public static final String REF = "detectionExtractionSplitter";

    private static final Logger LOG = LoggerFactory.getLogger(ArtifactExtractionSplitterImpl.class);

    private final JsonUtils _jsonUtils;

    private final InProgressBatchJobsService _inProgressBatchJobs;

    private final PropertiesUtil _propertiesUtil;

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;


    @Inject
    ArtifactExtractionSplitterImpl(
        JsonUtils jsonUtils,
        InProgressBatchJobsService inProgressBatchJobs,
        PropertiesUtil propertiesUtil,
        AggregateJobPropertiesUtil aggregateJobPropertiesUtil) {
        _jsonUtils = jsonUtils;
        _inProgressBatchJobs = inProgressBatchJobs;
        _propertiesUtil = propertiesUtil;
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
    }


    @Override
    public String getSplitterName() {
        return REF;
    }


    @Override
    public List<Message> wfmSplit(Exchange exchange) {
        TrackMergingContext trackMergingContext = _jsonUtils.deserialize(exchange.getIn().getBody(byte[].class),
                                                                         TrackMergingContext.class);
        BatchJob job = _inProgressBatchJobs.getJob(trackMergingContext.getJobId());

        if (job.isCancelled()) {
            LOG.warn("[Job {}|*|*] Artifact extraction will not be performed because this job has been cancelled.",
                     job.getId());
            return Collections.emptyList();
        }

        Table<Long, Integer, Set<Integer>> mediaAndActionToFrames
                = getFrameNumbersGroupedByMediaAndAction(job, trackMergingContext.getTaskIndex());

        List<Message> messages = new ArrayList<>(mediaAndActionToFrames.size());
        for (long mediaId : mediaAndActionToFrames.rowKeySet()) {
            Map<Integer, Set<Integer>> actionToFrameNumbers = mediaAndActionToFrames.row(mediaId);

            Media media = job.getMedia(mediaId);
            ArtifactExtractionRequest request = new ArtifactExtractionRequest(
                job.getId(),
                mediaId,
                media.getLocalPath().toString(),
                media.getMediaType(),
                trackMergingContext.getTaskIndex(),
                actionToFrameNumbers);

            Message message = new DefaultMessage();
            message.setBody(_jsonUtils.serialize(request));
            messages.add(message);
        }
        return messages;
    }


    private Table<Long, Integer, Set<Integer>> getFrameNumbersGroupedByMediaAndAction(
        BatchJob job, int taskIndex) {

        Table<Long, Integer, Set<Integer>> mediaAndActionToFrames = HashBasedTable.create();

        JobPipelineElements pipelineElements = job.getPipelineElements();
        Task task = pipelineElements.getTask(taskIndex);
        int numPipelineTasks = pipelineElements.getTaskCount();
        // If the user has requested output objects for the last task only, and this is
        // not the last task, then skip extraction for this media. Also return an empty
        // list if this is the second to last task, but the action type of the last task
        // is MARKUP.
        int lastTaskIndex = numPipelineTasks-1;
        ActionType finalActionType = pipelineElements.getAlgorithm(lastTaskIndex, 0).getActionType();
        if (finalActionType == ActionType.MARKUP) {
            lastTaskIndex = lastTaskIndex - 1;
        }
        boolean notLastTask = (taskIndex < lastTaskIndex);

        for (int actionIndex = 0; actionIndex < task.getActions().size(); actionIndex++) {

            for (Media media : job.getMedia()) {
                if (media.isFailed()
                    || (media.getMediaType() != MediaType.IMAGE
                        && media.getMediaType() != MediaType.VIDEO)) {
                    continue;
                }

                ArtifactExtractionPolicy extractionPolicy = getExtractionPolicy(job, media, taskIndex, actionIndex);
                if (extractionPolicy == ArtifactExtractionPolicy.NONE) {
                    continue;
                }

                boolean lastTaskOnly = Boolean.parseBoolean(
                    _aggregateJobPropertiesUtil.getValue("OUTPUT_LAST_TASK_ONLY", job, media));
                if (lastTaskOnly && notLastTask) {
                    LOG.info("[Job {}|*|*] ARTIFACT EXTRACTION IS SKIPPED for pipeline task {} and media {}.", job.getId(), task.getName(), media.getId());
                    continue;
                }

                Collection<Track> tracks
                        = _inProgressBatchJobs.getTracks(job.getId(), media.getId(), taskIndex, actionIndex);
                processTracks(mediaAndActionToFrames, tracks, job, media, taskIndex, actionIndex, extractionPolicy);
            }
        }

        return mediaAndActionToFrames;
    }


    private void processTracks(
        Table<Long, Integer, Set<Integer>> mediaAndActionToFrames,
        Collection<Track> tracks,
        BatchJob job,
        Media media,
        int taskIndex,
        int actionIndex,
        ArtifactExtractionPolicy policy) {

        for (Track track : tracks) {
            switch (policy) {
                case ALL_DETECTIONS:
                    for (Detection detection : track.getDetections()) {
                        addFrame(mediaAndActionToFrames, media.getId(), actionIndex, detection.getMediaOffsetFrame());
                    }
                    break;
                case VISUAL_TYPES_ONLY:
                    if (isNonVisualObjectType(track.getType())) {
                        break;
                    }
                    // fall through
                case ALL_TYPES:
                    processExtractionsInTrack(mediaAndActionToFrames, track, job, media, taskIndex, actionIndex);
            }
        }
    }


    private void processExtractionsInTrack(
        Table<Long, Integer, Set<Integer>> mediaAndActionToFrames,
        Track track,
        BatchJob job,
        Media media,
        int taskIndex,
        int actionIndex) {

        Action thisAction = job.getPipelineElements().getAction(taskIndex, actionIndex);
        int exemplarPlusExtractCount = Integer.parseInt(_aggregateJobPropertiesUtil
                                                        .getValue("ARTIFACT_EXTRACTION_POLICY_EXEMPLAR_FRAME_PLUS",
                                                                  job, media, thisAction));
        if (exemplarPlusExtractCount >= 0) {
            Detection exemplar = track.getExemplar();
            int exemplarFrame = exemplar.getMediaOffsetFrame();
            LOG.debug("Extracting exemplar frame {}", exemplarFrame);
            addFrame(mediaAndActionToFrames, media.getId(), actionIndex, exemplarFrame);
            while (exemplarPlusExtractCount > 0) {
                // before frame: only extract if the frame lies within this track and is
                // not less than 0.
                int beforeIndex = exemplarFrame - exemplarPlusExtractCount;
                if ((beforeIndex >= 0) && (beforeIndex >= track.getStartOffsetFrameInclusive())) {
                    LOG.debug("Extracting before-frame {}", beforeIndex);
                    addFrame(mediaAndActionToFrames, media.getId(), actionIndex, beforeIndex);
                }
                // after frame: only extract if the frame lies within this track and is
                // not greater than the last frame in the media.
                int afterIndex = exemplarFrame + exemplarPlusExtractCount;
                if ((afterIndex <= (media.getLength() - 1)) && (afterIndex <= track.getEndOffsetFrameInclusive())) {
                    LOG.debug("Extracting after-frame {}", afterIndex);
                    addFrame(mediaAndActionToFrames, media.getId(), actionIndex, afterIndex);
                }
                exemplarPlusExtractCount--;
            }
        }

        int firstDetectionFrame = track.getDetections().first().getMediaOffsetFrame();
        int lastDetectionFrame = track.getDetections().last().getMediaOffsetFrame();
        if (Boolean.parseBoolean(_aggregateJobPropertiesUtil
                                 .getValue("ARTIFACT_EXTRACTION_POLICY_FIRST_FRAME",
                                          job, media, thisAction))) {
            LOG.debug("Extracting first frame {}", firstDetectionFrame);
            addFrame(mediaAndActionToFrames, media.getId(), actionIndex, firstDetectionFrame);
        }

        if (Boolean.parseBoolean(_aggregateJobPropertiesUtil
                                 .getValue("ARTIFACT_EXTRACTION_POLICY_LAST_FRAME",
                                          job, media, thisAction))) {
            LOG.debug("Extracting last frame {}", lastDetectionFrame);
            addFrame(mediaAndActionToFrames, media.getId(), actionIndex, lastDetectionFrame);
        }

        if (Boolean.parseBoolean(_aggregateJobPropertiesUtil
                                 .getValue("ARTIFACT_EXTRACTION_POLICY_MIDDLE_FRAME",
                                          job, media, thisAction))) {
            // The goal here is to find the detection in the track that is closest to the "middle"
            // frame. The middle frame is the frame that is equally distant from the start and stop
            // frames, but that frame does not necessarily contain a detection in this track, so we
            // search for the detection in the track that is closest to that middle frame.
            int middleIndex = (firstDetectionFrame + lastDetectionFrame) / 2;
            int smallestDistance = Integer.MAX_VALUE;
            int middleFrame = 0;
            for (Detection detection : track.getDetections()) {
                int distance = Math.abs(detection.getMediaOffsetFrame() - middleIndex);
                if (distance < smallestDistance) {
                    smallestDistance = distance;
                    middleFrame = detection.getMediaOffsetFrame();
                }
            }
            LOG.debug("Extracting middle frame {}", middleFrame);
            addFrame(mediaAndActionToFrames, media.getId(), actionIndex, middleFrame);
        }

        int topConfidenceExtractCount = Integer.parseInt(_aggregateJobPropertiesUtil
                                                         .getValue("ARTIFACT_EXTRACTION_POLICY_TOP_CONFIDENCE_COUNT",
                                                                  job, media, thisAction));
        if (topConfidenceExtractCount > 0) {
            List<Detection> detectionsCopy = new ArrayList<>(track.getDetections());
            // Sort the detections by confidence, then by frame number, if two detections have equal
            // confidence. The sort by confidence is reversed so that the N highest confidence
            // detections are at the start of the list.
            detectionsCopy.sort(
                Comparator.comparing(Detection::getConfidence)
                .reversed()
                .thenComparing(Detection::getMediaOffsetFrame));
            int extractCount = Math.min(topConfidenceExtractCount, detectionsCopy.size());
            for (int i = 0; i < extractCount; i++) {
                LOG.debug("Extracting frame #{} with confidence = {}", detectionsCopy.get(i).getMediaOffsetFrame(),
                          detectionsCopy.get(i).getConfidence());
                addFrame(mediaAndActionToFrames, media.getId(), actionIndex,
                         detectionsCopy.get(i).getMediaOffsetFrame());
            }
        }
    }

    private static void addFrame(Table<Long, Integer, Set<Integer>> mediaAndActionToFrames,
                                 long mediaId, int actionIndex, int frameNumber) {
        Set<Integer> frameNumbers = mediaAndActionToFrames.get(mediaId, actionIndex);
        if (frameNumbers == null) {
            frameNumbers = new HashSet<>();
            mediaAndActionToFrames.put(mediaId, actionIndex, frameNumbers);
        }
        frameNumbers.add(frameNumber);
    }


    private ArtifactExtractionPolicy getExtractionPolicy(BatchJob job, Media media,
                                                         int taskIndex, int actionIndex) {
        Action action = job.getPipelineElements().getAction(taskIndex, actionIndex);
        Function<String, String> combinedProperties
                = _aggregateJobPropertiesUtil.getCombinedProperties(job, media, action);
        String extractionPolicyString = combinedProperties.apply(MpfConstants.ARTIFACT_EXTRACTION_POLICY_PROPERTY);

        ArtifactExtractionPolicy defaultPolicy = _propertiesUtil.getArtifactExtractionPolicy();
        return ArtifactExtractionPolicy.parse(extractionPolicyString, defaultPolicy);
    }


    private static boolean isNonVisualObjectType(String type) {
        return StringUtils.equalsIgnoreCase(type, "MOTION") || StringUtils.equalsIgnoreCase(type, "SPEECH");
    }
}
