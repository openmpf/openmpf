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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultHeadersMapFactory;
import org.apache.camel.impl.DefaultMessage;
import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.rest.api.MediaUri;
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
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.segmenting.TriggerProcessor;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;


public class TestTaskAnnotatorService extends MockitoTest.Strict {

    @Mock
    private AggregateJobPropertiesUtil _mockAggJobPropUtil;

    @Mock
    private TriggerProcessor _mockTriggerProcessor;

    @InjectMocks
    private TaskAnnotatorService _taskAnnotatorService;

    private BatchJob _testJob;

    private MediaImpl _testMedia;


    @Before
    public void init() {
        var algos = new ArrayList<Algorithm>();
        var actions = new ArrayList<Action>();
        var tasks = new ArrayList<Task>();

        for (int i = 0; i < 4; i++) {
            var algo = new Algorithm(
                    "ALGO" + i, "", ActionType.DETECTION, "", OptionalInt.empty(), null, null,
                    true, false);
            algos.add(algo);

            var action = new Action("ACTION" + i, "", algo.name(), List.of());
            actions.add(action);

            var task = new Task("TASK" + i, "", List.of(action.name()));
            tasks.add(task);
        }

        var taskNames = tasks.stream()
                .map(Task::name)
                .toList();
        var pipeline = new Pipeline("PIPELINE", "", taskNames);
        var pipelineElements = new JobPipelineElements(pipeline, tasks, actions, algos);

        _testMedia = new MediaImpl(
                321, MediaUri.create("file:///fake-uri"), UriScheme.FILE, null, Map.of(), Map.of(),
                List.of(), List.of(), List.of(), null, null);

        _testJob = new BatchJobImpl(
            123, null, null,
            pipelineElements, 0, null, null,
            List.of(_testMedia), Map.of(), Map.of(), false);
    }


    @Test
    public void testFirstTaskIsNeverAnnotator() {
        assertThat(_taskAnnotatorService.actionIsAnnotator(_testJob, _testMedia, 0, 0))
                .isFalse();
        verifyNoInteractions(_mockAggJobPropUtil);
    }


    @Test
    public void testTaskAnnotationDisabled() {
        assertThat(_taskAnnotatorService.needsBreadCrumb(_testJob, _testMedia, 1, 0))
            .isFalse();
        assertThat(getAnnotatedTaskIndices(Map.of())).isEmpty();
    }


    @Test
    public void addsBreadCrumbWhenTaskAnnotationEnabled() {
        setActionsAreAnnotators("ACTION3");
        assertThat(_taskAnnotatorService.needsBreadCrumb(_testJob, _testMedia, 3, 0))
                .isTrue();

        var track1 = createTrack(0, List.of(1));
        var message1 = createMessage();
        _taskAnnotatorService.addBreadCrumb(message1, List.of(track1));

        var breadCrumb1 = message1.getHeader("breadcrumbId", String.class);
        assertThat(breadCrumb1).startsWith("mpf-0;1-");
        assertThat(getAnnotatedTaskIndices(message1.getHeaders()))
                .containsExactly(0, 1);


        var track2 = createTrack(1, List.of(2));
        var message2 = createMessage();
        _taskAnnotatorService.addBreadCrumb(message2, List.of(track2));
        var breadCrumb2 = message2.getHeader("breadcrumbId", String.class);
        assertThat(breadCrumb2).startsWith("mpf-1;2-");
        assertThat(getAnnotatedTaskIndices(message2.getHeaders()))
                .containsExactly(1, 2);

        var prefixAndUuid1 = breadCrumb1.substring(0, breadCrumb1.lastIndexOf('-'));
        var prefixAndUuid2 = breadCrumb2.substring(0, breadCrumb2.lastIndexOf('-'));
        assertNotEquals(prefixAndUuid1, prefixAndUuid2);
    }


    @Test
    public void testInvalidBreadCrumb() {
        assertThat(getAnnotatedTaskIndices(Map.of("breadcrumbId", ""))).isEmpty();
        assertThat(getAnnotatedTaskIndices(Map.of("breadcrumbId", 1))).isEmpty();
        assertThat(getAnnotatedTaskIndices(Map.of("breadcrumbId", "mpf-a-uuid"))).isEmpty();
        assertThat(getAnnotatedTaskIndices(Map.of("breadcrumbId", "abc-1-uuid"))).isEmpty();
    }


