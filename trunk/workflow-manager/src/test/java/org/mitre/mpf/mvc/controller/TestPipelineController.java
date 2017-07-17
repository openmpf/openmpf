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

import junit.framework.TestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runner.notification.RunListener;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.service.PipelineService;
import org.mitre.mpf.wfm.pipeline.xml.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@ContextConfiguration(locations = {"classpath:applicationContext.xml"})

@RunWith(SpringJUnit4ClassRunner.class)
@RunListener.ThreadSafe
public class TestPipelineController extends TestCase {

    @Autowired
    private PipelineService pipelineService;

    @Autowired
    private MockPipelineController mockPipelineController;

    private MockMvc mockMvc;

    @Before
    public void setup() throws WfmProcessingException {
        mockMvc = MockMvcBuilders.standaloneSetup(mockPipelineController).build();

        // Setup dummy algorithms
        AlgorithmDefinition def1 = new AlgorithmDefinition(ActionType.DETECTION, "TEST_DETECTION_ALG", "Test algorithm for detection.");
        def1.getProvidesCollection().getAlgorithmProperties().add(
                new PropertyDefinition("TESTPROP", ValueType.BOOLEAN, "Test property", "TRUE", null)
            );
        pipelineService.saveAlgorithm(def1);
        pipelineService.saveAlgorithm(new AlgorithmDefinition(ActionType.MARKUP, "TEST_MARKUP_ALG", "Test algorithm for markup."));

        // Setup dummy actions
        HashMap<String, String> props = new HashMap();
        props.put("TESTPROP", "FALSE");
        pipelineService.saveAction(
                new ActionDefinition("TEST_DETECTION_ACTION1", "TEST_DETECTION_ALG", "Test action for detection."));
        pipelineService.saveAction(
                new ActionDefinition("TEST_DETECTION_ACTION2", "TEST_DETECTION_ALG", "Second test action for detection."));
        pipelineService.saveAction(
                new ActionDefinition("TEST_MARKUP_ACTION1", "TEST_MARKUP_ALG", "Test action for markup."));


        // Setup dummy tasks
        TaskDefinition td = new TaskDefinition("TEST_DETECTION_TASK1", "Test task for detection.");
        td.getActions().add(0, new ActionDefinitionRef("TEST_DETECTION_ACTION1"));
        pipelineService.saveTask(td);

        td = new TaskDefinition("TEST_DETECTION_TASK2", "Test task for detection.");
        td.getActions().add(0, new ActionDefinitionRef("TEST_DETECTION_ACTION2"));
        pipelineService.saveTask(td);

        td = new TaskDefinition("TEST_MARKUP_TASK1", "Test task for markup.");
        td.getActions().add(0, new ActionDefinitionRef("TEST_MARKUP_ACTION1"));
        pipelineService.saveTask(td);
    }

    @After
    public void tearDown() throws WfmProcessingException {
        pipelineService.deleteTask("TEST_DETECTION_TASK1");
        pipelineService.deleteTask("TEST_DETECTION_TASK2");
        pipelineService.deleteTask("TEST_MARKUP_TASK1");

        pipelineService.deleteAction("TEST_DETECTION_ACTION1");
        pipelineService.deleteAction("TEST_DETECTION_ACTION2");
        pipelineService.deleteAction("TEST_MARKUP_ACTION1");

        pipelineService.deleteAlgorithm("TEST_MARKUP_ALG");
        pipelineService.deleteAlgorithm("TEST_DETECTION_ALG");
    }


