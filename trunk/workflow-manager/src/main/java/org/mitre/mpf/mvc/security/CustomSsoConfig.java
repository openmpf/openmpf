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
import java.util.Optional;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

@Configuration
@Profile("custom_sso")
public class CustomSsoConfig {

    public static boolean isEnabled() {
        return CustomSsoProps.isEnabled();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain restSecurityFilterChain(
            HttpSecurity http,
            CustomSsoTokenValidator customSsoTokenValidator) throws Exception {
        var failureHandler = new FailureHandler();
        return http.antMatcher("/rest/**")
            .authorizeHttpRequests(x -> x.anyRequest().authenticated())
            .csrf(x -> x.disable())
            .addFilter(preAuthFilter(customSsoTokenValidator, failureHandler))
            .exceptionHandling(x -> x.authenticationEntryPoint(failureHandler))
            .build();
    }

    private static AbstractPreAuthenticatedProcessingFilter preAuthFilter(
            CustomSsoTokenValidator customSsoTokenValidator,
            FailureHandler failureHandler) {
        var authFilter = new AbstractPreAuthenticatedProcessingFilter() {
            @Override
            protected Object getPreAuthenticatedPrincipal(HttpServletRequest request) {
                return "REST Client";
            }

            @Override
            protected Object getPreAuthenticatedCredentials(HttpServletRequest request) {
                return request.getHeader("Authorization");
            }
        };
        authFilter.setAuthenticationManager(customSsoTokenValidator::authenticate);
        authFilter.setAuthenticationFailureHandler(failureHandler);
        return authFilter;
    }

    private static class FailureHandler
            implements AuthenticationEntryPoint, AuthenticationFailureHandler {

        @Override
        public void onAuthenticationFailure(
                HttpServletRequest request,
                HttpServletResponse response,
                AuthenticationException exception) {
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
                AuthenticationException authException) throws IOException {
            var message = getException(request)
                .orElse(authException)
                .getMessage();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            try (var respOutputStream = response.getOutputStream()) {
                respOutputStream.println(message);
            }
        }

        private static final String EXCEPTION_ATTR = FailureHandler.class.getName() + ".exception";

        private void setException(ServletRequest request, AuthenticationException exception) {
            request.setAttribute(EXCEPTION_ATTR, exception);
        }

        private static Optional<AuthenticationException> getException(ServletRequest request) {
            return Optional.ofNullable(
                (AuthenticationException) request.getAttribute(EXCEPTION_ATTR));
        }
    }
}
