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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.google.common.io.ByteStreams;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.rest.api.MediaSelectorType;
import org.mitre.mpf.rest.api.pipelines.*;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.camel.operations.detection.artifactextraction.ArtifactExtractionRequest;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.*;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import spark.Spark;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

public class TestS3StorageBackend {

    private final PropertiesUtil _mockPropertiesUtil = mock(PropertiesUtil.class);

    private final LocalStorageBackend _mockLocalStorageBackend = mock(LocalStorageBackend.class);

    private final InProgressBatchJobsService _mockInProgressJobs = mock(InProgressBatchJobsService.class);

    private final WorkflowPropertyService _mockWorkflowPropertyService
            = mock(WorkflowPropertyService.class);

    private S3StorageBackend _s3StorageBackend;

    @Rule
    public TemporaryFolder _tempFolder = new TemporaryFolder();

    private static final String S3_HOST = "http://localhost:5000/";

    private static final String RESULTS_BUCKET = "RESULTS_BUCKET";

    private static final String ACCESS_KEY = "<MY_ACCESS_KEY>";

    private static final String EXPECTED_HASH
                    = "5eacf0a11d51413300ee0f4719b7ac7b52b47310a49320703c1d2639ebbc9fea";

    private static volatile String EXPECTED_OBJECT_KEY;

    private static final String BUCKET_WITH_EXISTING_OBJECT = "EXISTING_OBJECT_BUCKET";

    private static final Collection<String> OBJECTS_POSTED = Collections.synchronizedList(new ArrayList<>());

    private static final AtomicInteger GET_COUNT = new AtomicInteger(0);

    private static final AtomicInteger REQUESTED_GET_FAILURES = new AtomicInteger(0);

    private static final AtomicInteger REQUESTED_PUT_FAILURES = new AtomicInteger(0);

    private URI _expectedUri;


    @BeforeClass
    public static void initClass() {
        startSpark();
    }

    @AfterClass
    public static void tearDownClass() {
        Spark.stop();
    }

    @Before
    public void init() {
        EXPECTED_OBJECT_KEY = "5e/ac/" + EXPECTED_HASH;
        _expectedUri = URI.create(S3_HOST + RESULTS_BUCKET + '/' + EXPECTED_OBJECT_KEY);

        OBJECTS_POSTED.clear();
        GET_COUNT.set(0);
        REQUESTED_GET_FAILURES.set(0);
        REQUESTED_PUT_FAILURES.set(0);

        when(_mockPropertiesUtil.getS3ClientCacheCount())
                .thenReturn(20);

        _s3StorageBackend = new S3StorageBackendImpl(
                _mockPropertiesUtil, _mockLocalStorageBackend, _mockInProgressJobs,
                new AggregateJobPropertiesUtil(_mockPropertiesUtil,
                                               _mockWorkflowPropertyService), null);
    }

    private static Map<String, String> getS3Properties() {
        Map<String, String> properties = new HashMap<>();
        properties.put(MpfConstants.S3_RESULTS_BUCKET, S3_HOST + RESULTS_BUCKET);
        properties.put(MpfConstants.S3_SECRET_KEY, "<MY_SECRET_KEY>");
        properties.put(MpfConstants.S3_ACCESS_KEY, ACCESS_KEY);
        properties.put(MpfConstants.S3_REGION, "us-east-1");
        return properties;
    }

    private Path getTestFileCopy() throws IOException {
        return copyTestFile("/samples/video_01.mp4");
    }

    private Path copyTestFile(String path) throws IOException {
        URI testFileUri = TestUtil.findFile(path);
        Path filePath = _tempFolder.newFolder().toPath().resolve("temp_file");
        Files.copy(Paths.get(testFileUri), filePath);
        return filePath;
    }


    @Test
    public void downloadsFromS3WhenHasKeys() throws StorageException {
        assertTrue(S3StorageBackend.requiresS3MediaDownload(getS3Properties()::get));
    }

