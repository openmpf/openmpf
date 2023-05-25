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
import org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionRequest.ImageRequest;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionContext;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component(ImageMediaSegmenter.REF)
public class ImageMediaSegmenter implements MediaSegmenter {
	public static final String REF = "imageMediaSegmenter";

	private final CamelContext _camelContext;

    private final TriggerProcessor _triggerProcessor;

	@Inject
	ImageMediaSegmenter(CamelContext camelContext, TriggerProcessor triggerProcessor) {
		_camelContext = camelContext;
        _triggerProcessor = triggerProcessor;
	}

	@Override
	public List<Message> createDetectionRequestMessages(Media media, DetectionContext context) {

		if (context.isFirstDetectionTask()) {
			return Collections.singletonList(createProtobufMessage(media, context, ImageRequest.getDefaultInstance()));
		}
		else if (MediaSegmenter.feedForwardIsEnabled(context)) {
            return _triggerProcessor.getTriggeredTracks(media, context)
                    .map(t -> createFeedForwardMessage(t, media, context))
                    .toList();
		}
		else if (!context.getPreviousTracks().isEmpty()) {
			return Collections.singletonList(createProtobufMessage(media, context, ImageRequest.getDefaultInstance()));
		}
		else {
			return Collections.emptyList();
		}
	}


	private Message createProtobufMessage(Media media, DetectionContext context,
	                                      ImageRequest imageRequest) {
		DetectionProtobuf.DetectionRequest detectionRequest = MediaSegmenter.initializeRequest(media, context)
				.setDataType(DetectionProtobuf.DetectionRequest.DataType.IMAGE)
				.setImageRequest(imageRequest)
				.build();

		Message message = new DefaultMessage(_camelContext);
		message.setBody(detectionRequest);
		return message;
	}


    private Message createFeedForwardMessage(Track track, Media media, DetectionContext ctx) {
        var imageLocation = MediaSegmenter.createImageLocation(track.getExemplar());
        ImageRequest imageRequest = ImageRequest.newBuilder()
                .setFeedForwardLocation(imageLocation)
                .build();
        return createProtobufMessage(media, ctx, imageRequest);
    }
}

