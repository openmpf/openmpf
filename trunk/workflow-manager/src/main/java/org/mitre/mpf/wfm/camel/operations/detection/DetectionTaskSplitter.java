/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.camel.operations.detection;

import org.apache.camel.Message;
import org.apache.commons.lang3.StringUtils;
import org.javasimon.aop.Monitored;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.ActionType;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.buffers.AlgorithmPropertyProtocolBuffer;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.SystemPropertiesSnapshot;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.*;
import org.mitre.mpf.wfm.segmenting.MediaSegmenter;
import org.mitre.mpf.wfm.segmenting.SegmentingPlan;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.*;

// DetectionSplitter will take in Job and Task, breaking them into manageable work units for the Components

@Component
@Monitored
public class DetectionTaskSplitter {

    private static final Logger log = LoggerFactory.getLogger(DetectionTaskSplitter.class);

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;
    private final InProgressBatchJobsService _inProgressBatchJobs;
    private final MediaSegmenter _imageMediaSegmenter;
    private final MediaSegmenter _videoMediaSegmenter;
    private final MediaSegmenter _audioMediaSegmenter;
    private final MediaSegmenter _defaultMediaSegmenter;

    @Inject
    public DetectionTaskSplitter(AggregateJobPropertiesUtil aggregateJobPropertiesUtil,
                                 InProgressBatchJobsService inProgressBatchJobs,
                                 MediaSegmenter imageMediaSegmenter,
                                 MediaSegmenter videoMediaSegmenter,
                                 MediaSegmenter audioMediaSegmenter,
                                 MediaSegmenter defaultMediaSegmenter)
    {
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
        _inProgressBatchJobs = inProgressBatchJobs;
        _imageMediaSegmenter = imageMediaSegmenter;
        _videoMediaSegmenter = videoMediaSegmenter;
        _audioMediaSegmenter = audioMediaSegmenter;
        _defaultMediaSegmenter = defaultMediaSegmenter;
    }

    public List<Message> performSplit(BatchJob job, Task task) {
        List<Message> messages = new ArrayList<>();

        for (Media media : job.getMedia()) { // this may include derivative media
            try {
                if (media.isFailed()) {
                    // If a media is in a failed state (it couldn't be retrieved, it couldn't be inspected, etc.), do nothing with it.
                    log.warn("Skipping Media #{} - it is in an error state.", media.getId());
                    continue;
                }

                int lastProcessedTaskForMedia = getLastProcessedTaskIndex(job, media);
                boolean isFirstDetectionTaskForMedia = lastProcessedTaskForMedia == -1;

                // If this is the first detection task in the pipeline, we should segment the entire media for detection.
                // If this is not the first detection task, we should build segments based off of the previous tasks's
                // tracks. Note that the MediaRanges created for these Tracks use the
                // non-feed-forward version of MediaSegmenter.createRangesForTracks
                SortedSet<Track> previousTracks;
                if (isFirstDetectionTaskForMedia) {
                    previousTracks = Collections.emptySortedSet();
                } else {
                    // Get the tracks for the last task that was processed for this media.
                    previousTracks = _inProgressBatchJobs.getTracks(
                            job.getId(), media.getId(),
                            lastProcessedTaskForMedia, 0);
                }

                // Iterate through each of the actions and segment the media using the properties provided in that action.
                for (int actionIndex = 0; actionIndex < task.getActions().size(); actionIndex++) {

                    String actionName = task.getActions().get(actionIndex);
                    Action action = job.getPipelineElements().getAction(actionName);

                    var combinedProperties = new HashMap<>(
                            _aggregateJobPropertiesUtil.getPropertyMap(job, media, action));

                    if (!_aggregateJobPropertiesUtil.actionAppliesToMedia(media, combinedProperties)) {
                        continue;
                    }

                    // Segmenting plan is only used by the VideoMediaSegmenter,
                    // so only create the DetectionContext to include the segmenting plan for jobs with video media.
                    SegmentingPlan segmentingPlan = null;
                    if (media.matchesType(MediaType.VIDEO)) {
                        // Note that single-frame gifs are treated like videos, but have no native frame rate
                        double fps = 1.0;
                        String fpsFromMetadata = media.getMetadata("FPS");
                        if (fpsFromMetadata != null) {
                            fps = Double.valueOf(fpsFromMetadata);
                        }

                        String calcframeInterval = _aggregateJobPropertiesUtil.calculateFrameInterval(
                                action, job, media,
                                job.getSystemPropertiesSnapshot().getSamplingInterval(),
                                job.getSystemPropertiesSnapshot().getFrameRateCap(), fps);
                        combinedProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, calcframeInterval);

                        segmentingPlan = createSegmentingPlan(
                                job.getSystemPropertiesSnapshot(), combinedProperties, media);
                    }

                    List<AlgorithmPropertyProtocolBuffer.AlgorithmProperty> algorithmProperties
                            = convertPropertiesMapToAlgorithmPropertiesList(combinedProperties);

                    DetectionContext detectionContext = new DetectionContext(
                            job.getId(),
                            job.getCurrentTaskIndex(),
                            task.getName(),
                            actionIndex,
                            action.getName(),
                            isFirstDetectionTaskForMedia,
                            algorithmProperties,
                            previousTracks,
                            segmentingPlan);

                    // get detection request messages from ActiveMQ
                    List<Message> detectionRequestMessages = createDetectionRequestMessages(media, detectionContext);

                    ActionType actionType = job.getPipelineElements()
                            .getAlgorithm(action.getAlgorithm())
                            .getActionType();
                    for (Message message : detectionRequestMessages) {
                        message.setHeader(MpfHeaders.RECIPIENT_QUEUE,
                                String.format("jms:MPF.%s_%s_REQUEST",
                                        actionType,
                                        action.getAlgorithm()));
                        message.setHeader(MpfHeaders.JMS_REPLY_TO,
                                StringUtils.replace(MpfEndpoints.COMPLETED_DETECTIONS, "jms:", ""));
                        media.getType().ifPresent(
                                mt -> message.setHeader(MpfHeaders.MEDIA_TYPE, mt.toString()));
                    }
                    messages.addAll(detectionRequestMessages);
                    log.debug("Created {} work units for Media #{}.",
                            detectionRequestMessages.size(), media.getId());
                }
            } catch (WfmProcessingException e) {
                _inProgressBatchJobs.addError(job.getId(), media.getId(), IssueCodes.OTHER,
                                             e.getMessage());
            }
        }