    @Test
    public void downloadsFromS3WhenResultsBucketMissing() throws StorageException {
        Map<String, String> s3Properties = getS3Properties();
        s3Properties.remove(MpfConstants.S3_RESULTS_BUCKET);
        assertTrue(S3StorageBackend.requiresS3MediaDownload(getS3Properties()::get));
    }

    @Test
    public void downloadsFromS3WhenUploadOnlyFalse() throws StorageException {
        Map<String, String> s3Properties = getS3Properties();
        s3Properties.put(MpfConstants.S3_UPLOAD_ONLY, "false");
        assertTrue(S3StorageBackend.requiresS3MediaDownload(s3Properties::get));
    }

    @Test
    public void doesNotDownloadFromS3WhenUploadOnlyTrue() throws StorageException {
        Map<String, String> s3Properties = getS3Properties();
        s3Properties.put(MpfConstants.S3_UPLOAD_ONLY, "true");
        assertFalse(S3StorageBackend.requiresS3MediaDownload(s3Properties::get));
    }

    @Test
    public void doesNotDownloadFromS3WhenNoKeys() throws StorageException {
        Map<String, String> s3Properties = getS3Properties();
        s3Properties.remove(MpfConstants.S3_SECRET_KEY);
        s3Properties.remove(MpfConstants.S3_ACCESS_KEY);
        assertFalse(S3StorageBackend.requiresS3MediaDownload(s3Properties::get));
    }

    @Test(expected = StorageException.class)
    public void doesNotDownloadFromS3WhenNoAccessKey() throws StorageException {
        Map<String, String> s3Properties = getS3Properties();
        s3Properties.remove(MpfConstants.S3_ACCESS_KEY);
        S3StorageBackend.requiresS3MediaDownload(s3Properties::get);
    }

    @Test(expected = StorageException.class)
    public void doesNotDownloadFromS3WhenNoSecretKey() throws StorageException {
        Map<String, String> s3Properties = getS3Properties();
        s3Properties.remove(MpfConstants.S3_SECRET_KEY);
        S3StorageBackend.requiresS3MediaDownload(s3Properties::get);
    }

    @Test
    public void uploadsToS3WhenHasKeysAndResultsBucket() throws StorageException {
        assertCanUpload(getS3Properties());
    }

    @Test
    public void doesNotUploadToS3WhenResultsBucketMissing() throws StorageException {
        Map<String, String> s3Properties = getS3Properties();
        s3Properties.remove(MpfConstants.S3_RESULTS_BUCKET);
        assertCanNotUpload(s3Properties);
    }

    @Test
    public void uploadsToS3WhenUploadOnlyFalse() throws StorageException {
        Map<String, String> s3Properties = getS3Properties();
        s3Properties.put(MpfConstants.S3_UPLOAD_ONLY, "false");
        assertCanUpload(s3Properties);
    }

    @Test
    public void uploadsToS3WhenUploadOnlyTrue() throws StorageException {
        Map<String, String> s3Properties = getS3Properties();
        s3Properties.put(MpfConstants.S3_UPLOAD_ONLY, "true");
        assertCanUpload(s3Properties);
    }


    @Test
    public void doesNotUploadToS3WhenNoKeys() {
        Map<String, String> s3Properties = getS3Properties();
        s3Properties.remove(MpfConstants.S3_SECRET_KEY);
        s3Properties.remove(MpfConstants.S3_ACCESS_KEY);
        assertThrowsWhenCallingCanStore(s3Properties);
    }

    @Test
    public void doesNotUploadToS3WhenNoAccessKey() {
        Map<String, String> s3Properties = getS3Properties();
        s3Properties.remove(MpfConstants.S3_ACCESS_KEY);
        assertThrowsWhenCallingCanStore(s3Properties);
    }

    @Test
    public void doesNotUploadToS3WhenNoSecretKey() {
        Map<String, String> s3Properties = getS3Properties();
        s3Properties.remove(MpfConstants.S3_SECRET_KEY);
        assertThrowsWhenCallingCanStore(s3Properties);
    }

