/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.camel.Exchange;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHttpResponse;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.interop.JsonActionOutputObject;
import org.mitre.mpf.interop.JsonDetectionOutputObject;
import org.mitre.mpf.interop.JsonDetectionProcessingError;
import org.mitre.mpf.interop.JsonIssueDetails;
import org.mitre.mpf.interop.JsonMarkupOutputObject;
import org.mitre.mpf.interop.JsonMediaIssue;
import org.mitre.mpf.interop.JsonMediaOutputObject;
import org.mitre.mpf.interop.JsonMediaRange;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.interop.JsonPipeline;
import org.mitre.mpf.interop.JsonTrackOutputObject;
import org.mitre.mpf.rest.api.JobCreationRequest;
import org.mitre.mpf.rest.api.TiesDbCheckStatus;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.HttpClientUtils;
import org.mitre.mpf.wfm.util.MediaActionProps;
import org.mitre.mpf.wfm.util.ObjectMapperFactory;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Streams;


public class TestTiesDbBeforeJobCheckService extends MockitoTest.Lenient {

    @Mock
    private PropertiesUtil _mockPropertiesUtil;

    @Mock
    private AggregateJobPropertiesUtil _mockAggJobProps;

    @Mock
    private JobConfigHasher _mockJobConfigHasher;

    @Mock
    private HttpClientUtils _mockHttpClientUtils;

    private final ObjectMapper _objectMapper = ObjectMapperFactory.customObjectMapper();

    @Mock
    private InProgressBatchJobsService _mockInProgressJobs;

    @Mock
    private S3StorageBackend _mockS3StorageBackend;

    private TiesDbBeforeJobCheckServiceImpl _tiesDbBeforeJobCheckService;


    @Before
    public void init() {
        when(_mockPropertiesUtil.getHttpCallbackRetryCount())
            .thenReturn(3);

        _tiesDbBeforeJobCheckService = new TiesDbBeforeJobCheckServiceImpl(
                _mockPropertiesUtil,
                _mockAggJobProps,
                _mockJobConfigHasher,
                _mockHttpClientUtils,
                _objectMapper,
                _mockInProgressJobs,
                _mockS3StorageBackend);
    }


    @Test
    public void testSkipCheckProperty() {
        var action1 = mock(Action.class);
        var action2 = mock(Action.class);

        var elements = mock(JobPipelineElements.class);
        when(elements.getAllActions())
            .thenReturn(ImmutableList.of(action1, action2));

        var media = mock(Media.class);

        var mockProps = mock(MediaActionProps.class);
        when(mockProps.get(MpfConstants.SKIP_TIES_DB_CHECK, media, action1))
            .thenReturn("True");

        when(_mockAggJobProps.getMediaActionProps(any(), any(), any(), any()))
            .thenReturn(mockProps);

        var jobCreationRequest = new JobCreationRequest();
        var result = _tiesDbBeforeJobCheckService.checkTiesDbBeforeJob(
                jobCreationRequest,
                null,
                List.of(media),
                elements);
        assertEquals(TiesDbCheckStatus.NOT_REQUESTED, result.status());
        assertTrue(result.checkInfo().isEmpty());
    }


    @Test
    public void testMediaMissingHash() {
        var action = mock(Action.class);
        when(action.getName())
            .thenReturn("action");
        var elements = mock(JobPipelineElements.class);
        when(elements.getAllActions())
            .thenReturn(ImmutableList.of(action));

        var media1 = mock(Media.class);
        when(media1.getId())
                .thenReturn(111L);
        when(media1.getLinkedHash())
                .thenReturn(Optional.of("HASH"));

        var media2 = mock(Media.class);
        when(media2.getId())
                .thenReturn(222L);
        when(media2.getLinkedHash())
                .thenReturn(Optional.empty());

        when(_mockAggJobProps.getMediaActionProps(any(), any(), any(), eq(elements)))
            .thenReturn(new MediaActionProps
                ((m, a) -> Map.of(MpfConstants.TIES_DB_URL, "http://localhost")));


        var result = _tiesDbBeforeJobCheckService.checkTiesDbBeforeJob(
                new JobCreationRequest(),
                null,
                List.of(media1, media2),
                elements);

        assertEquals(TiesDbCheckStatus.MEDIA_HASHES_ABSENT, result.status());
        assertTrue(result.checkInfo().isEmpty());
    }


    @Test
    public void testMediaMissingMimeType() {
        var action = mock(Action.class);
        when(action.getName())
            .thenReturn("action");
        var elements = mock(JobPipelineElements.class);
        when(elements.getAllActions())
            .thenReturn(ImmutableList.of(action));

        var media1 = mock(Media.class);
        when(media1.getLinkedHash())
                .thenReturn(Optional.of("HASH"));
        when(media1.getMimeType())
                .thenReturn(Optional.of("image/jpeg"));

        var media2 = mock(Media.class);
        when(media2.getLinkedHash())
                .thenReturn(Optional.of("HASH2"));
        when(media2.getMimeType())
                .thenReturn(Optional.empty());

        when(_mockAggJobProps.getMediaActionProps(any(), any(), any(), eq(elements)))
            .thenReturn(new MediaActionProps
                ((m, a) -> Map.of(MpfConstants.TIES_DB_URL, "http://localhost")));

        var result = _tiesDbBeforeJobCheckService.checkTiesDbBeforeJob(
                new JobCreationRequest(),
                null,
                List.of(media1, media2),
                elements);

        assertEquals(TiesDbCheckStatus.MEDIA_MIME_TYPES_ABSENT, result.status());
        assertTrue(result.checkInfo().isEmpty());
    }


