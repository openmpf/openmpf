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

package org.mitre.mpf.mvc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.mitre.mpf.mvc.model.*;
import org.mitre.mpf.mvc.util.JsonView;
import org.mitre.mpf.rest.api.PipelinesResponse;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.exceptions.DuplicateNameWfmProcessingException;
import org.mitre.mpf.wfm.exceptions.NotFoundWfmProcessingException;
import org.mitre.mpf.wfm.pipeline.*;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;


@Api( value = "Pipelines",
    description = "Available job pipeline information" )
@Controller
@Scope("request")
@Profile("website")
public class PipelineController {

    private static final Logger log = LoggerFactory.getLogger(PipelineController.class);

    private final PropertiesUtil _propertiesUtil;

    private final PipelineService _pipelineService;

    private final ObjectMapper _objectMapper;

    @Inject
    PipelineController(
            PropertiesUtil propertiesUtil,
            PipelineService pipelineService,
            ObjectMapper objectMapper) {
        _propertiesUtil = propertiesUtil;
        _pipelineService = pipelineService;
        _objectMapper = objectMapper;
    }

    /*
     *	/pipelines
     */
    //EXTERNAL
    //1. REST API - Gets the available pipelines that can be used to process media URIs
    // pipelines: todo: the final /rest/pipelines REST API should be identical to what /pipelines is doing
    @RequestMapping(value = {"/rest/pipelines"}, method = RequestMethod.GET)
    @ApiOperation(value="Retrieves list of available pipelines.",
        notes="The response will be a JSON array of PipelinesResponse with each item naming the pipeline, and that pipeline's capability to support a batch and/or a streaming job.",
        produces="application/json", response=PipelinesResponse.class, responseContainer="List" )
    @ApiResponses(value = { 
            @ApiResponse(code = 200, message = "Successful response"),
            @ApiResponse(code = 401, message = "Bad credentials") })
    @ResponseBody
    public List<PipelinesResponse> getAvailablePipelinesRest() {
        return _pipelineService.getPipelines()
                .stream()
                .map(p -> new PipelinesResponse(p.getName(), _pipelineService.pipelineSupportsBatch(p.getName()),
                                                _pipelineService.pipelineSupportsStreaming(p.getName())))
                .collect(toList());
    }

    //INTERNAL
    /**
     * Returns the name (pipeline key) and description of all pipelines.
     * @return A Set of all pipelines in the system.
     */
    @RequestMapping( value = {"/pipelines"},
            method = RequestMethod.GET,
            produces = "application/json")
    @ResponseBody
    public Set<PipelineComponentBasicInfo> getPipelines() {
        return _pipelineService.getPipelines()
                .stream()
                .map(c -> new PipelineComponentBasicInfo(c.getName(), c.getDescription()))
                .collect(Collectors.toSet());
    }


    //INTERNAL - deprecated
    // pipelines2: todo: should be refactored to do /pipeline/{name}
    // but since all data is stored as XML files, there is no benefit for now
    /** returns the details of all pipelines */
    @Deprecated
    @RequestMapping( value = {"/pipeline/all"},
            method = RequestMethod.GET,
            produces = "application/json")
    @ResponseBody
    public Collection<Pipeline> getAllAvailablePipelines() {
        return _pipelineService.getPipelines();
    }


    //INTERNAL - deprecated
    // todo: remove when custom pipeline page (version 1) no longer needed
    @Deprecated
    @RequestMapping(value = "/pipelines/model", method = RequestMethod.GET)
    @ResponseBody
    public PipelinesModel getPipelinesModel() {
        List<String> algorithmNames = _pipelineService.getAlgorithms()
                .stream()
                .map(Algorithm::getName)
                .collect(toList());

        List<String> actionNames = _pipelineService.getActions()
                .stream()
                .map(Action::getName)
                .collect(toList());

        List<String> taskNames = _pipelineService.getTasks()
                .stream()
                .map(Task::getName)
                .collect(toList());

        List<String> pipelineNames = _pipelineService.getPipelines()
                .stream()
                .map(Pipeline::getName)
                .collect(toList());

        return new PipelinesModel(algorithmNames, actionNames, taskNames, pipelineNames);
    }

