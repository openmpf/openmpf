/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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

import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.mitre.mpf.interop.JsonIssueDetails;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.access.JobRequestDao;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJobImpl;
import org.mitre.mpf.wfm.data.entities.persistent.DetectionProcessingError;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.MediaImpl;
import org.mitre.mpf.wfm.data.entities.persistent.SystemPropertiesSnapshot;
import org.mitre.mpf.wfm.data.entities.persistent.TiesDbInfo;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.IssueSources;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.service.JobStatusBroadcaster;
import org.mitre.mpf.wfm.util.FrameTimeInfo;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.MediaRange;
import org.mitre.mpf.wfm.util.MediaTypeUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

@Component
@Singleton
public class InProgressBatchJobsService {

    private static final Logger LOG = LoggerFactory.getLogger(InProgressBatchJobsService.class);

    private final PropertiesUtil _propertiesUtil;

    private final Redis _redis;

    private final JobRequestDao _jobRequestDao;

    private final JobStatusBroadcaster _jobStatusBroadcaster;

    private final MediaTypeUtils _mediaTypeUtils;

    private final Map<Long, BatchJobImpl> _jobs = new HashMap<>();

    private final Collection<Long> _jobsWithCallbacksInProgress = new HashSet<>();

    private final Map<Long, CompletableFuture<Optional<JsonOutputObject>>> _resultsAvailableFutures
            = new HashMap<>();


    @Inject
    public InProgressBatchJobsService(
            PropertiesUtil propertiesUtil,
            Redis redis,
            JobRequestDao jobRequestDao,
            JobStatusBroadcaster jobStatusBroadcaster,
            MediaTypeUtils mediaTypeUtils) {
        _propertiesUtil = propertiesUtil;
        _redis = redis;
        _jobRequestDao = jobRequestDao;
        _jobStatusBroadcaster = jobStatusBroadcaster;
        _mediaTypeUtils = mediaTypeUtils;
    }


