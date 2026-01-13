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
import org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionRequest.AllAudioTracksRequest;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionRequest.AudioRequest;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionContext;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.util.TextUtils;
import org.mitre.mpf.wfm.util.TopQualitySelectionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component(AudioMediaSegmenter.REF)
public class AudioMediaSegmenter implements MediaSegmenter {
    private static final Logger log = LoggerFactory.getLogger(AudioMediaSegmenter.class);
    public static final String REF = "audioMediaSegmenter";

    private final TriggerProcessor _triggerProcessor;

    @Inject
    AudioMediaSegmenter(TriggerProcessor triggerProcessor) {
        _triggerProcessor = triggerProcessor;
    }


    @Override
    public List<DetectionRequest> createDetectionRequests(Media media, DetectionContext context) {
        log.info("Media #{} is an audio file and will not be segmented.", media.getId());
        if (!context.isFirstDetectionTask() && MediaSegmenter.feedForwardIsEnabled(context)) {
            if (MediaSegmenter.feedForwardAllTracksIsEnabled(context)) {
                var feedForwardAllTracksRequest = createFeedForwardAllTracksRequest(media, context);
                return feedForwardAllTracksRequest.isPresent() ? List.of(feedForwardAllTracksRequest.get()) : List.of();
            }
            return _triggerProcessor.getTriggeredTracks(media, context)
                    .map(t -> createFeedForwardRequest(t, media, context))
                    .toList();
        }

        var audioRequest = AudioRequest.newBuilder()
                .setStartTime(0)
                .setStopTime(-1)
                .build();
        var protobuf = createProtobuf(media, context, audioRequest);
        return List.of(new DetectionRequest(protobuf));
    }


    private static DetectionProtobuf.DetectionRequest createProtobuf(
            Media media, DetectionContext context, AudioRequest audioRequest) {
        return MediaSegmenter
                .initializeRequest(media, context)
                .setAudioRequest(audioRequest)
                .build();
    }

    private static DetectionProtobuf.DetectionRequest createProtobuf(
            Media media,
            DetectionContext context,
            AllAudioTracksRequest AllAudioTracksRequest) {
        return MediaSegmenter.initializeRequest(media, context)
                .setAllAudioTracksRequest(AllAudioTracksRequest)
                .build();
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

    private static DetectionProtobuf.AudioTrack.Builder createFeedForwardTrackBuilder(
        Track track, int topQualityCount, String topQualitySelectionProp, Media media, DetectionContext context) {

        Set<Detection> includedDetections;
        int startTime;
        int stopTime;
        if (topQualityCount <= 0) {
            includedDetections = track.getDetections();
            startTime = track.getStartOffsetTimeInclusive();
            stopTime = track.getEndOffsetTimeInclusive();
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
            startTime = frameSummaryStats.getMin();
            stopTime = frameSummaryStats.getMax();
        }

        var protobufTrackBuilder = DetectionProtobuf.AudioTrack.newBuilder()
                .setStartTime(startTime)
                .setStopTime(stopTime)
                .setConfidence(track.getConfidence())
                .putAllDetectionProperties(track.getTrackProperties());

        return protobufTrackBuilder;
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

        var allAudioTracksRequestBuilder = AllAudioTracksRequest.newBuilder();
        for (Track track : tracks) {
            var protobufTrackBuilder = 
                createFeedForwardTrackBuilder(track, topQualityCount, topQualitySelectionProp, media, context);
            allAudioTracksRequestBuilder.addFeedForwardTracks(protobufTrackBuilder);
        }

        var startTime = tracks.stream()
                .mapToInt(Track::getStartOffsetTimeInclusive)
                .min();

        var stopTime = tracks.stream()
                .mapToInt(Track::getEndOffsetTimeInclusive)
                .max();

        AllAudioTracksRequest allAudioTracksRequest = allAudioTracksRequestBuilder
                .setStartTime(startTime.getAsInt())
                .setStopTime(stopTime.getAsInt())
                .build();

        var protobuf = createProtobuf(media, context, allAudioTracksRequest);

        return Optional.of(new DetectionRequest(protobuf, tracks));
    }

    private static DetectionRequest createFeedForwardRequest(
            Track track, Media media, DetectionContext ctx) {
        AudioRequest.Builder audioRequest = AudioRequest.newBuilder()
                .setStartTime(track.getStartOffsetTimeInclusive())
                .setStopTime(track.getEndOffsetTimeInclusive());

        Detection exemplar = track.getExemplar();

        audioRequest.getFeedForwardTrackBuilder()
                .setConfidence(exemplar.getConfidence())
                .setStartTime(track.getStartOffsetTimeInclusive())
                .setStopTime(track.getEndOffsetTimeInclusive())
                .putAllDetectionProperties(exemplar.getDetectionProperties());

        var protobuf = createProtobuf(media, ctx, audioRequest.build());
        return new DetectionRequest(protobuf, track);
    }
}
