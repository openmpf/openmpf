
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public interface JobStatusI {

    // Convenience mappings for JobStatus' that are generally applicable to any type of job.
    public static final JobStatus UNKNOWN = JobStatus.UNKNOWN;
    public static final JobStatus INITIALIZED = JobStatus.INITIALIZED;
    public static final JobStatus JOB_CREATION_ERROR = JobStatus.JOB_CREATION_ERROR;
    public static final JobStatus IN_PROGRESS = JobStatus.IN_PROGRESS;
    public static final JobStatus IN_PROGRESS_ERRORS = JobStatus.IN_PROGRESS_ERRORS;
    public static final JobStatus IN_PROGRESS_WARNINGS = JobStatus.IN_PROGRESS_WARNINGS;
    public static final JobStatus BUILDING_OUTPUT_OBJECT = JobStatus.BUILDING_OUTPUT_OBJECT;
    public static final JobStatus COMPLETE = JobStatus.COMPLETE;
    public static final JobStatus COMPLETE_WITH_ERRORS = JobStatus.COMPLETE_WITH_ERRORS;
    public static final JobStatus COMPLETE_WITH_WARNINGS = JobStatus.COMPLETE_WITH_WARNINGS;
    public static final JobStatus CANCELLED = JobStatus.CANCELLED;
    public static final JobStatus CANCELLED_BY_SHUTDOWN = JobStatus.CANCELLED_BY_SHUTDOWN;
    public static final JobStatus CANCELLING = JobStatus.CANCELLING;
    public static final JobStatus ERROR = JobStatus.ERROR;

    // JobStatus enumeration describes all possible job status conditions applicable to a batch or a streaming job.
    public enum JobStatus {

        // Section defines job statuses that may be applicable to either a batch or a streaming job.

        /**
         * Default: The status of the job is unknown.
         **/
        UNKNOWN(false),

        /**
         * The job has been initialized but not started.
         */
        INITIALIZED(false),

        /**
         * Indicates that a job was received, but a job could not be created from the contents of
         * the request.
         */
        JOB_CREATION_ERROR(true),

        /**
         * Indicates the job is in progress.
         */
        IN_PROGRESS(false),

        /**
         * Indicates the job is in progress with errors.
         */
        IN_PROGRESS_ERRORS(false),

        /**
         * Indicates the job is in progress with warnings.
         */
        IN_PROGRESS_WARNINGS(false),

        /**
         * Indicates that the job is having its output object built.
         */
        BUILDING_OUTPUT_OBJECT(false),

        /**
         * Indicates the job has completed.
         */
        COMPLETE(true),

        /**
         * Indicates the job has completed, but with processing errors.
         */
        COMPLETE_WITH_ERRORS(true),

        /**
         * Indicates the job has completed, but with warnings.
         */
        COMPLETE_WITH_WARNINGS(true),

        /**
         * Indicates the job is in the middle of cancellation.
         */
        CANCELLING(false),

        /**
         * Indicates the job was cancelled as a result of a system shutdown.
         */
        CANCELLED_BY_SHUTDOWN(true),

        /**
         * Indicates the job was cancelled by a user-initiated process.
         */
        CANCELLED(true),

        /**
         * Indicates the job is in an error state.
         */
        ERROR(true),

        // Section defines job statuses that may be applicable only to a streaming job.

        /**
         * Indicates the streaming job was terminated.
         */
        STREAMING_JOB_TERMINATED(true),

        /**
         * Indicates the streaming job is paused.
         */
        STREAMING_JOB_PAUSED(false),

        /**
         * Indicates the streaming job is stalled
         */
        STREAMING_JOB_STALLED(false);

        protected boolean terminal;

        public boolean isTerminal() {
            return terminal;
        }

        JobStatus(boolean terminal) { this.terminal = terminal; }

    } // end of JobStatus enum

    public static final JobStatus DEFAULT = COMPLETE;

    /** Finds the JobStatus which best matches the given input; if no match is found, {@link #DEFAULT} is used. */
    public static JobStatus parse(String input) {
        return parse(input, DEFAULT);
    }

    public static JobStatus parse(String input, JobStatus defaultValue) {
        String trimmed = StringUtils.trimToNull(input);
        for ( JobStatus jobStatus : JobStatus.values() ) {
            if ( StringUtils.equalsIgnoreCase(jobStatus.name(), trimmed) ) {
                return jobStatus;
            }
        }
        return defaultValue;
    }

    public static Collection<JobStatus> getNonTerminalStatuses() {
        List<JobStatus> jobStatuses = new ArrayList<>();
        for ( JobStatus jobStatus : JobStatus.values() ) {
            if (!jobStatus.isTerminal() ) {
                jobStatuses.add(jobStatus);
            }
        }
        return jobStatuses;
    }

}
