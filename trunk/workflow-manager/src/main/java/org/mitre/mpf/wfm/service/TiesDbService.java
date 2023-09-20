/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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


package org.mitre.mpf.wfm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.mitre.mpf.mvc.security.OAuthClientTokenProvider;
import org.mitre.mpf.rest.api.TiesDbRepostResponse;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.access.JobRequestDao;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.TiesDbInfo;
import org.mitre.mpf.wfm.data.entities.transients.TrackCounter;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.mitre.mpf.wfm.enums.IssueCodes;

/**
 * Refer to https://github.com/Noblis/ties-lib for more information on the Triage Import Export
 * Schema (TIES). For each piece of media, we create one or more TIES
 * "supplementalDescription (Data Object)" entries in the database, one for each
 * analytic (algorithm) run on the media. In general, a "supplementalDescription" is a kind of TIES
 * "assertion", which is used to represent metadata about the media object. In our case it
 * represents the detection and track information in the OpenMPF JSON output object.
 */
@Component
public class TiesDbService {

    private static final Logger LOG = LoggerFactory.getLogger(TiesDbService.class);

    private final PropertiesUtil _propertiesUtil;

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;

    private final ObjectMapper _objectMapper;

    private final JsonUtils _jsonUtils;

    private final HttpClientUtils _httpClientUtils;

    private final OAuthClientTokenProvider _oAuthClientTokenProvider;

    private final JobRequestDao _jobRequestDao;

    private final InProgressBatchJobsService _inProgressJobs;

    private final JobConfigHasher _jobConfigHasher;


    @Inject
    TiesDbService(PropertiesUtil propertiesUtil,
                  AggregateJobPropertiesUtil aggregateJobPropertiesUtil,
                  ObjectMapper objectMapper,
                  JsonUtils jsonUtils,
                  HttpClientUtils httpClientUtils,
                  OAuthClientTokenProvider oAuthClientTokenProvider,
                  JobRequestDao jobRequestDao,
                  InProgressBatchJobsService inProgressJobs,
                  JobConfigHasher jobConfigHasher) {
        _propertiesUtil = propertiesUtil;
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
        _objectMapper = objectMapper;
        _httpClientUtils = httpClientUtils;
        _oAuthClientTokenProvider = oAuthClientTokenProvider;
        _jsonUtils = jsonUtils;
        _jobRequestDao = jobRequestDao;
        _inProgressJobs = inProgressJobs;
        _jobConfigHasher = jobConfigHasher;
    }


    public void prepareAssertions(
            BatchJob job,
            BatchJobStatusType jobStatus,
            Instant timeCompleted,
            URI outputObjectLocation,
            String outputObjectSha,
            TrackCounter trackCounter) {
        if (anyMediaDownloadsFailed(job)) {
            return;
        }

        var parentWithDerivativeCounts = new HashMap<ParentMediaAlgoTiesDbUrl, TrackTypeCount>();

        for (var jobPart : JobPartsIter.of(job)) {
            var media = jobPart.getMedia();

            boolean outputLastTaskOnly
                    = _aggregateJobPropertiesUtil.isOutputLastTaskOnly(media, job);
            if (outputLastTaskOnly
                    && jobPart.getTaskIndex() != job.getPipelineElements().getTaskCount() - 1) {
                // suppressed
                continue;
            }

            var tasksToMerge = _aggregateJobPropertiesUtil.getTasksToMerge(media, job);
            if (tasksToMerge.containsValue(jobPart.getTaskIndex())) {
                // task will be merged away
                continue;
            }

            var trackCountEntry = trackCounter.get(jobPart);
            int trackCount;
            if (trackCountEntry == null) {
                // this part of the job was not performed on this media
                boolean mergeWithPrevTask = Boolean.parseBoolean(
                        _aggregateJobPropertiesUtil.getValue(
                                MpfConstants.OUTPUT_MERGE_WITH_PREVIOUS_TASK_PROPERTY, jobPart));
                if (mergeWithPrevTask) {
                    // It is a merge source so its algorithm name should not appear in the output object.
                    continue;
                }
                // Ensure that even if no derivative media was generated, or this task was skipped because no tracks
                // were generated in the previous task, that we create a "NO TRACKS" entry.
                trackCount = 0;
            }
            else {
                trackCount = trackCountEntry.getCount();
            }

            var parentMedia = media.isDerivative() ? job.getMedia(media.getParentId()) : media;
            var algoAndTypeAndTiesDbUrlToUse = getAlgoAndTypeAndTiesDbUrlToUse(
                    jobPart,
                    parentMedia,
                    trackCount,
                    trackCounter,
                    tasksToMerge);
            String algoName = algoAndTypeAndTiesDbUrlToUse.getLeft();
            String trackType = algoAndTypeAndTiesDbUrlToUse.getMiddle();
            String tiesDbUrl = algoAndTypeAndTiesDbUrlToUse.getRight();

            if (StringUtils.isBlank(tiesDbUrl)) {
                continue;
            }

            parentWithDerivativeCounts.merge(
                    new ParentMediaAlgoTiesDbUrl(parentMedia.getId(), algoName, tiesDbUrl),
                    new TrackTypeCount(trackType, trackCount),
                    TrackTypeCount::merge);
        }

        var jobConfigHash = parentWithDerivativeCounts.isEmpty()
                ? null
                : getJobConfigHash(job);
        for (var entry : parentWithDerivativeCounts.entrySet()) {
            var parentMediaAlgoUrl = entry.getKey();
            var assertion = createActionAssertion(
                    job,
                    jobStatus,
                    timeCompleted,
                    outputObjectLocation,
                    outputObjectSha,
                    parentMediaAlgoUrl.algorithmName(),
                    entry.getValue().trackType(),
                    entry.getValue().count(),
                    jobConfigHash);
            var tiesDbInfo = new TiesDbInfo(parentMediaAlgoUrl.tiesDbUrl(), assertion);
            _inProgressJobs.addTiesDbInfo(
                    job.getId(), parentMediaAlgoUrl.parentMediaId(), tiesDbInfo);
        }
    }


