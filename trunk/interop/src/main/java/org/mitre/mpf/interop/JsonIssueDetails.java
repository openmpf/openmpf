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

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Comparator;
import java.util.Objects;

import static org.mitre.mpf.interop.util.CompareUtils.stringCompare;

public class JsonIssueDetails implements Comparable<JsonIssueDetails> {

    private final String _source;
    public String getSource() { return _source; }

    private final String _code;
    public String getCode() { return _code; }

    private final String _message;
    public String getMessage() { return _message; }


    public JsonIssueDetails(
            @JsonProperty("source") String source,
            @JsonProperty("code") String code,
            @JsonProperty("message") String message) {
        _source = source;
        _code = code;
        _message = message;
    }


    @Override
    public boolean equals(Object other) {
        return other instanceof JsonIssueDetails
                && compareTo((JsonIssueDetails) other) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_source, _code, _message);
    }


    private static final Comparator<JsonIssueDetails> DEFAULT_COMPARATOR = Comparator
            .nullsFirst(
                stringCompare(JsonIssueDetails::getSource)
                .thenComparing(stringCompare(JsonIssueDetails::getCode))
                .thenComparing(stringCompare(JsonIssueDetails::getMessage)));

    @Override
    public int compareTo(JsonIssueDetails other) {
        //noinspection ObjectEquality - False positive
        return this == other
                ? 0
                : DEFAULT_COMPARATOR.compare(this, other);
    }
}