    @Test
    public void testNoTiesDbUrl() {
        var action = mock(Action.class);
        when(action.getName())
            .thenReturn("action");
        var elements = mock(JobPipelineElements.class);
        when(elements.getAllActions())
            .thenReturn(ImmutableList.of(action));

        var media = mock(Media.class);
        when(media.getLinkedHash())
                .thenReturn(Optional.of("HASH"));
        when(media.getMimeType())
                .thenReturn(Optional.of("image/jpeg"));


        when(_mockAggJobProps.getMediaActionProps(any(), any(), any(), eq(elements)))
            .thenReturn(new MediaActionProps((m, a) -> Map.of()));


        var result = _tiesDbBeforeJobCheckService.checkTiesDbBeforeJob(
                new JobCreationRequest(),
                null,
                List.of(media),
                elements);

        assertEquals(TiesDbCheckStatus.NO_TIES_DB_URL_IN_JOB, result.status());
        assertTrue(result.checkInfo().isEmpty());
    }


    @Test
    public void testInvalidUri() {
        var action = mock(Action.class);
        var elements = mock(JobPipelineElements.class);
        when(elements.getAllActions())
            .thenReturn(ImmutableList.of(action));

        var media = mock(Media.class);
        when(media.getLinkedHash())
                .thenReturn(Optional.of("HASH"));
        when(media.getMimeType())
                .thenReturn(Optional.of("image/jpeg"));

        var mockMediaActionProps = mock(MediaActionProps.class);
        when(mockMediaActionProps.get(MpfConstants.TIES_DB_URL, media, action))
            .thenReturn(":invalid_uri");

        when(_mockAggJobProps.getMediaActionProps(any(), any(), any(), eq(elements)))
            .thenReturn(mockMediaActionProps);

        setEmptyCombinedJobProps();

        var ex = TestUtil.assertThrows(
                WfmProcessingException.class,
                () -> _tiesDbBeforeJobCheckService.checkTiesDbBeforeJob(
                        new JobCreationRequest(),
                        null,
                        List.of(media),
                        elements));

        assertThat(ex.getMessage(), containsString("isn't a valid URI"));
        assertThat(ex.getCause(), instanceOf(URISyntaxException.class));
    }


    @Test
    public void testNoSupplementals() throws IOException {
        testGetResultsButNoMatch(List.of());
    }

    @Test
    public void testNoJobConfigHash() throws IOException {
        var tiesDbData = List.of(
            Map.of(
                "assertionId", "s10"),
                "dataObject", Map.of(
                    "algorithm", "FACECV"
                ),
            Map.of(
                "assertionId", "s11"),
                "dataObject", Map.of(
                    "algorithm", "MOG"
                )
        );
        testGetResultsButNoMatch(tiesDbData);
    }


    @Test
    public void testNoMatchingJobHash() throws IOException {
        var tiesDbData = List.of(
            Map.of(
                "assertionId", "s10"),
                "dataObject", Map.of(
                    "algorithm", "FACECV",
                    "jobConfigHash", "WRONG_HASH"
                ),
            Map.of(
                "assertionId", "s11"),
                "dataObject", Map.of(
                    "algorithm", "MOG",
                    "jobConfigHash", "WRONG_HASH2"
                ),
            Map.of(
                "assertionId", "s12"),
                "dataObject", Map.of(
                    "algorithm", "ALGO"
                )
        );
        testGetResultsButNoMatch(tiesDbData);
    }


    private void testGetResultsButNoMatch(Object tiesDbData) throws IOException {
        var checkResult = setupSingleUriSingleRequestTiesDbTest(tiesDbData);
        assertEquals(TiesDbCheckStatus.NO_MATCH, checkResult.status());
        assertTrue(checkResult.checkInfo().isEmpty());
    }


    private TiesDbCheckResult setupSingleUriSingleRequestTiesDbTest(Object tiesDbData) throws IOException {
        var requestCaptor = ArgumentCaptor.forClass(HttpGet.class);
        when(_mockHttpClientUtils.executeRequest(requestCaptor.capture(), eq(3)))
            .thenReturn(createHttpResponse(tiesDbData));

        var result = setupSingleTiesDbUriTest();
        var request = requestCaptor.getValue();
        assertThat(request, instanceOf(HttpGet.class));
        assertUriIsFirstPageTiesDbUri(request.getURI());
        return result;
    }


    private TiesDbCheckResult setupSingleTiesDbUriTest() throws IOException {
        var action = mock(Action.class);
        var elements = mock(JobPipelineElements.class);
        when(elements.getAllActions())
            .thenReturn(ImmutableList.of(action));

        var media = mock(Media.class);
        when(media.getLinkedHash())
                .thenReturn(Optional.of("MEDIA_HASH"));
        when(media.getMimeType())
                .thenReturn(Optional.of("image/jpeg"));

        var mockMediaActionProps = mock(MediaActionProps.class);
        when(mockMediaActionProps.get(MpfConstants.TIES_DB_URL, media, action))
            .thenReturn("http://tiesdb:1234");

        when(_mockAggJobProps.getMediaActionProps(any(), any(), any(), eq(elements)))
            .thenReturn(mockMediaActionProps);

        var combinedJobProps = Map.of(
            MpfConstants.TIES_DB_S3_COPY_ENABLED, "true",
            MpfConstants.S3_RESULTS_BUCKET, "results bucket",
            MpfConstants.S3_ACCESS_KEY, "access key",
            MpfConstants.S3_SECRET_KEY, "secret key"
        );

        when(_mockAggJobProps.getCombinedProperties(any(), any(), any(), any()))
            .thenReturn(combinedJobProps::get);

        when(_mockJobConfigHasher.getJobConfigHash(List.of(media), elements, mockMediaActionProps))
            .thenReturn("JOB_HASH");

        return _tiesDbBeforeJobCheckService.checkTiesDbBeforeJob(
                new JobCreationRequest(),
                null,
                List.of(media),
                elements);
    }


