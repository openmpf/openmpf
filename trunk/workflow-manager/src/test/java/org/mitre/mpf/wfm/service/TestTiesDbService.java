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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpResponse;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.mpf.interop.JsonActionTiming;
import org.mitre.mpf.interop.JsonTiming;
import org.mitre.mpf.mvc.security.OAuthClientTokenProvider;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.ActionType;
import org.mitre.mpf.rest.api.pipelines.Algorithm;
import org.mitre.mpf.rest.api.pipelines.Pipeline;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.access.JobRequestDao;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJobImpl;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.data.entities.persistent.MediaImpl;
import org.mitre.mpf.wfm.data.entities.persistent.TiesDbInfo;
import org.mitre.mpf.wfm.data.entities.transients.TrackCounter;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.HttpClientUtils;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.MediaActionProps;
import org.mitre.mpf.wfm.util.ObjectMapperFactory;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.hamcrest.MockitoHamcrest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSortedSet;

public class TestTiesDbService extends MockitoTest.Strict {

    @Mock
    private PropertiesUtil _mockPropertiesUtil;

    @Mock
    private AggregateJobPropertiesUtil _mockAggregateJobPropertiesUtil;

    private final ObjectMapper _objectMapper = ObjectMapperFactory.customObjectMapper();

    private final JsonUtils _jsonUtils = new JsonUtils(_objectMapper);

    @Mock
    private HttpClientUtils _mockHttpClientUtils;

    @Mock
    private OAuthClientTokenProvider _mockOAuthClientTokenProvider;

    @Mock
    private JobRequestDao _mockJobRequestDao;

    @Mock
    private InProgressBatchJobsService _mockInProgressJobs;

    @Mock
    private JobConfigHasher _mockJobConfigHasher;

    private TiesDbService _tiesDbService;

    private static final Instant PROCESS_DATE = Instant.ofEpochSecond(1622724824);

    private MediaImpl _tiesDbMedia;

    private MediaImpl _tiesDbParentMedia;

    private MediaImpl _tiesDbChildMedia;

    private MediaImpl _noTiesDbMedia;

    private List<MediaImpl> _allMedia;

    private BatchJobImpl _job;


    @BeforeClass
    public static void initClass() {
        ThreadUtil.start();
    }


    @Before
    public void init() {
        initTestMedia();
        initJob();
        _tiesDbService = new TiesDbService(
                _mockPropertiesUtil,
                _mockAggregateJobPropertiesUtil,
                _objectMapper,
                _jsonUtils,
                _mockHttpClientUtils,
                _mockOAuthClientTokenProvider,
                _mockJobRequestDao,
                _mockInProgressJobs,
                _mockJobConfigHasher);

        lenient().when(_mockPropertiesUtil.getHttpCallbackRetryCount())
                .thenReturn(3);
    }


    @Test
    public void testRemoteDownloadError() {
        _job.addError(
                _tiesDbParentMedia.getId(),
                "src",
                IssueCodes.REMOTE_STORAGE_DOWNLOAD.toString(),
                "msg");

        _tiesDbService.prepareAssertions(
                _job,
                null,
                null,
                null,
                null,
                null,
                null);

        verify(_mockJobRequestDao)
            .setTiesDbError(eq(123L), TestUtil.nonBlank());
        verifyNoMoreInteractions(_mockJobRequestDao);
    }


