/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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

// MarkupStatusType enumeration describes all possible terminal status conditions applicable to a markup result.
public enum MarkupStatusType {

    /**
     * Default: The status of markup is not defined or is unknown.
     **/
    UNKNOWN {
        @Override
        public MarkupStatusType onWarning() {
            return COMPLETE_WITH_WARNING;
        }

        @Override
        public MarkupStatusType onError() {
            return FAILED;
        }

        @Override
        public MarkupStatusType onComplete() {
            return COMPLETE;
        }

        @Override
        public MarkupStatusType onCancel() {
            return CANCELLED;
        }
    },

    /**
     * Indicates that markup completed successfully.
     */
    COMPLETE {
        @Override
        public MarkupStatusType onWarning() {
            return COMPLETE_WITH_WARNING;
        }

        @Override
        public MarkupStatusType onError() {
            return FAILED;
        }

        @Override
        public MarkupStatusType onComplete() {
            return this;
        }

        @Override
        public MarkupStatusType onCancel() {
            return this;
        }
    },

    /**
     * Indicates that the markup request was cancelled and consequently skipped.
     */
    CANCELLED {
        @Override
        public MarkupStatusType onWarning() {
            return this;
        }

        @Override
        public MarkupStatusType onError() {
            return this;
        }

        @Override
        public MarkupStatusType onComplete() {
            return this;
        }

        @Override
        public MarkupStatusType onCancel() {
            return this;
        }
    },

    /**
     * Indicates that markup failed.
     */
    FAILED {
        @Override
        public MarkupStatusType onWarning() {
            return this;
        }

        @Override
        public MarkupStatusType onError() {
            return this;
        }

        @Override
        public MarkupStatusType onComplete() {
            return this;
        }

        @Override
        public MarkupStatusType onCancel() {
            return this;
        }
    },

    /**
     * Indicates that markup completed with warnings.
     */
    COMPLETE_WITH_WARNING {
        @Override
        public MarkupStatusType onWarning() {
            return this;
        }

        @Override
        public MarkupStatusType onError() {
            return FAILED;
        }

        @Override
        public MarkupStatusType onComplete() {
            return this;
        }

        @Override
        public MarkupStatusType onCancel() {
            return this;
        }
    };


    /** Finds the MarkupStatusType which best matches the given input; if no match is found, {@link #UNKNOWN} is used. */
    public static MarkupStatusType parse(String input) {
        return parse(input, UNKNOWN);
    }

    public static MarkupStatusType parse(String input, MarkupStatusType defaultValue) {
        String trimmed = StringUtils.trimToNull(input);
        for ( MarkupStatusType markupStatus : MarkupStatusType.values() ) {
            if ( StringUtils.equalsIgnoreCase(markupStatus.name(), trimmed) ) {
                return markupStatus;
            }
        }
        return defaultValue;
    }

    public abstract MarkupStatusType onWarning();

    public abstract MarkupStatusType onError();

    public abstract MarkupStatusType onComplete();

    public abstract MarkupStatusType onCancel();

    public MarkupStatusType onFatalError() {
        return FAILED;
    }

}
