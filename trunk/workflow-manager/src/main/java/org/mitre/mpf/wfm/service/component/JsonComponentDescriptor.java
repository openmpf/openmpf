/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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
import java.util.List;

/**
 * This class is only used to avoid manually extracting information from the JSON descriptor file.
 * <p>
 * This intentionally does not use getters and setters. Adding getters and setters to this class
 * would only make it harder to figure out the structure of the descriptor.
 */
@SuppressWarnings("PublicField")
public class JsonComponentDescriptor {

    @NotBlank
    public String componentName;

    @NotNull
    public String componentVersion;

    @NotNull
    public String middlewareVersion;

    public String setupFile;

    public String instructionsFile;

    @NotNull(message = "must be java or c++")
    public ComponentLanguage sourceLanguage;

    @NotBlank
    public String pathName;

    @NotNull
    @Valid
    public List<@AllNotBlank String> launchArgs;

    @NotNull
    @Valid
    public List<EnvironmentVariable> environmentVariables;

    @NotNull
    @Valid
    public Algorithm algorithm;

    @Valid
    public List<Action> actions;

    @Valid
    public List<Task> tasks;

    @Valid
    public List<Pipeline> pipelines;

    public static class EnvironmentVariable {
        @NotBlank
        public String name;

        @NotNull
        public String value;

        @Pattern(regexp = ":", message = "must be \":\" or null")
        public String sep;
    }

    @ScriptAssert(lang = "javascript", script = "_this.supportsBatchProcessing || _this.supportsStreamProcessing",
            message = "must contain supportsBatchProcessing, supportsStreamProcessing, or both.")
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

        public boolean supportsBatchProcessing;

        public boolean supportsStreamProcessing;
    }

    public static class AlgoRequires {
        @NotNull
        @Valid
        public List<@AllNotBlank String> states;
    }

    public static class AlgoProvides {
        @NotNull
        @Valid
        public List<@AllNotBlank String> states;

        @NotNull
        @Valid
        public List<AlgoProvidesProp> properties;
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
    }

    public static class Pipeline {
        @NotBlank
        public String name;

        @NotBlank
        public String description;

        @NotEmpty
        @Valid
        public List<@AllNotBlank String> tasks;
    }

    public static class Task {
        @NotBlank
        public String name;

        @NotBlank
        public String description;

        @NotEmpty
        @Valid
        public List<@AllNotBlank String> actions;
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
    }

    public static class ActionProperty {
        @NotBlank
        public String name;

        @NotNull
        public String value;
    }
}
