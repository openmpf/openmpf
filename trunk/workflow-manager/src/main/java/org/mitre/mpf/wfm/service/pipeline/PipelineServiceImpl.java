/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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


package org.mitre.mpf.wfm.service.pipeline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import org.mitre.mpf.rest.api.pipelines.*;
import org.mitre.mpf.rest.api.pipelines.transients.TransientPipelineDefinition;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.springframework.core.io.WritableResource;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;


@Service
public class PipelineServiceImpl implements PipelineService, PipelinePartLookup {

    private final ObjectMapper _objectMapper;

    private final PipelineValidator _validator;


    private final Map<String, Algorithm> _algorithms = new HashMap<>();
    private final Map<String, Action> _actions = new HashMap<>();
    private final Map<String, Task> _tasks = new HashMap<>();
    private final Map<String, Pipeline> _pipelines = new HashMap<>();

    // Used by methods that work on any pipeline elements type so that they know where to save or load the
    // pipeline elements.
    private final Map<Map<String, ? extends PipelineElement>, WritableResource> _pipelineElementsToDefinitions;

    @Inject
    public PipelineServiceImpl(
            PropertiesUtil propertiesUtil,
            ObjectMapper objectMapper,
            PipelineValidator validator) throws IOException {
        _objectMapper = objectMapper;
        _validator = validator;

        _pipelineElementsToDefinitions = new IdentityHashMap<>(4);
        _pipelineElementsToDefinitions.put(_algorithms, propertiesUtil.getAlgorithmDefinitions());
        _pipelineElementsToDefinitions.put(_actions, propertiesUtil.getActionDefinitions());
        _pipelineElementsToDefinitions.put(_tasks, propertiesUtil.getTaskDefinitions());
        _pipelineElementsToDefinitions.put(_pipelines, propertiesUtil.getPipelineDefinitions());

        load(_algorithms, new TypeReference<>() { });
        load(_actions, new TypeReference<>() { });
        load(_tasks, new TypeReference<>() { });
        load(_pipelines, new TypeReference<>() { });
    }



    private <T extends PipelineElement> void load(
            Map<String, T> loadTarget, TypeReference<List<T>> typeReference) throws IOException {

        List<T> loadedList;
        try (var inputStream = _pipelineElementsToDefinitions.get(loadTarget).getInputStream()) {
            loadedList = _objectMapper.readValue(inputStream, typeReference);
        }
        loadedList.forEach(item -> add(item, loadTarget));
    }


    private <T extends PipelineElement> void add(T newItem, Map<String, T> existingItems) {
        _validator.validateOnAdd(newItem, existingItems);
        existingItems.put(newItem.getName(), newItem);
    }


    private <T extends PipelineElement> void save(T newItem, Map<String, T> existingItems) {
        add(newItem, existingItems);
        writeToDisk(existingItems);
    }

    private void delete(String name, Map<String, ?> items) {
        boolean existed = items.remove(fixName(name)) != null;
        if (existed) {
            writeToDisk(items);
        }
    }


    private void writeToDisk(Map<String, ?> items) {
        try (var outputStream = _pipelineElementsToDefinitions.get(items).getOutputStream()) {
            _objectMapper.writeValue(outputStream, items.values());
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String fixName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Name cannot be null.");
        }
        return name.strip().toUpperCase();
    }


    @Override
    public synchronized JobPipelineElements getBatchPipelineElements(String pipelineName) {
        pipelineName = fixName(pipelineName);
        _validator.verifyBatchPipelineRunnable(fixName(pipelineName), this);
        return getPipelineElements(pipelineName, this);
    }

    @Override
    public synchronized JobPipelineElements getStreamingPipelineElements(String pipelineName) {
        pipelineName = fixName(pipelineName);
        _validator.verifyStreamingPipelineRunnable(fixName(pipelineName), this);
        return getPipelineElements(pipelineName, this);
    }


    @Override
    public synchronized JobPipelineElements getBatchPipelineElements(
            TransientPipelineDefinition transientPipeline) {
        var transientPipelineLookup = new TransientPipelinePartLookup(transientPipeline, this);
        _validator.validateTransientPipeline(transientPipeline, transientPipelineLookup);
        return getPipelineElements(transientPipelineLookup.getPipelineName(),
                                   transientPipelineLookup);
    }


    private static JobPipelineElements getPipelineElements(String pipelineName,
                                                           PipelinePartLookup partLookup) {
        var pipeline = partLookup.getPipeline(fixName(pipelineName));

        var tasks = pipeline.getTasks()
                .stream()
                .map(partLookup::getTask)
                .collect(toList());

        var actions = tasks.stream()
                .flatMap(t -> t.getActions().stream())
                .map(partLookup::getAction)
                .collect(toList());

        var algorithms = actions.stream()
                .map(action -> partLookup.getAlgorithm(action.getAlgorithm()))
                .collect(toList());

        return new JobPipelineElements(pipeline, tasks, actions, algorithms);
    }


    @Override
    public synchronized Algorithm getAlgorithm(String name) {
        return _algorithms.get(fixName(name));
    }

    @Override
    public synchronized ImmutableList<Algorithm> getAlgorithms() {
        return ImmutableList.copyOf(_algorithms.values());
    }



    @Override
    public synchronized Action getAction(String name) {
        return _actions.get(fixName(name));
    }

    @Override
    public synchronized ImmutableList<Action> getActions() {
        return ImmutableList.copyOf(_actions.values());
    }

    @Override
    public synchronized Task getTask(String name) {
        return _tasks.get(fixName(name));
    }

    @Override
    public synchronized ImmutableList<Task> getTasks() {
        return ImmutableList.copyOf(_tasks.values());
    }

    @Override
    public synchronized Pipeline getPipeline(String name) {
        return _pipelines.get(fixName(name));
    }

    @Override
    public synchronized ImmutableList<Pipeline> getPipelines() {
        return ImmutableList.copyOf(_pipelines.values());
    }


    @Override
    public synchronized void save(Algorithm algorithm) {
        save(algorithm, _algorithms);
    }


    @Override
    public synchronized void save(Action action) {
        save(action, _actions);
    }


    @Override
    public synchronized void save(Task task) {
        save(task, _tasks);
    }


    @Override
    public synchronized void save(Pipeline pipeline) {
        save(pipeline, _pipelines);
    }


    @Override
    public synchronized void deleteAlgorithm(String algorithmName) {
        delete(algorithmName, _algorithms);
    }

    @Override
    public synchronized void deleteAction(String actionName) {
        delete(actionName, _actions);
    }


    @Override
    public synchronized void deleteTask(String taskName) {
        delete(taskName, _tasks);
    }


    @Override
    public synchronized void deletePipeline(String pipelineName) {
        delete(pipelineName, _pipelines);
    }
}
