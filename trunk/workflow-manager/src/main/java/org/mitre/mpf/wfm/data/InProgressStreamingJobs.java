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


package org.mitre.mpf.wfm.data;

import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobStatus;
import org.mitre.mpf.wfm.data.entities.transients.TransientPipeline;
import org.mitre.mpf.wfm.data.entities.transients.TransientStream;
import org.mitre.mpf.wfm.data.entities.transients.TransientStreamingJob;
import org.mitre.mpf.wfm.data.entities.transients.TransientStreamingJobImpl;
import org.mitre.mpf.wfm.enums.StreamingJobStatusType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

@Component
public class InProgressStreamingJobs {

    private final Map<Long, TransientStreamingJobImpl> _jobs = new HashMap<>();


    public TransientStreamingJob addJob(
            long jobId,
            String externalId,
            TransientPipeline pipeline,
            TransientStream stream,
            int priority,
            long stallTimeout,
            boolean outputEnabled,
            String outputObjectDirectory,
            String healthReportCallbackURI,
            String summaryReportCallbackURI,
            Map<String, String> jobProperties,
            Map<String, Map<String, String>> algorithmProperties) {

        TransientStreamingJobImpl job = new TransientStreamingJobImpl(
                jobId,
                externalId,
                pipeline,
                stream,
                priority,
                stallTimeout,
                outputEnabled,
                outputObjectDirectory,
                healthReportCallbackURI,
                summaryReportCallbackURI,
                jobProperties,
                algorithmProperties);
        _jobs.put(jobId, job);
        return job;
    }


    public TransientStreamingJob getJob(long jobId) {
        return getJobImpl(jobId);
    }


    private TransientStreamingJobImpl getJobImpl(long jobId) {
        TransientStreamingJobImpl job = _jobs.get(jobId);
        if (job != null) {
            return job;
        }
        throw new WfmProcessingException("Unable to locate streaming job with id: " + jobId);
    }


    public List<TransientStreamingJob> getActiveJobs() {
        return _jobs.values()
                .stream()
                .filter(j -> !j.getJobStatus().isTerminal())
                .collect(toList());
    }


    public Map<String, List<TransientStreamingJob>> getJobGroupedByHealthReportUri() {
        return _jobs.values()
                .stream()
                .filter(j -> !j.getJobStatus().isTerminal() && j.getHealthReportCallbackURI() != null)
                .collect(groupingBy(TransientStreamingJob::getHealthReportCallbackURI));

    }


    public void clearJob(long jobId) {
        _jobs.remove(jobId);
    }


    public void cancelJob(long jobId, boolean doCleanup) {
        TransientStreamingJobImpl job = getJobImpl(jobId);
        job.setCancelled(true);
        job.setJobStatus(new StreamingJobStatus(StreamingJobStatusType.CANCELLING));
        job.setCleanupEnabled(doCleanup);
    }


    public void setJobStatus(long jobId, StreamingJobStatusType statusType, String statusMessage) {
        setJobStatus(jobId, new StreamingJobStatus(statusType, statusMessage));
    }

    public void setJobStatus(long jobId, StreamingJobStatus status) {
        getJobImpl(jobId).setJobStatus(status);
    }

    public void setLastJobActivity(long jobId, long frameId, Instant time) {
        TransientStreamingJobImpl job = getJobImpl(jobId);
        job.setLastActivityFrame(frameId);
        job.setLastActivityTime(time);
    }
}
