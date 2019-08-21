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

import org.mitre.mpf.rest.api.pipelines.*;
import org.mitre.mpf.wfm.util.WorkflowPropertyService;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import javax.validation.Validator;
import java.util.*;
import java.util.function.Predicate;

import static java.util.stream.Collectors.*;

@Service
public class PipelineValidator {

    private final Validator _validator;

    private final WorkflowPropertyService _workflowPropertyService;

    @Inject
    public PipelineValidator(Validator validator, WorkflowPropertyService workflowPropertyService) {
        _validator = validator;
        _workflowPropertyService = workflowPropertyService;
    }


    public <T extends PipelineComponent> void validateOnAdd(T newPipelineComponent, Map<String, T> existingItems) {
        var violations = _validator.<PipelineComponent>validate(newPipelineComponent);
        if (!violations.isEmpty()) {
            throw new PipelineValidationException(newPipelineComponent, violations);
        }

        T existing = existingItems.get(newPipelineComponent.getName());
        if (existing != null && !existing.equals(newPipelineComponent)) {
            String type = newPipelineComponent.getClass().getSimpleName();
            throw new InvalidPipelineException(String.format(
                    "Failed to add %s with name \"%s\" because another %s with the same name already exists.",
                    type, newPipelineComponent.getName(), type));
        }

    }


    public void verifyBatchPipelineRunnable(
            String pipelineName,
            Map<String, Pipeline> pipelines,
            Map<String, Task> tasks,
            Map<String, Action> actions,
            Map<String, Algorithm> algorithms) {
        verifyPipelineRunnable(pipelineName,pipelines, tasks, actions, algorithms,
                               Algorithm::getSupportsBatchProcessing, "batch");
    }


    public void verifyStreamingPipelineRunnable(
            String pipelineName,
            Map<String, Pipeline> pipelines,
            Map<String, Task> tasks,
            Map<String, Action> actions,
            Map<String, Algorithm> algorithms) {
        verifyPipelineRunnable(pipelineName, pipelines, tasks, actions, algorithms,
                               Algorithm::getSupportsStreamProcessing, "stream");
    }

    private void verifyPipelineRunnable(
            String pipelineName,
            Map<String, Pipeline> pipelines,
            Map<String, Task> tasks,
            Map<String, Action> actions,
            Map<String, Algorithm> algorithms,
            Predicate<Algorithm> supportsPred,
            String processingType) {

        var pipeline = pipelines.get(pipelineName);
        if (pipeline == null) {
            throw new InvalidPipelineException("No pipeline named: " + pipelineName);
        }
        verifyAllPartsPresent(pipeline, tasks, actions, algorithms);

        verifyAlgorithmsSupportProcessingType(pipeline, tasks, actions, algorithms, supportsPred, processingType);

        var currentPipelineTasks = pipeline.getTasks()
                .stream()
                .map(tasks::get)
                .collect(toList());
        validateStates(pipeline, currentPipelineTasks, actions, algorithms);

        var currentPipelineActions = currentPipelineTasks
                .stream()
                .flatMap(t -> t.getActions().stream())
                .map(actions::get)
                .collect(toList());
        validateActionPropertyTypes(currentPipelineActions, algorithms);

        validateActionTypes(currentPipelineTasks, actions, algorithms);

        verifyNothingFollowMultiActionTask(pipeline, currentPipelineTasks);

        validateMarkupPipeline(pipeline, currentPipelineTasks, actions, algorithms);
    }


