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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.Mutable;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.wfm.camel.operations.detection.artifactextraction.ArtifactExtractionRequest;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.MarkupResult;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.RetryPolicyContext;
import software.amazon.awssdk.core.retry.backoff.FullJitterBackoffStrategy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Semaphore;
import java.util.function.UnaryOperator;

@Service
public class S3StorageBackend implements StorageBackend {

    private static final Logger LOG = LoggerFactory.getLogger(S3StorageBackend.class);

    private static LoadingCache<S3ClientConfig, S3Client> _s3ClientCache;

    private final PropertiesUtil _propertiesUtil;

    private final LocalStorageBackend _localStorageBackend;

    private final InProgressBatchJobsService _inProgressJobs;

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;

    @Inject
    public S3StorageBackend(PropertiesUtil propertiesUtil,
                            LocalStorageBackend localStorageBackend,
                            InProgressBatchJobsService inProgressBatchJobsService,
                            AggregateJobPropertiesUtil aggregateJobPropertiesUtil) {
        synchronized (S3StorageBackend.class) {
            _s3ClientCache = createClientCache(propertiesUtil.getS3ClientCacheCount());
        }
        _propertiesUtil = propertiesUtil;
        _localStorageBackend = localStorageBackend;
        _inProgressJobs = inProgressBatchJobsService;
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
    }


    @Override
    public boolean canStore(JsonOutputObject outputObject) throws StorageException {
        long internalJobId = _propertiesUtil.getJobIdFromExportedId(outputObject.getJobId());
        BatchJob job = _inProgressJobs.getJob(internalJobId);
        return requiresS3ResultUpload(_aggregateJobPropertiesUtil.getCombinedProperties(job));
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
        URI uploadedUri = putInS3IfAbsent(localPath, outputSha.getValue(),
                _aggregateJobPropertiesUtil.getCombinedProperties(job));
        Files.delete(localPath);
        return uploadedUri;
    }


    @Override
    public boolean canStore(ArtifactExtractionRequest request) throws StorageException {
        BatchJob job = _inProgressJobs.getJob(request.getJobId());
        Media media = job.getMedia(request.getMediaId());
        var combinedProperties = _aggregateJobPropertiesUtil.getCombinedProperties(job, media);
        return requiresS3ResultUpload(combinedProperties);
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
                resultUri = cell.getValue().join();
            }
            catch (CompletionException e) {
                _inProgressJobs.addWarning(
                        request.getJobId(), request.getMediaId(), IssueCodes.REMOTE_STORAGE_UPLOAD,
                        "Some artifacts were stored locally because storing them remotely failed due to: " +
                                e.getCause().getMessage());

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
            try {
                semaphore.acquire();
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }

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
        return requiresS3ResultUpload(combinedProperties);
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
        return requiresS3ResultUpload(combinedProperties);
    }

    @Override
    public void storeDerivativeMedia(BatchJob job, Media media) throws StorageException, IOException {
        var combinedProperties = _aggregateJobPropertiesUtil.getCombinedProperties(
                job,
                job.getMedia(media.getParentId()));
        URI uploadedUri = putInS3IfAbsent(media.getLocalPath(), combinedProperties);
        _inProgressJobs.addStorageUri(job.getId(), media.getId(), uploadedUri.toString());
    }


    /**
     * Ensures that the S3-related properties are valid.
     * @param properties Properties to validate
     * @throws StorageException when an invalid combination of S3 properties are provided.
     */
    public static void validateS3Properties(UnaryOperator<String> properties) throws StorageException {
        // Both will throw if properties are invalid.
        requiresS3MediaDownload(properties);
        requiresS3ResultUpload(properties);
    }



    public static boolean requiresS3MediaDownload(UnaryOperator<String> properties) throws StorageException {
        boolean uploadOnly = Boolean.parseBoolean(properties.apply(MpfConstants.S3_UPLOAD_ONLY));
        if (uploadOnly) {
            return false;
        }

        boolean hasAccessKey = StringUtils.isNotBlank(properties.apply(MpfConstants.S3_ACCESS_KEY));
        boolean hasSecretKey = StringUtils.isNotBlank(properties.apply(MpfConstants.S3_SECRET_KEY));
        if (hasAccessKey && hasSecretKey) {
            return true;
        }
        if (!hasAccessKey && !hasSecretKey) {
            return false;
        }

        String presentProperty;
        String missingProperty;
        if (hasAccessKey) {
            presentProperty = MpfConstants.S3_ACCESS_KEY;
            missingProperty = MpfConstants.S3_SECRET_KEY;
        }
        else {
            presentProperty = MpfConstants.S3_SECRET_KEY;
            missingProperty = MpfConstants.S3_ACCESS_KEY;
        }
        throw new StorageException(String.format("The %s property was set, but the %s property was not.",
                                                 presentProperty, missingProperty));
    }



