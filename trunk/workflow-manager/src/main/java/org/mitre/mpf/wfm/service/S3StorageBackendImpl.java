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

import static java.util.stream.Collectors.partitioningBy;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Semaphore;
import java.util.function.UnaryOperator;

import javax.inject.Inject;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.http.ConnectionClosedException;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.interop.subject.SubjectJobResult;
import org.mitre.mpf.mvc.security.OutgoingRequestTokenService;
import org.mitre.mpf.rest.api.MediaSelectorType;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.wfm.camel.operations.detection.artifactextraction.ArtifactExtractionRequest;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.MarkupResult;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.service.StorageService.OutputProcessor;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.RetryUtil;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Table;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.signer.internal.AbstractAwsS3V4Signer;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.RetryPolicyContext;
import software.amazon.awssdk.core.retry.backoff.FullJitterBackoffStrategy;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;


@Service
public class S3StorageBackendImpl implements S3StorageBackend {

    private static final Logger LOG = LoggerFactory.getLogger(S3StorageBackend.class);

    private static final ExecutionAttribute<Boolean> IS_COPY_DESTINATION
            = new ExecutionAttribute<>("mpf-is-copy-destination");

    private static LoadingCache<S3ClientConfig, S3ClientWrapper> _s3ClientCache;

    private final PropertiesUtil _propertiesUtil;

    private final LocalStorageBackend _localStorageBackend;

    private final InProgressBatchJobsService _inProgressJobs;

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;

    private final OutgoingRequestTokenService _tokenService;

    private final ObjectMapper _objectMapper;

    @Inject
    public S3StorageBackendImpl(PropertiesUtil propertiesUtil,
                                LocalStorageBackend localStorageBackend,
                                InProgressBatchJobsService inProgressBatchJobsService,
                                AggregateJobPropertiesUtil aggregateJobPropertiesUtil,
                                OutgoingRequestTokenService tokenService,
                                ObjectMapper objectMapper) {
        synchronized (S3StorageBackend.class) {
            _s3ClientCache = createClientCache(propertiesUtil.getS3ClientCacheCount());
        }
        _propertiesUtil = propertiesUtil;
        _localStorageBackend = localStorageBackend;
        _inProgressJobs = inProgressBatchJobsService;
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
        _tokenService = tokenService;
        _objectMapper = objectMapper;
    }


    @Override
    public boolean canStore(JsonOutputObject outputObject) throws StorageException {
        long internalJobId = _propertiesUtil.getJobIdFromExportedId(outputObject.getJobId());
        BatchJob job = _inProgressJobs.getJob(internalJobId);
        return S3StorageBackend.requiresS3ResultUpload(
                _aggregateJobPropertiesUtil.getCombinedProperties(job));
    }

    @Override
    public URI store(JsonOutputObject outputObject, Mutable<String> outputSha) throws StorageException, IOException {
        URI localUri = _localStorageBackend.store(outputObject, outputSha);
        Path localPath = Path.of(localUri);
        long internalJobId = _propertiesUtil.getJobIdFromExportedId(outputObject.getJobId());
        BatchJob job = _inProgressJobs.getJob(internalJobId);
        if (outputSha.getValue() == null) {
            outputSha.setValue(hashExistingFile(localPath));
        }
        var uploadedUri = putInS3IfAbsent(
                localPath, outputSha.getValue(),
                _aggregateJobPropertiesUtil.getCombinedProperties(job));
        Files.delete(localPath);
        return uploadedUri;
    }


    @Override
    public boolean canStore(ArtifactExtractionRequest request) throws StorageException {
        BatchJob job = _inProgressJobs.getJob(request.getJobId());
        Media media = job.getMedia(request.getMediaId());
        var combinedProperties = _aggregateJobPropertiesUtil.getCombinedProperties(job, media);
        return S3StorageBackend.requiresS3ResultUpload(combinedProperties);
    }


