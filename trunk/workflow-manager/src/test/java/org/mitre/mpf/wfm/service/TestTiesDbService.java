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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHttpResponse;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.mpf.rest.api.pipelines.*;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.access.JobRequestDao;
import org.mitre.mpf.wfm.data.entities.persistent.*;
import org.mitre.mpf.wfm.data.entities.transients.TrackCounter;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.function.Predicate;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mitre.mpf.test.TestUtil.nonBlank;
import static org.mockito.AdditionalMatchers.or;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;

public class TestTiesDbService {

    private static final Logger LOG = LoggerFactory.getLogger(TestTiesDbService.class);

    @Mock
    private PropertiesUtil _mockPropertiesUtil;

    @Mock
    private AggregateJobPropertiesUtil _mockAggregateJobPropertiesUtil;

    private final ObjectMapper _objectMapper = ObjectMapperFactory.customObjectMapper();

    private final JsonUtils _jsonUtils = new JsonUtils(_objectMapper);

    @Mock
    private CallbackUtils _mockCallbackUtils;

    @Mock
    private JobRequestDao _mockJobRequestDao;

    @Mock
    private InProgressBatchJobsService _mockInProgressJobs;

    private TiesDbService _tiesDbService;


    @BeforeClass
    public static void initClass() {
        ThreadUtil.start();
    }

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        _tiesDbService = new TiesDbService(
                _mockPropertiesUtil,
                _mockAggregateJobPropertiesUtil,
                _objectMapper,
                _jsonUtils,
                _mockCallbackUtils,
                _mockJobRequestDao,
                _mockInProgressJobs);

        when(_mockPropertiesUtil.getSemanticVersion())
                .thenReturn("1.5");

        when(_mockPropertiesUtil.getHttpCallbackTimeoutMs())
                .thenReturn(200);

        when(_mockPropertiesUtil.getHttpCallbackRetryCount())
                .thenReturn(3);

        when(_mockPropertiesUtil.getHostName())
                .thenReturn("localhost");