    private void assertCanUpload(Map<String, String> properties) throws StorageException {
        JsonOutputObject outputObject = setJobProperties(properties);
        assertTrue(_s3StorageBackend.canStore(outputObject));
    }

    private void assertCanNotUpload(Map<String, String> properties) throws StorageException {
        JsonOutputObject outputObject = setJobProperties(properties);
        assertFalse(_s3StorageBackend.canStore(outputObject));
    }

    private void assertThrowsWhenCallingCanStore(Map<String, String> properties) {
        try {
            JsonOutputObject outputObject = setJobProperties(properties);
            _s3StorageBackend.canStore(outputObject);
            fail("Expected StorageException");
        }
        catch (StorageException expected) {
        }
    }

    private JsonOutputObject setJobProperties(Map<String, String> properties) {
        long jobId = 123;
        String exportedJobId = "localhost-123";
        when(_mockPropertiesUtil.getJobIdFromExportedId(exportedJobId))
                .thenReturn(jobId);

        JsonOutputObject outputObject = mock(JsonOutputObject.class);
        when(outputObject.getJobId())
                .thenReturn(exportedJobId);

        var mockJob = mock(BatchJob.class);
        when(mockJob.getJobProperties())
                .thenReturn(ImmutableMap.copyOf(properties));
        when(_mockInProgressJobs.getJob(jobId))
                .thenReturn(mockJob);
        return outputObject;
    }


    @Test
    public void throwsExceptionWhenBadUri() {
        verifyThrowsExceptionWhenDownloading("NOT_A_URI");
        verifyThrowsExceptionWhenDownloading("http:://asdf/qwer/asdf");
        verifyThrowsExceptionWhenDownloading(S3_HOST);
        verifyThrowsExceptionWhenDownloading(S3_HOST + RESULTS_BUCKET);
    }


    private void verifyThrowsExceptionWhenDownloading(String uri) {
        Map<String, String> s3Properties = getS3Properties();

        Media media = mock(Media.class);
        when(media.getUri())
                .thenReturn(uri);
        try {
            _s3StorageBackend.downloadFromS3(media, s3Properties::get);
            fail("Expected StorageException");
        }
        catch (StorageException e) {
            assertEquals(0, GET_COUNT.get());
        }
    }


    @Test
    public void throwsExceptionWhenBadResultsBucket() throws IOException {
        Map<String, String> s3Properties = getS3Properties();

        s3Properties.put(MpfConstants.S3_RESULTS_BUCKET, "BUCKET");
        verifyThrowsExceptionWhenStoring(s3Properties);

        s3Properties.put(MpfConstants.S3_RESULTS_BUCKET, S3_HOST);
        verifyThrowsExceptionWhenStoring(s3Properties);
    }


    private void verifyThrowsExceptionWhenStoring(Map<String, String> s3Properties) throws IOException {
        Path filePath = getTestFileCopy();

        try {
            JsonOutputObject outputObject = setJobProperties(s3Properties);
            when(_mockLocalStorageBackend.store(same(outputObject), any()))
                    .thenReturn(filePath.toUri());

            _s3StorageBackend.store(outputObject, new MutableObject<>());
            fail("Expected StorageException");
        }
        catch(StorageException e) {
            Files.exists(filePath);
        }
    }



    @Test
    public void canStoreArtifacts() throws IOException, StorageException {
        ArtifactExtractionRequest request = createArtifactExtractionRequest();

        Path filePath0 = getTestFileCopy();
        Path filePath1 = copyTestFile("/samples/meds1.jpg");
        when(_mockLocalStorageBackend.storeArtifacts(request))
                .thenReturn(new ImmutableTable.Builder<Integer, Integer, URI>()
                        .put(0, 2, filePath0.toUri())
                        .put(1, 3, filePath1.toUri())
                        .build());

        assertTrue(_s3StorageBackend.canStore(request));
        Table<Integer, Integer, URI> results = _s3StorageBackend.storeArtifacts(request);
        Table<Integer, Integer, URI> expectedResults = new ImmutableTable.Builder<Integer, Integer, URI>()
                                                       .put(0, 2, _expectedUri)
                                                       .put(1, 3, filePath1.toUri())
                                                       .build();

        assertEquals(expectedResults, results);
        assertFalse(Files.exists(filePath0));
        assertTrue(Files.exists(filePath1));
        assertTrue(OBJECTS_POSTED.contains(RESULTS_BUCKET + '/' + EXPECTED_OBJECT_KEY));
        assertTrue(OBJECTS_POSTED.contains(
                RESULTS_BUCKET + "/c0/67/c067e7eed23a0fe022140c30dbfa993ae720309d6567a803d111ecec739a6713"));
    }


