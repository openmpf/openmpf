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

package org.mitre.mpf.interop;

import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

@JsonTypeName("DetectionOutputObject")
public class JsonDetectionOutputObject implements Comparable<JsonDetectionOutputObject> {

	@JsonProperty("x")
	@JsonPropertyDescription("The x-coordinate of the top-left corner of the detected object's bounding box in the artifact.")
	private int x;
	public int getX() { return x; }

	@JsonProperty("y")
	@JsonPropertyDescription("The y-coordinate of the top-left corner of the detected object's bounding box in the artifact.")
	private int y;
	public int getY() { return y; }

	@JsonProperty("width")
	@JsonPropertyDescription("The width of the detected object's bounding box in the artifact.")
	private int width;
	public int getWidth() { return width; }

	@JsonProperty("height")
	@JsonPropertyDescription("The height of the detected object's bounding box in the artifact.")
	private int height;
	public int getHeight() { return height; }

	@JsonProperty("confidence")
	@JsonPropertyDescription("The confidence score associated with this detection. Higher scores indicate more confidence in the detection.")
	private float confidence;
	public float getConfidence() { return confidence; }

	@JsonProperty("detectionProperties")
	@JsonPropertyDescription("Additional properties set by the detection.")
	private SortedMap<String,String> detectionProperties = new TreeMap<>();
	public SortedMap<String,String> getDetectionProperties() { return detectionProperties; }

	// MPF R0.6.0 backwards compatibility with MPF R0.5.0 (upgrade path)
	@JsonProperty("offset")
	public void setOffset(int offset) { offsetFrame = offset; }
	public int getOffset() { return offsetFrame; }

	@JsonProperty("offsetFrame")
	@JsonPropertyDescription("The offset frame of this detection in the parent medium. For images and audio, this value is not meaningful. For videos, this value indicates a zero-based frame index.")
	private int offsetFrame;
	public int getOffsetFrame() { return offsetFrame; }

	@JsonProperty("offsetTime")
	@JsonPropertyDescription("The offset time of this detection in the parent medium. For images, this value is not meaningful. For audio and video files, this value refers to a temporal index measured in milliseconds.")
	private int offsetTime;
	public int getOffsetTime() { return offsetTime; }

	@JsonProperty("artifactPath")
	@JsonPropertyDescription("The path to the artifact containing the best representation of this detection.")
	private String artifactPath;
	public String getArtifactPath() { return artifactPath; }

	@JsonProperty("artifactExtractionStatus")
	@JsonPropertyDescription("A status code indicating if an artifact was created for this detection.")
	private String artifactExtractionStatus;
	public String getArtifactExtractionStatus() { return artifactExtractionStatus; }

    public JsonDetectionOutputObject(){}

	@JsonCreator
	public JsonDetectionOutputObject(@JsonProperty("x") int x,
	                                 @JsonProperty("y") int y,
	                                 @JsonProperty("width") int width,
	                                 @JsonProperty("height") int height,
	                                 @JsonProperty("confidence") float confidence,
	                                 @JsonProperty("detectionProperties") SortedMap<String, String> detectionProperties,
	                                 @JsonProperty("offsetFrame") int offsetFrame,
									 @JsonProperty("offsetTime") int offsetTime,
	                                 @JsonProperty("artifactExtractionStatus") String artifactExtractionStatus,
	                                 @JsonProperty("artifactPath") String artifactPath) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.confidence = confidence;
		this.offsetFrame = offsetFrame;
		this.offsetTime = offsetTime;
		this.artifactExtractionStatus = artifactExtractionStatus;
		this.artifactPath = artifactPath;

		if (detectionProperties!=null) {
			this.detectionProperties.putAll(detectionProperties);
		}
	}

	public int hashCode() {
		return Objects.hash(x,y,width,height,confidence,offsetTime,offsetFrame,artifactPath,detectionProperties);
	}

	public boolean equals(Object other) {
		if(other == null || !(other instanceof JsonDetectionOutputObject)) {
			return false;
		} else {
			JsonDetectionOutputObject casted = (JsonDetectionOutputObject)other;
			return compareTo(casted) == 0;
		}
	}
	public int compareTo(JsonDetectionOutputObject other) {
		int result = 0;
		if(other == null) {
			return 1;
		} else if((result = Integer.compare(offsetFrame, other.offsetFrame)) != 0
			||( result = Integer.compare(offsetTime, other.offsetTime)) != 0
			|| (result = Integer.compare(x, other.x)) != 0
			|| (result = Integer.compare(y, other.y)) != 0
			|| (result = Integer.compare(width, other.width)) != 0
			|| (result = Integer.compare(height, other.height)) != 0
			|| (result = compareMap(detectionProperties,other.detectionProperties)) != 0) {
			return result;
		} else {
			return 0;
		}
	}

	private int compareMap(SortedMap<String, String> map1, SortedMap<String, String> map2) {
		if (map1 == null && map2 == null) {
			return 0;
		} else if (map1 == null) {
			return -1;
		} else if (map2 == null) {
			return 1;
		} else {
			int result = 0;
			if ((result = Integer.compare(map1.size(),map2.size())) != 0) {
				return result;
			}
			StringBuilder map1Str = new StringBuilder();
			for (String key : map1.keySet()) {
				map1Str.append(key).append(map1.get(key));
			}
			StringBuilder map2Str = new StringBuilder();
			for (String key : map2.keySet()) {
				map2Str.append(key).append(map2.get(key));
			}
			if ((result = ObjectUtils.compare(map1Str.toString(),map2Str.toString())) != 0) {
				return result;
			}
		}
		return 0;
	}
}
