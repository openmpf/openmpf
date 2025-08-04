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

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;


@Profile("custom_sso")
@Service
public class CustomSsoBrowserFailureHandler extends CustomSsoBaseFailureHandler {

    private final CustomSsoProps _customSsoProps;

    @Inject
    CustomSsoBrowserFailureHandler(CustomSsoProps customSsoProps) {
        super(CustomSsoBrowserFailureHandler.class);
        _customSsoProps = customSsoProps;
    }


    @Override
    public void doCommence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {
        if (authException instanceof AuthServerReportedBadCredentialsException) {
            CustomSsoProps.storeErrorMessage(request.getSession(), authException.getMessage());
            response.sendRedirect("/custom_sso_error");
        }
        else {
            response.sendRedirect(_customSsoProps.getFullRedirectUri().toString());
        }
    }
}
