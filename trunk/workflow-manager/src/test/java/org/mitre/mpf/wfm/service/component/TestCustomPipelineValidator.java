/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.service.component;

import com.google.common.collect.ImmutableSortedSet;
import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.pipeline.xml.AlgorithmDefinition;
import org.mitre.mpf.wfm.pipeline.xml.PropertyDefinition;
import org.mitre.mpf.wfm.pipeline.xml.ValueType;
import org.mitre.mpf.wfm.service.PipelineService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mitre.mpf.wfm.service.component.TestDescriptorConstants.*;
import static org.mockito.Mockito.when;

public class TestCustomPipelineValidator {

    @InjectMocks
    private CustomPipelineValidatorImpl _pipelineValidator;

    @Mock
    private PipelineService _mockPipelineService;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }


    @Test
    public void testValidationHappyPath() throws InvalidCustomPipelinesException {
        JsonComponentDescriptor descriptor = TestDescriptorFactory.getWithCustomPipeline();
        String existingActionRef = "EXISTING ACTIONS NAME";
        descriptor.tasks.get(0).actions.add(existingActionRef);
        String existingTaskRef = "EXISTING TASKS NAME";
        descriptor.pipelines.get(0).tasks.add(existingTaskRef);

        when(_mockPipelineService.getAlgorithmNames())
                .thenReturn(ImmutableSortedSet.of("foo", REFERENCED_ALGO_NAME));


        when(_mockPipelineService.getActionNames())
                .thenReturn(ImmutableSortedSet.of("bar", existingActionRef));
        when(_mockPipelineService.getTaskNames())
                .thenReturn(ImmutableSortedSet.of(existingTaskRef));

        setupReferencedAlgo();

        _pipelineValidator.validate(descriptor);
    }



    @Test
    public void throwsExceptionWhenDuplicates() {
        JsonComponentDescriptor descriptor = TestDescriptorFactory.getWithCustomPipeline();

        when(_mockPipelineService.getActionNames())
                .thenReturn(ImmutableSortedSet.copyOf(ACTION_NAMES.subList(0, 2)));

        when(_mockPipelineService.getTaskNames())
                .thenReturn(ImmutableSortedSet.of("foo", TASK_NAMES.get(0)));

        when(_mockPipelineService.getPipelineNames())
                .thenReturn(ImmutableSortedSet.of(PIPELINE_NAME));

        try {
            _pipelineValidator.validate(descriptor);
            fail();
        }
        catch (InvalidCustomPipelinesException ex) {
            assertEquals(2, ex.getDupActions().size());
            assertTrue(ex.getDupActions().contains(ACTION_NAMES.get(0)));
            assertTrue(ex.getDupActions().contains(ACTION_NAMES.get(1)));

            assertEquals(1, ex.getDupTasks().size());
            assertTrue(ex.getDupTasks().contains(TASK_NAMES.get(0)));
            assertFalse(ex.getDupTasks().contains("foo"));

            assertEquals(1, ex.getDupPipelines().size());
            assertTrue(ex.getDupPipelines().contains(PIPELINE_NAME));
        }
    }

    @Test
    public void throwsExceptionWhenInvalidRefs() {
        JsonComponentDescriptor descriptor = TestDescriptorFactory.getWithCustomPipeline();
        String invalidActionName = "INVALID ACTION NAME";
        descriptor.tasks.get(0).actions.add(invalidActionName);

        String invalidTaskName = "INVALID TASK NAME";
        descriptor.pipelines.get(0).tasks.add(invalidTaskName);

        when(_mockPipelineService.getAlgorithmNames())
                .thenReturn(ImmutableSortedSet.of("foo", "bar", "baz"));


        try {
           _pipelineValidator.validate(descriptor);
            fail();
        }
        catch (InvalidCustomPipelinesException ex) {
            assertEquals(1, ex.getInvalidAlgorithmRefs().size());
            assertTrue(ex.getInvalidAlgorithmRefs().contains(REFERENCED_ALGO_NAME));

            assertEquals(1, ex.getInvalidActionRefs().size());
            assertTrue(ex.getInvalidActionRefs().contains(invalidActionName));

            assertEquals(1, ex.getInvalidTaskRefs().size());
            assertTrue(ex.getInvalidTaskRefs().contains(invalidTaskName));
        }
    }

    @Test
    public void throwsExceptionWhenInvalidActionProperties() {
        JsonComponentDescriptor descriptor = TestDescriptorFactory.getWithCustomPipeline();
        JsonComponentDescriptor.ActionProperty actionProperty = new JsonComponentDescriptor.ActionProperty();
        actionProperty.name = "INVALID PROP NAME";
        descriptor.actions.get(0).properties.add(actionProperty);

        setupReferencedAlgo();


        try {
            _pipelineValidator.validate(descriptor);
            fail();
        }
        catch (InvalidCustomPipelinesException ex) {
            Map<String, Set<String>> actionsWithInvalidProps = ex.getActionsWithInvalidProps();
            assertEquals(1, actionsWithInvalidProps.size());
            Set<String> invalidProps = actionsWithInvalidProps.get(ACTION_NAMES.get(0));
            assertEquals(1, invalidProps.size());
            assertTrue(invalidProps.contains(actionProperty.name));
        }
    }

    @Test
    public void throwsExceptionWhenPropertiesInvalidForAlgoInDescriptor() {
        JsonComponentDescriptor descriptor = TestDescriptorFactory.getWithCustomPipeline();
        JsonComponentDescriptor.Action testAction = new JsonComponentDescriptor.Action();
        testAction.name = "TEST ACTION NAME";
        testAction.algorithm = descriptor.algorithm.name;

        JsonComponentDescriptor.ActionProperty validAp = new JsonComponentDescriptor.ActionProperty();
        validAp.name = ALGO_PROP_NAMES.get(0);

        JsonComponentDescriptor.ActionProperty invalidAp = new JsonComponentDescriptor.ActionProperty();
        invalidAp.name = "INVALID_NAME";
        testAction.properties = Arrays.asList(validAp, invalidAp);

        descriptor.actions.add(testAction);

        setupReferencedAlgo();

        try {
            _pipelineValidator.validate(descriptor);
            fail();
        }
        catch (InvalidCustomPipelinesException ex) {
            Map<String, Set<String>> actionsWithInvalidProps = ex.getActionsWithInvalidProps();
            assertEquals(1, actionsWithInvalidProps.size());

            Set<String> invalidProps = actionsWithInvalidProps.get(testAction.name);
            assertEquals(1, invalidProps.size());
            assertTrue(invalidProps.contains(invalidAp.name));
        }
    }


    @Test
    public void throwsExceptionWhenPipelinesHaveInvalidProcessingType() {
        setupReferencedAlgo();

        JsonComponentDescriptor descriptor = TestDescriptorFactory.getWithCustomPipeline();
        descriptor.algorithm.supportsBatchProcessing = false;
        descriptor.algorithm.supportsStreamProcessing = true;

        AlgorithmDefinition batchAndStreamingAlgo = new AlgorithmDefinition(ActionType.DETECTION,
                                                                            "BATCH_AND_STREAMING", "", true, true);
        when(_mockPipelineService.getAlgorithm(batchAndStreamingAlgo.getName()))
                .thenReturn(batchAndStreamingAlgo);

        AlgorithmDefinition batchOnlyAlgo = new AlgorithmDefinition(ActionType.DETECTION, "BATCH_ONLY", "", true, false);
        when(_mockPipelineService.getAlgorithm(batchOnlyAlgo.getName()))
                .thenReturn(batchOnlyAlgo);


        JsonComponentDescriptor.Action batchAndStreamingAction = new JsonComponentDescriptor.Action();
        batchAndStreamingAction.name = "BATCH_AND_STREAMING_ACTION";
        batchAndStreamingAction.algorithm = batchAndStreamingAlgo.getName();
        batchAndStreamingAction.properties = Collections.emptyList();
        descriptor.actions.add(batchAndStreamingAction);

        JsonComponentDescriptor.Action streamingOnlyAction = new JsonComponentDescriptor.Action();
        streamingOnlyAction.name = "STREAMING_ONLY_ACTION";
        streamingOnlyAction.algorithm = descriptor.algorithm.name;
        streamingOnlyAction.properties = Collections.emptyList();
        descriptor.actions.add(streamingOnlyAction);


        // Valid because first action supports either and second supports streaming
        JsonComponentDescriptor.Task bothAndStreamingOnlyTask = new JsonComponentDescriptor.Task();
        bothAndStreamingOnlyTask.name = "BOTH_AND_STREAMING_ONLY_TASK";
        bothAndStreamingOnlyTask.actions = Arrays.asList(batchAndStreamingAction.name, streamingOnlyAction.name);
        descriptor.tasks.add(bothAndStreamingOnlyTask);

        JsonComponentDescriptor.Pipeline bothAndStreamingOnlyPipeline = new JsonComponentDescriptor.Pipeline();
        bothAndStreamingOnlyPipeline.name = "BOTH_AND_STREAMING_ONLY_PIPELINE";
        bothAndStreamingOnlyPipeline.tasks = Collections.singletonList(bothAndStreamingOnlyTask.name);
        descriptor.pipelines.add(bothAndStreamingOnlyPipeline);



        JsonComponentDescriptor.Action batchOnlyAction = new JsonComponentDescriptor.Action();
        batchOnlyAction.name = "BATCH_ONLY_ACTION";
        batchOnlyAction.algorithm = batchOnlyAlgo.getName();
        batchOnlyAction.properties = Collections.emptyList();
        descriptor.actions.add(batchOnlyAction);

        JsonComponentDescriptor.Task batchOnlyTask = new JsonComponentDescriptor.Task();
        batchOnlyTask.name = "BATCH_ONLY_TASK";
        batchOnlyTask.actions = Collections.singletonList(batchOnlyAction.name);
        descriptor.tasks.add(batchOnlyTask);

        // Invalid because the first task's second action only supports streaming, making the task overall only support
        // streaming.
        JsonComponentDescriptor.Pipeline invalidPipeline = new JsonComponentDescriptor.Pipeline();
        invalidPipeline.name = "INVALID_PIPELINE";
        invalidPipeline.tasks = Arrays.asList(bothAndStreamingOnlyTask.name, batchOnlyTask.name);
        descriptor.pipelines.add(invalidPipeline);

        // Make sure validator doesn't consider this to have invalid processing type.
        // Validator should only report invalid task ref.
        JsonComponentDescriptor.Pipeline pipelineWithMissingTask = new JsonComponentDescriptor.Pipeline();
        pipelineWithMissingTask.name = "PIPELINE_WITH_MISSING_TASK";
        pipelineWithMissingTask.tasks = Arrays.asList("MISSING_TASK", batchOnlyTask.name);
        descriptor.pipelines.add(pipelineWithMissingTask);


        when(_mockPipelineService.getAlgorithmNames())
                .thenReturn(ImmutableSortedSet.of("foo", REFERENCED_ALGO_NAME, batchAndStreamingAlgo.getName(),
                                                  batchOnlyAlgo.getName()));

        try {
            _pipelineValidator.validate(descriptor);
            fail("expected InvalidCustomPipelinesException");
        }
        catch (InvalidCustomPipelinesException ex) {
            assertEquals(Collections.singleton(invalidPipeline.name), ex.getPipelinesWithInvalidProcessingType());
            assertEquals(Collections.singleton(pipelineWithMissingTask.tasks.get(0)), ex.getInvalidTaskRefs());
        }

    }


    @Test
    public void descriptorWithoutCustomPipelinesValidates() throws InvalidCustomPipelinesException {
        _pipelineValidator.validate(TestDescriptorFactory.get());
    }



    private void setupReferencedAlgo() {
        AlgorithmDefinition algoDef = new AlgorithmDefinition(ActionType.DETECTION, REFERENCED_ALGO_NAME, "", true, false);
        ACTION1_PROP_NAMES
                .stream()
                .map(n -> new PropertyDefinition(n, ValueType.INT, "1", "0"))
                .forEach(pd -> algoDef.getProvidesCollection().getAlgorithmProperties().add(pd));
        algoDef.getProvidesCollection().getAlgorithmProperties().add(
                new PropertyDefinition("foo", ValueType.INT, "2", "0"));

        when(_mockPipelineService.getAlgorithm(REFERENCED_ALGO_NAME))
                .thenReturn(algoDef);

        when(_mockPipelineService.getAlgorithmNames())
                .thenReturn(ImmutableSortedSet.of("foo", REFERENCED_ALGO_NAME));
    }
}
