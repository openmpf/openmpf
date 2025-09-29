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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.mitre.mpf.mvc.security.custom.sso.CustomSsoTokenValidator.TokenInfo;
import org.mitre.mpf.wfm.enums.UserRole;
import org.mitre.mpf.wfm.service.ClockService;
import org.mitre.mpf.wfm.util.HttpClientUtils;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@Profile("custom_sso")
public class CustomSsoTokenValidator {

    public record TokenInfo(
            String token, Instant expirationTime, String userId, String displayName) {
    }

    private static final Logger LOG = LoggerFactory.getLogger(CustomSsoTokenValidator.class);

    private static final String BEARER_PREFIX = "Bearer";

    private final CustomSsoProps _customSsoProps;

    private final HttpClientUtils _httpClient;

    private final ClockService _clock;

    private final ObjectMapper _objectMapper;

    private final TokenCache _cache;

    private final Map<String, CompletableFuture<TokenInfo>> _inProgressValidations
            = new ConcurrentHashMap<>();

    @Inject
    public CustomSsoTokenValidator(
            CustomSsoProps customSsoProps,
            HttpClientUtils httpClient,
            ClockService clock,
            ObjectMapper objectMapper) {
        _customSsoProps = customSsoProps;
        _httpClient = httpClient;
        _clock = clock;
        _objectMapper = objectMapper;
        _cache = new TokenCache(_clock);
    }


    public Authentication authenticateBearer(Authentication authenticationRequest)
            throws AuthenticationException {
        var authHeader = (String) authenticationRequest.getCredentials();
        if (authHeader == null) {
            throw new BadCredentialsException("No Authorization header present.");
        }

        if (!authHeader.startsWith(BEARER_PREFIX)) {
            throw new BadCredentialsException(
                "Authorization header did not start with \"%s\"".formatted(BEARER_PREFIX));
        }

        authHeader = authHeader.strip();
        if (authHeader.equals(BEARER_PREFIX)) {
            throw new BadCredentialsException(
                "The Authorization header contained \"%s\", without a token after it."
                .formatted(BEARER_PREFIX));
        }

        var token = authHeader.substring(BEARER_PREFIX.length() + 1);
        return authenticate(token, authenticationRequest);
    }


    public Authentication authenticateCookie(Authentication authenticationRequest) {
        var token = (String) authenticationRequest.getCredentials();
        if (token == null) {
            throw new BadCredentialsException("No cookie present.");
        }
        return authenticate(token, authenticationRequest);
    }


    private Authentication authenticate(String token, Authentication authenticationRequest) {
        var tokenInfo = validateToken(token);
        authenticationRequest.setAuthenticated(true);

        var authenticationResult = new PreAuthenticatedAuthenticationToken(
                tokenInfo.userId(),
                authenticationRequest.getCredentials(),
                List.of(new SimpleGrantedAuthority(UserRole.ADMIN.springName)));
        authenticationResult.setDetails(tokenInfo.displayName);
        authenticationResult.setAuthenticated(true);
        return authenticationResult;
    }


    private TokenInfo validateToken(String token) {
        var cachedToken = _cache.get(token);
        if (cachedToken.isPresent()) {
            LOG.info("Incoming SSO token was found in cache.");
            return cachedToken.get();
        }

        var newFuture = ThreadUtil.<TokenInfo>newFuture();
        var existingFuture = _inProgressValidations.putIfAbsent(token, newFuture);
        if (existingFuture != null) {
            return waitForExistingValidation(existingFuture);
        }

        // The cache is checked a second time here because there is period of time between the
        // cache being updated and the _inProgressValidations entry being removed.
        cachedToken = _cache.get(token);
        if (cachedToken.isPresent()) {
            newFuture.complete(cachedToken.get());
            _inProgressValidations.remove(token);
            return cachedToken.get();
        }

        LOG.info("Incoming SSO token was not in cache or was expired. "
            + "Sending token validation request.");
        try {
            var tokenInfo = validateRemotely(token);
            _cache.add(tokenInfo);
            LOG.info("Successfully validated incoming SSO token.");
            _inProgressValidations.remove(token);
            newFuture.complete(tokenInfo);
            return tokenInfo;
        }
        catch (Exception e) {
            _inProgressValidations.remove(token);
            newFuture.completeExceptionally(e);
            throw e;
        }
    }


