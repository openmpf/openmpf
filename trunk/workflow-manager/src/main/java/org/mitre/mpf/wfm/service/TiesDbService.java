/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.transients.TrackCounter;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
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
import java.util.Objects;
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

    @Inject
    TiesDbService(PropertiesUtil propertiesUtil,
                  AggregateJobPropertiesUtil aggregateJobPropertiesUtil,
                  ObjectMapper objectMapper,
                  CallbackUtils callbackUtils) {
        _propertiesUtil = propertiesUtil;
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
        _objectMapper = objectMapper;
        _callbackUtils = callbackUtils;
    }


    public CompletableFuture<Void> addAssertions(BatchJob job,
                                                 BatchJobStatusType jobStatus,
                                                 Instant timeCompleted,
                                                 URI outputObjectLocation,
                                                 String outputObjectSha,
                                                 TrackCounter trackCounter) {
        var futures = new ArrayList<CompletableFuture<Void>>();

        var parentTrackCounter = new TrackCounter();
        var derivativeTrackCounter = new TrackCounter();

        // Map <media id, algorithm name, detection type> to <TiesDb URL, track count>.
        // Each entry tallies parent and derivative media tracks.
        Map<Triple<Long, String, String>, Pair<String, Integer>> finalTrackCounts = new HashMap();

        // Create one entry for each task merge chain. Don't include suppressed tasks.
        for (var media : job.getMedia()) {
            boolean outputLastTaskOnly
                    = _aggregateJobPropertiesUtil.isOutputLastTaskOnly(media, job);
            var tasksToMerge = _aggregateJobPropertiesUtil.getTasksToMerge(media, job);

            for (var jobPart : JobPartsIter.of(job, media)) {
                if (outputLastTaskOnly
                        && jobPart.getTaskIndex() != job.getPipelineElements().getTaskCount() - 1) { // suppressed
                    continue;
                }

                if (tasksToMerge.values().contains(jobPart.getTaskIndex())) { // task will be merged away
                    continue;
                }

                var trackCountEntry = trackCounter.get(jobPart);
                if (trackCountEntry == null) { // this part of the job was not performed on this media
                    continue;
                }

                var algoAndDetectionType = getAlgoAndTypeToUse(
                        jobPart, trackCountEntry.getCount(), trackCounter, tasksToMerge);

                var mergedTrackCounter = (media.isDerivative() ? derivativeTrackCounter : parentTrackCounter);

                mergedTrackCounter.set(jobPart, algoAndDetectionType.getRight(), trackCountEntry.getCount());
            }
        }

        // Create parent track counts.
        for (var entry : parentTrackCounter.values()) {
            Media media = job.getMedia(entry.getMediaId());
            Action action = job.getPipelineElements().getAction(entry.getTaskIdx(), entry.getActionIdx());

            var tiesDbUrl = _aggregateJobPropertiesUtil.getValue("TIES_DB_URL", job, media, action);
            var tiesDbUrlToUse = (StringUtils.isNotBlank(tiesDbUrl) ? tiesDbUrl : null);

            Triple key = Triple.of(media.getId(), action.getAlgorithm(), entry.getTrackType());

            finalTrackCounts.put(key, ImmutablePair.of(tiesDbUrlToUse, entry.getCount()));
        }

        // Add derivative media track counts to parent counts.
        for (var entry : derivativeTrackCounter.values()) {
            Media media = job.getMedia(entry.getMediaId());
            Action action = job.getPipelineElements().getAction(entry.getTaskIdx(), entry.getActionIdx());

            var tiesDbUrl = _aggregateJobPropertiesUtil.getValue("TIES_DB_URL", job, media, action);
            var derivativeTiesDbUrl = (StringUtils.isNotBlank(tiesDbUrl) ? tiesDbUrl : null);

            Triple key = Triple.of(media.getParentId(), action.getAlgorithm(), entry.getTrackType());
            var value = finalTrackCounts.get(key);

            if (value == null) {
                finalTrackCounts.put(key, ImmutablePair.of(derivativeTiesDbUrl, entry.getCount()));
                continue;
            }

            String tiesDbUrlToUse = value.getLeft(); // parent
            if (tiesDbUrlToUse == null && derivativeTiesDbUrl != null) {
                tiesDbUrlToUse = derivativeTiesDbUrl;
                LOG.warn("Parent media {} did not specify a valid TiesDb URL. " +
                         "Using derivative media {} TiesDb URL of {} for the {} algorithm.",
                         media.getParentId(), media.getId(), tiesDbUrlToUse, key.getMiddle());
            } else if (tiesDbUrlToUse != null && derivativeTiesDbUrl != null) {
                LOG.warn("Using parent media {} TiesDb URL of {} instead of derivative media {} TiesDb URL of {} " +
                         "for the {} algorithm.",
                         media.getParentId(), tiesDbUrlToUse, media.getId(), derivativeTiesDbUrl, key.getMiddle());
            }

            int newCount = finalTrackCounts.get(key).getRight() + entry.getCount(); // accumulate
            finalTrackCounts.put(key, ImmutablePair.of(tiesDbUrlToUse, newCount));
        }

        // Post to TiesDb.
        for (var entry : finalTrackCounts.entrySet()) {
            String tiesDbUrl = entry.getValue().getLeft();
            if (StringUtils.isNotBlank(tiesDbUrl)) {
                futures.add(addActionAssertion(
                            job,
                            jobStatus,
                            timeCompleted,
                            outputObjectLocation,
                            outputObjectSha,
                            job.getMedia(entry.getKey().getLeft()),
                            tiesDbUrl,
                            entry.getKey().getMiddle(),
                            entry.getKey().getRight(),
                            entry.getValue().getRight()));
            }
        }

        return ThreadUtil.allOf(futures);
    }


    private static Pair<String, String> getAlgoAndTypeToUse(
            JobPart jobPart,
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

        if (trackCount == 0) {
            return Pair.of(algo.getName(), "NO TRACKS");
        }
        else {
            var trackCountEntry = trackCounter.get(
                    jobPart.getMedia().getId(),
                    taskIndexToUse,
                    actionIndexToUse);
            return Pair.of(algo.getName(), trackCountEntry.getTrackType());
        }
    }


    private CompletableFuture<Void> addActionAssertion(
            BatchJob job,
            BatchJobStatusType jobStatus,
            Instant timeCompleted,
            URI outputObjectLocation,
            String outputObjectSha,
            Media media,
            String tiesDbUrl,
            String algorithm,
            String trackType,
            int trackCount) {

        var dataObject = Map.ofEntries(
                Map.entry("pipeline", job.getPipelineElements().getName()),
                Map.entry("algorithm", algorithm),
                Map.entry("outputType", trackType),
                Map.entry("jobId", job.getId()),
                Map.entry("outputUri", outputObjectLocation.toString()),
                Map.entry("sha256OutputHash", outputObjectSha),
                Map.entry("processDate", timeCompleted),
                Map.entry("jobStatus", jobStatus),
                Map.entry("systemVersion", _propertiesUtil.getSemanticVersion()),
                Map.entry("systemHostname", getHostName()),
                Map.entry("trackCount", trackCount)
        );

        var assertionId = getAssertionId(
                job.getId(),
                trackType,
                algorithm,
                timeCompleted);

        var assertion = Map.of(
                "assertionId", assertionId,
                "informationType", "OpenMPF " + trackType,
                "securityTag", "UNCLASSIFIED",
                "system", "OpenMPF",
                "dataObject", dataObject);

        LOG.info("[Job {}] Posting assertion to TiesDb for the {} algorithm.",
                 job.getId(), algorithm);

        return postAssertion(job.getId(),
                             algorithm,
                             tiesDbUrl,
                             media.getSha256(),
                             assertion);
    }


    private static String getAssertionId(long jobId, String detectionType, String algorithm,
                                         Instant endTime) {
        var digest = DigestUtils.getSha256Digest();
        digest.update(String.valueOf(jobId).getBytes(StandardCharsets.UTF_8));
        digest.update(detectionType.getBytes(StandardCharsets.UTF_8));
        digest.update(algorithm.getBytes(StandardCharsets.UTF_8));
        digest.update(String.valueOf(endTime.getEpochSecond()).getBytes(StandardCharsets.UTF_8));
        return Hex.encodeHexString(digest.digest());
    }


    private static String getHostName() {
        return Objects.requireNonNullElseGet(
                System.getenv("NODE_HOSTNAME"),
                () -> System.getenv("HOSTNAME"));
    }


    private CompletableFuture<Void> postAssertion(long jobId,
                                                  String algorithm,
                                                  String tiesDbUrl,
                                                  String mediaSha,
                                                  Map<String, Object> assertions) {
        URI fullUrl;
        try {
            var baseUrl = new URI(tiesDbUrl);
            fullUrl = new URIBuilder(tiesDbUrl)
                    .setPath(baseUrl.getPath() + "/api/db/supplementals")
                    .setParameter("sha256Hash", mediaSha)
                    .build();
        }
        catch (URISyntaxException e) {
            handleHttpError(jobId, algorithm, tiesDbUrl, e);
            return ThreadUtil.completedFuture(null);
        }

        try {
            var jsonString = _objectMapper.writeValueAsString(assertions);

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
                    .thenApply(TiesDbService::checkResponse)
                    .exceptionally(err -> handleHttpError(jobId, algorithm, fullUrl.toString(),
                                                          err));
        }
        catch (JsonProcessingException e) {
            handleHttpError(jobId, algorithm, fullUrl.toString(), e);
            return ThreadUtil.completedFuture(null);
        }
    }


    private static Void checkResponse(HttpResponse response) {
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode >= 200 && statusCode <= 299) {
            return null;
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


    private static Void handleHttpError(long jobId, String url, String algorithm, Throwable error) {
        if (error instanceof CompletionException && error.getCause() != null) {
            error = error.getCause();
        }
        var warningMessage = String.format(
                "[Job %s] Sending HTTP POST to TiesDb (%s) for %s failed due to: %s",
                jobId, url, algorithm, error);
        LOG.warn(warningMessage, error);
        return null;
    }
}