    //INTERNAL - deprecated
    @Deprecated
    @RequestMapping(value = "/pipelines/algorithm-properties", method = RequestMethod.GET)
    @ResponseBody
    public List<Algorithm.Property> getAlgorithmPropertiesJson(
            @RequestParam(value = "algName", required = true) String algName) {
        Algorithm algorithm = _pipelineService.getAlgorithm(algName);
        if (algorithm == null) {
            return Collections.emptyList();
        }
        return algorithm.getProvidesCollection().getProperties();
    }

    //INTERNAL

    /** Returns the details of a specified pipeline.
     *
     * @param name  The name of the pipeline to retrieve.
     * @return  The specified pipeline.
     * @throws WfmProcessingException
     */
    @RequestMapping(value = "/pipelines",
            method = RequestMethod.GET,
            produces = "application/json",
            // Uses query string parameter instead of path variable to support names with special characters.
            params = "name")
    @ResponseBody
    public Pipeline getPipeline(
            @RequestParam("name") String name) throws WfmProcessingException{
        Pipeline pipeline = _pipelineService.getPipeline(name);
        if (pipeline == null) {
            throw new NotFoundWfmProcessingException("Pipeline not found: " + name + '.');
        }
        return pipeline;
    }

    // This method is used when a custom pipeline is created using the WFM UI option "Configuration" --> "Pipelines 2"
    //INTERNAL
    /**
     * Creates a new pipeline
     * @param pipelineModel The pipeline specifications
     * @throws WfmProcessingException
     */
    @RequestMapping(value = "/pipelines", method = RequestMethod.POST)
    @ResponseBody
    public void addPipeline(@RequestBody PipelineModel pipelineModel) throws WfmProcessingException {
        if(pipelineModel == null)
            throw new WfmProcessingException("Empty pipeline description.");
        else if (pipelineModel.getTasksToAdd().isEmpty())
            throw new WfmProcessingException("Pipelines must contain at least one task.");

        String description = pipelineModel.getDescription();
        //force uppercase
        String name = pipelineModel.getName().toUpperCase();

        try {
            Pipeline pipeline = new Pipeline(name, description, pipelineModel.getTasksToAdd());
            _pipelineService.save(pipeline);

        } catch (WfmProcessingException | InvalidPipelineException e) {

            // log the error, then rethrow the WfmProcessingException back to the caller for handling.
            log.error("pipeline name validation error with message: {}", e.getMessage());
            throw e;

        }
    }

    //INTERNAL
    /** Deletes a specified pipeline.
     *
     * @param name  The pipeline to delete.
     * @throws WfmProcessingException
     */
    @RequestMapping( value = "/pipelines",
            method = RequestMethod.DELETE,
            produces = "application/json",
            // Uses query string parameter instead of path variable to support names with special characters.
            params = "name")
    @ResponseBody
    public void deletePipeline(
            @RequestParam("name") String name) throws WfmProcessingException{
        _pipelineService.deletePipeline(name);
    }

    //INTERNAL
    /** Returns the name (task key) and description of all tasks.
     *
     * @return A set of all tasks in the system.
     */
    @RequestMapping( value = {"/pipeline-tasks"},
            method = RequestMethod.GET,
            produces = "application/json")
    @ResponseBody
    public Set<PipelineComponentBasicInfo> getPipelineTasks() {
         return _pipelineService.getTasks()
                .stream()
                .map(c -> new PipelineComponentBasicInfo(c.getName(), c.getDescription()))
                .collect(Collectors.toSet());
    }

