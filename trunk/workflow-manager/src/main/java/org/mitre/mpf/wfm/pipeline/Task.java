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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;
import org.mitre.mpf.wfm.util.AllNotBlank;
import org.mitre.mpf.wfm.util.TextUtils;

import javax.validation.Valid;
import java.util.Collection;
import java.util.Objects;

public class Task implements PipelineComponent {

    private final String _name;
    @Override
    @NotBlank
    public String getName() {
        return _name;
    }

    private final String _description;
    @NotBlank
    public String getDescription() {
        return _description;
    }

    private final ImmutableList<String> _actions;
    @NotEmpty @Valid
    public ImmutableList<@AllNotBlank String> getActions() {
        return _actions;
    }


    public Task(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("actions") Collection<String> actions) {
        _name = TextUtils.trimAndUpper(name);
        _description = StringUtils.trim(description);
        _actions = TextUtils.trimAndUpper(actions, ImmutableList.toImmutableList());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof Task)) {
            return false;
        }

        var other = (Task) obj;
        return Objects.equals(_name, other._name) &&
                Objects.equals(_description, other._description) &&
                Objects.equals(_actions, other._actions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_name, _description, _actions);
    }
}
