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

import static org.mitre.mpf.interop.util.TimeUtils.parseStringAsLocalDateTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.mitre.mpf.interop.exceptions.MpfInteropUsageException;
import org.mitre.mpf.interop.util.TimeUtils;

public class JsonHealthReportCollection {

    static class JsonHealthReport {

        private Long jobId = null;
        private String externalId = null;
        private String jobStatus = null;
        private String jobStatusDetail = null;
        private String activityFrameId = null;
        private String activityTimestamp = null;

        public Long getJobId() { return jobId; }
        public String getExternalId() {
            if ( externalId == null ) {
                return "";
            } else {
                return externalId;
            }
        }
        public String getJobStatus() { return jobStatus; }
        public String getJobStatusDetail() {
            if ( jobStatusDetail == null ) {
                return "";
            } else {
                return jobStatusDetail;
            }
        }
        public String getActivityFrameId() {
            if ( activityFrameId == null ) {
                return "";
            } else {
                return activityFrameId;
            }
        }
        public String getActivityTimestamp() {
            if ( activityTimestamp == null ) {
                return "";
            } else {
                return activityTimestamp;
            }
        }

        @JsonCreator
        public JsonHealthReport(@JsonProperty("jobId") Long jobId, @JsonProperty("externalId") String externalId,
            @JsonProperty("jobStatus") String jobStatus, @JsonProperty("jobStatusDetail") String jobStatusDetail,
            @JsonProperty("activityFrameId") String activityFrameId,
            @JsonProperty("activityTimestamp") String activityTimestamp) throws MpfInteropUsageException {

            this.jobId = jobId;
            this.jobStatus = jobStatus;
            this.jobStatusDetail = jobStatusDetail;
            this.externalId = externalId;
            this.activityFrameId = activityFrameId;
            this.activityTimestamp = activityTimestamp;
        }

        public String toString() {
            return "[ jobId = " + getJobId() + ", externalId = " + getExternalId() +
                   ", jobStatus = " + getJobStatus() + ", jobStatusDetail = " + getJobStatusDetail() +
                   ", activityFrameId = " + getActivityFrameId() + ", activityTimestamp = " + getActivityTimestamp() + "]";
        }
    }

    private LocalDateTime reportDate = null;
    /**
     * The date/time that this callback is being issued.
     * @return The date/time that this callback is being issued. This timestamp will be returned as a String
     * matching the TIMESTAMP_PATTERN, which is currently defined as {@value TimeUtils#TIMESTAMP_PATTERN}
     */
    @JsonGetter("reportDate")
    public String getReportDate() {
        return TimeUtils.getLocalDateTimeAsString(reportDate);
    }
    @JsonSetter("reportDate")
    public void setReportDate(LocalDateTime reportDate) { this.reportDate = reportDate; }

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

    /** Additional job status details for each streaming job in this health report.
     * @return Additional job status details for each streaming job in this health report.
     **/
    @JsonIgnore
    public List<String> getJobStatusDetails() {
        return reports.stream().map(report -> report.getJobStatusDetail()).collect(Collectors.toList()); }

    /** The external IDs that were specified for each of these jobs when they were requested.
     * Note that an externalId may be returned as an empty String if it was not specified for a streaming job.
     * @return external IDs that were specified for each of these jobs when they were requested. Values within the List will be emtpy String if
     * an externalId has not been specified for a streaming job.
     **/
    @JsonIgnore
    public List<String> getExternalIds() {
        return reports.stream().map(report -> report.getExternalId()).collect(Collectors.toList()); }

    /**
     * The activity frame ids for each streaming job in this health report.
     * @return activity frame ids associated with each streaming job. Values within the List will be emtpy String if
     * there has not been any activity in this streaming job.
     */
    @JsonIgnore
    public List<String> getActivityFrameIds() { return reports.stream().map(report -> report.getActivityFrameId()).collect(Collectors.toList()); }

