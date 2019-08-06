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

import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.rest.api.pipelines.*;
import org.mitre.mpf.wfm.service.component.JsonComponentDescriptor;
import org.mitre.mpf.wfm.service.component.TestDescriptorFactory;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;

public class TestPipelineValidator {

    private PipelineValidator _pipelineValidator;


    private final Map<String, Pipeline> _pipelines = new HashMap<>();
    private final Map<String, Task> _tasks = new HashMap<>();
    private final Map<String, Action> _actions = new HashMap<>();
    private final Map<String, Algorithm> _algorithms = new HashMap<>();

    @Before
    public void init() {
        var springValidator = new LocalValidatorFactoryBean();
        springValidator.afterPropertiesSet();
        _pipelineValidator = new PipelineValidator(springValidator);
    }


    private void verifyBatchPipelineRunnable(String pipelineName) {
        _pipelineValidator.verifyBatchPipelineRunnable(pipelineName, _pipelines, _tasks, _actions, _algorithms);
    }

    private void verifyStreamingPipelineRunnable(String pipelineName) {
        _pipelineValidator.verifyStreamingPipelineRunnable(pipelineName, _pipelines, _tasks, _actions, _algorithms);
    }

    private <T extends PipelineComponent> void addComponent(T pipelineComponent, Map<String, T> existing) {
        _pipelineValidator.validateOnAdd(pipelineComponent, existing);
        existing.put(pipelineComponent.getName(), pipelineComponent);
    }

    private void addComponent(PipelineComponent pipelineComponent) {
        if (pipelineComponent instanceof Pipeline) {
            addComponent((Pipeline) pipelineComponent, _pipelines);
            return;
        }
        if (pipelineComponent instanceof Task) {
            addComponent((Task) pipelineComponent, _tasks);
            return;
        }
        if (pipelineComponent instanceof Action) {
            addComponent((Action) pipelineComponent, _actions);
            return;
        }
        if (pipelineComponent instanceof Algorithm) {
            addComponent((Algorithm) pipelineComponent, _algorithms);
        }
    }


    private void addComponents(Collection<? extends PipelineComponent> pipelineComponents) {
        for (PipelineComponent component : pipelineComponents) {
            addComponent(component);
        }
    }

    private void addDescriptorPipelines(JsonComponentDescriptor descriptor) {
        addComponents(descriptor.getPipelines());
        addComponents(descriptor.getTasks());
        addComponents(descriptor.getActions());
        addComponent(descriptor.getAlgorithm());
    }



    @Test
    public void testValidationHappyPath() {
        var descriptor = TestDescriptorFactory.getWithCustomPipeline();

        addDescriptorPipelines(descriptor);
        addComponent(TestDescriptorFactory.getReferencedAlgorithm());

        verifyBatchPipelineRunnable(descriptor.getPipelines().get(0).getName());
    }


    @Test
    public void throwsExceptionWhenPipelineIsMissing() {
        var pipelineName = "TEST PIPELINE";
        try {
            verifyBatchPipelineRunnable(pipelineName);
            fail("Expected InvalidPipelineException");
        }
        catch (InvalidPipelineException e) {
            assertTrue(e.getMessage().contains(pipelineName));
        }
    }


