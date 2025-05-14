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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultHeadersMapFactory;
import org.apache.camel.impl.DefaultMessage;
import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.ActionType;
import org.mitre.mpf.rest.api.pipelines.Algorithm;
import org.mitre.mpf.rest.api.pipelines.Pipeline;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.test.MockitoTest;
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

    @InjectMocks
    private TaskMergingManager _taskMergingManager;

    private BatchJob _testJob;

    private MediaImpl _testMedia;

    @Before
    public void init() {
        var algo1 = new Algorithm(
                "ALGO1", "", ActionType.DETECTION, "", OptionalInt.empty(), null, null, true,
                false);
        var algo2 = new Algorithm(
                "ALGO2", "", ActionType.DETECTION, "", OptionalInt.empty(), null, null, true,
                false);
        var algo3 = new Algorithm(
                "ALGO3", "", ActionType.DETECTION, "", OptionalInt.empty(), null, null, true,
                false);
        var algo4 = new Algorithm(
                "ALGO4", "", ActionType.DETECTION, "", OptionalInt.empty(), null, null, true,
                false);

        var action1 = new Action("ACTION1", "", algo1.name(), List.of());
        var action2 = new Action("ACTION2", "", algo2.name(), List.of());
        var action3 = new Action("ACTION3", "", algo3.name(), List.of());
        var action4 = new Action("ACTION4", "", algo4.name(), List.of());

        var task1 = new Task("TASK1", "", List.of(action1.name()));
        var task2 = new Task("TASK2", "", List.of(action2.name()));
        var task3 = new Task("TASK3", "", List.of(action3.name()));
        var task4 = new Task("TASK4", "", List.of(action4.name()));

        var pipeline = new Pipeline(
                "PIPELINE", "",
                List.of(task1.name(), task2.name(), task3.name(), task4.name()));
        var pipelineElements = new JobPipelineElements(
                pipeline,
                List.of(task1, task2, task3, task4),
                List.of(action1, action2, action3, action4),
                List.of(algo1, algo2, algo3, algo4));

        _testMedia = new MediaImpl(
                321, "file:///fake-uri", UriScheme.FILE, null, Map.of(), Map.of(),
                List.of(), List.of(), List.of(), null, null);

        _testJob = new BatchJobImpl(
            123, null, null,
            pipelineElements, 0, null, null,
            List.of(_testMedia), Map.of(), Map.of(), false);
    }


    @Test
    public void testFirstTask() {
        assertFalse(_taskMergingManager.needsBreadCrumb(_testJob, _testMedia, 0, 0));
        int task = _taskMergingManager.getMergedTaskIndex(_testJob, _testMedia, 0, 0, Map.of());
        assertEquals(0, task);
    }


    @Test
    public void testTaskMergingDisabled() {
        assertFalse(_taskMergingManager.needsBreadCrumb(_testJob, _testMedia, 1, 0));
        int task = _taskMergingManager.getMergedTaskIndex(_testJob, _testMedia, 1, 0, Map.of());
        assertEquals(1, task);
    }


    @Test
    public void addsBreadCrumbWhenTaskMergingEnabled() {
        when(_mockAggJobPropUtil.getValue(
                    eq(MpfConstants.OUTPUT_MERGE_WITH_PREVIOUS_TASK_PROPERTY),
                    eq(_testJob), eq(_testMedia),
                    argThat(a -> a.name().equals("ACTION4"))))
                .thenReturn("TRUE");

        when(_mockAggJobPropUtil.getValue(
                    eq(MpfConstants.TRIGGER),
                    eq(_testJob), eq(_testMedia),
                    argThat(a -> a.name().equals("ACTION1"))))
                .thenReturn("TEST=TRUE");

        assertTrue(_taskMergingManager.needsBreadCrumb(_testJob, _testMedia, 3, 0));

        var track1 = createTrack(1);
        var message1 = createMessage();
        _taskMergingManager.addBreadCrumb(message1, List.of(track1));

        var breadCrumb1 = message1.getHeader("breadcrumbId", String.class);
        assertTrue(breadCrumb1.startsWith("mpf-1-"));
        assertEquals(1, _taskMergingManager.getMergedTaskIndex(
                _testJob, _testMedia, -1, -1, message1.getHeaders()));


        var track2 = createTrack(2);
        var message2 = createMessage();
        _taskMergingManager.addBreadCrumb(message2, List.of(track2));
        var breadCrumb2 = message2.getHeader("breadcrumbId", String.class);
        assertTrue(breadCrumb2.startsWith("mpf-2-"));
        assertEquals(2, _taskMergingManager.getMergedTaskIndex(
                _testJob, _testMedia, -1, -1, message2.getHeaders()));

        var prefixAndUuid1 = breadCrumb1.substring(0, breadCrumb1.lastIndexOf('-'));
        var prefixAndUuid2 = breadCrumb2.substring(0, breadCrumb2.lastIndexOf('-'));
        assertNotEquals(prefixAndUuid1, prefixAndUuid2);
    }


    @Test
    public void testTaskMergingWhenBreadCrumbNotNeeded() {
        when(_mockAggJobPropUtil.getValue(
                eq(MpfConstants.OUTPUT_MERGE_WITH_PREVIOUS_TASK_PROPERTY),
                eq(_testJob), eq(_testMedia),
                argThat(a -> a.name().equals("ACTION3")
                    || a.name().equals("ACTION4"))))
            .thenReturn("TRUE");

        when(_mockAggJobPropUtil.actionAppliesToMedia(
                eq(_testJob), eq(_testMedia), any(Action.class)))
            .thenReturn(true);

        assertFalse(_taskMergingManager.needsBreadCrumb(_testJob, _testMedia, 3, 0));
        int task = _taskMergingManager.getMergedTaskIndex(
            _testJob, _testMedia, 3, 0, Map.of());
        assertEquals(1, task);
    }


    private Message createMessage() {
        var camelContext = mock(CamelContext.class);
        when(camelContext.getHeadersMapFactory())
            .thenReturn(new DefaultHeadersMapFactory());
        return new DefaultMessage(camelContext);
    }


    private Track createTrack(int mergedTask) {
        return new Track(
            0, 0, 0, 0, 0, 0, 0, 0,
            mergedTask,
            0, List.of(), Map.of(), "", "", null, null);
    }

}