    private ArtifactExtractionRequest createArtifactExtractionRequest() {
        long jobId = 1243;
        long mediaId = 432;

        var media = mock(Media.class);

        var job = mock(BatchJob.class);
        when(job.getMedia(mediaId))
                .thenReturn(media);
        when(job.getJobProperties())
                .thenReturn(ImmutableMap.of(
                        MpfConstants.S3_ACCESS_KEY, ACCESS_KEY,
                        MpfConstants.S3_SECRET_KEY, ""
                ));

        when(media.getMediaSpecificProperties())
                .thenReturn(ImmutableMap.of(
                        MpfConstants.S3_RESULTS_BUCKET, S3_HOST + RESULTS_BUCKET,
                        MpfConstants.S3_SECRET_KEY, "<SECRET_KEY>",
                        MpfConstants.S3_REGION, "us-east-1"
                ));

        when(_mockInProgressJobs.getJob(jobId))
                .thenReturn(job);


        var request = mock(ArtifactExtractionRequest.class);
        when(request.getJobId())
                .thenReturn(jobId);
        when(request.getMediaId())
                .thenReturn(mediaId);

        return request;
    }


    @Test
    public void canStoreMarkupRequest() throws IOException, StorageException {
        long jobId = 534;
        long mediaId = 421;
        Path filePath = getTestFileCopy();

        var markupResult = mock(MarkupResult.class);
        var job = mock(BatchJob.class, RETURNS_DEEP_STUBS);
        var media = mock(Media.class);

        var algorithm = new Algorithm("TEST_ALGO", "description", ActionType.DETECTION, "TEST",
                                      OptionalInt.empty(),
                                      new Algorithm.Requires(List.of()),
                                      new Algorithm.Provides(List.of(), List.of()),
                                      true, true);
        var action = new Action(
                "TEST_ACTION", "description", algorithm.name(),
                List.of(new ActionProperty(MpfConstants.S3_ACCESS_KEY, ACCESS_KEY)));
        var task = new Task("TEST_TASK", "description", List.of(action.name()));
        var pipeline = new Pipeline("TEST_PIPELINE", "description",
                                    List.of(task.name()));
        var pipelineElements = new JobPipelineElements(
                pipeline, List.of(task), List.of(action),
                List.of(algorithm));

        when(markupResult.getJobId())
                .thenReturn(jobId);
        when(markupResult.getMediaId())
                .thenReturn(mediaId);
        when(markupResult.getMarkupUri())
                .thenReturn(filePath.toUri().toString());

        when(job.getMedia(mediaId))
                .thenReturn(media);

        when(job.getPipelineElements())
                .thenReturn(pipelineElements);

        when(media.getMediaSpecificProperties())
                .thenReturn(ImmutableMap.of(MpfConstants.S3_RESULTS_BUCKET, S3_HOST + RESULTS_BUCKET));
        when(media.getType())
                .thenReturn(Optional.of(MediaType.VIDEO));

        var overriddenAlgoProps
                = ImmutableMap.of("TEST_ALGO",
                                  ImmutableMap.of(MpfConstants.S3_SECRET_KEY, "<SECRET_KEY>"));
        when(job.getOverriddenAlgorithmProperties())
                .thenReturn(overriddenAlgoProps);

        when(_mockWorkflowPropertyService.getPropertyValue(
                    eq(MpfConstants.S3_REGION), eq(MediaType.VIDEO), any()))
                .thenReturn("us-east-1");

        when(job.getJobProperties())
                .thenReturn(ImmutableMap.of());

        when(_mockInProgressJobs.getJob(jobId))
                .thenReturn(job);

        assertTrue(_s3StorageBackend.canStore(markupResult));


        _s3StorageBackend.store(markupResult);

        verify(markupResult)
                .setMarkupUri(_expectedUri.toString());

        assertFalse(Files.exists(filePath));
        assertEquals(List.of(RESULTS_BUCKET + '/' + EXPECTED_OBJECT_KEY), OBJECTS_POSTED);
    }


