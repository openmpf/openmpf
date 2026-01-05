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

package org.mitre.mpf.mvc.security.custom.sso;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mitre.mpf.wfm.util.AuditEventLogger;
import org.mitre.mpf.wfm.util.LogAuditEventRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;


@Service
@Profile("custom_sso")
public class CustomSsoBrowserService extends BaseCustomSsoService {

    private final CustomSsoTokenValidator _customSsoTokenValidator;

    private final CustomSsoProps _customSsoProps;

    private final AuditEventLogger _auditLogger;

    @Inject
    CustomSsoBrowserService(
            AuthenticationEventPublisher authEventPublisher,
            CustomSsoTokenValidator customSsoTokenValidator,
            CustomSsoProps customSsoProps,
            AuditEventLogger auditLogger) {
        super(authEventPublisher);
        _customSsoTokenValidator = customSsoTokenValidator;
        _customSsoProps = customSsoProps;
        _auditLogger = auditLogger;
    }


    @Override
    protected Object getPreAuthenticatedPrincipal(HttpServletRequest request) {
        // We expect that users may arrive without a cookie, so we do not treat that as an error.
        // We return null in that case to indicate that we need to start the authentication process
        // by redirecting the user to the SSO login page.
        return getTokenFromCookie(request)
                .map(s -> "Unauthenticated SSO user")
                .orElse(null);
    }

    @Override
    protected Object getPreAuthenticatedCredentials(HttpServletRequest request) {
        return getTokenFromCookie(request)
                .orElse(null);
    }

    private Optional<String> getTokenFromCookie(HttpServletRequest request) {
        var tokenProp = _customSsoProps.getTokenProperty();
        return Stream.ofNullable(request.getCookies())
            .flatMap(Arrays::stream)
            .filter(c -> c.getName().equals(tokenProp))
            .findFirst()
            .map(Cookie::getValue);
    }


    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        return _customSsoTokenValidator.authenticateCookie(authentication);
    }

    @Override
    protected void handleAuthCommence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {
        if (authException instanceof AuthServerReportedBadCredentialsException) {
            CustomSsoProps.storeErrorMessage(request.getSession(), authException.getMessage());
            response.sendRedirect("/custom_sso_error");
        }
        else {
            var redirectDest = _customSsoProps.getFullRedirectUri().toString();
            _auditLogger.loginEvent()
                .withSecurityTag()
                .withEventId(LogAuditEventRecord.EventId.SSO_ACCESS.fail)
                .denied("User does not have an SSO token. Redirecting to %s", redirectDest);
            response.sendRedirect(redirectDest);
        }
    }
}
