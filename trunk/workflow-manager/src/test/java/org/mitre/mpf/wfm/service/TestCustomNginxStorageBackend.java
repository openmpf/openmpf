/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.io.ByteStreams;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.HttpHostConnectException;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.camel.operations.detection.artifactextraction.ArtifactExtractionRequest;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.MarkupResult;
import org.mitre.mpf.wfm.data.entities.transients.SystemPropertiesSnapshot;
import org.mitre.mpf.wfm.data.entities.transients.TransientJob;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.util.JniLoader;
import org.mitre.mpf.wfm.util.ObjectMapperFactory;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.ThreadUtil;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.Spark;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestCustomNginxStorageBackend {

    private static final long TEST_JOB_ID = 62343245;

    private static final String TEST_FILE = "/samples/video_01.mp4";

    private static final String TEST_FILE_SHA = "5eacf0a11d51413300ee0f4719b7ac7b52b47310a49320703c1d2639ebbc9fea";

    private static final URI SERVICE_URI = URI.create("http://127.0.0.1:5000");

    private final PropertiesUtil _mockPropertiesUtil = mock(PropertiesUtil.class);

    private final InProgressBatchJobsService _mockInProgressJobs = mock(InProgressBatchJobsService.class);

    private static final ObjectMapper _objectMapper = ObjectMapperFactory.customObjectMapper();


    private final StorageBackend _nginxStorageService
            = new CustomNginxStorageBackend(_mockPropertiesUtil, _objectMapper, _mockInProgressJobs);

    private static final AtomicInteger BAD_PATH_POST_COUNT = new AtomicInteger();

    @Rule
    public TemporaryFolder _tempFolder = new TemporaryFolder();

    @BeforeClass
    public static void initClass() {
        startSpark();
        ThreadUtil.start();
    }

    @AfterClass
    public static void tearDownClass() {
        Spark.stop();
        ThreadUtil.shutdown();
    }

    @Before
    public void init() throws StorageException {
        when(_mockPropertiesUtil.getHttpStorageUploadThreadCount())
                .thenReturn(2);

        when(_mockPropertiesUtil.getHttpStorageUploadSegmentSize())
                .thenReturn(2 * 1024 * 1024);

        when(_mockPropertiesUtil.getHttpStorageUploadRetryCount())
                .thenReturn(2);

        setStorageUri(SERVICE_URI.toString());

        BAD_PATH_POST_COUNT.set(0);
    }


    private void setStorageUri(String uri) throws StorageException {
        SystemPropertiesSnapshot propertiesSnapshot = mock(SystemPropertiesSnapshot.class);
        when(propertiesSnapshot.getHttpObjectStorageType())
                .thenReturn(StorageBackend.Type.CUSTOM_NGINX);
        when(propertiesSnapshot.getHttpStorageServiceUri())
                .thenReturn(URI.create(uri));

        TransientJob job = mock(TransientJob.class);
        when(job.getSystemPropertiesSnapshot())
                .thenReturn(propertiesSnapshot);

        when(_mockInProgressJobs.getJob(TEST_JOB_ID))
                .thenReturn(job);
    }

    private MarkupResult createMarkupResult() throws IOException {
        MarkupResult markup = new MarkupResult();
        markup.setJobId(TEST_JOB_ID);
        markup.setMarkupUri(getTestFile().toUri().toString());
        return markup;
    }

    private Path getTestFile() throws IOException {
        URI testFileUri = TestUtil.findFile(TEST_FILE);
        Path filePath = _tempFolder.newFolder().toPath().resolve("temp_file");
        Files.copy(Paths.get(testFileUri), filePath);
        return filePath;
    }

    private static URI getExpectedUri(String sha) throws URISyntaxException {
        return new URIBuilder(SERVICE_URI)
                .setPath("/fs/" + sha)
                .build();
    }

    @Test
    public void throwsExceptionWhenConnectionRefused() throws IOException {
        URI localUri = null;
        try {
            setStorageUri("http://127.0.0.1:12345");
            MarkupResult markup = createMarkupResult();
            localUri = URI.create(markup.getMarkupUri());
            _nginxStorageService.store(markup);
            fail("Expected exception not thrown");
        }
        catch (StorageException e) {
            assertTrue(e.getCause() instanceof HttpHostConnectException);
            assertNotNull(localUri);
            assertTrue(Files.exists(Paths.get(localUri)));
        }
    }


    @Test
    public void throwsExceptionWhenBadStatus() throws IOException {
        URI localUri = null;
        try {
            setStorageUri("http://127.0.0.1:5000/badpath");
            MarkupResult markup = createMarkupResult();
            localUri = URI.create(markup.getMarkupUri());
            _nginxStorageService.store(markup);
            fail("Expected exception");
        }
        catch (StorageException e) {
            assertEquals(3, BAD_PATH_POST_COUNT.intValue());
            assertNotNull(localUri);
            assertTrue(Files.exists(Paths.get(localUri)));
        }
    }


    @Test
    public void canUploadContent() throws StorageException, IOException, URISyntaxException {
        MarkupResult markup = createMarkupResult();
        URI localUri = URI.create(markup.getMarkupUri());
        _nginxStorageService.store(markup);
        String remoteLocation = markup.getMarkupUri();

        assertEquals(getExpectedUri(TEST_FILE_SHA), URI.create(remoteLocation));
        assertFalse(Files.exists(Paths.get(localUri)));
    }

    @Test
    public void testInvalidJson() throws IOException {
        when(_mockPropertiesUtil.getHttpStorageUploadRetryCount())
                .thenReturn(0);
        when(_mockPropertiesUtil.getHttpStorageUploadThreadCount())
                .thenReturn(1);
        try {
            JsonOutputObject outputObject = mock(JsonOutputObject.class);
            when(outputObject.getJobId())
                    .thenReturn(TEST_JOB_ID);
            _nginxStorageService.store(outputObject);
            fail("Expected exception not thrown.");
        }
        catch (StorageException e) {
            assertTrue(e.getCause() instanceof JsonProcessingException);
        }
    }

    @Test
    public void canStoreVideoArtifactRemotely() throws IOException, StorageException, URISyntaxException {
        assertTrue(JniLoader.isLoaded());
        ImmutableMap<Integer, URI> expectedResults = ImmutableMap.of(
                0, getExpectedUri("29373037d0551330acbde76c797cf6b2dadf635dc8c73b09b77ec7dec256abf6"),
                5, getExpectedUri("759574570cf9741a55cb2509ea117d01d42d3d7a01cacc3d5db6541f9730f657"),
                9, getExpectedUri("6036ecde5fe96e9fba9a56593d5bb64605dbc8f1c6ce07f7126b5efda63c05d3"));

        Path testFile = getTestFile();
        ArtifactExtractionRequest request = new ArtifactExtractionRequest(TEST_JOB_ID, 0, testFile.toString(),
                                                                          MediaType.VIDEO, 0);
        request.getActionIndexToMediaIndexes().put(0, Sets.newHashSet(0, 5));
        request.getActionIndexToMediaIndexes().put(1, Sets.newHashSet(5, 9));
        Map<Integer, URI> results = _nginxStorageService.storeVideoArtifacts(request);
        assertEquals(expectedResults, results);
    }

    private static final AtomicBoolean CAUSE_INIT_FAILURE = new AtomicBoolean();
    private static final AtomicBoolean CAUSE_SEGMENT_FAILURE = new AtomicBoolean();
    private static final AtomicBoolean CAUSE_UPLOAD_COMPLETE_FAILURE = new AtomicBoolean();

    @Test
    public void testRetry() throws StorageException, IOException, URISyntaxException {
        CAUSE_INIT_FAILURE.set(true);
        CAUSE_SEGMENT_FAILURE.set(true);
        CAUSE_UPLOAD_COMPLETE_FAILURE.set(true);

        MarkupResult markup = createMarkupResult();
        URI localUri = URI.create(markup.getMarkupUri());

        _nginxStorageService.store(markup);
        String remoteLocation = markup.getMarkupUri();
        assertEquals(getExpectedUri(TEST_FILE_SHA), URI.create(remoteLocation));
        assertFalse(Files.exists(Paths.get(localUri)));
    }



    private static void startSpark() {
        Spark.port(5000);

        Spark.post("/badpath/*", (req, resp) -> {
            BAD_PATH_POST_COUNT.incrementAndGet();
            Spark.halt(500);
            return "";
        });

        Spark.post("/api/uploadS3.php", new SparkUploadRoute());
        Spark.awaitInitialization();
    }


    private static class SparkUploadRoute implements Route {

        private final Map<String, Integer> _lastPartReceived = new HashMap<>();

        private final Table<String, Integer, byte[]> _uploadParts = HashBasedTable.create();

        private final Map<String, DigestOutputStream> _digestStreams = new HashMap<>();

        @Override
        public Object handle(Request req, Response resp) throws IOException, ServletException, NoSuchAlgorithmException {
            if (req.queryParams().contains("init")) {
                if (CAUSE_INIT_FAILURE.getAndSet(false)) {
                    Spark.halt(500, "Init failure requested.");
                }
                String uploadId = UUID.randomUUID().toString();
                _lastPartReceived.put(uploadId, -1);
                _digestStreams.put(uploadId, new DigestOutputStream(ByteStreams.nullOutputStream(),
                                                                    MessageDigest.getInstance("SHA-256")));
                return String.format("{\"upload_id\": \"%s\"}", uploadId);
            }

            String partNumber = req.queryParams("partNumber");
            if (partNumber != null) {
                if (CAUSE_SEGMENT_FAILURE.getAndSet(false)) {
                    Spark.halt(500, "Segment failure requested.");
                }
                return handlePartUpload(req, resp, Integer.valueOf(partNumber));
            }

            String uploadId = req.queryParams("uploadID");
            if (uploadId != null) {
                if (CAUSE_UPLOAD_COMPLETE_FAILURE.getAndSet(false)) {
                    Spark.halt(500, "Complete upload failure requested.");
                }
                return handleUploadComplete(req, uploadId);
            }

            Spark.halt(500);
            return "";
        }

        private synchronized String handlePartUpload(Request req, Response resp, int partNumber) throws IOException, ServletException {
            req.attribute("org.eclipse.jetty.multipartConfig",
                          new MultipartConfigElement("/opt/tmp", -1, -1, Integer.MAX_VALUE));

            String uploadId = req.queryParams("uploadID");

            DigestOutputStream digestOutputStream;
            int lastPartReceived;
            try (InputStream input = req.raw().getPart("file").getInputStream()) {
                lastPartReceived = _lastPartReceived.getOrDefault(uploadId, -1);
                if (partNumber != lastPartReceived + 1) {
                    _uploadParts.put(uploadId, partNumber, IOUtils.toByteArray(input));
                    resp.header("ETag", partNumber + "-tag");
                    return "Unused";
                }
                digestOutputStream = _digestStreams.get(uploadId);
                IOUtils.copy(input, digestOutputStream);
                lastPartReceived = partNumber;
            }

            while (true) {
                byte[] uploadPart = _uploadParts.remove(uploadId, lastPartReceived + 1);
                if (uploadPart == null) {
                    _lastPartReceived.put(uploadId, lastPartReceived);
                    break;
                }
                digestOutputStream.write(uploadPart);
                lastPartReceived += 1;
            }

            resp.header("ETag", partNumber + "-tag");
            return "Unused";
        }

        private String handleUploadComplete(Request req, String uploadId) throws IOException {
            validateCompleteUploadRequest(req, uploadId);

            _lastPartReceived.remove(uploadId);
            _uploadParts.row(uploadId).clear();

            DigestOutputStream digestStream = _digestStreams.remove(uploadId);
            digestStream.close();
            byte[] digest = digestStream.getMessageDigest().digest();

            String hash = Hex.encodeHexString(digest);
            return String.format("{\"status\":[{\"relative_url\":\"/fs/%s\"}]}", hash);
        }

        private void validateCompleteUploadRequest(Request req, String uploadId) throws IOException {
            JsonNode jsonNode = _objectMapper.readTree(req.body());
            JsonNode completeMultipartUpload = jsonNode.get("CompleteMultipartUpload");
            int i = -1;
            for (JsonNode node : completeMultipartUpload) {
                i += 1;
                int partNumber = node.get("PartNumber").asInt();
                String eTag = node.get("ETag").asText();
                if (partNumber != i || !eTag.equals(partNumber + "-tag")) {
                    Spark.halt(500, "Invalid complete upload JSON");
                }
            }
            if (i != _lastPartReceived.get(uploadId)) {
                Spark.halt(500, "Invalid complete upload JSON");
            }
        }
    }
}
