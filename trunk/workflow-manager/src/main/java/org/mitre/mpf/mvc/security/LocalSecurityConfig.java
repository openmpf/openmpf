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

import org.mitre.mpf.wfm.enums.UserRole;
import org.mitre.mpf.wfm.util.JsonLogger;
import org.mitre.mpf.wfm.util.LogEventRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@Profile("(!oidc & !jenkins) | test-with-security")
public class LocalSecurityConfig {

    private final JsonLogger jsonLogger;

    public LocalSecurityConfig(JsonLogger jsonLogger) {
        this.jsonLogger = jsonLogger;
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            jsonLogger.log(LogEventRecord.TagType.SECURITY, LogEventRecord.OpType.LOGIN, LogEventRecord.ResType.ACCESS, "User successfully logged in.");
            response.sendRedirect("/");
        };
    }

    @Bean
    @Order(1)
    public SecurityFilterChain restSecurityFilterChain(
            HttpSecurity http, RestBasicAuthEntryPoint restBasicAuthEntryPoint) throws Exception {

        return http.antMatcher("/rest/**")
            .authorizeHttpRequests(x -> x.anyRequest().authenticated())
            .httpBasic(x -> x.authenticationEntryPoint(restBasicAuthEntryPoint))
            .csrf(x -> x.disable())
            .build();
    }


    @Bean
    @Order(2)
    public SecurityFilterChain formSecurityFilterChain(
            HttpSecurity http,
            CustomAccessDeniedHandler customAccessDeniedHandler,
            AjaxAuthenticationEntrypoint ajaxAuthenticationEntrypoint,
            AuthenticationSuccessHandler authenticationSuccessHandler) throws Exception {

        return http.authorizeHttpRequests(x ->
                x.antMatchers("/login/**", "/resources/**").permitAll()
                .antMatchers("/actuator/hawtio/**").hasAnyAuthority(UserRole.ADMIN.springName)
                .anyRequest().authenticated())
            .formLogin(x ->
                x.loginPage("/login")
                .successHandler(authenticationSuccessHandler)
                .failureUrl("/login?reason=error"))
            .exceptionHandling(x ->
                x.accessDeniedHandler(customAccessDeniedHandler)
                .defaultAuthenticationEntryPointFor(
                        ajaxAuthenticationEntrypoint, ajaxAuthenticationEntrypoint)
                )
            // Hawtio requires CookieCsrfTokenRepository.withHttpOnlyFalse().
            .csrf(x -> x.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .build();
    }


    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
