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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultMessage;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf.AudioTrack;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionRequest.AudioRequest;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionContext;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.service.TaskMergingManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component(AudioMediaSegmenter.REF)
public class AudioMediaSegmenter implements MediaSegmenter {
    private static final Logger log = LoggerFactory.getLogger(AudioMediaSegmenter.class);
    public static final String REF = "audioMediaSegmenter";

    private final CamelContext _camelContext;

    private final TriggerProcessor _triggerProcessor;

    private final TaskMergingManager _taskMergingManager;

    @Inject
    AudioMediaSegmenter(
            CamelContext camelContext,
            TriggerProcessor triggerProcessor,
            TaskMergingManager taskMergingManager) {
        _camelContext = camelContext;
        _triggerProcessor = triggerProcessor;
        _taskMergingManager = taskMergingManager;
    }

    @Override
    public List<Message> createDetectionRequestMessages(
            BatchJob job, Media media, DetectionContext context) {
        log.warn("Media #{} is an audio file and will not be segmented.", media.getId());

        if (!context.isFirstDetectionTask() && MediaSegmenter.feedForwardIsEnabled(context)) {
            var taskMergingContext = _taskMergingManager.getRequestContext(
                    job, media, context.getTaskIndex(), context.getActionIndex());
            return _triggerProcessor.getTriggeredTracks(media, context)
                .map(t -> taskMergingContext.addBreadCrumbIfNeeded(
                            createFeedForwardMessage(t, media, context), t))
                .toList();
        }

        return Collections.singletonList(
                createProtobufMessage(media, context,
                                      AudioRequest.newBuilder().setStartTime(0).setStopTime(-1).build()));
    }


    private Message createProtobufMessage(Media media, DetectionContext context,
                                                 AudioRequest audioRequest) {
        DetectionProtobuf.DetectionRequest request = MediaSegmenter
                .initializeRequest(media, context)
                .setDataType(DetectionProtobuf.DetectionRequest.DataType.AUDIO)
                .setAudioRequest(audioRequest)
                .build();

        Message message = new DefaultMessage(_camelContext);
        message.setBody(request);
        return message;
    }


    private Message createFeedForwardMessage(Track track, Media media, DetectionContext ctx) {
        AudioRequest.Builder audioRequest = AudioRequest.newBuilder()
                .setStartTime(track.getStartOffsetTimeInclusive())
                .setStopTime(track.getEndOffsetTimeInclusive());

        Detection exemplar = track.getExemplar();

        AudioTrack.Builder audioTrackBuilder = audioRequest.getFeedForwardTrackBuilder()
                .setConfidence(exemplar.getConfidence())
                .setStartTime(track.getStartOffsetTimeInclusive())
                .setStopTime(track.getEndOffsetTimeInclusive());

        for (Map.Entry<String, String> entry : exemplar.getDetectionProperties().entrySet()) {
            audioTrackBuilder.addDetectionPropertiesBuilder()
                    .setKey(entry.getKey())
                    .setValue(entry.getValue());
        }
        return createProtobufMessage(media, ctx, audioRequest.build());
    }
}
