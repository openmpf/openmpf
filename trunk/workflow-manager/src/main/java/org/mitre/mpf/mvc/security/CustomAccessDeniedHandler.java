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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Service;

@Service("CustomAccessDeniedHandler")
public class CustomAccessDeniedHandler extends AccessDeniedHandlerImpl {

    private final AuditEventLogger auditEventLogger;
    
    @Autowired
    public CustomAccessDeniedHandler(AuditEventLogger auditEventLogger) {
        this.auditEventLogger = auditEventLogger;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {

        if (!(accessDeniedException instanceof CsrfException)) {
            super.handle(request, response, accessDeniedException);
        }
        else if (isAjax(request)) {
            response.sendError(403, "INVALID_CSRF_TOKEN");
        }
        else if ("/login".equals(request.getRequestURI())) {
            // CSRF failures on login page
            auditEventLogger.log(
                LogAuditEventRecord.TagType.SECURITY,
                LogAuditEventRecord.OpType.LOGIN,
                LogAuditEventRecord.ResType.DENY,
                "Login attempt failed: Invalid XSRF token"
            );
            
            response.sendRedirect("/");
        }
        else {
            super.handle(request, response, accessDeniedException);
        }
    }


    private static boolean isAjax(HttpServletRequest request) {
        return "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
    }
}
