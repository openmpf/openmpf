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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.wfm.camel.operations.detection.artifactextraction.ArtifactExtractionRequest;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.entities.persistent.MarkupResult;
import org.mitre.mpf.wfm.data.entities.transients.TransientDetectionSystemProperties;
import org.mitre.mpf.wfm.enums.MarkupStatus;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.util.JniLoader;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mitre.mpf.test.TestUtil.anyNonNull;
import static org.mockito.Mockito.*;

public class TestStorageService {

    private static final URI SERVICE_URI = URI.create("http://somehost");

    private final PropertiesUtil _mockPropertiesUtil = mock(PropertiesUtil.class);

    private final Redis _mockRedis = mock(Redis.class);

    private final ObjectMapper _mockObjectMapper = mock(ObjectMapper.class);

    private final StorageBackend _mockStorageBackend = mock(StorageBackend.class);

    private StorageServiceImpl _storageService;

    @Rule
    public TemporaryFolder _tempFolder = new TemporaryFolder();


    @BeforeClass
    public static void initClass() {
        assertTrue(JniLoader.isLoaded()); // Forces static initializer to run
    }

    @Before
    public void init() {
        when(_mockStorageBackend.getType())
                .thenReturn(StorageBackend.Type.CUSTOM_NGINX);

        _storageService = new StorageServiceImpl(_mockPropertiesUtil, _mockRedis, _mockObjectMapper,
                                                 Collections.singletonList(_mockStorageBackend));
    }

    private void setStorageType(long jobId, StorageBackend.Type storageType) {
        Map<String, String> propertiesMap = new HashMap<>();
        propertiesMap.put("http.object.storage.type", storageType.name());
        propertiesMap.put("http.object.storage.service_uri", SERVICE_URI.toString());
        TransientDetectionSystemProperties propertiesSnapshot = new TransientDetectionSystemProperties(propertiesMap);
        when(_mockRedis.getPropertiesSnapshot(jobId))
                .thenReturn(propertiesSnapshot);
    }


    @Test(expected = IllegalStateException.class)
    @SuppressWarnings("ResultOfObjectAllocationIgnored")
    public void throwsExceptionWhenNotAllStorageTypesConfigured() {
        new StorageServiceImpl(_mockPropertiesUtil, _mockRedis, _mockObjectMapper, Collections.emptyList());
    }


    @Test(expected = IllegalStateException.class)
    @SuppressWarnings("ResultOfObjectAllocationIgnored")
    public void throwsExceptionWhenDuplicateStorageBackends() {
        StorageBackend mockBadBackend = mock(StorageBackend.class);
        when(mockBadBackend.getType())
                .thenReturn(StorageBackend.Type.CUSTOM_NGINX);

        new StorageServiceImpl(_mockPropertiesUtil, _mockRedis, _mockObjectMapper,
                               Arrays.asList(_mockStorageBackend, mockBadBackend));
    }

    @Test
    public void canStoreOutputObjectRemotely() throws IOException, StorageException {
        long jobId = 6234;
        setStorageType(jobId, StorageBackend.Type.CUSTOM_NGINX);

        JsonOutputObject outputObject = mock(JsonOutputObject.class);
        when(outputObject.getJobId())
                .thenReturn(jobId);

        String expectedUrl = "http://somehost:1234";
        when(_mockStorageBackend.storeAsJson(SERVICE_URI, outputObject))
                .thenReturn(expectedUrl);

        String actualUrl = _storageService.store(outputObject);

        verify(_mockObjectMapper, never())
                .writeValue(any(File.class), any());

        assertEquals(expectedUrl, actualUrl);
        verifyNoRedisWarning();
    }


    @Test
    public void canStoreOutputObjectLocally() throws IOException, StorageException {
        setStorageType(471, StorageBackend.Type.NONE);
        verifyOutputObjectStoredLocally();
        verify(_mockStorageBackend, never())
                .storeAsJson(any(), any());
        verifyNoRedisWarning();
    }


    @Test
    public void outputObjectGetsStoredLocallyWhenBackendException() throws StorageException, IOException {
        setStorageType(471, StorageBackend.Type.CUSTOM_NGINX);
        doThrow(StorageException.class)
                .when(_mockStorageBackend).storeAsJson(any(), any());
        verifyOutputObjectStoredLocally();
        verifyNoRedisWarning();
    }