        when(_mockPropertiesUtil.getExportedJobId(anyLong()))
                .thenReturn("localhost-123");
    }


    @Test
    public void testAddAssertions() {
        var media1 = new MediaImpl(321, "file:///media1", null, null, Map.of(), Map.of(),
                                   List.of(), List.of(), null);
        media1.setSha256("MEDIA1_SHA");

        var media2 = new MediaImpl(325, "file:///media2", null, null, Map.of(), Map.of(),
                                   List.of(), List.of(), null);
        media2.setSha256("MEDIA2_SHA");

        var algo1 = new Algorithm("ALGO1", null, null, null, null, true, false);
        var algo2 = new Algorithm("ALGO2", null, null, null, null, true, false);

        var action1 = new Action("ACTION1", null, algo1.getName(), List.of());
        var action2 = new Action("ACTION2", null, algo2.getName(), List.of());

        var task1 = new Task("TASK1", null, List.of(action1.getName()));
        var task2 = new Task("TASK2", null, List.of(action2.getName()));

        var pipeline = new Pipeline("PIPELINE", null, List.of(task1.getName(), task2.getName()));
        var pipelineElements = new JobPipelineElements(
                pipeline, List.of(task1, task2), List.of(action1, action2), List.of(algo1, algo2));

        var job = new BatchJobImpl(
                123, null, null, pipelineElements, 4,
                null, null, List.of(media1, media2),
                Map.of(), Map.of());

        String url1 = "http://localhost:81/qwer";
        when(_mockAggregateJobPropertiesUtil.getValue(
                eq(MpfConstants.TIES_DB_URL),
                eq(job),
                or(eq(media1), eq(media2)),
                eq(action1)
        )).thenReturn(url1);

        String url2 = "http://localhost:90/qwer";
        when(_mockAggregateJobPropertiesUtil.getValue(
                eq(MpfConstants.TIES_DB_URL),
                eq(job),
                eq(media1),
                eq(action2)
        )).thenReturn(url2);

        var trackCounter = new TrackCounter();
        trackCounter.set(media1.getId(), 0, 0, "FACE", 100);
        trackCounter.set(media1.getId(), 1, 0, "CLASS", 200);

        trackCounter.set(media2.getId(), 0, 0, "FACE", 300);
        trackCounter.set(media2.getId(), 1, 0, "CLASS", 400);

        var timeCompleted = Instant.ofEpochSecond(1622724824);
        var outputObjectLocation = URI.create("http://localhost:321/asdf");
        var outputSha = "ed2e2a154b4bf6802c3f418a64488b7bf3f734fa9ebfd568cf302ae4e8f4c3bb";

        _tiesDbService.storeAssertions(
                job,
                BatchJobStatusType.COMPLETE_WITH_WARNINGS,
                timeCompleted,
                outputObjectLocation,
                outputSha,
                trackCounter);


        // action1 on media1, action1 on media2, and action2 on media1 are configured TiesDb.
        // action2 on media2 is not configured to use TiesDb
        verify(_mockInProgressJobs).addTiesDbInfo(
                eq(job.getId()),
                eq(media1.getId()),
                argThat(assertion -> assertionMatcher(
                        _mockPropertiesUtil.getExportedJobId(job.getId()),
                        BatchJobStatusType.COMPLETE_WITH_WARNINGS,
                        url1,
                        algo1.getName(),
                        "FACE",
                        100,
                        outputObjectLocation,
                        outputSha,
                        timeCompleted,
                        assertion)));

        verify(_mockInProgressJobs).addTiesDbInfo(
                eq(job.getId()),
                eq(media1.getId()),
                argThat(assertion -> assertionMatcher(
                        _mockPropertiesUtil.getExportedJobId(job.getId()),
                        BatchJobStatusType.COMPLETE_WITH_WARNINGS,
                        url2,
                        algo2.getName(),
                        "CLASS",
                        200,
                        outputObjectLocation,
                        outputSha,
                        timeCompleted,
                        assertion)));

        verify(_mockInProgressJobs).addTiesDbInfo(
                eq(job.getId()),
                eq(media2.getId()),
                argThat(assertion -> assertionMatcher(
                        _mockPropertiesUtil.getExportedJobId(job.getId()),
                        BatchJobStatusType.COMPLETE_WITH_WARNINGS,
                        url1,
                        algo1.getName(),
                        "FACE",
                        300,
                        outputObjectLocation,
                        outputSha,
                        timeCompleted,
                        assertion)));
    }


    @Test
    public void testTaskMerging() {
        testTwoStageTaskMerging("TYPE1", 451, 452);
    }

    @Test
    public void testTaskMergingLastTaskNoTracks() {
        testTwoStageTaskMerging("NO TRACKS", 451, 0);
    }

    @Test
    public void testTaskMergingFirstTaskNoTracks() {
        testTwoStageTaskMerging("TYPE1", 0, 452);
    }

    @Test
    public void testTaskMergingNoTracks() {
        testTwoStageTaskMerging("NO TRACKS", 0, 0);
    }


    private void testTwoStageTaskMerging(String expectedType,
                                         int task1TrackCount, int task2TrackCount) {
        var job = createTwoStageTestJob();
        String url = "http://localhost:81/qwer";
        setTiesDbUrlForMedia(url, job.getMedia(321));

        var tasksToMerge = Map.of(1, 0);
        when(_mockAggregateJobPropertiesUtil.getTasksToMerge(any(), any()))
                .thenReturn(tasksToMerge);

        for (int taskIndex : tasksToMerge.keySet()) {
            when(_mockAggregateJobPropertiesUtil.getValue(eq(MpfConstants.OUTPUT_MERGE_WITH_PREVIOUS_TASK_PROPERTY),
                    argThat(jp -> jp.getJob().equals(job) && jp.getTaskIndex() == taskIndex)))
                    .thenReturn("TRUE");
        }

        var trackCounter = new TrackCounter();
        trackCounter.set(321, 0, 0, "TYPE1", task1TrackCount);
        trackCounter.set(321, 1, 0, "TYPE2", task2TrackCount);

        var timeCompleted = Instant.ofEpochSecond(1622724824);
        var outputObjectLocation = URI.create("http://localhost:321/asdf");
        var outputSha = "ed2e2a154b4bf6802c3f418a64488b7bf3f734fa9ebfd568cf302ae4e8f4c3bb";


        _tiesDbService.storeAssertions(
                job,
                BatchJobStatusType.COMPLETE_WITH_WARNINGS,
                timeCompleted,
                outputObjectLocation,
                outputSha,
                trackCounter);

        verify(_mockInProgressJobs).addTiesDbInfo(
                eq(job.getId()),
                eq(job.getMedia().iterator().next().getId()),
                argThat(assertion -> assertionMatcher(
                        _mockPropertiesUtil.getExportedJobId(job.getId()),
                        BatchJobStatusType.COMPLETE_WITH_WARNINGS,
                        url,
                        "ALGO1",
                        expectedType,
                        task2TrackCount,
                        outputObjectLocation,
                        outputSha,
                        timeCompleted,
                        assertion)));
    }


    @Test
    public void testThreeStageMergeLast() {
        runThreeStageMergeLastTest(true);
    }

    @Test
    public void testThreeStageMergeLastAndOutputLastOnly() {
        when(_mockAggregateJobPropertiesUtil.isOutputLastTaskOnly(any(), any()))
                .thenReturn(true);
        runThreeStageMergeLastTest(false);
    }


    private void runThreeStageMergeLastTest(boolean verifyAlgo1Request) {
        var job = createThreeStageTestJob();
        var tasksToMerge = Map.of(2, 1);

        String url = "http://localhost:81/qwer";
        setTiesDbUrlForMedia(url, job.getMedia(321));

        when(_mockAggregateJobPropertiesUtil.getTasksToMerge(any(), any()))
                .thenReturn(tasksToMerge);

        for (int taskIndex : tasksToMerge.keySet()) {
            when(_mockAggregateJobPropertiesUtil.getValue(eq(MpfConstants.OUTPUT_MERGE_WITH_PREVIOUS_TASK_PROPERTY),
                    argThat(jp -> jp.getJob().equals(job) && jp.getTaskIndex() == taskIndex)))
                    .thenReturn("TRUE");
        }

        var trackCounter = new TrackCounter();
        trackCounter.set(321, 0, 0, "TYPE1", 451);
        trackCounter.set(321, 1, 0, "TYPE2", 452);
        trackCounter.set(321, 2, 0, "TYPE3", 453);

        var timeCompleted = Instant.ofEpochSecond(1622724824);
        var outputObjectLocation = URI.create("http://localhost:321/asdf");
        var outputSha = "ed2e2a154b4bf6802c3f418a64488b7bf3f734fa9ebfd568cf302ae4e8f4c3bb";

        _tiesDbService.storeAssertions(
                job,
                BatchJobStatusType.COMPLETE,
                timeCompleted,
                outputObjectLocation,
                outputSha,
                trackCounter);

        if (verifyAlgo1Request) {
            verify(_mockInProgressJobs).addTiesDbInfo(
                    eq(job.getId()),
                    eq(job.getMedia().iterator().next().getId()),
                    argThat(assertion -> assertionMatcher(
                            _mockPropertiesUtil.getExportedJobId(job.getId()),
                            BatchJobStatusType.COMPLETE,
                            url,
                            "ALGO1",
                            "TYPE1",
                            451,
                            outputObjectLocation,
                            outputSha,
                            timeCompleted,
                            assertion)));
        }

        verify(_mockInProgressJobs).addTiesDbInfo(
                eq(job.getId()),
                eq(job.getMedia().iterator().next().getId()),
                argThat(assertion -> assertionMatcher(
                        _mockPropertiesUtil.getExportedJobId(job.getId()),
                        BatchJobStatusType.COMPLETE,
                        url,
                        "ALGO2",
                        "TYPE2",
                        453,
                        outputObjectLocation,
                        outputSha,
                        timeCompleted,
                        assertion)));
    }


    @Test
    public void testMergingMiddleTask() {
        runMergeMiddleTest(true);
    }


    @Test
    public void testMergingMiddleTaskWithOutputLastTaskOnly() {
        when(_mockAggregateJobPropertiesUtil.isOutputLastTaskOnly(any(), any()))
                .thenReturn(true);
        runMergeMiddleTest(false);
    }


    private void runMergeMiddleTest(boolean verifyAlgo1Request) {
        var job = createThreeStageTestJob();
        var tasksToMerge = Map.of(1, 0);

        String url = "http://localhost:81/qwer";
        setTiesDbUrlForMedia(url, job.getMedia(321));

        when(_mockAggregateJobPropertiesUtil.getTasksToMerge(any(), any()))
                .thenReturn(tasksToMerge);

        for (int taskIndex : tasksToMerge.keySet()) {
            when(_mockAggregateJobPropertiesUtil.getValue(eq(MpfConstants.OUTPUT_MERGE_WITH_PREVIOUS_TASK_PROPERTY),
                    argThat(jp -> jp.getJob().equals(job) && jp.getTaskIndex() == taskIndex)))
                    .thenReturn("TRUE");
        }

        var trackCounter = new TrackCounter();
        trackCounter.set(321, 0, 0, "TYPE1", 451);
        trackCounter.set(321, 1, 0, "TYPE2", 452);
        trackCounter.set(321, 2, 0, "TYPE3", 453);

        var timeCompleted = Instant.ofEpochSecond(1622724824);
        var outputObjectLocation = URI.create("http://localhost:321/asdf");
        var outputSha = "ed2e2a154b4bf6802c3f418a64488b7bf3f734fa9ebfd568cf302ae4e8f4c3bb";

        _tiesDbService.storeAssertions(
                job,
                BatchJobStatusType.COMPLETE,
                timeCompleted,
                outputObjectLocation,
                outputSha,
                trackCounter);


        if (verifyAlgo1Request) {
            verify(_mockInProgressJobs).addTiesDbInfo(
                    eq(job.getId()),
                    eq(job.getMedia().iterator().next().getId()),
                    argThat(assertion -> assertionMatcher(
                            _mockPropertiesUtil.getExportedJobId(job.getId()),
                            BatchJobStatusType.COMPLETE,
                            url,
                            "ALGO1",
                            "TYPE1",
                            452,
                            outputObjectLocation,
                            outputSha,
                            timeCompleted,
                            assertion)));
        }

        verify(_mockInProgressJobs).addTiesDbInfo(
                eq(job.getId()),
                eq(job.getMedia().iterator().next().getId()),
                argThat(assertion -> assertionMatcher(
                        _mockPropertiesUtil.getExportedJobId(job.getId()),
                        BatchJobStatusType.COMPLETE,
                        url,
                        "ALGO3",
                        "TYPE3",
                        453,
                        outputObjectLocation,
                        outputSha,
                        timeCompleted,
                        assertion)));
    }


    @Test
    public void testMergingLastTwoTasks() {
        runMergeLastTwoTest();
    }

    @Test
    public void testMergingLastTwoTasksWithOutputLastTaskOnly() {
        when(_mockAggregateJobPropertiesUtil.isOutputLastTaskOnly(any(), any()))
                .thenReturn(true);
        runMergeLastTwoTest();
    }

    private void runMergeLastTwoTest() {
        var job = createThreeStageTestJob();

        String url = "http://localhost:81/qwer";
        setTiesDbUrlForMedia(url, job.getMedia(321));

        var tasksToMerge = Map.of(1, 0,  2, 1);
        when(_mockAggregateJobPropertiesUtil.getTasksToMerge(any(), any()))
                .thenReturn(tasksToMerge);

        for (int taskIndex : tasksToMerge.keySet()) {
            when(_mockAggregateJobPropertiesUtil.getValue(eq(MpfConstants.OUTPUT_MERGE_WITH_PREVIOUS_TASK_PROPERTY),
                    argThat(jp -> jp.getJob().equals(job) && jp.getTaskIndex() == taskIndex)))
                    .thenReturn("TRUE");
        }

        var trackCounter = new TrackCounter();
        trackCounter.set(321, 0, 0, "TYPE1", 451);
        trackCounter.set(321, 1, 0, "TYPE2", 452);
        trackCounter.set(321, 2, 0, "TYPE3", 453);

        var timeCompleted = Instant.ofEpochSecond(1622724824);
        var outputObjectLocation = URI.create("http://localhost:321/asdf");
        var outputSha = "ed2e2a154b4bf6802c3f418a64488b7bf3f734fa9ebfd568cf302ae4e8f4c3bb";

        _tiesDbService.storeAssertions(
                job,
                BatchJobStatusType.COMPLETE,
                timeCompleted,
                outputObjectLocation,
                outputSha,
                trackCounter);

        verify(_mockInProgressJobs).addTiesDbInfo(
                eq(job.getId()),
                eq(job.getMedia().iterator().next().getId()),
                argThat(assertion -> assertionMatcher(
                        _mockPropertiesUtil.getExportedJobId(job.getId()),
                        BatchJobStatusType.COMPLETE,
                        url,
                        "ALGO1",
                        "TYPE1",
                        453,
                        outputObjectLocation,
                        outputSha,
                        timeCompleted,
                        assertion)));
    }


    private static BatchJob createTwoStageTestJob() {
        var media = new MediaImpl(321, "file:///media1", null, null, Map.of(), Map.of(),
                                  List.of(), List.of(), null);
        media.setSha256("MEDIA1_SHA");

        var algo1 = new Algorithm("ALGO1", null, null, null, null, true, false);
        var algo2 = new Algorithm("ALGO2", null, null, null, null, true, false);

        var action1 = new Action("ACTION1", null, algo1.getName(), List.of());
        var action2 = new Action("ACTION2", null, algo2.getName(), List.of());

        var task1 = new Task("TASK1", null, List.of(action1.getName()));
        var task2 = new Task("TASK2", null, List.of(action2.getName()));

        var pipeline = new Pipeline("PIPELINE", null, List.of(task1.getName(), task2.getName()));
        var pipelineElements = new JobPipelineElements(
                pipeline, List.of(task1, task2), List.of(action1, action2), List.of(algo1, algo2));

        return new BatchJobImpl(
                123, null, null, pipelineElements, 4,
                null, null, List.of(media),
                Map.of(), Map.of());
    }


    private static BatchJob createThreeStageTestJob() {
        var media = new MediaImpl(321, "file:///media1", null, null, Map.of(), Map.of(),
                                  List.of(), List.of(), null);
        media.setSha256("MEDIA1_SHA");

        var algo1 = new Algorithm("ALGO1", null, null, null, null, true, false);
        var algo2 = new Algorithm("ALGO2", null, null, null, null, true, false);
        var algo3 = new Algorithm("ALGO3", null, null, null, null, true, false);

        var action1 = new Action("ACTION1", null, algo1.getName(), List.of());
        var action2 = new Action("ACTION2", null, algo2.getName(), List.of());
        var action3 = new Action("ACTION3", null, algo3.getName(), List.of());

        var task1 = new Task("TASK1", null, List.of(action1.getName()));
        var task2 = new Task("TASK2", null, List.of(action2.getName()));
        var task3 = new Task("TASK3", null, List.of(action3.getName()));

        var pipeline = new Pipeline("PIPELINE", null,
                                    List.of(task1.getName(), task2.getName(), task3.getName()));
        var pipelineElements = new JobPipelineElements(
                pipeline,
                List.of(task1, task2, task3),
                List.of(action1, action2, action3),
                List.of(algo1, algo2, algo3));

        return new BatchJobImpl(
                123, null, null, pipelineElements, 4,
                null, null, List.of(media),
                Map.of(), Map.of());
    }


    private static boolean assertionMatcher(
            String jobId,
            BatchJobStatusType jobStatus,
            String tiesDbBaseUrl,
            String algorithm,
            String trackType,
            int trackCount,
            URI outputUri,
            String outputObjectSha,
            Instant timeCompleted,
            TiesDbInfo actualTiesDbInfo) {
        // Assertions that apply to all possible arguments
        var actualAssertion = actualTiesDbInfo.assertion();
        assertFalse(actualAssertion.assertionId().isBlank());
        assertEquals("UNCLASSIFIED", actualAssertion.securityTag());
        assertEquals("OpenMPF", actualAssertion.system());

        var dataObject = actualAssertion.dataObject();

        LOG.info("assertionMatcher: Comparing expected [{}, {}, {}] to actual [{}, {}, {}].",
                 algorithm, trackType, trackCount,
                 dataObject.algorithm(), dataObject.outputType(),
                 dataObject.trackCount());


        assertEquals("1.5", dataObject.systemVersion());
        assertFalse(dataObject.systemHostname().isBlank());
        assertEquals("PIPELINE", dataObject.pipeline());

        // Assertions specific to given arguments
        if (!tiesDbBaseUrl.equals(actualTiesDbInfo.tiesDbUrl())) {
            return false;
        }

        var expectedInfoType = "OpenMPF " + trackType;
        if (!expectedInfoType.equals(actualAssertion.informationType())) {
            return false;
        }
        if (!algorithm.equals(dataObject.algorithm())) {
            return false;
        }
        if (!trackType.equals(dataObject.outputType())) {
            return false;
        }
        if (!jobId.equals(dataObject.jobId())) {
            return false;
        }
        if (!outputUri.toString().equals(dataObject.outputUri())) {
            return false;
        }
        if (!outputObjectSha.equals(dataObject.sha256OutputHash())) {
            return false;
        }
        if (!timeCompleted.equals(dataObject.processDate())) {
            return false;
        }
        if (jobStatus != dataObject.jobStatus()) {
            return false;
        }
        if (trackCount != dataObject.trackCount()) {
            return false;
        }

        return true;
    }

    //////////////////////////////////////////////////////////////////////
    // Test derivative media
    //////////////////////////////////////////////////////////////////////

    private static class AssertionEntry {
        private final String _algoName;
        public String getAlgoName() { return _algoName; }

        private final String _detectionType;
        public String getDetectionType() { return _detectionType; }

        private final int _count;
        public int getCount() { return _count; }

        private final String _tiesDbUrl;
        public String getTiesDbUrl() { return _tiesDbUrl; }

        public AssertionEntry(String algoName, String detectionType, int count, String tiesDbUrl) {
            _algoName = algoName;
            _detectionType = detectionType;
            _count = count;
            _tiesDbUrl = tiesDbUrl;
        }
    }

    @Test
    public void testNoMergingSourceAndDerivativeMediaDiffTasks() {
        var trackCounter = new TrackCounter();
        trackCounter.set(700, 0, 0, "MEDIA", 2); // parent
        trackCounter.set(700, 1, 0, "SOURCE_TYPE", 1); // parent
        trackCounter.set(701, 2, 0, "DERIVATIVE_TYPE", 2); // child1
        trackCounter.set(702, 2, 0, "DERIVATIVE_TYPE", 3); // child2

        var url = "http://localhost:81/qwer";
        var assertionEntries = new HashSet<AssertionEntry>();
        assertionEntries.add(new AssertionEntry("EXTRACT_ALGO", "MEDIA", 2, url));
        assertionEntries.add(new AssertionEntry("PARENT_ALGO", "SOURCE_TYPE", 1, url));
        assertionEntries.add(new AssertionEntry("CHILD_ALGO", "DERIVATIVE_TYPE", 5, url)); // sum of child tracks

        runSourceAndDerivativeMediaDiffTasks(true, trackCounter, assertionEntries);
    }

    @Test
    public void testNoMergingSourceAndDerivativeMediaNoSourceTracks() {
        var trackCounter = new TrackCounter();
        trackCounter.set(700, 0, 0, "MEDIA", 2); // parent
        trackCounter.set(700, 1, 0, "SOURCE_TYPE", 0); // parent
        trackCounter.set(701, 2, 0, "DERIVATIVE_TYPE", 2); // child1
        trackCounter.set(702, 2, 0, "DERIVATIVE_TYPE", 3); // child2

        var url = "http://localhost:81/qwer";
        var assertionEntries = new HashSet<AssertionEntry>();
        assertionEntries.add(new AssertionEntry("EXTRACT_ALGO", "MEDIA", 2, url));
        assertionEntries.add(new AssertionEntry("PARENT_ALGO", "NO TRACKS", 0, url));
        assertionEntries.add(new AssertionEntry("CHILD_ALGO", "DERIVATIVE_TYPE", 5, url));

        runSourceAndDerivativeMediaDiffTasks(true, trackCounter, assertionEntries);
    }

    @Test
    public void testNoMergingNoDerivatives() {
        var trackCounter = new TrackCounter();
        trackCounter.set(700, 0, 0, "MEDIA", 0); // parent
        trackCounter.set(700, 1, 0, "SOURCE_TYPE", 3); // parent

        var url = "http://localhost:81/qwer";
        var assertionEntries = new HashSet<AssertionEntry>();
        assertionEntries.add(new AssertionEntry("EXTRACT_ALGO", "NO TRACKS", 0, url));
        assertionEntries.add(new AssertionEntry("PARENT_ALGO", "SOURCE_TYPE", 3, url));
        assertionEntries.add(new AssertionEntry("CHILD_ALGO", "NO TRACKS", 0, url));

        runSourceAndDerivativeMediaDiffTasks(false, trackCounter, assertionEntries);
    }

    private void runSourceAndDerivativeMediaDiffTasks(boolean createChildren, TrackCounter trackCounter,
                                                      Set<AssertionEntry> assertionEntries) {
        var job = createDerivativeMediaThreeStageTestJobDiffTasks(createChildren);

        String url = "http://localhost:81/qwer";
        setTiesDbUrlForMedia(url, job.getMedia(700)); // parent

        var timeCompleted = Instant.ofEpochSecond(1622724824);
        var outputObjectLocation = URI.create("http://localhost:321/asdf");
        var outputSha = "ed2e2a154b4bf6802c3f418a64488b7bf3f734fa9ebfd568cf302ae4e8f4c3bb";

        _tiesDbService.storeAssertions(
                job,
                BatchJobStatusType.COMPLETE_WITH_WARNINGS,
                timeCompleted,
                outputObjectLocation,
                outputSha,
                trackCounter);

        for (var assertionEntry : assertionEntries) {
            verify(_mockInProgressJobs).addTiesDbInfo(
                    eq(job.getId()),
                    eq(700L),
                    argThat(assertion -> assertionMatcher(
                            _mockPropertiesUtil.getExportedJobId(job.getId()),
                            BatchJobStatusType.COMPLETE_WITH_WARNINGS,
                            url,
                            assertionEntry.getAlgoName(),
                            assertionEntry.getDetectionType(),
                            assertionEntry.getCount(),
                            outputObjectLocation,
                            outputSha,
                            timeCompleted,
                            assertion)));
        }
    }


    private static BatchJob createDerivativeMediaThreeStageTestJobDiffTasks(boolean createChildren) {
        var parentMedia = new MediaImpl(700, "file:///parent", null, null, Map.of(), Map.of(), List.of(), List.of(),
                null);
        parentMedia.setSha256("PARENT_SHA");

        var algo1 = new Algorithm("EXTRACT_ALGO", null, null, null, null, true, false);
        var algo2 = new Algorithm("PARENT_ALGO", null, null, null, null, true, false);
        var algo3 = new Algorithm("CHILD_ALGO", null, null, null, null, true, false);

        var action1 = new Action("EXTRACT_ACTION", null, algo1.getName(), List.of());
        var action2 = new Action("PARENT_ACTION", null, algo2.getName(),
                List.of(new ActionProperty("SOURCE_MEDIA_ONLY", "TRUE")));
        var action3 = new Action("CHILD_ACTION", null, algo3.getName(),
                List.of(new ActionProperty("DERIVATIVE_MEDIA_ONLY", "TRUE")));

        var task1 = new Task("TASK1", null, List.of(action1.getName()));
        var task2 = new Task("TASK2", null, List.of(action2.getName()));
        var task3 = new Task("TASK3", null, List.of(action3.getName()));

        var pipeline = new Pipeline("PIPELINE", null,
                List.of(task1.getName(), task2.getName(), task3.getName()));
        var pipelineElements = new JobPipelineElements(
                pipeline,
                List.of(task1, task2, task3),
                List.of(action1, action2, action3),
                List.of(algo1, algo2, algo3));

        BatchJobImpl job = new BatchJobImpl(
                123, null, null, pipelineElements, 4,
                null, null, List.of(parentMedia),
                Map.of(), Map.of());

        if (createChildren) {
            var childMedia1 = new MediaImpl(701, 700, 0, "file:///child1", null, null, Map.of(), Map.of(), null,
                    Map.of(MpfConstants.IS_DERIVATIVE_MEDIA, "TRUE"), List.of(), List.of(), List.of());
            childMedia1.setSha256("CHILD1_SHA");

            var childMedia2 = new MediaImpl(702, 700, 0, "file:///child2", null, null, Map.of(), Map.of(), null,
                    Map.of(MpfConstants.IS_DERIVATIVE_MEDIA, "TRUE"), List.of(), List.of(), List.of());
            childMedia2.setSha256("CHILD2_SHA");

            job.addDerivativeMedia(childMedia1);
            job.addDerivativeMedia(childMedia2);
        }

        return job;
    }


    @Test
    public void testNoMergingSourceAndDerivativeMediaDiffTasksSharedAlgoNoChildUri() {
        var job = createDerivativeMediaThreeStageTestJobDiffTasksSharedAlgo();

        String url = "http://localhost:81/qwer";
        when(_mockAggregateJobPropertiesUtil.getValue(
                eq(MpfConstants.TIES_DB_URL),
                eq(job),
                any(Media.class),
                argThat(a -> a.getName().equals("EXTRACT_ACTION")
                            || a.getName().equals("PARENT_ACTION"))
        )).thenReturn(url);

        var trackCounter = new TrackCounter();
        trackCounter.set(700, 0, 0, "MEDIA", 2); // parent
        trackCounter.set(700, 1, 0, "SHARED_TYPE", 5); // parent
        trackCounter.set(701, 2, 0, "SHARED_TYPE", 2); // child1
        trackCounter.set(702, 2, 0, "SHARED_TYPE", 3); // child2

        var timeCompleted = Instant.ofEpochSecond(1622724824);
        var outputObjectLocation = URI.create("http://localhost:321/asdf");
        var outputSha = "ed2e2a154b4bf6802c3f418a64488b7bf3f734fa9ebfd568cf302ae4e8f4c3bb";

        _tiesDbService.storeAssertions(
                job,
                BatchJobStatusType.COMPLETE_WITH_WARNINGS,
                timeCompleted,
                outputObjectLocation,
                outputSha,
                trackCounter);

        verify(_mockInProgressJobs).addTiesDbInfo(
                eq(job.getId()),
                eq(700L), // parent
                argThat(assertion -> assertionMatcher(
                        _mockPropertiesUtil.getExportedJobId(job.getId()),
                        BatchJobStatusType.COMPLETE_WITH_WARNINGS,
                        url,
                        "EXTRACT_ALGO",
                        "MEDIA",
                        2,
                        outputObjectLocation,
                        outputSha,
                        timeCompleted,
                        assertion)));

        verify(_mockInProgressJobs).addTiesDbInfo(
                eq(job.getId()),
                eq(700L), // parent
                argThat(assertion -> assertionMatcher(
                        _mockPropertiesUtil.getExportedJobId(job.getId()),
                        BatchJobStatusType.COMPLETE_WITH_WARNINGS,
                        url,
                        "SHARED_ALGO",
                        "SHARED_TYPE",
                        5, // don't include children
                        outputObjectLocation,
                        outputSha,
                        timeCompleted,
                        assertion)));
    }


    @Test
    public void testNoMergingSourceAndDerivativeMediaDiffTasksSharedAlgoNoParentUri() {
        var job = createDerivativeMediaThreeStageTestJobDiffTasksSharedAlgo();

        String url = "http://localhost:81/qwer";
        when(_mockAggregateJobPropertiesUtil.getValue(
                eq(MpfConstants.TIES_DB_URL),
                eq(job),
                any(Media.class),
                argThat(a -> a.getName().equals("PARENT_ACTION")
                        || a.getName().equals("CHILD_ACTION"))
        )).thenReturn(url);

        var trackCounter = new TrackCounter();
        trackCounter.set(700, 0, 0, "MEDIA", 2); // parent
        trackCounter.set(700, 1, 0, "SHARED_TYPE", 5); // parent
        trackCounter.set(701, 2, 0, "SHARED_TYPE", 2); // child1
        trackCounter.set(702, 2, 0, "SHARED_TYPE", 3); // child2

        var timeCompleted = Instant.ofEpochSecond(1622724824);
        var outputObjectLocation = URI.create("http://localhost:321/asdf");
        var outputSha = "ed2e2a154b4bf6802c3f418a64488b7bf3f734fa9ebfd568cf302ae4e8f4c3bb";

        _tiesDbService.storeAssertions(
                job,
                BatchJobStatusType.COMPLETE_WITH_WARNINGS,
                timeCompleted,
                outputObjectLocation,
                outputSha,
                trackCounter);

        verify(_mockInProgressJobs).addTiesDbInfo(
                eq(job.getId()),
                eq(700L),
                argThat(assertion -> assertionMatcher(
                        _mockPropertiesUtil.getExportedJobId(job.getId()),
                        BatchJobStatusType.COMPLETE_WITH_WARNINGS,
                        url,
                        "SHARED_ALGO",
                        "SHARED_TYPE",
                        10, // sum of parent and child tracks
                        outputObjectLocation,
                        outputSha,
                        timeCompleted,
                        assertion)));
    }


    private static BatchJob createDerivativeMediaThreeStageTestJobDiffTasksSharedAlgo() {
        var parentMedia = new MediaImpl(700, "file:///parent", null, null, Map.of(), Map.of(), List.of(), List.of(),
                null);
        parentMedia.setSha256("PARENT_SHA");

        var childMedia1 = new MediaImpl(701, 700, 0, "file:///child1", null, null, Map.of(), Map.of(), null,
                Map.of(MpfConstants.IS_DERIVATIVE_MEDIA, "TRUE"), List.of(), List.of(), List.of());
        childMedia1.setSha256("CHILD1_SHA");

        var childMedia2 = new MediaImpl(702, 700, 0, "file:///child2", null, null, Map.of(), Map.of(), null,
                Map.of(MpfConstants.IS_DERIVATIVE_MEDIA, "TRUE"), List.of(), List.of(), List.of());
        childMedia2.setSha256("CHILD2_SHA");

        var algo1 = new Algorithm("EXTRACT_ALGO", null, null, null, null, true, false);
        var algo2 = new Algorithm("SHARED_ALGO", null, null, null, null, true, false);

        var action1 = new Action("EXTRACT_ACTION", null, algo1.getName(), List.of());
        var action2 = new Action("PARENT_ACTION", null, algo2.getName(),
                List.of(new ActionProperty("SOURCE_MEDIA_ONLY", "TRUE")));
        var action3 = new Action("CHILD_ACTION", null, algo2.getName(),
                List.of(new ActionProperty("DERIVATIVE_MEDIA_ONLY", "TRUE")));

        var task1 = new Task("TASK1", null, List.of(action1.getName()));
        var task2 = new Task("TASK2", null, List.of(action2.getName()));
        var task3 = new Task("TASK3", null, List.of(action3.getName()));

        var pipeline = new Pipeline("PIPELINE", null,
                List.of(task1.getName(), task2.getName(), task3.getName()));
        var pipelineElements = new JobPipelineElements(
                pipeline,
                List.of(task1, task2, task3),
                List.of(action1, action2, action3),
                List.of(algo1, algo2));

        BatchJobImpl job = new BatchJobImpl(
                123, null, null, pipelineElements, 4,
                null, null, List.of(parentMedia),
                Map.of(), Map.of());

        job.addDerivativeMedia(childMedia1);
        job.addDerivativeMedia(childMedia2);

        return job;
    }


    @Test
    public void testNoMergingSourceAndDerivativeMediaSharedTask() {
        var job = createDerivativeMediaTwoStageTestJobSharedTask();

        String url = "http://localhost:81/qwer";
        setTiesDbUrlForMedia(url, job.getMedia(700)); // parent

        var trackCounter = new TrackCounter();
        trackCounter.set(700, 0, 0, "MEDIA", 2); // parent
        trackCounter.set(700, 1, 0, "SHARED_TYPE", 5); // parent
        trackCounter.set(701, 1, 0, "SHARED_TYPE", 2); // child1
        trackCounter.set(702, 1, 0, "SHARED_TYPE", 3); // child2

        var timeCompleted = Instant.ofEpochSecond(1622724824);
        var outputObjectLocation = URI.create("http://localhost:321/asdf");
        var outputSha = "ed2e2a154b4bf6802c3f418a64488b7bf3f734fa9ebfd568cf302ae4e8f4c3bb";

        _tiesDbService.storeAssertions(
                job,
                BatchJobStatusType.COMPLETE_WITH_WARNINGS,
                timeCompleted,
                outputObjectLocation,
                outputSha,
                trackCounter);

        verify(_mockInProgressJobs).addTiesDbInfo(
                eq(job.getId()),
                eq(700L), // parent
                argThat(assertion -> assertionMatcher(
                        _mockPropertiesUtil.getExportedJobId(job.getId()),
                        BatchJobStatusType.COMPLETE_WITH_WARNINGS,
                        url,
                        "EXTRACT_ALGO",
                        "MEDIA",
                        2,
                        outputObjectLocation,
                        outputSha,
                        timeCompleted,
                        assertion)));

        verify(_mockInProgressJobs).addTiesDbInfo(
                eq(job.getId()),
                eq(700L), // parent
                argThat(assertion -> assertionMatcher(
                        _mockPropertiesUtil.getExportedJobId(job.getId()),
                        BatchJobStatusType.COMPLETE_WITH_WARNINGS,
                        url,
                        "SHARED_ALGO",
                        "SHARED_TYPE",
                        10, // combined parent and children
                        outputObjectLocation,
                        outputSha,
                        timeCompleted,
                        assertion)));
    }


    private static BatchJob createDerivativeMediaTwoStageTestJobSharedTask() {
        var parentMedia = new MediaImpl(700, "file:///parent", null, null, Map.of(), Map.of(), List.of(), List.of(),
                null);
        parentMedia.setSha256("PARENT_SHA");

        var childMedia1 = new MediaImpl(701, 700, 0, "file:///child1", null, null, Map.of(), Map.of(), null,
                Map.of(MpfConstants.IS_DERIVATIVE_MEDIA, "TRUE"), List.of(), List.of(), List.of());
        childMedia1.setSha256("CHILD1_SHA");

        var childMedia2 = new MediaImpl(702, 700, 0, "file:///child2", null, null, Map.of(), Map.of(), null,
                Map.of(MpfConstants.IS_DERIVATIVE_MEDIA, "TRUE"), List.of(), List.of(), List.of());
        childMedia2.setSha256("CHILD2_SHA");

        var algo1 = new Algorithm("EXTRACT_ALGO", null, null, null, null, true, false);
        var algo2 = new Algorithm("SHARED_ALGO", null, null, null, null, true, false);

        var action1 = new Action("EXTRACT_ACTION", null, algo1.getName(), List.of());
        var action2 = new Action("SHARED_ACTION", null, algo2.getName(), List.of());

        var task1 = new Task("TASK1", null, List.of(action1.getName()));
        var task2 = new Task("TASK2", null, List.of(action2.getName()));

        var pipeline = new Pipeline("PIPELINE", null,
                List.of(task1.getName(), task2.getName()));
        var pipelineElements = new JobPipelineElements(
                pipeline,
                List.of(task1, task2),
                List.of(action1, action2),
                List.of(algo1, algo2));

        BatchJobImpl job = new BatchJobImpl(
                123, null, null, pipelineElements, 4,
                null, null, List.of(parentMedia),
                Map.of(), Map.of());

        job.addDerivativeMedia(childMedia1);
        job.addDerivativeMedia(childMedia2);

        return job;
    }

    //////////////////////////////////////////////////////////////////////
    // Test derivative media flows with multiple source-only and
    // derivative-only actions
    //////////////////////////////////////////////////////////////////////

    @Test
    public void testNoMergingSourceAndDerivativeMediaFlows() {
        var trackCounter = new TrackCounter();
        trackCounter.set(700, 0, 0, "MEDIA", 2); // parent
        trackCounter.set(700, 1, 0, "SOURCE_TYPE1", 2); // parent
        trackCounter.set(700, 2, 0, "SOURCE_TYPE2", 3); // parent
        trackCounter.set(701, 3, 0, "DERIVATIVE_TYPE1", 7); // child1
        trackCounter.set(702, 3, 0, "DERIVATIVE_TYPE1", 5); // child2
        trackCounter.set(701, 4, 0, "DERIVATIVE_TYPE2", 8); // child1
        trackCounter.set(702, 4, 0, "DERIVATIVE_TYPE2", 2); // child2
        trackCounter.set(700, 5, 0, "SHARED_TYPE", 5); // parent
        trackCounter.set(701, 5, 0, "SHARED_TYPE", 6); // child1
        trackCounter.set(702, 5, 0, "SHARED_TYPE", 3); // child2

        var url = "http://localhost:81/qwer";
        var assertionEntries = new HashSet<AssertionEntry>();
        assertionEntries.add(new AssertionEntry("EXTRACT_ALGO", "MEDIA", 2, url));
        assertionEntries.add(new AssertionEntry("PARENT_ALGO1", "SOURCE_TYPE1", 2, url));
        assertionEntries.add(new AssertionEntry("PARENT_ALGO2", "SOURCE_TYPE2", 3, url));
        assertionEntries.add(new AssertionEntry("CHILD_ALGO1", "DERIVATIVE_TYPE1", 12, url));
        assertionEntries.add(new AssertionEntry("CHILD_ALGO2", "DERIVATIVE_TYPE2", 10, url));
        assertionEntries.add(new AssertionEntry("SHARED_ALGO", "SHARED_TYPE", 14, url));

        BatchJob job = setupMergingLastSourceAndDerivativeMediaTasksJob(Map.of(), Map.of(), true);
        setTiesDbUrlForMedia(url, job.getMedia(700)); // parent
        runJob(trackCounter, assertionEntries, job);
    }

    @Test
    public void testMergingSourceAndDerivativeMediaFlowsDiffTypes() {
        var trackCounter = new TrackCounter();
        trackCounter.set(700, 0, 0, "MEDIA", 2); // parent
        trackCounter.set(700, 1, 0, "SOURCE_TYPE1", 2); // parent
        trackCounter.set(700, 2, 0, "SOURCE_TYPE2", 3); // parent
        trackCounter.set(701, 3, 0, "DERIVATIVE_TYPE1", 7); // child1
        trackCounter.set(702, 3, 0, "DERIVATIVE_TYPE1", 5); // child2
        trackCounter.set(701, 4, 0, "DERIVATIVE_TYPE2", 8); // child1
        trackCounter.set(702, 4, 0, "DERIVATIVE_TYPE2", 2); // child2
        trackCounter.set(700, 5, 0, "SHARED_TYPE", 5); // parent
        trackCounter.set(701, 5, 0, "SHARED_TYPE", 6); // child1
        trackCounter.set(702, 5, 0, "SHARED_TYPE", 3); // child2

        var url = "http://localhost:81/qwer";
        var assertionEntries = new HashSet<AssertionEntry>();
        assertionEntries.add(new AssertionEntry("EXTRACT_ALGO", "MEDIA", 2, url));
        assertionEntries.add(new AssertionEntry("PARENT_ALGO1", "SOURCE_TYPE1", 5, url));
        assertionEntries.add(new AssertionEntry("CHILD_ALGO1", "DERIVATIVE_TYPE1", 9, url));

        var parentTasksToMerge = Map.of(5, 2, 2, 1);
        var childTasksToMerge = Map.of(5, 4, 4, 3);

        BatchJob job = setupMergingLastSourceAndDerivativeMediaTasksJob(parentTasksToMerge, childTasksToMerge, true);
        setTiesDbUrlForMedia(url, job.getMedia(700)); // parent
        runJob(trackCounter, assertionEntries, job);
    }

    @Test
    public void testMergingSourceAndNoDerivativeMediaFlowsDiffTypes() {
        var trackCounter = new TrackCounter();
        trackCounter.set(700, 0, 0, "MEDIA", 0); // parent
        trackCounter.set(700, 1, 0, "SOURCE_TYPE1", 2); // parent
        trackCounter.set(700, 2, 0, "SOURCE_TYPE2", 3); // parent
        trackCounter.set(700, 5, 0, "SHARED_TYPE", 5); // parent

        var url = "http://localhost:81/qwer";
        var assertionEntries = new HashSet<AssertionEntry>();
        assertionEntries.add(new AssertionEntry("EXTRACT_ALGO", "NO TRACKS", 0, url));
        assertionEntries.add(new AssertionEntry("PARENT_ALGO1", "SOURCE_TYPE1", 5, url));
        assertionEntries.add(new AssertionEntry("CHILD_ALGO1", "NO TRACKS", 0, url));

        var parentTasksToMerge = Map.of(5, 2,  2, 1);
        var childTasksToMerge = Map.of(5, 4,  4, 3);

        BatchJob job = setupMergingLastSourceAndDerivativeMediaTasksJob(parentTasksToMerge, childTasksToMerge, false);
        setTiesDbUrlForMedia(url, job.getMedia(700)); // parent
        runJob(trackCounter, assertionEntries, job);
    }


    //////////////////////////////////////////////////////////////////////
    // Test setting TiesDb URLs
    //////////////////////////////////////////////////////////////////////

    @Test
    public void testNoMergingSourceAndDerivativeMediaFlowsSharedTiesDbUrl() {
        var trackCounter = new TrackCounter();
        trackCounter.set(700, 0, 0, "MEDIA", 2); // parent
        trackCounter.set(700, 1, 0, "SOURCE_TYPE1", 2); // parent
        trackCounter.set(700, 2, 0, "SOURCE_TYPE2", 3); // parent
        trackCounter.set(701, 3, 0, "DERIVATIVE_TYPE1", 7); // child1
        trackCounter.set(702, 3, 0, "DERIVATIVE_TYPE1", 5); // child2
        trackCounter.set(701, 4, 0, "DERIVATIVE_TYPE2", 8); // child1
        trackCounter.set(702, 4, 0, "DERIVATIVE_TYPE2", 2); // child2
        trackCounter.set(700, 5, 0, "SHARED_TYPE", 5); // parent
        trackCounter.set(701, 5, 0, "SHARED_TYPE", 6); // child1
        trackCounter.set(702, 5, 0, "SHARED_TYPE", 3); // child2

        var extractUrl = "http://localhost:81/extract";
        var sharedUrl = "http://localhost:81/shared";

        var assertionEntries = new HashSet<AssertionEntry>();
        assertionEntries.add(new AssertionEntry("EXTRACT_ALGO", "MEDIA", 2, extractUrl));
        assertionEntries.add(new AssertionEntry("SHARED_ALGO", "SHARED_TYPE", 14, sharedUrl));

        BatchJob job = setupMergingLastSourceAndDerivativeMediaTasksJob(Map.of(), Map.of(), true);

        setTiesDbUrlForMediaAndAction(extractUrl, job.getMedia(700), job.getPipelineElements().getAction(0, 0));
        setTiesDbUrlForMediaAndAction(sharedUrl, job.getMedia(700), job.getPipelineElements().getAction(5, 0));

        runJob(trackCounter, assertionEntries, job);
    }

    @Test
    public void testMergingSourceAndDerivativeMediaFlowsDiffTypesDiffTiesDbUrls() {
        var trackCounter = new TrackCounter();
        trackCounter.set(700, 0, 0, "MEDIA", 2); // parent
        trackCounter.set(700, 1, 0, "SOURCE_TYPE1", 2); // parent
        trackCounter.set(700, 2, 0, "SOURCE_TYPE2", 3); // parent
        trackCounter.set(701, 3, 0, "DERIVATIVE_TYPE1", 7); // child1
        trackCounter.set(702, 3, 0, "DERIVATIVE_TYPE1", 5); // child2
        trackCounter.set(701, 4, 0, "DERIVATIVE_TYPE2", 8); // child1
        trackCounter.set(702, 4, 0, "DERIVATIVE_TYPE2", 2); // child2
        trackCounter.set(700, 5, 0, "SHARED_TYPE", 5); // parent
        trackCounter.set(701, 5, 0, "SHARED_TYPE", 6); // child1
        trackCounter.set(702, 5, 0, "SHARED_TYPE", 3); // child2

        var extractUrl = "http://localhost:81/extract";
        var parentUrl = "http://localhost:81/parent";
        var childUrl = "http://localhost:81/child";

        var assertionEntries = new HashSet<AssertionEntry>();
        assertionEntries.add(new AssertionEntry("EXTRACT_ALGO", "MEDIA", 2, extractUrl));
        assertionEntries.add(new AssertionEntry("PARENT_ALGO1", "SOURCE_TYPE1", 5, parentUrl));
        assertionEntries.add(new AssertionEntry("CHILD_ALGO1", "DERIVATIVE_TYPE1", 9, childUrl));

        var parentTasksToMerge = Map.of(5, 2, 2, 1);
        var childTasksToMerge = Map.of(5, 4, 4, 3);

        BatchJob job = setupMergingLastSourceAndDerivativeMediaTasksJob(parentTasksToMerge, childTasksToMerge, true);

        setTiesDbUrlForMediaAndAction(extractUrl, job.getMedia(700), job.getPipelineElements().getAction(0, 0));
        setTiesDbUrlForMediaAndAction(parentUrl, job.getMedia(700), job.getPipelineElements().getAction(1, 0));
        setTiesDbUrlForMediaAndAction(childUrl, job.getMedia(700), job.getPipelineElements().getAction(3, 0));

        runJob(trackCounter, assertionEntries, job);
    }


    private BatchJob setupMergingLastSourceAndDerivativeMediaTasksJob(Map<Integer, Integer> parentTasksToMerge,
                                                                      Map<Integer, Integer> childTasksToMerge,
                                                                      boolean addChildren) {
        var job = createDerivativeMediaSixStageTestJob(addChildren);

        when(_mockAggregateJobPropertiesUtil.getTasksToMerge(
                argThat(m -> m != null && m.getId() == 700), any())) // parent
                .thenReturn(parentTasksToMerge);

        when(_mockAggregateJobPropertiesUtil.getTasksToMerge(
                argThat(m -> m != null && m.getId() != 700), any())) // children
                .thenReturn(childTasksToMerge);

        Set<Integer> tasksToMerge = new HashSet<>(parentTasksToMerge.keySet());
        tasksToMerge.addAll(childTasksToMerge.keySet());

        for (int taskIndex : tasksToMerge) {
            when(_mockAggregateJobPropertiesUtil.getValue(eq(MpfConstants.OUTPUT_MERGE_WITH_PREVIOUS_TASK_PROPERTY),
                    argThat(jp -> jp.getJob().equals(job) && jp.getTaskIndex() == taskIndex)))
                    .thenReturn("TRUE");
        }

        return job;
    }

    private void runJob(TrackCounter trackCounter, Set<AssertionEntry> assertionEntries, BatchJob job) {
        var timeCompleted = Instant.ofEpochSecond(1622724824);
        var outputObjectLocation = URI.create("http://localhost:321/asdf");
        var outputSha = "ed2e2a154b4bf6802c3f418a64488b7bf3f734fa9ebfd568cf302ae4e8f4c3bb";

        _tiesDbService.storeAssertions(
                job,
                BatchJobStatusType.COMPLETE_WITH_WARNINGS,
                timeCompleted,
                outputObjectLocation,
                outputSha,
                trackCounter);

        for (var assertionEntry : assertionEntries) {
            verify(_mockInProgressJobs).addTiesDbInfo(
                    eq(job.getId()),
                    eq(700L), // parent
                    argThat(assertion -> assertionMatcher(
                            _mockPropertiesUtil.getExportedJobId(job.getId()),
                            BatchJobStatusType.COMPLETE_WITH_WARNINGS,
                            assertionEntry.getTiesDbUrl(),
                            assertionEntry.getAlgoName(),
                            assertionEntry.getDetectionType(),
                            assertionEntry.getCount(),
                            outputObjectLocation,
                            outputSha,
                            timeCompleted,
                            assertion)));
        }
    }


    private static BatchJob createDerivativeMediaSixStageTestJob(boolean addChildren) {
        var parentMedia = new MediaImpl(700, "file:///parent", null, null, Map.of(), Map.of(), List.of(), List.of(),
                null);
        parentMedia.setSha256("PARENT_SHA");

        var algo1 = new Algorithm("EXTRACT_ALGO", null, null, null, null, true, false);
        var algo2 = new Algorithm("PARENT_ALGO1", null, null, null, null, true, false);
        var algo3 = new Algorithm("PARENT_ALGO2", null, null, null, null, true, false);
        var algo4 = new Algorithm("CHILD_ALGO1", null, null, null, null, true, false);
        var algo5 = new Algorithm("CHILD_ALGO2", null, null, null, null, true, false);
        var algo6 = new Algorithm("SHARED_ALGO", null, null, null, null, true, false);

        var sourceOnlyProperty = new ActionProperty("SOURCE_MEDIA_ONLY", "TRUE");
        var derivativeOnlyProperty = new ActionProperty("DERIVATIVE_MEDIA_ONLY", "TRUE");

        var action1 = new Action("EXTRACT_ACTION", null, algo1.getName(), List.of());
        var action2 = new Action("PARENT_ACTION1", null, algo2.getName(), List.of(sourceOnlyProperty));
        var action3 = new Action("PARENT_ACTION2", null, algo3.getName(), List.of(sourceOnlyProperty));
        var action4 = new Action("CHILD_ACTION1", null, algo4.getName(), List.of(derivativeOnlyProperty));
        var action5 = new Action("CHILD_ACTION2", null, algo5.getName(), List.of(derivativeOnlyProperty));
        var action6 = new Action("SHARED_ACTION", null, algo6.getName(), List.of());

        var task1 = new Task("TASK1", null, List.of(action1.getName()));
        var task2 = new Task("TASK2", null, List.of(action2.getName()));
        var task3 = new Task("TASK3", null, List.of(action3.getName()));
        var task4 = new Task("TASK4", null, List.of(action4.getName()));
        var task5 = new Task("TASK5", null, List.of(action5.getName()));
        var task6 = new Task("TASK6", null, List.of(action6.getName()));

        var pipeline = new Pipeline("PIPELINE", null,
                List.of(task1.getName(), task2.getName(), task3.getName(), task4.getName(), task5.getName(),
                        task6.getName()));
        var pipelineElements = new JobPipelineElements(
                pipeline,
                List.of(task1, task2, task3, task4, task5, task6),
                List.of(action1, action2, action3, action4, action5, action6),
                List.of(algo1, algo2, algo3, algo4, algo5, algo6));

        BatchJobImpl job = new BatchJobImpl(
                123, null, null, pipelineElements, 4,
                null, null, List.of(parentMedia),
                Map.of(), Map.of());

        if (addChildren) {
            var childMedia1 = new MediaImpl(701, 700, 0, "file:///child1", null, null, Map.of(), Map.of(), null,
                                            Map.of(MpfConstants.IS_DERIVATIVE_MEDIA, "TRUE"),
                                            List.of(), List.of(), List.of());
            childMedia1.setSha256("CHILD1_SHA");

            var childMedia2 = new MediaImpl(702, 700, 0, "file:///child2", null, null, Map.of(), Map.of(), null,
                                            Map.of(MpfConstants.IS_DERIVATIVE_MEDIA, "TRUE"),
                                            List.of(), List.of(), List.of());
            childMedia2.setSha256("CHILD2_SHA");
            job.addDerivativeMedia(childMedia1);
            job.addDerivativeMedia(childMedia2);
        }

        return job;
    }

    private void setTiesDbUrlForMedia(String url, Media media) {
        when(_mockAggregateJobPropertiesUtil.getValue(
                eq(MpfConstants.TIES_DB_URL), any(BatchJob.class), eq(media), any(Action.class)))
                .thenReturn(url);
    }

    private void setTiesDbUrlForMediaAndAction(String url, Media media, Action action) {
        when(_mockAggregateJobPropertiesUtil.getValue(
                eq(MpfConstants.TIES_DB_URL), any(BatchJob.class), eq(media), eq(action)))
                .thenReturn(url);
    }


    @Test
    public void testPost() throws IOException {
        var job = mock(BatchJob.class);
        when(job.getId())
                .thenReturn(10L);
        var media1 = mock(Media.class);
        when(media1.getSha256())
                .thenReturn("MEDIA_1_SHA");
        var media2 = mock(Media.class);
        when(media2.getSha256())
                .thenReturn("MEDIA_2_SHA");

        when(job.getMedia())
                .thenReturn(List.of(media1, media2));

        var tiesDbInfo1 = createValidTiesDbInfoMotion();
        var tiesDbInfo2 = createValidTiesDbInfoFace();

        when(media1.getTiesDbInfo())
                .thenReturn(List.of(tiesDbInfo1, tiesDbInfo2));

        var tiesDbInfo3 = new TiesDbInfo(
                "http://localhost:81",
                new TiesDbInfo.Assertion(
                        "ASSERTION_ID_3",
                        "FACE",
                        new TiesDbInfo.DataObject(
                                "PIPELINE",
                                "FACECV",
                                "FACE",
                                "hostname-1",
                                "file:///output-object.json",
                                "OUTPUT_OBJECT_SHA",
                                Instant.ofEpochSecond(1622724824),
                                BatchJobStatusType.COMPLETE,
                                "1.5",
                                "hostname",
                                101)));
        when(media2.getTiesDbInfo())
                .thenReturn(List.of(tiesDbInfo3));


        when(_mockCallbackUtils.executeRequest(any(HttpPost.class), eq(3), any()))
                .thenReturn(ThreadUtil.completedFuture(
                        new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK")));

        _tiesDbService.postAssertions(job).join();

        var httpRequestCaptor = ArgumentCaptor.forClass(HttpPost.class);
        verify(_mockCallbackUtils, times(3))
                .executeRequest(httpRequestCaptor.capture(), eq(3), any());

        {
            var expectedUri = URI.create(
                    "http://localhost:81/api/db/supplementals?sha256Hash=MEDIA_1_SHA");
            var httpRequest = httpRequestCaptor.getAllValues()
                    .stream()
                    .filter(r -> r.getURI().equals(expectedUri))
                    .findAny()
                    .orElseThrow();

            var postedAssertion = _objectMapper.readValue(httpRequest.getEntity().getContent(),
                                                          TiesDbInfo.Assertion.class);
            assertEquals(postedAssertion, tiesDbInfo1.assertion());
        }
        {
            var expectedUri = URI.create(
                    "http://localhost:82/api/db/supplementals?sha256Hash=MEDIA_1_SHA");
            var httpRequest = httpRequestCaptor.getAllValues()
                    .stream()
                    .filter(r -> r.getURI().equals(expectedUri))
                    .findAny()
                    .orElseThrow();

            var postedAssertion = _objectMapper.readValue(httpRequest.getEntity().getContent(),
                                                          TiesDbInfo.Assertion.class);
            assertEquals(postedAssertion, tiesDbInfo2.assertion());
        }
        {
            var expectedUri = URI.create(
                    "http://localhost:81/api/db/supplementals?sha256Hash=MEDIA_2_SHA");
            var httpRequest = httpRequestCaptor.getAllValues()
                    .stream()
                    .filter(r -> r.getURI().equals(expectedUri))
                    .findAny()
                    .orElseThrow();

            var postedAssertion = _objectMapper.readValue(httpRequest.getEntity().getContent(),
                                                          TiesDbInfo.Assertion.class);
            assertEquals(postedAssertion, tiesDbInfo3.assertion());
        }
        verify(_mockJobRequestDao)
                .setTiesDbSuccessful(10);
        verifyNoMoreInteractions(_mockJobRequestDao);
    }


    @Test
    public void canHandleInvalidUri() throws IOException {
        var job = mock(BatchJob.class);
        when(job.getId())
                .thenReturn(10L);
        var media1 = mock(Media.class);
        when(job.getMedia())
                .thenReturn(List.of(media1));
        when(media1.getSha256())
                .thenReturn("MEDIA_1_SHA");

        var validTiesDbInfo = createValidTiesDbInfoFace();

        var invalidTiesDbInfo = new TiesDbInfo(
                "BAD URI",
                new TiesDbInfo.Assertion(
                        "ASSERTION_ID_1",
                        "MOTION",
                        new TiesDbInfo.DataObject(
                                "PIPELINE",
                                "MOG",
                                "MOTION",
                                "hostname-1",
                                "file:///output-object.json",
                                "OUTPUT_OBJECT_SHA",
                                Instant.ofEpochSecond(1622724824),
                                BatchJobStatusType.COMPLETE,
                                "1.5",
                                "hostname",
                                20)));

        when(media1.getTiesDbInfo())
                .thenReturn(List.of(validTiesDbInfo, invalidTiesDbInfo));


        when(_mockCallbackUtils.executeRequest(any(HttpPost.class), eq(3), any()))
                .thenReturn(ThreadUtil.completedFuture(
                        new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK")));

        var future = _tiesDbService.postAssertions(job);
        TestUtil.assertThrows(CompletionException.class, future::join);

        var httpRequestCaptor = ArgumentCaptor.forClass(HttpPost.class);
        verify(_mockCallbackUtils, times(1))
                .executeRequest(httpRequestCaptor.capture(), anyInt(), any());

        verify(_mockJobRequestDao)
                .setTiesDbError(eq(10L), nonBlank());
        verifyNoMoreInteractions(_mockJobRequestDao);

        var httpRequest = httpRequestCaptor.getValue();
        var expectedUri = URI.create(
                "http://localhost:82/api/db/supplementals?sha256Hash=MEDIA_1_SHA");
        assertEquals(httpRequest.getURI(), expectedUri);

        var postedAssertion = _objectMapper.readValue(httpRequest.getEntity().getContent(),
                                                      TiesDbInfo.Assertion.class);
        assertEquals(postedAssertion, validTiesDbInfo.assertion());
    }


    @Test
    public void reportsErrorWhenOneRequestFailsAndOtherSucceeds() {
        var job = mock(BatchJob.class);
        when(job.getId())
                .thenReturn(10L);

        var media1 = mock(Media.class);
        when(job.getMedia())
                .thenReturn(List.of(media1));
        when(media1.getSha256())
                .thenReturn("MEDIA_1_SHA");

        when(media1.getTiesDbInfo())
                .thenReturn(List.of(createValidTiesDbInfoMotion(), createValidTiesDbInfoFace()));

        var successUri = URI.create(
                "http://localhost:81/api/db/supplementals?sha256Hash=MEDIA_1_SHA");

        var errorMsg = "test error message";
        when(_mockCallbackUtils.executeRequest(any(), eq(3), any()))
                .thenAnswer(inv -> {
                    if (inv.getArgument(0, HttpPost.class).getURI().equals(successUri)) {
                        return ThreadUtil.completedFuture(
                                new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "ok"));
                    }
                    var errorResponse = createErrorResponse(errorMsg);
                    var retryPred = (Predicate<HttpResponse>) inv.getArgument(2);
                    assertTrue(retryPred.test(errorResponse));
                    return ThreadUtil.completedFuture(errorResponse);
                });


        var future = _tiesDbService.postAssertions(job);
        var exception = TestUtil.assertThrows(CompletionException.class, future::join);
        assertThat(exception.getMessage(), containsString(errorMsg));

        verify(_mockCallbackUtils, times(2))
                .executeRequest(any(), anyInt(), any());

        verify(_mockJobRequestDao)
                .setTiesDbError(eq(10L), nonBlank());
        verifyNoMoreInteractions(_mockJobRequestDao);
    }


    @Test
    public void reportsAllErrorsWhenMultipleFailures() {
        var job = mock(BatchJob.class);
        when(job.getId())
                .thenReturn(10L);

        var media1 = mock(Media.class);
        when(job.getMedia())
                .thenReturn(List.of(media1));
        when(media1.getSha256())
                .thenReturn("MEDIA_1_SHA");

        when(media1.getTiesDbInfo())
                .thenReturn(List.of(createValidTiesDbInfoMotion(), createValidTiesDbInfoFace()));

        var uri1 = URI.create(
                "http://localhost:81/api/db/supplementals?sha256Hash=MEDIA_1_SHA");

        var errorMsg1 = "test error message";
        var errorMsg2 = "other failure message";
        when(_mockCallbackUtils.executeRequest(any(), eq(3), any()))
                .thenAnswer(inv -> {
                    String errorMsg;
                    if (inv.getArgument(0, HttpPost.class).getURI().equals(uri1)) {
                        errorMsg = errorMsg1;
                    }
                    else {
                        errorMsg = errorMsg2;
                    }
                    var errorResponse = createErrorResponse(errorMsg);
                    var retryPred = (Predicate<HttpResponse>) inv.getArgument(2);
                    assertTrue(retryPred.test(errorResponse));
                    return ThreadUtil.completedFuture(errorResponse);
                });


        var future = _tiesDbService.postAssertions(job);
        var exception = TestUtil.assertThrows(CompletionException.class, future::join);
        assertThat(exception.getMessage(), containsString(errorMsg1));
        assertThat(exception.getMessage(), containsString(errorMsg2));

        verify(_mockCallbackUtils, times(2))
                .executeRequest(any(), anyInt(), any());

        verify(_mockJobRequestDao)
                .setTiesDbError(eq(10L), nonBlank());
        verifyNoMoreInteractions(_mockJobRequestDao);
    }


    @Test
    public void doesNotRetryOnMediaMissingFromTiesDb() {
        var job = mock(BatchJob.class);
        when(job.getId())
                .thenReturn(10L);

        var media1 = mock(Media.class);
        when(job.getMedia())
                .thenReturn(List.of(media1));
        when(media1.getSha256())
                .thenReturn("MEDIA_1_SHA");

        when(media1.getTiesDbInfo())
                .thenReturn(List.of(createValidTiesDbInfoMotion(), createValidTiesDbInfoFace()));

        var errorMsg = "<other content> could not identify referenced item <other content>";
        when(_mockCallbackUtils.executeRequest(any(), eq(3), any()))
                .thenAnswer(inv -> {
                    var errorResponse = createErrorResponse(errorMsg);
                    var retryPred = (Predicate<HttpResponse>) inv.getArgument(2);
                    assertFalse(retryPred.test(errorResponse));
                    return ThreadUtil.completedFuture(errorResponse);
                });

        var future = _tiesDbService.postAssertions(job);
        var exception = TestUtil.assertThrows(CompletionException.class, future::join);
        assertThat(exception.getMessage(), containsString(errorMsg));

        verify(_mockCallbackUtils, times(2))
                .executeRequest(any(), anyInt(), any());

        verify(_mockJobRequestDao)
                .setTiesDbError(eq(10L), nonBlank());
        verifyNoMoreInteractions(_mockJobRequestDao);
    }



    private static HttpResponse createErrorResponse(String errorMsg) {
        var response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 400, "BAD REQUEST");
        var entity = new BasicHttpEntity();
        response.setEntity(entity);
        var bytes = errorMsg.getBytes(StandardCharsets.UTF_8);
        entity.setContent(new ByteArrayInputStream(bytes));
        return response;
    }


    private static TiesDbInfo createValidTiesDbInfoMotion() {
        return new TiesDbInfo(
                "http://localhost:81",
                new TiesDbInfo.Assertion(
                        "ASSERTION_ID_1",
                        "MOTION",
                        new TiesDbInfo.DataObject(
                                "PIPELINE",
                                "MOG",
                                "MOTION",
                                "hostname-1",
                                "file:///output-object.json",
                                "OUTPUT_OBJECT_SHA",
                                Instant.ofEpochSecond(1622724824),
                                BatchJobStatusType.COMPLETE,
                                "1.5",
                                "hostname",
                                20)));
    }


    private static TiesDbInfo createValidTiesDbInfoFace() {
        return new TiesDbInfo(
                "http://localhost:82",
                new TiesDbInfo.Assertion(
                        "ASSERTION_ID_2",
                        "FACE",
                        new TiesDbInfo.DataObject(
                                "PIPELINE",
                                "FACECV",
                                "FACE",
                                "hostname-1",
                                "file:///output-object.json",
                                "OUTPUT_OBJECT_SHA",
                                Instant.ofEpochSecond(1622724824),
                                BatchJobStatusType.COMPLETE,
                                "1.5",
                                "hostname",
                                100)));
    }


    @Test
    public void testRepost() {
        initJob(100);
        initJob(101);
        initJob(102);
        initJob(500);
        initJob(501);

        when(_mockInProgressJobs.containsJob(102))
                .thenReturn(true);


        var svcSpy = spy(_tiesDbService);
        doReturn(ThreadUtil.completedFuture(null))
                .when(svcSpy)
                .postAssertions(argThat(j -> j.getId() == 100 || j.getId() == 101));
        doReturn(ThreadUtil.failedFuture(new IllegalStateException("Failure for job 500")))
                .when(svcSpy)
                .postAssertions(argThat(j -> j.getId() == 500));
        doReturn(ThreadUtil.failedFuture(new IllegalStateException("Different failure")))
                .when(svcSpy)
                .postAssertions(argThat(j -> j.getId() == 501));


        var result = svcSpy.repost(List.of(101L, 100L, 404L, 102L, 500L, 501L));
        assertThat(result.success(), is(List.of(100L, 101L)));

        var failures = result.failures();
        assertThat(failures, hasSize(4));

        var missingJob = failures.stream()
                .filter(f -> f.jobId() == 404)
                .findAny()
                .orElseThrow();
        assertThat(missingJob.error(), containsString("Could not find job"));

        var runningJob = failures.stream()
                .filter(f -> f.jobId() == 102)
                .findAny()
                .orElseThrow();
        assertThat(runningJob.error(), is("Job is still running."));

        var postError1 = failures.stream()
                .filter(j -> j.jobId() == 500)
                .findAny()
                .orElseThrow();
        assertThat(postError1.error(), is("Failure for job 500"));

        var postError2 = failures.stream()
                .filter(j -> j.jobId() == 501)
                .findAny()
                .orElseThrow();
        assertThat(postError2.error(), is("Different failure"));
    }

    private void initJob(long jobId) {
        var jobRequest = new JobRequest();
        var job = createBatchJob(jobId);
        jobRequest.setJob(_jsonUtils.serialize(job));

        when(_mockJobRequestDao.findById(jobId))
                .thenReturn(jobRequest);
    }

    private static BatchJob createBatchJob(long jobId) {
        return new BatchJobImpl(
                jobId,
                null,
                null,
                null,
                0,
                null,
                null,
                List.of(),
                Map.of(),
                Map.of());

    }
}
