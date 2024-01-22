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

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import static org.mitre.mpf.interop.util.CompareUtils.sortedSetCompare;

@JsonPropertyOrder({"processingTime", "actions"})
public class JsonTiming implements Comparable<JsonTiming> {

    @JsonPropertyDescription(
            "The amount of time in milliseconds that all components spent executing the job.")
    public long getProcessingTime() { return _processingTime; }
    private final long _processingTime;

    @JsonPropertyDescription("The processing time for each action.")
    private final SortedSet<JsonActionTiming> _actions;
    public SortedSet<JsonActionTiming> getActions() { return _actions; }

    public JsonTiming(
                @JsonProperty("processingTime") long processingTime,
                @JsonProperty("actions") Collection<JsonActionTiming> actions) {
        _processingTime = processingTime;
        _actions = Collections.unmodifiableSortedSet(new TreeSet<>(actions));
    }


    @Override
    public boolean equals(Object other) {
        return other instanceof JsonTiming && compareTo((JsonTiming) other) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_processingTime, _actions);
    }


    private static final Comparator<JsonTiming> DEFAULT_COMPARATOR = Comparator
            .nullsFirst(Comparator
                .comparingLong(JsonTiming::getProcessingTime)
                .thenComparing(sortedSetCompare(JsonTiming::getActions)));

    @Override
    public int compareTo(JsonTiming other) {
        return this == other
                ? 0
                : DEFAULT_COMPARATOR.compare(this, other);
    }
}