    @Test
    public void testAddAssertion() {
        when(_mockPropertiesUtil.getSemanticVersion())
                .thenReturn("X.Y");
        when(_mockPropertiesUtil.getHostName())
                .thenReturn("host");

        when(_mockPropertiesUtil.getExportedJobId(123L))
                .thenReturn("host-123");

        when(_mockAggregateJobPropertiesUtil.getValue(MpfConstants.TIES_DB_URL, _job, _tiesDbMedia))
                .thenReturn("http://tiesdb");

        when(_mockAggregateJobPropertiesUtil.getValue(MpfConstants.TIES_DB_URL, _job, _tiesDbParentMedia))
                .thenReturn("http://tiesdbForParent");

        var pipelineElements = _job.getPipelineElements();
        var mediaActionProps = new MediaActionProps((m, a) -> Map.of());
        when(_mockAggregateJobPropertiesUtil.getMediaActionProps(
                    any(), any(), any(), eq(pipelineElements)))
                .thenReturn(mediaActionProps);

        when(_mockJobConfigHasher.getJobConfigHash(
                    argThat(m -> m.containsAll(_job.getMedia())),
                    eq(pipelineElements),
                    eq(mediaActionProps)))
                .thenReturn("FAKE_JOB_CONFIG_HASH");


        var trackCounter = new TrackCounter();
        trackCounter.add(_tiesDbMedia, 15);
        trackCounter.add(_tiesDbMedia, 5);
        trackCounter.add(_tiesDbParentMedia, 8);
        trackCounter.add(_tiesDbChildMedia, 2);

        var timing = new JsonTiming(
            1349,
            List.of(new JsonActionTiming("ACTION_0", 876), new JsonActionTiming("ACTION_1", 473)));

        _tiesDbService.prepareAssertions(
                _job,
                BatchJobStatusType.COMPLETE,
                PROCESS_DATE,
                URI.create("file:///fake-path"),
                "FAKE_OUTPUT_OBJECT_SHA",
                trackCounter,
                timing);

        {
            var tiesDbInfoCaptor = ArgumentCaptor.forClass(TiesDbInfo.class);
            verify(_mockInProgressJobs)
                    .addTiesDbInfo(
                            eq(123L), eq(_tiesDbMedia.getId()), tiesDbInfoCaptor.capture());

            var tiesDbInfo = tiesDbInfoCaptor.getValue();
            var expectedTiesDbInfo = createExpectedTiesDbInfo();
            assertEquals(expectedTiesDbInfo.tiesDbUrl(), tiesDbInfo.tiesDbUrl());
            assertFalse(tiesDbInfo.assertion().assertionId().isBlank());

            var dataObject = tiesDbInfo.assertion().dataObject();
            var expectedDataObject = expectedTiesDbInfo.assertion().dataObject();
            assertEquals(expectedDataObject, dataObject);
        }

        {
            var tiesDbInfoCaptor = ArgumentCaptor.forClass(TiesDbInfo.class);
            verify(_mockInProgressJobs)
                    .addTiesDbInfo(
                            eq(123L), eq(_tiesDbParentMedia.getId()),
                            tiesDbInfoCaptor.capture());

            var tiesDbInfo = tiesDbInfoCaptor.getValue();
            var expectedTiesDbInfo = createExpectedParentTiesDbInfo();
            assertEquals(expectedTiesDbInfo.tiesDbUrl(), tiesDbInfo.tiesDbUrl());
            assertFalse(tiesDbInfo.assertion().assertionId().isBlank());

            var dataObject = tiesDbInfo.assertion().dataObject();
            var expectedDataObject = expectedTiesDbInfo.assertion().dataObject();
            assertEquals(expectedDataObject, dataObject);
        }
    }


    @Test
    public void doesNotPostWhenNoTiesDbInfo() {
        var future = _tiesDbService.postAssertions(_job);
        assertTrue(future.isDone());
        verifyNoInteractions(_mockHttpClientUtils, _mockJobRequestDao);
    }


