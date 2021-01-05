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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

import static org.mitre.mpf.interop.util.CompareUtils.sortedSetCompare;

public class JsonMediaIssue implements Comparable<JsonMediaIssue> {

    private final long _mediaId;
    // When mediaId is 0, that means the warning applies to the entire job, rather than a specific piece of media.
    // The following annotation hides the mediaId field when it is 0.
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public long getMediaId() { return _mediaId; }

    private final SortedSet<JsonIssueDetails> _details;
    public SortedSet<JsonIssueDetails> getDetails() {
        return Collections.unmodifiableSortedSet(_details);
    }


    @JsonCreator
    public JsonMediaIssue(
            @JsonProperty("mediaId") long mediaId,
            @JsonProperty("details") Collection<JsonIssueDetails> details) {
        _mediaId = mediaId;
        _details = new TreeSet<>(details);
    }


    public void addDetails(JsonIssueDetails details) {
        _details.add(details);
    }


    @Override
    public boolean equals(Object other) {
        return other instanceof JsonMediaIssue &&
                compareTo((JsonMediaIssue) other) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_mediaId, _details);
    }


    private static final Comparator<JsonMediaIssue> DEFAULT_COMPARATOR = Comparator
            .nullsFirst(Comparator
                .comparingLong(JsonMediaIssue::getMediaId)
                .thenComparing(sortedSetCompare(JsonMediaIssue::getDetails)));

    @Override
    public int compareTo(JsonMediaIssue other) {
        //noinspection ObjectEquality - False
        return this == other
                ? 0
                : DEFAULT_COMPARATOR.compare(this, other);
    }
}
