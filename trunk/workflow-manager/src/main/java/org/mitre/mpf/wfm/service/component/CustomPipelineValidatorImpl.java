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

package org.mitre.mpf.wfm.service.component;

import org.mitre.mpf.wfm.service.PipelineService;
import org.mitre.mpf.wfm.pipeline.xml.AlgorithmDefinition;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

@Service
public class CustomPipelineValidatorImpl implements CustomPipelineValidator {

    private final PipelineService pipelineService;

    @Inject
    CustomPipelineValidatorImpl(PipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @Override
    public void validate(JsonComponentDescriptor descriptor) throws InvalidCustomPipelinesException {

        Optional<InvalidCustomPipelinesException> exception = getIfError(
                getDupActions(descriptor),
                getDupTasks(descriptor),
                getDupPipelines(descriptor),
                getInvalidAlgorithmRefs(descriptor),
                getInvalidActionRefs(descriptor),
                getInvalidTaskRefs(descriptor),
                getActionsWithInvalidProperties(descriptor));

        if (exception.isPresent()) {
            throw exception.get();
        }
    }

    private static Optional<InvalidCustomPipelinesException> getIfError(
            Set<String> dupActions,
            Set<String> dupTasks,
            Set<String> dupPipelines,
            Set<String> invalidAlgorithmRefs,
            Set<String> invalidActionRefs,
            Set<String> invalidTaskRefs,
            Map<String, Set<String>> actionsWithInvalidProps) {

        return Stream.of(dupActions, dupTasks, dupPipelines, invalidAlgorithmRefs,
                            invalidActionRefs, invalidTaskRefs, actionsWithInvalidProps.entrySet())
                .filter(s -> !s.isEmpty())
                .findAny()
                .map(s -> new InvalidCustomPipelinesException(dupActions, dupTasks, dupPipelines,
                        invalidAlgorithmRefs, invalidActionRefs, invalidTaskRefs, actionsWithInvalidProps));
    }

    private Set<String> getDupActions(JsonComponentDescriptor descriptor) {
        if (descriptor.actions == null) {
            return Collections.emptySet();
        }
        return getDuplicates(a -> a.name, descriptor.actions, pipelineService.getActionNames());
    }

    private Set<String> getDupTasks(JsonComponentDescriptor descriptor) {
        if (descriptor.tasks == null) {
            return Collections.emptySet();
        }
        return getDuplicates(t -> t.name, descriptor.tasks, pipelineService.getTaskNames());
    }

    private Set<String> getDupPipelines(JsonComponentDescriptor descriptor) {
        if (descriptor.pipelines == null) {
            return Collections.emptySet();
        }
        return getDuplicates(p -> p.name, descriptor.pipelines, pipelineService.getPipelineNames());
    }

    private static <T> Set<String> getDuplicates(
            Function<T, String> toString,
            Collection<T> newItems,
            Collection<String> existingItems) {

        Set<String> existing = existingItems
                .stream()
                .map(String::toUpperCase)
                .collect(toSet());

        return newItems
                .stream()
                .map(toString)
                .map(String::toUpperCase)
                .filter(existing::contains)
                .collect(toSet());
    }

    private Set<String> getInvalidAlgorithmRefs(JsonComponentDescriptor descriptor) {
        if (descriptor.actions == null) {
            return Collections.emptySet();
        }

        Stream<String> algoRefs = descriptor
                .actions
                .stream()
                .map(a -> a.algorithm);

        Stream<String> descriptorAlgo = null;
        if (descriptor.algorithm == null) {
            descriptorAlgo = Stream.empty();
        } else {
            descriptorAlgo = Stream.of(descriptor.algorithm.name);
        }

        return getInvalidRefs(algoRefs, descriptorAlgo, pipelineService.getAlgorithmNames());
    }


    private Set<String> getInvalidActionRefs(JsonComponentDescriptor descriptor) {
        if (descriptor.tasks == null) {
            return Collections.emptySet();
        }
        Stream<String> actionRefs = descriptor
                .tasks
                .stream()
                .flatMap(t -> t.actions.stream());

        Stream<String> descriptorActions = Stream.empty();
        if (descriptor.actions != null) {
            descriptorActions = descriptor.actions
                    .stream()
                    .map(a -> a.name);
        }

        return getInvalidRefs(actionRefs, descriptorActions, pipelineService.getActionNames());
    }


    private Set<String> getInvalidTaskRefs(JsonComponentDescriptor descriptor) {
        if (descriptor.pipelines == null) {
            return Collections.emptySet();
        }

        Stream<String> taskRefs = descriptor
                .pipelines
                .stream()
                .flatMap(p -> p.tasks.stream());

        Stream<String> descriptorTasks = Stream.empty();
        if (descriptor.tasks != null) {
            descriptorTasks = descriptor.tasks
                    .stream()
                    .map(t -> t.name);
        }

        return getInvalidRefs(taskRefs, descriptorTasks, pipelineService.getTaskNames());
    }

    private static Set<String> getInvalidRefs(
            Stream<String> refs,
            Stream<String> descriptorItems,
            Collection<String> existingItems) {

        Set<String> definedItems = Stream.concat(descriptorItems, existingItems.stream())
                .map(String::toUpperCase)
                .collect(toSet());

        return refs
                .map(String::toUpperCase)
                .filter(r -> !definedItems.contains(r))
                .collect(toSet());
    }

    private Map<String, Set<String>> getActionsWithInvalidProperties(JsonComponentDescriptor descriptor) {
        if (descriptor.actions == null) {
            return Collections.emptyMap();
        }

        Map<String, Set<String>> algoPropertyNameMap = descriptor
                .actions
                .stream()
                .map(a -> a.algorithm)
                .distinct()
                .collect(toMap(Function.identity(), this::getPropNames));

        if (descriptor.algorithm != null) {
            algoPropertyNameMap.put(descriptor.algorithm.name.toUpperCase(), getAlgoPropNames(descriptor));
        }

        Map<String, Set<String>> actionInvalidPropsMap = descriptor
                .actions
                .stream()
                .collect(toMap(a -> a.name, a -> getExtraActionProperties(a, algoPropertyNameMap)));

        // Remove entries with no invalid properties.
        return actionInvalidPropsMap
                .entrySet()
                .stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Set<String> getPropNames(String algoName) {
        AlgorithmDefinition algorithm = pipelineService.getAlgorithm(algoName);
        if (algorithm == null) {
            return Collections.emptySet();
        }
        return algorithm.getProvidesCollection().getAlgorithmProperties()
                .stream()
                .map(pd -> pd.getName().toUpperCase())
                .collect(toSet());
    }

    private static Set<String> getAlgoPropNames(JsonComponentDescriptor descriptor) {
        return descriptor.algorithm.providesCollection.properties
                .stream()
                .map(ap -> ap.name.toUpperCase())
                .collect(toSet());
    }

    private static Set<String> getExtraActionProperties(JsonComponentDescriptor.Action action,
                                                        Map<String, Set<String>> algoPropMap) {
        Set<String> algoProps = algoPropMap.get(action.algorithm.toUpperCase());
        if (algoProps == null) {
            return Collections.emptySet();
        }
        return action.properties
                .stream()
                .map(ap -> ap.name.toUpperCase())
                .filter(actionPropName -> !algoProps.contains(actionPropName))
                .collect(toSet());
    }
}
