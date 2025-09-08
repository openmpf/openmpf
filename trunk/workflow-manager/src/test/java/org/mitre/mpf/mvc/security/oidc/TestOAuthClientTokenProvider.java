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

package org.mitre.mpf.mvc.security.oidc;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.apache.http.client.methods.HttpGet;
import org.junit.Test;
import org.mitre.mpf.test.MockitoTest;
import org.mockito.Mock;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType;

public class TestOAuthClientTokenProvider extends MockitoTest.Strict {

    @Mock
    private OAuth2AuthorizedClientManager _mockClientManager;


    @Test
    public void doesNothingWhenOidcDisabled() {
        var request = new HttpGet("http://localhost");
        var tokenProvider = new OAuthClientTokenProvider(null);
        tokenProvider.addToken(request);
        assertEquals(0, request.getAllHeaders().length);
    }

    @Test
    public void addsAuthHeaderToRequest() {
        var mockClient = mock(OAuth2AuthorizedClient.class);
        var expectedToken = new OAuth2AccessToken(
                TokenType.BEARER, "theTokenValue", Instant.now(), Instant.now());
        when(mockClient.getAccessToken())
                .thenReturn(expectedToken);

        when(_mockClientManager.authorize(any(OAuth2AuthorizeRequest.class)))
                .thenReturn(mockClient);

        var request = new HttpGet("http://localhost");
        var tokenProvider = new OAuthClientTokenProvider(_mockClientManager);
        tokenProvider.addToken(request);

        var authHeaders = request.getHeaders("Authorization");
        assertEquals(1, authHeaders.length);
        var authHeaderValue = authHeaders[0].getValue();
        assertEquals("Bearer theTokenValue", authHeaderValue);
    }
}
