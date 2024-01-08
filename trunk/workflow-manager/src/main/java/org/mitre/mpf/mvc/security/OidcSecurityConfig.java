/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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

import static java.util.stream.Collectors.toSet;

import java.util.Optional;
import java.util.stream.Stream;

import org.mitre.mpf.wfm.enums.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@Profile("oidc")
public class OidcSecurityConfig {

    private static final Logger LOG = LoggerFactory.getLogger(OidcSecurityConfig.class);

    private static class Keys {
        public static final String
            ISSUER_URI = "OIDC_ISSUER_URI",
            JWT_ISSUER_URI = "OIDC_JWT_ISSUER_URI",
            REDIRECT_URI = "OIDC_REDIRECT_URI",
            CLIENT_ID = "OIDC_CLIENT_ID",
            CLIENT_SECRET = "OIDC_CLIENT_SECRET",
            SCOPES = "OIDC_SCOPES",
            USER_NAME_ATTR = "OIDC_USER_NAME_ATTR",
            USER_CLAIM_NAME = "OIDC_USER_CLAIM_NAME",
            USER_CLAIM_VALUE = "OIDC_USER_CLAIM_VALUE",
            ADMIN_CLAIM_NAME = "OIDC_ADMIN_CLAIM_NAME",
            ADMIN_CLAIM_VALUE = "OIDC_ADMIN_CLAIM_VALUE";
    }

    public static boolean isEnabled() {
        return getOptionalEnv(Keys.ISSUER_URI).isPresent();
    }


    @Bean
    public OidcClaimConfig claimConfig() {
        var adminClaimName = getOptionalEnv(Keys.ADMIN_CLAIM_NAME);
        var userClaimName = getOptionalEnv(Keys.USER_CLAIM_NAME);
        if (adminClaimName.isEmpty() && userClaimName.isEmpty()) {
            throw new IllegalStateException(
                "OIDC is enabled, but the \"%s\" and \"%s\" environment variable are not set."
                .formatted(Keys.ADMIN_CLAIM_NAME, Keys.USER_CLAIM_NAME));
        }

        var adminClaimValue = getOptionalEnv(Keys.ADMIN_CLAIM_VALUE);
        if (adminClaimName.isPresent() && adminClaimValue.isEmpty()) {
            throw new IllegalStateException(
                "When the \"%s\" environment variable is set, \"%s\" must also be set."
                .formatted(Keys.ADMIN_CLAIM_NAME, Keys.ADMIN_CLAIM_VALUE));
        }

        var userClaimValue = getOptionalEnv(Keys.USER_CLAIM_VALUE);
        if (userClaimName.isPresent() && userClaimValue.isEmpty()) {
            throw new IllegalStateException(
                "When the \"%s\" environment variable is set, \"%s\" must also be set."
                .formatted(Keys.USER_CLAIM_NAME, Keys.USER_CLAIM_VALUE));
        }

        if (adminClaimName.isPresent() && userClaimName.isEmpty()) {
            LOG.warn(
                "\"{}\" was not set. Only admin users can log in.",
                Keys.USER_CLAIM_NAME);
        }
        if (userClaimName.isPresent() && adminClaimName.isEmpty()) {
            LOG.warn(
                "\"{}\" was not set. No admin users can log in.",
                Keys.ADMIN_CLAIM_NAME);
        }

        return new OidcClaimConfig(
            adminClaimName, adminClaimValue, userClaimName, userClaimValue);
    }


    @Bean
    @Order(1)
    public SecurityFilterChain restSecurityFilterChain(
            HttpSecurity http,
            OidcAuthenticationManager oidcAuthenticationManager,
            JwtAccessDeniedHandler jwtAccessDeniedHandler) throws Exception {

        var jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(oidcAuthenticationManager);

        return http.securityMatcher("/rest/**")
            .authorizeHttpRequests(x -> x.anyRequest().access(oidcAuthenticationManager))
            .oauth2ResourceServer(x ->
                x.jwt(c ->
                    c.jwtAuthenticationConverter(jwtAuthenticationConverter)))
            .exceptionHandling(x -> x.accessDeniedHandler(jwtAccessDeniedHandler))
            .csrf(x -> x.disable())
            .build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        var jwtIssuerUri = getOptionalEnv(Keys.JWT_ISSUER_URI)
                .orElseGet(() -> getRequiredEnv(Keys.ISSUER_URI));
        // Normally, OIDC is configured using application.properties. Since we are using
        // environment variables, we need to manually create the JwtDecoder.
        return JwtDecoders.fromIssuerLocation(jwtIssuerUri);
    }


