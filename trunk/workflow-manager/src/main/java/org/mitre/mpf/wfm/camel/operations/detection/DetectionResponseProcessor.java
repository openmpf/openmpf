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

import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.camel.ResponseProcessor;
import org.mitre.mpf.wfm.camel.operations.detection.trackmerging.TrackMergingContext;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.pipeline.xml.ActionDefinition;
import org.mitre.mpf.wfm.pipeline.xml.AlgorithmDefinition;
import org.mitre.mpf.wfm.pipeline.xml.PropertyDefinition;
import org.mitre.mpf.wfm.service.PipelineService;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/** Processes the responses which have been returned from a detection component. */
@Component(DetectionResponseProcessor.REF)
public class DetectionResponseProcessor
		extends ResponseProcessor<DetectionProtobuf.DetectionResponse> {
	public static final String REF = "detectionResponseProcessor";
	private static final Logger log = LoggerFactory.getLogger(DetectionResponseProcessor.class);

	@Autowired
	private PipelineService pipelineService;

	public DetectionResponseProcessor() {
		clazz = DetectionProtobuf.DetectionResponse.class;
	}

	@Override
	public Object processResponse(long jobId, DetectionProtobuf.DetectionResponse detectionResponse, Map<String, Object> headers) throws WfmProcessingException {

		int totalResponses = detectionResponse.getVideoResponsesCount() +
				detectionResponse.getAudioResponsesCount() +
				detectionResponse.getImageResponsesCount() +
				detectionResponse.getGenericResponsesCount();

		if (totalResponses > 1) {
			throw new WfmProcessingException(
					String.format("Unsupported operation. More than one DetectionResponse type found for job {}: {}",
							jobId, detectionResponse)); // TODO: Test this
		}

		if (totalResponses > 0) {

			// Look for a confidence threshold.  If confidence threshold is defined, only return detections above the threshold.
			ActionDefinition action = pipelineService.getAction(detectionResponse.getActionName());
			TransientJob job = redis.getJob(jobId, detectionResponse.getMediaId());
			TransientMedia media = loadMedia(jobId, detectionResponse.getMediaId());

			double confidenceThreshold = calculateConfidenceThreshold(action, job, media);

			if (detectionResponse.getVideoResponsesCount() != 0) {
				processVideoResponse(jobId, detectionResponse, detectionResponse.getVideoResponses(0), confidenceThreshold, media);

			} else if (detectionResponse.getAudioResponsesCount() != 0) {
				processAudioResponse(jobId, detectionResponse, detectionResponse.getAudioResponses(0), confidenceThreshold);

			} else if (detectionResponse.getImageResponsesCount() != 0) {
				processImageResponse(jobId, detectionResponse, detectionResponse.getImageResponses(0), confidenceThreshold);

			} else {
				processGenericResponse(jobId, detectionResponse, detectionResponse.getGenericResponses(0), confidenceThreshold);
			}
		} else {
			String mediaLabel = getBasicMediaLabel(detectionResponse);
			log.debug("[{}] Response received, but no tracks were found for {}.", getLogLabel(jobId, detectionResponse), mediaLabel);
			checkErrors(jobId, mediaLabel, detectionResponse, 0, 0, 0, 0);
		}

		return jsonUtils.serialize(new TrackMergingContext(jobId, detectionResponse.getStageIndex()));
	}

	private double calculateConfidenceThreshold(ActionDefinition action, TransientJob job, TransientMedia media) {
		double confidenceThreshold = job.getDetectionSystemPropertiesSnapshot().getConfidenceThreshold();
		String confidenceThresholdProperty = AggregateJobPropertiesUtil.calculateValue(
				MpfConstants.CONFIDENCE_THRESHOLD_PROPERTY,
				action.getProperties(),
				job.getOverriddenJobProperties(),
				action,
				job.getOverriddenAlgorithmProperties(),
				media.getMediaSpecificProperties()).getValue();

		if (confidenceThresholdProperty == null) {
			AlgorithmDefinition algorithm = pipelineService.getAlgorithm(action);
			PropertyDefinition confidenceAlgorithmDef = algorithm.getProvidesCollection().getAlgorithmProperty(MpfConstants.CONFIDENCE_THRESHOLD_PROPERTY);
			if (confidenceAlgorithmDef != null) {
				confidenceThresholdProperty = confidenceAlgorithmDef.getDefaultValue();
			}
		}
		if (confidenceThresholdProperty != null) {
			try {
				confidenceThreshold = Double.valueOf(confidenceThresholdProperty);
			} catch (NumberFormatException nfe) {
				log.warn("Invalid search threshold specified: value should be numeric.  Provided value was: " + confidenceThresholdProperty);
			}
		}
		return confidenceThreshold;
	}

	// TODO: Improve efficiency. Get the media from Redis using the jobId and mediaId directly.
	private TransientMedia loadMedia(long jobId, long mediaId) throws WfmProcessingException {
		List<TransientMedia> mediaList = redis.getJob(jobId, mediaId).getMedia();
		for (TransientMedia item : mediaList) {
			if (item.getId() == mediaId) {
				return item;
			}
		}
		return null;
	}

	private void processVideoResponse(long jobId, DetectionProtobuf.DetectionResponse detectionResponse,
									   DetectionProtobuf.DetectionResponse.VideoResponse videoResponse,
									   double confidenceThreshold, TransientMedia media) {

		Float fps = null;
		if (media.getMetadata("FPS") != null) {
			fps = Float.valueOf(media.getMetadata("FPS"));
		}

		int startFrame = videoResponse.getStartFrame();
		int stopFrame = videoResponse.getStopFrame();
		int startTime = convertFrameToTime(startFrame, fps);
		int stopTime = convertFrameToTime(stopFrame, fps);

		String mediaLabel = String.format("Media #{}, Frames: {}-{}. Stage: '{}', Action: '{}'",
				detectionResponse.getMediaId(),
				startFrame,
				stopFrame,
				detectionResponse.getStageName(),
				detectionResponse.getActionName());
		log.debug("[{}] Response received for {}.", getLogLabel(jobId, detectionResponse), mediaLabel);

		checkErrors(jobId, mediaLabel, detectionResponse, startFrame, stopFrame, startTime, stopTime);

		// Begin iterating through the tracks that were found by the detector.
		for (DetectionProtobuf.VideoTrack objectTrack : videoResponse.getVideoTracksList()) {

			int startOffsetTime = convertFrameToTime(objectTrack.getStartFrame(), fps);
			int stopOffsetTime = convertFrameToTime(objectTrack.getStopFrame(), fps);

			// Create a new Track object.
			Track track = new Track(
					jobId,
					detectionResponse.getMediaId(),
					detectionResponse.getStageIndex(),
					detectionResponse.getActionIndex(),
					objectTrack.getStartFrame(),
					objectTrack.getStopFrame(),
					startOffsetTime,
					stopOffsetTime,
					videoResponse.getDetectionType(),
					objectTrack.getConfidence());

			track.getTrackProperties().putAll(toMap(objectTrack.getDetectionPropertiesList()));

			// Iterate through the list of detections. It is assumed that detections are not sorted in a meaningful way.
			for (DetectionProtobuf.VideoTrack.FrameLocationMap locationMap : objectTrack.getFrameLocationsList()) {
				DetectionProtobuf.ImageLocation location = locationMap.getImageLocation();

				if (location.getConfidence() >= confidenceThreshold) {

					int offsetTime = (fps == null ? 0 : Math.round(locationMap.getFrame() * 1000 / fps));

					track.getDetections().add(
							generateTrack(location, locationMap.getFrame(), offsetTime));
				}
			}

			if (!track.getDetections().isEmpty()) {
				track.setExemplar(findExemplar(track));
				redis.addTrack(track);
			}
		}
	}

	private void processAudioResponse(long jobId, DetectionProtobuf.DetectionResponse detectionResponse,
									   DetectionProtobuf.DetectionResponse.AudioResponse audioResponse,
									   double confidenceThreshold) {

		int startTime = audioResponse.getStartTime();
		int stopTime = audioResponse.getStartTime();

		String mediaLabel = String.format("Media #{}, Time: {}-{}, Stage: '{}', Action: '{}'",
				detectionResponse.getMediaId(),
				startTime,
				stopTime,
				detectionResponse.getActionName());
		log.debug("[{}] Response received for {}.", getLogLabel(jobId, detectionResponse), mediaLabel);

		checkErrors(jobId, mediaLabel, detectionResponse, 0, 0, startTime, stopTime);

		// Begin iterating through the tracks that were found by the detector.
		for (DetectionProtobuf.AudioTrack objectTrack : audioResponse.getAudioTracksList()) {
			// Create a new Track object.
			Track track = new Track(
					jobId,
					detectionResponse.getMediaId(),
					detectionResponse.getStageIndex(),
					detectionResponse.getActionIndex(),
					0,
					0,
					objectTrack.getStartTime(),
					objectTrack.getStopTime(),
					audioResponse.getDetectionType(),
					objectTrack.getConfidence());

			SortedMap<String, String> properties = toMap(objectTrack.getDetectionPropertiesList());
			track.getTrackProperties().putAll(properties);

			if (objectTrack.getConfidence() >= confidenceThreshold) {
				track.getDetections().add(
						new Detection(
								0,
								0,
								0,
								0,
								objectTrack.getConfidence(),
								0,
								objectTrack.getStartTime(),
								properties));
			}

			if (!track.getDetections().isEmpty()) {
				track.setExemplar(findExemplar(track));
				redis.addTrack(track);
			}
		}
	}

	private void processImageResponse(long jobId, DetectionProtobuf.DetectionResponse detectionResponse,
									   DetectionProtobuf.DetectionResponse.ImageResponse imageResponse,
									   double confidenceThreshold) {

		String mediaLabel = getBasicMediaLabel(detectionResponse);
		log.debug("[{}] Response received for {}.", getLogLabel(jobId, detectionResponse), mediaLabel);

		checkErrors(jobId, mediaLabel, detectionResponse, 0, 1, 0, 0);

		// Iterate through the list of detections. It is assumed that detections are not sorted in a meaningful way.
		for (DetectionProtobuf.ImageLocation location : imageResponse.getImageLocationsList()) {
			if (location.getConfidence() >= confidenceThreshold) {
				// Create a new Track object.
				Track track = new Track(
						jobId,
						detectionResponse.getMediaId(),
						detectionResponse.getStageIndex(),
						detectionResponse.getActionIndex(),
						0,
						1,
						imageResponse.getDetectionType(),
						location.getConfidence());

				track.getTrackProperties().putAll(toMap(location.getDetectionPropertiesList()));

				track.getDetections().add(
						generateTrack(location, 0, 0));
				if (!track.getDetections().isEmpty()) {
					track.setExemplar(findExemplar(track));
					redis.addTrack(track);
				}
			}
		}
}

	private void processGenericResponse(long jobId, DetectionProtobuf.DetectionResponse detectionResponse,
										 DetectionProtobuf.DetectionResponse.GenericResponse genericResponse,
										 double confidenceThreshold) {

		String mediaLabel = getBasicMediaLabel(detectionResponse);
		log.debug("[{}] Response received for {}.", getLogLabel(jobId, detectionResponse), mediaLabel);

		checkErrors(jobId, mediaLabel, detectionResponse, 0, 0, 0, 0);

		// Begin iterating through the tracks that were found by the detector.
		for (DetectionProtobuf.GenericTrack objectTrack : genericResponse.getGenericTracksList()) {
			// Create a new Track object.
			Track track = new Track(
					jobId,
					detectionResponse.getMediaId(),
					detectionResponse.getStageIndex(),
					detectionResponse.getActionIndex(),
					0,
					0,
					0,
					0,
					genericResponse.getDetectionType(),
					objectTrack.getConfidence());

			SortedMap<String, String> properties = toMap(objectTrack.getDetectionPropertiesList());
			track.getTrackProperties().putAll(properties);

			if (objectTrack.getConfidence() >= confidenceThreshold) {
				track.getDetections().add(
						new Detection(
								0,
								0,
								0,
								0,
								objectTrack.getConfidence(),
								0,
								0,
								properties));
			}

			if (!track.getDetections().isEmpty()) {
				track.setExemplar(findExemplar(track));
				redis.addTrack(track);
			}
		}
	}

	private void checkErrors(long jobId, String mediaLabel, DetectionProtobuf.DetectionResponse detectionResponse,
							 int startFrame, int stopFrame, int startTime, int stopTime) {

		if (detectionResponse.getError() != DetectionProtobuf.DetectionError.NO_DETECTION_ERROR) {
			String errorMessage;
			// Some error occurred during detection. Store this error.
			if (detectionResponse.getError() == DetectionProtobuf.DetectionError.REQUEST_CANCELLED) {
				log.debug("[{}] Encountered a detection error while processing {}: {}",
						getLogLabel(jobId, detectionResponse), mediaLabel, detectionResponse.getError());
				redis.setJobStatus(jobId, BatchJobStatusType.CANCELLING);
				errorMessage = MpfConstants.REQUEST_CANCELLED;
			}
			else {
				log.warn("[{}] Encountered a detection error while processing {}: {}",
						getLogLabel(jobId, detectionResponse), mediaLabel, detectionResponse.getError());
				redis.setJobStatus(jobId, BatchJobStatusType.IN_PROGRESS_ERRORS);
				errorMessage = Objects.toString(detectionResponse.getError());
			}

			redis.addDetectionProcessingError(new DetectionProcessingError(
					jobId,
					detectionResponse.getMediaId(),
					detectionResponse.getStageIndex(),
					detectionResponse.getActionIndex(),
					startFrame,
					stopFrame,
					startTime,
					stopTime,
					errorMessage));
		}
	}

	private static Detection findExemplar(Track track) {
		// Iterate through all of the detections in the track. Find the index of the one that has the greatest confidence.
		Detection exemplar = null;

		for (Detection detection : track.getDetections()) {
			if (exemplar == null) {
				exemplar = detection;
			} else if (exemplar.getConfidence() < detection.getConfidence()) {
				exemplar = detection;
			}
		}
		return exemplar;
	}

	private static Detection generateTrack(DetectionProtobuf.ImageLocation location, int frameNumber, int time) {
		SortedMap<String, String> detectionProperties = toMap(location.getDetectionPropertiesList());
		return new Detection(
				location.getXLeftUpper(),
				location.getYLeftUpper(),
				location.getWidth(),
				location.getHeight(),
				location.getConfidence(),
				frameNumber,
				time,
				detectionProperties);
	}

	private static SortedMap<String, String> toMap(Collection<DetectionProtobuf.PropertyMap> properties) {
	    SortedMap<String, String> result = new TreeMap<>();
	    for (DetectionProtobuf.PropertyMap property : properties) {
	    	result.put(property.getKey(), property.getValue());
	    }
	    return result;
	}

	private static String getLogLabel(long jobId, DetectionProtobuf.DetectionResponse detectionResponse) {
		return String.format("Job %d|%d|%d", jobId, detectionResponse.getStageIndex(), detectionResponse.getActionIndex());

	}

	private static String getBasicMediaLabel(DetectionProtobuf.DetectionResponse detectionResponse) {
		return String.format("Media #{}, Stage: '{}', Action: '{}'",
				detectionResponse.getMediaId(), detectionResponse.getStageName(), detectionResponse.getActionName());
	}

	private static int convertFrameToTime(int frame, Float fps) {
		return fps == null ? 0 : Math.round(frame * 1000 / fps); // in milliseconds
	}
}