    @Override
    public Table<Integer, Integer, URI> storeArtifacts(ArtifactExtractionRequest request) throws IOException {
        BatchJob job = _inProgressJobs.getJob(request.getJobId());
        Media media = job.getMedia(request.getMediaId());
        var combinedProperties = _aggregateJobPropertiesUtil.getCombinedProperties(job, media);

        Table<Integer, Integer, URI> localResults = _localStorageBackend.storeArtifacts(request);
        var futures = createArtifactUploadFutures(localResults, combinedProperties);
        Table<Integer, Integer, URI> remoteResults = HashBasedTable.create();
        for (var cell : futures.cellSet()) {
            URI resultUri;
            try {
                resultUri = joinOrThrow(cell.getValue());
            }
            catch (StorageException | IOException e) {
                _inProgressJobs.addWarning(
                        request.getJobId(), request.getMediaId(), IssueCodes.REMOTE_STORAGE_UPLOAD,
                        "Some artifacts were stored locally because storing them remotely failed due to: " +
                                e.getMessage());

                resultUri = localResults.get(cell.getRowKey(), cell.getColumnKey());
            }
            remoteResults.put(cell.getRowKey(), cell.getColumnKey(), resultUri);
        }
        return remoteResults;
    }

    private Table<Integer, Integer, CompletableFuture<URI>> createArtifactUploadFutures(
            Table<Integer, Integer, URI> localResults,
            UnaryOperator<String> combinedProperties) {

        var futures = HashBasedTable.<Integer, Integer, CompletableFuture<URI>>create();
        var semaphore = new Semaphore(Math.max(1, _propertiesUtil.getArtifactParallelUploadCount()));
        for (Table.Cell<Integer, Integer, URI> entry : localResults.cellSet()) {
            acquire(semaphore);

            var future = ThreadUtil.callAsync(
                () -> {
                    Path localPath = Path.of(entry.getValue());
                    URI uploadedUri = putInS3IfAbsent(localPath, combinedProperties);
                    Files.delete(localPath);
                    return uploadedUri;
                });

            future.whenComplete((x, y) -> semaphore.release());
            futures.put(entry.getRowKey(), entry.getColumnKey(), future);
        }
        return futures;
    }


    @Override
    public boolean canStore(MarkupResult markupResult) throws StorageException {
        BatchJob job = _inProgressJobs.getJob(markupResult.getJobId());
        Action action = job.getPipelineElements().getAction(markupResult.getTaskIndex(),
                                                            markupResult.getActionIndex());
        Media media = job.getMedia(markupResult.getMediaId());
        var combinedProperties
                = _aggregateJobPropertiesUtil.getCombinedProperties(job, media, action);
        return S3StorageBackend.requiresS3ResultUpload(combinedProperties);
    }

    @Override
    public void store(MarkupResult markupResult) throws StorageException, IOException {
        _localStorageBackend.store(markupResult);
        BatchJob job = _inProgressJobs.getJob(markupResult.getJobId());
        Media media = job.getMedia(markupResult.getMediaId());
        Action action = job.getPipelineElements().getAction(markupResult.getTaskIndex(),
                                                            markupResult.getActionIndex());
        var combinedProperties
                = _aggregateJobPropertiesUtil.getCombinedProperties(job, media, action);
        Path markupPath = Paths.get(URI.create(markupResult.getMarkupUri()));

        URI uploadedUri = putInS3IfAbsent(markupPath, combinedProperties);
        Files.delete(markupPath);

        markupResult.setMarkupUri(uploadedUri.toString());
    }


    @Override
    public boolean canStoreDerivativeMedia(BatchJob job, long parentMediaId) throws StorageException {
        var combinedProperties =
                _aggregateJobPropertiesUtil.getCombinedProperties(job, job.getMedia(parentMediaId));
        return S3StorageBackend.requiresS3ResultUpload(combinedProperties);
    }

