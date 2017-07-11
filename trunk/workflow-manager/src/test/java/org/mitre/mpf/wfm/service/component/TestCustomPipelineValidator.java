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
import org.mitre.mpf.wfm.pipeline.PipelinesService;
import org.mitre.mpf.wfm.pipeline.xml.AlgorithmDefinition;
import org.mitre.mpf.wfm.pipeline.xml.PropertyDefinition;
import org.mitre.mpf.wfm.pipeline.xml.ValueType;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.*;
import static org.mitre.mpf.wfm.service.component.TestDescriptorConstants.*;
import static org.mockito.Mockito.when;

public class TestCustomPipelineValidator {

    @InjectMocks
    private CustomPipelineValidatorImpl _pipelineValidator;

    @Mock
    private PipelinesService _mockPipelinesService;

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

        when(_mockPipelinesService.getAlgorithmNames())
                .thenReturn(ImmutableSortedSet.of("foo", REFERENCED_ALGO_NAME));


        when(_mockPipelinesService.getActionNames())
                .thenReturn(ImmutableSortedSet.of("bar", existingActionRef));
        when(_mockPipelinesService.getTaskNames())
                .thenReturn(ImmutableSortedSet.of(existingTaskRef));


        List<PropertyDefinition> propDefs = ACTION1_PROP_NAMES
                .stream()
                .map(n -> new PropertyDefinition(n, ValueType.INT, "1", "0"))
                .collect(toList());
        propDefs.add(new PropertyDefinition("foo", ValueType.INT, "2", "0"));

        setupAlgoWithProperties(REFERENCED_ALGO_NAME, propDefs);

        _pipelineValidator.validate(descriptor);
    }



    @Test
    public void throwsExceptionWhenDuplicates() {
        JsonComponentDescriptor descriptor = TestDescriptorFactory.getWithCustomPipeline();

        when(_mockPipelinesService.getActionNames())
                .thenReturn(ImmutableSortedSet.copyOf(ACTION_NAMES.subList(0, 2)));

        when(_mockPipelinesService.getTaskNames())
                .thenReturn(ImmutableSortedSet.of("foo", TASK_NAMES.get(0)));

        when(_mockPipelinesService.getPipelineNames())
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

        when(_mockPipelinesService.getAlgorithmNames())
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

        List<PropertyDefinition> propDefs = ACTION1_PROP_NAMES
                .stream()
                .map(n -> new PropertyDefinition(n, ValueType.INT, "1", "0"))
                .collect(toList());
        setupAlgoWithProperties(REFERENCED_ALGO_NAME, propDefs);


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

        List<PropertyDefinition> propDefs = ACTION1_PROP_NAMES
                .stream()
                .map(n -> new PropertyDefinition(n, ValueType.INT, "1", "0"))
                .collect(toList());
        setupAlgoWithProperties(REFERENCED_ALGO_NAME, propDefs);

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
    public void descriptorWithoutCustomPipelinesValidates() throws InvalidCustomPipelinesException {
        _pipelineValidator.validate(TestDescriptorFactory.get());
    }


    private void setupAlgoWithProperties(String algoName, List<PropertyDefinition> properties) {
        AlgorithmDefinition algoDef = new AlgorithmDefinition(ActionType.DETECTION, algoName, "");
        algoDef.getProvidesCollection().getAlgorithmProperties().addAll(properties);

        when(_mockPipelinesService.getAlgorithm(algoName))
                .thenReturn(algoDef);
    }
}
