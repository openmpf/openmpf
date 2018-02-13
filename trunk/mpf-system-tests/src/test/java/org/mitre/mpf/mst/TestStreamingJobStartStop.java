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

import org.apache.commons.lang3.tuple.Pair;
import org.jgroups.Address;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mitre.mpf.interop.JsonSegmentSummaryReport;
import org.mitre.mpf.nms.AddressParser;
import org.mitre.mpf.nms.MasterNode;
import org.mitre.mpf.nms.NodeTypes;
import org.mitre.mpf.wfm.businessrules.StreamingJobRequestBo;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobStatus;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.enums.StreamingJobStatusType;
import org.mitre.mpf.wfm.service.StreamingJobMessageSender;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalMatchers.*;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(initializers = TestSystemWithDefaultConfig.AppCtxInit.class)
@ActiveProfiles("jenkins")
@DirtiesContext // Make sure TestStreamingJobStartStop does not use same application context as other tests.
public class TestStreamingJobStartStop {

	private static final Logger LOG = LoggerFactory.getLogger(TestStreamingJobStartStop.class);

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

	@Autowired
	private MasterNode _masterNode;


	@Test(timeout = 5 * 60_000)
	public void testJobStartStop() throws InterruptedException {
		long jobId = 43231;
		long test_start_time = System.currentTimeMillis();

		waitForCorrectNodes();

		TransientStage stage1 = new TransientStage("stage1", "description", ActionType.DETECTION);
		stage1.getActions().add(new TransientAction("Action1", "description", "HelloWorld"));

		TransientPipeline pipeline = new TransientPipeline("HELLOWORLD SAMPLE PIPELINE", "desc");
		pipeline.getStages().add(stage1);


		URL videoUrl = getClass().getResource("/samples/face/new_face_video.avi");
		TransientStream stream = new TransientStream(124, videoUrl.toString());
		stream.setSegmentSize(10);
		TransientStreamingJob streamingJob = new TransientStreamingJob(
				jobId, "ext id", pipeline, 1, 1, false, "mydir",
				false);
		streamingJob.setStream(stream);

		_jobSender.launchJob(streamingJob);

		verify(_mockStreamingJobRequestBo, timeout(30_000).atLeastOnce())
				.handleNewActivityAlert(eq(jobId), geq(0L), gt(test_start_time));

		_jobSender.stopJob(jobId);


		verify(_mockStreamingJobRequestBo, timeout(30_000))
				.handleJobStatusChange(eq(jobId), or(eq(new StreamingJobStatus(StreamingJobStatusType.TERMINATED)), eq(new StreamingJobStatus(StreamingJobStatusType.CANCELLED))), gt(test_start_time));

		ArgumentCaptor<JsonSegmentSummaryReport> reportCaptor = ArgumentCaptor.forClass(JsonSegmentSummaryReport.class);

		verify(_mockStreamingJobRequestBo, timeout(30_000).atLeastOnce())
				.handleNewSummaryReport(reportCaptor.capture());

		JsonSegmentSummaryReport summaryReport = reportCaptor.getValue();
		assertEquals(jobId, summaryReport.getJobId());

		boolean hasNonEmptyDetection = summaryReport.getTypes()
				.values()
				.stream()
				.flatMap(Collection::stream)
				.flatMap(t -> t.getDetections().stream())
				.anyMatch(d -> d.getHeight() > 0 && d.getWidth() > 0);
		assertTrue(hasNonEmptyDetection);
	}



	private void waitForCorrectNodes() throws InterruptedException {
		while (!hasCorrectNodes()) {
			Thread.sleep(1000);
		}
	}


	// Sometimes when the test starts there are 2 master nodes.
	// This makes sure there is only one master node and only one child node.
	private boolean hasCorrectNodes() {
		List<Address> currentNodes = _masterNode.getCurrentNodeManagerHosts();
		Map<NodeTypes, Long> nodeTypeCounts = currentNodes
				.stream()
				.map(AddressParser::parse)
				.collect(groupingBy(Pair::getRight, counting()));

		long masterNodeCount = nodeTypeCounts.getOrDefault(NodeTypes.MasterNode, 0L);
		long childNodeCount = nodeTypeCounts.getOrDefault(NodeTypes.NodeManager, 0L);
		if (masterNodeCount == 1 && childNodeCount == 1) {
			return true;
		}

		String currentNodeList = currentNodes.stream()
				.map(Object::toString)
				.collect(joining("\n"));

		LOG.warn("Current Nodes:\n{}", currentNodeList);

		if (masterNodeCount != 1) {
			LOG.warn("Incorrect number of master nodes. Expected 1 but there were {}.", masterNodeCount);
		}
		if (childNodeCount != 1) {
			LOG.warn("Incorrect number of child nodes. Expected 1 but there were {}.", childNodeCount);
		}
		return false;
	}
}
