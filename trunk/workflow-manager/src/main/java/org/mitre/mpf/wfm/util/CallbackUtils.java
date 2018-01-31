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

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.mitre.mpf.interop.JsonHealthReportCollection;
import org.mitre.mpf.interop.JsonSegmentSummaryReport;
import org.mitre.mpf.interop.exceptions.MpfInteropUsageException;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.RedisImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class CallbackUtils {

    private static final int CONNECTION_TIMEOUT_MILLISEC = 5000;
    private static final int INACTIVITY_TIMEOUT_MILLISEC = 2000;

    private static final Logger log = LoggerFactory.getLogger(CallbackUtils.class);


    @Autowired
    private ApplicationContext context;

    @Autowired
    private TaskExecutor taskExecutor;

    @Autowired
    @Qualifier(RedisImpl.REF)
    private Redis redis;

    // Send the health report to the URI identified by callbackUri, using the HTTP POST method.
    public void doHealthReportCallback(List<Long> jobIds, String callbackUri) {

        // Get other information from REDIS about these streaming jobs.
        // Do this before spawning a thread to avoid a race condition where the job may be cleared from REDIS.
        log.info("Sending POST of health report containing jobIds: " + jobIds);
        List<String> externalIds = redis.getExternalIds(jobIds);
        List<String> jobStatuses = redis.getJobStatusesAsString(jobIds);
        List<String> lastActivityFrameIds = redis.getHealthReportLastActivityFrameIdsAsStrings(jobIds);
        List<String> lastActivityTimestamps = redis.getHealthReportLastActivityTimestampsAsStrings(jobIds);

        try {
            JsonHealthReportCollection jsonBody = new JsonHealthReportCollection(
                    LocalDateTime.now(), jobIds, externalIds, jobStatuses,
                    lastActivityFrameIds, lastActivityTimestamps);

            log.info("Sending POST of healthReport: " + jsonBody);
            doPostCallback(jsonBody, callbackUri);
            log.info("Finished POST of healthReport: " + jsonBody);
        } catch (WfmProcessingException | MpfInteropUsageException e) {
            log.error("Error sending health report(s) to " + callbackUri + ".", e);
        }
    }

    // Send the summary report to the URI identified by callbackUri, using the HTTP POST method.
    public void doSummaryReportCallback(JsonSegmentSummaryReport summaryReport, String callbackUri) {
        summaryReport.setReportDate(LocalDateTime.now());

        log.info("Sending POST of summaryReport: " + summaryReport);
        doPostCallback(summaryReport, callbackUri);
    }

    private void doPostCallback(Object json, String callbackUri) {
        PostCallbackThread thread = context.getBean(PostCallbackThread.class, json, callbackUri);
        // thread.setJson(json);
        // thread.setCallbackUri(callbackUri);
        taskExecutor.execute(thread);
    }


    // TODO: Implement GetCallbackThread

    @Component
    @Scope("prototype") // each request returns a new instance
    public static class PostCallbackThread extends Thread { // must be static for Spring

        @Autowired
        private JsonUtils jsonUtils;

        private Object json;
        // public void setJson(Object json) { this.json = json; }

        private String callbackUri;
        // public void setCallbackUri(String callbackUri) { this.callbackUri = callbackUri; }

        public PostCallbackThread(Object json, String callbackUri) {
            this.json = json;
            this.callbackUri = callbackUri;
        }

        // Send the JSON to the URI identified by callbackUri, using the HTTP POST method.
        @Override
        public void run() {
            RequestConfig config = RequestConfig.custom()
                    // time to establish the connection with the remote host
                    .setConnectTimeout(CONNECTION_TIMEOUT_MILLISEC)
                    // the time waiting for data – after the connection was established; maximum time of inactivity between two data packets
                    .setConnectionRequestTimeout(INACTIVITY_TIMEOUT_MILLISEC)
                    // the time to wait for a connection from the connection manager/pool
                    .setSocketTimeout(CONNECTION_TIMEOUT_MILLISEC).build();

            HttpClient httpClient = HttpClientBuilder.create().setDefaultRequestConfig(config).build();

            HttpPost post = new HttpPost(callbackUri);
            post.addHeader("Content-Type", "application/json");

            try {
                log.info("Sending POST callback to " + callbackUri);
                post.setEntity(new StringEntity(jsonUtils.serializeAsTextString(json)));

                // If the http request was properly constructed, then send it.
                if (post != null) {
                    HttpResponse response = httpClient.execute(post);
                    log.info("Callback sent to " + callbackUri + ". Response: " + response);
                } else {
                    log.error("Error sending callback to " + callbackUri + ". Failed to create POST object from: " + json);
                }
            } catch (IOException e) {
                // An HTTP connection or response failure is not critical.
                // Don't bother logging the stack trace. The message is sufficient.
                log.warn("Error sending callback to " + callbackUri + ": " + e.getMessage());
            } catch (WfmProcessingException e) {
                log.error("Error sending callback to " + callbackUri + ".", e);
            }
        }
    }
}