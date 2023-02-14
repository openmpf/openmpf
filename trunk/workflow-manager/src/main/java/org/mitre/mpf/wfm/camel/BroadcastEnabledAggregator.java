/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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

import javax.inject.Inject;

import org.apache.camel.Exchange;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.event.JobProgress;
import org.mitre.mpf.wfm.service.JobStatusBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BroadcastEnabledAggregator implements WfmAggregator {

    private static final Logger LOG = LoggerFactory.getLogger(BroadcastEnabledAggregator.class);

    private final InProgressBatchJobsService _inProgressBatchJobs;

    private final JobStatusBroadcaster _jobStatusBroadcaster;

    private final JobProgress _jobProgressStore;

    @Inject
    BroadcastEnabledAggregator(
            InProgressBatchJobsService inProgressBatchJobs,
            JobStatusBroadcaster jobStatusBroadcaster,
            JobProgress jobProgressStore) {

        _inProgressBatchJobs = inProgressBatchJobs;
        _jobStatusBroadcaster = jobStatusBroadcaster;
        _jobProgressStore = jobProgressStore;
    }

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        var newMsg = newExchange.getIn();
        Object suppressBroadcast = newMsg.getHeader(MpfHeaders.SUPPRESS_BROADCAST);
        if (suppressBroadcast instanceof Boolean && (boolean) suppressBroadcast) {
            return newExchange;
        }

        try {
            int numPartsReceived = 1;
            if (oldExchange != null) {
                numPartsReceived += oldExchange.getProperty("CamelAggregatedSize", int.class);
            }
            int splitSize = newMsg.getHeader(MpfHeaders.SPLIT_SIZE, Integer.class);
            long jobId = newMsg.getHeader(MpfHeaders.JOB_ID, Long.class);

            var job = _inProgressBatchJobs.getJob(jobId);
            int tasksCompleted = job.getCurrentTaskIndex();
            int totalTasks = job.getPipelineElements().getTaskCount();
            float progressInCurrentTask = (float) numPartsReceived / splitSize;
            // Make sure job progress never gets to 100% before the job is actually complete.
            float jobProgress = Math.min(
                    99,
                    (tasksCompleted + progressInCurrentTask) / totalTasks * 100);

            _jobStatusBroadcaster.broadcast(jobId, jobProgress, job.getStatus());
            _jobProgressStore.setJobProgress(jobId, jobProgress);
        }
        catch (Exception e) {
            LOG.error("Error getting necessary information to create a job progress update.");
        }
        return newExchange;
    }
}
