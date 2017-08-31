/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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
import org.apache.commons.lang3.ObjectUtils;
import org.mitre.mpf.wfm.enums.ArtifactExtractionStatus;
import org.mitre.mpf.wfm.util.TextUtils;

import java.util.Comparator;
import java.util.Objects;
import java.util.SortedMap;

/**
 * A Detection is the representation of an object which was found at a certain "position" in a medium.
 *
 * In the context of videos, a detection includes the frame number (mediaOffsetFrame) of the video where the object appears
 * as well as the bounding box of the object in the frame.
 *
 * In the context of images, detections are effectively treated as if they originated from a single-frame video.
 *
 * In the context of audio files, detections are not yet well-defined.
 * */
public class Detection implements Comparable<Detection> {

	private final int x;
	public int getX() { return x; }

	private final int y;
	public int getY() { return y; }

	private final int width;
	public int getWidth() { return width; }

	private final int height;
	public int getHeight() { return height; }

	private final float confidence;
	public float getConfidence() { return confidence; }

	private final int mediaOffsetFrame;
	public int getMediaOffsetFrame() { return mediaOffsetFrame; }

	private final int mediaOffsetTime;
	public int getMediaOffsetTime() { return mediaOffsetTime; }

	private final SortedMap<String,String> detectionProperties;
	public SortedMap<String,String> getDetectionProperties() { return detectionProperties; }

	private String artifactPath;
	public String getArtifactPath() { return artifactPath; }
	public void setArtifactPath(String artifactPath) { this.artifactPath = artifactPath; }

	private ArtifactExtractionStatus artifactExtractionStatus = ArtifactExtractionStatus.NOT_ATTEMPTED;
	public ArtifactExtractionStatus getArtifactExtractionStatus() { return artifactExtractionStatus; }
	public void setArtifactExtractionStatus(ArtifactExtractionStatus artifactExtractionStatus) { this.artifactExtractionStatus = artifactExtractionStatus; }

	@JsonCreator
	public Detection(@JsonProperty("x") int x, @JsonProperty("y") int y, @JsonProperty("width") int width, @JsonProperty("height") int height, @JsonProperty("confidence") float confidence, @JsonProperty("mediaOffsetFrame") int mediaOffsetFrame, @JsonProperty("mediaOffsetTime") int mediaOffsetTime, @JsonProperty("detectionProperties") SortedMap<String, String> detectionProperties) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.confidence = confidence;
		this.mediaOffsetFrame = mediaOffsetFrame;
		this.mediaOffsetTime = mediaOffsetTime;
		this.detectionProperties = detectionProperties;
	}

	@Override
	public int hashCode() {
		return Objects.hash(x,y,width,height,confidence,mediaOffsetFrame,mediaOffsetTime,detectionProperties);
	}

	@Override
	public boolean equals(Object obj) {
		if(!(obj instanceof Detection)) {
			return false;
		}
		Detection casted = (Detection)obj;
		return compareTo(casted) == 0;
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
				  .thenComparing(Detection::getArtifactPath, TextUtils::nullSafeCompare)
				  .thenComparing(Detection::getDetectionProperties, Detection::compareMap));

	/** Define natural order Comparable for Detections to be sorted by (1) ascending media frame offset, (2) ascending media time offset
	 * @param other Detection to be compared to this Detection
	 */
	@Override
	public int compareTo(Detection other) {
		return DEFAULT_COMPARATOR.compare(this, other);
	}

	public String toString() {
		return String.format("%s#<bounds=(%d, %d, %d, %d), confidence=%f, mediaOffsetFrame=%d, mediaOffsetTime=%d, detection properties='%s'>",
				this.getClass().getName(), x, y, (x + width), (y + width), confidence, mediaOffsetFrame, mediaOffsetTime,
				TextUtils.mapToStringValues(detectionProperties));
	}


	private static int compareMap(SortedMap<String, String> map1, SortedMap<String, String> map2) {
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
