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

package org.mitre.mpf.wfm.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.rest.api.pipelines.*;
import org.mitre.mpf.rest.api.pipelines.temp.TransientPipelineDefinition;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.mitre.mpf.wfm.service.pipeline.PipelineServiceImpl;
import org.mitre.mpf.wfm.service.pipeline.PipelineValidator;
import org.mitre.mpf.wfm.service.pipeline.TransientPipelinePartLookup;
import org.mitre.mpf.wfm.util.ObjectMapperFactory;
import org.mitre.mpf.wfm.util.PropertiesUtil;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;

public class TestPipelineService {

    private final PropertiesUtil _mockPropertiesUtil = mock(PropertiesUtil.class);

    private final ObjectMapper _objectMapper = ObjectMapperFactory.customObjectMapper();

    private final PipelineValidator _mockPipelineValidator = mock(PipelineValidator.class);

    private PipelineServiceImpl _pipelineService;


    private List<Algorithm> _algorithms;
    private List<Action> _actions;
    private List<Task> _tasks;
    private List<Pipeline> _pipelines;

    private Algorithm _algo1;
    private Algorithm _algo2;
    private Task _task1;
    private Action _action2;


    @Rule
    public TemporaryFolder _tempFolder = new TemporaryFolder();


    @Before
    public void init() throws IOException {
        TestUtil.initPipelineDataFiles(_mockPropertiesUtil, _tempFolder);

        _pipelineService = new PipelineServiceImpl(_mockPropertiesUtil, _objectMapper, _mockPipelineValidator);
        initPipelineElements();
    }

    private void initPipelineElements() {
        var algo1Properties = List.of(
                new AlgorithmProperty("PROP1", "PROP1 description", ValueType.INT,
                                      "1", null),
                new AlgorithmProperty("PROP2", "PROP2 description", ValueType.STRING,
                                      null, "prop2.value"));
        _algo1 = new Algorithm(
                "ALGO1", "algo1 description", ActionType.DETECTION,
                new Algorithm.Requires(List.of("STATE1", "STATE2")),
                new Algorithm.Provides(List.of("STATE3", "STATE3"), algo1Properties),
                true, true);

        _algo2 = new Algorithm(
                "ALGO2", "algo2 description", ActionType.DETECTION,
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of()),
                true, false);

        _algorithms = List.of(_algo1, _algo2);


        var action1 = new Action("ACTION1", "Action1 description", _algo1.getName(),
                                 List.of(new ActionProperty("PROP1", "PROP1Val"),
                                         new ActionProperty("Prop2", "Prop2Val")));

        _action2 = new Action("ACTION2", "Action 2 description", _algo2.getName(), List.of());

        var action3 = new Action("ACTION3", "Action 3 - missing algo", "SOME ALGO",
                                 List.of());
        _actions = List.of(action1, _action2, action3);


        _task1 = new Task("TASK1", "Task1 description",
                          List.of(action1.getName()));

        var task2 = new Task("TASK2", "Task2",
                             List.of(_action2.getName()));

        var task3 = new Task("TASK3", "Task3 description - missing action and missing algorithm",
                             List.of("SOME ACTION", action3.getName()));

        _tasks = List.of(_task1, task2, task3);


        var pipeline1 = new Pipeline("PIPELINE1", "Pipeline 1 description",
                                     List.of(_task1.getName(), task2.getName()));

        var pipeline2 = new Pipeline("PIPELINE2", "Pipeline 2 - missing task",
                                     List.of("SOME TASK"));

        var pipeline3 = new Pipeline("PIPELINE3", "Pipeline 3 - task missing action",
                                     List.of(_task1.getName(), task3.getName()));


