/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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
import org.mitre.mpf.wfm.data.RedisImpl;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.entities.transients.TransientAction;
import org.mitre.mpf.wfm.data.entities.transients.TransientJob;
import org.mitre.mpf.wfm.data.entities.transients.TransientMedia;
import org.mitre.mpf.wfm.data.entities.transients.TransientStage;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.enums.MpfEndpoints;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.segmenting.*;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.TimePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;

@Component(DetectionSplitter.REF)
public class DetectionSplitter implements StageSplitter {
	private static final Logger log = LoggerFactory.getLogger(DetectionSplitter.class);
	public static final String REF = "detectionStageSplitter";

	@Autowired
	protected PropertiesUtil propertiesUtil;

	@Autowired
	@Qualifier(RedisImpl.REF)
	protected Redis redis;

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

	/** Translates a collection of properties into a collection of AlgorithmProperty ProtoBuf messages. If the input is null or empty, an empty collection is returned. */
	private List<AlgorithmPropertyProtocolBuffer.AlgorithmProperty> convertPropertiesMapToAlgorithmPropertiesList(Map<String, String> propertyMessages) {
		if(propertyMessages == null || propertyMessages.size() == 0) {
			return new ArrayList<>(0);
		} else {
			List<AlgorithmPropertyProtocolBuffer.AlgorithmProperty> algorithmProperties = new ArrayList<>(propertyMessages.size());
			for(Map.Entry<String, String> entry : propertyMessages.entrySet()) {
				algorithmProperties.add(AlgorithmPropertyProtocolBuffer.AlgorithmProperty.newBuilder().setPropertyName(entry.getKey()).setPropertyValue(entry.getValue()).build());
			}
			return algorithmProperties;
		}
	}

