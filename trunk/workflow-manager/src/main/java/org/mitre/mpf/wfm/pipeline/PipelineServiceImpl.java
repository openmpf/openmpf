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


package org.mitre.mpf.wfm.pipeline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mitre.mpf.interop.JsonPipeline;
import org.mitre.mpf.rest.api.pipelines.*;
import org.mitre.mpf.wfm.data.entities.transients.TransientPipeline;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.springframework.core.io.WritableResource;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toList;


@Service
public class PipelineServiceImpl implements PipelineService {

    private final ObjectMapper _objectMapper;

    private final PipelineValidator _validator;

    private final JsonUtils _jsonUtils;


    private final Map<String, Algorithm> _algorithms = new HashMap<>();
    private final Map<String, Action> _actions = new HashMap<>();
    private final Map<String, Task> _tasks = new HashMap<>();
    private final Map<String, Pipeline> _pipelines = new HashMap<>();

    // Used by methods that work on any pipeline component type so that they know where to save or load the
    // pipeline components.
    private final Map<Map<String, ? extends PipelineComponent>, WritableResource> _pipelineComponentsToDefinitions;

    @Inject
    public PipelineServiceImpl(
            PropertiesUtil propertiesUtil,
            ObjectMapper objectMapper,
            PipelineValidator validator,
            JsonUtils jsonUtils) throws IOException {
        _objectMapper = objectMapper;
        _validator = validator;
        _jsonUtils = jsonUtils;

        _pipelineComponentsToDefinitions = new IdentityHashMap<>(4);
        _pipelineComponentsToDefinitions.put(_algorithms, propertiesUtil.getAlgorithmDefinitions());
        _pipelineComponentsToDefinitions.put(_actions, propertiesUtil.getActionDefinitions());
        _pipelineComponentsToDefinitions.put(_tasks, propertiesUtil.getTaskDefinitions());
        _pipelineComponentsToDefinitions.put(_pipelines, propertiesUtil.getPipelineDefinitions());

        load(_algorithms, new TypeReference<>() { });
        load(_actions, new TypeReference<>() { });
        load(_tasks, new TypeReference<>() { });
        load(_pipelines, new TypeReference<>() { });
    }



    private <T extends PipelineComponent> void load(
            Map<String, T> loadTarget, TypeReference<List<T>> typeReference) throws IOException {

        List<T> loadedList;
        try (var inputStream = _pipelineComponentsToDefinitions.get(loadTarget).getInputStream()) {
            loadedList = _objectMapper.readValue(inputStream, typeReference);
        }
        loadedList.forEach(item -> add(item, loadTarget));
    }


    private <T extends PipelineComponent> void add(T newItem, Map<String, T> existingItems) {
        _validator.validateOnAdd(newItem, existingItems);
        existingItems.put(newItem.getName(), newItem);
    }


    private <T extends PipelineComponent> void save(T newItem, Map<String, T> existingItems) {
        add(newItem, existingItems);
        writeToDisk(existingItems);
    }

    private void delete(String name, Map<String, ?> items) {
        boolean existed = items.remove(fixName(name)) != null;
        if (existed) {
            writeToDisk(_algorithms);
        }
    }


