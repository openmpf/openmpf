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


package org.mitre.mpf.wfm.util;

import java.util.Arrays;
import java.util.OptionalInt;

import org.mitre.mpf.wfm.camel.operations.mediainspection.Fraction;

public interface FrameTimeInfo {
    public boolean hasConstantFrameRate();

    public boolean requiresTimeEstimation();

    public int getTimeMsFromFrame(int frameIndex);

    public int getFrameFromTimeMs(int timeMs);

    public OptionalInt getExactFrameCount();

    public OptionalInt getEstimatedDuration();


    public static FrameTimeInfo forConstantFrameRate(
            Fraction fps, int startTime, boolean requiresTimeEstimation, int frameCount) {
        return fpsFrameTimeInfo(
                fps, startTime, true, requiresTimeEstimation, OptionalInt.of(frameCount));
    }

    public static FrameTimeInfo forConstantFrameRate(
            double fps, int startTime, boolean requiresTimeEstimation, int frameCount) {
        return fpsFrameTimeInfo(fps, startTime, true, requiresTimeEstimation, OptionalInt.of(frameCount));
    }


    public static FrameTimeInfo forVariableFrameRateWithEstimatedTimes(Fraction fps) {
        return fpsFrameTimeInfo(fps, 0, false, true, OptionalInt.empty());
    }

    public static FrameTimeInfo forVariableFrameRateWithEstimatedTimes(double fps, int frameCount) {
        return fpsFrameTimeInfo(fps, 0, false, true, OptionalInt.of(frameCount));
    }


    public static FrameTimeInfo forVariableFrameRate(Fraction fps, int[] timeStamps,
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
                    var msPerFrame = fps.invert().mul(1000);
                    return startTime + (int) msPerFrame.mul(frameIndex).toDouble();
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

            public OptionalInt getExactFrameCount() {
                return OptionalInt.of(timeStamps.length);
            }

            public OptionalInt getEstimatedDuration() {
                if (timeStamps.length == 0) {
                    return OptionalInt.of(0);
                }
                if (timeStamps.length == 1) {
                    return OptionalInt.of((int) fps.invert().mul(1000).roundUp());
                }
                int lastFrameTime = timeStamps[timeStamps.length - 1];
                int secondLastFrameTime = timeStamps[timeStamps.length - 2];
                int prevTimeDiff = lastFrameTime - secondLastFrameTime;
                int endTime = lastFrameTime + prevTimeDiff;
                return OptionalInt.of(endTime - timeStamps[0]);
            }
        };
    }


    private static FrameTimeInfo fpsFrameTimeInfo(
            Fraction fps, int startTime, boolean hasConstantFrameRate,
            boolean requiresTimeEstimation,
            OptionalInt exactFrameCount) {
        var framesPerMs = fps.mul(new Fraction(1, 1000));
        var msPerFrame =  framesPerMs.invert();

        return new FrameTimeInfo() {

            public boolean hasConstantFrameRate() {
                return hasConstantFrameRate;
            }

            public boolean requiresTimeEstimation() {
                return requiresTimeEstimation;
            }

            public int getTimeMsFromFrame(int frameIndex) {
                return startTime + (int) msPerFrame.mul(frameIndex).toDouble();
            }

            public int getFrameFromTimeMs(int timeMs) {
                if (timeMs > startTime) {
                    int msSinceStart = timeMs - startTime;
                    return (int) framesPerMs.mul(msSinceStart).toDouble();
                }
                else {
                    return 0;
                }
            }

            public OptionalInt getExactFrameCount() {
                return exactFrameCount;
            }

            public OptionalInt getEstimatedDuration() {
                return exactFrameCount.stream()
                    .map(fc -> (int) msPerFrame.mul(fc).roundUp())
                    .findAny();
            }
        };
    }


    private static FrameTimeInfo fpsFrameTimeInfo(
            double fps, int startTime, boolean hasConstantFrameRate,
            boolean requiresTimeEstimation,
            OptionalInt exactFrameCount) {
        var framesPerMs = fps / 1000;
        var msPerFrame =  1000 / fps;

        return new FrameTimeInfo() {

            public boolean hasConstantFrameRate() {
                return hasConstantFrameRate;
            }

            public boolean requiresTimeEstimation() {
                return requiresTimeEstimation;
            }

            public int getTimeMsFromFrame(int frameIndex) {
                return startTime + (int) (msPerFrame * frameIndex);
            }

            public int getFrameFromTimeMs(int timeMs) {
                if (timeMs > startTime) {
                    int msSinceStart = timeMs - startTime;
                    return (int) (framesPerMs * msSinceStart);
                }
                else {
                    return 0;
                }
            }

            public OptionalInt getExactFrameCount() {
                return exactFrameCount;
            }

            public OptionalInt getEstimatedDuration() {
                return exactFrameCount.stream()
                    .map(fc -> (int) Math.ceil(msPerFrame * fc))
                    .findAny();
            }
        };
    }
}