    private TokenInfo waitForExistingValidation(CompletableFuture<TokenInfo> validationFuture) {
        LOG.info("Incoming SSO token was not in cache or was expired. "
            + "Waiting for in progress validation to complete.");
        var tokenInfo = ThreadUtil.join(validationFuture);
        LOG.info("Successfully validated incoming SSO token.");
        return tokenInfo;
    }


    private TokenInfo validateRemotely(String token) {
        var validationStartTime = _clock.now();
        HttpResponse response;
        try {
            var request = new HttpGet(_customSsoProps.getValidationUri());
            request.addHeader("Cookie", _customSsoProps.getTokenProperty() + '=' + token);
            response = _httpClient.executeRequestSync(
                    request, _customSsoProps.getHttpRetryCount(),
                    HttpClientUtils.ONLY_RETRY_CONNECTION_ERRORS);
        }
        catch (Exception e) {
            throw new AuthServerReportedBadCredentialsException(
                    "Failed to validate token because communication with the SSO server failed with error: "
                    + e, e);
        }

        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode < 200 || statusCode > 299) {
            var errorPrefix = "Received %s response from token validator".formatted(statusCode);
            var errorDetails = getBody(response)
                .map(b -> ", with body: " + b)
                .orElse(".");
            throw new AuthServerReportedBadCredentialsException(errorPrefix + errorDetails);
        }
        return processValidationResponse(token, validationStartTime, response);
    }

    private TokenInfo processValidationResponse(
            String token,
            Instant validationStartTime,
            HttpResponse response) {
        JsonNode responseJson;
        try (var inputStream = response.getEntity().getContent()) {
            responseJson = _objectMapper.readTree(inputStream);
        }
        catch (IOException e) {
            throw new AuthServerReportedBadCredentialsException(
                "Failed to validate token because of an invalid response: " + e, e);
        }

        var userId = Optional
                .ofNullable(responseJson.get(_customSsoProps.getUserIdProperty()))
                .map(JsonNode::asText)
                .filter(s -> !s.isEmpty())
                .orElseThrow(() -> new AuthServerReportedBadCredentialsException(
                        "Could not determine user Id because the %s property was not present in the response."
                        .formatted(_customSsoProps.getUserIdProperty())));

        var displayName = Optional
                .ofNullable(responseJson.get(_customSsoProps.getDisplayNameProperty()))
                .map(JsonNode::asText)
                .filter(s -> !s.isEmpty())
                .orElse(userId);
        return new TokenInfo(
                token,
                validationStartTime.plus(_customSsoProps.getTokenLifeTime()),
                userId, displayName);
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
    private final ClockService _clock;

    private final Map<String, TokenInfo> _tokenLookup = new ConcurrentHashMap<>();

    private AtomicReference<Instant> _nextExpiration = new AtomicReference<>(Instant.MAX);

    private final PriorityQueue<TokenInfo> _expirationQueue = new PriorityQueue<>(
            Comparator.comparing(TokenInfo::expirationTime));

    private final Lock _lock = new ReentrantLock();

    public TokenCache(ClockService clock) {
        _clock = clock;
    }

    public Optional<TokenInfo> get(String token) {
        expireItemsIfNeeded();
        return Optional.ofNullable(_tokenLookup.get(token));
    }

    public void add(TokenInfo tokenInfo) {
        _lock.lock();
        try {
            if (_tokenLookup.containsKey(tokenInfo.token())) {
                throw new IllegalStateException(
                        "Caller should not have added a token if it was already present.");
            }
            _tokenLookup.put(tokenInfo.token(), tokenInfo);
            _expirationQueue.add(tokenInfo);
            if (tokenInfo.expirationTime().isBefore(_nextExpiration.get())) {
                _nextExpiration.set(tokenInfo.expirationTime());
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
                .map(TokenInfo::expirationTime)
                .orElse(Instant.MAX);
            _nextExpiration.set(nextExpiration);
        }
        finally {
            _lock.unlock();
        }
    }
}
