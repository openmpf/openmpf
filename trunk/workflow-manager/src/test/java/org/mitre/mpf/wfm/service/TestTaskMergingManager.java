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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultHeadersMapFactory;
import org.apache.camel.impl.DefaultMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.ActionType;
import org.mitre.mpf.rest.api.pipelines.Algorithm;
import org.mitre.mpf.rest.api.pipelines.Pipeline;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJobImpl;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.mitre.mpf.wfm.data.entities.persistent.MediaImpl;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mockito.InjectMocks;
import org.mockito.Mock;


public class TestTaskMergingManager extends MockitoTest.Strict {

    @Mock
    private AggregateJobPropertiesUtil _mockAggJobPropUtil;

    @Mock
    private Redis _mockRedis;

    @InjectMocks
    private TaskMergingManager _taskMergingManager;

    private BatchJob _testJob;

    private MediaImpl _testMedia;

    @Before
    public void init() {
        var algo1 = new Algorithm(
                "ALGO1", "", ActionType.DETECTION, OptionalInt.empty(), null, null, true, false);
        var algo2 = new Algorithm(
                "ALGO2", "", ActionType.DETECTION, OptionalInt.empty(), null, null, true, false);
        var algo3 = new Algorithm(
                "ALGO3", "", ActionType.DETECTION, OptionalInt.empty(), null, null, true, false);
        var algo4 = new Algorithm(
                "ALGO4", "", ActionType.DETECTION, OptionalInt.empty(), null, null, true, false);

        var action1 = new Action("ACTION1", "", algo1.getName(), List.of());
        var action2 = new Action("ACTION2", "", algo2.getName(), List.of());
        var action3 = new Action("ACTION3", "", algo3.getName(), List.of());
        var action4 = new Action("ACTION4", "", algo4.getName(), List.of());

        var task1 = new Task("TASK1", "", List.of(action1.getName()));
        var task2 = new Task("TASK2", "", List.of(action2.getName()));
        var task3 = new Task("TASK3", "", List.of(action3.getName()));
        var task4 = new Task("TASK4", "", List.of(action4.getName()));

        var pipeline = new Pipeline(
                "PIPELINE", "",
                List.of(task1.getName(), task2.getName(), task3.getName(), task4.getName()));
        var pipelineElements = new JobPipelineElements(
                pipeline,
                List.of(task1, task2, task3, task4),
                List.of(action1, action2, action3, action4),
                List.of(algo1, algo2, algo3, algo4));

        _testMedia = new MediaImpl(
                321, "file:///fake-uri", UriScheme.FILE, null, Map.of(), Map.of(),
                List.of(), List.of(), null);

        _testJob = new BatchJobImpl(
            123, null, null,
            pipelineElements, 0, null, null,
            List.of(_testMedia), Map.of(), Map.of(), false);
    }

