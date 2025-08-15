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
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class RestEndpointLoggingFilter extends OncePerRequestFilter {
    
    private final AuditEventLogger auditEventLogger;
    
    public RestEndpointLoggingFilter(AuditEventLogger auditEventLogger) {
        this.auditEventLogger = auditEventLogger;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String requestURI = request.getRequestURI();
        String method = request.getMethod();
        
        if (isRestEndpoint(requestURI)) {
            
            try {
                filterChain.doFilter(request, response);
                
                int statusCode = response.getStatus();
                
                LogAuditEventRecord.ResType result = statusCode >= 400 ? 
                    LogAuditEventRecord.ResType.ERROR : LogAuditEventRecord.ResType.ALLOW;
                
                String logMessage = String.format("Method: %s RequestURI: %s Status Code: %d ", 
                    method, requestURI, statusCode);
                
                auditEventLogger.log(LogAuditEventRecord.TagType.SECURITY, 
                    getOperationType(method), 
                    result, 
                    logMessage);
                    
            } catch (Exception e) {
                String logMessage = String.format("Method: %s RequestURI: %s - ERROR : %s", 
                    method, requestURI, e.getMessage());
                
                auditEventLogger.log(LogAuditEventRecord.TagType.SECURITY, 
                    getOperationType(method), 
                    LogAuditEventRecord.ResType.ERROR, 
                    logMessage);
                    
                throw e;
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }
    
    private boolean isRestEndpoint(String requestURI) {
        return requestURI.startsWith("/rest/") ||
               requestURI.startsWith("/jobs") ||
               requestURI.startsWith("/pipelines") ||
               requestURI.startsWith("/tasks") ||
               requestURI.startsWith("/actions") ||
               requestURI.startsWith("/algorithms") ||
               requestURI.startsWith("/nodes") ||
               requestURI.startsWith("/components") ||
               requestURI.startsWith("/properties") ||
               requestURI.startsWith("/server/") ||
               requestURI.startsWith("/markup/") ||
               requestURI.startsWith("/streaming/") ||
               requestURI.startsWith("/subject/") ||
               requestURI.startsWith("/upload/") ||
               requestURI.startsWith("/saveURL") ||
               requestURI.startsWith("/uploadFile") ||
               requestURI.startsWith("/media/") ||
               requestURI.startsWith("/system-message") ||
               requestURI.startsWith("/adminLogs") ||
               requestURI.startsWith("/adminStatistics") ||
               requestURI.startsWith("/info") ||
               requestURI.startsWith("/user/role-info") ||
               requestURI.startsWith("/actuator/hawtio") ||
               requestURI.startsWith("/actuator/jolokia") ||
               requestURI.startsWith("/swagger") ||
               requestURI.startsWith("/v2/api-docs") ||
               requestURI.startsWith("/v3/api-docs") ||
               requestURI.startsWith("/swagger-ui") ||
               requestURI.startsWith("/swagger-resources");
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