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

package org.mitre.mpf.mvc.security;

import org.mitre.mpf.mvc.controller.RequestEventId;
import org.mitre.mpf.wfm.util.AuditEventLogger;
import org.springframework.web.method.HandlerMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class RestAuditLoggingInterceptor implements HandlerInterceptor {
    
    private final AuditEventLogger _auditEventLogger;
    
    public RestAuditLoggingInterceptor(AuditEventLogger auditEventLogger) {
        this._auditEventLogger = auditEventLogger;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {

        if (handler instanceof ResourceHttpRequestHandler) {
            return;
        }

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            throw new IllegalStateException("Invalid handler");
        }
        var eventIdAnnotation = handlerMethod.getMethodAnnotation(RequestEventId.class);
        if (eventIdAnnotation == null) {
            // If the method is not annotated, that means the audit logging
            // was already done. This is an exception to the rule, for cases
            // where we want to log some information that is only available
            // internal to the endpoint method.
            return;
        }
        var eventId = eventIdAnnotation.value();

        var method = request.getMethod();
        var uri = request.getRequestURI();

        if (response == null) {
            throw new IllegalStateException("REST Response is null");
        }
        int responseStatus = response.getStatus();

        if (responseStatus >= 400) {
            String err = eventId.message + " failed with response code = " + String.valueOf(responseStatus);
            if (ex != null) {
                err = err + ": " + ex.getMessage();
            }
            getAuditEventByHttpMethod(method)
                .withSecurityTag()
                .withEventId(eventId.fail)
                .withUri(uri)
                .error(err);
        }
        else {
            getAuditEventByHttpMethod(method)
                .withSecurityTag()
                .withEventId(eventId.success)
                .withUri(uri)
                .allowed(eventId.message + " succeeded");
        }
    }
    
    private AuditEventLogger.BuilderTagStage getAuditEventByHttpMethod(String httpMethod) {
        switch (httpMethod.toLowerCase()) {
            case "get": return _auditEventLogger.readEvent();
            case "post": return _auditEventLogger.createEvent();
            case "put": return _auditEventLogger.modifyEvent();
            case "delete": return _auditEventLogger.deleteEvent();
            case "head": return _auditEventLogger.readEvent();
            default:
                throw new IllegalArgumentException(String.format("AuditEvent method \"%s\" not supported", httpMethod));
        }
    }
}
