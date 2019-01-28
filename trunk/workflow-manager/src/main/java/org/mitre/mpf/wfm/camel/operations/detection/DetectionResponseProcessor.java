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
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.pipeline.xml.ActionDefinition;
import org.mitre.mpf.wfm.pipeline.xml.AlgorithmDefinition;
import org.mitre.mpf.wfm.pipeline.xml.PropertyDefinition;
import org.mitre.mpf.wfm.service.PipelineService;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.JsonUtils;
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
	private JsonUtils jsonUtils;

	@Autowired
	private PipelineService pipelineService;

	@Autowired
	private InProgressBatchJobsService inProgressJobs;

	public DetectionResponseProcessor() {
		super(DetectionProtobuf.DetectionResponse.class);
	}

	@Override
	public Object processResponse(long jobId, DetectionProtobuf.DetectionResponse detectionResponse, Map<String, Object> headers) throws WfmProcessingException {
		String logLabel = String.format("Job %d|%d|%d", jobId, detectionResponse.getStageIndex(), detectionResponse.getActionIndex());
		TransientJob job = inProgressJobs.getJob(jobId);
		Float fps = null;
		TransientMedia media = job.getMedia()
				.stream()
                .filter(m -> m.getId() == detectionResponse.getMediaId())
				.findAny()
				.orElseThrow(() -> new IllegalStateException("Unable to locate media with id: " +
						                                             detectionResponse.getMediaId()));
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

		if (detectionResponse.getError() != DetectionProtobuf.DetectionError.NO_DETECTION_ERROR) {
			String errorMessage;
			// Some error occurred during detection. Store this error.
			if (detectionResponse.getError() == DetectionProtobuf.DetectionError.REQUEST_CANCELLED) {
				log.debug("[{}] Encountered a detection error while processing Media #{} [{}, {}]: {}",
				          logLabel, detectionResponse.getMediaId(), detectionResponse.getStartIndex(),
				          detectionResponse.getStopIndex(), detectionResponse.getError());
				inProgressJobs.setJobStatus(jobId, BatchJobStatusType.CANCELLING);
				errorMessage = MpfConstants.REQUEST_CANCELLED;
			}
			else {
				log.warn("[{}] Encountered a detection error while processing Media #{} [{}, {}]: {}", logLabel,
				         detectionResponse.getMediaId(), detectionResponse.getStartIndex(),
				         detectionResponse.getStopIndex(), detectionResponse.getError());
				inProgressJobs.setJobStatus(jobId, BatchJobStatusType.IN_PROGRESS_ERRORS);
				errorMessage = Objects.toString(detectionResponse.getError());
			}
			inProgressJobs.addDetectionProcessingError(new DetectionProcessingError(
					jobId,
					detectionResponse.getMediaId(),
					detectionResponse.getStageIndex(),
					detectionResponse.getActionIndex(),
					detectionResponse.getStartIndex(),
					detectionResponse.getStopIndex(),
					errorMessage));
		}

		if (detectionResponse.getAudioResponsesCount() == 0
				&& detectionResponse.getVideoResponsesCount() == 0
				&& detectionResponse.getImageResponsesCount() == 0
				&& detectionResponse.getGenericResponsesCount() == 0  ) {

			// The detector did not find any tracks in the medium between the given range. This isn't an error, but it is worth logging.
			log.debug("[{}] No tracks were found in Media #{} [{}, {}].", logLabel, detectionResponse.getMediaId(), detectionResponse.getStageIndex(), detectionResponse.getStopIndex());


		} else {
			// Look for a confidence threshold.  If confidence threshold is defined, only return detections above the threshold.
			ActionDefinition action = pipelineService.getAction(detectionResponse.getActionName());

			double confidenceThreshold = calculateConfidenceThreshold(action, job, media);

			// Process each type of response individually.
			processVideoResponses(jobId, detectionResponse, fps, confidenceThreshold);
			processAudioResponses(jobId, detectionResponse, confidenceThreshold);
			processImageResponses(jobId, detectionResponse, confidenceThreshold);
			processGenericResponses(jobId, detectionResponse, confidenceThreshold);
		}

		return jsonUtils.serialize(new TrackMergingContext(jobId, detectionResponse.getStageIndex()));
	}

	// transientJob coming from REDIS
	private double calculateConfidenceThreshold(ActionDefinition action, TransientJob job, TransientMedia media) {
		double confidenceThreshold = job.getSystemPropertiesSnapshot().getConfidenceThreshold();
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



	private void processVideoResponses(long jobId, DetectionProtobuf.DetectionResponse detectionResponse, Float fps, double confidenceThreshold) {
		// Iterate through the videoResponse
		for (DetectionProtobuf.DetectionResponse.VideoResponse videoResponse : detectionResponse.getVideoResponsesList()) {
			// Begin iterating through the tracks that were found by the detector.
			for (DetectionProtobuf.VideoTrack objectTrack : videoResponse.getVideoTracksList()) {

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
					inProgressJobs.addTrack(track);
				}
			}
		}
	}

	private void processAudioResponses(long jobId, DetectionProtobuf.DetectionResponse detectionResponse, double confidenceThreshold) {
		// Iterate through the audioResponse
		for (DetectionProtobuf.DetectionResponse.AudioResponse audioResponse : detectionResponse.getAudioResponsesList()) {
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
					inProgressJobs.addTrack(track);
				}
			}
		}
	}

	private void processImageResponses(long jobId, DetectionProtobuf.DetectionResponse detectionResponse, double confidenceThreshold) {
		// Iterate through the imageResponse
		for (DetectionProtobuf.DetectionResponse.ImageResponse imageResponse : detectionResponse.getImageResponsesList()) {
			// Begin iterating through the tracks that were found by the detector.

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
						inProgressJobs.addTrack(track);
					}
				}
			}
		}
	}

	private void processGenericResponses(long jobId, DetectionProtobuf.DetectionResponse detectionResponse, double confidenceThreshold) {
		// Iterate through the genericResponse
		for (DetectionProtobuf.DetectionResponse.GenericResponse genericResponse : detectionResponse.getGenericResponsesList()) {
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
					inProgressJobs.addTrack(track);
				}
			}
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
}
