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
import org.mitre.mpf.interop.util.CompareUtils;

import java.util.Comparator;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

@JsonTypeName("DetectionOutputObject")
@JsonPropertyOrder({ "offsetFrame", "offsetTime", "x", "y", "width", "height",
        "confidence", "detectionProperties", "artifactExtractionStatus", "artifactPath", "offset" })
@JsonIgnoreProperties("offset")
public class JsonDetectionOutputObject implements Comparable<JsonDetectionOutputObject> {

	@JsonProperty("x")
	@JsonPropertyDescription("The x-coordinate of the top-left corner of the detected object's bounding box in the artifact.")
	private final int x;
	public int getX() { return x; }

	@JsonProperty("y")
	@JsonPropertyDescription("The y-coordinate of the top-left corner of the detected object's bounding box in the artifact.")
	private final int y;
	public int getY() { return y; }

	@JsonProperty("width")
	@JsonPropertyDescription("The width of the detected object's bounding box in the artifact.")
	private final int width;
	public int getWidth() { return width; }

	@JsonProperty("height")
	@JsonPropertyDescription("The height of the detected object's bounding box in the artifact.")
	private final int height;
	public int getHeight() { return height; }

	@JsonProperty("confidence")
	@JsonPropertyDescription("The confidence score associated with this detection. Higher scores indicate more confidence in the detection.")
	private final float confidence;
	public float getConfidence() { return confidence; }

	@JsonProperty("detectionProperties")
	@JsonPropertyDescription("Additional properties set by the detection.")
	private final SortedMap<String,String> detectionProperties = new TreeMap<>();
	public SortedMap<String,String> getDetectionProperties() { return detectionProperties; }

	@JsonProperty("offsetFrame")
	@JsonPropertyDescription("The offset frame of this detection in the parent medium. For images and audio, this value is not meaningful. For videos, this value indicates a zero-based frame index.")
	private final int offsetFrame;
	public int getOffsetFrame() { return offsetFrame; }

	@JsonProperty("offsetTime")
	@JsonPropertyDescription("The offset time of this detection in the parent medium. For images, this value is not meaningful. For audio and video files, this value refers to a temporal index measured in milliseconds.")
	private final long offsetTime;
	public long getOffsetTime() { return offsetTime; }

	@JsonProperty("artifactPath")
	@JsonPropertyDescription("The path to the artifact containing the best representation of this detection.")
	private final String artifactPath;
	public String getArtifactPath() { return artifactPath; }

	@JsonProperty("artifactExtractionStatus")
	@JsonPropertyDescription("A status code indicating if an artifact was created for this detection.")
	private final String artifactExtractionStatus;
	public String getArtifactExtractionStatus() { return artifactExtractionStatus; }


	@JsonCreator
	public JsonDetectionOutputObject(@JsonProperty("x") int x,
	                                 @JsonProperty("y") int y,
	                                 @JsonProperty("width") int width,
	                                 @JsonProperty("height") int height,
	                                 @JsonProperty("confidence") float confidence,
	                                 @JsonProperty("detectionProperties") SortedMap<String, String> detectionProperties,
	                                 @JsonProperty("offsetFrame") int offsetFrame,
									 @JsonProperty("offsetTime") long offsetTime,
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

	@Override
	public int hashCode() {
		return Objects.hash(x, y, width, height, confidence, offsetTime, offsetFrame, artifactPath,
		                    detectionProperties);
	}

	@Override
	public boolean equals(Object other) {
	    return this == other
			    || (other instanceof JsonDetectionOutputObject
	                    && compareTo((JsonDetectionOutputObject) other) == 0);
	}


	private static final Comparator<JsonDetectionOutputObject> DEFAULT_COMPARATOR = Comparator
			.nullsFirst(Comparator
                .comparingInt(JsonDetectionOutputObject::getOffsetFrame)
                .thenComparingLong(JsonDetectionOutputObject::getOffsetTime)
                .thenComparingInt(JsonDetectionOutputObject::getX)
                .thenComparingInt(JsonDetectionOutputObject::getY)
                .thenComparingInt(JsonDetectionOutputObject::getWidth)
                .thenComparingInt(JsonDetectionOutputObject::getHeight)
                .thenComparing(JsonDetectionOutputObject::getDetectionProperties, CompareUtils.MAP_COMPARATOR)
			);

	@Override
	public int compareTo(JsonDetectionOutputObject other) {
	    return DEFAULT_COMPARATOR.compare(this, other);
	}
}