	@Override
	public final List<Message> performSplit(TransientJob transientJob, TransientStage transientStage) throws Exception {
		assert transientJob != null : "The provided transientJob must not be null.";
		assert transientStage != null : "The provided transientStage must not be null.";

		List<Message> messages = new ArrayList<>();

		// Is this the first detection stage in the pipeline?
		boolean isFirstDetectionStage = isFirstDetectionOperation(transientJob);

		for(TransientMedia transientMedia : transientJob.getMedia()) {

			if(transientMedia.isFailed()) {
				// If a media is in a failed state (it couldn't be retrieved, it couldn't be inspected, etc.), do nothing with it.
				log.debug("[Job {}:{}:*] Skipping Media #{} - it is in an error state.", transientJob.getId(), transientJob.getCurrentStage(), transientMedia.getId());
				continue;
			}

			// If this is the first detection stage in the pipeline, we should segment the entire media for detection.
			// If this is not the first detection stage, we should build segments based off of the previous stage's
			// tracks.
			List<TimePair> previousTrackTimePairs =
					isFirstDetectionStage ?
							Collections.singletonList(new TimePair(0, (transientMedia.getMediaType() == MediaType.AUDIO ? -1 : transientMedia.getLength() - 1))) :
							createTimePairsForTracks(
									redis.getTracks(transientJob.getId(), transientMedia.getId(), transientJob.getCurrentStage() - 1, 0));

			// Iterate through each of the actions and segment the media using the properties provided in that action.
			for(int actionIndex = 0; actionIndex < transientStage.getActions().size(); actionIndex++) {

				TransientAction transientAction = transientStage.getActions().get(actionIndex);
				SegmentingPlan segmentingPlan = createSegmentingPlan(transientAction.getProperties());
				Map<String,String> modifiedMap = new HashMap<>(transientAction.getProperties());

				// If the job is overriding properties related to flip, rotation, or ROI, we should reset all related
				// action properties to default.  We assume that when the user overrides one rotation/flip/roi
				// property for a piece of media, they are specifying all of the rotation/flip/roi properties they want
				// applied for this medium.
				for (String key : new String[]{MpfConstants.ROTATION_PROPERTY,MpfConstants.HORIZONTAL_FLIP_PROPERTY,
						MpfConstants.SEARCH_REGION_TOP_LEFT_X_DETECTION_PROPERTY,
						MpfConstants.SEARCH_REGION_TOP_LEFT_Y_DETECTION_PROPERTY,
						MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_X_DETECTION_PROPERTY,
						MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_Y_DETECTION_PROPERTY,
						MpfConstants.SEARCH_REGION_ENABLE_DETECTION_PROPERTY,
						MpfConstants.AUTO_ROTATE_PROPERTY, MpfConstants.AUTO_FLIP_PROPERTY}) {
					if (transientMedia.getMediaSpecificProperties().containsKey(key)) {
						modifiedMap.put(MpfConstants.AUTO_ROTATE_PROPERTY, "FALSE");
						modifiedMap.put(MpfConstants.AUTO_FLIP_PROPERTY, "FALSE");
						modifiedMap.put(MpfConstants.ROTATION_PROPERTY, "0");
						modifiedMap.put(MpfConstants.HORIZONTAL_FLIP_PROPERTY, "FALSE");
						modifiedMap.put(MpfConstants.SEARCH_REGION_TOP_LEFT_X_DETECTION_PROPERTY,"-1");
						modifiedMap.put(MpfConstants.SEARCH_REGION_TOP_LEFT_Y_DETECTION_PROPERTY,"-1");
						modifiedMap.put(MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_X_DETECTION_PROPERTY,"-1");
						modifiedMap.put(MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_Y_DETECTION_PROPERTY,"-1");
						modifiedMap.put(MpfConstants.SEARCH_REGION_ENABLE_DETECTION_PROPERTY, "FALSE");
						break;
					}
				}

				modifiedMap.putAll(transientMedia.getMediaSpecificProperties());

				List<AlgorithmPropertyProtocolBuffer.AlgorithmProperty> algorithmProperties = convertPropertiesMapToAlgorithmPropertiesList(modifiedMap);

				List<Message> detectionRequestMessages = getSegmenter(transientMedia.getMediaType()).createDetectionRequestMessages(transientMedia, new DetectionContext(transientJob.getId(), transientJob.getCurrentStage(), transientStage.getName(), actionIndex, transientAction.getName(), isFirstDetectionStage, algorithmProperties, previousTrackTimePairs, segmentingPlan));
				for(Message message : detectionRequestMessages) {
					message.setHeader(MpfHeaders.RECIPIENT_QUEUE, String.format("jms:MPF.%s_%s_REQUEST", transientStage.getActionType(), transientAction.getAlgorithm()));
					message.setHeader(MpfHeaders.JMS_REPLY_TO, StringUtils.replace(MpfEndpoints.COMPLETED_DETECTIONS, "jms:", ""));
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

	private MediaSegmenter getSegmenter(MediaType mediaType) {
		switch(mediaType) {
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

	protected SegmentingPlan createSegmentingPlan(Map<String, String> properties) {
		int targetSegmentLength = propertiesUtil.getTargetSegmentLength();
		int minSegmentLength = propertiesUtil.getMinSegmentLength();
		int samplingInterval = propertiesUtil.getSamplingInterval();
		int minGapBetweenSegments = propertiesUtil.getMinAllowableSegmentGap();

		if (properties != null) {
			for (Map.Entry<String, String> property : properties.entrySet()) {
				try {
					if (StringUtils.equalsIgnoreCase(property.getKey(), MpfConstants.TARGET_SEGMENT_LENGTH_PROPERTY)) {
						targetSegmentLength = Integer.parseInt(property.getValue());
					} else if (StringUtils.equalsIgnoreCase(property.getKey(), MpfConstants.MINIMUM_SEGMENT_LENGTH_PROPERTY)) {
						minSegmentLength = Integer.parseInt(property.getValue());
					} else if (StringUtils.equalsIgnoreCase(property.getKey(), MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)) {
						samplingInterval = Integer.parseInt(property.getValue());
					} else if (StringUtils.equalsIgnoreCase(property.getKey(), MpfConstants.MINIMUM_GAP_BETWEEN_SEGMENTS)) {
						minGapBetweenSegments = Integer.parseInt(property.getValue());
					}
				} catch (NumberFormatException numberFormatException) {
					// Failed to parse the preferred split size. At this point, we use the default split and minimum split size parameters from the properties.
					log.warn("Failed to parse the contents of '{}' with value '{}' as an integer. Assuming target segment length of '{}' AND minimum segment length of '{}'.",
							property.getKey(), property.getValue(), propertiesUtil.getTargetSegmentLength(), propertiesUtil.getMinSegmentLength());
					return new SegmentingPlan(propertiesUtil.getTargetSegmentLength(), propertiesUtil.getMinSegmentLength(), propertiesUtil.getSamplingInterval(), propertiesUtil.getMinAllowableSegmentGap());
				}
			}
		}

		return new SegmentingPlan(targetSegmentLength, minSegmentLength, samplingInterval, minGapBetweenSegments);
	}

	 /** Returns {@literal true} iff the current stage of this job is the first detection stage in the job. */
	 private boolean isFirstDetectionOperation(TransientJob transientJob) {
		boolean isFirst = false;
		for(int i = 0; i < transientJob.getPipeline().getStages().size(); i++) {

			// This is a detection stage.
			if(transientJob.getPipeline().getStages().get(i).getActionType() == ActionType.DETECTION) {
				// If this is the first detection stage, it must be true that the current stage's index is at most the current job stage's index.
				isFirst = (i >= transientJob.getCurrentStage());
				break;
			}
		}
		return isFirst;
	}


	private List<TimePair> createTimePairsForTracks(SortedSet<Track> tracks) {
		List<TimePair> timePairs = new ArrayList<>();
		for (Track track : tracks) {
			timePairs.add(new TimePair(track.getStartOffsetFrameInclusive(), track.getEndOffsetFrameInclusive() + 1));
		}
		Collections.sort(timePairs);
		return timePairs;
	}
}
