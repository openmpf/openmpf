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

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.rest.api.component.ComponentState;
import org.mitre.mpf.rest.api.component.RegisterComponentModel;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.pipeline.xml.AlgorithmDefinition;
import org.mitre.mpf.wfm.service.NodeManagerService;
import org.mitre.mpf.wfm.service.PipelineService;
import org.mitre.mpf.wfm.util.Tuple;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mitre.mpf.test.TestUtil.*;
import static org.mitre.mpf.wfm.service.component.TestDescriptorConstants.*;
import static org.mockito.Mockito.*;

public class TestAddComponentService {

    @InjectMocks
    private AddComponentServiceImpl _addComponentService;

    @Mock
    private PipelineService _mockPipelineService;

    @Mock
    private NodeManagerService _mockNodeManager;

    @Mock
    private ComponentDeploymentService _mockDeploymentService;

    @Mock
    private ComponentStateService _mockStateService;

    @Mock
    private ComponentDescriptorValidator _mockDescriptorValidator;

    @Mock
    private CustomPipelineValidator _mockPipelineValidator;

    @Mock
    private RemoveComponentService _mockRemoveComponentService;

    @Mock
    private Properties _mockProperties;

    @Mock
    private ObjectMapper _mockObjectMapper;

    private static final String _testPackageName = "test-package.tar.gz";

    private static final Tuple<Boolean, String> _successTuple = new Tuple<>(true, null);

