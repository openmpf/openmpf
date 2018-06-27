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
import org.mitre.mpf.interop.util.CompareUtils;

import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

@JsonTypeName("TrackOutputObject")
@JsonPropertyOrder({ "id", "startOffsetFrame", "stopOffsetFrame", "startOffsetTime", "stopOffsetTime",
		"type", "source", "trackProperties", "exemplar", "detections", "startOffset", "stopOffset" })
public class JsonTrackOutputObject implements Comparable<JsonTrackOutputObject> {

	@JsonProperty("id")
	@JsonPropertyDescription("The unique identifier for this track.")
	private String id;
	public String getId() { return id; }

	// MPF R0.6.0 backwards compatibility with MPF R0.5.0 (upgrade path)
	@JsonProperty("startOffset")
    @JsonPropertyDescription("Deprecated. Use startOffsetFrame instead. Left for backwards compatibility.")
	public void setStartOffset(int startOffset) { startOffsetFrame = startOffset; }
	public int getStartOffset() { return startOffsetFrame; }

	@JsonProperty("startOffsetFrame")
	@JsonPropertyDescription("The offset in the medium where the track starts.")
	private int startOffsetFrame;
	public int getStartOffsetFrame() { return startOffsetFrame; }

	// MPF R0.6.0 backwards compatibility with MPF R0.5.0 (upgrade path)
	@JsonProperty("stopOffset")
    @JsonPropertyDescription("Deprecated. Use stopOffsetFrame instead. Left for backwards compatibility.")
	public void setStopOffset(int stopOffset) { stopOffsetFrame = stopOffset; }
	public int getStopOffset() { return stopOffsetFrame; }

	@JsonProperty("stopOffsetFrame")
	@JsonPropertyDescription("The offset in the medium where the track ends.")
	private int stopOffsetFrame;
	public int getStopOffsetFrame() { return stopOffsetFrame; }

	@JsonProperty("startOffsetTime")
	@JsonPropertyDescription("The offset in the medium where the track starts, in milliseconds.")
	private long startOffsetTime;
	public long getStartOffsetTime() { return startOffsetTime; }

	@JsonProperty("stopOffsetTime")
	@JsonPropertyDescription("The offset in the medium where the track ends, in milliseconds.")
	private long stopOffsetTime;
	public long getStopOffsetTime() { return stopOffsetTime; }

	@JsonProperty("type")
	@JsonPropertyDescription("The type of object associated with this track.")
	private String type;
	public String getType() { return type; }

	@JsonProperty("source")
	@JsonPropertyDescription("The set of pipeline actions which produced this track.")
	private String source;
	public String getSource() { return source; }

	@JsonProperty("trackProperties")
	@JsonPropertyDescription("Additional properties set by the track.")
	private SortedMap<String,String> trackProperties = new TreeMap<>();
	public SortedMap<String,String> getTrackProperties() { return trackProperties; }

	@JsonProperty("exemplar")
	@JsonPropertyDescription("The detection which best represents this track.")
	private JsonDetectionOutputObject exemplar;
	public JsonDetectionOutputObject getExemplar() { return exemplar; }
	public void setExemplar(JsonDetectionOutputObject exemplar) { this.exemplar = exemplar; }

	@JsonProperty("detections")
	@JsonPropertyDescription("The collection of detections included in this track.")
	private SortedSet<JsonDetectionOutputObject> detections = new TreeSet<>();
	public SortedSet<JsonDetectionOutputObject> getDetections() { return detections; }

	public JsonTrackOutputObject(String id, int startOffsetFrame, int stopOffsetFrame, long startOffsetTime, long stopOffsetTime, String type, String source) {
		this.id = id;
		this.startOffsetFrame = startOffsetFrame;
		this.stopOffsetFrame = stopOffsetFrame;
		this.startOffsetTime = startOffsetTime;
		this.stopOffsetTime = stopOffsetTime;
		this.type = type;
		this.source = source;
	}

    public JsonTrackOutputObject(){}

	@JsonCreator
	public static JsonTrackOutputObject factory(@JsonProperty("id") String id,
	                                            @JsonProperty("startOffsetFrame") int startOffsetFrame,
	                                            @JsonProperty("stopOffsetFrame") int stopOffsetFrame,
												@JsonProperty("startOffsetTime") long startOffsetTime,
												@JsonProperty("stopOffsetTime") long stopOffsetTime,
	                                            @JsonProperty("type") String type,
	                                            @JsonProperty("source") String source,
												@JsonProperty("trackProperties") SortedMap<String, String> trackProperties,
	                                            @JsonProperty("exemplar") JsonDetectionOutputObject exemplar,
	                                            @JsonProperty("detections") SortedSet<JsonDetectionOutputObject> detections) {
		JsonTrackOutputObject trackOutputObject = new JsonTrackOutputObject(id, startOffsetFrame, stopOffsetFrame, startOffsetTime, stopOffsetTime, type, source);
		if (trackProperties != null) {
			trackProperties.putAll(trackProperties);
		}
		trackOutputObject.exemplar = exemplar;
		if(detections != null) {
			trackOutputObject.detections.addAll(detections);
		}
		return trackOutputObject;
	}

	public int hashCode() {
		return id.hashCode();
	}

	public boolean equals(Object other) {
		if(other == null || !(other instanceof JsonTrackOutputObject)) {
			return false;
		} else {
			JsonTrackOutputObject casted = (JsonTrackOutputObject)other;
			return compareTo(casted) == 0;
		}
	}

	@Override
	public int compareTo(JsonTrackOutputObject other) {
		int result = 0;
		if(other == null) {
			return 0;
		} else if((result = ObjectUtils.compare(id, other.id, false)) != 0
				|| (result = Integer.compare(startOffsetFrame, other.startOffsetFrame)) != 0
				|| (result = Integer.compare(stopOffsetFrame, other.stopOffsetFrame)) != 0
				|| (result = Long.compare(startOffsetTime, other.startOffsetTime)) != 0
				|| (result = Long.compare(stopOffsetTime, other.stopOffsetTime)) != 0
				|| (result = ObjectUtils.compare(type, other.type, false)) != 0
			    || (result = ObjectUtils.compare(source, other.source, false)) != 0
				|| (result = ObjectUtils.compare(exemplar, other.getExemplar(), false)) != 0
				|| (result = CompareUtils.compareMap(trackProperties,other.trackProperties)) != 0) {
			return result;
		} else {
			return 0;
		}
	}
}
