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

import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.camel.ResponseProcessor;
import org.mitre.mpf.wfm.camel.operations.detection.trackmerging.TrackMergingContext;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.JobStatus;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.pipeline.PipelinesService;
import org.mitre.mpf.wfm.pipeline.xml.ActionDefinition;
import org.mitre.mpf.wfm.pipeline.xml.AlgorithmDefinition;
import org.mitre.mpf.wfm.pipeline.xml.PropertyDefinition;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Processes the responses which have been returned from a detection component. */
@Component(DetectionResponseProcessor.REF)
public class DetectionResponseProcessor
		extends ResponseProcessor<DetectionProtobuf.DetectionResponse> {
	public static final String REF = "detectionResponseProcessor";
	private static final Logger log = LoggerFactory.getLogger(DetectionResponseProcessor.class);

	@Autowired
	private PipelinesService pipelinesService;

	@Autowired
	private PropertiesUtil propertiesUtil;

	public DetectionResponseProcessor() {
		clazz = DetectionProtobuf.DetectionResponse.class;
	}

	@Override
	public Object processResponse(long jobId, DetectionProtobuf.DetectionResponse detectionResponse, Map<String, Object> headers) throws WfmProcessingException {
		String logLabel = String.format("Job %d|%d|%d", jobId, detectionResponse.getStageIndex(), detectionResponse.getActionIndex());

		Float fps = null;
		TransientMedia media = loadMedia(jobId, detectionResponse.getMediaId());
		if (DetectionProtobuf.DetectionResponse.DataType.VIDEO.equals(detectionResponse.getDataType())) {
			if (media.getMetadata("FPS") != null) {
				fps = Float.valueOf(media.getMetadata("FPS"));
				log.debug("FPS of {}", fps);
			}
		}

		log.debug("[{}] Response received for Media #{} [{}-{}]. Stage: '{}'. Action: '{}'.",
				logLabel,
				detectionResponse.getMediaId(),
				detectionResponse.getStartIndex(),
				detectionResponse.getStopIndex(),
				detectionResponse.getStageName(),
				detectionResponse.getActionName());

		if(detectionResponse.getError() != DetectionProtobuf.DetectionError.NO_DETECTION_ERROR) {

			// Some error occurred during detection. Store this error.
			if(detectionResponse.getError() != DetectionProtobuf.DetectionError.REQUEST_CANCELLED) {
			    log.warn("[{}] Encountered a detection error while processing Media #{} [{}, {}]: {}", logLabel, detectionResponse.getMediaId(), detectionResponse.getStartIndex(), detectionResponse.getStopIndex(), detectionResponse.getError());
			} else {
				log.debug("[{}] Encountered a detection error while processing Media #{} [{}, {}]: {}", logLabel, detectionResponse.getMediaId(), detectionResponse.getStartIndex(), detectionResponse.getStopIndex(), detectionResponse.getError());
			}

			DetectionProcessingError detectionProcessingError = new DetectionProcessingError(jobId, detectionResponse.getMediaId(), detectionResponse.getStageIndex(), detectionResponse.getActionIndex(), detectionResponse.getStartIndex(), detectionResponse.getStopIndex(), Objects.toString(detectionResponse.getError()));
			if(!redis.addDetectionProcessingError(detectionProcessingError)) {

				// We failed to persist the detection error. The user will have no record of this error except this log message.
				log.warn("[{}] Failed to persist {} in the transient data store. The results of this job are unreliable.", logLabel, detectionProcessingError);
			}

			redis.setJobStatus(jobId, JobStatus.IN_PROGRESS_ERRORS);
		}

		if (detectionResponse.getAudioResponsesCount() == 0
				&& detectionResponse.getVideoResponsesCount() == 0
				&& detectionResponse.getImageResponsesCount() == 0
				&& detectionResponse.getGenericResponsesCount() == 0  ) {

			// The detector did not find any tracks in the medium between the given range. This isn't an error, but it is worth logging.
			log.debug("[{}] No tracks were found in Media #{} [{}, {}].", logLabel, detectionResponse.getMediaId(), detectionResponse.getStageIndex(), detectionResponse.getStopIndex());


		} else {
			// Look for a confidence threshold.  If confidence threshold is defined, only return detections above the threshold.
			ActionDefinition action = pipelinesService.getAction(detectionResponse.getActionName());
			TransientJob job = redis.getJob(jobId, detectionResponse.getMediaId());

			double confidenceThreshold = calculateConfidenceThreshold(action, job, media);

			// Process each type of response individually.
			processVideoResponses(jobId, detectionResponse, fps, confidenceThreshold);
			processAudioResponses(jobId, detectionResponse, fps, confidenceThreshold);
			processImageResponses(jobId, detectionResponse, confidenceThreshold);

		}

		return jsonUtils.serialize(new TrackMergingContext(jobId, detectionResponse.getStageIndex()));
	}

		// transientJob coming from REDIS
	private double calculateConfidenceThreshold(ActionDefinition action, TransientJob job, TransientMedia media) {
		double confidenceThreshold = propertiesUtil.getConfidenceThreshold();
		String confidenceThresholdProperty = AggregateJobPropertiesUtil.calculateValue(MpfConstants.CONFIDENCE_THRESHOLD_PROPERTY,
				action.getProperties(), job.getOverriddenJobProperties(), action, job.getOverriddenAlgorithmProperties(),
				media.getMediaSpecificProperties());

		if (confidenceThresholdProperty == null) {
			AlgorithmDefinition algorithm = pipelinesService.getAlgorithm(action);
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


	private TransientMedia loadMedia(long jobId, long mediaId) throws WfmProcessingException {
		List<TransientMedia> mediaList = redis.getJob(jobId, mediaId).getMedia();
		for (TransientMedia item : mediaList) {
			if (item.getId() == mediaId) {
				return item;
			}
		}

		return null;
	}
	private void processVideoResponses(long jobId, DetectionProtobuf.DetectionResponse detectionResponse, Float fps, double confidenceThreshold) {
		// Iterate through the videoResponse
		for (DetectionProtobuf.DetectionResponse.VideoResponse videoResponse : detectionResponse.getVideoResponsesList()) {
			// Begin iterating through the tracks that were found by the detector.
			for (DetectionProtobuf.DetectionResponse.VideoResponse.VideoTrack objectTrack : videoResponse.getVideoTracksList()) {

				int startOffsetTime = (fps == null ? 0 : Math.round(objectTrack.getStartFrame() * 1000 / fps));
				int stopOffsetTime  = (fps == null ? 0 : Math.round(objectTrack.getStopFrame()  * 1000 / fps));

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
						videoResponse.getDetectionType());


				// Iterate through the list of detections. It is assumed that detections are not sorted in a meaningful way.
				for (DetectionProtobuf.DetectionResponse.VideoResponse.VideoTrack.FrameLocationMap locationMap : objectTrack.getFrameLocationsList()) {
					DetectionProtobuf.DetectionResponse.ImageLocation location = locationMap.getImageLocation();

					if (location.getConfidence() >= confidenceThreshold) {

						int offsetTime = (fps == null ? 0 : Math.round(locationMap.getFrame() * 1000 / fps));

						track.getDetections().add(
								generateTrack(location, locationMap.getFrame(), offsetTime));
					}
				}

				if (!track.getDetections().isEmpty()) {
					track.setExemplar(findExemplar(track));
					if(!redis.addTrack(track)) {
						log.warn("Failed to add the track '{}'.", track);
					}
				}
			}
		}
	}

	private void processAudioResponses(long jobId, DetectionProtobuf.DetectionResponse detectionResponse, Float fps, double confidenceThreshold) {
		// Iterate through the videoResponse
		for (DetectionProtobuf.DetectionResponse.AudioResponse audioResponse : detectionResponse.getAudioResponsesList()) {
			// Begin iterating through the tracks that were found by the detector.
			for (DetectionProtobuf.DetectionResponse.AudioResponse.AudioTrack objectTrack : audioResponse.getAudioTracksList()) {

				int startOffsetFrame = (fps == null ? 0 : Math.round(objectTrack.getStartTime() * fps / 1000));
				int stopOffsetFrame  = (fps == null ? 0 : Math.round(objectTrack.getStopTime()  * fps / 1000));

				// Create a new Track object.
				Track track = new Track(
						jobId,
						detectionResponse.getMediaId(),
						detectionResponse.getStageIndex(),
						detectionResponse.getActionIndex(),
						startOffsetFrame,
						stopOffsetFrame,
						objectTrack.getStartTime(),
						objectTrack.getStopTime(),
						audioResponse.getDetectionType());


					if (objectTrack.getConfidence() >= confidenceThreshold) {
						TreeMap<String, String> detectionProperties = new TreeMap<>();
						for (DetectionProtobuf.PropertyMap item : objectTrack.getDetectionPropertiesList()) {
							detectionProperties.put(item.getKey(), item.getValue());
						}
						track.getDetections().add(
								new Detection(
										0,
										1,
										0,
										0,
										objectTrack.getConfidence(),
										startOffsetFrame,
										objectTrack.getStartTime(),
										detectionProperties));
					}

				if (!track.getDetections().isEmpty()) {
					track.setExemplar(findExemplar(track));
					if(!redis.addTrack(track)) {
						log.warn("Failed to add the track '{}'.", track);
					}
				}
			}
		}
	}

	private void processImageResponses(long jobId, DetectionProtobuf.DetectionResponse detectionResponse, double confidenceThreshold) {
		// Iterate through the videoResponse
		for (DetectionProtobuf.DetectionResponse.ImageResponse imageResponse : detectionResponse.getImageResponsesList()) {
			// Begin iterating through the tracks that were found by the detector.



				// Iterate through the list of detections. It is assumed that detections are not sorted in a meaningful way.
				for (DetectionProtobuf.DetectionResponse.ImageLocation location : imageResponse.getImageLocationsList()) {
					if (location.getConfidence() >= confidenceThreshold) {
						// Create a new Track object.
						Track track = new Track(
								jobId,
								detectionResponse.getMediaId(),
								detectionResponse.getStageIndex(),
								detectionResponse.getActionIndex(),
								0,
								1,
								imageResponse.getDetectionType());
						track.getDetections().add(
								generateTrack(location, 0, 0));
						if (!track.getDetections().isEmpty()) {
							track.setExemplar(findExemplar(track));
							if(!redis.addTrack(track)) {
								log.warn("Failed to add the track '{}'.", track);
							}
						}
					}
				}



			}
	}

	private Detection findExemplar(Track track) {
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

	private Detection generateTrack(DetectionProtobuf.DetectionResponse.ImageLocation location, int frameNumber, int time) {
		TreeMap<String, String> detectionProperties = new TreeMap<>();
		for (DetectionProtobuf.PropertyMap item : location.getDetectionPropertiesList()) {
			detectionProperties.put(item.getKey(), item.getValue());
		}
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
}
