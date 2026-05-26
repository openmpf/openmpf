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

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf.ImageLocation;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionRequest.AllImageLocationsRequest;
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
            if (MediaSegmenter.feedForwardAllTracksIsEnabled(context)) {
                var feedForwardAllTracksRequest = createFeedForwardAllImageLocationsRequest(media, context);
                return feedForwardAllTracksRequest.isPresent() ? List.of(feedForwardAllTracksRequest.get()) : List.of();
            }            
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
            Media media,
            DetectionContext context,
            ImageRequest imageRequest) {
		return MediaSegmenter.initializeRequest(media, context)
				.setImageRequest(imageRequest)
				.build();
	}
    
    private static DetectionProtobuf.DetectionRequest createProtobuf(
            Media media,
            DetectionContext context,
            AllImageLocationsRequest allImageLocationsRequest) {
        return MediaSegmenter.initializeRequest(media, context)
                .setAllImageLocationsRequest(allImageLocationsRequest)
                .build();
    }

    private Optional<DetectionRequest> createFeedForwardAllImageLocationsRequest(Media media, DetectionContext context) {
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

        var allImageLocationsRequestBuilder = AllImageLocationsRequest.newBuilder();
        for (Track track : tracks) {
            var imageLocation = MediaSegmenter.createImageLocation(track.getExemplar());
            var protobufLocationBuilder = DetectionProtobuf.ImageLocation.newBuilder()
                    .setXLeftUpper(imageLocation.getXLeftUpper())
                    .setYLeftUpper(imageLocation.getYLeftUpper())
                    .setWidth(imageLocation.getWidth())
                    .setHeight(imageLocation.getHeight())
                    .setConfidence(imageLocation.getConfidence())
                    .putAllDetectionProperties(imageLocation.getDetectionPropertiesMap());

            allImageLocationsRequestBuilder.addFeedForwardLocations(protobufLocationBuilder);
        }

        AllImageLocationsRequest allImageLocationsRequest = allImageLocationsRequestBuilder
                .build();

        var protobuf = createProtobuf(media, context, allImageLocationsRequest);

        return Optional.of(new DetectionRequest(protobuf, tracks));
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
