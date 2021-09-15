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
import java.util.function.IntUnaryOperator;

public class FrameTimeInfo {
    private final boolean _hasConstantFrameRate;

    private final boolean _requiresTimeEstimation;

    private final IntUnaryOperator _frameToTimeFn;

    private final IntUnaryOperator _timeToFrameFn;

    private FrameTimeInfo(boolean hasConstantFrameRate,  boolean requiresTimeEstimation,
                          IntUnaryOperator frameToTimeFn,
                          IntUnaryOperator timeToFrameFn) {
        _hasConstantFrameRate = hasConstantFrameRate;
        _requiresTimeEstimation = requiresTimeEstimation;
        _frameToTimeFn = frameToTimeFn;
        _timeToFrameFn = timeToFrameFn;
    }


    public static FrameTimeInfo forConstantFrameRate(double fps, int startTime,
                                                     boolean requiresTimeEstimation) {
        int startFrame = startTime * (int)(fps / 1000);
        return new FrameTimeInfo(true, requiresTimeEstimation,
                getTimeUsingFps(fps, startTime),
                getFrameUsingFps(fps, startFrame));
    }

    public static FrameTimeInfo forVariableFrameRate(double fps, int[] timeStamps,
                                                     boolean requiresTimeEstimation) {
        return new FrameTimeInfo(false, requiresTimeEstimation, frameIdx -> {
            try {
                return timeStamps[frameIdx];
            }
            catch (ArrayIndexOutOfBoundsException e) {
                int startTime = timeStamps.length > 0 ? timeStamps[0] : 0;
                return getTimeUsingFps(fps, startTime).applyAsInt(frameIdx);
            }
        },
                timeIdx -> {
            int insertionPoint  = Arrays.binarySearch(timeStamps, timeIdx);
            if (insertionPoint >= 0) {
                return timeStamps[insertionPoint];
            }
            else {
                return timeStamps[-(insertionPoint) - 1];
            }

        });
    }


    public static FrameTimeInfo forVariableFrameRateWithEstimatedTimes(double fps) {
        return new FrameTimeInfo(false, true,
                getTimeUsingFps(fps, 0),
                getFrameUsingFps(fps, 0));
    }

    private static IntUnaryOperator getTimeUsingFps(double fps, int startTime) {
        double msPerFrame = 1000 / fps;
        return frameIdx -> startTime + (int) (frameIdx * msPerFrame);
    }

    private static IntUnaryOperator getFrameUsingFps(double fps, int startFrame) {
        double framesPerMs = fps / 1000;
        return timeIdx -> startFrame + (int) (timeIdx * framesPerMs);
    }

    public boolean hasConstantFrameRate() {
        return _hasConstantFrameRate;
    }

    public boolean requiresTimeEstimation() {
        return _requiresTimeEstimation;
    }

    public int getTimeMsFromFrame(int frameIndex) {
        return _frameToTimeFn.applyAsInt(frameIndex);
    }

    public int getFrameFromTimeMs(int frameIndex) {
        return _frameToTimeFn.applyAsInt(frameIndex);
    }

}
