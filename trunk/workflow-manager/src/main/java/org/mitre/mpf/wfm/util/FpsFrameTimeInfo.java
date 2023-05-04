/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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

import java.util.OptionalInt;

import org.mitre.mpf.wfm.camel.operations.mediainspection.Fraction;

public class FpsFrameTimeInfo implements FrameTimeInfo {

    private final FpsWrapper _fpsWrapper;

    private final boolean _requiresTimeEstimation;

    private final int _startTime;

    private final boolean _hasConstantFrameRate;

    private final OptionalInt _exactFrameCount;

    public FpsFrameTimeInfo(
            double fps,
            OptionalInt optStartTime,
            boolean hasConstantFrameRate,
            OptionalInt exactFrameCount) {
        this(getFpsWrapper(fps), optStartTime, hasConstantFrameRate, exactFrameCount);
    }

    public FpsFrameTimeInfo(
            Fraction fps,
            OptionalInt optStartTime,
            boolean hasConstantFrameRate,
            OptionalInt exactFrameCount) {
        this(getFpsWrapper(fps), optStartTime, hasConstantFrameRate, exactFrameCount);
    }

    private FpsFrameTimeInfo(
            FpsWrapper fps,
            OptionalInt optStartTime,
            boolean hasConstantFrameRate,
            OptionalInt exactFrameCount) {
        _fpsWrapper = fps;
        _requiresTimeEstimation = optStartTime.isEmpty();
        _startTime = optStartTime.orElse(0);
        _hasConstantFrameRate = hasConstantFrameRate;
        _exactFrameCount = exactFrameCount;
    }

    @Override
    public boolean hasConstantFrameRate() {
        return _hasConstantFrameRate;
    }

    @Override
    public boolean requiresTimeEstimation() {
        return _requiresTimeEstimation;
    }

    @Override
    public int getTimeMsFromFrame(int frameIndex) {
        return _startTime + (int) _fpsWrapper.getTimeMsFromFrame(frameIndex);
    }


    @Override
    public int getFrameFromTimeMs(int timeMs) {
        if (timeMs > _startTime) {
            int msSinceStart = timeMs - _startTime;
            return (int) _fpsWrapper.getFrameFromTimeMs(msSinceStart);
        }
        else {
            return 0;
        }
    }


    @Override
    public OptionalInt getExactFrameCount() {
        return _exactFrameCount;
    }


    @Override
    public OptionalInt getEstimatedDuration() {
        return _exactFrameCount.stream()
            .map(fc -> (int) Math.ceil(_fpsWrapper.getTimeMsFromFrame(fc)))
            .findAny();
    }



    private static interface FpsWrapper {
        double getTimeMsFromFrame(int frameIndex);
        double getFrameFromTimeMs(int timeMs);
    }

    private static FpsWrapper getFpsWrapper(Fraction fps) {
        var framesPerMs = fps.mul(new Fraction(1, 1000));
        var msPerFrame = framesPerMs.invert();

        return new FpsWrapper() {
            public double getTimeMsFromFrame(int frameIndex) {
                return msPerFrame.mul(frameIndex).toDouble();
            }

            @Override
            public double getFrameFromTimeMs(int timeMs) {
                return framesPerMs.mul(timeMs).toDouble();
            }
        };
    }

    private static FpsWrapper getFpsWrapper(double fps) {
        var framesPerMs = fps / 1000;
        var msPerFrame = 1000 / fps;

        return new FpsWrapper() {
            public double getTimeMsFromFrame(int frameIndex) {
                return msPerFrame * frameIndex;
            }

            @Override
            public double getFrameFromTimeMs(int timeMs) {
                return framesPerMs * timeMs;
            }
        };
    }
}
