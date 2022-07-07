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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

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

    private final CallbackUtils _callbackUtils;

    private final JobRequestDao _jobRequestDao;

    private final InProgressBatchJobsService _inProgressJobs;

    @Inject
    TiesDbService(PropertiesUtil propertiesUtil,
                  AggregateJobPropertiesUtil aggregateJobPropertiesUtil,
                  ObjectMapper objectMapper,
                  CallbackUtils callbackUtils,
                  JobRequestDao jobRequestDao,
                  InProgressBatchJobsService inProgressJobs) {
        _propertiesUtil = propertiesUtil;
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
        _objectMapper = objectMapper;
        _callbackUtils = callbackUtils;
        _jobRequestDao = jobRequestDao;
        _inProgressJobs = inProgressJobs;
    }


    public void storeAssertions(BatchJob job,
                                BatchJobStatusType jobStatus,
                                Instant timeCompleted,
                                URI outputObjectLocation,
                                String outputObjectSha,
                                TrackCounter trackCounter) {
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
                    entry.getValue().count());
            var tiesDbInfo = new TiesDbInfo(parentMediaAlgoUrl.tiesDbUrl(), assertion);
            _inProgressJobs.addTiesDbInfo(
                    job.getId(), parentMediaAlgoUrl.parentMediaId(), tiesDbInfo);
        }
    }


    public CompletableFuture<Void> postAssertions(BatchJob job) {
        var futures = new ArrayList<CompletableFuture<Void>>();
        for (var media : job.getMedia()) {
            for (var tiesDbInfo : media.getTiesDbInfo()) {
                futures.add(postAssertion(tiesDbInfo, media.getSha256()));
            }
        }

        if (futures.isEmpty()) {
            return ThreadUtil.completedFuture(null);
        }

        return ThreadUtil.allOf(futures)
                .thenRun(() -> _jobRequestDao.setTiesDbSuccessful(job.getId()))
                .exceptionally(e -> reportExceptions(job.getId(), futures));
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
        _jobRequestDao.setTiesDbError(jobId, joiner.toString());
        return null;
    }


    private TiesDbInfo.Assertion createActionAssertion(
            BatchJob job,
            BatchJobStatusType jobStatus,
            Instant timeCompleted,
            URI outputObjectLocation,
            String outputObjectSha,
            String algorithm,
            String trackType,
            int trackCount) {

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
                trackCount);

        var assertionId = getAssertionId(
                _propertiesUtil.getExportedJobId(job.getId()),
                trackType,
                algorithm,
                timeCompleted);

        return new TiesDbInfo.Assertion(assertionId, trackType, dataObject);
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




    private CompletableFuture<Void> postAssertion(TiesDbInfo tiesDbInfo, String mediaSha) {
        URI fullUrl;
        try {
            var baseUrl = new URI(tiesDbInfo.tiesDbUrl());
            fullUrl = new URIBuilder(baseUrl)
                    .setPath(baseUrl.getPath() + "/api/db/supplementals")
                    .setParameter("sha256Hash", mediaSha)
                    .build();
        }
        catch (URISyntaxException e) {
            return ThreadUtil.failedFuture(convertError(
                    tiesDbInfo.tiesDbUrl(),
                    tiesDbInfo.assertion().dataObject().algorithm(),
                    e));
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

            return _callbackUtils.executeRequest(postRequest,
                                                 _propertiesUtil.getHttpCallbackRetryCount())
                    .thenAccept(TiesDbService::checkResponse)
                    .handle((x, err) -> {
                        if (err != null) {
                            throw convertError(
                                    fullUrl.toString(),
                                    assertion.dataObject().algorithm(),
                                    err);
                        }
                        return null;
                    });
        }
        catch (JsonProcessingException e) {
            return ThreadUtil.failedFuture(convertError(
                    fullUrl.toString(),
                    assertion.dataObject().algorithm(),
                    e));
        }
    }


    private static void checkResponse(HttpResponse response) {
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode >= 200 && statusCode <= 299) {
            return;
        }
        try {
            var responseContent = IOUtils.toString(response.getEntity().getContent(),
                                                   StandardCharsets.UTF_8);
            throw new IllegalStateException(String.format(
                    "TiesDb responded with a non-200 status code of %s and body: %s",
                    statusCode, responseContent));
        }
        catch (IOException e) {
            throw new IllegalStateException(
                    "TiesDb responded with a non-200 status code of " + statusCode, e);
        }
    }


    private static TiesDbException convertError(String url, String algorithm, Throwable error) {
        if (error instanceof CompletionException && error.getCause() != null) {
            error = error.getCause();
        }
        var errorMessage = String.format(
                "Sending HTTP POST to TiesDb (%s) for %s failed due to: %s.",
                url, algorithm, error);
        LOG.error(errorMessage, error);
        return new TiesDbException(errorMessage, error);
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
    }
}
