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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.mitre.mpf.wfm.enums.MediaType;

import java.util.*;

public class ArtifactExtractionRequest {
	/** The identifier of the medium associated with this request. */
	private long mediaId;
	public long getMediaId() { return mediaId; }

	/** The local path of the medium. */
	private String path;
	public String getPath() { return path; }

	/** The type of media associated with this request. */
	private MediaType mediaType;
	public MediaType getMediaType() { return mediaType; }

	/** The job id associated with this request. */
	private long jobId;
	public long getJobId() { return jobId; }

	/** The stage index in the pipeline. This information is necessary to map an artifact back to a track. */
	private int stageIndex;
	public int getStageIndex() { return stageIndex; }

	/** A mapping of actionIndexes to media indexes which should be extracted for that action. */
	private Map<Integer, Set<Integer>> actionIndexToMediaIndexes;
	public Map<Integer, Set<Integer>> getActionIndexToMediaIndexes() { return actionIndexToMediaIndexes; }
	public void setActionIndexToMediaIndexes(Map<Integer, Set<Integer>> actionIndexToMediaIndexes) { this.actionIndexToMediaIndexes = actionIndexToMediaIndexes; }

	@JsonCreator
	public ArtifactExtractionRequest(@JsonProperty("jobId") long jobId, @JsonProperty("mediaId") long mediaId, @JsonProperty("path") String path, @JsonProperty("mediaType") MediaType mediaType, @JsonProperty("stageIndex") int stageIndex) {
		this.jobId = jobId;
		this.mediaId = mediaId;
		this.path = path;
		this.mediaType = mediaType;
		this.stageIndex = stageIndex;
		this.actionIndexToMediaIndexes = new HashMap<Integer, Set<Integer>>();
	}

	public void add(int actionId, int mediaIndex) {
		if(!actionIndexToMediaIndexes.containsKey(actionId)) {
			actionIndexToMediaIndexes.put(actionId, new HashSet<Integer>());
		}
		actionIndexToMediaIndexes.get(actionId).add(mediaIndex);
	}

	public void add(int actionId, Collection<Integer> mediaIndexes) {
		if(!actionIndexToMediaIndexes.containsKey(actionId)) {
			actionIndexToMediaIndexes.put(actionId, new HashSet<Integer>());
		}
		actionIndexToMediaIndexes.get(actionId).addAll(mediaIndexes);
	}
}
