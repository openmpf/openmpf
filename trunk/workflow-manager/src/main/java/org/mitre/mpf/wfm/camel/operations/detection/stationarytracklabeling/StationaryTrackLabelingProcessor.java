/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.camel.operations.detection.stationarytracklabeling;

import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Doubles;
import org.apache.camel.Exchange;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.camel.WfmProcessor;
import org.mitre.mpf.wfm.camel.operations.detection.trackmerging.TrackMergingContext;
import org.mitre.mpf.wfm.data.DetectionErrorUtil;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
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
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;

@Component(StationaryTrackLabelingProcessor.REF)
public class StationaryTrackLabelingProcessor extends WfmProcessor {
    public static final String REF = "StationaryTrackLabelingProcessor";

    private static final Logger _log = LoggerFactory.getLogger(StationaryTrackLabelingProcessor.class);

    private final JsonUtils _jsonUtils;

    private final InProgressBatchJobsService _inProgressBatchJobs;

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;


    @Inject
    StationaryTrackLabelingProcessor(
            JsonUtils jsonUtils,
            InProgressBatchJobsService inProgressBatchJobs,
            AggregateJobPropertiesUtil aggregateJobPropertiesUtil) {
        _jsonUtils = jsonUtils;
        _inProgressBatchJobs = inProgressBatchJobs;
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
    }


    @Override
    public void wfmProcess(Exchange exchange) throws WfmProcessingException {
        TrackMergingContext trackMergingContext = _jsonUtils.deserialize(exchange.getIn().getBody(byte[].class),
                TrackMergingContext.class);
        BatchJob job = _inProgressBatchJobs.getJob(trackMergingContext.getJobId());
        Task task = job.getPipelineElements().getTask(trackMergingContext.getTaskIndex());

        for (int actionIndex = 0; actionIndex < task.getActions().size(); actionIndex++) {
            Action action = job.getPipelineElements().getAction(trackMergingContext.getTaskIndex(), actionIndex);

            for (Media media : job.getMedia()) {
                if (media.isFailed()
                        || (media.getType() != MediaType.IMAGE && media.getType() != MediaType.VIDEO)) {
                    continue;
                }

                Function<String, String> combinedProperties =
                        _aggregateJobPropertiesUtil.getCombinedProperties(job, media, action);

                boolean labelStationaryTracks = Boolean.parseBoolean(combinedProperties.apply(MpfConstants.LABEL_STATIONARY_TRACKS));

                if (!labelStationaryTracks) {
                    continue;
                }

                boolean dropStationaryTracks = Boolean.parseBoolean(combinedProperties.apply(MpfConstants.DROP_STATIONARY_TRACKS));
                double iouThreshold = Double.parseDouble(combinedProperties.apply(MpfConstants.MIN_IOU_THRESHOLD_STATIONARY_OBJECTS));
                int minMovingObjects = Integer.parseInt(combinedProperties.apply(MpfConstants.MIN_DETECTIONS_FOR_NON_STATIONARY_TRACKS));
                int trackSizeDiff;

                Collection<Track> tracks = _inProgressBatchJobs.getTracks(job.getId(), media.getId(),
                        trackMergingContext.getTaskIndex(), actionIndex);

                Collection<Track> newTracks = updateStationaryTracks(
                        job.getId(), media.getId(), dropStationaryTracks, iouThreshold, minMovingObjects, tracks);

                trackSizeDiff = tracks.size() - newTracks.size();

                if (trackSizeDiff > 0) {
                    _log.warn(String.format("Dropping %d stationary tracks for job id %s.",
                            trackSizeDiff, job.getId()));
                    _inProgressBatchJobs.addWarning(
                            job.getId(), media.getId(), IssueCodes.LABEL_STATIONARY, String.format("Dropping %d stationary tracks for job id %s.",
                            trackSizeDiff, job.getId()));
                }

                _inProgressBatchJobs.setTracks(job.getId(), media.getId(),
                        trackMergingContext.getTaskIndex(), actionIndex, newTracks);
            }
        }

        exchange.getOut().setBody(exchange.getIn().getBody());
    }