    @Override
    public void storeDerivativeMedia(BatchJob job, Media media)
                throws StorageException, IOException {
        var combinedProperties = _aggregateJobPropertiesUtil.getCombinedProperties(
                job,
                job.getMedia(media.getParentId()));
        URI uploadedUri = putInS3IfAbsent(media.getLocalPath(), combinedProperties);
        _inProgressJobs.addStorageUri(job.getId(), media.getId(), uploadedUri.toString());
    }


    @Override
    public boolean canStore(SubjectJobResult jobResult) throws StorageException  {
        var combinedProperties = _aggregateJobPropertiesUtil.getCombinedProperties(jobResult);
        return S3StorageBackend.requiresS3ResultUpload(combinedProperties);
    }

    @Override
    public URI store(SubjectJobResult jobResult) throws IOException, StorageException {
        var localUri = _localStorageBackend.store(jobResult);
        var localPath = Path.of(localUri);
        var combinedProperties = _aggregateJobPropertiesUtil.getCombinedProperties(jobResult);
        var uploadedUri = putInS3IfAbsent(localPath, combinedProperties);
        Files.delete(localPath);
        return uploadedUri;
    }


    @Override
    public void downloadFromS3(Media media, UnaryOperator<String> combinedProperties)
            throws StorageException {
        getFromS3(
                media.getUri().fullString(),
                combinedProperties,
                ResponseTransformer.toFile(media.getLocalPath()));
    }

    @Override
    public ResponseInputStream<GetObjectResponse> getFromS3(
            String uri, UnaryOperator<String> properties) throws StorageException {
        return getFromS3(uri, properties, ResponseTransformer.toInputStream());
    }

    private <T> T getFromS3(
            String uri,
            UnaryOperator<String> properties,
            ResponseTransformer<GetObjectResponse, T> responseTransformer) throws StorageException {
        try {
            var s3UrlUtil = S3UrlUtil.get(properties);
            var s3Client = getS3DownloadClient(uri, s3UrlUtil, properties);
            String[] pathParts = s3UrlUtil.splitBucketAndObjectKey(uri);
            String bucket = pathParts[0];
            String objectKey = pathParts[1];
            var getRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .overrideConfiguration(getOverrideConfig(properties))
                    .build();
            return s3Client.getObject(getRequest, responseTransformer);
        }
        catch (SdkException e) {
            throw new StorageException(
                    String.format("Failed to download \"%s\" due to %s", uri, e),
                    e);
        }
    }


    private URI putInS3IfAbsent(Path path, UnaryOperator<String> properties)
            throws IOException, StorageException {
        return putInS3IfAbsent(path, hashExistingFile(path), properties);
    }


    private URI putInS3IfAbsent(Path path, String hash, UnaryOperator<String> properties)
            throws StorageException {
        String objectName = getObjectName(
                hash, properties.apply(MpfConstants.S3_UPLOAD_OBJECT_KEY_PREFIX));
        var s3UrlUtil = S3UrlUtil.get(properties);
        return putInS3IfAbsent(
                path,
                properties.apply(MpfConstants.S3_RESULTS_BUCKET),
                objectName,
                getS3UploadClient(s3UrlUtil, properties),
                s3UrlUtil,
                getOverrideConfig(properties));
    }


