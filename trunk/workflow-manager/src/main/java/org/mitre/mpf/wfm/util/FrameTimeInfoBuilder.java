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

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.OptionalInt;
import java.util.function.LongToIntFunction;

import org.javasimon.SimonManager;
import org.javasimon.Split;
import org.mitre.mpf.pts.PtsExtractor;
import org.mitre.mpf.pts.PtsExtractorJniException;
import org.mitre.mpf.pts.PtsResult;
import org.mitre.mpf.wfm.camel.operations.mediainspection.FfprobeMetadata;
import org.mitre.mpf.wfm.camel.operations.mediainspection.MediaInspectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FrameTimeInfoBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(FrameTimeInfoBuilder.class);

    private FrameTimeInfoBuilder() {
    }

    public static FrameTimeInfo getFrameTimeInfo(
            Path mediaPath, FfprobeMetadata.Video ffprobeMetadata, String mimeType) {
        try {
            var split = startStopwatch();
            FrameTimeInfo timeInfo;
            try (split) {
                timeInfo = getFrameTimeInfo(mediaPath, ffprobeMetadata);
            }
            logStats(split, mediaPath, mimeType, timeInfo.getExactFrameCount().orElse(-1));
            if (timeInfo.hasConstantFrameRate()) {
                LOG.info("Determined that {} has a constant frame rate.", mediaPath);
            }
            else {
                LOG.info("Determined that {} has a variable frame rate.", mediaPath);
            }
            return timeInfo;

        }
        catch (PtsExtractorJniException e) {
            throw new MediaInspectionException(
                    "An error occurred while trying to get timestamps for %s: %s"
                    .formatted(mediaPath, e.getMessage()),
                    e);
        }
    }

    private static FrameTimeInfo getFrameTimeInfo(
            Path mediaPath, FfprobeMetadata.Video ffprobeMetadata) throws PtsExtractorJniException {
        long toMsNumerator = ffprobeMetadata.timeBase().numerator() * 1000;
        long toMsDenominator = ffprobeMetadata.timeBase().denominator();
        LongToIntFunction toMillis = p -> (int)(p * toMsNumerator / toMsDenominator);

        var ptsResult = PtsExtractor.getPts(mediaPath);
        var ptsValues = ptsResult.ptsValues();
        if (isCfr(ptsResult) && ptsValues.length != 0) {
            int startTime = toMillis.applyAsInt(ptsValues[0]);
            int frameCount = ptsValues.length;
            return FrameTimeInfo.forConstantFrameRate(
                    ffprobeMetadata.fps(),
                    OptionalInt.of(startTime), frameCount);
        }

        int[] frameTimes = Arrays.stream(ptsValues)
            .mapToInt(toMillis)
            .toArray();
        return FrameTimeInfo.forVariableFrameRate(
                ffprobeMetadata.fps(), frameTimes, ptsResult.estimated());
    }

    private static boolean isCfr(PtsResult ptsResult) {
        var ptsValues = ptsResult.ptsValues();
        if (ptsValues.length < 3) {
            return true;
        }
        long delta = ptsValues[1] - ptsValues[0];
        for (int i = 2; i < ptsValues.length; i++) {
            long currDelta = ptsValues[i] - ptsValues[i - 1];
            if (currDelta != delta) {
                return false;
            }
        }
        return true;
    }

    private static Split startStopwatch() {
        return SimonManager.getStopwatch(FrameTimeInfoBuilder.class.getName() + ".getFrameTimeInfo")
            .start();
    }

    private static void logStats(Split split, Path mediaPath, String mimeType, int frameCount) {
        var millis = Duration.ofNanos(split.runningFor()).toMillis();
        LOG.info("getFrameTimeInfo [stopwatch: {} msec, media: {}, mime type: {}, frame count: {}]",
                    millis,
                    mediaPath,
                    mimeType,
                    frameCount);
    }
}
