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
import org.mitre.mpf.wfm.pipeline.Action;
import org.mitre.mpf.wfm.pipeline.Algorithm;
import org.mitre.mpf.wfm.pipeline.Pipeline;
import org.mitre.mpf.wfm.pipeline.Task;

import javax.validation.Valid;
import javax.validation.constraints.Null;
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
    public String batchLibrary;

    @Null
    public String streamLibrary;

    @Null
    public List<JsonComponentDescriptor.EnvironmentVariable> environmentVariables;

    @Null
    public Algorithm algorithm;

    @Valid
    public List<Action> actions;

    @Valid
    public List<Task> tasks;

    @Valid
    public List<Pipeline> pipelines;

    public JsonExtrasDescriptor(JsonComponentDescriptor jsonDescriptor) {
        this.componentName        = jsonDescriptor.getComponentName();
        this.componentVersion     = jsonDescriptor.getComponentVersion();
        this.middlewareVersion    = jsonDescriptor.getMiddlewareVersion();
        this.sourceLanguage       = jsonDescriptor.getSourceLanguage();
        this.batchLibrary         = jsonDescriptor.getBatchLibrary();
        this.streamLibrary        = jsonDescriptor.getStreamLibrary();
        this.environmentVariables = jsonDescriptor.getEnvironmentVariables();
        this.algorithm            = jsonDescriptor.getAlgorithm();
        this.actions              = jsonDescriptor.getActions();
        this.pipelines            = jsonDescriptor.getPipelines();
    }
}
