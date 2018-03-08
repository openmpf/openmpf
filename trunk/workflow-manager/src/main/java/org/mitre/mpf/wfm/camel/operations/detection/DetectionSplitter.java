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

import static java.util.stream.Collectors.toMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import org.apache.camel.Message;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.wfm.buffers.AlgorithmPropertyProtocolBuffer;
import org.mitre.mpf.wfm.camel.StageSplitter;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.RedisImpl;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.data.entities.transients.TransientAction;
import org.mitre.mpf.wfm.data.entities.transients.TransientJob;
import org.mitre.mpf.wfm.data.entities.transients.TransientMedia;
import org.mitre.mpf.wfm.data.entities.transients.TransientStage;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.enums.MpfEndpoints;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.pipeline.xml.AlgorithmDefinition;
import org.mitre.mpf.wfm.pipeline.xml.PropertyDefinition;
import org.mitre.mpf.wfm.segmenting.AudioMediaSegmenter;
import org.mitre.mpf.wfm.segmenting.DefaultMediaSegmenter;
import org.mitre.mpf.wfm.segmenting.ImageMediaSegmenter;
import org.mitre.mpf.wfm.segmenting.MediaSegmenter;
import org.mitre.mpf.wfm.segmenting.SegmentingPlan;
import org.mitre.mpf.wfm.segmenting.VideoMediaSegmenter;
import org.mitre.mpf.wfm.service.PipelineService;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

// DetectionSplitter will take in Job and Stage(Action), breaking them into managable work units for the Components

@Component(DetectionSplitter.REF)
public class DetectionSplitter implements StageSplitter {
	private static final Logger log = LoggerFactory.getLogger(DetectionSplitter.class);
	public static final String REF = "detectionStageSplitter";

	@Autowired
	private PropertiesUtil propertiesUtil;

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
			// TODO look here for any modifications required to be made to support feed-forward
			SortedSet<Track> previousTracks;
			if (isFirstDetectionStage) {
				previousTracks = Collections.emptySortedSet();
			}
			else {
				previousTracks = redis.getTracks(transientJob.getId(),
				                                 transientMedia.getId(),
				                                 transientJob.getCurrentStage() - 1,
				                                 0);
			}

            // Allow for FRAME_RATE_CAP to override FRAME_INTERVAL by creating new property COMPUTED_FRAME_INTERVAL. This property is only
            // applicable to videos, which has metadata property FPS.
            // Note: FRAME_RATE_CAP set to -1 means the FRAME_RATE_CAP override is disabled.
            // FRAME_RATE_CAP override is allowed at 5 levels: system properties (lowest priority), action properties, job properties, algorithm properties or media properties (highest priority).

            Double media_fps = null;
            Double computedFrameInterval = null;
            if ( transientMedia.containsMetadata("FPS") ) {

                // Get the videos frame rate from the metadata.
                media_fps = Double.valueOf(transientMedia.getMetadata("FPS"));

                // Check for FRAME_RATE_CAP override of frame interval for videos at the default system property level.
                // If present and not disabled, set the computed frame interval from frame rate cap.
                // Otherwise, if FRAME_INTERVAL is not disabled at the system property level, use that as the default value for computed frame interval.
                if ( propertiesUtil.getFrameRateCap() > 0 ) {
                    // Set the  frame interval from the default detection frame rate cap.
                    // The goal of the FRAME_RATE_CAP is to ensure that a minimum number of frames are processed per second, which is why we round down this value if needed.
                    // But, the value should always be at least 1.
                    computedFrameInterval = Math.max(1, Math.floor(media_fps / propertiesUtil.getFrameRateCap()));
                    log.info("Set computed frame interval to {} from system property FRAME_RATE_CAP using FPS {} ", computedFrameInterval, media_fps);
                } else if ( propertiesUtil.getSamplingInterval() > 0 ) {
                    // Set the  frame interval from the default detection sampling interval
                    computedFrameInterval = Double.valueOf(propertiesUtil.getSamplingInterval());
                    log.info("Set computed frame interval to {} from system property FRAME_INTERVAL", computedFrameInterval);
                }

            }
            System.out.println("DetectionSplitter, debug: media_fps=" + media_fps);
            System.out.println("DetectionSplitter, debug: computedFrameInterval=" + computedFrameInterval);

