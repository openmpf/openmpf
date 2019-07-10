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

import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;
import org.hibernate.validator.constraints.ScriptAssert;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.pipeline.xml.ValueType;
import org.mitre.mpf.wfm.util.AllNotBlank;
import org.mitre.mpf.wfm.util.ValidAlgoPropValue;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * This class is only used to avoid manually extracting information from the JSON descriptor file.
 * <p>
 * This intentionally does not use getters and setters. Adding getters and setters to this class
 * would only make it harder to figure out the structure of the descriptor.
 */
@SuppressWarnings("PublicField")
@ScriptAssert(lang = "javascript", script = "_this.supportsBatchProcessing() || _this.supportsStreamProcessing()",
        message = "must contain batchLibrary, streamLibrary, or both")
public class JsonComponentDescriptor {

    @NotBlank
    public String componentName;

    @NotNull
    public String componentVersion;

    @NotNull
    public String middlewareVersion;

    public String setupFile;

    public String instructionsFile;

    @NotNull(message = "must be java, c++, or python")
    public ComponentLanguage sourceLanguage;

    public String batchLibrary;

    public boolean supportsBatchProcessing() {
        return batchLibrary != null;
    }

    public String streamLibrary;

    public boolean supportsStreamProcessing() {
        return streamLibrary != null;
    }

    @NotNull
    @Valid
    public List<EnvironmentVariable> environmentVariables;

    @NotNull
    @Valid
    public List<String> mediaTypesSupported;

    @NotNull
    @Valid
    public Algorithm algorithm;

    @Valid
    public List<Action> actions;

    @Valid
    public List<Task> tasks;

    @Valid
    public List<Pipeline> pipelines;


    public boolean deepEquals(JsonComponentDescriptor other) {
        return other != null
                && Objects.equals(componentName, other.componentName)
                && Objects.equals(componentVersion, other.componentVersion)
                && Objects.equals(middlewareVersion, other.middlewareVersion)
                && Objects.equals(setupFile, other.setupFile)
                && Objects.equals(instructionsFile, other.instructionsFile)
                && sourceLanguage == other.sourceLanguage
                && Objects.equals(batchLibrary, other.batchLibrary)
                && Objects.equals(streamLibrary, other.streamLibrary)
                && collectionDeepEquals(mediaTypesSupported, other.mediaTypesSupported)
                && collectionDeepEquals(environmentVariables, other.environmentVariables,
                                        EnvironmentVariable::deepEquals)
                && algorithm.deepEquals(other.algorithm)
                && collectionDeepEquals(actions, other.actions, Action::deepEquals)
                && collectionDeepEquals(tasks, other.tasks, Task::deepEquals)
                && collectionDeepEquals(pipelines, pipelines, Pipeline::deepEquals);
    }


    public static class EnvironmentVariable {
        @NotBlank
        public String name;

        @NotNull
        public String value;

        @Pattern(regexp = ":", message = "must be \":\" or null")
        public String sep;

        private boolean deepEquals(EnvironmentVariable other) {
            return other != null
                    && Objects.equals(name, other.name)
                    && Objects.equals(value, other.value)
                    && Objects.equals(sep, other.sep);
        }
    }

    public static class Algorithm {
        @NotBlank
        public String name;

        @NotBlank
        public String description;

        @NotNull
        public ActionType actionType;

        @NotNull
        @Valid
        public AlgoRequires requiresCollection;

        @NotNull
        @Valid
        public AlgoProvides providesCollection;

        public boolean deepEquals(Algorithm other) {
            return other != null
                    && Objects.equals(name, other.name)
                    && Objects.equals(description, other.description)
                    && actionType == other.actionType
                    && requiresCollection.deepEquals(other.requiresCollection)
                    && providesCollection.deepEquals(other.providesCollection);
        }
    }

    public static class AlgoRequires {
        @NotNull
        @Valid
        public List<@AllNotBlank String> states;

        public boolean deepEquals(AlgoRequires other) {
            return other != null
                    && collectionDeepEquals(states, other.states);
        }
    }


    public static class AlgoProvides {
        @NotNull
        @Valid
        public List<@AllNotBlank String> states;

        @NotNull
        @Valid
        public List<AlgoProvidesProp> properties;

        private boolean deepEquals(AlgoProvides other) {
            return other != null
                    && collectionDeepEquals(states, other.states)
                    && collectionDeepEquals(properties, other.properties, AlgoProvidesProp::deepEquals);
        }
    }

    @ValidAlgoPropValue
    public static class AlgoProvidesProp {
        @NotBlank
        public String description;

        @NotBlank
        public String name;

        @NotNull
        public ValueType type;

        // option A
        public String defaultValue;

        // option B
        public String propertiesKey;


        private boolean deepEquals(AlgoProvidesProp other) {
            return other != null
                    && Objects.equals(description, other.description)
                    && Objects.equals(name, other.name)
                    && type == other.type
                    && Objects.equals(defaultValue, other.defaultValue)
                    && Objects.equals(propertiesKey, other.propertiesKey);

        }
    }

    public static class Pipeline {
        @NotBlank
        public String name;

        @NotBlank
        public String description;

        @NotEmpty
        @Valid
        public List<@AllNotBlank String> tasks;

        private boolean deepEquals(Pipeline other) {
            return other != null
                    && Objects.equals(name, other.name)
                    && Objects.equals(description, other.description)
                    && collectionDeepEquals(tasks, other.tasks);
        }
    }


    public static class Task {
        @NotBlank
        public String name;

        @NotBlank
        public String description;

        @NotEmpty
        @Valid
        public List<@AllNotBlank String> actions;

        private boolean deepEquals(Task other) {
            return other != null
                    && Objects.equals(name, other.name)
                    && Objects.equals(description, other.description)
                    && collectionDeepEquals(actions, other.actions);
        }
    }


    public static class Action {
        @NotBlank
        public String name;

        @NotBlank
        public String description;

        @NotBlank
        public String algorithm;

        @NotNull
        @Valid
        public List<ActionProperty> properties;

        private boolean deepEquals(Action other) {
            return other != null
                    && Objects.equals(name, other.name)
                    && Objects.equals(description, other.description)
                    && Objects.equals(algorithm, other.algorithm)
                    && collectionDeepEquals(properties, other.properties, ActionProperty::deepEquals);
        }
    }

    public static class ActionProperty {
        @NotBlank
        public String name;

        @NotNull
        public String value;


        private boolean deepEquals(ActionProperty other) {
            return other != null
                    && Objects.equals(name, other.name)
                    && Objects.equals(value, other.value);
        }
    }


    private static <T> boolean collectionDeepEquals(Collection<T> collection1, Collection<T> collection2) {
        return collectionDeepEquals(collection1, collection2, Objects::equals);
    }


    private static <T> boolean collectionDeepEquals(Collection<T> collection1, Collection<T> collection2,
                                                    BiPredicate <T, T> equalsPred) {
        if (collection1 == null || collection2 == null) {
            return collection1 == null && collection2 == null;
        }
        if (collection1.size() != collection2.size()) {
            return false;
        }

        Iterator<T> iter1 = collection1.iterator();
        Iterator<T> iter2 = collection2.iterator();
        while (iter1.hasNext()) {
            T el1 = iter1.next();
            T el2 = iter2.next();
            if (el1 == null || el2 == null) {
                if (el1 == null && el2 == null) {
                    continue;
                }
                return false;
            }

            if (!equalsPred.test(el1, el2)) {
                return false;
            }
        }
        return true;
    }
}
