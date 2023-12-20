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

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mitre.mpf.test.TestUtil.collectionContaining;
import static org.mitre.mpf.test.TestUtil.nonEmptyCollection;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.rest.api.component.ComponentState;
import org.mitre.mpf.rest.api.component.RegisterComponentModel;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.wfm.util.ObjectMapperFactory;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TestStartupComponentRegistrationService extends MockitoTest.Strict {

    private StartupComponentRegistrationServiceImpl _startupRegisterSvc;

    @Mock
    private PropertiesUtil _mockPropertiesUtil;

    @Mock
    private ComponentStateService _mockComponentStateSvc;

    @Mock
    private AddComponentService _mockAddComponentSvc;

    @Mock
    private StartupComponentServiceStarter _mockServiceStarter;

    private final ObjectMapper _objectMapper = ObjectMapperFactory.customObjectMapper();

    @Rule
    public TemporaryFolder _componentUploadDir = new TemporaryFolder();

    @Rule
    public TemporaryFolder _pluginDeploymentDir = new TemporaryFolder();

    @Before
    public void init() throws IOException, ComponentRegistrationException {
        _componentUploadDir.newFolder("test");
        _componentUploadDir.newFile("bad.bad");

        when(_mockPropertiesUtil.getUploadedComponentsDirectory())
                .thenReturn(_componentUploadDir.getRoot());
        when(_mockPropertiesUtil.getPluginDeploymentPath())
                .thenReturn(_pluginDeploymentDir.getRoot().toPath());
        when(_mockPropertiesUtil.isStartupAutoRegistrationSkipped())
                .thenReturn(false);

        _startupRegisterSvc = new StartupComponentRegistrationServiceImpl(
                _mockPropertiesUtil, _mockComponentStateSvc, _mockAddComponentSvc, Optional.of(_mockServiceStarter),
                _objectMapper);

        lenient().when(_mockAddComponentSvc.registerComponent(notNull()))
                .thenAnswer(invocation -> {
                    String arg = invocation.getArgument(0);
                    String componentName = componentPackageToName(arg);

                    RegisterComponentModel result = new RegisterComponentModel();
                    result.setComponentState(ComponentState.REGISTERED);
                    result.setComponentName(componentName);
                    return result;
                });
    }


    @Test
    public void doesNothingWhenNoPackagesAndNoneRegistered() throws ComponentRegistrationException {
        _startupRegisterSvc.registerUnregisteredComponents();

        assertNoneRegistered();
    }


    @Test
    public void doesNothingWhenNoPackagesAndSomeRegistered() throws ComponentRegistrationException {
        when(_mockComponentStateSvc.get())
                .thenReturn(Arrays.asList(new RegisterComponentModel(), new RegisterComponentModel()));

        _startupRegisterSvc.registerUnregisteredComponents();

        assertNoneRegistered();
    }


    @Test
    public void doesNothingWhenAllPackagesRegistered() throws IOException, ComponentRegistrationException {
        List<Path> packages = addComponentPackages("Test1", "Test2", "Test3");

        List<RegisterComponentModel> components = packages.stream()
                .map(TestStartupComponentRegistrationService::createRegisteredComponent)
                .collect(toList());
        when(_mockComponentStateSvc.get())
                .thenReturn(components);

        _startupRegisterSvc.registerUnregisteredComponents();

        assertNoneRegistered();
    }



    @Test
    public void registersAllPackagesWhenNoneRegistered() throws IOException, ComponentRegistrationException {
        List<Path> packages = addComponentPackages("Test1", "Test2", "Test3");
        _startupRegisterSvc.registerUnregisteredComponents();
        assertAllRegistered(packages);
    }


    @Test
    public void onlyRegistersUnregisteredPackages() throws IOException, ComponentRegistrationException {
        List<Path> packages = addComponentPackages("Test1", "Test2", "Test3");
        List<RegisterComponentModel> registeredComponents = Arrays.asList(
                createRegisteredComponent(packages.get(1)),
                createRegisteredComponent(Paths.get("/tmp/fake.tar.gz")));

        when(_mockComponentStateSvc.get())
                .thenReturn(registeredComponents);

        _startupRegisterSvc.registerUnregisteredComponents();
        assertAllRegistered(Arrays.asList(packages.get(0), packages.get(2)));
        assertRegistrationNotAttempted(packages.get(1));

        assertServicesStarted(Arrays.asList(packages.get(0), packages.get(2)));
        assertServiceNotStarted(packages.get(1));
    }


    @Test
    public void doesNotReRegisterComponentsInErrorState() throws IOException, ComponentRegistrationException {
        List<Path> packages = addComponentPackages("RegError", "Test2", "Test3");
        RegisterComponentModel errorRcm = createRegisterModel(packages.get(0), ComponentState.REGISTER_ERROR);
        when(_mockComponentStateSvc.get())
                .thenReturn(Collections.singletonList(errorRcm));

        _startupRegisterSvc.registerUnregisteredComponents();
        assertAllRegistered(packages.subList(1, 3));
        assertRegistrationNotAttempted(packages.get(0));

        assertServicesStarted(packages.subList(1, 3));
        assertServiceNotStarted(packages.get(0));
    }


    @Test
    public void doesNotStopRegistrationAfterFirstFailure() throws IOException, ComponentRegistrationException {
        List<Path> packages = addComponentPackages("ATest1", "RegError", "Test3");

        when(_mockAddComponentSvc.registerComponent(packages.get(1).getFileName().toString()))
                .thenThrow(ComponentRegistrationSubsystemException.class);

        _startupRegisterSvc.registerUnregisteredComponents();

        assertRegistered(packages.get(0));
        assertRegistered(packages.get(2));

        assertServiceNotStarted(packages.get(1));
    }


    @Test
    public void canRegisterPreDeployedComponents() throws IOException, ComponentRegistrationException {
        List<Path> packages = addComponentPackages("Deployed", "NotDeployed");
        _pluginDeploymentDir.newFolder("Deployed", "descriptor");
        File deployedDescriptor = _pluginDeploymentDir.newFile("Deployed/descriptor/descriptor.json");

        _pluginDeploymentDir.newFolder("DescriptorOnly", "descriptor");
        File descriptorOnly = _pluginDeploymentDir.newFile("DescriptorOnly/descriptor/descriptor.json");
        var testDescriptor = TestDescriptorFactory.getWithCustomPipeline();
        _objectMapper.writeValue(descriptorOnly, testDescriptor);

        _startupRegisterSvc.registerUnregisteredComponents();

        verify(_mockComponentStateSvc)
                .addEntryForDeployedPackage(packages.get(0), deployedDescriptor.toPath());
        verify(_mockAddComponentSvc)
                .registerComponent("Deployed.tar.gz");

        verify(_mockComponentStateSvc)
                .addEntryForUploadedPackage(packages.get(1));
        verify(_mockAddComponentSvc)
                .registerComponent("NotDeployed.tar.gz");

        verify(_mockAddComponentSvc)
                .registerUnmanagedComponent(eq(testDescriptor));

        var rcmListCaptor = ArgumentCaptor.forClass(List.class);

        verify(_mockServiceStarter)
                .startServicesForComponents((List<RegisterComponentModel>) rcmListCaptor.capture());

        // DescriptorOnly is an unmanaged component and therefore the WFM will not have attempted to start that service.
        var rcmList = (List<RegisterComponentModel>) rcmListCaptor.getValue();
        assertEquals(2, rcmList.size());
        assertTrue(rcmList.stream()
                           .anyMatch(rcm -> rcm.getComponentName().equals("Deployed")));
        assertTrue(rcmList.stream()
                           .anyMatch(rcm -> rcm.getComponentName().equals("NotDeployed")));
    }


    private void assertNoneRegistered() throws ComponentRegistrationException {
        verify(_mockComponentStateSvc, never())
                .addEntryForUploadedPackage(any());

        verify(_mockAddComponentSvc, never())
                .registerComponent(any());

        verify(_mockServiceStarter, never())
                .startServicesForComponents(nonEmptyCollection());
    }



    private void assertAllRegistered(Collection<Path> componentPackages) throws ComponentRegistrationException {
        for (Path componentPackage : componentPackages) {
            assertRegistered(componentPackage);
        }
    }

    private void assertRegistered(Path componentPackage) throws ComponentRegistrationException {
        verify(_mockComponentStateSvc)
                .addEntryForUploadedPackage(componentPackage);
        verify(_mockAddComponentSvc)
                .registerComponent(componentPackage.getFileName().toString());
        assertServiceStarted(componentPackage);
    }


    private void assertRegistrationNotAttempted(Path componentPackage) throws ComponentRegistrationException {
        verify(_mockComponentStateSvc, never())
                .addEntryForUploadedPackage(componentPackage);
        verify(_mockAddComponentSvc, never())
                .registerComponent(componentPackage.getFileName().toString());
    }


    private void assertServicesStarted(Iterable<Path> componentPackages) {
        for (Path packagePath : componentPackages) {
            assertServiceStarted(packagePath);
        }
    }

    private void assertServiceStarted(Path componentPackage) {
        String name = componentPackageToName(componentPackage);
        verify(_mockServiceStarter)
                .startServicesForComponents(
                        collectionContaining(rcm -> rcm.getComponentName().equals(name)));
    }

    private void assertServiceNotStarted(Path componentPackage) {
        String name = componentPackageToName(componentPackage);
        verify(_mockServiceStarter, never())
                .startServicesForComponents(
                        collectionContaining(rcm -> rcm.getComponentName().equals(name)));
    }


    private List<Path> addComponentPackages(String... names) throws IOException {
        List<Path> results = new ArrayList<>();
        for (String name : names) {
            Path packagePath = _componentUploadDir.newFile(name + ".tar.gz").toPath();

            try (TarArchiveOutputStream outputStream
                         = new TarArchiveOutputStream(new GZIPOutputStream(Files.newOutputStream(packagePath)))) {
                ArchiveEntry entry = new TarArchiveEntry(name + "/descriptor");
                outputStream.putArchiveEntry(entry);
                outputStream.closeArchiveEntry();
            }
            results.add(packagePath);
        }
        return results;
    }


    private static RegisterComponentModel createRegisteredComponent(Path packagePath) {
        return createRegisterModel(packagePath, ComponentState.REGISTERED);
    }


    private static RegisterComponentModel createRegisterModel(Path packagePath, ComponentState state) {
        RegisterComponentModel result = new RegisterComponentModel();
        result.setFullUploadedFilePath(packagePath.toString());
        result.setJsonDescriptorPath("/tmp/" + packagePath.getFileName() + ".json");
        result.setComponentState(state);
        return result;
    }


    private static String componentPackageToName(String packageFileName) {
        return packageFileName.replace(".tar.gz", "");
    }

    private static String componentPackageToName(Path packagePath) {
        return componentPackageToName(packagePath.getFileName().toString());
    }
}