    private void writeToDisk(Map<String, ?> items) {
        try (OutputStream outputStream = _pipelineComponentsToDefinitions.get(items).getOutputStream()) {
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
    public TransientPipeline createTransientBatchPipeline(String pipelineName) {
        pipelineName = fixName(pipelineName);
        verifyBatchPipelineRunnable(pipelineName);
        return createTransientPipeline(pipelineName);
    }

    @Override
    public TransientPipeline createTransientStreamingPipeline(String pipelineName) {
        pipelineName = fixName(pipelineName);
        verifyStreamingPipelineRunnable(pipelineName);
        return createTransientPipeline(pipelineName);
    }


    private TransientPipeline createTransientPipeline(String pipelineName) {
        Pipeline pipeline = getPipeline(fixName(pipelineName));

        List<Task> tasks = pipeline.getTasks()
                .stream()
                .map(this::getTask)
                .collect(toList());

        List<Action> actions = tasks.stream()
                .flatMap(t -> t.getActions().stream())
                .map(this::getAction)
                .collect(toList());

        List<Algorithm> algorithms = actions.stream()
                .map(action -> getAlgorithm(action.getAlgorithm()))
                .collect(toList());

        return new TransientPipeline(pipeline, tasks, actions, algorithms);
    }


    @Override
    public Algorithm getAlgorithm(String name) {
        return _algorithms.get(fixName(name));
    }

    @Override
    public Collection<Algorithm> getAlgorithms() {
        return Collections.unmodifiableCollection(_algorithms.values());
    }

    @Override
    public Action getAction(String name) {
        return _actions.get(fixName(name));
    }

    @Override
    public Collection<Action> getActions() {
        return Collections.unmodifiableCollection(_actions.values());
    }

    @Override
    public Task getTask(String name) {
        return _tasks.get(fixName(name));
    }

    @Override
    public Collection<Task> getTasks() {
        return Collections.unmodifiableCollection(_tasks.values());
    }

    @Override
    public Pipeline getPipeline(String name) {
        return _pipelines.get(fixName(name));
    }

    @Override
    public Collection<Pipeline> getPipelines() {
        return Collections.unmodifiableCollection(_pipelines.values());
    }


    @Override
    public void save(Algorithm algorithm) {
        save(algorithm, _algorithms);
    }


    @Override
    public void save(Action action) {
        save(action, _actions);
    }


    @Override
    public void save(Task task) {
        save(task, _tasks);
    }


    @Override
    public void save(Pipeline pipeline) {
        save(pipeline, _pipelines);
    }



    @Override
    public void verifyBatchPipelineRunnable(String pipelineName) {
        _validator.verifyBatchPipelineRunnable(fixName(pipelineName), _pipelines, _tasks, _actions, _algorithms);
    }

    @Override
    public void verifyStreamingPipelineRunnable(String pipelineName) {
        _validator.verifyStreamingPipelineRunnable(fixName(pipelineName), _pipelines, _tasks, _actions, _algorithms);
    }

    @Override
    public boolean pipelineSupportsBatch(String pipelineName) {
        return pipelineSupportsProcessingType(fixName(pipelineName), Algorithm::getSupportsBatchProcessing);
    }

    @Override
    public boolean pipelineSupportsStreaming(String pipelineName) {
        return pipelineSupportsProcessingType(fixName(pipelineName), Algorithm::getSupportsStreamProcessing);

    }

    private boolean pipelineSupportsProcessingType(String pipelineName, Predicate<Algorithm> supportsPred) {
        return _pipelines.get(pipelineName)
                .getTasks()
                .stream()
                .map(this::getTask)
                .filter(Objects::nonNull)
                .flatMap(t -> t.getActions().stream())
                .map(this::getAction)
                .filter(Objects::nonNull)
                .map(action -> getAlgorithm(action.getAlgorithm()))
                .allMatch(supportsPred);
    }



    @Override
    public void deleteAlgorithm(String algorithmName) {
        delete(algorithmName, _algorithms);
    }

    @Override
    public void deleteAction(String actionName) {
        delete(actionName, _actions);
    }


    @Override
    public void deleteTask(String taskName) {
        delete(taskName, _tasks);
    }


    @Override
    public void deletePipeline(String pipelineName) {
        delete(pipelineName, _pipelines);
    }



    @Override
    public JsonPipeline createBatchJsonPipeline(String pipelineName) {
        return _jsonUtils.convert(createTransientBatchPipeline(fixName(pipelineName)));
    }

    @Override
    public JsonPipeline createStreamingJsonPipeline(String pipelineName) {
        return _jsonUtils.convert(createTransientStreamingPipeline(fixName(pipelineName)));
    }
}
