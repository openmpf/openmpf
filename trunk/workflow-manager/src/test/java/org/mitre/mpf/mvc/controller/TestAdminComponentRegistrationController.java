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

package org.mitre.mpf.mvc.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.rest.api.ResponseMessage;
import org.mitre.mpf.rest.api.component.ComponentState;
import org.mitre.mpf.rest.api.component.RegisterComponentModel;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.wfm.service.component.AddComponentService;
import org.mitre.mpf.wfm.service.component.ComponentReRegisterService;
import org.mitre.mpf.wfm.service.component.ComponentRegistrationException;
import org.mitre.mpf.wfm.service.component.ComponentRegistrationStatusException;
import org.mitre.mpf.wfm.service.component.ComponentStateService;
import org.mitre.mpf.wfm.service.component.DuplicateComponentException;
import org.mitre.mpf.wfm.service.component.JsonComponentDescriptor;
import org.mitre.mpf.wfm.service.component.ManagedComponentsUnsupportedException;
import org.mitre.mpf.wfm.service.component.RemoveComponentService;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

public class TestAdminComponentRegistrationController extends MockitoTest.Lenient {

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

        List<RegisterComponentModel> result = _controller.getComponentsRest();

        assertEquals(new HashSet<>(_models), new HashSet<>(result));
    }

    @Test
    public void returnsEmptyListWhenNoComponents() {
        when(_mockStateService.get())
                .thenReturn(Collections.emptyList());

        List<RegisterComponentModel> result = _controller.getComponentsRest();

        assertTrue(result.isEmpty());
    }


    @Test
    public void canGetSingleComponent() {
        when(_mockStateService.getByPackageFile(_testPackageName))
                .thenReturn(Optional.of(_testModel));

        ResponseEntity<RegisterComponentModel> result = _controller.getComponentRest(_testPackageName);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertCorrectModel(result.getBody());
    }


    @Test
    public void returns404WhenNoMatchingComponent() {
        when(_mockStateService.getByPackageFile(_testPackageName))
                .thenReturn(Optional.empty());

        ResponseEntity<RegisterComponentModel> result = _controller.getComponentRest(_testPackageName);
        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }


    @Test
    public void canRegisterComponent() throws ComponentRegistrationException {
        when(_mockStateService.getByPackageFile(_testPackageName))
                .thenReturn(Optional.of(_testModel));

        ResponseEntity<?> result = _controller.registerComponentRest(_testPackageName);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertCorrectModel((RegisterComponentModel) result.getBody());

        verify(_mockAddComponentService)
                .registerComponent(_testPackageName);
    }


    @Test
    public void canRegisterNewUnmanagedComponent() throws ComponentRegistrationException {
        verifyUnmanagedComponentRegistered(null, true,
                                           HttpStatus.CREATED, "New component registered.");
    }


    @Test
    public void canRegisterDuplicateUnmanagedComponent() throws ComponentRegistrationException {
        verifyUnmanagedComponentRegistered(new RegisterComponentModel(), false,
                                           HttpStatus.OK, "Component already registered.");
    }

    @Test
    public void canModifyExistingUnmanagedComponent() throws ComponentRegistrationException {
        verifyUnmanagedComponentRegistered(new RegisterComponentModel(), true,
                                           HttpStatus.OK, "Modified existing component.");
    }

    private void verifyUnmanagedComponentRegistered(
            RegisterComponentModel rcm, boolean wasReregistered,
            HttpStatus expectedStatus, String expectedResponseMessage) throws ComponentRegistrationException {

        when(_mockStateService.getByComponentName(_testComponentName))
                .thenReturn(Optional.ofNullable(rcm));

        JsonComponentDescriptor descriptor = mock(JsonComponentDescriptor.class);
        when(descriptor.getComponentName())
                .thenReturn(_testComponentName);

        when(_mockAddComponentService.registerUnmanagedComponent(descriptor))
                .thenReturn(wasReregistered);

        ResponseMessage response = _controller.registerUnmanagedComponent(descriptor);
        assertEquals(expectedStatus, response.getStatusCode());
        assertEquals(expectedResponseMessage, response.getBody().getMessage());

        verify(_mockAddComponentService)
                .registerUnmanagedComponent(descriptor);

    }


    @Test
    public void canReRegisterComponent() throws ComponentRegistrationException {
        when(_mockReRegisterService.reRegisterComponent(_testPackageName))
                .thenReturn(_testModel);

        ResponseEntity<?> result = _controller.reRegisterRest(_testPackageName);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(_testModel, result.getBody());
    }


    @Test
    public void registerReturnsErrorResponseWhenAlreadyRegistered() throws ComponentRegistrationException {
        doThrow(new ComponentRegistrationStatusException(ComponentState.REGISTERED))
                .when(_mockAddComponentService)
                .registerComponent(_testPackageName);

        ResponseEntity<?> result = _controller.registerComponentRest(_testPackageName);
        assertEquals(HttpStatus.CONFLICT, result.getStatusCode());
    }


    @Test
    public void registerReturnsErrorWhenUploadError() throws ComponentRegistrationException {
        doThrow(new ComponentRegistrationStatusException(ComponentState.UPLOAD_ERROR))
                .when(_mockAddComponentService)
                .registerComponent(_testPackageName);

        ResponseEntity<?> result = _controller.registerComponentRest(_testPackageName);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
    }


    @Test
    public void registerReturnsErrorWhenSameTld() throws ComponentRegistrationException {
        doThrow(new DuplicateComponentException(_testPackageName))
                .when(_mockAddComponentService)
                .registerComponent(_testPackageName);

        ResponseEntity<?> result = _controller.registerComponentRest(_testPackageName);
        assertEquals(HttpStatus.CONFLICT, result.getStatusCode());
    }


    @Test
    public void returnsErrorWhenUnregisterUnknownComponent() {
        when(_mockStateService.getByComponentName(_testComponentName))
                .thenReturn(Optional.empty());

        ResponseEntity<?> result = _controller.removeComponentRest(_testComponentName);

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }


    @Test
    public void canRemoveComponent() throws ManagedComponentsUnsupportedException {
        when(_mockStateService.getByComponentName(_testComponentName))
                .thenReturn(Optional.of(_testModel));

        ResponseEntity<?> result = _controller.removeComponentRest(_testComponentName);

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(_mockRemoveComponentService)
                .removeComponent(_testComponentName);
    }

    @Test
    public void canRemoveComponentByPackageName() throws ManagedComponentsUnsupportedException {
        _controller.removeComponentPackageRest(_testPackageName);
        verify(_mockRemoveComponentService)
                .removePackage(_testPackageName);
    }


    @Test
    public void uploadReturnsErrorWhenPackageIsDuplicate() throws IOException {
        File file = _tempFolder.newFile(_testPackageName);

        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename())
                .thenReturn(file.getName());

        ResponseEntity<?> result = _controller.uploadComponentRest(mockFile);

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

        ResponseEntity<?> result = _controller.uploadComponentRest(mockFile);

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

        ResponseEntity<?> result = _controller.uploadComponentRest(mockFile);
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
                .transferTo(any(File.class));
        doThrow(new IOException())
                .when(mockFile)
                .transferTo(any(Path.class));

        ResponseEntity<?> result = _controller.uploadComponentRest(mockFile);

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

        ResponseEntity<?> result = _controller.uploadComponentRest(mockFile);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(mockFile)
                .transferTo((File)notNull());

    }

    private void assertCorrectModel(RegisterComponentModel rcm) {
        assertEquals(_testModel.getPackageFileName(), rcm.getPackageFileName());
        assertEquals(_testModel.getComponentName(), rcm.getComponentName());
    }

    private void verifyNotSaved(MultipartFile mockFile) throws IOException {
        verify(mockFile, never())
                .transferTo(any(File.class));
        verify(mockFile, never())
                .transferTo(any(Path.class));
        verify(_mockStateService, never())
                .addEntryForUploadedPackage(any());
    }
}

