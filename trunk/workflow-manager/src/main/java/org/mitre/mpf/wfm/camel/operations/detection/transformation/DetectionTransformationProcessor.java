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

package org.mitre.mpf.wfm.camel.operations.detection.transformation;

import org.apache.camel.Exchange;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.camel.WfmProcessor;
import org.mitre.mpf.wfm.data.DetectionErrorUtil;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.TrackCache;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;

@Component(DetectionTransformationProcessor.REF)
public class DetectionTransformationProcessor extends WfmProcessor {
    public static final String REF = "detectionTransformationProcessor";

    private static final Logger _log = LoggerFactory.getLogger(DetectionTransformationProcessor.class);

    private final InProgressBatchJobsService _inProgressBatchJobs;

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;

    @Inject
    public DetectionTransformationProcessor(
            JsonUtils jsonUtils,
            InProgressBatchJobsService inProgressBatchJobs,
            AggregateJobPropertiesUtil aggregateJobPropertiesUtil) {
        _inProgressBatchJobs = inProgressBatchJobs;
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
    }


    @Override
    public void wfmProcess(Exchange exchange) throws WfmProcessingException {
        var trackCache = exchange.getIn().getBody(TrackCache.class);
        BatchJob job = _inProgressBatchJobs.getJob(trackCache.getJobId());
        Task task = job.getPipelineElements().getTask(trackCache.getTaskIndex());

        for (int actionIndex = 0; actionIndex < task.actions().size(); actionIndex++) {
            Action action = job.getPipelineElements().getAction(trackCache.getTaskIndex(), actionIndex);
            var algo = job.getPipelineElements().getAlgorithm(action.algorithm());

            for (Media media : job.getMedia()) {
                if (media.isFailed() || !media.matchesType(MediaType.IMAGE, MediaType.VIDEO)) {
                    continue;
                }

                Function<String, String> combinedProperties =
                        _aggregateJobPropertiesUtil.getCombinedProperties(job, media, action);

                Collection<Track> tracks = trackCache.getTracks(media.getId(), actionIndex);

                if (tracks.size() > 0) {

                    int frameWidth = Integer.parseInt(media.getMetadata().get("FRAME_WIDTH"));
                    int frameHeight = Integer.parseInt(media.getMetadata().get("FRAME_HEIGHT"));

                    Collection<Track> updatedTracks = removeIllFormedDetections(
                        trackCache, media.getId(), actionIndex, frameWidth, frameHeight,
                        algo.trackType(), tracks);

                    try {
                        if (requiresPadding(combinedProperties)) {

                            String xPadding = combinedProperties.apply(MpfConstants.DETECTION_PADDING_X);
                            String yPadding = combinedProperties.apply(MpfConstants.DETECTION_PADDING_Y);

                            padTracks(trackCache, media.getId(), actionIndex,
                                    xPadding, yPadding, frameWidth, frameHeight, updatedTracks);
                        }
                    } catch (DetectionTransformationException e) {
                        // This should not happen because we checked that the detection properties were valid when the
                        // job was created.
                        throw new WfmProcessingException(e);
                    }
                }
            }
        }

        exchange.getOut().setBody(exchange.getIn().getBody());
    }


    /**
     * Ensures that the detection padding properties are valid..
     * @param properties Properties to validate
     * @throws DetectionTransformationException when invalid detection padding properties are provided.
     */
    public static void validatePaddingProperties(Function<String, String> properties)
            throws DetectionTransformationException {
        // Both will throw if property is invalid.
        requiresPadding(properties, MpfConstants.DETECTION_PADDING_X);
        requiresPadding(properties, MpfConstants.DETECTION_PADDING_Y);
    }


    private static boolean requiresPadding(Function<String, String> properties)
            throws DetectionTransformationException {
        return requiresPadding(properties, MpfConstants.DETECTION_PADDING_X) ||
               requiresPadding(properties, MpfConstants.DETECTION_PADDING_Y);
    }


    private static boolean requiresPadding(Function<String, String> properties, String propertyName)
            throws DetectionTransformationException {
        String padding = properties.apply(propertyName);
        if (StringUtils.isBlank(padding)) {
            return false;
        }
        try {
            if (padding.endsWith("%")) {
                double percent = Double.parseDouble(padding.substring(0, padding.length() - 1));
                if (percent <= -50.0) {
                    // can't shrink to nothing
                    throw new DetectionTransformationException(String.format(
                            "The %s property was set to \"%s\", but that would result in empty detections. " +
                                    "When specified as a percentage, padding values must be > -50%%.",
                            propertyName, padding));
                }
                return percent != 0.0;
            }
            return Integer.parseInt(padding) != 0;
        } catch (NumberFormatException e) {
            throw new DetectionTransformationException(String.format(
                    "The %s property was set to \"%s\", but that is not a valid value. " +
                            "Padding must be specified as whole number integer, or a percentage that ends " +
                            "with \"%%\". Percentages can be decimal values. Negative values are allowed in both " +
                            "cases, but percentages must be > -50%%.",
                    propertyName, padding), e);
        }
    }

