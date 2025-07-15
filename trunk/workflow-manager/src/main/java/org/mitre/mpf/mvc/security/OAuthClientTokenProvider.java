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

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import javax.inject.Inject;

import org.apache.http.client.methods.HttpUriRequest;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.http.SdkHttpFullRequest;


@Component
@Profile("oidc")
public class OAuthClientTokenProvider implements ITokenProvider {

    private static final Logger LOG = LoggerFactory.getLogger(OAuthClientTokenProvider.class);

    public static final String REGISTRATION_ID = "client-auth-registration";

    private final OAuth2AuthorizedClientManager _clientManager;

    private final OAuth2AuthorizeRequest _authorizeRequest = OAuth2AuthorizeRequest
            .withClientRegistrationId(REGISTRATION_ID)
            .principal("wfm")
            .build();


    @Inject
    OAuthClientTokenProvider(OAuth2AuthorizedClientManager clientManager) {
        _clientManager = clientManager;
    }

    @Override
    public void addToken(HttpUriRequest request) {
        if (_clientManager == null) {
            LOG.warn("HTTP request to {} was configured to include an OAuth token, but"
                    + " Workflow Manager is not configured to use OIDC.", request.getURI());
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


    @Override
    public void addToken(SdkHttpFullRequest.Builder requestBuilder) {
        // There is no way to use S3 with OAuth.
    }
}