    /**
     * Get the timestamp from the last new Activity Alert received for each streaming job in this health report.
     * @return The last New Activity Alert timestamps for each streaming job. Values within the List may be empty String if
     * a New Activity Alert has not been issued for a streaming job. Otherwise, the timestamp will be returned as a String
     * matching the TIMESTAMP_PATTERN, which is currently defined as {@link TimeUtils#TIMESTAMP_PATTERN}
     */
    @JsonIgnore
    public List<String> getActivityTimestamps() {
        return reports.stream().map(report -> report.getActivityTimestamp()).collect(Collectors.toList());
    }

    /**
     * Constructor used to a create a callback body containing health report for a single streaming job
     * @param reportDate timestamp for this health report callback body. Should not be null.
     * @param jobId job id for this streaming job.
     * @param externalId external id for this streaming job. May be null if an external id was not defined.
     * @param jobStatus status of this streaming job.
     * @param jobStatusDetail additional status detail for this streaming job.
     * @param activityFrameId activity frame id for this streaming job. May be null if
     * there has not been any activity in this streaming job.
     * @param activityTimestamp activity timestamp for this streaming job. May be null if
     * there has not been any activity in this streaming job.
     */
    @JsonIgnore
    public JsonHealthReportCollection(LocalDateTime reportDate, long jobId, String externalId,
                                      String /*JobStatus*/ jobStatus, String jobStatusDetail,
                                      String activityFrameId, LocalDateTime activityTimestamp) throws MpfInteropUsageException {
        this.reportDate = reportDate;
        reports.add(new JsonHealthReport(jobId, externalId, jobStatus, jobStatusDetail, activityFrameId, TimeUtils.getLocalDateTimeAsString(activityTimestamp)));
    }

    /**
     * Constructor used to a create a callback body containing health reports for multiple streaming jobs.
     * @param reportDate timestamp formatted String for this health report callback body. Should not be null.
     * @param reports array of streaming job reports.
     * @exception MpfInteropUsageException is thrown if any List is null or if List sizes are not the same length.
     * DateTimeParseException may be thrown if the reportDate doesn't parse to a valid timestamp. A MpfInteropUsageException may be thrown if
     * any of the activityTimestamps don't parse to a valid timestamp.
     */
    @JsonCreator
    public JsonHealthReportCollection(@JsonProperty("reportDate") String reportDate,
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
     * @param jobStatusDetails additional status details for each streaming job.
     * @param activityFrameIds frame ids from the last new activity alert that was issued for
     * each streaming job. List may contain a null if a New Activity Alert has not yet been issued
     * for the streaming job.
     * @param activityTimestamps timestamp formatted Strings from the last new activity alert
     * that was issued for each streaming job. List may contain a null if a New Activity Alert has
     * not yet been issued for the streaming job.
     * @throws MpfInteropUsageException is thrown if any List is null or if List sizes are not the
     * same length. DateTimeParseException may be thrown if the reportDate doesn't parse to a valid
     * timestamp. A MpfInteropUsageException may be thrown if any of the activityTimestamps
     * don't parse to a valid timestamp.
     */
    @JsonIgnore
    public JsonHealthReportCollection(LocalDateTime reportDate, List<Long> jobIds, List<String> externalIds,
                                      List<String> /*JobStatus*/ jobStatuses, List<String> jobStatusDetails,
                                      List<String> activityFrameIds, List<String> activityTimestamps) throws MpfInteropUsageException, DateTimeParseException {

        // Prep to do some usage error checking and construct the health reports using validated job parameters Lists.
        Map<String,List> jobParameterMap = new HashMap<String,List>();
        jobParameterMap.put("jobIds",jobIds);
        jobParameterMap.put("externalIds",externalIds);
        jobParameterMap.put("jobStatuses",jobStatuses);
        jobParameterMap.put("activityFrameIds",activityFrameIds);
        jobParameterMap.put("activityTimestamps",activityTimestamps);

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
                    reports.add(new JsonHealthReport(jobIds.get(i), externalIds.get(i),
                                jobStatuses.get(i), jobStatusDetails.get(i),
                                activityFrameIds.get(i), activityTimestamps.get(i)));

                }
            }
        }

    }

    @JsonIgnore
    public String toString() {
        return "reportDate = " + reportDate + ", reports = " + reports;
    }

}