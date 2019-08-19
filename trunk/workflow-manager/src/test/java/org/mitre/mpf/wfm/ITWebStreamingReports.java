/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runners.MethodSorters;
import org.mitre.mpf.interop.JsonActionOutputObject;
import org.mitre.mpf.interop.JsonHealthReportCollection;
import org.mitre.mpf.interop.JsonSegmentSummaryReport;
import org.mitre.mpf.rest.api.MpfResponse;
import org.mitre.mpf.rest.api.StreamingJobCancelResponse;
import org.mitre.mpf.rest.api.StreamingJobInfo;
import org.mitre.mpf.wfm.service.component.ComponentRegistrationException;
import org.mitre.mpf.wfm.util.ObjectMapperFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// Test streaming health report and summary report callbacks.
// Test only the POST method, since GET is not being supported in OpenMPF for streaming jobs.

// NOTE: Needed to add confirmation of jobId in the health callbacks, because scheduled callbacks from a job created
// earlier were causing the callback to capture a health report sent before a later job.

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ITWebStreamingReports {

    private static final int MINUTES = 1000 * 60; // 1000 milliseconds/sec, 60 sec/minute

    private static final String PIPELINE_NAME = "SUBSENSE MOTION DETECTION (WITH TRACKING) PIPELINE";
    private static final String DETECTION_TYPE = "MOTION";

    // Use a public stream from: https://www.wowza.com/demo/rtsp
    private static final String STREAM_URI = "rtsp://wowzaec2demo.streamlock.net/vod/mp4:BigBuckBunny_115k.mov";

    private static final String CALLBACK_URI_PREFIX = "http://0.0.0.0:";
    private static final int CALLBACK_PORT = 20160;

    private static final String HEALTH_REPORT_CALLBACK_PATH = "/callback/health-report";
    private static final String HEALTH_REPORT_CALLBACK_URI =
            CALLBACK_URI_PREFIX + CALLBACK_PORT + HEALTH_REPORT_CALLBACK_PATH;

    private static final String SUMMARY_REPORT_CALLBACK_PATH = "/callback/summary-report";
    private static final String SUMMARY_REPORT_CALLBACK_URI =
            CALLBACK_URI_PREFIX + CALLBACK_PORT + SUMMARY_REPORT_CALLBACK_PATH;

    private static final String EXTERNAL_ID_1 = Integer.toString(701);
    private static final String EXTERNAL_ID_2 = Integer.toString(702);

    private static final Logger log = LoggerFactory.getLogger(ITWebStreamingReports.class);

    private static final ObjectMapper objectMapper = ObjectMapperFactory.customObjectMapper();

    private static long postJobId1 = -1L;
    private static long postJobId2 = -1L;

    private static Object healthReportLock = new Object();
    private static boolean healthReportCallbackFailed = false;
    private static JsonHealthReportCollection healthReportPostResponse = null;

    private static Object summaryReportLock = new Object();
    private static boolean summaryReportCallbackFailed = false;
    private static JsonSegmentSummaryReport summaryReportPostResponseForJob1 = null;
    private static JsonSegmentSummaryReport summaryReportPostResponseForJob2 = null;

    // run once
    @BeforeClass
    public static void initialize() throws ComponentRegistrationException, IOException {
        String pipelinesUrl = WebRESTUtils.REST_URL + "pipelines";
        String pipelinesResponse = WebRESTUtils
            .getJSON(new URL(pipelinesUrl), WebRESTUtils.MPF_AUTHORIZATION);

        Assume.assumeTrue("Please register the component that supports the following pipeline: " + PIPELINE_NAME,
                pipelinesResponse.contains(PIPELINE_NAME));

        setupSparkPost();

        // Submit streaming job request with a POST callback
        String createJobUrl = WebRESTUtils.REST_URL + "streaming/jobs";

        log.info("Creating new Streaming Job #1 for the POST test");
        // jobCreationResponseJson1 should be something like {"jobId":5, "outputObjectDirectory", "directoryWithJobIdHere", "mpfResponse":{"responseCode":0,"message":"success"}}
        String jobCreationResponseJson1 = createStreamingJob(createJobUrl, PIPELINE_NAME,
                EXTERNAL_ID_1, "POST");

        JSONObject obj1 = new JSONObject(jobCreationResponseJson1);
        postJobId1 = Long.valueOf(obj1.getInt("jobId"));
        log.info("Streaming job #1 with jobId " + postJobId1
                + " created with POST method, jobCreationResponse=" + jobCreationResponseJson1);

        log.info("Creating new Streaming Job #2 for the POST test");
        // jobCreationResponseJson2 should be something like {"jobId":6, "outputObjectDirectory", "directoryWithJobIdHere", "mpfResponse":{"responseCode":0,"message":"success"}}
        String jobCreationResponseJson2 = createStreamingJob(createJobUrl, PIPELINE_NAME,
                EXTERNAL_ID_2, "POST");

        JSONObject obj2 = new JSONObject(jobCreationResponseJson2);
        postJobId2 = Long.valueOf(obj2.getInt("jobId"));
        log.info("Streaming job #2 with jobId " + postJobId2
                + " created with POST method, jobCreationResponse=" + jobCreationResponseJson2);
    }

    // run once
    @AfterClass
    public static void shutdown() throws IOException, InterruptedException {

        try {
            // Wait until ready to attempt streaming job cancellations.
            String statusUrl1 = WebRESTUtils.REST_URL + "streaming/jobs/" + postJobId1;
            StreamingJobInfo streamingJobInfo1;
            do {
                String streamingJobInfoJson1 = WebRESTUtils
                        .getJSON(new URL(statusUrl1), WebRESTUtils.MPF_AUTHORIZATION);
                streamingJobInfo1 = objectMapper
                        .readValue(streamingJobInfoJson1, StreamingJobInfo.class);

                // Check every three seconds
                Thread.sleep(3000);
            } while (streamingJobInfo1 == null); // test will eventually timeout

            String statusUrl2 = WebRESTUtils.REST_URL + "streaming/jobs/" + postJobId2;
            StreamingJobInfo streamingJobInfo2;
            do {
                String streamingJobInfoJson2 = WebRESTUtils
                        .getJSON(new URL(statusUrl2), WebRESTUtils.MPF_AUTHORIZATION);
                streamingJobInfo2 = objectMapper
                        .readValue(streamingJobInfoJson2, StreamingJobInfo.class);

                // Check every three seconds
                Thread.sleep(3000);
            } while (streamingJobInfo2 == null); // test will eventually timeout

            // After running the POST test, clear the streaming jobs with doCleanup enabled.
            List<NameValuePair> cancelParams = new ArrayList<NameValuePair>();
            cancelParams.add(new BasicNameValuePair("doCleanup", "true"));

            String cancelUrl1 = WebRESTUtils.REST_URL + "streaming/jobs/" + Long.toString(postJobId1) + "/cancel";

            String jobCancelResponseJson1 = WebRESTUtils
                    .postParams(new URL(cancelUrl1), cancelParams, WebRESTUtils.MPF_AUTHORIZATION, 200);
            StreamingJobCancelResponse jobCancelResponse1 = objectMapper
                    .readValue(jobCancelResponseJson1, StreamingJobCancelResponse.class);

            log.info("Finished POST test, cancelled streaming job #1:\n     " + jobCancelResponseJson1);

            Assert.assertEquals(MpfResponse.RESPONSE_CODE_SUCCESS,
                    jobCancelResponse1.getMpfResponse().getResponseCode());
            Assert.assertTrue(jobCancelResponse1.getDoCleanup());

            String cancelUrl2 = WebRESTUtils.REST_URL + "streaming/jobs/" + Long.toString(postJobId2) + "/cancel";

            String jobCancelResponseJson2 = WebRESTUtils
                    .postParams(new URL(cancelUrl2), cancelParams, WebRESTUtils.MPF_AUTHORIZATION, 200);
            StreamingJobCancelResponse jobCancelResponse2 = objectMapper
                    .readValue(jobCancelResponseJson2, StreamingJobCancelResponse.class);

            log.info("Finished POST test, cancelled streaming job #2:\n     " + jobCancelResponseJson2);

            Assert.assertEquals(MpfResponse.RESPONSE_CODE_SUCCESS,
                    jobCancelResponse2.getMpfResponse().getResponseCode());
            Assert.assertTrue(jobCancelResponse2.getDoCleanup());
        } finally {
            Spark.stop();
        }
    }

    @Test(timeout = 5 * MINUTES)
    public void testPostPeriodicHealthReportCallback() throws InterruptedException {

        log.info("Beginning testPostHealthReportCallback()");

        // Wait for a health report callback that includes the jobId of these two test jobs.
        // Health reports should periodically be sent every 30 seconds, unless reset in the mpf.properties file.
        // Listen for a health report POST that has our two jobIds.
        do {
            Thread.sleep(1000); // test will eventually timeout
            Assert.assertFalse(healthReportCallbackFailed);
        } while (healthReportPostResponse == null);

        log.info("Received Spark POST health report response for Jobs #1 and #2:\n"
                + healthReportPostResponse);

        // Test to make sure the received health report is from the two streaming jobs.

        Assert.assertTrue(healthReportPostResponse.getJobIds().contains(Long.valueOf(postJobId1)));
        Assert.assertTrue(healthReportPostResponse.getExternalIds().contains(EXTERNAL_ID_1));

        Assert.assertTrue(healthReportPostResponse.getJobIds().contains(Long.valueOf(postJobId2)));
        Assert.assertTrue(healthReportPostResponse.getExternalIds().contains(EXTERNAL_ID_2));
    }

    @Test(timeout = 5 * MINUTES)
    public void testPostSummaryReportCallback() throws InterruptedException {

        log.info("Beginning testPostSummaryReportCallback()");

        // Wait for a summary report callbacks that include the jobIds of these two test jobs.
        do {
            Thread.sleep(1000); // test will eventually timeout
            Assert.assertFalse(summaryReportCallbackFailed);
        } while ((summaryReportPostResponseForJob1 == null) || (summaryReportPostResponseForJob2 == null));

        log.info("Received Spark POST summary report responses for Jobs #1 and #2");

        // Test to make sure the received summary reports are from the two streaming jobs.
        Assert.assertEquals(postJobId1, summaryReportPostResponseForJob1.getJobId());
        Assert.assertEquals("", summaryReportPostResponseForJob1.getErrorMessage());
        Assert.assertTrue(summaryReportPostResponseForJob1.getTypes().containsKey(DETECTION_TYPE) ||
                summaryReportPostResponseForJob1.getTypes().containsKey(JsonActionOutputObject.NO_TRACKS_TYPE));

        Assert.assertEquals(postJobId2, summaryReportPostResponseForJob2.getJobId());
        Assert.assertEquals("", summaryReportPostResponseForJob2.getErrorMessage());
        Assert.assertTrue(summaryReportPostResponseForJob2.getTypes().containsKey(DETECTION_TYPE) ||
                summaryReportPostResponseForJob2.getTypes().containsKey(JsonActionOutputObject.NO_TRACKS_TYPE));
    }

    private static void setupSparkPost() {
        Spark.port(CALLBACK_PORT);

        Spark.post(HEALTH_REPORT_CALLBACK_PATH, (request, resp) -> {

            synchronized (healthReportLock) {
                if (healthReportPostResponse != null) {
                    return ""; // we've already got what we need
                }

                log.info("In health report callback: Spark servicing " + request.requestMethod() + " health report callback at "
                        + DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now())
                        + ":\n     " + request.body());
                try {
                    JsonHealthReportCollection tmpHealthReportPostResponse = objectMapper
                            .readValue(request.bodyAsBytes(), JsonHealthReportCollection.class);

                    // If this health report includes the jobIds for our POST test, then set the appropriate indicator
                    // that the health report sent using POST method has been received.

                    if (tmpHealthReportPostResponse.getJobIds().contains(postJobId1) &&
                            tmpHealthReportPostResponse.getJobIds().contains(postJobId2)) {
                        healthReportPostResponse = tmpHealthReportPostResponse;
                        log.info("In health report callback: Got health report that contains both Job #1 and #2");
                    } else {
                        log.info("In health report callback: Still waiting for health report that contains both Job #1 and #2");
                    }

                } catch (Exception e) {
                    log.error("Exception caught while processing health report POST callback.", e);
                    healthReportCallbackFailed = true;
                }
                return "";
            }
        });

        Spark.post(SUMMARY_REPORT_CALLBACK_PATH, (request, resp) -> {

            synchronized (summaryReportLock) {
                if ((summaryReportPostResponseForJob1 != null) && (summaryReportPostResponseForJob2 != null)) {
                    return ""; // we've already got what we need
                }

                log.info("In summary report callback: Spark servicing " + request.requestMethod() + " summary report callback at "
                        + DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now())
                        + ":\n     " + request.body());
                try {
                    JsonSegmentSummaryReport tmpSummaryReportPostResponse = objectMapper
                            .readValue(request.bodyAsBytes(), JsonSegmentSummaryReport.class);

                    // If a summary report includes the jobId for our POST test, then set the appropriate indicator
                    // that the summary report sent using POST method has been received.

                    if (summaryReportPostResponseForJob1 == null) {
                        if (tmpSummaryReportPostResponse.getJobId() == postJobId1) {
                            summaryReportPostResponseForJob1 = tmpSummaryReportPostResponse;
                            log.info("In summary report callback: Got summary report for Job #1");
                        } else {
                            log.info("In summary report callback: Still waiting for summary report for Job #1");
                        }
                    }

                    if (summaryReportPostResponseForJob2 == null) {
                        if (tmpSummaryReportPostResponse.getJobId() == postJobId2) {
                            summaryReportPostResponseForJob2 = tmpSummaryReportPostResponse;
                            log.info("In summary report callback: Got summary report for Job #2");
                        } else {
                            log.info("In summary report callback: Still waiting for summary report for Job #2");
                        }
                    }

                } catch (Exception e) {
                    log.error("Exception caught while processing summary report POST callback.", e);
                    summaryReportCallbackFailed = true;
                }
                return "";
            }
        });

        Spark.awaitInitialization();
    }

    private static String createStreamingJob(String url, String pipelineName, String externalId,
        String callbackMethod) throws MalformedURLException {

        // create a request for a new streaming job using a component that supports streaming jobs.
        JSONObject params = new JSONObject();
        params.put("pipelineName", pipelineName);

        JSONObject mediaProperties = new JSONObject();
        mediaProperties.put("testProp", "testVal");

        JSONObject stream = new JSONObject();
        stream.put("streamUri", STREAM_URI);
        stream.put("mediaProperties", mediaProperties);
        stream.put("segmentSize", 10);

        params.put("stream", stream);
        params.put("stallTimeout", 10000);
        params.put("externalId", externalId);
        params.put("enableOutputToDisk", true);
        params.put("priority", 0);
        params.put("healthReportCallbackUri", HEALTH_REPORT_CALLBACK_URI);
        params.put("summaryReportCallbackUri", SUMMARY_REPORT_CALLBACK_URI);
        String param_string = params.toString();

        log.info("Create streaming job request sent to: " + url + ", Params: " + param_string);
        return WebRESTUtils.postJSON(new URL(url), param_string, WebRESTUtils.MPF_AUTHORIZATION);
    }
}