    public CompletableFuture<Void> postAssertions(BatchJob job) {
        var futures = new ArrayList<CompletableFuture<Void>>();
        for (var media : job.getMedia()) {
            boolean useOidc = Boolean.parseBoolean(
                    _aggregateJobPropertiesUtil.getValue(
                            MpfConstants.TIES_DB_USE_OIDC, job, media));
            for (var tiesDbInfo : media.getTiesDbInfo()) {
                futures.add(postAssertion(
                        tiesDbInfo, media.getLinkedHash().orElseThrow(), useOidc));
            }
        }

        if (futures.isEmpty()) {
            return ThreadUtil.completedFuture(null);
        }

        return ThreadUtil.allOf(futures)
                .thenRun(() -> _jobRequestDao.setTiesDbSuccessful(job.getId()))
                .exceptionally(e -> reportExceptions(job.getId(), futures));
    }


    public TiesDbRepostResponse repost(Collection<Long> jobIds) {
        var failures = new ArrayList<TiesDbRepostResponse.Failure>();
        var futures = new HashMap<Long, CompletableFuture<Void>>(jobIds.size());
        for (long jobId : jobIds) {
            var jobRequest = _jobRequestDao.findById(jobId);
            if (jobRequest == null) {
                failures.add(new TiesDbRepostResponse.Failure(
                        jobId, String.format("Could not find job with id %s.", jobId)));
            }
            else if (_inProgressJobs.containsJob(jobId)) {
                failures.add(new TiesDbRepostResponse.Failure(
                        jobId, "Job is still running."));
            }
            else {
                try {
                    var job = _jsonUtils.deserialize(jobRequest.getJob(), BatchJob.class);
                    futures.put(jobId, postAssertions(job));
                }
                catch (WfmProcessingException e) {
                    failures.add(new TiesDbRepostResponse.Failure(
                            jobId, e.getMessage()));
                }
            }
        }

        var success = new ArrayList<Long>();
        for (var entry : futures.entrySet()) {
            try {
                entry.getValue().join();
                success.add(entry.getKey());
            }
            catch (CompletionException e) {
                var errorMsg = e.getCause().getMessage();
                failures.add(new TiesDbRepostResponse.Failure(entry.getKey(), errorMsg));
            }
        }
        success.sort(Comparator.naturalOrder());
        failures.sort(Comparator.comparingLong(TiesDbRepostResponse.Failure::jobId));
        return new TiesDbRepostResponse(success, failures);
    }


    private Void reportExceptions(long jobId, Iterable<CompletableFuture<Void>> futures) {
        var joiner = new StringJoiner("\n\n ");
        for (var future : futures) {
            try {
                future.join();
            }
            catch (CompletionException e) {
                joiner.add(e.getCause().getMessage());
            }
        }
        var combinedErrorMsgs = joiner.toString();
        _jobRequestDao.setTiesDbError(jobId, combinedErrorMsgs);
        throw new TiesDbException(combinedErrorMsgs);
    }


