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

import java.util.function.IntUnaryOperator;

public class FrameTimeInfo {
    private final boolean _hasConstantFrameRate;

    private final boolean _requiresTimeEstimation;

    private final IntUnaryOperator _frameToTimeFn;


    private FrameTimeInfo(boolean hasConstantFrameRate,  boolean requiresTimeEstimation,
                          IntUnaryOperator frameToTimeFn) {
        _hasConstantFrameRate = hasConstantFrameRate;
        _requiresTimeEstimation = requiresTimeEstimation;
        _frameToTimeFn = frameToTimeFn;
    }


    public static FrameTimeInfo forConstantFrameRate(double fps, int startTime) {
        return new FrameTimeInfo(true, false, getTimeUsingFps(fps, startTime));
    }

    public static FrameTimeInfo forConstantFrameRateWithEstimatedTimes(double fps, int startTime) {
        return new FrameTimeInfo(true, true, getTimeUsingFps(fps, startTime));
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
        });
    }

    public static FrameTimeInfo forVariableFrameRateWithEstimatedTimes(double fps, int startTime) {
        return new FrameTimeInfo(false, true, getTimeUsingFps(fps, startTime));
    }

    private static IntUnaryOperator getTimeUsingFps(double fps, int startTime) {
        double msPerFrame = 1000 / fps;
        return frameIdx -> startTime + (int) (frameIdx * msPerFrame);
    }

    public boolean hasConstantFrameRate() {
        return _hasConstantFrameRate;
    }

    public boolean requiresTimeEstimation() {
        return _requiresTimeEstimation;
    }

    public int getFrameTimeMs(int frameIndex) {
        return _frameToTimeFn.applyAsInt(frameIndex);
    }
}
