/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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
import org.mitre.mpf.wfm.camel.operations.detection.DetectionContext;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * This segmenter returns an empty message collection and warns that the provided {@link Media}
 * does not have a type that is supported.
 */
@Component(DefaultMediaSegmenter.REF)
public class DefaultMediaSegmenter implements MediaSegmenter {
    private static final Logger log = LoggerFactory.getLogger(DefaultMediaSegmenter.class);

    public static final String REF = "defaultMediaSegmenter";

    private final CamelContext _camelContext;

    private final TriggerProcessor _triggerProcessor;

    @Inject
    DefaultMediaSegmenter(CamelContext camelContext, TriggerProcessor triggerProcessor) {
        _camelContext = camelContext;
        _triggerProcessor = triggerProcessor;
    }

    @Override
    public List<Message> createDetectionRequestMessages(Media media, DetectionContext context) {
        log.warn("Media {} is of the MIME type '{}' and will be processed generically.",
                 media.getId(),
                 media.getMimeType());

        if (!context.isFirstDetectionTask() && MediaSegmenter.feedForwardIsEnabled(context)) {
            return createFeedForwardMessages(media, context);
        }

        return Collections.singletonList(
                createProtobufMessage(media, context,
                                      DetectionProtobuf.DetectionRequest.GenericRequest.newBuilder().build()));
    }

    private Message createProtobufMessage(Media media, DetectionContext context,
                                          DetectionProtobuf.DetectionRequest.GenericRequest genericRequest) {
        DetectionProtobuf.DetectionRequest detectionRequest = MediaSegmenter.initializeRequest(media, context)
                .setDataType(DetectionProtobuf.DetectionRequest.DataType.UNKNOWN)
                .setGenericRequest(genericRequest)
                .build();

        Message message = new DefaultMessage(_camelContext);
        message.setBody(detectionRequest);
        return message;
    }

    private List<Message> createFeedForwardMessages(Media media, DetectionContext context) {
        var prevTracks = _triggerProcessor.getTriggeredTracks(media, context);
        List<Message> messages = new ArrayList<>(prevTracks.size());
        for (Track track : prevTracks) {

            DetectionProtobuf.DetectionRequest.GenericRequest.Builder genericRequest = DetectionProtobuf.DetectionRequest.GenericRequest.newBuilder();

            Detection exemplar = track.getExemplar();

            DetectionProtobuf.GenericTrack.Builder genericTrackBuilder = genericRequest.getFeedForwardTrackBuilder()
                    .setConfidence(exemplar.getConfidence());

            for (Map.Entry<String, String> entry : exemplar.getDetectionProperties().entrySet()) {
                genericTrackBuilder.addDetectionPropertiesBuilder()
                        .setKey(entry.getKey())
                        .setValue(entry.getValue());
            }

            messages.add(createProtobufMessage(media, context, genericRequest.build()));
        }
        return messages;
    }
}
