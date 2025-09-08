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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpResponse;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.test.MockClockService;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.enums.UserRole;
import org.mitre.mpf.wfm.util.HttpClientUtils;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.mockito.Mock;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;


public class TestCustomSsoTokenValidator extends MockitoTest.Strict {

    private static final Duration FUTURE_DURATION = TestUtil.FUTURE_DURATION;

    private static final URI VALIDATION_URL
            = URI.create("http://localhost:43234/user-info");

    private static final Duration TOKEN_LIFETIME = Duration.ofSeconds(5);

    private static final int HTTP_RETRY_COUNT = 3;

    @Mock
    private CustomSsoProps _mockSsoProps;

    @Mock
    private HttpClientUtils _mockHttpClient;

    private final MockClockService _mockClock = new MockClockService();

    private CustomSsoTokenValidator _tokenValidator;

    @Before
    public void init() {
        _tokenValidator = new CustomSsoTokenValidator(
            _mockSsoProps, _mockHttpClient, _mockClock);

        lenient().when(_mockSsoProps.getValidationUri())
            .thenReturn(VALIDATION_URL);
        lenient().when(_mockSsoProps.getTokenProperty())
            .thenReturn("CUSTOM_TOKEN_PROP");
        lenient().when(_mockSsoProps.getTokenLifeTime())
            .thenReturn(TOKEN_LIFETIME);
        lenient().when(_mockSsoProps.getHttpRetryCount())
                .thenReturn(HTTP_RETRY_COUNT);
    }


    @Test
    public void testNoAuthHeader() {
        assertBadCredentials(null, "No Authorization header present.");
    }


    @Test
    public void testMissingPrefix() {
        var expectedMsg = "Authorization header did not start with \"Bearer\"";
        assertBadCredentials("", expectedMsg);
        assertBadCredentials("asdf", expectedMsg);
    }

    @Test
    public void testPrefixButNoToken() {
        assertBadCredentials(
            "Bearer",
            "The Authorization header contained \"Bearer\", without a token after it");
    }


    @Test
    public void testRemoteValidationFails() throws IOException {
        var errorDetails = "<ERROR DETAILS>";
        var response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 400, "reason");
        response.setEntity(new StringEntity(errorDetails, StandardCharsets.UTF_8));

        when(_mockHttpClient.executeRequestSync(argThat(this::hasTokenCookieSet), eq(HTTP_RETRY_COUNT)))
            .thenReturn(response);

