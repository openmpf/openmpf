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

package org.mitre.mpf.wfm.camel.operations.detection;

import org.apache.camel.Message;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.ActionType;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.buffers.AlgorithmPropertyProtocolBuffer;
import org.mitre.mpf.wfm.camel.StageSplitter;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.SystemPropertiesSnapshot;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.*;
import org.mitre.mpf.wfm.segmenting.*;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;

// DetectionSplitter will take in Job and Stage(Action), breaking them into managable work units for the Components

@Component(DetectionSplitter.REF)
public class DetectionSplitter implements StageSplitter {

    private static final Logger log = LoggerFactory.getLogger(DetectionSplitter.class);
    public static final String REF = "detectionStageSplitter";

    @Autowired
    private PropertiesUtil propertiesUtil;

    @Autowired
    private AggregateJobPropertiesUtil aggregateJobPropertiesUtil;

    @Autowired
    private InProgressBatchJobsService inProgressBatchJobs;

    @Autowired
    @Qualifier(ImageMediaSegmenter.REF)
    private MediaSegmenter imageMediaSegmenter;

    @Autowired
    @Qualifier(VideoMediaSegmenter.REF)
    private MediaSegmenter videoMediaSegmenter;

    @Autowired
    @Qualifier(AudioMediaSegmenter.REF)
    private MediaSegmenter audioMediaSegmenter;

    @Autowired
    @Qualifier(DefaultMediaSegmenter.REF)
    private MediaSegmenter defaultMediaSegmenter;


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

    // property priorities are assigned in this method.  The property priorities are defined as:
    // action-property defaults (lowest) -> action-properties -> job-properties -> algorithm-properties -> media-properties (highest)
    @Override
    public final List<Message> performSplit(BatchJob job, Task task) {
        List<Message> messages = new ArrayList<>();

        // Is this the first detection task in the pipeline?
        boolean isFirstDetectionTask = isFirstDetectionTask(job);

        for (Media media : job.getMedia()) {
            try {
                if (media.isFailed()) {
                    // If a media is in a failed state (it couldn't be retrieved, it couldn't be inspected, etc.), do nothing with it.
                    log.debug("[Job {}:{}:*] Skipping Media #{} - it is in an error state.",
                            job.getId(),
                            job.getCurrentTaskIndex(),
                            media.getId());
                    continue;
                }

                // If this is the first detection task in the pipeline, we should segment the entire media for detection.
                // If this is not the first detection task, we should build segments based off of the previous tasks's
                // tracks. Note that the TimePairs created for these Tracks use the non-feed-forward version of timeUtils.createTimePairsForTracks
                SortedSet<Track> previousTracks;
                if (isFirstDetectionTask) {
                    previousTracks = Collections.emptySortedSet();
                }
                else {
                    previousTracks = inProgressBatchJobs.getTracks(
                            job.getId(), media.getId(), job.getCurrentTaskIndex() - 1, 0);
                }

                // Iterate through each of the actions and segment the media using the properties provided in that action.
                for (int actionIndex = 0; actionIndex < task.getActions().size(); actionIndex++) {

                    String actionName = task.getActions().get(actionIndex);
                    Action action = job.getPipelineElements().getAction(actionName);

                    var combinedProperties = new HashMap<String, String>(
                            aggregateJobPropertiesUtil.getPropertyMap(job, media, action));

                    // Segmenting plan is only used by the VideoMediaSegmenter,
                    // so only create the DetectionContext to include the segmenting plan for jobs with video media.
                    SegmentingPlan segmentingPlan = null;
                    if (media.getMediaType().equals(MediaType.VIDEO)) {

                        // Note that single-frame gifs are treated like videos, but have no native frame rate
                        double fps = 1.0;
                        String fpsFromMetadata = media.getMetadata("FPS");
                        if (fpsFromMetadata != null) {
                            fps = Double.valueOf(fpsFromMetadata);
                        }

                        String calcframeInterval = aggregateJobPropertiesUtil.calculateFrameInterval(
                                action, job, media,
                                job.getSystemPropertiesSnapshot().getSamplingInterval(),
                                job.getSystemPropertiesSnapshot().getFrameRateCap(), fps);
                        combinedProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, calcframeInterval);

                        segmentingPlan = createSegmentingPlan(job.getSystemPropertiesSnapshot(), combinedProperties);
                    }

                    List<AlgorithmPropertyProtocolBuffer.AlgorithmProperty> algorithmProperties
                            = convertPropertiesMapToAlgorithmPropertiesList(combinedProperties);

                    DetectionContext detectionContext = new DetectionContext(
                            job.getId(),
                            job.getCurrentTaskIndex(),
                            task.getName(),
                            actionIndex,
                            action.getName(),
                            isFirstDetectionTask,
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
                    }
                    messages.addAll(detectionRequestMessages);
                    log.debug("[Job {}|{}|{}] Created {} work units for Media #{}.",
                            job.getId(),
                            job.getCurrentTaskIndex(),
                            actionIndex,
                            detectionRequestMessages.size(), media.getId());
                }
            } catch (WfmProcessingException e) {
                inProgressBatchJobs.setJobStatus(job.getId(), BatchJobStatusType.IN_PROGRESS_ERRORS);
                inProgressBatchJobs.addMediaError(job.getId(), media.getId(), e.getMessage());
            }
        }

