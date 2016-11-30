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

package org.mitre.mpf.interop;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

@JsonTypeName("DetectionProcessingError")
public class JsonDetectionProcessingError implements Comparable<JsonDetectionProcessingError> {

	@JsonProperty("startOffset")
	@JsonPropertyDescription("The earliest offset in the medium which may be affected by this error.")
	private int startOffset;
	public int getStartOffset() { return startOffset; }

	@JsonProperty("stopOffset")
	@JsonPropertyDescription("The latest offset in the medium which may be affected by this error.")
	private int stopOffset;
	public int getStopOffset() { return stopOffset; }

	@JsonProperty("message")
	@JsonPropertyDescription("The messages associated with the detection error.")
	private String message;
	public String getMessage() { return message; }

	@JsonCreator
	public JsonDetectionProcessingError(@JsonProperty("startOffset") int startOffset,
	                                    @JsonProperty("stopOffset") int stopOffset,
	                                    @JsonProperty("message") String message) {
		this.startOffset = startOffset;
		this.stopOffset = stopOffset;
		this.message = message;
	}

	public int hashCode() {
		int result = 37;
		result = (37 * result) + startOffset;
		result = (37 * result) + stopOffset;
		result = (37 * result) + (message == null ? 0 : message.hashCode());
		return result;
	}

	public boolean equals(Object other) {
		if(other == null || !(other instanceof JsonDetectionProcessingError)) {
			return false;
		} else {
			JsonDetectionProcessingError casted = (JsonDetectionProcessingError)other;
			return startOffset == casted.startOffset &&
					stopOffset == casted.stopOffset &&
					StringUtils.equals(message, casted.message);
		}
	}

	@Override
	public int compareTo(JsonDetectionProcessingError other) {
		int result = 0;
		if(other == null) {
			return 1;
		} else if((result = Integer.compare(startOffset, other.startOffset)) != 0
			|| (result = Integer.compare(stopOffset, other.stopOffset)) != 0
			|| (result = ObjectUtils.compare(message, other.message)) != 0) {
			return result;
		} else {
			return result;
		}
	}
}
