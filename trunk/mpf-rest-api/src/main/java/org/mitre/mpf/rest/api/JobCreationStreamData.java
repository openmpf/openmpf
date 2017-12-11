/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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

import java.util.HashMap;
import java.util.Map;

public class JobCreationStreamData {

    private String streamUri;
    public JobCreationStreamData() {}
    public JobCreationStreamData(String uri) {
        setStreamUri(uri);
    }
    public String getStreamUri() {
        return streamUri;
    }
    public void setStreamUri(String streamUri) {
        this.streamUri = streamUri;
    }

    private Map<String,String> mediaProperties = new HashMap<>();
    public Map<String, String> getMediaProperties() {
        return mediaProperties;
    }
    public void setMediaProperties(Map<String, String> mediaProperties) {
        this.mediaProperties = mediaProperties;
    }

    private int segmentSize = 0;
    public void setSegmentSize(int segmentSize) { this.segmentSize=segmentSize; }
    public int getSegmentSize() { return segmentSize; }

    /** this method will check the current settings within this job creation stream data,
     * and will return true if the current settings are set within the constraints defined for
     * streaming job data, false otherwise
     * @return true if settings define a valid streaming job data, false otherwise.
     */
    public boolean isValidStreamData() {
        // do error checks on the streaming job data
        if ( getSegmentSize() >= 10 && getStreamUri() != null ) {
            return true;
        } else {
            return false;
        }
    }

}
