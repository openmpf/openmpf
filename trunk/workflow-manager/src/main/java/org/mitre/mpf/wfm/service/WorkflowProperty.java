/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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


package org.mitre.mpf.wfm.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.rest.api.pipelines.ValueType;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.util.TextUtils;

import java.util.Collection;

public class WorkflowProperty {

    private final String _name;
    public String getName() {
        return _name;
    }

    private final String _description;
    public String getDescription() {
        return _description;
    }

    private final ValueType _type;
    public ValueType getType() {
        return _type;
    }

    private final String _defaultValue;
    public String getDefaultValue() {
        return _defaultValue;
    }

    private final String _propertiesKey;
    public String getPropertiesKey() {
        return _propertiesKey;
    }

    private final ImmutableSet<MediaType> _mediaTypes;
    public ImmutableSet<MediaType> getMediaTypes() {
        return _mediaTypes;
    }


    public WorkflowProperty(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("type") ValueType type,
            @JsonProperty("defaultValue") String defaultValue,
            @JsonProperty("propertiesKey") String propertiesKey,
            @JsonProperty("mediaTypes") Collection<MediaType> mediaTypes) {
        _name = TextUtils.trimAndUpper(name);
        _description = StringUtils.trim(description);
        _type = type;
        _defaultValue = StringUtils.trim(defaultValue);
        _propertiesKey = StringUtils.trim(propertiesKey);
        _mediaTypes = ImmutableSet.copyOf(mediaTypes);
    }
}
