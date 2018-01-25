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

package org.mitre.mpf.wfm.enums;

import java.util.Collection;
import java.util.Objects;

// StreamingJobStatus gets common JobStatus enumerations from JobStatusI, plus includes a mutable detail field.
public class StreamingJobStatus implements JobStatusI {

    // Convenience mappings for JobStatus' that are only applicable to streaming jobs.
    public static final JobStatus STREAMING_JOB_TERMINATED = JobStatusI.JobStatus.STREAMING_JOB_TERMINATED;
    public static final JobStatus STREAMING_JOB_PAUSED = JobStatusI.JobStatus.STREAMING_JOB_PAUSED;
    public static final JobStatus STREAMING_JOB_STALLED = JobStatusI.JobStatus.STREAMING_JOB_STALLED;

    // Provide a field for this job status (initialized to DEFAULT)
    private JobStatus jobStatus = JobStatusI.DEFAULT;
    public void setJobStatus(JobStatus jobStatus) { this.jobStatus = jobStatus; }
    public JobStatus getJobStatus() { return jobStatus; }
    /** Checks to see if this job status represents any terminal condition.
     * @return true if this job status represents any terminal condition, false otherwise.
     */
    public boolean isTerminal() {
        return jobStatus.isTerminal();
    }

    // Include a mutable field so more detailed information about the status of a streaming job can be attached to the job status.
    // Detail will remain null if there is no detailed information available.
	private String detail = null;
	public String getDetail() { return detail; }
	public void setDetail(String detail) { this.detail = detail; }
	private boolean isDetailEqualTo(String otherDetail) {
        return (detail == null && otherDetail == null ) || detail.equals(otherDetail);
    }

    public StreamingJobStatus(String jobStatusString) {
        this.jobStatus = JobStatusI.parse(jobStatusString);
    }

    public StreamingJobStatus(String jobStatusString, String detailInfo) {
        this.jobStatus = JobStatusI.parse(jobStatusString);
        detail = detailInfo;
    }

    public StreamingJobStatus(JobStatus jobStatus) {
        this.jobStatus = jobStatus;
    }

