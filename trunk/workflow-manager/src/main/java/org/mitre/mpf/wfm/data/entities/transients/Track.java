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
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.wfm.util.TextUtils;

import java.util.*;

/**
 * A track is the logical grouping of detections of the same object as it appears at different offsets in a medium.
 *
 * In the context of a fixed-position video of a person walking across the street, a track would represent the person's
 * location in each frame of the video.
 *
 * In the context of an image, a track is effectively equivalent to a detection.
 *
 * A track in the context of an audio file is loosely defined based on times.
 */
public class Track implements Comparable<Track> {

	/** The numeric identifier for the job with which this track is associated. */
	private final long jobId;
	public long getJobId() { return jobId; }

	/** The numeric identifier for the media with which this track is associated. */
	private final long mediaId;
	public long getMediaId() { return mediaId; }

	/** The zero-based stage index in the pipeline containing the action which produced this track. */
	private final int stageIndex;
	public int getStageIndex() { return stageIndex; }

	/** The zero-based action index in the stage of the pipeline which produced this track. */
	private final int actionIndex;
	public int getActionIndex() { return actionIndex; }

	/** The zero-based index where the track begins in the medium, given in frames. */
	private final int startOffsetFrameInclusive;
	public int getStartOffsetFrameInclusive() { return startOffsetFrameInclusive; }

	/** The zero-based and inclusive final index where the track ends in the medium, given in frames. */
	private final int endOffsetFrameInclusive;
	public int getEndOffsetFrameInclusive() { return endOffsetFrameInclusive; }

	/** The zero-based index where the track begins in the medium, given in milliseconds. */
	private final int startOffsetTimeInclusive;
	public int getStartOffsetTimeInclusive() { return startOffsetTimeInclusive; }

	/** The zero-based and inclusive final index where the track ends in the medium, given in milliseconds. */
	private final int endOffsetTimeInclusive;
	public int getEndOffsetTimeInclusive() { return endOffsetTimeInclusive; }

	/** The type of object associated with this track (for example, FACE). */
	private final String type;
	public String getType() { return type; }

	/**
	 * The natural ordered (by start index) collection of detections which correspond to the position of the object
	 * as it moves through the track.
	 */
	private final SortedSet<Detection> detections;
	public SortedSet<Detection> getDetections() { return detections; }

	/** The detection with the highest confidence in the track. */
	private Detection exemplar;
	public Detection getExemplar() { return exemplar; }
	public void setExemplar(Detection exemplar) { this.exemplar = exemplar; }

	/**
	 * Creates a new track instance with the given immutable parameters.
	 *
	 * @param jobId The job with which this track is associated.
	 * @param mediaId The medium with which this track is associated.
	 * @param stageIndex The stage of the pipeline containing the action with which this track is associated.
	 * @param actionIndex The index of the action in the stage of the pipeline which created this track.
	 * @param startOffsetFrameInclusive The zero-based index where the track begins in the medium.
	 *                                     Frame number is relevant for image and video files.
	 * @param endOffsetFrameInclusive The zero-based and inclusive stop index where the track ends in the medium.
	 *                                     Frame number is relevant for image and video files.
	 * @param type The type of object associated with this track. This value is trimmed (to null)
	 *                  and converted to uppercase for convenience.
	 */
	public Track(
			long jobId,
			long mediaId,
			int stageIndex,
			int actionIndex,
			int startOffsetFrameInclusive,
			int endOffsetFrameInclusive,
			String type) {
		this(jobId, mediaId, stageIndex, actionIndex, startOffsetFrameInclusive, endOffsetFrameInclusive,
		     0, 0, type);
	}

