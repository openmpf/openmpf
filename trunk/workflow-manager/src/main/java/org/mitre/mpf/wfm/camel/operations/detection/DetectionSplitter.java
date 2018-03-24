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
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;

import static java.util.stream.Collectors.toMap;

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

	/* DJV
	@Autowired
    private AdaptiveFrameIntervalPropertyState adaptiveFrameIntervalPropertyState;
    */

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

		/* DJV
		// TODO: there must be a better way to do this, feedback is requested, adaptiveFrameIntervalPropertyState needs to be initialized for each TransientJob being processed
        // Without this initialization statement, adaptiveFrameIntervalPropertyState isn't getting initialized and the 40 frame rate cap
        // test cases defined in TestDetectionSplitter fails because the state variable isn't being initialized. But, the following
        // condition will only be true if the TransientJob has the first stage being a Detection. How to check for the first time
        // this DetectionSplitter has been called for a TransientJob?
		if ( isFirstDetectionStage ) {
            adaptiveFrameIntervalPropertyState.init();
        }
        */

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

            // Iterate through each of the actions and segment the media using the properties provided in that action.
			for (int actionIndex = 0; actionIndex < transientStage.getActions().size(); actionIndex++) {

                // starting setting of priorities here:  getting action property defaults
                TransientAction transientAction = transientStage.getActions().get(actionIndex);

                // modifiedMap initialized with algorithm specific properties
                Map<String, String> modifiedMap = new HashMap<>(
                    getAlgorithmProperties(transientAction.getAlgorithm()));

                // current modifiedMap properties overridden by action properties
                modifiedMap.putAll(transientAction.getProperties());

                /* DJV
                // OpenMPF allows for FRAME_RATE_CAP override of FRAME_INTERVAL and/or disable of FRAME_RATE_CAP or FRAME_INTERVAL for videos on a level-by-level basis.
                if (transientMedia.containsMetadata("FPS")) {

                    // Update state for video media at the action property level.
                    adaptiveFrameIntervalPropertyState.setAdaptiveFrameIntervalPropertyState(transientAction.getProperties(),
                                                                                            AdaptiveFrameIntervalPropertyState.PropertyLevel.ACTION_LEVEL);

                    // If the FRAME_RATE_CAP override of FRAME_INTERVAL has been detected at this property level, then
                    // remove FRAME_INTERVAL from the modifiedMap because the applied property FRAME_RATE_CAP would
                    // override any specification of FRAME_INTERVAL at this property level.
                    if ( adaptiveFrameIntervalPropertyState.isFrameRateCapPropertyOverrideEnabled() ) {
                        modifiedMap.remove(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY);
                    }

                }
                */

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

                /* DJV
                // OpenMPF allows for FRAME_RATE_CAP override of FRAME_INTERVAL and/or disable of FRAME_RATE_CAP or FRAME_INTERVAL for videos on a level-by-level basis.
                if ( transientMedia.containsMetadata("FPS") ) {

                    // Update state for video media at the job property level.
                    adaptiveFrameIntervalPropertyState.setAdaptiveFrameIntervalPropertyState(transientJob.getOverriddenJobProperties(),
                                                                                    AdaptiveFrameIntervalPropertyState.PropertyLevel.JOB_LEVEL);

                    // If the FRAME_RATE_CAP override of FRAME_INTERVAL has been detected at this property level, then
                    // remove FRAME_INTERVAL from the modifiedMap because the applied property FRAME_RATE_CAP would
                    // override any specification of FRAME_INTERVAL at this property level.
                    if ( adaptiveFrameIntervalPropertyState.isFrameRateCapPropertyOverrideEnabled() ) {
                        modifiedMap.remove(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY);
                    }
                }
                */

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
					Map<String,String> job_alg_m = transientJob.getOverriddenAlgorithmProperties().get(transientAction.getAlgorithm());

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

					/* DJV
                    // OpenMPF allows for FRAME_RATE_CAP override of FRAME_INTERVAL and/or disable of FRAME_RATE_CAP or FRAME_INTERVAL for videos on a level-by-level basis.
                    // Check for these conditions for video media at the algorithm property level.
                    if ( transientMedia.containsMetadata("FPS") ) {

                        // Update state for video media at the algorithm property level.
                        adaptiveFrameIntervalPropertyState.setAdaptiveFrameIntervalPropertyState(job_alg_m,
                                                                        AdaptiveFrameIntervalPropertyState.PropertyLevel.ALGORITHM_LEVEL);

                        // If the FRAME_RATE_CAP override of FRAME_INTERVAL has been detected at this property level, then
                        // remove FRAME_INTERVAL from the modifiedMap because the applied property FRAME_RATE_CAP would
                        // override any specification of FRAME_INTERVAL at this property level.
                        if ( adaptiveFrameIntervalPropertyState.isFrameRateCapPropertyOverrideEnabled() ) {
                            modifiedMap.remove(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY);
                        }
                    }
                    */

                } // end of algorithm name conditional

				for (String key : transformProperties) {
					if (transientMedia.getMediaSpecificProperties().containsKey(key)) {
						clearTransformPropertiesFromMap(modifiedMap);
						break;
					}
				}

				modifiedMap.putAll(transientMedia.getMediaSpecificProperties());

				/* DJV
                if ( transientMedia.containsMetadata("FPS") ) {
                    
                    // Update state for video media at the at the media property level.
                    adaptiveFrameIntervalPropertyState.setAdaptiveFrameIntervalPropertyState(transientMedia.getMediaSpecificProperties(),
                                                                                            AdaptiveFrameIntervalPropertyState.PropertyLevel.MEDIA_LEVEL);

                    // If the FRAME_RATE_CAP override of FRAME_INTERVAL has been detected at this property level, then
                    // remove FRAME_INTERVAL from the modifiedMap because the applied property FRAME_RATE_CAP would
                    // override any specification of FRAME_INTERVAL at this property level.
                    if ( adaptiveFrameIntervalPropertyState.isFrameRateCapPropertyOverrideEnabled() ) {
                        modifiedMap.remove(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY);
                    }

                    // Determine the adaptive frame interval based upon the FRAME_RATE_CAP vs. FRAME_INTERVAL state as
                    // tracked by adaptiveFrameIntervalPropertyState along with the cleaned up properties in the modifiedMap.
                    // At this point, modifiedMap may contain FRAME_RATE_CAP or FRAME_INTERVAL after the media, algorithm, job,
                    // or action property level-by-level comparison or overrides have been applied. This following code
                    // determines the adapted frame interval based upon which of those properties have been found, at which property level, and whether or not
                    // those properties have been disabled.
                    // System properties may be used to provide as a default value for computed frame interval if FRAME_RATE_CAP and/or FRAME_INTERVAL
                    // are disabled by setting to <= 0.
                    
                    // Get the videos frame rate from the metadata.
                    double mediaFPS = Double.valueOf(transientMedia.getMetadata("FPS"));
                    int computedFrameInterval = getComputedFrameIntervalForVideo(adaptiveFrameIntervalPropertyState, mediaFPS, modifiedMap);

                    // If computed frame interval has been determined, then replace the current value of FRAME_INTERVAL that will be sent to
                    // each sub-job with the computed frame interval.
                    if ( computedFrameInterval != -1 ) {
                        modifiedMap.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, Integer.toString(computedFrameInterval));
                     }
                }

                // TODO, I don't see any protection in here for a user specifying FRAME_INTERVAL of -1 for non-video media. The
                // createSegmentingPlan method will send a warning about using FRAME_INTERVAL set to 1 if this occurs. Is this ok?
                */

				SegmentingPlan segmentingPlan = createSegmentingPlan(modifiedMap);
				List<AlgorithmPropertyProtocolBuffer.AlgorithmProperty> algorithmProperties
						= convertPropertiesMapToAlgorithmPropertiesList(modifiedMap);

				if ( transientMedia.containsMetadata("FPS")) {
					String calcframeInterval = AggregateJobPropertiesUtil.calculateFrameInterval(
							transientAction, transientJob, transientMedia,
							propertiesUtil.getSamplingInterval(), propertiesUtil.getFrameRateCap(),
							Double.valueOf(transientMedia.getMetadata("FPS")));
					modifiedMap.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, calcframeInterval);
				}

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
     * Get the computed frame interval for a video by applying the FRAME_RATE_CAP / FRAME_INTERVAL property override strategy. Note that if FRAME_RATE_CAP or FRAME_INTERVAL
     * is disabled (i.e. set to <= 0) at a higher property level, then a non-disabled property value at a lower property level will be ignored. Property level rankings for OpenMPF
     * are: (lowest) system properties, action properties, job properties, algorithm properties, media properties (highest)
     * The adaptive frame interval strategy for OpenMPF is:
     * - Condition 1: If FRAME_RATE_CAP is present and not disabled, ignore FRAME_INTERVAL and use FRAME_RATE_CAP to derive computed frame interval.
     * - Condition 2: If FRAME_RATE_CAP is present and disabled, and FRAME_INTERVAL is present and not disabled, then use FRAME_INTERVAL value.
     * - Condition 3: If FRAME_RATE_CAP is present and disabled, and FRAME_INTERVAL is not present then only consider FRAME_INTERVAL at lower property levels until the default FRAME_INTERVAL system property is reached.
     * - Condition 4: If FRAME_RATE_CAP is not present and FRAME_INTERVAL is not present check for FRAME_RATE_CAP or FRAME_INTERVAL at lower property levels until the default FRAME_RATE_CAP or FRAME_INTERVAL system properties are reached.
     * - Condition 5: If FRAME_RATE_CAP is not present and FRAME_INTERVAL is present and not disabled check then use FRAME_INTERVAL value at this level.
     * - Condition 6: If FRAME_RATE_CAP is not present and FRAME_INTERVAL is present and disabled check then only consider FRAME_RATE_CAP at lower property levels until the default FRAME_RATE_CAP system property is reached.
     * - Condition 7: If both FRAME_RATE_CAP and FRAME_INTERVAL are present and disabled at any level, this should result in setting the frame interval to 1
     *   But, if FRAME_RATE_CAP system property is <= 0, this combination of both disabled properties should result in setting the
     *   frame interval to 1 (no matter what the frame interval system property is).
     * @param adaptiveFrameIntervalPropertyState current state after ranked FRAME_RATE_CAP and FRAME_INTERVAL properties have been applied.
     * @param mediaFPS Video frame rate per second, must be > 0.
     * @param modifiedMap the modifiedMap contains the set of properties that will be sent to each sub-job.
     * @return Computed frame interval, may be returned as -1 if not set based upon the adaptive frame interval strategy.
     */
    /* DJV
    private int getComputedFrameIntervalForVideo( AdaptiveFrameIntervalPropertyState adaptiveFrameIntervalPropertyState, double mediaFPS, Map<String,String> modifiedMap) {

        int computedFrameInterval = -1;

        if ( adaptiveFrameIntervalPropertyState.isFrameRateCapPropertyOverrideEnabled() ||

            ( adaptiveFrameIntervalPropertyState.isFrameRateCapPropertySpecifiedAndNotDisabled() &&
                ( adaptiveFrameIntervalPropertyState.isFrameIntervalPropertySpecifiedAndDisabled() || adaptiveFrameIntervalPropertyState.isFrameIntervalPropertyNotSpecified() ) ) ||

            ( adaptiveFrameIntervalPropertyState.isFrameRateCapPropertySpecifiedAndNotDisabled() &&
                adaptiveFrameIntervalPropertyState.isFrameIntervalPropertySpecifiedAndNotDisabled() &&
                adaptiveFrameIntervalPropertyState.getFrameRateCapPropertyLevel().ordinal() >= adaptiveFrameIntervalPropertyState.getFrameIntervalPropertyLevel().ordinal() ) ) {

            // Condition 1a: If FRAME_RATE_CAP override of FRAME_INTERVAL has been detected, or
            // Condition 1b: If FRAME_RATE_CAP is present and not disabled and FRAME_INTERVAL has been disabled or has not been specified.
            // Condition 1c: If FRAME_RATE_CAP is present and not disabled at some equal or higher property level than FRAME_INTERVAL has been specified.
            // then use FRAME_RATE_CAP to derive computed frame interval.
            computedFrameInterval = (int) Math.max(1, Math.floor(mediaFPS / Double.valueOf(modifiedMap.get(MpfConstants.FRAME_RATE_CAP_PROPERTY))));

        } else if ( adaptiveFrameIntervalPropertyState.isFrameRateCapPropertySpecifiedAndDisabled() && adaptiveFrameIntervalPropertyState.isFrameIntervalPropertySpecifiedAndNotDisabled() ) {

            // Condition 2: If FRAME_RATE_CAP is present as disabled and FRAME_INTERVAL was specified and never disabled, then use whatever FRAME_INTERVAL is in the modifiedMap.
            // modifiedMap should contain FRAME_INTERVAL from the highest ranked set of properties.
            computedFrameInterval = Integer.valueOf(modifiedMap.get(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY));

        } else if ( adaptiveFrameIntervalPropertyState.isFrameRateCapPropertySpecifiedAndDisabled() && adaptiveFrameIntervalPropertyState.isFrameIntervalPropertyNotSpecified() ) {

            // Condition 3: If FRAME_RATE_CAP is present as disabled, and FRAME_INTERVAL was never specified, then use whatever FRAME_INTERVAL from the system properties. If
            // if the FRAME_INTERVAL system property is disabled, then use fallback value of 1 for FRAME_INTERVAL.
            if (propertiesUtil.getSamplingInterval() > 0) {
                // Set the  frame interval from the default detection sampling interval
                computedFrameInterval = Integer.valueOf(propertiesUtil.getSamplingInterval());
            } else {
                // If both system properties are disabled, then always use FRAME_INTERVAL of 1.
                computedFrameInterval = 1;
            }

        } else if ( adaptiveFrameIntervalPropertyState.isFrameRateCapPropertyNotSpecified() && adaptiveFrameIntervalPropertyState.isFrameIntervalPropertyNotSpecified() ) {

            // Condition 4: If neither FRAME_RATE_CAP nor FRAME_INTERVAL were ever specified then check for FRAME_RATE_CAP or FRAME_INTERVAL in the default system properties
            if (propertiesUtil.getFrameRateCap() > 0) {
                // Set the  frame interval from the default detection frame rate cap.
                // The goal of the FRAME_RATE_CAP is to ensure that a minimum number of frames are processed per second, which is why we round down this value if needed.
                // But, the value should always be at least 1.
                computedFrameInterval = (int) Math.max(1, Math.floor(mediaFPS / propertiesUtil.getFrameRateCap()));
            } else if (propertiesUtil.getSamplingInterval() > 0) {
                // Set the  frame interval from the default detection sampling interval
                computedFrameInterval = Integer.valueOf(propertiesUtil.getSamplingInterval());
            } else {
                // If both system properties are disabled, then always use FRAME_INTERVAL of 1.
                computedFrameInterval = 1;
            }

        } else if ( adaptiveFrameIntervalPropertyState.isFrameRateCapPropertyNotSpecified() && adaptiveFrameIntervalPropertyState.isFrameIntervalPropertySpecifiedAndNotDisabled() ) {

            // Condition 5: If FRAME_RATE_CAP was never specified and FRAME_INTERVAL was specified and not disabled check then use FRAME_INTERVAL value at this level.
            computedFrameInterval = Integer.valueOf(modifiedMap.get(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY));

        } else if ( adaptiveFrameIntervalPropertyState.isFrameRateCapPropertyNotSpecified() && adaptiveFrameIntervalPropertyState.isFrameIntervalPropertySpecifiedAndDisabled() ) {

            // Condition 6: If FRAME_RATE_CAP was never specified and the FRAME_INTERVAL was specified as disabled, then check for FRAME_RATE_CAP in the default system properties.
            // If the FRAME_RATE_CAP in the default system properties is disabled, then fallback to using FRAME_INTERVAL of 1.
            if (propertiesUtil.getFrameRateCap() > 0) {
                // Set the  frame interval from the default detection frame rate cap.
                // The goal of the FRAME_RATE_CAP is to ensure that a minimum number of frames are processed per second, which is why we round down this value if needed.
                // But, the value should always be at least 1.
                computedFrameInterval = (int) Math
                    .max(1, Math.floor(mediaFPS / propertiesUtil.getFrameRateCap()));
            } else {
                // If both system properties are disabled, then always use FRAME_INTERVAL of 1.
                computedFrameInterval = 1;
            }

        } else if ( adaptiveFrameIntervalPropertyState.isFrameRateCapPropertySpecifiedAndDisabled() && adaptiveFrameIntervalPropertyState.isFrameIntervalPropertySpecifiedAndDisabled() ) {

            // Condition 7: If both FRAME_RATE_CAP and FRAME_INTERVAL are present and disabled at any level, this should result in setting the frame interval to 1.
            computedFrameInterval = 1;

        }

        return computedFrameInterval;

    }
    */
}
