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

import static java.util.stream.Collectors.toMap;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.camel.Exchange;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.mitre.mpf.interop.JsonActionOutputObject;
import org.mitre.mpf.interop.JsonDetectionOutputObject;
import org.mitre.mpf.interop.JsonMarkupOutputObject;
import org.mitre.mpf.interop.JsonMediaOutputObject;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.interop.JsonTrackOutputObject;
import org.mitre.mpf.interop.util.TimeUtils;
import org.mitre.mpf.rest.api.JobCreationRequest;
import org.mitre.mpf.rest.api.TiesDbCheckStatus;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.camel.WfmProcessor;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.SystemPropertiesSnapshot;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.HttpClientUtils;
import org.mitre.mpf.wfm.util.MediaActionProps;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Streams;

@Component(TiesDbBeforeJobCheckServiceImpl.REF)
public class TiesDbBeforeJobCheckServiceImpl
        extends WfmProcessor implements TiesDbBeforeJobCheckService {

    public static final String REF = "tiesDbBeforeJobCheckServiceImpl";

    private static final Logger LOG
            = LoggerFactory.getLogger(TiesDbBeforeJobCheckServiceImpl.class);

    private final PropertiesUtil _propertiesUtil;

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;

    private final JobConfigHasher _jobConfigHasher;

    private final HttpClientUtils _httpClientUtils;

    private final ObjectMapper _objectMapper;

    private final InProgressBatchJobsService _inProgressJobs;

    private final S3StorageBackend _s3StorageBackend;

    @Inject
    public TiesDbBeforeJobCheckServiceImpl(
            PropertiesUtil propertiesUtil,
            AggregateJobPropertiesUtil aggregateJobPropertiesUtil,
            JobConfigHasher jobConfigHasher,
            HttpClientUtils httpClientUtils,
            ObjectMapper objectMapper,
            InProgressBatchJobsService inProgressJobs,
            S3StorageBackend s3StorageBackend) {
        _propertiesUtil = propertiesUtil;
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
        _jobConfigHasher = jobConfigHasher;
        _httpClientUtils = httpClientUtils;
        _objectMapper = objectMapper;
        _inProgressJobs = inProgressJobs;
        _s3StorageBackend = s3StorageBackend;
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
        long jobId = exchange.getIn().getHeader(MpfHeaders.JOB_ID, long.class);
        try {
            checkTiesDbAfterMediaInspection(jobId, exchange);
        }
        catch (Exception e) {
            LOG.error("TiesDb check failed due to: " + e, e);
            _inProgressJobs.addFatalError(
                    jobId, IssueCodes.TIES_DB_BEFORE_JOB_CHECK, e.getMessage());
        }
    }

    private void checkTiesDbAfterMediaInspection(long jobId, Exchange exchange) {
        var job = _inProgressJobs.getJob(jobId);
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
                    ci.outputObjectUri().toString());
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
        if (!hasTiesDbUrl(media, jobPipelineElements.getAllActions(), props)) {
            return TiesDbCheckResult.noResult(TiesDbCheckStatus.NO_TIES_DB_URL_IN_JOB);
        }

        var allMediaHaveHashes = media.stream()
                .allMatch(m -> m.getLinkedHash().isPresent());
        if (!allMediaHaveHashes) {
            return TiesDbCheckResult.noResult(TiesDbCheckStatus.MEDIA_HASHES_ABSENT);
        }

        var allMediaHaveMimeTypes = media.stream()
                .allMatch(m -> m.getMimeType().isPresent());
        if (!allMediaHaveMimeTypes) {
            return TiesDbCheckResult.noResult(TiesDbCheckStatus.MEDIA_MIME_TYPES_ABSENT);
        }

        var mediaHashToBaseUris = getBaseTiesDbUris(
                media, jobPipelineElements.getAllActions(), props);

        var jobHash = _jobConfigHasher.getJobConfigHash(
                media,
                jobPipelineElements,
                props);

        // There may be multiple matching supplementals in TiesDb, so we don't want to report
        // an error if there was a problem getting one supplemental, but we were able to get a
        // different matching supplemental. Use an AtomicReference because some errors can occur
        // on threads belonging to the HTTP client.
        var lastException = new AtomicReference<Throwable>(null);
        var optCheckInfo = getCheckInfo(mediaHashToBaseUris, jobHash, lastException);
        if (optCheckInfo.isPresent()) {
            return new TiesDbCheckResult(
                    TiesDbCheckStatus.FOUND_MATCH,
                    optCheckInfo);
        }
        else if (lastException.get() == null) {
            return TiesDbCheckResult.noResult(TiesDbCheckStatus.NO_MATCH);
        }
        else if (lastException.get() instanceof RuntimeException ex) {
            throw ex;
        }
        else {
            throw new IllegalStateException(lastException.get());
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

    private static boolean hasTiesDbUrl(
            Iterable<Media> media, Collection<Action> actions, MediaActionProps props) {
        for (var medium : media) {
            for (var action : actions) {
                var urlProp = props.get(MpfConstants.TIES_DB_URL, medium, action);
                if (urlProp != null && !urlProp.isBlank()) {
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
            var hash = medium.getLinkedHash().orElseThrow();
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
            AtomicReference<Throwable> lastException) {
        // Need a temporary list so that all of the futures are created before we start calling
        // join on any of the futures.
        var futures = buildSupplementalUris(mediaHashToBaseUris, lastException)
            .stream()
            .map(u -> getSupplementals(u, 0, expectedJobHash, Optional.empty(), lastException))
            .toList();
        return futures
                .stream()
                .map(CompletableFuture::join)
                .flatMap(Optional::stream)
                .max(BEST_STATUS_THEN_DATE);
    }


    private static Set<URI> buildSupplementalUris(
            Multimap<String, String> tiesDbBaseUris,
            AtomicReference<Throwable> exception) {
        var supplementalUris = new HashSet<URI>();
        for (var entry : tiesDbBaseUris.entries()) {
            var mediaHash = entry.getKey();
            var baseUriStr = entry.getValue();
            try {
                var baseUri = new URI(baseUriStr);
                var fullUri = new URIBuilder(baseUriStr)
                        .setPath(baseUri.getPath() + "/api/db/supplementals")
                        .setParameter("sha256Hash", mediaHash)
                        .setParameter("system", "OpenMPF")
                        .build();
                supplementalUris.add(fullUri);
            }
            catch (URISyntaxException e) {
                exception.set(new WfmProcessingException(
                        "The \"%s\" property contained \"%s\" which isn't a valid URI."
                                .formatted(MpfConstants.TIES_DB_URL, e.getInput()),
                        e));
            }
        }
        return supplementalUris;
    }


    private CompletableFuture<Optional<TiesDbCheckResult.CheckInfo>> getSupplementals(
            URI unpagedUri, int offset, String jobHash,
            Optional<TiesDbCheckResult.CheckInfo> prevBest,
            AtomicReference<Throwable> lastException) {
        int limit = 100;
        var uri = addPaginationParams(unpagedUri, offset, limit);
        return _httpClientUtils.executeRequest(
                new HttpGet(uri),
                _propertiesUtil.getHttpCallbackRetryCount())
            .thenApply(resp -> checkResponse(unpagedUri, resp))
            .thenCompose(resp -> {
                var responseJson = parseResponse(resp);
                var bestMatch = getBestMatchSoFar(responseJson, jobHash, prevBest, lastException);
                boolean isLastPage = responseJson.size() < limit;
                if (isLastPage) {
                    return ThreadUtil.completedFuture(bestMatch);
                }
                return getSupplementals(
                            unpagedUri, offset + limit, jobHash, bestMatch, lastException);
            }).exceptionally(e -> {
                lastException.set(e.getCause());
                return prevBest;
            });
    }


    private static URI addPaginationParams(URI unpagedUri, int offset, int limit) {
        try {
            return new URIBuilder(unpagedUri)
                .setParameter("offset", String.valueOf(offset))
                .setParameter("limit", String.valueOf(limit))
                .build();
        }
        catch (URISyntaxException e) {
            // impossible
            throw new IllegalStateException(e);
        }
    }

    private JsonNode parseResponse(HttpResponse response) {
        try (var is = response.getEntity().getContent()) {
            return _objectMapper.readTree(is);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    private Optional<TiesDbCheckResult.CheckInfo> getBestMatchSoFar(
            JsonNode json, String expectedJobHash, Optional<TiesDbCheckResult.CheckInfo> prevBest,
            AtomicReference<Throwable> lastException) {

        Stream<TiesDbCheckResult.CheckInfo> newResults = Streams.stream(json)
                .map(j -> convertJsonToCheckInfo(j, expectedJobHash, lastException))
                .flatMap(Optional::stream);

        return Stream.concat(prevBest.stream(), newResults)
                .max(BEST_STATUS_THEN_DATE);
    }


    // Since there could be multiple supplementals with the same job hash we need a way to pick
    // the best one.
    private static final Comparator<TiesDbCheckResult.CheckInfo> BEST_STATUS_THEN_DATE
            = createComparator();

    private static Comparator<TiesDbCheckResult.CheckInfo> createComparator() {
        ToIntFunction<TiesDbCheckResult.CheckInfo> statusToInt = ci -> switch (ci.jobStatus()) {
            case COMPLETE -> 3;
            case COMPLETE_WITH_WARNINGS -> 2;
            case COMPLETE_WITH_ERRORS -> 1;
            default -> 0;
        };
        return Comparator.comparingInt(statusToInt)
                .thenComparing(TiesDbCheckResult.CheckInfo::processDate);
    }



    private HttpResponse checkResponse(URI requestUri, HttpResponse response) {
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode >= 200 && statusCode <= 299) {
            return response;
        }

        var errorPrefix = "Sending HTTP GET to TiesDb ("  + requestUri
                + ") failed because TiesDb responded with a non-200 status code of " + statusCode;
        try {
            var body = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            throw new IllegalStateException(errorPrefix + " and body: " + body);
        }
        catch (IOException e) {
            throw new IllegalStateException(errorPrefix + '.', e);
        }
    }


    private Optional<TiesDbCheckResult.CheckInfo> convertJsonToCheckInfo(
            JsonNode jsonEntry,
            String expectedJobHash,
            AtomicReference<Throwable> exception) {
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
            LOG.error("Failed to parse the outputUri in the TiesDb data object due to: " + e, e);
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
            exception.set(e);
            return Optional.empty();
        }

        return Optional.of(new TiesDbCheckResult.CheckInfo(
                outputUri,
                status,
                processDate));
    }


    public URI updateOutputObject(
            BatchJob job, URI outputObjectUriFromPrevJob, JobRequest jobRequest) {
        try {
            var jobProps = _aggregateJobPropertiesUtil.getCombinedProperties(job);
            if (!Boolean.parseBoolean(jobProps.apply(MpfConstants.TIES_DB_S3_COPY_ENABLED))
                    || !S3StorageBackend.requiresS3ResultUpload(jobProps)) {
                jobRequest.setTiesDbStatus("PAST JOB FOUND");
                _inProgressJobs.setJobStatus(job.getId(), job.getStatus().onComplete());
                return outputObjectUriFromPrevJob;
            }

            var s3CopyConfig = new S3CopyConfig(jobProps);
            var oldOutputObject = getOldJobOutputObject(outputObjectUriFromPrevJob, s3CopyConfig);
            var urisToCopy = getUrisToCopy(oldOutputObject);
            var oldUrisToNew = _s3StorageBackend.copyResults(urisToCopy, s3CopyConfig);

            var newOutputObject = createOutputObjectWithUpdatedUris(
                    job, oldOutputObject, oldUrisToNew);
            var newOutputObjectUri = _s3StorageBackend.store(
                    newOutputObject, new MutableObject<>());
            jobRequest.setTiesDbStatus("PAST JOB FOUND");
            _inProgressJobs.setJobStatus(
                    job.getId(), BatchJobStatusType.parse(newOutputObject.getStatus()));
            return newOutputObjectUri;
        }
        catch (StorageException | IOException e) {
            var msg = "A matching job was found in TiesDb," +
                " but copying results from the old job failed due to: " + e;
            LOG.error(msg, e);
            jobRequest.setTiesDbStatus("COPY ERROR: " + msg);
            _inProgressJobs.addFatalError(
                    job.getId(),
                    IssueCodes.TIES_DB_BEFORE_JOB_CHECK,
                    msg);
            _inProgressJobs.setJobStatus(job.getId(), job.getStatus().onComplete());
            return outputObjectUriFromPrevJob;
        }
    }

    private JsonOutputObject getOldJobOutputObject(
            URI outputObjectUriFromPrevJob, S3CopyConfig copyConfig)
            throws IOException, StorageException {
        if ("file".equalsIgnoreCase(outputObjectUriFromPrevJob.getScheme())) {
            return _objectMapper.readValue(
                    new File(outputObjectUriFromPrevJob), JsonOutputObject.class);
        }
        return _s3StorageBackend.getOldJobOutputObject(outputObjectUriFromPrevJob, copyConfig);
    }


    private static Set<URI> getUrisToCopy(JsonOutputObject outputObject) throws StorageException {
        var uris = new HashSet<URI>();

        for (var media : outputObject.getMedia()) {
            try {
                if (media.getMarkupResult() != null) {
                    uris.add(new URI(media.getMarkupResult().getPath()));
                }
            }
            catch (URISyntaxException e) {
                throw new StorageException(
                        "Could not copy markup to new bucket because \"%s\" is not a valid URI."
                                .formatted(media.getPath()),
                        e);
            }
        }

        var artifactPaths = outputObject.getMedia()
                .stream()
                .flatMap(m -> m.getDetectionTypes().values().stream())
                .flatMap(Collection::stream)
                .flatMap(a -> a.getTracks().stream())
                .flatMap(t -> t.getDetections().stream())
                .filter(d -> "COMPLETED".equals(d.getArtifactExtractionStatus()))
                .map(JsonDetectionOutputObject::getArtifactPath)
                .iterator();

        while (artifactPaths.hasNext()) {
            var path = artifactPaths.next();
            try {
                uris.add(new URI(path));
            }
            catch (URISyntaxException e) {
                throw new StorageException(
                        "Could not copy artifact to new bucket because \"%s\" is not a valid URI."
                                .formatted(path),
                        e);
            }
        }
        return uris;
    }


    private JsonOutputObject createOutputObjectWithUpdatedUris(
            BatchJob newJob, JsonOutputObject oldOutputObject, Map<URI, URI> updatedUris) {

        var mediaHashToNewUri = newJob.getMedia()
                .stream()
                .collect(toMap(m -> m.getLinkedHash().orElseThrow(), Media::getUri));

        var newMediaList = new ArrayList<JsonMediaOutputObject>();
        for (var oldMedia : oldOutputObject.getMedia()) {
            var newDetectionTypeMap = new TreeMap<String, SortedSet<JsonActionOutputObject>>();
            for (var oldDetectionTypeEntry : oldMedia.getDetectionTypes().entrySet()) {
                newDetectionTypeMap.put(
                        oldDetectionTypeEntry.getKey(),
                        updateActions(oldDetectionTypeEntry.getValue(), updatedUris));
            }

            var oldMarkup = oldMedia.getMarkupResult();
            JsonMarkupOutputObject newMarkup;
            if (oldMarkup == null) {
                newMarkup = null;
            }
            else {
                var newMarkupPath = updatedUris.get(URI.create(oldMarkup.getPath())).toString();
                newMarkup = new JsonMarkupOutputObject(
                        oldMarkup.getId(),
                        newMarkupPath,
                        oldMarkup.getStatus(),
                        oldMarkup.getMessage());
            }

            var newMediaUri = mediaHashToNewUri.get(oldMedia.getSha256());
            var newMedia = JsonMediaOutputObject.factory(
                    oldMedia.getMediaId(),
                    oldMedia.getParentMediaId(),
                    newMediaUri,
                    oldMedia.getType(),
                    oldMedia.getMimeType(),
                    oldMedia.getLength(),
                    oldMedia.getFrameRanges(),
                    oldMedia.getTimeRanges(),
                    oldMedia.getSha256(),
                    oldMedia.getStatus(),
                    oldMedia.getMediaMetadata(),
                    oldMedia.getMediaProperties(),
                    newMarkup,
                    newDetectionTypeMap,
                    oldMedia.getDetectionProcessingErrors());
            newMediaList.add(newMedia);
        }
        return JsonOutputObject.factory(
                _propertiesUtil.getExportedJobId(newJob.getId()),
                oldOutputObject.getJobId(),
                oldOutputObject.getObjectId(),
                oldOutputObject.getPipeline(),
                oldOutputObject.getPriority(),
                oldOutputObject.getSiteId(),
                oldOutputObject.getOpenmpfVersion(),
                oldOutputObject.getExternalJobId(),
                oldOutputObject.getTimeStart(),
                oldOutputObject.getTimeStop(),
                oldOutputObject.getStatus(),
                oldOutputObject.getAlgorithmProperties(),
                oldOutputObject.getJobProperties(),
                oldOutputObject.getEnvironmentVariableProperties(),
                newMediaList,
                oldOutputObject.getErrors(),
                oldOutputObject.getWarnings());
    }

    private static SortedSet<JsonActionOutputObject> updateActions(
            Collection<JsonActionOutputObject> oldActions, Map<URI, URI> updatedUris) {
        var newActions = new TreeSet<JsonActionOutputObject>();
        for (var oldAction : oldActions) {
            var newTracks = oldAction.getTracks()
                    .stream()
                    .map(t -> updateTrack(t, updatedUris))
                    .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));
            var newAction = JsonActionOutputObject.factory(
                oldAction.getSource(),
                oldAction.getAlgorithm(),
                newTracks);
            newActions.add(newAction);
        }
        return newActions;
    }


    private static JsonTrackOutputObject updateTrack(
            JsonTrackOutputObject oldTrack, Map<URI, URI> updatedUris) {
        var newDetections = oldTrack.getDetections().stream()
            .map(d -> updateDetection(d, updatedUris))
            .toList();

        return new JsonTrackOutputObject(
                oldTrack.getIndex(),
                oldTrack.getId(),
                oldTrack.getStartOffsetFrame(),
                oldTrack.getStopOffsetFrame(),
                oldTrack.getStartOffsetTime(),
                oldTrack.getStopOffsetTime(),
                oldTrack.getType(),
                oldTrack.getSource(),
                oldTrack.getConfidence(),
                oldTrack.getTrackProperties(),
                updateDetection(oldTrack.getExemplar(), updatedUris),
                newDetections);
    }

    private static JsonDetectionOutputObject updateDetection(
            JsonDetectionOutputObject oldDetection, Map<URI, URI> updatedUris) {
        if (!"COMPLETED".equals(oldDetection.getArtifactExtractionStatus())) {
            return oldDetection;
        }
        var newPath = updatedUris.get(URI.create(oldDetection.getArtifactPath())).toString();
        return new JsonDetectionOutputObject(
                oldDetection.getX(),
                oldDetection.getY(),
                oldDetection.getWidth(),
                oldDetection.getHeight(),
                oldDetection.getConfidence(),
                oldDetection.getDetectionProperties(),
                oldDetection.getOffsetFrame(),
                oldDetection.getOffsetTime(),
                oldDetection.getArtifactExtractionStatus(),
                newPath);
    }
}
