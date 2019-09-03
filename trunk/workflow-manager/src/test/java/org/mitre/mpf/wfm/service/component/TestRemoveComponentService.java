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

package org.mitre.mpf.wfm.service.component;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.rest.api.component.ComponentState;
import org.mitre.mpf.rest.api.component.RegisterComponentModel;
import org.mitre.mpf.rest.api.node.NodeManagerModel;
import org.mitre.mpf.rest.api.node.ServiceModel;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.Pipeline;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.wfm.service.pipeline.PipelineService;
import org.mitre.mpf.wfm.service.NodeManagerService;
import org.mitre.mpf.wfm.service.StreamingServiceManager;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mitre.mpf.wfm.service.component.TestDescriptorConstants.COMPONENT_NAME;
import static org.mitre.mpf.wfm.service.component.TestDescriptorConstants.DESCRIPTOR_PATH;
import static org.mockito.Mockito.*;

public class TestRemoveComponentService {

    @InjectMocks
    private RemoveComponentServiceImpl _removeComponentService;

    @Mock
    private NodeManagerService _mockNodeManager;

    @Mock
    private StreamingServiceManager _mockStreamingServiceManager;

    @Mock
    private ComponentDeploymentService _mockDeploymentService;

    @Mock
    private ComponentStateService _mockStateService;

    @Mock
    private PipelineService _mockPipelineService;

    @Rule
    public TemporaryFolder _tempDir = new TemporaryFolder();

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testRemoveComponentHappyPath() throws IOException {
        // Arrange
        JsonComponentDescriptor descriptor = TestDescriptorFactory.get();
        String serviceName = descriptor.getAlgorithm().getName();
        String algoName = descriptor.getAlgorithm().getName();

        RegisterComponentModel rcm = new RegisterComponentModel();
        rcm.setComponentState(ComponentState.REGISTERED);
        rcm.setComponentName(COMPONENT_NAME);
        rcm.setServiceName(serviceName);
        rcm.setStreamingServiceName(serviceName);
        rcm.setAlgorithmName(algoName);
        rcm.setJsonDescriptorPath(DESCRIPTOR_PATH);

        ServiceModel serviceModel = new ServiceModel();
        serviceModel.setServiceName(serviceName);
        ServiceModel serviceModel2 = new ServiceModel();
        serviceModel2.setServiceName("Other service");

        NodeManagerModel nodeManagerModel = new NodeManagerModel();
        nodeManagerModel.getServices().add(serviceModel);
        nodeManagerModel.getServices().add(serviceModel2);

        NodeManagerModel nodeManagerModel2 = new NodeManagerModel();
        nodeManagerModel2.getServices().add(serviceModel2);
        List<NodeManagerModel> nodeManagerModels = Arrays.asList(nodeManagerModel, nodeManagerModel2);

        when(_mockNodeManager.getNodeManagerModels())
                .thenReturn(nodeManagerModels);

        when(_mockStateService.getByComponentName(COMPONENT_NAME))
                .thenReturn(Optional.of(rcm));

        Action componentAction = new Action("component action", "description", algoName,
                                            Collections.emptyList());
        Action componentAction2 = new Action("component action2", "description", algoName,
                                             Collections.emptyList());
        Action otherAction = new Action("other action", "description", "foo",
                                    Collections.emptyList());
        when(_mockPipelineService.getActions())
                .thenReturn(ImmutableList.of(componentAction, componentAction2, otherAction));

        rcm.getActions().add(componentAction.getName());
        rcm.getActions().add(componentAction2.getName());

        Task componentTask = new Task("component task", "description",
                                      Collections.singleton(otherAction.getName()));
        Task otherTask = new Task("task1-name", "description",
                                  Collections.singleton(componentAction.getName()));
        when(_mockPipelineService.getTasks())
                .thenReturn(ImmutableList.of(
                        componentTask, otherTask,
                        new Task("asdf", "description", Collections.emptyList())));
        rcm.getTasks().add(componentTask.getName());

        Pipeline componentPipeline = new Pipeline("component pipeline", "description",
                                                  Collections.singleton(otherTask.getName()));
        Pipeline otherPipeline = new Pipeline("pipeline1-name", "description",
                                              Collections.singleton(otherTask.getName()));
        when(_mockPipelineService.getPipelines())
                .thenReturn(ImmutableList.of(
                        componentPipeline, otherPipeline,
                        new Pipeline("sdaf", "description", Collections.emptyList())));
        rcm.getPipelines().add(componentPipeline.getName());

        // Act
        _removeComponentService.removeComponent(COMPONENT_NAME);

        // Assert

        verify(_mockStateService)
                .removeComponent(COMPONENT_NAME);

        verify(_mockNodeManager)
                .saveAndReloadNodeManagerConfig(argThat(nodes -> nodes.contains(nodeManagerModel)
                        && nodes.contains(nodeManagerModel2)
                        && !nodeManagerModel.getServices().contains(serviceModel)
                        && nodeManagerModel.getServices().contains(serviceModel2)));
        verify(_mockNodeManager)
                .removeService(serviceName);

        verify(_mockStreamingServiceManager)
                .deleteService(serviceName);

        verify(_mockPipelineService)
                .deleteAlgorithm(algoName);

        verify(_mockPipelineService)
                .deleteAction(componentAction.getName());

        verify(_mockPipelineService)
                .deleteAction(componentAction2.getName());

        verify(_mockPipelineService)
                .deleteTask(componentTask.getName());

        verify(_mockPipelineService)
                .deletePipeline(componentPipeline.getName());

        verifyNoMoreInteractions(_mockPipelineService);

        verify(_mockDeploymentService)
                .undeployComponent(COMPONENT_NAME);

        verify(_mockStateService)
                .removeComponent(COMPONENT_NAME);
    }


    @Test
    public void testRemoveUnmanagedComponent() throws IOException {
        Path pluginsDir = _tempDir.newFolder("plugins").toPath();
        Path otherPluginDir = pluginsDir.resolve("other-plugin");
        Files.createDirectory(otherPluginDir);

        Path testComponentDir = pluginsDir.resolve(COMPONENT_NAME);
        Path testDescriptorDir = testComponentDir.resolve("descriptor");
        Files.createDirectories(testDescriptorDir);
        Path testDescriptor = testDescriptorDir.resolve("descriptor.json");
        Files.write(testDescriptor, Collections.singletonList("hello world"));


        RegisterComponentModel rcm = new RegisterComponentModel();
        rcm.setJsonDescriptorPath(testDescriptor.toString());
        rcm.setManaged(false);
        rcm.setComponentName(COMPONENT_NAME);
        rcm.setAlgorithmName("TEST ALGO");
        rcm.setPipelines(Collections.singletonList("TEST PIPELINE"));
        when(_mockStateService.getByComponentName(COMPONENT_NAME))
                .thenReturn(Optional.of(rcm));

        _removeComponentService.removeComponent(COMPONENT_NAME);

        verify(_mockStateService)
                .removeComponent(COMPONENT_NAME);
        // Pipeline deletion is more extensively tested in testRecursiveDelete
        verify(_mockPipelineService)
                .deleteAlgorithm("TEST ALGO");
        verify(_mockPipelineService)
                .deletePipeline("TEST PIPELINE");

        verifyZeroInteractions(_mockNodeManager, _mockStreamingServiceManager, _mockDeploymentService);

        assertTrue(Files.exists(otherPluginDir));
        assertFalse(Files.exists(testComponentDir));
    }
}

