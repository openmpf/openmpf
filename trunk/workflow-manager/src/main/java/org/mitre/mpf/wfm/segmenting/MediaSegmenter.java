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

package org.mitre.mpf.wfm.segmenting;

import com.google.common.collect.ImmutableSet;
import org.javasimon.aop.Monitored;
import org.mitre.mpf.wfm.buffers.AlgorithmPropertyProtocolBuffer;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionContext;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.util.MediaRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static java.util.stream.Collectors.toList;

@Monitored
public interface MediaSegmenter {
    static final Logger log = LoggerFactory.getLogger(MediaSegmenter.class);

    public static final String FEED_FORWARD_TYPE = "FEED_FORWARD_TYPE";

    public static final String FEED_FORWARD_TOP_QUALITY_COUNT = "FEED_FORWARD_TOP_QUALITY_COUNT";

    static final Set<String> FEED_FORWARD_TYPES
            = ImmutableSet.of("NONE", "FRAME", "SUPERSET_REGION", "REGION");


    List<DetectionRequest> createDetectionRequests(Media media, DetectionContext context);


    public static DetectionProtobuf.DetectionRequest.Builder initializeRequest(
            Media media, DetectionContext context) {

        DetectionProtobuf.DetectionRequest.Builder requestBuilder = DetectionProtobuf.DetectionRequest.newBuilder()
                .setRequestId(0)
                .setMediaId(media.getId())
                .setTaskIndex(context.getTaskIndex())
                .setTaskName(context.getTaskName())
                .setActionIndex(context.getActionIndex())
                .setActionName(context.getActionName())
                .setDataUri(media.getProcessingPath().toString())
                .addAllAlgorithmProperty(getAlgoProps(context));

        for (Map.Entry<String, String> entry : media.getMetadata().entrySet()) {
            requestBuilder.addMediaMetadataBuilder()
                    .setKey(entry.getKey())
                    .setValue(entry.getValue());
        }

        return requestBuilder;
    }


    static List<AlgorithmPropertyProtocolBuffer.AlgorithmProperty> getAlgoProps(DetectionContext context) {
        if (context.isFirstDetectionTask()) {
            return context.getAlgorithmProperties().stream()
                    .filter(ap -> !ap.getPropertyName().equalsIgnoreCase(FEED_FORWARD_TYPE))
                    .filter(ap -> !ap.getPropertyName().equalsIgnoreCase(FEED_FORWARD_TOP_QUALITY_COUNT))
                    .collect(toList());
        }
        return context.getAlgorithmProperties();
    }


    public static boolean feedForwardIsEnabled(DetectionContext context) {
        return context.getAlgorithmProperties()
                .stream()
                .filter(ap -> ap.getPropertyName().equalsIgnoreCase(FEED_FORWARD_TYPE))
                .findAny()
                .map(ap -> feedForwardIsEnabled(ap.getPropertyValue()))
                .orElse(false);
    }


    public static boolean feedForwardIsEnabled(String feedForwardType) {
        if (feedForwardType == null
                || feedForwardType.isBlank()
                || "NONE".equalsIgnoreCase(feedForwardType)) {
            return false;
        }
        if (FEED_FORWARD_TYPES.contains(feedForwardType.toUpperCase())) {
            return true;
        }
        else {
            log.warn("Unknown feed forward type: {}. Disabling feed forward.", feedForwardType);
            return false;
        }
    }


    public static DetectionProtobuf.ImageLocation createImageLocation(Detection detection) {
        DetectionProtobuf.ImageLocation.Builder imageLocationBuilder = DetectionProtobuf.ImageLocation.newBuilder()
                .setXLeftUpper(detection.getX())
                .setYLeftUpper(detection.getY())
                .setWidth(detection.getWidth())
                .setHeight(detection.getHeight())
                .setConfidence(detection.getConfidence());

        for (Map.Entry<String, String> entry : detection.getDetectionProperties().entrySet()) {
            imageLocationBuilder.addDetectionPropertiesBuilder()
                    .setKey(entry.getKey())
                    .setValue(entry.getValue());
        }

        return imageLocationBuilder.build();
    }


