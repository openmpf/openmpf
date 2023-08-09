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

package org.mitre.mpf.wfm.camel.operations.detection.trackmerging;

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import org.apache.camel.Exchange;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.camel.WfmProcessor;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.TrackCache;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.SystemPropertiesSnapshot;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toCollection;

/**
 * Merges tracks in a video. Also, prunes short video tracks.
 *
 * When a video is segmented to produce detection requests between frames [M, N], there is a risk that an object will
 * appear somewhere in the segment and remain in the video beyond Frame N. If this happens, the detector which processes
 * frame N+1 will likely find and begin a new track for this same object. The purpose of the TrackMergingProcessor
 * is to find and merge any tracks of the same type which are chronologically adjacent and and sufficiently overlapping.
 *
 * Consider a ball that is last seen in Frame 10 at the position (10, 10) and with size 100x100. In Frame 11, if another
 * ball is detected at (11, 10) with size 100x100, it is likely that these two tracks are of the same object, and so
 * the tracks are merged. Once merged, the track is updated to keep only one exemplar.
 *
 * "Chronologically adjacent" depends on the properties associated with the detection action that created the tracks.
 * Specifically, if the detection frame interval is set to 20 (meaning one frame is sampled and then 19 frames are
 * skipped), Frames 0 and 20 are considered adjacent.
 *
 * The {@link TrackMergingPlan} provides all of the information
 * necessary to modify the default behavior of the track merging algorithm.
 */
@Component(TrackMergingProcessor.REF)
public class TrackMergingProcessor extends WfmProcessor {
    public static final String REF = "trackMergingProcessor";
    private static final Logger log = LoggerFactory.getLogger(TrackMergingProcessor.class);

    private final InProgressBatchJobsService _inProgressBatchJobs;

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;

    @Inject
    public TrackMergingProcessor(
            InProgressBatchJobsService inProgressBatchJobs,
            AggregateJobPropertiesUtil aggregateJobPropertiesUtil) {
        _inProgressBatchJobs = inProgressBatchJobs;
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
    }


    @Override
    public void wfmProcess(Exchange exchange) throws WfmProcessingException {
        var jobId = exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class);
        var taskIndex = exchange.getIn().getHeader(MpfHeaders.TASK_INDEX, Integer.class);

        var trackCache = new TrackCache(jobId, taskIndex, _inProgressBatchJobs);
        exchange.getOut().setBody(trackCache);

        BatchJob job = _inProgressBatchJobs.getJob(jobId);

