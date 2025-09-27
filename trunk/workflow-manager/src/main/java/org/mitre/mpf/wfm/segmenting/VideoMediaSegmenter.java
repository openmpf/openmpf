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

package org.mitre.mpf.wfm.segmenting;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionRequest.AllVideoTracksRequest;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionRequest.VideoRequest;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionContext;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.util.MediaRange;
import org.mitre.mpf.wfm.util.TextUtils;
import org.mitre.mpf.wfm.util.TopQualitySelectionUtil;
import org.mitre.mpf.wfm.util.UserSpecifiedRangesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component(VideoMediaSegmenter.REF)
public class VideoMediaSegmenter implements MediaSegmenter {
    private static final Logger log = LoggerFactory.getLogger(VideoMediaSegmenter.class);
    public static final String REF = "videoMediaSegmenter";

    private final TriggerProcessor _triggerProcessor;


    @Inject
    VideoMediaSegmenter(TriggerProcessor triggerProcessor) {
        _triggerProcessor = triggerProcessor;
    }


    @Override
    public List<DetectionRequest> createDetectionRequests(Media media, DetectionContext context) {
        if (context.isFirstDetectionTask()) {
            Set<MediaRange> framesToProcess = UserSpecifiedRangesUtil.getCombinedRanges(media);
            // Process each range separately to prevent createMediaRangeMessages from filling
            // gaps between user specified ranges.
            return framesToProcess.stream()
                    .map(tp -> createMediaRangeRequests(media, context, List.of(tp)))
                    .flatMap(Collection::stream)
                    .collect(toList());
        }
        else if (MediaSegmenter.feedForwardIsEnabled(context)) {
            if (MediaSegmenter.feedForwardAllTracksIsEnabled(context)) {
                var feedForwardAllTracksRequest = createFeedForwardAllTracksRequest(media, context);
                return feedForwardAllTracksRequest.isPresent() ? List.of(feedForwardAllTracksRequest.get()) : List.of();
            }
            return createFeedForwardRequests(media, context);
        }
        else {
            var trackMediaRanges = MediaSegmenter.createRangesForTracks(
                    context.getPreviousTracks());
            return createMediaRangeRequests(media, context, trackMediaRanges);
        }
    }


    private List<DetectionRequest> createMediaRangeRequests(
            Media media, DetectionContext context, Collection<MediaRange> trackMediaRanges) {

        List<MediaRange> segments = MediaSegmenter.createSegments(
                trackMediaRanges,
                context.getSegmentingPlan().getTargetSegmentLength(),
                context.getSegmentingPlan().getMinSegmentLength(),
                context.getSegmentingPlan().getMinGapBetweenSegments());

        var requests = new ArrayList<DetectionRequest>();
        for(MediaRange segment : segments) {
            assert segment.getStartInclusive() >= 0
                    : String.format("Segment start must always be GTE 0. Actual: %d", segment.getStartInclusive());
            assert segment.getEndInclusive() >= 0
                    : String.format("Segment end must always be GTE 0. Actual: %d", segment.getEndInclusive());

            log.debug("Creating segment [{}, {}] for {}.",
                      segment.getStartInclusive(), segment.getEndInclusive(), media.getId());

            VideoRequest videoRequest = VideoRequest.newBuilder()
                    .setStartFrame(segment.getStartInclusive())
                    .setStopFrame(segment.getEndInclusive())
                    .build();
            requests.add(new DetectionRequest(createProtobuf(media, context, videoRequest)));
        }
        return requests;
    }


    private static DetectionProtobuf.DetectionRequest createProtobuf(
            Media media,
            DetectionContext context,
            VideoRequest videoRequest) {
        return MediaSegmenter.initializeRequest(media, context)
                .setVideoRequest(videoRequest)
                .build();
    }

    private static DetectionProtobuf.DetectionRequest createProtobuf(
            Media media,
            DetectionContext context,
            AllVideoTracksRequest AllVideoTracksRequest) {
        return MediaSegmenter.initializeRequest(media, context)
                .setAllVideoTracksRequest(AllVideoTracksRequest)
                .build();
    }


