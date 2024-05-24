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

package org.mitre.mpf.component.executor.detection;
import org.mitre.mpf.component.api.detection.MPFVideoTrack;

public class MPFDetectionVideoRequest {

    private int startFrame;
    private int stopFrame;
    private MPFVideoTrack feedForwardTrack;

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

    public MPFVideoTrack getFeedForwardTrack() {
        return feedForwardTrack;   // Could be null; be sure to check
    }

    // Constructor for a request that does not have a feed-forward track
    public MPFDetectionVideoRequest(int startFrame,
                                    int stopFrame) {
        this.startFrame = startFrame;
        this.stopFrame = stopFrame;
        this.feedForwardTrack = null;
    }

    // Constructor for a request that has a feed-forward track
    public MPFDetectionVideoRequest(int startFrame,
                                    int stopFrame,
                                    MPFVideoTrack track) {
        this.startFrame = startFrame;
        this.stopFrame = stopFrame;
        this.feedForwardTrack = track;
    }

}
