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
import com.google.common.base.Joiner;
import java.util.Comparator;
import java.util.Comparator;
import org.apache.commons.lang3.ObjectUtils;
import org.mitre.mpf.wfm.enums.ArtifactExtractionStatus;
import org.mitre.mpf.wfm.util.TextUtils;

import java.util.Map;
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
public class Detection implements Comparable<Detection>, Comparator<Detection> {

  /** Alternative Comparator developed for prioritizing Detections by (1) descending confidence, (2) ascending media frame offset, (3) ascending media time offset */
  public static final Comparator<Detection> DetectionConfidenceComparator = new Comparator<Detection> () {
    @Override
    public int compare(Detection d1, Detection d2) {
      // want to prioritize based on highest confidence, so we are sorting from high to low (i.e. descending) confidence
      // by using diff of d2(confidence) minus d1(confidence)
      // note importance of using the Detection.equals method for checking equivalency within this Comparator
      float diff = d2.getConfidence() - d1.getConfidence();
      if ( diff < 0 ) {
        return -1;
      } else if ( diff == 0 ) {
        // if confidence is equal, then return using the natural order for Detections
        return d1.compareTo(d2);
      } else {
        return 1;
      }
    }
    @Override
    public boolean equals(Object o) {
      if ( o instanceof Detection ) {
        return(this.equals((Detection)o));
      } else {
        return false;
      }
    }
  };

  private int x;
	public int getX() { return x; }

	private int y;
	public int getY() { return y; }

	private int width;
	public int getWidth() { return width; }

	private int height;
	public int getHeight() { return height; }

	private float confidence;
	public float getConfidence() { return confidence; }

	private int mediaOffsetFrame;
	public int getMediaOffsetFrame() { return mediaOffsetFrame; }

	private int mediaOffsetTime;
	public int getMediaOffsetTime() { return mediaOffsetTime; }

	private SortedMap<String,String> detectionProperties;
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
		if(obj == null || !(obj instanceof Detection)) {
			return false;
		} else {
			Detection casted = (Detection)obj;
			return compareTo(casted) == 0;
		}
	}

  /** Define natural order Comparator for Detections to be sorted by (1) ascending media frame offset, (2) ascending media time offset
   * @param d1 Detection to test
   * @param d2 Detection to be compared to d1
   */
  @Override
  public int compare(Detection d1, Detection d2) {
    return d1.compareTo(d2);
  }

  /** Define natural order Comparable for Detections to be sorted by (1) ascending media frame offset, (2) ascending media time offset
   * @param other Detection to be compared to this Detection
   */
	@Override
	public int compareTo(Detection other) {
		int comparison;
		if(other == null) {
      // kept original assumption that the other Detection is after this Detection if the other Detection is null
			comparison = 1;
		} else if((comparison = Integer.compare(mediaOffsetFrame, other.mediaOffsetFrame)) == 0 &&
				(comparison = Integer.compare(mediaOffsetTime, other.mediaOffsetTime)) == 0 &&
				(comparison = Integer.compare(x, other.x)) == 0 &&
				(comparison = Integer.compare(y, other.y)) == 0 &&
				(comparison = Integer.compare(width, other.width)) == 0 &&
				(comparison = Integer.compare(height, other.height)) == 0 &&
				(comparison = Float.compare(confidence, other.confidence)) == 0 &&
				(comparison = TextUtils.nullSafeCompare(artifactPath, other.artifactPath)) == 0 &&
				(comparison = compareMap(detectionProperties,other.detectionProperties)) == 0) {
			comparison = 0;
    } else if ( this.mediaOffsetFrame < other.mediaOffsetFrame ) {
      // This Detection is before the other Detection if this mediaOffsetFrame is less than the other mediaOffsetFrame
      comparison = -1;
    } else if ( this.mediaOffsetFrame > other.mediaOffsetFrame ) {
      // This Detection is after the other Detection if this mediaOffsetFrame is greater than the other mediaOffsetFrame
      comparison = 1;
    } else if ( this.mediaOffsetTime < other.mediaOffsetTime ) {
      // Secondary comparison, this Detection is before the other Detection if this mediaOffsetTime is less than the other mediaOffsetTime
      comparison = -1;
    } else if ( this.mediaOffsetTime > other.mediaOffsetTime ) {
      // Secondary comparison, this Detection is after the other Detection if this mediaOffsetTime is greater than the other mediaOffsetTime
      comparison = 1;
    } else {
      // If mediaOffsetFrame and mediaOffsetTime are equal, keep the original greater than assumption
      comparison = 1;
		}
		return comparison;
	}

	public String toString() {
		return String.format("%s#<bounds=(%d, %d, %d, %d), confidence=%f, mediaOffsetFrame=%d, mediaOffsetTime=%d, detection properties='%s'>",
				this.getClass().getName(), x, y, (x + width), (y + width), confidence, mediaOffsetFrame, mediaOffsetTime,
				TextUtils.mapToStringValues(detectionProperties));
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
