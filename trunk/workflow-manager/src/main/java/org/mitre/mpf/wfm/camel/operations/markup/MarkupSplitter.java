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

package org.mitre.mpf.wfm.camel.operations.markup;

import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultMessage;
import org.apache.tika.mime.MimeTypes;
import org.javasimon.aop.Monitored;
import org.mitre.mpf.rest.api.pipelines.ActionType;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.videooverlay.BoundingBox;
import org.mitre.mpf.videooverlay.BoundingBoxMap;
import org.mitre.mpf.videooverlay.BoundingBoxSource;
import org.mitre.mpf.wfm.buffers.Markup;
import org.mitre.mpf.wfm.data.IdGenerator;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.access.MarkupResultDao;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.*;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;
import java.util.*;
import java.util.stream.DoubleStream;

@Component
@Monitored
public class MarkupSplitter {
    private static final Logger log = LoggerFactory.getLogger(MarkupSplitter.class);

    private final CamelContext _camelContext;

    private final InProgressBatchJobsService _inProgressBatchJobs;

    private final PropertiesUtil _propertiesUtil;

    private final MarkupResultDao _markupResultDao;

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;

    @Inject
    public MarkupSplitter(
            CamelContext camelContext,
            InProgressBatchJobsService inProgressBatchJobs,
            PropertiesUtil propertiesUtil,
            MarkupResultDao markupResultDao,
            AggregateJobPropertiesUtil aggregateJobPropertiesUtil) {
        _camelContext = camelContext;
        _inProgressBatchJobs = inProgressBatchJobs;
        _propertiesUtil = propertiesUtil;
        _markupResultDao = markupResultDao;
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
    }


    public List<Message> performSplit(BatchJob job, Task markupTask) {
        _markupResultDao.deleteByJobId(job.getId());

        List<Message> messages = new ArrayList<>(job.getMedia().size());

        // Markup tasks always have one action.
        var actionName = markupTask.getActions().get(0);
        var markupAction = job.getPipelineElements().getAction(actionName);

        int mediaIndex = -1;
        for (var media : job.getMedia()) {
            mediaIndex++;
            if (media.isFailed()) {
                log.warn("Skipping '{}: {}' - it is in an error state.",
                         media.getId(), media.getLocalPath());
                continue;
            }
            if (!media.matchesType(MediaType.IMAGE, MediaType.VIDEO)) {
                log.debug("Skipping Media {} - only image and video files are eligible for markup.",
                          media.getId());
                continue;
            }

            var markupProperties = _aggregateJobPropertiesUtil.getPropertyMap(
                    job, media, markupAction);

            if (!_aggregateJobPropertiesUtil.actionAppliesToMedia(media, markupProperties)) {
                continue;
            }

            List<Markup.BoundingBoxMapEntry> boundingBoxMapEntryList
                    = createMapEntries(job, media, markupProperties);

            Path destinationPath;
            if (boundingBoxMapEntryList.isEmpty()) {
                var fileExtension = media.getMimeType()
                        .map(MarkupSplitter::getFileExtension)
                        .orElse(".bin");
                destinationPath = _propertiesUtil.createMarkupPath(
                        job.getId(), media.getId(), fileExtension);
            }
            else {
                destinationPath = _propertiesUtil.createMarkupPath(
                        job.getId(), media.getId(),
                        getMarkedUpMediaExtensionForMediaType(media, markupProperties));
            }


            var mediaType = media.getType()
                    .map(mt -> Markup.MediaType.valueOf(mt.toString().toUpperCase()))
                    .orElse(Markup.MediaType.UNKNOWN);

            Markup.MarkupRequest.Builder requestBuilder = Markup.MarkupRequest.newBuilder()
                    .setMediaIndex(mediaIndex)
                    .setTaskIndex(job.getCurrentTaskIndex())
                    .setActionIndex(0)
                    .setMediaId(media.getId())
                    .setMediaType(mediaType)
                    .setRequestId(IdGenerator.next())
                    .setSourceUri(media.getProcessingPath().toUri().toString())
                    .setDestinationUri(destinationPath.toUri().toString())
                    .addAllMapEntries(boundingBoxMapEntryList);

            for (var entry : media.getMetadata().entrySet()) {
                requestBuilder.addMediaMetadataBuilder()
                        .setKey(entry.getKey())
                        .setValue(entry.getValue());
            }

            for (var entry : markupProperties.entrySet()) {
                requestBuilder.addMarkupPropertiesBuilder()
                        .setKey(entry.getKey())
                        .setValue(entry.getValue());
            }

            var algorithm = job.getPipelineElements().getAlgorithm(markupAction.getAlgorithm());
            var message = new DefaultMessage(_camelContext);
            message.setHeader(
                    MpfHeaders.RECIPIENT_QUEUE,
                    String.format("jms:MPF.%s_%s_REQUEST", algorithm.getActionType(),
                                  markupAction.getAlgorithm()));
            message.setHeader(MpfHeaders.JMS_REPLY_TO,
                              MpfEndpoints.COMPLETED_MARKUP.replace("jms:", ""));
            message.setBody(requestBuilder.build());
            messages.add(message);
        }

        return messages;
    }