    @Test
    public void testFindMatchingSupplemental() throws IOException {
        var tiesDbData = List.of(
            Map.of(
                "assertionId", "s10",
                "dataObject", Map.of(
                    "algorithm", "FACECV",
                    "jobConfigHash", "JOB_HASH",
                    "outputUri", "file:///1.json",
                    "jobStatus", "COMPLETE",
                    "processDate", "2021-06-04T13:35:58.981Z"
                )
            ),
            Map.of(
                "assertionId", "s11",
                "dataObject", Map.of(
                    "algorithm", "MOG",
                    "jobConfigHash", "JOB_HASH",
                    "outputUri", "file:///2.json",
                    "jobStatus", "COMPLETE_WITH_WARNINGS",
                    "processDate", "2021-06-04T14:35:58.981Z"
                )
            ),
            Map.of(
                "assertionId", "s12",
                "dataObject", Map.of(
                    "algorithm", "ALGO"
                )
            ),
            Map.of(
                "assertionId", "s12",
                "dataObject", Map.of(
                    "algorithm", "ALGO"
                )
            )
        );

        var checkResult = setupSingleUriSingleRequestTiesDbTest(tiesDbData);
        assertEquals(TiesDbCheckStatus.FOUND_MATCH, checkResult.status());
        assertTrue(checkResult.checkInfo().isPresent());

        var info = checkResult.checkInfo().get();
        assertEquals(URI.create("file:///1.json"), info.outputObjectUri());
        assertEquals(BatchJobStatusType.COMPLETE, info.jobStatus());
        assertEquals(Instant.parse("2021-06-04T13:35:58.981Z"), info.processDate());
        assertTrue(info.s3CopyEnabled());
    }


    @Test
    public void testFindMatchingSupplementalOnMultiplePages() throws IOException {
        var unrelatedResults = List.of(
            Map.of(
                "assertionId", "s12",
                "dataObject", Map.of(
                    "algorithm", "ALGO"
                )
            ),
            Map.of(
                "assertionId", "s12",
                "dataObject", Map.of(
                    "algorithm", "ALGO"
                )
            ),
            Map.of(
                "assertionId", "s13",
                "dataObject", Map.of(
                    "algorithm", "FACECV",
                    "jobConfigHash", "JOB_HASH2",
                    "outputUri", "file:///3.json",
                    "jobStatus", "COMPLETE",
                    "processDate", "2021-12-04T13:35:58.981Z"
                )
            )
        );

        var match1 = Map.of(
            "assertionId", "s11",
            "dataObject", Map.of(
                "algorithm", "MOG",
                "jobConfigHash", "JOB_HASH",
                "outputUri", "file:///2.json",
                "jobStatus", "COMPLETE_WITH_WARNINGS",
                "processDate", "2021-06-04T14:35:58.981Z"
            )
        );

        var match2 = Map.of(
            "assertionId", "s10",
            "dataObject", Map.of(
                "algorithm", "FACECV",
                "jobConfigHash", "JOB_HASH",
                "outputUri", "file:///1.json",
                "jobStatus", "COMPLETE",
                "processDate", "2021-06-04T13:35:58.981Z"
            )
        );

        var match3 = Map.of(
            "assertionId", "s10",
            "dataObject", Map.of(
                "algorithm", "FACECV",
                "jobConfigHash", "JOB_HASH",
                "outputUri", "file:///5.json",
                "jobStatus", "COMPLETE",
                "processDate", "2021-01-01T13:35:58.981Z"
            )
        );

        var page1 = Streams.concat(
                repeat(unrelatedResults, 33),
                Stream.of(match1),
                repeat(unrelatedResults, 66))
            .toList();

        var page2 = Streams.concat(
                Stream.of(match2),
                repeat(unrelatedResults, 99))
            .toList();

        var page3 = Streams.concat(
                repeat(unrelatedResults, 33),
                Stream.of(match3),
                repeat(unrelatedResults, 10))
            .toList();


        var requestCaptor = ArgumentCaptor.forClass(HttpGet.class);
        when(_mockHttpClientUtils.executeRequest(requestCaptor.capture(), eq(3)))
            .thenReturn(
                createHttpResponse(page1),
                createHttpResponse(page2),
                createHttpResponse(page3));

        var checkResult = setupSingleTiesDbUriTest();
        assertEquals(TiesDbCheckStatus.FOUND_MATCH, checkResult.status());
        assertTrue(checkResult.checkInfo().isPresent());

        var info = checkResult.checkInfo().get();
        assertEquals(URI.create("file:///1.json"), info.outputObjectUri());
        assertEquals(BatchJobStatusType.COMPLETE, info.jobStatus());
        assertEquals(Instant.parse("2021-06-04T13:35:58.981Z"), info.processDate());
        assertTrue(info.s3CopyEnabled());

        var requests = requestCaptor.getAllValues();
        assertEquals(3, requests.size());
        assertUriIsFirstPageTiesDbUri(requests.get(0).getURI());

        var page2Uri = URI.create(
            "http://tiesdb:1234/api/db/supplementals?sha256Hash=MEDIA_HASH&system=OpenMPF&offset=100&limit=100");
        assertUrisMatch(page2Uri, requests.get(1).getURI());

        var page3Uri = URI.create(
            "http://tiesdb:1234/api/db/supplementals?sha256Hash=MEDIA_HASH&system=OpenMPF&offset=200&limit=100");
        assertUrisMatch(page3Uri, requests.get(2).getURI());
    }

    private static <T> Stream<T> repeat(List<T> items, int count) {
        return Stream.generate(() -> items)
            .flatMap(List::stream)
            .limit(count);
    }


