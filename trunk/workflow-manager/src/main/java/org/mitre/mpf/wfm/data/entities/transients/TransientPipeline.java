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


package org.mitre.mpf.wfm.data.entities.transients;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.Algorithm;
import org.mitre.mpf.rest.api.pipelines.Pipeline;
import org.mitre.mpf.rest.api.pipelines.Task;

public class TransientPipeline {

    private final Pipeline _pipeline;
    public Pipeline getPipeline() {
        return _pipeline;
    }

    private final ImmutableMap<String, Task> _tasks;
    public ImmutableMap<String, Task> getTasks() {
        return _tasks;
    }

    private final ImmutableMap<String, Action> _actions;
    public ImmutableMap<String, Action> getActions() {
        return _actions;
    }

    private final ImmutableMap<String, Algorithm> _algorithms;
    public ImmutableMap<String, Algorithm> getAlgorithms() {
        return _algorithms;
    }


    public String getName() {
        return _pipeline.getName();
    }

    public int getTaskCount() {
        return _pipeline.getTasks().size();
    }

    public Task getTask(String name) {
        return _tasks.get(name);
    }

    public Task getTask(int taskIndex) {
        String taskName = _pipeline.getTasks().get(taskIndex);
        return _tasks.get(taskName);
    }


    public Action getAction(String name) {
        return _actions.get(name);
    }

    public Action getAction(int taskIndex, int actionIndex) {
        Task task = getTask(taskIndex);
        String actionName = task.getActions().get(actionIndex);
        return _actions.get(actionName);
    }

    public Algorithm getAlgorithm(String name) {
        return _algorithms.get(name);
    }

    public Algorithm getAlgorithm(int taskIndex, int actionIndex) {
        Action action = getAction(taskIndex, actionIndex);
        return _algorithms.get(action.getAlgorithm());
    }


    public TransientPipeline(
            Pipeline pipeline,
            Iterable<Task> tasks,
            Iterable<Action> actions,
            Iterable<Algorithm> algorithms) {
        _pipeline = pipeline;
        _tasks = Maps.uniqueIndex(tasks, Task::getName);
        _actions = Maps.uniqueIndex(actions, Action::getName);
        _algorithms = Maps.uniqueIndex(algorithms, Algorithm::getName);

        for (String taskName : pipeline.getTasks()) {
            Task task = _tasks.get(taskName);
            if (task == null) {
                throw new IllegalArgumentException(
                        "Expected the \"tasks\" parameter to contain a task named \"" + taskName + "\".");
            }

            for (String actionName : task.getActions()) {
                Action action = _actions.get(actionName);
                if (action == null) {
                    throw new IllegalArgumentException(
                            "Expected the \"actions\" parameter to contain an action named \"" + actionName + "\".");
                }

                String algoName = action.getAlgorithm();
                Algorithm algorithm = _algorithms.get(algoName);
                if (algorithm == null) {
                    throw new IllegalArgumentException(
                            "Expected the \"algorithms\" parameter to contain an algorithm named \""
                                    + algoName + "\".");
                }
            }
        }
    }
}
