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

import static org.mitre.mpf.test.TestUtil.collectionContaining;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.rest.api.component.ComponentState;
import org.mitre.mpf.rest.api.component.RegisterComponentModel;
import org.mitre.mpf.rest.api.node.NodeManagerModel;
import org.mitre.mpf.rest.api.node.ServiceModel;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.wfm.service.NodeManagerService;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

public class TestComponentReRegisterService extends MockitoTest.Lenient {

    @InjectMocks
    private ComponentReRegisterServiceImpl _reRegisterService;

    private static final Path _componentUploadDir = Paths.get("/tmp/upload");

    private PropertiesUtil _mockPropertiesUtil;

    @Mock
    private ComponentStateService _mockStateService;

    @Mock
    private NodeManagerService _mockNodeManagerService;

    @Mock
    private AddComponentService _mockAddComponentService;

    @Mock
    private RemoveComponentService _mockRemoveComponentService;

    private RegisterComponentModel _componentToReRegister;

    private static final String _packageToReRegister = "ReRegisterMe.tar.gz";
    private static final Path _packageToReRegisterPath = _componentUploadDir.resolve(_packageToReRegister);

    private static final Path _existingComponent1Path = _componentUploadDir.resolve("ExistingComponent1.tar.gz");

    private static final Path _existingComponent2Path
            = Paths.get("/tmp/plugins/ExistingComponent2/descriptor.json");


    @Before
    public void init() throws ComponentRegistrationException {
        _mockPropertiesUtil = mock(PropertiesUtil.class);
        when(_mockPropertiesUtil.getUploadedComponentsDirectory())
                .thenReturn(_componentUploadDir.toFile());


        _componentToReRegister = new RegisterComponentModel();
        _componentToReRegister.setFullUploadedFilePath(_packageToReRegisterPath.toString());
        _componentToReRegister.setPackageFileName(_packageToReRegister);
        _componentToReRegister.setComponentState(ComponentState.REGISTERED);
        _componentToReRegister.setServiceName("ReRegisterMeService");

        when(_mockStateService.getByPackageFile(_packageToReRegister))
                .thenReturn(Optional.of(_componentToReRegister));

        when(_mockAddComponentService.registerComponent(_packageToReRegister))
                .thenReturn(_componentToReRegister);
    }



    private List<RegisterComponentModel> addDefaultOrderTestComponents() {
        RegisterComponentModel existingComponent1 = new RegisterComponentModel();
        existingComponent1.setFullUploadedFilePath(_existingComponent1Path.toString());
        existingComponent1.setPackageFileName(_existingComponent1Path.getFileName().toString());
        existingComponent1.setComponentState(ComponentState.REGISTERED);

        RegisterComponentModel existingComponent2 = new RegisterComponentModel();
        existingComponent2.setJsonDescriptorPath(_existingComponent2Path.toString());
        existingComponent2.setComponentState(ComponentState.REGISTERED);

        RegisterComponentModel existingComponent3 = new RegisterComponentModel();
        existingComponent3.setFullUploadedFilePath("/tmp/upload/ExistingComponent3.tar.gz");
        existingComponent3.setPackageFileName("ExistingComponent3.tar.gz");
        existingComponent3.setComponentState(ComponentState.REGISTER_ERROR);

        List<RegisterComponentModel> components = Lists.newArrayList(
                existingComponent1, existingComponent2, existingComponent3, _componentToReRegister);


        when(_mockStateService.get())
                .thenReturn(components);
        return components;
    }



    @Test
    public void onlyAddsComponentIfNotRegistered() throws ComponentRegistrationException, IOException {
        when(_mockStateService.getByPackageFile(any()))
                .thenReturn(Optional.empty());

        _reRegisterService.reRegisterComponent(_packageToReRegister);

        verify(_mockAddComponentService)
                .registerComponent(_packageToReRegister);
        verify(_mockRemoveComponentService, never())
                .unregisterRetainPackage(any());

        assertServicesNotUpdated();
    }


