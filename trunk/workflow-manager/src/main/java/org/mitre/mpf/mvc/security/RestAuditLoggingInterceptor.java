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
    
    private final AuditEventLogger auditEventLogger;
    
    public RestAuditLoggingInterceptor(AuditEventLogger auditEventLogger) {
        this.auditEventLogger = auditEventLogger;
    }
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestURI = request.getRequestURI();
        String method = request.getMethod();
        String logMessage = String.format("Method: %s RequestURI: %s", method, requestURI);
        
        auditEventLogger.log(LogAuditEventRecord.TagType.SECURITY, 
                getOperationType(method), 
                LogAuditEventRecord.ResType.ALLOW, 
                logMessage);
        
        return true;
    }
    
    private LogAuditEventRecord.OpType getOperationType(String httpMethod) {
        return switch (httpMethod.toLowerCase()) {
            case "get" -> LogAuditEventRecord.OpType.READ;
            case "post" -> LogAuditEventRecord.OpType.CREATE;
            case "put" -> LogAuditEventRecord.OpType.MODIFY;
            case "delete" -> LogAuditEventRecord.OpType.DELETE;
            default -> LogAuditEventRecord.OpType.READ;
        };
    }
}