    private Optional<DetectionRequest> createFeedForwardAllTracksRequest(Media media, DetectionContext context) {
        int topQualityCount = getTopQualityCount(context);
        String topQualitySelectionProp = context.getQualitySelectionProperty();
        var tracks = _triggerProcessor.getTriggeredTracks(media, context)
                .filter(t -> {
                    if (t.getDetections().isEmpty()) {
                        log.warn("Found track with no detections. "
                                    + "No feed forward request will be created for: {}", t);
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        if (tracks.isEmpty()) {
            return Optional.empty();
        }

        var allVideoTracksRequestBuilder = AllVideoTracksRequest.newBuilder();
        for (Track track : tracks) {
            var protobufTrackBuilder = 
                createFeedForwardTrackBuilder(track, topQualityCount, topQualitySelectionProp, media, context);
            allVideoTracksRequestBuilder.addFeedForwardTracks(protobufTrackBuilder);
        }

        var startFrame = tracks.stream()
                .mapToInt(Track::getStartOffsetFrameInclusive)
                .min();

        var stopFrame = tracks.stream()
                .mapToInt(Track::getEndOffsetFrameInclusive)
                .max();

        AllVideoTracksRequest allVideoTracksRequest = allVideoTracksRequestBuilder
                .setStartFrame(startFrame.getAsInt())
                .setStopFrame(stopFrame.getAsInt())
                .build();

        var protobuf = createProtobuf(media, context, allVideoTracksRequest);

        return Optional.of(new DetectionRequest(protobuf, tracks));
    }


    private List<DetectionRequest> createFeedForwardRequests(Media media, DetectionContext context) {
        int topQualityCount = getTopQualityCount(context);
        String topQualitySelectionProp = context.getQualitySelectionProperty();
        return _triggerProcessor.getTriggeredTracks(media, context)
                .filter(t -> {
                    if (t.getDetections().isEmpty()) {
                        log.warn("Found track with no detections. "
                                    + "No feed forward request will be created for: {}", t);
                        return false;
                    }
                    return true;
                })
                .map(t -> createFeedForwardRequest(t, topQualityCount, topQualitySelectionProp, media, context))
                .toList();
    }


    private DetectionRequest createFeedForwardRequest(
            Track track, int topQualityCount, String topQualitySelectionProp, Media media, DetectionContext context) {

        var protobufTrackBuilder = 
            createFeedForwardTrackBuilder(track, topQualityCount, topQualitySelectionProp, media, context);

        var videoRequest = VideoRequest.newBuilder()
                .setStartFrame(protobufTrackBuilder.getStartFrame())
                .setStopFrame(protobufTrackBuilder.getStopFrame())
                .setFeedForwardTrack(protobufTrackBuilder)
                .build();
        var protobuf = createProtobuf(media, context, videoRequest);

        return new DetectionRequest(protobuf, track);
    }


    private static DetectionProtobuf.VideoTrack.Builder createFeedForwardTrackBuilder(
        Track track, int topQualityCount, String topQualitySelectionProp, Media media, DetectionContext context) {

        Set<Detection> includedDetections;
        int startFrame;
        int stopFrame;
        if (topQualityCount <= 0) {
            includedDetections = track.getDetections();
            startFrame = track.getStartOffsetFrameInclusive();
            stopFrame = track.getEndOffsetFrameInclusive();
        }
        else {
            includedDetections = new TreeSet<>(TopQualitySelectionUtil.getTopQualityDetections(
                              track.getDetections(), topQualityCount, topQualitySelectionProp));

            var bestDetectionPropertyNamesList = getBestDetectionPropertyList(context);
            if (bestDetectionPropertyNamesList.isPresent()) {
                List<String> propNameList = TextUtils.parseListFromString(bestDetectionPropertyNamesList.get());
                propNameList = TextUtils.trimAndUpper(propNameList, Collectors.toList());
                for (Detection detection : track.getDetections()) {
                    for (String p : propNameList) {
                        if (detection.getDetectionProperties().containsKey(p)) {
                            log.debug("Will feed forward detection in frame {} with property {}", detection.getMediaOffsetFrame(), p);
                            includedDetections.add(detection);
                            break;
                        }
                    }
                }
            }
            var frameSummaryStats = includedDetections.stream()
                .mapToInt(Detection::getMediaOffsetFrame)
                .summaryStatistics();
            startFrame = frameSummaryStats.getMin();
            stopFrame = frameSummaryStats.getMax();
        }

        var protobufTrackBuilder = DetectionProtobuf.VideoTrack.newBuilder()
                .setStartFrame(startFrame)
                .setStopFrame(stopFrame)
                .setConfidence(track.getConfidence())
                .putAllDetectionProperties(track.getTrackProperties());

        for (Detection detection : includedDetections) {
            protobufTrackBuilder.putFrameLocations(
                    detection.getMediaOffsetFrame(),
                    MediaSegmenter.createImageLocation(detection));
        }

        return protobufTrackBuilder;
    }


    private static int getTopQualityCount(DetectionContext context) {
        return Optional.ofNullable(
                    context.getAlgorithmProperties().get(MediaSegmenter.FEED_FORWARD_TOP_QUALITY_COUNT))
                .map(Integer::parseInt)
                .orElse(0);
    }


    private static Optional<String> getBestDetectionPropertyList(DetectionContext context) {
        return Optional.ofNullable(
            context.getAlgorithmProperties().get(MediaSegmenter.FEED_FORWARD_BEST_DETECTION_PROPERTY_LIST));
    }
}