    //INTERNAL
    /** Returns the details of a specified task.
     *
     * @param name  The name of the task to get.
     * @return  The task being retrieved.
     * @throws WfmProcessingException
     */
    @RequestMapping(value = "/pipeline-tasks",
            method = RequestMethod.GET,
            produces = "application/json",
            // Uses query string parameter instead of path variable to support names with special characters.
            params = "name")
    @ResponseBody
    public Task getPipelineTask(
            @RequestParam("name") String name) throws WfmProcessingException {
        Task task = _pipelineService.getTask(name);
        if (task == null) {
            throw new NotFoundWfmProcessingException("Task not found: " + name + '.');
        }
        return task;
    }

    // This method is used when a custom action is created using the WFM UI option "Configuration" --> "Pipelines 2"
    //INTERNAL
    /**
     * Adds a task with an associated collection of actions.
     * @param taskModel  The task to add.
     * @param response   HttpServletResponse
     *
     * @throws WfmProcessingException  Indicates an error has occurred in processing.
     */
    @RequestMapping(value = "/pipeline-tasks", method = RequestMethod.POST)
    @ResponseBody
    public void addTask(@RequestBody TaskModel taskModel, HttpServletResponse response) throws WfmProcessingException {
        if(taskModel == null)
            throw new WfmProcessingException("Empty task description.");
        else if (taskModel.getActionsToAdd().isEmpty())
            throw new WfmProcessingException("Tasks must contain at least one action.");

        String description = taskModel.getDescription();
        //force uppercase
        String name = taskModel.getName().toUpperCase();

        Task task = new Task(name, description, taskModel.getActionsToAdd());
        _pipelineService.save(task);
    }

    //INTERNAL
    /** Deletes the specified task.
     *
     * @param name  The name of the task to delete
     * @throws WfmProcessingException
     */
    @RequestMapping(value = "/pipeline-tasks",
            method = RequestMethod.DELETE,
            produces = "application/json",
            // Uses query string parameter instead of path variable to support names with special characters.
            params = "name")
    @ResponseBody
    public void deletePipelineTask(
            @RequestParam("name") String name) throws WfmProcessingException {
        _pipelineService.deleteTask(name);
    }

    //INTERNAL
    /** Returns the name (action key) and description of all actions.
     *
     * @return  The set of all actions in the system.
     */
    @RequestMapping( value = {"/pipeline-actions"},
            method = RequestMethod.GET,
            produces = "application/json")
    @ResponseBody
    public Collection<Action> getPipelineActions() {
        return _pipelineService.getActions();
    }

    //INTERNAL
    /** Returns the details of a specified action.
     *
     * @param name  The name of the action to retrieve.
     * @return  The specified action.
     * @throws WfmProcessingException
     */
    @RequestMapping( value = {"/pipeline-actions"},
            method = RequestMethod.GET,
            produces = "application/json",
            // Uses query string parameter instead of path variable to support names with special characters.
            params = "name")
    @ResponseBody
    public Action getPipelineAction(
            @RequestParam("name") String name) throws WfmProcessingException {
        Action action = _pipelineService.getAction(name);
        if (action == null) {
            throw new NotFoundWfmProcessingException("Action not found: " + name + '.');
        }
        return action;
    }

    // This method is used when a custom action is created using the WFM UI option "Configuration" --> "Pipelines 2"
    //INTERNAL
    /**
     * Adds an action with an associated action and properties.
     *
     * @param actionModel  The action to add.
     * @param response   HttpServletResponse
     *
     * @throws WfmProcessingException  Indicates an error has occurred in processing.
     */

