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


package org.mitre.mpf.wfm.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.rest.api.pipelines.*;
import org.mitre.mpf.rest.api.pipelines.transients.TransientPipelineDefinition;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.service.WorkflowProperty;
import org.mitre.mpf.wfm.service.WorkflowPropertyService;
import org.mitre.mpf.wfm.service.component.JsonComponentDescriptor;
import org.mitre.mpf.wfm.service.component.TestDescriptorFactory;
import org.mitre.mpf.wfm.service.pipeline.InvalidPipelineException;
import org.mitre.mpf.wfm.service.pipeline.PipelinePartLookup;
import org.mitre.mpf.wfm.service.pipeline.PipelineValidator;
import org.mitre.mpf.wfm.service.pipeline.TransientPipelinePartLookup;
import org.mitre.mpf.wfm.util.ObjectMapperFactory;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestPipelineValidator {

    private PipelineValidator _pipelineValidator;

    private final WorkflowPropertyService _mockWorkflowPropertyService = mock(WorkflowPropertyService.class);

    private final ObjectMapper _objectMapper = ObjectMapperFactory.customObjectMapper();

    private final Map<String, Pipeline> _pipelines = new HashMap<>();
    private final Map<String, Task> _tasks = new HashMap<>();
    private final Map<String, Action> _actions = new HashMap<>();
    private final Map<String, Algorithm> _algorithms = new HashMap<>();

    private final PipelinePartLookup _pipelinePartLookup = createPartLookup();

    @Before
    public void init() {
        var springValidator = new LocalValidatorFactoryBean();
        springValidator.setMessageInterpolator(new ParameterMessageInterpolator());
        springValidator.afterPropertiesSet();
        _pipelineValidator = new PipelineValidator(springValidator, _mockWorkflowPropertyService);
    }

    private PipelinePartLookup createPartLookup() {
        return new PipelinePartLookup() {
            @Override
            public Pipeline getPipeline(String name) {
                return _pipelines.get(name);
            }

            @Override
            public Task getTask(String name) {
                return _tasks.get(name);
            }

            @Override
            public Action getAction(String name) {
                return _actions.get(name);
            }

            @Override
            public Algorithm getAlgorithm(String name) {
                return _algorithms.get(name);
            }
        };
    }

    private void verifyBatchPipelineRunnable(String pipelineName) {
        _pipelineValidator.verifyBatchPipelineRunnable(pipelineName, _pipelinePartLookup);
    }

    private void verifyStreamingPipelineRunnable(String pipelineName) {
        _pipelineValidator.verifyStreamingPipelineRunnable(pipelineName, _pipelinePartLookup);
    }

    private <T extends PipelineElement> void addElement(T pipelineElement, Map<String, T> existing) {
        _pipelineValidator.validateOnAdd(pipelineElement, existing);
        existing.put(pipelineElement.name(), pipelineElement);
    }

    private void addElement(PipelineElement pipelineElement) {
        if (pipelineElement instanceof Pipeline) {
            addElement((Pipeline) pipelineElement, _pipelines);
            return;
        }
        if (pipelineElement instanceof Task) {
            addElement((Task) pipelineElement, _tasks);
            return;
        }
        if (pipelineElement instanceof Action) {
            addElement((Action) pipelineElement, _actions);
            return;
        }
        if (pipelineElement instanceof Algorithm) {
            addElement((Algorithm) pipelineElement, _algorithms);
        }
    }


    private void addElement(Iterable<? extends PipelineElement> pipelineElements) {
        for (var pipelineElement : pipelineElements) {
            addElement(pipelineElement);
        }
    }

    private void addDescriptorPipelines(JsonComponentDescriptor descriptor) {
        addElement(descriptor.pipelines());
        addElement(descriptor.tasks());
        addElement(descriptor.actions());
        addElement(descriptor.algorithm());
    }



    @Test
    public void testValidationHappyPath() {
        var descriptor = TestDescriptorFactory.getWithCustomPipeline();

        addDescriptorPipelines(descriptor);
        addElement(TestDescriptorFactory.getReferencedAlgorithm());

        verifyBatchPipelineRunnable(descriptor.pipelines().get(0).name());
    }


    @Test
    public void canValidatePipelineReferencingWorkflowProperties() {
        var algorithm = new Algorithm("ALGO", "descr", ActionType.DETECTION, "TEST",
                                      OptionalInt.empty(),
                                      new Algorithm.Requires(List.of()),
                                      new Algorithm.Provides(List.of(), List.of()),
                                      true, true);
        _algorithms.put(algorithm.name(), algorithm);

        var workflowPropName = "WORKFLOW_PROP_NAME";
        var action = new Action("ACTION", "descr", algorithm.name(),
                                List.of(new ActionProperty(workflowPropName, "WORKFLOW_PROP_VAL")));
        _actions.put(action.name(), action);

        var task = new Task("TASK", "descr", List.of(action.name()));
        _tasks.put(task.name(), task);

        var pipeline = new Pipeline("PIPELINE", "descr", List.of(task.name()));
        _pipelines.put(pipeline.name(), pipeline);

        when(_mockWorkflowPropertyService.getProperty(workflowPropName))
                .thenReturn(new WorkflowProperty(workflowPropName, "descr", ValueType.DOUBLE,
                                                 "default", null, List.of()));


        var ex = TestUtil.assertThrows(InvalidPipelineException.class,
                                       () -> verifyBatchPipelineRunnable(pipeline.name()));
        assertEquals(
                "The \"WORKFLOW_PROP_NAME\" property from the \"ACTION\" action has a value of \"WORKFLOW_PROP_VAL\", which is not a valid \"DOUBLE\".",
                ex.getMessage());


        var correctedAction = new Action(action.name(), action.description(), action.algorithm(),
                                         List.of(new ActionProperty(workflowPropName, "2.997e8")));
        _actions.put(correctedAction.name(), correctedAction);
        verifyBatchPipelineRunnable(pipeline.name());
    }


    @Test
    public void throwsExceptionWhenPipelineIsMissing() {
        var pipelineName = "TEST PIPELINE";
        var ex = TestUtil.assertThrows(InvalidPipelineException.class,
                                       () -> verifyBatchPipelineRunnable(pipelineName));
        assertTrue(ex.getMessage().contains(pipelineName));
    }


    @Test
    public void throwsExceptionWhenPartOfPipelineMissing() {
        var invalidAlgorithmName = "INVALID ALGORITHM";
        var invalidActionName = "INVALID ACTION";
        var invalidTaskName = "INVALID TASK";

        var algorithm = TestDescriptorFactory.getReferencedAlgorithm();

        var action = new Action("TEST ACTION", "asdf", algorithm.name(),
                                   List.of());
        var missingAlgoAction = new Action("MISSING ALGO ACTION", "asdf", invalidAlgorithmName,
                                           List.of());

        var validTask = new Task("TEST TASK", "asdf",
                                  List.of(invalidActionName, action.name(), missingAlgoAction.name()));

        var pipeline = new Pipeline("TEST PIPELINE", "asdf",
                                    List.of(validTask.name(), invalidTaskName));
        addElement(List.of(algorithm, action, missingAlgoAction, validTask, pipeline));


        var ex = TestUtil.assertThrows(InvalidPipelineException.class,
                                       () -> verifyBatchPipelineRunnable(pipeline.name()));

        var errorMsg = ex.getMessage();

        assertTrue(errorMsg.contains(invalidAlgorithmName));
        assertTrue(errorMsg.contains(invalidActionName));
        assertTrue(errorMsg.contains(invalidTaskName));

        assertFalse(errorMsg.contains(algorithm.name()));
        assertFalse(errorMsg.contains(action.name()));
        assertFalse(errorMsg.contains(missingAlgoAction.name()));
        assertFalse(errorMsg.contains(validTask.name()));
    }


    @Test
    public void throwsExceptionWhenNotAllAlgorithmsSupportSameProcessingType() {
        var batchAlgo = new Algorithm(
                "BATCH ALGO", "asdf", ActionType.DETECTION, "BATCH", OptionalInt.empty(),
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of()),
                true, false);
        addElement(batchAlgo);

        var batchTaskName = createSingleActionTask("BATCH", batchAlgo.name());

        var streamAlgo = new Algorithm(
                "STREAM ALGO", "asdf", ActionType.DETECTION, "STREAM", OptionalInt.empty(),
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of()),
                false, true);
        addElement(streamAlgo);

        var streamTaskName = createSingleActionTask("STREAM", streamAlgo.name());


        var pipeline = new Pipeline("TEST PIPELINE", "asdf",
                                    List.of(batchTaskName, streamTaskName));
        addElement(pipeline);


        {
            var ex = TestUtil.assertThrows(InvalidPipelineException.class,
                                           () -> verifyBatchPipelineRunnable(pipeline.name()));
            var errorMsg = ex.getMessage();
            assertTrue(errorMsg.contains("support batch processing"));
            assertTrue(errorMsg.contains("STREAM ALGO"));
        }
        {
            var ex = TestUtil.assertThrows(InvalidPipelineException.class,
                                       () -> verifyStreamingPipelineRunnable(pipeline.name()));
            var errorMsg = ex.getMessage();
            assertTrue(errorMsg.contains("support stream processing"));
            assertTrue(errorMsg.contains("BATCH ALGO"));
        }
    }


    @Test
    public void canHandleWhenOneAlgoSupportsBatchAndOtherSupportsBoth() {
        var batchAlgo = new Algorithm(
                "BATCH ALGO", "descr", ActionType.DETECTION, "BATCH", OptionalInt.empty(),
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of()),
                true, false);
        addElement(batchAlgo);

        var batchTaskName = createSingleActionTask("BATCH", batchAlgo.name());


        var bothAlgo = new Algorithm(
                "BOTH ALGO", "descr", ActionType.DETECTION, "BOTH", OptionalInt.empty(),
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of()),
                true, true);
        addElement(bothAlgo);

        var bothTaskName = createSingleActionTask("BOTH", bothAlgo.name());

        var pipeline = new Pipeline("TEST PIPELINE", "descr",
                                         List.of(batchTaskName, bothTaskName));
        addElement(pipeline);

        verifyBatchPipelineRunnable(pipeline.name());
    }


    @Test
    public void canHandleWhenOneAlgoSupportsStreamingAndOtherSupportsBoth() {
        var bothAlgo = new Algorithm(
                "BOTH ALGO", "descr", ActionType.DETECTION, "BOTH", OptionalInt.empty(),
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of()),
                true, true);
        addElement(bothAlgo);

        String bothTaskName = createSingleActionTask("BOTH", bothAlgo.name());


        var streamAlgo = new Algorithm(
                "STREAM ALGO", "descr", ActionType.DETECTION, "STREAM", OptionalInt.empty(),
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of()),
                false, true);
        addElement(streamAlgo);

        var streamTaskName = createSingleActionTask("STREAM", streamAlgo.name());


        var pipeline = new Pipeline("TEST PIPELINE", "descr",
                                         List.of(streamTaskName, bothTaskName));
        addElement(pipeline);

        verifyStreamingPipelineRunnable(pipeline.name());
    }


    @Test
    public void throwsExceptionWhenInvalidStates() {
        var providesAlgo = new Algorithm(
                "PROVIDES ALGO", "descr", ActionType.DETECTION, "PROVIDES", OptionalInt.empty(),
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of("STATE_ONE", "STATE_THREE"), List.of()),
                true, true);
        addElement(providesAlgo);
        var providesTask = createSingleActionTask("PROVIDES", providesAlgo.name());

        Algorithm requiresAlgo = new Algorithm(
                "REQUIRES ALGO", "descr", ActionType.DETECTION, "REQUIRES", OptionalInt.empty(),
                new Algorithm.Requires(List.of("STATE_ONE", "STATE_TWO")),
                new Algorithm.Provides(List.of(), List.of()),
                true, true);
        addElement(requiresAlgo);
        var requiresTask = createSingleActionTask("REQUIRES", requiresAlgo.name());

        var pipeline = new Pipeline("INVALID STATES PIPELINE", "Adf",
                                         List.of(providesTask, requiresTask));
        addElement(pipeline);

        var ex = TestUtil.assertThrows(InvalidPipelineException.class,
                                       () -> verifyBatchPipelineRunnable(pipeline.name()));
        assertTrue(ex.getMessage().contains("The states for \"" + requiresTask + "\" are not satisfied"));
    }

    @Test
    public void throwsWhenActionReferencesNonExistentAlgoProp() {
        var property = new AlgorithmProperty("MY PROPERTY", "descr", ValueType.INT,
                                             "1", null);

        var algorithm = new Algorithm(
                "ALGO", "descr", ActionType.DETECTION, "TEST", OptionalInt.empty(),
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of(property)),
                true, false);
        addElement(algorithm);

        var action = new Action("ACTION", "descr", algorithm.name(),
                                   List.of(new ActionProperty("INVALID", "INVALID")));
        addElement(action);

        var task = new Task("TASK", "descr", List.of(action.name()));
        addElement(task);

        var pipeline = new Pipeline("PIPELINE", "descr", List.of(task.name()));
        addElement(pipeline);

        var ex = TestUtil.assertThrows(InvalidPipelineException.class,
                              () -> verifyBatchPipelineRunnable(pipeline.name()));
        assertEquals(
                "The \"INVALID\" property from the \"ACTION\" action does not exist in \"ALGO\" algorithm and is not the name of a workflow property.",
                ex.getMessage());
    }

    @Test
    public void throwsWhenActionHasInvalidPropertyValue() {
        var property = new AlgorithmProperty("MY PROPERTY", "asdf", ValueType.INT,
                                              "1", null);

        var algorithm = new Algorithm(
                "ALGO", "descr", ActionType.DETECTION, "TEST", OptionalInt.empty(),
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of(property)),
                true, false);
        addElement(algorithm);

        var action = new Action("ACTION", "descr", algorithm.name(),
                                   List.of(new ActionProperty("MY PROPERTY", "INVALID")));
        addElement(action);

        var task = new Task("TASK", "descr", List.of(action.name()));
        addElement(task);

        var pipeline = new Pipeline("PIPELINE", "descr", List.of(task.name()));
        addElement(pipeline);


        var ex = TestUtil.assertThrows(InvalidPipelineException.class,
                                       () -> verifyBatchPipelineRunnable(pipeline.name()));
        assertEquals(
                "The \"MY PROPERTY\" property from the \"ACTION\" action has a value of \"INVALID\", which is not a valid \"INT\".",
                ex.getMessage());
    }

    @Test
    public void throwsWhenTaskHasDifferentActionTypes() {
        var detectionAlgo = new Algorithm(
                "DETECTION ALGO", "descr", ActionType.DETECTION, "TEST", OptionalInt.empty(),
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of()),
                true, false);
        addElement(detectionAlgo);

        var detectionAction = new Action("DETECTION ACTION", "descr", detectionAlgo.name(),
                                            List.of());
        addElement(detectionAction);


        var markupAlgo = new Algorithm(
                "MARKUP ALGO", "descr", ActionType.MARKUP, "MARKUP", OptionalInt.empty(),
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of()),
                true, false);
        addElement(markupAlgo);

        var markupAction = new Action("MARKUP ACTION", "descr", markupAlgo.name(),
                                         List.of());
        addElement(markupAction);

        var task = new Task("DETECTION AND MARKUP TASK", "descr",
                             List.of(detectionAction.name(), markupAction.name()));
        addElement(task);

        var pipeline = new Pipeline("DETECTION AND MARKUP PIPELINE", "asdf",
                                         List.of(task.name()));
        addElement(pipeline);

        var ex = TestUtil.assertThrows(InvalidPipelineException.class,
                                       () -> verifyBatchPipelineRunnable(pipeline.name()));
        assertTrue(ex.getMessage().contains("tasks cannot contain actions which have different ActionTypes"));
    }


    @Test
    public void throwsWhenMultiActionTaskIsNotFinalTask() {
        var algorithm = TestDescriptorFactory.getReferencedAlgorithm();
        addElement(algorithm);

        var action = new Action("TEST ACTION", "descr", algorithm.name(), List.of());
        addElement(action);

        var multiActionTask = new Task("MULTI ACTION TASK", "descr",
                                       List.of(action.name(), action.name()));
        addElement(multiActionTask);

        var singleActionTask = new Task("SINGLE ACTION TASK", "descr",
                                         List.of(action.name()));
        addElement(singleActionTask);

        var pipeline = new Pipeline("TEST PIPELINE", "descr",
                                    List.of(multiActionTask.name(), singleActionTask.name()));
        addElement(pipeline);

        var ex = TestUtil.assertThrows(InvalidPipelineException.class,
                                       () -> verifyBatchPipelineRunnable(pipeline.name()));
        assertEquals("TEST PIPELINE: No tasks may follow the multi-detection task of MULTI ACTION TASK.",
                     ex.getMessage());
    }


    @Test
    public void throwsWhenMarkupPipelineIsInvalid() {
        var detectionAlgo = new Algorithm(
                "DETECTION ALGO", "descr", ActionType.DETECTION, "TEST", OptionalInt.empty(),
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of()),
                true, true);
        addElement(detectionAlgo);
        var detectionTaskName = createSingleActionTask("DETECTION", detectionAlgo.name());


        var markupAlgo = new Algorithm(
                "MARKUP ALGO", "descr", ActionType.MARKUP, "MARKUP", OptionalInt.empty(),
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of()),
                true, true);
        addElement(markupAlgo);
        var markupAction = new Action("MARKUP ACTION", "desc", markupAlgo.name(),
                                         List.of());
        addElement(markupAction);
        var markupTask = new Task("MARKUP TASK", "desc",
                                   List.of(markupAction.name()));
        addElement(markupTask);

        var pipelineWithTaskAfterMarkup = new Pipeline(
                "TASK AFTER MARKUP PIPELINE", "descr" ,
                List.of(detectionTaskName, markupTask.name(), detectionTaskName));
        addElement(pipelineWithTaskAfterMarkup);


        {
            var ex = TestUtil.assertThrows(InvalidPipelineException.class,
                                           () -> verifyBatchPipelineRunnable(pipelineWithTaskAfterMarkup.name()));
            assertEquals("TASK AFTER MARKUP PIPELINE: No tasks may follow a markup task of MARKUP TASK.",
                         ex.getMessage());
        }



        var pipelineWithMarkupFirst = new Pipeline("MARKUP FIRST PIPELINE", "desc",
                                                        List.of(markupTask.name()));
        addElement(pipelineWithMarkupFirst);

        {
            var ex = TestUtil.assertThrows(InvalidPipelineException.class,
                                           () -> verifyBatchPipelineRunnable(pipelineWithMarkupFirst.name()));
            assertEquals(
                    "MARKUP FIRST PIPELINE: A markup task may not be the first task in a pipeline.",
                    ex.getMessage());
        }


        var multiMarkupTask = new Task("PARALLEL MULTI MARKUP TASK", "desc",
                                        List.of(markupAction.name(), markupAction.name()));
        addElement(multiMarkupTask);

        var parallelMultiMarkupPipeline = new Pipeline("PARALLEL MULTI MARKUP PIPELINE", "desc",
                                                          List.of(detectionTaskName, multiMarkupTask.name()));
        addElement(parallelMultiMarkupPipeline);

        {
            var ex = TestUtil.assertThrows(InvalidPipelineException.class,
                                           () -> verifyBatchPipelineRunnable(parallelMultiMarkupPipeline.name()));
            assertEquals("PARALLEL MULTI MARKUP TASK: A markup task may only contain one action.",
                         ex.getMessage());
        }


        var sequentialMultiMarkupPipeline = new Pipeline(
                "SEQUENTIAL MULTI MARKUP TASK PIPELINE", "descr",
                List.of(markupTask.name(), markupTask.name()));
        addElement(sequentialMultiMarkupPipeline);

        {
            var ex = TestUtil.assertThrows(InvalidPipelineException.class,
                                           () -> verifyBatchPipelineRunnable(sequentialMultiMarkupPipeline.name()));
            assertEquals(
                    "SEQUENTIAL MULTI MARKUP TASK PIPELINE: No tasks may follow a markup task of MARKUP TASK.",
                    ex.getMessage());
        }

    }


    @Test
    public void throwsWhenTryingToAddDifferentPipelineWithSameName() {
        var p1 = new Pipeline("pipeline", "description", List.of("task1"));
        var p2 = new Pipeline("pipeline", "different description", List.of("task1"));
        var ex = TestUtil.assertThrows(InvalidPipelineException.class,
                                       () -> _pipelineValidator.validateOnAdd(p2, Map.of(p1.name(), p1)));
        assertTrue(ex.getMessage().contains("same name already exists"));
    }


    @Test
    public void canHandleAddingSameExactPipelineTwice() {
        var p1 = new Pipeline("pipeline", "description", List.of("task1"));
        var p2 = new Pipeline("pipeline", "description", List.of("task1"));

        _pipelineValidator.validateOnAdd(p2, Map.of(p1.name(), p1));
    }



    @Test
    public void canValidatePipeline() {
        var pipeline = new Pipeline(null, "", List.of());

        assertValidationErrors(
                pipeline,
                createViolationMessage("name", pipeline.name(), "may not be empty"),
                createViolationMessage("description", pipeline.description(), "may not be empty"),
                createViolationMessage("tasks", "[]", "may not be empty"));

        pipeline = new Pipeline("PIPELINE;", "desc.desc", List.of("TASK", "", "a/b"));
        assertValidationErrors(
                pipeline,
                createViolationMessage("name", pipeline.name(), "may not contain / or ;"),
                createViolationMessage("tasks[1].<collection element>", pipeline.tasks().get(1), "may not be empty"),
                createViolationMessage("tasks[2].<collection element>", pipeline.tasks().get(2), "may not contain / or ;"));

        pipeline = new Pipeline("PIPELINE", "desc.desc", List.of("TASK", "ASDF"));
        _pipelineValidator.validateOnAdd(pipeline, Map.of());
    }


    @Test
    public void canValidateTask() {
        var task = new Task("", "", List.of());

        assertValidationErrors(
                task,
                createViolationMessage("name", task.name(), "may not be empty"),
                createViolationMessage("description", task.description(), "may not be empty"),
                createViolationMessage("actions", "[]", "may not be empty"));

        task = new Task("TASK", "desc/desc", List.of("ACTION", ""));
        assertValidationErrors(
                task,
                createViolationMessage("actions[1].<collection element>", task.actions().get(1), "may not be empty"));

        task = new Task("TASK", "desc/desc", List.of("ACTION", "ASDF"));
        _pipelineValidator.validateOnAdd(task, Map.of());
    }


    @Test
    public void canValidateAction() {
        var property1 = new ActionProperty("PROP1", "Value1");
        var property2 = new ActionProperty("", "");
        var property3 = new ActionProperty("PROP1", null);


        var action = new Action(null, null, "", List.of(property1, property2, property3));
        assertValidationErrors(
                action,
                createViolationMessage("name", action.name(), "may not be empty"),
                createViolationMessage("description", action.description(), "may not be empty"),
                createViolationMessage("algorithm", action.algorithm(), "may not be empty"),
                createViolationMessage("properties[1].name", property2.name(), "may not be empty"),
                createViolationMessage("properties[2].value", property3.value(), "may not be null"));

        action = new Action("ACTION", "descr", "ALGO", List.of(property1));
        _pipelineValidator.validateOnAdd(action, Map.of());
    }


    @Test
    public void canValidateAlgorithm() {
        var algorithm = new Algorithm("", "", null, "", OptionalInt.empty(),
                                            null, null,
                                            false, false);
        assertValidationErrors(
                algorithm,
                createViolationMessage("name", algorithm.name(), "may not be empty"),
                createViolationMessage("description", algorithm.description(), "may not be empty"),
                createViolationMessage("actionType", null, "may not be null"),
                createViolationMessage("trackType", algorithm.trackType(), "may not be empty"),
                createViolationMessage("providesCollection", null, "may not be null"),
                createViolationMessage("requiresCollection", null, "may not be null"),
                "must support batch processing, stream processing, or both");

        var property1 = new AlgorithmProperty(
                "PROP1", "descr", ValueType.STRING, "default", null);

        var property2 = new AlgorithmProperty(
                "", null, null, "default", "property.key");

        algorithm = new Algorithm(
                "ALGO", "descr", ActionType.DETECTION, "TEST", OptionalInt.empty(),
                new Algorithm.Requires(List.of("STATE1", "")),
                new Algorithm.Provides(List.of("", "STATE1"), List.of(property1, property2)),
                true, true);

        assertValidationErrors(
            algorithm,
            createViolationMessage("requiresCollection.states[1].<collection element>", "", "may not be empty"),
            createViolationMessage("providesCollection.states[0].<collection element>", "", "may not be empty"),
            createViolationMessage("providesCollection.properties[1].defaultValue", property2.toString(),
                                   "must provide either a defaultValue or propertiesKey, but not both."),
            createViolationMessage("providesCollection.properties[1].propertiesKey", property2.toString(),
                                   "must provide either a defaultValue or propertiesKey, but not both."),
            createViolationMessage("providesCollection.properties[1].description", null, "may not be empty"),
            createViolationMessage("providesCollection.properties[1].name", "", "may not be empty"),
            createViolationMessage("providesCollection.properties[1].type", null, "may not be null"));


        algorithm = new Algorithm(
                "ALGO", "descr", ActionType.DETECTION, "TEST", OptionalInt.empty(),
                new Algorithm.Requires(List.of("STATE1", "STATE2")),
                new Algorithm.Provides(List.of("STATE1", "STATE2"), List.of(property1)),
                true, true);
        _pipelineValidator.validateOnAdd(algorithm, Map.of());
    }


    @Test
    public void canValidateTransientPipelinesWithoutError() throws IOException {
        var algoProp1 = new AlgorithmProperty(
                "prop1", "description", ValueType.STRING, "default", null);
        var algo1 = new Algorithm(
                "ALGO1", "description", ActionType.DETECTION, "TEST1", OptionalInt.empty(),
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of(algoProp1)),
                true, false);
        addElement(algo1);

        var algoProp2 = new AlgorithmProperty(
                "prop2", "description", ValueType.STRING, "default", null);
        var algo2 = new Algorithm(
                "ALGO2", "description", ActionType.DETECTION, "TEST2", OptionalInt.empty(),
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of(algoProp2)),
                true, false);
        addElement(algo2);

        var path = TestUtil.findFilePath("/transient-pipelines/all-transient.json");
        var transientPipeline = _objectMapper.readValue(path.toFile(),
                                                        TransientPipelineDefinition.class);
        var partLookup = new TransientPipelinePartLookup(transientPipeline,
                                                         _pipelinePartLookup);
        _pipelineValidator.validateTransientPipeline(transientPipeline, partLookup);
    }


    @Test
    public void canValidateTransientPipelineReferencingExistingParts() throws IOException {
        var task1 = new Task("TASK1", "description", List.of("ACTION2"));
        addElement(task1);
        var action2 = new Action("ACTION2", "description", "ALGO1",
                                 List.of());
        addElement(action2);
        var algoProp1 = new AlgorithmProperty(
                "prop1", "description", ValueType.STRING, "default", null);
        var algo1 = new Algorithm(
                "ALGO1", "description", ActionType.DETECTION, "TEST", OptionalInt.empty(),
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of(algoProp1)),
                true, false);
        addElement(algo1);


        var path = TestUtil.findFilePath("/transient-pipelines/partial-transient.json");
        var transientPipeline = _objectMapper.readValue(path.toFile(),
                                                        TransientPipelineDefinition.class);
        var partLookup = new TransientPipelinePartLookup(transientPipeline,
                                                         _pipelinePartLookup);
        _pipelineValidator.validateTransientPipeline(transientPipeline, partLookup);
    }


    @Test
    public void reportsValidationErrorsForTransientPipline() throws IOException {
        var path = TestUtil.findFilePath("/transient-pipelines/validation-errors.json");
        var transientPipeline = _objectMapper.readValue(path.toFile(),
                                                        TransientPipelineDefinition.class);
        var partLookup = new TransientPipelinePartLookup(transientPipeline, _pipelinePartLookup);
        var ex = TestUtil.assertThrows(
                InvalidPipelineException.class,
                () -> _pipelineValidator.validateTransientPipeline(transientPipeline, partLookup));

        assertValidationErrors(
            ex.getMessage(),
            createViolationMessage("actions[0].properties[0].value", null, "may not be null"),
            createViolationMessage("pipeline", "[]", "may not be empty"),
            createViolationMessage("tasks[0].actions", "[]", "may not be empty"));
    }


    @Test
    public void reportsWhenTransientPipelineHasDuplicates() throws IOException {
        var path = TestUtil.findFilePath("/transient-pipelines/duplicates.json");
        var transientPipeline = _objectMapper.readValue(path.toFile(),
                                                        TransientPipelineDefinition.class);
        var partLookup = new TransientPipelinePartLookup(transientPipeline, _pipelinePartLookup);
        var ex = TestUtil.assertThrows(
                InvalidPipelineException.class,
                () -> _pipelineValidator.validateTransientPipeline(transientPipeline, partLookup));
        var message = ex.getMessage();
        assertThat(message, containsString("DUPTASK3"));
        assertThat(message, containsString("DUPTASK4"));
        assertThat(message, containsString("DUPACTION"));

        assertThat(message, not(containsString("TASK1")));
        assertThat(message, not(containsString("TASK2")));
        assertThat(message, not(containsString("ACTION1")));
        assertThat(message, not(containsString("ACTION2")));
    }


    @Test
    public void reportsWhenTransientPipelineReferencesMissingParts() throws IOException {
        var path = TestUtil.findFilePath("/transient-pipelines/partial-transient.json");
        var transientPipeline = _objectMapper.readValue(path.toFile(),
                                                        TransientPipelineDefinition.class);

        var partLookup = new TransientPipelinePartLookup(transientPipeline, _pipelinePartLookup);
        var ex = TestUtil.assertThrows(
                InvalidPipelineException.class,
                () -> _pipelineValidator.verifyBatchPipelineRunnable(
                        partLookup.getPipelineName(), partLookup));

        var message = ex.getMessage();
        assertThat(message, containsString("TASK1"));
        assertThat(message, containsString("ACTION2"));
        assertThat(message, containsString("ALGO1"));

        assertThat(message, not(containsString("TASK2")));
        assertThat(message, not(containsString("ACTION1")));

    }



    private void assertValidationErrors(PipelineElement pipelineElement, String... messages) {
        assertTrue("No messages passed in to check", messages.length > 0);
        var ex = TestUtil.assertThrows(InvalidPipelineException.class,
                                       () -> _pipelineValidator.validateOnAdd(pipelineElement, Map.of()));
        assertValidationErrors(ex.getMessage(), messages);
    }


    private static void assertValidationErrors(String actualMessage, String... expectedMessages) {
        for (String expectedMsg : expectedMessages) {
            assertThat(actualMessage, containsString(expectedMsg));
        }

        var lineCount = actualMessage
                .chars()
                .filter(ch -> ch == '\n')
                .count();

        assertEquals("Did not contain the expected number of error messages.",
                     lineCount, expectedMessages.length);
    }


    private static String createViolationMessage(String field, String value, String errorMsg) {
        return String.format("%s=\"%s\": %s", field, value, errorMsg);
    }


    private String createSingleActionTask(String namePrefix, String algorithmName) {
        var action = new Action(namePrefix + " ACTION", "adf", algorithmName, List.of());
        addElement(action);

        var task = new Task(namePrefix + " TASK", "asfd", List.of(action.name()));
        addElement(task);
        return task.name();
    }
}