        return messages;
    }


    /**
     * Translates a collection of properties into a collection of AlgorithmProperty ProtoBuf messages.
     * If the input is null or empty, an empty collection is returned.
     */
    private static List<AlgorithmPropertyProtocolBuffer.AlgorithmProperty>
    convertPropertiesMapToAlgorithmPropertiesList(Map<String, String> propertyMessages) {

        if (propertyMessages == null || propertyMessages.isEmpty()) {
            return new ArrayList<>(0);
        }
        else {
            List<AlgorithmPropertyProtocolBuffer.AlgorithmProperty> algorithmProperties
                    = new ArrayList<>(propertyMessages.size());
            for (Map.Entry<String, String> entry : propertyMessages.entrySet()) {
                algorithmProperties.add(AlgorithmPropertyProtocolBuffer.AlgorithmProperty.newBuilder()
                                                .setPropertyName(entry.getKey())
                                                .setPropertyValue(entry.getValue())
                                                .build());
            }
            return algorithmProperties;
        }
    }

    private List<Message> createDetectionRequestMessages(Media media, DetectionContext detectionContext) {
        MediaSegmenter segmenter = getSegmenter(media.getType().orElse(MediaType.UNKNOWN));
        return segmenter.createDetectionRequestMessages(media, detectionContext);
    }

    private MediaSegmenter getSegmenter(MediaType mediaType) {
        return switch (mediaType) {
            case IMAGE -> _imageMediaSegmenter;
            case VIDEO -> _videoMediaSegmenter;
            case AUDIO -> _audioMediaSegmenter;
            default -> _defaultMediaSegmenter;
        };
    }

    /**
     * Create the segmenting plan using the properties defined for the sub-job.
     * @param systemPropertiesSnapshot contains detection system properties whose values were in effect when the job was created (will be used as system property default values)
     * @param properties properties defined for the sub-job
     * @return segmenting plan for this sub-job
     */
    private static SegmentingPlan createSegmentingPlan(
            SystemPropertiesSnapshot systemPropertiesSnapshot, Map<String, String> properties,
            Media media) {
        int samplingInterval = tryParseIntProperty(
                MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, properties,
                systemPropertiesSnapshot.getSamplingInterval());
        if (samplingInterval < 1) {
            samplingInterval = systemPropertiesSnapshot.getSamplingInterval(); // get FRAME_INTERVAL system property
            log.warn("'{}' is not an acceptable {} value. Defaulting to '{}'.",
                     MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY,
                     properties.get(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY),
                     samplingInterval);
        }

        int minGapBetweenSegments = tryParseIntProperty(
                MpfConstants.MINIMUM_GAP_BETWEEN_SEGMENTS, properties,
                systemPropertiesSnapshot.getMinAllowableSegmentGap());

        boolean hasConstantFrameRate
                = Boolean.parseBoolean(media.getMetadata().get("HAS_CONSTANT_FRAME_RATE"));
        int targetSegmentLength;
        int minSegmentLength;
        if (hasConstantFrameRate) {
            targetSegmentLength = tryParseIntProperty(
                    MpfConstants.TARGET_SEGMENT_LENGTH_PROPERTY, properties,
                    systemPropertiesSnapshot.getTargetSegmentLength());
            minSegmentLength = tryParseIntProperty(
                    MpfConstants.MINIMUM_SEGMENT_LENGTH_PROPERTY, properties,
                    systemPropertiesSnapshot.getMinSegmentLength());
        }
        else {
            targetSegmentLength = tryParseIntProperty(
                    MpfConstants.VFR_TARGET_SEGMENT_LENGTH_PROPERTY, properties,
                    systemPropertiesSnapshot.getVfrTargetSegmentLength());
            minSegmentLength = tryParseIntProperty(
                    MpfConstants.VFR_MINIMUM_SEGMENT_LENGTH_PROPERTY, properties,
                    systemPropertiesSnapshot.getVfrMinSegmentLength());
        }

        return new SegmentingPlan(targetSegmentLength, minSegmentLength, samplingInterval, minGapBetweenSegments);
    }


    private static int tryParseIntProperty(String propertyName, Map<String, String> properties,
                                           int defaultValue) {
        try {
            return Integer.parseInt(properties.get(propertyName));
        }
        catch (NumberFormatException exception) {
            log.warn(
                    "Attempted to parse {} value of '{}' but encountered an exception. Defaulting to '{}'.",
                    propertyName,
                    properties.get(propertyName),
                    defaultValue,
                    exception);
            return defaultValue;
        }
    }

    public int getLastProcessedTaskIndex(BatchJob job, Media media) {
        for (int taskIdx = job.getCurrentTaskIndex() - 1;
                    taskIdx > media.getCreationTask(); taskIdx--) {
            var action = job.getPipelineElements().getAction(taskIdx, 0);
            if (_aggregateJobPropertiesUtil.actionAppliesToMedia(job, media, action)) {
                return taskIdx;
            }
        }
        return -1;
    }
}
