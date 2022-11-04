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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.camel.Exchange;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.mitre.mpf.interop.util.TimeUtils;
import org.mitre.mpf.rest.api.JobCreationRequest;
import org.mitre.mpf.rest.api.TiesDbCheckStatus;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.camel.WfmProcessor;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.SystemPropertiesSnapshot;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.HttpClientUtils;
import org.mitre.mpf.wfm.util.MediaActionProps;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Streams;

@Component(TiesDbBeforeJobCheckServiceImpl.REF)
public class TiesDbBeforeJobCheckServiceImpl
        extends WfmProcessor implements TiesDbBeforeJobCheckService {

    public static final String REF = "tiesDbBeforeJobCheckServiceImpl";

    private final PropertiesUtil _propertiesUtil;

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;

    private final JobConfigHasher _jobConfigHasher;

    private final HttpClientUtils _httpClientUtils;

    private final ObjectMapper _objectMapper;

    private final InProgressBatchJobsService _inProgressJobs;

    @Inject
    public TiesDbBeforeJobCheckServiceImpl(
            PropertiesUtil propertiesUtil,
            AggregateJobPropertiesUtil aggregateJobPropertiesUtil,
            JobConfigHasher jobConfigHasher,
            HttpClientUtils httpClientUtils,
            ObjectMapper objectMapper,
            InProgressBatchJobsService inProgressJobs) {
        _propertiesUtil = propertiesUtil;
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
        _jobConfigHasher = jobConfigHasher;
        _httpClientUtils = httpClientUtils;
        _objectMapper = objectMapper;
        _inProgressJobs = inProgressJobs;
    }


    public TiesDbCheckResult checkTiesDbBeforeJob(
            JobCreationRequest jobCreationRequest,
            SystemPropertiesSnapshot systemPropertiesSnapshot,
            Collection<Media> media,
            JobPipelineElements jobPipelineElements) {
        return checkIfJobInTiesDb(
                jobCreationRequest.getJobProperties(),
                jobCreationRequest.getAlgorithmProperties(),
                systemPropertiesSnapshot,
                media,
                jobPipelineElements);
    }

    @Override
    public void wfmProcess(Exchange exchange) {
        checkTiesDbAfterMediaInspection(exchange);
    }

    private void checkTiesDbAfterMediaInspection(Exchange exchange) {
        var job = _inProgressJobs.getJob(
                exchange.getIn().getHeader(MpfHeaders.JOB_ID, long.class));
        if (!job.shouldCheckTiesDbAfterMediaInspection()) {
            return;
        }

        var checkResult = checkIfJobInTiesDb(job.getJobProperties(),
                           job.getOverriddenAlgorithmProperties(),
                           job.getSystemPropertiesSnapshot(),
                           job.getMedia(),
                           job.getPipelineElements());
        checkResult.checkInfo().ifPresent(ci -> {
            exchange.getOut().setHeader(MpfHeaders.JOB_COMPLETE, true);
            exchange.getOut().setHeader(
                    MpfHeaders.OUTPUT_OBJECT_URI_FROM_TIES_DB,
                    ci.outputObjectUri());
        });
    }


    private TiesDbCheckResult checkIfJobInTiesDb(
            Map<String, String> jobProperties,
            Map<String, ? extends Map<String, String>> algorithmProperties,
            SystemPropertiesSnapshot systemPropertiesSnapshot,
            Collection<Media> media,
            JobPipelineElements jobPipelineElements) {

        var props = _aggregateJobPropertiesUtil.getMediaActionProps(
                jobProperties,
                algorithmProperties,
                systemPropertiesSnapshot,
                jobPipelineElements);

        if (shouldSkipTiesDbCheck(media, jobPipelineElements.getAllActions(), props)) {
            return TiesDbCheckResult.noResult(TiesDbCheckStatus.NOT_REQUESTED);
        }

        var allMediaHaveHashes = media.stream()
                .allMatch(m -> m.getHash().isPresent());

        if (!allMediaHaveHashes) {
            return TiesDbCheckResult.noResult(TiesDbCheckStatus.MEDIA_HASHES_ABSENT);
        }

        var mediaHashToBaseUris = getBaseTiesDbUris(
                media, jobPipelineElements.getAllActions(), props);

        if (mediaHashToBaseUris.isEmpty()) {
            return TiesDbCheckResult.noResult(TiesDbCheckStatus.NO_TIES_DB_URL_IN_JOB);
        }


        var jobHash = _jobConfigHasher.getJobConfigHash(
                media,
                jobPipelineElements,
                props);

        var lastException = new MutableObject<Throwable>(null);
        var optCheckInfo = getCheckInfo(mediaHashToBaseUris, jobHash, lastException);
        if (optCheckInfo.isPresent()) {
            return new TiesDbCheckResult(
                    TiesDbCheckStatus.FOUND_MATCH,
                    optCheckInfo);
        }
        else if (lastException.getValue() == null) {
            return TiesDbCheckResult.noResult(TiesDbCheckStatus.NO_MATCH);
        }
        else if (lastException.getValue() instanceof RuntimeException ex) {
            throw ex;
        }
        else {
            throw new IllegalStateException(lastException.getValue());
        }
    }


    private static boolean shouldSkipTiesDbCheck(Iterable<Media> media,
                                                 Collection<Action> actions,
                                                 MediaActionProps props) {
        for (var medium : media) {
            for (var action : actions) {
                var skipProp = props.get(MpfConstants.SKIP_TIES_DB_CHECK, medium, action);
                if (Boolean.parseBoolean(skipProp)) {
                    return true;
                }
            }
        }
        return false;
    }


    private static Multimap<String, String> getBaseTiesDbUris(
            Iterable<Media> media,
            Collection<Action> actions,
            MediaActionProps props) {
        var mediaHashToBaseUris = HashMultimap.<String, String>create();
        for (var medium : media) {
            var hash = medium.getHash().orElseThrow();
            actions.stream()
                    .map(a -> props.get(MpfConstants.TIES_DB_URL, medium, a))
                    .filter(u -> u != null && !u.isBlank())
                    .forEach(u -> mediaHashToBaseUris.put(hash, u));
        }
        return mediaHashToBaseUris;
    }


    private Optional<TiesDbCheckResult.CheckInfo> getCheckInfo(
            Multimap<String, String> mediaHashToBaseUris,
            String expectedJobHash,
            MutableObject<Throwable> lastException) {
        var supplementalUris = buildSupplementalUris(mediaHashToBaseUris, lastException);
        var futures = Stream.<CompletableFuture<HttpResponse>>builder();
        for (var supplementalUri : supplementalUris) {
            var future = _httpClientUtils.executeRequest(
                new HttpGet(supplementalUri),
                _propertiesUtil.getHttpCallbackRetryCount()
            ).thenApply(this::checkResponse);
            futures.add(future);
        }

        return futures.build()
            .flatMap(f -> parseResponse(f, lastException))
            .map(jn -> convertJsonToCheckInfo(jn, expectedJobHash, lastException))
            .flatMap(Optional::stream)
            .max(COMPLETE_THEN_DATE);
    }


    private static Set<URI> buildSupplementalUris(
            Multimap<String, String> tiesDbBaseUris,
            Mutable<Throwable> exception) {
        var supplementalUris = new HashSet<URI>();
        for (var entry : tiesDbBaseUris.entries()) {
            var mediaHash = entry.getKey();
            var baseUriStr = entry.getValue();
            try {
                var baseUri = new URI(baseUriStr);
                var fullUri = new URIBuilder(baseUriStr)
                        .setPath(baseUri.getPath() + "/api/db/supplementals")
                        .setParameter("sha256Hash", mediaHash)
                        .build();
                supplementalUris.add(fullUri);
            }
            catch (URISyntaxException e) {
                exception.setValue(new WfmProcessingException(
                        "The \"%s\" property contained \"%s\" which isn't a valid URI."
                                .formatted(MpfConstants.TIES_DB_URL, e.getInput()),
                        e));
            }
        }
        return supplementalUris;
    }


    private static final Comparator<TiesDbCheckResult.CheckInfo> COMPLETE_THEN_DATE = Comparator
        .comparing(
            (TiesDbCheckResult.CheckInfo ci) -> ci.jobStatus() == BatchJobStatusType.COMPLETE)
        .thenComparing(TiesDbCheckResult.CheckInfo::processDate);



    private HttpResponse checkResponse(HttpResponse response) {
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode >= 200 && statusCode <= 299) {
            return response;
        }

        try {
            var body = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            throw new IllegalStateException(
                "TiesDb responded with a non-200 status code of %s and body: %s"
                    .formatted(statusCode, body));
        }
        catch (IOException e) {
            throw new IllegalStateException(
                "TiesDb responded with a non-200 status code of %s.".formatted(statusCode), e);
        }
    }

    private Stream<JsonNode> parseResponse(
            CompletableFuture<HttpResponse> responseFuture,
            MutableObject<Throwable> exception) {
        try (var is = responseFuture.join().getEntity().getContent()) {
            return Streams.stream(_objectMapper.readTree(is));
        }
        catch (CompletionException e) {
            exception.setValue(e.getCause());
        }
        catch (IOException e) {
            exception.setValue(e);
        }
        return Stream.empty();
    }


    private Optional<TiesDbCheckResult.CheckInfo> convertJsonToCheckInfo(
            JsonNode jsonEntry,
            String expectedJobHash,
            MutableObject<Throwable> exception) {
        var jobHash = jsonEntry.findPath("dataObject")
                .findPath("jobConfigHash").asText("");
        if (!expectedJobHash.equals(jobHash)) {
            return Optional.empty();
        }
        var outputUriStr = jsonEntry.findPath("outputUri").asText("");
        if (outputUriStr.isBlank()) {
            return Optional.empty();
        }
        URI outputUri;
        try {
            outputUri = new URI(outputUriStr);
        }
        catch (URISyntaxException e) {
            exception.setValue(e);
            return Optional.empty();
        }

        Instant processDate;
        try {
            processDate = TimeUtils.toInstant(
                jsonEntry.findPath("processDate").asText(""));
        }
        catch (DateTimeParseException e) {
            processDate = Instant.MIN;
        }

        BatchJobStatusType status;
        try {
            status = BatchJobStatusType.valueOf(jsonEntry.findPath("jobStatus").asText(""));
        }
        catch (IllegalArgumentException e) {
            exception.setValue(e);
            return Optional.empty();
        }

        return Optional.of(new TiesDbCheckResult.CheckInfo(
                outputUri,
                status,
                processDate));
    }
}
