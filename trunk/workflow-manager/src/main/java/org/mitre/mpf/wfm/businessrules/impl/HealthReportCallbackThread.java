/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.businessrules.impl;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.mitre.mpf.interop.JsonHealthReportDataCallbackBody;
import org.mitre.mpf.interop.exceptions.MpfInteropUsageException;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread creation class that supports sending of health reports to the requested health report callback URIs.
 * Note that this class only supports the HTTP POST method, The HTTP GET method for sending of health reports is not supported in OpenMPF.
 * Also note that sending of the health report requires that the time that the health report was sent be included in the health report.
 * To properly record the time, the timestamp is set in the run method.
 */
public class HealthReportCallbackThread implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(HealthReportCallbackThread.class);
    public static final String REF = "healthReportCallbackThread";

    private String callbackUri = null;
    private List<Long> jobIds = null;

    // Note: can't Autowire redis because this thread is outside of the application context. Redis access provided via the Constructor.
    private Redis redis;

    // Note: can't Autowire jsonUtils because this thread is outside of the application context. JsonUtils access provided via the Constructor.
    private JsonUtils jsonUtils;

    /**
     * Constructor used for sending a health report for a single streaming job.
     * @param redis short term storage for transient objects.
     * @param jsonUtils JSON utility class.
     * @param jobId unique id for the streaming job.
     * @param callbackUri health report callback URI for that single streaming job.
     */
    public HealthReportCallbackThread(Redis redis, JsonUtils jsonUtils, long jobId, String callbackUri) {
        this.redis = redis;
        this.jsonUtils = jsonUtils;
        this.callbackUri = callbackUri;
        jobIds = new ArrayList<Long>();
        jobIds.add(jobId);
    }

    /**
     * Constructor used for sending a health report for a set of streaming jobs which have
     * specified the same health report callback URI.
     * @param redis short term storage for transient objects.
     * @param jsonUtils JSON utility class.
     * @param jobIds list of unique ids for the streaming jobs which defined the same healthReportCallbackUri
     * @param callbackUri single health report callback URI defined for all these streaming jobs.
     */
    public HealthReportCallbackThread(Redis redis, JsonUtils jsonUtils, List<Long> jobIds, String callbackUri) {
        this.redis = redis;
        this.jsonUtils = jsonUtils;
        this.callbackUri = callbackUri;
        this.jobIds = jobIds;
    }

    // Send the health report to the URI identified by healthReportCallbackUri, using the HTTP POST method.
    private void sendHealthReportToCallback(HttpClient httpClient, LocalDateTime currentDateTime) {

        // Get other information from REDIS about these streaming jobs.
        log.info("HealthReportCallbackThread.sendHealthReportToCallback: posting health report containing jobIds=" + jobIds);
        List<String> externalIds = redis.getExternalIds(jobIds);
        List<String> jobStatuses = redis.getJobStatusesAsString(jobIds);
        List<String> lastActivityFrameIds = redis.getHealthReportLastActivityFrameIdsAsStrings(jobIds);
        List<String> lastActivityTimestamps = redis.getHealthReportLastActivityTimestampsAsStrings(jobIds);

        // Send the health report to the callback using POST.
        HttpPost post = new HttpPost(callbackUri);
        post.addHeader("Content-Type", "application/json");
        try {
            JsonHealthReportDataCallbackBody jsonBody = new JsonHealthReportDataCallbackBody(currentDateTime,
                jobIds, externalIds, jobStatuses,
                lastActivityFrameIds, lastActivityTimestamps);
            log.info("HealthReportCallback, sending POST of healthReport, jsonBody= " + jsonBody);
            post.setEntity(new StringEntity(jsonUtils.serializeAsTextString(jsonBody)));

            // If the http request was properly constructed, then send it.
            if (post != null) {
                HttpResponse response = httpClient.execute(post);
                log.info("Health report(s) sent to " + callbackUri + ". Response: " + response);
            } else {
                log.error("Error sending health report(s) to " + callbackUri + ". Failed to create POST object from: " + jsonBody);
            }

        } catch (WfmProcessingException | MpfInteropUsageException | IOException e) {
            log.error("Error sending health report(s) to " + callbackUri + ".", e);
        }

    }

    @Override
    public void run() {

        if ( jobIds == null ) {

            log.error("Error sending health report(s) to " + callbackUri + " because jobIds is null.");

        } else {

            final HttpClient httpClient = HttpClientBuilder.create().build();

            // Set the current timestamp for the health report.
            LocalDateTime currentDateTime = LocalDateTime.now();

            // Send the health report describing health of all jobIds to the single callback URI using the POST method.
            sendHealthReportToCallback(httpClient, currentDateTime);

         }
    }
}