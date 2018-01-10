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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;

public class SegmentSummaryReport {

	private final long _segmentNumber;

	private final long _segmentStartFrame;

	private final long _segmentStopFrame;

	private final String _detectionType;

	private final List<Track> _tracks;

	private final String _errorMessage;


	public SegmentSummaryReport(long segmentNumber, long segmentStartFrame, long segmentStopFrame,
	                            String detectionType, List<Track> tracks, String errorMessage) {
		_segmentNumber = segmentNumber;
		_segmentStartFrame = segmentStartFrame;
		_segmentStopFrame = segmentStopFrame;
		_detectionType = detectionType;
		_tracks = ImmutableList.copyOf(tracks);
		_errorMessage = errorMessage;
	}



	public long getSegmentNumber() {
		return _segmentNumber;
	}

	public long getSegmentStartFrame() {
		return _segmentStartFrame;
	}

	public long getSegmentStopFrame() {
		return _segmentStopFrame;
	}

	public String getDetectionType() {
		return _detectionType;
	}

	public List<Track> getTracks() {
		return _tracks;
	}

	public String getErrorMessage() {
		return _errorMessage;
	}


	public static class Track {

		private final long _startFrame;

		private final long _stopFrame;

		private final Map<Long, ImageLocation> _frameLocations;

		private final Map<String, String> _detectionProperties;

		public Track(long startFrame, long stopFrame, Map<Long, ImageLocation> frameLocations,
		             Map<String, String> detectionProperties) {
			_startFrame = startFrame;
			_stopFrame = stopFrame;
			_frameLocations = ImmutableMap.copyOf(frameLocations);
			_detectionProperties = ImmutableMap.copyOf(detectionProperties);
		}
	}


	public static class ImageLocation {

		private final int _xLeftUpper;

		private final int _yLeftUpper;

		private final int _width;

		private final int _height;

		private final double _confidence;

		private final Map<String, String> _detectionProperties;

		public ImageLocation(int xLeftUpper, int yLeftUpper, int width, int height, double confidence,
		                     Map<String, String> detectionProperties) {
			_xLeftUpper = xLeftUpper;
			_yLeftUpper = yLeftUpper;
			_width = width;
			_height = height;
			_confidence = confidence;
			_detectionProperties = ImmutableMap.copyOf(detectionProperties);
		}

		public int getxLeftUpper() {
			return _xLeftUpper;
		}

		public int getyLeftUpper() {
			return _yLeftUpper;
		}

		public int getWidth() {
			return _width;
		}

		public int getHeight() {
			return _height;
		}

		public double getConfidence() {
			return _confidence;
		}

		public Map<String, String> getDetectionProperties() {
			return _detectionProperties;
		}
	}
}