    @Test
    public void throwsExceptionWhenPartOfPipelineMissing() {
        var invalidAlgorithmName = "INVALID ALGORITHM";
        var invalidActionName = "INVALID ACTION";
        var invalidTaskName = "INVALID TASK";

        var algorithm = TestDescriptorFactory.getReferencedAlgorithm();

        var action = new Action("TEST ACTION", "asdf", algorithm.getName(),
                                   List.of());
        var missingAlgoAction = new Action("MISSING ALGO ACTION", "asdf", invalidAlgorithmName,
                                           List.of());

        var validTask = new Task("TEST TASK", "asdf",
                                  List.of(invalidActionName, action.getName(), missingAlgoAction.getName()));

        var pipeline = new Pipeline("TEST PIPELINE", "asdf",
                                    List.of(validTask.getName(), invalidTaskName));
        addComponents(List.of(algorithm, action, missingAlgoAction, validTask, pipeline));

        try {
            verifyBatchPipelineRunnable(pipeline.getName());
            fail("Expected and exception");
        }
        catch (InvalidPipelineException e) {
            String errorMsg = e.getMessage();

            assertTrue(errorMsg.contains(invalidAlgorithmName));
            assertTrue(errorMsg.contains(invalidActionName));
            assertTrue(errorMsg.contains(invalidTaskName));

            assertFalse(errorMsg.contains(algorithm.getName()));
            assertFalse(errorMsg.contains(action.getName()));
            assertFalse(errorMsg.contains(missingAlgoAction.getName()));
            assertFalse(errorMsg.contains(validTask.getName()));
        }
    }


    @Test
    public void throwsExceptionWhenNotAllAlgorithmsSupportSameProcessingType() {
        var batchAlgo = new Algorithm(
                "BATCH ALGO", "asdf", ActionType.DETECTION,
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of()),
                true, false);
        addComponent(batchAlgo);

        var batchTaskName = createSingleActionTask("BATCH", batchAlgo.getName());

