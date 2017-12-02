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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    /** The internal job identifier(s) assigned to these jobs by MPF. May be 1 to many. */
    private List<Long> jobIds = null;
    public List<Long> getJobIds() { return jobIds; }

    /** The external ID(s) that were provided for these jobs when they were was initially submitted. Note that some of these values may be null. */
    private List<String> externalids = null;
    public List<String> getExternalIds() { return externalids; }

    private List<String> /*JobStatus*/ jobStatuses = null;
    public List<String> /*JobStatus*/ getJobStatuses() {
        return jobStatuses;
    }

    private List<String> lastNewActivityAlertFrameIds = null;
    /**
     * The frame ids from the last new Activity Alerts received for these streaming jobs.
     * Value stored internally as a String to accommodate for this value to be sized in the Components as an unsigned long.
     * Since no arithmetic operations are being performed on this parameter within this class, this is an acceptable data type.
     * @return the last New Activity Alert frame ids associated with each streaming job. Values may be null if
     * a New Activity Alert has not been issued for a streaming job.
     */
    public List<String> getLastNewActivityAlertFrameIds() { return lastNewActivityAlertFrameIds; }

    private List<LocalDateTime> lastNewActivityAlertTimestamps = null;
    /**
     * The timestamp from the last new Activity Alert received for each streaming job.
     * @return The last New Activity Alert timestamps for each streaming. Values may be null if
     * a New Activity Alert has not been issued for a streaming job. If not null, the timestamp will be returned as a String
     * matching the TIMESTAMP_PATTERN, which is currently defined as {@value #TIMESTAMP_PATTERN}
     */
    public List<String> getLastNewActivityAlertTimeStamps() {
        return lastNewActivityAlertTimestamps.stream().map(timestamp -> getTimestampAsString(timestamp)).collect(
            Collectors.toList());
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
        jobIds = new ArrayList<>();
        jobIds.add(jobId);
        externalids = new ArrayList<>();
        externalids.add(externalId);
        jobStatuses = new ArrayList<>();
        jobStatuses.add(jobStatus);
        lastNewActivityAlertFrameIds = new ArrayList<>();
        lastNewActivityAlertFrameIds.add(lastNewActivityAlertFrameId);
        lastNewActivityAlertTimestamps = new ArrayList<>();
        lastNewActivityAlertTimestamps.add(lastNewActivityAlertTimestamp);
    }

    /**
     * Constructor used to create a health report for multiple jobs
     * @param reportDate timestamp for this Health Report.
     * @param jobIds job ids being reported on.
     * @param externalIds external ids for each streaming job. May be null if an external id was not defined.
     * @param jobStatuses status of each streaming job.
     * @param lastNewActivityAlertFrameIds frame ids from the last new activity alert that was issued for each streaming job. May be null if
     * a New Activity Alert has not yet been issued for the streaming job.
     * @param lastNewActivityAlertTimestamps timestamps from the last new activity alert that was issued for each streaming job. May be null if
     * a New Activity Alert has not yet been issued for the streaming job.
     */
    @JsonCreator
    public JsonHealthReportDataCallbackBody(@JsonProperty("reportDate") LocalDateTime reportDate,
        @JsonProperty("jobIds") List<Long> jobIds, @JsonProperty("externalIds") List<String> externalIds,
        @JsonProperty("jobStatuses") List<String> /*JobStatus*/ jobStatuses,
        @JsonProperty("lastNewActivityAlertFrameIds") List<String> lastNewActivityAlertFrameIds,
        @JsonProperty("lastNewActivityAlertTimestamps") List<LocalDateTime> lastNewActivityAlertTimestamps) {
        this.reportDate = reportDate;
        this.jobIds = jobIds;
        this.externalids = externalIds;
        this.jobStatuses = jobStatuses;
        this.lastNewActivityAlertFrameIds = lastNewActivityAlertFrameIds;
        this.lastNewActivityAlertTimestamps = lastNewActivityAlertTimestamps;
    }

}