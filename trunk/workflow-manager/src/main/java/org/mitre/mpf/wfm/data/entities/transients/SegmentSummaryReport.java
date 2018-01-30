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

import java.util.*;

public class SegmentSummaryReport {

	private final long _jobId;

	private final long _segmentNumber;

	private final long _segmentStartFrame;

	private final long _segmentStopFrame;

	private final String _detectionType;

	private final List<Track> _tracks;

	private final String _errorMessage;


	public SegmentSummaryReport(long jobId, long segmentNumber, long segmentStartFrame, long segmentStopFrame,
	                            String detectionType, List<Track> tracks, String errorMessage) {
		_jobId = jobId;
		_segmentNumber = segmentNumber;
		_segmentStartFrame = segmentStartFrame;
		_segmentStopFrame = segmentStopFrame;
		_detectionType = detectionType;
		_tracks = new ArrayList<>(tracks);
		_errorMessage = errorMessage;
	}


	public long getJobId() {
		return _jobId;
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
		return Collections.unmodifiableList(_tracks);
	}

	public String getErrorMessage() {
		return _errorMessage;
	}


	public static class Track {

		private final long _startFrame;

		private final long _startTime;

		private final long _stopFrame;

		private final long _stopTime;

		private final double _confidence;

		private final List<VideoDetection> _detections;

		private final Map<String, String> _detectionProperties;

		public Track(long startFrame, long startTime, long stopFrame, long stopTime, double confidence,
		             List<VideoDetection> detections, Map<String, String> detectionProperties) {
			_startFrame = startFrame;
			_startTime = startTime;
			_stopFrame = stopFrame;
			_stopTime = stopTime;
			_confidence = confidence;
			_detections = new ArrayList<>(detections);
			_detectionProperties = new HashMap<>(detectionProperties);
		}

		public long getStartFrame() {
			return _startFrame;
		}

		public long getStartTime() {
			return _startTime;
		}

		public long getStopFrame() {
			return _stopFrame;
		}

		public long getStopTime() {
			return _stopTime;
		}

		public double getConfidence() {
			return _confidence;
		}

		public List<VideoDetection> getDetections() {
			return Collections.unmodifiableList(_detections);
		}

		public Map<String, String> getDetectionProperties() {
			return Collections.unmodifiableMap(_detectionProperties);
		}
	}


	public static class VideoDetection {

		private final long _frameNumber;

		private final long _time;

		private final int _xLeftUpper;

		private final int _yLeftUpper;

		private final int _width;

		private final int _height;

		private final double _confidence;

		private final Map<String, String> _detectionProperties;


		public VideoDetection(long frameNumber, long time, int xLeftUpper, int yLeftUpper, int width, int height,
		                      double confidence, Map<String, String> detectionProperties) {
			_frameNumber = frameNumber;
			_time = time;
			_xLeftUpper = xLeftUpper;
			_yLeftUpper = yLeftUpper;
			_width = width;
			_height = height;
			_confidence = confidence;
			_detectionProperties = new HashMap<>(detectionProperties);
		}

		public long getFrameNumber() {
			return _frameNumber;
		}

		public long getTime() {
			return _time;
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
			return Collections.unmodifiableMap(_detectionProperties);
		}
	}
}
