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

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.camel.ResponseProcessor;
import org.mitre.mpf.wfm.camel.operations.mediainspection.MediaInspectionHelper;
import org.mitre.mpf.wfm.data.IdGenerator;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.DetectionProcessingError;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.service.TaskMergingManager;
import org.mitre.mpf.wfm.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.inject.Inject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/** Processes the responses which have been returned from a detection component. */
@Component(DetectionResponseProcessor.REF)
public class DetectionResponseProcessor
        extends ResponseProcessor<DetectionProtobuf.DetectionResponse> {
    public static final String REF = "detectionResponseProcessor";

    private static final Logger log = LoggerFactory.getLogger(DetectionResponseProcessor.class);

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;

    private final InProgressBatchJobsService _inProgressJobs;

    private final MediaInspectionHelper _mediaInspectionHelper;

    private final TaskMergingManager _taskMergingManager;

    @Inject
    public DetectionResponseProcessor(AggregateJobPropertiesUtil aggregateJobPropertiesUtil,
                                      InProgressBatchJobsService inProgressJobs,
                                      MediaInspectionHelper mediaInspectionHelper,
                                      TaskMergingManager taskMergingManager) {
        super(inProgressJobs, DetectionProtobuf.DetectionResponse.class);
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
        _inProgressJobs = inProgressJobs;
        _mediaInspectionHelper = mediaInspectionHelper;
        _taskMergingManager = taskMergingManager;
    }

    @Override
    public Object processResponse(long jobId,
                                  DetectionProtobuf.DetectionResponse detectionResponse,
                                  Map<String, Object> headers) throws WfmProcessingException {
        int totalResponses = detectionResponse.getVideoResponsesCount() +
                detectionResponse.getAudioResponsesCount() +
                detectionResponse.getImageResponsesCount() +
                detectionResponse.getGenericResponsesCount();

        if (totalResponses > 1) {
            throw new WfmProcessingException(
                    // Camel will print out the exchange, including the message body content, in the stack trace.
                    String.format("Unsupported operation. More than one DetectionResponse sub-message found for job %d.", jobId));
        }
        if (totalResponses == 0) {
            String mediaLabel = getBasicMediaLabel(detectionResponse);
            log.warn("Response received, but no tracks were found for {}.", mediaLabel);
            checkErrors(jobId, mediaLabel, detectionResponse, 0, 0, 0, 0);
            return null;
        }

        BatchJob job = _inProgressJobs.getJob(jobId);

        Media media = job.getMedia()
                .stream()
                .filter(m -> m.getId() == detectionResponse.getMediaId())
                .findAny()
                .orElseThrow(() -> new IllegalStateException("Unable to locate media with id: " +
                        detectionResponse.getMediaId()));
        Action action = job.getPipelineElements().getAction(detectionResponse.getActionName());
        double confidenceThreshold = calculateConfidenceThreshold(action, job, media);
        var trackType = job.getPipelineElements().getAlgorithm(action.getAlgorithm()).getTrackType();
        var mergedTaskIdx = _taskMergingManager.getMergedTaskIndex(
                job, media,
                detectionResponse.getTaskIndex(),
                detectionResponse.getActionIndex(),
                headers);

        if (detectionResponse.getVideoResponsesCount() != 0) {
            var exemplarPolicy = _aggregateJobPropertiesUtil.getValue(
                    ExemplarPolicyUtil.PROPERTY, job, media, action);
            processVideoResponse(
                    jobId,
                    detectionResponse,
                    detectionResponse.getVideoResponses(0),
                    confidenceThreshold,
                    media,
                    exemplarPolicy,
                    trackType,
                    mergedTaskIdx);
        }
        else if (detectionResponse.getAudioResponsesCount() != 0) {
            processAudioResponse(
                    jobId,
                    detectionResponse,
                    detectionResponse.getAudioResponses(0),
                    confidenceThreshold,
                    trackType,
                    mergedTaskIdx);
        }
        else if (detectionResponse.getImageResponsesCount() != 0) {
            processImageResponse(
                    jobId,
                    detectionResponse,
                    detectionResponse.getImageResponses(0),
                    confidenceThreshold,
                    trackType,
                    mergedTaskIdx);
        }
        else {
            processGenericResponse(
                    jobId,
                    detectionResponse,
                    detectionResponse.getGenericResponses(0),
                    confidenceThreshold,
                    trackType,
                    mergedTaskIdx);
        }
        return null;
    }

    private double calculateConfidenceThreshold(Action action, BatchJob job, Media media) {
        String confidenceThresholdProperty = _aggregateJobPropertiesUtil.getValue(
                MpfConstants.CONFIDENCE_THRESHOLD_PROPERTY, job, media, action);

        try {
            return Double.parseDouble(confidenceThresholdProperty);
        }
        catch (NumberFormatException e) {
            log.warn("Invalid confidence threshold specified: value should be numeric. Provided value was: "
                             + confidenceThresholdProperty);
            return job.getSystemPropertiesSnapshot().getConfidenceThreshold();
        }
    }

    private void processVideoResponse(
            long jobId,
            DetectionProtobuf.DetectionResponse detectionResponse,
            DetectionProtobuf.DetectionResponse.VideoResponse videoResponse,
            double confidenceThreshold,
            Media media,
            String exemplarPolicy,
            String trackType,
            int mergedTaskIdx) {
        int startFrame = videoResponse.getStartFrame();
        int stopFrame = videoResponse.getStopFrame();
        var frameTimeInfo = media.getFrameTimeInfo();
        int startTime = frameTimeInfo.getTimeMsFromFrame(startFrame);
        int stopTime = frameTimeInfo.getTimeMsFromFrame(stopFrame);

        String mediaLabel = String.format("Media #%d, Frames: %d-%d, Task: '%s', Action: '%s'",
                detectionResponse.getMediaId(),
                startFrame,
                stopFrame,
                detectionResponse.getTaskName(),
                detectionResponse.getActionName());

        log.debug("Response received for {}.", mediaLabel);
        checkErrors(jobId, mediaLabel, detectionResponse, startFrame, stopFrame, startTime, stopTime);

        // Begin iterating through the tracks that were found by the detector.
        boolean isMediaType = trackType.equals("MEDIA");
        for (DetectionProtobuf.VideoTrack objectTrack : videoResponse.getVideoTracksList()) {
            SortedMap<String, String> trackProperties = toImmutableMap(objectTrack.getDetectionPropertiesList());

            boolean hasDerivativeMedia = isMediaType &&
                                         trackProperties.containsKey(MpfConstants.DERIVATIVE_MEDIA_TEMP_PATH);
            if (hasDerivativeMedia) {
                throw new WfmProcessingException(
                        "Unsupported operation. Derivative media is not supported for jobs with video source media.");
            }

            if (objectTrack.getConfidence() < confidenceThreshold) {
                continue;
            }

            int startOffsetTime = frameTimeInfo.getTimeMsFromFrame(objectTrack.getStartFrame());
            int stopOffsetTime  = frameTimeInfo.getTimeMsFromFrame(objectTrack.getStopFrame());

            ImmutableSortedSet<Detection> detections = objectTrack.getFrameLocationsList()
                    .stream()
                    .filter(flm -> flm.getImageLocation().getConfidence() >= confidenceThreshold)
                    .map(flm -> toDetection(flm, frameTimeInfo))
                    .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));

            if (!detections.isEmpty()) {
                Track track = new Track(
                        jobId,
                        detectionResponse.getMediaId(),
                        detectionResponse.getTaskIndex(),
                        detectionResponse.getActionIndex(),
                        objectTrack.getStartFrame(),
                        objectTrack.getStopFrame(),
                        startOffsetTime,
                        stopOffsetTime,
                        mergedTaskIdx,
                        objectTrack.getConfidence(),
                        detections,
                        trackProperties,
                        exemplarPolicy);
                _inProgressJobs.addTrack(track);
            }
        }
    }

    private void processAudioResponse(
            long jobId,
            DetectionProtobuf.DetectionResponse detectionResponse,
            DetectionProtobuf.DetectionResponse.AudioResponse audioResponse,
            double confidenceThreshold,
            String tracktype,
            int mergedTaskIdx) {

        int startTime = audioResponse.getStartTime();
        int stopTime = audioResponse.getStopTime();
        String mediaLabel = String.format("Media #%d, Time: %d-%d, Task: '%s', Action: '%s'",
                detectionResponse.getMediaId(),
                startTime,
                stopTime,
                detectionResponse.getTaskName(),
                detectionResponse.getActionName());

        log.debug("Response received for {}.", mediaLabel);
        checkErrors(jobId, mediaLabel, detectionResponse, 0, 0, startTime, stopTime);

        // Begin iterating through the tracks that were found by the detector.
        boolean isMediaType = tracktype.equals("MEDIA");
        for (DetectionProtobuf.AudioTrack objectTrack : audioResponse.getAudioTracksList()) {
            SortedMap<String, String> trackProperties = toImmutableMap(objectTrack.getDetectionPropertiesList());

            boolean hasDerivativeMedia = isMediaType &&
                                         trackProperties.containsKey(MpfConstants.DERIVATIVE_MEDIA_TEMP_PATH);
            if (hasDerivativeMedia) {
                throw new WfmProcessingException(
                        "Unsupported operation. Derivative media is not supported for jobs with audio source media.");
            }

            if (objectTrack.getConfidence() >= confidenceThreshold) {
                Detection detection = new Detection(
                        0,
                        0,
                        0,
                        0,
                        objectTrack.getConfidence(),
                        0,
                        objectTrack.getStartTime(),
                        trackProperties);

                Track track = new Track(
                        jobId,
                        detectionResponse.getMediaId(),
                        detectionResponse.getTaskIndex(),
                        detectionResponse.getActionIndex(),
                        0,
                        0,
                        objectTrack.getStartTime(),
                        objectTrack.getStopTime(),
                        mergedTaskIdx,
                        objectTrack.getConfidence(),
                        ImmutableSortedSet.of(detection),
                        trackProperties,
                        "");

                _inProgressJobs.addTrack(track);
            }
        }
    }

    private void processImageResponse(
            long jobId,
            DetectionProtobuf.DetectionResponse detectionResponse,
            DetectionProtobuf.DetectionResponse.ImageResponse imageResponse,
            double confidenceThreshold,
            String trackType,
            int mergedTaskIdx) {
        String mediaLabel = getBasicMediaLabel(detectionResponse);
        log.debug("Response received for {}.", mediaLabel);

        checkErrors(jobId, mediaLabel, detectionResponse, 0, 1, 0, 0);

        // Iterate through the list of detections. It is assumed that detections are not sorted in a meaningful way.
        boolean isMediaType = trackType.equals("MEDIA");
        for (DetectionProtobuf.ImageLocation location : imageResponse.getImageLocationsList()) {
            SortedMap<String, String> locationProperties = toImmutableMap(location.getDetectionPropertiesList());

            boolean hasDerivativeMedia = isMediaType &&
                                         locationProperties.containsKey(MpfConstants.DERIVATIVE_MEDIA_TEMP_PATH);
            if (hasDerivativeMedia) {
                throw new WfmProcessingException(
                        "Unsupported operation. Derivative media is not supported for jobs with image source media.");
            }

            if (location.getConfidence() >= confidenceThreshold) {
                Track track = new Track(
                        jobId,
                        detectionResponse.getMediaId(),
                        detectionResponse.getTaskIndex(),
                        detectionResponse.getActionIndex(),
                        0,
                        0,
                        0,
                        0,
                        mergedTaskIdx,
                        location.getConfidence(),
                        ImmutableSortedSet.of(toDetection(location, 0, 0)),
                        locationProperties,
                        "");
                _inProgressJobs.addTrack(track);
            }
        }
    }

    private void processGenericResponse(
            long jobId,
            DetectionProtobuf.DetectionResponse detectionResponse,
            DetectionProtobuf.DetectionResponse.GenericResponse genericResponse,
            double confidenceThreshold,
            String trackType,
            int mergedTaskIdx) {
        String mediaLabel = getBasicMediaLabel(detectionResponse);
        log.debug("Response received for {}.", mediaLabel);

        checkErrors(jobId, mediaLabel, detectionResponse, 0, 0, 0, 0);

        // Begin iterating through the tracks that were found by the detector.
        boolean isMediaType = trackType.equals("MEDIA");
        for (DetectionProtobuf.GenericTrack objectTrack : genericResponse.getGenericTracksList()) {
            SortedMap<String, String> trackProperties = toMutableMap(objectTrack.getDetectionPropertiesList());

            boolean hasDerivativeMedia = isMediaType &&
                                         trackProperties.containsKey(MpfConstants.DERIVATIVE_MEDIA_TEMP_PATH);
            if (hasDerivativeMedia) {
                trackProperties = processDerivativeMedia(jobId, detectionResponse.getMediaId(),
                        detectionResponse.getTaskIndex(), trackProperties);
            }

            processGenericTrack(jobId, detectionResponse, genericResponse, objectTrack, confidenceThreshold,
                    trackProperties, mergedTaskIdx);
        }
    }


    private void processGenericTrack(
            long jobId,
            DetectionProtobuf.DetectionResponse detectionResponse,
            DetectionProtobuf.DetectionResponse.GenericResponse genericResponse,
            DetectionProtobuf.GenericTrack objectTrack,
            double confidenceThreshold,
            SortedMap<String, String> trackProperties,
            int mergedTaskIdx) {
        if (objectTrack.getConfidence() >= confidenceThreshold) {
            Detection detection = new Detection(
                    0,
                    0,
                    0,
                    0,
                    objectTrack.getConfidence(),
                    0,
                    0,
                    trackProperties);

            Track track = new Track(
                    jobId,
                    detectionResponse.getMediaId(),
                    detectionResponse.getTaskIndex(),
                    detectionResponse.getActionIndex(),
                    0,
                    0,
                    0,
                    0,
                    mergedTaskIdx,
                    objectTrack.getConfidence(),
                    ImmutableSortedSet.of(detection),
                    trackProperties,
                    "");

            _inProgressJobs.addTrack(track);
        }
    }

    private void checkErrors(long jobId, String mediaLabel, DetectionProtobuf.DetectionResponse detectionResponse,
                             int startFrame, int stopFrame, int startTime, int stopTime) {

        if (detectionResponse.getError() != DetectionProtobuf.DetectionError.NO_DETECTION_ERROR) {
            String errorCode;
            String errorMessage;
            // Some error occurred during detection. Store this error.
            if (detectionResponse.getError() == DetectionProtobuf.DetectionError.REQUEST_CANCELLED) {
                log.warn("Job cancelled while processing {}.", mediaLabel);
                errorCode = MpfConstants.REQUEST_CANCELLED;
                errorMessage = "Successfully cancelled.";
            }
            else {
                log.error("Encountered a detection error while processing {}: {}",
                        mediaLabel, detectionResponse.getError());
                errorCode = Objects.toString(detectionResponse.getError());
                errorMessage = detectionResponse.getErrorMessage();
            }
            _inProgressJobs.addDetectionProcessingError(new DetectionProcessingError(
                    jobId,
                    detectionResponse.getMediaId(),
                    detectionResponse.getTaskIndex(),
                    detectionResponse.getActionIndex(),
                    startFrame,
                    stopFrame,
                    startTime,
                    stopTime,
                    errorCode,
                    errorMessage));
        }
    }

    private static Detection toDetection(
            DetectionProtobuf.VideoTrack.FrameLocationMap frameLocationMap,
            FrameTimeInfo timeInfo) {
        int time = timeInfo.getTimeMsFromFrame(frameLocationMap.getFrame());
        return toDetection(frameLocationMap.getImageLocation(), frameLocationMap.getFrame(), time);
    }

    private static Detection toDetection(DetectionProtobuf.ImageLocation location, int frameNumber, int time) {
        SortedMap<String, String> detectionProperties = toImmutableMap(location.getDetectionPropertiesList());
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

    private static SortedMap<String, String> toImmutableMap(Collection<DetectionProtobuf.PropertyMap> properties) {
        return properties.stream()
                .collect(ImmutableSortedMap.toImmutableSortedMap(
                        Comparator.naturalOrder(),
                        DetectionProtobuf.PropertyMap::getKey,
                        DetectionProtobuf.PropertyMap::getValue));
    }

    private static SortedMap<String, String> toMutableMap(Collection<DetectionProtobuf.PropertyMap> properties) {
        return properties.stream()
                .collect(Collectors.toMap(
                        DetectionProtobuf.PropertyMap::getKey,
                        DetectionProtobuf.PropertyMap::getValue,
                        (u,v) -> { throw new IllegalStateException(String.format("Duplicate key %s", u)); },
                        TreeMap::new));
    }

    private static String getBasicMediaLabel(DetectionProtobuf.DetectionResponse detectionResponse) {
        return String.format("Media #%d, Task: '%s', Action: '%s'",
                detectionResponse.getMediaId(), detectionResponse.getTaskName(), detectionResponse.getActionName());
    }

    private SortedMap<String, String> processDerivativeMedia(long jobId,
                                                             long parentMediaId,
                                                             int taskIndex,
                                                             SortedMap<String, String> trackProperties) {
        Path localPath = Paths.get(trackProperties.get(MpfConstants.DERIVATIVE_MEDIA_TEMP_PATH)).toAbsolutePath();

        long mediaId = IdGenerator.next();

        // Add the media id to allow users to associate media tracks with media elements in the JSON output.
        trackProperties.put(MpfConstants.DERIVATIVE_MEDIA_ID, String.valueOf(mediaId));

        Media derivativeMedia = _inProgressJobs.initDerivativeMedia(
                jobId, mediaId, parentMediaId, taskIndex, localPath, trackProperties);

        _mediaInspectionHelper.inspectMedia(derivativeMedia, jobId);

        if (derivativeMedia.isFailed()) {
            _inProgressJobs.addError(jobId, derivativeMedia.getId(), IssueCodes.MEDIA_INITIALIZATION,
                    derivativeMedia.getErrorMessage());
        }

        return trackProperties;
    }
}
