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
import com.google.common.collect.Table;
import com.google.common.io.ByteStreams;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.apache.http.conn.HttpHostConnectException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
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
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestCustomNginxStorageBackend {

    private static final URI SERVICE_URI = URI.create("http://127.0.0.1:5000");

    private final PropertiesUtil _mockPropertiesUtil = mock(PropertiesUtil.class);

    private static final ObjectMapper _objectMapper = ObjectMapperFactory.customObjectMapper();


    private final CustomNginxStorageBackend _nginxStorageService
            = new CustomNginxStorageBackend(_mockPropertiesUtil, _objectMapper);

    private static final AtomicInteger BAD_PATH_POST_COUNT = new AtomicInteger();

    @BeforeClass
    public static void initClass() {
        startSpark();
        ThreadUtil.start();
    }

    @AfterClass
    public static void tearDownClass() {
        Spark.stop();
    }

    @Before
    public void init() {
        when(_mockPropertiesUtil.getHttpObjectStorageType())
                .thenReturn(StorageBackend.Type.CUSTOM_NGINX);

        when(_mockPropertiesUtil.getHttpStorageServiceUri())
                .thenReturn(URI.create("http://127.0.0.1:5000"));

        when(_mockPropertiesUtil.getHttpStorageUploadThreadCount())
                .thenReturn(2);

        when(_mockPropertiesUtil.getHttpStorageUploadSegmentSize())
                .thenReturn(2 * 1024 * 1024);

        when(_mockPropertiesUtil.getHttpStorageUploadRetryCount())
                .thenReturn(2);


        BAD_PATH_POST_COUNT.set(0);
    }



    @Test
    public void throwsExceptionWhenConnectionRefused() {
        try {
            _nginxStorageService.storeAsJson(URI.create("http://127.0.0.1:12345"), getSampleData());
            fail("Expected exception not thrown");
        }
        catch (StorageException e) {
            assertTrue(e.getCause() instanceof HttpHostConnectException);
        }
    }


    @Test
    public void throwsExceptionWhenBadStatus() {
        try {
            _nginxStorageService.storeAsJson(URI.create("http://127.0.0.1:5000/badpath"), getSampleData());
            fail("Expected exception");
        }
        catch (StorageException e) {
            assertEquals(3, BAD_PATH_POST_COUNT.intValue());
        }
    }


    @Test
    public void canUploadContent() throws StorageException {
        String location = _nginxStorageService.storeAsJson(SERVICE_URI, getSampleData());
        assertEquals(
                "http://127.0.0.1:5000/fs/d0d8582bd03ef1efd8ad2891c132f84a095fe167c50e3c503eebee0548f02016",
                location);
    }

    @Test
    public void testInvalidJson() {
        when(_mockPropertiesUtil.getHttpStorageUploadRetryCount())
                .thenReturn(0);
        when(_mockPropertiesUtil.getHttpStorageUploadThreadCount())
                .thenReturn(1);
        try {
            _nginxStorageService.storeAsJson(SERVICE_URI, new Object() { });
            fail("Expected exception not thrown.");
        }
        catch (StorageException e) {
            assertTrue(e.getCause() instanceof JsonProcessingException);
        }

    }


    private static final AtomicBoolean CAUSE_INIT_FAILURE = new AtomicBoolean();
    private static final AtomicBoolean CAUSE_SEGMENT_FAILURE = new AtomicBoolean();
    private static final AtomicBoolean CAUSE_UPLOAD_COMPLETE_FAILURE = new AtomicBoolean();

    @Test
    public void testRetry() throws StorageException {
        CAUSE_INIT_FAILURE.set(true);
        CAUSE_SEGMENT_FAILURE.set(true);
        CAUSE_UPLOAD_COMPLETE_FAILURE.set(true);
        String location = _nginxStorageService.storeAsJson(SERVICE_URI, getSampleData());
        assertEquals(
                "http://127.0.0.1:5000/fs/d0d8582bd03ef1efd8ad2891c132f84a095fe167c50e3c503eebee0548f02016",
                location);
    }



    private static Iterable<Integer> getSampleData() {
        return () -> IntStream.range(0, 1024 * 1024).iterator();
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
