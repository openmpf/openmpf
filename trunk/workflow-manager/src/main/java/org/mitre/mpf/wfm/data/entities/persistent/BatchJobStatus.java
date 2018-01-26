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
import org.mitre.mpf.wfm.enums.BatchJobStatusType;

/**
 * This class includes the essential information which describes batch job status.
 */
public class BatchJobStatus extends JobStatus {

    public static final BatchJobStatusType DEFAULT = BatchJobStatusType.COMPLETE;

    /** Finds the BatchJobStatusType which best matches the given input; if no match is found, {@link #DEFAULT} is used. */
    public static BatchJobStatusType parse(String input) {
        return parse(input, DEFAULT);
    }

    public static BatchJobStatusType parse(String input, BatchJobStatusType defaultValue) {
        String trimmed = StringUtils.trimToNull(input);
        for ( BatchJobStatusType jobStatus : BatchJobStatusType.values() ) {
            if ( StringUtils.equalsIgnoreCase(jobStatus.name(), trimmed) ) {
                return jobStatus;
            }
        }
        return defaultValue;
    }

    public static Collection<BatchJobStatusType> getNonTerminalStatuses() {
        List<BatchJobStatusType> jobStatuses = new ArrayList<>();
        for ( BatchJobStatusType jobStatus : BatchJobStatusType.values() ) {
            if (!jobStatus.isTerminal() ) {
                jobStatuses.add(jobStatus);
            }
        }
        return jobStatuses;
    }

    private BatchJobStatusType jobStatus = DEFAULT;
    public BatchJobStatusType getJobStatus() { return this.jobStatus; }
    public void setJobStatus(BatchJobStatusType jobStatus) { this.jobStatus = jobStatus; }
    public void setJobStatus(String jobStatusString) { this.jobStatus = parse(jobStatusString); }

    public BatchJobStatus(BatchJobStatusType jobStatus) {
        setJobStatus(jobStatus);
    }

    public BatchJobStatus(String jobStatusString) {
        setJobStatus(jobStatusString);
    }

}
