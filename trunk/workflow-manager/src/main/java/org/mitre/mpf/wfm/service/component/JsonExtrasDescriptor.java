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

import javax.validation.Valid;
import javax.validation.constraints.Null;
import javax.validation.constraints.Pattern;
import java.util.List;

public class JsonExtrasDescriptor {

    // @Pattern(regexp = "^Extras$") // exact match
    @NotBlank
    public String componentName;

    public String componentVersion;

    public String middlewareVersion;

    @Null
    public ComponentLanguage sourceLanguage;

    @Null
    public String batchLibraryPath;

    @Null
    public String streamLibraryPath;

    @Null
    public List<JsonComponentDescriptor.EnvironmentVariable> environmentVariables;

    @Null
    public JsonComponentDescriptor.Algorithm algorithm;

    @Valid
    public List<JsonComponentDescriptor.Action> actions;

    @Valid
    public List<JsonComponentDescriptor.Task> tasks;

    @Valid
    public List<JsonComponentDescriptor.Pipeline> pipelines;

    public JsonExtrasDescriptor(JsonComponentDescriptor jsonDescriptor) {
        this.componentName        = jsonDescriptor.componentName;
        this.componentVersion     = jsonDescriptor.componentVersion;
        this.middlewareVersion    = jsonDescriptor.middlewareVersion;
        this.sourceLanguage       = jsonDescriptor.sourceLanguage;
        this.batchLibraryPath     = jsonDescriptor.batchLibraryPath;
        this.streamLibraryPath    = jsonDescriptor.streamLibraryPath;
        this.environmentVariables = jsonDescriptor.environmentVariables;
        this.algorithm            = jsonDescriptor.algorithm;
        this.actions              = jsonDescriptor.actions;
        this.pipelines            = jsonDescriptor.pipelines;
    }
}
