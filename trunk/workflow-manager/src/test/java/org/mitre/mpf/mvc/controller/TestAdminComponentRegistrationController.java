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

package org.mitre.mpf.mvc.controller;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.rest.api.ResponseMessage;
import org.mitre.mpf.rest.api.component.ComponentState;
import org.mitre.mpf.rest.api.component.RegisterComponentModel;
import org.mitre.mpf.wfm.service.component.*;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TestAdminComponentRegistrationController {

    @InjectMocks
    private AdminComponentRegistrationController _controller;

    @Mock
    private ComponentStateService _mockStateService;

    @Mock
    private AddComponentService _mockAddComponentService;

    @Mock
    private RemoveComponentService _mockRemoveComponentService;

    @Mock
    private ComponentReRegisterService _mockReRegisterService;

    @Mock
    private PropertiesUtil _mockProperties;

    private static final String _testPackageName = "test-package.tar.gz";
    private static final String _testComponentName = "TestComponent";
    private RegisterComponentModel _testModel;
    private List<RegisterComponentModel> _models;

    @Rule
    public TemporaryFolder _tempFolder = new TemporaryFolder();

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        _testModel = new RegisterComponentModel();
        _testModel.setPackageFileName(_testPackageName);
        _testModel.setComponentName(_testComponentName);

        RegisterComponentModel rcm1 = new RegisterComponentModel();
        rcm1.setPackageFileName("package1.tar.gz");
        rcm1.setComponentName("component1");

        RegisterComponentModel rcm2 = new RegisterComponentModel();
        rcm2.setPackageFileName("package2.tar.gz");
        rcm2.setComponentName("component2");
        _models = Arrays.asList(rcm2, rcm1, _testModel);

        when(_mockProperties.getUploadedComponentsDirectory())
                .thenReturn(_tempFolder.getRoot());
    }

    @Test
    public void canGetComponentList() {
        when(_mockStateService.get())
                .thenReturn(_models);

        List<RegisterComponentModel> result = _controller.getComponents();

        assertEquals(new HashSet<>(_models), new HashSet<>(result));
    }

    @Test
    public void returnsEmptyListWhenNoComponents() {
        when(_mockStateService.get())
                .thenReturn(Collections.emptyList());

        List<RegisterComponentModel> result = _controller.getComponents();

        assertTrue(result.isEmpty());
    }


    @Test
    public void canGetSingleComponent() {
        when(_mockStateService.getByPackageFile(_testPackageName))
                .thenReturn(Optional.of(_testModel));

        ResponseEntity<RegisterComponentModel> result = _controller.getComponent(_testPackageName);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertCorrectModel(result.getBody());
    }


    @Test
    public void returns404WhenNoMatchingComponent() {
        when(_mockStateService.getByPackageFile(_testPackageName))
                .thenReturn(Optional.empty());

        ResponseEntity<RegisterComponentModel> result = _controller.getComponent(_testPackageName);
        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }


    @Test
    public void canRegisterComponent() throws ComponentRegistrationException {
        when(_mockStateService.getByPackageFile(_testPackageName))
                .thenReturn(Optional.of(_testModel));

        ResponseEntity<?> result = _controller.registerComponent(_testPackageName);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertCorrectModel((RegisterComponentModel) result.getBody());

        verify(_mockAddComponentService)
                .registerComponent(_testPackageName);
    }


    @Test
    public void canReRegisterComponent() throws ComponentRegistrationException {
        when(_mockReRegisterService.reRegisterComponent(_testPackageName))
                .thenReturn(_testModel);

        ResponseEntity<?> result = _controller.reRegister(_testPackageName);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(_testModel, result.getBody());
    }

    @Test
    public void canGetReRegisterOrder() {
        Path component1 = Paths.get("/tmp/Component1.tar.gz");
        Path component2 = Paths.get("/tmp/Component2.tar.gz");
        when(_mockReRegisterService.getReRegistrationOrder("Component1.tar.gz"))
                .thenReturn(Arrays.asList(component1, component2));

        ResponseEntity<?> response = _controller.getReRegisterOrder("Component1.tar.gz");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<String> reRegisterOrder = (List<String>) response.getBody();
        assertEquals(2, reRegisterOrder.size());
        assertEquals("Component1.tar.gz", reRegisterOrder.get(0));
        assertEquals("Component2.tar.gz", reRegisterOrder.get(1));

    }

    @Test
    public void registerReturnsErrorResponseWhenAlreadyRegistered() throws ComponentRegistrationException {
        doThrow(new ComponentRegistrationStatusException(ComponentState.REGISTERED))
                .when(_mockAddComponentService)
                .registerComponent(_testPackageName);

        ResponseEntity<?> result = _controller.registerComponent(_testPackageName);
        assertEquals(HttpStatus.CONFLICT, result.getStatusCode());
    }


    @Test
    public void registerReturnsErrorWhenUploadError() throws ComponentRegistrationException {
        doThrow(new ComponentRegistrationStatusException(ComponentState.UPLOAD_ERROR))
                .when(_mockAddComponentService)
                .registerComponent(_testPackageName);

        ResponseEntity<?> result = _controller.registerComponent(_testPackageName);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
    }


    @Test
    public void registerReturnsErrorWhenSameTld() throws ComponentRegistrationException {
        doThrow(new DuplicateComponentException(_testPackageName))
                .when(_mockAddComponentService)
                .registerComponent(_testPackageName);

        ResponseEntity<?> result = _controller.registerComponent(_testPackageName);
        assertEquals(HttpStatus.CONFLICT, result.getStatusCode());
    }


    @Test
    public void canRegisterViaFile() throws ComponentRegistrationException {
        String filePath = "/tmp/TestComponent/descriptor/descriptor.json";

        ResponseMessage result = _controller.registerViaFile(filePath);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(_mockAddComponentService)
                .registerDeployedComponent(filePath);
    }


    @Test
    public void returnsErrorWhenUnregisterUnknownComponent() {
        when(_mockStateService.getByComponentName(_testComponentName))
                .thenReturn(Optional.empty());

        ResponseEntity<?> result = _controller.removeComponent(_testComponentName);

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }


    @Test
    public void canRemoveComponent() {
        when(_mockStateService.getByComponentName(_testComponentName))
                .thenReturn(Optional.of(_testModel));

        ResponseEntity<?> result = _controller.removeComponent(_testComponentName);

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(_mockRemoveComponentService)
                .removeComponent(_testComponentName);
    }

    @Test
    public void canRemoveComponentByPackageName() {
        _controller.removeComponentPackage(_testPackageName);
        verify(_mockRemoveComponentService)
                .removePackage(_testPackageName);
    }


    @Test
    public void uploadReturnsErrorWhenPackageIsDuplicate() throws IOException {
        File file = _tempFolder.newFile(_testPackageName);

        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename())
                .thenReturn(file.getName());

        ResponseEntity<?> result = _controller.uploadComponent(mockFile);

        assertEquals(HttpStatus.CONFLICT, result.getStatusCode());
        verifyNotSaved(mockFile);
    }

    @Test
    public void uploadReturnsErrorWhenNoContentType() throws IOException {
        MultipartFile mockFile = mock(MultipartFile.class);

        when(mockFile.getOriginalFilename())
                .thenReturn(_testPackageName);

        when(mockFile.getContentType())
                .thenReturn(null);

        ResponseEntity<?> result = _controller.uploadComponent(mockFile);

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        verifyNotSaved(mockFile);
    }

    @Test
    public void uploadReturnsErrorWhenWrongContentType() throws IOException {
        MultipartFile mockFile = mock(MultipartFile.class);

        when(mockFile.getOriginalFilename())
                .thenReturn(_testPackageName);

        when(mockFile.getContentType())
                .thenReturn("foo");

        ResponseEntity<?> result = _controller.uploadComponent(mockFile);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        verifyNotSaved(mockFile);
    }


    @Test
    public void uploadReturnsErrorWhenSaveFails() throws IOException {
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename())
                .thenReturn(_testPackageName);
        when(mockFile.getContentType())
                .thenReturn("application/gzip");

        doThrow(new IOException())
                .when(mockFile)
                .transferTo(any());

        ResponseEntity<?> result = _controller.uploadComponent(mockFile);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());

        verify(_mockStateService, never())
                .addEntryForUploadedPackage(any());
    }


    @Test
    public void canUploadPackage() throws IOException {

        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename())
                .thenReturn(_testPackageName);
        when(mockFile.getContentType())
                .thenReturn("application/gzip");

        ResponseEntity<?> result = _controller.uploadComponent(mockFile);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(mockFile)
                .transferTo(notNull(File.class));

    }

    private void assertCorrectModel(RegisterComponentModel rcm) {
        assertEquals(_testModel.getPackageFileName(), rcm.getPackageFileName());
        assertEquals(_testModel.getComponentName(), rcm.getComponentName());
    }

    private void verifyNotSaved(MultipartFile mockFile) throws IOException {
        verify(mockFile, never())
                .transferTo(any());
        verify(_mockStateService, never())
                .addEntryForUploadedPackage(any());
    }
}

