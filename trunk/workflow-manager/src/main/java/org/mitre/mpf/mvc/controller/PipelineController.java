/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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
import org.mitre.mpf.wfm.pipeline.xml.*;
import org.mitre.mpf.wfm.service.PipelineService;
import org.mitre.mpf.wfm.util.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


@Api( value = "Pipelines",
	description = "Available job pipeline information" )
@Controller
@Scope("request")
@Profile("website")
public class PipelineController {

    private static final Logger log = LoggerFactory.getLogger(PipelineController.class);

    @Autowired
    private PipelineService pipelineService;

    @Autowired
    private ObjectMapper objectMapper;

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
        SortedSet<String> pipelineNames = pipelineService.getPipelineNames();
        return pipelineNames.stream().map(pipelineName -> new PipelinesResponse(pipelineName,
            pipelineService.pipelineSupportsBatch(pipelineName), pipelineService.pipelineSupportsStreaming(pipelineName))).collect(Collectors.toList());
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
        return pipelineService.getPipelines()
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
    public Set<PipelineDefinition> getAllAvailablePipelines() {
        return pipelineService.getPipelines();
    }


    //INTERNAL - deprecated
    // todo: remove when custom pipeline page (version 1) no longer needed
    @Deprecated
    @RequestMapping(value = "/pipelines/model", method = RequestMethod.GET)
    @ResponseBody
    public PipelinesModel getPipelinesModel() {
        return new PipelinesModel(
                new ArrayList<>(pipelineService.getAlgorithmNames()),
                new ArrayList<>(pipelineService.getActionNames()),
                new ArrayList<>(pipelineService.getTaskNames()),
                new ArrayList<>(pipelineService.getPipelineNames()));
    }

    //INTERNAL - deprecated
    @Deprecated
    @RequestMapping(value = "/pipelines/algorithm-properties", method = RequestMethod.GET)
    @ResponseBody
    public List<PropertyDefinition> getAlgorithmPropertiesJson(
            @RequestParam(value = "algName", required = true) String algName) {
        AlgorithmDefinition algorithmDefinition = pipelineService.getAlgorithm(algName);
        if (algorithmDefinition == null) {
            return Collections.emptyList();
        }
        return algorithmDefinition.getProvidesCollection().getAlgorithmProperties();
    }

    //INTERNAL

    /** Returns the details of a specified pipeline.
     *
     * @param pipelineName  The name of the pipeline to retrieve.
     * @return  The specified pipeline.
     * @throws WfmProcessingException
     */
    @RequestMapping( value = {"/pipelines/{pipelineName}"},
            method = RequestMethod.GET,
            produces = "application/json")
    @ResponseBody
    public PipelineDefinition getPipeline(
            @PathVariable("pipelineName") String pipelineName) throws WfmProcessingException{
        PipelineDefinition pipelineDefinition = pipelineService.getPipeline(pipelineName);
        if (pipelineDefinition==null) {
            throw new NotFoundWfmProcessingException("Pipeline not found: " + pipelineName + ".");
        }
        return pipelineDefinition;
    }

    // This method is used when a custom pipeline is created using the WFM UI option "Configuration" --> "Pipelines 2"
    //INTERNAL
    /**
     * Creates a new pipeline
     * @param pipelineModel The pipeline specifications
     * @param response      The HttpServletResponse
     * @throws WfmProcessingException
     */
    @RequestMapping(value = "/pipelines", method = RequestMethod.POST)
    @ResponseBody
    public void addPipeline(@RequestBody PipelineModel pipelineModel, HttpServletResponse response) throws WfmProcessingException {
        if(pipelineModel == null)
            throw new WfmProcessingException("Empty pipeline description.");
        else if (pipelineModel.getTasksToAdd().isEmpty())
            throw new WfmProcessingException("Pipelines must contain at least one task.");

        String description = pipelineModel.getDescription();
        //force uppercase
        String name = pipelineModel.getName().toUpperCase();

        try {

            // Only allow pipelines names that contain uppercase letters, numbers, dash, hyphen or white space.
            // This method may throw a WfmProcessingException, allowing for rejection of the pipeline if the name doesn't validate.
            validatePipelineOrTaskOrActionName(name);

            PipelineDefinition pipelineDefinition = new PipelineDefinition(name, description);
            for (String taskName : pipelineModel.getTasksToAdd()) {
                pipelineDefinition.getTaskRefs().add(new TaskDefinitionRef(taskName));
            }
            pipelineService.savePipeline(pipelineDefinition);

        } catch (WfmProcessingException e) {

            // log the error, then rethrow the WfmProcessingException back to the caller for handling.
            log.error("pipeline name validation error with message: {}", e.getMessage());
            throw e;

        }
    }

