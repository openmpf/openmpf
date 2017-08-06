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

package org.mitre.mpf.wfm.data.entities.transients;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.mitre.mpf.wfm.util.TextUtils;

public class DetectionProcessingError implements Comparable<DetectionProcessingError> {
	private long jobId;
	public long getJobId() { return jobId; }

	private long mediaId;
	public long getMediaId() { return mediaId; }

	private int stageIndex;
	public int getStageIndex() { return stageIndex; }

	private int actionIndex;
	public int getActionIndex() { return actionIndex; }

	private int startOffset;
	public int getStartOffset() { return startOffset; }

	private int endOffset;
	public int getEndOffset() { return endOffset; }

	private String error;
	public String getError() { return error; }

	@JsonCreator
	public DetectionProcessingError(@JsonProperty("jobId") long jobId, @JsonProperty("mediaId") long mediaId, @JsonProperty("stageIndex") int stageIndex, @JsonProperty("actionIndex") int actionIndex, @JsonProperty("startOffset") int startOffset, @JsonProperty("endOffset") int endOffset, @JsonProperty("error") String error) {
		this.jobId = jobId;
		this.mediaId = mediaId;
		this.stageIndex = stageIndex;
		this.actionIndex = actionIndex;
		this.startOffset = startOffset;
		this.endOffset = endOffset;
		this.error = error;
	}

	@Override
	public int hashCode() {
		int hash = 37;
		hash = 13 * hash + (int)(jobId ^ (jobId >>> 32));
		hash = 13 * hash + (int)(mediaId ^ (mediaId >>> 32));
		hash = 13 * hash + stageIndex;
		hash = 13 * hash + actionIndex;
		hash = 13 * hash + startOffset;
		hash = 13 * hash + endOffset;
		hash = 13 * hash + TextUtils.nullSafeHashCode(error);
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if(obj == null || !(obj instanceof DetectionProcessingError)) {
			return false;
		} else {
			DetectionProcessingError casted = (DetectionProcessingError)obj;
			return jobId == casted.jobId &&
				mediaId == casted.mediaId &&
				stageIndex == casted.stageIndex &&
				actionIndex == casted.actionIndex &&
				startOffset == casted.startOffset &&
				endOffset == casted.endOffset &&
				TextUtils.nullSafeEquals(error, casted.error);
		}
	}

	@Override
	public String toString() {
		return String.format("%s#<jobId=%d, mediaId=%d, stageIndex=%d, actionIndex=%d, startOffset=%d, endOffset=%d, error='%s'>",
				this.getClass().getSimpleName(), jobId, mediaId, stageIndex, actionIndex, startOffset, endOffset, error);
	}

	@Override
	public int compareTo(DetectionProcessingError other) {
		if(other == null) {
			return 1;
		} else {
			int comparisonResult;
			if((comparisonResult = Long.compare(jobId, other.jobId)) == 0 &&
				(comparisonResult = Long.compare(mediaId, other.mediaId)) == 0 &&
				(comparisonResult = Integer.compare(stageIndex, other.stageIndex)) == 0 &&
				(comparisonResult = Integer.compare(actionIndex, other.actionIndex)) == 0 &&
				(comparisonResult = Integer.compare(startOffset, other.startOffset)) == 0 &&
				(comparisonResult = Integer.compare(endOffset, other.endOffset)) == 0 &&
				(comparisonResult = TextUtils.nullSafeCompare(error, other.error)) == 0) {
				return 0;
			} else {
				return comparisonResult;
			}
		}
	}
}
