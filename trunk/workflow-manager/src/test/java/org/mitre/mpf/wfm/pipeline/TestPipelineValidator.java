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
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.service.component.JsonComponentDescriptor;
import org.mitre.mpf.wfm.service.component.TestDescriptorFactory;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.*;

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
        LocalValidatorFactoryBean springValidator = new LocalValidatorFactoryBean();
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
        JsonComponentDescriptor descriptor = TestDescriptorFactory.getWithCustomPipeline();

        addDescriptorPipelines(descriptor);
        addComponent(TestDescriptorFactory.getReferencedAlgorithm());

        verifyBatchPipelineRunnable(descriptor.getPipelines().get(0).getName());
    }


    @Test
    public void throwsExceptionWhenPipelineIsMissing() {
        String pipelineName = "TEST PIPELINE";
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
        String invalidAlgorithmName = "INVALID ALGORITHM";
        String invalidActionName = "INVALID ACTION";
        String invalidTaskName = "INVALID TASK";

        Algorithm algorithm = TestDescriptorFactory.getReferencedAlgorithm();

        Action action = new Action("TEST ACTION", "asdf", algorithm.getName(),
                                   Collections.emptyList());
        Action missingAlgoAction = new Action("MISSING ALGO ACTION", "asdf", invalidAlgorithmName,
                                              Collections.emptyList());

        Task validTask = new Task("TEST TASK", "asdf",
                                  Arrays.asList(invalidActionName, action.getName(), missingAlgoAction.getName()));

        Pipeline pipeline = new Pipeline("TEST PIPELINE", "asdf",
                                         Arrays.asList(validTask.getName(), invalidTaskName));
        addComponents(Arrays.asList(algorithm, action, missingAlgoAction, validTask, pipeline));

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
        Algorithm batchAlgo = new Algorithm(
                "BATCH ALGO", "asdf", ActionType.DETECTION,
                new Algorithm.Requires(Collections.emptyList()),
                new Algorithm.Provides(Collections.emptyList(), Collections.emptyList()),
                true, false);
        addComponent(batchAlgo);

        String batchTaskName = createSingleActionTask("BATCH", batchAlgo.getName());


        Algorithm streamAlgo = new Algorithm(
                "STREAM ALGO", "asdf", ActionType.DETECTION,
                new Algorithm.Requires(Collections.emptyList()),
                new Algorithm.Provides(Collections.emptyList(), Collections.emptyList()),
                false, true);
        addComponent(streamAlgo);

        String streamTaskName = createSingleActionTask("STREAM", streamAlgo.getName());


        Pipeline pipeline = new Pipeline("TEST PIPELINE", "asdf",
                                         Arrays.asList(batchTaskName, streamTaskName));
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
        Algorithm batchAlgo = new Algorithm(
                "BATCH ALGO", "descr", ActionType.DETECTION,
                new Algorithm.Requires(Collections.emptyList()),
                new Algorithm.Provides(Collections.emptyList(), Collections.emptyList()),
                true, false);
        addComponent(batchAlgo);

        String batchTaskName = createSingleActionTask("BATCH", batchAlgo.getName());


        Algorithm bothAlgo = new Algorithm(
                "BOTH ALGO", "descr", ActionType.DETECTION,
                new Algorithm.Requires(Collections.emptyList()),
                new Algorithm.Provides(Collections.emptyList(), Collections.emptyList()),
                true, true);
        addComponent(bothAlgo);

        String bothTaskName = createSingleActionTask("BOTH", bothAlgo.getName());

        Pipeline pipeline = new Pipeline("TEST PIPELINE", "descr",
                                         Arrays.asList(batchTaskName, bothTaskName));
        addComponent(pipeline);

        verifyBatchPipelineRunnable(pipeline.getName());
    }


    @Test
    public void canHandleWhenOneAlgoSupportsStreamingAndOtherSupportsBoth() {
        Algorithm bothAlgo = new Algorithm(
                "BOTH ALGO", "descr", ActionType.DETECTION,
                new Algorithm.Requires(Collections.emptyList()),
                new Algorithm.Provides(Collections.emptyList(), Collections.emptyList()),
                true, true);
        addComponent(bothAlgo);

        String bothTaskName = createSingleActionTask("BOTH", bothAlgo.getName());


        Algorithm streamAlgo = new Algorithm(
                "STREAM ALGO", "descr", ActionType.DETECTION,
                new Algorithm.Requires(Collections.emptyList()),
                new Algorithm.Provides(Collections.emptyList(), Collections.emptyList()),
                false, true);
        addComponent(streamAlgo);

        String streamTaskName = createSingleActionTask("STREAM", streamAlgo.getName());



        Pipeline pipeline = new Pipeline("TEST PIPELINE", "descr",
                                         Arrays.asList(streamTaskName, bothTaskName));
        addComponent(pipeline);

        verifyStreamingPipelineRunnable(pipeline.getName());
    }


    @Test
    public void throwsExceptionWhenInvalidStates() {
        Algorithm providesAlgo = new Algorithm(
                "PROVIDES ALGO", "descr", ActionType.DETECTION,
                new Algorithm.Requires(Collections.emptyList()),
                new Algorithm.Provides(Arrays.asList("STATE_ONE", "STATE_THREE"), Collections.emptyList()),
                true, true);
        addComponent(providesAlgo);
        String providesTask = createSingleActionTask("PROVIDES", providesAlgo.getName());

        Algorithm requiresAlgo = new Algorithm(
                "REQUIRES ALGO", "descr", ActionType.DETECTION,
                new Algorithm.Requires(Arrays.asList("STATE_ONE", "STATE_TWO")),
                new Algorithm.Provides(Collections.emptyList(), Collections.emptyList()),
                true, true);
        addComponent(requiresAlgo);
        String requiresTask = createSingleActionTask("REQUIRES", requiresAlgo.getName());

        Pipeline pipeline = new Pipeline("INVALID STATES PIPELINE", "Adf",
                                         Arrays.asList(providesTask, requiresTask));
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
        Algorithm.Property property = new Algorithm.Property("MY PROPERTY", "descr", ValueType.INT,
                                                             "1", null);

        Algorithm algorithm = new Algorithm(
                "ALGO", "descr", ActionType.DETECTION,
                new Algorithm.Requires(Collections.emptyList()),
                new Algorithm.Provides(Collections.emptyList(), Collections.singleton(property)),
                true, false);
        addComponent(algorithm);

        Action action = new Action("ACTION", "descr", algorithm.getName(),
                                   Collections.singleton(new Action.Property("INVALID", "INVALID")));
        addComponent(action);

        Task task = new Task("TASK", "descr", Collections.singleton(action.getName()));
        addComponent(task);

        Pipeline pipeline = new Pipeline("PIPELINE", "descr", Collections.singleton(task.getName()));
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
        Algorithm.Property property = new Algorithm.Property("MY PROPERTY", "asdf", ValueType.INT,
                                                             "1", null);

        Algorithm algorithm = new Algorithm(
                "ALGO", "descr", ActionType.DETECTION,
                new Algorithm.Requires(Collections.emptyList()),
                new Algorithm.Provides(Collections.emptyList(), Collections.singleton(property)),
                true, false);
        addComponent(algorithm);

        Action action = new Action("ACTION", "descr", algorithm.getName(),
                                   Collections.singleton(new Action.Property("MY PROPERTY", "INVALID")));
        addComponent(action);

        Task task = new Task("TASK", "descr", Collections.singleton(action.getName()));
        addComponent(task);

        Pipeline pipeline = new Pipeline("PIPELINE", "descr", Collections.singleton(task.getName()));
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
        Algorithm detectionAlgo = new Algorithm(
                "DETECTION ALGO", "descr", ActionType.DETECTION,
                new Algorithm.Requires(Collections.emptyList()),
                new Algorithm.Provides(Collections.emptyList(), Collections.emptyList()),
                true, false);
        addComponent(detectionAlgo);

        Action detectionAction = new Action("DETECTION ACTION", "descr", detectionAlgo.getName(),
                                            Collections.emptyList());
        addComponent(detectionAction);


        Algorithm markupAlgo = new Algorithm(
                "MARKUP ALGO", "descr", ActionType.MARKUP,
                new Algorithm.Requires(Collections.emptyList()),
                new Algorithm.Provides(Collections.emptyList(), Collections.emptyList()),
                true, false);
        addComponent(markupAlgo);

        Action markupAction = new Action("MARKUP ACTION", "descr", markupAlgo.getName(),
                                         Collections.emptyList());
        addComponent(markupAction);

        Task task = new Task("DETECTION AND MARKUP TASK", "descr",
                             Arrays.asList(detectionAction.getName(), markupAction.getName()));
        addComponent(task);

        Pipeline pipeline = new Pipeline("DETECTION AND MARKUP PIPELINE", "asdf",
                                         Collections.singleton(task.getName()));
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
        Algorithm algorithm = TestDescriptorFactory.getReferencedAlgorithm();
        addComponent(algorithm);

        Action action = new Action("TEST ACTION", "descr", algorithm.getName(),
                                   Collections.emptyList());
        addComponent(action);

        Task multiActionTask = new Task("MULTI ACTION TASK", "descr",
                                        Arrays.asList(action.getName(), action.getName()));
        addComponent(multiActionTask);

        Task singleActionTask = new Task("SINGLE ACTION TASK", "descr",
                                         Collections.singleton(action.getName()));
        addComponent(singleActionTask);

        Pipeline pipeline = new Pipeline("TEST PIPELINE", "descr",
                                         Arrays.asList(multiActionTask.getName(), singleActionTask.getName()));
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
        Algorithm detectionAlgo = new Algorithm(
                "DETECTION ALGO", "descr", ActionType.DETECTION,
                new Algorithm.Requires(Collections.emptyList()),
                new Algorithm.Provides(Collections.emptyList(), Collections.emptyList()),
                true, true);
        addComponent(detectionAlgo);
        String detectionTaskName = createSingleActionTask("DETECTION", detectionAlgo.getName());


        Algorithm markupAlgo = new Algorithm(
                "MARKUP ALGO", "descr", ActionType.MARKUP,
                new Algorithm.Requires(Collections.emptyList()),
                new Algorithm.Provides(Collections.emptyList(), Collections.emptyList()),
                true, true);
        addComponent(markupAlgo);
        Action markupAction = new Action("MARKUP ACTION", "desc", markupAlgo.getName(),
                                         Collections.emptyList());
        addComponent(markupAction);
        Task markupTask = new Task("MARKUP TASK", "desc",
                                   Collections.singleton(markupAction.getName()));
        addComponent(markupTask);

        Pipeline pipelineWithTaskAfterMarkup = new Pipeline(
                "TASK AFTER MARKUP PIPELINE", "descr" ,
                Arrays.asList(detectionTaskName, markupTask.getName(), detectionTaskName));
        addComponent(pipelineWithTaskAfterMarkup);


        try {
            verifyBatchPipelineRunnable(pipelineWithTaskAfterMarkup.getName());
            fail("Expected exception");
        }
        catch (InvalidPipelineException e) {
            assertEquals("TASK AFTER MARKUP PIPELINE: No tasks may follow a markup task of MARKUP TASK.",
                         e.getMessage());
        }



        Pipeline pipelineWithMarkupFirst = new Pipeline("MARKUP FIRST PIPELINE", "desc",
                                                        Collections.singleton(markupTask.getName()));
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


        Task multiMarkupTask = new Task("PARALLEL MULTI MARKUP TASK", "desc",
                                        Arrays.asList(markupAction.getName(), markupAction.getName()));
        addComponent(multiMarkupTask);

        Pipeline parallelMultiMarkupPipeline = new Pipeline("PARALLEL MULTI MARKUP PIPELINE", "desc",
                                                          Arrays.asList(detectionTaskName, multiMarkupTask.getName()));
        addComponent(parallelMultiMarkupPipeline);

        try {
            verifyBatchPipelineRunnable(parallelMultiMarkupPipeline.getName());
            fail("Expected exception");
        }
        catch (InvalidPipelineException e) {
            assertEquals("PARALLEL MULTI MARKUP TASK: A markup task may only contain one action.",
                         e.getMessage());
        }


        Pipeline sequentialMultiMarkupPipeline = new Pipeline(
                "SEQUENTIAL MULTI MARKUP TASK PIPELINE", "descr",
                Arrays.asList(markupTask.getName(), markupTask.getName()));
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
        Pipeline p1 = new Pipeline("pipeline", "description",
                                   Collections.singletonList("task1"));
        Pipeline p2 = new Pipeline("pipeline", "different description",
                                   Collections.singletonList("task1"));
        try {
            _pipelineValidator.validateOnAdd(p2, Collections.singletonMap(p1.getName(), p1));
            fail("Expected exception");
        }
        catch (InvalidPipelineException e) {
            assertTrue(e.getMessage().contains("same name already exists"));
        }
    }


    @Test
    public void canHandleAddingSameExactPipelineTwice() {
        Pipeline p1 = new Pipeline("pipeline", "description",
                                   Collections.singletonList("task1"));
        Pipeline p2 = new Pipeline("pipeline", "description",
                                   Collections.singletonList("task1"));

        _pipelineValidator.validateOnAdd(p2, Collections.singletonMap(p1.getName(), p1));
    }


    @Test
    public void canValidatePipelineComponentNames() {
        Action action = new Action("", "descr", "algo", Collections.emptyList());
        assertValidationErrors(action, createInvalidNameMessage("name", ""));

        action = new Action("ASDF/ASDF", "descr", "algo", Collections.emptyList());
        assertValidationErrors(action, createInvalidNameMessage("name", "ASDF/ASDF"));

        action = new Action("TEST", "descr", "BAD.ALGO", Collections.emptyList());
        assertValidationErrors(action, createInvalidNameMessage("algorithm", "BAD.ALGO"));

        // Test invalid name in list field
        Task task = new Task("TEST", "descr", Arrays.asList("OK", "BAD\\ACTION"));
        assertValidationErrors(task, createInvalidNameMessage("actions[1]", "BAD\\ACTION"));
    }


    @Test
    public void canValidatePipeline() {
        Pipeline pipeline = new Pipeline("bad.name", "", Collections.emptyList());

        assertValidationErrors(
                pipeline,
                createInvalidNameMessage("name", pipeline.getName()),
                createViolationMessage("description", pipeline.getDescription(), "may not be empty"),
                createViolationMessage("tasks", "[]", "may not be empty"));

        pipeline = new Pipeline("PIPELINE", "desc.desc", Arrays.asList("TASK", "ASDF.ASDF"));
        assertValidationErrors(pipeline,
                               createInvalidNameMessage("tasks[1]", pipeline.getTasks().get(1)));

        pipeline = new Pipeline("PIPELINE", "desc.desc", Arrays.asList("TASK", "ASDF"));
        _pipelineValidator.validateOnAdd(pipeline, Collections.emptyMap());
    }


    @Test
    public void canValidateTask() {
        Task task = new Task("bad/name", "", Collections.emptyList());

        assertValidationErrors(
                task,
                createInvalidNameMessage("name", task.getName()),
                createViolationMessage("description", task.getDescription(), "may not be empty"),
                createViolationMessage("actions", "[]", "may not be empty"));

        task = new Task("TASK", "desc/desc", Arrays.asList("ACTION", "ASDF\\ASDF"));
        assertValidationErrors(task,
                               createInvalidNameMessage("actions[1]", task.getActions().get(1)));

        task = new Task("TASK", "desc/desc", Arrays.asList("ACTION", "ASDF"));
        _pipelineValidator.validateOnAdd(task, Collections.emptyMap());
    }


    @Test
    public void canValidateAction() {
        Action.Property property1 = new Action.Property("PROP1", "Value1");
        Action.Property property2 = new Action.Property("", "");
        Action.Property property3 = new Action.Property("PROP1", null);


        Action action = new Action("BAD\\NAME", null, "bad/algo",
                                   Arrays.asList(property1, property2, property3));
        assertValidationErrors(
                action,
                createInvalidNameMessage("name", action.getName()),
                createViolationMessage("description", action.getDescription(), "may not be empty"),
                createInvalidNameMessage("algorithm", action.getAlgorithm()),
                createViolationMessage("properties[1].name", property2.getName(), "may not be empty"),
                createViolationMessage("properties[2].value", property3.getValue(), "may not be null"));

        action = new Action("ACTION", "descr", "ALGO", Collections.singleton(property1));
        _pipelineValidator.validateOnAdd(action, Collections.emptyMap());
    }


    @Test
    public void canValidateAlgorithm() {
        Algorithm algorithm = new Algorithm("a\\/.a", "", null,
                                            null, null,
                                            false, false);
        assertValidationErrors(
                algorithm,
                createInvalidNameMessage("name", algorithm.getName()),
                createViolationMessage("description", algorithm.getDescription(), "may not be empty"),
                createViolationMessage("actionType", null, "may not be null"),
                createViolationMessage("providesCollection", null, "may not be null"),
                createViolationMessage("requiresCollection", null, "may not be null"),
                "must support batch processing, stream processing, or both");

        Algorithm.Property property1 = new Algorithm.Property(
                "PROP1", "descr", ValueType.STRING, "default", null);

        Algorithm.Property property2 = new Algorithm.Property(
                "", null, null, "default", "property.key");

        algorithm = new Algorithm(
                "ALGO", "descr", ActionType.DETECTION,
                new Algorithm.Requires(Arrays.asList("STATE1", "")),
                new Algorithm.Provides(Arrays.asList("", "STATE1"), Arrays.asList(property1, property2)),
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
                new Algorithm.Requires(Arrays.asList("STATE1", "STATE2")),
                new Algorithm.Provides(Arrays.asList("STATE1", "STATE2"), Collections.singleton(property1)),
                true, true);
        _pipelineValidator.validateOnAdd(algorithm, Collections.emptyMap());
    }


    private void assertValidationErrors(PipelineComponent pipelineComponent, String... messages) {
        assertTrue("No messages passed in to check", messages.length > 0);
        try {
            _pipelineValidator.validateOnAdd(pipelineComponent, Collections.emptyMap());
            fail("Expected exception");
        }
        catch (PipelineValidationException e) {
            String errorMessage = e.getMessage();
            for (String expectedMsg : messages) {
                assertThat(errorMessage, containsString(expectedMsg));
            }

            long lineCount = errorMessage.chars().filter(ch -> ch == '\n').count();
            // First line is a message that doesn't contain an error.
            long validationErrorCount = lineCount - 1;

            assertEquals("Did not contain the expected number of error messages.",
                         validationErrorCount, messages.length);
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
        Action action = new Action(namePrefix + " ACTION", "adf", algorithmName,
                                   Collections.emptyList());
        addComponent(action);

        Task task = new Task(namePrefix + " TASK", "asfd", Collections.singleton(action.getName()));
        addComponent(task);
        return task.getName();
    }
}