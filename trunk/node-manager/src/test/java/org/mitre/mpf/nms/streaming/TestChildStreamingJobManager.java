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

package org.mitre.mpf.nms.streaming;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.nms.ChannelNode;
import org.mitre.mpf.nms.streaming.messages.LaunchStreamingJobMessage;
import org.mitre.mpf.nms.streaming.messages.StopStreamingJobMessage;
import org.mitre.mpf.nms.streaming.messages.StreamingJobExitedMessage;
import org.mockito.InjectMocks;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CompletableFuture;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class TestChildStreamingJobManager {

	@InjectMocks
	private ChildStreamingJobManager _childStreamingJobManager;

	@Mock
	private ChannelNode _mockChannel;

	@Mock
	private StreamingJobFactory _mockJobFactory;


	@Before
	public void init() {
		MockitoAnnotations.initMocks(this);
	}


	@Test
	public void canHandleJobStartAndStop() {
		StreamingJob job = mock(StreamingJob.class);
		JobController jobCtrl = setupMockJob(job);

		StreamingJob job2 = mock(StreamingJob.class);
		JobController jobCtrl2 = setupMockJob(job2);

		LaunchStreamingJobMessage launchMessage = StreamingJobTestUtil.createLaunchMessage(1);
		LaunchStreamingJobMessage launchMessage2 = StreamingJobTestUtil.createLaunchMessage(2);

		when(_mockJobFactory.createJob(launchMessage))
				.thenReturn(job);

		when(_mockJobFactory.createJob(launchMessage2))
				.thenReturn(job2);


		// Send launch messages
		_childStreamingJobManager.handle(launchMessage);
		verify(job)
				.startJob();

		_childStreamingJobManager.handle(launchMessage2);
		verify(job2)
				.startJob();



		// Send stop messages
		_childStreamingJobManager.handle(new StopStreamingJobMessage(1));
		verify(job)
				.stopJob();

		_childStreamingJobManager.handle(new StopStreamingJobMessage(2));
		verify(job)
				.stopJob();

		// Verify that message is not sent to master until the process finishes exiting
		verify(_mockChannel, never())
				.sendToMaster(any());


		// Make job2 complete before job1 even though stop message for job1 was received first,
		// since there could be situations where one job takes longer than another to exit.
		jobCtrl2.allowComplete();

		verify(_mockChannel)
				.sendToMaster(exitMessageWithId(2));
		// job1 is still running
		verify(_mockChannel, never())
				.sendToMaster(exitMessageWithId(1));


		jobCtrl.allowComplete();

		verify(_mockChannel)
				.sendToMaster(exitMessageWithId(1));

	}

	private static StreamingJobExitedMessage exitMessageWithId(long jobId) {
		return Matchers.argThat(new BaseMatcher<StreamingJobExitedMessage>() {
			@Override
			public void describeTo(Description description) {
				description.appendText("StreamingJobExitedMessage.jobId = ").appendValue(jobId);
			}

			@Override
			public boolean matches(Object item) {
				long otherJobId = ((StreamingJobExitedMessage) item).jobId;
				return jobId == otherJobId;
			}
		});
	}


	private static JobController setupMockJob(StreamingJob job) {
		CompletableFuture<Void> future = new CompletableFuture<>();
		when(job.startJob())
				.thenReturn(future);

		when(job.stopJob())
				.thenReturn(future);

		return () -> future.complete(null);
	}


	@FunctionalInterface
	private static interface JobController {
		void allowComplete();
	}
}

