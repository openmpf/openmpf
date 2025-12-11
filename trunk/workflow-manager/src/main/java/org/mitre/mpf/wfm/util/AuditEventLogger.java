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
import java.util.Optional;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class AuditEventLogger {
    private static final Logger log = LoggerFactory.getLogger(AuditEventLogger.class);
    private static final Marker AUDIT_MARKER = MarkerFactory.getMarker("mpf.AUDIT");

    private final PropertiesUtil _propertiesUtil;

    private final ObjectMapper _objectMapper;

    private String getCurrentLoggedInUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    @Inject
    public AuditEventLogger(PropertiesUtil propertiesUtil, ObjectMapper objectMapper) {
        _propertiesUtil = propertiesUtil;
        _objectMapper = objectMapper;
    }

    private void writeToLogger(LogAuditEventRecord event) {
        try {
            log.info(AUDIT_MARKER, _objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Failed to log event: {}", event, e);
        }
    }

    private AuditEventLogger log(
            LogAuditEventRecord.TagType tag,
            LogAuditEventRecord.OpType op,
            LogAuditEventRecord.ResType res,
            LogAuditEventRecord.EventId eid,
            String user,
            String uri,
            String msg,
            String bucket,
            String bucketKey) {

        //int eventId = getEventId(op, res, uri, msg);
        var eventRecord = new LogAuditEventRecord(
                Instant.now(), eid, tag, "openmpf", user, op, res, msg, uri, bucket, bucketKey);
        writeToLogger(eventRecord);
        return this;
    }
    // These event types are placeholders and should be changed to reflect the type of events better.
    //private int getEventId(LogAuditEventRecord.OpType op, LogAuditEventRecord.ResType res, String uri, String msg) {
        // Determine event ID based on operation type, result type, and context
    //    if (op == LogAuditEventRecord.OpType.LOGIN) {
    //        return res == LogAuditEventRecord.ResType.ALLOW
    //            ? LogAuditEventRecord.EventIds.LOGIN_SUCCESS
    //            : LogAuditEventRecord.EventIds.LOGIN_FAILURE;
    //    }

    //    if (res == LogAuditEventRecord.ResType.DENY) {
    //        return LogAuditEventRecord.EventIds.ACCESS_DENIED;
    //    }

    //    if (res == LogAuditEventRecord.ResType.ERROR) {
    //        if (msg != null && (msg.contains("TiesDB") || msg.contains("API call failed"))) {
   //             return LogAuditEventRecord.EventIds.EXTERNAL_API_ERROR;
    //        }
    //        if (msg != null && (msg.contains("Invalid") || msg.contains("Malformed") || msg.contains("missing"))) {
    //            return LogAuditEventRecord.EventIds.VALIDATION_ERROR;
    //        }
    //        return LogAuditEventRecord.EventIds.SYSTEM_ERROR;
    //    }

        // Success cases based on operation type and context
    //     switch (op) {
    //         case CREATE:
    //             if (msg != null && msg.contains("Job created")) {
    //                 return LogAuditEventRecord.EventIds.JOB_CREATE;
    //             }
    //             if (msg != null && msg.contains("Media file uploaded")) {
    //                 return LogAuditEventRecord.EventIds.MEDIA_UPLOAD;
    //             }
    //             return LogAuditEventRecord.EventIds.JOB_CREATE; // Default for CREATE

    //         case MODIFY:
    //             return LogAuditEventRecord.EventIds.JOB_MODIFY;

    //         case DELETE:
    //             return LogAuditEventRecord.EventIds.JOB_DELETE;

    //         default:
    //             return LogAuditEventRecord.EventIds.REST_API_ACCESS; 
    //     }
    // }

    public BuilderTagStage createEvent() {
        return getEventBuilder(LogAuditEventRecord.OpType.CREATE);
    }

    public BuilderTagStage readEvent() {
        return getEventBuilder(LogAuditEventRecord.OpType.READ);
    }

    public BuilderTagStage modifyEvent() {
        return getEventBuilder(LogAuditEventRecord.OpType.MODIFY);
    }

    public BuilderTagStage deleteEvent() {
        return getEventBuilder(LogAuditEventRecord.OpType.DELETE);
    }

    public BuilderTagStage loginEvent() {
        return getEventBuilder(LogAuditEventRecord.OpType.LOGIN);
    }

    public BuilderTagStage extractEvent() {
        return getEventBuilder(LogAuditEventRecord.OpType.EXTRACT);
    }

    private AuditEventBuilder getEventBuilder(LogAuditEventRecord.OpType opType) {
        if (_propertiesUtil.isAuditLoggingEnabled()) {
            return new EnabledEventBuilder(opType);
        }
        else {
            return DISABLED_EVENT_BUILDER;
        }
    }

    // Separate interface so that the caller is forced to set the tag.
    public static interface BuilderTagStage {
        AuditEventBuilder withSecurityTag();
    }

    public static class AuditEventBuilder implements BuilderTagStage {
        private AuditEventBuilder() {
        }

        /**
         * Normally setting authentication here is not needed because we can get it from the
         * SecurityContextHolder. Setting the authentication here is only required when listening
         * to Spring Security authentication events. Depending on the security configuration, the
         * events can fire before or after the SecurityContextHolder is set.
         * @param authentication Authentication info that will be used instead of the content of
         *                       SecurityContextHolder.
         * @return this
         */
        public AuditEventBuilder withAuth(Authentication authentication) {
            return this;
        }

        @Override
        public AuditEventBuilder withSecurityTag() {
            return this;
        }

        public AuditEventBuilder withEventId(LogAuditEventRecord.EventId eid) {
            return this;
        }

        public AuditEventBuilder withUri(String uri, Object... formatArgs) {
            return this;
        }

        public AuditEventBuilder withBucket(String bucket) {
            return this;
        }

        public AuditEventBuilder withBucketKey(String bucketKey) {
            return this;
        }

        public void allowed() {
        }

        public void allowed(String message, Object... formatArgs) {
        }

        public void denied(String message, Object... formatArgs) {
        }

        public void error(String message, Object... formatArgs) {
        }
    }

     private static final AuditEventBuilder DISABLED_EVENT_BUILDER = new AuditEventBuilder();

    private class EnabledEventBuilder extends AuditEventBuilder {

        private final LogAuditEventRecord.OpType _opType;

        private LogAuditEventRecord.TagType _tagType;

        private LogAuditEventRecord.EventId _eventId;

        private Authentication _auth;

        private String _uri;

        private String _bucket;

        private String _bucketKey;

        private EnabledEventBuilder(LogAuditEventRecord.OpType opType) {
            _opType = opType;
        }

        @Override
        public EnabledEventBuilder withAuth(Authentication authentication) {
            _auth = authentication;
            return this;
        }

        @Override
        public AuditEventBuilder withSecurityTag() {
            _tagType = LogAuditEventRecord.TagType.SECURITY;
            return this;
        }

        public AuditEventBuilder withEventId(LogAuditEventRecord.EventId eid) {
            _eventId = eid;
            return this;
        }

        @Override
        public AuditEventBuilder withUri(String uri, Object... formatArgs) {
            _uri = formatArgs != null && formatArgs.length > 0
                    ? uri.formatted(formatArgs)
                    : uri;
            return this;
        }

        @Override
        public AuditEventBuilder withBucket(String bucket) {
            _bucket = bucket;
            return this;
        }

        @Override
        public AuditEventBuilder withBucketKey(String bucketKey) {
            _bucketKey = bucketKey;
            return this;
        }

        @Override
        public void allowed() {
            logEvent(LogAuditEventRecord.ResType.ALLOW, null);
        }

        @Override
        public void allowed(String message, Object... formatArgs) {
            logEvent(LogAuditEventRecord.ResType.ALLOW, message, formatArgs);
        }

        @Override
        public void denied(String message, Object... formatArgs) {
            logEvent(LogAuditEventRecord.ResType.DENY, message, formatArgs);
        }

        @Override
        public void error(String message, Object... formatArgs) {
            logEvent(LogAuditEventRecord.ResType.ERROR, message, formatArgs);
        }

        private void logEvent(
                LogAuditEventRecord.ResType resType,
                String message,
                Object... formatArgs) {
            var formattedMessage = message != null && formatArgs != null && formatArgs.length > 0
                    ? message.formatted(formatArgs)
                    : message;

            var user = Optional.ofNullable(_auth)
                    .map(Authentication::getName)
                    .filter(s -> !s.isEmpty())
                    .orElseGet(() -> getCurrentLoggedInUser());

            log(_tagType, _opType, resType, _eventId, user, _uri, formattedMessage, _bucket, _bucketKey);
        }
    }
}
