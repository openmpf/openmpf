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


package org.mitre.mpf.wfm.util;

import java.util.Arrays;
import java.util.OptionalInt;

import org.mitre.mpf.wfm.camel.operations.mediainspection.Fraction;

public class VfrFrameTimeInfo implements FrameTimeInfo {

    private final Fraction _fps;

    private final int[] _timeStamps;

    private final boolean _requiresTimeEstimation;

    public VfrFrameTimeInfo(Fraction fps, int[] timeStamps, boolean requiresTimeEstimation) {
        _fps = fps;
        _timeStamps = timeStamps;
        _requiresTimeEstimation = requiresTimeEstimation;
    }


    @Override
    public boolean hasConstantFrameRate() {
        return false;
    }

    @Override
    public boolean requiresTimeEstimation() {
        return _requiresTimeEstimation;
    }

    @Override
    public int getTimeMsFromFrame(int frameIndex) {
        try {
            return _timeStamps[frameIndex];
        }
        catch (ArrayIndexOutOfBoundsException e) {
            int startTime = _timeStamps.length > 0 ? _timeStamps[0] : 0;
            var msPerFrame = _fps.invert().mul(1000);
            return startTime + (int) msPerFrame.mul(frameIndex).toDouble();
        }
    }

    @Override
    public int getFrameFromTimeMs(int timeMs) {
        int rv = Arrays.binarySearch(_timeStamps, timeMs);
        if (rv >= 0) {
            return rv;
        }
        else {
            int firstGreaterIdx = -rv - 1;
            return Math.max(firstGreaterIdx - 1, 0);
        }
    }

    @Override
    public OptionalInt getExactFrameCount() {
        return OptionalInt.of(_timeStamps.length);
    }

    @Override
    public OptionalInt getEstimatedDuration() {
        if (_timeStamps.length == 0) {
            return OptionalInt.of(0);
        }
        if (_timeStamps.length == 1) {
            return OptionalInt.of((int) _fps.invert().mul(1000).roundUp());
        }
        int lastFrameTime = _timeStamps[_timeStamps.length - 1];
        int secondLastFrameTime = _timeStamps[_timeStamps.length - 2];
        int prevTimeDiff = lastFrameTime - secondLastFrameTime;
        int endTime = lastFrameTime + prevTimeDiff;
        return OptionalInt.of(endTime - _timeStamps[0]);
    }
}
