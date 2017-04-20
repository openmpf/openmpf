/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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
import org.apache.commons.lang3.ObjectUtils;

import java.util.*;

@JsonTypeName("TypeOutputObject")
public class JsonActionOutputObject implements Comparable<JsonActionOutputObject> {

    public static final String NO_TRACKS_TYPE = "NO TRACKS";

    @JsonProperty("source")
    @JsonPropertyDescription("The action source.")
    private String source;
    public String getSource() { return source; }

    @JsonProperty("tracks")
    @JsonPropertyDescription("The set of object detection tracks produced in this action for the given medium.")
    private SortedSet<JsonTrackOutputObject> tracks;
    public SortedSet<JsonTrackOutputObject> getTracks() { return tracks; }

    public JsonActionOutputObject(String source) {
        this.source = source;
        this.tracks = new TreeSet<>();
    }

    public JsonActionOutputObject(){}

    @JsonCreator
    public static JsonActionOutputObject factory(@JsonProperty("source") String source,
                                               @JsonProperty("tracks") SortedSet<JsonTrackOutputObject> tracks) {
        JsonActionOutputObject trackOutputObject = new JsonActionOutputObject(source);
        if(tracks != null) {
            trackOutputObject.tracks.addAll(tracks);
        }
        return trackOutputObject;
    }

    public int hashCode() {
        return Objects.hash(source, tracks);
    }

    public boolean equals(Object other) {
        if(other == null || !(other instanceof JsonActionOutputObject)) {
            return false;
        } else {
            JsonActionOutputObject casted = (JsonActionOutputObject)other;
            return compareTo(casted) == 0;
        }
    }

    @Override
    public int compareTo(JsonActionOutputObject other) {
        int result = 0;
        if(other == null) {
            return 0;
        } else if((result = ObjectUtils.compare(source, other.source, false)) != 0
                || (result = Integer.compare(tracks.hashCode(), other.tracks.hashCode())) != 0) {
            return result;
        } else {
            return 0;
        }
    }
}