    private static final Tuple<Boolean, String> _failureTuple = new Tuple<>(false, "fail");


    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        when(_mockProperties.getProperty("my.test.key"))
                .thenReturn("my.test.value");
    }

    @Test
    public void throwsExceptionWhenNoRegisterModel() throws ComponentRegistrationException {
        when(_mockStateService.getByPackageFile(_testPackageName))
                .thenReturn(Optional.empty());

        try {
            _addComponentService.registerComponent(_testPackageName);
            fail();
        }
        catch (ComponentRegistrationStatusException ex) {
            assertEquals(ComponentState.UNKNOWN, ex.getComponentState());
        }

        verify(_mockStateService)
                .replacePackageState(_testPackageName, ComponentState.REGISTER_ERROR);

        verify(_mockStateService, never())
                .update(any());

        assertNeverUndeployed();
    }


    @Test
    public void throwsExceptionWhenComponentInRegisterState() throws ComponentRegistrationException {
        RegisterComponentModel registerModel = new RegisterComponentModel();
        registerModel.setComponentState(ComponentState.REGISTERED);

        when(_mockStateService.getByPackageFile(_testPackageName))
                .thenReturn(Optional.of(registerModel));

        try {
            _addComponentService.registerComponent(_testPackageName);
            fail();
        }
        catch (ComponentRegistrationStatusException ex) {
            assertEquals(ComponentState.REGISTERED, ex.getComponentState());
        }
        verify(_mockStateService)
                .replacePackageState(_testPackageName, ComponentState.REGISTER_ERROR);

        verify(_mockStateService, never())
                .update(any());
        assertNeverUndeployed();
    }


    @Test
    public void throwsExceptionWhenDeployFails() throws ComponentRegistrationException {
        RegisterComponentModel rcm = new RegisterComponentModel();
        rcm.setComponentState(ComponentState.UPLOADED);
        when(_mockStateService.getByPackageFile(_testPackageName))
                .thenReturn(Optional.of(rcm));

        when(_mockDeploymentService.deployComponent(anyString()))
                .thenThrow(new DuplicateComponentException(""));

        try {
            _addComponentService.registerComponent(_testPackageName);
            fail();
        }
        catch (DuplicateComponentException ignored) {
        }

        verify(_mockStateService)
                .replacePackageState(_testPackageName, ComponentState.REGISTER_ERROR);
        verify(_mockStateService, never())
                .update(any());
        assertNeverUndeployed();
    }

    @Test
    public void testRegistrationHappyPath() throws ComponentRegistrationException, IOException {
        // Arrange
        JsonComponentDescriptor descriptor = TestDescriptorFactory.get();

        RegisterComponentModel registerModel = new RegisterComponentModel();
        registerModel.setComponentState(ComponentState.UPLOADED);
        registerModel.setPackageFileName(_testPackageName);

        AlgorithmDefinition algoDef = new AlgorithmDefinition(
                ActionType.DETECTION, descriptor.algorithm.name.toUpperCase(), descriptor.algorithm.description);

        when(_mockPipelineService.addAndSaveAlgorithm(algoDef))
                .thenReturn(_successTuple);

        Map<String, String> propertyMap = new HashMap<>();
        propertyMap.put(ALGO_PROP_NAMES.get(0), "100");
        propertyMap.put(ALGO_PROP_NAMES.get(1), "my.test.value");

        when(_mockPipelineService.addAndSaveActionDeprecated(contains(algoDef.getName()), anyString(), eq(algoDef.getName()), eq(propertyMap)))
                .thenReturn(_successTuple);

        when(_mockPipelineService.addAndSaveTaskDeprecated(whereArg(td -> td.getName().contains(algoDef.getName()))))
                .thenReturn(_successTuple);

        when(_mockStateService.getByPackageFile(_testPackageName))
                .thenReturn(Optional.of(registerModel));

        when(_mockDeploymentService.deployComponent(_testPackageName))
                .thenReturn(DESCRIPTOR_PATH);

        when(_mockObjectMapper.readValue(new File(DESCRIPTOR_PATH), JsonComponentDescriptor.class))
                .thenReturn(descriptor);

        when(_mockNodeManager.getServiceModels())
                .thenReturn(Collections.emptyMap());

        when(_mockNodeManager.addService(whereArg(s -> s.getName().equals(COMPONENT_NAME)
                    && s.getArgs().contains("launch-arg1"))))
                .thenReturn(true);

        // Act
        _addComponentService.registerComponent(_testPackageName);


        // Assert
        verify(_mockStateService)
                .replacePackageState(_testPackageName, ComponentState.REGISTERING);

        verify(_mockStateService, times(2))
                .update(whereArg(rcm -> rcm.getServiceName().contains(COMPONENT_NAME)
                        && rcm.getJsonDescriptorPath().equals(DESCRIPTOR_PATH)
                        && rcm.getComponentName().equals(COMPONENT_NAME)
                        && rcm.getActions().size() == 1
                        && rcm.getTasks().size() == 1));

        // Verify mocked methods
        verify(_mockPipelineService)
                .addAndSaveAlgorithm(algoDef);

        verify(_mockPipelineService)
                .addAndSaveActionDeprecated(contains(algoDef.getName()), anyString(), anyString(), eq(propertyMap));

        verify(_mockDeploymentService)
                .deployComponent(_testPackageName);

        verify(_mockNodeManager)
                .addService(whereArg(s -> s.getName().equals(COMPONENT_NAME)));

        assertNeverUndeployed();
    }


    @Test
    public void canHandleInvalidJson() throws IOException, ComponentRegistrationException {
        // Arrange

        RegisterComponentModel registerModel = new RegisterComponentModel();
        registerModel.setComponentState(ComponentState.UPLOADED);
        registerModel.setComponentName(COMPONENT_NAME);

        when(_mockStateService.getByPackageFile(_testPackageName))
                .thenReturn(Optional.of(registerModel));

        when(_mockDeploymentService.deployComponent(_testPackageName))
                .thenReturn(DESCRIPTOR_PATH);

        when(_mockObjectMapper.readValue(new File(DESCRIPTOR_PATH), JsonComponentDescriptor.class))
                .thenThrow(new JsonMappingException(null));

        // Act
        try {
            _addComponentService.registerComponent(_testPackageName);
            fail("Add should have failed but it was successful");
        }
        catch (FailedToParseDescriptorException ignored) { }

        // Assert
        verify(_mockStateService, never())
                .update(whereArg(rcm -> rcm.getComponentState() == ComponentState.REGISTERED));

        verify(_mockStateService)
                .update(whereArg(rcm -> rcm.getJsonDescriptorPath().equals(DESCRIPTOR_PATH)));

        verify(_mockStateService)
                .replacePackageState(_testPackageName, ComponentState.REGISTER_ERROR);

        assertUndeployed(COMPONENT_NAME);
    }


    @Test
    public void testRegistrationWithCustomPipelineHappyPath() throws ComponentRegistrationException, IOException {
        // Arrange
        JsonComponentDescriptor descriptor = TestDescriptorFactory.getWithCustomPipeline();
        setUpMocksForDescriptor(descriptor);

        AlgorithmDefinition algoDef = new AlgorithmDefinition(
                ActionType.DETECTION, descriptor.algorithm.name.toUpperCase(), descriptor.algorithm.description);

        when(_mockPipelineService.addAndSaveAlgorithm(algoDef))
                .thenReturn(_successTuple);

        when(_mockPipelineService.addAndSaveActionDeprecated(nonBlank(), nonBlank(), eq(REFERENCED_ALGO_NAME),
                anyNonNull()))
                .thenReturn(_successTuple);

        when(_mockPipelineService.addAndSaveTaskDeprecated(anyNonNull()))
                .thenReturn(_successTuple);

        when(_mockPipelineService.addAndSavePipelineDeprecated(anyNonNull()))
                .thenReturn(_successTuple);

        when(_mockNodeManager.getServiceModels())
                .thenReturn(Collections.singletonMap("fake name", null));

        when(_mockNodeManager.addService(anyNonNull()))
                .thenReturn(true);

        // Act
        _addComponentService.registerComponent(_testPackageName);

        // Assert
        verify(_mockStateService)
                .replacePackageState(_testPackageName, ComponentState.REGISTERING);

        verify(_mockStateService, atLeastOnce())
                .update(whereArg(
                        rcm -> rcm.getActions().containsAll(ACTION_NAMES)
                                && rcm.getTasks().containsAll(TASK_NAMES)
                                && rcm.getPipelines().contains(PIPELINE_NAME)));

        verify(_mockPipelineService, times(3))
                .addAndSaveActionDeprecated(nonBlank(), nonBlank(), eq(REFERENCED_ALGO_NAME), anyNonNull());

        verify(_mockPipelineService)
                .addAndSaveActionDeprecated(
                        eq(ACTION_NAMES.get(0)),
                        nonBlank(),
                        nonBlank(),
                        whereArg(m -> m.get(ACTION1_PROP_NAMES.get(0))
                                .equals(ACTION1_PROP_VALUES.get(0))));

        verify(_mockPipelineService)
                .addAndSaveTaskDeprecated(whereArg(t ->
                        t.getName().equals(TASK_NAMES.get(0))
                                && t.getDescription().equals(TASK_NAMES.get(0) + " description")
                                && t.getActions().size() == 1));

        verify(_mockPipelineService)
                .addAndSaveTaskDeprecated(whereArg(t ->
                        t.getName().equals(TASK_NAMES.get(1))
                                && t.getDescription().equals(TASK_NAMES.get(1) + " description")
                                && t.getActions().size() == 2));

        verify(_mockPipelineService)
                .addAndSavePipelineDeprecated(whereArg(p ->
                        p.getName().equals(PIPELINE_NAME)
                                && p.getDescription().contains("description")
                                && p.getTaskRefs().size() == 2));

        verify(_mockNodeManager)
                .addService(whereArg(s -> s.getName().equals(COMPONENT_NAME)));
        assertNeverUndeployed();
    }


    @Test
    public void doesNotDeleteAlgorithmIfFailedToAdd() throws ComponentRegistrationException, IOException {
        // Arrange
        JsonComponentDescriptor descriptor = TestDescriptorFactory.getWithCustomPipeline();

        setUpMocksForDescriptor(descriptor);

        when(_mockPipelineService.addAndSaveAlgorithm(any()))
                .thenReturn(_failureTuple);

        // Act
        try {
            _addComponentService.registerComponent(_testPackageName);
            fail("register component should have thrown an exception");
        }
        catch (ComponentRegistrationSubsystemException ignored) {
        }

        // Assert
        verify(_mockRemoveComponentService, never())
                .recursivelyDeleteCustomPipelines(any());
        assertUndeployed(COMPONENT_NAME);
    }

    @Test
    public void throwsExceptionWhenInvalidDescriptor() throws ComponentRegistrationException, IOException {
        JsonComponentDescriptor descriptor = TestDescriptorFactory.getWithCustomPipeline();
        setUpMocksForDescriptor(descriptor);

        doThrow(InvalidComponentDescriptorException.class)
                .when(_mockDescriptorValidator)
                .validate(descriptor);
        try {
            _addComponentService.registerComponent(_testPackageName);
            fail();
        }
        catch (InvalidComponentDescriptorException ignored) {
        }

        verify(_mockPipelineService, never())
                .addAndSaveAlgorithm(any());

        verify(_mockPipelineService, never())
                .addAndSaveActionDeprecated(any(), any(), any(), any());
        assertUndeployed(COMPONENT_NAME);
    }

    @Test
    public void throwsExceptionWhenInvalidCustomPipelines() throws ComponentRegistrationException, IOException {
        JsonComponentDescriptor descriptor = TestDescriptorFactory.getWithCustomPipeline();
        setUpMocksForDescriptor(descriptor);

        doThrow(InvalidCustomPipelinesException.class)
                .when(_mockPipelineValidator)
                .validate(descriptor);
        try {
            _addComponentService.registerComponent(_testPackageName);
            fail();
        }
        catch (InvalidCustomPipelinesException ignored) {
        }

        verify(_mockPipelineService, never())
                .addAndSaveAlgorithm(any());

        verify(_mockPipelineService, never())
                .addAndSaveActionDeprecated(any(), any(), any(), any());
        assertUndeployed(COMPONENT_NAME);
    }

    private void setUpMocksForDescriptor(JsonComponentDescriptor descriptor) throws DuplicateComponentException, IOException {
        RegisterComponentModel rcm = new RegisterComponentModel();
        rcm.setComponentState(ComponentState.UPLOADED);
        rcm.setPackageFileName(_testPackageName);

        when(_mockStateService.getByPackageFile(_testPackageName))
                .thenReturn(Optional.of(rcm));

        when(_mockDeploymentService.deployComponent(_testPackageName))
                .thenReturn(DESCRIPTOR_PATH);

        when(_mockObjectMapper.readValue(new File(DESCRIPTOR_PATH), JsonComponentDescriptor.class))
                .thenReturn(descriptor);
    }


    private void assertUndeployed(String componentTld) {
        verify(_mockDeploymentService)
                .undeployComponent(componentTld);
    }

    private void assertNeverUndeployed() {
        verify(_mockDeploymentService, never())
                .undeployComponent(any());
    }
}

