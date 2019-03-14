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
import com.google.common.collect.ImmutableSortedSet;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultMessage;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.wfm.camel.WfmSplitter;
import org.mitre.mpf.wfm.camel.operations.detection.trackmerging.TrackMergingContext;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.transients.*;
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
import java.lang.Float;

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
        TransientJob job = _inProgressBatchJobs.getJob(trackMergingContext.getJobId());

        if (job.isCancelled()) {
            LOG.warn("[Job {}|*|*] Artifact extraction will not be performed because this job has been cancelled.",
                     job.getId());
            return Collections.emptyList();
        }

        Table<Long, Integer, Set<Integer>> mediaAndActionToFrames
                = getFrameNumbersGroupedByMediaAndAction(job, trackMergingContext.getStageIndex());

        List<Message> messages = new ArrayList<>(mediaAndActionToFrames.size());
        for (long mediaId : mediaAndActionToFrames.rowKeySet()) {
            Map<Integer, Set<Integer>> actionToFrameNumbers = mediaAndActionToFrames.row(mediaId);

            TransientMedia media = job.getMedia(mediaId);
            ArtifactExtractionRequest request = new ArtifactExtractionRequest(
                    job.getId(),
                    mediaId,
                    media.getLocalPath().toString(),
                    media.getMediaType(),
                    trackMergingContext.getStageIndex(),
                    actionToFrameNumbers);

            Message message = new DefaultMessage();
            message.setBody(_jsonUtils.serialize(request));
            messages.add(message);
        }
        return messages;
    }


    private Table<Long, Integer, Set<Integer>> getFrameNumbersGroupedByMediaAndAction(
            TransientJob job, int stageIndex) {

        Table<Long, Integer, Set<Integer>> mediaAndActionToFrames = HashBasedTable.create();
        TransientStage stage = job.getPipeline().getStages().get(stageIndex);

        for (int actionIndex = 0; actionIndex < stage.getActions().size(); actionIndex++) {

            for (TransientMedia media : job.getMedia()) {
                if (media.isFailed()
                        || (media.getMediaType() != MediaType.IMAGE
                            && media.getMediaType() != MediaType.VIDEO)) {
                    continue;
                }

                ArtifactExtractionPolicy extractionPolicy = getExtractionPolicy(job, media, stageIndex, actionIndex);
                if (extractionPolicy == ArtifactExtractionPolicy.NONE) {
                    continue;
                }

                Collection<Track> tracks
                        = _inProgressBatchJobs.getTracks(job.getId(), media.getId(), stageIndex, actionIndex);
                processTracks(mediaAndActionToFrames, tracks, media, actionIndex, extractionPolicy);
            }
        }

        return mediaAndActionToFrames;
    }


    private void processTracks(
            Table<Long, Integer, Set<Integer>> mediaAndActionToFrames,
            Iterable<Track> tracks,
            TransientMedia media,
            int actionIndex,
            ArtifactExtractionPolicy policy) {

        for (Track track : tracks) {
            switch (policy) {
                case ALL_FRAMES:
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
                    if (_propertiesUtil.getArtifactExtractionPolicyExemplarFramePlus() >= 0) {
                        Detection exemplar = track.getExemplar();
                        int exemplarFrame = exemplar.getMediaOffsetFrame();
                        LOG.info("Extracting exemplar frame {}", exemplarFrame);
                        addFrame(mediaAndActionToFrames, media.getId(), actionIndex, exemplarFrame);
                        int extractCount = _propertiesUtil.getArtifactExtractionPolicyExemplarFramePlus();
                        while (extractCount > 0) {
                            // before frame: only extract if the frame lies within this track and is not less than 0.
                            if (((exemplarFrame - extractCount) >= 0) &&
                                ((exemplarFrame - extractCount) >= track.getStartOffsetFrameInclusive())) {
                                LOG.info("Extracting before-frame {}", exemplarFrame-extractCount);
                                addFrame(mediaAndActionToFrames, media.getId(), actionIndex, exemplarFrame-extractCount);
                            }
                            // after frame: only extract if the frame lies within this track and is not greater than the
                            // last frame in the media.
                            if (((exemplarFrame + extractCount) <= (media.getLength() - 1)) &&
                                ((exemplarFrame + extractCount) <= track.getEndOffsetFrameInclusive())) {
                                LOG.info("Extracting after-frame {}", exemplarFrame+extractCount);
                                addFrame(mediaAndActionToFrames, media.getId(), actionIndex, exemplarFrame+extractCount);
                            }
                            extractCount--;
                        }
                    }
                    if (_propertiesUtil.isArtifactExtractionPolicyFirstFrame()) {
                        LOG.debug("Extracting first frame {}", track.getDetections().first().getMediaOffsetFrame());
                        addFrame(mediaAndActionToFrames, media.getId(), actionIndex, track.getDetections().first().getMediaOffsetFrame());
                    }
                    if (_propertiesUtil.isArtifactExtractionPolicyLastFrame()) {
                        LOG.debug("Extracting last frame {}", track.getDetections().last().getMediaOffsetFrame());
                        addFrame(mediaAndActionToFrames, media.getId(), actionIndex, track.getDetections().last().getMediaOffsetFrame());
                    }
                    if (_propertiesUtil.isArtifactExtractionPolicyMiddleFrame()) {
                        // The goal here is to find the detection in the track that is closest to the "middle"
                        // frame. The middle frame is the frame that is equally distant from the start and stop
                        // frames, but that frame does not necessarily contain a detection in this track, so we
                        // search for the detection in the track that is closest to that middle frame.
                        int middleIndex = (track.getDetections().first().getMediaOffsetFrame() + track.getDetections().last().getMediaOffsetFrame())/2;
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
                    if (_propertiesUtil.getArtifactExtractionPolicyTopConfidenceCount() > 0) {
                        List<Detection> detectionsCopy = new ArrayList<>(track.getDetections());
                        // Sort the detections by confidence, then by frame number, if two detections have equal
                        // confidence. The sort by confidence is reversed so that the N highest confidence
                        // detections are at the start of the list.
                        detectionsCopy.sort(Comparator.comparing(Detection::getConfidence).reversed().thenComparing(Detection::getMediaOffsetFrame));
                        for (int i = 0; i < _propertiesUtil.getArtifactExtractionPolicyTopConfidenceCount(); i++) {
                            LOG.info("frame #{} confidence = {}", detectionsCopy.get(i).getMediaOffsetFrame(), detectionsCopy.get(i).getConfidence());
                            addFrame(mediaAndActionToFrames, media.getId(), actionIndex, detectionsCopy.get(i).getMediaOffsetFrame());
                        }
                    }
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


    private ArtifactExtractionPolicy getExtractionPolicy(TransientJob job, TransientMedia media,
                                                         int stageIndex, int actionIndex) {
        Function<String, String> combinedProperties
                = _aggregateJobPropertiesUtil.getCombinedProperties(job, media.getId(), stageIndex, actionIndex);
        String extractionPolicyString = combinedProperties.apply(MpfConstants.ARTIFACT_EXTRACTION_POLICY_PROPERTY);

        ArtifactExtractionPolicy defaultPolicy = _propertiesUtil.getArtifactExtractionPolicy();
        return ArtifactExtractionPolicy.parse(extractionPolicyString, defaultPolicy);
    }


    private boolean isNonVisualObjectType(String type) {
        for ( String propType : _propertiesUtil.getArtifactExtractionNonvisualTypesList() ) {
            if (StringUtils.equalsIgnoreCase(type, propType)) return true;
        }
        return false;
    }

}