    /**
     * Returns the last task in the pipeline containing a detection action. This effectively filters preprocessor
     * detections so that the output is not cluttered with motion detections.
     */
    private static int findLastDetectionTaskIndex(JobPipelineElements pipeline) {
        int taskIndex = -1;
        for (int i = 0; i < pipeline.getTaskCount(); i++) {
            ActionType actionType = pipeline.getAlgorithm(i, 0).getActionType();
            if(actionType == ActionType.DETECTION) {
                taskIndex = i;
            }
        }
        return taskIndex;
    }

    /** Creates a BoundingBoxMap containing all of the tracks which were produced by the specified action history keys. */
    private List<Markup.BoundingBoxMapEntry> createMapEntries(
            BatchJob job, Media media, Map<String, String> markupProperties) {

        var labelFromDetections = Boolean.parseBoolean(markupProperties.get(
                MpfConstants.MARKUP_LABELS_FROM_DETECTIONS));
        var labelUseTrackIndex = Boolean.parseBoolean(markupProperties.get(
                MpfConstants.MARKUP_LABELS_TRACK_INDEX_ENABLED));
        var labelTextPropToShow = markupProperties.get(
                MpfConstants.MARKUP_LABELS_TEXT_PROP_TO_SHOW);
        var labelNumericPropToShow = markupProperties.get(
                MpfConstants.MARKUP_LABELS_NUMERIC_PROP_TO_SHOW);
        var animate = Boolean.parseBoolean(markupProperties.get(
                MpfConstants.MARKUP_ANIMATION_ENABLED));
        int labelMaxLength = getMaxLabelLength(job.getId(), media.getId(), markupProperties);

        Iterator<Color> trackColors = getTrackColors();
        BoundingBoxMap boundingBoxMap = new BoundingBoxMap();

        int taskToMarkupIndex = findLastDetectionTaskIndex(job.getPipelineElements());
        // PipelineValidator made sure taskToMarkupIndex only has one action.
        SortedSet<Track> tracks = _inProgressBatchJobs.getTracks(
                job.getId(), media.getId(), taskToMarkupIndex, 0);

        int trackIndex = 0;
        for (Track track : tracks) {
            String labelPrefix = "";
            if (labelUseTrackIndex) {
                labelPrefix = "[" + trackIndex + "]";
            }
            addTrackToBoundingBoxMap(
                    track, boundingBoxMap, trackColors.next(), labelPrefix, labelFromDetections,
                    labelTextPropToShow, labelNumericPropToShow, animate, labelMaxLength);
            trackIndex++;
        }

        return boundingBoxMap.toBoundingBoxMapEntryList();
    }


    private int getMaxLabelLength(long jobId, long mediaId, Map<String, String> properties) {
        var labelMaxLengthStr = properties.getOrDefault(
                MpfConstants.MARKUP_TEXT_LABEL_MAX_LENGTH, "");
        int labelMaxLength;
        try {
            labelMaxLength = Integer.parseInt(labelMaxLengthStr);
            if (labelMaxLength > 0) {
                return labelMaxLength;
            }
            labelMaxLength = 10;
        }
        catch (NumberFormatException e) {
            labelMaxLength = 10;
        }

        var errorMsg = """
                Expected the value of the "%s" property to be a positive integer, \
                but it was "%s". Using %s instead."""
                .formatted(MpfConstants.MARKUP_TEXT_LABEL_MAX_LENGTH, labelMaxLengthStr,
                           labelMaxLength);
        _inProgressBatchJobs.addWarning(jobId, mediaId, IssueCodes.MARKUP,
                                        errorMsg);
        return labelMaxLength;
    }


