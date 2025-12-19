/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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
import org.mitre.mpf.rest.api.component.RegisterComponentModel;
import org.mitre.mpf.wfm.service.component.*;
import org.mitre.mpf.wfm.util.LogAuditEventRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;

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

    private final RemoveComponentService _removeComponentService;

    private final ComponentStateService _componentState;

    private static final ReadWriteLock LOCK = new ReentrantReadWriteLock();


    @Inject
    BasicAdminComponentRegistrationController(
            RemoveComponentService removeComponentService,
            ComponentStateService componentState) {
        _removeComponentService = removeComponentService;
        _componentState = componentState;
    }


    @RequestMapping(value = {"/components", "/rest/components"}, method = RequestMethod.GET)
    @RequestEventId(value = LogAuditEventRecord.EventId.COMPONENT_REGISTRATION)
    @ResponseBody
    public List<RegisterComponentModel> getComponentsRest() {
        return withReadLock(_componentState::get);
    }


    @ApiOperation(value = "Remove a component", code = 204)
    @ApiResponses({
            @ApiResponse(code = 204, message = "The component was successfully removed."),
            @ApiResponse(code = 404, message = "There was no component with the specified name.")
    })
    @RequestMapping(value = {"/components/{componentName}", "/rest/components/{componentName}"},
            method = RequestMethod.DELETE)
    @RequestEventId(value = LogAuditEventRecord.EventId.COMPONENT_REGISTRATION)
    @ResponseBody
    // Prevents Swagger from automatically adding 200 as a response status.
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<String> removeComponentRest(@PathVariable("componentName") String componentName) {
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
                String err = "Managed components are not supported in Docker deployments.";
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
            }
        });
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