    @Test
    public void outputObjectGetsStoredLocallyWhenBadBackendType() throws IOException {
        Map<String, String> propertiesMap = new HashMap<>();
        propertiesMap.put("http.object.storage.type", "BAD_TYPE");
        propertiesMap.put("http.object.storage.service_uri", SERVICE_URI.toString());
        TransientDetectionSystemProperties propertiesSnapshot = new TransientDetectionSystemProperties(propertiesMap);
        when(_mockRedis.getPropertiesSnapshot(471))
                .thenReturn(propertiesSnapshot);

        verifyOutputObjectStoredLocally();
        verifyNoRedisWarning();
    }


    private void verifyOutputObjectStoredLocally() throws IOException {
        JsonOutputObject outputObject = mock(JsonOutputObject.class);
        when(outputObject.getJobId())
                .thenReturn(471L);

        when(_mockPropertiesUtil.createDetectionOutputObjectFile(471L))
                .thenReturn(new File("/dev/null"));

        String resultUrl = _storageService.store(outputObject);

        verify(_mockObjectMapper)
                .writeValue(new File("/dev/null"), outputObject);


        assertEquals("file:/dev/null", resultUrl);
    }


    @Test
    public void canStoreImageArtifactsRemotely() throws IOException, StorageException {
        setStorageType(434, StorageBackend.Type.CUSTOM_NGINX);

        ClassPathResource resource = new ClassPathResource("samples/meds1.jpg", getClass().getClassLoader());
        String imgPath = resource.getFile().getAbsolutePath();


        String expectedUrl = "http://somehost:1234";
        MutableObject<byte[]> actualSha = new MutableObject<>();

        when(_mockStorageBackend.store(any(), any(Path.class)))
                .thenAnswer(invocation -> {
                    Path path = invocation.getArgumentAt(1, Path.class);
                    try (InputStream is = Files.newInputStream(path)) {
                        actualSha.setValue(DigestUtils.sha256(is));
                    }
                    return expectedUrl;
                });

        ArtifactExtractionRequest request = new ArtifactExtractionRequest(434, 0, imgPath,
                                                                          MediaType.IMAGE, 0);

        Map<Integer, String> storeResult = _storageService.store(request);
        assertEquals(1, storeResult.size());
        assertEquals(expectedUrl, storeResult.get(0));

        byte[] expectedSha;
        try (InputStream is = resource.getInputStream()) {
            expectedSha = DigestUtils.sha256(is);
        }
        assertArrayEquals(expectedSha, actualSha.getValue());
        verifyNoRedisWarning();
    }


    @Test
    public void canStoreImageArtifactsLocally() throws IOException, StorageException {
        setStorageType(434, StorageBackend.Type.NONE);
        verifyImageArtifactStoredLocally();
        verify(_mockStorageBackend, never())
                .store(any(), any(InputStream.class));
        verifyNoRedisWarning();
    }


    @Test
    public void imageArtifactGetStoredLocallyWhenBackendException() throws IOException, StorageException {
        setStorageType(434, StorageBackend.Type.CUSTOM_NGINX);
        doThrow(StorageException.class)
                .when(_mockStorageBackend).store(any(), any(Path.class));
        verifyImageArtifactStoredLocally();
        verifyRedisWarning(434);
    }


    private void verifyImageArtifactStoredLocally() throws IOException {
        ClassPathResource resource = new ClassPathResource("samples/meds1.jpg", getClass().getClassLoader());
        String imgPath = resource.getFile().getAbsolutePath();

        File outputFile = _tempFolder.newFile();
        when(_mockPropertiesUtil.createArtifactFile(434, 0, 0, resource.getFilename()))
                .thenReturn(outputFile);

        ArtifactExtractionRequest request = new ArtifactExtractionRequest(434, 0, imgPath,
                                                                          MediaType.IMAGE, 0);

        Map<Integer, String> storeResult = _storageService.store(request);

        assertTrue(FileUtils.contentEquals(resource.getFile(), outputFile));
        assertEquals(1, storeResult.size());
        assertEquals(outputFile.toURI(), URI.create(storeResult.get(0)));

    }

