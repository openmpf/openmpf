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
import org.mitre.mpf.wfm.data.entities.transients.TransientPipeline;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.springframework.core.io.WritableResource;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toList;


@Service
public class PipelineServiceImpl implements PipelineService {

    private final PropertiesUtil _propertiesUtil;

    private final ObjectMapper _objectMapper;

    private final PipelineValidator _validator;

    //TODO: remove
    private final JsonUtils _jsonUtils;


    private final Map<String, Algorithm> _algorithms = new HashMap<>();
    private final Map<String, Action> _actions = new HashMap<>();
    private final Map<String, Task> _tasks = new HashMap<>();
    private final Map<String, Pipeline> _pipelines = new HashMap<>();

    @Inject
    public PipelineServiceImpl(
            PropertiesUtil propertiesUtil,
            ObjectMapper objectMapper,
            PipelineValidator validator,
            JsonUtils jsonUtils) throws IOException {
        _propertiesUtil = propertiesUtil;
        _objectMapper = objectMapper;
        _validator = validator;
        _jsonUtils = jsonUtils;


        List<Algorithm> algorithmList;
        try (InputStream inputStream = _propertiesUtil.getAlgorithmDefinitions().getInputStream()) {
            algorithmList = _objectMapper.readValue(inputStream, new TypeReference<List<Algorithm>>() {});
        }
        algorithmList.forEach(a -> add(a, _algorithms));


        List<Action> actionList;
        try (InputStream inputStream = _propertiesUtil.getActionDefinitions().getInputStream()) {
            actionList = _objectMapper.readValue(inputStream, new TypeReference<List<Action>>() {});
        }
        actionList.forEach(a -> add(a, _actions));

        List<Task> taskList;
        try (InputStream inputStream = _propertiesUtil.getTaskDefinitions().getInputStream()) {
            taskList = _objectMapper.readValue(inputStream, new TypeReference<List<Task>>() {});
        }
        taskList.forEach(t -> add(t, _tasks));

        List<Pipeline> pipelineList;
        try (InputStream inputStream = _propertiesUtil.getPipelineDefinitions().getInputStream()) {
            pipelineList = _objectMapper.readValue(inputStream, new TypeReference<List<Pipeline>>() {});
        }
        pipelineList.forEach(p -> add(p, _pipelines));
    }


    @Override
    public TransientPipeline createTransientBatchPipeline(String pipelineName) {
        verifyBatchPipelineRunnable(pipelineName);
        return createTransientPipeline(pipelineName);
    }

    @Override
    public TransientPipeline createTransientStreamingPipeline(String pipelineName) {
        verifyStreamingPipelineRunnable(pipelineName);
        return createTransientPipeline(pipelineName);
    }


    private TransientPipeline createTransientPipeline(String pipelineName) {
        Pipeline pipeline = getPipeline(pipelineName);

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
        return _algorithms.get(name.toUpperCase());
    }

    @Override
    public Collection<Algorithm> getAlgorithms() {
        return Collections.unmodifiableCollection(_algorithms.values());
    }

    @Override
    public Action getAction(String name) {
        return _actions.get(name.toUpperCase());
    }

    @Override
    public Collection<Action> getActions() {
        return Collections.unmodifiableCollection(_actions.values());
    }

    @Override
    public Task getTask(String name) {
        return _tasks.get(name.toUpperCase());
    }

    @Override
    public Collection<Task> getTasks() {
        return Collections.unmodifiableCollection(_tasks.values());
    }

    @Override
    public Pipeline getPipeline(String name) {
        return _pipelines.get(name.toUpperCase());
    }

    @Override
    public Collection<Pipeline> getPipelines() {
        return Collections.unmodifiableCollection(_pipelines.values());
    }


    @Override
    public void save(Algorithm algorithm) {
        add(algorithm, _algorithms);
        writeToDisk(_algorithms, _propertiesUtil.getAlgorithmDefinitions());
    }


    @Override
    public void save(Action action) {
        add(action, _actions);
        writeToDisk(_actions, _propertiesUtil.getActionDefinitions());
    }


    @Override
    public void save(Task task) {
        add(task, _tasks);
        writeToDisk(_tasks, _propertiesUtil.getTaskDefinitions());
    }


    @Override
    public void save(Pipeline pipeline) {
        add(pipeline, _pipelines);
        writeToDisk(_pipelines, _propertiesUtil.getPipelineDefinitions());
    }


    @Override
    public void verifyBatchPipelineRunnable(String pipelineName) {
        _validator.verifyBatchPipelineRunnable(pipelineName, _pipelines, _tasks, _actions, _algorithms);
    }

    @Override
    public void verifyStreamingPipelineRunnable(String pipelineName) {
        _validator.verifyStreamingPipelineRunnable(pipelineName, _pipelines, _tasks, _actions, _algorithms);
    }

