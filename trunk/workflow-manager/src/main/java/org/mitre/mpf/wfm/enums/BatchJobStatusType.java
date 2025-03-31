/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// BatchJobStatusType enumeration describes all possible job status conditions applicable to a batch job.
public enum BatchJobStatusType {

    /**
     * Default: The status of the job is unknown.
     **/
    UNKNOWN(false) {
        @Override
        public BatchJobStatusType onWarning() {
            return this;
        }

        @Override
        public BatchJobStatusType onError() {
            return IN_PROGRESS_ERRORS;
        }

        @Override
        public BatchJobStatusType onComplete() {
            return ERROR;
        }

        @Override
        public BatchJobStatusType onCancel() {
            return CANCELLING;
        }
    },

    /**
     * The job has been initialized but not started.
     */
    INITIALIZED(false) {
        @Override
        public BatchJobStatusType onWarning() {
            return IN_PROGRESS_WARNINGS;
        }

        @Override
        public BatchJobStatusType onError() {
            return IN_PROGRESS_ERRORS;
        }

        @Override
        public BatchJobStatusType onComplete() {
            return COMPLETE;
        }

        @Override
        public BatchJobStatusType onCancel() {
            return CANCELLING;
        }
    },

    /**
     * Indicates the job is in progress.
     */
    IN_PROGRESS(false) {
        @Override
        public BatchJobStatusType onWarning() {
            return IN_PROGRESS_WARNINGS;
        }

        @Override
        public BatchJobStatusType onError() {
            return IN_PROGRESS_ERRORS;
        }

        @Override
        public BatchJobStatusType onComplete() {
            return COMPLETE;
        }

        @Override
        public BatchJobStatusType onCancel() {
            return CANCELLING;
        }
    },

    /**
     * Indicates the job is in progress with errors.
     */
    IN_PROGRESS_ERRORS(false) {
        @Override
        public BatchJobStatusType onWarning() {
            return this;
        }

        @Override
        public BatchJobStatusType onError() {
            return this;
        }

        @Override
        public BatchJobStatusType onComplete() {
            return ERROR;
        }

        @Override
        public BatchJobStatusType onCancel() {
            return CANCELLING;
        }
    },

    /**
     * Indicates the job is in progress with warnings.
     */
    IN_PROGRESS_WARNINGS(false) {
        @Override
        public BatchJobStatusType onWarning() {
            return this;
        }

        @Override
        public BatchJobStatusType onError() {
            return IN_PROGRESS_ERRORS;
        }

        @Override
        public BatchJobStatusType onComplete() {
            return COMPLETE_WITH_WARNINGS;
        }

        @Override
        public BatchJobStatusType onCancel() {
            return CANCELLING;
        }
    },

    /**
     * Indicates the job has completed.
     */
    COMPLETE(true) {
        @Override
        public BatchJobStatusType onWarning() {
            return COMPLETE_WITH_WARNINGS;
        }

        @Override
        public BatchJobStatusType onError() {
            return ERROR;
        }

        @Override
        public BatchJobStatusType onComplete() {
            return this;
        }

        @Override
        public BatchJobStatusType onCancel() {
            return this;
        }
    },

    /**
     * Indicates the job has completed, but with processing errors.
     */
    COMPLETE_WITH_ERRORS(true) {
        @Override
        public BatchJobStatusType onWarning() {
            return this;
        }

        @Override
        public BatchJobStatusType onError() {
            return this;
        }

        @Override
        public BatchJobStatusType onComplete() {
            return this;
        }

        @Override
        public BatchJobStatusType onCancel() {
            return this;
        }
    },

    /**
     * Indicates the job has completed, but with warnings.
     */
    COMPLETE_WITH_WARNINGS(true) {
        @Override
        public BatchJobStatusType onWarning() {
            return this;
        }

        @Override
        public BatchJobStatusType onError() {
            return ERROR;
        }

        @Override
        public BatchJobStatusType onComplete() {
            return this;
        }

        @Override
        public BatchJobStatusType onCancel() {
            return this;
        }
    },

    /**
     * Indicates the job is in the middle of cancellation.
     */
    CANCELLING(false) {
        @Override
        public BatchJobStatusType onWarning() {
            return this;
        }

        @Override
        public BatchJobStatusType onError() {
            return this;
        }

        @Override
        public BatchJobStatusType onComplete() {
            return CANCELLED;
        }

        @Override
        public BatchJobStatusType onCancel() {
            return this;
        }
    },

    /**
     * Indicates the job was cancelled as a result of a system shutdown.
     */
    CANCELLED_BY_SHUTDOWN(true) {
        @Override
        public BatchJobStatusType onWarning() {
            return this;
        }

        @Override
        public BatchJobStatusType onError() {
            return this;
        }

        @Override
        public BatchJobStatusType onComplete() {
            return this;
        }

        @Override
        public BatchJobStatusType onCancel() {
            return this;
        }
    },

    /**
     * Indicates the job was cancelled by a user-initiated process.
     */
    CANCELLED(true) {
        @Override
        public BatchJobStatusType onWarning() {
            return this;
        }

        @Override
        public BatchJobStatusType onError() {
            return this;
        }

        @Override
        public BatchJobStatusType onComplete() {
            return this;
        }

        @Override
        public BatchJobStatusType onCancel() {
            return this;
        }
    },

    /**
     * Indicates the job is in an error state. This is used for unknown/unrecoverable
     * errors, and when a piece of media is not available or cannot be retrieved.
     */
    ERROR(true) {
        @Override
        public BatchJobStatusType onWarning() {
            return this;
        }

        @Override
        public BatchJobStatusType onError() {
            return this;
        }

        @Override
        public BatchJobStatusType onComplete() {
            return this;
        }

        @Override
        public BatchJobStatusType onCancel() {
            return CANCELLING;
        }
    };

    private final boolean _terminal;

    public boolean isTerminal() {
        return _terminal;
    }

    BatchJobStatusType(boolean terminal) { _terminal = terminal; }


    /** Finds the BatchJobStatusType which best matches the given input; if no match is found, {@link #UNKNOWN} is used. */
    public static BatchJobStatusType parse(String input) {
        return parse(input, UNKNOWN);
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

    public abstract BatchJobStatusType onWarning();

    public abstract BatchJobStatusType onError();

    public abstract BatchJobStatusType onComplete();

    public abstract BatchJobStatusType onCancel();

    public BatchJobStatusType onFatalError() {
        return ERROR;
    }

}
