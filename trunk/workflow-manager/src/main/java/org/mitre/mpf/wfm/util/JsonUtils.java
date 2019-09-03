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

package org.mitre.mpf.wfm.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.javasimon.aop.Monitored;
import org.mitre.mpf.interop.JsonAction;
import org.mitre.mpf.interop.JsonPipeline;
import org.mitre.mpf.interop.JsonTask;
import org.mitre.mpf.interop.util.InstantJsonModule;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.ActionType;
import org.mitre.mpf.rest.api.pipelines.Pipeline;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

@Component(JsonUtils.REF)
@Scope("singleton") // We only ever want 1 and only 1 instance of this class.
@Monitored
public class JsonUtils {
    private static final Logger log = LoggerFactory.getLogger(JsonUtils.class);
    public static final String REF = "jsonUtils";

    /**
     * Single instance of the ObjectMapper which converts between Java objects and binary JSON. Binary JSON is expected
     * to be significantly more compact than text-based JSON.
     */
    private final ObjectMapper _smileObjectMapper;

    /**
     * Single instance of the ObjectMapper which converts between Java objects and text-based JSON. While less efficient
     * in terms of space, text-based JSON remains user-readable.
     */
    private final ObjectMapper _jsonObjectMapper;


    @Inject
    public JsonUtils(ObjectMapper jsonObjectMapper) {
        _smileObjectMapper = new ObjectMapper(new SmileFactory());
        _smileObjectMapper.registerModule(new InstantJsonModule());
        _smileObjectMapper.registerModule(new Jdk8Module());

        _jsonObjectMapper = jsonObjectMapper;
    }


    /** Parses the provided smile binary JSON object as an instance of the specified type or throws an exception if this conversion cannot be performed. */
    public <T> T deserialize(byte[] json, Class<T> targetClass) throws WfmProcessingException {
        assert json != null : "The input json must not be null.";
        assert targetClass != null : "The targetClass parameter must not be null.";

        try {
            return _smileObjectMapper.readValue(json, targetClass);
        } catch(IOException ioe) {
            throw new WfmProcessingException(String.format("Failed to deserialize instance of '%s': %s", targetClass.getSimpleName(), ioe.getMessage()), ioe);
        }
    }

    /** Parses the provided text JSON object as an instance of the specified type or throws an exception if this conversion cannot be performed. */
    public <T> T deserializeFromText(byte[] json, Class<T> targetClass) throws WfmProcessingException {
        assert json != null : "The input json must not be null.";
        assert targetClass != null : "The targetClass parameter must not be null.";

        try {
            return _jsonObjectMapper.readValue(json, targetClass);
        } catch (IOException ioe) {
            throw new WfmProcessingException(String.format("Failed to deserialize instance of '%s': %s", targetClass.getSimpleName(), ioe.getMessage()), ioe);
        }
    }

    /** Serializes the specified object to a smile binary JSON or throws an exception if the serialization cannot be performed. */
    public byte[] serialize(Object object) throws WfmProcessingException {
        try {
            return _smileObjectMapper.writeValueAsBytes(object);
        } catch(IOException ioe) {
            throw new WfmProcessingException(String.format("Failed to serialize '%s': %s", object, ioe.getMessage()), ioe);
        }
    }

    /** Serializes the specified object to a smile binary JSON or throws an exception if the serialization cannot be performed. */
    public byte[] serializeAsText(Object object) throws WfmProcessingException {
        try {
            return _jsonObjectMapper.writeValueAsBytes(object);
        } catch(IOException ioe) {
            throw new WfmProcessingException(String.format("Failed to serialize '%s': %s", object, ioe.getMessage()), ioe);
        }
    }

    /** Serializes the specified object to a file using text-based JSON or throws an exception if the serialization cannot be performed. */
    public void serialize(Object object, File targetFile) throws WfmProcessingException {
        try {
            _jsonObjectMapper.writeValue(targetFile, object);
        } catch(IOException ioe) {
            throw new WfmProcessingException(String.format("Failed to serialize '%s' to '%s': %s", object, targetFile, ioe.getMessage()), ioe);
        }
    }

    /** Serializes the specified object to an output stream using text-based JSON or throws an exception if the serialization cannot be performed. */
    public void serialize(Object object, OutputStream outputStream) throws WfmProcessingException {
        try {
            _jsonObjectMapper.writeValue(outputStream, object);
        } catch(IOException ioe) {
            throw new WfmProcessingException(String.format("Failed to serialize '%s' to output stream: %s", object, ioe.getMessage()), ioe);
        }
    }

    /** Serializes the specified object to a text JSON or throws an exception if the serialization cannot be performed. */
    public String serializeAsTextString(Object object) throws WfmProcessingException {
        try {
            return _jsonObjectMapper.writeValueAsString(object);
        } catch(IOException ioe) {
            throw new WfmProcessingException(String.format("Failed to serialize '%s': %s", object, ioe.getMessage()) ,ioe);
        }
    }


    public JsonPipeline convert(JobPipelineElements pipelineElements) {
        Pipeline pipeline = pipelineElements.getPipeline();
        JsonPipeline jsonPipeline = new JsonPipeline(pipeline.getName(), pipeline.getDescription());

        for (String taskName : pipeline.getTasks()) {
            Task task = pipelineElements.getTask(taskName);
            JsonTask jsonTask = new JsonTask(getActionType(pipelineElements, task).name(), taskName,
                                               task.getDescription());

            for (String actionName : task.getActions()) {
                Action action = pipelineElements.getAction(actionName);
                JsonAction jsonAction = new JsonAction(action.getAlgorithm(), actionName, action.getDescription());
                for (Action.Property property : action.getProperties()) {
                    jsonAction.getProperties().put(property.getName(), property.getValue());
                }
                jsonTask.getActions().add(jsonAction);
            }

            jsonPipeline.getTasks().add(jsonTask);
        }
        return jsonPipeline;
    }

    private static ActionType getActionType(JobPipelineElements pipeline, Task task) {
        Action action = pipeline.getAction(task.getActions().get(0));
        return pipeline.getAlgorithm(action.getAlgorithm()).getActionType();
    }

}
