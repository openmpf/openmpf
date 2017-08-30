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

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import org.mitre.mpf.nms.ChannelNode;
import org.mitre.mpf.nms.streaming.messages.StopStreamingJobMessage;
import org.mitre.mpf.nms.streaming.messages.StreamingJobExitedMessage;
import org.mitre.mpf.nms.streaming.messages.StreamingJobLaunchMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;


@Component
public class MasterStreamingJobManager {

	private static final Logger LOG = LoggerFactory.getLogger(MasterStreamingJobManager.class);

	private final ChannelNode _channelNode;

	private final JobLocationCounter _jobLocationCounter = new JobLocationCounter();


	@Autowired
	public MasterStreamingJobManager(ChannelNode channelNode) {
		_channelNode = channelNode;
	}



	public void startJob(StreamingJobLaunchMessage launchMessage, Collection<String> runningNodes) {
		synchronized (_jobLocationCounter) {
			String nodeWithMinJobs = getNodeWithMinJobs(runningNodes);
			_jobLocationCounter.addJob(launchMessage.jobId, nodeWithMinJobs);
			_channelNode.sendToChild(nodeWithMinJobs, launchMessage);
		}
	}


	public void stopJob(StopStreamingJobMessage stopMessage) {
		synchronized (_jobLocationCounter) {
			String nodeHostname = _jobLocationCounter.getJobLocation(stopMessage.jobId);
			if (nodeHostname == null) {
				LOG.warn(
						"Received stop request for streaming job {}, but that job has already exited or does not exist.",
						stopMessage.jobId);
				return;
			}
			_channelNode.sendToChild(nodeHostname, stopMessage);
		}
	}


	public void streamingJobExited(StreamingJobExitedMessage message) {
		synchronized (_jobLocationCounter) {
			LOG.info("Job {} has exited.", message.jobId);
			_jobLocationCounter.removeJob(message.jobId);
		}
	}


	private String getNodeWithMinJobs(Collection<String> runningNodes) {
		return runningNodes.stream()
				.min(Comparator.comparingInt(_jobLocationCounter::getJobCount))
				.orElseThrow(() -> new IllegalStateException(
						"Unable to start streaming job because there are no running nodes."));
	}



	private static class JobLocationCounter {
		private final Map<Long, String> _jobToNodeMap = new HashMap<>();
		private final Multiset<String> _nodeJobCounts = HashMultiset.create();

		public String getJobLocation(long jobId) {
			return _jobToNodeMap.get(jobId);
		}

		public int getJobCount(String node) {
			return _nodeJobCounts.count(node);
		}

		public void addJob(long jobId, String node) {
			String existingJobLocation = getJobLocation(jobId);
			if (existingJobLocation != null) {
				throw new IllegalStateException(String.format(
						"Unable to start job with id %s because it is already running on %s",
						jobId, existingJobLocation));
			}
			_jobToNodeMap.put(jobId, node);
			_nodeJobCounts.add(node);
		}

		public void removeJob(long jobId) {
			String removedNode = _jobToNodeMap.remove(jobId);
			if (removedNode != null) {
				_nodeJobCounts.remove(removedNode);
			}
		}
	}
}