    private static OptionalDouble getRotation(Map<String, String> properties) {
        String rotationString = properties.get("ROTATION");
        if (rotationString == null) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(Double.parseDouble(rotationString));
    }


    private static Optional<Boolean> getFlip(Map<String, String> properties) {
        return Optional.ofNullable(properties.get("HORIZONTAL_FLIP"))
                .map(s -> Boolean.parseBoolean(s.strip()));
    }

    private void addTrackToBoundingBoxMap(
            Track track,
            BoundingBoxMap boundingBoxMap,
            Color trackColor,
            String labelPrefix,
            boolean labelFromDetections,
            String labelTextPropToShow,
            String labelNumericPropToShow,
            boolean animate,
            int textLength) {
        OptionalDouble trackRotation = getRotation(track.getTrackProperties());
        Optional<Boolean> trackFlip = getFlip(track.getTrackProperties());

        Optional<String> label = Optional.empty();
        boolean moving = false;
        if (!labelFromDetections) { // get track-level details
            label = getLabel(track, labelPrefix, labelTextPropToShow, textLength,
                             labelNumericPropToShow);
            moving = Boolean.parseBoolean(track.getTrackProperties().get("MOVING"));
        }

        List<Detection> orderedDetections = new ArrayList<>(track.getDetections());
        Collections.sort(orderedDetections);
        for (int i = 0; i < orderedDetections.size(); i++) {
            Detection detection = orderedDetections.get(i);
            int currentFrame = detection.getMediaOffsetFrame();

            BoundingBoxSource detectionSource = BoundingBoxSource.DETECTION_ALGORITHM;
            if (Boolean.parseBoolean(detection.getDetectionProperties().get("FILLED_GAP"))) {
                detectionSource = BoundingBoxSource.TRACKING_FILLED_GAP;
            }

            OptionalDouble detectionRotation = getRotation(detection.getDetectionProperties());
            Optional<Boolean> detectionFlip = getFlip(detection.getDetectionProperties());

            if (labelFromDetections) { // get detection-level details
                label = getLabel(detection, labelPrefix, labelTextPropToShow, textLength,
                                 labelNumericPropToShow);
                moving = Boolean.parseBoolean(detection.getDetectionProperties().get("MOVING"));
            }

            // Create a bounding box at the location.
            BoundingBox boundingBox = new BoundingBox(
                    detection.getX(),
                    detection.getY(),
                    detection.getWidth(),
                    detection.getHeight(),
                    detectionRotation.orElse(trackRotation.orElse(0)),
                    detectionFlip.orElse(trackFlip.orElse(false)),
                    trackColor.getRed(),
                    trackColor.getGreen(),
                    trackColor.getBlue(),
                    detectionSource,
                    moving,
                    track.getExemplar().equals(detection),
                    label);

            if (_aggregateJobPropertiesUtil.isExemptFromIllFormedDetectionRemoval(track.getType())) {
                // Special case: Speech doesn't populate object locations for each frame in the video, so you have to
                // go by the track start and stop frames.
                boundingBoxMap.putOnFrames(track.getStartOffsetFrameInclusive(),
                                           track.getEndOffsetFrameInclusive(), boundingBox);
                break;
            }

            boolean isLastDetection = (i == (orderedDetections.size() - 1));
            if (isLastDetection) {
                boundingBoxMap.putOnFrame(currentFrame, boundingBox);
                break;
            }

            Detection nextDetection = orderedDetections.get(i + 1);
            int gapBetweenNextDetection = nextDetection.getMediaOffsetFrame() - detection.getMediaOffsetFrame();
            if (gapBetweenNextDetection == 1) {
                boundingBoxMap.putOnFrame(currentFrame, boundingBox);
            }
            else if (animate) {
                // Since the gap between frames is greater than 1 and we are not at the last result in the
                // collection, we draw bounding boxes on each frame in the collection such that on the
                // first frame, the bounding box is at the position given by the object location, and on the
                // last frame in the interval, the bounding box is very close to the position given by the object
                // location of the next result. Consequently, the original bounding box appears to resize
                // and translate to the position and size of the next result's bounding box.

                OptionalDouble nextDetectionRotation = getRotation(nextDetection.getDetectionProperties());
                Optional<Boolean> nextDetectionFlip = getFlip(nextDetection.getDetectionProperties());

                if (labelFromDetections) { // get detection-level details
                    label = getLabel(nextDetection, labelPrefix, labelTextPropToShow, textLength,
                                     labelNumericPropToShow);
                }

                BoundingBox nextBoundingBox = new BoundingBox(
                        nextDetection.getX(),
                        nextDetection.getY(),
                        nextDetection.getWidth(),
                        nextDetection.getHeight(),
                        nextDetectionRotation.orElse(trackRotation.orElse(0)),
                        nextDetectionFlip.orElse(trackFlip.orElse(false)),
                        boundingBox.getRed(),
                        boundingBox.getBlue(),
                        boundingBox.getGreen(),
                        BoundingBoxSource.ANIMATION,
                        moving,
                        false, // not exemplar
                        label);
                boundingBoxMap.animate(boundingBox, nextBoundingBox, currentFrame, gapBetweenNextDetection);
            }
        }
    }



