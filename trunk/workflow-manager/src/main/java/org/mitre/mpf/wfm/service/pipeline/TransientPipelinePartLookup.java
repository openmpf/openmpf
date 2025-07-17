/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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

import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.Algorithm;
import org.mitre.mpf.rest.api.pipelines.Pipeline;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.rest.api.pipelines.transients.TransientAction;
import org.mitre.mpf.rest.api.pipelines.transients.TransientPipelineDefinition;
import org.mitre.mpf.rest.api.pipelines.transients.TransientTask;
import org.mitre.mpf.rest.api.util.Utils;

import java.util.Map;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

public class TransientPipelinePartLookup implements PipelinePartLookup {

    private final Pipeline _pipeline;

    private final PipelinePartLookup _existingPipelineParts;

    private final Map<String, Task> _tasks;

    private final Map<String, Action> _actions;

    public TransientPipelinePartLookup(
            TransientPipelineDefinition transientPipeline,
            PipelinePartLookup existingPipelineParts,
            String pipelineName) {

        _pipeline = new Pipeline(pipelineName, pipelineName, transientPipeline.pipeline());

        _tasks = transientPipeline.tasks()
                .stream()
                .map(TransientPipelinePartLookup::convertTask)
                .collect(toMap(Task::name, Function.identity(), (t1, t2) -> t1));

        _actions = transientPipeline.actions()
                .stream()
                .map(TransientPipelinePartLookup::convertAction)
                .collect(toMap(Action::name, Function.identity(), (a1, a2) -> a1));

        _existingPipelineParts = existingPipelineParts;
    }


    public String getPipelineName() {
        return _pipeline.name();
    }

    @Override
    public Pipeline getPipeline(String name) {
        return _pipeline;
    }

    @Override
    public Task getTask(String name) {
        var normalizedName = Utils.trimAndUpper(name);
        var task = _tasks.get(normalizedName);
        return task != null
                ? task
                : _existingPipelineParts.getTask(normalizedName);
    }

    @Override
    public Action getAction(String name) {
        var normalizedName = Utils.trimAndUpper(name);
        var action = _actions.get(normalizedName);
        return action != null
                ? action
                : _existingPipelineParts.getAction(normalizedName);
    }

    @Override
    public Algorithm getAlgorithm(String name) {
        return _existingPipelineParts.getAlgorithm(name);
    }


    private static Task convertTask(TransientTask task) {
        return new Task(
                task.name(),
                "Job specified transient task: " + task.name(),
                task.actions());
    }

    private static Action convertAction(TransientAction action) {
        return new Action(
                action.name(),
                "Job specified transient action: " + action.name(),
                action.algorithm(),
                action.properties());
    }
}
