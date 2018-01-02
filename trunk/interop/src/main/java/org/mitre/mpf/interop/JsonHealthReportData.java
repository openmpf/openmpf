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
import org.mitre.mpf.interop.exceptions.MpfInteropUsageException;

public class JsonHealthReportData {

    // All timestamps in OpenMPF should adhere to this date/time pattern.
    protected static final String TIMESTAMP_PATTERN = "yyyy-MM-dd kk:mm:ss.S";
    protected static final DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN);

    /**
     * Parse the timestamp String into a LocalDateTime using the date/time pattern adhered to by OpenMPF.
     * @param s timestamp String, may not be null
     * @return timestamp as a LocalDateTime
     * @throws MpfInteropUsageException is thrown if the timestamp String is null. Throws DateTimeParseException if the timestamp String isn't parsable
     * using the date/time pattern adhered to by OpenMPF.
     */
    @JsonIgnore
    public static LocalDateTime parseStringAsLocalDateTime(String s) throws MpfInteropUsageException, DateTimeParseException {
        if ( s == null ) {
            throw new MpfInteropUsageException("Error, timestamp String may not be null");
        } else {
            return timestampFormatter.parse(s, LocalDateTime::from);
        }
    }

    /**
     * Format the LocalDateTime as a timestamp String using the date/time pattern adhered to by OpenMPF.
     * @param timestamp timestamp as a LocalDateTime. May be null.
     * @return timestamp as a String, will be empty String if timestamp is null.
     */
    @JsonIgnore
    public static String getLocalDateTimeAsString(LocalDateTime timestamp) {
        if ( timestamp == null ) {
            return (String) "";
        } else {
            return timestampFormatter.format(timestamp);
        }
    }

    /** The unique job identifier(s) assigned to this job by MPF. */
    private Long jobId;
    @JsonSetter
    public void setJobId(Long jobId) { this.jobId = jobId; }
    @JsonIgnore
    public Long getJobId() { return jobId; }

    /** The external ID that was specified for this job when it was requested.
     * An externalId may be null if not specified for a streaming job. If so, this method will internally store the null as an empty String.
     **/
    private String externalId = null;
    @JsonSetter("externalId")
    public void setExternalId(String externalId) {
        if (externalId == null) {
            this.externalId = "";
        } else {
            this.externalId = externalId;
        }
    }
    @JsonIgnore
    /**
     * @return Get the external ID that was specified for this job when it was requested. Value
     * will be an empty String if the externalId was not specified for this streaming job.
     */
    public String getExternalId() {
        if (externalId == null) {
            return (String) "";
        } else {
            return externalId;
        }
    }

    private String jobStatus = null;
    @JsonSetter("jobStatus")
    public void setJobStatus(String externalId) { this.jobStatus = jobStatus; }
    @JsonIgnore
    public String getJobStatus() { return jobStatus; }

    private String lastActivityFrameId = null;
    @JsonSetter("lastActivityFrameId")
    public void setLastActivityFrameId(String lastActivityFrameId) {
        if ( lastActivityFrameId == null ) {
            this.lastActivityFrameId = "";
        } else {
            this.lastActivityFrameId = lastActivityFrameId;
        }
    }

    /**
     * The frame id from the last new Activity Alerts received for this streaming job.
     * Value is stored internally as a String to accommodate for this value to be sized in the Components as an unsigned long.
     * This data type is acceptable since the WFM will not be doing any processing on this parameter.
     * @return the last New Activity Alert frame id associated with this streaming job. Value will be null if
     * a New Activity Alert has not been issued for this streaming job.
     */
    @JsonIgnore
    public String getLastActivityFrameId() { return lastActivityFrameId; }

    private LocalDateTime lastActivityTimestamp = null;
    /**
     * Get the timestamp from the last new Activity Alert received for this streaming job.
     * @return The last New Activity Alert timestamps for this streaming job. Value may be empty String if
     * a New Activity Alert has not been issued for this streaming job. Otherwise, the timestamp will be returned as a String
     * matching the TIMESTAMP_PATTERN, which is currently defined as {@value #TIMESTAMP_PATTERN}
     */
    @JsonIgnore
    public String getLastActivityTimeStampAsString() {
        if ( lastActivityTimestamp == null ) {
            return "";
        } else {
            return getLocalDateTimeAsString(lastActivityTimestamp);
        }
    }

    /**
     * Set the timestamp from the last new Activity Alert received for this streaming job.
     * Value may be null if a New Activity Alert has not been issued for this streaming job.
     * @param timestamp timestamp as a String using the date/time pattern adhered to by OpenMPF.
     * @throws MpfInteropUsageException is thrown if the timestamp String is null. Throws DateTimeParseException if the timestamp String isn't parsable
     * using the date/time pattern adhered to by OpenMPF.
     */
    @JsonSetter("lastActivityTimestamp")
    public void setLastActivityTimestamp(String timestamp) throws MpfInteropUsageException, DateTimeParseException {
        if (timestamp == null) {
            lastActivityTimestamp = null;
        } else {
            lastActivityTimestamp = parseStringAsLocalDateTime(timestamp);
        }
    }

    /**
     * Constructor used to create a health report for a single job
     * @param jobId job id for this streaming job.
     * @param externalId external id for this streaming job. May be null if an external id was not defined.
     * @param jobStatus status of this streaming job.
     * @param lastActivityFrameId frame id from the last new activity alert that was issued for this streaming job. May be null if
     * a New Activity Alert has not yet been issued for this streaming job.
     * @param lastActivityTimestamp timestamp from the last new activity alert that was issued for this streaming job. May be null if
     * a New Activity Alert has not yet been issued for this streaming job.
     */
    @JsonIgnore
    public JsonHealthReportData(@JsonProperty("jobId") long jobId, @JsonProperty("externalId") String externalId,
        @JsonProperty("jobStatus") String /*JobStatus*/ jobStatus,
        @JsonProperty("lastActivityFrameId") String lastActivityFrameId,
        @JsonProperty("lastActivityTimestamp") LocalDateTime lastActivityTimestamp) {

        this.jobId = jobId;
        this.jobStatus = jobStatus;
        this.externalId = externalId;
        this.lastActivityFrameId = lastActivityFrameId;
        this.lastActivityTimestamp = lastActivityTimestamp;
    }

    /**
     * Constructor used to create a health report for a single job
     * @param jobId job id for this streaming job.
     * @param externalId external id for this streaming job. May be null if an external id was not defined.
     * @param jobStatus status of this streaming job.
     * @param lastActivityFrameId frame id from the last new activity alert that was issued for this streaming job. May be null if
     * a New Activity Alert has not yet been issued for this streaming job.
     * @param lastActivityTimestamp timestamp from the last new activity alert that was issued for this streaming job. May be null if
     * a New Activity Alert has not yet been issued for this streaming job.
     */
    @JsonCreator
    public JsonHealthReportData(@JsonProperty("jobId") long jobId, @JsonProperty("externalId") String externalId,
        @JsonProperty("jobStatus") String /*JobStatus*/ jobStatus,
        @JsonProperty("lastActivityFrameId") String lastActivityFrameId,
        @JsonProperty("lastActivityTimestamp") String lastActivityTimestamp) throws MpfInteropUsageException {

        this.jobId = jobId;
        this.jobStatus = jobStatus;
        this.externalId = externalId;
        this.lastActivityFrameId = lastActivityFrameId;
        setLastActivityTimestamp(lastActivityTimestamp);
    }

    @JsonIgnore
    public String toString() {
        return "jobId = " + getJobId() + ", externalId = " + getExternalId() +
            ", jobStatus = " + getJobStatus() + ", lastActivityFrameId = " + getLastActivityFrameId() +
            ", lastActivityTimestamp = " + getLocalDateTimeAsString(lastActivityTimestamp);
    }

}