    @Test
    public void testPartialFailureButMatchStillFound() throws IOException {
        var tiesDbData = List.of(
            Map.of(
                "assertionId", "s10",
                "dataObject", Map.of(
                    "algorithm", "FACECV",
                    "jobConfigHash", "JOB_HASH",
                    "outputUri", "file:///1.json",
                    "jobStatus", "COMPLETE",
                    "processDate", "2021-06-04T13:35:58.981Z"
                )
            ),
            Map.of(
                "assertionId", "s11",
                "dataObject", Map.of(
                    "algorithm", "MOG",
                    "jobConfigHash", "JOB_HASH",
                    "outputUri", "file:///2.json",
                    "jobStatus", "COMPLETE_WITH_WARNINGS",
                    "processDate", "2021-06-04T14:35:58.981Z"
                )
            )
        );

        var checkResult = testPartialFailure(tiesDbData);
        assertEquals(TiesDbCheckStatus.FOUND_MATCH, checkResult.status());
        assertTrue(checkResult.checkInfo().isPresent());

        var info = checkResult.checkInfo().get();
        assertEquals(URI.create("file:///1.json"), info.outputObjectUri());
        assertEquals(BatchJobStatusType.COMPLETE, info.jobStatus());
        assertEquals(Instant.parse("2021-06-04T13:35:58.981Z"), info.processDate());
        assertFalse(info.s3CopyEnabled());
    }



    @Test
    public void testPartialFailureNoMatchFound() throws IOException {
        var tiesDbData = List.of(
            Map.of(
                "assertionId", "s10",
                "dataObject", Map.of(
                    "algorithm", "FACECV",
                    "jobConfigHash", "WRONG_JOB_HASH",
                    "outputUri", "file:///1.json",
                    "jobStatus", "COMPLETE",
                    "processDate", "2021-06-04T13:35:58.981Z"
                )
            ),
            Map.of(
                "assertionId", "s11",
                "dataObject", Map.of(
                    "algorithm", "MOG",
                    "jobConfigHash", "WRONG_JOB_HASH",
                    "outputUri", "file:///2.json",
                    "jobStatus", "COMPLETE_WITH_WARNINGS",
                    "processDate", "2021-06-04T14:35:58.981Z"
                )
            )
        );

        var ex = TestUtil.assertThrows(
                IllegalStateException.class,
                () -> testPartialFailure(tiesDbData));

        assertThat(ex.getMessage(), containsString(
                "TiesDb responded with a non-200 status code of 400 and body: test-error"));
    }


    private TiesDbCheckResult testPartialFailure(Object tiesDbData) throws IOException {
        var action = mock(Action.class);
        var elements = mock(JobPipelineElements.class);
        when(elements.getAllActions())
            .thenReturn(ImmutableList.of(action));

        var media1 = mock(Media.class);
        when(media1.getLinkedHash())
                .thenReturn(Optional.of("HASH"));
        when(media1.getMimeType())
                .thenReturn(Optional.of("image/jpeg"));
        var media2 = mock(Media.class);
        when(media2.getLinkedHash())
                .thenReturn(Optional.of("HASH2"));
        when(media2.getMimeType())
                .thenReturn(Optional.of("image/jpeg"));

        var mockMediaActionProps = mock(MediaActionProps.class);
        when(mockMediaActionProps.get(MpfConstants.TIES_DB_URL, media1, action))
            .thenReturn("http://tiesdb:1234");

        when(mockMediaActionProps.get(MpfConstants.TIES_DB_URL, media2, action))
            .thenReturn("http://tiesdb-error:1234");

        when(_mockAggJobProps.getMediaActionProps(any(), any(), any(), eq(elements)))
            .thenReturn(mockMediaActionProps);

        setEmptyCombinedJobProps();

        when(_mockJobConfigHasher.getJobConfigHash(
                    List.of(media1, media2), elements, mockMediaActionProps))
            .thenReturn("JOB_HASH");

        doReturn(createHttpResponse(tiesDbData))
            .when(_mockHttpClientUtils)
            .executeRequest(
                requestWithUri("http://tiesdb:1234/api/db/supplementals?sha256Hash=HASH&system=OpenMPF&offset=0&limit=100"),
                eq(3));

        doReturn(createErrorResponse("test-error"))
            .when(_mockHttpClientUtils)
            .executeRequest(
                requestWithUri("http://tiesdb-error:1234/api/db/supplementals?sha256Hash=HASH2&system=OpenMPF&offset=0&limit=100"),
                eq(3));


        return _tiesDbBeforeJobCheckService.checkTiesDbBeforeJob(
                new JobCreationRequest(),
                null,
                List.of(media1, media2),
                elements);
    }


    @Test
    public void testCheckAfterMediaInspectionNoMatch() throws IOException {
        var exchange = runSuccessfulAfterMediaInspectionTest("WRONG_JOB_HASH");
        assertTrue(exchange.getOut().getHeaders().isEmpty());
    }


    @Test
    public void testCheckAfterMediaInspectionWithMatch() throws IOException {
        var exchange = runSuccessfulAfterMediaInspectionTest("JOB_HASH");
        assertEquals(true, exchange.getOut().getHeader(MpfHeaders.JOB_COMPLETE));
        assertEquals(
                "file:///1.json",
                exchange.getOut().getHeader(MpfHeaders.OUTPUT_OBJECT_URI_FROM_TIES_DB));
    }


    @Test
    public void testFailureAfterMediaInspection() throws IOException {
        var errorMsg = "<custom error message>";
        when(_mockHttpClientUtils.executeRequest(any(), eq(3)))
            .thenReturn(createErrorResponse(errorMsg));

        runAfterMediaInspectionTest();

        verify(_mockInProgressJobs)
            .addFatalError(
                        eq(123L), eq(IssueCodes.TIES_DB_BEFORE_JOB_CHECK),
                        contains(errorMsg));
    }

