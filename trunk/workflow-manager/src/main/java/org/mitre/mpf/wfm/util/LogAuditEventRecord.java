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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public record LogAuditEventRecord(
    Instant time,

    @JsonProperty("eid")
    int  eventId,

    TagType tag,
    String app,
    String user,
    OpType op,
    ResType res,

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String uri,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String uriQueryString,

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String bucket,

    @JsonProperty("object_key")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String objectKey,
    
    String msg) {

 
    public enum EventId {
        ACCESS_HOMEPAGE(100, 4100, "homepage access"),
        LOGIN_PAGE_ACCESS(101, 4101, "login page access"),
        USER_LOGIN(102, 4102, "user login"),
        USER_LOGOUT(103, 4103, "user logout"),
        AUTHENTICATED_WEB_REQUEST(104, 4104, "authenticated web request"),
        ACCESS_DENIED(105, 4105, "access denied"),
        SSO_ACCESS(106,4106, "SSO access"),
        GET_USER_CREDENTIALS(107, 4107, "get user credentials"),
        CREATE_JOB(200, 4200, "create job"),
        RESUBMIT_JOB(201, 4201, "resubmit job"),
        CANCEL_JOB(202, 4202, "cancel job"),
        GET_JOB_INFO(203, 4203, "get job info"),
        GET_JOB_OUTPUT(204, 4204, "get job output"),
        GET_JOB_STATUS(205,4205, "get job status"),
        JOB_CALLBACK(206, 4206, "job completion callback"),
        COMPONENT_REGISTRATION(300, 4300, "component registration"),
        SUBJECT_TRACKING_COMPONENTS(301, 4301, "get subject tracking component info"),
        CREATE_SUBJECT_TRACKING_JOB(302, 4302, "create subject tracking job"),
        CANCEL_SUBJECT_TRACKING_JOB(303, 4303, "cancel subject tracking job"),
        GET_SUBJECT_TRACKING_JOB_INFO(304, 4304, "get subject tracking job info"),
        GET_SUBJECT_TRACKING_JOB_OUTPUT(305, 4305, "get subject tracking job output"),
        REST_PIPELINES(400, 4400, "get pipeline info"),
        REST_QUEUES(410, 4410, "get queue info"),
        VIEW_MEDIA(500, 4500, "view media"),
        UPLOAD_MEDIA(501, 4501, "upload media"),
        DOWNLOAD_MEDIA(502, 4502, "download media"),
        VIEW_MARKUP(503,4503, "view markup"),
        DOWNLOAD_MARKUP(504,4504, "download markup"),
        UPLOAD_FILE(505, 4505, "upload file"),
        DOWNLOAD_FILE(506, 4506, "download file"),
        CREATE_FILE(507, 4507, "create file"),
        CREATE_DIRECTORY(508, 4508, "create directory"),
        GET_DIRECTORY_LISTING(509, 4509, "get directory listing"),
        TIES_DB_GET(600, 4600, "TiesDB get"),
        TIES_DB_POST(601, 4601, "TiesDB post"),
        TIES_DB_REPOST(602, 4602, "TiesDB repost"),
        S3_UPLOAD(700, 4700, "upload to S3"),
        S3_DOWNLOAD(701, 4701, " download from S3"),
        S3_UPLOAD_SKIPPED(702, 4702, "skipped upload to S3"),
        HAWTIO_ACCESS(800, 4800, "Hawtio access"),
        ADMIN_LOGS(1000,5000, "admin logs access"),
        ADMIN_STATISTICS(1001, 5001, "admin statistics access"),
        PROPERTY_SETTINGS_ACCESS(1002, 5002, "property settings access");

        public final int success;
        public final int fail;
        public final String message;

        EventId(int success, int fail, String message) {
            this.success = success;
            this.fail = fail;
            this.message = message;
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
