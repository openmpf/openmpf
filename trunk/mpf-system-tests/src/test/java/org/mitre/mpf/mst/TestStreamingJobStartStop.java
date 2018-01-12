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


package org.mitre.mpf.mst;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mitre.mpf.wfm.businessrules.StreamingJobRequestBo;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.enums.JobStatus;
import org.mitre.mpf.wfm.service.StreamingJobMessageSender;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.mockito.AdditionalMatchers.gt;
import static org.mockito.AdditionalMatchers.or;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(initializers = TestSystemWithDefaultConfig.AppCtxInit.class)
@ActiveProfiles("jenkins")
@DirtiesContext // Make sure TestStreamingJobStartStop does not use same application context as other tests.
public class TestStreamingJobStartStop {

	private static final StreamingJobRequestBo _mockStreamingJobRequestBo = mock(StreamingJobRequestBo.class);

	@Configuration
	public static class TestConfig {

		@Bean
		@Primary
		public StreamingJobRequestBo streamingJobRequestBo() {
			return _mockStreamingJobRequestBo;
		}
	}

	@Autowired
	private StreamingJobMessageSender _jobSender;



	@Test
	public void testJobStartStop() throws InterruptedException {
		long jobId = 43231;

		TransientStage stage1 = new TransientStage("stage1", "description", ActionType.DETECTION);
		stage1.getActions().add(new TransientAction("Action1", "description", "CPLUSPLUSHELLOWORLD"));

		TransientPipeline pipeline = new TransientPipeline("HELLOWORLD TEST PIPELINE", "desc");
		pipeline.getStages().add(stage1);


		URL videoUrl = getClass().getResource("/samples/face/new_face_video.avi");
		TransientStream stream = new TransientStream(124, videoUrl.toString());
		stream.setSegmentSize(10);
		TransientStreamingJob streamingJob = new TransientStreamingJob(
				jobId, "ext id", pipeline, 1, 1, false, "mydir",
				false);
		streamingJob.setStream(stream);

		_jobSender.launchJob(streamingJob);

		Thread.sleep(3000);

		_jobSender.stopJob(jobId);


		Thread.sleep(3000);

		verify(_mockStreamingJobRequestBo, timeout(30_000))
				.jobCompleted(eq(jobId), or(eq(JobStatus.TERMINATED), eq(JobStatus.STALLED)));

		verify(_mockStreamingJobRequestBo, atLeastOnce())
				.handleNewActivityAlert(eq(jobId), gt(0L), gt(0L));

		ArgumentCaptor<SegmentSummaryReport> reportCaptor = ArgumentCaptor.forClass(SegmentSummaryReport.class);
		verify(_mockStreamingJobRequestBo, atLeastOnce())
				.handleNewSummaryReport(reportCaptor.capture());

		SegmentSummaryReport summaryReport = reportCaptor.getValue();
		assertEquals(jobId, summaryReport.getJobId());
	}
}