    public StreamingJobStatus(JobStatus jobStatus, String detailInfo) {
        this(jobStatus);
        detail = detailInfo;
    }

//    /** Returns a StreamingJobStatus set to the specified condition. */
//    public static StreamingJobStatus getStatusInitialized() {
//        return new StreamingJobStatus(INITIALIZED);
//    }
//
//    /** Returns a StreamingJobStatus set to the specified condition.
//     * @param detail extra detail information to associate with this streaming job status
//     */
//    public static StreamingJobStatus getStatusInitialized(String detail) {
//        return new StreamingJobStatus(INITIALIZED,detail);
//    }
//
//    /** Checks this StreamingJobStatus for the specified condition.
//     * @return true if this StreamingJobStatus is in the specified condition, false otherwise.
//     */
//    public boolean isStatusInitialized() {
//        return jobStatus == INITIALIZED;
//    }
//
//    public static StreamingJobStatus getStatusInProgress() {
//        return new StreamingJobStatus(IN_PROGRESS);
//    }
//
//    /** Returns a StreamingJobStatus set to the specified condition.
//     * @param detail extra detail information to associate with this streaming job status
//     */
//    public static StreamingJobStatus getStatusInProgress(String detail) {
//        return new StreamingJobStatus(IN_PROGRESS,detail);
//    }
//
//    /** Checks this StreamingJobStatus for the specified condition.
//     * @return true if this StreamingJobStatus is in the specified condition, false otherwise.
//     */
//    public boolean isStatusInProgress() {
//        return jobStatus == IN_PROGRESS;
//    }
//
//    /** Returns a StreamingJobStatus set to the specified condition. */
//    public static StreamingJobStatus getStatusCancelling() {
//        return new StreamingJobStatus(CANCELLING);
//    }
//
//    /** Returns a StreamingJobStatus set to the specified condition.
//     * @param detail extra detail information to associate with this streaming job status
//     */
//    public static StreamingJobStatus getStatusCancelling(String detail) {
//        return new StreamingJobStatus(CANCELLING,detail);
//    }
//
//    /** Checks this StreamingJobStatus for the specified condition.
//     * @return true if this StreamingJobStatus is in the specified condition, false otherwise.
//     */
//    public boolean isStatusCancelling() {
//        return jobStatus == CANCELLING;
//    }
//
//    /** Returns a StreamingJobStatus set to the specified condition. */
//    public static StreamingJobStatus getStatusCancelledByShutdown() {
//        return new StreamingJobStatus(CANCELLED_BY_SHUTDOWN);
//    }
//
//    /** Returns a StreamingJobStatus set to the specified condition.
//     * @param detail extra detail information to associate with this streaming job status
//     */
//    public static StreamingJobStatus getStatusCancelledByShutdown(String detail) {
//        return new StreamingJobStatus(CANCELLED_BY_SHUTDOWN,detail);
//    }
//
//    /** Checks this StreamingJobStatus for the specified condition.
//     * @return true if this StreamingJobStatus is in the specified condition, false otherwise.
//     */
//    public boolean isStatusCancelledByShutdown() {
//        return jobStatus == CANCELLED_BY_SHUTDOWN;
//    }
//
//    /** Returns a StreamingJobStatus set to the specified condition. */
//    public static StreamingJobStatus getStatusCancelled() {
//        return new StreamingJobStatus(CANCELLED);
//    }
//
//    /** Returns a StreamingJobStatus set to the specified condition.
//     * @param detail extra detail information to associate with this streaming job status
//     */
//    public static StreamingJobStatus getStatusCancelled(String detail) {
//        return new StreamingJobStatus(CANCELLED,detail);
//    }
//
//    /** Checks this StreamingJobStatus for the specified condition.
//     * @return true if this StreamingJobStatus is in the specified condition, false otherwise.
//     */
//    public boolean isStatusCancelled() {
//        return jobStatus == CANCELLED;
//    }
//
//    /** Returns a StreamingJobStatus set to the specified condition. */
//    public static StreamingJobStatus getStatusJobCreationError() {
//        return new StreamingJobStatus(JOB_CREATION_ERROR);
//    }
//
//    /** Returns a StreamingJobStatus set to the specified condition.
//     * @param detail extra detail information to associate with this streaming job status
//     */
//    public static StreamingJobStatus getStatusJobCreationError(String detail) {
//        return new StreamingJobStatus(JOB_CREATION_ERROR,detail);
//    }
//
//    /** Checks this StreamingJobStatus for the specified condition.
//     * @return true if this StreamingJobStatus is in the specified condition, false otherwise.
//     */
//    public boolean isStatusJobCreationError() {
//        return jobStatus == JOB_CREATION_ERROR;
//    }
//
//    /** Returns a StreamingJobStatus set to the specified condition. */
//    public static StreamingJobStatus getStatusInProgressErrors() {
//        return new StreamingJobStatus(IN_PROGRESS_ERRORS);
//    }
//
//    /** Returns a StreamingJobStatus set to the specified condition.
//     * @param detail extra detail information to associate with this streaming job status
//     */
//    public static StreamingJobStatus getStatusInProgressErrors(String detail) {
//        return new StreamingJobStatus(IN_PROGRESS_ERRORS,detail);
//    }
//
//    /** Checks this StreamingJobStatus for the specified condition.
//     * @return true if this StreamingJobStatus is in the specified condition, false otherwise.
//     */
//    public boolean isStatusInProgressErrors() {
//        return jobStatus == IN_PROGRESS_ERRORS;
//    }
//
//    /** Returns a StreamingJobStatus set to the specified condition. */
//    public static StreamingJobStatus getStatusError() {
//        return new StreamingJobStatus(ERROR);
//    }
//
//    /** Returns a StreamingJobStatus set to the specified condition.
//     * @param detail extra detail information to associate with this streaming job status
//     */
//    public static StreamingJobStatus getStatusError(String detail) {
//        return new StreamingJobStatus(ERROR,detail);
//    }
//
//    /** Checks this StreamingJobStatus for the specified condition.
//     * @return true if this StreamingJobStatus is in the specified condition, false otherwise.
//     */
//    public boolean isStatusError() {
//        return jobStatus == ERROR;
//    }
//
//    /** Returns a StreamingJobStatus set to the specified condition. */
//    public static StreamingJobStatus getStatusTerminated() {
//        return new StreamingJobStatus(STREAMING_JOB_TERMINATED);
//    }
//
//    /** Returns a StreamingJobStatus set to the specified condition.
//     * @param detail extra detail information to associate with this streaming job status
//     */
//    public static StreamingJobStatus getStatusTerminated(String detail) {
//        return new StreamingJobStatus(STREAMING_JOB_TERMINATED,detail);
//    }
//
//    /** Checks this StreamingJobStatus for the specified condition.
//     * @return true if this StreamingJobStatus is in the specified condition, false otherwise.
//     */
//    public boolean isStatusTerminated() {
//        return jobStatus == STREAMING_JOB_TERMINATED;
//    }

    // allow for static methods defined in JobStatusI interface to be called using this class.
    public static JobStatus parse(String input) { return JobStatusI.parse(input); }
    public static JobStatus parse(String input, JobStatus defaultValue) { return JobStatusI.parse(input, defaultValue); }
    public static Collection<JobStatus> getNonTerminalStatuses() { return JobStatusI.getNonTerminalStatuses(); }

    /** Get the string equivalent of the job status, without the detail information.
     * @return job status, without the detail information.
     */
    public String getStatusAsString() {
        return jobStatus.toString();
    }

    @Override
    public String toString() {
        if ( detail == null ) {
            return "jobStatus = " + getStatusAsString();
        } else {
            return "jobStatus = " + getStatusAsString() + ", detail = " + getDetail();
        }
    }

    @Override
    /** If the JobStatus enumeration within this object is equivalent to the JobStatus enumeration in the other object, then
     * consider the StreamingJobStatus objects to be equal only if the detail strings are also equal.
     */
    public boolean equals(Object obj) {
        if ( obj instanceof StreamingJobStatus ) {
            StreamingJobStatus other = (StreamingJobStatus)obj;
            return jobStatus == other.jobStatus && isDetailEqualTo(other.detail);
        } else {
            return false;
        }
    }

    @Override
    // Override if equals method requires override of hashCode method.
    public int hashCode() {
        if ( detail == null ) {
            return Objects.hash(jobStatus);
        } else {
            return Objects.hash(jobStatus,detail);
        }
    }


}