    @Test
    public void testGetActions() throws Exception{
        mockMvc.perform(get("/pipeline-actions"))
                .andExpect(status().isOk());
    }
    @Test
    public void testAddAction() throws Exception{
        String actionName = "TEST_ADD_ACTION";

        mockMvc.perform(post("/pipeline-actions").contentType(MediaType.APPLICATION_JSON).content(
                "{\"actionName\": \"" + actionName + "\", \"actionDescription\": \"This is a test action\", \"algorithmName\": \"TEST_DETECTION_ALG\",\"properties\": \"{\\\"TESTPROP\\\":\\\"FALSE\\\"}\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/pipeline-actions/" + actionName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(actionName))
                .andExpect(jsonPath("$.description").value("This is a test action"))
                .andExpect(jsonPath("$.algorithmRef").value("TEST_DETECTION_ALG"))
                .andExpect(jsonPath("$.properties").isArray())
                .andExpect(jsonPath("$.properties").isNotEmpty());

        mockMvc.perform(delete("/pipeline-actions/" + actionName))
                .andExpect(status().isOk());

        mockMvc.perform(get("/pipeline-actions/" + actionName))
                .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    public void testAddActionErrors() throws Exception{
        String actionName = "TEST_ADD_ACTION_ERROR";

        mockMvc.perform(post("/pipeline-actions").contentType(MediaType.APPLICATION_JSON).content(
                "{\"actionName\": \"" + actionName + "\", \"actionDescription\": \"This is a test action\", \"algorithmName\": \"TEST_DETECTION_ALG\",\"properties\": \"Stuff\"}"))
                .andExpect(status().is(HttpStatus.BAD_REQUEST.value()))
                .andExpect(content().string("Invalid properties value: Stuff."));
        mockMvc.perform(post("/pipeline-actions").contentType(MediaType.APPLICATION_JSON).content(
                "{\"actionName\": \"" + actionName + "\", \"actionDescription\": \"This is a test action\", \"algorithmName\": \"Something\",\"properties\": \"{}\"}"))
                .andExpect(status().is(HttpStatus.BAD_REQUEST.value()))
                .andExpect(content().string("TEST_ADD_ACTION_ERROR: referenced algorithm SOMETHING does not exist."));
        mockMvc.perform(post("/pipeline-actions").contentType(MediaType.APPLICATION_JSON).content(
                "{\"actionName\": \"" + actionName + "\", \"actionDescription\": \"This is a test action\", \"algorithmName\": \"TEST_DETECTION_ALG\",\"properties\": \"{}\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/pipeline-actions").contentType(MediaType.APPLICATION_JSON).content(
                "{\"actionName\": \"" + actionName + "\", \"actionDescription\": \"This is a test action (duplicate)\", \"algorithmName\": \"TEST_DETECTION_ALG\",\"properties\": \"{}\"}"))
                .andExpect(status().is(HttpStatus.CONFLICT.value()))
                .andExpect(content().string("TEST_ADD_ACTION_ERROR: action name is already in use."));

        mockMvc.perform(delete("/pipeline-actions/" + actionName))
                .andExpect(status().isOk());
        mockMvc.perform(get("/pipeline-actions/" + actionName))
                .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }


    @Test
    public void testGetTasks() throws Exception{
        mockMvc.perform(get("/pipeline-tasks"))
                .andExpect(status().isOk());
    }

    @Test
    public void testAddTask() throws Exception{
        String taskName = "TEST_ADD_TASK";

        mockMvc.perform(get("/pipeline-tasks").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(post("/pipeline-tasks").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + taskName + "\", \"description\": \"This is a test task\", \"actionsToAdd\": [\"TEST_DETECTION_ACTION1\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/pipeline-tasks/" + taskName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(taskName))
                .andExpect(jsonPath("$.description").value("This is a test task"))
                .andExpect(jsonPath("$.actions").isArray())
                .andExpect(jsonPath("$.actions").isNotEmpty())
                .andExpect(jsonPath("$.actions[0].name").value("TEST_DETECTION_ACTION1"));;

        mockMvc.perform(delete("/pipeline-tasks/" + taskName))
                .andExpect(status().isOk());

        mockMvc.perform(get("/pipeline-tasks/" + taskName))
                .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    public void testAddTasksErrors() throws Exception{
        String taskName = "TEST_ADD_TASK_ERROR";

        mockMvc.perform(post("/pipeline-tasks").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + taskName + "\", \"description\": \"This is a test task\", \"actionsToAdd\": []}"))
                .andExpect(status().is(HttpStatus.BAD_REQUEST.value()))
                .andExpect(content().string("Tasks must contain at least one action."));

        mockMvc.perform(post("/pipeline-tasks").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + taskName + "\", \"description\": \"This is a test task\", \"actionsToAdd\": [\"INVALID ACTION NAME\"]}"))
                .andExpect(status().is(HttpStatus.BAD_REQUEST.value()))
                .andExpect(content().string("TEST_ADD_TASK_ERROR: The referenced action (INVALID ACTION NAME) does not exist."));
        mockMvc.perform(post("/pipeline-tasks").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + taskName + "\", \"description\": \"This is a test task\", \"actionsToAdd\": [\"TEST_DETECTION_ACTION1\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/pipeline-tasks").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + taskName + "\", \"description\": \"This is a test task (duplicate)\", \"actionsToAdd\": [\"TEST_DETECTION_ACTION1\"]}"))
                .andExpect(status().is(HttpStatus.CONFLICT.value()))
                .andExpect(content().string("TEST_ADD_TASK_ERROR: task name is already in use."));

        mockMvc.perform(delete("/pipeline-tasks/" + taskName))
                .andExpect(status().isOk());
        mockMvc.perform(get("/pipeline-tasks/" + taskName))
                .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    public void testAddTaskMultipleActions() throws Exception{
        String taskName = "TEST_MULTIPLE_ACTIONS_GOOD_TASK";

        mockMvc.perform(get("/pipeline-tasks").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(post("/pipeline-tasks").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + taskName + "\", \"description\": \"This is a test task\", \"actionsToAdd\": [\"TEST_DETECTION_ACTION1\", \"TEST_DETECTION_ACTION2\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/pipeline-tasks/" + taskName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(taskName))
                .andExpect(jsonPath("$.description").value("This is a test task"))
                .andExpect(jsonPath("$.actions").isArray())
                .andExpect(jsonPath("$.actions").isNotEmpty())
                .andExpect(jsonPath("$.actions[0].name").value("TEST_DETECTION_ACTION1"))
                .andExpect(jsonPath("$.actions[1].name").value("TEST_DETECTION_ACTION2"));

        mockMvc.perform(delete("/pipeline-tasks/" + taskName))
                .andExpect(status().isOk());

        mockMvc.perform(get("/pipeline-tasks/" + taskName))
                .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    public void testAddTaskMultipleActionsInvalid() throws Exception{
        String taskName = "TEST_MULTIPLE_ACTIONS_BAD_TASK";

        mockMvc.perform(get("/pipeline-tasks").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(post("/pipeline-tasks").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + taskName + "\", \"description\": \"This is a test task\", \"actionsToAdd\": [\"TEST_DETECTION_ACTION1\", \"TEST_MARKUP_ACTION_1\"]}"))
                .andExpect(status().is(HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(get("/pipeline-tasks/" + taskName))
                .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    public void testGetPipelines() throws Exception{
        mockMvc.perform(get("/pipelines"))
                .andExpect(status().isOk());
    }

    @Test
    public void testAddPipelines() throws Exception{
        String pipelineName = "TEST_ADD_PIPELINE";

        mockMvc.perform(get("/pipelines").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(post("/pipelines").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + pipelineName + "\", \"description\": \"This is a test pipeline\", \"tasksToAdd\": [\"TEST_DETECTION_TASK1\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/pipelines/" + pipelineName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(pipelineName))
                .andExpect(jsonPath("$.description").value("This is a test pipeline"))
                .andExpect(jsonPath("$.taskRefs").isArray())
                .andExpect(jsonPath("$.taskRefs").isNotEmpty())
                .andExpect(jsonPath("$.taskRefs[0].name").value("TEST_DETECTION_TASK1"));;

        mockMvc.perform(delete("/pipelines/" + pipelineName))
                .andExpect(status().isOk());

        mockMvc.perform(get("/pipelines/" + pipelineName))
                .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    public void testAddPipelinesErrors() throws Exception{
        String pipelineName = "TEST_ADD_PIPELINES_ERROR";

        mockMvc.perform(post("/pipelines").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + pipelineName + "\", \"description\": \"This is a test pipeline\", \"tasksToAdd\": []}"))
                .andExpect(status().is(HttpStatus.BAD_REQUEST.value()))
                .andExpect(content().string("Pipelines must contain at least one task."));

        mockMvc.perform(post("/pipelines").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + pipelineName + "\", \"description\": \"This is a test pipeline\", \"tasksToAdd\": [\"INVALID TASK NAME\"]}"))
                .andExpect(status().is(HttpStatus.BAD_REQUEST.value()))
                .andExpect(content().string("TEST_ADD_PIPELINES_ERROR: Task with name INVALID TASK NAME does not exist."));
        mockMvc.perform(post("/pipelines").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + pipelineName + "\", \"description\": \"This is a test pipeline\", \"tasksToAdd\": [\"TEST_DETECTION_TASK1\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/pipelines").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + pipelineName + "\", \"description\": \"This is a test pipeline (duplicate)\", \"tasksToAdd\": [\"TEST_DETECTION_TASK1\"]}"))
                .andExpect(status().is(HttpStatus.CONFLICT.value()))
                .andExpect(content().string("TEST_ADD_PIPELINES_ERROR: pipeline name is already in use."));

        mockMvc.perform(delete("/pipelines/" + pipelineName))
                .andExpect(status().isOk());
        mockMvc.perform(get("/pipelines/" + pipelineName))
                .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    public void testAddPipelinesTaskOrder() throws Exception{
        String pipelineName = "TEST_GOOD_TASK_ORDER_PIPELINE";

        mockMvc.perform(get("/pipelines").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(post("/pipelines").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + pipelineName + "\", \"description\": \"This is a test pipeline\", \"tasksToAdd\": [\"TEST_DETECTION_TASK1\", \"TEST_MARKUP_TASK1\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/pipelines/" + pipelineName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(pipelineName))
                .andExpect(jsonPath("$.description").value("This is a test pipeline"))
                .andExpect(jsonPath("$.taskRefs").isArray())
                .andExpect(jsonPath("$.taskRefs").isNotEmpty())
                .andExpect(jsonPath("$.taskRefs[0].name").value("TEST_DETECTION_TASK1"))
                .andExpect(jsonPath("$.taskRefs[1].name").value("TEST_MARKUP_TASK1"));

        mockMvc.perform(delete("/pipelines/" + pipelineName))
                .andExpect(status().isOk());

        mockMvc.perform(get("/pipelines/" + pipelineName))
                .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    public void testAddPipelinesTaskOrderInvalid() throws Exception{
        String pipelineName = "TEST_BAD_TASK_ORDER_PIPELINE";

        mockMvc.perform(get("/pipelines").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(post("/pipelines").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + pipelineName + "\", \"description\": \"This is a test pipeline\", \"tasksToAdd\": [\"TEST_MARKUP_TASK1\", \"TEST_DETECTION_TASK1\"]}"))
                .andExpect(status().is(HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(get("/pipelines/" + pipelineName))
                .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }
}