    @Test
    public void testPostAssertions() throws IOException {

        var expectedTiesDbInfo = createExpectedTiesDbInfo();
        _tiesDbMedia.setTiesDbInfo(expectedTiesDbInfo);

        var expectedParentTiesDbInfo = createExpectedParentTiesDbInfo();
        _tiesDbParentMedia.setTiesDbInfo(expectedParentTiesDbInfo);

        when(_mockAggregateJobPropertiesUtil.getValue(
                MpfConstants.TIES_DB_USE_OIDC, _job, _tiesDbMedia))
                .thenReturn("true");

        var httpRespFuture = ThreadUtil.<HttpResponse>newFuture();
        var httpRespFuture2 = ThreadUtil.<HttpResponse>newFuture();
        var httpRequestCaptor = ArgumentCaptor.forClass(HttpPost.class);
        when(_mockHttpClientUtils.executeRequest(httpRequestCaptor.capture(), eq(3), any()))
                .thenReturn(httpRespFuture)
                .thenReturn(httpRespFuture2);

        var postAllAssertionsFuture = _tiesDbService.postAssertions(_job);
        assertFalse(postAllAssertionsFuture.isDone());
        verify(_mockJobRequestDao, never())
                .setTiesDbSuccessful(anyLong());

        httpRespFuture.complete(new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK"));
        // Make sure future doesn't complete until both responses have been received.
        assertFalse(postAllAssertionsFuture.isDone());
        verify(_mockJobRequestDao, never())
                .setTiesDbSuccessful(anyLong());

        httpRespFuture2.complete(new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK"));
        postAllAssertionsFuture.join();
        assertTrue(postAllAssertionsFuture.isDone());

        verify(_mockJobRequestDao)
                .setTiesDbSuccessful(_job.getId());

        assertEquals(2, httpRequestCaptor.getAllValues().size());
        var expectedUriForRegularMedia = URI.create(
            "http://tiesdb/api/db/supplementals?sha256Hash=MEDIA_676_HASH");
        var httpRequest = httpRequestCaptor.getAllValues()
                .stream()
                .filter(r -> r.getURI().equals(expectedUriForRegularMedia))
                .findAny()
                .orElseThrow();
        var postedAssertion = _objectMapper.readValue(
                httpRequest.getEntity().getContent(),
                TiesDbInfo.Assertion.class);
        assertEquals(expectedTiesDbInfo.assertion(), postedAssertion);
        verify(_mockOAuthClientTokenProvider)
                .addToken(httpRequest);


        var parentHttpRequest = httpRequestCaptor.getAllValues()
                .stream()
                .filter(p -> p != httpRequest)
                .findAny()
                .orElseThrow();
        assertEquals(
                URI.create("http://tiesdbForParent/api/db/supplementals?sha256Hash=LINKED_MEDIA_HASH"),
                parentHttpRequest.getURI());
        var parentPostedAssertion = _objectMapper.readValue(
                parentHttpRequest.getEntity().getContent(),
                TiesDbInfo.Assertion.class);
        assertEquals(expectedParentTiesDbInfo.assertion(), parentPostedAssertion);
        verify(_mockOAuthClientTokenProvider, never())
                .addToken(parentHttpRequest);
    }


    @Test
    public void canHandleInvalidUri() {
        var tiesDbInfo = new TiesDbInfo("BAD URI", null);
        _tiesDbMedia.setTiesDbInfo(tiesDbInfo);

        var future = _tiesDbService.postAssertions(_job);
        var ex = TestUtil.assertThrows(
                CompletionException.class, future::join);
        assertTrue(Throwables.getRootCause(ex) instanceof URISyntaxException);
        verify(_mockJobRequestDao)
                .setTiesDbError(eq(_job.getId()), TestUtil.nonBlank());
    }


    @Test
    public void doesNotRetryOnMediaMissingFromTiesDb() throws UnsupportedEncodingException {
        var tiesDbInfo = createExpectedTiesDbInfo();
        _tiesDbMedia.setTiesDbInfo(tiesDbInfo);

        var errorMsg = "<other content> could not identify referenced item <other content>";
        when(_mockHttpClientUtils.executeRequest(any(), eq(3), any()))
                .then(inv -> {
                    var errorResponse = createErrorResponse(errorMsg);
                    var retryPred = inv.getArgument(2, Predicate.class);
                    assertFalse(retryPred.test(errorResponse));
                    return ThreadUtil.completedFuture(errorResponse);
                });

        var future = _tiesDbService.postAssertions(_job);
        var exception = TestUtil.assertThrows(CompletionException.class, future::join);
        assertThat(exception.getMessage(), containsString(errorMsg));

        verify(_mockJobRequestDao)
                .setTiesDbError(
                        eq(_job.getId()), MockitoHamcrest.argThat(containsString(errorMsg)));
        verifyNoMoreInteractions(_mockJobRequestDao);
    }


    private static HttpResponse createErrorResponse(String errorMsg) throws UnsupportedEncodingException {
        var response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 400, "BAD REQUEST");
        response.setEntity(new StringEntity(errorMsg));
        return response;
    }

    @Test
    public void testRepost() {
        long successfulJob1 = 100;
        initRepostJob(successfulJob1);
        long successfulJob2 = 101;
        initRepostJob(successfulJob2);
        long runningJob = 102;
        initRepostJob(runningJob);
        long failureJob1 = 500;
        initRepostJob(failureJob1);
        long failureJob2 = 501;
        initRepostJob(failureJob2);

        when(_mockInProgressJobs.containsJob(anyLong()))
                .thenReturn(false);
        when(_mockInProgressJobs.containsJob(runningJob))
                .thenReturn(true);

        long missingJob = 404;
        when(_mockJobRequestDao.findById(missingJob))
                .thenReturn(null);

        var svcSpy = spy(_tiesDbService);
        doReturn(ThreadUtil.completedFuture(null))
                .when(svcSpy)
                .postAssertions(argThat(
                        j -> j.getId() == successfulJob1 || j.getId() == successfulJob2));
        doReturn(ThreadUtil.failedFuture(new IllegalStateException("Failure for job 500")))
                .when(svcSpy)
                .postAssertions(argThat(j -> j.getId() == failureJob1));
        doReturn(ThreadUtil.failedFuture(new IllegalStateException("Different failure")))
                .when(svcSpy)
                .postAssertions(argThat(j -> j.getId() == failureJob2));


        var result = svcSpy.repost(List.of(
                successfulJob1, successfulJob2, missingJob, runningJob, failureJob1, failureJob2));
        assertThat(result.success(), is(List.of(successfulJob1, successfulJob2)));

        var failures = result.failures();
        assertThat(failures, hasSize(4));
        var errorsMap = failures.stream()
                .collect(toMap(f -> f.jobId(), f -> f.error()));

        assertThat(errorsMap.get(missingJob), containsString("Could not find job"));
        assertThat(errorsMap.get(runningJob), is("Job is still running."));
        assertThat(errorsMap.get(failureJob1), is("Failure for job 500"));
        assertThat(errorsMap.get(failureJob2), is("Different failure"));
    }


    private void initRepostJob(long jobId) {
        var job = new BatchJobImpl(
                jobId,
                null,
                null,
                null,
                0,
                null,
                null,
                List.of(),
                Map.of(),
                Map.of(),
                false);

        var jobRequest = new JobRequest();
        jobRequest.setJob(_jsonUtils.serialize(job));
        when(_mockJobRequestDao.findById(jobId))
                .thenReturn(jobRequest);
    }


    private static JobPipelineElements createTestPipeline() {

        var algo0 = createAlgorithm(0, "PARENT_SUPPRESSED");
        var action0 = createAction(0, algo0);
        var task0 = createTask(0, action0);

        var algo1 = createAlgorithm(1, "MERGE_SOURCE");
        var action1 = createAction(1, algo1);
        var task1 = createTask(1, action1);

        var algo2 = createAlgorithm(2, "MERGE_SOURCE_AND_TARGET");
        var action2 = createAction(2, algo2);
        var task2 = createTask(2, action2);

        var algo3 = createAlgorithm(3, "MERGE_SOURCE0");
        var action3 = createAction(3, algo3);
        var algo4 = createAlgorithm(4, "MERGE_SOURCE1");
        var action4 = createAction(4, algo4);
        var task3 = createTask(3, action3, action4);

        var tasks = List.of(task0, task1, task2, task3);
        var taskNames = tasks.stream().map(Task::name).toList();
        var pipeline = new Pipeline("TEST_PIPELINE", "", taskNames);
        return new JobPipelineElements(
                pipeline, tasks,
                List.of(action0, action1, action2, action3, action4),
                List.of(algo0, algo1, algo2, algo3, algo4));
    }


    private static Algorithm createAlgorithm(int idx, String trackType) {
        return new Algorithm(
            "ALGO_" + idx,
            null,
            ActionType.DETECTION,
            trackType,
            null,
            null,
            null,
            false,
            false);
    }


    private static Action createAction(int idx, Algorithm algorithm) {
        return new Action("ACTION_" + idx, null, algorithm.name(), List.of());
    }

    private static Task createTask(int idx, Action... actions) {
        var names = Stream.of(actions)
            .map(Action::name)
            .toList();
        return new Task("TASK_" + idx, null, names);
    }

    private static TiesDbInfo createExpectedTiesDbInfo() {
        var timing = new JsonTiming(
                1349,
                List.of(
                        new JsonActionTiming("ACTION_0", 876),
                        new JsonActionTiming("ACTION_1", 473)));

        var dataObject = new TiesDbInfo.DataObject(
                "TEST_PIPELINE",
                ImmutableSortedSet.of(
                        "PARENT_SUPPRESSED", "MERGE_SOURCE", "MERGE_SOURCE_AND_TARGET",
                        "MERGE_SOURCE0", "MERGE_SOURCE1"),
                "host-123",
                "file:///fake-path",
                "FAKE_OUTPUT_OBJECT_SHA",
                PROCESS_DATE,
                BatchJobStatusType.COMPLETE,
                "X.Y",
                "host",
                20,
                "FAKE_JOB_CONFIG_HASH",
                timing);

        var assertion = new TiesDbInfo.Assertion("ASSERTION_ID", dataObject);
        return new TiesDbInfo("http://tiesdb", assertion);
    }


    private static TiesDbInfo createExpectedParentTiesDbInfo() {
        var timing = new JsonTiming(
                1349,
                List.of(
                        new JsonActionTiming("ACTION_0", 876),
                        new JsonActionTiming("ACTION_1", 473)));

        var dataObject = new TiesDbInfo.DataObject(
                "TEST_PIPELINE",
                ImmutableSortedSet.of("PARENT_SUPPRESSED",
                        "MERGE_SOURCE", "MERGE_SOURCE_AND_TARGET", "MERGE_SOURCE0",
                        "MERGE_SOURCE1"),
                "host-123",
                "file:///fake-path",
                "FAKE_OUTPUT_OBJECT_SHA",
                PROCESS_DATE,
                BatchJobStatusType.COMPLETE,
                "X.Y",
                "host",
                10,
                "FAKE_JOB_CONFIG_HASH",
                timing);

        var assertion = new TiesDbInfo.Assertion("ASSERTION_ID", dataObject);
        return new TiesDbInfo("http://tiesdbForParent", assertion);
    }


    private void initTestMedia() {
        _tiesDbMedia = new MediaImpl(
            676,
            "file:///media-676",
            UriScheme.FILE,
            null,
            Map.of(),
            Map.of("MEDIA_HASH", "MEDIA_676_HASH"),
            List.of(),
            List.of(),
            List.of(),
            null,
            null
        );

        _tiesDbParentMedia = new MediaImpl(
            677,
            "file:///media-677",
            UriScheme.FILE,
            null,
            Map.of(MpfConstants.LINKED_MEDIA_HASH, "LINKED_MEDIA_HASH"),
            Map.of("MEDIA_HASH", "PARENT_MEDIA_HASH"),
            List.of(),
            List.of(),
            List.of(),
            null,
            null
        );

        _tiesDbChildMedia = new MediaImpl(
            679,
            677,
            1,
            "file:///media-679",
            UriScheme.FILE,
            null,
            Map.of(),
            Map.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null);
        _tiesDbChildMedia.addMetadata(MpfConstants.IS_DERIVATIVE_MEDIA, "true");


        _noTiesDbMedia = new MediaImpl(
            678,
            "file:///media-678",
            UriScheme.FILE,
            null,
            Map.of(),
            Map.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null
        );
        _allMedia = List.of(_tiesDbMedia, _tiesDbParentMedia, _tiesDbChildMedia, _noTiesDbMedia);
    }

    private void initJob() {
        _job = new BatchJobImpl(
            123,
            null,
            null,
            createTestPipeline(),
            1,
            null,
            null,
            _allMedia,
            Map.of(),
            Map.of(),
            false);
        _job.addError(
                _tiesDbParentMedia.getId(),
                "src",
                IssueCodes.ARTIFACT_EXTRACTION.toString(),
                "msg");
    }
}