        var streamAlgo = new Algorithm(
                "STREAM ALGO", "asdf", ActionType.DETECTION,
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of()),
                false, true);
        addComponent(streamAlgo);

        var streamTaskName = createSingleActionTask("STREAM", streamAlgo.getName());


        var pipeline = new Pipeline("TEST PIPELINE", "asdf",
                                    List.of(batchTaskName, streamTaskName));
        addComponent(pipeline);

        try {
            verifyBatchPipelineRunnable(pipeline.getName());
            fail("Expected exception");
        }
        catch (InvalidPipelineException e) {
            String errorMsg = e.getMessage();
            assertTrue(errorMsg.contains("support batch processing"));
            assertTrue(errorMsg.contains("STREAM ALGO"));
        }

        try {
            verifyStreamingPipelineRunnable(pipeline.getName());
            fail("Expected exception");
        }
        catch (InvalidPipelineException e) {
            String errorMsg = e.getMessage();
            assertTrue(errorMsg.contains("support stream processing"));
            assertTrue(errorMsg.contains("BATCH ALGO"));
        }
    }


    @Test
    public void canHandleWhenOneAlgoSupportBatchAndOtherSupportsBoth() {
        var batchAlgo = new Algorithm(
                "BATCH ALGO", "descr", ActionType.DETECTION,
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of()),
                true, false);
        addComponent(batchAlgo);

        var batchTaskName = createSingleActionTask("BATCH", batchAlgo.getName());


        Algorithm bothAlgo = new Algorithm(
                "BOTH ALGO", "descr", ActionType.DETECTION,
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of()),
                true, true);
        addComponent(bothAlgo);

        var bothTaskName = createSingleActionTask("BOTH", bothAlgo.getName());

        var pipeline = new Pipeline("TEST PIPELINE", "descr",
                                         List.of(batchTaskName, bothTaskName));
        addComponent(pipeline);

        verifyBatchPipelineRunnable(pipeline.getName());
    }


    @Test
    public void canHandleWhenOneAlgoSupportsStreamingAndOtherSupportsBoth() {
        var bothAlgo = new Algorithm(
                "BOTH ALGO", "descr", ActionType.DETECTION,
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of()),
                true, true);
        addComponent(bothAlgo);

        String bothTaskName = createSingleActionTask("BOTH", bothAlgo.getName());


        var streamAlgo = new Algorithm(
                "STREAM ALGO", "descr", ActionType.DETECTION,
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of()),
                false, true);
        addComponent(streamAlgo);

        var streamTaskName = createSingleActionTask("STREAM", streamAlgo.getName());


        var pipeline = new Pipeline("TEST PIPELINE", "descr",
                                         List.of(streamTaskName, bothTaskName));
        addComponent(pipeline);

        verifyStreamingPipelineRunnable(pipeline.getName());
    }


    @Test
    public void throwsExceptionWhenInvalidStates() {
        var providesAlgo = new Algorithm(
                "PROVIDES ALGO", "descr", ActionType.DETECTION,
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of("STATE_ONE", "STATE_THREE"), List.of()),
                true, true);
        addComponent(providesAlgo);
        var providesTask = createSingleActionTask("PROVIDES", providesAlgo.getName());

        Algorithm requiresAlgo = new Algorithm(
                "REQUIRES ALGO", "descr", ActionType.DETECTION,
                new Algorithm.Requires(List.of("STATE_ONE", "STATE_TWO")),
                new Algorithm.Provides(List.of(), List.of()),
                true, true);
        addComponent(requiresAlgo);
        var requiresTask = createSingleActionTask("REQUIRES", requiresAlgo.getName());

        var pipeline = new Pipeline("INVALID STATES PIPELINE", "Adf",
                                         List.of(providesTask, requiresTask));
        addComponent(pipeline);

        try {
            verifyBatchPipelineRunnable(pipeline.getName());
            fail("Expected exception");
        }
        catch (InvalidPipelineException e) {
            assertTrue(e.getMessage().contains("The states for \"" + requiresTask + "\" are not satisfied"));
        }
    }

    @Test
    public void throwsWhenActionReferencesNonExistentAlgoProp() {
        var property = new Algorithm.Property("MY PROPERTY", "descr", ValueType.INT,
                                              "1", null);

        var algorithm = new Algorithm(
                "ALGO", "descr", ActionType.DETECTION,
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of(property)),
                true, false);
        addComponent(algorithm);

        var action = new Action("ACTION", "descr", algorithm.getName(),
                                   List.of(new Action.Property("INVALID", "INVALID")));
        addComponent(action);

        var task = new Task("TASK", "descr", List.of(action.getName()));
        addComponent(task);

        var pipeline = new Pipeline("PIPELINE", "descr", List.of(task.getName()));
        addComponent(pipeline);

        try {
            verifyBatchPipelineRunnable(pipeline.getName());
            fail("Expected exception");
        }
        catch (InvalidPipelineException e) {
            assertEquals(
                    "The \"INVALID\" property from the \"ACTION\" action does not exist in \"ALGO\" algorithm.",
                    e.getMessage());
        }
    }

    @Test
    public void throwsWhenActionHasInvalidPropertyValue() {
        var property = new Algorithm.Property("MY PROPERTY", "asdf", ValueType.INT,
                                              "1", null);

        var algorithm = new Algorithm(
                "ALGO", "descr", ActionType.DETECTION,
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of(property)),
                true, false);
        addComponent(algorithm);

        var action = new Action("ACTION", "descr", algorithm.getName(),
                                   List.of(new Action.Property("MY PROPERTY", "INVALID")));
        addComponent(action);

        var task = new Task("TASK", "descr", List.of(action.getName()));
        addComponent(task);

        var pipeline = new Pipeline("PIPELINE", "descr", List.of(task.getName()));
        addComponent(pipeline);

        try {
            verifyBatchPipelineRunnable(pipeline.getName());
            fail("Expected exception");
        }
        catch (InvalidPipelineException e) {
            assertEquals(
                    "The \"MY PROPERTY\" property from the \"ACTION\" action has a value of \"INVALID\", which is not a valid \"INT\".",
                    e.getMessage());
        }
    }

    @Test
    public void throwsWhenTaskHasDifferentActionTypes() {
        var detectionAlgo = new Algorithm(
                "DETECTION ALGO", "descr", ActionType.DETECTION,
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of()),
                true, false);
        addComponent(detectionAlgo);

        var detectionAction = new Action("DETECTION ACTION", "descr", detectionAlgo.getName(),
                                            List.of());
        addComponent(detectionAction);


        var markupAlgo = new Algorithm(
                "MARKUP ALGO", "descr", ActionType.MARKUP,
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of()),
                true, false);
        addComponent(markupAlgo);

        var markupAction = new Action("MARKUP ACTION", "descr", markupAlgo.getName(),
                                         List.of());
        addComponent(markupAction);

        var task = new Task("DETECTION AND MARKUP TASK", "descr",
                             List.of(detectionAction.getName(), markupAction.getName()));
        addComponent(task);

        var pipeline = new Pipeline("DETECTION AND MARKUP PIPELINE", "asdf",
                                         List.of(task.getName()));
        addComponent(pipeline);

        try {
            verifyBatchPipelineRunnable(pipeline.getName());
            fail("Expected exception");
        }
        catch (InvalidPipelineException e) {
            assertTrue(e.getMessage().contains("tasks cannot contain actions which have different ActionTypes"));
        }
    }


    @Test
    public void throwsWhenMultiActionTaskIsNotFinalTask() {
        var algorithm = TestDescriptorFactory.getReferencedAlgorithm();
        addComponent(algorithm);

        var action = new Action("TEST ACTION", "descr", algorithm.getName(), List.of());
        addComponent(action);

        var multiActionTask = new Task("MULTI ACTION TASK", "descr",
                                       List.of(action.getName(), action.getName()));
        addComponent(multiActionTask);

        var singleActionTask = new Task("SINGLE ACTION TASK", "descr",
                                         List.of(action.getName()));
        addComponent(singleActionTask);

        var pipeline = new Pipeline("TEST PIPELINE", "descr",
                                    List.of(multiActionTask.getName(), singleActionTask.getName()));
        addComponent(pipeline);

        try {
            verifyBatchPipelineRunnable(pipeline.getName());
        }
        catch (InvalidPipelineException e) {
            assertEquals("TEST PIPELINE: No tasks may follow the multi-detection task of MULTI ACTION TASK.",
                         e.getMessage());
        }
    }


    @Test
    public void throwsWhenMarkupTaskInvalid() {
        var detectionAlgo = new Algorithm(
                "DETECTION ALGO", "descr", ActionType.DETECTION,
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of()),
                true, true);
        addComponent(detectionAlgo);
        var detectionTaskName = createSingleActionTask("DETECTION", detectionAlgo.getName());


        var markupAlgo = new Algorithm(
                "MARKUP ALGO", "descr", ActionType.MARKUP,
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of()),
                true, true);
        addComponent(markupAlgo);
        var markupAction = new Action("MARKUP ACTION", "desc", markupAlgo.getName(),
                                         List.of());
        addComponent(markupAction);
        var markupTask = new Task("MARKUP TASK", "desc",
                                   List.of(markupAction.getName()));
        addComponent(markupTask);

        var pipelineWithTaskAfterMarkup = new Pipeline(
                "TASK AFTER MARKUP PIPELINE", "descr" ,
                List.of(detectionTaskName, markupTask.getName(), detectionTaskName));
        addComponent(pipelineWithTaskAfterMarkup);


        try {
            verifyBatchPipelineRunnable(pipelineWithTaskAfterMarkup.getName());
            fail("Expected exception");
        }
        catch (InvalidPipelineException e) {
            assertEquals("TASK AFTER MARKUP PIPELINE: No tasks may follow a markup task of MARKUP TASK.",
                         e.getMessage());
        }



        var pipelineWithMarkupFirst = new Pipeline("MARKUP FIRST PIPELINE", "desc",
                                                        List.of(markupTask.getName()));
        addComponent(pipelineWithMarkupFirst);

        try {
            verifyBatchPipelineRunnable(pipelineWithMarkupFirst.getName());
            fail("Expected exception");
        }
        catch (InvalidPipelineException e) {
            assertEquals(
                    "MARKUP FIRST PIPELINE: A markup task may not be the first task in a pipeline.",
                    e.getMessage());
        }


        var multiMarkupTask = new Task("PARALLEL MULTI MARKUP TASK", "desc",
                                        List.of(markupAction.getName(), markupAction.getName()));
        addComponent(multiMarkupTask);

        var parallelMultiMarkupPipeline = new Pipeline("PARALLEL MULTI MARKUP PIPELINE", "desc",
                                                          List.of(detectionTaskName, multiMarkupTask.getName()));
        addComponent(parallelMultiMarkupPipeline);

        try {
            verifyBatchPipelineRunnable(parallelMultiMarkupPipeline.getName());
            fail("Expected exception");
        }
        catch (InvalidPipelineException e) {
            assertEquals("PARALLEL MULTI MARKUP TASK: A markup task may only contain one action.",
                         e.getMessage());
        }


        var sequentialMultiMarkupPipeline = new Pipeline(
                "SEQUENTIAL MULTI MARKUP TASK PIPELINE", "descr",
                List.of(markupTask.getName(), markupTask.getName()));
        addComponent(sequentialMultiMarkupPipeline);

        try {
            verifyBatchPipelineRunnable(sequentialMultiMarkupPipeline.getName());
        }
        catch (InvalidPipelineException e) {
            assertEquals(
                    "SEQUENTIAL MULTI MARKUP TASK PIPELINE: No tasks may follow a markup task of MARKUP TASK.",
                    e.getMessage());
        }

    }


    @Test
    public void throwsWhenTryingToAddDifferentPipelineWithSameName() {
        var p1 = new Pipeline("pipeline", "description", List.of("task1"));
        var p2 = new Pipeline("pipeline", "different description", List.of("task1"));
        try {
            _pipelineValidator.validateOnAdd(p2, Map.of(p1.getName(), p1));
            fail("Expected exception");
        }
        catch (InvalidPipelineException e) {
            assertTrue(e.getMessage().contains("same name already exists"));
        }
    }


    @Test
    public void canHandleAddingSameExactPipelineTwice() {
        var p1 = new Pipeline("pipeline", "description", List.of("task1"));
        var p2 = new Pipeline("pipeline", "description", List.of("task1"));

        _pipelineValidator.validateOnAdd(p2, Map.of(p1.getName(), p1));
    }



    @Test
    public void canValidatePipeline() {
        var pipeline = new Pipeline(null, "", List.of());

        assertValidationErrors(
                pipeline,
                createViolationMessage("name", pipeline.getName(), "may not be empty"),
                createViolationMessage("description", pipeline.getDescription(), "may not be empty"),
                createViolationMessage("tasks", "[]", "may not be empty"));

        pipeline = new Pipeline("PIPELINE", "desc.desc", List.of("TASK", ""));
        assertValidationErrors(pipeline,
                               createViolationMessage("tasks[1]", pipeline.getTasks().get(1), "may not be empty"));

        pipeline = new Pipeline("PIPELINE", "desc.desc", List.of("TASK", "ASDF"));
        _pipelineValidator.validateOnAdd(pipeline, Map.of());
    }


    @Test
    public void canValidateTask() {
        var task = new Task("", "", List.of());

        assertValidationErrors(
                task,
                createViolationMessage("name", task.getName(), "may not be empty"),
                createViolationMessage("description", task.getDescription(), "may not be empty"),
                createViolationMessage("actions", "[]", "may not be empty"));

        task = new Task("TASK", "desc/desc", List.of("ACTION", ""));
        assertValidationErrors(task,
                               createViolationMessage("actions[1]", task.getActions().get(1), "may not be empty"));

        task = new Task("TASK", "desc/desc", List.of("ACTION", "ASDF"));
        _pipelineValidator.validateOnAdd(task, Map.of());
    }


    @Test
    public void canValidateAction() {
        var property1 = new Action.Property("PROP1", "Value1");
        var property2 = new Action.Property("", "");
        var property3 = new Action.Property("PROP1", null);


        var action = new Action(null, null, "", List.of(property1, property2, property3));
        assertValidationErrors(
                action,
                createViolationMessage("name", action.getName(), "may not be empty"),
                createViolationMessage("description", action.getDescription(), "may not be empty"),
                createViolationMessage("algorithm", action.getAlgorithm(), "may not be empty"),
                createViolationMessage("properties[1].name", property2.getName(), "may not be empty"),
                createViolationMessage("properties[2].value", property3.getValue(), "may not be null"));

        action = new Action("ACTION", "descr", "ALGO", List.of(property1));
        _pipelineValidator.validateOnAdd(action, Map.of());
    }


    @Test
    public void canValidateAlgorithm() {
        var algorithm = new Algorithm("", "", null,
                                            null, null,
                                            false, false);
        assertValidationErrors(
                algorithm,
                createViolationMessage("name", algorithm.getName(), "may not be empty"),
                createViolationMessage("description", algorithm.getDescription(), "may not be empty"),
                createViolationMessage("actionType", null, "may not be null"),
                createViolationMessage("providesCollection", null, "may not be null"),
                createViolationMessage("requiresCollection", null, "may not be null"),
                "must support batch processing, stream processing, or both");

        var property1 = new Algorithm.Property(
                "PROP1", "descr", ValueType.STRING, "default", null);

        var property2 = new Algorithm.Property(
                "", null, null, "default", "property.key");

        algorithm = new Algorithm(
                "ALGO", "descr", ActionType.DETECTION,
                new Algorithm.Requires(List.of("STATE1", "")),
                new Algorithm.Provides(List.of("", "STATE1"), List.of(property1, property2)),
                true, true);

        assertValidationErrors(
            algorithm,
            createViolationMessage("requiresCollection.states[1]", "", "may not be empty"),
            createViolationMessage("providesCollection.states[0]", "", "may not be empty"),
            createViolationMessage("providesCollection.properties[1].defaultValue", property2.toString(),
                                   "must provide either a defaultValue or propertiesKey, but not both."),
            createViolationMessage("providesCollection.properties[1].propertiesKey", property2.toString(),
                                   "must provide either a defaultValue or propertiesKey, but not both."),
            createViolationMessage("providesCollection.properties[1].description", null, "may not be empty"),
            createViolationMessage("providesCollection.properties[1].name", "", "may not be empty"),
            createViolationMessage("providesCollection.properties[1].type", null, "may not be null"));


        algorithm = new Algorithm(
                "ALGO", "descr", ActionType.DETECTION,
                new Algorithm.Requires(List.of("STATE1", "STATE2")),
                new Algorithm.Provides(List.of("STATE1", "STATE2"), List.of(property1)),
                true, true);
        _pipelineValidator.validateOnAdd(algorithm, Map.of());
    }


    private void assertValidationErrors(PipelineComponent pipelineComponent, String... messages) {
        assertTrue("No messages passed in to check", messages.length > 0);
        try {
            _pipelineValidator.validateOnAdd(pipelineComponent, Map.of());
            fail("Expected exception");
        }
        catch (PipelineValidationException e) {
            String errorMessage = e.getMessage();
            for (String expectedMsg : messages) {
                assertThat(errorMessage, containsString(expectedMsg));
            }

            long lineCount = errorMessage.chars().filter(ch -> ch == '\n').count();

            assertEquals("Did not contain the expected number of error messages.",
                         lineCount, messages.length);
        }
    }


    private static String createViolationMessage(String field, String value, String errorMsg) {
        return String.format("%s=\"%s\": %s", field, value, errorMsg);
    }


    private static String createInvalidNameMessage(String field, String value) {
        return createViolationMessage(
                field, value,
                "Names cannot be empty and cannot contain lowercase letters, dots(.), slashes(/), or backslashes(\\).");
    }


    private String createSingleActionTask(String namePrefix, String algorithmName) {
        var action = new Action(namePrefix + " ACTION", "adf", algorithmName, List.of());
        addComponent(action);

        var task = new Task(namePrefix + " TASK", "asfd", List.of(action.getName()));
        addComponent(task);
        return task.getName();
    }
}