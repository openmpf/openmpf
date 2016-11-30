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

public class MPFImageLocation {

    private int xLeftUpper;
    private int yLeftUpper;
    private int width;
    private int height;
    private float confidence;
    private Map<String, String> detectionProperties;

    public int getXLeftUpper() {
        return xLeftUpper;
    }

    public void setXLeftUpper(int xLeftUpper) {
        this.xLeftUpper = xLeftUpper;
    }

    public int getYLeftUpper() {
        return yLeftUpper;
    }

    public void setYLeftUpper(int yLeftUpper) {
        this.yLeftUpper = yLeftUpper;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
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

    public MPFImageLocation(
        int xLeftUpper,
        int yLeftUpper,
        int width,
        int height,
        float confidence,
        Map<String, String> detectionProperties
    ) {
        this.xLeftUpper = xLeftUpper;
        this.yLeftUpper = yLeftUpper;
        this.width = width;
        this.height = height;
        this.confidence = confidence;
        this.detectionProperties = detectionProperties;
    }
}
