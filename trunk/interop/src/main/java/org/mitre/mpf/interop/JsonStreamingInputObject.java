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

import java.util.HashMap;
import java.util.Map;

@JsonTypeName("StreamingInputObject")
public class JsonStreamingInputObject {

    @JsonProperty("streamURI")
    @JsonPropertyDescription("The URI for this stream object.")
    private String streamURI;
    public String getStreamURI() { return streamURI; }

    @JsonProperty("segmentSize")
    @JsonPropertyDescription("The segment size to be applied to this stream.")
    private int segmentSize;
    public int getSegmentSize() { return segmentSize; }

    @JsonProperty("mediaProperties")
    @JsonPropertyDescription("A map of medium-specific properties that override algorithm properties.")
    private Map<String,String> mediaProperties;
    public Map<String,String> getMediaProperties() { return mediaProperties; }
    public void addProperty(String key, String value){
        mediaProperties.put(key, value);
    }


    public JsonStreamingInputObject(String streamURI, int segmentSize, Map <String,String> media_properties) {
        this.streamURI = streamURI;
        this.segmentSize = segmentSize;
        this.mediaProperties = media_properties;
    }

    @JsonCreator
    public static JsonStreamingInputObject factory(@JsonProperty("streamURI") String streamURI, @JsonProperty("segmentSize") int segmentSize,
                                                   @JsonProperty("mediaProperties") HashMap<String, String> mediaProperties) {
        JsonStreamingInputObject obj = null;

        if ( mediaProperties != null)  {
            obj = new JsonStreamingInputObject(streamURI, segmentSize, mediaProperties);
        } else {
            obj = new JsonStreamingInputObject(streamURI, segmentSize, new HashMap<String,String>());
        }
        return obj;
    }
}
