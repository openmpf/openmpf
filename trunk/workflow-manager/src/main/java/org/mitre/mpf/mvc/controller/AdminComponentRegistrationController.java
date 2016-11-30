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

import com.google.common.collect.ImmutableSet;
import io.swagger.annotations.Api;
import org.mitre.mpf.rest.api.ResponseMessage;
import org.mitre.mpf.rest.api.component.RegisterComponentModel;
import org.mitre.mpf.wfm.service.component.*;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

// swagger includes

@Api(value = "Component Registrar",
     description = "External component registration and removal" )
@Controller
@Scope("request")
@Profile("website")
public class AdminComponentRegistrationController {

    private static final Logger log = LoggerFactory.getLogger(AdminComponentRegistrationController.class);

	private final PropertiesUtil _propertiesUtil;

    private final AddComponentService _addComponentService;
  
	private final RemoveComponentService _removeComponentService;

    private final ComponentStateService _componentState;

    private static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();

    @Inject
    AdminComponentRegistrationController(
            PropertiesUtil propertiesUtil,
            AddComponentService addComponentService,
            RemoveComponentService removeComponentService,
            ComponentStateService componentState) {
        _propertiesUtil = propertiesUtil;
        _addComponentService = addComponentService;
        _removeComponentService = removeComponentService;
        _componentState = componentState;
    }


    /*
     * GET all component info as a list - INTERNAL
     */
    @RequestMapping(value = {"/components", "/rest/components"}, method = RequestMethod.GET)
    @ResponseBody
	public List<RegisterComponentModel> getComponentsMapAsList() {
    	//TODO: could sort by the date while streaming to the list
        return withReadLock(_componentState::get);
    }
    
    /*
     * GET single component info - INTERNAL
     */
    //the componentPackageFileName is the filename of the tar.gz file submitted - will require the ending '/'
    @RequestMapping(value = {"/components/{componentPackageFileName:.+}",
            "/rest/components/{componentPackageFileName:.+}"},
            method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<RegisterComponentModel> getComponent(
            @PathVariable("componentPackageFileName") String componentPackageFileName)
    {
        return withReadLock(() ->
            _componentState.getByPackageFile(componentPackageFileName)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND)));
    }


    /*
     * POST - register component - INTERNAL
     */
    @RequestMapping(value = {"/components/{componentPackageFileName:.+}/register",
            "/rest/components/{componentPackageFileName:.+}/register"},
            method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity registerComponent(
            @PathVariable("componentPackageFileName") String componentPackageFileName) {
        return withWriteLock(() -> {
            try {
                _addComponentService.registerComponent(componentPackageFileName);
                return _componentState.getByPackageFile(componentPackageFileName)
                        .map(ResponseEntity::ok)
                        .orElseGet(() -> new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
            }
            catch (ComponentRegistrationStatusException ex) {
                HttpStatus responseCode;
                switch (ex.getComponentState()) {
                    case REGISTERING:
                    case REGISTERED:
                        responseCode = HttpStatus.CONFLICT;
                        break;
                    default:
                        responseCode = HttpStatus.INTERNAL_SERVER_ERROR;
                }
                return handleRegistrationErrorResponse(componentPackageFileName, ex.getMessage(), responseCode, ex);
            }
            catch (DuplicateComponentException | ComponentRegistrationSubsystemException ex) {
                return handleRegistrationErrorResponse(componentPackageFileName, ex.getMessage(), HttpStatus.CONFLICT);
            }
            catch (ComponentRegistrationException ex) {
                return handleRegistrationErrorResponse(
                        componentPackageFileName, ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, ex);
            }
        });
    }


    private static ResponseMessage handleRegistrationErrorResponse(
            String componentPackageFileName, String reason, HttpStatus httpStatus, ComponentRegistrationException ex) {

        String errorMsg = String.format("Cannot register component: \"%s\": %s", componentPackageFileName, reason);
        log.error(errorMsg, ex);
        return new ResponseMessage(errorMsg, httpStatus);
    }


    private static ResponseMessage handleRegistrationErrorResponse(
            String componentPackageFileName, String reason, HttpStatus httpStatus) {
        return handleRegistrationErrorResponse(componentPackageFileName, reason, httpStatus, null);
    }



    private static final Set<String> acceptableComponentContentTypes =
            ImmutableSet.of("application/gzip", "application/x-gzip", "binary/octet-stream");


    @RequestMapping(value = {"/components", "/rest/components"}, method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity uploadComponent(@RequestParam("file") MultipartFile file) {
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
                return handleRegistrationErrorResponse(componentPackageName, errorMsg, HttpStatus.INTERNAL_SERVER_ERROR);
            }
        });
    }



    //TODO: commenting out the swagger annotations because this is still needed externally by ansible
    /* @ApiOperation(value = "Register an external component",
            notes = "The component's algorithm will be added to the list of services available for deployment via the web UI",
            produces = "application/json" )
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful response"),
            @ApiResponse(code = 401, message = "Bad credentials") }) */
    @RequestMapping(value = {"/component/registerViaFile", "/rest/component/registerViaFile"}, method = RequestMethod.GET)
    @ResponseBody
    public ResponseMessage registerViaFile(
            /*@ApiParam(required = true, defaultValue = "",
                      value = "The path to the JSON component descriptor file")*/
            @RequestParam String filePath
    ) {
        return withWriteLock(() -> {
            log.info("Entered {}", "[rest/component/registerViaFile]");
            try {
                _addComponentService.registerDeployedComponent(filePath);
                return ResponseMessage.ok("Component successfully registered");
            }
            catch (ComponentRegistrationException ex) {
                return handleRegistrationErrorResponse(null, ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, ex);
            }
        });
    }

  //TODO: commenting out the swagger annotations because this is still needed externall by ansible
    // NOTE, un-registering a component does not shut down or remove any deployed running instances
