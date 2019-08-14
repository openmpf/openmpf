/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

import com.google.common.collect.ImmutableList;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJobImpl;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Component
@Singleton
public class InProgressBatchJobsService {

    private static final Logger LOG = LoggerFactory.getLogger(InProgressBatchJobsService.class);

    private final PropertiesUtil _propertiesUtil;

    private final Redis _redis;

    private final Map<Long, BatchJobImpl> _jobs = new HashMap<>();


    @Inject
    InProgressBatchJobsService(PropertiesUtil propertiesUtil, Redis redis) {
        _propertiesUtil = propertiesUtil;
        _redis = redis;
    }


    public BatchJob addJob(
            long jobId,
            String externalId,
            SystemPropertiesSnapshot propertiesSnapshot,
            TransientPipeline pipeline,
            int priority,
            boolean outputEnabled,
            String callbackUrl,
            String callbackMethod,
            Collection<TransientMedia> transientMedia,
            Map<String, String> jobProperties,
            Map<String, ? extends Map<String, String>> algorithmProperties) {

        if (_jobs.containsKey(jobId)) {
            throw new IllegalArgumentException(String.format("Job with id %s already exists.", jobId));
        }

        LOG.info("Initializing batch job {} which will run the \"{}\" pipeline", jobId, pipeline.getName());
        List<TransientMediaImpl> mediaImpls = transientMedia.stream()
                .map(TransientMediaImpl::toTransientMediaImpl)
                .collect(ImmutableList.toImmutableList());

        var job = new BatchJobImpl(
                jobId,
                externalId,
                propertiesSnapshot,
                pipeline,
                priority,
                outputEnabled,
                callbackUrl,
                callbackMethod,
                mediaImpls,
                jobProperties,
                algorithmProperties);
        _jobs.put(jobId, job);
        return job;
    }



    public synchronized BatchJob getJob(long jobId) {
        return getJobImpl(jobId);
    }


    private BatchJobImpl getJobImpl(long jobId) {
        BatchJobImpl job = _jobs.get(jobId);
        if (job != null) {
            return job;
        }
        throw new WfmProcessingException("Unable to locate batch job with id: " + jobId);
    }


    public synchronized void clearJob(long jobId) {
        LOG.info("Clearing all job information for job: {}", jobId);
        BatchJobImpl job = getJobImpl(jobId);
        _redis.clearTracks(job);
        _jobs.remove(jobId);
        for (TransientMedia media : job.getMedia()) {
            if (media.getUriScheme().isRemote()) {
                try {
                    Files.deleteIfExists(media.getLocalPath());
                }
                catch (IOException e) {
                    LOG.warn(String.format(
                            "Failed to delete the local file '%s' which was created retrieved from a remote location - it must be manually deleted.",
                            media.getLocalPath()), e);
                }
            }
        }
    }

    public synchronized boolean containsJob(long jobId) {
        return _jobs.containsKey(jobId);
    }

    public synchronized boolean cancelJob(long jobId) {
        LOG.info("Marking job {} as cancelled.", jobId);
        getJobImpl(jobId).setCancelled(true);
        return true;
    }

    public synchronized SortedSet<Track> getTracks(long jobId, long mediaId, int taskIndex, int actionIndex) {
        return _redis.getTracks(jobId, mediaId, taskIndex, actionIndex);
    }


    public synchronized void addTrack(Track track) {
        LOG.debug("Storing new track for job {}'s media {}.", track.getJobId(), track.getMediaId());
        _redis.addTrack(track);
    }

    public synchronized void setTracks(long jobId, long mediaId, int taskIndex, int actionIndex,
                                       Collection<Track> tracks) {
        LOG.info("Replacing tracks for job {}'s media {}", jobId, mediaId);
        _redis.setTracks(jobId, mediaId, taskIndex, actionIndex, tracks);
    }


    public synchronized void addJobWarning(long jobId, String message) {
        LOG.info("Adding the following warning to job {}: {}", jobId, message);
        getJobImpl(jobId).addWarning(message);
    }


    public synchronized void addJobError(long jobId, String message) {
        LOG.info("Adding the following error to job {}: {}", jobId, message);
        getJobImpl(jobId).addError(message);
    }

    public synchronized void addDetectionProcessingError(DetectionProcessingError error) {
        LOG.info("Adding detection processing error for job {}'s media {} with message: {}",
                 error.getJobId(), error.getMediaId(), error.getError());
        getJobImpl(error.getJobId()).addDetectionProcessingError(error);
    }