    public static boolean requiresS3ResultUpload(UnaryOperator<String> properties) throws StorageException {
        if (StringUtils.isBlank(properties.apply(MpfConstants.S3_RESULTS_BUCKET))) {
            return false;
        }
        boolean hasAccessKey = StringUtils.isNotBlank(properties.apply(MpfConstants.S3_ACCESS_KEY));
        boolean hasSecretKey = StringUtils.isNotBlank(properties.apply(MpfConstants.S3_SECRET_KEY));
        if (hasAccessKey && hasSecretKey) {
            return true;
        }

        if (!hasAccessKey && !hasSecretKey) {
            throw new StorageException(String.format(
                    "The %s property was set, but the %s and %s properties were not.",
                    MpfConstants.S3_RESULTS_BUCKET, MpfConstants.S3_ACCESS_KEY,
                    MpfConstants.S3_SECRET_KEY));
        }

        String presentProperty;
        String missingProperty;
        if (hasAccessKey) {
            presentProperty = MpfConstants.S3_ACCESS_KEY;
            missingProperty = MpfConstants.S3_SECRET_KEY;
        }
        else {
            presentProperty = MpfConstants.S3_SECRET_KEY;
            missingProperty = MpfConstants.S3_ACCESS_KEY;
        }
        throw new StorageException(String.format(
                "The %s and %s properties were set, but the %s property was not.",
                MpfConstants.S3_RESULTS_BUCKET, presentProperty, missingProperty));
    }


    public void downloadFromS3(Media media, UnaryOperator<String> combinedProperties)
            throws StorageException {
        try {
            var s3UrlUtil = S3UrlUtil.get(combinedProperties);
            var s3Client = getS3DownloadClient(media.getUri(), s3UrlUtil, combinedProperties);
            String[] pathParts = s3UrlUtil.splitBucketAndObjectKey(media.getUri());
            String bucket = pathParts[0];
            String objectKey = pathParts[1];
            LOG.info("Downloading from S3 bucket \"{}\", object \"{}\"", bucket, objectKey);
            s3Client.getObject(
                    r -> r.bucket(bucket).key(objectKey),
                    media.getLocalPath());
        }
        catch (S3Exception | SdkClientException e) {
            throw new StorageException(
                    String.format("Failed to download \"%s\" due to %s", media.getUri(), e), e);
        }
    }


    public ResponseInputStream<GetObjectResponse> getFromS3(
            String uri, UnaryOperator<String> properties) throws StorageException {
        try {
            var s3UrlUtil = S3UrlUtil.get(properties);
            var s3Client = getS3DownloadClient(uri, s3UrlUtil, properties);
            String[] pathParts = s3UrlUtil.splitBucketAndObjectKey(uri);
            String bucket = pathParts[0];
            String objectKey = pathParts[1];
            return s3Client.getObject(b -> b.bucket(bucket).key(objectKey));
        }
        catch (S3Exception | SdkClientException e) {
            throw new StorageException(String.format("Failed to download \"%s\" due to %s", uri, e),
                                       e);
        }
    }


    private URI putInS3IfAbsent(Path path, UnaryOperator<String> properties)
            throws IOException, StorageException {
        return putInS3IfAbsent(path, hashExistingFile(path), properties);
    }

    private URI putInS3IfAbsent(Path path, String hash, UnaryOperator<String> properties)
            throws StorageException {
        String objectName = getObjectName(hash, properties);
        URI bucketUri = URI.create(properties.apply(MpfConstants.S3_RESULTS_BUCKET));
        var s3UrlUtil = S3UrlUtil.get(properties);
        String resultsBucket = s3UrlUtil.getResultsBucketName(bucketUri);
        LOG.info("Storing \"{}\" in S3 bucket \"{}\" with object key \"{}\" ...", path, bucketUri, objectName);

        try {
            var s3Client = getS3UploadClient(s3UrlUtil, properties);
            if (objectExists(s3Client, resultsBucket, objectName)) {
                LOG.info("Did not upload \"{}\" to S3 bucket \"{}\" and object key \"{}\" " +
                               "because a file with the same SHA-256 hash was already there.",
                         path, bucketUri, objectName);
            }
            else {
                s3Client.putObject(r -> r.bucket(resultsBucket).key(objectName), path);
                LOG.info("Successfully stored \"{}\" in S3 bucket \"{}\" with object key \"{}\".",
                         path, bucketUri, objectName);
            }
            return s3UrlUtil.getFullUri(bucketUri, objectName);
        }
        catch (S3Exception | SdkClientException e) {
            LOG.error("Failed to upload {} due to S3 error: {}", path, e);
            // Don't include path so multiple failures appear as one issue in JSON output object.
            throw new StorageException("Failed to upload due to S3 error: " + e, e);
        }
    }

