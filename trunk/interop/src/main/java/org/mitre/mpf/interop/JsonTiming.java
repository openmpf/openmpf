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

package org.mitre.mpf.interop;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"processingTime", "actions"})
public class JsonTiming {

    @JsonPropertyDescription(
            "The amount of time in milliseconds that all components spent executing the job.")
    public long getProcessingTime() { return _processingTime; }
    private final long _processingTime;

    @JsonPropertyDescription("The processing time for each action.")
    private final Set<JsonActionTiming> _actions;
    public Set<JsonActionTiming> getActions() { return _actions; }

    public JsonTiming(
                @JsonProperty("processingTime") long processingTime,
                // The creator will pass in the actions in the order they appear in the pipeline.
                @JsonProperty("actions") Collection<JsonActionTiming> actions) {
        _processingTime = processingTime;
        _actions = Collections.unmodifiableSet(new LinkedHashSet<>(actions));
    }


    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof JsonTiming)) {
            return false;
        }
        JsonTiming otherTiming = (JsonTiming) other;
        return _processingTime == otherTiming.getProcessingTime()
                && _actions.equals(otherTiming.getActions());
    }

    @Override
    public int hashCode() {
        return Objects.hash(_processingTime, _actions);
    }
}