    private static void verifyAllPartsPresent(
            Pipeline pipeline,
            Map<String, Task> tasks,
            Map<String, Action> actions,
            Map<String, Algorithm> algorithms) {
        var missingTasks = new HashSet<String>();
        var missingActions = new HashSet<String>();
        var missingAlgorithms = new HashSet<String>();

        for (String taskName : pipeline.getTasks()) {
            var task = tasks.get(taskName);
            if (task == null) {
                missingTasks.add(taskName);
                continue;
            }

            for (String actionName : task.getActions()) {
                Action action = actions.get(actionName);
                if (action == null) {
                    missingActions.add(actionName);
                    continue;
                }

                var algorithm = algorithms.get(action.getAlgorithm());
                if (algorithm == null) {
                    missingAlgorithms.add(action.getAlgorithm());
                }
            }
        }

        if (missingTasks.isEmpty() && missingActions.isEmpty() && missingAlgorithms.isEmpty()) {
            return;
        }

        var errorMsgBuilder = new StringBuilder("Cannot run pipeline ")
                .append(pipeline.getName())
                .append(" due to the following issues: ");

        if (!missingTasks.isEmpty()) {
            errorMsgBuilder.append("The following tasks are missing: ")
                    .append(String.join(", ", missingTasks))
                    .append(". ");
        }

        if (!missingActions.isEmpty()) {
            errorMsgBuilder.append("The following actions are missing: ")
                    .append(String.join(", ", missingActions))
                    .append(". ");
        }

        if (!missingAlgorithms.isEmpty()) {
            errorMsgBuilder.append("The following algorithms are missing: ")
                    .append(String.join(", ", missingAlgorithms))
                    .append(". ");
        }

        throw new InvalidPipelineException(errorMsgBuilder.toString());
    }

    private static void verifyAlgorithmsSupportProcessingType(
            Pipeline pipeline,
            Map<String, Task> tasks,
            Map<String, Action> actions,
            Map<String, Algorithm> algorithms,
            Predicate<Algorithm> supportsPred,
            String processingType) {
        String invalidSupportOfBatchOrStreamingAlgorithms = pipeline.getTasks()
                .stream()
                .flatMap(taskName -> tasks.get(taskName).getActions().stream())
                .map(actionName -> actions.get(actionName).getAlgorithm())
                .filter(algoName -> !supportsPred.test(algorithms.get(algoName)))
                .collect(joining(", "));

        if (!invalidSupportOfBatchOrStreamingAlgorithms.isEmpty()) {
            throw new InvalidPipelineException(String.format(
                    "Expected entire \"%s\" pipeline to support %s processing, but the following algorithms do not: %s",
                    pipeline.getName(), processingType, invalidSupportOfBatchOrStreamingAlgorithms));
        }
    }

    private static void validateStates(
            Pipeline pipeline,
            Collection<Task> tasks,
            Map<String, Action> actions,
            Map<String, Algorithm> algorithms) {

        if (tasks.size() == 1) {
            return;
        }

        var tasksIter1 = tasks.iterator();
        var tasksIter2 = tasks.iterator();
        tasksIter2.next();
        while (tasksIter2.hasNext()) {
            var task1 = tasksIter1.next();
            Set<String> providedStates = task1.getActions()
                    .stream()
                    .map(actions::get)
                    .map(action -> algorithms.get(action.getAlgorithm()))
                    .flatMap(algo -> algo.getProvidesCollection().getStates().stream())
                    .collect(toSet());

            var task2 = tasksIter2.next();
            Set<String> requiredStates = task2.getActions()
                    .stream()
                    .map(actions::get)
                    .map(action -> algorithms.get(action.getAlgorithm()))
                    .flatMap(algo -> algo.getRequiresCollection().getStates().stream())
                    .collect(toSet());

            if (!providedStates.containsAll(requiredStates)) {
                throw new InvalidPipelineException(String.format(
                        "%s: The states for \"%s\" are not satisfied. Provided: %s. Required: %s.",
                        pipeline.getName(), task2.getName(), providedStates, requiredStates));
            }
        }
    }