	/**
	 * Creates a new track instance with the given immutable parameters.
	 *
	 * @param jobId The job with which this track is associated.
	 * @param mediaId The medium with which this track is associated.
	 * @param stageIndex The stage of the pipeline containing the action with which this track is associated.
	 * @param actionIndex The index of the action in the stage of the pipeline which created this track.
	 * @param startOffsetFrameInclusive The zero-based index where the track begins in the medium.
	 *                                      Frame number is relevant for image and video files.
	 * @param endOffsetFrameInclusive The zero-based and inclusive stop index where the track ends in the medium.
	 *                                      Frame number is relevant for image and video files.
	 * @param startOffsetTimeInclusive The zero-based index where the track begins in the medium.
	 *                                      Time is given in milliseconds, and is relevant for video and audio files.
	 * @param endOffsetTimeInclusive The zero-based and inclusive stop index where the track ends in the medium.
	 *                                      Time is given in milliseconds, and is relevant for video and audio files.
	 * @param type The type of object associated with this track.
	 *                  This value is trimmed (to null) and converted to uppercase for convenience.
	 */
	@JsonCreator
	public Track(
			@JsonProperty("jobId") long jobId,
			@JsonProperty("mediaId") long mediaId,
			@JsonProperty("stageIndex") int stageIndex,
			@JsonProperty("actionIndex") int actionIndex,
			@JsonProperty("startOffsetFrameInclusive") int startOffsetFrameInclusive,
			@JsonProperty("endOffsetFrameInclusive") int endOffsetFrameInclusive,
			@JsonProperty("startOffsetTimeInclusive") int startOffsetTimeInclusive,
			@JsonProperty("endOffsetTimeInclusive") int endOffsetTimeInclusive,
			@JsonProperty("type") String type) {
		this.jobId = jobId;
		this.mediaId = mediaId;
		this.stageIndex = stageIndex;
		this.actionIndex = actionIndex;
		this.startOffsetFrameInclusive = startOffsetFrameInclusive;
		this.endOffsetFrameInclusive = endOffsetFrameInclusive;
		this.startOffsetTimeInclusive = startOffsetTimeInclusive;
		this.endOffsetTimeInclusive = endOffsetTimeInclusive;
		this.type = StringUtils.upperCase(StringUtils.trimToNull(type));
		detections = new TreeSet<>();
	}

	@Override
	public int hashCode() {
		return Objects.hash(jobId, mediaId, stageIndex, actionIndex, startOffsetFrameInclusive, endOffsetFrameInclusive,
		                    startOffsetTimeInclusive, endOffsetTimeInclusive, TextUtils.nullSafeHashCode(type));
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Track)) {
			return false;
		}
		Track casted = (Track) obj;
		return compareTo(casted) == 0;
	}



	private static final Comparator<Set<Detection>> DETECTION_SET_COMPARATOR = Comparator
			.nullsFirst(Comparator
				.<Set<Detection>>comparingInt(Set::size))
				.thenComparing((s1, s2) -> {
					Iterator<Detection> it1 = s1.iterator();
					Iterator<Detection> it2 = s2.iterator();
					while (it1.hasNext()) {
						int comp = it1.next().compareTo(it2.next());
						if (comp != 0) {
							return comp;
						}
					}
					return 0;
				});

	private static final Comparator<Track> DEFAULT_COMPARATOR = Comparator
		.nullsFirst(Comparator
			.comparingLong(Track::getJobId)
			.thenComparingLong(Track::getMediaId)
			.thenComparingInt(Track::getStageIndex)
			.thenComparingInt(Track::getActionIndex)
			.thenComparingInt(Track::getStartOffsetFrameInclusive)
			.thenComparingInt(Track::getEndOffsetFrameInclusive)
			.thenComparingInt(Track::getStartOffsetTimeInclusive)
			.thenComparingInt(Track::getEndOffsetTimeInclusive)
			.thenComparing(Track::getType, Comparator.nullsFirst(Comparator.naturalOrder()))
			.thenComparing(Track::getExemplar, Comparator.nullsFirst(Comparator.naturalOrder()))
			.thenComparing(Track::getDetections, DETECTION_SET_COMPARATOR));

	@Override
	public int compareTo(Track other) {
		return DEFAULT_COMPARATOR.compare(this, other);
	}



	@Override
	public String toString() {
		return String.format(
			"%s#<startOffsetFrameInclusive=%d, endOffsetFrameInclusive=%d>#<startOffsetTimeInclusive=%d, endOffsetTimeInclusive=%d>",
			getClass().getSimpleName(),
			startOffsetFrameInclusive,
			endOffsetFrameInclusive,
			startOffsetTimeInclusive,
			endOffsetTimeInclusive);
	}
}