    /** Returns the appropriate markup extension for a given {@link MediaType}. */
    private static String getMarkedUpMediaExtensionForMediaType(
            Media media, Map<String, String> markupProperties) {

        if (media.matchesType(MediaType.IMAGE)) {
            return ".png";
        }
        // Already verified media is image or video.
        assert media.getType().isPresent() && media.getType().get() == MediaType.VIDEO;

        var encoder = markupProperties.get(MpfConstants.MARKUP_VIDEO_ENCODER).toLowerCase();
        switch (encoder) {
            case "vp9":
                return ".webm";
            case "h264":
                return ".mp4";
            case "mjpeg":
                return ".avi";
            default:
                log.warn("\"{}\" is not a valid encoder. Defaulting to mjpeg.", encoder);
                return ".avi";
        }
    }

    private static String getFileExtension(String mimeType) {
        try {
            return MimeTypes.getDefaultMimeTypes().forName(mimeType).getExtension();
        } catch (Exception exception) {
            log.warn("Failed to map the MIME type '{}' to an extension. Defaulting to .bin.", mimeType);
            return ".bin";
        }
    }

    private static final double GOLDEN_RATIO_CONJUGATE = 2 / (1 + Math.sqrt(5));

    // Uses method described in https://martin.ankerl.com/2009/12/09/how-to-create-random-colors-programmatically/
    // to create an infinite iterator of randomish colors.
    // The article says to use the HSV color space, but HSB is identical.
    private static Iterator<Color> getTrackColors() {
        return DoubleStream.iterate(0.5, x -> (x + GOLDEN_RATIO_CONJUGATE) % 1)
                .mapToObj(x -> Color.getHSBColor((float) x, 0.5f, 0.95f))
                .iterator();
    }

    private static Optional<String> getLabel(String prefix,
                                             String textPart,
                                             int textLength,
                                             String numericPart) {
        String label = prefix;
        if (textPart != null) {
            if (textPart.length() <= textLength) {
                label += textPart.strip();
            }
            else {
                label += textPart.substring(0, textLength).strip();
            }
        }
        if (numericPart != null) {
            try {
                float numericVal = Float.parseFloat(numericPart);
                label += String.format(" %.3f", numericVal);
            } catch (NumberFormatException e) {
                log.warn("Failed to convert '{}' to a float when generating bounding box label.", numericPart);
            }
        }
        label = label.strip();
        return label.isBlank() ? Optional.empty() : Optional.of(label);
    }

    public static Optional<String> getLabel(Track track,
                                            String prefix,
                                            String textProp,
                                            int textLength,
                                            String numericProp) {
        String textStr = track.getTrackProperties().get(textProp);
        if (textStr == null) {
            textStr = track.getExemplar().getDetectionProperties().get(textProp);
        }
        String numericStr = track.getTrackProperties().get(numericProp);
        if (numericStr == null) {
            numericStr = track.getExemplar().getDetectionProperties().get(numericProp);
        }
        if (numericStr == null && numericProp.equalsIgnoreCase("CONFIDENCE")) {
            numericStr = Float.toString(track.getConfidence());
        }
        return getLabel(prefix, textStr, textLength, numericStr);
    }

    public static Optional<String> getLabel(Detection detection,
                                            String prefix,
                                            String textProp,
                                            int textLength,
                                            String numericProp) {
        String textStr = detection.getDetectionProperties().get(textProp);
        String numericStr = detection.getDetectionProperties().get(numericProp);
        if (numericStr == null && numericProp.equalsIgnoreCase("CONFIDENCE")) {
            numericStr = Float.toString(detection.getConfidence());
        }
        return getLabel(prefix, textStr, textLength, numericStr);
    }
}
