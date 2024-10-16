/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mitre.mpf.wfm.service.component.TestDescriptorConstants.ACTION1_PROP_NAMES;
import static org.mitre.mpf.wfm.service.component.TestDescriptorConstants.ACTION1_PROP_VALUES;
import static org.mitre.mpf.wfm.service.component.TestDescriptorConstants.ACTION_NAMES;
import static org.mitre.mpf.wfm.service.component.TestDescriptorConstants.COMPONENT_NAME;
import static org.mitre.mpf.wfm.service.component.TestDescriptorConstants.DESCRIPTOR_PATH;
import static org.mitre.mpf.wfm.service.component.TestDescriptorConstants.PIPELINE_NAME;
import static org.mitre.mpf.wfm.service.component.TestDescriptorConstants.REFERENCED_ALGO_NAME;
import static org.mitre.mpf.wfm.service.component.TestDescriptorConstants.TASK_NAMES;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.rest.api.component.ComponentState;
import org.mitre.mpf.rest.api.component.RegisterComponentModel;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.Algorithm;
import org.mitre.mpf.rest.api.pipelines.Pipeline;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.service.NodeManagerService;
import org.mitre.mpf.wfm.service.StreamingServiceManager;
import org.mitre.mpf.wfm.service.pipeline.InvalidPipelineException;
import org.mitre.mpf.wfm.service.pipeline.PipelineService;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestAddComponentService extends MockitoTest.Strict {

    private AddComponentServiceImpl _addComponentService;

    @Mock
    private PropertiesUtil _mockPropertiesUtil;

    @Mock
    private PipelineService _mockPipelineService;

    @Mock
    private NodeManagerService _mockNodeManager;

    @Mock
    private StreamingServiceManager _mockStreamingServiceManager;

    @Mock
    private ComponentDeploymentService _mockDeploymentService;

    @Mock
    private ComponentStateService _mockStateService;

    @Mock
    private ComponentDescriptorValidator _mockDescriptorValidator;

    @Mock
    private RemoveComponentService _mockRemoveComponentService;

    @Mock
    private ObjectMapper _mockObjectMapper;

    private static final String _testPackageName = "test-package.tar.gz";

    @Rule
    public TemporaryFolder _tempDir = new TemporaryFolder();

    @Before
    public void init() {
        _addComponentService = new AddComponentServiceImpl(
                _mockPropertiesUtil, _mockPipelineService, Optional.of(_mockNodeManager),
                Optional.of(_mockStreamingServiceManager), _mockDeploymentService, _mockStateService,
                _mockDescriptorValidator, null, _mockRemoveComponentService, _mockObjectMapper);
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


        when(_mockStateService.getByPackageFile(_testPackageName))
                .thenReturn(Optional.of(registerModel));

        when(_mockDeploymentService.deployComponent(_testPackageName))
                .thenReturn(DESCRIPTOR_PATH);

        when(_mockObjectMapper.readValue(new File(DESCRIPTOR_PATH), JsonComponentDescriptor.class))
                .thenReturn(descriptor);

        when(_mockNodeManager.getServiceModels())
                .thenReturn(Collections.emptyMap());

        when(_mockNodeManager.addService(argThat(s -> s.name().equals(COMPONENT_NAME)
                    && s.args().contains("/path/to/batch/lib.so"))))
                .thenReturn(true);

        // Act
        _addComponentService.registerComponent(_testPackageName);


        // Assert
        verify(_mockStateService)
                .replacePackageState(_testPackageName, ComponentState.REGISTERING);

        verify(_mockStateService, times(2))
                .update(argThat(rcm -> rcm.getServiceName().contains(COMPONENT_NAME)
                        && rcm.getJsonDescriptorPath().equals(DESCRIPTOR_PATH)
                        && rcm.getComponentName().equals(COMPONENT_NAME)
                        && rcm.getActions().size() == 1
                        && rcm.getTasks().size() == 1));


        // Verify mocked methods

        String expectedAlgoName = descriptor.algorithm().name();

        verifyDescriptorAlgoSaved(descriptor);

        verify(_mockPipelineService)
                .save(argThat((Action a) -> a.name().contains(expectedAlgoName)
                        && a.algorithm().equals(expectedAlgoName)
                        && a.properties().isEmpty() ));

        verify(_mockPipelineService)
                .save(argThat((Task t) -> t.name().contains(expectedAlgoName)));

        verify(_mockDeploymentService)
                .deployComponent(_testPackageName);

        verify(_mockNodeManager)
                .addService(argThat(s -> s.name().equals(COMPONENT_NAME)));

        verify(_mockStreamingServiceManager)
                .addService(argThat(
                        s -> s.getServiceName().equals(COMPONENT_NAME)
                                && s.getAlgorithmName().equals(descriptor.algorithm().name())
                                && s.getEnvironmentVariables().size() == descriptor.environmentVariables().size()));

        assertNeverUndeployed();
    }

    private void verifyDescriptorAlgoSaved(JsonComponentDescriptor descriptor) {
        verify(_mockPipelineService)
                .save(argThat((Algorithm algo) -> algo.name().equals(descriptor.algorithm().name())
                        && algo.supportsBatchProcessing() == descriptor.supportsBatchProcessing()
                        && algo.supportsStreamProcessing() == descriptor.supportsStreamProcessing()));

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
                .update(argThat(rcm -> rcm.getComponentState() == ComponentState.REGISTERED));

        verify(_mockStateService)
                .update(argThat(rcm -> rcm.getJsonDescriptorPath().equals(DESCRIPTOR_PATH)));

        verify(_mockStateService)
                .replacePackageState(_testPackageName, ComponentState.REGISTER_ERROR);

        assertUndeployed(COMPONENT_NAME);
    }


    @Test
    public void testRegistrationWithCustomPipelineHappyPath() throws ComponentRegistrationException, IOException {
        // Arrange
        JsonComponentDescriptor descriptor = TestDescriptorFactory.getWithCustomPipeline();
        setUpMocksForDescriptor(descriptor);

        when(_mockNodeManager.getServiceModels())
                .thenReturn(Collections.singletonMap("fake name", null));

        when(_mockNodeManager.addService(notNull()))
                .thenReturn(true);

        // Act
        _addComponentService.registerComponent(_testPackageName);

        // Assert
        assertNeverUndeployed();

        verify(_mockStateService)
                .replacePackageState(_testPackageName, ComponentState.REGISTERING);

        verifyCustomPipelinesSaved(descriptor);

        verify(_mockNodeManager)
                .addService(argThat(s -> s.name().equals(COMPONENT_NAME)));

        verify(_mockStreamingServiceManager)
                .addService(argThat(
                        s -> s.getServiceName().equals(COMPONENT_NAME)
                                && s.getAlgorithmName().equals(descriptor.algorithm().name())
                                && s.getEnvironmentVariables().size() == descriptor.environmentVariables().size()));
    }



    private void verifyCustomPipelinesSaved(JsonComponentDescriptor descriptor) {
        verify(_mockStateService, atLeastOnce())
                .update(argThat(
                        rcm -> rcm.getActions().containsAll(ACTION_NAMES)
                                && rcm.getTasks().containsAll(TASK_NAMES)
                                && rcm.getPipelines().contains(PIPELINE_NAME)));


        verifyDescriptorAlgoSaved(descriptor);

        verify(_mockPipelineService, times(3))
                .save(argThat((Action a) -> a.algorithm().equals(REFERENCED_ALGO_NAME)));

        verify(_mockPipelineService)
                .save(argThat((Action a) -> a.name().equals(ACTION_NAMES.get(0))
                        && a.properties().stream()
                                .anyMatch(pd -> pd.name().equals(ACTION1_PROP_NAMES.get(0))
                                        && pd.value().equals(ACTION1_PROP_VALUES.get(0)))));

        verify(_mockPipelineService)
                .save(argThat((Task t) ->
                        t.name().equals(TASK_NAMES.get(0))
                                && t.description().equals(TASK_NAMES.get(0) + " description")
                                && t.actions().size() == 1));

        verify(_mockPipelineService)
                .save(argThat((Task t) ->
                        t.name().equals(TASK_NAMES.get(1))
                                && t.description().equals(TASK_NAMES.get(1) + " description")
                                && t.actions().size() == 2));

        verify(_mockPipelineService)
                .save(argThat((Pipeline p) ->
                        p.name().equals(PIPELINE_NAME)
                                && p.description().contains("description")
                                && p.tasks().size() == 2));
    }


    @Test
    public void canAddNewUnmanagedComponent() throws ComponentRegistrationException, IOException {
        when(_mockPropertiesUtil.getPluginDeploymentPath())
                .thenReturn(_tempDir.newFolder("plugins").toPath());

        when(_mockStateService.getByComponentName(COMPONENT_NAME))
                .thenReturn(Optional.empty());


        JsonComponentDescriptor descriptor = TestDescriptorFactory.getWithCustomPipeline();


        boolean wasModified = _addComponentService.registerUnmanagedComponent(descriptor);
        assertTrue(wasModified);

        verifyNoInteractions(_mockRemoveComponentService, _mockNodeManager);
        verifySuccessfullyAddedUnmanagedComponent(descriptor);
    }


    @Test
    public void canReplaceExistingUnmanagedComponent() throws ComponentRegistrationException, IOException {
        when(_mockPropertiesUtil.getPluginDeploymentPath())
                .thenReturn(_tempDir.newFolder("plugins").toPath());

        JsonComponentDescriptor existingDescriptor = TestDescriptorFactory.getWithCustomPipeline();

        RegisterComponentModel alreadyRegisteredComponent = new RegisterComponentModel();
        alreadyRegisteredComponent.setManaged(false);
        String existingDescriptorPath = "/tmp/fake/path/descriptor.json";
        alreadyRegisteredComponent.setJsonDescriptorPath(existingDescriptorPath);

        when(_mockStateService.getByComponentName(COMPONENT_NAME))
                .thenReturn(Optional.of(alreadyRegisteredComponent));
        when(_mockObjectMapper.readValue(new File(existingDescriptorPath), JsonComponentDescriptor.class))
                .thenReturn(existingDescriptor);

        Algorithm existingAlgo = existingDescriptor.algorithm();
        Algorithm algoWithChange = new Algorithm(
                existingAlgo.name(),
                existingAlgo.description(),
                existingAlgo.actionType(),
                existingAlgo.trackType(),
                existingAlgo.outputChangedCounter(),
                // Just pick a random field to change
                new Algorithm.Requires(List.of("asdf")),
                existingAlgo.providesCollection(),
                existingAlgo.supportsBatchProcessing(),
                existingAlgo.supportsStreamProcessing());

        JsonComponentDescriptor newDescriptor = new JsonComponentDescriptor(
                existingDescriptor.componentName(),
                existingDescriptor.componentVersion(),
                existingDescriptor.middlewareVersion(),
                existingDescriptor.setupFile(),
                existingDescriptor.instructionsFile(),
                existingDescriptor.sourceLanguage(),
                existingDescriptor.batchLibrary(),
                existingDescriptor.streamLibrary(),
                existingDescriptor.environmentVariables(),
                algoWithChange,
                existingDescriptor.actions(),
                existingDescriptor.tasks(),
                existingDescriptor.pipelines());


        boolean wasModified = _addComponentService.registerUnmanagedComponent(newDescriptor);
        assertTrue(wasModified);

        verifyNoInteractions(_mockNodeManager);
        verify(_mockRemoveComponentService)
                .removeComponent(COMPONENT_NAME);

        verifySuccessfullyAddedUnmanagedComponent(newDescriptor);
    }


    private void verifySuccessfullyAddedUnmanagedComponent(JsonComponentDescriptor descriptor) throws IOException {
        Path expected_descriptor_dir = _tempDir.getRoot().toPath().resolve("plugins/" + COMPONENT_NAME + "/descriptor");
        Path expected_descriptor_path = expected_descriptor_dir.resolve("descriptor.json");
        assertTrue(Files.isDirectory(expected_descriptor_dir));
        verify(_mockObjectMapper)
                .writeValue(expected_descriptor_path.toFile(), descriptor);

        verifyCustomPipelinesSaved(descriptor);

        ArgumentCaptor<RegisterComponentModel> rcmCaptor = ArgumentCaptor.forClass(RegisterComponentModel.class);
        verify(_mockStateService)
                .update(rcmCaptor.capture());
        RegisterComponentModel registerModel = rcmCaptor.getValue();
        assertFalse(registerModel.isManaged());
        assertEquals(COMPONENT_NAME, registerModel.getComponentName());
        assertEquals(expected_descriptor_path.toAbsolutePath().toString(), registerModel.getJsonDescriptorPath());
        assertTrue(registerModel.getDateUploaded().isAfter(Instant.now().minusSeconds(30)));
        assertTrue(registerModel.getDateRegistered().isAfter(Instant.now().minusSeconds(30)));
        assertEquals(ComponentState.REGISTERED, registerModel.getComponentState());
    }


    @Test
    public void doesNotChangeAnythingWhenExistingUnmanagedComponentIsIdentical() throws IOException, ComponentRegistrationException {
        RegisterComponentModel existingUnmanaged = new RegisterComponentModel();
        existingUnmanaged.setManaged(false);
        existingUnmanaged.setJsonDescriptorPath(DESCRIPTOR_PATH);
        JsonComponentDescriptor existingDescriptor = TestDescriptorFactory.getWithCustomPipeline();

        when(_mockStateService.getByComponentName(COMPONENT_NAME))
                .thenReturn(Optional.of(existingUnmanaged));
        when(_mockObjectMapper.readValue(new File(DESCRIPTOR_PATH), JsonComponentDescriptor.class))
                .thenReturn(existingDescriptor);

        JsonComponentDescriptor newDescriptor = TestDescriptorFactory.getWithCustomPipeline();
        boolean wasModified = _addComponentService.registerUnmanagedComponent(newDescriptor);
        assertFalse(wasModified);


        verifyNoInteractions(_mockRemoveComponentService, _mockDescriptorValidator,
                               _mockPipelineService, _mockNodeManager);

        verify(_mockObjectMapper, never())
                .writeValue(any(File.class), any());

        verify(_mockStateService, never())
                .update(any());
    }


    @Test(expected = DuplicateComponentException.class)
    public void doesNotOverwriteManagedComponentWithUnmanaged() throws ComponentRegistrationException {
        RegisterComponentModel existingManaged = new RegisterComponentModel();
        existingManaged.setManaged(true);

        when(_mockStateService.getByComponentName(COMPONENT_NAME))
                .thenReturn(Optional.of(existingManaged));

        JsonComponentDescriptor descriptor = TestDescriptorFactory.getWithCustomPipeline();
        _addComponentService.registerUnmanagedComponent(descriptor);
    }


    @Test
    public void removesInvalidDescriptorFileWhenRegisteringUnmanagedComponentFails() throws ComponentRegistrationException {
        when(_mockStateService.getByComponentName(COMPONENT_NAME))
                .thenReturn(Optional.empty());

        JsonComponentDescriptor descriptor = TestDescriptorFactory.getWithCustomPipeline();

        doThrow(InvalidComponentDescriptorException.class)
                .when(_mockDescriptorValidator).validate(descriptor);


        try {
            _addComponentService.registerUnmanagedComponent(descriptor);
            fail("Expected InvalidComponentDescriptorException");
        }
        catch (InvalidComponentDescriptorException ignored) {
        }

        verifyNoInteractions(_mockObjectMapper);
        verify(_mockStateService, never())
                .update(any());
    }


    @Test
    public void doesNotDeleteAlgorithmIfFailedToAdd() throws ComponentRegistrationException, IOException {
        // Arrange
        JsonComponentDescriptor descriptor = TestDescriptorFactory.getWithCustomPipeline();

        setUpMocksForDescriptor(descriptor);

        doThrow(WfmProcessingException.class)
                .when(_mockPipelineService).save(any(Algorithm.class));

        // Act
        try {
            _addComponentService.registerComponent(_testPackageName);
            fail("register component should have thrown an exception");
        }
        catch (ComponentRegistrationSubsystemException ignored) {
        }

        // Assert
        verify(_mockRemoveComponentService, never())
                .deleteCustomPipelines(any());
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
                .save(any(Algorithm.class));

        verify(_mockPipelineService, never())
                .save(any(Action.class));
        assertUndeployed(COMPONENT_NAME);
    }

    @Test
    public void throwsExceptionWhenInvalidCustomPipelines() throws ComponentRegistrationException, IOException {
        JsonComponentDescriptor descriptor = TestDescriptorFactory.getWithCustomPipeline();
        setUpMocksForDescriptor(descriptor);

        doThrow(InvalidPipelineException.class)
                .when(_mockPipelineService)
                .save(eq(descriptor.algorithm()));
        try {
            _addComponentService.registerComponent(_testPackageName);
            fail();
        }
        catch (ComponentRegistrationSubsystemException ex) {
            assertThat(ex.getCause(), instanceOf(InvalidPipelineException.class));
        }


        verify(_mockPipelineService, never())
                .save(any(Action.class));
        verify(_mockPipelineService, never())
                .save(any(Task.class));
        verify(_mockPipelineService, never())
                .save(any(Pipeline.class));

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
