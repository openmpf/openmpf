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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.camel.Exchange;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHttpResponse;
import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.rest.api.JobCreationRequest;
import org.mitre.mpf.rest.api.TiesDbCheckStatus;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
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
import org.mockito.MockitoAnnotations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;

public class TestTiesDbBeforeJobCheckService {

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

    private TiesDbBeforeJobCheckServiceImpl _tiesDbBeforeJobCheckService;


    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);

        when(_mockPropertiesUtil.getHttpCallbackRetryCount())
            .thenReturn(3);

        _tiesDbBeforeJobCheckService = new TiesDbBeforeJobCheckServiceImpl(
                _mockPropertiesUtil,
                _mockAggJobProps,
                _mockJobConfigHasher,
                _mockHttpClientUtils,
                _objectMapper,
                _mockInProgressJobs);
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
        var elements = mock(JobPipelineElements.class);
        when(elements.getAllActions())
            .thenReturn(ImmutableList.of(mock(Action.class)));

        var media1 = mock(Media.class);
        when(media1.getHash())
                .thenReturn(Optional.of("HASH"));

        var media2 = mock(Media.class);
        when(media2.getHash())
                .thenReturn(Optional.empty());

        when(_mockAggJobProps.getMediaActionProps(any(), any(), any(), eq(elements)))
            .thenReturn((p, m, a) -> null);


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
        var elements = mock(JobPipelineElements.class);
        when(elements.getAllActions())
            .thenReturn(ImmutableList.of(mock(Action.class)));

        var media1 = mock(Media.class);
        when(media1.getHash())
                .thenReturn(Optional.of("HASH"));
        when(media1.getMimeType())
                .thenReturn(Optional.of("image/jpeg"));

        var media2 = mock(Media.class);
        when(media2.getHash())
                .thenReturn(Optional.of("HASH2"));
        when(media2.getMimeType())
                .thenReturn(Optional.empty());

        when(_mockAggJobProps.getMediaActionProps(any(), any(), any(), eq(elements)))
            .thenReturn((p, m, a) -> null);


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
        var elements = mock(JobPipelineElements.class);
        when(elements.getAllActions())
            .thenReturn(ImmutableList.of(mock(Action.class)));

        var media = mock(Media.class);
        when(media.getHash())
                .thenReturn(Optional.of("HASH"));
        when(media.getMimeType())
                .thenReturn(Optional.of("image/jpeg"));


        when(_mockAggJobProps.getMediaActionProps(any(), any(), any(), eq(elements)))
            .thenReturn((p, m, a) -> null);


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
        when(media.getHash())
                .thenReturn(Optional.of("HASH"));
        when(media.getMimeType())
                .thenReturn(Optional.of("image/jpeg"));

        var mockMediaActionProps = mock(MediaActionProps.class);
        when(mockMediaActionProps.get(MpfConstants.TIES_DB_URL, media, action))
            .thenReturn(":invalid_uri");

        when(_mockAggJobProps.getMediaActionProps(any(), any(), any(), eq(elements)))
            .thenReturn(mockMediaActionProps);


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
        var checkResult = setupSingleTiesDbUriTest(tiesDbData);
        assertEquals(TiesDbCheckStatus.NO_MATCH, checkResult.status());
        assertTrue(checkResult.checkInfo().isEmpty());
    }

    private TiesDbCheckResult setupSingleTiesDbUriTest(Object tiesDbData) throws IOException {
        var action = mock(Action.class);
        var elements = mock(JobPipelineElements.class);
        when(elements.getAllActions())
            .thenReturn(ImmutableList.of(action));

        var media = mock(Media.class);
        when(media.getHash())
                .thenReturn(Optional.of("HASH"));
        when(media.getMimeType())
                .thenReturn(Optional.of("image/jpeg"));

        var mockMediaActionProps = mock(MediaActionProps.class);
        when(mockMediaActionProps.get(MpfConstants.TIES_DB_URL, media, action))
            .thenReturn("http://tiesdb:1234");

        when(_mockAggJobProps.getMediaActionProps(any(), any(), any(), eq(elements)))
            .thenReturn(mockMediaActionProps);

        when(_mockJobConfigHasher.getJobConfigHash(List.of(media), elements, mockMediaActionProps))
            .thenReturn("JOB_HASH");

        var requestCaptor = ArgumentCaptor.forClass(HttpGet.class);
        when(_mockHttpClientUtils.executeRequest(requestCaptor.capture(), eq(3)))
            .thenReturn(createHttpResponse(tiesDbData));

        var result = _tiesDbBeforeJobCheckService.checkTiesDbBeforeJob(
                new JobCreationRequest(),
                null,
                List.of(media),
                elements);

        var request = requestCaptor.getValue();
        assertThat(request, instanceOf(HttpGet.class));
        assertEquals(URI.create("http://tiesdb:1234/api/db/supplementals?sha256Hash=HASH"),
                request.getURI());

        return result;
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

        var checkResult = setupSingleTiesDbUriTest(tiesDbData);
        assertEquals(TiesDbCheckStatus.FOUND_MATCH, checkResult.status());
        assertTrue(checkResult.checkInfo().isPresent());

        var info = checkResult.checkInfo().get();
        assertEquals(URI.create("file:///1.json"), info.outputObjectUri());
        assertEquals(BatchJobStatusType.COMPLETE, info.jobStatus());
        assertEquals(Instant.parse("2021-06-04T13:35:58.981Z"), info.processDate());
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
        when(media1.getHash())
                .thenReturn(Optional.of("HASH"));
        when(media1.getMimeType())
                .thenReturn(Optional.of("image/jpeg"));
        var media2 = mock(Media.class);
        when(media2.getHash())
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

        when(_mockJobConfigHasher.getJobConfigHash(
                    List.of(media1, media2), elements, mockMediaActionProps))
            .thenReturn("JOB_HASH");

        doReturn(createHttpResponse(tiesDbData))
            .when(_mockHttpClientUtils)
            .executeRequest(
                requestWithUri("http://tiesdb:1234/api/db/supplementals?sha256Hash=HASH"),
                eq(3));

        doReturn(createErrorResponse("test-error"))
            .when(_mockHttpClientUtils)
            .executeRequest(
                requestWithUri("http://tiesdb-error:1234/api/db/supplementals?sha256Hash=HASH2"),
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
            URI.create("file:///1.json"),
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
        assertEquals(
                URI.create("http://tiesdb:1234/api/db/supplementals?sha256Hash=MEDIA_HASH"),
                request.getURI());

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
        when(media.getHash())
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
        verifyZeroInteractions(
                _mockPropertiesUtil,
                _mockAggJobProps,
                _mockJobConfigHasher,
                _mockHttpClientUtils);
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

    private HttpGet requestWithUri(String uri) {
        return ArgumentMatchers.argThat(h -> h.getURI().equals(URI.create(uri)));
    }
}
