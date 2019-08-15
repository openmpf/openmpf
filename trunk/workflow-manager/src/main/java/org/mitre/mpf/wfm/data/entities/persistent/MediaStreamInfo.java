/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.data.entities.persistent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class MediaStreamInfo {

    private final long _id;
    public long getId() { return _id; }

    private final String _uri;
    public String getUri() { return _uri; }


    private final ImmutableMap<String, String> _mediaProperties;
    public ImmutableMap<String, String> getMediaProperties() { return _mediaProperties; }
    public String getMediaProperty(String key) { return _mediaProperties.get(key); }

    private final int _segmentSize;
    public int getSegmentSize() { return _segmentSize; }

    @JsonCreator
    public MediaStreamInfo(
            @JsonProperty("id") long id,
            @JsonProperty("uri") String uri,
            @JsonProperty("segmentSize") int segmentSize,
            @JsonProperty("mediaProperties") Map<String, String> mediaProperties) {
        _id = id;
        _uri = uri;
        _segmentSize = segmentSize;
        _mediaProperties = ImmutableMap.copyOf(mediaProperties);
    }

    @Override
    public String toString() {
        return String.format("%s#<id=%d, uri='%s'>",
                             getClass().getSimpleName(), _id, getUri());
    }
}
