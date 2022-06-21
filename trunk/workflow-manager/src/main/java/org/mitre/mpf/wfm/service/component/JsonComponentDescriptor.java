/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotBlank;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.Algorithm;
import org.mitre.mpf.rest.api.pipelines.Pipeline;
import org.mitre.mpf.rest.api.pipelines.Task;

import javax.validation.Valid;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.Collection;
import java.util.Objects;


//@ScriptAssert(lang = "javascript", script = "_this.supportsBatchProcessing() || _this.supportsStreamProcessing()",
//        message = "must contain batchLibrary, streamLibrary, or both")
public class JsonComponentDescriptor {

    private final String _componentName;
    @NotBlank
    public String getComponentName() {
        return _componentName;
    }

    private final String _componentVersion;
    @NotNull
    public String getComponentVersion() {
        return _componentVersion;
    }

    private final String _middlewareVersion;
    @NotNull
    public String getMiddlewareVersion() {
        return _middlewareVersion;
    }

    private final String _setupFile;
    public String getSetupFile() {
        return _setupFile;
    }

    private final String _instructionsFile;
    public String getInstructionsFile() {
        return _instructionsFile;
    }

    private final ComponentLanguage _sourceLanguage;
    @NotNull(message = "must be java, c++, or python")
    public ComponentLanguage getSourceLanguage() {
        return _sourceLanguage;
    }

    private final String _batchLibrary;
    public String getBatchLibrary() {
        return _batchLibrary;
    }

    public boolean supportsBatchProcessing() {
        return StringUtils.isNotBlank(_batchLibrary);
    }


    private final String _streamLibrary;
    public String getStreamLibrary() {
        return _streamLibrary;
    }

    public boolean supportsStreamProcessing() {
        return StringUtils.isNotBlank(_streamLibrary);
    }

    @AssertTrue(message = "must contain batchLibrary, streamLibrary, or both")
    public boolean getSupportsBatchOrStreaming() {
        return supportsBatchProcessing() || supportsStreamProcessing();
    }

    private final ImmutableList<EnvironmentVariable> _environmentVariables;
    @NotNull @Valid
    public ImmutableList<EnvironmentVariable> getEnvironmentVariables() {
        return _environmentVariables;
    }

    private final Algorithm _algorithm;
    @NotNull @Valid
    public Algorithm getAlgorithm() {
        return _algorithm;
    }

    private final ImmutableList<Action> _actions;
    @Valid
    public ImmutableList<Action> getActions() {
        return _actions;
    }

    private final ImmutableList<Task> _tasks;
    @Valid
    public ImmutableList<Task> getTasks() {
        return _tasks;
    }

    private final ImmutableList<Pipeline> _pipelines;
    @Valid
    public ImmutableList<Pipeline> getPipelines() {
        return _pipelines;
    }


    public JsonComponentDescriptor(
            @JsonProperty("componentName") String componentName,
            @JsonProperty("componentVersion") String componentVersion,
            @JsonProperty("middlewareVersion") String middlewareVersion,
            @JsonProperty("setupFile") String setupFile,
            @JsonProperty("instructionsFile") String instructionsFile,
            @JsonProperty("sourceLanguage") ComponentLanguage sourceLanguage,
            @JsonProperty("batchLibrary") String batchLibrary,
            @JsonProperty("streamLibrary") String streamLibrary,
            @JsonProperty("environmentVariables") Collection<EnvironmentVariable> environmentVariables,
            @JsonProperty("algorithm") Algorithm algorithm,
            @JsonProperty("actions") Collection<Action> actions,
            @JsonProperty("tasks") Collection<Task> tasks,
            @JsonProperty("pipelines") Collection<Pipeline> pipelines) {
        _componentName = componentName;
        _componentVersion = componentVersion;
        _middlewareVersion = middlewareVersion;
        _setupFile = setupFile;
        _instructionsFile = instructionsFile;
        _sourceLanguage = sourceLanguage;
        _batchLibrary = batchLibrary;
        _streamLibrary = streamLibrary;

        _environmentVariables = environmentVariables == null
                ? ImmutableList.of()
                : ImmutableList.copyOf(environmentVariables);

        _algorithm = algorithm == null
                ? null
                : new Algorithm(
                    algorithm.getName(),
                    algorithm.getDescription(),
                    algorithm.getActionType(),
                    algorithm.getRequiresCollection(),
                    algorithm.getProvidesCollection(),
                    StringUtils.isNotBlank(batchLibrary),
                    StringUtils.isNotBlank(streamLibrary));

        _actions = actions == null
                ? ImmutableList.of()
                : ImmutableList.copyOf(actions);
        _tasks = tasks == null
                ? ImmutableList.of()
                : ImmutableList.copyOf(tasks);
        _pipelines = pipelines == null
                ? ImmutableList.of()
                : ImmutableList.copyOf(pipelines);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof JsonComponentDescriptor)) {
            return false;
        }
        JsonComponentDescriptor other = (JsonComponentDescriptor) obj;
        return Objects.equals(_componentName, other._componentName)
                && Objects.equals(_componentVersion, other._componentVersion)
                && Objects.equals(_middlewareVersion, other._middlewareVersion)
                && Objects.equals(_setupFile, other._setupFile)
                && Objects.equals(_instructionsFile, other._instructionsFile)
                && _sourceLanguage == other._sourceLanguage
                && Objects.equals(_batchLibrary, other._batchLibrary)
                && Objects.equals(_streamLibrary, other._streamLibrary)
                && Objects.equals(_environmentVariables, other._environmentVariables)
                && Objects.equals(_algorithm, other._algorithm)
                && Objects.equals(_actions, other._actions)
                && Objects.equals(_tasks, other._tasks)
                && Objects.equals(_pipelines, other._pipelines);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_componentName,
                            _componentVersion,
                            _middlewareVersion,
                            _setupFile,
                            _instructionsFile,
                            _sourceLanguage,
                            _batchLibrary,
                            _streamLibrary,
                            _environmentVariables,
                            _algorithm,
                            _actions,
                            _tasks,
                            _pipelines);
    }

    public static class EnvironmentVariable {
        private final String _name;
        @NotBlank
        public String getName() {
            return _name;
        }

        private final String _value;
        @NotNull
        public String getValue() {
            return _value;
        }

        private final String _sep;
        @Pattern(regexp = ":", message = "must be \":\" or null")
        public String getSep() {
            return _sep;
        }

        public EnvironmentVariable(
                @JsonProperty("name") String name,
                @JsonProperty("value") String value,
                @JsonProperty("sep") String sep) {
            _name = name;
            _value = value;
            _sep = sep;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof EnvironmentVariable)) {
                return false;
            }
            EnvironmentVariable other = (EnvironmentVariable) obj;
            return Objects.equals(_name, other._name)
                    && Objects.equals(_value, other._value)
                    && Objects.equals(_sep, other._sep);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_name, _value, _sep);
        }
    }
}
