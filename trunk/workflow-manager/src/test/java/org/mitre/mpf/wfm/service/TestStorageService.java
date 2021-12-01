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

import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.interop.JsonDetectionOutputObject;
import org.mitre.mpf.interop.JsonIssueDetails;
import org.mitre.mpf.interop.JsonMediaIssue;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.wfm.camel.operations.detection.artifactextraction.ArtifactExtractionRequest;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.MarkupResult;
import org.mitre.mpf.wfm.enums.*;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.junit.Assert.*;
import static org.mitre.mpf.test.TestUtil.nonBlank;
import static org.mitre.mpf.test.TestUtil.nonEmptyCollection;
import static org.mockito.Mockito.*;

public class TestStorageService {

    @InjectMocks
    private StorageService _storageService;

    @Mock
    private InProgressBatchJobsService _mockInProgressJobs;

    @Mock
    private S3StorageBackend _mockS3Backend;

    @Mock
    private CustomNginxStorageBackend _mockNginxBackend;

    @Mock
    private LocalStorageBackend _mockLocalBackend;

    @Mock
    private PropertiesUtil _mockPropertiesUtil;

    private final JsonOutputObject _mockOutputObject = mock(JsonOutputObject.class);

    private static final URI TEST_REMOTE_URI = URI.create("http://somehost.xyz/path");

    private static final URI TEST_LOCAL_URI = URI.create("file:///path");

