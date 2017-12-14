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

package org.mitre.mpf.mvc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runner.notification.RunListener;
import org.mitre.mpf.interop.JsonHealthReportDataCallbackBody;
import org.mitre.mpf.rest.api.StreamingJobCreationRequest;
import org.mitre.mpf.rest.api.StreamingJobCreationResponse;
import org.mitre.mpf.rest.api.StreamingJobInfo;
import org.mitre.mpf.rest.api.component.ComponentState;
import org.mitre.mpf.rest.api.component.RegisterComponentModel;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.businessrules.JobRequestBo;
import org.mitre.mpf.wfm.businessrules.StreamingJobRequestBo;
import org.mitre.mpf.wfm.businessrules.impl.JobRequestBoImpl;
import org.mitre.mpf.wfm.businessrules.impl.StreamingJobRequestBoImpl;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateDao;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateStreamingJobRequestDaoImpl;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobRequest;
import org.mitre.mpf.wfm.event.JobProgress;
import org.mitre.mpf.wfm.service.*;
import org.mitre.mpf.wfm.service.component.*;
import org.mitre.mpf.wfm.ui.Utils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultHandler;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.Spark;

import javax.persistence.MapKeyClass;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.mitre.mpf.test.TestUtil.anyNonNull;
import static org.mitre.mpf.test.TestUtil.whereArg;
import static org.mitre.mpf.wfm.service.component.TestDescriptorConstants.*;
import static org.mitre.mpf.wfm.service.component.TestDescriptorConstants.COMPONENT_NAME;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ContextConfiguration(locations = {"classpath:applicationContext.xml"})
// @WebAppConfiguration

@RunWith(SpringJUnit4ClassRunner.class)
@RunListener.ThreadSafe
public class TestStreamingJobController {

    /*
    @Mock
    private ComponentDeploymentService _mockDeploymentService;

    @Mock
    private ComponentStateService _mockStateService;

    @Mock
    private ObjectMapper _mockObjectMapper;

    @Mock
    private NodeManagerService _mockNodeManager;

    @InjectMocks
    private AddComponentServiceImpl _addComponentService;

    @Mock
    private PipelineService _mockPipelineService;

    @Mock
    private ComponentDescriptorValidator _mockDescriptorValidator;

    @Mock
    private CustomPipelineValidator _mockPipelineValidator;

    @Mock
    private RemoveComponentService _mockRemoveComponentService;

    @Mock
    private StreamingServiceManager _mockStreamingServiceManager;
    */

    @Mock
    private JobProgress jobProgress;

    @Mock
    private PipelineServiceImpl pipelineServiceImpl;

    //@Mock // dependency of StreamingJobController
    //private HibernateDao<StreamingJobRequest> streamingJobRequestDao;

    @Mock // dependency of StreamingJobController
    private StreamingJobRequestBoImpl streamingJobRequestBoImpl;

    @Mock // dependency of StreamingJobController
    private PropertiesUtil _propertiesUtil;

    @Mock // dependency of StreamingJobController
    private MpfServiceImpl mpfServiceImpl;

    @InjectMocks
    private MockStreamingJobController mockStreamingJobController;


    private MockMvc mockMvc;

    private static final Logger log = LoggerFactory.getLogger(TestStreamingJobController.class);
    private static final int MINUTES = 1000 * 60; // 1000 milliseconds/sec, 60 sec/minute

    private static final String _testPackageName = "test-package.tar.gz";
    protected static String rest_url = Utils.BASE_URL + "/workflow-manager/rest/";
    protected static String MPF_AUTHORIZATION = "Basic bXBmOm1wZjEyMw==";// mpf user base64 <username:password>
    protected static String ADMIN_AUTHORIZATION = "Basic YWRtaW46bXBmYWRtCg";// admin user base64 <username:password>

