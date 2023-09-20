package org.mitre.mpf.mvc.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.apache.http.client.methods.HttpUriRequest;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Component;


@Component
public class OAuthClientTokenProvider {

    private static final Logger LOG = LoggerFactory.getLogger(OAuthClientTokenProvider.class);

    public static final String REGISTRATION_ID = "client-auth-registration";

    private final OAuth2AuthorizedClientManager _clientManager;

    private final OAuth2AuthorizeRequest _authorizeRequest = OAuth2AuthorizeRequest
            .withClientRegistrationId(REGISTRATION_ID)
            .principal("wfm")
            .build();


    @Inject
    OAuthClientTokenProvider(@Nullable OAuth2AuthorizedClientManager clientManager) {
        _clientManager = clientManager;
    }

    public void addToken(HttpUriRequest request) {
        if (_clientManager == null) {
            // OIDC is disabled.
            return;
        }
        try {
            LOG.info("Getting OAuth token for HTTP request to: {}", request.getURI());
            var client = _clientManager.authorize(_authorizeRequest);
            Objects.requireNonNull(client);
            var token = client.getAccessToken();
            LOG.info("Using token that expires in {} minutes.", getMinutesRemaining(token));
            request.addHeader("Authorization", "Bearer " + token.getTokenValue());
        }
        catch (ClientAuthorizationException e) {
            throw new WfmProcessingException(
                "Failed to get authorization token for HTTP request to %s due to: %s"
                .formatted(request.getURI(), e), e);
        }
    }

    private String getMinutesRemaining(OAuth2AccessToken token) {
        var durationRemaining = Duration.between(Instant.now(), token.getExpiresAt());
        return "%s:%02d".formatted(
                durationRemaining.toMinutes(), durationRemaining.toSecondsPart());
    }
}
