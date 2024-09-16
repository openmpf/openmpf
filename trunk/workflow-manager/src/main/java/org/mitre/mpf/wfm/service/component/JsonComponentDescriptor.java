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

package org.mitre.mpf.wfm.service.component;

import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotBlank;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.Algorithm;
import org.mitre.mpf.rest.api.pipelines.Pipeline;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.rest.api.util.MethodReturnsTrue;
import org.mitre.mpf.rest.api.util.ValidName;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableList;


@MethodReturnsTrue(
        method = "supportsBatchOrStreaming",
        message = "must contain batchLibrary, streamLibrary, or both")
public record JsonComponentDescriptor(
        @ValidName String componentName,
        @NotNull String componentVersion,
        @NotNull String middlewareVersion,
        String setupFile,
        String instructionsFile,
        @NotNull(message = "must be java, c++, or python") ComponentLanguage sourceLanguage,
        String batchLibrary,
        String streamLibrary,
        @NotNull @Valid List<EnvironmentVariable> environmentVariables,
        @NotNull @Valid Algorithm algorithm,
        @Valid List<Action> actions,
        @Valid List<Task> tasks,
        @Valid List<Pipeline> pipelines
) {
    public JsonComponentDescriptor {
        environmentVariables = environmentVariables == null
                ? ImmutableList.of()
                : ImmutableList.copyOf(environmentVariables);
        algorithm = algorithm == null
                ? null
                : new Algorithm(
                    algorithm.name(),
                    algorithm.description(),
                    algorithm.actionType(),
                    algorithm.trackType(),
                    algorithm.outputChangedCounter(),
                    algorithm.requiresCollection(),
                    algorithm.providesCollection(),
                    StringUtils.isNotBlank(batchLibrary),
                    StringUtils.isNotBlank(streamLibrary));

        actions = actions == null
                ? ImmutableList.of()
                : ImmutableList.copyOf(actions);
        tasks = tasks == null
                ? ImmutableList.of()
                : ImmutableList.copyOf(tasks);
        pipelines = pipelines == null
                ? ImmutableList.of()
                : ImmutableList.copyOf(pipelines);
    }

    public boolean supportsBatchProcessing() {
        return StringUtils.isNotBlank(batchLibrary);
    }

    public boolean supportsStreamProcessing() {
        return StringUtils.isNotBlank(streamLibrary);
    }

    @JsonIgnore
    public boolean supportsBatchOrStreaming() {
        return supportsBatchProcessing() || supportsStreamProcessing();
    }


    record EnvironmentVariable(
            @NotBlank String name,
            @NotNull String value,
            @Pattern(regexp = ":", message = "must be \":\" or null") String sep) {
    }
}
