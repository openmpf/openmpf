/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.service.TaskAnnotatorService;
import org.mitre.mpf.wfm.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/** Processes the responses which have been returned from a detection component. */
@Component(DetectionResponseProcessor.REF)
public class DetectionResponseProcessor
        extends ResponseProcessor<DetectionProtobuf.DetectionResponse> {
    public static final String REF = "detectionResponseProcessor";

    private static final Logger log = LoggerFactory.getLogger(DetectionResponseProcessor.class);

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;

    private final InProgressBatchJobsService _inProgressJobs;

    private final MediaInspectionHelper _mediaInspectionHelper;

    private final TaskAnnotatorService _taskMergingManager;

    @Inject
    public DetectionResponseProcessor(AggregateJobPropertiesUtil aggregateJobPropertiesUtil,
                                      InProgressBatchJobsService inProgressJobs,
                                      MediaInspectionHelper mediaInspectionHelper,
                                      TaskAnnotatorService taskMergingManager) {
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
        var job = _inProgressJobs.getJob(jobId);
        var action = job.getPipelineElements().getAction(
                detectionResponse.getTaskIndex(), detectionResponse.getActionIndex());
        addProcessingTime(jobId, action, headers);

        var media = job.getMedia(detectionResponse.getMediaId());
        if (media == null) {
            throw new IllegalStateException(
                    "Unable to locate media with id: " + detectionResponse.getMediaId());
        }
        var qualityFilter = createQualityFilter(job, media, action);
        var qualitySelectionProp = _aggregateJobPropertiesUtil.getQualitySelectionProp(job, media, action);
        var trackType = job.getPipelineElements().getAlgorithm(action.algorithm()).trackType();
        var annotatedTaskIdx = _taskMergingManager.getAnnotatedTaskIndex(
                job, media,
                detectionResponse.getTaskIndex(),
                detectionResponse.getActionIndex(),
                headers);

        if (detectionResponse.hasVideoResponse()) {
            var exemplarPolicy = _aggregateJobPropertiesUtil.getValue(
                    ExemplarPolicyUtil.PROPERTY, job, media, action);
            processVideoResponse(
                    jobId,
                    detectionResponse,
                    detectionResponse.getVideoResponse(),
                    qualityFilter,
                    qualitySelectionProp,
                    media,
                    exemplarPolicy,
                    trackType,
                    annotatedTaskIdx);
        }
        else if (detectionResponse.hasAudioResponse()) {
            processAudioResponse(
                    jobId,
                    detectionResponse,
                    detectionResponse.getAudioResponse(),
                    qualityFilter,
                    trackType,
                    annotatedTaskIdx);
        }
        else if (detectionResponse.hasImageResponse()) {
            processImageResponse(
                    jobId,
                    detectionResponse,
                    detectionResponse.getImageResponse(),
                    qualityFilter,
                    trackType,
                    annotatedTaskIdx);
        }
        else if (detectionResponse.hasGenericResponse()) {
            processGenericResponse(
                    jobId,
                    detectionResponse,
                    detectionResponse.getGenericResponse(),
                    qualityFilter,
                    trackType,
                    annotatedTaskIdx,
                    headers);
        }
        else {
            String mediaLabel = getBasicMediaLabel(detectionResponse);
            log.warn("Response received, but no tracks were found for {}.", mediaLabel);
            checkErrors(jobId, mediaLabel, detectionResponse, 0, 0, 0, 0);
        }
        return null;
    }

    private void processVideoResponse(
            long jobId,
            DetectionProtobuf.DetectionResponse detectionResponse,
            DetectionProtobuf.DetectionResponse.VideoResponse videoResponse,
            QualityFilter qualityFilter,
            String qualitySelectionProp,
            Media media,
            String exemplarPolicy,
            String trackType,
            int annotatedTaskIdx) {
        int startFrame = videoResponse.getStartFrame();
        int stopFrame = videoResponse.getStopFrame();
        var frameTimeInfo = media.getFrameTimeInfo();
        int startTime = frameTimeInfo.getTimeMsFromFrame(startFrame);
        int stopTime = frameTimeInfo.getTimeMsFromFrame(stopFrame);

        String mediaLabel = String.format("Media #%d, Frames: %d-%d, Task: '%s', Action: '%s'",
                detectionResponse.getMediaId(),
                startFrame,
                stopFrame,
                detectionResponse.getTaskIndex(),
                detectionResponse.getActionIndex());

        log.debug("Response received for {}.", mediaLabel);
        checkErrors(jobId, mediaLabel, detectionResponse, startFrame, stopFrame, startTime, stopTime);

        // Begin iterating through the tracks that were found by the detector.
        boolean isMediaType = trackType.equals("MEDIA");
        for (DetectionProtobuf.VideoTrack objectTrack : videoResponse.getVideoTracksList()) {
            var trackProperties = objectTrack.getDetectionPropertiesMap();

            boolean hasDerivativeMedia = isMediaType &&
                                         trackProperties.containsKey(MpfConstants.DERIVATIVE_MEDIA_TEMP_PATH);
            if (hasDerivativeMedia) {
                throw new WfmProcessingException(
                        "Unsupported operation. Derivative media is not supported for jobs with video source media.");
            }

            // Drop the track if it doesn't pass the quality filter but we know that the quality
            // selection property exists as a track property. Tracks that don't contain the quality selection property
            // as a track property will still be processed.
            if (!qualityFilter.meetsThreshold(objectTrack.getConfidence(), trackProperties, false) &&
                    ("CONFIDENCE".equalsIgnoreCase(qualitySelectionProp) || trackProperties.containsKey(qualitySelectionProp))) {
                continue;
                }

            int startOffsetTime = frameTimeInfo.getTimeMsFromFrame(objectTrack.getStartFrame());
            int stopOffsetTime  = frameTimeInfo.getTimeMsFromFrame(objectTrack.getStopFrame());

            try {
                var detections = objectTrack.getFrameLocationsMap().entrySet()
                        .stream()
                        .filter(e -> qualityFilter.meetsThreshold(
                                e.getValue().getConfidence(),
                                e.getValue().getDetectionPropertiesMap(), true))
                        .map(e -> toDetection(e.getKey(), e.getValue(), frameTimeInfo))
                        .toList();

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
                            annotatedTaskIdx,
                            objectTrack.getConfidence(),
                            detections,
                            trackProperties,
                            exemplarPolicy,
                            qualitySelectionProp,
                            null,
                            null);
                    _inProgressJobs.addTrack(track);
                }
            }
            catch (Exception e) {
                String exceptionString = "Exception caught while creating detections list and new Track: " + e.getMessage();
                _inProgressJobs.addWarning(jobId, media.getId(), IssueCodes.INVALID_DETECTION, exceptionString);
            }
        }
    }

    private void processAudioResponse(
            long jobId,
            DetectionProtobuf.DetectionResponse detectionResponse,
            DetectionProtobuf.DetectionResponse.AudioResponse audioResponse,
            QualityFilter qualityFilter,
            String tracktype,
            int annotatedTaskIdx) {

        int startTime = audioResponse.getStartTime();
        int stopTime = audioResponse.getStopTime();
        String mediaLabel = String.format("Media #%d, Time: %d-%d, Task: '%s', Action: '%s'",
                detectionResponse.getMediaId(),
                startTime,
                stopTime,
                detectionResponse.getTaskIndex(),
                detectionResponse.getActionIndex());

        log.debug("Response received for {}.", mediaLabel);
        checkErrors(jobId, mediaLabel, detectionResponse, 0, 0, startTime, stopTime);

        // Begin iterating through the tracks that were found by the detector.
        boolean isMediaType = tracktype.equals("MEDIA");
        for (DetectionProtobuf.AudioTrack objectTrack : audioResponse.getAudioTracksList()) {
            var trackProperties = objectTrack.getDetectionPropertiesMap();

            boolean hasDerivativeMedia = isMediaType &&
                                         trackProperties.containsKey(MpfConstants.DERIVATIVE_MEDIA_TEMP_PATH);
            if (hasDerivativeMedia) {
                throw new WfmProcessingException(
                        "Unsupported operation. Derivative media is not supported for jobs with audio source media.");
            }

            if (qualityFilter.meetsThreshold(objectTrack.getConfidence(), trackProperties, true)) {
                Detection detection = new Detection(
                        0,
                        0,
                        0,
                        0,
                        (float)objectTrack.getConfidence(),
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
                        annotatedTaskIdx,
                        objectTrack.getConfidence(),
                        ImmutableSortedSet.of(detection),
                        trackProperties,
                        "",
                        "",
                        null,
                        null);

                _inProgressJobs.addTrack(track);
            }
        }
    }

    private void processImageResponse(
            long jobId,
            DetectionProtobuf.DetectionResponse detectionResponse,
            DetectionProtobuf.DetectionResponse.ImageResponse imageResponse,
            QualityFilter qualityFilter,
            String trackType,
            int annotatedTaskIdx) {
        String mediaLabel = getBasicMediaLabel(detectionResponse);
        log.debug("Response received for {}.", mediaLabel);

        checkErrors(jobId, mediaLabel, detectionResponse, 0, 1, 0, 0);

        // Iterate through the list of detections. It is assumed that detections are not sorted in a meaningful way.
        boolean isMediaType = trackType.equals("MEDIA");
        for (DetectionProtobuf.ImageLocation location : imageResponse.getImageLocationsList()) {
            var locationProperties = location.getDetectionPropertiesMap();

            boolean hasDerivativeMedia = isMediaType &&
                                         locationProperties.containsKey(MpfConstants.DERIVATIVE_MEDIA_TEMP_PATH);
            if (hasDerivativeMedia) {
                throw new WfmProcessingException(
                        "Unsupported operation. Derivative media is not supported for jobs with image source media.");
            }

            if (qualityFilter.meetsThreshold(location.getConfidence(), locationProperties, true)) {
                Track track = new Track(
                        jobId,
                        detectionResponse.getMediaId(),
                        detectionResponse.getTaskIndex(),
                        detectionResponse.getActionIndex(),
                        0,
                        0,
                        0,
                        0,
                        annotatedTaskIdx,
                        location.getConfidence(),
                        ImmutableSortedSet.of(toDetection(location, 0, 0)),
                        locationProperties,
                        "",
                        "",
                        null,
                        null);
                _inProgressJobs.addTrack(track);
            }
        }
    }

    private void processGenericResponse(
            long jobId,
            DetectionProtobuf.DetectionResponse detectionResponse,
            DetectionProtobuf.DetectionResponse.GenericResponse genericResponse,
            QualityFilter qualityFilter,
            String trackType,
            int annotatedTaskIdx,
            Map<String, Object> headers) {
        String mediaLabel = getBasicMediaLabel(detectionResponse);
        log.debug("Response received for {}.", mediaLabel);

        checkErrors(jobId, mediaLabel, detectionResponse, 0, 0, 0, 0);

        // Begin iterating through the tracks that were found by the detector.
        boolean isMediaType = trackType.equals("MEDIA");
        for (DetectionProtobuf.GenericTrack objectTrack : genericResponse.getGenericTracksList()) {
            var trackProperties = objectTrack.getDetectionPropertiesMap();

            boolean hasDerivativeMedia = isMediaType &&
                                         trackProperties.containsKey(MpfConstants.DERIVATIVE_MEDIA_TEMP_PATH);
            if (hasDerivativeMedia) {
                long derivativeMediaId = processDerivativeMedia(
                        jobId, detectionResponse.getMediaId(),
                        detectionResponse.getTaskIndex(), trackProperties);
                var mutableTrackProperties = new HashMap<>(trackProperties);
                // Add the media id to allow users to associate media tracks with media elements
                // in the JSON output.
                mutableTrackProperties.put(
                        MpfConstants.DERIVATIVE_MEDIA_ID, String.valueOf(derivativeMediaId));
                trackProperties = mutableTrackProperties;
            }

            if (qualityFilter.meetsThreshold(objectTrack.getConfidence(), trackProperties, true))
                processGenericTrack(jobId, detectionResponse, objectTrack,
                                    trackProperties, annotatedTaskIdx, headers);
        }
    }


    private void processGenericTrack(
            long jobId,
            DetectionProtobuf.DetectionResponse detectionResponse,
            DetectionProtobuf.GenericTrack objectTrack,
            Map<String, String> trackProperties,
            int annotatedTaskIdx,
            Map<String, Object> headers) {
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
                annotatedTaskIdx,
                objectTrack.getConfidence(),
                ImmutableSortedSet.of(detection),
                trackProperties,
                "",
                "",
                getSelectorId(headers),
                getSelectedInput(headers));

        _inProgressJobs.addTrack(track);
    }


    private static UUID getSelectorId(Map<String, Object> headers) {
        var selectorId = headers.get(MpfHeaders.MEDIA_SELECTOR_ID);
        if (selectorId == null) {
            return null;
        }
        if (selectorId instanceof String string) {
            return UUID.fromString(string);
        }
        else if (selectorId instanceof UUID uuid) {
            return uuid;
        }
        throw new WfmProcessingException(
                "The %s header did not contain a string. It was a %s"
                .formatted(MpfHeaders.MEDIA_SELECTOR_ID, selectorId.getClass()));
    }

    private static String getSelectedInput(Map<String, Object> headers) {
        return Optional.ofNullable(headers.get(MpfHeaders.SELECTED_TEXT_CONTENT))
            .map(Object::toString)
            .orElse(null);
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
            int frame,
            DetectionProtobuf.ImageLocation imageLocation,
            FrameTimeInfo timeInfo) {
        int time = timeInfo.getTimeMsFromFrame(frame);
        return toDetection(imageLocation, frame, time);
    }

    private static Detection toDetection(DetectionProtobuf.ImageLocation location, int frameNumber, int time) {
        return new Detection(
                location.getXLeftUpper(),
                location.getYLeftUpper(),
                location.getWidth(),
                location.getHeight(),
                location.getConfidence(),
                frameNumber,
                time,
                location.getDetectionPropertiesMap());
    }

    private static String getBasicMediaLabel(DetectionProtobuf.DetectionResponse detectionResponse) {
        return String.format("Media #%d, Task: '%s', Action: '%s'",
                detectionResponse.getMediaId(), detectionResponse.getTaskIndex(), detectionResponse.getActionIndex());
    }

    private long processDerivativeMedia(
            long jobId,
            long parentMediaId,
            int taskIndex,
            Map<String, String> trackProperties) {
        Path localPath = Paths.get(trackProperties.get(MpfConstants.DERIVATIVE_MEDIA_TEMP_PATH))
                .toAbsolutePath();

        long derivativeMediaId = IdGenerator.next();
        Media derivativeMedia = _inProgressJobs.initDerivativeMedia(
                jobId, derivativeMediaId, parentMediaId, taskIndex, localPath, trackProperties);

        _mediaInspectionHelper.inspectMedia(derivativeMedia, jobId);

        if (derivativeMedia.isFailed()) {
            _inProgressJobs.addError(
                    jobId, derivativeMedia.getId(), IssueCodes.MEDIA_INITIALIZATION,
                    derivativeMedia.getErrorMessage());
        }
        return derivativeMediaId;
    }

    private static interface QualityFilter {
        boolean meetsThreshold(double confidence, Map<String, String> detectionProperties, boolean reportWarning);
    }

    private QualityFilter createQualityFilter(BatchJob job, Media media, Action action) {
        var qualityThresholdProp = _aggregateJobPropertiesUtil.getValue(
                MpfConstants.QUALITY_THRESHOLD_PROPERTY, job, media, action);
        if (qualityThresholdProp == null || qualityThresholdProp.isBlank()) {
            return (c, p, b) -> true;
        }

        double qualityThreshold;
        try {
            qualityThreshold = Double.parseDouble(qualityThresholdProp);
            if (qualityThreshold == Double.NEGATIVE_INFINITY) {
                return (c, p, b) -> true;
            }
        }
        catch (NumberFormatException e) {
            _inProgressJobs.addWarning(
                    job.getId(), media.getId(), IssueCodes.OTHER,
                    "Expected %s to be a number but it was was: %s".formatted(
                        MpfConstants.QUALITY_THRESHOLD_PROPERTY, qualityThresholdProp));
            return (c, p, b) -> true;
        }

        var qualityProp = _aggregateJobPropertiesUtil.getQualitySelectionProp(job, media, action);
        if (qualityProp.equalsIgnoreCase("CONFIDENCE")) {
            return (c, p, b) -> c >= qualityThreshold;
        }
        else {
            return createQualityFilter(qualityProp, qualityThreshold);
        }
    }

    private static QualityFilter createQualityFilter(
            String qualityPropName, double qualityThreshold) {
        return new QualityFilter() {
            boolean _loggedWarning;
            String propName = qualityPropName;
            double threshold = qualityThreshold;

            public boolean meetsThreshold(
                    double confidence, Map<String, String> detectionProperties, boolean reportWarning) {
                var qualityValue = detectionProperties.getOrDefault(propName, "");
                try {
                    return Double.parseDouble(qualityValue) >= threshold;
                }
                catch (NumberFormatException e) {
                    if (reportWarning && !_loggedWarning) {
                        log.warn("QualityFilter: One or more detections did not have a valid quality property.");
                        _loggedWarning = true;
                    }
                    return false;
                }
            }
        };
    }
}