    public static List<MediaRange> createRangesForTracks(Set<Track> tracks) {
        List<MediaRange> mediaRanges = new ArrayList<>();
        for (Track track : tracks) {
            // form MediaRanges for the set of tracks. Note that frame offsets are inclusive so no adjustments are necessary
            mediaRanges.add(new MediaRange(track.getStartOffsetFrameInclusive(), track.getEndOffsetFrameInclusive()));
        }
        Collections.sort(mediaRanges);
        return mediaRanges;
    }


    public static List<MediaRange> createSegments(Collection<MediaRange> inputs, int targetSegmentLength,
                                                  int minSegmentLength, int minGapBetweenSegments) {
        if (inputs == null || inputs.isEmpty()) {
            // If the input collection was empty (or null), no segments should be returned.
            return Collections.emptyList();
        }

        // Create a copy of the input list and sort it.
        List<MediaRange> tracks = new ArrayList<MediaRange>(inputs);
        Collections.sort(tracks);

        // Begin building the result list.
        List<MediaRange> result = new ArrayList<MediaRange>();

        MediaRange current = null;
        for (MediaRange nextTrack : tracks) {
            if (current == null) {
                current = nextTrack;
            }
            else {
                if (overlaps(current, nextTrack, minGapBetweenSegments)) {
                    current = merge(current, nextTrack);
                }
                else {
                    result.addAll(segment(current, targetSegmentLength, minSegmentLength));
                    current = nextTrack;
                }
            }
        }

        result.addAll(segment(current, targetSegmentLength, minSegmentLength));
        return result;
    }

    /**
     * Divides a large segment into a collection of one or more segments which respect the target and minimum segment
     * length parameters.
     *
     * If the length of the input is less than the provided targetSegmentLength, then only one segment will be returned.
     * It will be the entire size of the input.
     *
     * If the length of the input is at least the provided targetSegmentLength, all but the last segment in the returned
     * collection are guaranteed to have a length of targetSegmentLength. The last segment will have a length between
     * [minSegmentLength, targetSegmentLength + minSegmentLength - 1].
     *
     * @param mediaRange The MediaRange representing the inclusive start and stop times from which segments are
     *                      to be created.
     * @param targetSegmentLength The preferred size of each segment. If this value is less than or equal to 0,
     *                                 no segmenting will be performed.
     * @param minSegmentLength The minimum size of a segment.
     * @return A collection of zero or more MediaRange instances which represent the inclusive start and stop times.
     */
    public static Collection<MediaRange> segment(MediaRange mediaRange, int targetSegmentLength, int minSegmentLength) {
        if (targetSegmentLength <= 0 || targetSegmentLength == Integer.MAX_VALUE) {
            // The targetSegmentLength indicates that segmenting should not be performed.
            // Return a list containing the unmodified input segment.
            return Collections.singletonList(mediaRange);
        }

        List<MediaRange> result = new ArrayList<>(mediaRange.length() / targetSegmentLength);
        for (int start = mediaRange.getStartInclusive(); start <= mediaRange.getEndInclusive(); start += targetSegmentLength) {
            if (mediaRange.getEndInclusive() <= (start + (targetSegmentLength - 1) + (minSegmentLength - 1))) {
                result.add(new MediaRange(start, mediaRange.getEndInclusive()));
                break;
            }
            result.add(new MediaRange(start, start + targetSegmentLength - 1));
        }
        return result;
    }

    /**
     * Returns {@link java.lang.Boolean#TRUE} iff the current and probe tracks overlap each other from a temporal context.
     * Assumes that the current track has a start time which is less than or equal to the target track's start time.
     */
    public static boolean overlaps(MediaRange current, MediaRange target, int minGapBetweenSegments) {
        // Current spans [S, E], Target spans  [S*, E*], and it is known that S <= S*.
        // The tracks overlap if S <= S* <= E or (S* - E) <= G
        return (current.getStartInclusive() <= target.getStartInclusive() && target.getStartInclusive() <= current.getEndInclusive()) ||
                target.getStartInclusive() - current.getEndInclusive() <= minGapBetweenSegments;
    }

    /**
     * Modifies the current MediaRange such that it includes the entire range represented by the current
     * and target MediaRanges.
     */
    public static MediaRange merge(MediaRange current, MediaRange target) {
        return new MediaRange(current.getStartInclusive(),
                              Math.max(current.getEndInclusive(), target.getEndInclusive()));
    }
}
