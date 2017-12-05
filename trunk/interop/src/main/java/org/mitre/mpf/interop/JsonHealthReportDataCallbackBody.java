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
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;;

public class JsonHealthReportDataCallbackBody {

    private LocalDateTime reportDate = null;
    /**
     * The date/time that this Health Report is being issued.
     * @return The date/time that this Health Report is being issued. This timestamp will be returned as a String
     * matching the TIMESTAMP_PATTERN, which is currently defined as {@value #TIMESTAMP_PATTERN}
     */
    public String getReportDate() {
        return timestampFormatter.format(reportDate);
    }

    public static final String TIMESTAMP_PATTERN = "yyyy-MM-dd kk:mm:ss.S";
    public static final DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN);
    public static String formatLocalDateTimeAsString(LocalDateTime d) { return timestampFormatter.format(d); }
    public static LocalDateTime parseStringAsLocalDateTime(String s) throws DateTimeParseException {
        return (LocalDateTime) timestampFormatter.parse(s);
    }

    /** The internal job identifier(s) assigned to these jobs by MPF. May be 1 to many. */
    private List<Long> jobId = null;
    public List<Long> getJobId() { return jobId; }
    public String getJobIdsAsDelimitedString(String delimiter) { return jobId.stream().map(jobId -> jobId.toString()).collect(Collectors.joining(delimiter));}

    /** The external ID(s) that were specified for these jobs when they were requested. Note that an externalId may be null if not specified for a streaming job.
    **/
    private List<String> externalid = null;
    public List<String> getExternalId() { return externalid; }
    public String getExternalIdsAsDelimitedString(String delimiter) { return externalid.stream().map(externalId -> externalId.toString()).collect(Collectors.joining(delimiter));}

    private List<String> /*JobStatus*/ jobStatus = null;
    public List<String> /*JobStatus*/ getJobStatus() {
        return jobStatus;
    }
    public String getJobStatusAsDelimitedString(String delimiter) { return jobStatus.stream().map(jobStatus -> jobStatus.toString()).collect(Collectors.joining(delimiter));}

    private List<String> lastNewActivityAlertFrameId = null;
    /**
     * The frame ids from the last new Activity Alerts received for these streaming jobs.
     * Value stored internally as a String to accommodate for this value to be sized in the Components as an unsigned long.
     * Since no arithmetic operations are being performed on this parameter within this class, this is an acceptable data type.
     * @return the last New Activity Alert frame ids associated with each streaming job. Values within the List may be null if
     * a New Activity Alert has not been issued for a streaming job.
     */
    public List<String> getLastNewActivityAlertFrameId() { return lastNewActivityAlertFrameId; }
    public String getLastNewActivityAlertFrameIdAsDelimitedString(String delimiter) {
        return lastNewActivityAlertFrameId.stream().map(lastNewActivityAlertFrameId -> lastNewActivityAlertFrameId.toString()).collect(Collectors.joining(delimiter));
    }

    private List<LocalDateTime> lastNewActivityAlertTimestamp = null;
    /**
     * The timestamp from the last new Activity Alert received for each streaming job.
     * @return The last New Activity Alert timestamps for each streaming. Values within the List may be null if
     * a New Activity Alert has not been issued for a streaming job. If not null, the timestamp will be returned as a String
     * matching the TIMESTAMP_PATTERN, which is currently defined as {@value #TIMESTAMP_PATTERN}
     */
    public List<String> getLastNewActivityAlertTimeStamp() {
        return lastNewActivityAlertTimestamp.stream().map(timestamp -> getTimestampAsString(timestamp)).collect(
            Collectors.toList());
    }

    /**
     * Same as method {@link #getLastNewActivityAlertTimeStamp()}, except the timestamps are returned as a delimited string.
     * @param delimiter delimiter to be used in the returned string.
     * @return data returned as a delimited string.
     */
    public String getLastNewActivityAlertTimeStampAsDelimitedString(String delimiter) {
        return getLastNewActivityAlertTimeStamp().stream().map(timestamp -> timestamp.toString()).collect(Collectors.joining(delimiter));
    }

    private String getTimestampAsString(LocalDateTime timestamp) {
        if ( timestamp == null ) {
            return (String) null;
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
    @JsonCreator
    public JsonHealthReportDataCallbackBody(@JsonProperty("reportDate") LocalDateTime reportDate,
        @JsonProperty("jobId") long jobId, @JsonProperty("externalId") String externalId,
        @JsonProperty("jobStatus") String /*JobStatus*/ jobStatus,
        @JsonProperty("lastNewActivityAlertFrameId") String lastNewActivityAlertFrameId,
        @JsonProperty("lastNewActivityAlertTimestamp") LocalDateTime lastNewActivityAlertTimestamp) {
        this.reportDate = reportDate;
        this.jobId = new ArrayList<>();
        this.jobId.add(jobId);
        this.externalid = new ArrayList<>();
        this.externalid.add(externalId);
        this.jobStatus = new ArrayList<>();
        this.jobStatus.add(jobStatus);
        this.lastNewActivityAlertFrameId = new ArrayList<>();
        this.lastNewActivityAlertFrameId.add(lastNewActivityAlertFrameId);
        this.lastNewActivityAlertTimestamp = new ArrayList<>();
        this.lastNewActivityAlertTimestamp.add(lastNewActivityAlertTimestamp);
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
    @JsonCreator
    public JsonHealthReportDataCallbackBody(@JsonProperty("reportDate") LocalDateTime reportDate,
        @JsonProperty("jobId") List<Long> jobId, @JsonProperty("externalId") List<String> externalId,
        @JsonProperty("jobStatus") List<String> /*JobStatus*/ jobStatus,
        @JsonProperty("lastNewActivityAlertFrameId") List<String> lastNewActivityAlertFrameId,
        @JsonProperty("lastNewActivityAlertTimestamp") List<LocalDateTime> lastNewActivityAlertTimestamp) {
        this.reportDate = reportDate;
        this.jobId = jobId;
        this.externalid = externalId;
        this.jobStatus = jobStatus;
        this.lastNewActivityAlertFrameId = lastNewActivityAlertFrameId;
        this.lastNewActivityAlertTimestamp = lastNewActivityAlertTimestamp;
    }

    public void print(PrintStream out) {
        out.println("reportDate = "+reportDate);
        out.println("jobId = "+getJobIdsAsDelimitedString(","));
        out.println("externalid = "+getExternalIdsAsDelimitedString(","));
        out.println("jobStatus = "+getJobStatusAsDelimitedString(","));
        out.println("lastNewActivityAlertFrameId = "+getLastNewActivityAlertFrameIdAsDelimitedString(","));
        out.println("lastNewActivityAlertTimestamp = "+getLastNewActivityAlertTimeStampAsDelimitedString(","));
    }

}