        return messages;
    }


    private List<Message> createDetectionRequestMessages(Media media, DetectionContext detectionContext) {
        MediaSegmenter segmenter = getSegmenter(media.getMediaType());
        return segmenter.createDetectionRequestMessages(media, detectionContext);
    }

    private MediaSegmenter getSegmenter(MediaType mediaType) {
        switch (mediaType) {
            case IMAGE:
                return imageMediaSegmenter;
            case VIDEO:
                return videoMediaSegmenter;
            case AUDIO:
                return audioMediaSegmenter;
            default:
                return defaultMediaSegmenter;
        }
    }

    /**
     * Create the segmenting plan using the properties defined for the sub-job.
     * @param systemPropertiesSnapshot contains detection system properties whose values were in effect when the job was created (will be used as system property default values)
     * @param properties properties defined for the sub-job
     * @return segmenting plan for this sub-job
     */
    private SegmentingPlan createSegmentingPlan(SystemPropertiesSnapshot systemPropertiesSnapshot, Map<String, String> properties) {
        int targetSegmentLength = systemPropertiesSnapshot.getTargetSegmentLength();
        int minSegmentLength = systemPropertiesSnapshot.getMinSegmentLength();
        int samplingInterval = systemPropertiesSnapshot.getSamplingInterval(); // get FRAME_INTERVAL system property
        int minGapBetweenSegments = systemPropertiesSnapshot.getMinAllowableSegmentGap();

        // TODO: Better to use direct map access rather than a loop, but that requires knowing the case of the keys in the map.
        // Enforce case-sensitivity throughout the WFM.
        if (properties != null) {
            for (Map.Entry<String, String> property : properties.entrySet()) {
                if (StringUtils.equalsIgnoreCase(property.getKey(), MpfConstants.TARGET_SEGMENT_LENGTH_PROPERTY)) {
                    try {
                        targetSegmentLength = Integer.valueOf(property.getValue());
                    }
                    catch (NumberFormatException exception) {
                        log.warn(
                            "Attempted to parse {} value of '{}' but encountered an exception. Defaulting to '{}'.",
                            MpfConstants.TARGET_SEGMENT_LENGTH_PROPERTY,
                            property.getValue(),
                            targetSegmentLength,
                            exception);
                    }
                }
                if (StringUtils.equalsIgnoreCase(property.getKey(), MpfConstants.MINIMUM_SEGMENT_LENGTH_PROPERTY)) {
                    try {
                        minSegmentLength = Integer.valueOf(property.getValue());
                    }
                    catch (NumberFormatException exception) {
                        log.warn(
                            "Attempted to parse {} value of '{}' but encountered an exception. Defaulting to '{}'.",
                            MpfConstants.MINIMUM_SEGMENT_LENGTH_PROPERTY,
                            property.getValue(),
                            minSegmentLength,
                            exception);
                    }
                }
                if (StringUtils.equalsIgnoreCase(property.getKey(), MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)) {
                    try {
                        samplingInterval = Integer.valueOf(property.getValue());
                        if (samplingInterval < 1) {
                            samplingInterval = systemPropertiesSnapshot.getSamplingInterval(); // get FRAME_INTERVAL system property
                            log.warn("'{}' is not an acceptable {} value. Defaulting to '{}'.",
                                     MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY,
                                     property.getValue(),
                                     samplingInterval);
                        }
                    }
                    catch (NumberFormatException exception) {
                        log.warn(
                            "Attempted to parse {} value of '{}' but encountered an exception. Defaulting to '{}'.",
                             MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY,
                             property.getValue(),
                             samplingInterval,
                             exception);
                    }
                }
                if (StringUtils.equalsIgnoreCase(property.getKey(), MpfConstants.MINIMUM_GAP_BETWEEN_SEGMENTS)) {
                    try {
                        minGapBetweenSegments = Integer.valueOf(property.getValue());
                    }
                    catch (NumberFormatException exception) {
                        log.warn(
                            "Attempted to parse {} value of '{}' but encountered an exception. Defaulting to '{}'.",
                            MpfConstants.MINIMUM_GAP_BETWEEN_SEGMENTS,
                            property.getValue(),
                            minGapBetweenSegments,
                            exception);
                    }
                }
            }
        }

        return new SegmentingPlan(targetSegmentLength, minSegmentLength, samplingInterval, minGapBetweenSegments);
    }

    /**
     * Returns {@literal true} iff the current task of this job is the first detection task in the job.
     */
    private static boolean isFirstDetectionTask(BatchJob job) {
        boolean isFirst = false;
        for (int i = 0; i < job.getPipelineElements().getTaskCount(); i++) {
            ActionType actionType = job.getPipelineElements().getAlgorithm(i, 0).getActionType();
            // This is a detection task.
            if (actionType == ActionType.DETECTION) {
                // If this is the first detection task, it must be true that the current task's index is at most the
                // current job task's index.
                isFirst = i >= job.getCurrentTaskIndex();
                break;
            }
        }

        return isFirst;
    }
}
