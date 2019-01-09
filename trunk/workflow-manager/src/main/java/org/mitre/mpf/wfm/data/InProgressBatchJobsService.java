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
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

@Component
public class InProgressBatchJobsService {

    private static final Logger LOG = LoggerFactory.getLogger(InProgressBatchJobsService.class);

    private final Redis _redis;

    private final Map<Long, TransientJobImpl> _jobs = new HashMap<>();

    @Inject
    InProgressBatchJobsService(Redis redis) {
        _redis = redis;
    }


    public TransientJob addJob(
            long jobId,
            String externalId,
            TransientDetectionSystemProperties propertiesSnapshot,
            TransientPipeline pipeline,
            int priority,
            boolean outputEnabled,
            String callbackUrl,
            String callbackMethod,
            List<TransientMedia> transientMedia,
            Map<String, String> jobProperties,
            Map<String, Map<String, String>> algorithmProperties) {
        TransientJobImpl job = new TransientJobImpl(
                jobId,
                externalId,
                propertiesSnapshot,
                pipeline,
                priority,
                outputEnabled,
                callbackUrl,
                callbackMethod,
                transientMedia,
                jobProperties,
                algorithmProperties);
        _jobs.put(jobId, job);
        return job;
    }


    public TransientJob getJob(long jobId) {
        return getJobImpl(jobId);
    }


    private TransientJobImpl getJobImpl(long jobId) {
        TransientJobImpl job = _jobs.get(jobId);
        if (job != null) {
            return job;
        }
        throw new WfmProcessingException("Unable to locate batch job with id: " + jobId);
    }


    public void clearJob(long jobId) {
        TransientJobImpl job = getJobImpl(jobId);
        _redis.clearTracks(job);
        _jobs.remove(jobId);
        for (TransientMedia media : job.getMedia()) {
            if (media.getUriScheme().isRemote()) {
                try {
                    Files.deleteIfExists(Paths.get(media.getLocalPath()));
                }
                catch (IOException e) {
                    LOG.warn(String.format(
                            "Failed to delete the local file '%s' which was created retrieved from a remote location - it must be manually deleted.",
                            media.getLocalPath()), e);
                }
            }
        }
    }

    public boolean containsJob(long jobId) {
        return _jobs.containsKey(jobId);
    }

    public boolean cancelJob(long jobId) {
        getJobImpl(jobId).setCancelled(true);
        return true;
    }

    public SortedSet<Track> getTracks(long jobId, long mediaId, int taskIndex, int actionIndex) {
        return _redis.getTracks(jobId, mediaId, taskIndex, actionIndex);
    }

    public void addTrack(Track track) {
        _redis.addTrack(track);
    }


    public void addJobWarning(long jobId, String message) {
        getJobImpl(jobId).addWarning(message);
    }


    public void addJobError(long jobId, String message) {
        getJobImpl(jobId).addError(message);
    }

    public void addDetectionProcessingError(DetectionProcessingError error) {
        getJobImpl(error.getJobId()).addDetectionProcessingError(error);
    }

    public void setTracks(long jobId, long mediaId, int taskIndex, int actionIndex, Collection<Track> tracks) {
        _redis.setTracks(jobId, mediaId, taskIndex, actionIndex, tracks);
    }


    public void setJobStatus(long jobId, BatchJobStatusType batchJobStatusType) {
        getJobImpl(jobId).setStatus(batchJobStatusType);
    }


    public void addMediaError(long jobId, long mediaId, String message) {
        getJob(jobId)
                .getMedia()
                .stream()
                .filter(m -> m.getId() == mediaId)
                .findAny()
                .ifPresent(m -> {
                    m.setFailed(true);
                    m.setMessage(message);
                });
    }


    public void setCurrentTaskIndex(long jobId, int taskIndex) {
        getJobImpl(jobId).setCurrentStage(taskIndex);
    }

    public long nextId() {
        return IdGenerator.next();
    }
}