    public static Collection<Track> updateStationaryTracks(long jobId, long mediaId, boolean dropStationaryTracks,
                                                     double iouThreshold, int minMovingObjects, Iterable<Track> tracks) {
        var newTracks = new TreeSet<Track>();

        for (Track track : tracks) {
            Track newTrack = processTrack(jobId, mediaId, iouThreshold, minMovingObjects, track);
            if (newTrack.getTrackProperties().get("IS_STATIONARY_TRACK") == "TRUE" && dropStationaryTracks) {
                continue;
            }
            newTracks.add(newTrack);
        }
        return newTracks;
    }


    public static Track processTrack(long jobId, long mediaId, double iouThreshold, int minMovingObjects, Track track) {
        Map<String, String> newTrackProperties;

        String isStationary = "TRUE";
        SortedSet<Detection> newDetections = new TreeSet<>();
        int nonStationaryObjects = 0;
        double avgX = 0, avgY = 0, avgWidth = 0, avgHeight = 0, trackSize = 0;

        for (Detection detection : track.getDetections()) {
            avgX += detection.getX();
            avgY += detection.getY();
            avgWidth += detection.getWidth();
            avgHeight += detection.getHeight();
            trackSize++;
        }

        avgX = avgX / trackSize;
        avgY = avgY / trackSize;
        avgWidth = avgWidth / trackSize;
        avgHeight = avgHeight / trackSize;
        Rectangle2D.Double avg_bbox = new Rectangle2D.Double(avgX, avgY, avgWidth, avgHeight);

        for (Detection detection : track.getDetections()) {
            Detection newDetection = relabelDetection(avg_bbox, iouThreshold, detection);
            if (newDetection.getDetectionProperties().get("IS_STATIONARY_OBJECT") == "FALSE") {
                nonStationaryObjects++;
            }
            newDetections.add(newDetection);
        }

        if (nonStationaryObjects >= minMovingObjects) {
            isStationary = "FALSE";
        }

        newTrackProperties = ImmutableMap.<String, String>builder()
                .putAll(track.getTrackProperties())
                .put("IS_STATIONARY_TRACK", isStationary)
                .build();

        return new Track(
                track.getJobId(),
                track.getMediaId(),
                track.getTaskIndex(),
                track.getActionIndex(),
                track.getStartOffsetFrameInclusive(),
                track.getEndOffsetFrameInclusive(),
                track.getStartOffsetTimeInclusive(),
                track.getEndOffsetTimeInclusive(),
                track.getType(),
                track.getConfidence(),
                newDetections,
                newTrackProperties);
    }

    public static Detection relabelDetection(Rectangle2D.Double rectangle1, double iouThreshold, Detection detection) {
        Rectangle2D.Double rectangle2 = new Rectangle2D.Double(detection.getX(), detection.getY(), detection.getWidth(), detection.getHeight());
        String objectIsStationary = "TRUE";
        if (rectangle1.getWidth() == 0 || rectangle2.getWidth() == 0 || rectangle1.getHeight() == 0 || rectangle2.getHeight() == 0) {
            return updateDetection(objectIsStationary, detection);
        }

        if (!rectangle1.intersects(rectangle2)) {
            if (0 < iouThreshold) {
                objectIsStationary = "FALSE";
            }
            return updateDetection(objectIsStationary, detection);
        }

        Rectangle2D.Double intersection = (Rectangle2D.Double) rectangle1.createIntersection(rectangle2);

        double intersectArea = intersection.getHeight() * intersection.getWidth();
        double unionArea = (rectangle2.getHeight() * rectangle2.getWidth()) + (rectangle1.getHeight() * rectangle1.getWidth()) - intersectArea;
        double percentOverlap = intersectArea / unionArea;

        if (percentOverlap < iouThreshold) {
            objectIsStationary = "FALSE";
        }

        return updateDetection(objectIsStationary, detection);
    }

    private static Detection updateDetection(String objectIsStationary, Detection originalDetection) {
        int x = originalDetection.getX();
        int y = originalDetection.getY();
        int width = originalDetection.getWidth();
        int height = originalDetection.getHeight();

        Map<String, String> detectionProperties = ImmutableMap.<String, String>builder()
                .putAll(originalDetection.getDetectionProperties())
                .put("IS_STATIONARY_OBJECT", objectIsStationary)
                .build();

        return new Detection(
                x, y, width, height,
                originalDetection.getConfidence(),
                originalDetection.getMediaOffsetFrame(),
                originalDetection.getMediaOffsetTime(),
                detectionProperties);
    }
}
