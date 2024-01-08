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

import jakarta.inject.Inject;

import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionRequest.ImageRequest;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionContext;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.springframework.stereotype.Component;

@Component(ImageMediaSegmenter.REF)
public class ImageMediaSegmenter implements MediaSegmenter {
    public static final String REF = "imageMediaSegmenter";

    private final TriggerProcessor _triggerProcessor;

	@Inject
	ImageMediaSegmenter(TriggerProcessor triggerProcessor) {
        _triggerProcessor = triggerProcessor;
	}


	@Override
	public List<DetectionRequest> createDetectionRequests(Media media, DetectionContext context) {
		if (context.isFirstDetectionTask()) {
            return createSingleRequest(media, context);
		}
		else if (MediaSegmenter.feedForwardIsEnabled(context)) {
            return _triggerProcessor.getTriggeredTracks(media, context)
                    .map(t -> createFeedForwardRequest(t, media, context))
                    .toList();
		}
		else if (!context.getPreviousTracks().isEmpty()) {
            return createSingleRequest(media, context);
		}
		else {
			return List.of();
		}
	}


    private static List<DetectionRequest> createSingleRequest(
            Media media, DetectionContext context) {
        var protobuf = createProtobuf(media, context, ImageRequest.getDefaultInstance());
        return List.of(new DetectionRequest(protobuf));
    }


	private static DetectionProtobuf.DetectionRequest createProtobuf(
            Media media, DetectionContext context, ImageRequest imageRequest) {
		return MediaSegmenter.initializeRequest(media, context)
				.setDataType(DetectionProtobuf.DetectionRequest.DataType.IMAGE)
				.setImageRequest(imageRequest)
				.build();
	}


    private static DetectionRequest createFeedForwardRequest(
            Track track, Media media, DetectionContext ctx) {
        var imageLocation = MediaSegmenter.createImageLocation(track.getExemplar());
        ImageRequest imageRequest = ImageRequest.newBuilder()
                .setFeedForwardLocation(imageLocation)
                .build();
        return new DetectionRequest(createProtobuf(media, ctx, imageRequest), track);
    }
}