    private Exchange runSuccessfulAfterMediaInspectionTest(String tiesDbHash) throws IOException {
        var tiesDbData = List.of(
            Map.of(
                "assertionId", "s10",
                "dataObject", Map.of(
                    "algorithm", "FACECV",
                    "jobConfigHash", tiesDbHash,
                    "outputUri", "file:///1.json",
                    "jobStatus", "COMPLETE",
                    "processDate", "2021-06-04T13:35:58.981Z"
                )
            )
        );
        var requestCaptor = ArgumentCaptor.forClass(HttpGet.class);
        when(_mockHttpClientUtils.executeRequest(requestCaptor.capture(), eq(3)))
                .thenReturn(createHttpResponse(tiesDbData));

        var exchange = runAfterMediaInspectionTest();

        var request = requestCaptor.getValue();
        assertUriIsFirstPageTiesDbUri(request.getURI());
        return exchange;
    }


    private Exchange runAfterMediaInspectionTest() throws IOException {
        var exchange = TestUtil.createTestExchange();

        var action = mock(Action.class);
        var pipelineElements = mock(JobPipelineElements.class);
        when(pipelineElements.getAllActions())
                .thenReturn(ImmutableList.of(action));

        long jobId = 123;
        exchange.getIn().setHeader(MpfHeaders.JOB_ID, jobId);

        var job = mock(BatchJob.class);
        when(_mockInProgressJobs.getJob(jobId))
                .thenReturn(job);
        when(job.getPipelineElements())
                .thenReturn(pipelineElements);

        var media = mock(Media.class);
        when(media.getLinkedHash())
                .thenReturn(Optional.of("MEDIA_HASH"));
        when(media.getMimeType())
                .thenReturn(Optional.of("image/jpeg"));
        when(job.getMedia())
                .thenReturn(List.of(media));
        when(job.shouldCheckTiesDbAfterMediaInspection())
                .thenReturn(true);

        var mockProps = mock(MediaActionProps.class);
        when(mockProps.get(MpfConstants.TIES_DB_URL, media, action))
                .thenReturn("http://tiesdb:1234");

        when(_mockAggJobProps.getMediaActionProps(any(), any(), any(), eq(pipelineElements)))
                .thenReturn(mockProps);

        setEmptyCombinedJobProps();

        when(_mockJobConfigHasher.getJobConfigHash(List.of(media), pipelineElements, mockProps))
                .thenReturn("JOB_HASH");

        _tiesDbBeforeJobCheckService.wfmProcess(exchange);
        return exchange;
    }



    @Test
    public void doesNotDoDuplicateTiesDbCheck() {
        long jobId = 123;
        var exchange = TestUtil.createTestExchange();
        exchange.getIn().setHeader(MpfHeaders.JOB_ID, jobId);

        var job = mock(BatchJob.class);
        when(_mockInProgressJobs.getJob(jobId))
                .thenReturn(job);
        when(job.shouldCheckTiesDbAfterMediaInspection())
            .thenReturn(false);

        _tiesDbBeforeJobCheckService.wfmProcess(exchange);

        assertTrue(exchange.getOut().getHeaders().isEmpty());
        verifyNoInteractions(
                _mockPropertiesUtil,
                _mockAggJobProps,
                _mockJobConfigHasher,
                _mockHttpClientUtils);
    }


