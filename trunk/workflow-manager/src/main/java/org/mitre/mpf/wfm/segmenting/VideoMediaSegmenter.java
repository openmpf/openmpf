/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

import org.apache.camel.Message;
import org.apache.camel.impl.DefaultMessage;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionRequest.VideoRequest;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionContext;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.data.entities.transients.TransientMedia;
import org.mitre.mpf.wfm.util.TimePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component(VideoMediaSegmenter.REF)
public class VideoMediaSegmenter implements MediaSegmenter {
	private static final Logger log = LoggerFactory.getLogger(VideoMediaSegmenter.class);
	public static final String REF = "videoMediaSegmenter";


	@Override
	public List<Message> createDetectionRequestMessages(
			TransientMedia media, DetectionContext context) {
		if (context.isFirstDetectionStage()) {
			return createTimePairMessages(
					media, context, Collections.singletonList(new TimePair(0, media.getLength() - 1)));
		}
		else if (MediaSegmenter.feedForwardIsEnabled(context)) {
			return createFeedForwardMessages(media, context);
		}
		else {
			List<TimePair> trackTimePairs = MediaSegmenter.createTimePairsForTracks(context.getPreviousTracks());
			return createTimePairMessages(media, context, trackTimePairs);
		}
	}


	private static List<Message> createTimePairMessages(
			TransientMedia media, DetectionContext context, Collection<TimePair> trackTimePairs) {

		List<TimePair> segments = MediaSegmenter.createSegments(
				trackTimePairs,
				context.getSegmentingPlan().getTargetSegmentLength(),
				context.getSegmentingPlan().getMinSegmentLength(),
				context.getSegmentingPlan().getMinGapBetweenSegments());

		List<Message> messages = new ArrayList<>(segments.size());
		for(TimePair segment : segments) {
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


	private static Message createProtobufMessage(
			TransientMedia media,
			DetectionContext context,
			VideoRequest videoRequest) {

		DetectionProtobuf.DetectionRequest detectionRequest = MediaSegmenter.initializeRequest(media, context)
				.setDataType(DetectionProtobuf.DetectionRequest.DataType.VIDEO)
				.setVideoRequest(videoRequest)
				.build();

		Message message = new DefaultMessage();
		message.setBody(detectionRequest);
		return message;
	}


	private static List<Message> createFeedForwardMessages(TransientMedia media, DetectionContext context) {
		int topConfidenceCount = getTopConfidenceCount(context);

		String xPadding = getPadding(context, FEED_FORWARD_X_PADDING);
		String yPadding = getPadding(context, FEED_FORWARD_Y_PADDING);

		List<Message> messages = new ArrayList<>();
		for (Track track : context.getPreviousTracks()) {
			if (track.getDetections().isEmpty()) {
				log.warn("Found track with no detections. No feed forward request will be created for: {}", track);
				continue;
			}

			// TODO: if there is padding, generate a new track, set exemplar
			if (!xPadding.equals("0") || !yPadding.equals("0")) {
				for (Detection detection : track.getDetections()) {

					int xOffset;
					if (xPadding.indexOf('%') != 0) {
						double xPercent = Double.parseDouble(xPadding.substring(0, -1));
						xOffset = (int) (xPercent / 100 * detection.getWidth());
					} else {
						xOffset = Integer.parseInt(xPadding);
					}

					int yOffset;
					if (yPadding.indexOf('%') != 0) {
						double yPercent = Double.parseDouble(yPadding.substring(0, -1));
						yOffset = (int) (yPercent / 100 * detection.getHeight());
					} else {
						yOffset = Integer.parseInt(yPadding);
					}

					// TODO: Need to get frame dim: media inspection?
					Detection newDetection = new Detection(
							Math.min(0, detection.getX() - xOffset),
							Math.min(0, detection.getY() - yOffset),
							Math.max(context.))

				}
			}

			VideoRequest videoRequest = createFeedForwardVideoRequest(track, topConfidenceCount);
			messages.add(createProtobufMessage(media, context, videoRequest));
		}

		return messages;
	}


	private static VideoRequest createFeedForwardVideoRequest(Track track, int topConfidenceCount) {

		Collection<Detection> topDetections = getTopConfidenceDetections(track.getDetections(), topConfidenceCount);
		IntSummaryStatistics frameSummaryStats = topDetections.stream()
				.mapToInt(Detection::getMediaOffsetFrame)
				.summaryStatistics();

		DetectionProtobuf.VideoTrack.Builder videoTrackBuilder = DetectionProtobuf.VideoTrack.newBuilder()
				.setStartFrame(frameSummaryStats.getMin())
				.setStopFrame(frameSummaryStats.getMax())
				.setConfidence(track.getExemplar().getConfidence());

		for (Detection detection : topDetections) {
			videoTrackBuilder.addFrameLocationsBuilder()
					.setFrame(detection.getMediaOffsetFrame())
					.setImageLocation(MediaSegmenter.createImageLocation(detection));
		}

		return VideoRequest.newBuilder()
				.setStartFrame(frameSummaryStats.getMin())
				.setStopFrame(frameSummaryStats.getMax())
				.setFeedForwardTrack(videoTrackBuilder)
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


	private static Collection<Detection> getTopConfidenceDetections(Collection<Detection> allDetections,
																	int topConfidenceCount) {
	}


	private static String getPadding(DetectionContext context, String propertyName) {
		String padding = context.getAlgorithmProperties()
				.stream()
				.filter(ap -> ap.getPropertyName().equalsIgnoreCase(propertyName))
				.map(ap -> ap.getPropertyValue())
				.findAny()
				.orElse("0");
		return padding.equals("0%") ? "0" : padding;
	}

	private static int getPercentOfDimension(String percent, int dimension) {

		double percentNum = Double.parseDouble(percent.substring(0, -1));
		if (percentNum < 0.0) {
			return 0;
		}
		else if (percentNum > 100.0) {
			return dimension;
		}
		return (int)(percentNum*dimension/100.0);
	}
}
