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

package org.mitre.mpf.wfm.camel.operations.markup;

import org.apache.camel.Message;
import org.apache.camel.impl.DefaultMessage;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.mime.MimeTypes;
import org.mitre.mpf.videooverlay.BoundingBox;
import org.mitre.mpf.videooverlay.BoundingBoxMap;
import org.mitre.mpf.wfm.buffers.Markup;
import org.mitre.mpf.wfm.camel.StageSplitter;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.RedisImpl;
import org.mitre.mpf.wfm.data.access.MarkupResultDao;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateMarkupResultDaoImpl;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.enums.MpfEndpoints;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.*;

@Component(MarkupStageSplitter.REF)
public class MarkupStageSplitter implements StageSplitter {
	public static final String REF = "markupStageSplitter";
	private static final Logger log = LoggerFactory.getLogger(MarkupStageSplitter.class);

	@Autowired
	@Qualifier(RedisImpl.REF)
	private Redis redis;

	@Autowired
	private PropertiesUtil propertiesUtil;

	@Autowired
	@Qualifier(HibernateMarkupResultDaoImpl.REF)
	private MarkupResultDao hibernateMarkupResultDao;


	/**
	 * Returns the last task in the pipeline containing a detection action. This effectively filters preprocessor
	 * detections so that the output is not cluttered with motion detections.
	 */
	private int findLastDetectionStageIndex(TransientPipeline transientPipeline) {
		int stageIndex = -1;
		for(int i = 0; i < transientPipeline.getStages().size(); i++) {
			TransientStage transientStage = transientPipeline.getStages().get(i);
			if(transientStage.getActionType() == ActionType.DETECTION) {
				stageIndex = i;
			}
		}

		assert 0 <= stageIndex : String.format("The stage index of %d was expected to be greater than or equal to 0.", stageIndex);
		return stageIndex;
	}

	/** Creates a BoundingBoxMap containing all of the tracks which were produced by the specified action history keys. */
	private BoundingBoxMap createMap(TransientJob job, TransientMedia media, int stageIndex, TransientStage transientStage) {
		BoundingBoxMap boundingBoxMap = new BoundingBoxMap();
		long mediaId = media.getId();
		for (int actionIndex = 0; actionIndex < transientStage.getActions().size(); actionIndex++) {
			SortedSet<Track> tracks = redis.getTracks(job.getId(), mediaId, stageIndex, actionIndex);
			for (Track track : tracks) {
				addTrackToBoundingBoxMap(track, boundingBoxMap);
			}
		}
		return boundingBoxMap;
	}


	private static void addTrackToBoundingBoxMap(Track track, BoundingBoxMap boundingBoxMap) {
		Random random = new Random(track.hashCode());
		int[] randomColor = { 56 + random.nextInt(200), 56 + random.nextInt(200),
								56 + random.nextInt(200) };

		List<Detection> orderedDetections = new ArrayList<>(track.getDetections());
		Collections.sort(orderedDetections);
		for (int i = 0; i < orderedDetections.size(); i++) {
			Detection detection = orderedDetections.get(i);
			int currentFrame = detection.getMediaOffsetFrame();

			// Create a bounding box at the location.
			BoundingBox boundingBox = new BoundingBox();
			boundingBox.setWidth(detection.getWidth());
			boundingBox.setHeight(detection.getHeight());
			boundingBox.setX(detection.getX());
			boundingBox.setY(detection.getY());
			boundingBox.setColor(randomColor[0], randomColor[1], randomColor[2]);

			String objectType = track.getType();
			if ("SPEECH".equalsIgnoreCase(objectType) || "AUDIO".equalsIgnoreCase(objectType)) {
				// Special case: Speech doesn't populate object locations for each frame in the video, so you have to
				// go by the track start and stop frames.
				boundingBoxMap.putOnFrames(track.getStartOffsetFrameInclusive(),
				                           track.getEndOffsetFrameInclusive(), boundingBox);
				break;
			}

			boolean isLastDetection = (i == (orderedDetections.size() - 1));
			if (isLastDetection) {
				boundingBoxMap.putOnFrame(currentFrame, boundingBox);
				break;
			}

			Detection nextDetection = orderedDetections.get(i + 1);
			int gapBetweenNextDetection = nextDetection.getMediaOffsetFrame() - detection.getMediaOffsetFrame();
			if (gapBetweenNextDetection == 1) {
				boundingBoxMap.putOnFrame(currentFrame, boundingBox);
			}
			else {
				// Since the gap between frames is greater than 1 and we are not at the last result in the
				// collection, we draw bounding boxes on each frame in the collection such that on the
				// first frame, the bounding box is at the position given by the object location, and on the
				// last frame in the interval, the bounding box is very close to the position given by the object
				// location of the next result. Consequently, the original bounding box appears to resize
				// and translate to the position and size of the next result's bounding box.
				BoundingBox nextBoundingBox = new BoundingBox();
				nextBoundingBox.setWidth(nextDetection.getWidth());
				nextBoundingBox.setHeight(nextDetection.getHeight());
				nextBoundingBox.setX(nextDetection.getX());
				nextBoundingBox.setY(nextDetection.getY());
				nextBoundingBox.setColor(boundingBox.getColor());
				boundingBoxMap.animate(boundingBox, nextBoundingBox, currentFrame, gapBetweenNextDetection);
			}
		}
	}


