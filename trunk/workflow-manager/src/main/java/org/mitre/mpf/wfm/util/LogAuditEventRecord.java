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


package org.mitre.mpf.wfm.util;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonValue;

public record LogAuditEventRecord(
    Instant time,
    TagType tag,
    String app,
    String user,
    OpType op,
    ResType res,
    String uri,
    String msg,
    int eid
) {

    // These event IDs are placeholders and should be changed to reflect the type of events better.
    public static final class EventIds {
        // User/Service audit events: 100-1000
        public static final int LOGIN_SUCCESS = 100;
        public static final int LOGIN_FAILURE = 101;
        public static final int JOB_CREATE = 110;
        public static final int JOB_MODIFY = 112;
        public static final int JOB_DELETE = 113;
        public static final int MEDIA_UPLOAD = 120;
        public static final int MEDIA_URL_SAVE = 121;
        public static final int REST_API_ACCESS = 200;
        public static final int ACCESS_DENIED = 300;
        public static final int TIES_DB_POST = 400;

        // System/Diagnostic operational events: 4001-5000
        public static final int SYSTEM_ERROR = 4001;
        public static final int VALIDATION_ERROR = 4002;
        public static final int EXTERNAL_API_ERROR = 4003;

        private EventIds() {
            // Utility class
        }
    }
    public enum TagType {
        SECURITY("&B1E7-FFFF&");

        private final String value;

        TagType( String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

    }

    public enum OpType {
        CREATE("c"),
        READ("r"),
        MODIFY("m"),
        DELETE("d"),
        LOGIN("l"),
        EXTRACT("e");

        private final String value;

        OpType( String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }
    }

    public enum ResType {
        ALLOW("a"),
        DENY("d"),
        ERROR("e");

        private final String value;

        ResType( String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }
    }
}
