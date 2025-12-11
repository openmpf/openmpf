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

import org.mitre.mpf.wfm.util.AuditEventLogger;
import org.mitre.mpf.wfm.util.LogAuditEventRecord;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class RestAuditLoggingInterceptor implements HandlerInterceptor {
    
    private final AuditEventLogger _auditEventLogger;
    
    public RestAuditLoggingInterceptor(AuditEventLogger auditEventLogger) {
        this._auditEventLogger = auditEventLogger;
    }
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestURI = request.getRequestURI();
        String method = request.getMethod();

        getAuditEventByHttpMethod(method)
                .withSecurityTag()
                .withEventId(LogAuditEventRecord.EventId.REST_API_ACCESS)
                .withUri(requestURI)
                .allowed();
        return true;
    }
    
    private AuditEventLogger.BuilderTagStage getAuditEventByHttpMethod(String httpMethod) {
        return switch (httpMethod.toLowerCase()) {
            case "get" -> _auditEventLogger.readEvent();
            case "post" -> _auditEventLogger.createEvent();
            case "put" -> _auditEventLogger.modifyEvent();
            case "delete" -> _auditEventLogger.deleteEvent();
            default -> _auditEventLogger.readEvent();
        };
    }
}
