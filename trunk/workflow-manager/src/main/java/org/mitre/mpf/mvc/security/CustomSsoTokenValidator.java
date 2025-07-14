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

package org.mitre.mpf.mvc.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.mitre.mpf.wfm.service.ClockService;
import org.mitre.mpf.wfm.util.HttpClientUtils;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
@Profile("custom_sso")
public class CustomSsoTokenValidator {

    private static final Logger LOG = LoggerFactory.getLogger(CustomSsoTokenValidator.class);

    private static final String BEARER_PREFIX = "Bearer ";

    private final CustomSsoProps _customSsoProps;

    private final HttpClientUtils _httpClient;

    private final ClockService _clock;

    private final TokenCache _cache;

    private final Map<String, CompletableFuture<Void>> _inProgressValidations
            = new ConcurrentHashMap<>();

    @Inject
    public CustomSsoTokenValidator(
            CustomSsoProps customSsoProps,
            HttpClientUtils httpClient,
            ClockService clock) {
        _customSsoProps = customSsoProps;
        _httpClient = httpClient;
        _clock = clock;
        _cache = new TokenCache(_clock);
    }


    public Authentication authenticate(Authentication authentication)
            throws AuthenticationException {
        var authHeader = (String) authentication.getCredentials();
        if (authHeader == null) {
            throw new BadCredentialsException("No Authorization header present.");
        }

        if (!authHeader.startsWith(BEARER_PREFIX)) {
            throw new BadCredentialsException(
                "Authorization header did not start with \"%s\"".formatted(BEARER_PREFIX));
        }

        var token = authHeader.substring(BEARER_PREFIX.length());
        if (token.isBlank()) {
            throw new BadCredentialsException(
                "No token following \"%s\"".formatted(BEARER_PREFIX));
        }
        validateToken(token);
        authentication.setAuthenticated(true);
        return authentication;
    }


    private void validateToken(String token) {
        if (_cache.contains(token)) {
            LOG.info("Incoming SSO token was found in cache.");
            return;
        }

        var newFuture = ThreadUtil.<Void>newFuture();
        var existingFuture = _inProgressValidations.putIfAbsent(token, newFuture);
        if (existingFuture != null) {
            waitForExistingValidation(existingFuture);
            return;
        }

        if (_cache.contains(token)) {
            // There is period of time between the cache being updated and the
            // _inProgressValidations entry being removed.
            newFuture.complete(null);
            _inProgressValidations.remove(token);
            return;
        }

        LOG.info("Incoming SSO token was not in cache or was expired. "
            + "Sending token validation request.");
        try {
            var validationStartTime = _clock.now();
            validateRemotely(token);
            _cache.add(
                    token,
                    validationStartTime.plus(_customSsoProps.getTokenLifeTime()));
            LOG.info("Successfully validated incoming SSO token.");
            _inProgressValidations.remove(token);
            newFuture.complete(null);
        }
        catch (Exception e) {
            _inProgressValidations.remove(token);
            newFuture.completeExceptionally(e);
            throw e;
        }
    }


    private void waitForExistingValidation(CompletableFuture<Void> validationFuture) {
        LOG.info("Incoming SSO token was not in cache or was expired. "
            + "Waiting for in progres validation to complete.");
        ThreadUtil.join(validationFuture);
        LOG.info("Successfully validated incoming SSO token.");
    }


    private void validateRemotely2(String token) {
        var request = new HttpGet(_customSsoProps.getValidationUri());
        request.addHeader("Cookie", _customSsoProps.getTokenProperty() + '=' + token);
        var response = ThreadUtil.join(
                _httpClient.executeRequest(request, _customSsoProps.getHttpRetryCount()));

        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode < 200 || statusCode > 299) {
            var errorPrefix = "Received %s response from token validator".formatted(statusCode);
            var errorDetails = getBody(response)
                .map(b -> ", with body: " + b)
                .orElse(".");
            throw new BadCredentialsException(errorPrefix + errorDetails);
        }
    }

    private void validateRemotely(String token) {
        HttpResponse response;
        try {
            var request = new HttpGet(_customSsoProps.getValidationUri());
            request.addHeader("Cookie", _customSsoProps.getTokenProperty() + '=' + token);
            response = ThreadUtil.join(
                    _httpClient.executeRequest(request, _customSsoProps.getHttpRetryCount()),
                    Exception.class);
        }
        catch (Exception e) {
            throw new BadCredentialsException(
                    "Failed to validate token because communication with the SSO server failed with error: "
                    + e, e);
        }

        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode < 200 || statusCode > 299) {
            var errorPrefix = "Received %s response from token validator".formatted(statusCode);
            var errorDetails = getBody(response)
                .map(b -> ", with body: " + b)
                .orElse(".");
            throw new BadCredentialsException(errorPrefix + errorDetails);
        }
    }

    private static Optional<String> getBody(HttpResponse response) {
        var entity = response.getEntity();
        if (entity == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(IOUtils.toString(entity.getContent(), StandardCharsets.UTF_8));
        }
        catch (IOException | UnsupportedOperationException e) {
            LOG.warn("Could not convert HTTP response body to string due to: " + e, e);
            return Optional.empty();
        }
    }
}



class TokenCache {
    private record CachedToken(String token, Instant expirationTime) {
    }

    private final ClockService _clock;

    private final Set<String> _tokenLookup = ConcurrentHashMap.newKeySet();

    private AtomicReference<Instant> _nextExpiration = new AtomicReference<>(Instant.MAX);

    private final PriorityQueue<CachedToken> _expirationQueue = new PriorityQueue<>(
            Comparator.comparing(CachedToken::expirationTime));

    private final Lock _lock = new ReentrantLock();

    public TokenCache(ClockService clock) {
        _clock = clock;
    }

    public boolean contains(String token) {
        expireItemsIfNeeded();
        return _tokenLookup.contains(token);
    }

    public void add(String token, Instant expirationTime) {
        _lock.lock();
        try {
            if (_tokenLookup.contains(token)) {
                throw new IllegalStateException(
                        "Caller should not have added a token if it was already present.");
            }
            _tokenLookup.add(token);
            _expirationQueue.add(new CachedToken(token, expirationTime));
            if (expirationTime.isBefore(_nextExpiration.get())) {
                _nextExpiration.set(expirationTime);
            }
        }
        finally {
            _lock.unlock();
        }
    }


    private void expireItemsIfNeeded() {
        if (_clock.now().isBefore(_nextExpiration.get())) {
            return;
        }

        _lock.lock();
        try {
            var now = _clock.now();
            if (now.isBefore(_nextExpiration.get())) {
                // In the time between the initial check and acquiring the lock, a different
                // thread handled eviction.
                return;
            }

            while (!_expirationQueue.isEmpty()
                    && now.isAfter(_expirationQueue.peek().expirationTime())) {
                var evictedToken = _expirationQueue.poll();
                _tokenLookup.remove(evictedToken.token());
            }
            var nextExpiration = Optional.ofNullable(_expirationQueue.peek())
                .map(CachedToken::expirationTime)
                .orElse(Instant.MAX);
            _nextExpiration.set(nextExpiration);
        }
        finally {
            _lock.unlock();
        }
    }
}
