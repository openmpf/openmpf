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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

import org.apache.http.Header;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpResponse;
import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.mvc.security.FailedToGetTokenException;
import org.mitre.mpf.test.MockClockService;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.wfm.util.HttpClientUtils;
import org.mitre.mpf.wfm.util.ObjectMapperFactory;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.http.SdkHttpFullRequest;

public class TestCustomSsoOutgoingTokenService extends MockitoTest.Strict {

    @Mock
    private CustomSsoProps _mockSsoProps;

    private final ObjectMapper _objectMapper = ObjectMapperFactory.customObjectMapper();

    @Mock
    private HttpClientUtils _mockHttpClient;

    private final MockClockService _mockClock = new MockClockService();

    private CustomSsoOutgoingTokenService _outgoingTokenService;

    private static final URI TOKEN_URI = URI.create("http://localhost/token");
    private static final String SSO_USER = "test user";
    private static final String SSO_PASSWORD = "test password";
    private static final int HTTP_RETRY_COUNT = 3;


    @Before
    public void init() {
        _outgoingTokenService = new CustomSsoOutgoingTokenService(
            _mockSsoProps, _objectMapper, _mockHttpClient, _mockClock);

        when(_mockSsoProps.getTokenUri())
            .thenReturn(TOKEN_URI);
        when(_mockSsoProps.getSsoUser())
            .thenReturn(SSO_USER);
        when(_mockSsoProps.getSsoPassword())
            .thenReturn(SSO_PASSWORD);
        lenient().when(_mockSsoProps.getTokenProperty())
            .thenReturn("token_prop");
        when(_mockSsoProps.getHttpRetryCount())
            .thenReturn(HTTP_RETRY_COUNT);

    }

    @Test
    public void testErrorFromSsoServer() throws IOException {
        var tokenResponse = new BasicHttpResponse(HttpVersion.HTTP_1_1, 400, "error");

        var captor = ArgumentCaptor.forClass(HttpPost.class);
        when(_mockHttpClient.executeRequestSync(captor.capture(), eq(HTTP_RETRY_COUNT)))
            .thenReturn(tokenResponse);

        var callbackRequest = new HttpGet();
        assertThatExceptionOfType(FailedToGetTokenException.class)
            .isThrownBy(() -> _outgoingTokenService.addToken(callbackRequest))
            .withMessageContaining("Request to get new SSO token failed");

        validateSsoTokenRequest(captor.getValue());
    }

    @Test
    public void testAddTokenToHttpRequest() throws IOException {
        var ssoRequestCaptor = setUpTokenResponse();

        var callbackRequest = new HttpGet();
        _outgoingTokenService.addToken(callbackRequest);

        assertHeaderSet(callbackRequest, "Authorization", "Bearer <MY TOKEN>");
        assertHeaderSet(callbackRequest, "Cookie", "token_prop=<MY TOKEN>");

        var ssoRequest = ssoRequestCaptor.getValue();
        validateSsoTokenRequest(ssoRequest);
    }

    @Test
    public void testAddTokenToS3Request() throws IOException {
        var ssoRequestCaptor = setUpTokenResponse();

        var s3RequestBuilder = SdkHttpFullRequest.builder();
        _outgoingTokenService.addToken(s3RequestBuilder);

        assertThat(s3RequestBuilder.headers().get("Cookie"))
            .singleElement()
            .isEqualTo("token_prop=<MY TOKEN>");

        var ssoRequest = ssoRequestCaptor.getValue();
        validateSsoTokenRequest(ssoRequest);
    }

    @Test
    public void testCaching() throws IOException {
        when(_mockSsoProps.getTokenLifeTime())
            .thenReturn(Duration.ofMinutes(5));

        var tokenResponse = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        tokenResponse.setEntity(new StringEntity(
             "{\"token_prop\": \"<MY TOKEN>\"}",
            ContentType.APPLICATION_JSON));

        var captor = ArgumentCaptor.forClass(HttpPost.class);
        when(_mockHttpClient.executeRequestSync(captor.capture(), eq(HTTP_RETRY_COUNT)))
            .thenReturn(tokenResponse);

        {
            var callbackRequest = new HttpGet();
            _outgoingTokenService.addToken(callbackRequest);
            assertThat(captor.getAllValues()).hasSize(1);
            validateSsoTokenRequest(captor.getValue());
            assertHeaderSet(callbackRequest, "Authorization", "Bearer <MY TOKEN>");
            assertHeaderSet(callbackRequest, "Cookie", "token_prop=<MY TOKEN>");
        }

        _mockClock.advance(Duration.ofMinutes(2));
        {
            var callbackRequest = new HttpGet();
            _outgoingTokenService.addToken(callbackRequest);
            assertThat(captor.getAllValues())
                .as("The cached token should have been used.")
                .hasSize(1);
            assertHeaderSet(callbackRequest, "Authorization", "Bearer <MY TOKEN>");
            assertHeaderSet(callbackRequest, "Cookie", "token_prop=<MY TOKEN>");
        }

        _mockClock.advance(Duration.ofMinutes(2));
        {
            var callbackRequest = new HttpGet();
            _outgoingTokenService.addToken(callbackRequest);
            assertThat(captor.getAllValues())
                .as("The cached token should have been used.")
                .hasSize(1);
            assertHeaderSet(callbackRequest, "Authorization", "Bearer <MY TOKEN>");
            assertHeaderSet(callbackRequest, "Cookie", "token_prop=<MY TOKEN>");
        }

        _mockClock.advance(Duration.ofMinutes(2));
        {
            var callbackRequest = new HttpGet();
            _outgoingTokenService.addToken(callbackRequest);
            assertThat(captor.getAllValues())
                .as("A new token should have been used for this request.")
                .hasSize(2);
            validateSsoTokenRequest(captor.getValue());
            assertHeaderSet(callbackRequest, "Authorization", "Bearer <MY TOKEN>");
            assertHeaderSet(callbackRequest, "Cookie", "token_prop=<MY TOKEN>");
        }
    }


    private void assertHeaderSet(HttpUriRequest request, String name, String value) {
        assertThat(request.getHeaders(name))
            .singleElement()
            .extracting(Header::getValue)
            .isEqualTo(value);
    }

    private ArgumentCaptor<HttpPost> setUpTokenResponse() throws IOException {
        var tokenResponse = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        tokenResponse.setEntity(new StringEntity(
             "{\"token_prop\": \"<MY TOKEN>\"}",
            ContentType.APPLICATION_JSON));

        var captor = ArgumentCaptor.forClass(HttpPost.class);
        when(_mockHttpClient.executeRequestSync(captor.capture(), eq(HTTP_RETRY_COUNT)))
            .thenReturn(tokenResponse);
        return captor;
    }

    private void validateSsoTokenRequest(HttpPost ssoRequest) throws IOException {
        JsonNode ssoRequestBody;
        try (var is = ssoRequest.getEntity().getContent()) {
            ssoRequestBody = _objectMapper.readTree(is);
        }
        assertThat(ssoRequestBody.get("username").asText())
            .isEqualTo(SSO_USER);
        assertThat(ssoRequestBody.get("password").asText())
            .isEqualTo(SSO_PASSWORD);
    }
}
