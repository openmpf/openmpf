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


package org.mitre.mpf.mst;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.notNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mitre.mpf.rest.api.node.EnvironmentVariableModel;
import org.mitre.mpf.wfm.businessrules.StreamingJobRequestBo;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobStatus;
import org.mitre.mpf.wfm.data.entities.transients.TransientAction;
import org.mitre.mpf.wfm.data.entities.transients.TransientPipeline;
import org.mitre.mpf.wfm.data.entities.transients.TransientStage;
import org.mitre.mpf.wfm.data.entities.transients.TransientStream;
import org.mitre.mpf.wfm.data.entities.transients.TransientStreamingJob;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.pipeline.xml.AlgorithmDefinition;
import org.mitre.mpf.wfm.pipeline.xml.PropertyDefinition;
import org.mitre.mpf.wfm.pipeline.xml.ValueType;
import org.mitre.mpf.wfm.service.PipelineService;
import org.mitre.mpf.wfm.service.PipelineServiceImpl;
import org.mitre.mpf.wfm.service.StreamingJobMessageSender;
import org.mitre.mpf.wfm.service.StreamingServiceManager;
import org.mitre.mpf.wfm.service.StreamingServiceModel;
import org.mitre.mpf.wfm.service.component.ComponentLanguage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(initializers = TestSystemWithDefaultConfig.AppCtxInit.class)
@ActiveProfiles("jenkins")
@DirtiesContext // Make sure TestStreamingJobStartStop does not use same application context as other tests.
public class TestStreamingJobStartStop {

	private static final StreamingJobRequestBo _mockStreamingJobRequestBo = mock(StreamingJobRequestBo.class);

	private static final StreamingServiceManager _mockServiceManager = mock(StreamingServiceManager.class);

	private static PipelineService _pipelineServiceSpy;

	// TODO: Remove mocks when real streaming component executor is available.
	@Configuration
	public static class TestConfig {

		@Bean
		@Primary
		public StreamingJobRequestBo streamingJobRequestBo() {
			return _mockStreamingJobRequestBo;
		}

		@Bean
		@Primary
		public StreamingServiceManager streamingServiceManager() {
			return _mockServiceManager;
		}

		@Bean
		@Primary
		public PipelineService pipelineService(PipelineServiceImpl realPipelineService) {
			if (_pipelineServiceSpy == null) {
				_pipelineServiceSpy = spy(realPipelineService);
			}
			return _pipelineServiceSpy;
		}
	}

	@Autowired
	private StreamingJobMessageSender _jobSender;


	@Before
	public void init() {
		StreamingServiceModel testService = new StreamingServiceModel(
				"STREAMING_ALGO", "STREAMING_ALGO", ComponentLanguage.CPP, "path/to/libmyLib.so",
				Collections.singletonList(new EnvironmentVariableModel("env_var1", "env_val1", null))
		);

		when(_mockServiceManager.getServices())
				.thenReturn(Collections.singletonList(testService));

		AlgorithmDefinition algorithmDef = new AlgorithmDefinition(
				ActionType.DETECTION, testService.getAlgorithmName(), "description", true, true);
		algorithmDef.getProvidesCollection().getAlgorithmProperties().add(
				new PropertyDefinition("Prop1", ValueType.STRING, "description",
				                       "propval1"));

		doReturn(algorithmDef)
				.when(_pipelineServiceSpy)
				.getAlgorithm(algorithmDef.getName());
	}


	@Test
	public void testJobStartStop() throws InterruptedException {

		TransientStage stage1 = new TransientStage("stage1", "description", ActionType.DETECTION);
		stage1.getActions().add(new TransientAction("Action1", "descrption", "STREAMING_ALGO"));

		TransientPipeline pipeline = new TransientPipeline("PipelineName", "desc");
		pipeline.getStages().add(stage1);


		TransientStream stream = new TransientStream(124, "stream://thestream");
		TransientStreamingJob streamingJob = new TransientStreamingJob(
				123, "ext id", pipeline, 1, 1, false, "mydir",
				false);
		streamingJob.setStream(stream);

		_jobSender.launchJob(streamingJob);

		Thread.sleep(2000);

		_jobSender.stopJob(123);

		// The python test process is used for this test. The test process sleeps for 3 seconds before exiting.
		verify(_mockStreamingJobRequestBo, never())
				.jobCompleted(anyLong(), any(StreamingJobStatus.class));

		Thread.sleep(3200);

		verify(_mockStreamingJobRequestBo, timeout(30_000))
				.jobCompleted(eq(123L), notNull(StreamingJobStatus.class));

	}
}