/*    @ApiOperation(value = "Remove (unregister) an external component",
            notes = "The component's algorithm will be removed from the list of services available for deployment via the web UI",
            produces = "application/json" )
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful response"),
            @ApiResponse(code = 401, message = "Bad credentials") })*/
    @RequestMapping(value = {"/component/unregisterViaFile", "/rest/component/unregisterViaFile"}, method = RequestMethod.GET)
    @ResponseBody
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(
            /*@ApiParam(required = true, value = "The path to the JSON component descriptor file")*/
            @RequestParam String filePath
    ) {
        withWriteLock(() -> _removeComponentService.unregisterViaFile(filePath));
    }

    @RequestMapping(value = {"/components/{componentName}", "/rest/components/{componentName}"},
            method=RequestMethod.DELETE)
    @ResponseBody
    public ResponseEntity removeComponent(@PathVariable("componentName") String componentName) {
        return withWriteLock(() -> {
            Optional<RegisterComponentModel> existingRegisterModel = _componentState.getByComponentName(componentName);
            if (!existingRegisterModel.isPresent()) {
                return new ResponseEntity(HttpStatus.NOT_FOUND);
            }
            _removeComponentService.removeComponent(componentName);
            return new ResponseEntity(HttpStatus.NO_CONTENT);
        });
    }


    @RequestMapping(value = {"/components/packages/{componentPackageFileName:.+}/",
            "/rest/components/packages/{componentPackageFileName:.+}/"},
            method = RequestMethod.DELETE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ResponseBody
    public void removeComponentPackage(
            @PathVariable("componentPackageFileName") String componentPackageFileName) {
        withWriteLock(() -> _removeComponentService.removePackage(componentPackageFileName));
    }



    private static <T> T withReadLock(Supplier<T> supplier) {
        try {
            LOCK.readLock().lock();
            return supplier.get();
        }
        finally {
            LOCK.readLock().unlock();
        }
    }


    private static <T> T withWriteLock(Supplier<T> supplier) {
        try {
            LOCK.writeLock().lock();
            return supplier.get();
        }
        finally {
            LOCK.writeLock().unlock();
        }
    }

    private static void withWriteLock(Runnable runnable) {
        try {
            LOCK.writeLock().lock();
            runnable.run();
        }
        finally {
            LOCK.writeLock().unlock();
        }
    }
}