    @Before
    public void setup() throws WfmProcessingException {
        MockitoAnnotations.initMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(mockStreamingJobController).build();
        // mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    // Test the Health Report Callbacks, both the GET and POST methods.
    // Note #1: Need to create 2 streaming jobs for these tests, but the components don't yet support streaming. Working around this
    // issue by spoofing the WFM by using a place-holder streaming component created using Mockito.
    // Note #2: Needed to add confirmation of jobId in the health callbacks, because scheduled callbacks from a job created
    // earlier was causing the callback to capture a health report sent before a later job under test was scheduled, and
    // causing the test to fail.
    final int healthReportCallbackPort = 20160;
    long jobIdGetTest = -1L;
    long jobIdPostTest = -1L;
    boolean healthSparkGetResponse = false;
    boolean healthSparkPostResponse = false;
    JsonHealthReportDataCallbackBody healthReportGetCallbackBody = null;
    JsonHealthReportDataCallbackBody healthReportPostCallbackBody = null;

    @Test(timeout = 5*MINUTES)
    public void testStreamingJobWithHealthReportCallback() throws Exception {
        String myExternalId1 =  "myExternalId is "+701;
        String myExternalId2 =  "myExternalId is "+702;

        try {
            // Spoof the WFM by using a streaming component created using Mockito as a place-holder.
            // Create a Mock component descriptor which supports streaming jobs, and creates the custom pipeline named PIPELINE_NAME
            JsonComponentDescriptor descriptor = TestDescriptorFactory.getWithCustomPipeline();
            Optional<JsonComponentDescriptor.Pipeline> customPipeline = descriptor.pipelines.stream().findFirst();

/*
            setUpMocksForDescriptor(descriptor);

            when(_mockNodeManager.getServiceModels())
                    .thenReturn(Collections.singletonMap("fake name", null));

            when(_mockNodeManager.addService(anyNonNull()))
                    .thenReturn(true);

            // Act
            _addComponentService.registerComponent(_testPackageName);

//
//            // Assert
//            verify(_mockStateService)
//                    .replacePackageState(_testPackageName, ComponentState.REGISTERING);
//
//            verify(_mockStateService, atLeastOnce())
//                    .update(whereArg(
//                            rcm -> rcm.getActions().containsAll(ACTION_NAMES)
//                                    && rcm.getTasks().containsAll(TASK_NAMES)
//                                    && rcm.getPipelines().contains(PIPELINE_NAME)));
//
//            verifyDescriptorAlgoSaved(descriptor);
//
//            verify(_mockPipelineService, times(3))
//                    .saveAction(whereArg(ad -> ad.getAlgorithmRef().equals(REFERENCED_ALGO_NAME)));
//
//            verify(_mockPipelineService)
//                    .saveAction(whereArg(ad -> ad.getName().equals(ACTION_NAMES.get(0))
//                            && ad.getProperties().stream()
//                            .anyMatch(pd -> pd.getName().equals(ACTION1_PROP_NAMES.get(0))
//                                    && pd.getValue().equals(ACTION1_PROP_VALUES.get(0)))));
//
//            verify(_mockPipelineService)
//                    .saveTask(whereArg(t ->
//                            t.getName().equals(TASK_NAMES.get(0))
//                                    && t.getDescription().equals(TASK_NAMES.get(0) + " description")
//                                    && t.getActions().size() == 1));
//
//            verify(_mockPipelineService)
//                    .saveTask(whereArg(t ->
//                            t.getName().equals(TASK_NAMES.get(1))
//                                    && t.getDescription().equals(TASK_NAMES.get(1) + " description")
//                                    && t.getActions().size() == 2));
//
//            verify(_mockPipelineService)
//                    .savePipeline(whereArg(p ->
//                            p.getName().equals(PIPELINE_NAME)
//                                    && p.getDescription().contains("description")
//                                    && p.getTaskRefs().size() == 2));
//
//            verify(_mockNodeManager)
//                    .addService(whereArg(s -> s.getName().equals(COMPONENT_NAME)));
//
//            verify(_mockStreamingServiceManager)
//                    .addService(whereArg(
//                            s -> s.getServiceName().equals(COMPONENT_NAME)
//                                    && s.getAlgorithmName().equals(descriptor.algorithm.name.toUpperCase())
//                                    && s.getEnvironmentVariables().size() == descriptor.environmentVariables.size()));
//
//            assertNeverUndeployed();
//

//
//            // Should have a custom pipeline that supports streaming for this test.
//            Assert.assertTrue(customPipeline.isPresent());
//            Assert.assertTrue(descriptor.supportsStreamProcessing());
//

            // End of section to create a Mock component descriptor which supports streaming jobs

            healthSparkGetResponse = false;
            healthSparkPostResponse = false;

            setupSparkForHealthReport(); // Start the listener for health reports.

            // Submit 1st streaming job request with a POST callback
            log.info("testStreamingJobWithHealthReportCallback: Creating a new Streaming Job for the POST test");
            // String url = rest_url + "streaming/jobs";

            // jobCreationResponse should be something like {"jobId":5, "outputObjectDirectory", "directoryWithJobIdHere", "mpfResponse":{"responseCode":0,"message":"success"}}
*/

            StreamingJobCreationResponse jobCreationResponse = createStreamingJobForHealthReportTest(customPipeline.get().name, myExternalId1, "POST");
            jobIdPostTest = jobCreationResponse.getJobId();

            log.info("testStreamingJobWithHealthReportCallback: streaming jobId " + jobIdPostTest + " created with POST method, jobCreationResponse=" + jobCreationResponse);

/*
            // Wait for a Health Report callback that includes the jobId of this test job.
            // Health reports should periodically be sent every 30 seconds. Listen for at least one Health Report POST that includes our jobId.
            int count = 0;
            while (healthSparkPostResponse != true && count < 120) {
                Thread.sleep(1000);
                count++;
            }

            if ( healthSparkPostResponse ) {
                log.info("testStreamingJobWithHealthReportCallback: received a Spark POST response, while testing jobIdPostTest=" + jobIdPostTest +", healthReportPostCallbackBody="+healthReportPostCallbackBody);
                if (healthReportPostCallbackBody != null) {
                    // Test to make sure the received health report is from the 1st streaming job.
                    Assert.assertTrue(
                            healthReportPostCallbackBody.getJobId().contains(Long.valueOf(jobIdPostTest))
                                    && healthReportPostCallbackBody.getExternalId().contains(myExternalId1));
                } else {
                    log.error("testStreamingJobWithHealthReportCallback: Error, couldn't form a Health Report from the POST request test");
                }
            } else {
                log.error("testStreamingJobWithHealthReportCallback: Error, didn't receive a response to the POST request test");
            }

            // Wait till ready to attempt a streaming job cancellation.
            String urlStreamingJobId1Status = rest_url + "streaming/jobs/" + jobIdPostTest;
            StreamingJobInfo streamingJobInfo = null;
            do {
                //WORK: String jsonStreamingJobInfo = GetJSON(new URL(urlStreamingJobId1Status), MPF_AUTHORIZATION);
                //WORK: streamingJobInfo  = objectMapper.readValue(jsonStreamingJobInfo, StreamingJobInfo.class);

                // Check every three seconds
                Thread.sleep(3000);
            } while( streamingJobInfo == null );

            // After running the POST test, clear the 1st streaming job from REDIS with doCleanup enabled.
            List<NameValuePair> cancelParams = new ArrayList<NameValuePair>();
            cancelParams.add(new BasicNameValuePair("doCleanup", "true"));
            URL cancelUrl = new URL(rest_url + "streaming/jobs/" + Long.toString(jobIdPostTest) + "/cancel");
            //WORK: String jobCancelResponse = PostParams(cancelUrl, cancelParams, MPF_AUTHORIZATION, 200);
            log.info("testStreamingJobWithHealthReportCallback: finished POST test, cancelling 1st streaming job using cancelUrl=" + cancelUrl +
                    " and cancelParams=" + cancelParams);
            //WORK: log.info("testStreamingJobWithHealthReportCallback: finished POST test, cancelled 1st streaming job with results:" + jobCancelResponse);
*/

            /*
            // Submit 2nd streaming job request with a GET callback.
            log.info("testStreamingJobWithHealthReportCallback: Creating a new Streaming Job for the GET test");

            // jobCreationResponse should be something like {"jobId":6, "outputObjectDirectory", "directoryWithJobIdHere", "mpfResponse":{"responseCode":0,"message":"success"}}
            jobCreationResponse = createStreamingJobForHealthReportTest(url, customPipeline.get().name, myExternalId2, "GET");
            log.info("testStreamingJobWithHealthReportCallback: create streaming job GET results:" + jobCreationResponse);
            obj = new JSONObject(jobCreationResponse);
            jobIdGetTest =  Long.valueOf(obj.getInt("jobId"));
            log.info("testStreamingJobWithHealthReportCallback: streaming jobId " + jobIdGetTest + " created with GET method, jobCreationResponse=" + jobCreationResponse);

            // Wait for a Health Report callback that includes the jobId of this test job.
            // Health reports should periodically be sent every 30 second. Listen for at least one Health Report GET that includes our jobId.
            count = 0;
            while (healthSparkGetResponse != true  && count < 120) {
                Thread.sleep(1000);
                count++;
            }

            if ( healthSparkGetResponse ) {
                log.info("testStreamingJobWithHealthReportCallback: received a Spark GET response while testing jobIdGetTest=" + jobIdGetTest +", healthReportGetCallbackBody="+healthReportGetCallbackBody);
                if (healthReportGetCallbackBody != null) {
                    // Test to make sure the received health report is from the 2nd streaming job.
                    Assert.assertTrue(
                        healthReportGetCallbackBody.getJobId().contains(Long.valueOf(jobIdGetTest))
                            && healthReportGetCallbackBody.getExternalId().contains(myExternalId2));
                } else {
                    log.error("testStreamingJobWithHealthReportCallback: Error, couldn't form a Health Report from the GET request test");
                }
            } else {
                log.error("testStreamingJobWithHealthReportCallback: Error, didn't receive a response to the GET request test");
            }
            */

            /*
            // Wait till ready to attempt a streaming job cancellation
            String urlStreamingJobId2Status = rest_url + "streaming/jobs/" + jobIdGetTest;
            streamingJobInfo = null;
            do {
                String jsonStreamingJobInfo = GetJSON(new URL(urlStreamingJobId2Status), MPF_AUTHORIZATION);
                streamingJobInfo  = objectMapper.readValue(jsonStreamingJobInfo, StreamingJobInfo.class);

                // Check every three seconds
                Thread.sleep(3000);
            } while( streamingJobInfo == null );

            // After running the GET test, clear the 2nd streaming job from REDIS with doCleanup enabled.
            cancelUrl = new URL(rest_url + "streaming/jobs/" + Long.toString(jobIdGetTest) + "/cancel");
            jobCancelResponse = PostParams(cancelUrl, cancelParams, MPF_AUTHORIZATION, 200);
            log.info("testStreamingJobWithHealthReportCallback: finished GET test, cancelling 2nd streaming job using cancelUrl=" + cancelUrl +
                    " and cancelParams=" + cancelParams);
            log.info("testStreamingJobWithHealthReportCallback: finished GET test, cancelled 2nd streaming job with results:" + jobCancelResponse);

            log.info("testStreamingJobWithHealthReportCallback: Finished POST and GET tests of health report callbacks");
            */
        } finally {
            Spark.stop();
        }
    } // end of method testStreamingJobWithHealthReportCallback

    /*
    private void setUpMocksForDescriptor(JsonComponentDescriptor descriptor) throws DuplicateComponentException, IOException {
        RegisterComponentModel rcm = new RegisterComponentModel();
        rcm.setComponentState(ComponentState.UPLOADED);
        rcm.setPackageFileName(_testPackageName);

        when(_mockStateService.getByPackageFile(_testPackageName))
                .thenReturn(Optional.of(rcm));

        when(_mockDeploymentService.deployComponent(_testPackageName))
                .thenReturn(DESCRIPTOR_PATH);

        when(_mockObjectMapper.readValue(new File(DESCRIPTOR_PATH), JsonComponentDescriptor.class))
                .thenReturn(descriptor);
    }
    */


    /** Create a StreamingJobCreationRequest for the Health Report system tests. Issue: the components don't yet support streaming. Working around this
     * issue by spoofing the WFM by using a streaming component created using Mockito as a place-holder.
     */
    private StreamingJobCreationResponse createStreamingJobForHealthReportTest(String customPipelineName, String externalId, String callbackMethod) throws Exception {

        // create a request for a new streaming job using a component that supports streaming jobs.
        JSONObject params = new JSONObject();
        params.put("pipelineName", customPipelineName);

        JSONObject stream = new JSONObject();
        // Using this sample video for initial testing.
        stream.put("streamUri", "rtsp://home/mpf/openmpf-projects/openmpf/trunk/mpf-system-tests/target/test-classes/samples/person/obama-basketball.mp4");
        stream.put("mediaProperties", new org.json.simple.JSONObject());
        stream.put("segmentSize", 100);

        params.put("stream", stream);
        params.put("stallTimeout", 180);
        params.put("externalId", externalId);
        params.put("enableOutputToDisk", true);
        params.put("priority", 0);
        params.put("healthReportCallbackUri", "http://0.0.0.0:" + healthReportCallbackPort + "/callback");
        params.put("callbackMethod", callbackMethod);

        // final StreamingJobCreationResponse response;
        MvcResult mvcResult = mockMvc.perform(post("/rest/streaming/jobs")
                .header("Authorization", ADMIN_AUTHORIZATION)
                .contentType(MediaType.APPLICATION_JSON)
                .content(params.toString())
                .accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andReturn();

        String strResponse = mvcResult.getResponse().getContentAsString();
        //ObjectMapper jsonObjectMapper = new ObjectMapper();
        //StreamingJobCreationResponse response = jsonObjectMapper.readValue(mvcResult.getResponse().getContentAsByteArray(), StreamingJobCreationResponse.class);

        // return _streamingJobController.createStreamingJobRest(request).getBody();
        return null; // DEBUG
    }


    private void setupSparkForHealthReport(){
        Spark.port(healthReportCallbackPort);
        Spark.get("/callback", new Route() {
            @Override
            public Object handle(Request request, Response resp) throws Exception {
                log.info("Spark Servicing request..  Received a HealthReport GET Callback ..from method " + request.requestMethod());
                try {

                    log.info("Spark GET Health Report Callback, request.queryParams(reportDate)=" + request.queryParams("reportDate"));
                    log.info("Spark GET Health Report Callback, request.queryParams(jobId)=" + request.queryParams("jobId"));
                    log.info("Spark GET Health Report Callback, request.queryParams(externalId)=" + request.queryParams("externalId"));
                    log.info("Spark GET Health Report Callback, request.queryParams(jobStatus)=" + request.queryParams("jobStatus"));
                    log.info("Spark GET Health Report Callback, request.queryParams(lastNewActivityAlertFrameId)=" + request.queryParams("lastNewActivityAlertFrameId"));
                    log.info("Spark GET Health Report Callback, request.queryParams(lastNewActivityAlertTimestamp)=" + request.queryParams("lastNewActivityAlertTimestamp"));

                    // Convert from requests JSON parameters to String or List as needed to construct the health report.
                    ObjectMapper objectMapper = new ObjectMapper();
                    List<Long> jobIds = Arrays.asList(objectMapper.readValue(request.queryParams("jobId"), Long[].class));
                    List<String> externalIds = Arrays.asList(objectMapper.readValue(request.queryParams("externalId"), String[].class));
                    List<String> jobStatuses = Arrays.asList(objectMapper.readValue(request.queryParams("jobStatus"), String[].class));
                    List<BigInteger> lastNewActivityAlertFrameIds = Arrays.asList(objectMapper.readValue(request.queryParams("lastNewActivityAlertFrameId"), BigInteger[].class));
                    List<String> lastNewActivityAlertTimestamps = Arrays.asList(objectMapper.readValue(request.queryParams("lastNewActivityAlertTimestamp"), String[].class));
                    healthReportGetCallbackBody = new JsonHealthReportDataCallbackBody(request.queryParams("reportDate"),
                            jobIds, externalIds, jobStatuses, lastNewActivityAlertFrameIds, lastNewActivityAlertTimestamps);
                    log.info("Spark GET Callback, received Health Report at time="+ DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now()) + ", with timestamp "
                            + healthReportGetCallbackBody.getReportDate());
                    log.info("  jobIds=" + healthReportGetCallbackBody.getJobId());
                    log.info("  externalIds=" + healthReportGetCallbackBody.getExternalId());
                    log.info("  jobStatus=" + healthReportGetCallbackBody.getJobStatus());
                    log.info("  lastNewActivityAlertFrameId=" + healthReportGetCallbackBody.getLastNewActivityAlertFrameId());
                    log.info("  lastNewActivityAlertTimestamp=" + healthReportGetCallbackBody.getLastNewActivityAlertTimeStamp());
                    // If this health report includes the jobId for our GET test, then set indicator
                    // that a health report sent using GET method has been received. Need to add this check
                    // to ensure a periodic health report sent prior to creation of our test job doesn't prematurely stop the test.
                    if ( healthReportGetCallbackBody.getJobId().contains(jobIdGetTest) ) {
                        healthSparkGetResponse = true;
                    }
                } catch (Exception e) {
                    log.error("Error, Exception caught while processing Health Report GET callback.", e);
                }
                return "";
            }
        });
        Spark.post("/callback", new Route() {
            @Override
            public Object handle(Request request, Response resp) throws Exception {
                log.info("Spark Servicing request..POST..from method " + request.requestMethod() + " body:"+request.body());
                try {
                    ObjectMapper jsonObjectMapper = new ObjectMapper();
                    // The health report uses Java8 time, so we need to include the external JavaTimeModule which provides support for Java 8 Time.
                    JavaTimeModule javaTimeModule = new JavaTimeModule();
                    jsonObjectMapper.registerModule(javaTimeModule);
                    log.info("Spark POST Callback, received Health Report at time="+ DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now()) + ", constructing JsonHealthReportDataCallbackBody");
                    healthReportPostCallbackBody = jsonObjectMapper.readValue(request.bodyAsBytes(), JsonHealthReportDataCallbackBody.class);
                    log.info("Spark POST Callback, received Health Report " + healthReportPostCallbackBody);
                    log.info("  jobIds=" + healthReportPostCallbackBody.getJobId());
                    log.info("  externalIds=" + healthReportPostCallbackBody.getExternalId());
                    log.info("  jobStatus=" + healthReportPostCallbackBody.getJobStatus());
                    log.info("  lastNewActivityAlertFrameId=" + healthReportPostCallbackBody.getLastNewActivityAlertFrameId());
                    log.info("  lastNewActivityAlertTimestamp=" + healthReportPostCallbackBody.getLastNewActivityAlertTimeStamp());
                    // If this health report includes the jobId for our POST test, then set indicator
                    // that a health report sent using POST method has been received. Need to add this check
                    // to ensure a periodic health report sent prior to creation of our test job doesn't prematurely stop the test.
                    if ( healthReportPostCallbackBody.getJobId().contains(jobIdPostTest) ) {
                        healthSparkPostResponse = true;
                    }
                } catch (Exception e) {
                    log.error("Error, Exception caught while processing Health Report POST callback.", e);
                }
                return "";
            }
        });

        Spark.awaitInitialization();
    } // method used to setup Spark for a Health Report callback
}
