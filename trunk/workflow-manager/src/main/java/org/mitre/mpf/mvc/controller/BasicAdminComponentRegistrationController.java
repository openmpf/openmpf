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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.mitre.mpf.rest.api.MessageModel;
import org.mitre.mpf.rest.api.ResponseMessage;
import org.mitre.mpf.rest.api.component.RegisterComponentModel;
import org.mitre.mpf.wfm.service.component.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

@Api(value = "Component Registrar",
        description = "External component registration and removal" )
@Controller
@Scope("request")
@Profile("docker")
public class BasicAdminComponentRegistrationController {

    private static final Logger log = LoggerFactory.getLogger(BasicAdminComponentRegistrationController.class);

    private final AddComponentService _addComponentService;

    private final RemoveComponentService _removeComponentService;

    private final ComponentStateService _componentState;


    private static final ReadWriteLock LOCK = new ReentrantReadWriteLock();



    @Inject
    BasicAdminComponentRegistrationController(
            AddComponentService addComponentService,
            RemoveComponentService removeComponentService,
            ComponentStateService componentState) {
        _addComponentService = addComponentService;
        _removeComponentService = removeComponentService;
        _componentState = componentState;
    }


    @RequestMapping(value = {"/components", "/rest/components"}, method = RequestMethod.GET)
    @ResponseBody
    public List<RegisterComponentModel> getComponentsRest() {
        return withReadLock(_componentState::get);
    }


    @ApiOperation(value = "Register an unmanaged component.",
            notes = "An unmanaged component is a component that is not started or stopped by the Node Manager " +
                    "so it must be done externally. For example, a component that runs in its own Docker " +
                    "container is considered an unmanaged component. If there is no existing component with " +
                    "the same name, then the component will be registered. If there is an existing unmanaged " +
                    "component and it has an identical descriptor, nothing is changed. If there is an " +
                    "existing unmanaged component and the descriptor is different, the existing component " +
                    "will be replaced. If there is an existing managed component with the same name, " +
                    "registration will fail with a 409 - Conflict response.",
            produces = "application/json",
            response = MessageModel.class)
    @ApiResponses({
            @ApiResponse(code = 200, message = "Successfully updated existing unmanaged component."),
            @ApiResponse(code = 201, message = "Successfully registered new component."),
            @ApiResponse(code = 400, message = "The descriptor was invalid.", response = MessageModel.class),
            @ApiResponse(code = 409, message = "The component conflicts with an existing registered component.",
                    response = MessageModel.class)
    })
    @RequestMapping(value = {"/components/registerUnmanaged", "/rest/components/registerUnmanaged"},
            method = RequestMethod.POST)
    @ResponseBody
    public ResponseMessage registerUnmanagedComponent(@RequestBody JsonComponentDescriptor descriptor) {
        return withWriteLock(() -> {
            boolean alreadyRegistered = _componentState.getByComponentName(descriptor.getComponentName()).isPresent();
            try {
                boolean reRegistered = _addComponentService.registerUnmanagedComponent(descriptor);
                if (alreadyRegistered) {
                    if (reRegistered) {
                        return new ResponseMessage("Modified existing component.", HttpStatus.OK);
                    }
                    return new ResponseMessage("Component already registered.", HttpStatus.OK);
                }
                else {
                    return new ResponseMessage("New component registered.", HttpStatus.CREATED);
                }
            }
            catch (ComponentRegistrationException e) {
                return handleAddComponentExceptions(descriptor.getComponentName(), e);
            }
        });
    }


    @ExceptionHandler(HttpMessageConversionException.class)
    public ResponseMessage handle(HttpMessageConversionException exception){
        // Handles invalid JSON being POSTed to registerUnmanagedComponent
        return new ResponseMessage(exception.getMessage(), HttpStatus.BAD_REQUEST);
    }


    @ApiOperation(value = "Remove a component", code = 204)
    @ApiResponses({
            @ApiResponse(code = 204, message = "The component was successfully removed."),
            @ApiResponse(code = 404, message = "There was no component with the specified name.")
    })
    @RequestMapping(value = {"/components/{componentName}", "/rest/components/{componentName}"},
            method = RequestMethod.DELETE)
    @ResponseBody
    // Prevents Swagger from automatically adding 200 as a response status.
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<?> removeComponentRest(@PathVariable("componentName") String componentName) {
        return withWriteLock(() -> {
            Optional<RegisterComponentModel> existingRegisterModel = _componentState.getByComponentName(componentName);
            if (existingRegisterModel.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            try {
                _removeComponentService.removeComponent(componentName);
                return ResponseEntity.noContent().build();
            }
            catch (ManagedComponentsUnsupportedException e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Managed components are not supported in Docker deployments.");
            }
        });
    }


    protected static ResponseMessage handleAddComponentExceptions(
            String componentPackage,
            ComponentRegistrationException exception) {

        if (exception instanceof ComponentRegistrationStatusException) {
            ComponentRegistrationStatusException ex = (ComponentRegistrationStatusException) exception;
            HttpStatus responseCode;
            switch (ex.getComponentState()) {
                case REGISTERING:
                case REGISTERED:
                    responseCode = HttpStatus.CONFLICT;
                    break;
                default:
                    responseCode = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            return handleRegistrationErrorResponse(componentPackage, ex.getMessage(), responseCode, ex);
        }
        else if (exception instanceof DuplicateComponentException
                || exception instanceof ComponentRegistrationSubsystemException) {
            return handleRegistrationErrorResponse(componentPackage, exception.getMessage(), HttpStatus.CONFLICT);
        }
        else if (exception instanceof InvalidComponentDescriptorException) {
            return handleRegistrationErrorResponse(componentPackage, exception.getMessage(),
                                                   HttpStatus.BAD_REQUEST, exception);
        }
        else {
            return handleRegistrationErrorResponse(componentPackage, exception.getMessage(),
                                                   HttpStatus.INTERNAL_SERVER_ERROR, exception);
        }
    }


    protected static ResponseMessage handleRegistrationErrorResponse(
            String componentPackageFileName, String reason, HttpStatus httpStatus, Exception ex) {

        String errorMsg = String.format("Cannot register component: \"%s\": %s", componentPackageFileName, reason);
        log.error(errorMsg, ex);
        return new ResponseMessage(errorMsg, httpStatus);
    }


    protected static ResponseMessage handleRegistrationErrorResponse(
            String componentPackageFileName, String reason, HttpStatus httpStatus) {
        return handleRegistrationErrorResponse(componentPackageFileName, reason, httpStatus, null);
    }



    protected static <T> T withReadLock(Supplier<T> supplier) {
        try {
            LOCK.readLock().lock();
            return supplier.get();
        }
        finally {
            LOCK.readLock().unlock();
        }
    }


    protected static <T> T withWriteLock(Supplier<T> supplier) {
        try {
            LOCK.writeLock().lock();
            return supplier.get();
        }
        finally {
            LOCK.writeLock().unlock();
        }
    }

    protected static void withWriteLock(Runnable runnable) {
        try {
            LOCK.writeLock().lock();
            runnable.run();
        }
        finally {
            LOCK.writeLock().unlock();
        }
    }
}
