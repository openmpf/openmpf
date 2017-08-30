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

package org.mitre.mpf.nms.streaming;

import org.mitre.mpf.nms.ChannelNode;
import org.mitre.mpf.nms.streaming.messages.StopStreamingJobMessage;
import org.mitre.mpf.nms.streaming.messages.StreamingJobExitedMessage;
import org.mitre.mpf.nms.streaming.messages.StreamingJobLaunchMessage;
import org.mitre.mpf.nms.streaming.messages.StreamingJobMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class ChildStreamingJobManager {

	private static final Logger LOG = LoggerFactory.getLogger(ChildStreamingJobManager.class);

	private final ChannelNode _channelNode;

	private final StreamingJobFactory _streamingJobFactory;

	private final Map<Long, StreamingJob> _streamingJobs = new HashMap<>();


	@Autowired
	public ChildStreamingJobManager(ChannelNode channelNode, StreamingJobFactory streamingJobFactory) {
		_channelNode = channelNode;
		_streamingJobFactory = streamingJobFactory;
	}


	public void handle(StreamingJobMessage message) {
		LOG.info("Received Message: {}", message);
		if (message instanceof StreamingJobLaunchMessage) {
			handleJobLaunch((StreamingJobLaunchMessage) message);
		}
		else if (message instanceof StopStreamingJobMessage) {
			handleJobStop((StopStreamingJobMessage) message);
		}
	}


	private void handleJobLaunch(StreamingJobLaunchMessage message) {
		synchronized (_streamingJobs) {
			if (_streamingJobs.containsKey(message.jobId)) {
				LOG.error("Received StreamingJobLaunchMessage for job id {}, but a job with that id is already running",
				          message.jobId);
				return;
			}
			StreamingJob job = _streamingJobFactory.createJob(message);
			CompletableFuture<Void> jobCompleteFuture = job.startJob();

			_streamingJobs.put(message.jobId, job);

			jobCompleteFuture
					.whenComplete((none, error) -> onJobExit(message.jobId, error));
		}
	}


	private void handleJobStop(StopStreamingJobMessage stopMessage) {
		synchronized (_streamingJobs) {
			StreamingJob job = _streamingJobs.get(stopMessage.jobId);
			if (job == null) {
				LOG.info("Received StopStreamingJobMessage for job id {}, but that job has already completed or does not exist.",
				         stopMessage.jobId);
				return;
			}
			job.stopJob();
		}
	}



	private void onJobExit(long jobId, Throwable error) {
		synchronized (_streamingJobs) {
			_streamingJobs.remove(jobId);
			if (error != null) {
				LOG.warn("An error occurred during the execution of job: " + jobId, error);
			}
			_channelNode.sendToMaster(new StreamingJobExitedMessage(jobId));
		}
	}
}