    public synchronized void setJobStatus(long jobId, BatchJobStatusType batchJobStatusType) {
        LOG.info("Setting status of job {} to {}", jobId, batchJobStatusType);
        getJobImpl(jobId).setStatus(batchJobStatusType);
    }


    public synchronized void incrementTask(long jobId) {
        var job = getJobImpl(jobId);
        int currentTask = job.getCurrentTaskIndex();
        int nextTask = currentTask + 1;
        LOG.info("Changing job {}'s current task index from {} to {}", jobId, currentTask, nextTask);
        job.setCurrentTaskIndex(nextTask);
    }



    private static final Set<UriScheme> SUPPORTED_URI_SCHEMES = EnumSet.of(UriScheme.FILE, UriScheme.HTTP,
                                                                           UriScheme.HTTPS);
    private static final String NOT_DEFINED_URI_SCHEME = "URI scheme not defined";
    private static final String NOT_SUPPORTED_URI_SCHEME = "Unsupported URI scheme";
    private static final String LOCAL_FILE_DOES_NOT_EXIST = "File does not exist";
    private static final String LOCAL_FILE_NOT_READABLE = "File is not readable";


    public synchronized TransientMedia initMedia(String uriStr, Map<String, String> mediaSpecificProperties) {
        long mediaId = IdGenerator.next();
        LOG.info("Initializing media from {} with id {}", uriStr, mediaId);

        try {
            URI uri = new URI(uriStr);
            UriScheme uriScheme = UriScheme.parse(uri.getScheme());
            String errorMessage = null;
            Path localPath = null;

            if (uriScheme == UriScheme.UNDEFINED) {
                errorMessage = NOT_DEFINED_URI_SCHEME;
            }
            else if (!SUPPORTED_URI_SCHEMES.contains(uriScheme)) {
                errorMessage = NOT_SUPPORTED_URI_SCHEME;
            }
            else if (uriScheme == UriScheme.FILE) {
                localPath = Paths.get(uri).toAbsolutePath();
                errorMessage = checkForLocalFileError(localPath);
            }
            else {
                localPath = _propertiesUtil.getTemporaryMediaDirectory()
                        .toPath()
                        .resolve(UUID.randomUUID().toString())
                        .toAbsolutePath();
            }

            return new TransientMediaImpl(mediaId, uriStr, uriScheme, localPath, mediaSpecificProperties,
                                          errorMessage);
        }
        catch (URISyntaxException | IllegalArgumentException | FileSystemNotFoundException e) {
            return new TransientMediaImpl(mediaId, uriStr, UriScheme.UNDEFINED, null,
                                          mediaSpecificProperties, e.getMessage());
        }
    }

    private static String checkForLocalFileError(Path path) {
        if (Files.notExists(path)) {
            return LOCAL_FILE_DOES_NOT_EXIST;
        }
        if (!Files.isReadable(path)) {
            return LOCAL_FILE_NOT_READABLE;
        }
        return null;
    }


    public synchronized void addMediaInspectionInfo(
            long jobId, long mediaId, String sha256, String mimeType, int length,
            Map<String, String> metadata) {
        LOG.info("Adding media inspections results to job {}'s media {}.", jobId, mediaId);
        TransientMediaImpl media = getMediaImpl(jobId, mediaId);
        media.setSha256(sha256);
        media.setType(mimeType);
        media.setLength(length);
        media.addMetadata(metadata);
    }


    public synchronized void addMediaError(long jobId, long mediaId, String message) {
        LOG.info("Adding the following error message to job {}'s media {}: {}", jobId, mediaId, message);
        TransientMediaImpl media = getMediaImpl(jobId, mediaId);
        media.setFailed(true);
        media.setMessage(message);
    }

    public synchronized void clearMediaError(long jobId, long mediaId) {
        LOG.info("Clearing error from job {}'s media {}", jobId, mediaId);
        getMediaImpl(jobId, mediaId).setFailed(false);
    }


    private TransientMediaImpl getMediaImpl(long jobId, long mediaId) {
        TransientMediaImpl media = getJobImpl(jobId).getMedia(mediaId);
        if (media == null) {
            throw new IllegalArgumentException(String.format("Job %s does not have media with id %s", jobId, mediaId));
        }
        return media;
    }
}
