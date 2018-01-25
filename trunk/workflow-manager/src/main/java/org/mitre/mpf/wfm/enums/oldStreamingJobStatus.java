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
public class oldStreamingJobStatus implements JobStatusI {

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

    public oldStreamingJobStatus(String jobStatusString) {
        this.jobStatus = JobStatusI.parse(jobStatusString);
    }

    public oldStreamingJobStatus(String jobStatusString, String detailInfo) {
        this.jobStatus = JobStatusI.parse(jobStatusString);
        detail = detailInfo;
    }

    public oldStreamingJobStatus(JobStatus jobStatus) {
        this.jobStatus = jobStatus;
    }

    public oldStreamingJobStatus(JobStatus jobStatus, String detailInfo) {
        this(jobStatus);
        detail = detailInfo;
    }

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