    public Collection<Track> removeIllFormedDetections(
            TrackCache trackCache, long mediaId, int actionIndex, int frameWidth, int frameHeight,
            String trackType, Collection<Track> tracks) {
        // Remove any detections with zero width/height, or that are entirely outside of the frame.
        // If the number of detections goes to 0, drop the track.
        // Do not remove ill-formed detections for those types that are exempted, because they normally do not generate
        // bounding boxes for detections.
        if (_aggregateJobPropertiesUtil.isExemptFromIllFormedDetectionRemoval(trackType)) {
            return tracks;
        }

        var newTracks = new TreeSet<Track>();
        var zeroSizeFrames = IntStream.builder();
        var outsideFrames = IntStream.builder();
        var frameBoundingBox = new Rectangle2D.Double(0, 0, frameWidth, frameHeight);
        for (Track track : tracks) {
            SortedSet<Detection> goodDetections = new TreeSet<>();
            for (Detection detection : track.getDetections()) {
                if (detection.getWidth() <= 0 || detection.getHeight() <= 0) {
                    zeroSizeFrames.add(detection.getMediaOffsetFrame());
                    continue;
                }

                AffineTransform transform = getInPlaceTransform(detection);
                Rectangle2D.Double detectionRect = new Rectangle2D.Double(detection.getX(), detection.getY(),
                        detection.getWidth(), detection.getHeight());
                Shape detectionShape = transform.createTransformedShape(detectionRect);

                /*
                if (false) { // if true show visualization
                    DebugCanvas.clear();
                    DebugCanvas.draw(frameBoundingBox, Color.yellow);
                    DebugCanvas.draw(detection, Color.green, Color.red);
                    DebugCanvas.show("removeIllFormedDetections");
                }
                */

                if (detectionShape.intersects(frameBoundingBox)) {
                    goodDetections.add(detection);
                }
                else {
                    outsideFrames.add(detection.getMediaOffsetFrame());
                }
            }
            if (goodDetections.size() > 0) {
                newTracks.add(new Track(
                        track.getJobId(),
                        track.getMediaId(),
                        track.getTaskIndex(),
                        track.getActionIndex(),
                        goodDetections.first().getMediaOffsetFrame(),
                        goodDetections.last().getMediaOffsetFrame(),
                        goodDetections.first().getMediaOffsetTime(),
                        goodDetections.last().getMediaOffsetTime(),
                        track.getMergedTaskIndex(),
                        track.getConfidence(),
                        goodDetections,
                        track.getTrackProperties(),
                        track.getExemplarPolicy(),
                        track.getQualitySelectionProperty()));
            }
            else {
                _log.warn(String.format("Empty track dropped after removing ill-formed detection(s): %s", track));
            }

        }

        Optional<String> zeroSizeFramesString = zeroSizeFrames.build()
                .boxed()
                .collect(DetectionErrorUtil.toFrameRangesString());

        Optional<String> outsideFramesString = outsideFrames.build()
                .boxed()
                .collect(DetectionErrorUtil.toFrameRangesString());

        long jobId = trackCache.getJobId();
        if (zeroSizeFramesString.isPresent()) {
            _log.warn(String.format("Dropped one or more ill-formed detection regions for job id %s with width or " +
                            "height equal to 0. %s",
                    jobId, zeroSizeFramesString.get()));
            _inProgressBatchJobs.addWarning(
                    jobId, mediaId, IssueCodes.INVALID_DETECTION, String.format(
                            "Dropped one or more ill-formed detection regions with width or height equal to 0. %s",
                            zeroSizeFramesString.get()));
        }

        if (outsideFramesString.isPresent()) {
            _log.warn(String.format("Dropped one or more ill-formed detection regions for job id %s with bounding " +
                            "box completely outside of the frame. %s",
                    jobId, outsideFramesString.get()));
            _inProgressBatchJobs.addWarning(
                    jobId, mediaId, IssueCodes.INVALID_DETECTION, String.format(
                            "Dropped one or more ill-formed detection regions with bounding box completely outside frame. %s",
                            outsideFramesString.get()));
        }
        if (zeroSizeFramesString.isPresent() || outsideFramesString.isPresent()) {
            trackCache.updateTracks(mediaId, actionIndex, newTracks);
        }

        return newTracks;
    }


