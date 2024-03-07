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

import java.util.List;

import javax.inject.Inject;

import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionRequest.AudioRequest;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionContext;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
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