    private static boolean objectExists(S3Client s3Client, String bucket, String key) {
        try {
            s3Client.headObject(b -> b.bucket(bucket).key(key));
            return true;
        }
        catch (NoSuchKeyException | NoSuchBucketException e) {
            return false;
        }
    }


    private static String hashExistingFile(Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            return DigestUtils.sha256Hex(is);
        }
    }


    private static String getObjectName(String hash, UnaryOperator<String> properties) {
        var prefix = properties.apply(MpfConstants.S3_UPLOAD_OBJECT_KEY_PREFIX);
        if (prefix == null || prefix.isBlank()) {
            prefix = "";
        }
        String firstPair = hash.substring(0, 2);
        String secondPair = hash.substring(2, 4);
        return prefix + firstPair + '/' + secondPair + '/' + hash;
    }


    private S3Client getS3DownloadClient(
            String mediaUri,
            S3UrlUtil s3UrlUtil,
            UnaryOperator<String> properties) throws StorageException {
        return _s3ClientCache.getUnchecked(new S3ClientConfig(
                s3UrlUtil.getS3Endpoint(mediaUri),
                _propertiesUtil.getRemoteMediaDownloadRetries(),
                properties));
    }

    private S3Client getS3UploadClient(
            S3UrlUtil s3UrlUtil,
            UnaryOperator<String> properties) throws StorageException {
        var endpoint = s3UrlUtil.getS3Endpoint(
                properties.apply(MpfConstants.S3_RESULTS_BUCKET));
        return _s3ClientCache.getUnchecked(new S3ClientConfig(
                endpoint,
                _propertiesUtil.getHttpStorageUploadRetryCount(),
                properties));
    }


    private static boolean shouldRetry(RetryPolicyContext context, int maxRetries) {
        if (context.originalRequest() instanceof HeadObjectRequest
                && context.httpStatusCode() != null
                && context.httpStatusCode() == 404) {
            // A HEAD request is sent prior to uploading an object to determine if the object
            // already exists and the upload can be avoided. In most cases the object will not
            // exist, so we expect the 404 error in that case.
            return false;
        }
        int attemptsRemaining = maxRetries - context.retriesAttempted();
        LOG.warn("\"{}\" responded with a non-200 status code of {}. " +
                         "There are {} attempts remaining.",
                 context.request().getUri(), context.httpStatusCode(), attemptsRemaining);
        return true;
    }


    private static LoadingCache<S3ClientConfig, S3Client> createClientCache(int cacheSize) {
        return CacheBuilder.newBuilder()
                .maximumSize(cacheSize)
                .<S3ClientConfig, S3Client>removalListener(l -> l.getValue().close())
                .build(CacheLoader.from(S3StorageBackend::buildClient));
    }


    private static S3Client buildClient(S3ClientConfig clientConfig) {
        AwsCredentials credentials;
        if (clientConfig.sessionToken != null && !clientConfig.sessionToken.isBlank()) {
            credentials = AwsSessionCredentials.create(
                    clientConfig.accessKey,
                    clientConfig.secretKey,
                    clientConfig.sessionToken);
        }
        else {
            credentials = AwsBasicCredentials.create(
                    clientConfig.accessKey,
                    clientConfig.secretKey);
        }

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
        return S3Client.builder()
                .region(Region.of(clientConfig.region))
                .endpointOverride(clientConfig.endpoint)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s -> s.pathStyleAccessEnabled(true))
                .overrideConfiguration(o -> o.retryPolicy(retry))
                .build();
    }


    private static class S3ClientConfig {
        final URI endpoint;
        final int retryCount;
        final String accessKey;
        final String secretKey;
        final String sessionToken;
        final String region;

        S3ClientConfig(URI endpoint, int retryCount, UnaryOperator<String> properties) {
            this.endpoint = endpoint;
            this.retryCount = retryCount;
            accessKey = properties.apply(MpfConstants.S3_ACCESS_KEY);
            secretKey = properties.apply(MpfConstants.S3_SECRET_KEY);
            sessionToken = properties.apply(MpfConstants.S3_SESSION_TOKEN);
            region = properties.apply(MpfConstants.S3_REGION);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)  {
                return true;
            }
            if (!(o instanceof S3ClientConfig)) {
                return false;
            }
            var that = (S3ClientConfig) o;
            return endpoint.equals(that.endpoint)
                    && retryCount == that.retryCount
                    && accessKey.equals(that.accessKey)
                    && secretKey.equals(that.secretKey)
                    && Objects.equals(sessionToken, that.sessionToken)
                    && region.equals(that.region);
        }

        @Override
        public int hashCode() {
            return Objects.hash(endpoint, retryCount, accessKey, secretKey, sessionToken, region);
        }
    }
}
