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

package org.mitre.mpf.wfm.service.component;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

public class InvalidCustomPipelinesException extends ComponentRegistrationException {

    private final Set<String> _dupActions;
    private final Set<String> _dupTasks;
    private final Set<String> _dupPipelines;
    private final Set<String> _invalidAlgorithmRefs;
    private final Set<String> _invalidActionRefs;
    private final Set<String> _invalidTaskRefs;
    private final Map<String, Set<String>> _actionsWithInvalidProps;
    private final Set<String> _pipelinesWithInvalidProcessingType;

    public InvalidCustomPipelinesException(
            Set<String> dupActions,
            Set<String> dupTasks,
            Set<String> dupPipelines,
            Set<String> invalidAlgorithmRefs,
            Set<String> invalidActionRefs,
            Set<String> invalidTaskRefs,
            Map<String, Set<String>> actionsWithInvalidProps,
            Set<String> pipelinesWithInvalidProcessingType) {
        super(createMessage(
                dupActions,
                dupTasks,
                dupPipelines,
                invalidAlgorithmRefs,
                invalidActionRefs,
                invalidTaskRefs,
                actionsWithInvalidProps,
                pipelinesWithInvalidProcessingType));

        _dupActions = ImmutableSet.copyOf(dupActions);
        _dupTasks = ImmutableSet.copyOf(dupTasks);
        _dupPipelines = ImmutableSet.copyOf(dupPipelines);
        _invalidAlgorithmRefs = ImmutableSet.copyOf(invalidAlgorithmRefs);
        _invalidActionRefs = ImmutableSet.copyOf(invalidActionRefs);
        _invalidTaskRefs = ImmutableSet.copyOf(invalidTaskRefs);
        _actionsWithInvalidProps = ImmutableMap.copyOf(actionsWithInvalidProps);
        _pipelinesWithInvalidProcessingType = ImmutableSet.copyOf(pipelinesWithInvalidProcessingType);
    }

    private static String createMessage(
            Set<String> dupActions,
            Set<String> dupTasks,
            Set<String> dupPipelines,
            Set<String> invalidAlgorithmRefs,
            Set<String> invalidActionRefs,
            Set<String> invalidTaskRefs,
            Map<String, Set<String>> actionsWithInvalidProps,
            Set<String> pipelinesWithInvalidProcessingType) {

        String duplicatesMsg = ImmutableMap.of("action", dupActions, "task", dupTasks, "pipeline", dupPipelines)
                .entrySet()
                .stream()
                .filter(e -> !e.getValue().isEmpty())
                .map(e -> createDuplicatesMessage(e.getKey(), e.getValue()))
                .collect(joining("\n"));

        String invalidRefsMsg = ImmutableMap.of("algorithms", invalidAlgorithmRefs, "actions", invalidActionRefs,
                                                "tasks", invalidTaskRefs)
                .entrySet()
                .stream()
                .filter(e -> !e.getValue().isEmpty())
                .map(e -> createInvalidRefMessage(e.getKey(), e.getValue()))
                .collect(joining("\n"));

        String invalidActionPropsMsg = createInvalidPropsMessage(actionsWithInvalidProps);

        String invalidProcessingTypeMessage = createInvalidProcessingTypeMessage(pipelinesWithInvalidProcessingType);

        return Stream.of(duplicatesMsg, invalidRefsMsg, invalidActionPropsMsg, invalidProcessingTypeMessage)
                .filter(s -> !s.isEmpty())
                .collect(joining("\n"));
    }

    private static String createDuplicatesMessage(String itemType, Set<String> duplicates) {
        String duplicatesString = String.join(", ", duplicates);
        return String.format("The following %s names are already in use: %s.", itemType, duplicatesString);
    }

    private static String createInvalidRefMessage(String itemType, Set<String> invalidRefs) {
        String refsString = String.join(", ", invalidRefs);
        return String.format("The following %s are referenced in the JSON descriptor but do not exist: %s.",
                itemType, refsString);
    }

    private static String createInvalidPropsMessage(Map<String, Set<String>> actionsWithInvalidProps) {
        return actionsWithInvalidProps
                .entrySet()
                .stream()
                .map(x -> String.format("The \"%s\" action contains the following invalid properties: %s.",
                        x.getKey(), String.join(", ", x.getValue())))
                .collect(joining("\n"));
    }

    private static String createInvalidProcessingTypeMessage(Set<String> pipelinesWithInvalidProcessingType) {
        if (pipelinesWithInvalidProcessingType.isEmpty()) {
            return "";
        }
        String pipelinesString = String.join(", ", pipelinesWithInvalidProcessingType);
        return String.format(
                "The algorithms utilized in the following pipelines either do not all support batch processing or do not all support stream processing: %s",
                pipelinesString);
    }

    public Set<String> getDupActions() {
        return _dupActions;
    }

    public Set<String> getDupTasks() {
        return _dupTasks;
    }

    public Set<String> getDupPipelines() {
        return _dupPipelines;
    }

    public Set<String> getInvalidAlgorithmRefs() {
        return _invalidAlgorithmRefs;
    }

    public Set<String> getInvalidActionRefs() {
        return _invalidActionRefs;
    }

    public Set<String> getInvalidTaskRefs() {
        return _invalidTaskRefs;
    }

    public Map<String, Set<String>> getActionsWithInvalidProps() {
        return _actionsWithInvalidProps;
    }

    public Set<String> getPipelinesWithInvalidProcessingType() {
        return _pipelinesWithInvalidProcessingType;
    }
}
