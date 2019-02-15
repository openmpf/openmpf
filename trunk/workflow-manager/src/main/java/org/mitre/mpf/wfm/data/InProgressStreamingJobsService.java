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

import org.mitre.mpf.interop.util.TimeUtils;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobStatus;
import org.mitre.mpf.wfm.data.entities.transients.TransientPipeline;
import org.mitre.mpf.wfm.data.entities.transients.TransientStream;
import org.mitre.mpf.wfm.data.entities.transients.TransientStreamingJob;
import org.mitre.mpf.wfm.data.entities.transients.TransientStreamingJobImpl;
import org.mitre.mpf.wfm.enums.StreamingJobStatusType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Singleton;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

@Component
@Singleton
public class InProgressStreamingJobsService {

    private static final Logger LOG = LoggerFactory.getLogger(InProgressStreamingJobsService.class);

    private final Map<Long, TransientStreamingJobImpl> _jobs = new HashMap<>();


    public synchronized TransientStreamingJob addJob(
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

        if (_jobs.containsKey(jobId)) {
            throw new IllegalArgumentException(String.format("Job with id %s already exists.", jobId));
        }

        LOG.info("Initializing streaming job {} which will run the \"{}\" pipeline", jobId, pipeline.getName());

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


    public synchronized TransientStreamingJob getJob(long jobId) {
        return getJobImpl(jobId);
    }


    private TransientStreamingJobImpl getJobImpl(long jobId) {
        TransientStreamingJobImpl job = _jobs.get(jobId);
        if (job != null) {
            return job;
        }
        throw new WfmProcessingException("Unable to locate streaming job with id: " + jobId);
    }


    public synchronized List<TransientStreamingJob> getActiveJobs() {
        return _jobs.values()
                .stream()
                .filter(j -> !j.getJobStatus().isTerminal())
                .collect(toList());
    }


    public synchronized Map<String, List<TransientStreamingJob>> getJobsGroupedByHealthReportUri() {
        //noinspection OptionalGetWithoutIsPresent - False positive because .isPresent() checked in call to .filter().
        return _jobs.values()
                .stream()
                .filter(j -> !j.getJobStatus().isTerminal() && j.getHealthReportCallbackURI().isPresent())
                .collect(groupingBy(j -> j.getHealthReportCallbackURI().get()));
    }


    public synchronized void clearJob(long jobId) {
        LOG.info("Clearing all job information for job {}", jobId);
        _jobs.remove(jobId);
    }


    public synchronized void cancelJob(long jobId, boolean doCleanup) {
        LOG.info("Marking job {} as cancelled.", jobId);
        TransientStreamingJobImpl job = getJobImpl(jobId);
        job.setCancelled(true);
        job.setJobStatus(new StreamingJobStatus(StreamingJobStatusType.CANCELLING));
        job.setCleanupEnabled(doCleanup);
    }


    public synchronized void setJobStatus(long jobId, StreamingJobStatusType statusType, String statusMessage) {
        setJobStatus(jobId, new StreamingJobStatus(statusType, statusMessage));
    }

    public synchronized void setJobStatus(long jobId, StreamingJobStatus status) {
        LOG.info("Setting status of job {} to {}.", jobId, status);
        getJobImpl(jobId).setJobStatus(status);
    }

    public synchronized void setLastJobActivity(long jobId, long frameId, Instant time) {
        LOG.info("Setting last activity of job {} to frame {} at time {}",
                 jobId, frameId, TimeUtils.toIsoString(time));
        TransientStreamingJobImpl job = getJobImpl(jobId);
        job.setLastActivityFrame(frameId);
        job.setLastActivityTime(time);
    }
}