    @Test
    public void canStoreJsonOutputObject() throws IOException, StorageException {
        Path filePath = getTestFileCopy();

        JsonOutputObject outputObject = setJobProperties(getS3Properties());
        when(_mockLocalStorageBackend.store(same(outputObject), any()))
                .thenReturn(filePath.toUri());

        URI remoteUri = _s3StorageBackend.store(outputObject, new MutableObject<>());
        assertEquals(_expectedUri, remoteUri);
        assertFalse(Files.exists(filePath));
        assertEquals(List.of(RESULTS_BUCKET + '/' + EXPECTED_OBJECT_KEY), OBJECTS_POSTED);
    }


    @Test
    public void canSetKeyPrefix() throws IOException, StorageException {
        Path filePath = getTestFileCopy();

        var properties = getS3Properties();
        properties.put(MpfConstants.S3_UPLOAD_OBJECT_KEY_PREFIX, "prefix/");

        JsonOutputObject outputObject = setJobProperties(properties);
        when(_mockLocalStorageBackend.store(same(outputObject), any()))
                .thenReturn(filePath.toUri());

        EXPECTED_OBJECT_KEY = "prefix/" + EXPECTED_OBJECT_KEY;

        URI remoteUri = _s3StorageBackend.store(outputObject, new MutableObject<>());
        assertEquals(URI.create(S3_HOST + RESULTS_BUCKET + "/" + EXPECTED_OBJECT_KEY), remoteUri);
        assertFalse(Files.exists(filePath));
        assertEquals(List.of(RESULTS_BUCKET + '/' + EXPECTED_OBJECT_KEY), OBJECTS_POSTED);
    }


    @Test
    public void doesNotStoreDuplicateOutputObject() throws IOException, StorageException {
        Path filePath = getTestFileCopy();

        Map<String, String> s3Properties = getS3Properties();
        s3Properties.put(MpfConstants.S3_RESULTS_BUCKET, S3_HOST + BUCKET_WITH_EXISTING_OBJECT);

        JsonOutputObject outputObject = setJobProperties(s3Properties);
        when(_mockLocalStorageBackend.store(same(outputObject), any()))
                .thenReturn(filePath.toUri());

        URI remoteUri = _s3StorageBackend.store(outputObject, new MutableObject<>());
        assertEquals(URI.create(S3_HOST + BUCKET_WITH_EXISTING_OBJECT + '/' + EXPECTED_OBJECT_KEY), remoteUri);
        assertFalse(Files.exists(filePath));
        assertTrue(OBJECTS_POSTED.isEmpty());
    }


    @Test
    public void canHandleConnectionRefused() throws IOException {
        Map<String, String> s3Properties = getS3Properties();
        s3Properties.put(MpfConstants.S3_RESULTS_BUCKET, "http://localhost:5001/" + RESULTS_BUCKET);
        Path filePath = getTestFileCopy();

        JsonOutputObject outputObject = setJobProperties(s3Properties);
        when(_mockLocalStorageBackend.store(same(outputObject), any()))
                .thenReturn(filePath.toUri());

        try {
            _s3StorageBackend.store(outputObject, new MutableObject<>());
            fail("Expected StorageException to be thrown.");
        }
        catch (StorageException expected) {
            assertTrue(OBJECTS_POSTED.isEmpty());
            assertTrue(Files.exists(filePath));
        }
    }