    @Override
    public boolean pipelineSupportsBatch(String pipelineName) {
        return pipelineSupportsProcessingType(pipelineName, Algorithm::getSupportsBatchProcessing);
    }

    @Override
    public boolean pipelineSupportsStreaming(String pipelineName) {
        return pipelineSupportsProcessingType(pipelineName, Algorithm::getSupportsStreamProcessing);

    }

    private boolean pipelineSupportsProcessingType(String pipelineName, Predicate<Algorithm> supportsPred) {
        //TODO: how to handle incomplete pipelines?
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
        if (_algorithms.remove(algorithmName.toUpperCase()) != null) {
            writeToDisk(_algorithms, _propertiesUtil.getAlgorithmDefinitions());
        }
    }


    @Override
    public void deleteAction(String actionName) {
        if (_actions.remove(actionName.toUpperCase()) != null) {
            writeToDisk(_actions, _propertiesUtil.getActionDefinitions());
        }
    }


    @Override
    public void deleteTask(String taskName) {
        if (_tasks.remove(taskName.toUpperCase()) != null)  {
            writeToDisk(_tasks, _propertiesUtil.getTaskDefinitions());
        }

//        Task taskToDelete = getTask(taskName);
//        if (taskToDelete == null) {
//            return;
//        }
//
//        Set<String> actionsWithOtherReferences = getTasks().stream()
//                .filter(t -> !t.equals(taskToDelete))
//                .flatMap(t -> t.getActions().stream())
//                .collect(toSet());
//
//        Set<String> actionsToDelete = new HashSet<>(taskToDelete.getActions());
//        actionsToDelete.removeAll(actionsWithOtherReferences);
//        if (!actionsToDelete.isEmpty()) {
//            for (String actionName : actionsToDelete) {
//                _actions.remove(actionName);
//            }
//            writeToDisk(_actions, _propertiesUtil.getActionDefinitions());
//        }
//
//        _tasks.remove(taskName);
//        writeToDisk(_tasks, _propertiesUtil.getTaskDefinitions());
    }


    @Override
    public void deletePipeline(String pipelineName) {
        if (_pipelines.remove(pipelineName.toUpperCase()) != null) {
            writeToDisk(_pipelines, _propertiesUtil.getPipelineDefinitions());
        }
//        Pipeline pipelineToDelete = getPipeline(pipelineName);
//        if (pipelineToDelete == null) {
//            return;
//        }
//
//        Set<String> tasksWithOtherReferences = getPipelines()
//                .stream()
//                .filter(p -> !p.equals(pipelineToDelete))
//                .flatMap(p -> p.getTasks().stream())
//                .collect(toSet());
//
//
//        Set<String> tasksToRemove = new HashSet<>(pipelineToDelete.getTasks());
//        tasksToRemove.removeAll(tasksWithOtherReferences);
//
//
//        Set<String> actionsWithOtherReferences = getTasks().stream()
//                .filter(t -> !tasksToRemove.contains(t.getName()))
//                .flatMap(t -> t.getActions().stream())
//                .collect(toSet());
//
//        Set<String> actionsToRemove = tasksToRemove
//                .stream()
//                .map(this::getTask)
//                .flatMap(t -> t.getActions().stream())
//                .filter(aName -> !actionsWithOtherReferences.contains(aName))
//                .collect(toSet());
//
//        if (!actionsToRemove.isEmpty()) {
//            for (String actionName : actionsToRemove) {
//                _actions.remove(actionName);
//            }
//            writeToDisk(_actions, _propertiesUtil.getActionDefinitions());
//        }
//
//        if (!tasksToRemove.isEmpty()) {
//            for (String taskName : tasksToRemove) {
//                _tasks.remove(taskName);
//            }
//            writeToDisk(_tasks, _propertiesUtil.getTaskDefinitions());
//        }
//
//        _pipelines.remove(pipelineName);
//        writeToDisk(_pipelines, _propertiesUtil.getPipelineDefinitions());
    }


    private void writeToDisk(Map<String, ?> items, WritableResource destination) {
        try (OutputStream outputStream = destination.getOutputStream()) {
            _objectMapper.writeValue(outputStream, items.values());
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private <T extends PipelineComponent> void add(T newItem, Map<String, T> items) {
        _validator.validateOnAdd(newItem, items);
        items.put(newItem.getName(), newItem);
    }


    //TODO: remove
    @Override
    public JsonPipeline createBatchJsonPipeline(String pipelineName) {
        return _jsonUtils.convert(createTransientBatchPipeline(pipelineName));
    }

    //TODO: remove
    @Override
    public JsonPipeline createStreamingJsonPipeline(String pipelineName) {
        return _jsonUtils.convert(createTransientStreamingPipeline(pipelineName));
    }
}
