/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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

import javax.validation.constraints.NotNull;
import java.util.Objects;

public class ActionProperty {

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


    public ActionProperty(
            @JsonProperty("name") String name,
            @JsonProperty("value") String value) {
        _name = Utils.trimAndUpper(name);
        _value = Utils.trim(value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ActionProperty)) {
            return false;
        }
        var property = (ActionProperty) obj;
        return Objects.equals(_name, property._name)
                && Objects.equals(_value, property._value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_name, _value);
    }
}
