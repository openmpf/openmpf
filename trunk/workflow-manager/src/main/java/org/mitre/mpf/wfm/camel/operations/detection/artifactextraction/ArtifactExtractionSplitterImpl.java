/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.camel.operations.detection.artifactextraction;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultMessage;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.camel.WfmSplitter;
import org.mitre.mpf.wfm.camel.operations.detection.trackmerging.TrackMergingContext;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.RedisImpl;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.ArtifactExtractionPolicy;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;

@Component(ArtifactExtractionSplitterImpl.REF)
public class ArtifactExtractionSplitterImpl extends WfmSplitter {
	private static final Logger log = LoggerFactory.getLogger(ArtifactExtractionSplitterImpl.class);
	public static final String REF = "detectionExtractionSplitter";

	@Autowired
	private JsonUtils jsonUtils;

	@Override
	public String getSplitterName() { return REF; }

	@Autowired
	@Qualifier(RedisImpl.REF)
	private Redis redis;

	@Autowired
	private PropertiesUtil propertiesUtil;

	/**
	 * Uses a String value to look up the appropriate {@link org.mitre.mpf.wfm.enums.MpfConstants#ARTIFACT_EXTRACTION_POLICY_PROPERTY};
	 * if the property is not found, or if the provided property value doesn't make sense, the {@link org.mitre.mpf.wfm.enums.ArtifactExtractionPolicy#DEFAULT}
	 * value is used.
	 */
	private ArtifactExtractionPolicy getDetectionExtractionPolicy(String extractionPolicyProperty) {
		ArtifactExtractionPolicy artifactExtractionPolicy = propertiesUtil.getArtifactExtractionPolicy();
		if (extractionPolicyProperty != null) {
			artifactExtractionPolicy = ArtifactExtractionPolicy.parse(extractionPolicyProperty, artifactExtractionPolicy);
		}
		return artifactExtractionPolicy;
	}

	private boolean isNonVisualObjectType(String type) {
		return StringUtils.equalsIgnoreCase(type, "MOTION") || StringUtils.equalsIgnoreCase(type, "SPEECH");
	}

