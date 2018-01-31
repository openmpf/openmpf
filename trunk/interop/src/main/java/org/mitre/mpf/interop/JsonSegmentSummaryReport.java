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

import java.time.LocalDateTime;
import java.util.*;

@JsonTypeName("SegmentSummaryReport")
@JsonPropertyOrder({ "reportDate", "jobId", "segmentId", "segmentStartFrame", "segmentStopFrame",
        "errorMessage", "detectionType", "tracks" })
public class JsonSegmentSummaryReport {

    private LocalDateTime reportDate = null;
    /**
     * The date/time that this callback is being issued.
     * @return The date/time that this callback is being issued. This timestamp will be returned as a String
     * matching the TIMESTAMP_PATTERN, which is currently defined as {@value TimeUtils#TIMESTAMP_PATTERN}
     */
    @JsonGetter("reportDate")
    public String getReportDate() { return TimeUtils.getLocalDateTimeAsString(reportDate); }
    @JsonSetter("reportDate")
    public void setReportDate(LocalDateTime reportDate) { this.reportDate = reportDate; }

    @JsonProperty("jobId")
    @JsonPropertyDescription("The unique identifier assigned to this job by the system.")
    private long jobId;
    public long getJobId() { return jobId; }

    @JsonProperty("segmentId")
    @JsonPropertyDescription("The unique identifier assigned to this segment.")
    private long segmentId;
    public long getSegmentId() { return segmentId; }

    @JsonProperty("segmentStartFrame")
    @JsonPropertyDescription("The unique identifier for the frame at the start of this segment.")
    private long segmentStartFrame;
    public long getSegmentStartFrame() { return segmentStartFrame; }

    @JsonProperty("segmentStopFrame")
    @JsonPropertyDescription("The unique identifier for the frame at the end of this segment.")
    private long segmentStopFrame;
    public long getSegmentStopFrame() { return segmentStopFrame; }

    @JsonProperty("errorMessage")
    @JsonPropertyDescription("An error generated while detecting tracks. May be empty.")
    private String errorMessage;
    public String getErrorMessage() { return errorMessage; }

    @JsonProperty("output")
    @JsonPropertyDescription("Mapping of detection types to tracks.")
    private SortedMap<String, SortedSet<JsonTrackOutputObject>> types = new TreeMap<>();
    public SortedMap<String, SortedSet<JsonTrackOutputObject>> getTypes() { return types; }

    /*
    @JsonProperty("detectionType")
    @JsonPropertyDescription("The type of detections produced by the last pipeline stage.")
    private String detectionType;
    public String getDetectionType() { return detectionType; }

    @JsonProperty("tracks")
    @JsonPropertyDescription("The set of detection tracks produced by the last pipeline stage.")
    private List<JsonTrackOutputObject> tracks;
    public List<JsonTrackOutputObject> getTracks() { return tracks; }
    */

	public JsonSegmentSummaryReport(long jobId, long segmentId, long segmentStartFrame, long segmentStopFrame,
                                    String detectionType, List<JsonTrackOutputObject> tracks, String errorMessage) {
		this.jobId = jobId;
        this.segmentId = segmentId;
        this.segmentStartFrame = segmentStartFrame;
		this.segmentStopFrame = segmentStopFrame;
		this.errorMessage = errorMessage;

		if (tracks.isEmpty()) {
            types.put(JsonActionOutputObject.NO_TRACKS_TYPE, new TreeSet<>());
        } else {
		    types.put(detectionType, new TreeSet<>(tracks));
        }
	}

    @JsonCreator
    public static JsonSegmentSummaryReport factory(@JsonProperty("jobId") long jobId,
                                                   @JsonProperty("segmentId") long segmentId,
                                                   @JsonProperty("segmentStartFrame") long segmentStartFrame,
                                                   @JsonProperty("segmentStopFrame") long segmentStopFrame,
                                                   @JsonProperty("detectionType") String detectionType,
                                                   @JsonProperty("tracks") List<JsonTrackOutputObject> tracks,
                                                   @JsonProperty("errorMessage") String errorMessage) {
        JsonSegmentSummaryReport summaryReport =
                new JsonSegmentSummaryReport(jobId, segmentId, segmentStartFrame, segmentStopFrame, detectionType, tracks, errorMessage);
        /*
        if (tracks != null) {
            summaryReport.tracks.addAll(tracks);
        }
        */
        return summaryReport;
    }

    /*
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
	*/
}
