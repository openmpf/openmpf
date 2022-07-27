/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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

package org.mitre.mpf.component.executor.detection;
import org.mitre.mpf.component.api.detection.MPFAudioTrack;

public class MPFDetectionAudioRequest {

    private int startTime;
    private int stopTime;
    private MPFAudioTrack feedForwardTrack;

    public int getStartTime() {
        return startTime;
    }

    public void setStartTime(int startTime) {
        this.startTime = startTime;
    }

    public int getStopTime() {
        return stopTime;
    }

    public void setStopTime(int stopTime) {
        this.stopTime = stopTime;
    }

    public MPFAudioTrack getFeedForwardTrack() {
        return feedForwardTrack;   // Could be null; be sure to check
    }

    // Constructor for a request that does not have a feed-forward track
    public MPFDetectionAudioRequest(int startTime,
                                    int stopTime) {
        this.startTime = startTime;
        this.stopTime = stopTime;
        this.feedForwardTrack = null;
    }

    // Constructor for a request that has a feed-forward track
    public MPFDetectionAudioRequest(int startTime,
                                    int stopTime,
                                    MPFAudioTrack track) {
        this.startTime = startTime;
        this.stopTime = stopTime;
        this.feedForwardTrack = track;
    }

}
