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
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runners.MethodSorters;
import org.mitre.mpf.interop.JsonHealthReportDataCallbackBody;
import org.mitre.mpf.rest.api.*;
import org.mitre.mpf.wfm.service.component.AddComponentServiceImpl;
import org.mitre.mpf.wfm.service.component.ComponentRegistrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.Spark;

import java.io.IOException;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


// Test streaming health report callbacks. Test both the GET and POST methods.
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
	private ObjectMapper objectMapper = new ObjectMapper();

	private long healthReportGetJobId = -1L;
	private long healthReportPostJobId = -1L;
	private boolean gotHealthReportGetResponse = false;
	private boolean gotHealthReportPostResponse = false;
	private JsonHealthReportDataCallbackBody healthReportGetCallbackBody = null;
	private JsonHealthReportDataCallbackBody healthReportPostCallbackBody = null;

	@Autowired
	private AddComponentServiceImpl addComponentService;

	@Before // runs before each test
	public void initialize() throws ComponentRegistrationException, IOException {
		// TODO: When streaming components are implemented, consider using a real streaming component pipeline.

		String pipelinesUrl = WebRESTUtils.REST_URL + "pipelines";
		String pipelinesResponse = WebRESTUtils.getJSON(new URL(pipelinesUrl), WebRESTUtils.MPF_AUTHORIZATION);

		if (!pipelinesResponse.contains(PIPELINE_NAME)) {
			String descriptorPath = getClass().getClassLoader().getResource(DESCRIPTOR_NAME).getPath();
			String registerUrl = WebRESTUtils.REST_URL + "component/registerViaFile?filePath=" + descriptorPath;

			String registerResponseJson = WebRESTUtils.getJSON(new URL(registerUrl), WebRESTUtils.MPF_AUTHORIZATION);
			MpfResponse registerResponse = objectMapper.readValue(registerResponseJson, MpfResponse.class);

			Assert.assertEquals("Component successfully registered", registerResponse.getMessage());
		}
	}

	@Test(timeout = 5 * MINUTES)
	public void testPostHealthReportCallback() throws Exception {
		String externalId = Integer.toString(701);

		try {
			log.info("Beginning testPostHealthReportCallback()");

			setupSparkPost();

			// Submit streaming job request with a POST callback
			log.info("Creating a new Streaming Job for the POST test");
			String createJobUrl = WebRESTUtils.REST_URL + "streaming/jobs";

			// jobCreationResponseJson should be something like {"jobId":5, "outputObjectDirectory", "directoryWithJobIdHere", "mpfResponse":{"responseCode":0,"message":"success"}}
			String jobCreationResponseJson = createStreamingJob(createJobUrl, PIPELINE_NAME, externalId, "POST");

			JSONObject obj = new JSONObject(jobCreationResponseJson);
			healthReportPostJobId = Long.valueOf(obj.getInt("jobId"));
			log.info("Streaming jobId " + healthReportPostJobId + " created with POST method, jobCreationResponse=" + jobCreationResponseJson);

			// Wait for a health report callback that includes the jobId of this test job.
			// Health reports should periodically be sent every 30 seconds. Listen for at least one health report POST that includes our jobId.
			while (!gotHealthReportPostResponse) {
				Thread.sleep(1000); // test will eventually timeout
			}

			log.info("Received a Spark POST response");

			// Test to make sure the received health report is from the streaming job.
			Assert.assertTrue(
					healthReportPostCallbackBody.getJobId().contains(Long.valueOf(healthReportPostJobId))
							&& healthReportPostCallbackBody.getExternalId().contains(externalId));

			// Wait until ready to attempt a streaming job cancellation.
			String statusUrl = WebRESTUtils.REST_URL + "streaming/jobs/" + healthReportPostJobId;
			StreamingJobInfo streamingJobInfo;
			do {
				String streamingJobInfoJson = WebRESTUtils.getJSON(new URL(statusUrl), WebRESTUtils.MPF_AUTHORIZATION);
				streamingJobInfo = objectMapper.readValue(streamingJobInfoJson, StreamingJobInfo.class);

				// Check every three seconds
				Thread.sleep(3000);
			} while(streamingJobInfo == null); // test will eventually timeout

			// After running the POST test, clear the streaming job from REDIS with doCleanup enabled.
			List<NameValuePair> cancelParams = new ArrayList<NameValuePair>();
			cancelParams.add(new BasicNameValuePair("doCleanup", "true"));
			String cancelUrl = WebRESTUtils.REST_URL + "streaming/jobs/" + Long.toString(healthReportPostJobId) + "/cancel";

			String jobCancelResponseJson = WebRESTUtils.postParams(new URL(cancelUrl), cancelParams, WebRESTUtils.MPF_AUTHORIZATION, 200);
			StreamingJobCancelResponse jobCancelResponse = objectMapper.readValue(jobCancelResponseJson, StreamingJobCancelResponse.class);

			log.info("Finished POST test, cancelled streaming job:\n     " + jobCancelResponseJson);

			Assert.assertEquals(MpfResponse.RESPONSE_CODE_SUCCESS, jobCancelResponse.getMpfResponse().getResponseCode());
			Assert.assertTrue(jobCancelResponse.getDoCleanup());
		} finally {
			Spark.stop();
		}
	}

	@Test(timeout = 5 * MINUTES)
	public void testGetHealthReportCallback() throws Exception {
		String externalId = Integer.toString(702);

		try {
			log.info("Beginning testGetHealthReportCallback()");

			setupSparkGet();

			// Submit streaming job request with a GET callback
			log.info("Creating a new Streaming Job for the GET test.");
			String createJobUrl = WebRESTUtils.REST_URL + "streaming/jobs";

			// jobCreationResponseJson should be something like {"jobId":6, "outputObjectDirectory", "directoryWithJobIdHere", "mpfResponse":{"responseCode":0,"message":"success"}}
			String jobCreationResponseJson = createStreamingJob(createJobUrl, PIPELINE_NAME, externalId, "GET");

			JSONObject obj = new JSONObject(jobCreationResponseJson);
			healthReportGetJobId =  Long.valueOf(obj.getInt("jobId"));
			log.info("Streaming jobId " + healthReportGetJobId + " created with GET method, jobCreationResponse=" + jobCreationResponseJson);

			// Wait for a health report callback that includes the jobId of this test job.
			// Health reports should periodically be sent every 30 seconds. Listen for at least one health report GET that includes our jobId.
			while (!gotHealthReportGetResponse) {
				Thread.sleep(1000); // test will eventually timeout
			}

			log.info("Received a Spark GET response");

			// Test to make sure the received health report is from the streaming job.
			Assert.assertTrue(
					healthReportGetCallbackBody.getJobId().contains(Long.valueOf(healthReportGetJobId))
							&& healthReportGetCallbackBody.getExternalId().contains(externalId));

			// Wait until ready to attempt a streaming job cancellation
			String statusUrl = WebRESTUtils.REST_URL + "streaming/jobs/" + healthReportGetJobId;
			StreamingJobInfo streamingJobInfo = null;
			do {
				String streamingJobInfoJson = WebRESTUtils.getJSON(new URL(statusUrl), WebRESTUtils.MPF_AUTHORIZATION);
				streamingJobInfo = objectMapper.readValue(streamingJobInfoJson, StreamingJobInfo.class);

				// Check every three seconds
				Thread.sleep(3000);
			} while (streamingJobInfo == null); // test will eventually timeout

			// After running the GET test, clear the streaming job from REDIS with doCleanup enabled.
			List<NameValuePair> cancelParams = new ArrayList<NameValuePair>();
			cancelParams.add(new BasicNameValuePair("doCleanup", "true"));
			String cancelUrl = WebRESTUtils.REST_URL + "streaming/jobs/" + Long.toString(healthReportGetJobId) + "/cancel";

			String jobCancelResponseJson = WebRESTUtils.postParams(new URL(cancelUrl), cancelParams, WebRESTUtils.MPF_AUTHORIZATION, 200);
			StreamingJobCancelResponse jobCancelResponse = objectMapper.readValue(jobCancelResponseJson, StreamingJobCancelResponse.class);

			log.info("Finished GET test, cancelled streaming job:\n     " + jobCancelResponseJson);

			Assert.assertEquals(MpfResponse.RESPONSE_CODE_SUCCESS, jobCancelResponse.getMpfResponse().getResponseCode());
			Assert.assertTrue(jobCancelResponse.getDoCleanup());
		} finally {
			Spark.stop();
		}
	}

	private void setupSparkPost() {
		Spark.port(HEALTH_REPORT_CALLBACK_PORT);

		Spark.post("/callback", new Route() {
			@Override
			public Object handle(Request request, Response resp) throws Exception {
				log.info("Spark servicing " + request.requestMethod() + " health report callback at "
						+ DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now()) + ":\n     " + request.body());
				try {
					ObjectMapper jsonObjectMapper = new ObjectMapper();

					// The health report uses Java8 time, so we need to include the external JavaTimeModule which provides support for Java 8 Time.
					jsonObjectMapper.registerModule(new JavaTimeModule());

					healthReportPostCallbackBody = jsonObjectMapper.readValue(request.bodyAsBytes(), JsonHealthReportDataCallbackBody.class);

					log.info("Converted to JsonHealthReportDataCallbackBody:\n     " + healthReportPostCallbackBody);

					// If this health report includes the jobId for our POST test, then set indicator
					// that a health report sent using POST method has been received. Need to add this check
					// to ensure a periodic health report sent prior to creation of our test job doesn't prematurely stop the test.
					if (healthReportPostCallbackBody.getJobId().contains(healthReportPostJobId)) {
						gotHealthReportPostResponse = true;
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

	private void setupSparkGet() {
		Spark.port(HEALTH_REPORT_CALLBACK_PORT);

		Spark.get("/callback", new Route() {
			@Override
			public Object handle(Request request, Response resp) throws Exception {
				log.info("Spark servicing " + request.requestMethod() + " health report callback at "
						+ DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now()) + ":\n     " + request.queryString());
				try {
					// Convert from requests JSON parameters to String or List as needed to construct the health report.
					ObjectMapper objectMapper = new ObjectMapper();
					List<Long> jobIds = Arrays.asList(objectMapper.readValue(request.queryParams("jobId"), Long[].class));
					List<String> externalIds = Arrays.asList(objectMapper.readValue(request.queryParams("externalId"), String[].class));
					List<String> jobStatuses = Arrays.asList(objectMapper.readValue(request.queryParams("jobStatus"), String[].class));
					List<BigInteger> lastNewActivityAlertFrameIds =
							Arrays.asList(objectMapper.readValue(request.queryParams("lastNewActivityAlertFrameId"), BigInteger[].class));
					List<String> lastNewActivityAlertTimestamps =
							Arrays.asList(objectMapper.readValue(request.queryParams("lastNewActivityAlertTimestamp"), String[].class));

					healthReportGetCallbackBody = new JsonHealthReportDataCallbackBody(request.queryParams("reportDate"),
							jobIds, externalIds, jobStatuses, lastNewActivityAlertFrameIds, lastNewActivityAlertTimestamps);

					log.info("Converted to JsonHealthReportDataCallbackBody:\n     " + healthReportGetCallbackBody);

					// If this health report includes the jobId for our GET test, then set indicator
					// that a health report sent using GET method has been received. Need to add this check
					// to ensure a periodic health report sent prior to creation of our test job doesn't prematurely stop the test.
					if (healthReportGetCallbackBody.getJobId().contains(healthReportGetJobId)) {
						gotHealthReportGetResponse = true;
					}
				} catch (Exception e) {
					log.error("Exception caught while processing health report GET callback.", e);
					Assert.fail();
				}
				return "";
			}
		});

		Spark.awaitInitialization();
	}

	private String createStreamingJob(String url, String customPipelineName, String externalId, String callbackMethod) throws MalformedURLException{

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
		params.put("healthReportCallbackUri", "http://0.0.0.0:" + HEALTH_REPORT_CALLBACK_PORT + "/callback");
		params.put("callbackMethod", callbackMethod);
		String param_string = params.toString();

		log.info("Create streaming job request sent to: " + url + ", Params: " + param_string);
		return WebRESTUtils.postJSON(new URL(url), param_string, WebRESTUtils.MPF_AUTHORIZATION);
	}
}
