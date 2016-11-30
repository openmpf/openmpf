/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A container of metadata which describes which artifacts in a medium should be extracted.
 *
 * This is effectively a Map where the key is an action index and the values are the artifacts (generally interpreted as
 * frames) which are to be extracted so that they may be associated with detections produced by the corresponding action.
 */
public class ArtifactExtractionPlan {
	/** The local path to the medium from which samples should be extracted. */
	private String path;
	public String getPath() { return path; }

	/** The type of file to process. */
	private MediaType mediaType;
	public MediaType getMediaType() { return mediaType; }

	/** A mapping of action indexes to the offsets to extract for this medium. */
	private Map<Integer, Set<Integer>> actionIndexToExtractionOffsetsMap;
	public Map<Integer, Set<Integer>> getActionIndexToExtractionOffsetsMap() { return actionIndexToExtractionOffsetsMap; }
	public void setActionIndexToExtractionOffsetsMap(Map<Integer, Set<Integer>> actionIndexToExtractionOffsetsMap) { this.actionIndexToExtractionOffsetsMap = actionIndexToExtractionOffsetsMap; }

	@JsonCreator
	public ArtifactExtractionPlan(@JsonProperty("path") String path, @JsonProperty("mediaType") MediaType mediaType) {
		this.path = path;
		this.mediaType = mediaType;
		this.actionIndexToExtractionOffsetsMap = new HashMap<Integer, Set<Integer>>();
	}

	/** Convenience method. */
	public void add(int actionIndex, int mediaIndex) {
		if(!actionIndexToExtractionOffsetsMap.containsKey(actionIndex)) {
			actionIndexToExtractionOffsetsMap.put(actionIndex, new HashSet<Integer>());
		}
		actionIndexToExtractionOffsetsMap.get(actionIndex).add(mediaIndex);
	}
}