    @Test
    public void testCopyingResultsToNewBucket() throws StorageException, IOException {

        var job = mock(BatchJob.class);
        when(job.getId())
            .thenReturn(36L);

        var jobMedia1 = mock(Media.class);
        when(jobMedia1.getLinkedHash())
            .thenReturn(Optional.of("SHA1"));
        when(jobMedia1.getUri())
            .thenReturn("http://localhost/dest-bucket/media1");

        var jobMedia2 = mock(Media.class);
        when(jobMedia2.getLinkedHash())
            .thenReturn(Optional.of("SHA2"));
        when(jobMedia2.getUri())
            .thenReturn("http://localhost/dest-bucket/media2");

        when(job.getMedia())
            .thenReturn(List.of(jobMedia1, jobMedia2));

        var detection1 = new JsonDetectionOutputObject(
                1, 2, 3, 4, 0.5f,
                ImmutableSortedMap.of("prop1", "value1"),
                5, 6, "COMPLETED", "http://localhost/bucket/artifact1");

        var detection2 = new JsonDetectionOutputObject(
                13, 14, 15, 16, 0.2f,
                ImmutableSortedMap.of("prop2", "value2"),
                17, 18, "FAILED", null);

        var detection3 = new JsonDetectionOutputObject(
                7, 8, 9, 10, 1,
                ImmutableSortedMap.of("prop2", "value2"),
                11, 12, "COMPLETED", "http://localhost/bucket/artifact2");


        var track1 = new JsonTrackOutputObject(
                19, "19", 20, 21, 22, 23, "type1", "source1", 0.5f,
                Map.of("prop3", "prop4"), detection1, List.of(detection1, detection2));

        var track2 = new JsonTrackOutputObject(
                24, "24", 25, 26, 27, 28, "type2", "source1", 1,
                Map.of("prop3", "prop4"), detection3, List.of(detection3));

        var action1 = JsonActionOutputObject.factory(
                "source1", "algo1", ImmutableSortedSet.of(track1));

        var action2 = JsonActionOutputObject.factory(
                "source2", "algo2", ImmutableSortedSet.of(track2));

        var detectionTypeMap = ImmutableSortedMap.<String, SortedSet<JsonActionOutputObject>>of(
                "type1", ImmutableSortedSet.of(action1),
                "type2", ImmutableSortedSet.of(action2));

        var detectionError = new JsonDetectionProcessingError(
                35, 36, 37, 38, "ISSUE_CODE", "ISSUE_MESSAGE");

        var mediaError = new JsonMediaIssue(39, List.of(
                new JsonIssueDetails("source1", "code1", "error msg")));

        var mediaWarning = new JsonMediaIssue(40, List.of(
                new JsonIssueDetails("source2", "code2", "warning msg")));

        var media1 = JsonMediaOutputObject.factory(
                29L, -1L, "http://localhost/bucket/media1", null, "VIDEO", "video/mp4", 30,
                ImmutableSortedSet.of(new JsonMediaRange(31, 32)),
                ImmutableSortedSet.of(new JsonMediaRange(33, 34)), "SHA1", null,
                ImmutableSortedMap.of("META1", "META1VALUE"),
                ImmutableSortedMap.of("MEDIA_PROP1", "MEDIA_PROP1_VALUE"),
                new JsonMarkupOutputObject(35, "http://localhost/bucket/markup", "complete", null),
                detectionTypeMap,
                ImmutableSortedMap.of("ALGO", ImmutableSortedSet.of(detectionError)));


        var media2 = JsonMediaOutputObject.factory(
                290L, -1L, "http://localhost/bucket/media2", null, "IMAGE", "image/png", 300,
                ImmutableSortedSet.of(),
                ImmutableSortedSet.of(), "WRONG_SHA", null,
                ImmutableSortedMap.of("META2", "META2VALUE"),
                ImmutableSortedMap.of(MpfConstants.LINKED_MEDIA_HASH, "SHA2"),
                null,
                ImmutableSortedMap.of(),
                ImmutableSortedMap.of("ALGO2", ImmutableSortedSet.of()));

        var startTime = Instant.now();
        var endTime = startTime.plusSeconds(1000);
        var outputObject = JsonOutputObject.factory(
                "localhost-36", null, "id",
                new JsonPipeline("PIPELINE_NAME", "pipeline description"),
                9, "site", "version", "external id",
                startTime, endTime,
                "COMPLETE_WITH_ERRORS", Map.of("ALGO", Map.of("ALGO_KEY", "ALGO_VALUE")),
                Map.of("JOB_PROP", "JOB_VALUE"),
                Map.of("ENV_VAR1", "ENV_VALUE"),
                List.of(media1, media2),
                List.of(mediaError),
                List.of(mediaWarning));

        when(_mockAggJobProps.getCombinedProperties(job))
            .thenReturn(name -> {
                if (name.equals(MpfConstants.TIES_DB_S3_COPY_ENABLED)) {
                    return "true";
                }
                else {
                    return name;
                }
            });

        when(_mockS3StorageBackend.getOldJobOutputObject(
                eq(URI.create("http://localhost/bucket/output-object")), isNotNull()))
            .thenReturn(outputObject);

        var uriMappings = Map.of(
                URI.create("http://localhost/bucket/artifact1"),
                URI.create("http://localhost/dest-bucket/artifact1"),

                URI.create("http://localhost/bucket/artifact2"),
                URI.create("http://localhost/dest-bucket/artifact2"),

                URI.create("http://localhost/bucket/markup"),
                URI.create("http://localhost/dest-bucket/markup"));

        when(_mockS3StorageBackend.copyResults(eq(uriMappings.keySet()), isNotNull()))
                .thenReturn(uriMappings);

        when(_mockPropertiesUtil.getExportedJobId(36))
                .thenReturn("localhost-36");

        var newOutputObjectCaptor = ArgumentCaptor.forClass(JsonOutputObject.class);
        when(_mockS3StorageBackend.store(newOutputObjectCaptor.capture(), isNotNull()))
                .thenReturn(URI.create("http://localhost/dest-bucket/output-object"));


        var jobRequest = new JobRequest();
        var newOutputObjectUri = _tiesDbBeforeJobCheckService.updateOutputObject(
                job, URI.create("http://localhost/bucket/output-object"), jobRequest);

        assertEquals(
                URI.create("http://localhost/dest-bucket/output-object"), newOutputObjectUri);
        assertEquals("PAST JOB FOUND", jobRequest.getTiesDbStatus());

        verify(_mockInProgressJobs)
            .setJobStatus(36, BatchJobStatusType.COMPLETE_WITH_ERRORS);

        var newOutputObject = newOutputObjectCaptor.getValue();
        var outputObjectChecker = new FieldChecker<>(outputObject, newOutputObject);
        outputObjectChecker.eq(j -> j.getJobId());
        assertEquals(outputObject.getJobId(), newOutputObject.getTiesDbSourceJobId());
        outputObjectChecker.eq(j -> j.getObjectId());
        outputObjectChecker.eq(j -> j.getPipeline().getName());
        outputObjectChecker.eq(j -> j.getPipeline().getDescription());
        outputObjectChecker.eq(j -> j.getPipeline().getTasks());
        outputObjectChecker.eq(j -> j.getPriority());
        outputObjectChecker.eq(j -> j.getSiteId());
        outputObjectChecker.eq(j -> j.getOpenmpfVersion());
        outputObjectChecker.eq(j -> j.getExternalJobId());
        outputObjectChecker.eq(j -> j.getTimeStart().toEpochMilli());
        outputObjectChecker.eq(j -> j.getTimeStop().toEpochMilli());
        outputObjectChecker.eq(j -> j.getStatus());
        outputObjectChecker.eq(j -> j.getAlgorithmProperties());
        outputObjectChecker.eq(j -> j.getEnvironmentVariableProperties());
        outputObjectChecker.eq(j -> j.getJobProperties());
        outputObjectChecker.neq(j -> j.getMedia());
        outputObjectChecker.eq(j -> j.getErrors());
        outputObjectChecker.eq(j -> j.getWarnings());

        outputObjectChecker.eq(j -> j.getMedia().size());
        var newMedia1 = newOutputObject.getMedia().first();
        compareMedia(media1, newMedia1, "http://localhost/dest-bucket/media1");

        var newMedia2 = newOutputObject.getMedia().last();
        compareMedia(media2, newMedia2, "http://localhost/dest-bucket/media2");

        var newMarkup = newMedia1.getMarkupResult();
        var markupChecker = new FieldChecker<>(media1.getMarkupResult(), newMarkup);
        markupChecker.eq(m -> m.getId());
        assertEquals("http://localhost/dest-bucket/markup", newMarkup.getPath());
        markupChecker.eq(m -> m.getStatus());
        markupChecker.eq(m -> m.getMessage());

        var newAction1 = newMedia1.getDetectionTypes().get("type1").first();
        var action1Checker = new FieldChecker<>(action1, newAction1);
        action1Checker.eq(a -> a.getSource());
        action1Checker.eq(a -> a.getAlgorithm());
        action1Checker.eq(a -> a.getTracks().size());

        var newTrack1 = newAction1.getTracks().first();
        assertTracksEqualExceptDetections(track1, newTrack1);
        var newTrack1Exemplar = newTrack1.getExemplar();
        assertDetectionsEqualExceptArtifactPath(detection1, newTrack1Exemplar);
        assertEquals(
                "http://localhost/dest-bucket/artifact1",
                newTrack1Exemplar.getArtifactPath());

        var newDetection1 = newTrack1.getDetections().first();
        assertDetectionsEqualExceptArtifactPath(detection1, newDetection1);
        assertEquals(
                "http://localhost/dest-bucket/artifact1",
                newDetection1.getArtifactPath());

        var newDetection2 = newTrack1.getDetections().last();
        assertDetectionsEqualExceptArtifactPath(detection2, newDetection2);
        assertNull(newDetection2.getArtifactPath());


        var newAction2 = newMedia1.getDetectionTypes().get("type2").first();
        var action2Checker = new FieldChecker<>(action2, newAction2);
        action2Checker.eq(a -> a.getSource());
        action2Checker.eq(a -> a.getAlgorithm());
        action2Checker.eq(a -> a.getTracks().size());

        var newTrack2 = newAction2.getTracks().first();
        assertTracksEqualExceptDetections(track2, newTrack2);
        var newTrack2Exemplar = newTrack2.getExemplar();
        assertDetectionsEqualExceptArtifactPath(detection3, newTrack2Exemplar);
        assertEquals(
                "http://localhost/dest-bucket/artifact2",
                newTrack2Exemplar.getArtifactPath());

        var newDetection3 = newTrack2.getDetections().first();
        assertDetectionsEqualExceptArtifactPath(detection3, newDetection3);
        assertEquals(
                "http://localhost/dest-bucket/artifact2",
                newDetection3.getArtifactPath());
    }


