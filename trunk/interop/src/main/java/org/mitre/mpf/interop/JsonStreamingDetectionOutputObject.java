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

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

@JsonTypeName("StreamingDetectionOutputObject")
@JsonPropertyOrder({ "offsetFrame", "timestamp", "x", "y", "width", "height",
        "confidence", "detectionProperties" })
public class JsonStreamingDetectionOutputObject implements Comparable<JsonStreamingDetectionOutputObject> {

	@JsonPropertyDescription("The x-coordinate of the top-left corner of the detected object's bounding box in the artifact.")
	private final int x;
	public int getX() { return x; }

	@JsonPropertyDescription("The y-coordinate of the top-left corner of the detected object's bounding box in the artifact.")
	private final int y;
	public int getY() { return y; }

	@JsonPropertyDescription("The width of the detected object's bounding box in the artifact.")
	private final int width;
	public int getWidth() { return width; }

	@JsonPropertyDescription("The height of the detected object's bounding box in the artifact.")
	private final int height;
	public int getHeight() { return height; }

	@JsonPropertyDescription("The confidence score associated with this detection. Higher scores indicate more confidence in the detection.")
	private final float confidence;
	public float getConfidence() { return confidence; }

	@JsonPropertyDescription("Additional properties set by the detection.")
	private final SortedMap<String, String> detectionProperties = new TreeMap<>();
	public SortedMap<String, String> getDetectionProperties() { return detectionProperties; }

	@JsonPropertyDescription("The offset frame of this detection in the parent medium. For images and audio, this value is not meaningful. For videos, this value indicates a zero-based frame index.")
	private final int offsetFrame;
	public int getOffsetFrame() { return offsetFrame; }

	@JsonPropertyDescription("The date and time that this detection was detected")
	private final Instant timestamp;
	public Instant getTimestamp() { return timestamp; }


	@JsonCreator
	public JsonStreamingDetectionOutputObject(
			@JsonProperty("x") int x,
			@JsonProperty("y") int y,
			@JsonProperty("width") int width,
			@JsonProperty("height") int height,
			@JsonProperty("confidence") float confidence,
			@JsonProperty("detectionProperties") SortedMap<String, String> detectionProperties,
			@JsonProperty("offsetFrame") int offsetFrame,
			@JsonProperty("timestamp") Instant timestamp) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.confidence = confidence;
		this.offsetFrame = offsetFrame;
		this.timestamp = timestamp;
		if (detectionProperties != null) {
			this.detectionProperties.putAll(detectionProperties);
		}
	}


	@Override
	public int hashCode() {
		return Objects.hash(offsetFrame, timestamp, confidence, x, y, width, height, detectionProperties);
	}


	@Override
	public boolean equals(Object other) {
		return this == other
				|| (other instanceof JsonStreamingDetectionOutputObject
						&& compareTo((JsonStreamingDetectionOutputObject) other) == 0);
	}


	private static final Comparator<JsonStreamingDetectionOutputObject> DEFAULT_COMPARATOR = Comparator
			.nullsFirst(Comparator
					.comparingInt(JsonStreamingDetectionOutputObject::getOffsetFrame)
					.thenComparing(JsonStreamingDetectionOutputObject::getTimestamp)
					.thenComparingDouble(JsonStreamingDetectionOutputObject::getConfidence)
					.thenComparingInt(JsonStreamingDetectionOutputObject::getX)
					.thenComparingInt(JsonStreamingDetectionOutputObject::getY)
					.thenComparingInt(JsonStreamingDetectionOutputObject::getWidth)
					.thenComparingInt(JsonStreamingDetectionOutputObject::getHeight)
					.thenComparing(JsonStreamingDetectionOutputObject::getDetectionProperties,
					               CompareUtils.MAP_COMPARATOR));

	@Override
	public int compareTo(JsonStreamingDetectionOutputObject other) {
		return DEFAULT_COMPARATOR.compare(this, other);
	}
}
