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

package org.mitre.mpf.wfm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mitre.mpf.interop.JsonHealthReportDataCallbackBody;
import org.mitre.mpf.rest.api.MpfResponse;
import org.mitre.mpf.rest.api.StreamingJobCancelResponse;
import org.mitre.mpf.rest.api.StreamingJobInfo;
import org.mitre.mpf.wfm.service.component.ComponentRegistrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.Spark;

// Test streaming health report callbacks. Test only the POST method, since GET is not being supported in OpenMPF for streaming jobs.
// NOTE: Needed to add confirmation of jobId in the health callbacks, because scheduled callbacks from a job created
// earlier were causing the callback to capture a health report sent before a later job.

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ITWebStreamingHealthReports {

    private static final int MINUTES = 1000 * 60; // 1000 milliseconds/sec, 60 sec/minute

    private static final String DESCRIPTOR_NAME = "CplusplusHelloWorldComponent.json";
    private static final String ALGORITHM_NAME = "CPLUSPLUSHELLOWORLD";
    private static final String PIPELINE_NAME = ALGORITHM_NAME + " DETECTION PIPELINE";

    private static final int HEALTH_REPORT_CALLBACK_PORT = 20160;

    private static final Logger log = LoggerFactory.getLogger(ITWebStreamingHealthReports.class);

    // for converting the JSON response to the actual java object
    private static ObjectMapper objectMapper = new ObjectMapper();
    private static boolean registeredComponent = false;

    private long healthReportPostJobId1 = -1L;
    private long healthReportPostJobId2 = -1L;
    private boolean gotHealthReportPostResponseForJob1 = false;
    private boolean gotHealthReportPostResponseForJob2 = false;
    private JsonHealthReportDataCallbackBody healthReportPostCallbackBody = null;

    // run once
    @BeforeClass
    public static void initialize() throws ComponentRegistrationException, IOException {
        // TODO: When streaming components are implemented, consider using a real streaming component pipeline.

        String pipelinesUrl = WebRESTUtils.REST_URL + "pipelines";
        String pipelinesResponse = WebRESTUtils
            .getJSON(new URL(pipelinesUrl), WebRESTUtils.MPF_AUTHORIZATION);

        if (!pipelinesResponse.contains(PIPELINE_NAME)) {
            String descriptorPath = ITWebStreamingHealthReports.class.getClassLoader()
                .getResource(DESCRIPTOR_NAME).getPath();
            String registerUrl =
                WebRESTUtils.REST_URL + "component/registerViaFile?filePath=" + descriptorPath;

            String registerResponseJson = WebRESTUtils
                .getJSON(new URL(registerUrl), WebRESTUtils.MPF_AUTHORIZATION);
            MpfResponse registerResponse = objectMapper
                .readValue(registerResponseJson, MpfResponse.class);

            Assert.assertEquals("Component successfully registered", registerResponse.getMessage());

            registeredComponent = true;
        }
    }

    // run once
    @AfterClass
    public static void shutdown() throws IOException {
        if (registeredComponent) {
            String descriptorPath = ITWebStreamingHealthReports.class.getClassLoader()
                .getResource(DESCRIPTOR_NAME).getPath();
            String unregisterUrl =
                WebRESTUtils.REST_URL + "component/unregisterViaFile?filePath=" + descriptorPath;

            String unregisterResponseJson = WebRESTUtils
                .getJSON(new URL(unregisterUrl), WebRESTUtils.MPF_AUTHORIZATION);
            MpfResponse unregisterResponse = objectMapper
                .readValue(unregisterResponseJson, MpfResponse.class);

            Assert.assertEquals("Component successfully unregistered",
                unregisterResponse.getMessage());
        }
    }

    @Test(timeout = 5 * MINUTES)
    public void testPostHealthReportCallback() throws Exception {
        String externalId1 = Integer.toString(701);
        String externalId2 = Integer.toString(702);
        try {
            log.info("Beginning testPostHealthReportCallback()");

            setupSparkPost();

            // Submit streaming job request with a POST callback
            String createJobUrl = WebRESTUtils.REST_URL + "streaming/jobs";

            log.info("Creating new Streaming Job #1 for the POST test");
            // jobCreationResponseJson2 should be something like {"jobId":5, "outputObjectDirectory", "directoryWithJobIdHere", "mpfResponse":{"responseCode":0,"message":"success"}}
            String jobCreationResponseJson1 = createStreamingJob(createJobUrl, PIPELINE_NAME,
                externalId1, "POST");

            JSONObject obj1 = new JSONObject(jobCreationResponseJson1);
            healthReportPostJobId1 = Long.valueOf(obj1.getInt("jobId"));
            log.info("Streaming job #1 with jobId " + healthReportPostJobId1
                + " created with POST method, jobCreationResponse=" + jobCreationResponseJson1);

            log.info("Creating new Streaming Job #2 for the POST test");
            // jobCreationResponseJson2 should be something like {"jobId":6, "outputObjectDirectory", "directoryWithJobIdHere", "mpfResponse":{"responseCode":0,"message":"success"}}
            String jobCreationResponseJson2 = createStreamingJob(createJobUrl, PIPELINE_NAME,
                externalId2, "POST");

            JSONObject obj2 = new JSONObject(jobCreationResponseJson2);
            healthReportPostJobId2 = Long.valueOf(obj2.getInt("jobId"));
            log.info("Streaming job #2 with jobId " + healthReportPostJobId2
                + " created with POST method, jobCreationResponse=" + jobCreationResponseJson2);

            // Wait for a health report callback that includes the jobId of these two test jobs.
            // Health reports should periodically be sent every 30 seconds, unless reset in the mpf.properties file.
            // Listen for a health report POST that has our two jobIds.
            while (!gotHealthReportPostResponseForJob1 && !gotHealthReportPostResponseForJob2) {
                Thread.sleep(1000); // test will eventually timeout
            }

            log.info(
                "Received Spark POST responses for Jobs #1 and #2, healthReportPostCallbackBody = "
                    + healthReportPostCallbackBody);

            // Test to make sure the received health report is from the two streaming jobs.
            Assert.assertTrue(
                healthReportPostCallbackBody.getJobIds()
                    .contains(Long.valueOf(healthReportPostJobId1))
                    && healthReportPostCallbackBody.getExternalIds().contains(externalId1));
            Assert.assertTrue(
                healthReportPostCallbackBody.getJobIds()
                    .contains(Long.valueOf(healthReportPostJobId2))
                    && healthReportPostCallbackBody.getExternalIds().contains(externalId2));

            // Wait until ready to attempt streaming job cancellations.
            String statusUrl1 = WebRESTUtils.REST_URL + "streaming/jobs/" + healthReportPostJobId1;
            StreamingJobInfo streamingJobInfo1;
            do {
                String streamingJobInfoJson1 = WebRESTUtils
                    .getJSON(new URL(statusUrl1), WebRESTUtils.MPF_AUTHORIZATION);
                streamingJobInfo1 = objectMapper
                    .readValue(streamingJobInfoJson1, StreamingJobInfo.class);

                // Check every three seconds
                Thread.sleep(3000);
            } while (streamingJobInfo1 == null); // test will eventually timeout

            String statusUrl2 = WebRESTUtils.REST_URL + "streaming/jobs/" + healthReportPostJobId2;
            StreamingJobInfo streamingJobInfo2;
            do {
                String streamingJobInfoJson2 = WebRESTUtils
                    .getJSON(new URL(statusUrl2), WebRESTUtils.MPF_AUTHORIZATION);
                streamingJobInfo2 = objectMapper
                    .readValue(streamingJobInfoJson2, StreamingJobInfo.class);

                // Check every three seconds
                Thread.sleep(3000);
            } while (streamingJobInfo2 == null); // test will eventually timeout

            // After running the POST test, clear the streaming jobs from REDIS with doCleanup enabled.
            List<NameValuePair> cancelParams = new ArrayList<NameValuePair>();
            cancelParams.add(new BasicNameValuePair("doCleanup", "true"));

            String cancelUrl1 =
                WebRESTUtils.REST_URL + "streaming/jobs/" + Long.toString(healthReportPostJobId1)
                    + "/cancel";

            String jobCancelResponseJson1 = WebRESTUtils
                .postParams(new URL(cancelUrl1), cancelParams, WebRESTUtils.MPF_AUTHORIZATION, 200);
            StreamingJobCancelResponse jobCancelResponse1 = objectMapper
                .readValue(jobCancelResponseJson1, StreamingJobCancelResponse.class);

            log.info(
                "Finished POST test, cancelled streaming job #1:\n     " + jobCancelResponseJson1);

            Assert.assertEquals(MpfResponse.RESPONSE_CODE_SUCCESS,
                jobCancelResponse1.getMpfResponse().getResponseCode());
            Assert.assertTrue(jobCancelResponse1.getDoCleanup());

            String cancelUrl2 =
                WebRESTUtils.REST_URL + "streaming/jobs/" + Long.toString(healthReportPostJobId2)
                    + "/cancel";

            String jobCancelResponseJson2 = WebRESTUtils
                .postParams(new URL(cancelUrl2), cancelParams, WebRESTUtils.MPF_AUTHORIZATION, 200);
            StreamingJobCancelResponse jobCancelResponse2 = objectMapper
                .readValue(jobCancelResponseJson2, StreamingJobCancelResponse.class);

            log.info(
                "Finished POST test, cancelled streaming job #2:\n     " + jobCancelResponseJson2);

            Assert.assertEquals(MpfResponse.RESPONSE_CODE_SUCCESS,
                jobCancelResponse2.getMpfResponse().getResponseCode());
            Assert.assertTrue(jobCancelResponse2.getDoCleanup());

        } finally {
            Spark.stop();
        }
    }

    private void setupSparkPost() {
        Spark.port(HEALTH_REPORT_CALLBACK_PORT);

        Spark.post("/callback", new Route() {
            @Override
            public Object handle(Request request, Response resp) throws Exception {
                log.info(
                    "Spark servicing " + request.requestMethod() + " health report callback at "
                        + DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now())
                        + ":\n     " + request.body());
                try {
                    ObjectMapper jsonObjectMapper = new ObjectMapper();

                    // The health report uses Java8 time, so we need to include the external JavaTimeModule which provides support for Java 8 Time.
                    jsonObjectMapper.registerModule(new JavaTimeModule());

                    healthReportPostCallbackBody = jsonObjectMapper
                        .readValue(request.bodyAsBytes(), JsonHealthReportDataCallbackBody.class);

                    log.info("Converted to JsonHealthReportDataCallbackBody:\n     "
                        + healthReportPostCallbackBody);

                    // If this health report includes the jobIds for our POST test, then set the appropriate indicator
                    // that a health report sent using POST method has been received. Need to add this check
                    // to ensure a periodic health report sent prior to creation of our test job doesn't prematurely stop the test.
                    // Note that the health report might contain jobId of both streaming jobs, so we can't do an else test here.
                    if (healthReportPostCallbackBody.getJobIds().contains(healthReportPostJobId1)) {
                        gotHealthReportPostResponseForJob1 = true;
                    }
                    if (healthReportPostCallbackBody.getJobIds().contains(healthReportPostJobId2)) {
                        gotHealthReportPostResponseForJob2 = true;
                    }

                } catch (Exception e) {
                    log.error("Exception caught while processing health report POST callback.", e);
                    Assert.fail();
                }
                return "";
            }
        });

        Spark.awaitInitialization();
    }

    private String createStreamingJob(String url, String customPipelineName, String externalId,
        String callbackMethod) throws MalformedURLException {

        // create a request for a new streaming job using a component that supports streaming jobs.
        JSONObject params = new JSONObject();
        params.put("pipelineName", customPipelineName);

        JSONObject stream = new JSONObject();
        stream.put("streamUri", "rtsp://test/test.mp4");
        stream.put("mediaProperties", new org.json.simple.JSONObject());
        stream.put("segmentSize", 100);

        params.put("stream", stream);
        params.put("stallTimeout", 180);
        params.put("externalId", externalId);
        params.put("enableOutputToDisk", true);
        params.put("priority", 0);
        params.put("healthReportCallbackUri",
            "http://0.0.0.0:" + HEALTH_REPORT_CALLBACK_PORT + "/callback");
        String param_string = params.toString();

        log.info("Create streaming job request sent to: " + url + ", Params: " + param_string);
        return WebRESTUtils.postJSON(new URL(url), param_string, WebRESTUtils.MPF_AUTHORIZATION);
    }
}
