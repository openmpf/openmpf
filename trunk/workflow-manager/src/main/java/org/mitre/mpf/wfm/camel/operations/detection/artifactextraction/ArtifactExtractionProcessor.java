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
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.camel.WfmProcessor;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.ArtifactExtractionStatus;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;

/**
 * Extracts artifacts from a media file based on the contents of the {@link org.mitre.mpf.wfm.camel.operations.detection.artifactextraction.ArtifactExtractionRequest}
 * contained in the incoming message body.
 */
@Component(ArtifactExtractionProcessor.REF)
public class ArtifactExtractionProcessor extends WfmProcessor {

	public static final String REF = "trackDetectionExtractionProcessor";
	public static final String ERROR_PATH = "#ERROR_PATH#";
	public static final String UNSUPPORTED_PATH = "#UNSUPPORTED_PATH";

	private static final Logger log = LoggerFactory.getLogger(ArtifactExtractionProcessor.class);

	@Autowired
	private Redis redis;

	@Autowired
	private StorageService storageService;

	@Override
	public void wfmProcess(Exchange exchange) throws WfmProcessingException {
		assert exchange.getIn().getBody() != null : "The body must not be null.";
		assert exchange.getIn().getBody(byte[].class) != null : "The body must be convertible to a String.";

		// Deserialize the contents of the message body.
		ArtifactExtractionRequest request = jsonUtils.deserialize(exchange.getIn().getBody(byte[].class), ArtifactExtractionRequest.class);
		Map<Integer, String> results = storageService.store(request);

		for(int actionIndex : request.getActionIndexToMediaIndexes().keySet()) {
			// The results map now has a mapping of media offsets to artifacts. In simpler terms for images and
			// videos, the map contains a key which is the frame number and a value which is a path to the extracted
			// frame. We now need to iterate through all of the detections in all of the tracks from this stage
			// and action combination and associate the artifact path with a detection if the detection offset
			// matches a key in the map. That is, if there was a detection at frame 15 and there is a 15 key
			// in the results map, the detection's artifact path should be set to 15's extracted frame.
			SortedSet<Track> tracks = redis.getTracks(request.getJobId(), request.getMediaId(), request.getStageIndex(), actionIndex);

			for(Track track : tracks) {

				// An exemplar is a specific detection, so it must also be checked.
                if(results.containsKey(track.getExemplar().getMediaOffsetFrame())) {
                    String exemplarMediaOffsetResult = results.get(track.getExemplar().getMediaOffsetFrame());
                    if(Objects.equals(exemplarMediaOffsetResult,ERROR_PATH)) {
						track.getExemplar().setArtifactExtractionStatus(ArtifactExtractionStatus.FAILED);
						track.getExemplar().setArtifactPath(null);
					} else if(Objects.equals(exemplarMediaOffsetResult,UNSUPPORTED_PATH)) {
						track.getExemplar().setArtifactExtractionStatus(ArtifactExtractionStatus.UNSUPPORTED_MEDIA_TYPE);
						track.getExemplar().setArtifactPath(null);
					} else {
						track.getExemplar().setArtifactExtractionStatus(ArtifactExtractionStatus.COMPLETED);
						track.getExemplar().setArtifactPath(results.get(track.getExemplar().getMediaOffsetFrame()));
					}
				}

				for(Detection detection : track.getDetections()) {
					if(results.containsKey(detection.getMediaOffsetFrame())) {
						String detectionMediaOffsetResult = results.get(detection.getMediaOffsetFrame());
						if(Objects.equals(detectionMediaOffsetResult,ERROR_PATH)) {
							detection.setArtifactExtractionStatus(ArtifactExtractionStatus.FAILED);
							detection.setArtifactPath(null);
						} else if(Objects.equals(detectionMediaOffsetResult,UNSUPPORTED_PATH)) {
							detection.setArtifactExtractionStatus(ArtifactExtractionStatus.UNSUPPORTED_MEDIA_TYPE);
							detection.setArtifactPath(null);
						} else {
							detection.setArtifactExtractionStatus(ArtifactExtractionStatus.COMPLETED);
							detection.setArtifactPath(results.get(detection.getMediaOffsetFrame()));
						}
					}
				}
			}

			// It's likely we've updated at least one track, so the new values need to be pushed to the transient
			// data store.
			redis.setTracks(request.getJobId(), request.getMediaId(), request.getStageIndex(), actionIndex, tracks);
		}

		exchange.getOut().getHeaders().put(MpfHeaders.CORRELATION_ID, exchange.getIn().getHeader(MpfHeaders.CORRELATION_ID));
		exchange.getOut().getHeaders().put(MpfHeaders.SPLIT_SIZE, exchange.getIn().getHeader(MpfHeaders.SPLIT_SIZE));
	}
}
