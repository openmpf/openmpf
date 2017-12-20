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

package org.mitre.mpf.interop;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class JsonHealthReportDataCallbackBody {

    // All timestamps in OpenMPF should adhere to this date/time pattern.
    private static final String TIMESTAMP_PATTERN = "yyyy-MM-dd kk:mm:ss.S";
    private static final DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN);

    private LocalDateTime reportDate = null;
    /**
     * The date/time that this Health Report is being issued.
     * @return The date/time that this Health Report is being issued. This timestamp will be returned as a String
     * matching the TIMESTAMP_PATTERN, which is currently defined as {@value #TIMESTAMP_PATTERN}
     */
    @JsonGetter("reportDate")
    public String getReportDate() {
        return timestampFormatter.format(reportDate);
    }
    @JsonSetter("reportDate")
    @JsonFormat(pattern = TIMESTAMP_PATTERN)
    public void setReportDate(String s) throws DateTimeParseException {
        this.reportDate = parseStringAsLocalDateTime(s);
    }

    @JsonIgnore
    public static String formatLocalDateTimeAsString(LocalDateTime d) { return timestampFormatter.format(d); }
    @JsonIgnore
    public static LocalDateTime parseStringAsLocalDateTime(String s) throws DateTimeParseException {
        return timestampFormatter.parse(s, LocalDateTime::from);
    }

    /** The internal job identifier(s) assigned to these jobs by MPF. May be 1 to many. */
    private List<Long> jobId = new ArrayList<>();
    @JsonSetter
    public void setJobId(List<Long> jobId) { this.jobId = jobId; }
    @JsonGetter
    public List<Long> getJobId() { return jobId; }

    /** The external ID(s) that were specified for these jobs when they were requested. Note that an externalId may be null if not specified for a streaming job.
    **/
    private List<String> externalId = new ArrayList<>();
    @JsonSetter("externalId")
    public void setExternalId(List<String> externalId) { this.externalId = externalId; }
    @JsonGetter("externalId")
    public List<String> getExternalId() {
        return externalId.stream().map(externalId -> {
        if ( externalId == null ) {
            return (String) "";
        } else {
            return externalId;
        }
    }).collect(Collectors.toList()); }

    private List<String> /*JobStatus*/ jobStatus = new ArrayList<>();
    @JsonSetter("jobStatus")
    public void setJobStatus(List<String> jobStatus) { this.jobStatus = jobStatus; }
    @JsonGetter("jobStatus")
    public List<String> /*JobStatus*/ getJobStatus() {
        return jobStatus.stream().map(status -> {
            if ( status == null ) {
                return (String) "";
            } else {
                return status;
            }
        }).collect(Collectors.toList());
    }

    public static final BigInteger INVALID_FRAME_ID = BigInteger.valueOf(-1L);
    private List<BigInteger> lastNewActivityAlertFrameId = new ArrayList<>();
    @JsonSetter("lastNewActivityAlertFrameId")
    public void setLastNewActivityAlertFrameId(List<BigInteger> lastNewActivityAlertFrameId) { this.lastNewActivityAlertFrameId = lastNewActivityAlertFrameId; }
    /**
     * The frame ids from the last new Activity Alerts received for these streaming jobs.
     * Value stored internally as a BigInteger to accommodate for this value to be sized in the Components as an unsigned long.
     * @return the last New Activity Alert frame ids associated with each streaming job. Values within the List will be INVALID_FRAME_ID if
     * a New Activity Alert has not been issued for a streaming job.
     */
    @JsonGetter("lastNewActivityAlertFrameId")
    public List<BigInteger> getLastNewActivityAlertFrameId() { return lastNewActivityAlertFrameId.stream().map(frameId -> {
        if ( frameId == null ) {
            return INVALID_FRAME_ID;
        } else {
            return frameId;
        }
    }).collect(Collectors.toList()); }

    private List<LocalDateTime> lastNewActivityAlertTimestamp = new ArrayList<>();
    /**
     * Get the timestamp from the last new Activity Alert received for each streaming job.
     * @return The last New Activity Alert timestamps for each streaming. Values within the List may be empty String if
     * a New Activity Alert has not been issued for a streaming job. Otherwise, the timestamp will be returned as a String
     * matching the TIMESTAMP_PATTERN, which is currently defined as {@value #TIMESTAMP_PATTERN}
     */
    @JsonGetter("lastNewActivityAlertTimestamp")
    public List<String> getLastNewActivityAlertTimeStamp() {
        return lastNewActivityAlertTimestamp.stream().map(timestamp -> getTimestampAsString(timestamp)).collect(
            Collectors.toList());
    }
    /**
     * Set the timestamp from the last new Activity Alert received for each streaming job.
     * Values within the List may be null if a New Activity Alert has not been issued for a streaming job.
     */
    @JsonSetter("lastNewActivityAlertTimestamp")
    public void setLastNewActivityAlertTimestamp(List<String> timestamps) {
        lastNewActivityAlertTimestamp = timestamps.stream().map(s -> {
            if ( s.isEmpty() ) {
                return (LocalDateTime) null;
            } else {
                return parseStringAsLocalDateTime(s);
            }
        }).collect(Collectors.toList());
    }

    @JsonIgnore
    private String getTimestampAsString(LocalDateTime timestamp) {
        if ( timestamp == null ) {
            return (String) "";
        } else {
            return timestampFormatter.format(timestamp);
        }
    }

     /**
     * Constructor used to create a health report for a single job
     * @param reportDate timestamp for this Health Report.
     * @param jobId job id for this streaming job.
     * @param externalId external id for this streaming job. May be null if an external id was not defined.
     * @param jobStatus status of this streaming job.
     * @param lastNewActivityAlertFrameId frame id from the last new activity alert that was issued for this streaming job. May be null if
     * a New Activity Alert has not yet been issued for this streaming job.
     * @param lastNewActivityAlertTimestamp timestamp from the last new activity alert that was issued for this streaming job. May be null if
     * a New Activity Alert has not yet been issued for this streaming job.
     */
     @JsonIgnore
    public JsonHealthReportDataCallbackBody(@JsonProperty("reportDate") LocalDateTime reportDate,
        @JsonProperty("jobId") long jobId, @JsonProperty("externalId") String externalId,
        @JsonProperty("jobStatus") String /*JobStatus*/ jobStatus,
        @JsonProperty("lastNewActivityAlertFrameId") BigInteger lastNewActivityAlertFrameId,
        @JsonProperty("lastNewActivityAlertTimestamp") LocalDateTime lastNewActivityAlertTimestamp) {

        this.reportDate = reportDate;
        this.jobId.add(jobId);
        this.jobStatus.add(jobStatus);
        // Leave properties that may be null as an empty List if the passed values are null.
        if ( externalId != null ) {
            this.externalId.add(externalId);
        }
        if ( lastNewActivityAlertFrameId != null ) {
            this.lastNewActivityAlertFrameId.add(lastNewActivityAlertFrameId);
        }
        if ( lastNewActivityAlertTimestamp != null ) {
            this.lastNewActivityAlertTimestamp.add(lastNewActivityAlertTimestamp);
        }
    }

    /**
     * Constructor used to create a health report for multiple jobs
     * @param reportDate timestamp for this Health Report.
     * @param jobId job ids being reported on.
     * @param externalId external ids for each streaming job. May be null if an external id was not defined.
     * @param jobStatus status of each streaming job.
     * @param lastNewActivityAlertFrameId frame ids from the last new activity alert that was issued for each streaming job. May be null if
     * a New Activity Alert has not yet been issued for the streaming job.
     * @param lastNewActivityAlertTimestamp timestamps from the last new activity alert that was issued for each streaming job. May be null if
     * a New Activity Alert has not yet been issued for the streaming job.
     */
    @JsonIgnore
    public JsonHealthReportDataCallbackBody(@JsonProperty("reportDate") LocalDateTime reportDate,
        @JsonProperty("jobId") List<Long> jobId, @JsonProperty("externalId") List<String> externalId,
        @JsonProperty("jobStatus") List<String> /*JobStatus*/ jobStatus,
        @JsonProperty("lastNewActivityAlertFrameId") List<BigInteger> lastNewActivityAlertFrameId,
        @JsonProperty("lastNewActivityAlertTimestamp") List<LocalDateTime> lastNewActivityAlertTimestamp) {
        this.reportDate = reportDate;
        this.jobId = jobId;
        this.jobStatus = jobStatus;
        // Leave properties that may be null as an empty List if the passed values are null.
        if ( externalId != null ) {
            this.externalId = externalId;
        }
        if ( lastNewActivityAlertFrameId != null ) {
            this.lastNewActivityAlertFrameId = lastNewActivityAlertFrameId;
        }
        if ( lastNewActivityAlertTimestamp != null ) {
            this.lastNewActivityAlertTimestamp = lastNewActivityAlertTimestamp;
        }
    }

    /**
     * Constructor used to create a health report for multiple jobs
     * @param reportDate timestamp formatted String for this Health Report.
     * @param jobId job ids being reported on.
     * @param externalId external ids for each streaming job. May be null if an external id was not defined.
     * @param jobStatus status of each streaming job.
     * @param lastNewActivityAlertFrameId frame ids from the last new activity alert that was issued for each streaming job. May be null if
     * a New Activity Alert has not yet been issued for the streaming job.
     * @param lastNewActivityAlertTimestamp timestamp formatted Strings from the last new activity alert
     * that was issued for each streaming job. May be null if
     * a New Activity Alert has not yet been issued for the streaming job.
     */
    @JsonCreator
    public JsonHealthReportDataCallbackBody(@JsonProperty("reportDate") String reportDate,
        @JsonProperty("jobId") List<Long> jobId, @JsonProperty("externalId") List<String> externalId,
        @JsonProperty("jobStatus") List<String> /*JobStatus*/ jobStatus,
        @JsonProperty("lastNewActivityAlertFrameId") List<BigInteger> lastNewActivityAlertFrameId,
        @JsonProperty("lastNewActivityAlertTimestamp") List<String> lastNewActivityAlertTimestamp) throws DateTimeParseException{
        setReportDate(reportDate);
        this.jobId = jobId;
        this.jobStatus = jobStatus;
        // Leave properties that may be null as an empty List if the passed values are null.
        if ( externalId != null ) {
            this.externalId = externalId;
        }
        if ( lastNewActivityAlertFrameId != null ) {
            this.lastNewActivityAlertFrameId = lastNewActivityAlertFrameId;
        }
        if ( lastNewActivityAlertTimestamp != null ) {
            setLastNewActivityAlertTimestamp(lastNewActivityAlertTimestamp);
        }
    }

    @JsonIgnore
    public String toString() {
        return "reportDate = " + reportDate + ", jobId = " + getJobId() + ", externalId = " + getExternalId() +
            ", jobStatus = " + getJobStatus() + ", lastNewActivityAlertFrameId = " + getLastNewActivityAlertFrameId() +
            ", lastNewActivityAlertTimestamp = " + getLastNewActivityAlertTimeStamp();
    }

}