    private static AffineTransform getTransform(Detection detection) {
        double rotationDegrees = Optional.ofNullable(detection.getDetectionProperties().get("ROTATION"))
                .filter(StringUtils::isNotBlank)
                .map(Double::parseDouble)
                .orElse(0.0);

        boolean flip = Boolean.parseBoolean(detection.getDetectionProperties().get("HORIZONTAL_FLIP"));
        AffineTransform transform;
        if (flip) {
            transform = new AffineTransform(
                    -1, 0,
                    0, 1,
                    0, 0
            );
            transform.rotate(Math.toRadians(360 - rotationDegrees));
        }
        else {
            transform = AffineTransform.getRotateInstance(Math.toRadians(rotationDegrees));
        }

        return transform;
    }


    public static AffineTransform getInPlaceTransform(Detection detection) {
        double rotationDegrees = Optional.ofNullable(detection.getDetectionProperties().get("ROTATION"))
                .filter(StringUtils::isNotBlank)
                .map(Double::parseDouble)
                .orElse(0.0);

        boolean flip = Boolean.parseBoolean(detection.getDetectionProperties().get("HORIZONTAL_FLIP"));

        AffineTransform detectionTransform = new AffineTransform(); // identity

        if (flip) {
            AffineTransform moveBackTransform = AffineTransform.getTranslateInstance(detection.getX(), 0);
            detectionTransform.concatenate(moveBackTransform);

            AffineTransform flipTransform = AffineTransform.getScaleInstance(-1, 1);
            detectionTransform.concatenate(flipTransform);

            AffineTransform moveToCenterTransform = AffineTransform.getTranslateInstance(-detection.getX(), 0);
            detectionTransform.concatenate(moveToCenterTransform);
        }

        AffineTransform rotationTransform =
                AffineTransform.getRotateInstance(Math.toRadians(-rotationDegrees), detection.getX(), detection.getY());
        detectionTransform.concatenate(rotationTransform);

        return detectionTransform;
    }

    private void padTracks(TrackCache trackCache, long mediaId, int actionIndex,
                           String xPadding, String yPadding, int frameWidth,
                           int frameHeight, Collection<Track> tracks) {
        var newTracks = new TreeSet<Track>();
        var shrunkToNothingFrames = IntStream.builder();

        for (Track track : tracks) {
            SortedSet<Detection> newDetections = new TreeSet<>();

            for (Detection detection : track.getDetections()) {
                Detection newDetection = padDetection(xPadding, yPadding, frameWidth, frameHeight, detection);
                if (newDetection.getDetectionProperties().containsKey("SHRUNK_TO_NOTHING")) {
                    shrunkToNothingFrames.add(newDetection.getMediaOffsetFrame());
                }
                newDetections.add(newDetection);
            }

            newTracks.add(new Track(
                    track.getJobId(),
                    track.getMediaId(),
                    track.getTaskIndex(),
                    track.getActionIndex(),
                    track.getStartOffsetFrameInclusive(),
                    track.getEndOffsetFrameInclusive(),
                    track.getStartOffsetTimeInclusive(),
                    track.getEndOffsetTimeInclusive(),
                    track.getMergedTaskIndex(),
                    track.getConfidence(),
                    newDetections,
                    track.getTrackProperties(),
                    track.getExemplarPolicy(),
                    track.getQualitySelectionProperty()));
        }

        Optional<String> shrunkToNothingString = shrunkToNothingFrames.build()
                .boxed()
                .collect(DetectionErrorUtil.toFrameRangesString());

        if (shrunkToNothingString.isPresent()) {
            long jobId = trackCache.getJobId();
            _log.warn(String.format("Shrunk one or more detection regions for job id %s to nothing. " +
                                            "1-pixel detection regions used instead. %s",
                                    jobId, shrunkToNothingString.get()));

            _inProgressBatchJobs.addWarning(
                    jobId, mediaId, IssueCodes.PADDING, String.format(
                    "Shrunk one or more detection regions to nothing. " +
                            "1-pixel detection regions used instead. %s", shrunkToNothingString.get()));
        }

        trackCache.updateTracks(mediaId, actionIndex, newTracks);
    }