            // Iterate through each of the actions and segment the media using the properties provided in that action.
			for (int actionIndex = 0; actionIndex < transientStage.getActions().size(); actionIndex++) {

				// starting setting of priorities here:  getting action property defaults
				TransientAction transientAction = transientStage.getActions().get(actionIndex);

				// modifiedMap initialized with algorithm specific properties
				Map<String, String> modifiedMap = new HashMap<>(getAlgorithmProperties(transientAction.getAlgorithm()));

                // If processing a video, determine the computed frame interval based upon FRAME_RATE_CAP or FRAME_INTERVAL specified as action properties
                // that override the previous determination of computed frame interval based upon system properties.
                if ( media_fps != null ) {
                    computedFrameInterval = getComputedFrameIntervalForVideo(media_fps, transientAction.getProperties(), computedFrameInterval);
                    log.info("Set computed frame interval to {} from action property with FPS {} ", computedFrameInterval, media_fps);
                }

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

                // If processing a video, determine the computed frame interval based upon FRAME_RATE_CAP or FRAME_INTERVAL specified as job properties
                // that override the previous determination of computed frame interval based upon action or system properties.
                if ( media_fps != null ) {
                    computedFrameInterval = getComputedFrameIntervalForVideo(media_fps, transientJob.getOverriddenJobProperties(), computedFrameInterval);
                    log.info("Set computed frame interval to {} from job property with FPS {} ", computedFrameInterval, media_fps);
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
					Map job_alg_m = transientJob.getOverriddenAlgorithmProperties().get(transientAction.getAlgorithm());

                    // If processing a video, determine the computed frame interval based upon FRAME_RATE_CAP or FRAME_INTERVAL specified as algorithm properties
                    // that override the computed frame interval previously determined from the job, action or system properties.
                    if ( media_fps != null ) {
                        computedFrameInterval = getComputedFrameIntervalForVideo(media_fps, job_alg_m, computedFrameInterval);
                        log.info("Set computed frame interval to {} from algorithm property with FPS {} ", computedFrameInterval, media_fps);
                    }

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

                // If processing a video, determine the computed frame interval based upon FRAME_RATE_CAP or FRAME_INTERVAL specified as media properties
                // that override the computed frame interval previously determined from the algorithm, job, action or system properties.
                if ( media_fps != null ) {
                    computedFrameInterval = getComputedFrameIntervalForVideo(media_fps, transientMedia.getMediaSpecificProperties(), computedFrameInterval);
                    log.info("Set computed frame interval to {} from media property with FPS {} ", computedFrameInterval, media_fps);
                }

				for (String key : transformProperties) {
					if (transientMedia.getMediaSpecificProperties().containsKey(key)) {
						clearTransformPropertiesFromMap(modifiedMap);
						break;
					}
				}

				modifiedMap.putAll(transientMedia.getMediaSpecificProperties());

                // Moving forward COMPUTED_FRAME_INTERVAL rather than FRAME_INTERVAL will always be
                // part of the job properties map for each sub-job when processing a video. So, if computed frame interval
                // has been set from the media, algorithm, job, action or system properties - then
                // store the value in the modified map (i.e. the map which contains properties to be sent to the sub-job)
                // and store the computed frame interval in the TransientJob algorithm properties.
                // Also persist the update to REDIS so that the change will be in the json output object.
                if ( computedFrameInterval != null ){
                    // For videos, phasing out FRAME_INTERVAL property in favor of COMPUTED_FRAME_INTERVAL_PROPERTY property sent to each sub-job.
                    modifiedMap.remove(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY);
                    modifiedMap.put(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY, computedFrameInterval.toString());
                    // Replace the FRAME_INTERVAL property (if it exists) with the new COMPUTED_FRAME_INTERVAL value
                    // in the transient job and persist it to REDIS so it will show up in the output-object.
                    transientJob.replaceOverriddenAlgorithmProperty(transientAction.getAlgorithm(),
                                        MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY,
                                        computedFrameInterval.toString());
                    redis.persistJob(transientJob);
                }

                System.out.println("DetectionSplitter, debug: modifiedMap = " + modifiedMap);

				SegmentingPlan segmentingPlan = createSegmentingPlan(modifiedMap);
				List<AlgorithmPropertyProtocolBuffer.AlgorithmProperty> algorithmProperties
						= convertPropertiesMapToAlgorithmPropertiesList(modifiedMap);

				// get detection request messages from ActiveMQ
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

                System.out.println("DetectionSplitter, debug: detectionContext = " + detectionContext);

				List<Message> detectionRequestMessages
						= createDetectionRequestMessages(transientMedia, detectionContext);

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

	private SegmentingPlan createSegmentingPlan(Map<String, String> properties) {
		int targetSegmentLength = propertiesUtil.getTargetSegmentLength();
		int minSegmentLength = propertiesUtil.getMinSegmentLength();
		int samplingInterval = propertiesUtil.getSamplingInterval(); // get FRAME_INTERVAL system property
		int minGapBetweenSegments = propertiesUtil.getMinAllowableSegmentGap();

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
							samplingInterval = propertiesUtil.getSamplingInterval(); // get FRAME_INTERVAL system property
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


	private Map<String, String> getAlgorithmProperties(String algorithmName) {
		AlgorithmDefinition algorithm = pipelineService.getAlgorithm(algorithmName);
		if (algorithm == null) {
			return Collections.emptyMap();
		}
		return algorithm.getProvidesCollection().getAlgorithmProperties().stream()
				.collect(toMap(PropertyDefinition::getName, PropertyDefinition::getDefaultValue));
	}


    /**
     * Get the computed frame interval for a video by applying the FRAME_RATE_CAP / FRAME_INTERVAL property override strategy.
     * @param media_fps Video frame rate per second, may not be null.
     * @param propertyOverrideMap Property override map.
     * @param lastComputedFrameInterval Previously computed frame interval, may be null.
     * @return Computed frame interval, may be null.
     */
    private Double getComputedFrameIntervalForVideo( Double media_fps, Map<String,String> propertyOverrideMap, Double lastComputedFrameInterval) {
	    Double computedFrameInterval = null;
        if ( propertyOverrideMap.containsKey(MpfConstants.FRAME_RATE_CAP_PROPERTY) && Double.valueOf(propertyOverrideMap.get(MpfConstants.FRAME_RATE_CAP_PROPERTY)) > 0) {
            // Apply override of FRAME_RATE_CAP_PROPERTY property to media
            computedFrameInterval = Math.max(1, Math.floor(media_fps / Double.valueOf(propertyOverrideMap.get(MpfConstants.FRAME_RATE_CAP_PROPERTY))));
        } else if ( propertyOverrideMap.containsKey(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) && Double.valueOf(propertyOverrideMap.get(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)) > 0) {
            // Apply override of FRAME_INTERVAL property
            computedFrameInterval = Double.valueOf(propertyOverrideMap.get(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY));
        } else if ( lastComputedFrameInterval != null ) {
            // Let the computed frame interval take on the previously established value (if it was previously set).
            computedFrameInterval = lastComputedFrameInterval;
        }
        return computedFrameInterval;
    }
}