    private static final long TEST_INTERNAL_JOB_ID = 2;
    private static final String TEST_EXPORTED_JOB_ID = "localhost-2";

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        when(_mockPropertiesUtil.getHostName())
                .thenReturn("localhost");
        when(_mockPropertiesUtil.getJobIdFromExportedId(TEST_EXPORTED_JOB_ID))
                .thenReturn(TEST_INTERNAL_JOB_ID);
        when(_mockOutputObject.getJobId())
                .thenReturn(TEST_EXPORTED_JOB_ID);
    }


    @Test
    public void S3BackendHasHigherPriorityThanNginx() throws IOException, StorageException {
        when(_mockS3Backend.canStore(_mockOutputObject))
                .thenReturn(true);
        when(_mockNginxBackend.canStore(_mockOutputObject))
                .thenReturn(true);

        var outputSha = new MutableObject<String>();
        _storageService.store(_mockOutputObject, outputSha);

        verifyNoInProgressJobWarnings();
        verify(_mockS3Backend)
                .store(_mockOutputObject, outputSha);
        verify(_mockNginxBackend, never())
                .store(any(JsonOutputObject.class), any());
    }


    @Test
    public void canStoreOutputObjectRemotely() throws IOException, StorageException {

        when(_mockS3Backend.canStore(_mockOutputObject))
                .thenReturn(true);

        when(_mockS3Backend.store(same(_mockOutputObject), any()))
                .thenReturn(TEST_REMOTE_URI);

        URI result = _storageService.store(_mockOutputObject, new MutableObject<>());
        assertEquals(TEST_REMOTE_URI, result);

        verifyZeroInteractions(_mockLocalBackend);
        verifyNoInProgressJobWarnings();
    }

    @Test
    public void canStoreOutputObjectLocally() throws IOException, StorageException {
        when(_mockLocalBackend.store(same(_mockOutputObject), any()))
                .thenReturn(TEST_LOCAL_URI);

        URI result = _storageService.store(_mockOutputObject, new MutableObject<>());
        assertEquals(TEST_LOCAL_URI, result);

        verifyNoInProgressJobWarnings();
        verify(_mockS3Backend)
                .canStore(_mockOutputObject);
        verify(_mockNginxBackend)
                .canStore(_mockOutputObject);
    }

    @Test
    public void outputObjectGetsStoredLocallyWhenBackendException() throws IOException, StorageException {
        SortedSet<JsonMediaIssue> warnings = setupWarnings(_mockOutputObject);
        setInitialJobStatus(TEST_INTERNAL_JOB_ID, BatchJobStatusType.COMPLETE);


        when(_mockS3Backend.canStore(_mockOutputObject))
                .thenReturn(true);
        doThrow(StorageException.class)
                .when(_mockS3Backend).store(same(_mockOutputObject), any());

        when(_mockLocalBackend.store(same(_mockOutputObject), any()))
                .thenReturn(TEST_LOCAL_URI);

        URI result = _storageService.store(_mockOutputObject, new MutableObject<>());
        assertEquals(TEST_LOCAL_URI, result);

        verifyJobWarning(TEST_INTERNAL_JOB_ID);
        verifySingleWarningAddedToOutputObject(0, warnings);
    }


    @Test
    public void outputObjectGetsStoredLocallyWhenCanStoreFails() throws StorageException, IOException {

        SortedSet<JsonMediaIssue> warnings = setupWarnings(_mockOutputObject);
        setInitialJobStatus(TEST_INTERNAL_JOB_ID, BatchJobStatusType.COMPLETE);

        doThrow(StorageException.class)
                .when(_mockNginxBackend).canStore(_mockOutputObject);

        when(_mockLocalBackend.store(same(_mockOutputObject), any()))
                .thenReturn(TEST_LOCAL_URI);

        URI result = _storageService.store(_mockOutputObject, new MutableObject<>());
        assertEquals(TEST_LOCAL_URI, result);

        verifyJobWarning(TEST_INTERNAL_JOB_ID);
        verifySingleWarningAddedToOutputObject(0, warnings);

        verify(_mockNginxBackend, never())
                .store(any(JsonOutputObject.class), any());
    }


    @Test
    public void throwsExceptionWhenFailsToStoreLocally() throws IOException, StorageException {
        SortedSet<JsonMediaIssue> warnings = setupWarnings(_mockOutputObject);
        setInitialJobStatus(TEST_INTERNAL_JOB_ID, BatchJobStatusType.COMPLETE);

        when(_mockS3Backend.canStore(_mockOutputObject))
                .thenReturn(true);

        doThrow(StorageException.class)
                .when(_mockS3Backend).store(same(_mockOutputObject), any());

        doThrow(new IOException("test"))
                .when(_mockLocalBackend).store(same(_mockOutputObject), any());

        try {
            _storageService.store(_mockOutputObject, new MutableObject<>());
            fail("Expected IOException");
        }
        catch (IOException e) {
            assertTrue(e.getSuppressed()[0] instanceof StorageException);
            // The code that calls _storageService.store() will catch the exception and set the final status to ERROR.
            // Until then, the status is COMPLETE_WITH_WARNINGS.
            verifySingleWarningAddedToOutputObject(0, warnings);
        }
    }


    @Test
    public void canStoreImageArtifactsRemotely() throws IOException, StorageException {
        ArtifactExtractionRequest request = mock(ArtifactExtractionRequest.class);
        when(request.getMediaType())
                .thenReturn(MediaType.IMAGE);

        when(_mockNginxBackend.canStore(request))
                .thenReturn(true);
        Table<Integer, Integer, URI> expectedResults = new ImmutableTable.Builder<Integer, Integer, URI>()
                                                           .put(0, 0, TEST_REMOTE_URI).build();
        when(_mockNginxBackend.storeArtifacts(request))
                .thenReturn(expectedResults);

        Table<Integer, Integer, URI> result = _storageService.storeArtifacts(request);
        assertEquals(expectedResults, result);

        verifyZeroInteractions(_mockLocalBackend);
        verifyNoInProgressJobWarnings();
    }


    @Test
    public void canStoreImageArtifactsLocally() throws IOException, StorageException {
        ArtifactExtractionRequest request = mock(ArtifactExtractionRequest.class);
        when(request.getMediaType())
                .thenReturn(MediaType.IMAGE);

        Table<Integer, Integer, URI> expectedResults = new ImmutableTable.Builder<Integer, Integer, URI>()
                .put(0, 0, TEST_LOCAL_URI).build();
        when(_mockLocalBackend.storeArtifacts(request))
                .thenReturn(expectedResults);

        Table<Integer, Integer, URI> result = _storageService.storeArtifacts(request);
        assertEquals(expectedResults, result);

        verifyNoInProgressJobWarnings();
        verify(_mockS3Backend)
                .canStore(request);
        verify(_mockNginxBackend)
                .canStore(request);
    }

    @Test
    public void imageArtifactGetStoredLocallyWhenBackendException() throws IOException, StorageException {
        long jobId = 4231;
        long mediaId = 456;
        ArtifactExtractionRequest request = mock(ArtifactExtractionRequest.class);
        when(request.getJobId())
                .thenReturn(jobId);
        when(request.getMediaType())
                .thenReturn(MediaType.IMAGE);
        when(request.getMediaId())
                .thenReturn(mediaId);

        when(_mockNginxBackend.canStore(request))
                .thenReturn(true);
        doThrow(StorageException.class)
                .when(_mockNginxBackend).storeArtifacts(request);

        Table<Integer, Integer, URI> expectedResults = new ImmutableTable.Builder<Integer, Integer, URI>()
                .put(0, 0, TEST_LOCAL_URI).build();
        when(_mockLocalBackend.storeArtifacts(request))
                .thenReturn(expectedResults);

        Table<Integer, Integer, URI> result = _storageService.storeArtifacts(request);
        assertEquals(expectedResults, result);

        verifyWarning(jobId, request.getMediaId());
    }


    @Test
    public void canStoreVideoArtifactRemotely() throws IOException, StorageException {
        ArtifactExtractionRequest request = mock(ArtifactExtractionRequest.class);
        when(request.getMediaType())
                .thenReturn(MediaType.VIDEO);

        Table<Integer, Integer, URI> expectedResults = new ImmutableTable.Builder<Integer, Integer, URI>()
                .put(0, 2, TEST_REMOTE_URI)
                .put(5, 6, URI.create("http://example"))
                .build();

        when(_mockS3Backend.canStore(request))
                .thenReturn(true);
        when(_mockS3Backend.storeArtifacts(request))
                .thenReturn(expectedResults);

        Table<Integer, Integer, URI> result = _storageService.storeArtifacts(request);
        assertEquals(expectedResults, result);

        verifyZeroInteractions(_mockLocalBackend);
        verifyNoInProgressJobWarnings();
    }


    @Test
    public void canStoreVideoArtifactLocally() throws IOException, StorageException {
        ArtifactExtractionRequest request = mock(ArtifactExtractionRequest.class);
        when(request.getMediaType())
                .thenReturn(MediaType.VIDEO);

        ImmutableTable<Integer, Integer, URI> expectedResults = new ImmutableTable.Builder<Integer, Integer, URI>()
                .put(0, 2, TEST_LOCAL_URI)
                .put(5, 10, URI.create("file:///example"))
                .build();

        when(_mockLocalBackend.storeArtifacts(request))
                .thenReturn(expectedResults);

        Table<Integer, Integer, URI> result = _storageService.storeArtifacts(request);
        assertEquals(expectedResults, result);

        verifyNoInProgressJobWarnings();
        verify(_mockS3Backend)
                .canStore(request);
        verify(_mockNginxBackend)
                .canStore(request);
    }


    @Test
    public void videoArtifactsGetStoredLocallyWhenBackendException() throws IOException, StorageException {
        long jobId = 4233;
        long mediaId = 548;
        ArtifactExtractionRequest request = mock(ArtifactExtractionRequest.class);
        when(request.getJobId())
                .thenReturn(jobId);
        when(request.getMediaId())
                .thenReturn(mediaId);
        when(request.getMediaType())
                .thenReturn(MediaType.VIDEO);

        when(_mockNginxBackend.canStore(request))
                .thenReturn(true);
        doThrow(StorageException.class)
                .when(_mockNginxBackend).storeArtifacts(request);

        ImmutableTable<Integer, Integer, URI> expectedResults = new ImmutableTable.Builder<Integer, Integer, URI>()
                .put(0, 5, TEST_LOCAL_URI)
                .put(5, 14, URI.create("file:///example"))
                .build();

        when(_mockLocalBackend.storeArtifacts(request))
                .thenReturn(expectedResults);

        Table<Integer, Integer, URI> result = _storageService.storeArtifacts(request);
        assertEquals(expectedResults, result);

        verifyWarning(jobId, mediaId);
    }


    @Test
    public void canStoreMarkupRemotely() throws IOException, StorageException {
        MarkupResult markup = mock(MarkupResult.class);
        when(_mockS3Backend.canStore(markup))
                .thenReturn(true);

        _storageService.store(markup);

        verify(_mockS3Backend)
                .store(markup);

        verifyNoMarkupError(markup);
        verifyZeroInteractions(_mockLocalBackend);
        verifyNoInProgressJobWarnings();
    }


    @Test
    public void canStoreMarkupLocally() {
        MarkupResult markup = mock(MarkupResult.class);

        _storageService.store(markup);

        verify(_mockLocalBackend)
                .store(markup);
        verifyNoMarkupError(markup);
        verifyNoInProgressJobWarnings();
    }

    @Test
    public void markupStoredLocallyWhenBackendException() throws IOException, StorageException {
        long jobId = 5134;
        long mediaId = 975;
        MarkupResult markup = mock(MarkupResult.class);
        when(markup.getJobId())
                .thenReturn(jobId);
        when(markup.getMediaId())
                .thenReturn(mediaId);

        when(_mockS3Backend.canStore(markup))
                .thenReturn(true);

        doThrow(StorageException.class)
                .when(_mockS3Backend).store(markup);

        _storageService.store(markup);

        verify(_mockLocalBackend)
                .store(markup);

        verifyWarning(jobId, mediaId);
        verify(markup)
                .setMessage(nonBlank());
        verify(markup)
                .setMarkupStatus(MarkupStatus.COMPLETE_WITH_WARNING);
    }


    private static SortedSet<JsonMediaIssue> setupWarnings(JsonOutputObject outputObject) {
        SortedSet<JsonMediaIssue> warnings = new TreeSet<>();
        when(outputObject.getWarnings())
                .thenReturn(warnings);

        doAnswer(a -> warnings.add(new JsonMediaIssue(
                a.getArgument(0),
                (Collection<JsonIssueDetails>) a.getArgument(1))))
                .when(outputObject).addWarnings(anyLong(), nonEmptyCollection());

        return warnings;
    }

    private void setInitialJobStatus(long jobId, BatchJobStatusType status) {
        var job = mock(BatchJob.class);
        when(job.getStatus())
                .thenReturn(status);
        when(_mockInProgressJobs.getJob(jobId))
                .thenReturn(job);
    }

    private static void verifyNoMarkupError(MarkupResult markup) {
        verify(markup, never())
                .setMessage(any());
        verify(markup, never())
                .setMarkupStatus(any());
    }

    private void verifyNoInProgressJobWarnings() {
        verify(_mockInProgressJobs, never())
                .addJobWarning(anyLong(), any(), any());

        verify(_mockInProgressJobs, never())
                .addWarning(anyLong(), anyLong(), any(), any());
    }

    private void verifyJobWarning(long jobId) {
        verify(_mockInProgressJobs)
                .addJobWarning(eq(jobId), eq(IssueCodes.REMOTE_STORAGE_UPLOAD), nonBlank());
    }

    private void verifyWarning(long jobId, long mediaId) {
        verify(_mockInProgressJobs)
                .addWarning(eq(jobId), eq(mediaId), eq(IssueCodes.REMOTE_STORAGE_UPLOAD), nonBlank());
    }

    private static void verifySingleWarningAddedToOutputObject(long mediaId, SortedSet<JsonMediaIssue> warnings) {
        assertEquals(1, warnings.size());

        JsonMediaIssue warning = warnings.first();
        assertEquals(mediaId, warning.getMediaId());
        assertEquals(1, warning.getDetails().size());

        JsonIssueDetails details = warning.getDetails().first();
        assertFalse(StringUtils.isBlank(details.getMessage()));
        assertEquals(IssueSources.WORKFLOW_MANAGER.toString(), details.getSource());
        assertEquals(IssueCodes.REMOTE_STORAGE_UPLOAD.toString(), details.getCode());
    }
}
