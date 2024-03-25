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
import org.mitre.mpf.wfm.camel.operations.detection.DetectionContext;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * This segmenter returns an empty message collection and warns that the provided {@link Media}
 * does not have a type that is supported.
 */
@Component(DefaultMediaSegmenter.REF)
public class DefaultMediaSegmenter implements MediaSegmenter {
    private static final Logger log = LoggerFactory.getLogger(DefaultMediaSegmenter.class);

    public static final String REF = "defaultMediaSegmenter";

    private final TriggerProcessor _triggerProcessor;

    @Inject
    DefaultMediaSegmenter(TriggerProcessor triggerProcessor) {
        _triggerProcessor = triggerProcessor;
    }


    @Override
    public List<DetectionRequest> createDetectionRequests(Media media, DetectionContext context) {
        log.info("Media {} is of the MIME type '{}' and will be processed generically.",
                 media.getId(),
                 media.getMimeType());

        if (!context.isFirstDetectionTask() && MediaSegmenter.feedForwardIsEnabled(context)) {
            return _triggerProcessor.getTriggeredTracks(media, context)
                    .map(t -> createFeedForwardRequest(t, media, context))
                    .toList();
        }

        var genericRequest = DetectionProtobuf.DetectionRequest.GenericRequest.newBuilder().build();
        var protobuf = createProtobuf(media, context, genericRequest);
        return List.of(new DetectionRequest(protobuf));
    }


    private static DetectionProtobuf.DetectionRequest createProtobuf(
            Media media, DetectionContext context,
            DetectionProtobuf.DetectionRequest.GenericRequest genericRequest) {
        return MediaSegmenter.initializeRequest(media, context)
                .setGenericRequest(genericRequest)
                .build();
    }


    private static DetectionRequest createFeedForwardRequest(
            Track track, Media media, DetectionContext ctx) {
        var genericRequest = DetectionProtobuf.DetectionRequest.GenericRequest.newBuilder();

        Detection exemplar = track.getExemplar();

        genericRequest.getFeedForwardTrackBuilder()
                .setConfidence(exemplar.getConfidence())
                .putAllDetectionProperties(exemplar.getDetectionProperties());
        var protobuf = createProtobuf(media, ctx, genericRequest.build());
        return new DetectionRequest(protobuf, track);
    }
}