    @Test
    public void testCopyFailure() throws StorageException, IOException {

        var job = mock(BatchJob.class);
        when(job.getId())
            .thenReturn(38L);
        when(job.getStatus())
            .thenReturn(BatchJobStatusType.IN_PROGRESS_ERRORS);

        var detection = new JsonDetectionOutputObject(
                1, 1, 1, 1, 0.5f, Collections.emptySortedMap(), 0, 0, "COMPLETED",
                "path");

        var track = new JsonTrackOutputObject(
                1, "id", 0, 0, 0, 0, "type", "source", 0.5f, Map.of(), detection,
                List.of(detection));

        var action = new JsonActionOutputObject("source", "algo");
        action.getTracks().add(track);

        var media = new JsonMediaOutputObject(
                39, -1, "path", null, "IMAGE", "image/png", 1, "SHA", "");
        media.getDetectionTypes().put("FACE", ImmutableSortedSet.of(action));

        var outputObject = new JsonOutputObject(
                null, null, null, 4, null, null, null, null, null, null);
        outputObject.getMedia().add(media);

        doThrow(new StorageException("TEST_MSG"))
                .when(_mockS3StorageBackend).copyResults(any(), any());

        var jobProps = Map.of(
                MpfConstants.S3_RESULTS_BUCKET, "http://localhost:2000/bucket",
                MpfConstants.S3_ACCESS_KEY, "ACCESS_KEY",
                MpfConstants.S3_SECRET_KEY, "SECRET_KEY",
                MpfConstants.TIES_DB_S3_COPY_ENABLED, "TRUE");

        when(_mockAggJobProps.getCombinedProperties(job))
            .thenReturn(jobProps::get);

        var oldOutputObjectUri = URI.create("http://localhost/bucket/output-object");

        when(_mockS3StorageBackend.getOldJobOutputObject(
                eq(oldOutputObjectUri), isNotNull()))
            .thenReturn(outputObject);

        var jobRequest = new JobRequest();

        var newOutputObjectUri = _tiesDbBeforeJobCheckService.updateOutputObject(
                job, oldOutputObjectUri, jobRequest);

        assertThat(jobRequest.getTiesDbStatus(), Matchers.startsWith("COPY ERROR: "));
        assertThat(jobRequest.getTiesDbStatus(), Matchers.containsString("TEST_MSG"));

        verify(_mockInProgressJobs).addFatalError(
                eq(38L),
                eq(IssueCodes.TIES_DB_BEFORE_JOB_CHECK),
                contains("TEST_MSG"));

        verify(_mockInProgressJobs)
            .setJobStatus(38L, BatchJobStatusType.COMPLETE_WITH_ERRORS);

        assertEquals(oldOutputObjectUri, newOutputObjectUri);
    }


