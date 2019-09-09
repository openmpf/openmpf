/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.service.pipeline.PipelineServiceImpl;
import org.mitre.mpf.wfm.service.pipeline.PipelineValidator;
import org.mitre.mpf.wfm.util.ObjectMapperFactory;
import org.mitre.mpf.wfm.util.PropertiesUtil;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.mockito.Mockito.*;

public class TestPipelineService {

    private final PropertiesUtil _mockPropertiesUtil = mock(PropertiesUtil.class);

    private final ObjectMapper _objectMapper = ObjectMapperFactory.customObjectMapper();

    private final PipelineValidator _mockPipelineValidator = mock(PipelineValidator.class);

    private PipelineServiceImpl _pipelineService;


    @Rule
    public TemporaryFolder _tempFolder = new TemporaryFolder();


    @Before
    public void init() throws IOException {
        TestUtil.initPipelineDataFiles(_mockPropertiesUtil, _tempFolder);

        _pipelineService = new PipelineServiceImpl(_mockPropertiesUtil, _objectMapper, _mockPipelineValidator);
    }

    @Test
    public void canSaveLoadAndDeletePipelineElements() throws IOException {
        var algo1Properties = List.of(
                new AlgorithmProperty("PROP1", "PROP1 description", ValueType.INT,
                                       "1", null),
                new AlgorithmProperty("PROP2", "PROP2 description", ValueType.STRING,
                                       null, "prop2.value"));
        var algo1 = new Algorithm(
                "ALGO1", "algo1 description", ActionType.DETECTION,
                new Algorithm.Requires(List.of("STATE1", "STATE2")),
                new Algorithm.Provides(List.of("STATE3", "STATE3"), algo1Properties),
                true, true);

        _pipelineService.save(algo1);
        verify(_mockPipelineValidator)
                .validateOnAdd(eq(algo1), notNull());


        var algo2 = new Algorithm(
                "ALGO2", "algo2 description", ActionType.DETECTION,
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of()),
                true, false);

        _pipelineService.save(algo2);
        verify(_mockPipelineValidator)
                .validateOnAdd(eq(algo2), notNull());

        var action1 = new Action("ACTION1", "Action1 description", algo1.getName(),
                                    List.of(new ActionProperty("PROP1", "PROP1Val"),
                                            new ActionProperty("Prop2", "Prop2Val")));
        _pipelineService.save(action1);
        verify(_mockPipelineValidator)
                .validateOnAdd(eq(action1), notNull());

        var action2 = new Action("ACTION2", "Action 2 description", "SOME ALGO",
                                    List.of());
        _pipelineService.save(action2);
        verify(_mockPipelineValidator)
                .validateOnAdd(eq(action2), notNull());

        var task1 = new Task("TASK1", "Task1 description",
                             List.of(action1.getName(), action2.getName()));
        _pipelineService.save(task1);
        verify(_mockPipelineValidator)
                .validateOnAdd(eq(task1), notNull());

        var task2 = new Task("TASK2", "Task2 description", List.of("SOME ACTION"));
        _pipelineService.save(task2);
        verify(_mockPipelineValidator)
                .validateOnAdd(eq(task2), notNull());


        var pipeline1 = new Pipeline("PIPELINE1", "Pipeline 1 description",
                                     List.of(task2.getName(), task1.getName()));
        _pipelineService.save(pipeline1);
        verify(_mockPipelineValidator)
                .validateOnAdd(eq(pipeline1), notNull());

        var pipeline2 = new Pipeline("PIPELINE2", "Pipeline 2 description",
                                     List.of("SOME TASK"));
        _pipelineService.save(pipeline2);
        verify(_mockPipelineValidator)
                .validateOnAdd(eq(pipeline2), notNull());

        verifyLoaded(
                List.of(algo1, algo2),
                List.of(action1, action2),
                List.of(task1, task2),
                List.of(pipeline1, pipeline2));


        _pipelineService.deleteAlgorithm(algo1.getName());
        _pipelineService.deleteAction(action1.getName());
        _pipelineService.deleteTask(task1.getName());
        _pipelineService.deletePipeline(pipeline1.getName());
        _pipelineService.deletePipeline("DOES NOT EXIST");
        verifyLoaded(
                List.of(algo2),
                List.of(action2),
                List.of(task2),
                List.of(pipeline2));
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
}
