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

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.inject.Inject;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.mitre.mpf.mvc.security.FailedToGetTokenException;
import org.mitre.mpf.mvc.security.ITokenProvider;
import org.mitre.mpf.wfm.service.ClockService;
import org.mitre.mpf.wfm.util.HttpClientUtils;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.http.SdkHttpFullRequest;


@Service
@Profile("custom_sso")
public class CustomSsoOutgoingTokenService implements ITokenProvider {

    private final CustomSsoProps _customSsoProps;

    private final ObjectMapper _objectMapper;

    private final HttpClientUtils _httpClient;

    private final ClockService _clock;

    private final Lock _lock = new ReentrantLock();

    private final AtomicReference<CachedToken> _cachedToken = new AtomicReference<>(
            new CachedToken("", Instant.MIN));

    @Inject
    CustomSsoOutgoingTokenService(
            CustomSsoProps customSsoProps,
            ObjectMapper objectMapper,
            HttpClientUtils httpClient,
            ClockService clock) {
        _customSsoProps = customSsoProps;
        _objectMapper = objectMapper;
        _httpClient = httpClient;
        _clock = clock;
    }


    @Override
    public void addToken(HttpUriRequest request) {
        var token = getToken();
        request.addHeader("Authorization", "Bearer " + token);
        request.addHeader("Cookie", createCookie(token));
    }

    @Override
    public void addToken(SdkHttpFullRequest.Builder requestBuilder) {
        var token = getToken();
        requestBuilder.putHeader("Cookie", createCookie(token));
    }

    private String createCookie(String token) {
        return _customSsoProps.getTokenProperty() + '=' + token;
    }


    private String getToken() {
        var optToken = getCachedToken();
        if (optToken.isPresent()) {
            return optToken.get();
        }

        _lock.lock();
        try {
            // The cached token may have been updated while the thread was waiting to acquire the
            // lock.
            optToken = getCachedToken();
            if (optToken.isPresent()) {
                return optToken.get();
            }
            var newToken = requestNewToken();
            _cachedToken.set(newToken);
            return newToken.token();
        }
        finally {
            _lock.unlock();
        }
    }

    private Optional<String> getCachedToken() {
        var cachedToken = _cachedToken.get();
        return _clock.now().isBefore(cachedToken.expirationTime)
            ? Optional.of(cachedToken.token)
            : Optional.empty();
    }


    private CachedToken requestNewToken() {
        Instant tokenRequestStartTime;
        HttpResponse response;
        try {
            var request = new HttpPost(_customSsoProps.getTokenUri());
            request.addHeader("Content-Type", "application/json");
            var body = Map.of(
                "username", _customSsoProps.getSsoUser(),
                "password", _customSsoProps.getSsoPassword());
            var bodyJson = _objectMapper.writeValueAsString(body);
            request.setEntity(new StringEntity(bodyJson, ContentType.APPLICATION_JSON));

            tokenRequestStartTime = _clock.now();
            response = _httpClient.executeRequestSync(request, _customSsoProps.getHttpRetryCount());
        }
        catch (Exception e) {
            throw new FailedToGetTokenException(
                    "Request to get new SSO token failed due to: " + e, e);
        }

        try {
            int responseStatusCode = response.getStatusLine().getStatusCode();
            if (responseStatusCode < 200 || responseStatusCode > 299) {
                throw new FailedToGetTokenException(
                    "Request to get new SSO token failed with status code: " + responseStatusCode);
            }

            String newToken;
            try (var is = response.getEntity().getContent()) {
                var responseBody = _objectMapper.readTree(is);
                newToken = responseBody.get(_customSsoProps.getTokenProperty()).asText();
            }
            return new CachedToken(
                    newToken,
                    tokenRequestStartTime.plus(_customSsoProps.getTokenLifeTime()));
        }
        catch (IOException e) {
            throw new FailedToGetTokenException(
                    "Request to get new SSO token failed due to: " + e, e);
        }
    }

    private record CachedToken(String token, Instant expirationTime) {
    }
}