    @Bean
    @Order(2)
    public SecurityFilterChain formSecurityFilterChain(
            HttpSecurity http,
            ClientRegistrationRepository clientRegistrationRepository,
            OidcAuthenticationManager oidcAuthenticationManager,
            AjaxAuthenticationEntrypoint ajaxAuthenticationEntrypoint) throws Exception {

        var logoutHandler = new OidcClientInitiatedLogoutSuccessHandler(
                clientRegistrationRepository);
        logoutHandler.setPostLogoutRedirectUri("{baseUrl}");

        return http
            .authorizeHttpRequests(x ->
                x.requestMatchers("/login/**", "/resources/**", "/oidc-access-denied")
                    .permitAll()
                .requestMatchers("/actuator/hawtio/**").hasAnyAuthority(UserRole.ADMIN.springName)
                .anyRequest().access(oidcAuthenticationManager))
            .oauth2Login(x ->
                x.userInfoEndpoint(c ->
                    c.userAuthoritiesMapper(oidcAuthenticationManager)))
            .logout(x -> x.logoutSuccessHandler(logoutHandler))
            .exceptionHandling(x ->
                x.accessDeniedPage("/oidc-access-denied")
                .defaultAuthenticationEntryPointFor(
                        ajaxAuthenticationEntrypoint, ajaxAuthenticationEntrypoint))
            // Hawtio requires CookieCsrfTokenRepository.withHttpOnlyFalse().
            .csrf(x -> x.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .build();
    }


    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        // Normally, OIDC is configured using application.properties. Since we are using
        // environment variables, we need to manually create the ClientRegistrationRepository
        // and ClientRegistration.
        Stream<String> envScopes = getOptionalEnv(Keys.SCOPES)
                .stream()
                .flatMap(sc -> Stream.of(sc.split(",")))
                .map(String::strip)
                .filter(s -> !s.isBlank());

        var scopes = Stream.concat(
                    envScopes,
                    // OIDC requires the "openid" scope.
                    Stream.of("openid"))
                .collect(toSet());

        var registration = ClientRegistrations
                .fromOidcIssuerLocation(getRequiredEnv(Keys.ISSUER_URI))
                .registrationId("provider")
                .clientId(getRequiredEnv(Keys.CLIENT_ID))
                .clientSecret(getRequiredEnv(Keys.CLIENT_SECRET))
                .scope(scopes);

        getOptionalEnv(Keys.REDIRECT_URI)
                .ifPresent(registration::redirectUri);
        getOptionalEnv(Keys.USER_NAME_ATTR)
                .ifPresent(registration::userNameAttributeName);

        var clientCredentialsRegistration = ClientRegistrations
                .fromOidcIssuerLocation(getRequiredEnv(Keys.ISSUER_URI))
                .registrationId(OAuthClientTokenProvider.REGISTRATION_ID)
                .clientId(getRequiredEnv(Keys.CLIENT_ID))
                .clientSecret(getRequiredEnv(Keys.CLIENT_SECRET))
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS);

        return new InMemoryClientRegistrationRepository(
                registration.build(), clientCredentialsRegistration.build());
    }


    @Bean
    public AuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientServiceAndManager(
            ClientRegistrationRepository clientRegistrationRepository) {
        var authorizedClientService = new InMemoryOAuth2AuthorizedClientService(
                clientRegistrationRepository);
        return new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                clientRegistrationRepository, authorizedClientService);
    }


    private static final String getRequiredEnv(String varName) {
        return getOptionalEnv(varName)
            .orElseThrow(() -> new IllegalStateException(
                        "OIDC is enabled, but the \"%s\" environment variable is not set."
                        .formatted(varName)));
    }

    private static final Optional<String> getOptionalEnv(String varName) {
        return Optional.ofNullable(System.getenv(varName))
                .filter(s -> !s.isBlank());
    }
}