	@Override
	public final List<Message> performSplit(TransientJob transientJob, TransientStage transientStage) throws Exception {
		List<Message> messages = new ArrayList<>();

		int lastDetectionStageIndex = findLastDetectionStageIndex(transientJob.getPipeline());

		hibernateMarkupResultDao.deleteByJobId(transientJob.getId());

		for(int actionIndex = 0; actionIndex < transientStage.getActions().size(); actionIndex++) {
			int mediaIndex = 0;
			TransientAction transientAction = transientStage.getActions().get(actionIndex);
			for (TransientMedia transientMedia : transientJob.getMedia()) {
				if (transientMedia.isFailed()) {
					log.debug("Skipping '{}' - it is in an error state.", transientMedia.getId(), transientMedia.getLocalPath());
					continue;
				} else if(!StringUtils.startsWith(transientMedia.getType(), "image") && !StringUtils.startsWith(transientMedia.getType(), "video")) {
					log.debug("Skipping Media {} - only image and video files are eligible for markup.", transientMedia.getId());
				} else {
					List<Markup.BoundingBoxMapEntry> boundingBoxMapEntryList = createMap(transientJob, transientMedia, lastDetectionStageIndex, transientJob.getPipeline().getStages().get(lastDetectionStageIndex)).toBoundingBoxMapEntryList();
					Markup.MarkupRequest markupRequest = Markup.MarkupRequest.newBuilder()
							.setMediaIndex(mediaIndex)
							.setTaskIndex(transientJob.getCurrentStage())
							.setActionIndex(actionIndex)
							.setMediaId(transientMedia.getId())
							.setMediaType(Markup.MediaType.valueOf(transientMedia.getMediaType().toString().toUpperCase()))
							.setRequestId(redis.getNextSequenceValue())
							.setSourceUri(new File(transientMedia.getLocalPath()).getAbsoluteFile().toURI().toString())
							.setDestinationUri(boundingBoxMapEntryList.size() > 0 ?
									propertiesUtil.createMarkupPath(transientJob.getId(), transientMedia.getId(), getMarkedUpMediaExtensionForMediaType(transientMedia.getMediaType())).toUri().toString() :
									propertiesUtil.createMarkupPath(transientJob.getId(), transientMedia.getId(), getFileExtension(transientMedia.getType())).toUri().toString())
							.addAllMapEntries(boundingBoxMapEntryList)
							.build();

					DefaultMessage message = new DefaultMessage(); // We will sort out the headers later.
					message.setHeader(MpfHeaders.RECIPIENT_QUEUE, String.format("jms:MPF.%s_%s_REQUEST", transientStage.getActionType(), transientAction.getAlgorithm()));
					message.setHeader(MpfHeaders.JMS_REPLY_TO, StringUtils.replace(MpfEndpoints.COMPLETED_MARKUP, "jms:", ""));
					message.setBody(markupRequest);
					messages.add(message);
				}
				mediaIndex++;
			}
		}

		return messages;
	}

	/** Returns the appropriate markup extension for a given {@link org.mitre.mpf.wfm.enums.MediaType}. */
	private String getMarkedUpMediaExtensionForMediaType(MediaType mediaType) {
		switch(mediaType) {
			case IMAGE:
				return ".png";
			case VIDEO:
				return ".avi";

			case AUDIO: // Falls through
			case UNSUPPORTED: // Falls through
			default:
				return ".bin";
		}
	}

	private String getFileExtension(String mimeType) {
		try {
			return MimeTypes.getDefaultMimeTypes().forName(mimeType).getExtension();
		} catch (Exception exception) {
			log.warn("Failed to map the MIME type '{}' to an extension. Defaulting to .bin.", mimeType);
			return ".bin";
		}
	}
}