    @Test
    public void canStoreVideoArtifactRemotely() throws IOException, StorageException {
        ClassPathResource resource = new ClassPathResource("samples/five-second-marathon-clip.mkv",
                                                           getClass().getClassLoader());
        String videoPath = resource.getFile().getAbsolutePath();

        ArtifactExtractionRequest request = new ArtifactExtractionRequest(435, 0, videoPath,
                                                                          MediaType.VIDEO, 0);
        request.getActionIndexToMediaIndexes().put(0, Sets.newHashSet(0, 5));
        request.getActionIndexToMediaIndexes().put(1, Sets.newHashSet(5, 9));

        Map<Integer, byte[]> expectedHashes = getHashesFromDisk(request);

        setStorageType(435, StorageBackend.Type.CUSTOM_NGINX);

        List<byte[]> actualHashes = new ArrayList<>();

        String baseUrl = "http://somhost:4321/";
        AtomicInteger count = new AtomicInteger();
        when(_mockStorageBackend.store(any(), notNull(Path.class)))
                .thenAnswer(invocation -> {
                    Path path = invocation.getArgumentAt(1, Path.class);
                    try (InputStream is = Files.newInputStream(path)) {
                        actualHashes.add(DigestUtils.sha256(is));
                    }
                    return baseUrl + count.getAndIncrement();
                });

        Map<Integer, String> results = _storageService.store(request);
        assertEquals(3, results.size());
        assertEquals(baseUrl + 0, results.get(0));
        assertEquals(baseUrl + 1, results.get(5));
        assertEquals(baseUrl + 2, results.get(9));

        assertEquals(3, actualHashes.size());
        assertArrayEquals(expectedHashes.get(0), actualHashes.get(0));
        assertArrayEquals(expectedHashes.get(5), actualHashes.get(1));
        assertArrayEquals(expectedHashes.get(9), actualHashes.get(2));

        verifyNoRedisWarning();
    }


    private Map<Integer, byte[]> getHashesFromDisk(ArtifactExtractionRequest request) throws IOException {
        setStorageType(request.getJobId(), StorageBackend.Type.NONE);

        when(_mockPropertiesUtil.createArtifactDirectory(request.getJobId(), request.getMediaId(),
                                                         request.getStageIndex()))
                .thenReturn(_tempFolder.newFolder());

        Map<Integer, String> paths = _storageService.store(request);
        Map<Integer, byte[]> hashes = new HashMap<>();

        for (Map.Entry<Integer, String> pathEntry : paths.entrySet()) {
            try (InputStream is = Files.newInputStream(Paths.get(URI.create(pathEntry.getValue())))) {
                byte[] sha = DigestUtils.sha256(is);
                hashes.put(pathEntry.getKey(), sha);
            }
        }
        return hashes;
    }


    @Test
    public void canStoreVideoArtifactLocally() throws IOException, StorageException {
        setStorageType(436, StorageBackend.Type.NONE);
        verifyVideoArtifactsStoredLocally();
        verify(_mockStorageBackend, never())
                .store(any(), any(InputStream.class));
        verifyNoRedisWarning();
    }


    @Test
    public void videoArtifactsGetStoredLocallyWhenBackendException() throws IOException, StorageException {
        setStorageType(436, StorageBackend.Type.CUSTOM_NGINX);
        doThrow(StorageException.class)
                .when(_mockStorageBackend).store(any(), any(Path.class));
        verifyVideoArtifactsStoredLocally();
        verifyRedisWarning(436);
    }


    private void verifyVideoArtifactsStoredLocally() throws IOException {
        File artifactDirectory = _tempFolder.newFolder();
        when(_mockPropertiesUtil.createArtifactDirectory(436, 1, 1))
                .thenReturn(artifactDirectory);

        ClassPathResource resource = new ClassPathResource("samples/five-second-marathon-clip.mkv",
                                                           getClass().getClassLoader());
        String videoPath = resource.getFile().getAbsolutePath();

        ArtifactExtractionRequest request = new ArtifactExtractionRequest(436, 1, videoPath,
                                                                          MediaType.VIDEO, 1);
        request.getActionIndexToMediaIndexes().put(0, Sets.newHashSet(0, 5));
        request.getActionIndexToMediaIndexes().put(1, Sets.newHashSet(5, 9));

        Map<Integer, String> results = _storageService.store(request);
        assertEquals(3, results.size());

        for (int frameNumber : new int[]{0, 5, 9}) {
            Path expectedPath = artifactDirectory.toPath()
                    .resolve(String.format("frame-%s.png", frameNumber));
            assertEquals(expectedPath.toUri().toString(), results.get(frameNumber));
            assertTrue(Files.size(expectedPath) > 0);
        }
    }


