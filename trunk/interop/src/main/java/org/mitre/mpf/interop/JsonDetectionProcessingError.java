/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.Comparator;
import java.util.Objects;

import static org.mitre.mpf.interop.util.CompareUtils.stringCompare;


@JsonTypeName("DetectionProcessingError")
public class JsonDetectionProcessingError implements Comparable<JsonDetectionProcessingError> {

    @JsonProperty("startOffsetFrame")
    @JsonPropertyDescription("The earliest frame in the medium which may be affected by this error.")
    private final int _startOffsetFrame;
    public int getStartOffsetFrame() { return _startOffsetFrame; }

    @JsonProperty("stopOffsetFrame")
    @JsonPropertyDescription("The latest frame in the medium which may be affected by this error.")
    private final int _stopOffsetFrame;
    public int getStopOffsetFrame() { return _stopOffsetFrame; }

    @JsonProperty("startOffsetTime")
    @JsonPropertyDescription("The earliest time in the medium which may be affected by this error, in milliseconds.")
    private final int _startOffsetTime;
    public int getStartOffsetTime() { return _startOffsetTime; }

    @JsonProperty("stopOffsetTime")
    @JsonPropertyDescription("The latest time in the medium which may be affected by this error, in milliseconds.")
    private final int _stopOffsetTime;
    public int getStopOffsetTime() { return _stopOffsetTime; }

    @JsonProperty("code")
    @JsonPropertyDescription("The error code associated with the detection error.")
    private final String _code;
    public String getCode() { return _code; }

    @JsonProperty("message")
    @JsonPropertyDescription("The messages associated with the detection error.")
    private final String _message;
    public String getMessage() { return _message; }


    @JsonCreator
    public JsonDetectionProcessingError(@JsonProperty("startOffsetFrame") int startOffsetFrame,
                                        @JsonProperty("stopOffsetFrame") int stopOffsetFrame,
                                        @JsonProperty("startOffsetTime") int startOffsetTime,
                                        @JsonProperty("stopOffsetTime") int stopOffsetTime,
                                        @JsonProperty("code") String code,
                                        @JsonProperty("message") String message) {
        _startOffsetFrame = startOffsetFrame;
        _stopOffsetFrame = stopOffsetFrame;
        _startOffsetTime = startOffsetTime;
        _stopOffsetTime = stopOffsetTime;
        _code = code;
        _message = message;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_startOffsetFrame, _stopOffsetFrame, _startOffsetTime, _stopOffsetTime, _code, _message);
    }



    @Override
    public boolean equals(Object obj) {
        return obj instanceof JsonDetectionProcessingError && compareTo((JsonDetectionProcessingError) obj) == 0;
    }

    private static final Comparator<JsonDetectionProcessingError> DEFAULT_COMPARATOR = Comparator.nullsFirst(
            Comparator.comparingInt(JsonDetectionProcessingError::getStartOffsetFrame)
                    .thenComparingInt(JsonDetectionProcessingError::getStopOffsetFrame)
                    .thenComparingInt(JsonDetectionProcessingError::getStartOffsetTime)
                    .thenComparingInt(JsonDetectionProcessingError::getStopOffsetTime)
                    .thenComparing(stringCompare(JsonDetectionProcessingError::getCode))
                    .thenComparing(stringCompare(JsonDetectionProcessingError::getMessage)));

    @Override
    public int compareTo(JsonDetectionProcessingError other) {
        //noinspection ObjectEquality - False positive
        if (this == other) {
            return 0;
        }
        return DEFAULT_COMPARATOR.compare(this, other);
    }
}
