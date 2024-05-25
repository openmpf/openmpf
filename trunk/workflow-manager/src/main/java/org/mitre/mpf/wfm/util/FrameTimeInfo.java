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
            Fraction fps, OptionalInt startTime, int frameCount) {
        return new FpsFrameTimeInfo(fps, startTime, true, OptionalInt.of(frameCount));
    }

    public static FrameTimeInfo forConstantFrameRate(
            double fps, OptionalInt startTime, int frameCount) {
        return new FpsFrameTimeInfo(fps, startTime, true, OptionalInt.of(frameCount));
    }


    public static FrameTimeInfo forVariableFrameRateWithEstimatedTimes(Fraction fps) {
        return new FpsFrameTimeInfo(fps, OptionalInt.empty(), false, OptionalInt.empty());
    }

    public static FrameTimeInfo forVariableFrameRateWithEstimatedTimes(double fps, int frameCount) {
        return new FpsFrameTimeInfo(fps, OptionalInt.empty(), false, OptionalInt.of(frameCount));
    }

    public static FrameTimeInfo forVariableFrameRate(
            Fraction fps, int[] timeStamps, boolean requiresTimeEstimation) {
        return new VfrFrameTimeInfo(fps, timeStamps, requiresTimeEstimation);
    }
}
