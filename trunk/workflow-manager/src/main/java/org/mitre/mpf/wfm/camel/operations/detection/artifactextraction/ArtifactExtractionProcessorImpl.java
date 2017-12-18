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

package org.mitre.mpf.wfm.camel.operations.detection.artifactextraction;

import org.apache.camel.Exchange;
import org.mitre.mpf.frameextractor.FrameExtractor;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.camel.WfmProcessor;
import org.mitre.mpf.wfm.data.RedisImpl;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.entities.transients.TransientMedia;
import org.mitre.mpf.wfm.enums.ArtifactExtractionStatus;
import org.mitre.mpf.wfm.enums.JobStatus;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Extracts artifacts from a media file based on the contents of the {@link org.mitre.mpf.wfm.camel.operations.detection.artifactextraction.ArtifactExtractionRequest}
 * contained in the incoming message body.
 */
@Component(ArtifactExtractionProcessorImpl.REF)
public class ArtifactExtractionProcessorImpl extends WfmProcessor implements ArtifactExtractionProcessorInterface {

	public static final String REF = "trackDetectionExtractionProcessor";
	public static final String ERROR_PATH = "#ERROR_PATH#";
	public static final String UNSUPPORTED_PATH = "#UNSUPPORTED_PATH";

	private static final Logger log = LoggerFactory.getLogger(ArtifactExtractionProcessorImpl.class);

	@Autowired
	@Qualifier(RedisImpl.REF)
	private Redis redis;

	@Autowired
	private PropertiesUtil propertiesUtil;

