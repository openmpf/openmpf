package org.mitre.mpf.mvc.security;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;

import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CustomSsoProps {

    private static final Logger LOG = LoggerFactory.getLogger(CustomSsoProps.class);

    private static class EnvKeys {
        public static final String VALIDATION_URL = "CUSTOM_SSO_VALIDATION_URL";
        public static final String TOKEN_URL = "CUSTOM_SSO_TOKEN_URL";
        public static final String TOKEN_PROPERTY = "CUSTOM_SSO_TOKEN_PROPERTY";
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
    }


    public static boolean isEnabled() {
        var optEnvVal = getOptionalEnv(EnvKeys.VALIDATION_URL);
        if (optEnvVal.isEmpty()) {
            return false;
        }
        try {
            new URI(optEnvVal.get());
            return true;
        }
        catch (URISyntaxException e) {
            throw new IllegalArgumentException(
                "Expected the \"%s\" environment variable to be a valid URI, but it was \"%s\""
                .formatted(EnvKeys.VALIDATION_URL, optEnvVal.get()), e);
        }
    }


    public URI getValidationUri() {
        return getRequiredUriPropOrEnv(EnvKeys.VALIDATION_URL);
    }


    public URI getTokenUri() {
        return getRequiredUriPropOrEnv(EnvKeys.TOKEN_URL);
    }


    private static final Duration DEFAULT_LIFE_TIME = Duration.ofMinutes(5);

    public Duration getTokenLifeTime() {
        var optSecondsString = getOptionalPropOrEnv(EnvKeys.SSO_TOKEN_LIFE_TIME_SECONDS);
        if (optSecondsString.isEmpty()) {
            LOG.info(
                "No token lifetime provided. Using default of {} seconds",
                DEFAULT_LIFE_TIME.toSeconds());
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


    public String getTokenProperty() {
        return getRequiredPropOrEnv(EnvKeys.TOKEN_PROPERTY);
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
            .filter(s -> !s.isBlank()) ;
    }


    private static final Map<String, String> ENV_NAME_TO_PROP_NAME = new ConcurrentHashMap<>();

    private static String getPropName(String envName) {
        return ENV_NAME_TO_PROP_NAME.computeIfAbsent(
                envName, en -> en.toLowerCase().replace('_', '.'));
    }
}
