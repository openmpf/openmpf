/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2020 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2020 The MITRE Corporation                                       *
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


package org.mitre.mpf.rest.api.pipelines;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import org.hibernate.validator.constraints.NotBlank;
import org.mitre.mpf.rest.api.util.Utils;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Collection;
import java.util.Objects;

public class Action implements PipelineElement {

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

    private final String _algorithm;
    @NotBlank
    public String getAlgorithm() {
        return _algorithm;
    }

    private final ImmutableList<ActionProperty> _properties;
    @NotNull @Valid
    public ImmutableList<ActionProperty> getProperties() {
        return _properties;
    }

    public String getPropertyValue(String name) {
        return _properties.stream()
                .filter(p -> p.getName().equalsIgnoreCase(name))
                .map(ActionProperty::getValue)
                .findAny()
                .orElse(null);
    }


    public Action(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("algorithm") String algorithm,
            @JsonProperty("properties") Collection<ActionProperty> properties) {
        _name = Utils.trimAndUpper(name);
        _description = Utils.trim(description);
        _algorithm = Utils.trimAndUpper(algorithm);
        _properties = ImmutableList.copyOf(properties);
    }


    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof Action)) {
            return false;
        }
        var other = (Action) obj;
        return Objects.equals(_name, other._name)
                && Objects.equals(_description, other._description)
                && Objects.equals(_algorithm, other._algorithm)
                && Objects.equals(_properties, other._properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_name, _description, _algorithm, _properties);
    }
}
