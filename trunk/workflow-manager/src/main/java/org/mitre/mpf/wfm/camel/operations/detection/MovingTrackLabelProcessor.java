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
import com.google.common.collect.Maps;
import org.apache.camel.Exchange;
import org.mitre.mpf.wfm.camel.WfmProcessor;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.TrackCache;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.awt.geom.Rectangle2D;
import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

import static java.util.stream.Collectors.toCollection;

@Component(MovingTrackLabelProcessor.REF)
public class MovingTrackLabelProcessor extends WfmProcessor {
    public static final String REF = "MovingTrackLabelProcessor";

    private static final Logger _log = LoggerFactory.getLogger(MovingTrackLabelProcessor.class);

    private final InProgressBatchJobsService _inProgressJobs;

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;

    @Inject
    MovingTrackLabelProcessor(
            InProgressBatchJobsService inProgressJobs,
            AggregateJobPropertiesUtil aggregateJobPropertiesUtil) {
        _inProgressJobs = inProgressJobs;
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
    }


    @Override
    public void wfmProcess(Exchange exchange) {
        var trackCache = exchange.getIn().getBody(TrackCache.class);
        var job = _inProgressJobs.getJob(trackCache.getJobId());
        var task = job.getPipelineElements().getTask(trackCache.getTaskIndex());

        for (int actionIndex = 0; actionIndex < task.getActions().size(); actionIndex++) {
           var action = job.getPipelineElements().getAction(
                    trackCache.getTaskIndex(), actionIndex);

           for (var media : job.getMedia())  {
               if (media.isFailed() || !media.matchesType(MediaType.VIDEO)) {
                   continue;
               }

               var combinedProperties
                       = _aggregateJobPropertiesUtil.getCombinedProperties(job, media, action);

               var movingTrackLabelsEnabled = Boolean.parseBoolean(
                       combinedProperties.apply(MpfConstants.MOVING_TRACK_LABELS_ENABLED));
               if (!movingTrackLabelsEnabled) {
                   continue;
               }

               var movingTracksOnly = Boolean.parseBoolean(
                       combinedProperties.apply(MpfConstants.MOVING_TRACKS_ONLY));
               var maxIou = Double.parseDouble(
                       combinedProperties.apply(MpfConstants.MOVING_TRACK_MAX_IOU));
               int minMovingDetections = Integer.parseInt(
                       combinedProperties.apply(MpfConstants.MOVING_TRACK_MIN_DETECTIONS));


                var originalTracks = trackCache.getTracks(media.getId(), actionIndex);

               var labeledTracks = updateMovingTracks(movingTracksOnly, maxIou,
                                                      minMovingDetections, originalTracks);

               int numDropped = originalTracks.size() - labeledTracks.size();
               if (numDropped != 0) {
                   _log.warn("Job {}, task {}, action {} originally had {} tracks. {} was " +
                                 "true so {} tracks were dropped because they were not in motion.",
                             job.getId(), trackCache.getTaskIndex(), actionIndex,
                             originalTracks.size(), MpfConstants.MOVING_TRACKS_ONLY, numDropped);
               }
                trackCache.updateTracks(media.getId(), actionIndex, labeledTracks);
           }
        }
        exchange.getOut().setBody(exchange.getIn().getBody());
    }


    private static SortedSet<Track> updateMovingTracks(
            boolean movingTracksOnly, double maxIou, int minMovingDetections,
            Collection<Track> originalTracks) {
        if (movingTracksOnly) {
            return originalTracks.stream()
                    .map(t -> processTrack(maxIou, minMovingDetections, t))
                    .filter(t -> Boolean.parseBoolean(t.getTrackProperties().get("MOVING")))
                    .collect(toCollection(TreeSet::new));
        }
        else {
            return originalTracks.stream()
                    .map(t -> processTrack(maxIou, minMovingDetections, t))
                    .collect(toCollection(TreeSet::new));
        }
    }


    private static Track processTrack(double maxIou, int minMovingDetections, Track track) {
        double avgX = 0, avgY = 0, avgWidth = 0, avgHeight = 0;
        for (Detection detection : track.getDetections()) {
            avgX += detection.getX();
            avgY += detection.getY();
            avgWidth += detection.getWidth();
            avgHeight += detection.getHeight();
        }

        int trackSize = track.getDetections().size();
        avgX /= trackSize;
        avgY /= trackSize;
        avgWidth /= trackSize;
        avgHeight /= trackSize;
        var averageBox = new Rectangle2D.Double(avgX, avgY, avgWidth, avgHeight);

        int numMovingDetections = 0;
        var newDetectionsBuilder = ImmutableSortedSet.<Detection>naturalOrder();
        for (Detection detection : track.getDetections()) {
            var isMoving = isMovingDetection(averageBox, maxIou, detection);
            newDetectionsBuilder.add(addMotionLabel(isMoving, detection));
            if (isMoving) {
                numMovingDetections++;
            }
        }

        boolean trackIsMoving = numMovingDetections >= minMovingDetections;
        var newTrackProperties = ImmutableSortedMap.<String, String>naturalOrder()
                .putAll(Maps.filterKeys(track.getTrackProperties(), key -> !key.equals("MOVING")))
                .put("MOVING", trackIsMoving ? "TRUE" : "FALSE")
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
                track.getMergedType(),
                track.getMergedAlgorithm(),
                track.getConfidence(),
                newDetectionsBuilder.build(),
                newTrackProperties,
                track.getExemplarPolicy());
    }


    private static boolean isMovingDetection(Rectangle2D.Double averageBox,
                                             double maxIou, Detection detection) {
        var detectionBox = new Rectangle2D.Double(detection.getX(), detection.getY(),
                                                  detection.getWidth(), detection.getHeight());
        if (averageBox.isEmpty() || detectionBox.isEmpty()) {
            return false;
        }

        double iou;
        var intersection = averageBox.createIntersection(detectionBox);
        if (intersection.isEmpty()) {
            iou = 0;
        }
        else {
            double intersectionArea = intersection.getHeight() * intersection.getWidth();
            double avgBoxArea = averageBox.getHeight() * averageBox.getWidth();
            double detectionArea = detection.getHeight() * detection.getWidth();
            double unionArea = avgBoxArea + detectionArea - intersectionArea;
            iou = intersectionArea / unionArea;
        }
        return iou <= maxIou;
    }


    private static Detection addMotionLabel(boolean isMoving, Detection detection) {
        var newDetectionProperties = ImmutableSortedMap.<String, String>naturalOrder()
                .putAll(Maps.filterKeys(detection.getDetectionProperties(), key -> !key.equals("MOVING")))
                .put("MOVING", isMoving ? "TRUE" : "FALSE")
                .build();

        return new Detection(
                detection.getX(),
                detection.getY(),
                detection.getWidth(),
                detection.getHeight(),
                detection.getConfidence(),
                detection.getMediaOffsetFrame(),
                detection.getMediaOffsetTime(),
                newDetectionProperties);
    }
}
