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
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;
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
import org.mitre.mpf.mvc.security.OutgoingRequestTokenService;
import org.mitre.mpf.rest.api.TiesDbCheckStatus;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.camel.WfmProcessor;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.AuditEventLogger;
import org.mitre.mpf.wfm.util.HttpClientUtils;
import org.mitre.mpf.wfm.util.JobPartsIter;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
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

    private final OutgoingRequestTokenService _clientTokenProvider;

    private final ObjectMapper _objectMapper;

    private final InProgressBatchJobsService _inProgressJobs;

    private final S3StorageBackend _s3StorageBackend;

    private final AuditEventLogger _auditEventLogger;

    @Inject
    public TiesDbBeforeJobCheckServiceImpl(
            PropertiesUtil propertiesUtil,
            AggregateJobPropertiesUtil aggregateJobPropertiesUtil,
            JobConfigHasher jobConfigHasher,
            HttpClientUtils httpClientUtils,
            OutgoingRequestTokenService clientTokenProvider,
            ObjectMapper objectMapper,
            InProgressBatchJobsService inProgressJobs,
            S3StorageBackend s3StorageBackend,
            AuditEventLogger auditEventLogger) {
        _propertiesUtil = propertiesUtil;
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
        _jobConfigHasher = jobConfigHasher;
        _httpClientUtils = httpClientUtils;
        _clientTokenProvider = clientTokenProvider;
        _objectMapper = objectMapper;
        _inProgressJobs = inProgressJobs;
        _s3StorageBackend = s3StorageBackend;
        _auditEventLogger = auditEventLogger;
    }

    @Override
    public TiesDbCheckResult checkTiesDbBeforeJob(long jobId) {
        var job = _inProgressJobs.getJob(jobId);
        return getCheckNotPossibleReason(job)
                .map(TiesDbCheckResult::noResult)
                .orElseGet(() -> checkIfJobInTiesDb(job));
    }


    private static final Set<TiesDbCheckStatus> NEED_MEDIA_INSPECTION_STATUSES = EnumSet.of(
            TiesDbCheckStatus.MEDIA_HASHES_ABSENT,
            TiesDbCheckStatus.MEDIA_MIME_TYPES_ABSENT);

    @Override
    public void wfmProcess(Exchange exchange) {
        long jobId = exchange.getIn().getHeader(MpfHeaders.JOB_ID, long.class);
        var job = _inProgressJobs.getJob(jobId);
        getCheckNotPossibleReason(job)
                .filter(NEED_MEDIA_INSPECTION_STATUSES::contains)
                .flatMap(s -> checkIfJobInTiesDb(job).checkInfo())
                .ifPresent(ci -> {
                    exchange.getOut().setHeader(MpfHeaders.JOB_COMPLETE, true);
                    exchange.getOut().setHeader(
                            MpfHeaders.OUTPUT_OBJECT_URI_FROM_TIES_DB,
                            ci.outputObjectUri().toString());
                });
    }


    private TiesDbCheckResult checkIfJobInTiesDb(BatchJob job) {
        try {
            return tryCheckIfJobInTiesDb(job);
        }
        catch (Exception e) {
            var msg = "TiesDb check failed due to: " + e;
            LOG.warn(msg, e);
            _inProgressJobs.addJobWarning(
                    job.getId(), IssueCodes.TIES_DB_BEFORE_JOB_CHECK, msg);
            return TiesDbCheckResult.noResult(TiesDbCheckStatus.FAILED);
        }
    }


    private TiesDbCheckResult tryCheckIfJobInTiesDb(BatchJob job) throws StorageException {
        var mediaHashToBaseUris = getBaseTiesDbUris(job);
        var jobHash = _jobConfigHasher.getJobConfigHash(job);
        var combinedJobProps = _aggregateJobPropertiesUtil.getCombinedProperties(job);
        var s3CopyEnabled = s3CopyEnabled(combinedJobProps);

        // There may be multiple matching supplementals in TiesDb, so we don't want to report
        // an error if there was a problem getting one supplemental, but we were able to get a
        // different matching supplemental. Use an AtomicReference because some errors can occur
        // on threads belonging to the HTTP client.
        var lastException = new AtomicReference<Throwable>(null);
        var optCheckInfo = getCheckInfo(
                mediaHashToBaseUris, jobHash, s3CopyEnabled, combinedJobProps, lastException);
        if (optCheckInfo.isPresent()) {
            return new TiesDbCheckResult(
                    TiesDbCheckStatus.FOUND_MATCH,
                    optCheckInfo);
        }
        else if (lastException.get() == null) {
            return TiesDbCheckResult.noResult(TiesDbCheckStatus.NO_MATCH);
        }
        else {
            Throwables.throwIfUnchecked(lastException.get());
            throw new IllegalStateException(lastException.get());
        }
    }


    private Optional<TiesDbCheckStatus> getCheckNotPossibleReason(BatchJob job) {
        if (skipTiesDbCheckRequested(job)) {
            return Optional.of(TiesDbCheckStatus.NOT_REQUESTED);
        }
        if (!hasTiesDbUrl(job)) {
            return Optional.of(TiesDbCheckStatus.NO_TIES_DB_URL_IN_JOB);
        }

        var allMediaHaveHashes = job.getMedia().stream()
                .allMatch(TiesDbBeforeJobCheckServiceImpl::mediaHasProvidedHash);
        if (!allMediaHaveHashes) {
            return Optional.of(TiesDbCheckStatus.MEDIA_HASHES_ABSENT);
        }

        var allMediaHaveMimeTypes = job.getMedia().stream()
                .map(m -> m.getProvidedMetadata().get("MIME_TYPE"))
                .allMatch(m -> m != null && !m.isBlank());
        if (!allMediaHaveMimeTypes) {
            return Optional.of(TiesDbCheckStatus.MEDIA_MIME_TYPES_ABSENT);
        }

        return Optional.empty();
    }


    private static boolean mediaHasProvidedHash(Media media) {
        var linkedHash = media.getMediaSpecificProperty(MpfConstants.LINKED_MEDIA_HASH);
        if (linkedHash != null && !linkedHash.isBlank()) {
            return true;
        }
        var hash = media.getProvidedMetadata().get("MEDIA_HASH");
        return hash != null && !hash.isBlank();
    }


    private boolean skipTiesDbCheckRequested(BatchJob job) {
        return JobPartsIter.stream(job)
                .map(jp -> _aggregateJobPropertiesUtil.getValue(
                        MpfConstants.SKIP_TIES_DB_CHECK, jp))
                .anyMatch(Boolean::parseBoolean);
    }


    private boolean hasTiesDbUrl(BatchJob job) {
        return JobPartsIter.stream(job)
            .map(jp -> _aggregateJobPropertiesUtil.getValue(MpfConstants.TIES_DB_URL, jp))
            .anyMatch(u -> u != null && !u.isBlank());
    }


    private Multimap<String, String> getBaseTiesDbUris(BatchJob job) {
        var mediaHashToBaseUris = HashMultimap.<String, String>create();
        for (var medium : job.getMedia()) {
            var hash = medium.getLinkedHash().orElseThrow();
            JobPartsIter.stream(job, medium)
                    .map(jp -> _aggregateJobPropertiesUtil.getValue(MpfConstants.TIES_DB_URL, jp))
                    .filter(u -> u != null && !u.isBlank())
                    .forEach(u -> mediaHashToBaseUris.put(hash, u));
        }
        return mediaHashToBaseUris;
    }


    private Optional<TiesDbCheckResult.CheckInfo> getCheckInfo(
            Multimap<String, String> mediaHashToBaseUris,
            String expectedJobHash,
            boolean s3CopyEnabled,
            UnaryOperator<String> combinedProps,
            AtomicReference<Throwable> lastException) {
        // Need a temporary list so that all of the futures are created before we start calling
        // join on any of the futures.
        var futures = buildSupplementalUris(mediaHashToBaseUris, lastException)
            .stream()
            .map(u -> getSupplementals(
                        u, 0, expectedJobHash, Optional.empty(), s3CopyEnabled,
                        combinedProps, lastException))
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
            boolean s3CopyEnabled,
            UnaryOperator<String> combinedProps,
            AtomicReference<Throwable> lastException) {
        int limit = 100;
        var uri = addPaginationParams(unpagedUri, offset, limit);
        var request = new HttpGet(uri);
        _clientTokenProvider.addTokenToTiesDbRequest(combinedProps, request);
        return _httpClientUtils.executeRequest(
                request,
                _propertiesUtil.getHttpCallbackRetryCount())
            .thenApply(resp -> {
                int statusCode = resp.getStatusLine().getStatusCode();
                _auditEventLogger.readEvent()
                    .withSecurityTag()
                    .allowed("TiesDB API call: GET %s - Status Code: %s", uri, statusCode);
                return checkResponse(unpagedUri, resp);
            })
            .thenCompose(resp -> {
                var responseJson = parseResponse(resp);
                var bestMatch = getBestMatchSoFar(
                        responseJson, jobHash, prevBest, s3CopyEnabled,  lastException);
                boolean isLastPage = responseJson.size() < limit;
                if (isLastPage) {
                    return ThreadUtil.completedFuture(bestMatch);
                }
                return getSupplementals(
                            unpagedUri, offset + limit, jobHash, bestMatch, s3CopyEnabled,
                            combinedProps, lastException);
            }).exceptionally(e -> {
                _auditEventLogger.readEvent()
                    .withSecurityTag()
                    .error("TiesDB API call failed: GET %s : %s", uri, e.getCause().getMessage());
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
            boolean s3CopyEnabled,
            AtomicReference<Throwable> lastException) {

        Stream<TiesDbCheckResult.CheckInfo> newResults = Streams.stream(json)
                .map(j -> convertJsonToCheckInfo(j, expectedJobHash, s3CopyEnabled, lastException))
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
            boolean s3CopyEnabled,
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
                processDate,
                s3CopyEnabled));
    }


    public URI updateOutputObject(
            BatchJob job, URI outputObjectUriFromPrevJob, JobRequest jobRequest) {
        try {
            var jobProps = _aggregateJobPropertiesUtil.getCombinedProperties(job);
            if (!s3CopyEnabled(jobProps)) {
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
            _inProgressJobs.reportJobResultsAvailable(job.getId(), newOutputObject);
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


    private static boolean s3CopyEnabled(UnaryOperator<String> jobProps) throws StorageException {
        return Boolean.parseBoolean(jobProps.apply(MpfConstants.TIES_DB_S3_COPY_ENABLED))
                && S3StorageBackend.requiresS3ResultUpload(jobProps);
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
            if (media.getMarkupResult() != null) {
                uris.add(toUri(media.getMarkupResult().getPath(), "markup"));
            }
            if (media.getMediaSelectorsOutputUri() != null) {
                uris.add(toUri(media.getMediaSelectorsOutputUri(), "media selectors output"));
            }
        }

        var artifactPaths = outputObject.getMedia()
                .stream()
                .flatMap(m -> m.getTrackTypes().values().stream())
                .flatMap(Collection::stream)
                .flatMap(a -> a.getTracks().stream())
                .flatMap(t -> t.getDetections().stream())
                .filter(d -> "COMPLETED".equals(d.getArtifactExtractionStatus()))
                .map(JsonDetectionOutputObject::getArtifactPath)
                .iterator();

        while (artifactPaths.hasNext()) {
            var path = artifactPaths.next();
            uris.add(toUri(path, "artifact"));
        }
        return uris;
    }

    private static URI toUri(String uriString, String objectName) throws StorageException {
        try {
            return new URI(uriString);
        }
        catch (URISyntaxException e) {
            throw new StorageException(
                    "Could not copy %s to new bucket because \"%s\" is not a valid URI."
                            .formatted(objectName, uriString),
                    e);
        }
    }

    private JsonOutputObject createOutputObjectWithUpdatedUris(
            BatchJob newJob, JsonOutputObject oldOutputObject, Map<URI, URI> updatedUris) {

        var mediaHashToNewUri = newJob.getMedia()
                .stream()
                .collect(toMap(m -> m.getLinkedHash().orElseThrow(), Media::getUri));

        var newMediaList = new ArrayList<JsonMediaOutputObject>();
        for (var oldMedia : oldOutputObject.getMedia()) {
            var newTrackTypeMap = new TreeMap<String, SortedSet<JsonActionOutputObject>>();
            for (var oldTrackTypeEntry : oldMedia.getTrackTypes().entrySet()) {
                newTrackTypeMap.put(
                        oldTrackTypeEntry.getKey(),
                        updateActions(oldTrackTypeEntry.getValue(), updatedUris));
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

            var oldMediaSelectorsOutputUri = oldMedia.getMediaSelectorsOutputUri();
            String newMediaSelectorsOutputUri;
            if (oldMediaSelectorsOutputUri == null) {
                newMediaSelectorsOutputUri = null;
            }
            else {
                var oldUri = URI.create(oldMediaSelectorsOutputUri);
                newMediaSelectorsOutputUri = updatedUris.get(oldUri).toString();
            }


            var oldMediaHash = Objects.requireNonNullElse(
                    oldMedia.getMediaProperties().get(MpfConstants.LINKED_MEDIA_HASH),
                    oldMedia.getSha256());
            var newMediaUri = mediaHashToNewUri.get(oldMediaHash);

            var newMedia = JsonMediaOutputObject.factory(
                    oldMedia.getMediaId(),
                    oldMedia.getParentMediaId(),
                    newMediaUri.fullString(),
                    oldMedia.getPath(),
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
                    newMediaSelectorsOutputUri,
                    newTrackTypeMap,
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
                oldOutputObject.getWarnings(),
                oldOutputObject.getTiming());
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
                oldAction.getAction(),
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
