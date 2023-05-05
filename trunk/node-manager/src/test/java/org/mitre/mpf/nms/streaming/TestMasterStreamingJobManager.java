/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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

package org.mitre.mpf.nms.streaming;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.nms.ChannelNode;
import org.mitre.mpf.nms.streaming.messages.LaunchStreamingJobMessage;
import org.mitre.mpf.nms.streaming.messages.StopStreamingJobMessage;
import org.mitre.mpf.nms.streaming.messages.StreamingJobExitedMessage;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mitre.mpf.nms.streaming.StreamingJobTestUtil.createLaunchMessage;
import static org.mockito.Mockito.*;

public class TestMasterStreamingJobManager {

	private AutoCloseable _closeable;

	@InjectMocks
	private MasterStreamingJobManager _streamingJobManager;

	@Mock
	private ChannelNode _mockChannel;

	private static final List<String> TEST_NODES = Arrays.asList("Node1", "Node2", "Node3");

	@Before
	public void init() {
		_closeable = MockitoAnnotations.openMocks(this);
	}

	@After
	public void close() throws Exception {
		_closeable.close();
	}

	@Test
	public void jobsStartOnNodeWithMinJobs() {
		String job1Node = startJob(createLaunchMessage(1));
		String job2Node = startJob(createLaunchMessage(2));
		assertNotEquals(job1Node, job2Node);

		String job3Node = startJob(createLaunchMessage(3));
		assertNotEquals(job1Node, job3Node);
		assertNotEquals(job2Node, job3Node);

		assertTrue(TEST_NODES.contains(job1Node));
		assertTrue(TEST_NODES.contains(job2Node));
		assertTrue(TEST_NODES.contains(job3Node));
	}


	@Test
	public void doesNotStartSameJobMultipleTimes() {
		LaunchStreamingJobMessage launchMessage = createLaunchMessage(1);
		LaunchStreamingJobMessage dupLaunchMessage = createLaunchMessage(1);

		startJob(launchMessage);

		try {
			startJob(dupLaunchMessage);
			fail("Expected IllegalStateException");
		}
		catch (IllegalStateException ignored) { }


		verify(_mockChannel, times(1))
				.sendToChild(any(), any());
	}


	@Test
	public void sendsStopToCorrectChild() {
		String job1Node = startJob(createLaunchMessage(1));
		String job2Node = startJob(createLaunchMessage(2));
		String job3Node = startJob(createLaunchMessage(3));

		verifyStopSentToNode(2, job2Node);
		verifyStopSentToNode(3, job3Node);
		verifyStopSentToNode(1, job1Node);
	}


	@Test
	public void noMessagesSentWhenStoppingInvalidJobId() {
		startJob(createLaunchMessage(1));
		startJob(createLaunchMessage(2));
		startJob(createLaunchMessage(3));

		_streamingJobManager.stopJob(new StopStreamingJobMessage(4));

		verify(_mockChannel, never())
				.sendToChild(any(), isA(StopStreamingJobMessage.class));
	}


	@Test
	public void doesNotRemoveJobUntilResponseFromChild() {
		String n1 = "Node1";
		String n2 = "Node2";
		startJob(createLaunchMessage(1), Collections.singletonList(n1));
		startJob(createLaunchMessage(2), Collections.singletonList(n1));
		startJob(createLaunchMessage(3), Collections.singletonList(n2));
		// n1 has 2 jobs and n2 has 1 job.


		// Need to send two stop job messages so that n1 and n2 won't have the same number of jobs.
		// If they had the same number of jobs, the new job could be sent to either n1 or n2.
		_streamingJobManager.stopJob(new StopStreamingJobMessage(1));
		_streamingJobManager.stopJob(new StopStreamingJobMessage(2));

		// If stopJob removed job immediately, then n1 would have been selected since it would have no jobs.
		assertEquals(n2, startJob(createLaunchMessage(4), Arrays.asList(n1, n2)));

		_streamingJobManager.streamingJobExited(new StreamingJobExitedMessage(
				1, StreamingProcessExitReason.CANCELLED));
		_streamingJobManager.streamingJobExited(new StreamingJobExitedMessage(
				2, StreamingProcessExitReason.CANCELLED));

		// At this point n1 should have 0 jobs and n2 should have 2 job so n1 should be selected.
		assertEquals(n1, startJob(createLaunchMessage(5), Arrays.asList(n1, n2)));

	}



	private String startJob(LaunchStreamingJobMessage launchMessage, Collection<String> availableNodes) {
		_streamingJobManager.startJob(launchMessage, availableNodes);
		ArgumentCaptor<String> hostnameCaptor = ArgumentCaptor.forClass(String.class);
		verify(_mockChannel)
				.sendToChild(hostnameCaptor.capture(), eq(launchMessage));
		assertTrue(availableNodes.contains(hostnameCaptor.getValue()));

		return hostnameCaptor.getValue();
	}


	private String startJob(LaunchStreamingJobMessage launchMessage) {
		return startJob(launchMessage, TEST_NODES);
	}


	private void verifyStopSentToNode(long jobId, String hostname) {
		StopStreamingJobMessage stopMessage = new StopStreamingJobMessage(jobId);

		_streamingJobManager.stopJob(stopMessage);
		verify(_mockChannel)
				.sendToChild(hostname, stopMessage);

	}
}


