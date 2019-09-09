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


package org.mitre.mpf.rest.api.pipelines;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.NotBlank;
import org.mitre.mpf.rest.api.util.Utils;
import org.mitre.mpf.rest.api.util.ValidAlgoPropValue;

import javax.validation.constraints.NotNull;
import java.util.Objects;


@ValidAlgoPropValue
public class AlgorithmProperty {

    private final String _name;
    @NotBlank
    public String getName() {
        return _name;
    }

    private final String _description;
    @NotBlank
    public String getDescription() {
        return _description;
    }

    private final ValueType _type;
    @NotNull
    public ValueType getType() {
        return _type;
    }

    // option A
    private final String _defaultValue;
    public String getDefaultValue() {
        return _defaultValue;
    }

    // option B
    private final String _propertiesKey;
    public String getPropertiesKey() {
        return _propertiesKey;
    }


    public AlgorithmProperty(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("type") ValueType type,
            @JsonProperty("defaultValue") String defaultValue,
            @JsonProperty("propertiesKey") String propertiesKey) {
        _name = Utils.trimAndUpper(name);
        _description = Utils.trim(description);
        _type = type;
        _defaultValue = Utils.trim(defaultValue);
        _propertiesKey = Utils.trim(propertiesKey);
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AlgorithmProperty)) {
            return false;
        }
        var other = (AlgorithmProperty) obj;
        return Objects.equals(_name, other._name)
                && Objects.equals(_description, other._description)
                && _type == other._type
                && Objects.equals(_defaultValue, other._defaultValue)
                && Objects.equals(_propertiesKey, other._propertiesKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_name, _description, _type, _defaultValue, _propertiesKey);
    }
}

