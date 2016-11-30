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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.codehaus.jackson.map.ObjectMapper;
import org.mitre.mpf.mvc.model.ActionModel;
import org.mitre.mpf.mvc.model.AddToPipelineModel;
import org.mitre.mpf.mvc.model.PipelinesModel;
import org.mitre.mpf.mvc.util.JsonView;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.pipeline.xml.*;
import org.mitre.mpf.wfm.service.PipelineService;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Api( value = "Pipelines",
	description = "Available job pipeline information" )
@Controller
@Scope("request")
@Profile("website")
public class PipelineController {

    private static final Logger log = LoggerFactory.getLogger(PipelineController.class);

    public static final String DEFAULT_ERROR_VIEW = "error";

    @Autowired
    private PipelineService pipelineService;

    @Autowired
    @Qualifier(PropertiesUtil.REF)
    private PropertiesUtil propertiesUtil;

    private List<String> getAvailablePipelinesVersionOne() {
    	return pipelineService.getPipelineNames();
    }
    
    /*
     *	/pipelines
     */
    //EXTERNAL
    //1. REST API - Gets the available pipelines that can be used to process media URIs
    @RequestMapping(value = {"/rest/pipelines"}, method = RequestMethod.GET)
	@ApiOperation(value="Retrieves list of available pipelines.",
		notes="The response will be a JSON array of strings with each item being a pipeline.",
		produces="application/json", response=String.class, responseContainer="List" )
    @ApiResponses(value = { 
    		@ApiResponse(code = 200, message = "Successful response"), 
    		@ApiResponse(code = 401, message = "Bad credentials") })
    @ResponseBody
    public List<String> getAvailablePipelinesRest(/*@ApiParam(required=false,
			value="The version of this request - NOT IMPLEMENTED")    		
    		@RequestParam(value = "v", required = false) String v*/) {
    	return getAvailablePipelinesVersionOne();
    }


    @RequestMapping(value = {"/pipelines/details"}, method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public String getAvailablePipelinesDetails() {
        return pipelineService.getPipelineDefinitionAsJson();
    }


    //INTERNAL
    @RequestMapping(value = {"/pipelines"}, method = RequestMethod.GET)
    @ResponseBody
    public List<String> getAvailablePipelinesSession() {
    	return getAvailablePipelinesVersionOne();
    }
        
    @RequestMapping(value = "/pipelines/model", method = RequestMethod.GET)
    @ResponseBody
    public PipelinesModel getPipelinesModel() {
    	return new PipelinesModel(pipelineService.getAlgorithmNames(), pipelineService.getActionNames(),
                pipelineService.getTaskNames(), pipelineService.getPipelineNames());
    }    
    
    @RequestMapping(value = "/pipelines/algorithm-properties", method = RequestMethod.GET)
    @ResponseBody
    public List<PropertyDefinition> getAlgorithmPropertiesJson(
            @RequestParam(value = "algName", required = true) String algName) {

        return pipelineService.getAlgorithmProperties(algName);
    }

    @RequestMapping(value = "/pipelines/create-action", method = RequestMethod.POST)
    @ResponseBody
    public Object createActionJson(@RequestBody ActionModel actionModel, HttpServletResponse response) throws WfmProcessingException {
        Tuple<Boolean,String> responseTuple = null;

        //IMPORTANT!! - only passing in the modified properties in this method
        Map<String, String> modifiedProperties = new HashMap<String, String>();
        try {
        	modifiedProperties = new ObjectMapper().readValue(actionModel.getProperties(), HashMap.class);
        } catch (IOException e) {
            log.error("error getting properties with message: {}", e.getMessage());
            responseTuple = new Tuple<Boolean, String>(false, e.getMessage());
            return JsonView.Render(responseTuple, response);
        }

        //unlike "/addToPipelineController" - an action, is added and save is done at the same time, did not want to change existing logic
        Tuple<Boolean,String> saveTuple = pipelineService.addAndSaveAction(actionModel.getActionName().toUpperCase(),
                actionModel.getActionDescription(), actionModel.getAlgorithmName(), modifiedProperties);
        if (!saveTuple.getFirst()) {
        	log.error("new action creation failed and nothing was saved to xml");
        } else {
        	log.debug("Successfully created action ", saveTuple.getSecond());
        }
        responseTuple = saveTuple;
        return JsonView.Render(responseTuple, response);
    }

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
            String type = addToPipelineModel.getType();

            //actions is handled by "/createaction"
            if(type.equals("task")) {
                TaskDefinition taskDefinition = new TaskDefinition(name, description);
                for(String actionName : addToPipelineModel.getItemsToAdd()) {
                    taskDefinition.getActions().add( new ActionDefinitionRef(actionName) );
                }
                successfulAdd = pipelineService.addTask(taskDefinition);
            }
            else if(type.equals("pipeline")) {
                PipelineDefinition pipelineDefinition = new PipelineDefinition(name, description);
                for(String taskName : addToPipelineModel.getItemsToAdd()) {
                    pipelineDefinition.getTaskRefs().add( new TaskDefinitionRef(taskName) );
                }
                successfulAdd = pipelineService.addPipeline(pipelineDefinition);
            }

            if(!successfulAdd) {
                //responseMap.put(false, "failed to add the " + type + " check logs for detailed error");
                responseTuple = new Tuple<Boolean, String>(false, "failed to add the " + type + " check logs for detailed error");
            } else {
            	log.debug("success adding to the {} collection", type);
                //only try to save if an add was successful
                Tuple<Boolean,String> saveTuple = pipelineService.savePipelineChanges(type);
                if(!saveTuple.getFirst()) {
                    responseTuple = saveTuple;
                    log.error("failure saving to the {} xml file", type);
                } else {
                    responseTuple = saveTuple;
                    log.debug("success adding and saving to the {} xml file", type);
                }
            }
        } else {
        	//assuming that the addToPipelineModel is not null and the user forgot to add items before clicking create
            responseTuple = new Tuple<Boolean, String>(false, "Please add items before clicking 'Create'!");
        }

        return JsonView.Render(responseTuple, response);
    }
}
