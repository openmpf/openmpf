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
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.wfm.camel.WfmSplitter;
import org.mitre.mpf.wfm.camel.operations.detection.trackmerging.TrackMergingContext;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.data.entities.transients.TransientMedia;
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
            BatchJob job, int stageIndex) {

        Table<Long, Integer, Set<Integer>> mediaAndActionToFrames = HashBasedTable.create();
        Task task = job.getTransientPipeline().getTask(stageIndex);

        for (int actionIndex = 0; actionIndex < task.getActions().size(); actionIndex++) {

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
                case VISUAL_EXEMPLARS_ONLY:
                    if (isNonVisualObjectType(track.getType())) {
                        break;
                    }
                    // fall through
                case EXEMPLARS_ONLY:
                    addFrame(mediaAndActionToFrames, media.getId(), actionIndex,
                             track.getExemplar().getMediaOffsetFrame());
                    break;
                case ALL_VISUAL_DETECTIONS:
                    if (isNonVisualObjectType(track.getType())) {
                        break;
                    }
                    // fall through
                case ALL_DETECTIONS:
                    for (Detection detection : track.getDetections()) {
                        addFrame(mediaAndActionToFrames, media.getId(), actionIndex, detection.getMediaOffsetFrame());
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


    private ArtifactExtractionPolicy getExtractionPolicy(BatchJob job, TransientMedia media,
                                                         int stageIndex, int actionIndex) {
        Function<String, String> combinedProperties
                = _aggregateJobPropertiesUtil.getCombinedProperties(job, media.getId(), stageIndex, actionIndex);
        String extractionPolicyString = combinedProperties.apply(MpfConstants.ARTIFACT_EXTRACTION_POLICY_PROPERTY);

        ArtifactExtractionPolicy defaultPolicy = _propertiesUtil.getArtifactExtractionPolicy();
        return ArtifactExtractionPolicy.parse(extractionPolicyString, defaultPolicy);
    }


    private static boolean isNonVisualObjectType(String type) {
        return StringUtils.equalsIgnoreCase(type, "MOTION") || StringUtils.equalsIgnoreCase(type, "SPEECH");
    }
}