    @Test
    public void canRetryUploadAndFailWhenServerError() throws IOException {
        int retryCount = 2;
        when(_mockPropertiesUtil.getHttpStorageUploadRetryCount())
                .thenReturn(retryCount);

        Path filePath = getTestFileCopy();
        Map<String, String> s3Properties = getS3Properties();
        s3Properties.put(MpfConstants.S3_RESULTS_BUCKET, S3_HOST + "BAD_BUCKET");

        JsonOutputObject outputObject = setJobProperties(s3Properties);
        when(_mockLocalStorageBackend.store(same(outputObject), any()))
                .thenReturn(filePath.toUri());

        try {
            _s3StorageBackend.store(outputObject, new MutableObject<>());
            fail("Expected StorageException to be thrown.");
        }
        catch (StorageException expected) {
            assertTrue(Files.exists(filePath));
            String expectedObject = "BAD_BUCKET/" + EXPECTED_OBJECT_KEY;
            long numAttempts = OBJECTS_POSTED.stream()
                    .filter(expectedObject::equals)
                    .count();
            assertEquals(retryCount + 1, numAttempts);
        }
    }


    @Test
    public void canRetryUploadAndRecoverWhenServerError() throws IOException, StorageException {
        REQUESTED_PUT_FAILURES.set(2);
        when(_mockPropertiesUtil.getHttpStorageUploadRetryCount())
                .thenReturn(2);

        Path filePath = getTestFileCopy();

        JsonOutputObject outputObject = setJobProperties(getS3Properties());
        when(_mockLocalStorageBackend.store(same(outputObject), any()))
                .thenReturn(filePath.toUri());

        URI remoteUri = _s3StorageBackend.store(outputObject, new MutableObject<>());
        assertEquals(_expectedUri, remoteUri);
        assertFalse(Files.exists(filePath));

        // two failures one success
        assertEquals(3, OBJECTS_POSTED.size());

        String expectedObject = RESULTS_BUCKET + '/' + EXPECTED_OBJECT_KEY;
        boolean allCorrectObject = OBJECTS_POSTED.stream()
                .allMatch(expectedObject::equals);
        assertTrue(allCorrectObject);
    }


    @Test
    public void canDownloadFromS3() throws IOException, StorageException {
        Map<String, String> s3Properties = getS3Properties();
        Path localPath = _tempFolder.newFolder().toPath().resolve("temp_downloaded_media");

        Media media = mock(Media.class);
        when(media.getUri())
                .thenReturn(_expectedUri.toString());
        when(media.getLocalPath())
                .thenReturn(localPath);

        assertFalse(Files.exists(localPath));

        _s3StorageBackend.downloadFromS3(media, s3Properties::get);

        assertTrue(Files.exists(localPath));
        String sha;
        try (InputStream is = Files.newInputStream(localPath)) {
            sha = DigestUtils.sha256Hex(is);
        }
        assertEquals(EXPECTED_HASH, sha);
    }


    @Test
    public void canRetryDownloadAndFailWhenServerError() throws IOException {
        int retryCount = 2;
        when(_mockPropertiesUtil.getRemoteMediaDownloadRetries())
                .thenReturn(retryCount);

        Path localPath = _tempFolder.newFolder().toPath().resolve("temp_downloaded_media");

        Media media = mock(Media.class);
        when(media.getUri())
                .thenReturn(S3_HOST + "BAD_BUCKET/12/34/1234567");
        when(media.getLocalPath())
                .thenReturn(localPath);

        try {
            _s3StorageBackend.downloadFromS3(media, getS3Properties()::get);
            fail("Expected StorageException");
        }
        catch (StorageException e) {
            assertFalse(Files.exists(localPath));
            assertEquals(retryCount + 1, GET_COUNT.get());
        }
    }


