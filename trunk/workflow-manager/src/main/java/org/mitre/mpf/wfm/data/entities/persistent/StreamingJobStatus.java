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

import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.wfm.enums.StreamingJobStatusType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This class includes the essential information which describes streaming job status.
 */
public class StreamingJobStatus {

    public static final StreamingJobStatusType UNKNOWN = StreamingJobStatusType.UNKNOWN;

    /** Finds the StreamingJobStatusType which best matches the given input; if no match is found, {@link #UNKNOWN} is used. */
    public static StreamingJobStatusType parse(String input) {
        return parse(input, UNKNOWN);
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

    private String detail = null;
    public void setDetail(String detail) { this.detail = detail; }
    public String getDetail() { return detail; }

    private StreamingJobStatusType type = UNKNOWN;
    public void setType(StreamingJobStatusType type) { this.type = type; }
    public StreamingJobStatusType getType() { return this.type; }
    public boolean isTerminal() {
        return type.isTerminal();
    }

    public StreamingJobStatus(StreamingJobStatusType statusType) {
        this(statusType,null);
    }

    public StreamingJobStatus(StreamingJobStatusType statusType, String statusDetail) {
        setType(statusType);
        setDetail(statusDetail);
    }

    // Overriding equals method so Mocking in TestStreamingJobStartStop will work. Note that value of status
    // detail is intentionally not a factor when determining equality.
    @Override
    public boolean equals(Object otherStreamingJobStatus) {
        if ( otherStreamingJobStatus instanceof StreamingJobStatus ) {
            return this.type == ((StreamingJobStatus) otherStreamingJobStatus).type;
        } else {
            return false;
        }
    }

    // Overriding hashCode due to override of equals method. Note that value of status
    // detail is intentionally not a factor when determining hashCode.
    @Override
    public int hashCode() {
        return System.identityHashCode(this.type);
    }

    public static String toString(StreamingJobStatusType type, String detail) {
        String retval = "";
        if (type != null) {
            retval += type.toString();
        }
        if (detail != null) {
            retval += ": " + detail;
        }
        return retval;
    }

    @Override
    public String toString() {
        return toString(this.type, this.detail);
    }
}
