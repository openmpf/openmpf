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
import org.mitre.mpf.interop.util.TimeUtils;

import java.util.Comparator;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.mitre.mpf.interop.util.CompareUtils.nullsFirst;


@JsonTypeName("StreamingTrackOutputObject")
@JsonPropertyOrder({ "id", "startOffsetFrame", "stopOffsetFrame", "startTimestamp", "stopTimestamp",
		"type", "source", "exemplar", "detections" })
public class JsonStreamingTrackOutputObject implements Comparable<JsonStreamingTrackOutputObject> {

	@JsonPropertyDescription("The unique identifier for this track.")
	private final String id;
	public String getId() { return id; }

	@JsonPropertyDescription("The offset in the medium where the track starts.")
	private final int startOffsetFrame;
	public int getStartOffsetFrame() { return startOffsetFrame; }

	@JsonPropertyDescription("The offset in the medium where the track ends.")
	private final int stopOffsetFrame;
	public int getStopOffsetFrame() { return stopOffsetFrame; }

	@JsonPropertyDescription("The date and time that the beginning of this track was detected")
	private final String startTimestamp;
	public String getStartTimestamp() { return startTimestamp; }

	@JsonPropertyDescription("The date and time that the end of this track was detected")
	private final String stopTimestamp;
	public String getStopTimestamp() { return stopTimestamp; }

	@JsonPropertyDescription("The type of object associated with this track.")
	private final String type;
	public String getType() { return type; }

	@JsonPropertyDescription("The set of pipeline actions which produced this track.")
	private final String source;
	public String getSource() { return source; }

	@JsonProperty("exemplar")
	@JsonPropertyDescription("The detection which best represents this track.")
	private final JsonStreamingDetectionOutputObject exemplar;
	public JsonStreamingDetectionOutputObject getExemplar() { return exemplar; }

	@JsonProperty("detections")
	@JsonPropertyDescription("The collection of detections included in this track.")
	private final SortedSet<JsonStreamingDetectionOutputObject> detections = new TreeSet<>();
	public SortedSet<JsonStreamingDetectionOutputObject> getDetections() { return detections; }


	public JsonStreamingTrackOutputObject(
			String id,
			int startOffsetFrame,
			int stopOffsetFrame,
			long startTimestampMillis,
			long stopTimestampMillis,
			String type,
			String source,
			JsonStreamingDetectionOutputObject exemplar,
			SortedSet<JsonStreamingDetectionOutputObject> detections) {

		this(id, startOffsetFrame, stopOffsetFrame, TimeUtils.millisToDateTimeString(startTimestampMillis),
		     TimeUtils.millisToDateTimeString(stopTimestampMillis), type ,source, exemplar, detections);
	}


	@JsonCreator
	public JsonStreamingTrackOutputObject(
			@JsonProperty("id") String id,
			@JsonProperty("startOffsetFrame") int startOffsetFrame,
			@JsonProperty("stopOffsetFrame") int stopOffsetFrame,
			@JsonProperty("startTimestamp") String startTimestamp,
			@JsonProperty("stopTimestamp") String stopTimestamp,
			@JsonProperty("type") String type,
			@JsonProperty("source") String source,
			@JsonProperty("exemplar") JsonStreamingDetectionOutputObject exemplar,
			@JsonProperty("detections") SortedSet<JsonStreamingDetectionOutputObject> detections) {
		this.id = id;
		this.startOffsetFrame = startOffsetFrame;
		this.stopOffsetFrame = stopOffsetFrame;
		this.startTimestamp = startTimestamp;
		this.stopTimestamp = stopTimestamp;
		this.type = type;
		this.source = source;
		this.exemplar = exemplar;
		if (detections != null) {
			this.detections.addAll(detections);
		}
	}



	public int hashCode() {
		return Objects.hash(startOffsetFrame, stopOffsetFrame, startTimestamp, stopTimestamp, type, source,
		                    exemplar, id);
	}

	public boolean equals(Object other) {
		return this == other
				|| (other instanceof JsonStreamingTrackOutputObject
						&& compareTo((JsonStreamingTrackOutputObject) other) == 0);
	}


	private static final Comparator<JsonStreamingTrackOutputObject> DEFAULT_COMPARATOR = Comparator
			.nullsFirst(Comparator
				.comparingInt(JsonStreamingTrackOutputObject::getStartOffsetFrame)
				.thenComparingInt(JsonStreamingTrackOutputObject::getStopOffsetFrame)
				.thenComparing(JsonStreamingTrackOutputObject::getStartTimestamp)
				.thenComparing(JsonStreamingTrackOutputObject::getStopTimestamp)
				.thenComparing(JsonStreamingTrackOutputObject::getType, nullsFirst())
				.thenComparing(JsonStreamingTrackOutputObject::getSource, nullsFirst())
				.thenComparing(JsonStreamingTrackOutputObject::getExemplar, nullsFirst())
				.thenComparing(JsonStreamingTrackOutputObject::getId, nullsFirst())
			);


	@Override
	public int compareTo(JsonStreamingTrackOutputObject other) {
		return DEFAULT_COMPARATOR.compare(this, other);
	}
}
