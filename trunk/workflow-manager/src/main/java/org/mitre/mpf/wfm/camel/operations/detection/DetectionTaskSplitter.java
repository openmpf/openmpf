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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultMessage;
import org.javasimon.aop.Monitored;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.buffers.AlgorithmPropertyProtocolBuffer;
import org.mitre.mpf.wfm.camel.routes.DetectionResponseRouteBuilder;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.SystemPropertiesSnapshot;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.segmenting.AudioMediaSegmenter;
import org.mitre.mpf.wfm.segmenting.DefaultMediaSegmenter;
import org.mitre.mpf.wfm.segmenting.ImageMediaSegmenter;
import org.mitre.mpf.wfm.segmenting.MediaSegmenter;
import org.mitre.mpf.wfm.segmenting.SegmentingPlan;
import org.mitre.mpf.wfm.segmenting.VideoMediaSegmenter;
import org.mitre.mpf.wfm.service.TaskMergingManager;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// DetectionSplitter will take in Job and Task, breaking them into manageable work units for the Components

@Component
@Monitored
public class DetectionTaskSplitter {

    private static final Logger log = LoggerFactory.getLogger(DetectionTaskSplitter.class);

    private final CamelContext _camelContext;

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;

    private final InProgressBatchJobsService _inProgressBatchJobs;

    private final TaskMergingManager _taskMergingManager;

    private final MediaSegmenter _imageMediaSegmenter;

    private final MediaSegmenter _videoMediaSegmenter;

    private final MediaSegmenter _audioMediaSegmenter;

    private final MediaSegmenter _defaultMediaSegmenter;


    @Inject
    public DetectionTaskSplitter(
            CamelContext camelContext,
            AggregateJobPropertiesUtil aggregateJobPropertiesUtil,
            InProgressBatchJobsService inProgressBatchJobs,
            TaskMergingManager taskMergingManager,
            @Named(ImageMediaSegmenter.REF) MediaSegmenter imageMediaSegmenter,
            @Named(VideoMediaSegmenter.REF) MediaSegmenter videoMediaSegmenter,
            @Named(AudioMediaSegmenter.REF) MediaSegmenter audioMediaSegmenter,
            @Named(DefaultMediaSegmenter.REF) MediaSegmenter defaultMediaSegmenter) {
        _camelContext = camelContext;
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
        _inProgressBatchJobs = inProgressBatchJobs;
        _taskMergingManager = taskMergingManager;
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
                    log.warn("Skipping media {}. It is in an error state.", media.getId());
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

                    if (!AggregateJobPropertiesUtil.actionAppliesToMedia(
                            media, combinedProperties)) {
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
                            segmentingPlan,
                            combinedProperties.get(MpfConstants.QUALITY_SELECTION_PROPERTY));

                    var detectionRequestMessages = createDetectionRequestMessages(
                            job, media, action, detectionContext);
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


    private List<Message> createDetectionRequestMessages(
            BatchJob job, Media media, Action action, DetectionContext detectionContext) {
        var segmenter = getSegmenter(media.getType().orElse(MediaType.UNKNOWN));
        var requests = segmenter.createDetectionRequests(media, detectionContext);
        if (requests.isEmpty()) {
            return List.of();
        }

        var actionType = job.getPipelineElements().getAlgorithm(action.getAlgorithm())
                .getActionType();
        var destination = "MPF.%s_%s_REQUEST".formatted(actionType, action.getAlgorithm());
        boolean needsBreadCrumb = _taskMergingManager.needsBreadCrumb(
                job, media, detectionContext.getTaskIndex(), detectionContext.getActionIndex());

        var messages = new ArrayList<Message>(requests.size());
        for (var request : requests) {
            var message = new DefaultMessage(_camelContext);
            message.setHeader(MpfHeaders.JMS_DESTINATION, destination);
            message.setHeader(
                    MpfHeaders.JMS_REPLY_TO,
                    DetectionResponseRouteBuilder.JMS_DESTINATION);
            media.getType()
                    .ifPresent(mt -> message.setHeader(MpfHeaders.MEDIA_TYPE, mt.toString()));
            if (needsBreadCrumb) {
                request.feedForwardTrack()
                    .ifPresent(t -> _taskMergingManager.addBreadCrumb(message, t));
            }
            message.setBody(request.protobuf());
            messages.add(message);
        }
        return messages;
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
