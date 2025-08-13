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
import java.util.Optional;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;


public abstract class BaseCustomSsoService
        extends AbstractPreAuthenticatedProcessingFilter
        implements AuthenticationProvider, AuthenticationEntryPoint, AuthenticationFailureHandler {


    protected BaseCustomSsoService(AuthenticationEventPublisher authEventPublisher) {
        var providerManager = new ProviderManager(this);
        providerManager.setAuthenticationEventPublisher(authEventPublisher);
        setAuthenticationManager(providerManager);
        setAuthenticationFailureHandler(this);
    }


    @Override
    public boolean supports(Class<?> authentication) {
        return PreAuthenticatedAuthenticationToken.class.isAssignableFrom(authentication);
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        // Trying to send an error response here will result in a Spring error. To send a
        // specific error response, it must be sent in AuthenticationEntryPoint.commence.
        // The exception here contains an error message describing why token validation failed,
        // but the exception passed to AuthenticationEntryPoint.commence contains a generic
        // error message. We store the exception as a request attribute so that it can be
        // retrieved during commence.
        setException(request, exception);
    }


    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {
        var exception = getException(request).orElse(authException);
        handleAuthCommence(request, response, exception);
    }


    protected abstract void handleAuthCommence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException;


    private static final String EXCEPTION_ATTR
            = BaseCustomSsoService.class.getName() + ".exception";

    private void setException(ServletRequest request, AuthenticationException exception) {
        request.setAttribute(EXCEPTION_ATTR, exception);
    }

    private Optional<AuthenticationException> getException(ServletRequest request) {
        return Optional.ofNullable(
                (AuthenticationException) request.getAttribute(EXCEPTION_ATTR));
    }
}
