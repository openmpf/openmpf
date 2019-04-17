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

package org.mitre.mpf.wfm.data.entities.transients;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSortedMap;
import org.mitre.mpf.interop.util.CompareUtils;
import org.mitre.mpf.wfm.enums.ArtifactExtractionStatus;
import org.mitre.mpf.wfm.util.TextUtils;

import java.util.*;

/**
 * A Detection is the representation of an object which was found at a certain "position" in a medium.
 *
 * In the context of videos, a detection includes the frame number (mediaOffsetFrame) of the video where the object
 * appears as well as the bounding box of the object in the frame.
 *
 * In the context of images, detections are effectively treated as if they originated from a single-frame video.
 *
 * In the context of audio files, detections are not yet well-defined.
 */
public class Detection implements Comparable<Detection> {

	private final int _x;
	public int getX() { return _x; }

	private final int _y;
	public int getY() { return _y; }

	private final int _width;
	public int getWidth() { return _width; }

	private final int _height;
	public int getHeight() { return _height; }

	private final float _confidence;
	public float getConfidence() { return _confidence; }

	private final int _mediaOffsetFrame;
	public int getMediaOffsetFrame() { return _mediaOffsetFrame; }

	private final int _mediaOffsetTime;
	public int getMediaOffsetTime() { return _mediaOffsetTime; }

	private final ImmutableSortedMap<String,String> _detectionProperties;
	public ImmutableSortedMap<String,String> getDetectionProperties() { return _detectionProperties; }

	private String _artifactPath;
	public String getArtifactPath() { return _artifactPath; }
	public void setArtifactPath(String artifactPath) { _artifactPath = artifactPath; }

	private ArtifactExtractionStatus _artifactExtractionStatus = ArtifactExtractionStatus.NOT_ATTEMPTED;
	public ArtifactExtractionStatus getArtifactExtractionStatus() { return _artifactExtractionStatus; }
	public void setArtifactExtractionStatus(ArtifactExtractionStatus artifactExtractionStatus) {
		_artifactExtractionStatus = artifactExtractionStatus;
	}

	@JsonCreator
	public Detection(
			@JsonProperty("x") int x,
			@JsonProperty("y") int y,
			@JsonProperty("width") int width,
			@JsonProperty("height") int height,
			@JsonProperty("confidence") float confidence,
			@JsonProperty("mediaOffsetFrame") int mediaOffsetFrame,
			@JsonProperty("mediaOffsetTime") int mediaOffsetTime,
			@JsonProperty("detectionProperties") Map<String, String> detectionProperties) {
		_x = x;
		_y = y;
		_width = width;
		_height = height;
		_confidence = confidence;
		_mediaOffsetFrame = mediaOffsetFrame;
		_mediaOffsetTime = mediaOffsetTime;
		_detectionProperties = ImmutableSortedMap.copyOf(detectionProperties);
	}

	@Override
	public int hashCode() {
		return Objects.hash(_mediaOffsetFrame, _mediaOffsetTime, _x, _y, _width, _height, _confidence,
		                    _detectionProperties);
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof Detection && compareTo((Detection) obj) == 0;
	}


	private static final Comparator<Detection> DEFAULT_COMPARATOR = Comparator
		.nullsFirst(Comparator
			.comparingInt(Detection::getMediaOffsetFrame)
			.thenComparingInt(Detection::getMediaOffsetTime)
			.thenComparingInt(Detection::getX)
			.thenComparingInt(Detection::getY)
			.thenComparingInt(Detection::getWidth)
			.thenComparingInt(Detection::getHeight)
			.thenComparingDouble(Detection::getConfidence)
			.thenComparing(Detection::getDetectionProperties, CompareUtils.MAP_COMPARATOR));

	@Override
	public int compareTo(Detection other) {
		//noinspection ObjectEquality - Just an optimization to avoid comparing all fields when compared to itself.
		return this == other ? 0 : DEFAULT_COMPARATOR.compare(this, other);
	}

	@Override
	public String toString() {
		return String.format(
			"%s#<bounds=(%d, %d, %d, %d), confidence=%f, mediaOffsetFrame=%d, mediaOffsetTime=%d, detection properties='%s'>",
			getClass().getName(),
			_x,
			_y,
			(_x + _width),
			(_y + _width),
			_confidence,
			_mediaOffsetFrame,
			_mediaOffsetTime,
			TextUtils.mapToStringValues(_detectionProperties));
	}
}