	@Override
	public void wfmProcess(Exchange exchange) throws WfmProcessingException {
		assert exchange.getIn().getBody() != null : "The body must not be null.";
		assert exchange.getIn().getBody(byte[].class) != null : "The body must be convertible to a String.";

		// Deserialize the contents of the message body.
		ArtifactExtractionRequest request = jsonUtils.deserialize(exchange.getIn().getBody(byte[].class), ArtifactExtractionRequest.class);
		Map<Integer, String> results = new HashMap<>();
		switch(request.getMediaType()) {
			case IMAGE:
				results.put(0, processImageRequest(request));
				break;
			case VIDEO:
				results.putAll(processVideoRequest(request, null));
				break;
			case AUDIO:
			default:
				results.putAll(processUnsupportedMediaType(request));
				break;
		}

		for(int actionIndex : request.getActionIndexToMediaIndexes().keySet()) {
			// The results map now has a mapping of media offsets to artifacts. In simpler terms for images and
			// videos, the map contains a key which is the frame number and a value which is a path to the extracted
			// frame. We now need to iterate through all of the detections in all of the tracks from this stage
			// and action combination and associate the artifact path with a detection if the detection offset
			// matches a key in the map. That is, if there was a detection at frame 15 and there is a 15 key
			// in the results map, the detection's artifact path should be set to 15's extracted frame.
			SortedSet<Track> tracks = redis.getTracks(request.getJobId(), request.getMediaId(), request.getStageIndex(), actionIndex);

			boolean error = false;
			for(Track track : tracks) {

				// An exemplar is a specific detection, so it must also be checked.
                if(results.containsKey(track.getExemplar().getMediaOffsetFrame())) {
                    String exemplarMediaOffsetResult = results.get(track.getExemplar().getMediaOffsetFrame());
                    if(Objects.equals(exemplarMediaOffsetResult,ERROR_PATH)) {
						track.getExemplar().setArtifactExtractionStatus(ArtifactExtractionStatus.FAILED);
						track.getExemplar().setArtifactPath(null);
						error = true;
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
							error = true;
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

			if (error) {
				redis.setJobStatus(request.getJobId(), JobStatus.IN_PROGRESS_ERRORS);

				TransientMedia transientMedia = redis.getJob(request.getJobId()).getMedia().stream()
						.filter(m -> m.getId() == request.getMediaId()).findAny().get();

				transientMedia.setMessage("Error extracting frames. Check the Workflow Manager log for details.");
				redis.persistMedia(request.getJobId(), transientMedia);
			}
		}

		exchange.getOut().getHeaders().put(MpfHeaders.CORRELATION_ID, exchange.getIn().getHeader(MpfHeaders.CORRELATION_ID));
		exchange.getOut().getHeaders().put(MpfHeaders.SPLIT_SIZE, exchange.getIn().getHeader(MpfHeaders.SPLIT_SIZE));
	}

	public Map<Integer, String> processUnsupportedMediaType(ArtifactExtractionRequest request) throws WfmProcessingException {
		log.warn("[Job {}:{}:ARTIFACT_EXTRACTION] Media #{} reports media type {} which is not supported by artifact extraction.",
				request.getJobId(), request.getStageIndex(), request.getMediaId(), request.getMediaType());
		Map<Integer, String> results = new HashMap<>();
		for(Collection<Integer> values : request.getActionIndexToMediaIndexes().values()) {
			for(Integer value : values) {
				results.put(value, UNSUPPORTED_PATH);
			}
		}
		return results;
	}

	public String processImageRequest(ArtifactExtractionRequest request) {
		try {
			File file = propertiesUtil.createArtifactFile(request.getJobId(), request.getMediaId(), request.getStageIndex(), new File(request.getPath()).getName());
			return Files.copy(Paths.get(request.getPath()), Paths.get(file.getAbsoluteFile().toURI()), StandardCopyOption.REPLACE_EXISTING).toFile().getAbsolutePath();
		} catch(IOException | RuntimeException exception) {
			log.warn("[{}|{}|ARTIFACT_EXTRACTION] Failed to copy the image Media #{} to the artifacts directory due to an exception." +
					" All detections (including exemplars) produced in this stage for this medium will NOT have an associated artifact.",
					request.getJobId(), request.getStageIndex(), request.getMediaId(), exception);
			return ERROR_PATH;
		}
	}

	public Map<Integer, String> processVideoRequest(ArtifactExtractionRequest request, FrameExtractor extractor) throws WfmProcessingException {
		// In the context of videos, artifacts are equivalent to frames. We previously built a mapping of
		// action indexes (within a single stage) to a set of frames to extract which are somehow associated
		// with tracks produced in that action. In reality, it doesn't matter which action index a frame is
		// associated with, so we simply create one set containing the union of all frames in the map.

		Map<Integer, String> results = new HashMap<>();

		Set<Integer> unionSet = new HashSet<Integer>();

		for (Collection<Integer> integerCollection : request.getActionIndexToMediaIndexes().values()) {
			unionSet.addAll(integerCollection);
		}

		try {
			FrameExtractor frameExtractor = extractor;
			if (frameExtractor == null) {
				frameExtractor = new FrameExtractor(
						new File(request.getPath()).toURI(),
						propertiesUtil.createArtifactDirectory(request.getJobId(), request.getMediaId(), request.getStageIndex()).toURI());
			}

			frameExtractor.getFrames().addAll(unionSet);
			log.info("[{}|{}|ARTIFACT_EXTRACTION] Need to extract {} artifacts for Media #{}.",
					request.getJobId(), request.getStageIndex(), unionSet.size(), request.getMediaId());
			results.putAll(frameExtractor.execute());

			for (Integer offset : unionSet) {
				if (!results.containsKey(offset)) {
					log.warn("[Job {}|{}|ARTIFACT_EXTRACTION] Failed to extract artifact from Media #{} at frame {}.",
							request.getJobId(), request.getStageIndex(), request.getMediaId(), offset);
					results.put(offset, ERROR_PATH); // mark individual extractions as failed
				}
			}
		} catch (IOException | RuntimeException exception) {
			// In the event of an exception, all of the tracks (and associated detections) created for this medium in this
			// stage (regardless of the action which produced them) will not have artifacts associated with them.
			log.warn("[Job {}|{}|ARTIFACT_EXTRACTION] Failed to extract the artifacts from Media #{} due to an exception." +
							" All detections (including exemplars) produced in this stage for this medium will NOT have an associated artifact.",
					request.getJobId(), request.getStageIndex(), request.getMediaId(), exception);

			results.clear();
			for (Integer failedOffset : unionSet) {
				results.put(failedOffset, ERROR_PATH);
			}
		} finally {
			return results;
		}
	}
}