    @Test
    public void canRetryDownloadAndRecoverWhenServerError() throws IOException, StorageException {
        REQUESTED_GET_FAILURES.set(2);
        when(_mockPropertiesUtil.getRemoteMediaDownloadRetries())
                .thenReturn(2);

        Path localPath = _tempFolder.newFolder().toPath().resolve("temp_downloaded_media");
        Media media = mock(Media.class);
        when(media.getUri())
                .thenReturn(_expectedUri.toString());
        when(media.getLocalPath())
                .thenReturn(localPath);

        assertFalse(Files.exists(localPath));

        _s3StorageBackend.downloadFromS3(media, getS3Properties()::get);

        assertTrue(Files.exists(localPath));
        String sha;
        try (InputStream is = Files.newInputStream(localPath)) {
            sha = DigestUtils.sha256Hex(is);
        }
        assertEquals(EXPECTED_HASH, sha);

        // Two failed attempts and one successful
        assertEquals(3, GET_COUNT.get());
    }


    @Test
    public void throwsStorageExceptionWhenRemoteFileMissing() throws IOException {
        Path localPath = _tempFolder.newFolder().toPath().resolve("temp_downloaded_media");

        Media media = mock(Media.class);
        when(media.getUri())
                .thenReturn(S3_HOST + "BAD_BUCKET/12/34/1234567");
        when(media.getLocalPath())
                .thenReturn(localPath);

        try {
            _s3StorageBackend.downloadFromS3(media, getS3Properties()::get);
            fail("Expected StorageException");
        }
        catch (StorageException e) {
            assertFalse(Files.exists(localPath));
        }
    }


    @Test
    public void canStoreDerivativeMedia() throws IOException, StorageException {
        long jobId = 534;
        long parentMediaId = 420;
        long derivativeMediaId = 421;
        Path filePath = getTestFileCopy();

        var parentMedia = mock(Media.class);
        var derivativeMedia = mock(MediaImpl.class);

        var job = mock(BatchJob.class, RETURNS_DEEP_STUBS);

        when(job.getId())
                .thenReturn(jobId);

        when(job.getMedia(parentMediaId))
                .thenReturn(parentMedia);

        when(job.getMedia(derivativeMediaId))
                .thenReturn(derivativeMedia);

        when(job.getJobProperties())
                .thenReturn(ImmutableMap.copyOf(getS3Properties()));

        when(parentMedia.getMediaSpecificProperties())
                .thenReturn(ImmutableMap.of());

        when(derivativeMedia.getId())
                .thenReturn(derivativeMediaId);

        when(derivativeMedia.getLocalPath())
                .thenReturn(filePath);

        when(_mockInProgressJobs.getJob(jobId))
                .thenReturn(job);


        assertTrue(_s3StorageBackend.canStoreDerivativeMedia(job, parentMediaId));

        _s3StorageBackend.storeDerivativeMedia(job, derivativeMedia);

        verify(_mockInProgressJobs)
                .addStorageUri(jobId, derivativeMediaId, _expectedUri.toString());

        assertEquals(List.of(RESULTS_BUCKET + '/' + EXPECTED_OBJECT_KEY), OBJECTS_POSTED);
    }


    @Test
    public void canHandleStoreDerivativeMediaFailure() throws IOException, StorageException {
        long jobId = 534;
        long parentMediaId = 420;
        long derivativeMediaId = 421;
        Path filePath = getTestFileCopy();

        var parentMedia = mock(MediaImpl.class);
        var derivativeMedia = mock(MediaImpl.class);

        var job = mock(BatchJob.class, RETURNS_DEEP_STUBS);

        when(job.getId())
                .thenReturn(jobId);

        when(job.getMedia(parentMediaId))
                .thenReturn(parentMedia);

        when(job.getMedia(derivativeMediaId))
                .thenReturn(derivativeMedia);

        when(job.getJobProperties())
                .thenReturn(ImmutableMap.copyOf(getS3Properties()));

        when(parentMedia.getMediaSpecificProperties())
                .thenReturn(ImmutableMap.of());

        when(derivativeMedia.getId())
                .thenReturn(derivativeMediaId);

        when(derivativeMedia.getLocalPath())
                .thenReturn(filePath);

        when(_mockInProgressJobs.getJob(jobId))
                .thenReturn(job);


        assertTrue(_s3StorageBackend.canStoreDerivativeMedia(job, parentMediaId));

        _s3StorageBackend.storeDerivativeMedia(job, derivativeMedia);

        verify(_mockInProgressJobs)
                .addStorageUri(jobId, derivativeMediaId, _expectedUri.toString());

        assertEquals(List.of(RESULTS_BUCKET + '/' + EXPECTED_OBJECT_KEY), OBJECTS_POSTED);
    }


