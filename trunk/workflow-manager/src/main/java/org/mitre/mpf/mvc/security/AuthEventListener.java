/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2025 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2025 The MITRE Corporation                                       *
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
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.stereotype.Component;

@Component
public class AuthEventListener {

    private final AuditEventLogger _auditEventLogger;

    AuthEventListener(AuditEventLogger auditEventLogger) {
        _auditEventLogger = auditEventLogger;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent success) {
        _auditEventLogger.loginEvent()
            .withSecurityTag()
            .withAuth(success.getAuthentication())
            .allowed("User successfully logged in.");
    }


    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        _auditEventLogger.loginEvent()
            .withSecurityTag()
            .withAuth(event.getAuthentication())
            .denied("Authentication failed: %s", event.getException().getMessage());
    }


    @EventListener
    public void onLogout(LogoutSuccessEvent logoutEvent) {
        _auditEventLogger.loginEvent()
            .withSecurityTag()
            .withAuth(logoutEvent.getAuthentication())
            .allowed("User logged out.");
    }
}
