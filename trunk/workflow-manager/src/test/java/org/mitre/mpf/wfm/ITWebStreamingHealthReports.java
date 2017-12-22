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
import org.mitre.mpf.wfm.service.component.ComponentRegistrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	private long healthReportPostJobId = -1L;
	private boolean gotHealthReportPostResponse = false;
	private JsonHealthReportDataCallbackBody healthReportPostCallbackBody = null;


	// run once
	@BeforeClass
	public static void initialize() throws ComponentRegistrationException, IOException {
		// TODO: When streaming components are implemented, consider using a real streaming component pipeline.

		String pipelinesUrl = WebRESTUtils.REST_URL + "pipelines";
		String pipelinesResponse = WebRESTUtils.getJSON(new URL(pipelinesUrl), WebRESTUtils.MPF_AUTHORIZATION);

		if (!pipelinesResponse.contains(PIPELINE_NAME)) {
			String descriptorPath = ITWebStreamingHealthReports.class.getClassLoader().getResource(DESCRIPTOR_NAME).getPath();
			String registerUrl = WebRESTUtils.REST_URL + "component/registerViaFile?filePath=" + descriptorPath;

			String registerResponseJson = WebRESTUtils.getJSON(new URL(registerUrl), WebRESTUtils.MPF_AUTHORIZATION);
			MpfResponse registerResponse = objectMapper.readValue(registerResponseJson, MpfResponse.class);

			Assert.assertEquals("Component successfully registered", registerResponse.getMessage());

			registeredComponent = true;
		}
	}

	// run once
	@AfterClass
	public static void shutdown() throws IOException {
		if (registeredComponent) {
			String descriptorPath = ITWebStreamingHealthReports.class.getClassLoader().getResource(DESCRIPTOR_NAME).getPath();
			String unregisterUrl = WebRESTUtils.REST_URL + "component/unregisterViaFile?filePath=" + descriptorPath;

			String unregisterResponseJson = WebRESTUtils.getJSON(new URL(unregisterUrl), WebRESTUtils.MPF_AUTHORIZATION);
			MpfResponse unregisterResponse = objectMapper.readValue(unregisterResponseJson, MpfResponse.class);

			Assert.assertEquals("Component successfully unregistered", unregisterResponse.getMessage());
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
			// Health reports should periodically be sent every 30 seconds, unless reset in the mpf.properties file.
			// Listen for at least one health report POST that includes our jobId.
			while (!gotHealthReportPostResponse) {
				Thread.sleep(1000); // test will eventually timeout
			}

			log.info("Received a Spark POST response");

			// Test to make sure the received health report is from the streaming job.
			Assert.assertTrue(
					healthReportPostCallbackBody.getJobIds().contains(Long.valueOf(healthReportPostJobId))
							&& healthReportPostCallbackBody.getExternalIds().contains(externalId));

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
					if (healthReportPostCallbackBody.getJobIds().contains(healthReportPostJobId)) {
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
		String param_string = params.toString();

		log.info("Create streaming job request sent to: " + url + ", Params: " + param_string);
		return WebRESTUtils.postJSON(new URL(url), param_string, WebRESTUtils.MPF_AUTHORIZATION);
	}
}