    @RequestMapping(value = "/pipeline-actions",
            method = RequestMethod.POST,
            produces = "application/json" )
    @ResponseBody
    public void createActionJson2(@RequestBody ActionModel actionModel, HttpServletResponse response) throws WfmProcessingException {

        Map<String, String> modifiedProperties;
        try {
            modifiedProperties = _objectMapper.readValue(actionModel.getProperties(), HashMap.class);
        } catch (WfmProcessingException wfme) {

            // log the error, then rethrow the WfmProcessingException back to the caller for handling.
            log.error("action name validation error with message: " + wfme.getMessage(), wfme);
            throw wfme;

        } catch (Exception e) {

            log.error("error getting properties with message: " + e.getMessage(), e);
            throw new WfmProcessingException("Invalid properties value: " + actionModel.getProperties() + '.', e);
        }
        saveAction(actionModel, modifiedProperties);

    }


    private void saveAction(ActionModel actionModel, Map<String, String> actionProperties) {
        List<Action.Property> propertyList = actionProperties.entrySet()
                .stream()
                .map(e -> new Action.Property(e.getKey(), e.getValue()))
                .collect(toList());
        Action action = new Action(actionModel.getActionName(), actionModel.getActionDescription(),
                                   actionModel.getAlgorithmName(), propertyList);
        _pipelineService.save(action);
    }


    //INTERNAL
    /** Deletes a specified action.
     *
     * @param name  The name of the action to delete.
     * @throws WfmProcessingException
     */
    @RequestMapping( value = "/pipeline-actions",
            method = RequestMethod.DELETE,
            produces = "application/json",
            // Uses query string parameter instead of path variable to support names with special characters.
            params = "name")
    @ResponseBody
    public void deletePipelineAction(
            @RequestParam("name") String name) throws WfmProcessingException {
        _pipelineService.deleteAction(name);
    }

    //INTERNAL
    /** Returns the name (algorithm key) and description of all algorithms.
     *
     * @return  The set of all algorithms in the system.
     */
    @RequestMapping( value = {"/pipeline-algorithms"},
            method = RequestMethod.GET,
            produces = "application/json")
    @ResponseBody
    public Set<PipelineComponentBasicInfo> getPipelineAlgorithms() {
        return _pipelineService.getAlgorithms()
                .stream()
                .map(c -> new PipelineComponentBasicInfo(c.getName(), c.getDescription()))
                .collect(Collectors.toSet());
    }


