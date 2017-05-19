/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mitre.mpf.interop.JsonCallbackBody;
import org.mitre.mpf.rest.api.*;
import org.mitre.mpf.wfm.ui.Utils;
import org.mitre.mpf.wfm.enums.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.Spark;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.nio.charset.StandardCharsets;

//mvn -Dtest=ITWebREST test if running tomcat before
//mvn verify -Dtest=none -DfailIfNoTests=false -Dit.test=ITWebREST
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ITWebREST {
	private static final Logger log = LoggerFactory.getLogger(ITWebREST.class);
	private static int testCtr = 0;
	private static final int MINUTES = 1000 * 60; // 1000 milliseconds/sec, 60 sec/minute
	private static long processedJobId = -1;	
	protected static String rest_url = Utils.BASE_URL + "/workflow-manager/rest/";	
	protected static String MPF_AUTHORIZATION = "Basic bXBmOm1wZjEyMw==";// mpf user base64 <username:password>
	protected static String ADMIN_AUTHORIZATION = "Basic YWRtaW46bXBmYWRtCg";// admin user base64 <username:password>
	private static final String TEST_PIPELINE_NAME = "OCV FACE DETECTION PIPELINE";
	protected long starttime = 0;
	protected static String JSONstring;
	protected static long job_created_id = -1L;
	//for converting the JSON response to the actual java object
	protected static ObjectMapper objectMapper = new ObjectMapper();

	// based on the registered components, this may not be a complete list of pipelines
	protected static String[] test_pipelines = {
			"MOG MOTION DETECTION (WITH TRACKING) PIPELINE",
			"OALPR LICENSE PLATE TEXT DETECTION PIPELINE",
			"OALPR LICENSE PLATE TEXT DETECTION (WITH MARKUP) PIPELINE",
			"OALPR LICENSE PLATE TEXT DETECTION (WITH MOG MOTION PREPROCESSOR) PIPELINE",
			"OALPR LICENSE PLATE TEXT DETECTION (WITH MOG MOTION PREPROCESSOR AND MARKUP) PIPELINE",
			"OALPR LICENSE PLATE TEXT DETECTION (WITH SUBSENSE MOTION PREPROCESSOR) PIPELINE",
			"OALPR LICENSE PLATE TEXT DETECTION (WITH SUBSENSE MOTION PREPROCESSOR AND MARKUP) PIPELINE",
			"OCV FACE DETECTION PIPELINE",
			"OCV FACE DETECTION (WITH MARKUP) PIPELINE",
			"OCV FACE DETECTION (WITH MOG MOTION PREPROCESSOR) PIPELINE",
			"OCV FACE DETECTION (WITH MOG MOTION PREPROCESSOR AND MARKUP) PIPELINE",
			"OCV FACE DETECTION (WITH SUBSENSE MOTION PREPROCESSOR) PIPELINE",
			"OCV FACE DETECTION (WITH SUBSENSE MOTION PREPROCESSOR AND MARKUP) PIPELINE",
			"DLIB FACE DETECTION PIPELINE",
			"DLIB FACE DETECTION (WITH MARKUP) PIPELINE",
			"DLIB FACE DETECTION (WITH MOG MOTION PREPROCESSOR) PIPELINE",
			"DLIB FACE DETECTION (WITH MOG MOTION PREPROCESSOR AND MARKUP) PIPELINE",
			"DLIB FACE DETECTION (WITH SUBSENSE MOTION PREPROCESSOR) PIPELINE",
			"DLIB FACE DETECTION (WITH SUBSENSE MOTION PREPROCESSOR AND MARKUP) PIPELINE",
			"OCV PERSON DETECTION PIPELINE",
			"OCV PERSON DETECTION (WITH MARKUP) PIPELINE",
			"OCV PERSON DETECTION (WITH MOG MOTION PREPROCESSOR) PIPELINE",
			"OCV PERSON DETECTION (WITH MOG MOTION PREPROCESSOR AND MARKUP) PIPELINE",
			"OCV PERSON DETECTION (WITH SUBSENSE MOTION PREPROCESSOR) PIPELINE",
			"OCV PERSON DETECTION (WITH SUBSENSE MOTION PREPROCESSOR AND MARKUP) PIPELINE",
			"SPHINX SPEECH DETECTION PIPELINE",
			"SPHINX SPEECH DETECTION (WITH MARKUP) PIPELINE"
	};

	// based on the registered components, this may not be a complete list of services
	protected static String[] test_services = { "Markup",
			"OcvPersonDetection", "SphinxSpeechDetection",
			"MogMotionDetection", "OcvFaceDetection",
			"DlibFaceDetection", "OalprLicensePlateTextDetection" };

	private static boolean test_ready = true;
	// run before each test
	@BeforeClass
	public static void setup() throws InterruptedException, JsonParseException, JsonMappingException, IOException {
		log.info("Starting the REST Tests");
		ITWebREST.job_created_id = ITWebREST.createNewJob();
		if(!ITWebREST.waitForJobToTerminate(ITWebREST.job_created_id, 5000)) {
			test_ready = false;
			throw new InterruptedException("job did not end");
		}
	}

	// run once
	@AfterClass
	public static void aftertest() {
		log.info("Finished REST Tests");
	}
	
	protected void startTest(String testname,String url) throws MalformedURLException {
		test_ready=false;
		testCtr++;
		log.info("Beginning test {} #{} REST: [{}]", testname,testCtr, url);
		starttime = DateTime.now().getMillis();
		//added this argument to start the test without a GET request... in case a POST is needed
		log.debug("[startTest] ");
	}

	protected void endTest(String testname) {
		long end = DateTime.now().getMillis() - starttime;
		log.info("Finished test #{} Time elapsed: {} milliseconds ", testCtr,end);
		test_ready=true;
	}

	// ///////////////////////////
	// Tests
	// ///////////////////////////
	//expects an exception @throws Exception
	@Test(timeout = 1 * MINUTES, expected = RuntimeException.class)
	public void testRestNoAuth() throws Exception {
		if(!test_ready){log.info("A test failed");return;}
		String url = rest_url + "jobs/stats.json";
		testCtr++;
		log.info("Beginning test {} #{} REST: [{}]", "testRestNoAuth",testCtr, url);
		GetJSON(new URL(url), null);
	}

	/*
	 * rest/jobs (ALL) is not currently exposed
	 */
//	@Test(timeout = 1 * MINUTES)
//	public void testPing_Jobs_Status() throws Exception {
//		if(!test_ready){log.info("A test failed");return;}
//		String url = rest_url + "jobs.json";
//		startTest("testPing_Jobs_Status",url);
//		JSONstring = GetJSON(new URL(url), MPF_AUTHORIZATION);
//		JSONArray array = new JSONArray(JSONstring);
//		log.info("array length :" + array.length());
//		Assert.assertTrue(array.length() >= 0);
//		endTest("testPing_Jobs_Status");
//	}

	/*
	 * rest/jobs (ALL) is not currently exposed
	 */
//	@Test(timeout = 1 * MINUTES)
//	public void test_Jobs_Status() throws Exception {
//		if(!test_ready){log.info("A test failed");return;}
//		String url = rest_url + "jobs.json";
//		startTest("test_Jobs_Status",url);
//		JSONstring = GetJSON(new URL(url), MPF_AUTHORIZATION);
//		JSONArray array = new JSONArray(JSONstring);
//		log.info("array length :" + array.length());
//		if (array.length() == 0) {
//			log.info("[Jobs_Status] No jobs to check the status.");
//			Assert.assertTrue(false);
//		} else {
//			boolean found = false;
//			//find our job
//			for (int i = 0;i<array.length();i++) {
//				JSONObject job = array.getJSONObject(i);
//				log.debug("[Jobs_Status] job :" + job);
//				if(job.getLong("jobId") == Long.parseLong(job_created_id )){
//					log.info("[Jobs_Status] job found :" + job);
//						Assert.assertTrue(job.getLong("startDate") > 0);
//						Assert.assertTrue(job.getInt("jobId") > 0);
//						Assert.assertTrue(job.getLong("endDate") > 0);
//						Assert.assertTrue(job.getString("pipelineName").length() > 0);
//						Assert.assertTrue(job.getString("pipelineName").equals(TEST_PIPELINE_NAME));
//						Assert.assertTrue(job.getString("jobStatus").equals("COMPLETE"));
//						Assert.assertTrue(job.getString("outputObjectPath").length() > 0);
//						Assert.assertTrue(job.getString("outputObjectPath").endsWith("detection.json"));
//
//					found =true;
//					break;
//				}
//			}
//			Assert.assertTrue(found);
//		}
//		endTest("test_Jobs_Status");
//	}
	
	/*
	 * rest/jobs (ALL) is not currently exposed, using the the exposed
	 * rest/jobs/{id} endpoint instead
	 */
	@Test(timeout = 1 * MINUTES)
	public void test_Jobs_Status_Single() throws Exception {
		if(!test_ready){log.info("A test failed");return;}
		String url = rest_url + "jobs/" + job_created_id + ".json";
		startTest("test_Jobs_Status",url);
		JSONstring = GetJSON(new URL(url), MPF_AUTHORIZATION);

		boolean found = false;
		//find our job
		JSONObject job = new JSONObject(JSONstring); // array.getJSONObject(i);
		log.debug("[Jobs_Status] job :" + job);
		if(job.getLong("jobId") == job_created_id){
			log.info("[Jobs_Status] job found :" + job);
			Assert.assertTrue(job.getLong("startDate") > 0);
			Assert.assertTrue(job.getInt("jobId") > 0);
			Assert.assertTrue(job.getLong("endDate") > 0);
			Assert.assertTrue(job.getString("pipelineName").length() > 0);
			Assert.assertTrue(job.getString("pipelineName").equals(TEST_PIPELINE_NAME));
			Assert.assertTrue(job.getString("jobStatus").equals("COMPLETE"));
			Assert.assertTrue(job.getString("outputObjectPath").length() > 0);
			Assert.assertTrue(job.getString("outputObjectPath").endsWith("detection.json"));

			found =true;
		}

		Assert.assertTrue(found);

		endTest("test_Jobs_Status");
	}

	/***
	 * @throws Exception
	 */
	@Test(timeout = 1 * MINUTES)
	public void testPing_Jobs_SerializedOutput() throws Exception {
		if(!test_ready){log.info("A test failed");return;}
		// just see if endpoint is there
		String url = rest_url + "jobs/" +  job_created_id + "/output/detection" ;
		startTest("testPing_Jobs_SerializedOutput",url);
		JSONstring = GetJSON(new URL(url), MPF_AUTHORIZATION);
		Assert.assertTrue(JSONstring != null);
		endTest("testPing_Jobs_SerializedOutput");
	}

	@Test(timeout = 2 * MINUTES)
	public void test_Jobs_SerializedOutput() throws Exception {
		if(!test_ready){log.info("A test failed");return;}
				
		String postJobsUrl = rest_url + "jobs";
		startTest("test_Jobs_SerializedOutput - postJobsUrl",postJobsUrl);
		String detPipeline = "OCV FACE DETECTION PIPELINE";

		//a video will be good to test being able to cancel before completion!
		String resourcePath = "samples/new_face_video.avi";
		String mediaPathUrl = getClass().getClassLoader().getResource(resourcePath).toURI().toURL().toExternalForm();

		JobCreationRequest jobCreationRequest = new JobCreationRequest();
		jobCreationRequest.getMedia().add(new JobCreationMediaData(mediaPathUrl));
		jobCreationRequest.setPipelineName(detPipeline);
		jobCreationRequest.setPriority(7); //why not
		
		//convert params to json string		
		String params = objectMapper.writeValueAsString(jobCreationRequest);
		
		URL actualUrl = new URL(postJobsUrl);
		String response = PostJSON(actualUrl, params, MPF_AUTHORIZATION);
		
		JobCreationResponse jobCreationResponse = objectMapper.readValue(response, JobCreationResponse.class);
		
		//check message, responseCode, and jobId
		Assert.assertTrue(jobCreationResponse.getMpfResponse().getResponseCode() == 0);
		Assert.assertNull(jobCreationResponse.getMpfResponse().getMessage());
		Assert.assertTrue(jobCreationResponse.getJobId() >= 1);
		
		long completeJobId = jobCreationResponse.getJobId();
		//use this id for resubmit and cancel testing
		log.info("completeJobId: " + completeJobId);
		
		//Need to wait for the job to complete
		log.info("Waiting for job with id '{}' to complete", completeJobId);		
		String urlJobsStatus = rest_url + "jobs/" + completeJobId;
		
		SingleJobInfo singleJobInfo = null;
		//wait till ready to attempt a job cancellation
		do {
			String jsonSingleJobInfoStr = GetJSON(new URL(urlJobsStatus), MPF_AUTHORIZATION);
			singleJobInfo  = objectMapper.readValue(jsonSingleJobInfoStr, SingleJobInfo.class);
			
			//check every three seconds
			Thread.sleep(3000);
		} while( !(singleJobInfo != null && singleJobInfo.isTerminal() && singleJobInfo.getJobStatus().startsWith("COMPLETE")) );
				
		endTest("test_Jobs_SerializedOutput - postJobsUrl");
				
		log.info("Job now complete using pipeline '{}'", detPipeline);
		String baseOutputUrl = rest_url + "jobs/" +  completeJobId + "/output/" ;	

		String outputObjectType = "detection";
		String url = baseOutputUrl + outputObjectType;
		startTest("test_Jobs_SerializedOutput - " + outputObjectType,url);
		JSONstring = GetJSON(new URL(url), MPF_AUTHORIZATION);
		Assert.assertTrue(JSONstring != null);// returns a path to the file
		// created during job creation
		log.info("[test_Jobs_SerializedOutput] json length :" + JSONstring.length());
		JSONParser parser = new JSONParser();
		org.json.simple.JSONObject json = (org.json.simple.JSONObject) parser
				.parse(JSONstring);
		log.info("[test_Jobs_SerializedOutput] - {} - json: {}", outputObjectType,
				json.toString());
		if(outputObjectType.equals("detection")) {
			Assert.assertTrue((long) json.get("jobId") == completeJobId);
			Assert.assertTrue(((String) json.get("objectId")).length() > 0);
			Assert.assertTrue(((String) json.get("timeStart")).length() > 0);
			org.json.simple.JSONObject pipeline = (org.json.simple.JSONObject) json.get("pipeline");
			Assert.assertTrue(((String) pipeline.get("name")).equals(detPipeline));
			Assert.assertTrue(Integer.parseInt(json.get("priority").toString()) == 7);
			org.json.simple.JSONArray arr = (org.json.simple.JSONArray) json.get("media");
			org.json.simple.JSONObject media = (org.json.simple.JSONObject) arr.get(0);
			String path = (String) media.get("path");
			Assert.assertTrue(path.endsWith(resourcePath));		
			Assert.assertTrue(((String) media.get("status")).equals("COMPLETE"));
		} else {
			//bad object type
			Assert.assertTrue(false);
		}
		endTest("test_Jobs_SerializedOutput - " + outputObjectType);
	}	
	
	//TOOD: use the new model
	@Test(timeout = 1 * MINUTES)
	public void testPing_Jobs_Stats() throws Exception {
		if(!test_ready){log.info("A test failed");return;}
		String url = rest_url + "jobs/stats.json";
		startTest("testPing_Jobs_Stats",url);
		JSONstring = GetJSON(new URL(url), MPF_AUTHORIZATION);
		JSONObject obj = new JSONObject(JSONstring);
		Assert.assertTrue(obj.has("totalJobs") && obj.has("aggregatePipelineStatsMap")
				&& obj.has("elapsedTimeMs") && obj.has("jobTypes"));
		//TODO: use model and do a comparison
		endTest("testPing_Jobs_Stats");
	}

	//TOOD: use the new model
	@Test(timeout = 1 * MINUTES)
	public void test_Jobs_Stats() throws Exception {
		if(!test_ready){log.info("A test failed");return;}
		String url = rest_url + "jobs/stats.json";
		startTest("test_Jobs_Stats",url);
		JSONstring = GetJSON(new URL(url), MPF_AUTHORIZATION);
		log.info("[test_Jobs_Stats] json:" + JSONstring);
		JSONObject objs = new JSONObject(JSONstring);
		boolean found = false;
		Assert.assertTrue(objs.has("totalJobs") && objs.has("aggregatePipelineStatsMap")
				&& objs.has("elapsedTimeMs") && objs.has("jobTypes"));
		if (objs.getInt("totalJobs") > 0) {
			JSONObject data = objs.getJSONObject("aggregatePipelineStatsMap");
			Iterator<String> keys = data.keys();			
			while (keys.hasNext()) {
				String key = keys.next();
				if (key.equals(TEST_PIPELINE_NAME))
					found = true;// see atleast if the job that was created is in there
			}
		}
		
		//TODO: use model and do a comparison
		
		Assert.assertTrue(found);
		endTest("test_Jobs_Stats");
	}

	@Test(timeout = 1 * MINUTES)
	public void testPing_Pipelines_Available() throws Exception {
		if(!test_ready){log.info("A test failed");return;}
		String url = rest_url + "pipelines.json";
		startTest("testPing_Pipelines_Available",url);
		JSONstring = GetJSON(new URL(url), MPF_AUTHORIZATION);
		JSONArray array = new JSONArray(JSONstring);
		log.info("array length :" + array.length());
		Assert.assertTrue(array.length() >= 0);
		endTest("testPing_Pipelines_Available");
	}

	/***
	 * Make sure the list of pipelines match the test list of pipeline
	 *
	 * @throws Exception
	 */
	@Test(timeout = 1 * MINUTES)
	public void test_Pipelines_Available() throws Exception {
		if(!test_ready){log.info("A test failed");return;}
		String url = rest_url + "pipelines.json";
		startTest("test_Pipelines_Available",url);
		JSONstring = GetJSON(new URL(url), MPF_AUTHORIZATION);
		JSONArray pipelines = new JSONArray(JSONstring);
		Assert.assertTrue(pipelines.length() >= 0);
		log.info("Pipelines available: (" + pipelines.length() + ") ");
		log.debug("Pipelines available: " + JSONstring);
		log.info("Pipelines testing : (" + test_pipelines.length + ")");
		JSONArray testing_pipelines = new JSONArray(test_pipelines);
		Assert.assertTrue(testing_pipelines.length() >= 0);

		for (int i = 0; i < testing_pipelines.length(); i++) {
			String test_pipeline = testing_pipelines.getString(i);
			boolean found = false;
			for (int j = 0; j < pipelines.length(); j++) {
				if (pipelines.getString(j).equals(test_pipeline)) {
					found = true;
					break;
				}
			}
			if (!found) {
				log.error("Pipeline not found: " + test_pipeline);
			}
			Assert.assertTrue(found);
		}

		endTest("test_Pipelines_Available");
	}

	@Test(timeout = 1 * MINUTES)
	public void testPing_NodeManager_getNodeManagerInfo() throws Exception {
		if(!test_ready){log.info("A test failed");return;}
		String url = rest_url + "nodes/info.json";
		startTest("testPing_NodeManager_getNodeManagerInfo",url);
		JSONstring = GetJSON(new URL(url), MPF_AUTHORIZATION);
		JSONObject obj = new JSONObject(JSONstring);
		JSONArray array = obj.getJSONArray("nodeModels");
		log.info("array length :" + array.length());
		Assert.assertTrue(array.length() >= 0);// 16
		endTest("testPing_NodeManager_getNodeManagerInfo");
	}
	
	@Test(timeout = 1 * MINUTES)
	public void test_NodeManager_getNodeManagerInfo() throws Exception {
		if(!test_ready){log.info("A test failed");return;}
		String url = rest_url + "nodes/info.json";
		startTest("test_NodeManager_getNodeManagerInfo",url);
		JSONstring = GetJSON(new URL(url), MPF_AUTHORIZATION);
		JSONObject obj = new JSONObject(JSONstring);
		JSONArray array = obj.getJSONArray("nodeModels");
		log.info("array length (should be >= 2):" + array.length()); // assume at least two services are running
		Assert.assertTrue(array.length() >= 2);

		for (String test_service : test_services) {
			log.debug("service:" + test_service);
			JSONObject service = null;
			for (int i = 0; i < array.length(); i++) {
				if (array.getJSONObject(i).getString("name").toLowerCase().contains(test_service.toLowerCase())) {
					service = array.getJSONObject(i);
					break;
				}
			}
			Assert.assertTrue(service != null);
			Assert.assertTrue(service.getInt("rank") >= 0);
			Assert.assertTrue(service.getInt("serviceCount") >= 0);
			Assert.assertTrue(service.getInt("restartCount") >= 0);
			Assert.assertTrue(service.getString("kind").equals("simple")
					|| service.getString("kind").equals("generic"));
		}
		endTest("test_NodeManager_getNodeManagerInfo");
	}

	// nodes/config GET
	@Test(timeout = 1 * MINUTES)
	public void testPing_NodeManager_getNodeManagerConfig() throws Exception {
		if(!test_ready){log.info("A test failed");return;}
		String url = rest_url + "nodes/config.json";
		startTest("testPing_NodeManager_getNodeManagerConfig",url);
		JSONstring = GetJSON(new URL(url), MPF_AUTHORIZATION);
		JSONArray array = new JSONArray(JSONstring);
		log.info("array length :" + array.length());
		Assert.assertTrue(array.length() >= 0);
		JSONObject obj = array.getJSONObject(0);
		JSONArray array2 = obj.getJSONArray("services");
		Assert.assertTrue(array2.length() >= 0);
		endTest("testPing_NodeManager_getNodeManagerConfig");
	}

	/***
	 * nodeManager/getNodeManagerConfig POST,GET
 	 * Test the first node for a few services and make sure 
 	 * they have some correct fields
	 **/
	protected void test_NodeManager_getNodeManagerConfig() throws Exception {
		if(!test_ready){log.info("A test failed");return;}
		String url = rest_url + "nodes/config.json";
		startTest("test_NodeManager_getNodeManagerConfig",url);
		JSONstring = GetJSON(new URL(url), MPF_AUTHORIZATION);
		log.info("[test_NodeManager_getNodeManagerConfig] GET:"+url);
		JSONArray array = new JSONArray(JSONstring);
		Assert.assertTrue(array.length() >= 0);
		log.info("[test_NodeManager_getNodeManagerConfig] services :" + JSONstring);
		JSONObject obj = array.getJSONObject(0);
		Assert.assertTrue(obj.getString("host").length() > 0);
		JSONArray services = obj.getJSONArray("services");
		Assert.assertTrue(services.length() >= 0);
		log.info("[test_NodeManager_getNodeManagerConfig] services :" + services.length());

		for (String test_service : test_services) {
			log.info("[test_NodeManager_getNodeManagerConfig] Verifying Service Exists:" + test_service);
			JSONObject service = null;
			for (int i = 0; i < services.length(); i++) {
				if (services.getJSONObject(i).getString("serviceName").toLowerCase().equals(test_service.toLowerCase())) {
					log.info("[test_NodeManager_getNodeManagerConfig] Service Found:" + test_service);
					service = services.getJSONObject(i);
					break;
				}
			}
			log.info("{} found {}", test_service, service != null);
			Assert.assertTrue(service != null);
			Assert.assertTrue(service.getString("cmd").length() >= 0);
		}

		endTest("test_NodeManager_getNodeManagerConfig");
	}
	@Test(timeout = 1 * MINUTES)
	public void test_NodeManager_getNodeManagerConfigGET() throws Exception {
		test_NodeManager_getNodeManagerConfig(/*false*/); //GET
	}
		
	@Test(timeout = 1 * MINUTES)
	public void test_NodeManager_saveNodeManagerConfigPOST() throws Exception {
		if(!test_ready){log.info("A test failed");return;}
		String url = rest_url + "nodes/config";
		//get the current config
		String config = rest_url + "nodes/config.json";
		startTest("test_NodeManager_saveNodeManagerConfigPOST",config);
		JSONstring = GetJSON(new URL(config), MPF_AUTHORIZATION);
		log.info("[saveNodeManagerConfigPOST] original config:"+JSONstring);
		String orig_config =JSONstring;
		JSONArray array = new JSONArray(JSONstring);
		Assert.assertTrue(array.length() >= 0);

		//modify original by removing first service
		JSONObject obj = array.getJSONObject(0);
		JSONArray services = obj.getJSONArray("services");
		JSONObject first = services.getJSONObject(0);
		log.info("[saveNodeManagerConfigPOST] Removing Service:" + first.getString("serviceName"));
		services.remove(0);
		array.remove(0);
		obj.put("services", services);
		array.put(obj);

		//post back
		String params = array.toString();
		log.debug("[saveNodeManagerConfigPOST]  post {} params:"+params,url);
		//requires admin auth
		JSONstring = PostJSON(new URL(url), params, ADMIN_AUTHORIZATION);		 
		log.debug("[saveNodeManagerConfigPOST]  post results:"+JSONstring);//should return true
		
		Assert.assertNotNull(JSONstring);
		MpfResponse mpfResponse = objectMapper.readValue(JSONstring, MpfResponse.class);
		
		//verify that the config did save
		Assert.assertTrue(mpfResponse.getResponseCode() == 0);
		Assert.assertNull(mpfResponse.getMessage());
		log.info("[saveNodeManagerConfigPOST]  Waiting 10 seconds for changes to complete");
		Thread.sleep(10000);

		//verify changes by pulling the config and make sure the service is missing
		log.debug("[saveNodeManagerConfigGet]  {}",url);
		JSONstring = GetJSON(new URL(config), MPF_AUTHORIZATION);
		log.info("[saveNodeManagerConfigPOST]  new config:"+JSONstring);
		array = new JSONArray(JSONstring);
		Assert.assertTrue(array.length() >= 0);
		obj = array.getJSONObject(0);
		services = obj.getJSONArray("services");
		for (int i = 0; i < services.length(); i++) {
			JSONObject service = services.getJSONObject(i);
			log.debug("Service:" + service.toString());
			Assert.assertFalse(service.getString("serviceName").equals(first.getString("serviceName")));
		}

		//cleanup - add back in for future tests if needed
		log.info("Restoring original configuration - post {}",url);
		//requires admin auth
		JSONstring = PostJSON(new URL(url), orig_config, ADMIN_AUTHORIZATION);
		
		log.debug("post results:"+JSONstring);
		
		mpfResponse = objectMapper.readValue(JSONstring, MpfResponse.class);
		Assert.assertTrue(mpfResponse.getResponseCode() == 0);
		Assert.assertNull(mpfResponse.getMessage());
		
		log.info("Waiting 10 seconds for changes to complete");
		Thread.sleep(10000);
		//Maybe verify it set?

		endTest("test_NodeManager_saveNodeManagerConfigPOST");
	}

	@Test(timeout = 1 * MINUTES)
	public void test_NodeManager_shutdown_startService() throws Exception {
		if(!test_ready){log.info("A test failed");return;}
		startTest("test_NodeManager_shutdown_startService","");
		JSONArray nodes = getNodes();
		Assert.assertTrue(nodes.length() > 0);
		// get the first node that is running
		JSONObject node = null;
		for (int i = 0; i < nodes.length(); i++) {
			node = nodes.getJSONObject(i);
			log.info("Node:" + node.toString());
			if (node.getString("lastKnownState").equals("Running"))
				break;
		}
		if (node == null) {
			log.error("No node is 'Running'");
			Assert.assertTrue(false);
		}
		String service_name = node.getString("name");
		Assert.assertTrue(service_name != null && service_name.length() > 0);
		
		/*
		 * stop service tests		
		 */
		List<NameValuePair> paramsList = new ArrayList<NameValuePair>();
		
		//make sure this fails with regular mpf auth (401)
		String url = rest_url + "nodes/services/" + service_name + "/stop" ;
		log.info("test_NodeManager_shutdownService get {}",url);
		//this will throw an IOException
		JSONstring = PostParams(new URL(url), paramsList, MPF_AUTHORIZATION, 401);
		//make sure response is null
		Assert.assertNull(JSONstring);
		
		//try with bad service name cat:asdfjkl:4
		url = rest_url + "nodes/services/" + "cat:asdfjkl:4" + "/stop" ;
		log.info("test_NodeManager_shutdownService get {}",url);		
		JSONstring = PostParams(new URL(url), paramsList, ADMIN_AUTHORIZATION, 200);
		//convert JSONString to mpfResponse
		MpfResponse mpfResponse = objectMapper.readValue(JSONstring, MpfResponse.class);
		//not a success
		Assert.assertTrue(mpfResponse.getResponseCode() != 0);
		//make sure service is in the response message
		Assert.assertTrue(mpfResponse.getMessage().contains("service"));
		
		//requires admin auth
		url = rest_url + "nodes/services/" + service_name + "/stop" ;
		log.info("test_NodeManager_shutdownService get {}",url);
		JSONstring = PostParams(new URL(url), paramsList, ADMIN_AUTHORIZATION, 200);
		//convert JSONString to mpfResponse
		mpfResponse = objectMapper.readValue(JSONstring, MpfResponse.class);
		//success
		Assert.assertTrue(mpfResponse.getResponseCode() == 0);
		//make sure service is null on success
		Assert.assertTrue(mpfResponse.getMessage() == null);
		Thread.sleep(3000);// give it some time to shut down

		// verify service is shut down
		nodes = getNodes();
		Assert.assertTrue(nodes.length() > 0);
		boolean completed = false;
		for (int i = 0; i < nodes.length(); i++) {
			JSONObject verify_node = nodes.getJSONObject(i);
			log.info("Node:" + verify_node.toString());
			if (node.getString("name").equals(verify_node.getString("name"))
					&& !verify_node.getString("lastKnownState").equals(
							"Running")) {
				completed = true;
				break;
			} 
		}
		Assert.assertTrue(completed);
		
		/*
		 * start service tests
		 */
		//make sure this fails with regular mpf auth (401)
		url = rest_url + "nodes/services/" + service_name + "/start" ;
		log.info("test_NodeManager_shutdownService get {}",url);
		//this will throw an IOException
		JSONstring = PostParams(new URL(url), paramsList, MPF_AUTHORIZATION, 401);
		//make sure response is null
		Assert.assertNull(JSONstring);
		
		//try with bad service name cat:asdfjkl:7
		url = rest_url + "nodes/services/" + "cat:asdfjkl:7" + "/start" ;
		log.info("test_NodeManager_shutdownService get {}",url);		
		JSONstring = PostParams(new URL(url), paramsList, ADMIN_AUTHORIZATION, 200);
		//convert JSONString to mpfResponse
		mpfResponse = objectMapper.readValue(JSONstring, MpfResponse.class);
		//not a success
		Assert.assertTrue(mpfResponse.getResponseCode() != 0);
		//make sure service is in the response message
		Assert.assertTrue(mpfResponse.getMessage().contains("service"));
				
		url = rest_url + "nodes/services/" + service_name + "/start" ;		
		log.info("test_NodeManager_shutdown_startService get {}",url);		
		paramsList = new ArrayList<NameValuePair>();
		//requires admin auth
		JSONstring = PostParams(new URL(url), paramsList, ADMIN_AUTHORIZATION, 200);
		//convert JSONString to mpfResponse
		mpfResponse = objectMapper.readValue(JSONstring, MpfResponse.class);
		//success
		Assert.assertTrue(mpfResponse.getResponseCode() == 0);
		//make sure service is null on success
		Assert.assertTrue(mpfResponse.getMessage() == null);
		Thread.sleep(3000);// give it some time to shut down

		// verify service is shut down
		nodes = getNodes();
		Assert.assertTrue(nodes.length() > 0);
		completed = false;
		for (int i = 0; i < nodes.length(); i++) {
			JSONObject verify_node = nodes.getJSONObject(i);
			log.info("Node:" + verify_node.toString());
			if (node.getString("name").equals(verify_node.getString("name"))
					&& verify_node.getString("lastKnownState").equals("Running")) {
				completed = true;
				break;
			}
		}
		Assert.assertTrue(completed);
		endTest("test_NodeManager_shutdown_startService");
	}

	@Test(timeout = 1 * MINUTES)
	public void test_MediaProcess() throws Exception {
		if(!test_ready){log.info("A test failed");return;}
		startTest("test_MediaProcess","");
		Assert.assertTrue(job_created_id >= 0);
		endTest("test_MediaProcess");
	}


	//rest/media/process
	//will use the job id for cancel and resubmit
	//using 1 after test to make sure this runs after before jobs/{id}/cancel and jobs/{id}/resubmit
	@Test(timeout = 1 * MINUTES)
	public void test1ProcessMedia() throws Exception {
		if(!test_ready){log.info("A test failed");return;}
		String url = rest_url + "jobs";
		startTest("test1ProcessMedia",url);

		//a video will be good to test being able to cancel before completion!
		String mediaPathUrl = getClass().getClassLoader().getResource("samples/new_face_video.avi").toURI().toURL().toExternalForm();

		JobCreationRequest jobCreationRequest = new JobCreationRequest();
		jobCreationRequest.getMedia().add(new JobCreationMediaData(mediaPathUrl));
		jobCreationRequest.setPipelineName("OCV FACE DETECTION PIPELINE");
		//jobCreationRequest.setPriority(priority);
		
		//convert params to json string	
		String params = objectMapper.writeValueAsString(jobCreationRequest);
		
		URL actualUrl = new URL(url);
		String response = PostJSON(actualUrl, params, MPF_AUTHORIZATION);
		
		JobCreationResponse jobCreationResponse = objectMapper.readValue(response, JobCreationResponse.class);
		
		//null error message and JobId >= 1, could check error code as well
		Assert.assertTrue(jobCreationResponse.getMpfResponse().getResponseCode() == 0);
		Assert.assertNull(jobCreationResponse.getMpfResponse().getMessage());
		Assert.assertTrue(jobCreationResponse.getJobId() >= 1);
		
		ITWebREST.processedJobId = jobCreationResponse.getJobId();
		//use this id for resubmit and cancel testing
		log.info("processedJobId: " + ITWebREST.processedJobId);
		
		endTest("test1ProcessMedia");
	}
	
	/**
	 * rest/jobs/{id}/cancel
	 */
	@Test(timeout = 2 * MINUTES) //giving this an extra minute, it may take some time for the job to get to a terminal (CANCELLED) state
	//using 2 after test to make sure this runs after jobs
	public void test2CancelInProgressJob() throws Exception {
		if(!test_ready){log.info("A test failed");return;}
		String url = rest_url + "jobs/" + Long.toString(ITWebREST.processedJobId) + "/cancel";
		startTest("test2CancelInProgressJob",url);		
		
		SingleJobInfo singleJobInfo = null;
		//wait till ready to attempt a job cancellation
		do {
			singleJobInfo  = getSingleJobInfo(ITWebREST.processedJobId);
			
			//check every three seconds
			Thread.sleep(3000);
		} while( !(singleJobInfo != null && !singleJobInfo.isTerminal() && singleJobInfo.getJobStatus().equals("IN_PROGRESS")) );		
		
		//jobId - REQUIRED
		//create params object
	    List<NameValuePair> params = new ArrayList<NameValuePair>();		
		URL actualUrl = new URL(url);
		String response = /*PostJSON*/ PostParams(actualUrl, params, MPF_AUTHORIZATION, 200);		
		MpfResponse mpfResponse = objectMapper.readValue(response, MpfResponse.class);
		
		//looking for isSuccess to be true and null error message
		Assert.assertTrue(mpfResponse.getResponseCode() == 0);
		Assert.assertNull(mpfResponse.getMessage());
		
		singleJobInfo = null;
		//wait till job is in a CANCELLED state to verify the job has been CANCELLED
		do {
			singleJobInfo  = getSingleJobInfo(ITWebREST.processedJobId);
			
			//check every three seconds
			Thread.sleep(3000);
		} while( !(singleJobInfo!= null && singleJobInfo.isTerminal() && singleJobInfo.getJobStatus().equals("CANCELLED")) );
		
		//completed the do/while and that means the job has been successfully cancelled
		endTest("test2CancelInProgressJob");
	}
	
	/**
	 * rest/jobs/{id}/resubmit
	 */
	@Test(timeout = 2 * MINUTES)
	//using 3 after test to make sure this runs after jobs/{id}/cancel
	public void test3ResubmitCancelledJob() throws Exception {
		if(!test_ready){log.info("A test failed");return;}
		String url = rest_url + "jobs/" + Long.toString(ITWebREST.processedJobId) + "/resubmit";
		startTest("test3ResubmitCancelledJob",url);
		
		//need to make sure the job is in a terminal state before trying to resubmit!
		String urlJobsStatus = rest_url + "jobs/" + ITWebREST.processedJobId + ".json";
		
		SingleJobInfo singleJobInfo = null;
		//wait till ready to attempt a job resubmission
		do {
			singleJobInfo  = getSingleJobInfo(ITWebREST.processedJobId);
			
			//check every three seconds
			Thread.sleep(3000);
		} while( !(singleJobInfo!= null && singleJobInfo.isTerminal() && singleJobInfo.getJobStatus().equals("CANCELLED")) );
		
		//jobId - REQUIRED - now a path variable
		//jobPriority - OPTIONAL		
		//create params object
	    List<NameValuePair> params = new ArrayList<NameValuePair>();
	    params.add(new BasicNameValuePair("jobPriority", "9"));		   	    
		URL actualUrl = new URL(url);
		String response = PostParams(actualUrl, params, MPF_AUTHORIZATION, 200);
		
		JobCreationResponse jobCreationResponse = objectMapper.readValue(response, JobCreationResponse.class);
		
		//null error message and verifying the resubmitted job id is equal to the processedJobId
		Assert.assertTrue(jobCreationResponse.getMpfResponse().getResponseCode() == 0);
		Assert.assertNull(jobCreationResponse.getMpfResponse().getMessage());
		Assert.assertEquals(jobCreationResponse.getJobId(), ITWebREST.processedJobId);
		
	    singleJobInfo = null;
	    //wait till job is complete to prevent logs from this job showing up in other tests...
	    do {
	    	singleJobInfo  = getSingleJobInfo(ITWebREST.processedJobId);
	      
	      //check every three seconds
	      Thread.sleep(3000);
	    } while( !(singleJobInfo!= null && singleJobInfo.isTerminal() && singleJobInfo.getJobStatus().equals("COMPLETE")) );
	    
	    Assert.assertTrue(singleJobInfo.getJobStatus().equals("COMPLETE"));
		
		endTest("test3ResubmitCancelledJob");
	}

	@Test(timeout = 1 * MINUTES)
	public void testPing_Pipelines() throws Exception {
		if(!test_ready){log.info("A test failed");return;}
		String url = rest_url + "pipelines.json";
		startTest("testPing_Pipelines",url);
		JSONstring = GetJSON(new URL(url), MPF_AUTHORIZATION);
		JSONArray array = new JSONArray(JSONstring);
		log.info("array length :" + array.length());
		Assert.assertTrue(array.length() >= 0);
		endTest("testPing_Pipelines");
	}

	@Test(timeout = 1 * MINUTES)
	public void testPing_NodeManagerInfo() throws Exception {
		if(!test_ready){log.info("A test failed");return;}
		String url = rest_url + "nodes/info.json";
		startTest("testPing_NodeManagerInfo",url);
		JSONstring = GetJSON(new URL(url), MPF_AUTHORIZATION);
		JSONObject obj = new JSONObject(JSONstring);
		JSONArray array =obj.getJSONArray("nodeModels");
		log.info("array length :" + array.length());
		Assert.assertTrue(array.length() >= 0);
		endTest("testPing_NodeManagerInfo");
	}
	
	//nodes/config GET
	@Test(timeout = 1 * MINUTES)
	public void testPing_NodeManagerConfig() throws Exception {
		if(!test_ready){log.info("A test failed");return;}
		String url = rest_url + "nodes/config.json";
		startTest("testPing_NodeManagerConfig",url);
		JSONstring = GetJSON(new URL(url), MPF_AUTHORIZATION);
		JSONArray array = new JSONArray(JSONstring);
		log.info("array length :" + array.length());
		Assert.assertTrue(array.length() >= 0);
		JSONObject obj = array.getJSONObject(0);
		JSONArray array2 =obj.getJSONArray("services");		
		Assert.assertTrue(array2.length() >= 0);
		endTest("testPing_NodeManagerConfig");
	}


/*
	//mvn -Dtest=ITWebREST#createLotsOfJobs test
	@Test
	public void createLotsOfJobs() throws Exception {
		int num = 2500;
		log.info("create "+num+" jobs");
		String url = rest_url + "jobs";
		JSONObject params = new JSONObject();
		params.put("pipelineName", "OCV FACE DETECTION PIPELINE");
		JSONArray params_media_urls = new JSONArray();
		params_media_urls.put(Utils.IMG_URL);
		params.put("mediaUris", params_media_urls);
		params.put("externalId", "external id");
		params.put("buildOutput", true);
		params.put("priority", 9);
		String param_string = params.toString();
		log.info("Post to: " + url + " Params: " + param_string);
		for(int i =0; i< num;i++){
			JSONstring = PostJSON(new URL(url), param_string, MPF_AUTHORIZATION);
		}
		log.info("create lots of jobs complete");
	}
*/
	// /////////////////////////
	// Helpers
	// ///////////////////////////

	protected JSONArray getNodes() throws JSONException, MalformedURLException {
		String url = rest_url + "nodes/info.json";
		log.debug("getNodes get {}",url);
		JSONObject obj = new JSONObject(GetJSON(new URL(url), MPF_AUTHORIZATION));
		return obj.getJSONArray("nodeModels");
	}

	protected static String GetJSON(URL url, String auth) {
		HttpURLConnection conn = null;
		log.debug("GetJSON url :" + url);
		try {
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");

			conn.setRequestProperty("Accept", "application/json");

			if (auth != null && auth.length() > 0)// add authorization
				conn.setRequestProperty("Authorization", auth);

			if (conn.getResponseCode() != 200) {
				throw new RuntimeException("Failed : HTTP error code : "
						+ conn.getResponseCode());
			}
			String results = getStringFromInputStream(conn.getInputStream());
			log.debug("url :" + url + " json results:" + results);
			return results;
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (conn != null)
				conn.disconnect();
		}
		return null;
	}

	/***
	 *
	 * @param url
	 * @param params
	 *            i.e. "{\"qty\":100,\"name\":\"iPad 4\"}"
	 * @return
	 */
	protected static String PostJSON(URL url, String params, String auth) {
		log.debug("PostJSON url :" + url);
		try {
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json");

			if (auth != null && auth.length() > 0)// add authorization
				conn.setRequestProperty("Authorization", auth);

			if(params != null){
				log.debug("url :" + url + " params:" + params);
				OutputStream os = conn.getOutputStream();
				os.write(params.getBytes());
				os.flush();
			}

			if (conn.getResponseCode() != HttpURLConnection.HTTP_CREATED && conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
				throw new RuntimeException("Failed : HTTP error code : "
						+ conn.getResponseCode());
			}

			String results = getStringFromInputStream(conn.getInputStream());
			log.debug("url :" + url + " json results:" + results);
			conn.disconnect();
			return results;
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/***
	 *
	 * @param url
	 *            i.e. "param1=a&param2=b&param3=c"
	 * @return
	 * @throws IOException
	 */
	protected String PostParams(URL url, List<NameValuePair> paramsList, String auth, int httpResponseCode) {
		try {
			//URLEncodedUtils is a nice helper when building a params set for http requests
			String urlParameters = URLEncodedUtils.format(paramsList, StandardCharsets.UTF_8);

			byte[] postData = urlParameters.getBytes( StandardCharsets.UTF_8 );
			int postDataLength = postData.length;

			HttpURLConnection conn= (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setInstanceFollowRedirects(false);
			conn.setRequestMethod( "POST" );
			conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			conn.setRequestProperty("charset", "utf-8");
			conn.setRequestProperty("Content-Length", Integer.toString( postDataLength));
			conn.setUseCaches(false);

			if (auth != null && auth.length() > 0)// add authorization
				conn.setRequestProperty("Authorization", auth);

			OutputStream os = conn.getOutputStream();
			os.write(postData);
			os.flush();

			if (conn.getResponseCode() != httpResponseCode) {
				throw new RuntimeException("Failed : HTTP error code : "
						+ conn.getResponseCode());
			}

			String results = getStringFromInputStream(conn.getInputStream());
			conn.disconnect();
			return results;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/***
	 * Helper method to convert InputStream to String
	 *
	 * @param is
	 * @return the data as a string
	 */
	private static String getStringFromInputStream(InputStream is) {
		BufferedReader br = null;
		StringBuilder sb = new StringBuilder();
		String line;
		try {
			br = new BufferedReader(new InputStreamReader(is));
			while ((line = br.readLine()) != null) {
				sb.append(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return sb.toString();
	}

	public static long createNewJob() throws MalformedURLException,
			InterruptedException {
		log.info("Creating new Job");
		String url = rest_url + "jobs";
		// create a JobCreationRequest
		JSONObject params = new JSONObject();
		params.put("pipelineName", TEST_PIPELINE_NAME);
		JSONObject params_media_urls = new JSONObject();
		params_media_urls.put(Utils.IMG_URL, new JSONObject());
		params.put("mediaUris", params_media_urls);
		params.put("externalId", "external id");
		params.put("buildOutput", true);
		params.put("priority", 9);
		String param_string = params.toString();
		log.info("Post to: " + url + " Params: " + param_string);
		JSONstring = PostJSON(new URL(url), param_string, MPF_AUTHORIZATION);
		log.info("results:" + JSONstring);
		JSONObject obj = new JSONObject(JSONstring);
		return Long.valueOf(obj.getInt("jobId"));
	}

	public static SingleJobInfo getSingleJobInfo(long jobId) throws JsonParseException, JsonMappingException, IOException {
		String urlJobsStatus = rest_url + "jobs/" + Long.toString(jobId) + ".json";
		String jsonJobResponse = GetJSON(new URL(urlJobsStatus), MPF_AUTHORIZATION);
		Assert.assertTrue("Failed to retrieve JSON when GETting job info for job id: " + Long.toString(jobId), jsonJobResponse.length() >= 0);
		return objectMapper.readValue(jsonJobResponse, SingleJobInfo.class);
	}
	
	public static JobStatus getJobsStatus(long jobid)throws JsonParseException, JsonMappingException, IOException  {
		SingleJobInfo singleJobInfo = getSingleJobInfo(jobid);
		//convert to the enum and return
		return JobStatus.valueOf(singleJobInfo.getJobStatus());		 
	}

    public static boolean waitForJobToTerminate(long jobid, long delay) throws InterruptedException, JsonParseException, JsonMappingException, IOException {
        log.info("[waitForJobToTerminate] job {}, delay:{} ", jobid, delay);
        int count=20;
        JobStatus status;
        do{
            status = getJobsStatus(jobid);
            log.info("[waitForJobToTerminate] job {}, status:{} delay:{} count{}" ,jobid,status,delay,count);
            Thread.sleep(delay);
            count--;
        }
        while(count > 0 && !status.isTerminal());
        if(count > 0) return true;
        return false;
    }

	final boolean[] sparkresponse = new boolean[2];
	final long[] sparkIds = new long[2];
	@Test(timeout = 5*MINUTES)
	public void testJobWithCallback() throws Exception {
		long externalId =  555;
		try {
			log.info("Beginning test #{} testJobWithCallback()", testCtr);
			testCtr++;
			log.info("Creating new Job");
			String url = rest_url + "jobs";

			// create a JobCreationRequest
			JSONObject params = new JSONObject();
			params.put("pipelineName", "OCV FACE DETECTION (WITH MARKUP) PIPELINE");

			ArrayList media = new ArrayList<JSONObject>();
			JSONObject medium = new JSONObject();
			medium.put("mediaUri", Utils.IMG_URL);
			medium.put("properties", new org.json.simple.JSONObject());
			media.add(medium);

			params.put("media", media);
			params.put("externalId", ""+externalId);
			params.put("buildOutput", true);
			params.put("priority", 9);
			params.put("callbackURL","http://0.0.0.0:20159/callback");
			params.put("callbackMethod","POST");
			String param_string = params.toString();

			sparkresponse[0] =false;
			sparkresponse[1] =false;

			setupSpark();//start the listener

			//submit a job request with a POST callback
			log.info("Post to: " + url + " Params: " + param_string);
			JSONstring = PostJSON(new URL(url), param_string, MPF_AUTHORIZATION);
			log.info("results:" + JSONstring);// {"errorCode":0,"errorMessage":null,"jobId":5}
			JSONObject obj = new JSONObject(JSONstring);
			long jobId =  Long.valueOf(obj.getInt("jobId"));

			//wait for it to callback
			int count = 0;
			while (sparkresponse[1] != true && count < 100) {
				log.info("Waiting for POST callback...");
				Thread.sleep(500);
				count++;
			}
			sparkresponse[0] = (jobId == sparkIds[0] && externalId == sparkIds[1]);
			Assert.assertTrue(sparkresponse[0]);

			//test GET
			sparkresponse[0] =false;
			sparkresponse[1] =false;
			sparkIds[0] = -1;
			sparkIds[1] = -1;
			params.put("callbackMethod","GET");
			param_string = params.toString();
			log.info("Post to: " + url + " Params: " + param_string);
			JSONstring = PostJSON(new URL(url), param_string, MPF_AUTHORIZATION);
			log.info("results:" + JSONstring);// {"errorCode":0,"errorMessage":null,"jobId":5}
			obj = new JSONObject(JSONstring);
			jobId =  Long.valueOf(obj.getInt("jobId"));

			//wait for it to callback
			count = 0;
			while (sparkresponse[1] != true  && count < 100) {
				log.info("Waiting for GET callback...");
				Thread.sleep(500);
				count++;
			}
			sparkresponse[0] = (jobId == sparkIds[0] && externalId == sparkIds[1]);
			Assert.assertTrue(sparkresponse[0]);

			log.info("Finished test testJobWithCallback()");
		} finally {
			Spark.stop();
		}
	}

	private void setupSpark(){
		Spark.port(20159);
		Spark.get("/callback", new Route() {
			@Override
			public Object handle(Request request, Response resp) throws Exception {
				log.info("Spark Servicing request..GET..from " + request.requestMethod());
				sparkIds[0] = Long.parseLong(request.queryParams("jobid"));
				sparkIds[1] = Long.parseLong(request.queryParams("externalid"));
				log.info("Spark GET Callback jobid=" + sparkIds[0] + " externalid="+sparkIds[1]);
				sparkresponse[1] = true;
				return "";
			}
		});
		Spark.post("/callback", new Route() {
			@Override
			public Object handle(Request request, Response resp) throws Exception {
				log.info("Spark Servicing request..POST..from " + request.requestMethod() + " body:"+request.body());
				ObjectMapper jsonObjectMapper = new ObjectMapper();
				JsonCallbackBody callbackBody = jsonObjectMapper.readValue(request.bodyAsBytes(), JsonCallbackBody.class);
				sparkIds[0] = callbackBody.getJobId();
				sparkIds[1] = Long.parseLong(callbackBody.getExternalId());
				log.info("Spark POST Callback jobid=" + sparkIds[0] + " externalid="+sparkIds[1]);
				sparkresponse[1] = true;
				return "";
			}
		});

		Spark.awaitInitialization();
	}
}
