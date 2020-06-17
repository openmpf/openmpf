/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2020 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2020 The MITRE Corporation                                       *
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
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runner.notification.RunListener;
import org.mitre.mpf.rest.api.pipelines.*;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.service.WorkflowProperty;
import org.mitre.mpf.wfm.service.WorkflowPropertyService;
import org.mitre.mpf.wfm.service.pipeline.PipelineServiceImpl;
import org.mitre.mpf.wfm.service.pipeline.PipelineValidator;
import org.mitre.mpf.wfm.util.ObjectMapperFactory;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@ContextConfiguration(classes = PipelineController.class)

@RunWith(SpringJUnit4ClassRunner.class)
@RunListener.ThreadSafe
public class TestPipelineController {

    private MockMvc mockMvc;

    private final ObjectMapper _objectMapper = ObjectMapperFactory.customObjectMapper();

    @Rule
    public TemporaryFolder _tempFolder = new TemporaryFolder();


    @Before
    public void setup() throws WfmProcessingException, IOException {
        var mockPropertiesUtil = mock(PropertiesUtil.class);
        TestUtil.initPipelineDataFiles(mockPropertiesUtil, _tempFolder);

        var mockWorkflowPropertyService = mock(WorkflowPropertyService.class);
        when(mockWorkflowPropertyService.getProperties())
                .thenReturn(ImmutableList.of(
                        new WorkflowProperty("TEST_WF_PROPERTY", "WF PROP DESCR",
                                             ValueType.INT, "5", null,
                                             List.of(org.mitre.mpf.wfm.enums.MediaType.VIDEO))));
        when(mockWorkflowPropertyService.getPropertyValue("TEST_WF_PROPERTY"))
                .thenReturn("5");

        var springValidator = new LocalValidatorFactoryBean();
        springValidator.afterPropertiesSet();
        var pipelineValidator = new PipelineValidator(springValidator, mockWorkflowPropertyService);

        var pipelineService = new PipelineServiceImpl(mockPropertiesUtil, _objectMapper, pipelineValidator);

        var pipelineController = new PipelineController(mockPropertiesUtil, mockWorkflowPropertyService,
                                                        pipelineService);

        mockMvc = MockMvcBuilders.standaloneSetup(pipelineController).build();


        var testAlgoProp = new AlgorithmProperty(
                "TEST_ALGO_PROP", "Test algo property", ValueType.BOOLEAN, "TRUE", null);

        var algorithm1 = new Algorithm(
                "TEST_DETECTION_ALG", "Test algorithm for detection.", ActionType.DETECTION,
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of(testAlgoProp)),
                true, false);
        pipelineService.save(algorithm1);