    private URI putInS3IfAbsent(
            Path path,
            String bucketUri,
            String objectName,
            S3ClientWrapper s3Client,
            S3UrlUtil urlUtil,
            AwsRequestOverrideConfiguration overrideConfig) throws StorageException {

        LOG.info("Storing \"{}\" in S3 bucket \"{}\" with object key \"{}\" ...",
                path, bucketUri, objectName);
        try {
            var bucketName = urlUtil.getResultsBucketName(bucketUri);
            if (s3Client.objectExists(bucketName, objectName, overrideConfig)) {
                LOG.info(
                        "Did not upload \"{}\" to S3 bucket \"{}\" and object key \"{}\" "
                                + "because a file with the same SHA-256 hash was already there.",
                        path, bucketUri, objectName);
            }
            else {
                var putRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectName)
                        .overrideConfiguration(overrideConfig)
                        .build();
                s3Client.putObject(putRequest, path);
                LOG.info("Successfully stored \"{}\" in S3 bucket \"{}\" with object key \"{}\".",
                        path, bucketUri, objectName);
            }
            return urlUtil.getFullUri(bucketUri, objectName);
        }
        catch (SdkException e) {
            LOG.error("Failed to upload {} due to S3 error: {}", path, e);
            // Don't include path so multiple failures appear as one issue in JSON output object.
            throw new StorageException("Failed to upload due to S3 error: " + e, e);
        }
    }


    public ResponseInputStream<GetObjectResponse> getOldJobOutputObjectStream(
            URI outputObjectUri, S3CopyConfig copyConfig) throws StorageException {

        var sourceUrlUtil = copyConfig.sourceUrlUtil;
        var sourceClientConfig = new S3ClientConfig(
                sourceUrlUtil.getS3Endpoint(outputObjectUri),
                _propertiesUtil.getHttpStorageUploadRetryCount(),
                copyConfig.sourceRegion);

        var sourceClient = _s3ClientCache.getUnchecked(sourceClientConfig);
        var sourceBucketAndKey = sourceUrlUtil.splitBucketAndObjectKey(outputObjectUri.toString());
        var overrideConfig = getOverrideConfig(copyConfig.sourceCredentials, copyConfig.props);
        var getRequest = GetObjectRequest.builder()
                .overrideConfiguration(overrideConfig)
                .bucket(sourceBucketAndKey[0])
                .key(sourceBucketAndKey[1])
                .build();

        return sourceClient.getObject(getRequest);
    }

    @Override
    public JsonOutputObject getOldJobOutputObject(URI outputObjectUri, S3CopyConfig copyConfig)
            throws StorageException {
        try {
            return RetryUtil.execute(
                _propertiesUtil.getHttpStorageUploadRetryCount(),
                ConnectionClosedException.class,
                "Retreiving output object",
                () -> getParsedOutputObject(outputObjectUri, copyConfig));
        }
        catch (StorageException e) {
            throw e;
        }
        catch (Exception e) {
            throw new StorageException(
                "Getting the output object from %s failed due to: %s".formatted(outputObjectUri, e),
                e);
        }
    }

    private JsonOutputObject getParsedOutputObject(URI outputObjectUri, S3CopyConfig copyConfig)
            throws Exception {
        try (var is = getOldJobOutputObjectStream(outputObjectUri, copyConfig)) {
            return _objectMapper.readValue(is, JsonOutputObject.class);
        }
    }

    @Override
    public Map<URI, URI> copyResults(
            Collection<URI> urisToCopy, S3CopyConfig copyConfig)
            throws StorageException, IOException {

        var partitionedUris = urisToCopy.stream()
                .collect(partitioningBy(u -> "file".equalsIgnoreCase(u.getScheme())));
        var remoteUris = partitionedUris.get(false);
        var groupedByEndpoint = groupByS3Endpoint(remoteUris, copyConfig);

        var allUploadFutures = new HashMap<URI, CompletableFuture<URI>>();
        var semaphore = new Semaphore(Math.max(
                1, _propertiesUtil.getArtifactParallelUploadCount()));
        for (var endpointGroup : groupedByEndpoint.asMap().entrySet()) {
            var endpointUri = endpointGroup.getKey();
            if (copyConfig.canUseSameClient(endpointUri)) {
                var groupFutures = fastCopyItems(
                        endpointUri,
                        endpointGroup.getValue(),
                        copyConfig,
                        semaphore);
                allUploadFutures.putAll(groupFutures);
            }
            else {
                var groupFutures = slowCopyItems(
                        endpointUri,
                        endpointGroup.getValue(),
                        copyConfig,
                        semaphore);
                allUploadFutures.putAll(groupFutures);
            }
        }

        var localUris = partitionedUris.get(true);
        if (!localUris.isEmpty()) {
            allUploadFutures.putAll(copyLocalItems(localUris, copyConfig, semaphore));
        }

        var sourceToDestUris = new HashMap<URI, URI>();
        for (var entry : allUploadFutures.entrySet()) {
            var destUri = joinOrThrow(entry.getValue());
            sourceToDestUris.put(entry.getKey(), destUri);
        }
        return sourceToDestUris;
    }

    private static Multimap<URI, URI> groupByS3Endpoint(
            Collection<URI> urisToCopy, S3CopyConfig copyConfig) throws StorageException {
        var groupedByEndpoint = MultimapBuilder
                .hashKeys()
                .hashSetValues()
                .<URI, URI>build();

        var urlUtil = copyConfig.sourceUrlUtil;
        for (var uri : urisToCopy) {
            groupedByEndpoint.put(urlUtil.getS3Endpoint(uri), uri);
        }
        return groupedByEndpoint;
    }


    private Map<URI, CompletableFuture<URI>> fastCopyItems(
            URI endpointUri,
            Collection<URI> itemUris,
            S3CopyConfig copyConfig,
            Semaphore semaphore) {

        var clientConfig = new S3ClientConfig(
            endpointUri,
            _propertiesUtil.getHttpStorageUploadRetryCount(),
            copyConfig.destinationRegion);
        var s3Client = _s3ClientCache.getUnchecked(clientConfig);

        var futures = new HashMap<URI, CompletableFuture<URI>>();
        for (var itemUri : itemUris) {
            acquire(semaphore);
            var future = ThreadUtil.callAsync(() -> fastCopy(itemUri, s3Client, copyConfig));
            future.whenComplete((x, err) -> semaphore.release());
            futures.put(itemUri, future);
        }
        return futures;
    }


    private URI fastCopy(URI itemUri, S3ClientWrapper s3Client, S3CopyConfig copyConfig)
                throws StorageException {
        var urlUtil = copyConfig.destinationUrlUtil;
        var sourceBucketAndKey = urlUtil.splitBucketAndObjectKey(itemUri.toString());
        var sourceBucket = sourceBucketAndKey[0];
        var sourceKey = sourceBucketAndKey[1];
        var destinationBucket = copyConfig.getDestinationBucket();
        var destinationKey = copyConfig.getDestinationKey(sourceKey);
        var newUri = urlUtil.getFullUri(copyConfig.destinationBucketUri, destinationKey);
        var overrideConfig = getOverrideConfig(copyConfig.destinationCredentials, copyConfig.props);
        try {
            var exists = s3Client.objectExists(
                    destinationBucket, destinationKey, overrideConfig);
            if (exists) {
                LOG.info("Did not copy \"{}\" to \"{}\" because it already exists.",
                        itemUri, newUri);
                return newUri;
            }

            var copyRequest = CopyObjectRequest.builder()
                    .overrideConfiguration(overrideConfig)
                    .sourceBucket(sourceBucket)
                    .sourceKey(sourceKey)
                    .destinationBucket(destinationBucket)
                    .destinationKey(destinationKey)
                    .build();
            s3Client.copyObject(copyRequest);
            LOG.info("Successfully copied {} to {}", itemUri, newUri);
            return newUri;
        }
        catch (SdkException e) {
            throw new StorageException(
                "Failed to copy %s to %s due to: %s".formatted(itemUri, newUri, e), e);
        }
    }


    private Map<URI, CompletableFuture<URI>> slowCopyItems(
            URI sourceEndpoint,
            Collection<URI> itemUris,
            S3CopyConfig copyConfig,
            Semaphore semaphore) throws StorageException {

        var sourceClientConfig = new S3ClientConfig(
                sourceEndpoint,
                _propertiesUtil.getHttpStorageUploadRetryCount(),
                copyConfig.sourceRegion);
        var sourceClient = _s3ClientCache.getUnchecked(sourceClientConfig);

        var destinationClient = getDestinationClient(copyConfig);
        var putOverrideBuilder = getOverrideConfigBuilder(
                copyConfig.destinationCredentials, copyConfig.props);
        var putOverrideConfig = putOverrideBuilder
                .putExecutionAttribute(IS_COPY_DESTINATION, true)
                .build();

        var futures = new HashMap<URI, CompletableFuture<URI>>();
        for (var itemUri : itemUris) {
            acquire(semaphore);
            var future = ThreadUtil.callAsync(() -> slowCopy(
                    itemUri, sourceClient, destinationClient, copyConfig, putOverrideConfig));
            future.whenComplete((x, err) -> semaphore.release());
            futures.put(itemUri, future);
        }

        return futures;
    }

    private URI slowCopy(
            URI itemUri,
            S3ClientWrapper sourceClient,
            S3ClientWrapper destinationClient,
            S3CopyConfig copyConfig,
            AwsRequestOverrideConfiguration putOverrideConfig) throws StorageException, IOException {

        var sourceBucketAndKey
                = copyConfig.sourceUrlUtil.splitBucketAndObjectKey(itemUri.toString());
        var sourceBucket = sourceBucketAndKey[0];
        var sourceKey = sourceBucketAndKey[1];
        var destinationBucket = copyConfig.getDestinationBucket();
        var destinationKey = copyConfig.getDestinationKey(sourceKey);
        var newUri = copyConfig.destinationUrlUtil.getFullUri(
                copyConfig.destinationBucketUri, destinationKey);
        try {
            var exists = destinationClient.objectExists(
                        destinationBucket, destinationKey,
                        getOverrideConfig(copyConfig.destinationCredentials, copyConfig.props));
            if (exists) {
                LOG.info("Did not copy \"{}\" to \"{}\" because it already exists.",
                        itemUri, newUri);
                return newUri;
            }

            var sourceOverrideConfig = getOverrideConfig(
                    copyConfig.sourceCredentials, copyConfig.props);
            var getRequest = GetObjectRequest.builder()
                    .overrideConfiguration(sourceOverrideConfig)
                    .bucket(sourceBucket)
                    .key(sourceKey)
                    .build();
            var putRequest = PutObjectRequest.builder()
                    .overrideConfiguration(putOverrideConfig)
                    .bucket(destinationBucket)
                    .key(destinationKey)
                    .build();

            RetryUtil.execute(
                _propertiesUtil.getHttpStorageUploadRetryCount(),
                CopyInterruptedException.class,
                "Copying S3 object from %s to %s".formatted(itemUri, newUri),
                () -> {
                    try (var is = sourceClient.getObject(getRequest)) {
                        var responseLength = is.response().contentLength();
                        destinationClient.putObject(
                                    putRequest, RequestBody.fromInputStream(is, responseLength));
                    }
                });
            LOG.info("Successfully copied {} to {}", itemUri, newUri);
            return newUri;
        }
        catch (SdkException | CopyInterruptedException e) {
            throw new StorageException(
                "Failed to copy %s to %s due to: %s".formatted(itemUri, newUri, e), e);
        }
    }

    private Map<URI, CompletableFuture<URI>> copyLocalItems(
            Collection<URI> localUris, S3CopyConfig copyConfig, Semaphore semaphore)
            throws StorageException {

        var futures = new HashMap<URI, CompletableFuture<URI>>();
        var s3Client = getDestinationClient(copyConfig);
        for (var localUri : localUris) {
            acquire(semaphore);
            var future = ThreadUtil.callAsync(() -> copyLocalItem(localUri, copyConfig, s3Client));
            future.whenComplete((x, err) -> semaphore.release());
            futures.put(localUri, future);
        }
        return futures;
    }

    private URI copyLocalItem(URI localUri, S3CopyConfig copyConfig, S3ClientWrapper s3Client)
            throws IOException, StorageException {
        var path = Path.of(localUri);
        var hash = hashExistingFile(path);
        var objectName = getObjectName(hash, copyConfig.destinationPrefix);
        return putInS3IfAbsent(
                Path.of(localUri),
                copyConfig.destinationBucketUri,
                objectName,
                s3Client,
                copyConfig.destinationUrlUtil,
                getOverrideConfig(copyConfig.destinationCredentials, copyConfig.props));
    }


    private static String hashExistingFile(Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            return DigestUtils.sha256Hex(is);
        }
    }


    private static String getObjectName(String hash, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            prefix = "";
        }
        String firstPair = hash.substring(0, 2);
        String secondPair = hash.substring(2, 4);
        return prefix + firstPair + '/' + secondPair + '/' + hash;
    }


    private S3ClientWrapper getDestinationClient(S3CopyConfig copyConfig) throws StorageException {
        var clientConfig = new S3ClientConfig(
                copyConfig.destinationUrlUtil.getS3Endpoint(copyConfig.destinationBucketUri),
                _propertiesUtil.getHttpStorageUploadRetryCount(),
                copyConfig.destinationRegion);
        return _s3ClientCache.getUnchecked(clientConfig);
    }


    private S3ClientWrapper getS3DownloadClient(
            String mediaUri,
            S3UrlUtil s3UrlUtil,
            UnaryOperator<String> properties) throws StorageException {
        var region = properties.apply(MpfConstants.S3_REGION);
        return _s3ClientCache.getUnchecked(new S3ClientConfig(
                s3UrlUtil.getS3Endpoint(mediaUri),
                _propertiesUtil.getRemoteMediaDownloadRetries(),
                region));
    }

    private S3ClientWrapper getS3UploadClient(
            S3UrlUtil s3UrlUtil,
            UnaryOperator<String> properties) throws StorageException {
        var endpoint = s3UrlUtil.getS3Endpoint(
                properties.apply(MpfConstants.S3_RESULTS_BUCKET));
        var region = properties.apply(MpfConstants.S3_REGION);
        return _s3ClientCache.getUnchecked(new S3ClientConfig(
                endpoint,
                _propertiesUtil.getHttpStorageUploadRetryCount(),
                region));
    }


    private static boolean shouldRetry(RetryPolicyContext context, int maxRetries) {
        int httpStatus = Objects.requireNonNullElse(context.httpStatusCode(), -1);
        if (context.originalRequest() instanceof HeadObjectRequest && httpStatus == 404) {
            // A HEAD request is sent prior to uploading an object to determine if the object
            // already exists and the upload can be avoided. In most cases the object will not
            // exist, so we expect the 404 error in that case.
            return false;
        }
        var isCopyDest = context.executionAttributes().getAttribute(IS_COPY_DESTINATION);
        if (isCopyDest != null && isCopyDest && httpStatus == 400
                && context.exception() instanceof S3Exception s3Exception
                && "IncompleteBody".equals(s3Exception.awsErrorDetails().errorCode())) {
            throw new CopyInterruptedException(s3Exception);
        }

        int attemptsRemaining = maxRetries - context.retriesAttempted();
        LOG.warn("\"{}\" responded with a non-200 status code of {}. " +
                         "There are {} attempts remaining.",
                 context.request().getUri(), context.httpStatusCode(), attemptsRemaining);
        return true;
    }


    private static <T> T joinOrThrow(CompletableFuture<T> future) throws IOException, StorageException {
        try {
            return future.join();
        }
        catch (CompletionException e) {
            Throwables.propagateIfPossible(e.getCause(), StorageException.class, IOException.class);
            throw e;
        }
    }



    private static LoadingCache<S3ClientConfig, S3ClientWrapper> createClientCache(int cacheSize) {
        return CacheBuilder.newBuilder()
                .maximumSize(cacheSize)
                .build(CacheLoader.from(S3StorageBackendImpl::buildClient));
    }


    private static S3ClientWrapper buildClient(S3ClientConfig clientConfig) {
        var backoff = FullJitterBackoffStrategy.builder()
                .baseDelay(Duration.ofMillis(100))
                .maxBackoffTime(Duration.ofSeconds(30))
                .build();

        var retry = RetryPolicy.builder()
                .backoffStrategy(backoff)
                .numRetries(clientConfig.retryCount)
                .retryCondition(ctx -> shouldRetry(ctx, clientConfig.retryCount))
                .build();

        LOG.info("Creating S3 client for endpoint \"{}\"", clientConfig.endpoint);
        var s3Client = S3Client.builder()
                .region(Region.of(clientConfig.region))
                .endpointOverride(clientConfig.endpoint)
                .serviceConfiguration(s -> s.pathStyleAccessEnabled(true))
                .overrideConfiguration(o -> o.retryPolicy(retry))
                .credentialsProvider(S3StorageBackendImpl::throwWhenNoCredentialsInRequest)
                .build();
        return new S3ClientWrapper(s3Client);
    }


    private static AwsCredentials throwWhenNoCredentialsInRequest() {
        // Setting credentials on the individual requests allows S3Client instances to be shared
        // when only credentials are different.
        throw new IllegalStateException(
                "Add credentials to request using AwsRequestOverrideConfiguration.");
    }

    private AwsRequestOverrideConfiguration getOverrideConfig(UnaryOperator<String> properties) {
        AwsCredentials credentials;
        var sessionToken = properties.apply(MpfConstants.S3_SESSION_TOKEN);
        var accessKey = properties.apply(MpfConstants.S3_ACCESS_KEY);
        var secretKey = properties.apply(MpfConstants.S3_SECRET_KEY);
        if (sessionToken != null && !sessionToken.isBlank()) {
            credentials = AwsSessionCredentials.create(accessKey, secretKey, sessionToken);
        }
        else {
            credentials = AwsBasicCredentials.create(accessKey, secretKey);
        }
        return getOverrideConfig(StaticCredentialsProvider.create(credentials), properties);
    }

    private AwsRequestOverrideConfiguration getOverrideConfig(
            AwsCredentialsProvider credentialsProvider,
            UnaryOperator<String> properties) {
        return getOverrideConfigBuilder(credentialsProvider, properties).build();
    }

    private AwsRequestOverrideConfiguration.Builder getOverrideConfigBuilder(
            AwsCredentialsProvider credentialsProvider,
            UnaryOperator<String> properties) {
        return AwsRequestOverrideConfiguration
            .builder()
            .credentialsProvider(credentialsProvider)
            .signer(customSigner(properties));
    }

    // The default behavior of the S3 library is to include custom headers in the signature
    // calculation. To avoid this, we use a custom signer to add the headers after signing the
    // request.
    private AbstractAwsS3V4Signer customSigner(UnaryOperator<String> properties) {
        return new AbstractAwsS3V4Signer() {
            @Override
            public SdkHttpFullRequest sign(
                    SdkHttpFullRequest request,
                    ExecutionAttributes executionAttributes) {
                var signedRequest = super.sign(request, executionAttributes);
                return _tokenService.addTokenToS3Request(properties, signedRequest);
            }
        };
    }


    private static void acquire(Semaphore semaphore) {
        try {
            semaphore.acquire();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }


    private record S3ClientConfig(URI endpoint, int retryCount, String region) {
    }

    private static class CopyInterruptedException extends RuntimeException {
        public CopyInterruptedException(S3Exception e) {
            super(e);
        }

        @Override
        public String toString() {
            return getCause().toString();
        }
    }

    @Override
    public boolean canStoreMediaSelectorsOutput(BatchJob job, Media media) throws StorageException {
        var combinedProperties = _aggregateJobPropertiesUtil.getCombinedProperties(job, media);
        return S3StorageBackend.requiresS3ResultUpload(combinedProperties);
    }


    @Override
    public URI storeMediaSelectorsOutput(
            BatchJob job,
            Media media,
            MediaSelectorType selectorType,
            OutputProcessor outputProcessor) throws StorageException, IOException {
        var localUri = _localStorageBackend.storeMediaSelectorsOutput(
                job, media, selectorType, outputProcessor);
        var localPath = Path.of(localUri);
        var uploadedUri = putInS3IfAbsent(
                localPath, _aggregateJobPropertiesUtil.getCombinedProperties(job, media));
        Files.delete(localPath);
        return uploadedUri;
    }
}
