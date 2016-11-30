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

package org.mitre.mpf.component.api.detection;

import java.util.Map;

public class MPFVideoTrack {

    public int startFrame;
    public int stopFrame;
    public Map<Integer, MPFImageLocation> frameLocations;
    public float confidence;
    private Map<String, String> detectionProperties;

    public int getStartFrame() {
        return startFrame;
    }

    public void setStartFrame(int startFrame) {
        this.startFrame = startFrame;
    }

    public int getStopFrame() {
        return stopFrame;
    }

    public void setStopFrame(int stopFrame) {
        this.stopFrame = stopFrame;
    }

    public Map<Integer, MPFImageLocation> getFrameLocations() {
        return frameLocations;
    }

    public void setFrameLocations(Map<Integer, MPFImageLocation> frameLocations) {
        this.frameLocations = frameLocations;
    }

    public float getConfidence() {
        return confidence;
    }

    public void setConfidence(float confidence) {
        this.confidence = confidence;
    }

    public Map<String, String> getDetectionProperties() {
        return detectionProperties;
    }

    public void setDetectionProperties(Map<String, String> detectionProperties) {
        this.detectionProperties = detectionProperties;
    }

    public MPFVideoTrack(
        int startFrame,
        int stopFrame,
        Map<Integer, MPFImageLocation> frameLocations,
        float confidence,
        Map<String, String> detectionProperties
    ) {
        this.startFrame = startFrame;
        this.stopFrame = stopFrame;
        this.frameLocations = frameLocations;
        this.confidence = confidence;
        this.detectionProperties = detectionProperties;
    }
}