        var markupAlgo = new Algorithm(
                "TEST_MARKUP_ALG", "Test algorithm for markup.", ActionType.MARKUP,
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), List.of()),
                true, false);
        pipelineService.save(markupAlgo);


        // Setup dummy actions
        pipelineService.save(new Action("TEST_DETECTION_ACTION1", "Test action for detection.",
                                        "TEST_DETECTION_ALG", List.of()));

        pipelineService.save(new Action("TEST_DETECTION_ACTION2", "Second test action for detection.",
                                        "TEST_DETECTION_ALG", List.of()));

        pipelineService.save(new Action("TEST_MARKUP_ACTION1", "Test action for markup.",
                                        "TEST_MARKUP_ALG", List.of()));


        // Setup dummy tasks
        pipelineService.save(new Task("TEST_DETECTION_TASK1", "Test task for detection.",
                                      List.of("TEST_DETECTION_ACTION1")));

        pipelineService.save(new Task("TEST_DETECTION_TASK2", "Test task for detection.",
                                      List.of("TEST_DETECTION_ACTION2")));

        pipelineService.save(new Task("TEST_MARKUP_TASK1", "Test task for markup.",
                                      List.of("TEST_MARKUP_ACTION1")));


        pipelineService.save(new Pipeline("TEST_PIPELINE", "Test pipeline",
                                          List.of("TEST_DETECTION_TASK1")));
    }


    @Test
    public void testGetAlgorithms() throws Exception {
        mockMvc.perform(get("/algorithms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isNotEmpty());
    }


    @Test
    public void testGetAlgorithm() throws Exception {
        var mvcResult = mockMvc.perform(get("/algorithms?name=TEST_DETECTION_ALG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("TEST_DETECTION_ALG"))
                .andExpect(jsonPath("$.description").value("Test algorithm for detection."))
                .andExpect(jsonPath("$.actionType").value("DETECTION"))
                .andReturn();

        var algorithm = _objectMapper.readValue(mvcResult.getResponse().getContentAsByteArray(), Algorithm.class);

        var properties = algorithm.getProvidesCollection().getProperties();
        assertEquals(2, properties.size());
        

        var algoProp = properties.stream()
                .filter(p -> p.getName().equals("TEST_ALGO_PROP"))
                .findAny()
                .orElse(null);
        assertNotNull(algoProp);
        assertEquals("Test algo property", algoProp.getDescription());
        assertEquals("TRUE", algoProp.getDefaultValue());
        assertEquals(ValueType.BOOLEAN, algoProp.getType());
        assertNull(algoProp.getPropertiesKey());


        var wfProp = properties.stream()
                .filter(p -> p.getName().equals("TEST_WF_PROPERTY"))
                .findAny()
                .orElse(null);
        assertNotNull(wfProp);
        assertEquals("WF PROP DESCR", wfProp.getDescription());
        assertEquals("5", wfProp.getDefaultValue());
        assertEquals(ValueType.INT, wfProp.getType());
        assertNull(wfProp.getPropertiesKey());
    }


    @Test
    public void testGetActions() throws Exception {
        mockMvc.perform(get("/actions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isNotEmpty());
    }

    @Test
    public void testAddAction() throws Exception {
        var actionName = "TEST_ADD_ACTION";

        mockMvc.perform(post("/actions").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + actionName + "\", \"description\": \"This is a test action\", \"algorithm\": \"TEST_DETECTION_ALG\",\"properties\": [{ \"name\": \"TEST_ALGO_PROP\", \"value\": \"FALSE\" }] }"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actions?name=" + actionName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(actionName))
                .andExpect(jsonPath("$.description").value("This is a test action"))
                .andExpect(jsonPath("$.algorithm").value("TEST_DETECTION_ALG"))
                .andExpect(jsonPath("$.properties[0].name").value("TEST_ALGO_PROP"))
                .andExpect(jsonPath("$.properties[0].value").value("FALSE"));

        mockMvc.perform(delete("/actions?name=" + actionName))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actions?name=" + actionName))
                .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    public void testAddActionErrors() throws Exception {
        var actionName = "TEST_ADD_ACTION_ERROR";

        mockMvc.perform(post("/actions").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + actionName + "\", \"description\": \"This is a test action\", \"algorithm\": \"TEST_DETECTION_ALG\",\"properties\": \"Stuff\"}"))
                .andExpect(status().is(HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(post("/actions").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + actionName + "\", \"description\": \"This is a test action\", \"algorithm\": \"TEST_DETECTION_ALG\",\"properties\": []}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/actions").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + actionName + "\", \"description\": \"This is a test action (duplicate)\", \"algorithm\": \"TEST_DETECTION_ALG\",\"properties\": []}"))
                .andExpect(status().is(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").value("Failed to add Action with name \"TEST_ADD_ACTION_ERROR\" because another Action with the same name already exists."));

        mockMvc.perform(delete("/actions?name=" + actionName))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actions?name=" + actionName))
                .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }


    @Test
    public void testAddActionWithMissingAlgorithm() throws Exception {
        var actionName = "ACTION_MISSING_ALGO";
        mockMvc.perform(post("/actions").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + actionName + "\", \"description\": \"This is a test action\", \"algorithm\": \"MISSING ALGO\",\"properties\": []}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actions?=name" + actionName))
                .andExpect(status().isOk());
    }



    @Test
    public void testGetTasks() throws Exception {
        mockMvc.perform(get("/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isNotEmpty());
    }

    @Test
    public void testAddTask() throws Exception {
        var taskName = "TEST_ADD_TASK";

        mockMvc.perform(get("/tasks").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(post("/tasks").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + taskName + "\", \"description\": \"This is a test task\", \"actions\": [\"TEST_DETECTION_ACTION1\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/tasks?name=" + taskName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(taskName))
                .andExpect(jsonPath("$.description").value("This is a test task"))
                .andExpect(jsonPath("$.actions").isArray())
                .andExpect(jsonPath("$.actions").isNotEmpty())
                .andExpect(jsonPath("$.actions[0]").value("TEST_DETECTION_ACTION1"));;

        mockMvc.perform(delete("/tasks?name=" + taskName))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tasks?name=" + taskName))
                .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    public void testAddTasksErrors() throws Exception{
        String taskName = "TEST_ADD_TASK_ERROR";

        mockMvc.perform(post("/tasks").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + taskName + "\", \"description\": \"This is a test task\", \"actions\": []}"))
                .andExpect(status().is(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").value("TEST_ADD_TASK_ERROR has errors in the following fields:\n" +
                                                               "actions=\"[]\": may not be empty"));

        mockMvc.perform(post("/tasks").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + taskName + "\", \"description\": \"This is a test task\", \"actions\": [\"TEST_DETECTION_ACTION1\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/tasks").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + taskName + "\", \"description\": \"This is a test task (duplicate)\", \"actions\": [\"TEST_DETECTION_ACTION1\"]}"))
                .andExpect(status().is(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").value("Failed to add Task with name \"TEST_ADD_TASK_ERROR\" because another Task with the same name already exists."));

        mockMvc.perform(delete("/tasks?name=" + taskName))
                .andExpect(status().isOk());
        mockMvc.perform(get("/tasks?name=" + taskName))
                .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }


    @Test
    public void testAddTaskWithMissingActions() throws Exception {
        var taskName = "TASK WITH MISSING ACTION";
        mockMvc.perform(post("/tasks").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + taskName + "\", \"description\": \"This is a test task\", \"actions\": [\"MISSING ACTION1\", \"MISSING ACTION2\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/tasks?name=" + taskName))
                .andExpect(status().isOk());
    }


    @Test
    public void testAddTaskMultipleActions() throws Exception {
        var taskName = "TEST_MULTIPLE_ACTIONS_GOOD_TASK";

        mockMvc.perform(get("/tasks").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(post("/tasks").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + taskName + "\", \"description\": \"This is a test task\", \"actions\": [\"TEST_DETECTION_ACTION1\", \"TEST_DETECTION_ACTION2\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/tasks?name=" + taskName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(taskName))
                .andExpect(jsonPath("$.description").value("This is a test task"))
                .andExpect(jsonPath("$.actions").isArray())
                .andExpect(jsonPath("$.actions").isNotEmpty())
                .andExpect(jsonPath("$.actions[0]").value("TEST_DETECTION_ACTION1"))
                .andExpect(jsonPath("$.actions[1]").value("TEST_DETECTION_ACTION2"));

        mockMvc.perform(delete("/tasks?name=" + taskName))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tasks?name=" + taskName))
                .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }


    @Test
    public void testGetPipelines() throws Exception {
        mockMvc.perform(get("/pipelines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isNotEmpty());
    }

    @Test
    public void testAddPipelines() throws Exception {
        var pipelineName = "TEST_ADD_PIPELINE";

        mockMvc.perform(get("/pipelines").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(post("/pipelines").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + pipelineName + "\", \"description\": \"This is a test pipeline\", \"tasks\": [\"TEST_DETECTION_TASK1\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/pipelines?name=" + pipelineName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(pipelineName))
                .andExpect(jsonPath("$.description").value("This is a test pipeline"))
                .andExpect(jsonPath("$.tasks").isArray())
                .andExpect(jsonPath("$.tasks").isNotEmpty())
                .andExpect(jsonPath("$.tasks[0]").value("TEST_DETECTION_TASK1"));;

        mockMvc.perform(delete("/pipelines?name=" + pipelineName))
                .andExpect(status().isOk());

        mockMvc.perform(get("/pipelines?name=" + pipelineName))
                .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    public void testAddPipelinesErrors() throws Exception {
        var pipelineName = "TEST_ADD_PIPELINES_ERROR";

        mockMvc.perform(post("/pipelines").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + pipelineName + "\", \"description\": \"This is a test pipeline\", \"tasks\": []}"))
                .andExpect(status().is(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").value("TEST_ADD_PIPELINES_ERROR has errors in the following fields:\n" +
                                                               "tasks=\"[]\": may not be empty"));

        mockMvc.perform(post("/pipelines").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + pipelineName + "\", \"description\": \"This is a test pipeline\", \"tasks\": [\"TEST_DETECTION_TASK1\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/pipelines").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + pipelineName + "\", \"description\": \"This is a test pipeline (duplicate)\", \"tasks\": [\"TEST_DETECTION_TASK1\"]}"))
                .andExpect(status().is(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").value("Failed to add Pipeline with name \"TEST_ADD_PIPELINES_ERROR\" because another Pipeline with the same name already exists."));

        mockMvc.perform(delete("/pipelines?name=" + pipelineName))
                .andExpect(status().isOk());
        mockMvc.perform(get("/pipelines?name=" + pipelineName))
                .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }


    @Test
    public void testAddPipelineWithMissingTask() throws Exception {
        var pipelineName = "PIPELINE  WITH MISSING TASK";
        mockMvc.perform(post("/pipelines").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + pipelineName + "\", \"description\": \"This is a test pipeline\", \"tasks\": [\"MISSING TASK\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/pipelines?name=" + pipelineName))
                .andExpect(status().isOk());
    }

    @Test
    public void testAddWithSpecialCharacters() throws Exception {
        var pipelineName = "~`!@#$%^&* ()_+-=[]\\{}|;':\",./<>?";
        var jsonEncodedName = "~`!@#$%^&* ()_+-=[]\\\\{}|;':\\\",./<>?";

        mockMvc.perform(post("/pipelines").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + jsonEncodedName + "\", \"description\": \"This is a test pipeline\", \"tasks\": [\"TASK\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/pipelines?name={name}", pipelineName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(pipelineName));
    }


    @Test
    public void testAddPipelinesTaskOrder() throws Exception {
        var pipelineName = "TEST_GOOD_TASK_ORDER_PIPELINE";

        mockMvc.perform(get("/pipelines").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(post("/pipelines").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"" + pipelineName + "\", \"description\": \"This is a test pipeline\", \"tasks\": [\"TEST_DETECTION_TASK1\", \"TEST_MARKUP_TASK1\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/pipelines?name=" + pipelineName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(pipelineName))
                .andExpect(jsonPath("$.description").value("This is a test pipeline"))
                .andExpect(jsonPath("$.tasks").isArray())
                .andExpect(jsonPath("$.tasks").isNotEmpty())
                .andExpect(jsonPath("$.tasks[0]").value("TEST_DETECTION_TASK1"))
                .andExpect(jsonPath("$.tasks[1]").value("TEST_MARKUP_TASK1"));

        mockMvc.perform(delete("/pipelines?name=" + pipelineName))
                .andExpect(status().isOk());

        mockMvc.perform(get("/pipelines?name=" + pipelineName))
                .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }
}