    public synchronized BatchJob addJob(
            long jobId,
            String externalId,
            SystemPropertiesSnapshot propertiesSnapshot,
            JobPipelineElements pipeline,
            int priority,
            String callbackUrl,
            String callbackMethod,
            Collection<Media> media,
            Map<String, String> jobProperties,
            Map<String, ? extends Map<String, String>> algorithmProperties,
            boolean shouldCheckTiesDbAfterMediaInspection) {

        if (_jobs.containsKey(jobId)) {
            throw new IllegalArgumentException(String.format("Job with id %s already exists.", jobId));
        }

        var mediaInfo = media.stream()
                .map(m -> String.format("\"%s\" (id=%s)", m.getUri(), m.getId()))
                .collect(joining(", "));

        LOG.info("Initializing batch job {} which will run the \"{}\" pipeline on the following " +
                         "media: {}", jobId, pipeline.getName(), mediaInfo);
        List<MediaImpl> mediaImpls = media.stream()
                .map(MediaImpl::toMediaImpl)
                .collect(ImmutableList.toImmutableList());

        var job = new BatchJobImpl(
                jobId,
                externalId,
                propertiesSnapshot,
                pipeline,
                priority,
                callbackUrl,
                callbackMethod,
                mediaImpls,
                jobProperties,
                algorithmProperties,
                shouldCheckTiesDbAfterMediaInspection);
        _jobs.put(jobId, job);

        media.stream()
                .filter(Media::isFailed)
                .forEach(m -> addError(jobId, m.getId(), IssueCodes.MEDIA_INITIALIZATION, m.getErrorMessage()));

        _resultsAvailableFutures.put(jobId, ThreadUtil.newFuture());
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

    public synchronized void setCallbacksInProgress(long jobId) {
        _jobsWithCallbacksInProgress.add(jobId);
    }

    public synchronized boolean jobHasCallbacksInProgress(long jobId) {
        return _jobsWithCallbacksInProgress.contains(jobId);
    }

    public synchronized void clearJob(long jobId) {
        LOG.info("Clearing all job information for job: {}", jobId);
        BatchJobImpl job = getJobImpl(jobId);
        _redis.clearTracks(job);
        _jobs.remove(jobId);
        _jobsWithCallbacksInProgress.remove(jobId);
        var resultsAvailableFuture = _resultsAvailableFutures.remove(jobId);
        if (!resultsAvailableFuture.isDone()) {
            resultsAvailableFuture.complete(Optional.empty());
        }

        for (Media media : job.getMedia()) {
            if (media.getUriScheme().isRemote()) {
                try {
                    Files.deleteIfExists(media.getLocalPath());
                }
                catch (IOException e) {
                    LOG.warn(String.format(
                            "Failed to delete the local file '%s' which was retrieved " +
                                    "from a remote location - it must be manually deleted.",
                            media.getLocalPath()), e);
                }
            }

            if (media.getConvertedMediaPath().isPresent()) {
                try {
                    Files.deleteIfExists(media.getConvertedMediaPath().get());
                }
                catch (IOException e) {
                    LOG.warn(String.format(
                            "Failed to delete the converted media file '%s' - " +
                                    "it must be manually deleted.",
                            media.getConvertedMediaPath().get()), e);
                }
            }
        }

        // Clean up derivative media directory for this job in case any media was moved to remote storage.
        boolean hasDerivativeMedia = job.getMedia().stream().anyMatch(Media::isDerivative);
        if (hasDerivativeMedia) {
            Path derivativeMediaPath = _propertiesUtil.getJobDerivativeMediaDirectory(jobId).toPath();
            IoUtils.deleteEmptyDirectoriesRecursively(derivativeMediaPath);
        }
    }

    public synchronized void clearOnInitializationError(long jobId) {
        _jobs.remove(jobId);
    }

    public synchronized boolean containsJob(long jobId) {
        return _jobs.containsKey(jobId);
    }

    public synchronized boolean cancelJob(long jobId) {
        var job = getJobImpl(jobId);
        if (!job.isCancelled()) {
            LOG.info("Marking job {} as cancelled.", jobId);
            setJobStatus(jobId, job.getStatus().onCancel());
        }
        return job.isCancelled();
    }

    public synchronized SortedSet<Track> getTracks(
            long jobId, long mediaId, int taskIndex, int actionIndex) {
        return _redis.getTracks(jobId, mediaId, taskIndex, actionIndex);
    }

    public synchronized Stream<Track> getTracksStream(
            long jobId, long mediaId, int taskIndex, int actionIndex) {
        return _redis.getTracksStream(jobId, mediaId, taskIndex, actionIndex);
    }

    public synchronized int getTrackCount(
            long jobId, long mediaId, int taskIndex, int actionIndex) {
        return _redis.getTrackCount(jobId, mediaId, taskIndex, actionIndex);
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


    public synchronized void addJobWarning(long jobId, IssueCodes code, String message) {
        addWarning(jobId, 0, code, message);
    }

    public synchronized void addWarning(long jobId, long mediaId, IssueCodes code, String message) {
        addWarning(jobId, mediaId, code, message, IssueSources.WORKFLOW_MANAGER);
    }

    public synchronized void addWarning(long jobId, long mediaId, IssueCodes code, String message,
                                        IssueSources source) {
        var codeString = IssueCodes.toString(code);
        LOG.warn("Adding the following warning to job {}'s media {}: {} - {}", jobId, mediaId, codeString, message);

        var job = getJobImpl(jobId);
        job.addWarning(mediaId, IssueSources.toString(source), codeString, message);
        setJobStatus(jobId, job.getStatus().onWarning());
    }


    public synchronized void addJobError(long jobId, IssueCodes code, String message) {
        addError(jobId, 0, code, message);
    }

    public synchronized void addError(long jobId, long mediaId, IssueCodes code, String message) {
        addError(jobId, mediaId, code, message, IssueSources.WORKFLOW_MANAGER);
    }

    public synchronized void addError(long jobId, long mediaId, IssueCodes code,
                                      String message, IssueSources source) {
        var codeString = IssueCodes.toString(code);
        LOG.error("Adding the following error to job {}'s media {}: {} - {}", jobId, mediaId, codeString, message);
        var job = getJobImpl(jobId);
        job.addError(mediaId, IssueSources.toString(source), codeString, message);
        if (source != IssueSources.MARKUP && mediaId != 0) {
            getMediaImpl(jobId, mediaId).setFailed(true);
        }
        setJobStatus(jobId, job.getStatus().onError());
    }


    public synchronized void addFatalError(long jobId, IssueCodes code, String message) {
        var codeString = IssueCodes.toString(code);
        LOG.error("Adding the following error to job {}: {} - {}", jobId, codeString, message);
        var job = getJobImpl(jobId);
        job.addError(0, IssueSources.toString(IssueSources.WORKFLOW_MANAGER), codeString, message);
        setJobStatus(jobId, job.getStatus().onFatalError());
    }


    public synchronized void addDetectionProcessingError(DetectionProcessingError error) {
        LOG.error("Adding detection processing error for job {}'s media {}: {} - {}",
                 error.getJobId(), error.getMediaId(), error.getErrorCode(), error.getErrorMessage());
        var job = getJobImpl(error.getJobId());
        job.addDetectionProcessingError(error);

        var media = getMediaImpl(error.getJobId(), error.getMediaId());
        media.setFailed(true);

        if (error.getErrorCode().equals(MpfConstants.REQUEST_CANCELLED)) {
            cancelJob(error.getJobId());
        }
        else {
            setJobStatus(error.getJobId(), job.getStatus().onError());
        }
    }


    public synchronized Multimap<Long, JsonIssueDetails> getMergedDetectionErrors(long jobId) {
        return DetectionErrorUtil.getMergedDetectionErrors(getJob(jobId));
    }


    public synchronized void handleMarkupCancellation(long jobId, long mediaId) {
        var job = getJobImpl(jobId);
        job.addError(mediaId, IssueSources.MARKUP.toString(), MpfConstants.REQUEST_CANCELLED,
                     "Successfully cancelled.");
        cancelJob(jobId);
    }


    public synchronized void setJobStatus(long jobId, BatchJobStatusType batchJobStatus) {
        var job = getJobImpl(jobId);
        if (job.getStatus() == batchJobStatus) {
            return;
        }
        LOG.info("Setting status of job {} to {}", jobId, batchJobStatus);
        job.setStatus(batchJobStatus);
        if (!batchJobStatus.isTerminal()) {
            // Terminal jobs are persisted, and status is broadcast, in JobCompleteProcessorImpl.
            _jobRequestDao.updateStatus(jobId, batchJobStatus);
            _jobStatusBroadcaster.broadcast(jobId, batchJobStatus);
        }
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


    public synchronized Media initMedia(
            String uriStr,
            Map<String, String> mediaSpecificProperties,
            Map<String, String> providedMetadataProperties,
            Collection<MediaRange> frameRanges,
            Collection<MediaRange> timeRanges) {
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

            var media = new MediaImpl(
                    mediaId,
                    uriStr,
                    uriScheme,
                    localPath,
                    mediaSpecificProperties,
                    providedMetadataProperties,
                    frameRanges,
                    timeRanges,
                    errorMessage);
            var mimeType = providedMetadataProperties.get("MIME_TYPE");
            if (mimeType != null && !mimeType.isBlank()) {
                var mediaType = _mediaTypeUtils.parse(mimeType);
                media.setMimeType(mimeType);
                media.setType(mediaType);
            }
            return media;
        }
        catch (URISyntaxException | IllegalArgumentException | FileSystemNotFoundException e) {
            return new MediaImpl(
                    mediaId,
                    uriStr,
                    UriScheme.UNDEFINED,
                    null,
                    mediaSpecificProperties,
                    providedMetadataProperties,
                    frameRanges,
                    timeRanges,
                    e.getMessage());
        }
    }

    public synchronized Media initDerivativeMedia(long jobId,
                                                  long mediaId,
                                                  long parentMediaId,
                                                  int taskIndex,
                                                  Path localPath,
                                                  Map<String, String> trackProperties) {
        LOG.info("Initializing derivative media from {} with id {}", localPath.toString(), mediaId);

        String errorMessage = checkForLocalFileError(localPath);

        var metadata = new HashMap<>(trackProperties); // include page number and other info, if available
        metadata.remove(MpfConstants.DERIVATIVE_MEDIA_TEMP_PATH);
        metadata.remove(MpfConstants.DERIVATIVE_MEDIA_ID);
        metadata.put(MpfConstants.IS_DERIVATIVE_MEDIA, "TRUE");

        MediaImpl derivativeMedia = new MediaImpl(
                mediaId,
                parentMediaId,
                taskIndex,
                localPath.toUri().toString(),
                UriScheme.FILE,
                localPath,
                // Derivative media do not inherit parent media properties. For example, specifying
                // a ROTATION value on the parent may not be appropriate for the children.
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableSet.of(),
                ImmutableSet.of(),
                errorMessage);

        derivativeMedia.addMetadata(metadata);

        getJobImpl(jobId).addDerivativeMedia(derivativeMedia);

        return derivativeMedia;
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
            long jobId, long mediaId, String sha256, MediaType mediaType, String mimeType, int length,
            Map<String, String> metadata) {
        LOG.info("Adding media metadata to job {}'s media {}.", jobId, mediaId);
        MediaImpl media = getMediaImpl(jobId, mediaId);
        media.setSha256(sha256);
        media.setType(mediaType);
        media.setMimeType(mimeType);
        media.setLength(length);
        media.addMetadata(metadata);
    }

    public synchronized void addConvertedMediaPath(long jobId, long mediaId,
                                                   Path convertedMediaPath) {
        LOG.info("Setting job {}'s media {}'s converted media path to {}",
                 jobId, mediaId, convertedMediaPath);
        getMediaImpl(jobId, mediaId).setConvertedMediaPath(convertedMediaPath);
    }

    public synchronized void addStorageUri(long jobId, long mediaId,
                                           String storageUri) {
        LOG.info("Setting job {}'s media {}'s storage URI to {}",
                jobId, mediaId, storageUri);
        getMediaImpl(jobId, mediaId).setStorageUri(storageUri);
    }

    public synchronized void addFrameTimeInfo(long jobId, long mediaId,
                                              FrameTimeInfo frameTimeInfo) {
        LOG.info("Adding frame time info to job {}'s media {}.", jobId, mediaId);
        getMediaImpl(jobId, mediaId).setFrameTimeInfo(frameTimeInfo);
    }


    public synchronized void addTiesDbInfo(long jobId, long mediaId, TiesDbInfo tiesDbInfo) {
        getMediaImpl(jobId, mediaId)
                .setTiesDbInfo(tiesDbInfo);
    }


    public synchronized Optional<MediaType> getMediaType(long jobId, long mediaId) {
        return Optional.ofNullable(_jobs.get(jobId))
                .map(j -> j.getMedia(mediaId))
                .flatMap(MediaImpl::getType);
    }

    private MediaImpl getMediaImpl(long jobId, long mediaId) {
        MediaImpl media = getJobImpl(jobId).getMedia(mediaId);
        if (media == null) {
            throw new IllegalArgumentException(String.format("Job %s does not have media with id %s", jobId, mediaId));
        }
        return media;
    }


    public synchronized CompletableFuture<Optional<JsonOutputObject>>
            getJobResultsAvailableFuture(long jobId) {
        return Optional.ofNullable(_resultsAvailableFutures.get(jobId))
                .map(CompletableFuture::copy)
                .orElseGet(() -> ThreadUtil.completedFuture(Optional.empty()));
    }

    public synchronized void reportJobResultsAvailable(long jobId, JsonOutputObject outputObject) {
        var future = _resultsAvailableFutures.get(jobId);
        if (future == null) {
            throw new WfmProcessingException("Unable to locate batch job with id: " + jobId);
        }
        future.completeAsync(() -> Optional.ofNullable(outputObject));
    }


    public synchronized void addProcessingTime(long jobId, Action action, long processingTime) {
        getJobImpl(jobId).addProcessingTime(action, processingTime);
    }

    public void reportMissingProcessingTime(long jobId, Action action) {
        addProcessingTime(jobId, action, -1);
    }
}
