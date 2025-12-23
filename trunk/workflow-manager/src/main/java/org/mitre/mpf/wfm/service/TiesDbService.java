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


package org.mitre.mpf.wfm.service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.SortedSet;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.mitre.mpf.interop.JsonTiming;
import org.mitre.mpf.mvc.security.OutgoingRequestTokenService;
import org.mitre.mpf.rest.api.TiesDbRepostResponse;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.access.JobRequestDao;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.TiesDbInfo;
import org.mitre.mpf.wfm.data.entities.transients.TrackCounter;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.AuditEventLogger;
import org.mitre.mpf.wfm.util.HttpClientUtils;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.LogAuditEventRecord;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSortedSet;

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

    private final OutgoingRequestTokenService _clientTokenProvider;

    private final JobRequestDao _jobRequestDao;

    private final InProgressBatchJobsService _inProgressJobs;

    private final JobConfigHasher _jobConfigHasher;

    private final TaskMergingManager _taskMergingManager;

    private final AuditEventLogger _auditEventLogger;


    @Inject
    TiesDbService(PropertiesUtil propertiesUtil,
                  AggregateJobPropertiesUtil aggregateJobPropertiesUtil,
                  ObjectMapper objectMapper,
                  JsonUtils jsonUtils,
                  HttpClientUtils httpClientUtils,
                  OutgoingRequestTokenService clientTokenProvider,
                  JobRequestDao jobRequestDao,
                  InProgressBatchJobsService inProgressJobs,
                  JobConfigHasher jobConfigHasher,
                  TaskMergingManager taskMergingManager,
                  AuditEventLogger auditEventLogger) {
        _propertiesUtil = propertiesUtil;
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
        _objectMapper = objectMapper;
        _httpClientUtils = httpClientUtils;
        _clientTokenProvider = clientTokenProvider;
        _jsonUtils = jsonUtils;
        _jobRequestDao = jobRequestDao;
        _inProgressJobs = inProgressJobs;
        _jobConfigHasher = jobConfigHasher;
        _taskMergingManager = taskMergingManager;
        _auditEventLogger = auditEventLogger;
    }

    public void prepareAssertions(
            BatchJob job,
            BatchJobStatusType jobStatus,
            Instant timeCompleted,
            URI outputObjectLocation,
            String outputObjectSha,
            TrackCounter trackCounter,
            JsonTiming timing) {
        if (anyMediaDownloadsFailed(job)) {
            return;
        }

        for (var media : job.getMedia()) {
            if (media.isDerivative()) {
                continue;
            }
            var tiesDbUrl = _aggregateJobPropertiesUtil.getValue(
                    MpfConstants.TIES_DB_URL, job, media);
            if (tiesDbUrl == null || tiesDbUrl.isBlank()) {
                continue;
            }

            var dataObject = new TiesDbInfo.DataObject(
                    job.getPipelineElements().getName(),
                    getTrackTypes(job, media),
                    _propertiesUtil.getExportedJobId(job.getId()),
                    outputObjectLocation.toString(),
                    outputObjectSha,
                    timeCompleted,
                    jobStatus,
                    _propertiesUtil.getSemanticVersion(),
                    _propertiesUtil.getHostName(),
                    trackCounter.get(media),
                    _jobConfigHasher.getJobConfigHash(job),
                    timing);
            var assertion = new TiesDbInfo.Assertion(UUID.randomUUID().toString(), dataObject);
            var tiesDbInfo = new TiesDbInfo(tiesDbUrl, assertion);
            _inProgressJobs.addTiesDbInfo(job.getId(), media.getId(), tiesDbInfo);
        }
    }


    public CompletableFuture<Void> postAssertions(BatchJob job) {
        var futures = job.getMedia()
                .stream()
                .filter(m -> m.getTiesDbInfo().isPresent())
                .map(m -> postAssertion(job, m))
                .toList();
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
        Throwable exception = null;
        int numExceptions = 0;
        for (var future : futures) {
            try {
                future.join();
            }
            catch (CompletionException e) {
                exception = e.getCause();
                joiner.add(exception.getMessage());
                numExceptions++;
            }
        }

        var combinedErrorMsgs = joiner.toString();
        _jobRequestDao.setTiesDbError(jobId, combinedErrorMsgs);
        if (numExceptions == 1) {
            Throwables.throwIfUnchecked(exception);
            throw new TiesDbException(combinedErrorMsgs, exception);
        }
        else {
            throw new TiesDbException(combinedErrorMsgs);
        }
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


    private SortedSet<String> getTrackTypes(BatchJob job, Media media) {
        var pipelineElements = job.getPipelineElements();
        Stream<Task> tasks;
        if (_aggregateJobPropertiesUtil.isOutputLastTaskOnly(media, job)) {
            int lastDetectionTaskIdx = pipelineElements.getLastDetectionTaskIdx();
            var endingTaskIdxs = IntStream.of(
                    lastDetectionTaskIdx, pipelineElements.getTaskCount() - 1);

            var lastDetectionTask = pipelineElements.getTask(lastDetectionTaskIdx);
            // Include the track types that were merged away.
            var mergedTaskIdxs = IntStream.range(0, lastDetectionTask.actions().size())
                    .flatMap(ai -> _taskMergingManager.getTransitiveMergeTargets(
                            job, media, lastDetectionTaskIdx, ai));

            tasks = IntStream.concat(endingTaskIdxs, mergedTaskIdxs)
                    .distinct()
                    .mapToObj(pipelineElements::getTask);
        }
        else {
            tasks = pipelineElements.getTaskStreamInOrder();
        }
        return tasks
                .flatMap(pipelineElements::getActionStreamInOrder)
                .map(a -> pipelineElements.getAlgorithm(a.algorithm()).trackType())
                .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));
    }



    private CompletableFuture<Void> postAssertion(BatchJob job, Media media) {
        var tiesDbInfo = media.getTiesDbInfo().orElseThrow();
        URI fullUrl;
        try {
            var baseUrl = new URI(tiesDbInfo.tiesDbUrl());
            fullUrl = new URIBuilder(baseUrl)
                    .setPath(baseUrl.getPath() + "/api/db/supplementals")
                    .setParameter("sha256Hash", media.getLinkedHash().orElseThrow())
                    .build();
        }
        catch (URISyntaxException e) {
            return convertError(
                    tiesDbInfo.tiesDbUrl(),
                    e);
        }

        var assertion = tiesDbInfo.assertion();
        LOG.info("Posting assertion to TiesDb ({}). Track count = {}. Track types = {}.",
                fullUrl, assertion.dataObject().trackCount(), assertion.dataObject().outputTypes());

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
            _clientTokenProvider.addTokenToTiesDbRequest(job, media, postRequest);

            var responseChecker = new ResponseChecker();
            return _httpClientUtils.executeRequest(
                        postRequest,
                        _propertiesUtil.getHttpCallbackRetryCount(),
                        responseChecker::shouldRetry)
                    .thenAccept(response -> {
                        _auditEventLogger.createEvent()
                            .withSecurityTag()
                            .withEventId(LogAuditEventRecord.EventId.TIES_DB_POST.success)
                            .withUri(fullUrl.toString())
                            .allowed(LogAuditEventRecord.EventId.TIES_DB_POST.message + " succeeded");
                        responseChecker.checkResponse(response);
                    })
                    .exceptionallyCompose(err -> {
                        _auditEventLogger.createEvent()
                            .withSecurityTag()
                            .withEventId(LogAuditEventRecord.EventId.TIES_DB_POST.fail)
                            .withUri(fullUrl.toString())
                            .error(LogAuditEventRecord.EventId.TIES_DB_POST.message + " failed: %s", err.getMessage());
                        return convertError(fullUrl.toString(), err);
                    });
        }
        catch (Exception e) {
            return convertError(fullUrl.toString(), e);
        }
    }


    private static CompletableFuture<Void> convertError(String url, Throwable error) {
        if (error instanceof CompletionException && error.getCause() != null) {
            error = error.getCause();
        }
        var errorMessage = String.format(
                "Sending HTTP POST to TiesDb (%s) failed due to: %s.",
                url, error);
        LOG.error(errorMessage, error);
        return ThreadUtil.failedFuture(new TiesDbException(errorMessage, error));
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
