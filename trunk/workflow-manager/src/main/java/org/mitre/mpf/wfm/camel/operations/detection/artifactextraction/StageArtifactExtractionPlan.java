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

import java.util.HashMap;
import java.util.Map;

public class StageArtifactExtractionPlan {
	/** The job id associated with this plan. */
	private long jobId;
	public long getJobId() { return jobId; }

	/** The stage index in the pipeline associated with this plan. */
	private int stageIndex;
	public int getStageIndex() { return stageIndex; }

	/** A mapping of media ids to the media-specific extraction plan for that media. */
	private Map<Long, ArtifactExtractionPlan> mediaIdToArtifactExtractionPlanMap;
	public Map<Long, ArtifactExtractionPlan> getMediaIdToArtifactExtractionPlanMap() { return mediaIdToArtifactExtractionPlanMap; }
	public void setMediaIdToArtifactExtractionPlanMap(Map<Long, ArtifactExtractionPlan> mediaIdToArtifactExtractionPlanMap) { this.mediaIdToArtifactExtractionPlanMap = mediaIdToArtifactExtractionPlanMap; }

	@JsonCreator
	public StageArtifactExtractionPlan(@JsonProperty("jobId") long jobId, @JsonProperty("stageIndex") int stageIndex) {
		this.jobId = jobId;
		this.stageIndex = stageIndex;
		this.mediaIdToArtifactExtractionPlanMap = new HashMap<Long, ArtifactExtractionPlan>();
	}

	/**
	 * Adds a new offset for extraction to this instance.
	 *
	 * @param mediaId The id of the media containing the mediaOffset to extract.
	 * @param mediaPath The local path to the media.
	 * @param mediaType The type associated with this media.
	 * @param actionIndex The action index (in this stage) which contains a track that requires this offset's extraction.
	 * @param mediaOffset The offset in the media to extract.
	 */
	public void addIndexToMediaExtractionPlan(long mediaId, String mediaPath, MediaType mediaType, int actionIndex, int mediaOffset) {
		if(!mediaIdToArtifactExtractionPlanMap.containsKey(mediaId)) {
			mediaIdToArtifactExtractionPlanMap.put(mediaId, new ArtifactExtractionPlan(mediaPath, mediaType));
		}
		mediaIdToArtifactExtractionPlanMap.get(mediaId).add(actionIndex, mediaOffset);
	}
}
