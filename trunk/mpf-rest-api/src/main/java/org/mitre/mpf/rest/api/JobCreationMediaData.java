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

package org.mitre.mpf.rest.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class JobCreationMediaData {

    private String mediaUri;

    private Map<String, String> properties = new HashMap<>();

    private Map<String, String> metadata = new HashMap<>();

    private List<JobCreationMediaRange> frameRanges = new ArrayList<>();

    private List<JobCreationMediaRange> timeRanges = new ArrayList<>();

    public JobCreationMediaData() {}

    public JobCreationMediaData(String uri) {
        this.mediaUri = uri;
    }

    public String getMediaUri() {
        return mediaUri;
    }

    public void setMediaUri(String mediaUri) {
        this.mediaUri = mediaUri;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public List<JobCreationMediaRange> getFrameRanges() {
        return frameRanges;
    }

    public void setFrameRanges(List<JobCreationMediaRange> frameRanges) {
        this.frameRanges = frameRanges;
    }

    public List<JobCreationMediaRange> getTimeRanges() {
        return timeRanges;
    }

    public void setTimeRanges(List<JobCreationMediaRange> timeRanges) {
        this.timeRanges = timeRanges;
    }
}
