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

import java.time.Instant;
import java.util.*;

import static org.mitre.mpf.interop.util.CompareUtils.nullsFirst;


@JsonTypeName("StreamingTrackOutputObject")
@JsonPropertyOrder({ "id", "startOffsetFrame", "stopOffsetFrame", "startTimestamp", "stopTimestamp",
		"type", /*"source",*/ "confidence", "trackProperties", "exemplar", "detections" })
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
	private final Instant startTimestamp;
	public Instant getStartTimestamp() { return startTimestamp; }

	@JsonPropertyDescription("The date and time that the end of this track was detected")
	private final Instant stopTimestamp;
	public Instant getStopTimestamp() { return stopTimestamp; }

	@JsonPropertyDescription("The type of object associated with this track.")
	private final String type;
	public String getType() { return type; }

	/* TODO: For future use
	@JsonPropertyDescription("The set of pipeline actions which produced this track.")
	private final String source;
	public String getSource() { return source; }
	*/

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
	private final JsonStreamingDetectionOutputObject exemplar;
	public JsonStreamingDetectionOutputObject getExemplar() { return exemplar; }

	@JsonProperty("detections")
	@JsonPropertyDescription("The collection of detections included in this track.")
	private final SortedSet<JsonStreamingDetectionOutputObject> detections = new TreeSet<>();
	public SortedSet<JsonStreamingDetectionOutputObject> getDetections() { return detections; }



	@JsonCreator
	public JsonStreamingTrackOutputObject(
			@JsonProperty("id") String id,
			@JsonProperty("startOffsetFrame") int startOffsetFrame,
			@JsonProperty("stopOffsetFrame") int stopOffsetFrame,
			@JsonProperty("startTimestamp") Instant startTimestamp,
			@JsonProperty("stopTimestamp") Instant stopTimestamp,
			@JsonProperty("type") String type,
			/* @JsonProperty("source") String source, */
            @JsonProperty("confidence") float confidence,
			@JsonProperty("trackProperties") Map<String, String> trackProperties,
			@JsonProperty("exemplar") JsonStreamingDetectionOutputObject exemplar,
			@JsonProperty("detections") Collection<JsonStreamingDetectionOutputObject> detections) {
		this.id = id;
		this.startOffsetFrame = startOffsetFrame;
		this.stopOffsetFrame = stopOffsetFrame;
		this.startTimestamp = startTimestamp;
		this.stopTimestamp = stopTimestamp;
		this.type = type;
		/* this.source = source; */
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
		return Objects.hash(startOffsetFrame, stopOffsetFrame, startTimestamp, stopTimestamp, type, /* source, */
		                    confidence, trackProperties, exemplar, id);
	}

	@Override
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
				/* .thenComparing(JsonStreamingTrackOutputObject::getSource, nullsFirst()) */
                .thenComparingDouble(JsonStreamingTrackOutputObject::getConfidence)
                .thenComparing(JsonStreamingTrackOutputObject::getTrackProperties, CompareUtils.MAP_COMPARATOR)
				.thenComparing(JsonStreamingTrackOutputObject::getExemplar, nullsFirst())
				.thenComparing(JsonStreamingTrackOutputObject::getId, nullsFirst())
			);


	@Override
	public int compareTo(JsonStreamingTrackOutputObject other) {
		return DEFAULT_COMPARATOR.compare(this, other);
	}
}
