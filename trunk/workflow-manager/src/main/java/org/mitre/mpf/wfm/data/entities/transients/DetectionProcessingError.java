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

package org.mitre.mpf.wfm.data.entities.transients;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.mitre.mpf.wfm.util.TextUtils;

import java.util.Comparator;
import java.util.Objects;

public class DetectionProcessingError implements Comparable<DetectionProcessingError> {
	private long jobId;
	public long getJobId() { return jobId; }

	private long mediaId;
	public long getMediaId() { return mediaId; }

	private int stageIndex;
	public int getStageIndex() { return stageIndex; }

	private int actionIndex;
	public int getActionIndex() { return actionIndex; }

	private int startFrame;
	public int getStartFrame() { return startFrame; }

	private int stopFrame;
	public int getStopFrame() { return stopFrame; }

	private int startTime;
	public int getStartTime() { return startTime; }

	private int stopTime;
	public int getStopTime() { return stopTime; }

	private String error;
	public String getError() { return error; }

	@JsonCreator
	public DetectionProcessingError(@JsonProperty("jobId") long jobId,
									@JsonProperty("mediaId") long mediaId,
									@JsonProperty("stageIndex") int stageIndex,
									@JsonProperty("actionIndex") int actionIndex,
									@JsonProperty("startFrame") int startFrame,
									@JsonProperty("stopFrame") int stopFrame,
									@JsonProperty("startTime") int startTime,
									@JsonProperty("stopTime") int stopTime,
									@JsonProperty("error") String error) {
		this.jobId = jobId;
		this.mediaId = mediaId;
		this.stageIndex = stageIndex;
		this.actionIndex = actionIndex;
		this.startFrame = startFrame;
		this.stopFrame = stopFrame;
		this.startTime = startTime;
		this.stopTime = stopTime;
		this.error = error;
	}

	@Override
	public int hashCode() {
		return Objects.hash(jobId, mediaId, stageIndex, actionIndex, startFrame, stopFrame, startTime, stopTime,
				TextUtils.nullSafeHashCode(error));
	}

	@Override
	public boolean equals(Object obj) {
		return this == obj
				|| (obj instanceof DetectionProcessingError
				&& compareTo((DetectionProcessingError) obj) == 0);
	}

	private static final Comparator<DetectionProcessingError> DEFAULT_COMPARATOR = Comparator
			.nullsFirst(Comparator
					.comparingLong(DetectionProcessingError::getJobId)
					.thenComparingLong(DetectionProcessingError::getMediaId)
					.thenComparingInt(DetectionProcessingError::getStageIndex)
					.thenComparingInt(DetectionProcessingError::getActionIndex)
					.thenComparingInt(DetectionProcessingError::getStartFrame)
					.thenComparingInt(DetectionProcessingError::getStopFrame)
					.thenComparingInt(DetectionProcessingError::getStartTime)
					.thenComparingInt(DetectionProcessingError::getStopTime)
					.thenComparing(DetectionProcessingError::getError, TextUtils::nullSafeCompare));

	@Override
	public int compareTo(DetectionProcessingError other) {
		return DEFAULT_COMPARATOR.compare(this, other);
	}

	@Override
	public String toString() {
		return String.format("%s#<jobId=%d, mediaId=%d, stageIndex=%d, actionIndex=%d, startFrame=%d, stopFrame=%d, startTime=%d, stopTime=%d, error='%s'>",
				this.getClass().getSimpleName(), jobId, mediaId, stageIndex, actionIndex, startFrame, stopFrame, startTime, stopTime, error);
	}
}
