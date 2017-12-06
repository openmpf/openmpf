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

package org.mitre.mpf.wfm.service;


import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.nms.MasterNode;
import org.mitre.mpf.nms.streaming.messages.LaunchStreamingJobMessage;
import org.mitre.mpf.nms.streaming.messages.StopStreamingJobMessage;
import org.mitre.mpf.rest.api.node.EnvironmentVariableModel;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.enums.StreamingEndpoints;
import org.mitre.mpf.wfm.pipeline.xml.AlgorithmDefinition;
import org.mitre.mpf.wfm.pipeline.xml.PropertyDefinition;
import org.mitre.mpf.wfm.pipeline.xml.ValueType;
import org.mitre.mpf.wfm.service.component.ComponentLanguage;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class TestStreamingJobMessageSender {

	@InjectMocks
	private StreamingJobMessageSenderImpl _messageSender;

	@Mock
	private PropertiesUtil _mockProperties;

	@Mock
	private MasterNode _mockMasterNode;

	@Mock
	private PipelineService _mockPipelineService;

	@Mock
	private StreamingServiceManager _mockServiceManager;


	@Before
	public void init() {
		MockitoAnnotations.initMocks(this);
	}


	@Test
	public void testLaunch() {
		long stallAlertThreshold = 234;
		when(_mockProperties.getStreamingJobStallAlertThreshold())
				.thenReturn(stallAlertThreshold);

		String activeMqUri = "failover://(tcp://localhost.localdomain:61616)?jms.prefetchPolicy.all=1&startupMaxReconnectAttempts=1";
		when(_mockProperties.getAmqUri())
				.thenReturn(activeMqUri);

		AlgorithmDefinition algoDef = new AlgorithmDefinition(ActionType.DETECTION, "TEST ALGO", "Algo Description", true,
		                                                      true);

		algoDef.getProvidesCollection().getAlgorithmProperties().add(
				new PropertyDefinition("OVERRIDDEN ACTION PROPERTY", ValueType.STRING, "desc", "Bad Value"));
		algoDef.getProvidesCollection().getAlgorithmProperties().add(
				new PropertyDefinition("ALGO PROPERTY2", ValueType.STRING, "desc", "Algo Value2"));
		algoDef.getProvidesCollection().getAlgorithmProperties().add(
				new PropertyDefinition("OVERRIDDEN JOB PROPERTY", ValueType.STRING, "desc", "Bad Value"));
		algoDef.getProvidesCollection().getAlgorithmProperties().add(
				new PropertyDefinition("OVERRIDDEN STREAM PROPERTY", ValueType.STRING, "desc", "Bad Value"));

		when(_mockPipelineService.getAlgorithm("TEST ALGO"))
				.thenReturn(algoDef);


		TransientAction action = new TransientAction("ActionName", "Action description", "TEST ALGO");
		action.getProperties().put("OVERRIDDEN ACTION PROPERTY", "ACTION VAL");
		action.getProperties().put("OVERRIDDEN JOB PROPERTY", "Bad Value");

		TransientStage stage = new TransientStage("StageName", "Stage description",
		                                          ActionType.DETECTION);
		stage.getActions().add(action);

		TransientPipeline pipeline = new TransientPipeline("MyStreamingPipeline",
		                                                   "Pipeline description");
		pipeline.getStages().add(stage);

		TransientStream stream = new TransientStream(5234, "stream://myStream");
		stream.setSegmentSize(543);
		stream.addMetadata("mediaProp1", "mediaVal1");
		stream.addMediaProperty("OVERRIDDEN STREAM PROPERTY", "Stream Specific Value");

		long jobId = 1234;
		TransientStreamingJob job = new TransientStreamingJob(
				jobId, "external id", pipeline, 5, 100, true,
				"output-dir", false);
		job.setStream(stream);
		job.getOverriddenJobProperties().put("OVERRIDDEN JOB PROPERTY", "Job Overridden Value");

		ArgumentCaptor<LaunchStreamingJobMessage> msgCaptor = ArgumentCaptor.forClass(LaunchStreamingJobMessage.class);

		List<EnvironmentVariableModel> envVars = Arrays.asList(
				new EnvironmentVariableModel("LD_LIB_PATH", "/opt/mpf", null),
				new EnvironmentVariableModel("var2", "val2", null));

		StreamingServiceModel streamingServiceModel = new StreamingServiceModel(
				"MyService", action.getAlgorithm(), ComponentLanguage.CPP,
				"my-component/lib/libComponent.so", envVars);

		when(_mockServiceManager.getServices())
				.thenReturn(Arrays.asList(
						new StreamingServiceModel(
								"Wrong Service", "Wrong Algo", ComponentLanguage.JAVA, "bad-path/lib",
								Collections.emptyList()),
						streamingServiceModel));



		_messageSender.launchJob(job);



		verify(_mockMasterNode)
				.startStreamingJob(msgCaptor.capture());

		LaunchStreamingJobMessage launchMessage = msgCaptor.getValue();

		assertEquals(jobId, launchMessage.jobId);
		assertEquals(stream.getUri(), launchMessage.streamUri);
		assertEquals(stream.getSegmentSize(), launchMessage.segmentSize);
		assertEquals(job.getStallTimeout(), launchMessage.stallTimeout);
		assertEquals(stallAlertThreshold, launchMessage.stallAlertThreshold);
		assertEquals(streamingServiceModel.getServiceName(), launchMessage.componentName);
		assertEquals(streamingServiceModel.getLibraryPath(), launchMessage.componentLibraryPath);

		assertEquals("val2", launchMessage.componentEnvironmentVariables.get("var2"));
		assertEquals("/opt/mpf", launchMessage.componentEnvironmentVariables.get("LD_LIB_PATH"));

		assertEquals("Algo Value2", launchMessage.jobProperties.get("ALGO PROPERTY2"));
		assertEquals("ACTION VAL", launchMessage.jobProperties.get("OVERRIDDEN ACTION PROPERTY"));
		assertEquals("Job Overridden Value", launchMessage.jobProperties.get("OVERRIDDEN JOB PROPERTY"));
		assertEquals("Stream Specific Value", launchMessage.jobProperties.get("OVERRIDDEN STREAM PROPERTY"));


		assertEquals("mediaVal1", launchMessage.mediaProperties.get("mediaProp1"));

		assertEquals(activeMqUri, launchMessage.messageBrokerUri);
		assertEquals(StreamingEndpoints.WFM_STREAMING_JOB_STATUS.queueName(), launchMessage.jobStatusQueue);
		assertEquals(StreamingEndpoints.WFM_STREAMING_JOB_ACTIVITY.queueName(), launchMessage.activityAlertQueue);
		assertEquals(StreamingEndpoints.WFM_STREAMING_JOB_SUMMARY_REPORTS.queueName(),
		             launchMessage.summaryReportQueue);

	}



	@Test
	public void testStop() {
		long jobId = 1245;
		_messageSender.stopJob(jobId);

		ArgumentCaptor<StopStreamingJobMessage> msgCaptor = ArgumentCaptor.forClass(StopStreamingJobMessage.class);
		verify(_mockMasterNode)
				.stopStreamingJob(msgCaptor.capture());

		StopStreamingJobMessage stopMessage = msgCaptor.getValue();
		assertEquals(jobId, stopMessage.jobId);
	}
}