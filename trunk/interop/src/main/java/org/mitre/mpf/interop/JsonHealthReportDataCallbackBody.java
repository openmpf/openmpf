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

import static org.mitre.mpf.interop.JsonHealthReportData.TIMESTAMP_PATTERN;
import static org.mitre.mpf.interop.JsonHealthReportData.parseStringAsLocalDateTime;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.mitre.mpf.interop.exceptions.MpfInteropUsageException;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class JsonHealthReportDataCallbackBody {

    // All timestamps in OpenMPF should adhere to this date/time pattern.
    public static final String TIMESTAMP_PATTERN = "yyyy-MM-dd kk:mm:ss.S";
    public static final DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN);

    /**
     * Parse the timestamp String into a LocalDateTime using the date/time pattern adhered to by OpenMPF.
     * @param s timestamp String, may not be null
     * @return timestamp as a LocalDateTime
     * @throws MpfInteropUsageException is thrown if the timestamp String is null. Throws DateTimeParseException if the timestamp String isn't parsable
     * using the date/time pattern adhered to by OpenMPF.
     */
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
    public static String getLocalDateTimeAsString(LocalDateTime timestamp) {
        if ( timestamp == null ) {
            return (String) "";
        } else {
            return timestampFormatter.format(timestamp);
        }
    }

    static class Report {
        private Long jobId = null;
        private String externalId = null;
        private String jobStatus = null;
        private String lastActivityFrameId = null;
        private String lastActivityTimestamp = null;

        public Long getJobId() { return jobId; }
        public String getExternalId() { return externalId; }
        public String getJobStatus() { return jobStatus; }
        public String getLastActivityFrameId() { return lastActivityFrameId; }
        public String getLastActivityTimestamp() { return lastActivityTimestamp; }

        @JsonCreator
        public Report(@JsonProperty("jobId") Long jobId, @JsonProperty("externalId") String externalId,
            @JsonProperty("jobStatus") String jobStatus,
            @JsonProperty("lastActivityFrameId") String lastActivityFrameId,
            @JsonProperty("lastActivityTimestamp") String lastActivityTimestamp) throws MpfInteropUsageException {

            this.jobId = jobId;
            this.jobStatus = jobStatus;
            this.externalId = externalId;
            this.lastActivityFrameId = lastActivityFrameId;
            this.lastActivityTimestamp = lastActivityTimestamp;
        }
    }

    private LocalDateTime reportDate = null;
    /**
     * The date/time that this Health Report is being issued.
     * @return The date/time that this Health Report is being issued. This timestamp will be returned as a String
     * matching the TIMESTAMP_PATTERN, which is currently defined as {@value JsonHealthReportData#TIMESTAMP_PATTERN}
     */
    @JsonGetter("reportDate")
    public String getReportDate() {
        return JsonHealthReportData.timestampFormatter.format(reportDate);
    }
    @JsonSetter("reportDate")
    @JsonFormat(pattern = TIMESTAMP_PATTERN)
    public void setReportDate(String s) throws DateTimeParseException, MpfInteropUsageException {
        this.reportDate = parseStringAsLocalDateTime(s);
    }

/*
    */
/** The Health Reports for the active streaming jobs in OpenMPF. May be 0 to many. *//*

    private List<JsonHealthReportData> reports = new ArrayList<>();
    @JsonSetter
    public void setReports(List<JsonHealthReportData> reports) { this.reports = reports; }
    @JsonGetter("reports")
    public List<JsonHealthReportData> getReports() { return reports; }
*/

    /** The Health Reports for the active streaming jobs in OpenMPF. May be 0 to many. */
    private List<Report> reports = new ArrayList<>();
    @JsonSetter
    public void setReports(List<Report> reports) { this.reports = reports; }
    @JsonGetter("reports")
    public List<Report> getReports() { return reports; }

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
        return reports.stream().map(report -> {
            String externalId = report.getExternalId();
            if ( externalId == null ) {
                return "";
            } else {
                return externalId;
            }
        }).collect(Collectors.toList()); }

    /**
     * The frame ids from the last new Activity Alerts received for each streaming job in this health report.
     * Value stored internally as a String to accommodate for this value to be sized in the Components as an unsigned long.
     * This data type is acceptable since the WFM will not be doing any processing on this parameter.
     * @return the last New Activity Alert frame ids associated with each streaming job. Values within the List will be emtpy String if
     * a New Activity Alert has not been issued for a streaming job.
     */
    @JsonIgnore
    public List<String> getLastActivityFrameIds() { return reports.stream().map(report -> {
        String frameId = report.getLastActivityFrameId();
        if ( frameId == null ) {
            return "";
        } else {
            return frameId;
        }}).collect(Collectors.toList()); }

    /**
     * Get the timestamp from the last new Activity Alert received for each streaming job in this health report.
     * @return The last New Activity Alert timestamps for each streaming job. Values within the List may be empty String if
     * a New Activity Alert has not been issued for a streaming job. Otherwise, the timestamp will be returned as a String
     * matching the TIMESTAMP_PATTERN, which is currently defined as {@value JsonHealthReportData#TIMESTAMP_PATTERN}
     */
    @JsonIgnore
    public List<String> getLastActivityTimestamps() {
        return reports.stream().map(report -> {
            String timestamp = report.getLastActivityTimestamp();
            if ( timestamp == null ) {
                return "";
            } else {
                return timestamp;
            }
        }).collect(Collectors.toList());
    }
