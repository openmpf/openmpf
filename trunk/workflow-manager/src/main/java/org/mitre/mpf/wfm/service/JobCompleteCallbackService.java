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

package org.mitre.mpf.wfm.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;

import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.mitre.mpf.interop.JsonCallbackBody;
import org.mitre.mpf.interop.subject.CallbackMethod;
import org.mitre.mpf.mvc.security.OAuthClientTokenProvider;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.DbSubjectJob;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.HttpClientUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class JobCompleteCallbackService {

    private static final Logger LOG = LoggerFactory.getLogger(JobCompleteCallbackService.class);

    private final HttpClientUtils _httpClientUtils;

    private final ObjectMapper _objectMapper;

    private final PropertiesUtil _propertiesUtil;

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;

    private final OAuthClientTokenProvider _oAuthClientTokenProvider;

    @Inject
    JobCompleteCallbackService(
            HttpClientUtils httpClientUtils,
            ObjectMapper objectMapper,
            PropertiesUtil propertiesUtil,
            AggregateJobPropertiesUtil aggregateJobPropertiesUtil,
            OAuthClientTokenProvider oAuthClientTokenProvider) {
        _httpClientUtils = httpClientUtils;
        _objectMapper = objectMapper;
        _propertiesUtil = propertiesUtil;
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
        _oAuthClientTokenProvider = oAuthClientTokenProvider;
    }


    public CompletableFuture<HttpResponse> sendCallback(BatchJob job, URI outputObjectUri) {
        if (job.getCallbackUrl().isEmpty()) {
            throw new IllegalArgumentException("Job did not have a callback URL.");
        }

        try {
            var request = createCallbackRequest(
                    new URI(job.getCallbackUrl().get()),
                    getCallbackMethod(job),
                    job.getId(),
                    Optional.ofNullable(outputObjectUri),
                    job.getExternalId());
            if (shouldUseOidc(job)) {
                _oAuthClientTokenProvider.addToken(request);
            }
            return sendCallback(request);
        }
        catch (Exception e) {
            return ThreadUtil.failedFuture(e);
        }
    }


    public CompletableFuture<HttpResponse> sendCallback(DbSubjectJob job) {
        if (job.getCallbackUri().isEmpty()) {
            throw new IllegalArgumentException("Job did not have a callback URL.");
        }

        try {
            var request = createCallbackRequest(
                    job.getCallbackUri().get(),
                    job.getCallbackMethod().orElseGet(CallbackMethod::getDefault),
                    job.getId(),
                    job.getOutputUri(),
                    job.getExternalId());
            if (shouldUseOidc(job)) {
                _oAuthClientTokenProvider.addToken(request);
            }
            return sendCallback(request);
        }
        catch (Exception e) {
            return ThreadUtil.failedFuture(e);
        }
    }

    private CompletableFuture<HttpResponse> sendCallback(HttpUriRequest request) {
        LOG.info("Sending job completion callback to: {}", request.getURI());
        return _httpClientUtils.executeRequest(
                    request, _propertiesUtil.getHttpCallbackRetryCount())
                .thenApplyAsync(JobCompleteCallbackService::checkResponse);
    }


    private static CallbackMethod getCallbackMethod(BatchJob job) {
        try {
            return job.getCallbackMethod()
                .map(s -> CallbackMethod.valueOf(s.toUpperCase()))
                .orElseGet(CallbackMethod::getDefault);
        }
        catch (IllegalArgumentException e) {
            return CallbackMethod.getDefault();
        }
    }


    private HttpUriRequest createCallbackRequest(
            URI callbackUri,
            CallbackMethod method,
            long jobId,
            Optional<URI> outputObjectUri,
            Optional<String> externalId
            ) throws URISyntaxException, JsonProcessingException {

        var requestConfig = RequestConfig.custom()
                .setSocketTimeout(_propertiesUtil.getHttpCallbackTimeoutMs())
                .setConnectTimeout(_propertiesUtil.getHttpCallbackTimeoutMs())
                .build();

        var exportedJobId = _propertiesUtil.getExportedJobId(jobId);
        if (method == CallbackMethod.GET) {
            var callbackUriBuilder = new URIBuilder(callbackUri)
                    .setParameter("jobid", exportedJobId);
            externalId.ifPresent(id -> callbackUriBuilder.setParameter("externalid", id));

            outputObjectUri.ifPresent(
                    u -> callbackUriBuilder.setParameter("outputobjecturi", u.toString()));

            var getRequest = new HttpGet(callbackUriBuilder.build());
            getRequest.setConfig(requestConfig);
            return getRequest;
        }

        var postRequest = new HttpPost(callbackUri);
        postRequest.setConfig(requestConfig);

        var jsonBody = new JsonCallbackBody(
                exportedJobId,
                externalId.map(Object::toString).orElse(null),
                outputObjectUri.map(Object::toString).orElse(null));
        var jsonString = _objectMapper.writeValueAsString(jsonBody);
        postRequest.setEntity(new StringEntity(jsonString, ContentType.APPLICATION_JSON));
        return postRequest;
    }


    private boolean shouldUseOidc(BatchJob job) {
        return job.getMedia()
            .stream()
            .map(m -> _aggregateJobPropertiesUtil.getValue(MpfConstants.CALLBACK_USE_OIDC, job, m))
            .anyMatch(Boolean::parseBoolean);
    }

    private boolean shouldUseOidc(DbSubjectJob job) {
        return _aggregateJobPropertiesUtil.getValue(MpfConstants.CALLBACK_USE_OIDC, job)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    private static HttpResponse checkResponse(HttpResponse response) {
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode < 200 || statusCode > 299) {
            throw new IllegalStateException(
                    "The remote server responded with a non-200 status code of: " + statusCode);
        }
        return response;
    }
}
