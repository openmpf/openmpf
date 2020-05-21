/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

package org.mitre.mpf.interop;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.Objects;


@JsonTypeName("DetectionProcessingError")
public class JsonDetectionProcessingError implements Comparable<JsonDetectionProcessingError> {

	@JsonProperty("startOffsetFrame")
	@JsonPropertyDescription("The earliest frame in the medium which may be affected by this error.")
	private int startOffsetFrame;
	public int getStartOffsetFrame() { return startOffsetFrame; }

	@JsonProperty("stopOffsetFrame")
	@JsonPropertyDescription("The latest frame in the medium which may be affected by this error.")
	private int stopOffsetFrame;
	public int getStopOffsetFrame() { return stopOffsetFrame; }

	@JsonProperty("startOffsetTime")
	@JsonPropertyDescription("The earliest time in the medium which may be affected by this error, in milliseconds.")
	private int startOffsetTime;
	public int getStartOffsetTime() { return startOffsetTime; }

	@JsonProperty("stopOffsetTime")
	@JsonPropertyDescription("The latest time in the medium which may be affected by this error, in milliseconds.")
	private int stopOffsetTime;
	public int getStopOffsetTime() { return stopOffsetTime; }

	@JsonProperty("message")
	@JsonPropertyDescription("The messages associated with the detection error.")
	private String message;
	public String getMessage() { return message; }


	@JsonCreator
	public JsonDetectionProcessingError(@JsonProperty("startOffsetFrame") int startOffsetFrame,
										@JsonProperty("stopOffsetFrame") int stopOffsetFrame,
										@JsonProperty("startOffsetTime") int startOffsetTime,
										@JsonProperty("stopOffsetTime") int stopOffsetTime,
										@JsonProperty("message") String message) {
		this.startOffsetFrame = startOffsetFrame;
		this.stopOffsetFrame = stopOffsetFrame;
		this.startOffsetTime = startOffsetTime;
		this.stopOffsetTime = stopOffsetTime;
		this.message = message;
	}

	@Override
	public int hashCode() {
		return Objects.hash(startOffsetFrame, stopOffsetFrame, startOffsetTime, stopOffsetTime, message);
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof JsonDetectionProcessingError && compareTo((JsonDetectionProcessingError) obj) == 0;
	}

	private static final Comparator<JsonDetectionProcessingError> COMPARATOR = Comparator.nullsFirst(
			Comparator.comparingInt(JsonDetectionProcessingError::getStartOffsetFrame)
					.thenComparingInt(JsonDetectionProcessingError::getStopOffsetFrame)
					.thenComparingInt(JsonDetectionProcessingError::getStartOffsetTime)
					.thenComparingInt(JsonDetectionProcessingError::getStopOffsetTime)
					.thenComparing(JsonDetectionProcessingError::getMessage, StringUtils::compare));

	@Override
	public int compareTo(JsonDetectionProcessingError other) {
		if (this == other) {
			return 0;
		}
		return COMPARATOR.compare(this, other);
	}
}