    private void validateActionPropertyTypes(
            Iterable<Action> actions,
            Map<String, Algorithm> algorithms) {

        for (var action : actions) {
            var algorithm = algorithms.get(action.getAlgorithm());

            for (var actionProperty : action.getProperties()) {
                var algoProperty = algorithm.getProperty(actionProperty.getName());
                if (algoProperty != null) {
                    validateValueType(action, actionProperty, algoProperty.getType());
                    continue;
                }

                var workflowProperty = _workflowPropertyService.getProperty(actionProperty.getName());
                if (workflowProperty != null) {
                    validateValueType(action, actionProperty, workflowProperty.getType());
                    continue;
                }

                throw new InvalidPipelineException(String.format(
                        "The \"%s\" property from the \"%s\" action does not exist in \"%s\" algorithm and is not the name of workflow property.",
                        actionProperty.getName(), action.getName(), algorithm.getName()));
            }
        }
    }


    private static void validateValueType(Action action, Action.Property property, ValueType type) {
        try {
            String value = property.getValue();
            switch (type) {
                case BOOLEAN:
                    Boolean.parseBoolean(value);
                    break;
                case DOUBLE:
                    Double.parseDouble(value);
                    break;
                case FLOAT:
                    Float.parseFloat(value);
                    break;
                case INT:
                    Integer.parseInt(value);
                    break;
                case LONG:
                    Long.parseLong(value);
                    break;
                case STRING:
                    break;
            }

        }
        catch (NumberFormatException ex) {
            throw new InvalidPipelineException(String.format(
                "The \"%s\" property from the \"%s\" action has a value of \"%s\", which is not a valid \"%s\".",
                property.getName(), action.getName(), property.getValue(), type), ex);
        }
    }


    private static void validateActionTypes(
            Iterable<Task> currentPipelineTasks,
            Map<String, Action> actions,
            Map<String, Algorithm> algorithms) {

        for (var task : currentPipelineTasks) {
            Set<ActionType> actionTypes = task.getActions()
                    .stream()
                    .map(actions::get)
                    .map(action -> algorithms.get(action.getAlgorithm()))
                    .map(Algorithm::getActionType)
                    .collect(toSet());

            if (actionTypes.size() > 1) {
                String actionTypeNames = actionTypes
                        .stream()
                        .map(Enum::toString)
                        .collect(joining(", "));

                throw new InvalidPipelineException(String.format(
                    "%s: tasks cannot contain actions which have different ActionTypes, " +
                            "but it had the following action types: %s.",
                    task.getName(), actionTypeNames));
            }
        }
    }


    private static void verifyNothingFollowMultiActionTask(Pipeline pipeline, Iterable<Task> tasks) {
        var taskIter = tasks.iterator();
        while (taskIter.hasNext()) {
            var task = taskIter.next();
            if (task.getActions().size() > 1 && taskIter.hasNext()) {
                throw new InvalidPipelineException(String.format(
                        "%s: No tasks may follow the multi-detection task of %s.",
                        pipeline.getName(), task.getName()));
            }
        }
    }


    private static void validateMarkupPipeline(
            Pipeline pipeline,
            List<Task> currentPipelineTasks,
            Map<String, Action> actions,
            Map<String, Algorithm> algorithms) {

        for (int i = 0; i < currentPipelineTasks.size(); i++) {
            var task = currentPipelineTasks.get(i);
            boolean taskHasMarkup = task.getActions()
                    .stream()
                    .map(actions::get)
                    .map(action -> algorithms.get(action.getAlgorithm()))
                    .map(Algorithm::getActionType)
                    .anyMatch(ActionType.MARKUP::equals);
            if (!taskHasMarkup) {
                continue;
            }

            if (i != currentPipelineTasks.size() - 1) {
                throw new InvalidPipelineException(String.format(
                        "%s: No tasks may follow a markup task of %s.", pipeline.getName(), task.getName()));
            }

            if (i == 0) {
                throw new InvalidPipelineException(String.format(
                        "%s: A markup task may not be the first task in a pipeline.", pipeline.getName()));
            }

            if (task.getActions().size() != 1) {
                throw new InvalidPipelineException(String.format(
                        "%s: A markup task may only contain one action.", task.getName()));
            }
        }
    }
}