    //INTERNAL
    /** Deletes a specified pipeline.
     *
     * @param pipelineName  The pipeline to delete.
     * @throws WfmProcessingException
     */
    @RequestMapping( value = {"/pipelines/{pipelineName}"},
            method = RequestMethod.DELETE,
            produces = "application/json")
    @ResponseBody
    public void deletePipeline(
            @PathVariable("pipelineName") String pipelineName) throws WfmProcessingException{
    	pipelineService.deletePipeline(pipelineName);
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
         return pipelineService.getTasks()
                .stream()
                .map(c -> new PipelineComponentBasicInfo(c.getName(), c.getDescription()))
                .collect(Collectors.toSet());
    }

    //INTERNAL
    /** Returns the details of a specified task.
     *
     * @param taskName  The name of the task to get.
     * @return  The task being retrieved.
     * @throws WfmProcessingException
     */
    @RequestMapping( value = {"/pipeline-tasks/{taskName}"},
            method = RequestMethod.GET,
            produces = "application/json")
    @ResponseBody
    public TaskDefinition getPipelineTask(
            @PathVariable("taskName") String taskName) throws WfmProcessingException {
        TaskDefinition task = pipelineService.getTask(taskName);
        if (task == null) {
            throw new NotFoundWfmProcessingException("Task not found: " + taskName + ".");
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

        // Only allow task names that contain uppercase letters, numbers, dash, hyphen or white space.
        // This method may throw a WfmProcessingException, allowing for rejection of the task if the name doesn't validate.
        validatePipelineOrTaskOrActionName(name);

        TaskDefinition taskDefinition = new TaskDefinition(name, description);
        for (String actionName : taskModel.getActionsToAdd()) {
            taskDefinition.getActions().add(new ActionDefinitionRef(actionName));
        }
        pipelineService.saveTask(taskDefinition);
    }

    //INTERNAL
    /** Deletes the specified task.
     *
     * @param taskName  The name of the task to delete
     * @throws WfmProcessingException
     */
    @RequestMapping( value = {"/pipeline-tasks/{taskName}"},
            method = RequestMethod.DELETE,
            produces = "application/json")
    @ResponseBody
    public void deletePipelineTask(
            @PathVariable("taskName") String taskName) throws WfmProcessingException {
        pipelineService.deleteTask(taskName);
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
    public Set<PipelineComponentBasicInfo> getPipelineActions() {
        return pipelineService.getActions()
                .stream()
                .map(c -> new PipelineComponentBasicInfo(c.getName(), c.getDescription()))
                .collect(Collectors.toSet());
    }

    //INTERNAL
    /** Returns the details of a specified action.
     *
     * @param actionName  The name of the action to retrieve.
     * @return  The specified action.
     * @throws WfmProcessingException
     */
    @RequestMapping( value = {"/pipeline-actions/{actionName}"},
            method = RequestMethod.GET,
            produces = "application/json")
    @ResponseBody
    public ActionDefinition getPipelineAction(
            @PathVariable("actionName") String actionName) throws WfmProcessingException {
        ActionDefinition action = pipelineService.getAction(actionName);
        if (action == null) {
            throw new NotFoundWfmProcessingException("Action not found: " + actionName + ".");
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

            // Only allow action names that contain uppercase letters, numbers, dash, hyphen or white space.
            // This method may throw a WfmProcessingException, allowing for rejection of the action if the name doesn't validate.
            String name = actionModel.getActionName().toUpperCase();
            validatePipelineOrTaskOrActionName(name);

            modifiedProperties = objectMapper.readValue(actionModel.getProperties(), HashMap.class);

        } catch (WfmProcessingException wfme) {

            // log the error, then rethrow the WfmProcessingException back to the caller for handling.
            log.error("action name validation error with message: {}", wfme.getMessage());
            throw wfme;

        } catch (Exception e) {

            log.error("error getting properties with message: {}", e.getMessage());
            throw new WfmProcessingException("Invalid properties value: " + actionModel.getProperties() + ".", e);

        }

        saveAction(actionModel, modifiedProperties);
    }


    private void saveAction(ActionModel actionModel, Map<String, String> actionProperties) {
        ActionDefinition actionDef = new ActionDefinition(
                actionModel.getActionName(),
                actionModel.getAlgorithmName(),
                actionModel.getActionDescription());

        for (Map.Entry<String, String> propEntry : actionProperties.entrySet()) {
            PropertyDefinitionRef propDef = new PropertyDefinitionRef(propEntry.getKey(), propEntry.getValue()) ;
            actionDef.getProperties().add(propDef);
        }
        pipelineService.saveAction(actionDef);
    }


    //INTERNAL
    /** Deletes a specified action.
     *
     * @param actionName  The name of the action to delete.
     * @throws WfmProcessingException
     */
    @RequestMapping( value = {"/pipeline-actions/{actionName}"},
            method = RequestMethod.DELETE,
            produces = "application/json")
    @ResponseBody
    public void deletePipelineAction(
            @PathVariable("actionName") String actionName) throws WfmProcessingException {
        pipelineService.deleteAction(actionName);
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
        return pipelineService.getAlgorithms()
                .stream()
                .map(c -> new PipelineComponentBasicInfo(c.getName(), c.getDescription()))
                .collect(Collectors.toSet());
    }


    //INTERNAL
    /** Returns the details of a specified algorithm.
     *
     * @param algorithmName  The name of the algorithm to retrieve.
     * @return  The specified algorithm
     * @throws WfmProcessingException
     */
    @RequestMapping( value = {"/pipeline-algorithms/{algorithmName}"},
            method = RequestMethod.GET,
            produces = "application/json")
    @ResponseBody
    public AlgorithmDefinition getPipelineAlgorithm(
            @PathVariable("algorithmName") String algorithmName) throws WfmProcessingException {
        AlgorithmDefinition algorithmDefinition = pipelineService.getAlgorithm(algorithmName);
        if (algorithmDefinition == null) {
            throw new NotFoundWfmProcessingException("Algorithm not found: " + algorithmName + ".");
        }
        return algorithmDefinition;
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
    @ExceptionHandler(WfmProcessingException.class)
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
        	modifiedProperties = objectMapper.readValue(actionModel.getProperties(), HashMap.class);
        } catch (IOException e) {
            log.error("error getting properties with message: {}", e.getMessage());
            responseTuple = new Tuple<Boolean, String>(false, e.getMessage());
            return JsonView.Render(responseTuple, response);
        }

        try {

            // Only allow action names that contain uppercase letters, numbers, dash, hyphen or white space.
            // This method may throw a WfmProcessingException, allowing for rejection of the action if the name doesn't validate.
            String name = actionModel.getActionName().toUpperCase();
            validatePipelineOrTaskOrActionName(name);

            saveAction(actionModel, modifiedProperties);
            log.debug("Successfully created action: " + actionModel.getActionName());
        	responseTuple = new Tuple<>(true, null);
        }
        catch (WfmProcessingException ex) {
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

                // Only allow pipelines names that contain uppercase letters, numbers, dash, hyphen or white space. Reject the pipeline if the name doesn't validate.
                validatePipelineOrTaskOrActionName(name);

                //actions is handled by "/createaction"
                if(type.equals("task")) {
                    TaskDefinition taskDefinition = new TaskDefinition(name, description);
                    for(String actionName : addToPipelineModel.getItemsToAdd()) {
                        taskDefinition.getActions().add( new ActionDefinitionRef(actionName) );
                    }
                    pipelineService.saveTask(taskDefinition);
                }
                else if(type.equals("pipeline")) {
                    PipelineDefinition pipelineDefinition = new PipelineDefinition(name, description);
                    for(String taskName : addToPipelineModel.getItemsToAdd()) {
                        pipelineDefinition.getTaskRefs().add( new TaskDefinitionRef(taskName) );
                    }
                    pipelineService.savePipeline(pipelineDefinition);
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

    /** Only allow pipeline, task or action names that contain uppercase letters, numbers, dash, hyphen or white space.
     * The pipeline, task or action should be rejected if the name doesn't validate.
     * This method assumes the conversion from lower to uppercase has already been done, so there is no need to check for lowercase letters.
     * @param name name of the task or pipeline to be validated.
     * @exception WfmProcessingException is thrown if the pipeline, task or action name doesn't validate
     **/
    private static void validatePipelineOrTaskOrActionName(String name) throws WfmProcessingException {
        if ( !name.matches("([A-Z0-9 _\\-()])+") ) {
            throw new WfmProcessingException("Pipeline, task, and action names can only contain uppercase letters, " +
                    "numbers, dashes, hyphens, parentheses, and white space. \"" + name + "\" didn't validate.");
        }
    }
}
