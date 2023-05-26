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

import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultMessage;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionRequest.VideoRequest;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionContext;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.util.MediaRange;
import org.mitre.mpf.wfm.util.UserSpecifiedRangesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.*;

import static java.util.stream.Collectors.toList;

@Component(VideoMediaSegmenter.REF)
public class VideoMediaSegmenter implements MediaSegmenter {
    private static final Logger log = LoggerFactory.getLogger(VideoMediaSegmenter.class);
    public static final String REF = "videoMediaSegmenter";

    private final CamelContext _camelContext;

    @Inject
    VideoMediaSegmenter(CamelContext camelContext) {
        _camelContext = camelContext;
    }

    @Override
    public List<Message> createDetectionRequestMessages(
            Media media, DetectionContext context) {
        if (context.isFirstDetectionTask()) {
            Set<MediaRange> framesToProcess = UserSpecifiedRangesUtil.getCombinedRanges(media);
            // Process each range separately to prevent createMediaRangeMessages from filling
            // gaps between user specified ranges.
            return framesToProcess.stream()
                    .map(tp -> createMediaRangeMessages(media, context, List.of(tp)))
                    .flatMap(Collection::stream)
                    .collect(toList());
        }
        else if (MediaSegmenter.feedForwardIsEnabled(context)) {
            return createFeedForwardMessages(media, context);
        }
        else {
            List<MediaRange> trackMediaRanges = MediaSegmenter.createRangesForTracks(context.getPreviousTracks());
            return createMediaRangeMessages(media, context, trackMediaRanges);
        }
    }

    private List<Message> createMediaRangeMessages(
            Media media, DetectionContext context, Collection<MediaRange> trackMediaRanges) {

        List<MediaRange> segments = MediaSegmenter.createSegments(
                trackMediaRanges,
                context.getSegmentingPlan().getTargetSegmentLength(),
                context.getSegmentingPlan().getMinSegmentLength(),
                context.getSegmentingPlan().getMinGapBetweenSegments());

        List<Message> messages = new ArrayList<>(segments.size());
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

            messages.add(createProtobufMessage(media, context, videoRequest));
        }
        return messages;
    }


    private Message createProtobufMessage(
            Media media,
            DetectionContext context,
            VideoRequest videoRequest) {

        DetectionProtobuf.DetectionRequest detectionRequest = MediaSegmenter.initializeRequest(media, context)
                .setDataType(DetectionProtobuf.DetectionRequest.DataType.VIDEO)
                .setVideoRequest(videoRequest)
                .build();

        Message message = new DefaultMessage(_camelContext);
        message.setBody(detectionRequest);
        return message;
    }


    private List<Message> createFeedForwardMessages(Media media, DetectionContext context) {
        int topConfidenceCount = getTopConfidenceCount(context);

        List<Message> messages = new ArrayList<>();
        for (Track track : context.getPreviousTracks()) {
            if (track.getDetections().isEmpty()) {
                log.warn("Found track with no detections. No feed forward request will be created for: {}", track);
                continue;
            }

            VideoRequest videoRequest = createFeedForwardVideoRequest(track, topConfidenceCount);
            messages.add(createProtobufMessage(media, context, videoRequest));
        }

        return messages;
    }


    private static VideoRequest createFeedForwardVideoRequest(Track track, int topConfidenceCount) {
        Collection<Detection> includedDetections;
        int startFrame;
        int stopFrame;
        if (topConfidenceCount <= 0) {
            includedDetections = track.getDetections();
            startFrame = track.getStartOffsetFrameInclusive();
            stopFrame = track.getEndOffsetFrameInclusive();
        }
        else {
            includedDetections = getTopConfidenceDetections(track.getDetections(),
                                                            topConfidenceCount);
            var frameSummaryStats = includedDetections.stream()
                    .mapToInt(Detection::getMediaOffsetFrame)
                    .summaryStatistics();
            startFrame = frameSummaryStats.getMin();
            stopFrame = frameSummaryStats.getMax();
        }


        var protobufTrackBuilder = DetectionProtobuf.VideoTrack.newBuilder()
                .setStartFrame(startFrame)
                .setStopFrame(stopFrame)
                .setConfidence(track.getConfidence());

        for (Map.Entry<String, String> entry : track.getTrackProperties().entrySet()) {
            protobufTrackBuilder.addDetectionPropertiesBuilder()
                    .setKey(entry.getKey())
                    .setValue(entry.getValue());
        }

        for (Detection detection : includedDetections) {
            protobufTrackBuilder.addFrameLocationsBuilder()
                    .setFrame(detection.getMediaOffsetFrame())
                    .setImageLocation(MediaSegmenter.createImageLocation(detection));
        }

        return VideoRequest.newBuilder()
                .setStartFrame(startFrame)
                .setStopFrame(stopFrame)
                .setFeedForwardTrack(protobufTrackBuilder)
                .build();
    }



    private static Collection<Detection> getTopConfidenceDetections(Collection<Detection> allDetections,
                                                                    int topConfidenceCount) {
        if (topConfidenceCount <= 0 || topConfidenceCount >= allDetections.size()) {
            return allDetections;
        }

        Comparator<Detection> confidenceComparator = Comparator
                .comparingDouble(Detection::getConfidence)
                .thenComparing(Comparator.naturalOrder());

        PriorityQueue<Detection> topDetections = new PriorityQueue<>(topConfidenceCount, confidenceComparator);

        Iterator<Detection> allDetectionsIter = allDetections.iterator();
        for (int i = 0; i < topConfidenceCount; i++) {
            topDetections.add(allDetectionsIter.next());
        }

        while (allDetectionsIter.hasNext()) {
            Detection detection = allDetectionsIter.next();
            // Check if current detection is less than the minimum top detection so far
            if (confidenceComparator.compare(detection, topDetections.peek()) > 0) {
                topDetections.poll();
                topDetections.add(detection);
            }
        }
        return topDetections;
    }


    private static int getTopConfidenceCount(DetectionContext context) {
        return context.getAlgorithmProperties()
                .stream()
                .filter(ap -> ap.getPropertyName().equalsIgnoreCase(FEED_FORWARD_TOP_CONFIDENCE_COUNT))
                .mapToInt(ap -> Integer.parseInt(ap.getPropertyValue()))
                .findAny()
                .orElse(0);
    }
}
