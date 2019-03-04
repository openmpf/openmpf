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


    private static void processTracks(
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
                case VISUAL_ONLY:
                    if (isNonVisualObjectType(track.getType())) {
                        break;
                    }
                    if (propertiesUtil.getArtifactExtractionPolicyExemplarFramePlus() >= 0) {
                        Detection exemplar = track.getExemplar();
                        int exemplar_frame = exemplar.getMediaOffsetFrame();
                        log.debug("Extracting frame {}", exemplar_frame);
                        addFrame(mediaAndActionToFrames, media.getId(), actionIndex, exemplar_frame);
                        int extract_count = propertiesUtil.getArtifactExtractionPolicyExemplarFramePlus();
                        while (extract_count > 0) {
                            if ((exemplar_frame - extract_count) >= 0) {
                                log.debug("Extracting frame {}", exemplar_frame-extract_count);
                                addFrame(mediaAndActionToFrames, media.getId(), actionIndex, exemplar_frame-extract_count);
                            }
                            if ((exemplar_frame + extract_count) <= (media.getLength() - 1)) {
                                log.debug("Extracting frame {}", exemplar_frame+extract_count);
                                addFrame(mediaAndActionToFrames, media.getId(), actionIndex, exemplar_frame+extract_count);
                            }
                            extract_count--;
                        }
                    }
                    if (propertiesUtil.isArtifactExtractionPolicyFirstFrame()) {
                        log.debug("Extracting frame {}", track.getDetections().first().getMediaOffsetFrame());
                        addFrame(mediaAndActionToFrames, media.getId(), actionIndex, track.getDetections().first().getMediaOffsetFrame());
                    }
                    if (propertiesUtil.isArtifactExtractionPolicyLastFrame()) {
                        log.debug("Extracting frame {}", track.getDetections().last().getMediaOffsetFrame());
                        addFrame(mediaAndActionToFrames, media.getId(), actionIndex, track.getDetections().last().getMediaOffsetFrame());
                    }
                    if (propertiesUtil.isArtifactExtractionPolicyMiddleFrame()) {
                        // The goal here is to find the detection in the track that is closest to the "middle"
                        // frame. The middle frame is the frame that is equally distant from the start and stop
                        // frames, but that frame does not necessarily contain a detection in this track, so we
                        // search for the detection in the track that is closest to that middle frame.
                        int middle_index = (track.getDetections().first().getMediaOffsetFrame() + track.getDetections().last().getMediaOffsetFrame())/2;
                        int smallest_distance = Integer.MAX_VALUE;
                        int middle_frame = 0;
                        for (Detection detection : track.getDetections()) {
                            int distance = Math.abs(detection.getMediaOffsetFrame() - middle_index);
                            if (distance < smallest_distance) {
                                smallest_distance = distance;
                                middle_frame = detection.getMediaOffsetFrame();
                            }
                        }
                        log.debug("Extracting frame {}", middle_frame);
                        addFrame(mediaAndActionToFrames, media.getId(), actionIndex, middle_frame);
                    }
                    if (propertiesUtil.getArtifactExtractionPolicyTopConfidenceCount() > 0) {
                        List<Detection> detections_copy = new ArrayList(track.getDetections());
                        // Sort the detections by confidence, then by frame number, if two detections have equal
                        // confidence. The sort by confidence is reversed so that the N highest confidence
                        // detections are at the start of the list.
                        detections_copy.sort(Comparator.comparing(Detection::getConfidence).reversed().thenComparing(Detection::getMediaOffsetFrame));
                        for (int i = 0; i < propertiesUtil.getArtifactExtractionPolicyTopConfidenceCount(); i++) {
                            log.debug("frame #{} confidence = {}", detections_copy.get(i).getMediaOffsetFrame(), detections_copy.get(i).getConfidence());
                            addFrame(mediaAndActionToFrames, media.getId(), actionIndex, detections_copy.get(i).getMediaOffsetFrame());
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
        return (propertiesUtil.getArtifactExtractionNonvisualTypesList().contains(type));
    }

}