    @Test
    public void canStoreMediaSelectorOutput() throws StorageException, IOException {
        var job = mock(BatchJob.class);
        when(job.getJobProperties())
                .thenReturn(ImmutableMap.copyOf(getS3Properties()));

        var media = mock(Media.class);
        when(media.getMediaSpecificProperties())
                .thenReturn(ImmutableMap.of());

        assertThat(_s3StorageBackend.canStoreMediaSelectorsOutput(job, media)).isTrue();

        var outputProcessor = mock(StorageService.OutputProcessor.class);
        Path filePath = getTestFileCopy();
        when(_mockLocalStorageBackend.storeMediaSelectorsOutput(
                    job, media, MediaSelectorType.JSON_PATH, outputProcessor))
                .thenReturn(filePath.toUri());

        var remoteUri = _s3StorageBackend.storeMediaSelectorsOutput(
                job,
                media,
                MediaSelectorType.JSON_PATH,
                outputProcessor);

        assertThat(remoteUri).isEqualTo(_expectedUri);
        assertThat(filePath).doesNotExist();
        assertThat(OBJECTS_POSTED)
                .singleElement()
                .isEqualTo(RESULTS_BUCKET + '/' + EXPECTED_OBJECT_KEY);
    }


    private static void startSpark() {
        Spark.port(5000);
        IntUnaryOperator decrementUntilZero = i -> Math.max(i - 1, 0);

        Spark.before((req, resp) -> {
            if (!req.headers("Authorization").contains(ACCESS_KEY)) {
                Spark.halt(403, "Unauthorized");
            }
        });

        // S3 client uses the HTTP HEAD method to check if object exists.
        Spark.head("/:bucket/*", (req, resp) -> {
            String bucket = req.params(":bucket");
            String key = req.splat()[0];
            if (BUCKET_WITH_EXISTING_OBJECT.equals(bucket) && EXPECTED_OBJECT_KEY.equals(key)) {
                Spark.halt(200);
            }
            Spark.halt(404);
            return "";
        });

        Spark.get("/:bucket/*", (req, resp) -> {
            GET_COUNT.incrementAndGet();

            String bucket = req.params(":bucket");
            String key = req.splat()[0];
            if (REQUESTED_GET_FAILURES.getAndUpdate(decrementUntilZero) > 0
                    || !RESULTS_BUCKET.equals(bucket)
                    || !EXPECTED_OBJECT_KEY.equals(key)) {
                Spark.halt(500);
            }
            Path path = Paths.get(TestUtil.findFile("/samples/video_01.mp4"));
            long fileSize = Files.size(path);
            resp.header("Content-Length", String.valueOf(fileSize));

            try (OutputStream out = resp.raw().getOutputStream()) {
                Files.copy(path, out);
            }
            resp.raw().flushBuffer();
            return "";
        });

        Spark.put("/:bucket/*", (req, resp) -> {
            String bucket = req.params(":bucket");
            String key = req.splat()[0];
            OBJECTS_POSTED.add(bucket + '/' + key);

            if (REQUESTED_PUT_FAILURES.getAndUpdate(decrementUntilZero) > 0
                    || !RESULTS_BUCKET.equals(bucket)
                    || !EXPECTED_OBJECT_KEY.equals(key)) {
                Spark.halt(500);
            }
            try (InputStream is = req.raw().getInputStream()) {
                ByteStreams.exhaust(is);
            }
            return "";
        });

        Spark.awaitInitialization();
    }
}
