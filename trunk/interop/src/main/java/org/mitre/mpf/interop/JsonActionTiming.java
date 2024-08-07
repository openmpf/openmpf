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

package org.mitre.mpf.interop;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"name", "processingTime"})
public class JsonActionTiming {

    @JsonPropertyDescription("The name of the action.")
    private final String _name;
    public String getName() { return _name; }

    @JsonPropertyDescription(
            "The amount of time in milliseconds that a component spent executing the action.")
    private final long _processingTime;
    public long getProcessingTime() { return _processingTime; }

    public JsonActionTiming(
            @JsonProperty("name") String name,
            @JsonProperty("processingTime") long processingTime) {
        _name = name;
        _processingTime = processingTime;
    }


    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof JsonActionTiming)) {
            return false;
        }
        JsonActionTiming otherTiming = (JsonActionTiming) other;
        return _name.equals(otherTiming.getName())
                && _processingTime == otherTiming._processingTime;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_name, _processingTime);
    }
}