        assertBadCredentials("Bearer <MY TOKEN>", errorDetails);
    }


    @Test
    public void testCache() throws IOException {
        var authHeader = "Bearer <MY TOKEN>";
        var numHttpRequests = new AtomicInteger(0);
        {
            var response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "ok");
            when(_mockHttpClient.executeRequestSync(
                    argThat(this::hasTokenCookieSet), eq(HTTP_RETRY_COUNT)))
                .thenAnswer(a -> {
                    numHttpRequests.incrementAndGet();
                    return response;
                });

            var authResult = _tokenValidator.authenticateBearer(createAuth(authHeader));
            assertThat(authResult.isAuthenticated()).isTrue();
        }

        _mockClock.advance(Duration.ofSeconds(3));
        {
            var authResult = _tokenValidator.authenticateBearer(createAuth(authHeader));
            assertThat(authResult.isAuthenticated()).isTrue();
            assertThat(numHttpRequests)
                .as("Cached value should have been used")
                .hasValue(1);
        }

        _mockClock.advance(Duration.ofSeconds(1));
        {
            var authResult = _tokenValidator.authenticateBearer(createAuth(authHeader));
            assertThat(authResult.isAuthenticated()).isTrue();
            assertThat(numHttpRequests)
                .as("Cached value should have been used")
                .hasValue(1);
        }

        _mockClock.advance(Duration.ofSeconds(4));
        {
            var authResult = _tokenValidator.authenticateBearer(createAuth(authHeader));
            assertThat(authResult.isAuthenticated()).isTrue();
            assertThat(numHttpRequests)
                .as("Cached value should not have been used")
                .hasValue(2);
        }
    }


    @Test
    public void testOverlappingRequests() throws IOException {
        var request1Started = ThreadUtil.<Void>newFuture();
        var request2Started = ThreadUtil.<Void>newFuture();

        var httpFuture1 = ThreadUtil.<HttpResponse>newFuture();
        var httpFuture2 = ThreadUtil.<HttpResponse>newFuture();
        var numToken1Requests = new AtomicInteger(0);
        var numToken2Requests = new AtomicInteger(0);


        when(_mockHttpClient.executeRequestSync(any(), eq(HTTP_RETRY_COUNT)))
            .thenAnswer(inv -> {
                HttpUriRequest request = inv.getArgument(0);
                if (hasTokenCookieSet(request, "<MY TOKEN>")) {
                    numToken1Requests.incrementAndGet();
                    request1Started.complete(null);
                    return httpFuture1.join();
                }
                else if (hasTokenCookieSet(request, "<MY TOKEN2>")) {
                    numToken2Requests.incrementAndGet();
                    request2Started.complete(null);
                    return httpFuture2.join();
                }
                Assert.fail("Unexpected token");
                return null;
            });

        var authHeader = "Bearer <MY TOKEN>";
        var authFuture1 = ThreadUtil.callAsync(
            () -> _tokenValidator.authenticateBearer(createAuth(authHeader)));
        var authFuture2 = ThreadUtil.callAsync(
            () -> _tokenValidator.authenticateBearer(createAuth(authHeader)));

        var authHeader2 = "Bearer <MY TOKEN2>";
        var authFuture3 = ThreadUtil.callAsync(
            () -> _tokenValidator.authenticateBearer(createAuth(authHeader2)));
        var authFuture4 = ThreadUtil.callAsync(
            () -> _tokenValidator.authenticateBearer(createAuth(authHeader2)));

        // Verify that both requests have started.
        assertThat(ThreadUtil.allOf(request1Started, request2Started))
            .succeedsWithin(Duration.ofMillis(100));

        // None of the auth futures should be able to complete until the HTTP responses are
        // received.
        TestUtil.assertNotDone(CompletableFuture.anyOf(
                authFuture1, authFuture2, authFuture3, authFuture4));

        // Trigger completion of httpFuture2 before httpFuture1 to simulate HTTP responses being
        // received out of order.
        httpFuture2.complete(new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "ok"));

        // Make sure the requests associated with the second token complete.
        assertThat(authFuture3).succeedsWithin(FUTURE_DURATION)
            .satisfies(a -> assertThat(a.isAuthenticated()).isTrue());
        assertThat(authFuture4).succeedsWithin(FUTURE_DURATION)
            .satisfies(a -> assertThat(a.isAuthenticated()).isTrue());

        // Make sure we are still waiting for the first token.
        TestUtil.assertNotDone(CompletableFuture.anyOf(authFuture1, authFuture2));

        httpFuture1.complete(new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "ok"));
        assertThat(authFuture1).succeedsWithin(FUTURE_DURATION)
            .satisfies(a -> assertThat(a.isAuthenticated()).isTrue());
        assertThat(authFuture2).succeedsWithin(FUTURE_DURATION)
            .satisfies(a -> assertThat(a.isAuthenticated()).isTrue());

        assertThat(numToken1Requests).hasValue(1);
        assertThat(numToken2Requests).hasValue(1);
    }


    @Test
    public void testValidationFromBrowser() throws IOException {
        var httpResponse = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "ok");
        when(_mockHttpClient.executeRequestSync(argThat(this::hasTokenCookieSet), eq(HTTP_RETRY_COUNT)))
                .thenReturn(httpResponse);

        var authRequest = new PreAuthenticatedAuthenticationToken("n/a", "<MY TOKEN>");
        var authResponse = _tokenValidator.authenticateCookie(authRequest);
        assertThat(authResponse.getAuthorities())
                .singleElement()
                .extracting(g -> g.getAuthority())
                .isEqualTo(UserRole.ADMIN.springName);
    }


    private boolean hasTokenCookieSet(HttpUriRequest request) {
        return hasTokenCookieSet(request, "<MY TOKEN>");
    }

    private boolean hasTokenCookieSet(HttpUriRequest request, String expectedToken) {
        assertThat(request.getURI()).isEqualTo(VALIDATION_URL);
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getHeaders("Cookie"))
            .singleElement()
            .extracting(h -> h.getValue(), Assertions.STRING)
            .startsWith("CUSTOM_TOKEN_PROP=");

        var cookieHeader = request.getFirstHeader("Cookie").getValue();
        return cookieHeader.equals("CUSTOM_TOKEN_PROP=" + expectedToken);
    }


    private void assertBadCredentials(String token, String expectedMsg) {
        var auth = createAuth(token);
        assertThatExceptionOfType(BadCredentialsException.class)
            .isThrownBy(() -> _tokenValidator.authenticateBearer(auth))
            .withMessageContaining(expectedMsg);
        assertThat(auth.isAuthenticated()).isFalse();
    }


    private static Authentication createAuth(String authHeader) {
        return new PreAuthenticatedAuthenticationToken("n/a", authHeader);
    }
}
