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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.Comparator;
import java.util.Objects;

@JsonTypeName("MediaRange")
@JsonPropertyOrder({"start", "stop"})
public class JsonMediaRange implements Comparable<JsonMediaRange> {

    private final int start;
    @JsonPropertyDescription("Inclusive beginning of range")
    public int getStart() { return start; }

    private final int stop;
    @JsonPropertyDescription("Inclusive end of range")
    public int getStop() { return stop; }


    public JsonMediaRange(
            @JsonProperty("begin") int start,
            @JsonProperty("end") int stop) {
        this.start = start;
        this.stop = stop;
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, stop);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JsonMediaRange && compareTo((JsonMediaRange) other) == 0;
    }

    private static final Comparator<JsonMediaRange> DEFAULT_COMPARATOR = Comparator
            .comparingInt(JsonMediaRange::getStart)
            .thenComparingInt(JsonMediaRange::getStop);

    @Override
    public int compareTo(JsonMediaRange other) {
        //noinspection ObjectEquality - False positive
        if (this == other) {
            return 0;
        }
        else {
            return DEFAULT_COMPARATOR.compare(this, other);
        }
    }
}
