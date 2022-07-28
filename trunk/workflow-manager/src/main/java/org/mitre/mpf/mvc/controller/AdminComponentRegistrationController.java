/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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

import com.google.common.collect.ImmutableSet;
import io.swagger.annotations.Api;
import org.mitre.mpf.rest.api.component.RegisterComponentModel;
import org.mitre.mpf.wfm.service.component.*;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;


@Api(value = "Component Registrar",
     description = "External component registration and removal" )
@Controller
@Scope("request")
@Profile("!docker")
public class AdminComponentRegistrationController extends BasicAdminComponentRegistrationController {

    private final PropertiesUtil _propertiesUtil;

    private final AddComponentService _addComponentService;

    private final RemoveComponentService _removeComponentService;

    private final ComponentStateService _componentState;

    private final ComponentReRegisterService _reRegisterService;

    @Inject
    AdminComponentRegistrationController(
            PropertiesUtil propertiesUtil,
            AddComponentService addComponentService,
            RemoveComponentService removeComponentService,
            ComponentStateService componentState,
            ComponentReRegisterService reRegisterService) {
        super(addComponentService, removeComponentService, componentState);
        _propertiesUtil = propertiesUtil;
        _addComponentService = addComponentService;
        _removeComponentService = removeComponentService;
        _componentState = componentState;
        _reRegisterService = reRegisterService;
    }



    /*
     * GET single component info
     */
    //the componentPackageFileName is the filename of the tar.gz file submitted - will require the ending '/'
    @RequestMapping(value = {"/components/{componentPackageFileName:.+}",
            "/rest/components/{componentPackageFileName:.+}"},
            method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<RegisterComponentModel> getComponentRest(
            @PathVariable("componentPackageFileName") String componentPackageFileName)
    {
        return withReadLock(() ->
            _componentState.getByPackageFile(componentPackageFileName)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND)));
    }


    /*
     * POST - register component
     */
    @RequestMapping(value = {"/components/{componentPackageFileName:.+}/register",
            "/rest/components/{componentPackageFileName:.+}/register"},
            method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<?> registerComponentRest(
            @PathVariable("componentPackageFileName") String componentPackageFileName) {
        return withWriteLock(() -> {
            try {
                _addComponentService.registerComponent(componentPackageFileName);
                return _componentState.getByPackageFile(componentPackageFileName)
                        .map(ResponseEntity::ok)
                        .orElseGet(() -> new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
            }
            catch (ComponentRegistrationException ex) {
                return handleAddComponentExceptions(componentPackageFileName, ex);
            }
        });
    }


    private static final Set<String> acceptableComponentContentTypes =
            ImmutableSet.of("application/gzip", "application/x-gzip", "binary/octet-stream");


    @RequestMapping(value = {"/components", "/rest/components"}, method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<?> uploadComponentRest(@RequestParam("file") MultipartFile file) {
        return withWriteLock(() -> {

            String componentPackageName = file.getOriginalFilename();

            Path uploadFileDestinationPath = Paths.get(
                    _propertiesUtil.getUploadedComponentsDirectory().getAbsolutePath(),
                    componentPackageName);
            if (Files.exists(uploadFileDestinationPath)) {
                String errorMsg = "a component with the same file name has already been uploaded";
                return handleRegistrationErrorResponse(componentPackageName, errorMsg, HttpStatus.CONFLICT);
            }

            String contentType = file.getContentType();
            if (contentType == null) {
                String errorMsg = "Content type was not provided for uploaded file";
                return handleRegistrationErrorResponse(componentPackageName, errorMsg, HttpStatus.BAD_REQUEST);
            }
            if (!acceptableComponentContentTypes.contains(contentType.toLowerCase())) {
                String errorMsg = "Uploaded file content type is not application/gzip";
                return handleRegistrationErrorResponse(componentPackageName, errorMsg, HttpStatus.BAD_REQUEST);
            }
            if (!componentPackageName.toLowerCase().endsWith(".tar.gz")) {
                String errorMsg = "Uploaded file does not have the .tar.gz extension";
                return handleRegistrationErrorResponse(componentPackageName, errorMsg, HttpStatus.BAD_REQUEST);
            }

            try {
                file.transferTo(uploadFileDestinationPath.toFile());
                _componentState.addEntryForUploadedPackage(uploadFileDestinationPath);
                return ResponseEntity.ok(Collections.singletonMap("success", componentPackageName + " uploaded"));
            }
            catch (IOException ex) {
                String errorMsg = "An error occurred while saving uploaded file";
                return handleRegistrationErrorResponse(componentPackageName, errorMsg,
                                                       HttpStatus.INTERNAL_SERVER_ERROR, ex);
            }
        });
    }


    @RequestMapping(value = {"/components/packages/{componentPackageFileName:.+}/",
            "/rest/components/packages/{componentPackageFileName:.+}/"},
            method = RequestMethod.DELETE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ResponseBody
    public void removeComponentPackageRest(
            @PathVariable("componentPackageFileName") String componentPackageFileName) {
        withWriteLock(() -> {
            try {
                _removeComponentService.removePackage(componentPackageFileName);
            }
            catch (ManagedComponentsUnsupportedException e) {
                // Impossible because this class has @Profile("!docker").
                throw new IllegalStateException(e);
            }
        });
    }


    @RequestMapping(value = {"/components/{componentPackageFileName:.+}/reRegister",
            "/rest/components/{componentPackageFileName:.+}/reRegister"},
            method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<?> reRegisterRest(@PathVariable("componentPackageFileName") String componentPackageFileName) {
        return withWriteLock(() -> {
            try {
                RegisterComponentModel componentModel = _reRegisterService
                        .reRegisterComponent(componentPackageFileName);
                return ResponseEntity.ok(componentModel);
            }
            catch (ComponentRegistrationException e) {
                return handleAddComponentExceptions(componentPackageFileName, e);
            }
        });
    }
}
