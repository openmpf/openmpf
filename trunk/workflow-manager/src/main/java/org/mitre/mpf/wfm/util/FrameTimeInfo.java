/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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


package org.mitre.mpf.wfm.util;

import java.util.Arrays;

public interface FrameTimeInfo {
    public boolean hasConstantFrameRate();

    public boolean requiresTimeEstimation();

    public int getTimeMsFromFrame(int frameIndex);

    public int getFrameFromTimeMs(int timeMs);


    public static FrameTimeInfo forConstantFrameRate(double fps, int startTime,
                                                     boolean requiresTimeEstimation) {
        return fpsFrameTimeInfo(fps, startTime, true, requiresTimeEstimation);
    }

    public static FrameTimeInfo forVariableFrameRateWithEstimatedTimes(double fps) {
        return fpsFrameTimeInfo(fps, 0, false, true);
    }


    public static FrameTimeInfo forVariableFrameRate(double fps, int[] timeStamps,
                                                     boolean requiresTimeEstimation) {
        return new FrameTimeInfo() {

            public boolean hasConstantFrameRate() {
                return false;
            }

            public boolean requiresTimeEstimation() {
                return requiresTimeEstimation;
            }

            public int getTimeMsFromFrame(int frameIndex) {
                try {
                    return timeStamps[frameIndex];
                }
                catch (ArrayIndexOutOfBoundsException e) {
                    int startTime = timeStamps.length > 0 ? timeStamps[0] : 0;
                    return getTimeUsingFps(frameIndex, fps, startTime);
                }
            }

            public int getFrameFromTimeMs(int timeMs) {
                int rv = Arrays.binarySearch(timeStamps, timeMs);
                if (rv >= 0) {
                    return rv;
                }
                else {
                    int firstGreaterIdx = -rv - 1;
                    return Math.max(firstGreaterIdx - 1, 0);
                }
            }
        };
    }

    private static FrameTimeInfo fpsFrameTimeInfo(
            double fps, int startTime, boolean hasConstantFrameRate,
            boolean requiresTimeEstimation) {
        double framesPerMs = fps / 1000;
        return new FrameTimeInfo() {

            public boolean hasConstantFrameRate() {
                return hasConstantFrameRate;
            }

            public boolean requiresTimeEstimation() {
                return requiresTimeEstimation;
            }

            public int getTimeMsFromFrame(int frameIndex) {
                return getTimeUsingFps(frameIndex, fps, startTime);
            }

            public int getFrameFromTimeMs(int timeMs) {
                if (timeMs > startTime) {
                    return (int) (framesPerMs * (timeMs - startTime));
                }
                else {
                    return 0;
                }
            }
        };
    }


    private static int getTimeUsingFps(int frameIndex, double fps, int startTime) {
        return startTime + (int) (frameIndex * 1000 / fps);
    }
}
