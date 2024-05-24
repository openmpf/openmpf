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


package org.mitre.mpf.rest.api.pipelines;

import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.hibernate.validator.constraints.NotBlank;
import org.mitre.mpf.rest.api.util.Utils;
import org.mitre.mpf.rest.api.util.ValidName;

import com.google.common.collect.ImmutableList;


public record Action(
        @ValidName String name,
        @NotBlank String description,
        @ValidName String algorithm,
        @NotNull @Valid List<ActionProperty> properties
        ) implements PipelineElement {

    public Action {
        name = Utils.trimAndUpper(name);
        description = Utils.trim(description);
        algorithm = Utils.trimAndUpper(algorithm);
        properties = ImmutableList.copyOf(properties);
    }


    public String propertyValue(String name) {
        return properties.stream()
                .filter(p -> p.name().equalsIgnoreCase(name))
                .map(ActionProperty::value)
                .findAny()
                .orElse(null);
    }
}
