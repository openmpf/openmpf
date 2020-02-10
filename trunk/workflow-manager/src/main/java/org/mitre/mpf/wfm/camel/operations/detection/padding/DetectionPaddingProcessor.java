/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2020 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2020 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.camel.operations.detection.padding;

import org.apache.camel.Exchange;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.camel.WfmProcessor;
import org.mitre.mpf.wfm.camel.operations.detection.trackmerging.TrackMergingContext;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.awt.*;
import java.util.*;
import java.util.function.Function;

@Component(DetectionPaddingProcessor.REF)
public class DetectionPaddingProcessor extends WfmProcessor {
    public static final String REF = "detectionPaddingProcessor";

    private static final Logger _log = LoggerFactory.getLogger(DetectionPaddingProcessor.class);

    private final JsonUtils _jsonUtils;

    private final InProgressBatchJobsService _inProgressBatchJobs;

    private final PropertiesUtil _propertiesUtil;

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;


    @Inject
    DetectionPaddingProcessor(
            JsonUtils jsonUtils,
            InProgressBatchJobsService inProgressBatchJobs,
            PropertiesUtil propertiesUtil,
            AggregateJobPropertiesUtil aggregateJobPropertiesUtil) {
        _jsonUtils = jsonUtils;
        _inProgressBatchJobs = inProgressBatchJobs;
        _propertiesUtil = propertiesUtil;
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
    }


    @Override
    public void wfmProcess(Exchange exchange) throws WfmProcessingException {
        TrackMergingContext trackMergingContext = _jsonUtils.deserialize(exchange.getIn().getBody(byte[].class),
                                                                         TrackMergingContext.class);
        TransientJob job = _inProgressBatchJobs.getJob(trackMergingContext.getJobId());
        TransientStage stage = job.getPipeline().getStages().get(trackMergingContext.getStageIndex());

        for (int actionIndex = 0; actionIndex < stage.getActions().size(); actionIndex++) {

            for (TransientMedia media : job.getMedia()) {
                if (media.isFailed()
                        || (media.getMediaType() != MediaType.IMAGE
                        && media.getMediaType() != MediaType.VIDEO)) {
                    continue;
                }

                Function<String, String> combinedProperties = _aggregateJobPropertiesUtil.getCombinedProperties(
                        job, media.getId(), trackMergingContext.getStageIndex(), actionIndex);

                try {
                    if (!requiresPadding(combinedProperties)) {
                        continue;
                    }
                } catch (DetectionPaddingException e) {
                    // This should not happen because we checked that the detection properties were valid when the
                    // job was created.
                    throw new WfmProcessingException(e);
                }

                String xPadding = combinedProperties.apply(MpfConstants.DETECTION_PADDING_X);
                if (xPadding == null) {
                    xPadding = _propertiesUtil.getDetectionPaddingX();
                }

                String yPadding = combinedProperties.apply(MpfConstants.DETECTION_PADDING_Y);
                if (yPadding == null) {
                    yPadding = _propertiesUtil.getDetectionPaddingY();
                }

                int frameWidth = Integer.parseInt(media.getMetadata().get("FRAME_WIDTH"));
                int frameHeight = Integer.parseInt(media.getMetadata().get("FRAME_HEIGHT"));

                Collection<Track> tracks = _inProgressBatchJobs.getTracks(job.getId(), media.getId(),
                        trackMergingContext.getStageIndex(), actionIndex);

                Collection<Track> newTracks = processTracks(
                        job.getId(), xPadding, yPadding, frameWidth, frameHeight, tracks);

                _inProgressBatchJobs.setTracks(job.getId(), media.getId(),
                        trackMergingContext.getStageIndex(), actionIndex, newTracks);
            }
        }

        exchange.getOut().setBody(exchange.getIn().getBody());
    }


    public static boolean requiresPadding(Function<String, String> properties)
            throws DetectionPaddingException {
        return requiresPadding(properties, MpfConstants.DETECTION_PADDING_X) ||
               requiresPadding(properties, MpfConstants.DETECTION_PADDING_Y);
    }


    private static boolean requiresPadding(Function<String, String> properties, String propertyName)
            throws DetectionPaddingException {
        String padding = properties.apply(propertyName);
        if (padding == null) {
            return false;
        }
        try {
            if (padding.endsWith("%")) {
                double percent = Double.parseDouble(padding.substring(0, padding.length() - 1));
                if (percent <= -50.0) {
                    // can't shrink to nothing
                    throw new DetectionPaddingException(String.format(
                            "The %s property was set to \"%s\", but that would result in empty detections. " +
                                    "When specified as a percentage, padding values must be > -50%%.",
                            propertyName, padding));
                }
                return percent != 0.0;
            }
            return Integer.parseInt(padding) != 0;
        } catch (NumberFormatException e) {
            throw new DetectionPaddingException(String.format(
                    "The %s property was set to \"%s\", but that is not a valid value. " +
                            "Padding must be specified as whole number integer, or a percentage that ends " +
                            "with \"%%\". Percentages can be decimal values. Negative values are allowed in both " +
                            "cases, but percentages must be > -50%%.",
                    propertyName, padding));
        }
    }