    @After
    public void assertCleanedUp() {
        try {
            var field = TaskMergingManager.class
                    .getDeclaredField("_algoTrackTypes");
            field.setAccessible(true);
            var algoTrackTypes = (Map<?, ?>) field.get(_taskMergingManager);
            // If entries are not removed from the map, it will cause a memory leak.
            // While it is not typical to access to private fields in a test, the danger of a
            // memory leak warrants breaking that rule in this case.
            assertTrue(algoTrackTypes.isEmpty());
        }
        catch (IllegalAccessException | NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }


    @Test
    public void isNoOpOnFirstTask() {
        var requestCtx = _taskMergingManager.getRequestContext(
                _testJob, _testMedia, 0, 0);
        var message = createMessage();
        var updatedMsg = requestCtx.addBreadCrumb(message, null);
        assertSame(message, updatedMsg);
        assertTrue(updatedMsg.getHeaders().isEmpty());

        try (var respCtx = _taskMergingManager.getResponseContext(
                _testJob, _testMedia, 0, 0, "TEST_DET_TYPE1", Map.of())) {
            assertEquals("TEST_DET_TYPE1", respCtx.getDetectionType());
            assertEquals("ALGO1", respCtx.getAlgorithm());
        }
    }

    @Test
    public void isNoOpWhenTaskMergingDisabled() {
        var requestCtx = _taskMergingManager.getRequestContext(
                _testJob, _testMedia, 1, 0);
        var message = createMessage();
        var updatedMsg = requestCtx.addBreadCrumb(message, null);
        assertSame(message, updatedMsg);
        assertTrue(updatedMsg.getHeaders().isEmpty());

        try (var respCtx = _taskMergingManager.getResponseContext(
                _testJob, _testMedia, 1, 0, "TEST_DET_TYPE2", Map.of())) {
            assertEquals("TEST_DET_TYPE2", respCtx.getDetectionType());
            assertEquals("ALGO2", respCtx.getAlgorithm());
        }
    }


    @Test
    public void addsBreadCrumbWhenTaskMergingEnabled() {
        when(_mockAggJobPropUtil.getValue(
                eq(MpfConstants.OUTPUT_MERGE_WITH_PREVIOUS_TASK_PROPERTY),
                eq(_testJob), eq(_testMedia),
                argThat(a -> a.getName().equals("ACTION4"))))
            .thenReturn("TRUE");

        var requestCtx = _taskMergingManager.getRequestContext(
                _testJob, _testMedia, 3, 0);

        var track1 = createTrack("ALGO2", "TEST_DET_TYPE2");
        var message1 = createMessage();
        var updatedMsg1 = requestCtx.addBreadCrumb(message1, track1);
        assertSame(message1, updatedMsg1);
        assertTrue(updatedMsg1.getHeader("breadcrumbId").toString().startsWith("mpf-"));

        var track2 = createTrack("ALGO3", "TEST_DET_TYPE3");
        var message2 = createMessage();
        var updatedMsg2 = requestCtx.addBreadCrumb(message2, track2);
        assertSame(message2, updatedMsg2);
        assertTrue(updatedMsg2.getHeader("breadcrumbId").toString().startsWith("mpf-"));
        assertNotEquals(
            updatedMsg1.getHeader("breadcrumbId"),
            updatedMsg2.getHeader("breadcrumbId"));


        try (var respCtx = _taskMergingManager.getResponseContext(
                _testJob, _testMedia, 3, 0, "TEST_DET_TYPE4",
                updatedMsg1.getHeaders())) {
            assertEquals("TEST_DET_TYPE2", respCtx.getDetectionType());
            assertEquals("ALGO2", respCtx.getAlgorithm());
        }

        try (var respCtx = _taskMergingManager.getResponseContext(
                _testJob, _testMedia, 3, 0, "TEST_DET_TYPE4",
                updatedMsg2.getHeaders())) {
            assertEquals("TEST_DET_TYPE3", respCtx.getDetectionType());
            assertEquals("ALGO3", respCtx.getAlgorithm());
        }
    }


    @Test
    // The RequestContext is only used on feed forward jobs.
    public void testNonFeedForwardTaskMerging() {
        when(_mockAggJobPropUtil.getValue(
                eq(MpfConstants.OUTPUT_MERGE_WITH_PREVIOUS_TASK_PROPERTY),
                eq(_testJob), eq(_testMedia),
                argThat(a -> a.getName().equals("ACTION3")
                    || a.getName().equals("ACTION4"))))
            .thenReturn("TRUE");

        when(_mockAggJobPropUtil.actionAppliesToMedia(
                eq(_testJob), eq(_testMedia), any(Action.class)))
            .thenReturn(true);

        when(_mockRedis.getTrackType(123, 321, 1, 0))
            .thenReturn(Optional.of("TEST_DET_TYPE2"));

        try (var respCtx = _taskMergingManager.getResponseContext(
                _testJob, _testMedia, 3, 0, "TEST_DET_TYPE4",
                Map.of())) {
            assertEquals("TEST_DET_TYPE2", respCtx.getDetectionType());
            assertEquals("ALGO2", respCtx.getAlgorithm());
        }
    }


    @Test
    public void testClearJob() {
        when(_mockAggJobPropUtil.getValue(
                eq(MpfConstants.OUTPUT_MERGE_WITH_PREVIOUS_TASK_PROPERTY),
                eq(_testJob), eq(_testMedia),
                argThat(a -> a.getName().equals("ACTION4"))))
            .thenReturn("TRUE");

        var requestCtx = _taskMergingManager.getRequestContext(
                _testJob, _testMedia, 3, 0);

        var track1 = createTrack("ALGO2", "TEST_DET_TYPE2");
        var message1 = requestCtx.addBreadCrumb(createMessage(), track1);
        assertTrue(message1.getHeader("breadcrumbId").toString().startsWith("mpf-"));

        var track2 = createTrack("ALGO3", "TEST_DET_TYPE3");
        var message2 = requestCtx.addBreadCrumb(createMessage(), track2);
        assertTrue(message2.getHeader("breadcrumbId").toString().startsWith("mpf-"));

        var respCtx = _taskMergingManager.getResponseContext(
            _testJob, _testMedia, 3, 0,
            "TEST_DET_TYPE4", message1.getHeaders());
        respCtx.close();

        _taskMergingManager.clearJob(_testJob.getId());
        assertCleanedUp();
    }


    private Message createMessage() {
        var camelContext = mock(CamelContext.class);
        when(camelContext.getHeadersMapFactory())
            .thenReturn(new DefaultHeadersMapFactory());
        return new DefaultMessage(camelContext);
    }


    private Track createTrack(String mergedAlgorithm, String mergedType) {
        return new Track(
            0, 0, 0, 0, 0, 0, 0, 0,
            "ACTUAL_TYPE", mergedType, mergedAlgorithm,
            0, List.of(), Map.of(), mergedType);
    }

}