/*

    // common method for building health report data callable from all Constructors that build from Lists.
    private void createJobHealthReports(@JsonProperty("reportDate") String reportDate,
        @JsonProperty("jobIds") List<Long> jobIds, @JsonProperty("externalIds") List<String> externalIds,
        @JsonProperty("jobStatuses") List<String> */
/*JobStatus*//*
 jobStatuses,
        @JsonProperty("lastActivityFrameIds") List<String> lastActivityFrameIds,
        @JsonProperty("lastActivityTimestamps") List<String> lastActivityTimestamps) throws MpfInteropUsageException {

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
            } else {

                // If we get to here, then all job parameter Lists passed the error check. Construct this health report.
                this.reportDate = parseStringAsLocalDateTime(reportDate);

                // Add each job as a separate health report.
                for ( int i=0; i<numJobIds; i++ ) {
                    reports.add(new JsonHealthReportData (jobIds.get(i), externalIds.get(i), jobStatuses.get(i),
                                lastActivityFrameIds.get(i), lastActivityTimestamps.get(i)));
                }
            }
        }

    }
*/

    /**
     * Constructor used to create a health report for a single streaming job
     * @param reportDate timestamp for this Health Report.
     * @param jobId job id for this streaming job.
     * @param externalId external id for this streaming job. May be null if an external id was not defined.
     * @param jobStatus status of this streaming job.
     * @param lastActivityFrameId frame id from the last new activity alert that was issued for this streaming job. May be null if
     * a New Activity Alert has not yet been issued for this streaming job.
     * @param lastActivityTimestamp timestamp from the last new activity alert that was issued for this streaming job. May be null if
     * a New Activity Alert has not yet been issued for this streaming job.
     */
    @JsonIgnore
    public JsonHealthReportDataCallbackBody(@JsonProperty("reportDate") LocalDateTime reportDate,
        @JsonProperty("jobId") long jobId, @JsonProperty("externalId") String externalId,
        @JsonProperty("jobStatus") String /*JobStatus*/ jobStatus,
        @JsonProperty("lastActivityFrameId") String lastActivityFrameId,
        @JsonProperty("lastActivityTimestamp") LocalDateTime lastActivityTimestamp) throws MpfInteropUsageException {

        this.reportDate = reportDate;
/*
        reports.add(new JsonHealthReportData (jobId, externalId, jobStatus, lastActivityFrameId, lastActivityTimestamp));
*/
        reports.add(new Report(jobId, externalId, jobStatus, lastActivityFrameId, getLocalDateTimeAsString(lastActivityTimestamp)));
    }

/*
    */
/**
     * Constructor used to create a health report containing multiple streaming jobs.
     * @param reportDate timestamp for this Health Report.
     * @param jobIds job ids being reported on.
     * @param externalIds external ids for each streaming job. May be null if an external id was not defined.
     * @param jobStatuses status of each streaming job.
     * @param lastActivityFrameIds frame ids from the last new activity alert that was issued for each streaming job. List may contain null if
     * a New Activity Alert has not yet been issued for the streaming job.
     * @param lastActivityTimestamps timestamps from the last new activity alert that was issued for each streaming job. List may contain null if
     * a New Activity Alert has not yet been issued for the streaming job.
     * @exception MpfInteropUsageException is thrown if any List is null or if List sizes are not the same length.
     *//*

    @JsonIgnore
    public JsonHealthReportDataCallbackBody(@JsonProperty("reportDate") LocalDateTime reportDate,
        @JsonProperty("jobIds") List<Long> jobIds, @JsonProperty("externalIds") List<String> externalIds,
        @JsonProperty("jobStatuses") List<String> */
/*JobStatus*//*
 jobStatuses,
        @JsonProperty("lastActivityFrameIds") List<String> lastActivityFrameIds,
        @JsonProperty("lastActivityTimestamps") List<LocalDateTime> lastActivityTimestamps) throws MpfInteropUsageException {

        // Call common method to create health reports for all specified jobs.
        createJobHealthReports(reportDate, jobIds, externalIds, jobStatuses, lastActivityFrameIds, lastActivityTimestamps);

    }
*/

/*
    */
