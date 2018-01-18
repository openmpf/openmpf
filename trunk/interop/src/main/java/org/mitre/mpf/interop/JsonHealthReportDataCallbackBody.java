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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.mitre.mpf.interop.exceptions.MpfInteropUsageException;

public class JsonHealthReportDataCallbackBody {

    // All timestamps in OpenMPF should adhere to this date/time pattern.
    public static final String TIMESTAMP_PATTERN = "yyyy-MM-dd kk:mm:ss.S";
    // The timestampFormatter must remain as a static, or the jackson conversion to JSON will no longer work
    public static final DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN);

    /**
     * Parse the timestamp String into a LocalDateTime using the date/time pattern adhered to by OpenMPF.
     * @param timestampStr timestamp String, may not be null
     * @return timestamp as a LocalDateTime
     * @throws MpfInteropUsageException is thrown if the timestamp String is null. Throws DateTimeParseException if the timestamp String isn't parsable
     * using the date/time pattern adhered to by OpenMPF.
     */
    private LocalDateTime parseStringAsLocalDateTime(String timestampStr) throws MpfInteropUsageException, DateTimeParseException {
        if ( timestampStr == null ) {
            throw new MpfInteropUsageException("Error, timestamp String may not be null");
        } else {
            return timestampFormatter.parse(timestampStr, LocalDateTime::from);
        }
    }

    /**
     * Format the LocalDateTime as a timestamp String using the date/time pattern adhered to by OpenMPF.
     * @param timestamp timestamp as a LocalDateTime. May be null.
     * @return timestamp as a String, will be empty String if timestamp is null.
     */
    private String getLocalDateTimeAsString(LocalDateTime timestamp) {
        if ( timestamp == null ) {
            return "";
        } else {
            return timestampFormatter.format(timestamp);
        }
    }

    static class JsonHealthReport {

        private Long jobId = null;
        private String externalId = null;
        private String jobStatus = null;
        private String lastActivityFrameId = null;
        private String lastActivityTimestamp = null;

        public Long getJobId() { return jobId; }
        public String getExternalId() {
            if ( externalId == null ) {
                return "";
            } else {
                return externalId;
            }
        }
        public String getJobStatus() { return jobStatus; }
        public String getLastActivityFrameId() {
            if ( lastActivityFrameId == null ) {
                return "";
            } else {
                return lastActivityFrameId;
            }
        }
        public String getLastActivityTimestamp() {
            if ( lastActivityTimestamp == null ) {
                return "";
            } else {
                return lastActivityTimestamp;
            }
        }

        @JsonCreator
        public JsonHealthReport(@JsonProperty("jobId") Long jobId, @JsonProperty("externalId") String externalId,
            @JsonProperty("jobStatus") String jobStatus,
            @JsonProperty("lastActivityFrameId") String lastActivityFrameId,
            @JsonProperty("lastActivityTimestamp") String lastActivityTimestamp) throws MpfInteropUsageException {

            this.jobId = jobId;
            this.jobStatus = jobStatus;
            this.externalId = externalId;
            this.lastActivityFrameId = lastActivityFrameId;
            this.lastActivityTimestamp = lastActivityTimestamp;
        }

        public String toString() {
            return "[ jobId = " + getJobId() + ", externalId = " + getExternalId() + ", jobStatus = " + getJobStatus() +
                   ", lastActivityFrameId = " + getLastActivityFrameId() + ", lastActivityTimestamp = " + getLastActivityTimestamp() + "]";
        }
    }

    private LocalDateTime reportDate = null;
    /**
     * The date/time that this callback is being issued.
     * @return The date/time that this callback is being issued. This timestamp will be returned as a String
     * matching the TIMESTAMP_PATTERN, which is currently defined as {@value #TIMESTAMP_PATTERN}
     */
    @JsonGetter("reportDate")
    public String getReportDate() {
        return timestampFormatter.format(reportDate);
    }
    @JsonSetter("reportDate")
    @JsonFormat(pattern = TIMESTAMP_PATTERN)
    public void setReportDate(String timestampStr) throws DateTimeParseException, MpfInteropUsageException {
        this.reportDate = parseStringAsLocalDateTime(timestampStr);
    }

    /** The health reports for the active streaming jobs in OpenMPF. May be 0 to many. */
    private List<JsonHealthReport> reports = new ArrayList<>();
    @JsonSetter
    public void setReports(List<JsonHealthReport> reports) { this.reports = reports; }
    @JsonGetter("reports")
    public List<JsonHealthReport> getReports() { return reports; }

    /**
     * The unique job ids assigned by OpenMPF to each streaming job in this health report.
     * @return unique job ids assigned by OpenMPF to each streaming job in this health report.
     */
    @JsonIgnore
    public List<Long> getJobIds() { return reports.stream().map(report -> report.getJobId()).collect(Collectors.toList());  }

    /** The job status of each streaming job in this health report.
     * @return job status of each streaming job in this health report.
     **/
    @JsonIgnore
    public List<String> getJobStatuses() {
        return reports.stream().map(report -> report.getJobStatus()).collect(Collectors.toList()); }


    /** The external IDs that were specified for each of these jobs when they were requested.
     * Note that an externalId may be returned as an empty String if it was not specified for a streaming job.
     * @return external IDs that were specified for each of these jobs when they were requested. Values within the List will be emtpy String if
     * an externalId has not been specified for a streaming job.
     **/
    @JsonIgnore
    public List<String> getExternalIds() {
        return reports.stream().map(report -> report.getExternalId()).collect(Collectors.toList()); }

    /**
     * The frame ids from the last new Activity Alerts received for each streaming job in this health report.
     * @return the last New Activity Alert frame ids associated with each streaming job. Values within the List will be emtpy String if
     * a New Activity Alert has not been issued for a streaming job.
     */
    @JsonIgnore
    public List<String> getLastActivityFrameIds() { return reports.stream().map(report -> report.getLastActivityFrameId()).collect(Collectors.toList()); }

    /**
     * Get the timestamp from the last new Activity Alert received for each streaming job in this health report.
     * @return The last New Activity Alert timestamps for each streaming job. Values within the List may be empty String if
     * a New Activity Alert has not been issued for a streaming job. Otherwise, the timestamp will be returned as a String
     * matching the TIMESTAMP_PATTERN, which is currently defined as {@value #TIMESTAMP_PATTERN}
     */
    @JsonIgnore
    public List<String> getLastActivityTimestamps() {
        return reports.stream().map(report -> report.getLastActivityTimestamp()).collect(Collectors.toList());
    }

    /**
     * Constructor used to a create a callback body containing health report for a single streaming job
     * @param reportDate timestamp for this health report callback body. Should not be null.
     * @param jobId job id for this streaming job.
     * @param externalId external id for this streaming job. May be null if an external id was not defined.
     * @param jobStatus status of this streaming job.
     * @param lastActivityFrameId frame id from the last new activity alert that was issued for this streaming job. May be null if
     * a New Activity Alert has not yet been issued for this streaming job.
     * @param lastActivityTimestamp timestamp from the last new activity alert that was issued for this streaming job. May be null if
     * a New Activity Alert has not yet been issued for this streaming job.
     */
    @JsonIgnore
    public JsonHealthReportDataCallbackBody(LocalDateTime reportDate, long jobId, String externalId, String /*JobStatus*/ jobStatus,
        String lastActivityFrameId, LocalDateTime lastActivityTimestamp) throws MpfInteropUsageException {
        this.reportDate = reportDate;
        reports.add(new JsonHealthReport(jobId, externalId, jobStatus, lastActivityFrameId, getLocalDateTimeAsString(lastActivityTimestamp)));
    }

    /**
     * Constructor used to a create a callback body containing health reports for multiple streaming jobs.
     * @param reportDate timestamp formatted String for this health report callback body. Should not be null.
     * @param reports array of streaming job reports.
     * @exception MpfInteropUsageException is thrown if any List is null or if List sizes are not the same length.
     * DateTimeParseException may be thrown if the reportDate doesn't parse to a valid timestamp. A MpfInteropUsageException may be thrown if
     * any of the lastActivityTimestamps don't parse to a valid timestamp.
     */
    @JsonCreator
    public JsonHealthReportDataCallbackBody(@JsonProperty("reportDate") String reportDate,
                                            @JsonProperty("reports") List<JsonHealthReport> reports) throws MpfInteropUsageException {
        this.reportDate = parseStringAsLocalDateTime(reportDate);
        this.reports = reports;
    }

    /**
     * Constructor used to a create a callback body containing health reports for multiple streaming jobs.
     *
     * @param reportDate timestamp formatted String for this health report callback body. Should not be null.
     * @param jobIds job ids being reported on.
     * @param externalIds external ids for each streaming job. List may contain a null if an
     * external id was not defined for the job.
     * @param jobStatuses status of each streaming job.
     * @param lastActivityFrameIds frame ids from the last new activity alert that was issued for
     * each streaming job. List may contain a null if a New Activity Alert has not yet been issued
     * for the streaming job.
     * @param lastActivityTimestamps timestamp formatted Strings from the last new activity alert
     * that was issued for each streaming job. List may contain a null if a New Activity Alert has
     * not yet been issued for the streaming job.
     * @throws MpfInteropUsageException is thrown if any List is null or if List sizes are not the
     * same length. DateTimeParseException may be thrown if the reportDate doesn't parse to a valid
     * timestamp. A MpfInteropUsageException may be thrown if any of the lastActivityTimestamps
     * don't parse to a valid timestamp.
     */
    @JsonIgnore
    public JsonHealthReportDataCallbackBody(LocalDateTime reportDate, List<Long> jobIds, List<String> externalIds, List<String> /*JobStatus*/ jobStatuses,
        List<String> lastActivityFrameIds, List<String> lastActivityTimestamps) throws MpfInteropUsageException, DateTimeParseException {

        // Prep to do some usage error checking and construct the health reports using validated job parameters Lists.
        Map<String,List> jobParameterMap = new HashMap<String,List>();
        jobParameterMap.put("jobIds",jobIds);
        jobParameterMap.put("externalIds",externalIds);
        jobParameterMap.put("jobStatuses",jobStatuses);
        jobParameterMap.put("lastActivityFrameIds",lastActivityFrameIds);
        jobParameterMap.put("lastActivityTimestamps",lastActivityTimestamps);

        // Do some error checking against the specified job parameters. First, make sure that none of the job parameter Lists are null.
        // Second, make sure that the job parameter Lists are all the same size.
        if ( jobParameterMap.values().stream().filter( v -> v == null ).count() > 0 ) {
            // Found that at least one of the job parameter Lists passed to this method were null. Throw an exception which identifies the Lists that are null for the caller.
            throw new MpfInteropUsageException("Error: the following Lists can't be null: " +
                jobParameterMap.entrySet().stream().filter( m -> m.getValue()==null).map( m -> m.getKey()).collect(Collectors.joining(",")));
        } else {
            // all job parameters Lists should be the same size as the number of jobIds.
            int numJobIds = jobIds.size();
            if ( jobParameterMap.values().stream().filter( v -> v.size() != numJobIds ).count() > 0 ) {
                // Throw an exception identifying for the caller the job parameter Lists whose size does not match the number of jobIds.
                throw new MpfInteropUsageException("Error: the following Lists should be of size " + numJobIds +" but are not: " +
                    jobParameterMap.entrySet().stream().filter( m ->  m.getValue().size() != numJobIds ).map( m -> m.getKey() ).collect(Collectors.joining(",")));

            } else if ( reportDate == null ) {
                throw new MpfInteropUsageException("Error: reportDate should not be null");

            } else {

                // If we get to here, then all job parameter Lists passed the error check. Construct this health report.
                this.reportDate = reportDate;

                // Add each job as a separate health report.
                for ( int i=0; i<numJobIds; i++ ) {
                    reports.add(new JsonHealthReport(jobIds.get(i), externalIds.get(i), jobStatuses.get(i),
                        lastActivityFrameIds.get(i), lastActivityTimestamps.get(i)));

                }
            }
        }

    }

    @JsonIgnore
    public String toString() {
        return "reportDate = " + reportDate + ", reports = " + reports;
    }

}