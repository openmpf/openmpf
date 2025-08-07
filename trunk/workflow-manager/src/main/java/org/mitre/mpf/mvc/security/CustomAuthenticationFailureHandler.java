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

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mitre.mpf.wfm.util.AuditEventLogger;
import org.mitre.mpf.wfm.util.LogAuditEventRecord;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class CustomAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final AuditEventLogger auditEventLogger;

    public CustomAuthenticationFailureHandler(AuditEventLogger auditEventLogger) {
        this.auditEventLogger = auditEventLogger;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                      AuthenticationException exception) throws IOException, ServletException {
        
        if (exception instanceof BadCredentialsException) {
            String attemptedUsername = request.getParameter("username");
            String badCredentialsMessage;
            if (attemptedUsername != null && !attemptedUsername.trim().isEmpty()) {
                badCredentialsMessage = "Failed login attempt for user '" + attemptedUsername + "': Invalid username and/or password.";
            } else {
                badCredentialsMessage = "Failed login attempt: Invalid username and/or password.";
            }
            auditEventLogger.log(LogAuditEventRecord.TagType.SECURITY, LogAuditEventRecord.OpType.LOGIN, 
                               LogAuditEventRecord.ResType.DENY, badCredentialsMessage);
        }
        
        response.sendRedirect("/login?reason=error");
    }
}