        _pipelines = List.of(pipeline1, pipeline2, pipeline3);
    }


    @Test
    public void canSaveLoadAndDeletePipelineElements() throws IOException {

        for (var algorithm : _algorithms) {
            _pipelineService.save(algorithm);
            verify(_mockPipelineValidator)
                    .validateOnAdd(eq(algorithm), notNull());
        }

        for (var action : _actions) {
            _pipelineService.save(action);
            verify(_mockPipelineValidator)
                    .validateOnAdd(eq(action), notNull());
        }

        for (var task : _tasks) {
            _pipelineService.save(task);
            verify(_mockPipelineValidator)
                    .validateOnAdd(eq(task), notNull());
        }

        for (var pipeline : _pipelines) {
            _pipelineService.save(pipeline);
            verify(_mockPipelineValidator)
                    .validateOnAdd(eq(pipeline), notNull());
        }


        verifyLoaded(_algorithms, _actions, _tasks, _pipelines);


        _pipelineService.deleteAlgorithm(_algorithms.get(0).getName());
        _pipelineService.deleteAction(_actions.get(0).getName());
        _pipelineService.deleteTask(_tasks.get(0).getName());
        _pipelineService.deletePipeline(_pipelines.get(0).getName());
        _pipelineService.deletePipeline("DOES NOT EXIST");

        verifyLoaded(
                _algorithms.subList(1, 2),
                _actions.subList(1, 3),
                _tasks.subList(1, 3),
                _pipelines.subList(1, 3));
    }

    private void verifyLoaded(
            Collection<Algorithm> expectedAlgorithms,
            Collection<Action> expectedActions,
            Collection<Task> expectedTasks,
            Collection<Pipeline> expectedPipelines) throws IOException {

        // Use separate validator so that when we verify methods are called,
        // it won't pass from the validation that was performed when the pipeline element was saved.
        var loaderPipelineValidator = mock(PipelineValidator.class);

        var loaderPipelineService = new PipelineServiceImpl(_mockPropertiesUtil, _objectMapper,
                                                            loaderPipelineValidator);

        assertEquals(expectedAlgorithms.size(), loaderPipelineService.getAlgorithms().size());
        for (var expectedAlgo : expectedAlgorithms) {
            var loadedAlgo = loaderPipelineService.getAlgorithm(expectedAlgo.getName());
            assertNotSame("Deserialized version should be a different object.", expectedAlgo, loadedAlgo);
            assertEquals("Deserialized version should be equal to original.", expectedAlgo, loadedAlgo);

            verify(loaderPipelineValidator)
                    .validateOnAdd(eq(loadedAlgo), notNull());
        }

        assertEquals(expectedActions.size(), loaderPipelineService.getActions().size());
        for (var expectedAction : expectedActions) {
            var loadedAction = loaderPipelineService.getAction(expectedAction.getName());
            assertNotSame("Deserialized version should be a different object.", expectedAction, loadedAction);
            assertEquals("Deserialized version should be equal to original.", expectedAction, loadedAction);

            verify(loaderPipelineValidator)
                    .validateOnAdd(eq(loadedAction), notNull());
        }

        assertEquals(expectedTasks.size(), loaderPipelineService.getTasks().size());
        for (var expectedTask : expectedTasks) {
            var loadedTask = loaderPipelineService.getTask(expectedTask.getName());
            assertNotSame("Deserialized version should be a different object.", expectedTask, loadedTask);
            assertEquals("Deserialized version should be equal to original.", expectedTask, loadedTask);

            verify(loaderPipelineValidator)
                    .validateOnAdd(eq(loadedTask), notNull());
        }


        assertEquals(expectedPipelines.size(), loaderPipelineService.getPipelines().size());
        for (var expectedPipeline : expectedPipelines) {
            var loadedPipeline = loaderPipelineService.getPipeline(expectedPipeline.getName());
            assertNotSame("Deserialized version should be a different object.",
                          expectedPipeline, loadedPipeline);
            assertEquals("Deserialized version should be equal to original.", expectedPipeline, loadedPipeline);

            verify(loaderPipelineValidator)
                    .validateOnAdd(eq(loadedPipeline), notNull());
        }
    }


    @Test
    public void canGetJobPipelineElements() {
        saveAllPipelineElements();

        JobPipelineElements pipelineElements = _pipelineService.getBatchPipelineElements(_pipelines.get(0).getName());

        verify(_mockPipelineValidator)
                .verifyBatchPipelineRunnable(eq(_pipelines.get(0).getName()),
                                             same(_pipelineService));

        assertEquals(_pipelines.get(0), pipelineElements.getPipeline());

        assertEquals(2, pipelineElements.getTaskCount());
        assertEquals(2, pipelineElements.getTasks().size());
        assertEquals(_tasks.get(0), pipelineElements.getTask(_tasks.get(0).getName()));
        assertEquals(_tasks.get(1), pipelineElements.getTask(_tasks.get(1).getName()));

        assertEquals(2, pipelineElements.getActions().size());
        assertEquals(_actions.get(0), pipelineElements.getAction(_actions.get(0).getName()));
        assertEquals(_actions.get(1), pipelineElements.getAction(_actions.get(1).getName()));
        assertEquals(_actions.get(0), pipelineElements.getAction(0, 0));
        assertEquals(_actions.get(1), pipelineElements.getAction(1, 0));


        assertEquals(2, pipelineElements.getAlgorithms().size());
        assertEquals(_algorithms.get(0), pipelineElements.getAlgorithm(_algorithms.get(0).getName()));
        assertEquals(_algorithms.get(1), pipelineElements.getAlgorithm(_algorithms.get(1).getName()));
        assertEquals(_algorithms.get(0), pipelineElements.getAlgorithm(0, 0));
        assertEquals(_algorithms.get(1), pipelineElements.getAlgorithm(1, 0));
    }



    @Test
    public void canGetJobPipelineElementsWithDuplicateTasks() {
        saveAllPipelineElements();

        var pipeline = new Pipeline("with dups", "descr",
                                    List.of(_tasks.get(0).getName(), _tasks.get(0).getName()));
        _pipelineService.save(pipeline);

        JobPipelineElements pipelineElements = _pipelineService.getBatchPipelineElements(pipeline.getName());

        verify(_mockPipelineValidator)
                .verifyBatchPipelineRunnable(eq(pipeline.getName()), same(_pipelineService));

        assertEquals(pipeline, pipelineElements.getPipeline());

        assertEquals(2, pipelineElements.getTaskCount());
        assertEquals(1, pipelineElements.getTasks().size());
        assertEquals(_tasks.get(0), pipelineElements.getTask(_tasks.get(0).getName()));

        assertEquals(1, pipelineElements.getActions().size());
        assertEquals(_actions.get(0), pipelineElements.getAction(_actions.get(0).getName()));
        assertEquals(_actions.get(0), pipelineElements.getAction(0, 0));
        assertEquals(_actions.get(0), pipelineElements.getAction(1, 0));


        assertEquals(1, pipelineElements.getAlgorithms().size());
        assertEquals(_algorithms.get(0), pipelineElements.getAlgorithm(_algorithms.get(0).getName()));
        assertEquals(_algorithms.get(0), pipelineElements.getAlgorithm(0, 0));
        assertEquals(_algorithms.get(0), pipelineElements.getAlgorithm(1, 0));
    }


    private void saveAllPipelineElements() {
        _algorithms.forEach(_pipelineService::save);
        _actions.forEach(_pipelineService::save);
        _tasks.forEach(_pipelineService::save);
        _pipelines.forEach(_pipelineService::save);
    }


    @Test
    public void canConvertTransientPipelineToRegularPipeline() throws IOException {
        saveAllPipelineElements();

        var path = TestUtil.findFilePath("/transient-pipelines/all-transient.json");
        var transientPipeline = _objectMapper.readValue(path.toFile(),
                                                        TransientPipelineDefinition.class);
        var pipelineElements = _pipelineService.getBatchPipelineElements(transientPipeline);

        verify(_mockPipelineValidator)
                .validateTransientPipeline(same(transientPipeline),
                                           any(TransientPipelinePartLookup.class));

        assertEquals(List.of("TASK1", "TASK2"), pipelineElements.getPipeline().getTasks());

        var task1 = pipelineElements.getTask("TASK1");
        assertEquals("TASK1", task1.getName());
        assertThat(task1.getDescription(), containsString("transient"));
        assertEquals(List.of("ACTION1"), task1.getActions());


        var task2 = pipelineElements.getTask("TASK2");
        assertEquals("TASK2", task2.getName());
        assertThat(task2.getDescription(), containsString("transient"));
        assertEquals(List.of("ACTION2", "ACTION3"), task2.getActions());

        var action1 = pipelineElements.getAction("ACTION1");
        assertEquals("ALGO1", action1.getAlgorithm());
        assertThat(action1.getDescription(), containsString("transient"));
        assertThat(action1.getProperties(), hasSize(1));
        assertEquals("value1", action1.getPropertyValue("prop1"));
        assertEquals(_algo1, pipelineElements.getAlgorithm(0, 0));

        var action2 = pipelineElements.getAction("ACTION2");
        assertEquals("ALGO2", action2.getAlgorithm());
        assertThat(action2.getProperties(), hasSize(1));
        assertEquals("value2", action2.getPropertyValue("prop2"));
        assertEquals(_algo2, pipelineElements.getAlgorithm(1, 0));


        var action3 = pipelineElements.getAction("ACTION3");
        assertEquals("ALGO2", action3.getAlgorithm());
        assertThat(action3.getProperties(), empty());
        assertEquals(_algo2, pipelineElements.getAlgorithm(1, 1));
    }


    @Test
    public void transientPipelineCanUseRegularTasksAndActions() throws IOException {
        saveAllPipelineElements();

        var path = TestUtil.findFilePath("/transient-pipelines/partial-transient.json");
        var transientPipeline = _objectMapper.readValue(path.toFile(),
                                                        TransientPipelineDefinition.class);
        var pipelineElements = _pipelineService.getBatchPipelineElements(transientPipeline);

        verify(_mockPipelineValidator)
                .validateTransientPipeline(same(transientPipeline),
                                           any(TransientPipelinePartLookup.class));

        assertEquals(List.of("TASK1", "TASK2"), pipelineElements.getPipeline().getTasks());
        assertEquals(_task1, pipelineElements.getTask("TASK1"));
        assertEquals(_action2, pipelineElements.getAction(1, 1));

        var task2 = pipelineElements.getTask("TASK2");
        assertEquals("TASK2", task2.getName());
        assertThat(task2.getDescription(), containsString("transient"));
        assertEquals(List.of("ACTION1", "ACTION2"), task2.getActions());

        var action1 = pipelineElements.getAction("ACTION1");
        assertEquals("ALGO1", action1.getAlgorithm());
        assertThat(action1.getDescription(), containsString("transient"));
        assertThat(action1.getProperties(), hasSize(1));
        assertEquals("value1", action1.getPropertyValue("prop1"));
        assertEquals(_algo1, pipelineElements.getAlgorithm(0, 0));
    }
}