    private Collection<Track> processTracks(long jobId, String xPadding, String yPadding,
                                                   int frameWidth, int frameHeight, Iterable<Track> tracks) {
        boolean shrunkToNothing = false;
        SortedSet<Track> newTracks = new TreeSet<>();

        for (Track track : tracks) {
            SortedSet<Detection> newDetections = new TreeSet<>();

            for (Detection detection : track.getDetections()) {
                int rotatedFrameWidth = frameWidth;
                int rotatedFrameHeight = frameHeight;
                boolean clipToFrame = false;

                // Don't clip padded region to frame when the detection has a non-orthogonal rotation.
                // Doing so may prevent capturing pixels near the frame corners.
                int rotation = getOrthogonalRotation(detection);
                if (rotation != -1) {
                    clipToFrame = true;
                    if (rotation == 90 || rotation == 270) {
                        rotatedFrameWidth = frameHeight;
                        rotatedFrameHeight = frameWidth;
                    }
                }
                Detection newDetection = padDetection(xPadding, yPadding, rotatedFrameWidth, rotatedFrameHeight,
                        detection, clipToFrame);
                shrunkToNothing |= newDetection.getDetectionProperties().containsKey("SHRUNK_TO_NOTHING");
                newDetections.add(newDetection);
            }

            newTracks.add(new Track(
                    track.getJobId(),
                    track.getMediaId(),
                    track.getStageIndex(),
                    track.getActionIndex(),
                    track.getStartOffsetFrameInclusive(),
                    track.getEndOffsetFrameInclusive(),
                    track.getStartOffsetTimeInclusive(),
                    track.getEndOffsetTimeInclusive(),
                    track.getType(),
                    track.getConfidence(),
                    newDetections,
                    track.getTrackProperties()));
        }

        if (shrunkToNothing) {
            _log.warn(String.format("Shrunk one or more detection regions for job id %s to nothing. " +
                    "1-pixel detection regions used instead.", jobId));
            _inProgressBatchJobs.addJobWarning(jobId, "Shrunk one or more detection regions to nothing. " +
                    "1-pixel detection regions used instead.");
        }

        return newTracks;
    }


    /**
     * Return 0, 90, 180, or 270 if the detection has an orthogonal rotation, or no rotation.
     * Return -1 for other (non-orthogonal) angles of rotation.
     */
    private static int getOrthogonalRotation(Detection detection) {
        String rotation = detection.getDetectionProperties().get("ROTATION");
        if (rotation == null) {
            // TODO: ROTATION_ANGLE is not used in R4.1+
            rotation = detection.getDetectionProperties().get("ROTATION_ANGLE");
            if (rotation == null) {
                return 0;
            }
        }
        double tmp = Double.parseDouble(rotation);
        int rot = (int)tmp;
        if (Math.abs(tmp - rot) > 0) {
            return -1; // decimal rotations are not orthogonal
        }
        rot = rot % 360;
        if (rot < 0) {
            rot += 360;
        }
        if (rot % 90 == 0) {
            return rot;
        }
        return -1;
    }


    /**
     * Padding is applied uniformly on both sides of the detection region.
     * For example, an x padding value of 50 increases the width by 100 px (50 px padded on the left and right,
     * respectively).
     * For example, an x padding value of 50% on a region with a width of 100 px results in a width of 200 px
     * (50% of 100 px is 50 px, which is padded on the left and right, respectively).
     * If the detection is shrunk to nothing, return a 1-pixel detection.
     */
    public static Detection padDetection(String xPadding, String yPadding, int frameWidth, int frameHeight,
                                         Detection detection, boolean clipToFrame) {
        int deltaX = getOffset(xPadding, detection.getWidth());
        int deltaY = getOffset(yPadding, detection.getHeight());

        Rectangle detectionRect = new Rectangle(detection.getX(), detection.getY(),
                detection.getWidth(), detection.getHeight());
        detectionRect.grow(deltaX, deltaY);

        if (clipToFrame) {
            Rectangle frameRect = new Rectangle(0, 0, frameWidth, frameHeight);
            detectionRect = detectionRect.intersection(frameRect);
        }

        SortedMap<String,String> detectionProperties = detection.getDetectionProperties();
        if (detectionRect.isEmpty()) {
            // return a 1-pixel detection at detection center
            detectionRect = new Rectangle((int)detectionRect.getCenterX(), (int)detectionRect.getCenterY(), 1, 1);
            detectionProperties = new TreeMap<>(detection.getDetectionProperties());
            detectionProperties.put("SHRUNK_TO_NOTHING", "TRUE");
        }

        return new Detection(
                detectionRect.x, detectionRect.y, detectionRect.width, detectionRect.height,
                detection.getConfidence(),
                detection.getMediaOffsetFrame(),
                detection.getMediaOffsetTime(),
                detectionProperties);
    }


    private static int getOffset(String padding, int length) {
        if (padding.endsWith("%")) {
            double percent = Double.parseDouble(padding.substring(0, padding.length()-1));
            percent = Math.max(-50, percent); // can't shrink to less than nothing
            double offset = length * percent / 100;
            return (int) (Math.signum(offset) * Math.ceil(Math.abs(offset))); // get negative or positive extreme
        }
        int offset = Integer.parseInt(padding);
        if (length + (offset * 2) < 0) { // can't shrink to less than nothing
            return (int) Math.floor(length / -2.0); // get negative extreme
        }
        return offset;
    }
}
