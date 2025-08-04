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

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Profile;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.stereotype.Service;

@Service
@Profile("custom_sso")
public class CustomSsoBrowserAuthFilter extends AbstractPreAuthenticatedProcessingFilter {

    private final CustomSsoProps _customSsoProps;

    @Inject
    CustomSsoBrowserAuthFilter(
            CustomSsoTokenValidator customSsoTokenValidator,
            CustomSsoBrowserFailureHandler failureHandler,
            CustomSsoProps customSsoProps) {
        setAuthenticationManager(customSsoTokenValidator::authenticateCookie);
        setAuthenticationFailureHandler(failureHandler);
        _customSsoProps = customSsoProps;
    }

    @Override
    protected Object getPreAuthenticatedPrincipal(HttpServletRequest request) {
        return _customSsoProps.getTokenFromCookie(request)
            .map(s -> "SSO user")
            .orElse(null);
    }

    @Override
    protected Object getPreAuthenticatedCredentials(HttpServletRequest request) {
        return _customSsoProps.getTokenFromCookie(request)
                .orElse(null);
    }
}
