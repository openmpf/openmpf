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


package org.mitre.mpf.rest.api.pipelines.transients;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import org.hibernate.validator.constraints.NotEmpty;
import org.mitre.mpf.rest.api.util.AllNotBlank;
import org.mitre.mpf.rest.api.util.Utils;

import javax.validation.Valid;
import java.util.List;

public class TransientPipelineDefinition {

    private final ImmutableList<String> _pipeline;
    @NotEmpty
    @Valid
    public ImmutableList<@AllNotBlank String> getPipeline() {
        return _pipeline;
    }

    private final ImmutableList<TransientTask> _tasks;
    @Valid
    public ImmutableList<TransientTask> getTasks() {
        return _tasks;
    }

    private final ImmutableList<TransientAction> _actions;
    @Valid
    public ImmutableList<TransientAction> getActions() {
        return _actions;
    }

    private final String _displayName;

    public String getDisplayName() {
        return _displayName;
    }

    public TransientPipelineDefinition(
            @JsonProperty("pipeline") List<String> pipeline,
            @JsonProperty("tasks") List<TransientTask> tasks,
            @JsonProperty("actions") List<TransientAction> actions, 
            @JsonProperty("displayName") String displayName) {
        
        _displayName = displayName.isBlank() 
                ? "Job specified transient pipeline" 
                : displayName;
        _pipeline = Utils.trimAndUpper(pipeline, ImmutableList.toImmutableList());
        _tasks = tasks == null
                ? ImmutableList.of()
                : ImmutableList.copyOf(tasks);
        _actions = actions == null
                ? ImmutableList.of()
                : ImmutableList.copyOf(actions);
    }
}
