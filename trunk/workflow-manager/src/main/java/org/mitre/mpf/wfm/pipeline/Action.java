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
import org.mitre.mpf.wfm.util.TextUtils;
import org.mitre.mpf.wfm.util.ValidPipelineComponentName;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Collection;
import java.util.Objects;

public class Action implements PipelineComponent {

    private final String _name;
    @Override
    @ValidPipelineComponentName
    public String getName() {
        return _name;
    }

    private final String _description;
    @NotBlank
    public String getDescription() {
        return _description;
    }

    private final String _algorithm;
    @ValidPipelineComponentName
    public String getAlgorithm() {
        return _algorithm;
    }

    private final ImmutableList<Property> _properties;
    @NotNull @Valid
    public ImmutableList<Property> getProperties() {
        return _properties;
    }

    public String getPropertyValue(String name) {
        return _properties.stream()
                .filter(p -> p.getName().equalsIgnoreCase(name))
                .map(Property::getValue)
                .findAny()
                .orElse(null);
    }


    public Action(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("algorithm") String algorithm,
            @JsonProperty("properties") Collection<Property> properties) {
        _name = TextUtils.trimAndUpper(name);
        _description = StringUtils.trim(description);
        _algorithm = TextUtils.trimAndUpper(algorithm);
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
        Action other = (Action) obj;
        return Objects.equals(_name, other._name)
                && Objects.equals(_description, other._description)
                && Objects.equals(_properties, other._properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_name, _description, _properties);
    }



    public static class Property {
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

        public Property(
                @JsonProperty("name") String name,
                @JsonProperty("value") String value) {
            _name = TextUtils.trimAndUpper(name);
            _value = StringUtils.trim(value);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Property)) {
                return false;
            }
            Property property = (Property) obj;
            return Objects.equals(_name, property._name)
                    && Objects.equals(_value, property._value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_name, _value);
        }
    }
}