    private boolean anyMediaDownloadsFailed(BatchJob job) {
        var downloadFailedCode = IssueCodes.REMOTE_STORAGE_DOWNLOAD.toString();
        boolean anyDownloadFailures = job.getErrors()
                .values()
                .stream()
                .flatMap(Collection::stream)
                .anyMatch(ji -> ji.getCode().equals(downloadFailedCode));
        if (anyDownloadFailures) {
            _jobRequestDao.setTiesDbError(job.getId(), "Media download failed.");
        }
        return anyDownloadFailures;
    }

    private TiesDbInfo.Assertion createActionAssertion(
            BatchJob job,
            BatchJobStatusType jobStatus,
            Instant timeCompleted,
            URI outputObjectLocation,
            String outputObjectSha,
            String algorithm,
            String trackType,
            int trackCount,
            String jobConfigHash) {

        var dataObject = new TiesDbInfo.DataObject(
                job.getPipelineElements().getName(),
                algorithm,
                trackType,
                _propertiesUtil.getExportedJobId(job.getId()),
                outputObjectLocation.toString(),
                outputObjectSha,
                timeCompleted,
                jobStatus,
                _propertiesUtil.getSemanticVersion(),
                _propertiesUtil.getHostName(),
                trackCount,
                jobConfigHash);

        var assertionId = getAssertionId(
                _propertiesUtil.getExportedJobId(job.getId()),
                trackType,
                algorithm,
                timeCompleted);

        return new TiesDbInfo.Assertion(assertionId, trackType, dataObject);
    }


    private String getJobConfigHash(BatchJob job) {
        var props = _aggregateJobPropertiesUtil.getMediaActionProps(
                job.getJobProperties(),
                job.getOverriddenAlgorithmProperties(),
                job.getSystemPropertiesSnapshot(),
                job.getPipelineElements());

        return _jobConfigHasher.getJobConfigHash(
                job.getMedia(), job.getPipelineElements(), props);
    }

    private Triple<String, String, String> getAlgoAndTypeAndTiesDbUrlToUse(
            JobPart jobPart,
            Media parentMedia,
            int trackCount,
            TrackCounter trackCounter,
            Map<Integer, Integer> tasksToMerge) {
        int taskIndexToUse = jobPart.getTaskIndex();
        int actionIndexToUse = jobPart.getActionIndex();

        // Traverse the chain of merged tasks backwards to find the first one in the chain.
        while (tasksToMerge.containsKey(taskIndexToUse)) {
            taskIndexToUse = tasksToMerge.get(taskIndexToUse);
            actionIndexToUse = 0;
        }

        var algo = jobPart.getJob().getPipelineElements()
                .getAlgorithm(taskIndexToUse, actionIndexToUse);

        var action = jobPart.getJob().getPipelineElements()
                .getAction(taskIndexToUse, actionIndexToUse);

        // When actions are merged away, always use the URL for the action at the beginning of the merge chain.
        var tiesDbUrl = _aggregateJobPropertiesUtil.getValue(
                MpfConstants.TIES_DB_URL, jobPart.getJob(), parentMedia, action);

        if (trackCount == 0) {
            return Triple.of(algo.getName(), "NO TRACKS", tiesDbUrl);
        }
        else {
            var trackCountEntry = trackCounter.get(
                    jobPart.getMedia().getId(),
                    taskIndexToUse,
                    actionIndexToUse);
            return Triple.of(algo.getName(), trackCountEntry.getTrackType(), tiesDbUrl);
        }
    }

    private static String getAssertionId(String jobId, String detectionType, String algorithm,
                                         Instant endTime) {
        var digest = DigestUtils.getSha256Digest();
        digest.update(jobId.getBytes(StandardCharsets.UTF_8));
        digest.update(detectionType.getBytes(StandardCharsets.UTF_8));
        digest.update(algorithm.getBytes(StandardCharsets.UTF_8));
        digest.update(String.valueOf(endTime.getEpochSecond()).getBytes(StandardCharsets.UTF_8));
        return Hex.encodeHexString(digest.digest());
    }


