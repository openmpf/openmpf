/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.util;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.EntityTemplate;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.mitre.mpf.interop.JsonHealthReportCollection;
import org.mitre.mpf.interop.JsonSegmentSummaryReport;
import org.mitre.mpf.mvc.util.MdcUtil;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toList;


@Component
public class HttpClientUtils implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HttpClientUtils.class);

    private final JsonUtils jsonUtils;

    private final CloseableHttpAsyncClient httpAsyncClient;


    @Inject
    public HttpClientUtils(PropertiesUtil propertiesUtil, JsonUtils jsonUtils) throws IOReactorException {
        this.jsonUtils = jsonUtils;

        IOReactorConfig ioConfig = IOReactorConfig.custom()
                .setIoThreadCount(Math.min(8, Runtime.getRuntime().availableProcessors()))
                // the default connect timeout value for non-blocking connection requests.
                .setConnectTimeout(propertiesUtil.getHttpCallbackSocketTimeout())
                // the default socket timeout value for non-blocking I/O operations
                .setSoTimeout(propertiesUtil.getHttpCallbackSocketTimeout()).build();

        ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor(ioConfig);

        PoolingNHttpClientConnectionManager cm = new PoolingNHttpClientConnectionManager(ioReactor);
        cm.setDefaultMaxPerRoute(propertiesUtil.getHttpCallbackConcurrentConnectionsPerRoute()); // default is 2
        cm.setMaxTotal(propertiesUtil.getHttpCallbackConcurrentConnections()); // default is 20


        httpAsyncClient = HttpAsyncClients.custom().setConnectionManager(cm).build();
        httpAsyncClient.start();
    }


    @Override
    public void close() throws IOException {
        httpAsyncClient.close();
    }


    // Send the health report to the URI identified by callbackUri, using the HTTP POST method.
    public void sendHealthReportCallback(String callbackUri, List<StreamingJob> jobs) {

        List<JsonHealthReportCollection.JsonHealthReport> reports = jobs
                .stream()
                .map(job -> new JsonHealthReportCollection.JsonHealthReport(
                        job.getId(), job.getExternalId().orElse(null), job.getJobStatus().getType().toString(),
                        job.getJobStatus().getDetail(), String.valueOf(job.getLastActivityFrame()),
                        job.getLastActivityTime()))
                .collect(toList());

        List<Long> jobIds = jobs.stream()
                .map(StreamingJob::getId)
                .collect(toList());
        try {
            JsonHealthReportCollection jsonBody = new JsonHealthReportCollection(Instant.now(), reports);

            sendPostCallback(jsonBody, callbackUri, jobIds, "health report(s)");
        } catch (WfmProcessingException e) {
            log.error(String.format("Error sending health report(s) for job ids %s to %s.", jobIds, callbackUri), e);
        }
    }


    // Send the summary report to the URI identified by callbackUri, using the HTTP POST method.
    public void sendSummaryReportCallback(JsonSegmentSummaryReport summaryReport, String callbackUri) {
        sendPostCallback(summaryReport, callbackUri, Collections.singletonList(summaryReport.getJobId()),
                         "summary report");
    }


    public CompletableFuture<HttpResponse> executeRequest(HttpUriRequest request) {
        var future = ThreadUtil.<HttpResponse>newFuture();
        var mdcCtx = MDC.getCopyOfContextMap();
        httpAsyncClient.execute(request, new FutureCallback<>() {
            @Override
            public void completed(HttpResponse result) {
                MdcUtil.all(mdcCtx, () -> future.complete(result));
            }

            @Override
            public void failed(Exception ex) {
                MdcUtil.all(mdcCtx, () -> future.completeExceptionally(ex));
            }

            @Override
            public void cancelled() {
                MdcUtil.all(mdcCtx, () -> future.cancel(true));
            }
        });
        return future;
    }


    public CompletableFuture<HttpResponse> executeRequest(HttpUriRequest request, int retries) {
        return executeRequest(request, retries, 100, r -> true);
    }


    public CompletableFuture<HttpResponse> executeRequest(HttpUriRequest request, int retries,
                                                          Predicate<HttpResponse> isRetryable) {
        return executeRequest(request, retries, 100, isRetryable);
    }


    private CompletableFuture<HttpResponse> executeRequest(
            HttpUriRequest request,
            int retries,
            long delayMs,
            Predicate<HttpResponse> isRetryable) {

        log.info("Starting {} request to \"{}\".", request.getMethod(), request.getURI());
        long nextDelay = Math.min(delayMs * 2, 30_000);

        return executeRequest(request).thenApply(resp -> {
            int statusCode = resp.getStatusLine().getStatusCode();
            if ((statusCode >= 200 && statusCode <= 299) || retries <= 0
                    || !isRetryable.test(resp)) {
                return ThreadUtil.completedFuture(resp);
            }

            log.warn("\"{}\" responded with a non-200 status code of {}. There are {} " +
                             "attempts remaining and the next attempt will begin in {} ms.",
                     request.getURI(),statusCode, retries, nextDelay);
            return ThreadUtil.<HttpResponse>failedFuture(new IllegalStateException("non-200"));
        })
        .exceptionally(err -> {
            if (retries > 0) {
                log.error(String.format(
                        "Failed to issue %s request to '%s'. There are %s attempts remaining " +
                                "and the next attempt will begin in %s ms.",
                        request.getMethod(), request.getURI(), retries, nextDelay), err);
            }
            else {
                log.error(String.format(
                        "Failed to issue %s request to '%s'. All retry attempts exhausted.",
                        request.getMethod(), request.getURI()), err);
            }
            return ThreadUtil.failedFuture(err);
        })
        .thenCompose(future -> {
            if (future.isCompletedExceptionally() && retries > 0) {
                return ThreadUtil.delayAndUnwrap(
                        nextDelay, TimeUnit.MILLISECONDS,
                        () -> executeRequest(request, retries - 1, nextDelay, isRetryable));
            }
            return future;
        });
    }


    // TODO: Implement sendGetCallback

    private void sendPostCallback(Object json, String callbackUri, List<Long> jobIds, String callbackType) {
        HttpPost post = new HttpPost(callbackUri);
        post.addHeader("Content-Type", "application/json");

        log.debug("Starting POST of {} callback to {} for job ids {}.", callbackType, callbackUri, jobIds);
        try {

            /*
             * Don't do this:
             * post.setEntity(new StringEntity(jsonUtils.serializeAsTextString(json)));
             *
             * That results in pre-buffering the content, which is unnecessary and may take a non-trivial amount of
             * time and memory depending on the amount of data. The Jackson JSON parser is fast, but JSON serialization
             * can still be an expensive operation.
             *
             * Instead, the line in use below utilizes a ContentProducer to produce the content on demand,
             * which allows the JSON serialization to be performed when the HttpPost is executed by the HttpAsyncClient
             * in a separate thread.
             *
             * See https://wiki.apache.org/HttpComponents/HttpEntity#EntityTemplate
             */
            post.setEntity(new EntityTemplate((outputStream) -> jsonUtils.serialize(json, outputStream)));

            httpAsyncClient.execute(post, new FutureCallback<HttpResponse>() {
                public void completed(final HttpResponse response) {
                    log.info("Sent {} callback to {} for job ids {}. Response: {}",
                             callbackType, callbackUri, jobIds, response);

                }
                public void failed(final Exception e) {
                    // We make a best effort attempt to send the callback, but an HTTP connection failure,
                    // or failure to get an HTTP response is not critical, so just log a warning.
                    // Also, don't bother logging the stack trace. That adds clutter.
                    if (e instanceof SocketTimeoutException) {
                        // The message for a SocketTimeoutException is "null", so let's be more descriptive.
                        log.warn("Sent {} callback to {} for job ids {}. Receiver did not respond.",
                                 callbackType, callbackUri, jobIds);
                    } else {
                        log.warn("Error sending {} callback to {} for job ids {}: {}",
                                 callbackType, callbackUri, jobIds, e.getMessage());
                    }
                }
                public void cancelled() {
                    log.warn("Cancelled sending {} callback to {} for job ids {}.",
                             callbackType, callbackUri, jobIds);
                }
            });
        } catch (WfmProcessingException | IllegalArgumentException e) {
            log.error(String.format("Error sending %s callback to %s for job ids %s.",
                                    callbackType, callbackUri, jobIds), e);
       }
    }
}
