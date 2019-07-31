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
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.util.ObjectMapperFactory;
import org.mitre.mpf.wfm.util.PropertiesUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.mitre.mpf.test.TestUtil.anyNonNull;
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

        _pipelineService = new PipelineServiceImpl(_mockPropertiesUtil, _objectMapper, _mockPipelineValidator,
                                                   null);
    }

    private static void copyTemplate(String templateFileName, Path dest) throws IOException {
        Path source = Paths.get(TestUtil.findFile("/templates/" + templateFileName));
        Files.copy(source, dest);
    }



    @Test
    public void canSaveAndLoadPipelineComponents() throws IOException {
        List<Algorithm.Property> algo1Properties = Arrays.asList(
                new Algorithm.Property("PROP1", "PROP1 description", ValueType.INT,
                                       "1", null),
                new Algorithm.Property("PROP2", "PROP2 description", ValueType.STRING,
                                       null, "prop2.value"));
        Algorithm algo1 = new Algorithm(
                "ALGO1", "algo1 description", ActionType.DETECTION,
                new Algorithm.Requires(Arrays.asList("STATE1", "STATE2")),
                new Algorithm.Provides(Arrays.asList("STATE3", "STATE3"), algo1Properties),
                true, true);

        _pipelineService.save(algo1);
        verify(_mockPipelineValidator)
                .validateOnAdd(eq(algo1), anyNonNull());


        Algorithm algo2 = new Algorithm(
                "ALGO2", "algo2 description", ActionType.DETECTION,
                new Algorithm.Requires(Collections.emptyList()),
                new Algorithm.Provides(Collections.emptyList(), Collections.emptyList()),
                true, false);

        _pipelineService.save(algo2);
        verify(_mockPipelineValidator)
                .validateOnAdd(eq(algo2), anyNonNull());

        Action action1 = new Action("ACTION1", "Action1 description", algo1.getName(),
                                    Arrays.asList(new Action.Property("PROP1", "PROP1Val"),
                                                  new Action.Property("Prop2", "Prop2Val")));
        _pipelineService.save(action1);
        verify(_mockPipelineValidator)
                .validateOnAdd(eq(action1), anyNonNull());

        Action action2 = new Action("ACTION2", "Action 2 description", "SOME ALGO",
                                    Collections.emptyList());
        _pipelineService.save(action2);
        verify(_mockPipelineValidator)
                .validateOnAdd(eq(action2), anyNonNull());

        Task task1 = new Task("TASK1", "Task1 description",
                              Arrays.asList(action1.getName(), action2.getName()));
        _pipelineService.save(task1);
        verify(_mockPipelineValidator)
                .validateOnAdd(eq(task1), anyNonNull());

        Task task2 = new Task("TASK2", "Task2 description", Collections.singletonList("SOME ACTION"));
        _pipelineService.save(task2);
        verify(_mockPipelineValidator)
                .validateOnAdd(eq(task2), anyNonNull());


        Pipeline pipeline1 = new Pipeline("PIPELINE1", "Pipeline 1 description",
                                          Arrays.asList(task2.getName(), task1.getName()));
        _pipelineService.save(pipeline1);
        verify(_mockPipelineValidator)
                .validateOnAdd(eq(pipeline1), anyNonNull());

        Pipeline pipeline2 = new Pipeline("PIPELINE2", "Pipeline 2 description",
                                          Collections.singleton("SOME TASK"));
        _pipelineService.save(pipeline2);
        verify(_mockPipelineValidator)
                .validateOnAdd(eq(pipeline2), anyNonNull());



        verifyLoaded(
                Arrays.asList(algo1, algo2),
                Arrays.asList(action1, action2),
                Arrays.asList(task1, task2),
                Arrays.asList(pipeline1, pipeline2));

    }

    private void verifyLoaded(
            Collection<Algorithm> expectedAlgorithms,
            Collection<Action> expectedActions,
            Collection<Task> expectedTasks,
            Collection<Pipeline> expectedPipelines) throws IOException {

        // Use separate validator so that when we verify methods are called,
        // it won't pass from the validation that was performed when the pipeline component was saved.
        PipelineValidator loaderPipelineValidator = mock(PipelineValidator.class);

        PipelineService loaderPipelineService = new PipelineServiceImpl(_mockPropertiesUtil, _objectMapper,
                                                                        loaderPipelineValidator, null);

        for (Algorithm expectedAlgo : expectedAlgorithms) {
            Algorithm loadedAlgo = loaderPipelineService.getAlgorithm(expectedAlgo.getName());
            assertNotSame("Deserialized version should be a different object.", expectedAlgo, loadedAlgo);
            assertEquals("Deserialized version should be equal to original.", expectedAlgo, loadedAlgo);

            verify(loaderPipelineValidator)
                    .validateOnAdd(eq(loadedAlgo), anyNonNull());
        }

        for (Action expectedAction : expectedActions) {
            Action loadedAction = loaderPipelineService.getAction(expectedAction.getName());
            assertNotSame("Deserialized version should be a different object.", expectedAction, loadedAction);
            assertEquals("Deserialized version should be equal to original.", expectedAction, loadedAction);

            verify(loaderPipelineValidator)
                    .validateOnAdd(eq(loadedAction), anyNonNull());
        }

        for (Task expectedTask : expectedTasks) {
            Task loadedTask = loaderPipelineService.getTask(expectedTask.getName());
            assertNotSame("Deserialized version should be a different object.", expectedTask, loadedTask);
            assertEquals("Deserialized version should be equal to original.", expectedTask, loadedTask);

            verify(loaderPipelineValidator)
                    .validateOnAdd(eq(loadedTask), anyNonNull());
        }


        for (Pipeline expectedPipeline : expectedPipelines) {
            Pipeline loadedPipeline = loaderPipelineService.getPipeline(expectedPipeline.getName());
            assertNotSame("Deserialized version should be a different object.",
                          expectedPipeline, loadedPipeline);
            assertEquals("Deserialized version should be equal to original.", expectedPipeline, loadedPipeline);

            verify(loaderPipelineValidator)
                    .validateOnAdd(eq(loadedPipeline), anyNonNull());
        }
    }



}