    private CompletableFuture<Void> postAssertion(
            TiesDbInfo tiesDbInfo, String mediaSha, boolean useOidc) {
        URI fullUrl;
        try {
            var baseUrl = new URI(tiesDbInfo.tiesDbUrl());
            fullUrl = new URIBuilder(baseUrl)
                    .setPath(baseUrl.getPath() + "/api/db/supplementals")
                    .setParameter("sha256Hash", mediaSha)
                    .build();
        }
        catch (URISyntaxException e) {
            return convertError(
                    tiesDbInfo.tiesDbUrl(),
                    tiesDbInfo.assertion().dataObject().algorithm(),
                    e);
        }

        var assertion = tiesDbInfo.assertion();
        LOG.info("Posting assertion to TiesDb for the {} algorithm. Track type = {}. Track count = {}",
                 assertion.dataObject().algorithm(),
                 assertion.dataObject().outputType(),
                 assertion.dataObject().trackCount());

        try {
            var jsonString = _objectMapper.writeValueAsString(assertion);

            var postRequest = new HttpPost(fullUrl);
            postRequest.addHeader("Content-Type", "application/json");
            RequestConfig requestConfig = RequestConfig.custom()
                    .setSocketTimeout(_propertiesUtil.getHttpCallbackTimeoutMs())
                    .setConnectTimeout(_propertiesUtil.getHttpCallbackTimeoutMs())
                    .build();
            postRequest.setConfig(requestConfig);
            postRequest.setEntity(new StringEntity(jsonString, ContentType.APPLICATION_JSON));
            if (useOidc) {
                _oAuthClientTokenProvider.addToken(postRequest);
            }

            var responseChecker = new ResponseChecker();
            return _httpClientUtils.executeRequest(
                        postRequest,
                        _propertiesUtil.getHttpCallbackRetryCount(),
                        responseChecker::shouldRetry)
                    .thenAccept(responseChecker::checkResponse)
                    .exceptionallyCompose(err -> convertError(
                            fullUrl.toString(),
                            assertion.dataObject().algorithm(),
                            err));
        }
        catch (JsonProcessingException e) {
            return convertError(fullUrl.toString(), assertion.dataObject().algorithm(), e);
        }
    }


    private static CompletableFuture<Void> convertError(String url, String algorithm,
                                                        Throwable error) {
        if (error instanceof CompletionException && error.getCause() != null) {
            error = error.getCause();
        }
        var errorMessage = String.format(
                "Sending HTTP POST to TiesDb (%s) for %s failed due to: %s.",
                url, algorithm, error);
        LOG.error(errorMessage, error);
        return ThreadUtil.failedFuture(new TiesDbException(errorMessage, error));
    }


    private record ParentMediaAlgoTiesDbUrl(
            long parentMediaId, String algorithmName, String tiesDbUrl) {}


    private record TrackTypeCount(String trackType, int count) {
        public TrackTypeCount merge(TrackTypeCount other) {
            if (count == 0) {
                return other;
            }
            else if (other.count() == 0) {
                return this;
            }
            else {
                return new TrackTypeCount(trackType, count + other.count());
            }
        }
    }


    private static class TiesDbException extends RuntimeException {
        public TiesDbException(String message, Throwable cause) {
            super(message, cause);
        }

        public TiesDbException(String message) {
            super(message);
        }
    }


    // The response content stream can only be read from once, but we need to read it twice. Once
    // to check for MISSING_MEDIA_MSG and another time to report the final error. This class
    // will hold on to the most recently tested response.
    private static class ResponseChecker {
        private static final String MISSING_MEDIA_MSG = "could not identify referenced item";
        private String _responseContent;
        private IOException _exception;

        public boolean shouldRetry(HttpResponse resp) {
            try {
                _responseContent = IOUtils.toString(resp.getEntity().getContent(),
                                                    StandardCharsets.UTF_8);
                _exception = null;
                return !_responseContent.contains(MISSING_MEDIA_MSG);
            }
            catch (IOException e) {
                _responseContent = null;
                _exception = e;
                return true;
            }
        }

        public void checkResponse(HttpResponse response) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode >= 200 && statusCode <= 299) {
                return;
            }
            if (_exception != null) {
                throw new IllegalStateException(
                        "TiesDb responded with a non-200 status code of " + statusCode,
                        _exception);
            }
            else {
                throw new IllegalStateException(String.format(
                        "TiesDb responded with a non-200 status code of %s and body: %s",
                        statusCode, _responseContent));
            }
        }
    }
}