    private static void compareMedia(
            JsonMediaOutputObject oldMedia, JsonMediaOutputObject newMedia, String newMediaPath) {

        var mediaChecker = new FieldChecker<>(oldMedia, newMedia);
        mediaChecker.eq(m -> m.getMediaId());
        mediaChecker.eq(m -> m.getParentMediaId());
        assertEquals(newMediaPath, newMedia.getPath());
        assertEquals(oldMedia.getPath(), newMedia.getTiesDbSourceMediaPath());
        mediaChecker.eq(m -> m.getType());
        mediaChecker.eq(m -> m.getMimeType());
        mediaChecker.eq(m -> m.getLength());
        mediaChecker.eq(m -> m.getFrameRanges());
        mediaChecker.eq(m -> m.getTimeRanges());
        mediaChecker.eq(m -> m.getSha256());
        mediaChecker.eq(m -> m.getStatus());
        mediaChecker.eq(m -> m.getMediaMetadata());
        mediaChecker.eq(m -> m.getMediaProperties());
        if (oldMedia.getMarkupResult() == null) {
            mediaChecker.eq(m -> m.getMarkupResult());
        }
        else {
            mediaChecker.neq(m -> m.getMarkupResult());
        }

        mediaChecker.eq(m -> m.getDetectionTypes().keySet());
        for (var key : oldMedia.getDetectionTypes().keySet()) {
            mediaChecker.eq(m -> m.getDetectionTypes().get(key).size());
        }
        mediaChecker.eq(m -> m.getDetectionProcessingErrors());
    }

    private static void assertTracksEqualExceptDetections(
            JsonTrackOutputObject expectedTrack, JsonTrackOutputObject actualTrack) {
        var checker = new FieldChecker<>(expectedTrack, actualTrack);
        checker.eq(t -> t.getIndex());
        checker.eq(t -> t.getId());
        checker.eq(t -> t.getStartOffsetFrame());
        checker.eq(t -> t.getStopOffsetFrame());
        checker.eq(t -> t.getStartOffsetTime());
        checker.eq(t -> t.getStopOffsetTime());
        checker.eq(t -> t.getType());
        checker.eq(t -> t.getSource());
        assertEquals(expectedTrack.getConfidence(), actualTrack.getConfidence(), 0.01);
        checker.eq(t -> t.getTrackProperties());
    }

    private static void assertDetectionsEqualExceptArtifactPath(
            JsonDetectionOutputObject expectedDetection,
            JsonDetectionOutputObject actualDetection) {

        var checker = new FieldChecker<>(expectedDetection, actualDetection);
        checker.eq(d -> d.getX());
        checker.eq(d -> d.getY());
        checker.eq(d -> d.getWidth());
        checker.eq(d -> d.getHeight());
        assertEquals(expectedDetection.getConfidence(), actualDetection.getConfidence(), 0.01);
        checker.eq(d -> d.getDetectionProperties());
        checker.eq(d -> d.getOffsetFrame());
        checker.eq(d -> d.getOffsetTime());
        checker.eq(d -> d.getArtifactExtractionStatus());
    }


    // When the output object is updated a lot of parameters need to be passed to JsonOutputObject
    // and its sub-objects making it easy to accidentally pass in arguments in the wrong order.
    // Using a regular call to assertEquals in the tests makes it possible to accidentally
    // have different fields for the two arguments to assertEquals.
    // Using this class ensures that same field is used for both arguments to assertEquals.
    private static record FieldChecker<T>(T expected, T actual) {
        public void eq(Function<T, Object> propGetter) {
            assertEquals(propGetter.apply(expected), propGetter.apply(actual));
        }

        public void neq(Function<T, Object> propGetter) {
            assertNotEquals(propGetter.apply(expected), propGetter.apply(actual));
        }
    }


    private CompletableFuture<HttpResponse> createHttpResponse(Object content) throws IOException {
        var response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        var entity = new BasicHttpEntity();
        response.setEntity(entity);
        var outStream = new ByteArrayOutputStream();
        _objectMapper.writeValue(outStream, content);
        entity.setContent(new ByteArrayInputStream(outStream.toByteArray()));
        return ThreadUtil.completedFuture(response);
    }

    private static CompletableFuture<HttpResponse> createErrorResponse(String errorMsg) {
        var response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 400, "BAD REQUEST");
        var entity = new BasicHttpEntity();
        response.setEntity(entity);
        var bytes = errorMsg.getBytes(StandardCharsets.UTF_8);
        entity.setContent(new ByteArrayInputStream(bytes));
        return ThreadUtil.completedFuture(response);
    }

    private HttpGet requestWithUri(String expectedUri) {
        return ArgumentMatchers.argThat(h -> {
            try {
                assertUrisMatch(URI.create(expectedUri), h.getURI());
                return true;
            }
            catch (AssertionError e) {
                return false;
            }
        });
    }


    private static void assertUriIsFirstPageTiesDbUri(URI actualUri) {
        var firstPageUri = URI.create(
            "http://tiesdb:1234/api/db/supplementals?sha256Hash=MEDIA_HASH&system=OpenMPF&offset=0&limit=100");
        assertUrisMatch(firstPageUri, actualUri);
    }

    private static void assertUrisMatch(URI expectedUri, URI actualUri) {
        assertEquals(expectedUri.getScheme(), actualUri.getScheme());
        assertEquals(expectedUri.getHost(), actualUri.getHost());
        assertEquals(expectedUri.getPort(), actualUri.getPort());
        assertEquals(expectedUri.getPath(), actualUri.getPath());
        assertEquals(getQueryMap(expectedUri), getQueryMap(actualUri));
    }

    private static Map<String, String> getQueryMap(URI uri) {
        return new URIBuilder(uri)
            .getQueryParams()
            .stream()
            .collect(toMap(NameValuePair::getName, NameValuePair::getValue));
    }

    private void setEmptyCombinedJobProps() {
        when(_mockAggJobProps.getCombinedProperties(any(), any(), any(), any()))
            .thenReturn(s -> null);
    }
}