        Task task = job.getPipelineElements().getTask(taskIndex);
        for (int actionIndex = 0; actionIndex < task.getActions().size(); actionIndex++) {
            Action action = job.getPipelineElements()
                    .getAction(taskIndex, actionIndex);

            for (Media media : job.getMedia()) {

                // NOTE: Only perform track merging and track pruning on video data.
                if (media.isFailed() || !media.matchesType(MediaType.VIDEO)) {
                    continue;
                }

                TrackMergingPlan trackMergingPlan = createTrackMergingPlan(job, media, action);

                boolean mergeRequested = trackMergingPlan.isMergeTracks();
                boolean pruneRequested = trackMergingPlan.getMinTrackLength() > 1;

                if (!mergeRequested && !pruneRequested) {
                    continue; // nothing to do
                }

                SortedSet<Track> tracks = trackCache.getTracks(media.getId(), actionIndex);

                if (tracks.isEmpty() || !isEligibleForFixup(tracks)) {
                    continue;
                }

                if (mergeRequested) {
                    int initialSize = tracks.size();
                    tracks = new TreeSet<>(combine(tracks, trackMergingPlan));

                    log.debug("Merging {} tracks down to {} in Media {}.",
                              initialSize, tracks.size(), media.getId());
                }

                if (pruneRequested) {
                    int initialSize = tracks.size();
                    int minTrackLength = trackMergingPlan.getMinTrackLength();
                    tracks = tracks.stream()
                            .filter(t -> t.getEndOffsetFrameInclusive() - t.getStartOffsetFrameInclusive() >= minTrackLength - 1)
                            .collect(toCollection(TreeSet::new));

                    log.debug("Pruning {} tracks down to {} tracks at least {} frames long in Media {}.",
                              initialSize, tracks.size(), minTrackLength, media.getId());
                }

                trackCache.updateTracks(media.getId(), actionIndex, tracks);
            }
        }
    }

    private TrackMergingPlan createTrackMergingPlan(BatchJob job, Media media,
                                                    Action action) {
        Function<String, String> combinedProperties = _aggregateJobPropertiesUtil.getCombinedProperties(
                job, media, action);

        // If there exist media-specific properties for track merging, use them.
        String minTrackLengthProperty = combinedProperties.apply(MpfConstants.MIN_TRACK_LENGTH);

        String mergeTracksProperty = combinedProperties.apply(MpfConstants.MERGE_TRACKS_PROPERTY);

        String minGapBetweenTracksProperty = combinedProperties.apply(MpfConstants.MIN_GAP_BETWEEN_TRACKS);

        String minTrackOverlapProperty = combinedProperties.apply(MpfConstants.MIN_TRACK_OVERLAP);

        SystemPropertiesSnapshot systemPropertiesSnapshot = job.getSystemPropertiesSnapshot();

        boolean mergeTracks = systemPropertiesSnapshot.isTrackMerging();
        int minGapBetweenTracks = systemPropertiesSnapshot.getMinAllowableTrackGap();
        int minTrackLength = systemPropertiesSnapshot.getMinTrackLength();
        double minTrackOverlap = systemPropertiesSnapshot.getTrackOverlapThreshold();

        if (mergeTracksProperty != null) {
            mergeTracks = Boolean.parseBoolean(mergeTracksProperty);
        }

        if (minGapBetweenTracksProperty != null) {
            try {
                minGapBetweenTracks = Integer.parseInt(minGapBetweenTracksProperty);
            } catch (NumberFormatException exception) {
                log.warn(String.format(
                        "Attempted to parse %s value of '%s' but encountered an exception. Defaulting to '%s'.",
                         MpfConstants.MIN_GAP_BETWEEN_TRACKS, minGapBetweenTracksProperty, minGapBetweenTracks),
                         exception);

            }
        }

        if (minTrackLengthProperty != null) {
            try {
                minTrackLength = Integer.parseInt(minTrackLengthProperty);
            } catch (NumberFormatException exception) {
                log.warn(String.format(
                        "Attempted to parse %s value of '%s', but encountered an exception. Defaulting to '%s'.",
                         MpfConstants.MIN_TRACK_LENGTH, minTrackLengthProperty, minTrackLength),
                         exception);
            }
        }

        if (minTrackOverlapProperty != null) {
            try {
                minTrackOverlap = Double.parseDouble(minTrackOverlapProperty);
            } catch (NumberFormatException exception) {
                log.warn(String.format(
                        "Attempted to parse %s value of '%s', but encountered an exception. Defaulting to '%s'.",
                        MpfConstants.MIN_TRACK_OVERLAP, minTrackOverlapProperty, minTrackOverlap),
                        exception);
            }
        }

        return new TrackMergingPlan(mergeTracks, minGapBetweenTracks, minTrackLength, minTrackOverlap);
    }

    private static Set<Track> combine(SortedSet<Track> sourceTracks, TrackMergingPlan plan) {
        // Do not attempt to merge an empty or null set.
        if (sourceTracks.isEmpty()) {
            return sourceTracks;
        }

        List<Track> tracks = new LinkedList<>(sourceTracks);
        List<Track> mergedTracks = new LinkedList<>();

        while (tracks.size() > 0) {
            // Pop off the track with the earliest start time.
            Track merged = tracks.remove(0);
            boolean performedMerge = false;
            Track trackToRemove = null;

            for (Track candidate : tracks) {
                // Iterate through the remaining tracks until a track is found which is within the frame gap and has sufficient region overlap.
                if (canMerge(merged, candidate, plan)) {
                    // If one is found, merge them and then push this track back to the beginning of the collection.
                    tracks.add(0, merge(merged, candidate));
                    performedMerge = true;

                    // Keep a reference to the track which was merged into the original - it will be removed.
                    trackToRemove = candidate;
                    break;
                }
            }

            if (performedMerge) {
                // A merge was performed, so it is necessary to remove the merged track.
                tracks.remove(trackToRemove);
            } else {
                // No merge was performed. The current track is no longer a candidate for merging.
                mergedTracks.add(merged);
            }
        }

        log.trace("Track merging complete. The {} input tracks were merged as appropriate to form {} output tracks.",
                  sourceTracks.size(), mergedTracks.size());

        return new HashSet<>(mergedTracks);
    }

    /** Combines two tracks. This is a destructive method. The contents of track1 reflect the merged track. */
    public static Track merge(Track track1, Track track2){

        Collection<Detection> detections = Stream.of(track1, track2)
                .flatMap(t -> t.getDetections().stream())
                .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));

        ImmutableSortedMap<String, String> properties = Stream.of(track1, track2)
                .flatMap(t -> t.getTrackProperties().entrySet().stream())
                .collect(ImmutableSortedMap.toImmutableSortedMap(
                        Comparator.naturalOrder(),
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (v1, v2) -> v1.equals(v2) ? v1 : v1 + "; " + v2));

        Track merged = new Track(
                track1.getJobId(),
                track1.getMediaId(),
                track1.getTaskIndex(),
                track1.getActionIndex(),
                track1.getStartOffsetFrameInclusive(),
                track2.getEndOffsetFrameInclusive(),
                track1.getStartOffsetTimeInclusive(),
                track2.getEndOffsetTimeInclusive(),
                track1.getType(),
                track1.getMergedType(),
                track1.getMergedAlgorithm(),
                Math.max(track1.getConfidence(), track2.getConfidence()),
                detections,
                properties,
                track1.getExemplarPolicy());
        return merged;
    }

    private static boolean canMerge(Track track1, Track track2, TrackMergingPlan plan) {
        return StringUtils.equalsIgnoreCase(track1.getType(), track2.getType())
                && isEligibleForMerge(track1, track2)
                && isWithinGap(track1, track2, plan.getMinGapBetweenTracks())
                && intersects(track1, track2, plan.getMinTrackOverlap());
    }

    private boolean isEligibleForFixup(SortedSet<Track> tracks) {
        // NOTE: All tracks should be the same type.
        String type = tracks.first().getType();
        return !_aggregateJobPropertiesUtil.isExemptFromTrackMerging(type);
    }

    // This method assumes that isEligibleForFixup() has been checked.
    private static boolean isEligibleForMerge(Track track1, Track track2) {
        // NOTE: All tracks should be the same type.
        switch (track1.getType().toUpperCase()) {
            case "CLASS":
                return isSameClassification(track1, track2);
            default:
                return true;
        }
    }

    private static boolean isSameClassification(Track track1, Track track2) {
        if (track1.getDetections().isEmpty() || track2.getDetections().isEmpty()) {
            return false;
        }
        String class1 = track1.getDetections().last().getDetectionProperties().get("CLASSIFICATION");
        String class2 = track2.getDetections().first().getDetectionProperties().get("CLASSIFICATION");
        return StringUtils.equalsIgnoreCase(class1, class2);
    }

    private static boolean isWithinGap(Track track1, Track track2, double minGapBetweenTracks) {
        if (track1.getEndOffsetFrameInclusive() + 1 == track2.getStartOffsetFrameInclusive()) {
            return true; // tracks are adjacent
        }
        return (track1.getEndOffsetFrameInclusive() < track2.getStartOffsetFrameInclusive()) &&
                (minGapBetweenTracks - 1 >= track2.getStartOffsetFrameInclusive() - track1.getEndOffsetFrameInclusive());
    }

    private static boolean intersects(Track track1, Track track2, double minTrackOverlap) {
        Detection track1End = track1.getDetections().last();
        Detection track2Start = track2.getDetections().first();

        Rectangle rectangle1 = new Rectangle(track1End.getX(), track1End.getY(), track1End.getWidth(), track1End.getHeight());
        Rectangle rectangle2 = new Rectangle(track2Start.getX(), track2Start.getY(), track2Start.getWidth(), track2Start.getHeight());

        if (rectangle1.getWidth() == 0 || rectangle2.getWidth() == 0 || rectangle1.getHeight() == 0 || rectangle2.getHeight() == 0) {
            return false;
        }

        Rectangle intersection = rectangle1.intersection(rectangle2);

        if (intersection.isEmpty()) {
            return 0 >= minTrackOverlap;
        }

        double intersectArea = intersection.getHeight() * intersection.getWidth();
        double unionArea = (rectangle2.getHeight() * rectangle2.getWidth()) + (rectangle1.getHeight() * rectangle1.getWidth()) - intersectArea;
        double percentOverlap = intersectArea / unionArea;

        return percentOverlap >= minTrackOverlap;
    }
}
