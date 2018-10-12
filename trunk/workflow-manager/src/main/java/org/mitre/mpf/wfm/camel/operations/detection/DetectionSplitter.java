/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.buffers.AlgorithmPropertyProtocolBuffer;
import org.mitre.mpf.wfm.camel.StageSplitter;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.RedisImpl;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.*;
import org.mitre.mpf.wfm.pipeline.xml.AlgorithmDefinition;
import org.mitre.mpf.wfm.pipeline.xml.PropertyDefinition;
import org.mitre.mpf.wfm.segmenting.*;
import org.mitre.mpf.wfm.service.PipelineService;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

// DetectionSplitter will take in Job and Stage(Action), breaking them into managable work units for the Components

@Component(DetectionSplitter.REF)
public class DetectionSplitter implements StageSplitter {

    private static final Logger log = LoggerFactory.getLogger(DetectionSplitter.class);
    public static final String REF = "detectionStageSplitter";

    @Autowired
    @Qualifier(RedisImpl.REF)
    private Redis redis;

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

    @Autowired
    private PipelineService pipelineService;

    private static final String[] transformProperties = {
            MpfConstants.ROTATION_PROPERTY,
            MpfConstants.HORIZONTAL_FLIP_PROPERTY,
            MpfConstants.SEARCH_REGION_TOP_LEFT_X_DETECTION_PROPERTY,
            MpfConstants.SEARCH_REGION_TOP_LEFT_Y_DETECTION_PROPERTY,
            MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_X_DETECTION_PROPERTY,
            MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_Y_DETECTION_PROPERTY,
            MpfConstants.SEARCH_REGION_ENABLE_DETECTION_PROPERTY,
            MpfConstants.AUTO_ROTATE_PROPERTY,
            MpfConstants.AUTO_FLIP_PROPERTY};

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
    public final List<Message> performSplit(TransientJob transientJob, TransientStage transientStage) {
        assert transientJob != null : "The provided transientJob must not be null.";
        assert transientStage != null : "The provided transientStage must not be null.";

        List<Message> messages = new ArrayList<>();

        // Is this the first detection stage in the pipeline?
        boolean isFirstDetectionStage = isFirstDetectionOperation(transientJob);

        for (TransientMedia transientMedia : transientJob.getMedia()) {
            try {
                if (transientMedia.isFailed()) {
                    // If a media is in a failed state (it couldn't be retrieved, it couldn't be inspected, etc.), do nothing with it.
                    log.debug("[Job {}:{}:*] Skipping Media #{} - it is in an error state.",
                            transientJob.getId(),
                            transientJob.getCurrentStage(),
                            transientMedia.getId());
                    continue;
                }

                // If this is the first detection stage in the pipeline, we should segment the entire media for detection.
                // If this is not the first detection stage, we should build segments based off of the previous stage's
                // tracks. Note that the TimePairs created for these Tracks use the non-feed-forward version of timeUtils.createTimePairsForTracks
                SortedSet<Track> previousTracks;
                if (isFirstDetectionStage) {
                    previousTracks = Collections.emptySortedSet();
                } else {
                    previousTracks = redis.getTracks(transientJob.getId(),
                            transientMedia.getId(),
                            transientJob.getCurrentStage() - 1,
                            0);
                }

                // Iterate through each of the actions and segment the media using the properties provided in that action.
                for (int actionIndex = 0; actionIndex < transientStage.getActions().size(); actionIndex++) {

                    // starting setting of priorities here:  getting action property defaults
                    TransientAction transientAction = transientStage.getActions().get(actionIndex);

                    // modifiedMap initialized with algorithm specific properties
                    Map<String, String> modifiedMap = new HashMap<>(getAlgorithmProperties(transientAction.getAlgorithm(), transientJob.getDetectionSystemPropertiesSnapshot()));

                    // current modifiedMap properties overridden by action properties
                    modifiedMap.putAll(transientAction.getProperties());

                    // If the job is overriding properties related to flip, rotation, or ROI, we should reset all related
                    // action properties to default.  We assume that when the user overrides one rotation/flip/roi
                    // property for a piece of media, they are specifying all of the rotation/flip/roi properties they want
                    // applied for this medium.  This logic is applied THREE times
                    //            -- once for job properties,
                    //            -- once for algorithm properties
                    //            -- and once for media properties.
                    // If the overridden job properties contain any of these values, pipeline properties are reset.
                    // If algorithm properties contain any of these values, overridden job properties and pipeline properties are reset.
                    // If media properties are specified, overridden algorithm properties and job properties and pipeline properties are reset.

                    for (String key : transformProperties) {
                        if (transientJob.getOverriddenJobProperties().containsKey(key)) {
                            clearTransformPropertiesFromMap(modifiedMap);
                            break;
                        }
                    }

                    // Note: by this point override of system properties by job properties has already been applied to the transient job.
                    modifiedMap.putAll(transientJob.getOverriddenJobProperties());

                    // overriding by AlgorithmProperties.  Note that algorithm-properties are of type
                    // Map<String,Map>, so the transform properties to be overridden are actually in the value section of the Map returned
                    // by transientJob.getOverriddenAlgorithmProperties().  This is handled here.
                    // Note that the intent is to override ALL transform properties if ANY single transform properties is overridden

                    // If ANY transform setting is provided at a given level, all transform settings for lower levels are overridden.
                    // The reason is that transform settings interact oddly with each other sometimes.  In the case where auto-flip is
                    // turned on, for instance, a region of interest provided without that in mind might be looking in the wrong area
                    // of a flipped image.

                    // By policy, we say that if any transform settings are defined in a given properties map,
                    // all applicable transform properties must be defined there

                    // Note: only want to consider the algorithm from algorithm properties that corresponds to the current
                    // action being processed.  Which algorithm (i.e. action) that is being processed
                    // is available using transientAction.getAlgorithm().  So, see if our algorithm properties include
                    // override of the action (i.e. algorithm) that we are currently processing
                    // Note that this implementation depends on algorithm property keys matching what would be returned by transientAction.getAlgorithm()
                    if (transientJob.getOverriddenAlgorithmProperties().keySet().contains(transientAction.getAlgorithm())) {
                        // this transient job contains the a algorithm property which may override what is in our current action
                        Map<String, String> job_alg_m = transientJob.getOverriddenAlgorithmProperties().get(transientAction.getAlgorithm());

                        // see if any of these algorithm properties are transform properties.  If so, clear the
                        // current set of transform properties from the map to allow for this algorithm properties to
                        // override the current settings
                        for (String key : transformProperties) {
                            if (job_alg_m.keySet().contains(key)) {
                                clearTransformPropertiesFromMap(modifiedMap);
                                break;
                            }
                        }
                        modifiedMap.putAll(job_alg_m);

                    } // end of algorithm name conditional

                    for (String key : transformProperties) {
                        if (transientMedia.getMediaSpecificProperties().containsKey(key)) {
                            clearTransformPropertiesFromMap(modifiedMap);
                            break;
                        }
                    }

                    modifiedMap.putAll(transientMedia.getMediaSpecificProperties());

                    // Segmenting plan is only used by the VideoMediaSegmenter, so only create the DetectionContext to include the segmenting plan for jobs with video media.
                    SegmentingPlan segmentingPlan = null;
                    if (transientMedia.getMediaType().equals(MediaType.VIDEO)) {

                        // Note that single-frame gifs are treated like videos, but have no native frame rate
                        double fps = 1.0;
                        if (transientMedia.containsMetadata("FPS")) {
                            fps = Double.valueOf(transientMedia.getMetadata("FPS"));
                        }

                        String calcframeInterval = AggregateJobPropertiesUtil.calculateFrameInterval(
                                transientAction, transientJob, transientMedia,
                                transientJob.getDetectionSystemPropertiesSnapshot().getSamplingInterval(), transientJob.getDetectionSystemPropertiesSnapshot().getFrameRateCap(), fps);
                        modifiedMap.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, calcframeInterval);

                        segmentingPlan = createSegmentingPlan(transientJob.getDetectionSystemPropertiesSnapshot(), modifiedMap);
                    }

                    List<AlgorithmPropertyProtocolBuffer.AlgorithmProperty> algorithmProperties = convertPropertiesMapToAlgorithmPropertiesList(modifiedMap);

                    DetectionContext detectionContext = new DetectionContext(
                            transientJob.getId(),
                            transientJob.getCurrentStage(),
                            transientStage.getName(),
                            actionIndex,
                            transientAction.getName(),
                            isFirstDetectionStage,
                            algorithmProperties,
                            previousTracks,
                            segmentingPlan);

                    // get detection request messages from ActiveMQ

                    List<Message> detectionRequestMessages = createDetectionRequestMessages(transientMedia, detectionContext);

                    for (Message message : detectionRequestMessages) {
                        message.setHeader(MpfHeaders.RECIPIENT_QUEUE,
                                String.format("jms:MPF.%s_%s_REQUEST",
                                        transientStage.getActionType(),
                                        transientAction.getAlgorithm()));
                        message.setHeader(MpfHeaders.JMS_REPLY_TO,
                                StringUtils.replace(MpfEndpoints.COMPLETED_DETECTIONS, "jms:", ""));
                    }
                    messages.addAll(detectionRequestMessages);
                    log.debug("[Job {}|{}|{}] Created {} work units for Media #{}.",
                            transientJob.getId(),
                            transientJob.getCurrentStage(),
                            actionIndex,
                            detectionRequestMessages.size(), transientMedia.getId());
                }
            } catch (WfmProcessingException e) {
                redis.setJobStatus(transientJob.getId(), BatchJobStatusType.IN_PROGRESS_ERRORS);
                transientMedia.setFailed(true);
                transientMedia.setMessage(e.getMessage());
                redis.persistMedia(transientJob.getId(), transientMedia);
            }
        }

