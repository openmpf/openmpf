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

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.servlet.http.HttpSession;

import org.apache.http.client.utils.URIBuilder;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("custom_sso")
public class CustomSsoProps {

    private static final Logger LOG = LoggerFactory.getLogger(CustomSsoProps.class);

    private static class EnvKeys {
        public static final String VALIDATION_URL = "CUSTOM_SSO_VALIDATION_URL";
        public static final String TOKEN_URL = "CUSTOM_SSO_TOKEN_URL";
        public static final String TOKEN_PROPERTY = "CUSTOM_SSO_TOKEN_PROPERTY";
        public static final String USER_ID_PROPERTY = "CUSTOM_SSO_USER_ID_PROPERTY";
        public static final String DISPLAY_NAME_PROPERTY = "CUSTOM_SSO_DISPLAY_NAME_PROPERTY";

        public static final String SSO_LOG_IN_URL = "CUSTOM_SSO_LOG_IN_URL";
        public static final String SSO_RETURN_URL = "CUSTOM_SSO_RETURN_URL";
        public static final String SSO_LOG_OUT_URL = "CUSTOM_SSO_LOG_OUT_URL";

        public static final String SSO_TOKEN_LIFE_TIME_SECONDS = "CUSTOM_SSO_TOKEN_LIFE_TIME_SECONDS";
        public static final String SSO_USER = "CUSTOM_SSO_USER";
        public static final String SSO_PASSWORD = "CUSTOM_SSO_PASSWORD";


        private EnvKeys(){
        }
    }

    private final PropertiesUtil _propertiesUtil;

    @Inject
    CustomSsoProps(PropertiesUtil propertiesUtil) {
        _propertiesUtil = propertiesUtil;
        validateProps();
    }


    public static boolean isEnabled() {
        return getOptionalEnv(EnvKeys.VALIDATION_URL).isPresent();
    }

    private void validateProps() {
        getValidationUri();
        getTokenUri();
        getTokenProperty();
        getFullRedirectUri();
        validateTokenLifeTime();
        getSsoUser();
        getSsoPassword();
    }


    public URI getValidationUri() {
        return getRequiredUriPropOrEnv(EnvKeys.VALIDATION_URL);
    }


    public URI getTokenUri() {
        return getRequiredUriPropOrEnv(EnvKeys.TOKEN_URL);
    }


    public URI getFullRedirectUri() {
        var loginUri = getRequiredUriPropOrEnv(EnvKeys.SSO_LOG_IN_URL);
        var returnUri = getRequiredUriPropOrEnv(EnvKeys.SSO_RETURN_URL);
        try {
            return new URIBuilder(loginUri)
                .addParameter("return_to", returnUri.toString())
                .build();
        }
        catch (URISyntaxException e) {
            // Should be impossible because loginUri is already a URI.
            throw new WfmProcessingException("The Custom SSO login uri was invalid.");
        }
    }

    public URI getLogoutUri() {
        return getRequiredUriPropOrEnv(EnvKeys.SSO_LOG_OUT_URL);
    }


    private static final Duration DEFAULT_LIFE_TIME = Duration.ofMinutes(5);

    public Duration getTokenLifeTime() {
        var optSecondsString = getOptionalPropOrEnv(EnvKeys.SSO_TOKEN_LIFE_TIME_SECONDS);
        if (optSecondsString.isEmpty()) {
            return DEFAULT_LIFE_TIME;
        }
        try {
            return Duration.ofSeconds(Integer.parseInt(optSecondsString.get()));
        }
        catch (NumberFormatException e) {
            LOG.error(
                "Token life time was not an integer. Using default of %s seconds"
                .formatted(DEFAULT_LIFE_TIME.toSeconds()), e);
            return DEFAULT_LIFE_TIME;
        }
    }

    private void validateTokenLifeTime() {
        var optSecondsString = getOptionalPropOrEnv(EnvKeys.SSO_TOKEN_LIFE_TIME_SECONDS);
        if (optSecondsString.isPresent()) {
            getTokenLifeTime();
        }
        else {
            LOG.warn("{} was not provided. Using default of {} seconds.",
                    EnvKeys.SSO_TOKEN_LIFE_TIME_SECONDS, DEFAULT_LIFE_TIME.toSeconds());
        }
    }


    public String getTokenProperty() {
        return getRequiredPropOrEnv(EnvKeys.TOKEN_PROPERTY);
    }

    public String getUserIdProperty() {
        return getRequiredPropOrEnv(EnvKeys.USER_ID_PROPERTY);
    }

    public String getDisplayNameProperty() {
        return getOptionalPropOrEnv(EnvKeys.DISPLAY_NAME_PROPERTY)
            .orElseGet(this::getUserIdProperty);
    }


    public String getSsoUser() {
        return getRequiredPropOrEnv(EnvKeys.SSO_USER);
    }

    public String getSsoPassword()  {
        return getRequiredPropOrEnv(EnvKeys.SSO_PASSWORD);
    }

    public int getHttpRetryCount() {
        return _propertiesUtil.getHttpCallbackRetryCount();
    }

    private static final String SSO_ERROR_ATTR = CustomSsoProps.class.getName() + ".custom-sso-error";
    public static void storeErrorMessage(HttpSession session, String message) {
        session.setAttribute(SSO_ERROR_ATTR, message);
    }

    public static Optional<String> getStoredErrorMessage(HttpSession session) {
        return Optional.ofNullable((String) session.getAttribute(SSO_ERROR_ATTR));
    }

    private Optional<String> getOptionalPropOrEnv(String envName) {
        return toNonBlank(_propertiesUtil.lookup(getPropName(envName)))
            .or(() -> getOptionalEnv(envName));
    }


    private String getRequiredPropOrEnv(String envName) {
        return getOptionalPropOrEnv(envName)
            .orElseThrow(() -> new IllegalStateException(
                "Expected either the \"%s\" system property or the \"%s\" environment variable to be set to a non-blank string."
                .formatted(getPropName(envName), envName)));
    }

    private URI getRequiredUriPropOrEnv(String envName) {
        try {
            return new URI(getRequiredPropOrEnv(envName));
        }
        catch (URISyntaxException e) {
            throw new IllegalStateException(
                "Expected either the \"%s\" system property or the \"%s\" environment variable to be set to a valid URI."
                .formatted(getPropName(envName), envName), e);
        }
    }


    private static final Optional<String> getOptionalEnv(String varName) {
        return toNonBlank(System.getenv(varName))
            .or(() -> toNonBlank(System.getenv("MPF_PROP_" + varName)));
    }

    private static Optional<String> toNonBlank(String string)  {
        return Optional.ofNullable(string)
            .map(String::strip)
            .filter(s -> !s.isBlank());
    }


    private static final Map<String, String> ENV_NAME_TO_PROP_NAME = new ConcurrentHashMap<>();

    private static String getPropName(String envName) {
        return ENV_NAME_TO_PROP_NAME.computeIfAbsent(
                envName, en -> en.toLowerCase().replace('_', '.'));
    }
}
