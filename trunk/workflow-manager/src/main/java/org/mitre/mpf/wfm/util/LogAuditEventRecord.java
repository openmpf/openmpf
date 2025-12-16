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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;

public record LogAuditEventRecord(
    Instant time,
    EventId eid,
    TagType tag,
    String app,
    String user,
    OpType op,
    ResType res,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String uri,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String bucket,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String object_key,
    String msg) {

    // Event IDS:
    //   100 through 999: successful events
    //   4000 through 4999: failure/error events
    public enum EventId {
        // Successful events
        ACCESS_HOMEPAGE(100),
        LOGIN_PAGE_ACCESS(101),
        LOGIN_SUCCESS(102),
        AUTHENTICATED_WEB_REQUEST(103),
        USER_LOGOUT(104),
        CREATE_JOB(200),
        RESUBMIT_JOB(202),
        CANCEL_JOB(203),
        GET_JOB_INFO(204),
        GET_JOB_OUTPUT(205),
        JOB_CALLBACK_SENT(210),
        UPLOAD_MEDIA(300),
        DOWNLOAD_MEDIA(301),
        UPLOAD_FILE(302),
        DOWNLOAD_FILE(303),
        CREATE_FILE(304),
        CREATE_DIRECTORY(305),
        VIEW_FILES(306),
        TIES_DB_GET(500),
        TIES_DB_POST(501),
        S3_UPLOAD(600),
        S3_DOWNLOAD(601),
        S3_UPLOAD_SKIPPED(602),
        HAWTIO_ACCESS(700),
        REST_API_ACCESS(800),
        // Failure/error events
        ACCESS_DENIED(4100),
        LOGIN_FAILURE(4102),
        JOB_CREATE_ERROR(4200),
        JOB_RESUBMIT_ERROR(4202),
        JOB_CANCEL_ERROR(4203),
        GET_JOB_INFO_ERROR(4204),
        GET_JOB_OUTPUT_ERROR(4205),
        JOB_CALLBACK_ERROR(4210),
        MEDIA_UPLOAD_ERROR(4300),
        MEDIA_DOWNLOAD_ERROR(4301),
        FILE_UPLOAD_ERROR(4302),
        FILE_DOWNLOAD_ERROR(4303),
        FILE_CREATE_ERROR(4304),
        DIRECTORY_CREATE_ERROR(4305),
        FILE_ACCESS_ERROR(4306),
        TIESDB_GET_ERROR(4500),
        TIESDB_POST_ERROR(4501),
        S3_UPLOAD_ERROR(4600),
        S3_DOWNLOAD_ERROR(4601),
        INVALID_PARAMETER_ERROR(5000),
        INVALID_JOB_ID_ERROR(5001),
        URI_SYNTAX_ERROR(5002);

        private final int value;
        EventId(int value) {
            this.value = value;
        }
        @JsonValue
        public int getValue() {
            return value;
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