    /**
     * Padding is applied uniformly on both sides of the detection region.
     * For example, an x padding value of 50 increases the width by 100 px (50 px padded on the left and right,
     * respectively).
     * For example, an x padding value of 50% on a region with a width of 100 px results in a width of 200 px
     * (50% of 100 px is 50 px, which is padded on the left and right, respectively).
     * If the detection width or height is shrunk to nothing, use a 1-pixel width or height, respectively.
     */
    public static Detection padDetection(String xPadding, String yPadding, int frameWidth, int frameHeight,
                                         Detection detection) {
        AffineTransform transform = getTransform(detection);

        Rectangle2D.Double correctedDetectionRect = transformToRect(detection, transform);
        Rectangle2D.Double grownDetectionRect = grow(correctedDetectionRect, xPadding, yPadding);
        Rectangle2D.Double clippedDetectionRect = clip(grownDetectionRect, frameWidth, frameHeight, transform);
        Rectangle2D.Double detectionRectMappedBack = inverseTransform(clippedDetectionRect, transform);

        Detection retvalDetection = rectToDetection(detectionRectMappedBack, detection);

        /*
        if (false) { // if true show visualization
            DebugCanvas.clear();
            Rectangle2D frameRect = new Rectangle2D.Double(0, 0, frameWidth, frameHeight);
            DebugCanvas.draw(frameRect, Color.yellow);
            DebugCanvas.draw(detection, Color.green, Color.red);
            DebugCanvas.draw(retvalDetection, Color.cyan, Color.magenta);
            DebugCanvas.show("padDetection");
        }
        */

        return retvalDetection;
    }


    private static Rectangle2D.Double transformToRect(Detection detection, AffineTransform transform) {
        double[] newTopLeft = new double[2];
        transform.transform(new double[] { detection.getX(), detection.getY() }, 0, newTopLeft, 0, 1);
        return new Rectangle2D.Double(newTopLeft[0], newTopLeft[1],
                                      detection.getWidth(), detection.getHeight());
    }


    private static Rectangle2D.Double grow(Rectangle2D.Double rect, String xPadding, String yPadding) {
        double changeInWidth = getPaddingPixelCount(xPadding, rect.getWidth());
        double changeInHeight = getPaddingPixelCount(yPadding, rect.getHeight());

        double newX = rect.getX() - changeInWidth;
        double newY = rect.getY() - changeInHeight;
        double newWidth = rect.getWidth() + 2 * changeInWidth;
        double newHeight = rect.getHeight() + 2 * changeInHeight;
        return new Rectangle2D.Double(newX, newY, newWidth, newHeight);
    }


    private static double getPaddingPixelCount(String padding, double totalLength) {
        if (padding.endsWith("%")) {
            double percent = Double.parseDouble(padding.substring(0, padding.length() - 1));
            percent = Math.max(-50, percent); // can't shrink to less than nothing
            return totalLength * percent / 100.0;
        }
        int offset = Integer.parseInt(padding);
        if (totalLength + (offset * 2) < 0) { // can't shrink to less than nothing
            return totalLength / -2.0;
        }
        return offset;
    }


    private static Rectangle2D.Double clip(Rectangle2D.Double detectionRect, int frameWidth, int frameHeight,
                                           AffineTransform transform) {
        var preTransformFrameRect = new Rectangle2D.Double(0, 0, frameWidth, frameHeight);
        var frameRect = transform.createTransformedShape(preTransformFrameRect).getBounds2D();
        Rectangle2D.Double result = new Rectangle2D.Double();
        Rectangle2D.intersect(detectionRect, frameRect, result);
        return result;
    }


    private static Rectangle2D.Double inverseTransform(Rectangle2D.Double rect, AffineTransform transform) {
        try {
            double[] newTopLeft = new double[2];
            transform.inverseTransform(new double[] { rect.getX(), rect.getY() }, 0, newTopLeft, 0, 1);
            return new Rectangle2D.Double(newTopLeft[0], newTopLeft[1], rect.getWidth(), rect.getHeight());
        }
        catch (NoninvertibleTransformException e) {
            // It is impossible for this exception to be thrown because rotation is always invertible.
            throw new IllegalStateException(e);
        }
    }


    private static Detection rectToDetection(Rectangle2D.Double rect, Detection originalDetection) {
        int x = (int) Math.round(rect.getX());
        int y = (int) Math.round(rect.getY());
        int width = (int) Math.ceil(rect.getWidth());
        int height = (int) Math.ceil(rect.getHeight());

        boolean shrunkToNothing = false;
        if (width <= 0) {
            width = 1;
            shrunkToNothing = true;
        }
        if (height <= 0) {
            height = 1;
            shrunkToNothing = true;
        }

        Map<String, String> detectionProperties;
        if (shrunkToNothing) {
            detectionProperties = new HashMap<>(originalDetection.getDetectionProperties());
            detectionProperties.put("SHRUNK_TO_NOTHING", "TRUE");
        }
        else {
            detectionProperties = originalDetection.getDetectionProperties();
        }

        return new Detection(
                x, y, width, height,
                originalDetection.getConfidence(),
                originalDetection.getMediaOffsetFrame(),
                originalDetection.getMediaOffsetTime(),
                detectionProperties);
    }
}