    @Test
    public void canStoreMarkupRemotely() throws IOException, StorageException {
        long jobId = 52123;
        setStorageType(jobId, StorageBackend.Type.CUSTOM_NGINX);

        String expectedMarkupContent = "This is fake markup.";
        Path fakeMarkup = _tempFolder.newFile("fake_markup").toPath();
        Files.write(fakeMarkup, Collections.singletonList(expectedMarkupContent));

        String expectedUploadUri = "http://somehost:1234/markup/0";
        MutableObject<String> actualMarkupContent = new MutableObject<>();
        when(_mockStorageBackend.store(any(), any(URL.class)))
                .thenAnswer(invocation -> {
                    URL url = invocation.getArgumentAt(1, URL.class);
                    actualMarkupContent.setValue(IOUtils.toString(url));
                    return expectedUploadUri;
                });

        MarkupResult markupResult = new MarkupResult();
        markupResult.setJobId(jobId);
        markupResult.setMarkupStatus(MarkupStatus.COMPLETE);
        markupResult.setMarkupUri(fakeMarkup.toUri().toString());

        _storageService.store(markupResult);

        assertEquals(expectedMarkupContent + '\n', actualMarkupContent.getValue());
        assertEquals(expectedUploadUri, markupResult.getMarkupUri());
        assertFalse(Files.exists(fakeMarkup));

        verifyNoRedisWarning();
    }


    @Test
    public void canStoreMarkupLocally() throws IOException, StorageException {
        setStorageType(1337, StorageBackend.Type.NONE);
        MarkupResult markupResult = verifyMarkupStoredLocally();

        verify(_mockStorageBackend, never())
                .store(any(), any(InputStream.class));

        assertEquals(MarkupStatus.COMPLETE, markupResult.getMarkupStatus());
        assertTrue(markupResult.getMessage() == null || markupResult.getMessage().isEmpty());

        verifyNoRedisWarning();
    }


    @Test
    public void markupStoredLocallyWhenBackendException() throws StorageException, IOException {
        setStorageType(1337, StorageBackend.Type.CUSTOM_NGINX);
        doThrow(StorageException.class)
                .when(_mockStorageBackend).store(any(), any(URL.class));
        MarkupResult markupResult = verifyMarkupStoredLocally();

        assertEquals(MarkupStatus.COMPLETE_WITH_WARNING, markupResult.getMarkupStatus());
        assertTrue(markupResult.getMessage() != null && !markupResult.getMessage().isEmpty());

        verifyRedisWarning(1337);
    }


    private MarkupResult verifyMarkupStoredLocally() throws IOException {
        String expectedMarkupContent = "This is fake markup.";
        Path fakeMarkup = _tempFolder.newFile("fake_markup").toPath();
        Files.write(fakeMarkup, Collections.singletonList(expectedMarkupContent));

        MarkupResult markupResult = new MarkupResult();
        markupResult.setJobId(1337);
        markupResult.setMarkupStatus(MarkupStatus.COMPLETE);
        markupResult.setMarkupUri(fakeMarkup.toUri().toString());

        _storageService.store(markupResult);

        assertEquals(fakeMarkup.toUri().toString(), markupResult.getMarkupUri());

        String actualContent = String.join("", Files.readAllLines(fakeMarkup));
        assertEquals(expectedMarkupContent, actualContent);
        assertTrue(Files.exists(fakeMarkup));

        return markupResult;
    }

    private void verifyNoRedisWarning() {
        verify(_mockRedis, never())
                .addJobWarning(anyLong(), any());
    }

    private void verifyRedisWarning(long jobId) {
        verify(_mockRedis)
                .addJobWarning(eq(jobId), anyNonNull());
    }
}
