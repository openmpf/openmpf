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

public class oldBatchJobStatus implements JobStatusI {

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

    public oldBatchJobStatus(JobStatus jobStatus) {
        this.jobStatus = jobStatus;
    }

    // allow for static methods defined in JobStatusI interface to be called using this class.
    public static JobStatus parse(String input) { return JobStatusI.parse(input); }
    public static JobStatus parse(String input, JobStatus defaultValue) { return JobStatusI.parse(input, defaultValue); }
    public static Collection<JobStatus> getNonTerminalStatuses() { return JobStatusI.getNonTerminalStatuses(); }

    @Override
    /** If the JobStatus enumeration within this object is equivalent to the JobStatus enumeration in the other object, then
     * consider the BatchJobStatus objects to be equal.
     */
    public boolean equals(Object obj) {
        if ( obj instanceof BatchJobStatus ) {
            BatchJobStatus other = (BatchJobStatus)obj;
            return jobStatus == other.jobStatus;
        } else {
            return false;
        }
    }

    @Override
    // Override if equals method requires override of hashCode method.
    public int hashCode() {
        return Objects.hash(jobStatus);
    }

    @Override
    public String toString() {
        return jobStatus.toString();
    }

}

