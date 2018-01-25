/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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

import java.util.HashMap;
import java.util.Map;

@JsonTypeName("MediaInputObject")
public class JsonMediaInputObject {

    @JsonProperty("mediaUri")
    @JsonPropertyDescription("The URI for this media object.")
    private String mediaUri;
    public String getMediaUri() { return mediaUri; }

    @JsonProperty("properties")
    @JsonPropertyDescription("A map of medium-specific properties that override algorithm properties.")
    private Map<String,String> properties;
    public Map<String,String> getProperties() { return properties; }
    public void addProperty(String key, String value){
        properties.put(key,value);
    }


    public JsonMediaInputObject(String mediaUri) {
        this.mediaUri = mediaUri;
        this.properties = new HashMap<>();
    }

    @JsonCreator
    public static JsonMediaInputObject factory(@JsonProperty("mediaUri") String mediaUri,
                                         @JsonProperty("properties") HashMap<String, String> properties) {
        JsonMediaInputObject obj = new JsonMediaInputObject(mediaUri);

        if (properties!=null) {
            obj.properties.putAll(properties);
        }
        return obj;
    }
}