    @Test
    public void doesNotAddServicesIfComponentDoesNotHaveServices() throws ComponentRegistrationException, IOException {
        _componentToReRegister.setServiceName(null);

        _reRegisterService.reRegisterComponent(_packageToReRegister);

        assertRemovedThenAdded(_packageToReRegister);
        assertServicesNotUpdated();
    }


    @Test
    public void addsServicesBackAfterReRegister() throws ComponentRegistrationException, IOException {
        int serviceCount = 5;
        String serviceName = _componentToReRegister.getServiceName();

        Map<String, ServiceModel> existingSvcModels = ImmutableMap.of(
                _componentToReRegister.getServiceName(), createServiceModel(serviceName, serviceCount),
                "OtherService", createServiceModel("OtherService", 4));

        List<NodeManagerModel> existingNodes = createNodeManagerModels(serviceName, serviceCount);

        Map<String, ServiceModel> afterReRegSvcModels = ImmutableMap.of(
                _componentToReRegister.getServiceName(), createServiceModel(serviceName, 0),
                "OtherService", createServiceModel("OtherService", 4));

        List<NodeManagerModel> afterReRegNodes = createNodeManagerModels(serviceName, -1);

        when(_mockNodeManagerService.getServiceModels())
                .thenReturn(existingSvcModels, afterReRegSvcModels);
        when(_mockNodeManagerService.getNodeManagerModels())
                .thenReturn(existingNodes, afterReRegNodes);


        _reRegisterService.reRegisterComponent(_packageToReRegister);

        assertRemovedThenAdded(_packageToReRegister);
        assertServiceModelSaved(_componentToReRegister.getServiceName(), serviceCount);
        assertServiceAddedToNode("localhost", serviceName, serviceCount);
        assertServiceAddedToNode("SomeHost", serviceName, serviceCount);
    }



    private static ServiceModel createServiceModel(String name, int count) {
        ServiceModel service = new ServiceModel();
        service.setServiceName(name);
        service.setServiceCount(count);
        return service;
    }

    private static List<NodeManagerModel> createNodeManagerModels(String svcName, int count) {
        NodeManagerModel localNode = new NodeManagerModel("localhost");
        localNode.getServices().add(createServiceModel("OtherServices", 20));

        NodeManagerModel someNode = new NodeManagerModel("SomeHost");
        someNode.getServices().add(createServiceModel("OtherServices", 20));

        if (count >= 0) {
            localNode.getServices().add(createServiceModel(svcName, count));
            someNode.getServices().add(createServiceModel(svcName, count));
        }

        return Arrays.asList(localNode, someNode);
    }



    private void assertRemovedThenAdded(String componentPackage) throws ComponentRegistrationException {
        InOrder inOder = inOrder(_mockRemoveComponentService, _mockAddComponentService);

        inOder.verify(_mockRemoveComponentService)
                .unregisterRetainPackage(componentPackage);

        inOder.verify(_mockAddComponentService)
                .registerComponent(componentPackage);
    }


    private void assertServicesNotUpdated() throws IOException {
        verify(_mockNodeManagerService, never())
                .saveAndReloadNodeManagerConfig(any());
        verify(_mockNodeManagerService, never())
                .setServiceModels(any());
    }

    private void assertServiceModelSaved(String name, int count) {
        verify(_mockNodeManagerService)
                .setServiceModels(argThat(m -> m.get(name).getServiceCount() == count));
    }


    private void assertServiceAddedToNode(String nodeHost, String serviceName, int serviceCount) throws IOException {
        verify(_mockNodeManagerService)
                .saveAndReloadNodeManagerConfig(collectionContaining(
                        n -> n.getHost().equals(nodeHost) && nodeHasService(n, serviceName, serviceCount)));
    }


    private static boolean nodeHasService(NodeManagerModel node, String serviceName, int serviceCount) {
        return node.getServices()
                .stream()
                .anyMatch(sm -> serviceName.equals(sm.getServiceName()) && sm.getServiceCount() == serviceCount);
    }

}
