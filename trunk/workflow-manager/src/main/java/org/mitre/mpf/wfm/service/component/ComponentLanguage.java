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

package org.mitre.mpf.wfm.service.component;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

public enum ComponentLanguage {
    JAVA("java"),
    CPP("c++"),
    PYTHON("python");

    private static final Map<String, ComponentLanguage> _namesMap = getNamesMap();

    private final String _stringValue;

    ComponentLanguage(String stringValue) {
        _stringValue = stringValue.toLowerCase();
    }

    @JsonCreator
    public static ComponentLanguage forValue(String value) {
        return _namesMap.get(value.toLowerCase());
    }

    @JsonValue
    public String getValue() {
        return _stringValue;
    }

    private static Map<String, ComponentLanguage> getNamesMap() {
        return Stream.of(ComponentLanguage.values())
                .collect(toMap(ComponentLanguage::getValue, Function.identity()));
    }

}
