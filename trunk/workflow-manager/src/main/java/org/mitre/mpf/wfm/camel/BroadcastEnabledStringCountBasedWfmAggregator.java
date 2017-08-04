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

package org.mitre.mpf.wfm.camel;

import org.apache.camel.Exchange;
import org.mitre.mpf.mvc.controller.AtmosphereController;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.RedisImpl;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateJobRequestDao;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateJobRequestDaoImpl;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.mvc.model.JobStatusMessage;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.event.JobProgress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component(BroadcastEnabledStringCountBasedWfmAggregator.REF)
public class BroadcastEnabledStringCountBasedWfmAggregator extends StringCountBasedWfmAggregator {
	private static final Logger log = LoggerFactory.getLogger(BroadcastEnabledStringCountBasedWfmAggregator.class);
	public static final String REF = "broadcastEnabledStringCountBasedWfmAggregator";

	@Autowired
	@Qualifier(RedisImpl.REF)
	private Redis redis;

	@Autowired
	@Qualifier(HibernateJobRequestDaoImpl.REF)
	private HibernateJobRequestDao hibernateJobRequestDao;

	@Autowired
	private JobProgress jobProgressStore;

	public void onResponse(Exchange newExchange) {
		super.onResponse(newExchange);
		if(!Boolean.TRUE.equals(newExchange.getIn().getHeader(MpfHeaders.SUPPRESS_BROADCAST))) {
			try {
				int aggregateCount = newExchange.getOut().getHeader(MpfHeaders.AGGREGATED_COUNT, Integer.class);
				int splitSize = newExchange.getOut().getHeader(MpfHeaders.SPLIT_SIZE, Integer.class);
				long jobId = newExchange.getOut().getHeader(MpfHeaders.JOB_ID, Long.class);
				if (redis.getTaskCountForJob(jobId) != 0) {
					int currentStage = 1 + redis.getCurrentTaskIndexForJob(jobId);
					int totalStages = redis.getTaskCountForJob(jobId);
					float progressPerStage = 1 / (1f * totalStages) * 100f;

					float taskProgress = (((float) aggregateCount) / ((float) splitSize));
					float jobProgress = (currentStage - 1) * (progressPerStage) + (progressPerStage) * taskProgress;

					JobRequest jobRequest = hibernateJobRequestDao.findById(jobId); // TODO: Does this have a significant impact on the speed of this method?

					AtmosphereController.broadcast(new JobStatusMessage(jobId, jobProgress, jobRequest.getStatus(), null));

					//store the current job progress to prevent progress displaying as zero on manual refreshes
					jobProgressStore.setJobProgress(jobId, jobProgress);
				}
			} catch (Exception e) {
				log.error("Error getting necessary information to create a job progress update.");
			}
		}
	}
}
