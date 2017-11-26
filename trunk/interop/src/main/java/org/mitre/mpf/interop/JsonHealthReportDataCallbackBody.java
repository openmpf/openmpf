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
import java.util.Date;

public class JsonHealthReportDataCallbackBody extends JsonCallbackBody {

    private Date reportDate = null;
    public Date getReportDate() {
        return reportDate;
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
     * @return the last new activity alert frame id from this Health Report as a String.
     */
    public String getLastNewActivityAlertFrameId() { return lastNewActivityAlertFrameId; }

    /**
     * Get the elapsed time of this streaming job, in seconds.
     * Preferred implementation would be to define this parameter as java.timing.Duration, but usage of Java8 isn't supported in this module.
     * @return the elapsed time of this streaming job, in seconds.
     */
    private Long elapsedTime = null;
    public Long getElapsedTime() { return elapsedTime; }

    /**
     * Constructor
     * @param jobId job id for this streaming job
     * @param reportDate date/time that this Health Report was issued
     * @param jobStatus status of this streaming job
     * @param elapsedTime duration of this streaming job, in seconds. May be null if duration of this job is not available.
     * @param lastNewActivityAlertFrameId frame id from the last new activity alert issued for this streaming job. May be null if not applicable.
     */
    @JsonCreator
    public JsonHealthReportDataCallbackBody(@JsonProperty("jobId") long jobId, @JsonProperty("externalId") String externalId,
        @JsonProperty("reportDate") Date reportDate, @JsonProperty("jobStatus") String /*JobStatus*/ jobStatus,
        @JsonProperty("elapsedTime") Long elapsedTime,
        @JsonProperty("lastNewActivityAlertFrameId") String lastNewActivityAlertFrameId ) {
        super(jobId,externalId);
        this.reportDate = reportDate;
        this.jobStatus = jobStatus;
        this.elapsedTime = elapsedTime;
        this.lastNewActivityAlertFrameId = lastNewActivityAlertFrameId;
    }

}