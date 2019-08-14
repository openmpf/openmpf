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

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.camel.ResponseProcessor;
import org.mitre.mpf.wfm.camel.operations.detection.trackmerging.TrackMergingContext;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
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
    private PropertiesUtil propertiesUtil;

    @Autowired
    private AggregateJobPropertiesUtil aggregateJobPropertiesUtil;

    @Autowired
    private JsonUtils jsonUtils;

    @Autowired
    private InProgressBatchJobsService inProgressJobs;

    public DetectionResponseProcessor() {
        super(DetectionProtobuf.DetectionResponse.class);
    }

    @Override
    public Object processResponse(long jobId, DetectionProtobuf.DetectionResponse detectionResponse, Map<String, Object> headers) throws WfmProcessingException {
        String logLabel = String.format("Job %d|%d|%d", jobId, detectionResponse.getStageIndex(), detectionResponse.getActionIndex());
        BatchJob job = inProgressJobs.getJob(jobId);
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
            Action action = job.getTransientPipeline().getAction(detectionResponse.getActionName());

            double confidenceThreshold = calculateConfidenceThreshold(action, job, media);

            // Process each type of response individually.
            processVideoResponses(jobId, detectionResponse, fps, confidenceThreshold);
            processAudioResponses(jobId, detectionResponse, confidenceThreshold);
            processImageResponses(jobId, detectionResponse, confidenceThreshold);
            processGenericResponses(jobId, detectionResponse, confidenceThreshold);
        }

        return jsonUtils.serialize(new TrackMergingContext(jobId, detectionResponse.getStageIndex()));
    }


    private double calculateConfidenceThreshold(Action action, BatchJob job, TransientMedia media) {
        String confidenceThresholdProperty = aggregateJobPropertiesUtil.calculateValue(
                MpfConstants.CONFIDENCE_THRESHOLD_PROPERTY, job, media, action);

        try {
            return Double.parseDouble(confidenceThresholdProperty);

        }
        catch (NumberFormatException e) {
            log.warn("Invalid search threshold specified: value should be numeric. Provided value was: "
                             + confidenceThresholdProperty);
            return job.getSystemPropertiesSnapshot().getConfidenceThreshold();

        }
    }



    private void processVideoResponses(long jobId, DetectionProtobuf.DetectionResponse detectionResponse, Float fps, double confidenceThreshold) {
        // Iterate through the videoResponse
        for (DetectionProtobuf.DetectionResponse.VideoResponse videoResponse : detectionResponse.getVideoResponsesList()) {
            // Begin iterating through the tracks that were found by the detector.
            for (DetectionProtobuf.VideoTrack objectTrack : videoResponse.getVideoTracksList()) {
                if (objectTrack.getConfidence() < confidenceThreshold) {
                    continue;
                }

                int startOffsetTime = (fps == null ? 0 : Math.round(objectTrack.getStartFrame() * 1000 / fps));
                int stopOffsetTime  = (fps == null ? 0 : Math.round(objectTrack.getStopFrame()  * 1000 / fps));

                ImmutableSortedSet<Detection> detections = objectTrack.getFrameLocationsList()
                        .stream()
                        .filter(flm -> flm.getImageLocation().getConfidence() >= confidenceThreshold)
                        .map(flm -> toDetection(flm, fps))
                        .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));

                if (!detections.isEmpty()) {
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
                            objectTrack.getConfidence(),
                            detections,
                            toMap(objectTrack.getDetectionPropertiesList()));

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
                if (objectTrack.getConfidence() >= confidenceThreshold) {
                    SortedMap<String, String> properties = toMap(objectTrack.getDetectionPropertiesList());
                    Detection detection = new Detection(
                            0,
                            0,
                            0,
                            0,
                            objectTrack.getConfidence(),
                            0,
                            objectTrack.getStartTime(),
                            properties);

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
                            objectTrack.getConfidence(),
                            ImmutableSortedSet.of(detection),
                            properties);
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
                    Track track = new Track(
                            jobId,
                            detectionResponse.getMediaId(),
                            detectionResponse.getStageIndex(),
                            detectionResponse.getActionIndex(),
                            0,
                            1,
                            0,
                            0,
                            imageResponse.getDetectionType(),
                            location.getConfidence(),
                            ImmutableSortedSet.of(toDetection(location, 0, 0)),
                            toMap(location.getDetectionPropertiesList()));
                    inProgressJobs.addTrack(track);
                }
            }
        }
    }

    private void processGenericResponses(long jobId, DetectionProtobuf.DetectionResponse detectionResponse, double confidenceThreshold) {
        // Iterate through the genericResponse
        for (DetectionProtobuf.DetectionResponse.GenericResponse genericResponse : detectionResponse.getGenericResponsesList()) {
            // Begin iterating through the tracks that were found by the detector.
            for (DetectionProtobuf.GenericTrack objectTrack : genericResponse.getGenericTracksList()) {
                if (objectTrack.getConfidence() >= confidenceThreshold) {
                    SortedMap<String, String> properties = toMap(objectTrack.getDetectionPropertiesList());

                    Detection detection = new Detection(
                            0,
                            0,
                            0,
                            0,
                            objectTrack.getConfidence(),
                            0,
                            0,
                            properties);

                    Track track1 = new Track(
                            jobId,
                            detectionResponse.getMediaId(),
                            detectionResponse.getStageIndex(),
                            detectionResponse.getActionIndex(),
                            0,
                            0,
                            0,
                            0,
                            genericResponse.getDetectionType(),
                            objectTrack.getConfidence(),
                            ImmutableSortedSet.of(detection),
                            properties);
                    inProgressJobs.addTrack(track1);

                }
            }
        }
    }


    private static Detection toDetection(DetectionProtobuf.VideoTrack.FrameLocationMap frameLocationMap, Float fps) {
        int time = fps == null ? 0 : Math.round(frameLocationMap.getFrame() * 1000 / fps);
        return toDetection(frameLocationMap.getImageLocation(), frameLocationMap.getFrame(), time);
    }


    private static Detection toDetection(DetectionProtobuf.ImageLocation location, int frameNumber, int time) {
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
        return properties.stream()
                .collect(ImmutableSortedMap.toImmutableSortedMap(
                        Comparator.naturalOrder(),
                        DetectionProtobuf.PropertyMap::getKey,
                        DetectionProtobuf.PropertyMap::getValue));
    }
}