	public List<Message> wfmSplit(Exchange exchange) throws Exception {
		assert exchange.getIn().getBody() != null : "The body must not be null.";
		assert exchange.getIn().getBody(byte[].class) != null : "The body must be convertible to a String.";

		List<Message> messages = new ArrayList<>();
		TrackMergingContext trackMergingContext = jsonUtils.deserialize(exchange.getIn().getBody(byte[].class), TrackMergingContext.class);
		TransientJob transientJob = redis.getJob(trackMergingContext.getJobId());

		if(!transientJob.isCancelled()) {
			// Allocate at least enough room for each of the media in the job so we don't have to resize the list.
			((ArrayList)messages).ensureCapacity(transientJob.getMedia().size());

			TransientStage transientStage = transientJob.getPipeline().getStages().get(trackMergingContext.getStageIndex());
			StageArtifactExtractionPlan plan = new StageArtifactExtractionPlan(transientJob.getId(), trackMergingContext.getStageIndex());

			for (int actionIndex = 0; actionIndex < transientStage.getActions().size(); actionIndex++) {
				TransientAction transientAction = transientStage.getActions().get(actionIndex);

				// Iterate through the media and process as indicated.
				for (TransientMedia transientMedia : transientJob.getMedia()) {

					String extractionPolicyProperty = AggregateJobPropertiesUtil.calculateValue(
							MpfConstants.ARTIFACT_EXTRACTION_POLICY_PROPERTY, transientAction.getProperties(),
							transientJob.getOverriddenJobProperties(), transientAction, transientJob.getOverriddenAlgorithmProperties(),
							transientMedia.getMediaSpecificProperties());
					ArtifactExtractionPolicy artifactExtractionPolicy = getDetectionExtractionPolicy(extractionPolicyProperty);

					if (artifactExtractionPolicy != ArtifactExtractionPolicy.NONE) {
						// The user either wants to extract all detections or the user wants to detect only the exemplar
						// detections for a given track. Therefore, build a collection of detection extraction requests.

						// If the Media is in an error condition, it can be safely skipped.
						if (transientMedia.isFailed()) {
							continue;
						}

						// Explicitly check that the medium is not an audio file. If it is, then we can continue
						// to the next medium without retrieving the tracks collection.
						if (transientMedia.getMediaType() == MediaType.AUDIO) {
							log.debug("Extracting detections from an audio file is not supported at this time.");
							continue;
						}

						SortedSet<Track> tracks = redis.getTracks(trackMergingContext.getJobId(), transientMedia.getId(), trackMergingContext.getStageIndex(), actionIndex);

						for (Track track : tracks) {

							// Determine if the track is a non-visual object type (e.g., SPEECH).
							boolean isNonVisualObjectType = isNonVisualObjectType(track.getType());

							if (isNonVisualObjectType && (artifactExtractionPolicy == ArtifactExtractionPolicy.ALL_VISUAL_DETECTIONS || artifactExtractionPolicy == ArtifactExtractionPolicy.VISUAL_EXEMPLARS_ONLY)) {
								// We are not interested in non-visual object types. This track can be ignored.
								continue;
							}

							if (transientMedia.getMediaType() == MediaType.IMAGE) {
								plan.addIndexToMediaExtractionPlan(transientMedia.getId(), transientMedia.getLocalPath(), transientMedia.getMediaType(), actionIndex, 0);
							} else if (transientMedia.getMediaType() == MediaType.VIDEO) {
								if (artifactExtractionPolicy == ArtifactExtractionPolicy.ALL_VISUAL_DETECTIONS || artifactExtractionPolicy == ArtifactExtractionPolicy.ALL_DETECTIONS) {
									for (Detection detection : track.getDetections()) {
										plan.addIndexToMediaExtractionPlan(transientMedia.getId(), transientMedia.getLocalPath(), transientMedia.getMediaType(), actionIndex, detection.getMediaOffsetFrame());
									}
								} else {
									plan.addIndexToMediaExtractionPlan(transientMedia.getId(), transientMedia.getLocalPath(), transientMedia.getMediaType(), actionIndex, track.getExemplar().getMediaOffsetFrame());
								}
							} else {
								log.warn("Media {} with type {} was not expected at this time. None of the detections in this track will be extracted.", transientMedia.getId(), transientMedia.getType());
							}
						}
					}
				}
			}

			messages.addAll(createMessages(plan));
		} else {
			log.warn("[Job {}|*|*] Artifact extraction will not be performed because this job has been cancelled.", transientJob.getId());
		}

		return messages;
	}

	private List<Message> createMessages(StageArtifactExtractionPlan plan) throws WfmProcessingException {
		List<Message> messages = new ArrayList<Message>();

		for(long mediaId : plan.getMediaIdToArtifactExtractionPlanMap().keySet()) {
			ArtifactExtractionPlan mediaPlan = plan.getMediaIdToArtifactExtractionPlanMap().get(mediaId);
			ArtifactExtractionRequest request = new ArtifactExtractionRequest(plan.getJobId(), mediaId, mediaPlan.getPath(), mediaPlan.getMediaType(), plan.getStageIndex());
			for(int actionIndex : mediaPlan.getActionIndexToExtractionOffsetsMap().keySet()) {
				request.add(actionIndex, mediaPlan.getActionIndexToExtractionOffsetsMap().get(actionIndex));
			}

			try {
				Message message = new DefaultMessage();
				message.setBody(jsonUtils.serialize(request));
				messages.add(message);
			} catch (WfmProcessingException wpe) {
				log.error("Failed to create a message for '{}'. Attempting to continue without it...", request);
			}
		}
		return messages;
	}
}