    @Test
    public void testValidBreadCrumb() {
        assertThat(getAnnotatedTaskIndices(Map.of("breadcrumbId", "mpf-1-uuid")))
                .containsExactly(1);
        assertThat(getAnnotatedTaskIndices(Map.of("breadcrumbId", "mpf-0;1;2-uuid")))
                .containsExactly(0, 1, 2);
    }


    @Test
    public void testNoTracksAreAnnotated() {
        setActionsApplyToAllMedia();
        setNoTriggers();

        var isAnnotated = _taskAnnotatorService.createIsAnnotatedChecker(_testJob, _testMedia);
        assertThat(isAnnotated)
            .rejects(
                createTrack(0),
                createTrack(1),
                createTrack(2),
                createTrack(3));
    }


    @Test
    public void isAnnotatedChecksIfActionIsNotApplicable() {
        setActionsApplyToAllMedia();
        setNoTriggers();
        setActionsAreAnnotators("ACTION2", "ACTION3");

        var isAnnotated = _taskAnnotatorService.createIsAnnotatedChecker(_testJob, _testMedia);
        assertThat(isAnnotated)
            .accepts(
                createTrack(1),
                createTrack(2))
            .rejects(
                createTrack(0),
                createTrack(3));
    }

    @Test
    public void isAnnotatedChecksIfActionIsApplicable() {
        setActionsAreAnnotators("ACTION2", "ACTION3");
        when(_mockAggJobPropUtil.actionAppliesToMedia(eq(_testJob), eq(_testMedia), notNull()))
            .thenAnswer(inv -> {
                Action action = inv.getArgument(2);
                return !action.name().equals("ACTION1");
            });
        setNoTriggers();

        var isAnnotated = _taskAnnotatorService.createIsAnnotatedChecker(_testJob, _testMedia);
        assertThat(isAnnotated)
            .accepts(
                createTrack(0),
                createTrack(1),
                createTrack(2))
            .rejects(
                createTrack(3));
    }


    @Test
    public void isAnnotatedChecksTriggers() {
        setActionsApplyToAllMedia();
        setActionsAreAnnotators("ACTION2", "ACTION3");

        var untriggeredTrack = createTrack(0);
        when(_mockTriggerProcessor.createActionTrigger(eq(_testJob), eq(_testMedia), notNull()))
            .thenAnswer((Answer<Predicate<Track>>) inv -> {
                Action action = inv.getArgument(2);
                if (action.name().equals("ACTION1")) {
                    return t -> t != untriggeredTrack;
                }
                else {
                    return t -> true;
                }
            });

        var isAnnotated = _taskAnnotatorService.createIsAnnotatedChecker(_testJob, _testMedia);
        assertThat(isAnnotated)
            .accepts(
                untriggeredTrack,
                createTrack(1),
                createTrack(2))
            .rejects(
                createTrack(0),
                createTrack(3));
    }


    private static List<Integer> getAnnotatedTaskIndices(Map<String, Object> headers) {
        return TaskAnnotatorService.getAnnotatedTaskIndices(headers);
    }

    private void setNoTriggers() {
        when(_mockTriggerProcessor.createActionTrigger(eq(_testJob), eq(_testMedia), notNull()))
            .thenReturn(t -> true);
    }

    private void setActionsApplyToAllMedia() {
        when(_mockAggJobPropUtil.actionAppliesToMedia(eq(_testJob), eq(_testMedia), notNull()))
            .thenReturn(true);
    }


    private void setActionsAreAnnotators(String... actionNames) {
        var annotatorActionNames = Set.of(actionNames);
        when(_mockAggJobPropUtil.getBool(eq("IS_ANNOTATOR"), eq(_testJob), eq(_testMedia), notNull()))
                .thenAnswer(inv -> {
                    Action action = inv.getArgument(3);
                    return annotatorActionNames.contains(action.name());
                });
    }

    private Message createMessage() {
        var camelContext = mock(CamelContext.class);
        when(camelContext.getHeadersMapFactory())
            .thenReturn(new DefaultHeadersMapFactory());
        return new DefaultMessage(camelContext);
    }

    private Track createTrack(int creationTaskIdx) {
        return createTrack(creationTaskIdx, List.of());
    }

    private Track createTrack(int creationTaskIdx, Collection<Integer> annotatedTaskIndices) {
        return new Track(
            0, 0, creationTaskIdx, 0, 0, 0, 0, 0,
            annotatedTaskIndices,
            0, List.of(), Map.of(), "", "", null, null);
    }
}
