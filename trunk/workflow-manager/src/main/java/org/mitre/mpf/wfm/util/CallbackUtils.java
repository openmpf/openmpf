/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.EntityTemplate;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.mitre.mpf.interop.JsonHealthReportCollection;
import org.mitre.mpf.interop.JsonSegmentSummaryReport;
import org.mitre.mpf.interop.exceptions.MpfInteropUsageException;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.RedisImpl;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class CallbackUtils {

    private static final int MAX_CONNECTIONS_PER_ROUTE = 10;
    private static final int MAX_CONNECTIONS_TOTAL = 100;

    private static final int SOCKET_TIMEOUT_MILLISEC = 5000;

    private static final Logger log = LoggerFactory.getLogger(CallbackUtils.class);

    @Autowired
    private JsonUtils jsonUtils;

    @Autowired
    @Qualifier(RedisImpl.REF)
    private Redis redis;

    private static CloseableHttpAsyncClient httpAsyncClient;

    @PostConstruct
    public void initialize() throws IOException {
        IOReactorConfig ioConfig = IOReactorConfig.custom()
                // the default connect timeout value for non-blocking connection requests.
                .setConnectTimeout(SOCKET_TIMEOUT_MILLISEC)
                // the default socket timeout value for non-blocking I/O operations
                .setSoTimeout(SOCKET_TIMEOUT_MILLISEC).build();

        ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor(ioConfig);

        PoolingNHttpClientConnectionManager cm = new PoolingNHttpClientConnectionManager(ioReactor);
        cm.setDefaultMaxPerRoute(MAX_CONNECTIONS_PER_ROUTE); // default is 2
        cm.setMaxTotal(MAX_CONNECTIONS_TOTAL); // default is 20

        httpAsyncClient = HttpAsyncClients.custom().setConnectionManager(cm).build();
        httpAsyncClient.start();
    }

    @PreDestroy
    public void shutdown() {
        try {
            if (httpAsyncClient != null) {
                httpAsyncClient.close();
            }
        } catch (IOException e) {
            log.warn("Unable to close httpAsyncClient.", e);
        }
    }

    // Send the health report to the URI identified by callbackUri, using the HTTP POST method.
    public void sendHealthReportCallback(String callbackUri, List<Long> jobIds) {

        // Get information from REDIS about these streaming jobs. Do this before spawning a thread to avoid
        // a race condition where the job may be cleared from REDIS before this information can be retrieved.

        // TODO: Consider refactoring this so that Redis only needs to be queried once per job,
        // instead of multiple times per job to get each of the following pieces of data.

        log.info("Starting POST of health report(s) to callbackUri " + callbackUri + " containing jobIds: " + jobIds);
        List<String> externalIds = redis.getExternalIds(jobIds);
        List<StreamingJobStatus> streamingJobStatuses = redis.getStreamingJobStatuses(jobIds);
        List<String> jobStatusTypes = streamingJobStatuses.stream().map( jobStatus -> jobStatus.getType().name()).collect(Collectors.toList());
        List<String> jobStatusDetails = streamingJobStatuses.stream().map( jobStatus -> jobStatus.getDetail()).collect(Collectors.toList());
        List<String> activityFrameIds = redis.getActivityFrameIdsAsStrings(jobIds);
        List<String> activityTimestamps = redis.getActivityTimestampsAsStrings(jobIds);

        try {
            JsonHealthReportCollection jsonBody = new JsonHealthReportCollection(
                    LocalDateTime.now(), jobIds, externalIds, jobStatusTypes, jobStatusDetails,
                    activityFrameIds, activityTimestamps);

            log.debug("POSTing to callbackUri = " + callbackUri + ", jsonBody = " + jsonBody);
            sendPostCallback(jsonBody, callbackUri);
        } catch (WfmProcessingException | MpfInteropUsageException e) {
            log.error("Error sending health report(s) to " + callbackUri + ".", e);
        }
    }

    // Send the summary report to the URI identified by callbackUri, using the HTTP POST method.
    public void sendSummaryReportCallback(JsonSegmentSummaryReport summaryReport, String callbackUri) {
        log.info("Starting POST of summaryReport to " + callbackUri + " with jobId " + summaryReport.getJobId());
        sendPostCallback(summaryReport, callbackUri);
    }

    // TODO: Implement doGetCallback

    private void sendPostCallback(Object json, String callbackUri) {
        HttpPost post = new HttpPost(callbackUri);
        post.addHeader("Content-Type", "application/json");

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
                    log.info("Callback sent to " + callbackUri + ". Response: " + response);
                }
                public void failed(final Exception e) {
                    // We make a best effort attempt to send the callback, but an HTTP connection failure,
                    // or failure to get an HTTP response is not critical, so just log a warning.
                    // Also, don't bother logging the stack trace. That adds clutter.
                    if (e instanceof SocketTimeoutException) {
                        // The message for a SocketTimeoutException is "null", so let's be more descriptive.
                        log.warn("Callback sent to " + callbackUri + ". Receiver did not respond.");
                    } else {
                        log.warn("Error sending callback to " + callbackUri + ": " + e.getMessage());
                    }
                }
                public void cancelled() {
                    log.warn("Cancelled sending callback to " + callbackUri + ".");
                }
            });
        } catch (WfmProcessingException | IllegalArgumentException e) {
            log.error("Error sending post to callback " + callbackUri + ".", e);
        }
    }
}