    //INTERNAL
    /** Returns the details of a specified algorithm.
     *
     * @param name  The name of the algorithm to retrieve.
     * @return  The specified algorithm
     * @throws WfmProcessingException
     */
    @RequestMapping( value = "/pipeline-algorithms",
            method = RequestMethod.GET,
            produces = "application/json",
            // Uses query string parameter instead of path variable to support names with special characters.
            params = "name")
    @ResponseBody
    public Algorithm getPipelineAlgorithm(
            @RequestParam("name") String name) throws WfmProcessingException {

        Algorithm algorithm = _pipelineService.getAlgorithm(name);
        if (algorithm == null) {
            throw new NotFoundWfmProcessingException("Algorithm not found: " + name + '.');
        }

        List<Algorithm.Property> propsWithDefaultSet = new ArrayList<>();
        for (Algorithm.Property property : algorithm.getProvidesCollection().getProperties()) {
            if (property.getDefaultValue() != null) {
                propsWithDefaultSet.add(property);
            }
            else {
                Algorithm.Property propWithDefault = new Algorithm.Property(
                        property.getName(), property.getDescription(), property.getType(),
                        _propertiesUtil.lookup(property.getPropertiesKey()), property.getPropertiesKey());
                propsWithDefaultSet.add(propWithDefault);
            }
        }

        return new Algorithm(
                algorithm.getName(), algorithm.getDescription(), algorithm.getActionType(),
                algorithm.getRequiresCollection(),
                new Algorithm.Provides(algorithm.getProvidesCollection().getStates(), propsWithDefaultSet),
                algorithm.getSupportsBatchProcessing(), algorithm.getSupportsStreamProcessing());
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(DuplicateNameWfmProcessingException.class)
    @ResponseBody
    public String handleDuplicateNameWfmProcessingException(HttpServletRequest req, Exception ex) {
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NotFoundWfmProcessingException.class)
    @ResponseBody
    public String handleNotFoundWfmProcessingException(HttpServletRequest req, Exception ex) {
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({WfmProcessingException.class, InvalidPipelineException.class})
    @ResponseBody
    public String handleWfmProcessingException(HttpServletRequest req, Exception ex) {
        return ex.getMessage();
    }

    // ---------------------------------------------------------------
    // --- old web services that have not been updated for pipelines 2 page
    //      and are still being used by piplines 1 page
    // old?    @RequestMapping(value = "/pipelines/algorithm-properties", method = RequestMethod.GET)

    // This method is used when a custom action is created using the WFM UI option "Configuration" --> "Pipelines"
    //TODO: Remove when Pipelines1 page is phased out.
    @Deprecated
    @RequestMapping(value = "/pipelines/create-action", method = RequestMethod.POST)
    @ResponseBody
    public Object createActionJson(@RequestBody ActionModel actionModel, HttpServletResponse response) throws WfmProcessingException {


        Tuple<Boolean,String> responseTuple = null;

        //IMPORTANT!! - only passing in the modified properties in this method
        Map<String, String> modifiedProperties = new HashMap<String, String>();
        try {
            modifiedProperties = _objectMapper.readValue(actionModel.getProperties(), HashMap.class);
        } catch (IOException e) {
            log.error("error getting properties with message: {}", e.getMessage());
            responseTuple = new Tuple<Boolean, String>(false, e.getMessage());
            return JsonView.Render(responseTuple, response);
        }

        try {
            saveAction(actionModel, modifiedProperties);
            log.debug("Successfully created action: " + actionModel.getActionName());
            responseTuple = new Tuple<>(true, null);
        }
        catch (WfmProcessingException | InvalidPipelineException ex) {
            log.error("Unable to save action. Please check the server logs.", ex);
            responseTuple = new Tuple<>(false, "Unable to save action.");
        }

        return JsonView.Render(responseTuple, response);
    }

    // This method is used when a custom pipeline is created using the WFM UI option "Configuration" --> "Pipelines"
    //TODO: Remove when Pipelines1 page is phased out.
    @Deprecated
    @RequestMapping(value = "/pipelines/add-task-or-pipeline", method = RequestMethod.POST)
    @ResponseBody
    public Object addToPipelineControllerJson(@RequestBody AddToPipelineModel addToPipelineModel, HttpServletResponse response) throws WfmProcessingException {
        boolean successfulAdd = false;
        //Map<Boolean,String> responseMap = new HashMap<Boolean,String>();
        Tuple<Boolean,String> responseTuple = null;
        if(addToPipelineModel != null && !addToPipelineModel.getItemsToAdd().isEmpty()) {

            String description = addToPipelineModel.getDescription();
            //force uppercase
            String name = addToPipelineModel.getName().toUpperCase();

            String type = null;

            try {

                type = addToPipelineModel.getType();

                //actions is handled by "/createaction"
                if(type.equals("task")) {
                    Task task = new Task(name, description, addToPipelineModel.getItemsToAdd());
                    _pipelineService.save(task);
                }
                else if(type.equals("pipeline")) {
                    Pipeline pipeline = new Pipeline(name, description, addToPipelineModel.getItemsToAdd());
                    _pipelineService.save(pipeline);
                }
                log.debug("success adding to the {} collection", type);
                responseTuple = new Tuple<Boolean, String>(true, null);
            }
            catch (WfmProcessingException ex) {
                responseTuple = new Tuple<Boolean, String>(false, "Unable to save " + type +". Please check the server logs.");
                log.error(responseTuple.getSecond(), ex);
            }
        } else {
            //assuming that the addToPipelineModel is not null and the user forgot to add items before clicking create
            responseTuple = new Tuple<Boolean, String>(false, "Please add items before clicking 'Create'!");
        }

        return JsonView.Render(responseTuple, response);
    }
}