        return messages;
    }

    private static void clearTransformPropertiesFromMap(Map<String, String> modifiedMap) {
        for (String propertyName : transformProperties) {
            modifiedMap.remove(propertyName);
        }
    }


    private List<Message> createDetectionRequestMessages(TransientMedia media, DetectionContext detectionContext) {
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
	 * @param transientDetectionSystemProperties contains detection system properties whose values were in effect when the transient job was created (will be used as system property default values)
	 * @param properties properties defined for the sub-job
	 * @return segmenting plan for this sub-job
	 */
    private SegmentingPlan createSegmentingPlan(TransientDetectionSystemProperties transientDetectionSystemProperties, Map<String, String> properties) {
        int targetSegmentLength = transientDetectionSystemProperties.getTargetSegmentLength();
        int minSegmentLength = transientDetectionSystemProperties.getMinSegmentLength();
        int samplingInterval = transientDetectionSystemProperties.getSamplingInterval(); // get FRAME_INTERVAL system property
        int minGapBetweenSegments = transientDetectionSystemProperties.getMinAllowableSegmentGap();

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
                            samplingInterval = transientDetectionSystemProperties.getSamplingInterval(); // get FRAME_INTERVAL system property
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
     * Returns {@literal true} iff the current stage of this job is the first detection stage in the job.
     */
    private static boolean isFirstDetectionOperation(TransientJob transientJob) {
        boolean isFirst = false;
        for (int i = 0; i < transientJob.getPipeline().getStages().size(); i++) {

            // This is a detection stage.
            if (transientJob.getPipeline().getStages().get(i).getActionType() == ActionType.DETECTION) {
                // If this is the first detection stage, it must be true that the current stage's index is at most the current job stage's index.
                isFirst = (i >= transientJob.getCurrentStage());
                break;
            }
        }
        return isFirst;
    }


    private Map<String, String> getAlgorithmProperties(String algorithmName, TransientDetectionSystemProperties transientDetectionSystemProperties) {
        AlgorithmDefinition algorithm = pipelineService.getAlgorithm(algorithmName);
        if (algorithm == null) {
            return Collections.emptyMap();
        }

        Set<String> invalidSysPropertiesSet = algorithm.getProvidesCollection().getAlgorithmProperties().stream()
                .filter( pd -> pd.getPropertiesKey() != null && pd.getDefaultValue() == null)
                .map(PropertyDefinition::getPropertiesKey)
                .collect(toSet());

        if (!invalidSysPropertiesSet.isEmpty()) {
            String message = "Missing default values for the following system properties: " + invalidSysPropertiesSet
                    + ". They may need to be defined in one of the *.properties files.";
            log.error(message);
            throw new WfmProcessingException(message);
        }

        // Collect the algorithm properties into a map, but for algorithm definitions that may have been updated from detection system properties
        // changed using the web UI, get the values of those properties from the detection system properties snapshot that was generated when the job was created.
        // NOTE: A filter allows for usage of captured detection system properties when the algorithm PropertyDefinition propertiesKey is null.
        Map<String, String> algPropertiesMap = algorithm.getProvidesCollection().getAlgorithmProperties().stream()
                .filter( pd -> pd.getPropertiesKey() == null )
                .collect(toMap(PropertyDefinition::getName, PropertyDefinition::getDefaultValue)); // collect() will throw a NPE if the property value is null

        // Next statement may replace or add some properties from detection system properties whose values were captured when this job was created.
        // Only want to pull the value from the snapshot if the PropertyDefinition has a proprietiesKey set.
        // This processing is most applicable to jobs where the web UI may have been used to update properties between two stages of a pipeline.
        algorithm.getProvidesCollection().getAlgorithmProperties().stream()
                .filter( pd -> pd.getPropertiesKey() != null )
                .forEach( pd -> algPropertiesMap.put(pd.getName(), transientDetectionSystemProperties.lookup(pd.getPropertiesKey())));

        return algPropertiesMap;

    }

}
