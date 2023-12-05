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
import org.mitre.mpf.rest.api.pipelines.*;
import org.mitre.mpf.wfm.service.WorkflowPropertyService;
import org.mitre.mpf.wfm.service.pipeline.InvalidPipelineException;
import org.mitre.mpf.wfm.service.pipeline.PipelineService;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;
import java.util.*;

import static java.util.stream.Collectors.toList;

@Api("Pipelines")
@Scope("request")
@RestController
@RequestMapping(produces = "application/json")
public class PipelineController {

    private static final Logger log = LoggerFactory.getLogger(PipelineController.class);

    private final PropertiesUtil _propertiesUtil;

    private final WorkflowPropertyService _workflowPropertyService;

    private final PipelineService _pipelineService;

    @Inject
    PipelineController(
            PropertiesUtil propertiesUtil,
            WorkflowPropertyService workflowPropertyService,
            PipelineService pipelineService) {
        _propertiesUtil = propertiesUtil;
        _workflowPropertyService = workflowPropertyService;
        _pipelineService = pipelineService;
    }


    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(InvalidPipelineException.class)
    public MessageModel invalidPipelineHandler(InvalidPipelineException ex) {
        log.error(ex.getMessage(), ex);
        return new MessageModel(ex.getMessage());
    }



    @GetMapping({  "/pipelines", "/rest/pipelines" })
    @ApiOperation("Retrieves list of available pipelines.")
    public List<Pipeline> getPipelines() {
        return _pipelineService.getPipelines();
    }


    @GetMapping({ "/pipelines/{name}", "/rest/pipelines/{name}" })
    @ApiOperation("Retrieves a single pipeline.")
    @ApiResponses(@ApiResponse(code = 404, message = "Not found"))
    public ResponseEntity<Pipeline> getPipeline(@PathVariable String name) {
        return Optional.ofNullable(_pipelineService.getPipeline(name))
                .map(p -> new ResponseEntity<>(p, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }


    @PostMapping({ "/pipelines", "/rest/pipelines" })
    @ApiOperation("Adds a new pipeline.")
    @ApiResponses(@ApiResponse(code = 400, message = "Invalid request", response = MessageModel.class))
    public void add(@RequestBody Pipeline pipeline) {
        _pipelineService.save(pipeline);
    }


    @DeleteMapping({ "/pipelines/{name}", "/rest/pipelines/{name}" })
    @ApiOperation("Deletes a pipeline.")
    public void deletePipeline(@PathVariable String name) {
        _pipelineService.deletePipeline(name);
    }




    @GetMapping({ "/tasks", "/rest/tasks" })
    @ApiOperation("Retrieves list of available tasks.")
    public List<Task> getTasks() {
        return _pipelineService.getTasks();
    }


    @GetMapping({ "/tasks/{name}", "/rest/tasks/{name}" })
    @ApiOperation("Retrieves a single task.")
    @ApiResponses(@ApiResponse(code = 404, message = "Not found"))
    public ResponseEntity<Task> getTask(@PathVariable String name) {
        return Optional.ofNullable(_pipelineService.getTask(name))
                .map(p -> new ResponseEntity<>(p, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }


    @PostMapping({ "/tasks", "/rest/tasks" })
    @ApiOperation("Adds a new task.")
    @ApiResponses(@ApiResponse(code = 400, message = "Invalid request", response = MessageModel.class))
    public void add(@RequestBody Task task) {
        _pipelineService.save(task);
    }


    @DeleteMapping({ "/tasks/{name}", "/rest/tasks/{name}" })
    @ApiOperation("Deletes a task.")
    public void deleteTask(@PathVariable String name) {
        _pipelineService.deleteTask(name);
    }





    @GetMapping({ "/actions", "/rest/actions" })
    @ApiOperation("Retrieves list of available actions.")
    public List<Action> getActions() {
        return _pipelineService.getActions();
    }


    @GetMapping({ "/actions/{name}", "/rest/actions/{name}" })
    @ApiOperation("Retrieves a single  action")
    @ApiResponses(@ApiResponse(code = 404, message = "Not found"))
    public ResponseEntity<Action> getAction(@PathVariable String name) {
        return Optional.ofNullable(_pipelineService.getAction(name))
                .map(p -> new ResponseEntity<>(p, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }


    @PostMapping({ "/actions", "/rest/actions" })
    @ApiOperation("Adds a new action.")
    @ApiResponses(@ApiResponse(code = 400, message = "Invalid request", response = MessageModel.class))
    public void add(@RequestBody Action action) {
        _pipelineService.save(action);
    }


    @DeleteMapping({ "/actions/{name}", "/rest/actions/{name}" })
    @ApiOperation("Deletes an action.")
    public void deleteAction(@PathVariable String name) {
        _pipelineService.deleteAction(name);
    }




    @GetMapping({ "/algorithms", "/rest/algorithms" })
    @ApiOperation("Retrieves list of available algorithms.")
    public List<Algorithm> getAlgorithms() {
        return _pipelineService.getAlgorithms()
                .stream()
                .map(this::getAlgoWithDefaultValuesSet)
                .collect(toList());
    }


    @GetMapping({ "/algorithms/{name}", "/rest/algorithms/{name}" })
    @ApiOperation("Retrieves a single algorithm.")
    @ApiResponses(@ApiResponse(code = 404, message = "Not found"))
    public ResponseEntity<Algorithm> getAlgorithm(@PathVariable String name) {
        return Optional.ofNullable(_pipelineService.getAlgorithm(name))
                .map(this::getAlgoWithDefaultValuesSet)
                .map(p -> new ResponseEntity<>(p, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }


    private Algorithm getAlgoWithDefaultValuesSet(Algorithm algorithm) {
        var propsWithDefaultSet = new ArrayList<AlgorithmProperty>();
        var propertiesAdded = new HashSet<String>();

        for (var property : algorithm.getProvidesCollection().getProperties()) {
            if (property.getDefaultValue() != null) {
                propsWithDefaultSet.add(property);
            }
            else {
                var propWithDefault = new AlgorithmProperty(
                        property.getName(), property.getDescription(), property.getType(),
                        _propertiesUtil.lookup(property.getPropertiesKey()), property.getPropertiesKey());
                propsWithDefaultSet.add(propWithDefault);
            }
            propertiesAdded.add(property.getName());
        }

        if (algorithm.getActionType() == ActionType.DETECTION) {
            addWorkflowProperties(propertiesAdded, propsWithDefaultSet);
        }

        return new Algorithm(
                algorithm.getName(), algorithm.getDescription(), algorithm.getActionType(),
                algorithm.getTrackType(),
                algorithm.getOutputChangedCounter(),
                algorithm.getRequiresCollection(),
                new Algorithm.Provides(algorithm.getProvidesCollection().getStates(), propsWithDefaultSet),
                algorithm.getSupportsBatchProcessing(), algorithm.getSupportsStreamProcessing());
    }


    private void addWorkflowProperties(Collection<String> propertiesAlreadyAdded,
                                       Collection<AlgorithmProperty> propsWithDefaultSet) {

        for (var workflowProperty : _workflowPropertyService.getProperties()) {
            if (!propertiesAlreadyAdded.contains(workflowProperty.getName())) {
                var workflowPropVal = _workflowPropertyService.getPropertyValue(workflowProperty.getName());
                if (workflowPropVal != null) {
                    propsWithDefaultSet.add(
                            new AlgorithmProperty(workflowProperty.getName(), workflowProperty.getDescription(),
                                                  workflowProperty.getType(), workflowPropVal,
                                                  workflowProperty.getPropertiesKey()));
                }
            }
        }
    }
}
