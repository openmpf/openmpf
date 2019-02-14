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
import org.mitre.mpf.wfm.camel.WfmProcessor;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.ArtifactExtractionStatus;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.service.StorageService;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;

import static java.util.stream.Collectors.toCollection;

/**
 * Extracts artifacts from a media file based on the contents of the {@link org.mitre.mpf.wfm.camel.operations.detection.artifactextraction.ArtifactExtractionRequest}
 * contained in the incoming message body.
 */
@Component(ArtifactExtractionProcessor.REF)
public class ArtifactExtractionProcessor extends WfmProcessor {

	public static final String REF = "trackDetectionExtractionProcessor";

	private static final Logger LOG = LoggerFactory.getLogger(ArtifactExtractionProcessor.class);

	private final JsonUtils _jsonUtils;

	private final InProgressBatchJobsService _inProgressBatchJobs;

	private final StorageService _storageService;

	@Inject
	ArtifactExtractionProcessor(
			JsonUtils jsonUtils,
			InProgressBatchJobsService inProgressBatchJobs,
			StorageService storageService) {
		_jsonUtils = jsonUtils;
		_inProgressBatchJobs = inProgressBatchJobs;
		_storageService = storageService;
	}


	@Override
	public void wfmProcess(Exchange exchange) {
		ArtifactExtractionRequest request = _jsonUtils.deserialize(exchange.getIn().getBody(byte[].class),
		                                                           ArtifactExtractionRequest.class);
		switch (request.getMediaType()) {
			case IMAGE:
				processImageRequest(request);
				break;
			case VIDEO:
				processVideoRequest(request);
				break;
			default:
				processUnsupportedMediaRequest(request);
		}

		exchange.getOut().setHeader(MpfHeaders.CORRELATION_ID, exchange.getIn().getHeader(MpfHeaders.CORRELATION_ID));
		exchange.getOut().setHeader(MpfHeaders.SPLIT_SIZE, exchange.getIn().getHeader(MpfHeaders.SPLIT_SIZE));
	}


	private void processImageRequest(ArtifactExtractionRequest request) {
	    URI uri;
	    try {
		    uri = _storageService.storeImageArtifact(request);
	    }
	    catch (IOException e) {
	        handleException(request, e);
	        return;
	    }

		processDetections(request, d -> {
			d.setArtifactExtractionStatus(ArtifactExtractionStatus.COMPLETED);
			d.setArtifactPath(uri.toString());
		});
	}


	private void processVideoRequest(ArtifactExtractionRequest request) {
		Map<Integer, URI> frameToUri;
	    try {
	    	frameToUri = _storageService.storeVideoArtifacts(request);
	    }
	    catch (IOException e) {
	    	handleException(request, e);
	    	return;
	    }

		processDetections(request, d -> {
			URI uri = frameToUri.get(d.getMediaOffsetFrame());
			if (uri == null) {
				d.setArtifactExtractionStatus(ArtifactExtractionStatus.FAILED);
			}
			else {
				d.setArtifactPath(uri.toString());
				d.setArtifactExtractionStatus(ArtifactExtractionStatus.COMPLETED);
			}
		});
	}


	private void processUnsupportedMediaRequest(ArtifactExtractionRequest request) {
		processDetections(
				request,
				d -> d.setArtifactExtractionStatus(ArtifactExtractionStatus.UNSUPPORTED_MEDIA_TYPE));
	}


	private void handleException(ArtifactExtractionRequest request, IOException e) {
		LOG.warn("[Job {}|{}|ARTIFACT_EXTRACTION] Failed to extract the artifacts from Media #{} due to an " +
				         "exception. All detections (including exemplars) produced in this stage " +
				         "for this medium will NOT have an associated artifact.",
		         request.getJobId(), request.getStageIndex(), request.getMediaId(), e);
		processDetections(request, d -> d.setArtifactExtractionStatus(ArtifactExtractionStatus.FAILED));
	}


	private void processDetections(ArtifactExtractionRequest request, Consumer<Detection> detectionHandler) {
		for (Map.Entry<Integer, Set<Integer>> entry : request.getActionIndexToMediaIndexes().entrySet()) {
		    int actionIndex = entry.getKey();
			Set<Integer> artifactFrames = entry.getValue();
			Set<Track> tracks = _inProgressBatchJobs.getTracks(request.getJobId(), request.getMediaId(),
			                                                   request.getStageIndex(), actionIndex);

			tracks.stream()
                    .flatMap(t -> t.getDetections().stream())
                    .filter(d -> artifactFrames.contains(d.getMediaOffsetFrame()))
					.forEach(detectionHandler);

			_inProgressBatchJobs.setTracks(request.getJobId(), request.getMediaId(), request.getStageIndex(),
			                              actionIndex, tracks);

			SortedSet<Integer> missingFrames = tracks.stream()
					.flatMap(t -> t.getDetections().stream())
					.filter(d -> d.getArtifactExtractionStatus() == ArtifactExtractionStatus.FAILED)
					.map(Detection::getMediaOffsetFrame)
					.collect(toCollection(TreeSet::new));

			if (!missingFrames.isEmpty()) {
				_inProgressBatchJobs.setJobStatus(request.getJobId(), BatchJobStatusType.IN_PROGRESS_ERRORS);
				_inProgressBatchJobs.addMediaError(request.getJobId(), request.getMediaId(),
				                                  "Error extracting frame(s): " + missingFrames);
			}
		}
	}
}