/**
     * Constructor used to create a health report containing multiple streaming jobs.
     * @param reportDate timestamp formatted String for this Health Report.
     * @param reports array of streaming job reports.
     * @exception MpfInteropUsageException is thrown if any List is null or if List sizes are not the same length.
     * DateTimeParseException may be thrown if the reportDate doesn't parse to a valid timestamp. A MpfInteropUsageException may be thrown if
     * any of the lastActivityTimestamps don't parse to a valid timestamp.
     *//*

    @JsonIgnore
    public JsonHealthReportDataCallbackBody(@JsonProperty("reportDate") String reportDate,
                                            @JsonProperty("reports") List<JsonHealthReportData> reports) throws MpfInteropUsageException {
        this.reportDate = parseStringAsLocalDateTime(reportDate);
        this.reports = reports;
    }
*/

    /**
     * Constructor used to create a health report containing multiple streaming jobs.
     * @param reportDate timestamp formatted String for this Health Report.
     * @param reports array of streaming job reports.
     * @exception MpfInteropUsageException is thrown if any List is null or if List sizes are not the same length.
     * DateTimeParseException may be thrown if the reportDate doesn't parse to a valid timestamp. A MpfInteropUsageException may be thrown if
     * any of the lastActivityTimestamps don't parse to a valid timestamp.
     */
    @JsonCreator
    public JsonHealthReportDataCallbackBody(@JsonProperty("reportDate") String reportDate,
                                            @JsonProperty("reports") List<Report> reports) throws MpfInteropUsageException {
        this.reportDate = parseStringAsLocalDateTime(reportDate);
        this.reports = reports;
    }

    /**
     * Constructor used to create a health report containing multiple streaming jobs.
     *
     * @param reportDate timestamp formatted String for this Health Report.
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
    public JsonHealthReportDataCallbackBody(@JsonProperty("reportDate") String reportDate,
        @JsonProperty("jobIds") List<Long> jobIds, @JsonProperty("externalIds") List<String> externalIds,
        @JsonProperty("jobStatuses") List<String> /*JobStatus*/ jobStatuses,
        @JsonProperty("lastActivityFrameId") List<String> lastActivityFrameIds,
        @JsonProperty("lastActivityTimestamp") List<String> lastActivityTimestamps) throws MpfInteropUsageException, DateTimeParseException {

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
            } else {

                // If we get to here, then all job parameter Lists passed the error check. Construct this health report.
                this.reportDate = parseStringAsLocalDateTime(reportDate);

                // Add each job as a separate health report.
                for ( int i=0; i<numJobIds; i++ ) {
/*
                    reports.add(new JsonHealthReportData (jobIds.get(i), externalIds.get(i), jobStatuses.get(i),
                        lastActivityFrameIds.get(i), lastActivityTimestamps.get(i)));
*/
                    reports.add(new Report(jobIds.get(i), externalIds.get(i), jobStatuses.get(i),
                        lastActivityFrameIds.get(i), lastActivityTimestamps.get(i)));

                }
            }
        }

 /*       // Error check, lastActivityTimestamp may not be a null
        if ( lastActivityTimestamps == null ) {
            throw new MpfInteropUsageException("Error, lastActivityTimestamp may not be null.");
        } else {
            // Note: need to handle possible checked exceptions MpfInteropUsageException or DateTimeParseException thrown in Lambda expression.
            try {
                List<LocalDateTime> lastActivityTimestampList = lastActivityTimestamps.stream()
                    .map(timestamp -> {
                        try {
                            if ( timestamp != null ) {
                                return parseStringAsLocalDateTime(timestamp);
                            } else {
                                return null;
                            }
                        } catch (MpfInteropUsageException | DateTimeParseException e) {
                            // Issue: in Java8, can only throw a checked Exception out of a lambda expression.
                            // Handle that here by throwing an unchecked Exception instead. The checked Exception will
                            // be re-created outside the lambda so it can be thrown back to the caller.
                            throw new IllegalArgumentException("Error parsing last activity timestamp " + timestamp, e);
                        }
                    }).collect(Collectors.toList());

                // Call common method to create health reports for all specified jobs.
                createJobHealthReports(reportDate, jobIds, externalIds, jobStatuses,
                    lastActivityFrameIds, lastActivityTimestampList);

            } catch (IllegalArgumentException e) {

                // The only time we should get this is if we got a MpfInteropUsageException or DateTimeParseException while parsing
                // the lastActivityTimestamps as a Lambda expression with the stream. If this happens, throw the Exception back to the caller
                // as a MpfInteropUsageException.
                throw new MpfInteropUsageException(e.getMessage(), e.getCause());
            }
        }
*/
    }

    @JsonIgnore
    public String toString() {
        return "reportDate = " + reportDate + ", reports = " + reports;
    }

}