/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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

import java.time.Instant;
import java.util.*;

@JsonTypeName("SegmentSummaryReport")
@JsonPropertyOrder({ "reportDate", "jobId", "externalId", "segmentId", "segmentStartFrame", "segmentStopFrame",
        "errorMessage", "trackType", "tracks" })
public class JsonSegmentSummaryReport {

    @JsonProperty("reportDate")
    @JsonPropertyDescription("The timestamp for this report, local system time. Example: 2018-12-19T12:12:59.995-05:00")
    private Instant reportDate = null;
    public Instant getReportDate() { return reportDate; }
    public void setReportDate(Instant reportDate) { this.reportDate = reportDate; }

    @JsonProperty("jobId")
    @JsonPropertyDescription("The unique identifier assigned to this job by the system.")
    private long jobId;
    public long getJobId() { return jobId; }

    @JsonProperty("externalId")
    @JsonPropertyDescription("The external identifier defined in the job creation request.")
    private String externalId;
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

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
    @JsonPropertyDescription("Mapping of track types to tracks.")
    private SortedMap<String, SortedSet<JsonStreamingTrackOutputObject>> trackTypes = new TreeMap<>();
    public SortedMap<String, SortedSet<JsonStreamingTrackOutputObject>> getTrackTypes() { return trackTypes; }

    @JsonCreator
    public JsonSegmentSummaryReport(@JsonProperty("reportDate") Instant reportDate,
                                    @JsonProperty("jobId") long jobId,
                                    @JsonProperty("segmentId") long segmentId,
                                    @JsonProperty("segmentStartFrame") long segmentStartFrame,
                                    @JsonProperty("segmentStopFrame") long segmentStopFrame,
                                    @JsonProperty("trackType") String trackType,
                                    @JsonProperty("tracks") List<JsonStreamingTrackOutputObject> tracks,
                                    @JsonProperty("errorMessage") String errorMessage) {
        this.reportDate = reportDate;
        this.jobId = jobId;
        this.segmentId = segmentId;
        this.segmentStartFrame = segmentStartFrame;
        this.segmentStopFrame = segmentStopFrame;
        this.errorMessage = errorMessage;

        if (tracks == null || tracks.isEmpty()) {
            trackTypes.put(JsonActionOutputObject.NO_TRACKS_TYPE, new TreeSet<>());
        } else {
            trackTypes.put(trackType, new TreeSet<>(tracks));
        }
    }
}
