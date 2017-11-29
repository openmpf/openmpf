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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;;

public class JsonHealthReportDataCallbackBody extends JsonCallbackBody {

    private LocalDateTime reportDate = null;
    /**
     * The date/time that this Health Report is being issued.
     * @return The date/time that this Health Report is being issued, should be returned as a ISO-8601 formatted String
     */
    public String getReportDate() {
        return DateTimeFormatter.ISO_INSTANT.format(reportDate);
    }

    private String /*JobStatus*/ jobStatus;
    public String /*JobStatus*/ getJobStatus() {
        return jobStatus;
    }

    private String lastNewActivityAlertFrameId = null;
    /**
     * Get the last new activity alert frame id from this Health Report.
     * Value stored internally as a String to accommodate for potential for this value to be sized as an unsigned long.
     * Since no arithmetic operations are being performed on this parameter within this class, this is an acceptable data type.
     * @return the last new activity alert frame id from this Health Report as a String. May be null if
     * a New Activity Alert has not been issued for this streaming job.
     */
    public String getLastNewActivityAlertFrameId() { return lastNewActivityAlertFrameId; }

    /**
     * Get the run time for this streaming job, should be returned as a String using ISO-8601 seconds based representation.
     * @return the run time of this streaming job, returned as a String using ISO-8601 seconds based representation. May
     * be null if the run time for this streaming job is not known.
     */
    private Duration jobRunTime = null;
    public String getElapsedTime() {
        if ( jobRunTime != null ) {
            return jobRunTime.toString();
        } else {
            return null;
        }
    }

    /**
     * Constructor
     * @param jobId job id for this streaming job
     * @param externalId external id for this streaming job. May be null if an external id was not defined.
     * @param jobStatus status of this streaming job
     * @param reportDate date/time that this Health Report was issued, should be passed as a ISO-8601 formatted String.
     * @param jobRunTime How long this streaming job has been running, should be passed as a String using ISO-8601 seconds based representation.
     * May be null if the run time for this streaming job is not known.
     * @param lastNewActivityAlertFrameId frame id from the last new activity alert issued for this streaming job. May be null if
     * a New Activity Alert has not been issued for this streaming job.
     */
    @JsonCreator
    public JsonHealthReportDataCallbackBody(@JsonProperty("jobId") long jobId, @JsonProperty("externalId") String externalId,
        @JsonProperty("jobStatus") String /*JobStatus*/ jobStatus,
        @JsonProperty("reportDate") LocalDateTime reportDate,
        @JsonProperty("jobRunTime") Duration jobRunTime,
        @JsonProperty("lastNewActivityAlertFrameId") String lastNewActivityAlertFrameId ) {
        super(jobId,externalId);
        this.reportDate = reportDate;
        this.jobStatus = jobStatus;
        this.jobRunTime = jobRunTime;
        this.lastNewActivityAlertFrameId = lastNewActivityAlertFrameId;
    }

}