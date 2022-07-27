/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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

import java.util.*;

import static org.mitre.mpf.interop.util.CompareUtils.nullsFirst;

@JsonTypeName("TrackOutputObject")
@JsonPropertyOrder({ "index", "id", "startOffsetFrame", "stopOffsetFrame", "startOffsetTime", "stopOffsetTime",
		"type", "source", "confidence", "trackProperties", "exemplar", "detections", "startOffset", "stopOffset" })
// Deprecated. Use startOffsetFrame and stopOffsetFrame instead, respectively. Left for backwards compatibility.
@JsonIgnoreProperties({ "startOffset", "stopOffset" })
public class JsonTrackOutputObject implements Comparable<JsonTrackOutputObject> {

	@JsonProperty("index")
	@JsonPropertyDescription("An index for this track relative to the medium and job.")
	private final int index;
	public int getIndex() { return index; }

	@JsonProperty("id")
	@JsonPropertyDescription("The unique identifier for this track.")
	private final String id;
	public String getId() { return id; }

	@JsonProperty("startOffsetFrame")
	@JsonPropertyDescription("The frame in the medium where the track starts.")
	private final int startOffsetFrame;
	public int getStartOffsetFrame() { return startOffsetFrame; }

	@JsonProperty("stopOffsetFrame")
	@JsonPropertyDescription("The frame in the medium where the track ends.")
	private final int stopOffsetFrame;
	public int getStopOffsetFrame() { return stopOffsetFrame; }

	@JsonProperty("startOffsetTime")
	@JsonPropertyDescription("The time in the medium where the track starts, in milliseconds.")
	private final long startOffsetTime;
	public long getStartOffsetTime() { return startOffsetTime; }

	@JsonProperty("stopOffsetTime")
	@JsonPropertyDescription("The time in the medium where the track ends, in milliseconds.")
	private final long stopOffsetTime;
	public long getStopOffsetTime() { return stopOffsetTime; }

	@JsonProperty("type")
	@JsonPropertyDescription("The type of object associated with this track.")
	private final String type;
	public String getType() { return type; }

	@JsonProperty("source")
	@JsonPropertyDescription("The set of pipeline actions which produced this track.")
	private final String source;
	public String getSource() { return source; }

	@JsonProperty("confidence")
	@JsonPropertyDescription("The confidence score associated with this track. " +
							 " Higher scores indicate more confidence in the track.")
	private final float confidence;
	public float getConfidence() { return confidence; }

	@JsonProperty("trackProperties")
	@JsonPropertyDescription("Additional properties set by the track.")
	private final SortedMap<String, String> trackProperties = new TreeMap<>();
	public SortedMap<String, String> getTrackProperties() { return trackProperties; }

	@JsonProperty("exemplar")
	@JsonPropertyDescription("The detection which best represents this track.")
	private final JsonDetectionOutputObject exemplar;
	public JsonDetectionOutputObject getExemplar() { return exemplar; }

	@JsonProperty("detections")
	@JsonPropertyDescription("The collection of detections included in this track.")
	private final SortedSet<JsonDetectionOutputObject> detections = new TreeSet<>();
	public SortedSet<JsonDetectionOutputObject> getDetections() { return detections; }


	@JsonCreator
	public JsonTrackOutputObject(
			@JsonProperty("index") int index,
			@JsonProperty("id") String id,
			@JsonProperty("startOffsetFrame") int startOffsetFrame,
			@JsonProperty("stopOffsetFrame") int stopOffsetFrame,
			@JsonProperty("startOffsetTime") long startOffsetTime,
			@JsonProperty("stopOffsetTime") long stopOffsetTime,
			@JsonProperty("type") String type,
			@JsonProperty("source") String source,
			@JsonProperty("confidence") float confidence,
			@JsonProperty("trackProperties") Map<String, String> trackProperties,
			@JsonProperty("exemplar") JsonDetectionOutputObject exemplar,
			@JsonProperty("detections") Collection<JsonDetectionOutputObject> detections) {
		this.index = index;
		this.id = id;
		this.startOffsetFrame = startOffsetFrame;
		this.stopOffsetFrame = stopOffsetFrame;
		this.startOffsetTime = startOffsetTime;
		this.stopOffsetTime = stopOffsetTime;
		this.type = type;
		this.source = source;
		this.confidence = confidence;
		if (trackProperties != null) {
			this.trackProperties.putAll(trackProperties);
		}
		this.exemplar = exemplar;
		if (detections != null) {
			this.detections.addAll(detections);
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(startOffsetFrame, stopOffsetFrame, startOffsetTime, stopOffsetTime, type, source,
		                    exemplar, id, confidence, trackProperties);
	}

	@Override
	public boolean equals(Object other) {
		return this == other
				|| (other instanceof JsonTrackOutputObject
						&& compareTo((JsonTrackOutputObject) other) == 0);
	}


	private static final Comparator<JsonTrackOutputObject> DEFAULT_COMPARATOR = Comparator
			.nullsFirst(Comparator
                .comparingInt(JsonTrackOutputObject::getStartOffsetFrame)
                .thenComparingInt(JsonTrackOutputObject::getStopOffsetFrame)
                .thenComparingLong(JsonTrackOutputObject::getStartOffsetTime)
                .thenComparingLong(JsonTrackOutputObject::getStopOffsetTime)
                .thenComparing(JsonTrackOutputObject::getType)
                .thenComparing(JsonTrackOutputObject::getSource, nullsFirst())
                .thenComparing(JsonTrackOutputObject::getExemplar)
                .thenComparing(JsonTrackOutputObject::getId, nullsFirst())
                .thenComparingDouble(JsonTrackOutputObject::getConfidence)
                .thenComparing(JsonTrackOutputObject::getTrackProperties, CompareUtils.MAP_COMPARATOR)
			);

	@Override
	public int compareTo(JsonTrackOutputObject other) {
	    return DEFAULT_COMPARATOR.compare(this, other);
	}
}
