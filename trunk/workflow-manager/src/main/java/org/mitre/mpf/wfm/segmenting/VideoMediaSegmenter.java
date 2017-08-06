/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionContext;
import org.mitre.mpf.wfm.data.entities.transients.TransientMedia;
import org.mitre.mpf.wfm.util.TimePair;
import org.mitre.mpf.wfm.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component(VideoMediaSegmenter.REF)
public class VideoMediaSegmenter implements MediaSegmenter {
	private static final Logger log = LoggerFactory.getLogger(VideoMediaSegmenter.class);
	public static final String REF = "videoMediaSegmenter";

	@Autowired
	private TimeUtils timeUtils;

	@Override
	public List<Message> createDetectionRequestMessages(TransientMedia transientMedia, DetectionContext detectionContext) throws WfmProcessingException {
		List<Message> messages = new ArrayList<Message>();
		List<TimePair> segments;

		if (detectionContext.isFirstDetectionStage()) {
			List<TimePair> defaultTimePair = new ArrayList<TimePair>(1);
			defaultTimePair.add(new TimePair(0, transientMedia.getLength() - 1));
			segments = timeUtils.createSegments(defaultTimePair,
					detectionContext.getSegmentingPlan().getTargetSegmentLength(),
					detectionContext.getSegmentingPlan().getMinSegmentLength(),
					detectionContext.getSegmentingPlan().getMinGapBetweenSegments());
		} else {
			segments = timeUtils.createSegments(detectionContext.getPreviousTrackTimePairs(), detectionContext.getSegmentingPlan().getTargetSegmentLength(), detectionContext.getSegmentingPlan().getMinSegmentLength(), detectionContext.getSegmentingPlan().getMinGapBetweenSegments());
		}

		messages.addAll(createDetectionRequestMessages(transientMedia, detectionContext, segments));
		return messages;
	}

	private List<Message> createDetectionRequestMessages(TransientMedia transientMedia, DetectionContext detectionContext, List<TimePair> segments) {
		List<Message> messages = new ArrayList<Message>(segments.size());
		for(TimePair segment : segments) {
                    assert segment.getStartInclusive() >= 0 : String.format("Segment start must always be GTE 0. Actual: %d", segment.getStartInclusive());
                    assert segment.getEndInclusive() >= 0 : String.format("Segment end must always be GTE 0. Actual: %d", segment.getEndInclusive());
			log.debug("Creating segment [{}, {}] for {}.", segment.getStartInclusive(), segment.getEndInclusive(), transientMedia.getId());
			Message message = new DefaultMessage();

			DetectionProtobuf.DetectionRequest.Builder requestBuilder = DetectionProtobuf.DetectionRequest.newBuilder()
							.setDataType(DetectionProtobuf.DetectionRequest.DataType.VIDEO)
							.setRequestId(0)
							.setMediaId(transientMedia.getId())
							.setStageIndex(detectionContext.getStageIndex())
							.setStageName(detectionContext.getStageName())
							.setActionIndex(detectionContext.getActionIndex())
							.setActionName(detectionContext.getActionName())
							.setDataUri(transientMedia.getLocalPath())
							.addAllAlgorithmProperty(detectionContext.getAlgorithmProperties())
							.setVideoRequest(DetectionProtobuf.DetectionRequest.VideoRequest.newBuilder()
												.setStartFrame(segment.getStartInclusive())
												.setStopFrame(segment.getEndInclusive()));

			for (Map.Entry<String,String> entry : transientMedia.getMetadata().entrySet()) {
				requestBuilder.addMediaMetadata(DetectionProtobuf.PropertyMap.newBuilder()
					.setKey(entry.getKey())
					.setValue(entry.getValue()));
			}

			message.setBody(requestBuilder.build());
			messages.add(message);
		}
		return messages;
	}

}
