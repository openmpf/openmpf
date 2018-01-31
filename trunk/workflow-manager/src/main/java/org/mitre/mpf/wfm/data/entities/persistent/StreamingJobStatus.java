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

package org.mitre.mpf.wfm.data.entities.persistent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.wfm.enums.StreamingJobStatusType;

/**
 * This class includes the essential information which describes streaming job status.
 */
public class StreamingJobStatus extends JobStatus {

    public static final StreamingJobStatusType DEFAULT = StreamingJobStatusType.COMPLETE;

    /** Finds the StreamingJobStatusType which best matches the given input; if no match is found, {@link #DEFAULT} is used. */
    public static StreamingJobStatusType parse(String input) {
        return parse(input, DEFAULT);
    }

    public static StreamingJobStatusType parse(String input, StreamingJobStatusType defaultValue) {
        String trimmed = StringUtils.trimToNull(input);
        for ( StreamingJobStatusType jobStatus : StreamingJobStatusType.values() ) {
            if ( StringUtils.equalsIgnoreCase(jobStatus.name(), trimmed) ) {
                return jobStatus;
            }
        }
        return defaultValue;
    }

    public static Collection<StreamingJobStatusType> getNonTerminalStatuses() {
        List<StreamingJobStatusType> jobStatuses = new ArrayList<>();
        for ( StreamingJobStatusType jobStatus : StreamingJobStatusType.values() ) {
            if (!jobStatus.isTerminal() ) {
                jobStatuses.add(jobStatus);
            }
        }
        return jobStatuses;
    }

    private String status = null;
    public void setStatus(String status) {
        this.status = status;
        this.jobStatus = parse(status);
    }
    public void setStatus(StreamingJobStatusType jobStatus) {
        this.status = jobStatus.name();
        this.jobStatus = jobStatus;
    }
    public String getStatus() { return status; }

    private String statusDetail = null;
    public void setStatusDetail(String statusDetail) { this.statusDetail = statusDetail; }
    public String getStatusDetail() { return statusDetail; }

    private StreamingJobStatusType jobStatus = DEFAULT;
    public StreamingJobStatusType getJobStatus() { return this.jobStatus; }

    public StreamingJobStatus(StreamingJobStatusType jobStatus) {
        this(jobStatus,null);
    }

    public StreamingJobStatus(String jobStatusString) {
        this(jobStatusString,null);
    }

    public StreamingJobStatus(StreamingJobStatusType jobStatus, String statusDetail) {
        setStatus(jobStatus);
        setStatusDetail(statusDetail);
    }

    public StreamingJobStatus(String jobStatusString, String statusDetail) {
        setStatus(jobStatusString);
        setStatusDetail(statusDetail);
    }

    // Overriding equals method so Mocking in TestStreamingJobStartStop will work. Note that value of status
    // detail is intentionally not a factor when determining equality.
    @Override
    public boolean equals(Object otherStreamingJobStatus) {
        if ( otherStreamingJobStatus instanceof StreamingJobStatus ) {
            return this.jobStatus == ((StreamingJobStatus) otherStreamingJobStatus).jobStatus;
        } else {
            return false;
        }
    }

    // Overriding hashCode due to override of equals method. Note that value of status
    // detail is intentionally not a factor when determining hashCode.
    @Override
    public int hashCode() {
        return System.identityHashCode(this.jobStatus);
    }
}
