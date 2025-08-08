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

import org.javasimon.SimonManager;
import org.javasimon.Split;
import org.mitre.mpf.wfm.camel.operations.mediainspection.FfprobeMetadata;
import org.mitre.mpf.wfm.camel.operations.mediainspection.Fraction;
import org.mitre.mpf.wfm.camel.operations.mediainspection.MediaInspectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.OptionalInt;
import java.util.function.LongConsumer;
import java.util.stream.LongStream;

public class FrameTimeInfoBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(FrameTimeInfoBuilder.class);

    private static final String FFPROBE_THREADS = String.valueOf(
            Math.min(8, Runtime.getRuntime().availableProcessors()));

    private FrameTimeInfoBuilder() {
    }


    public static FrameTimeInfo getFrameTimeInfo(
                Path mediaPath, FfprobeMetadata.Video ffprobeMetadata, String mimeType) {
        String[] command = {
                "ffprobe", "-threads", FFPROBE_THREADS, "-hide_banner", "-select_streams", "v",
                "-show_entries", "frame=best_effort_timestamp",
                "-print_format", "default=noprint_wrappers=1:nokey=1", mediaPath.toString()
        };
        LOG.info("Getting PTS values using ffprobe with the following command: {}",
                 Arrays.toString(command));

        try {
            PtsBuilder ptsValuesBuilder;
            var split = startFfprobePtsStopwatch();
            try (split) {
                ptsValuesBuilder = getPtsFromFfprobe(command, mediaPath);
            }
            logStats(split, mediaPath, mimeType, ptsValuesBuilder.getFrameCount());

            var timeInfo = ptsValuesBuilder.build(
                    ffprobeMetadata.fps(), ffprobeMetadata.timeBase());
            if (timeInfo.hasConstantFrameRate()) {
                LOG.info("Determined that {} has a constant frame rate.", mediaPath);
            }
            else {
                LOG.info("Determined that {} has a variable frame rate.", mediaPath);
            }
            return timeInfo;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        catch (IOException e) {
            throw new MediaInspectionException(
                    "An error occurred while trying to get timestamps for %s: %s"
                    .formatted(mediaPath, e.getMessage()),
                    e);
        }
    }

    private static PtsBuilder getPtsFromFfprobe(
                String[] command, Path mediaPath) throws IOException, InterruptedException {
        var ptsValuesBuilder = new PtsBuilder();
        var process = new ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        process.getOutputStream().close();

        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                ptsValuesBuilder.accept(line);
            }
        }
        if (process.waitFor() != 0) {
            throw new MediaInspectionException(
                "Failed to get timestamps for \"%s\" because ffprobe exited with status %s"
                .formatted(mediaPath, process.exitValue()));
        }
        return ptsValuesBuilder;
    }


    private static Split startFfprobePtsStopwatch() {
        return SimonManager.getStopwatch(FrameTimeInfoBuilder.class.getName() + ".ffprobePts")
            .start();
    }

    private static void logStats(Split split, Path mediaPath, String mimeType, int frameCount) {
        var millis = Duration.ofNanos(split.runningFor()).toMillis();
        LOG.info("getFrameTimeInfo [stopwatch: {}, media: {}, mime type: {}, frame count: {}]",
                    millis,
                    mediaPath,
                    mimeType,
                    frameCount);
    }


    private static class PtsBuilder {
        private int _frameCount;
        private long _firstPts = 0;
        private long _prevPts = -1;
        private long _delta = -1;
        private boolean _isInitialized;
        private boolean _foundVariableDelta;
        private final LongStream.Builder _streamBuilder = LongStream.builder();

        private LongConsumer _state = this::initPrev;

        public void accept(String line) {
            _frameCount++;
            long pts;
            try {
                pts = Long.parseLong(line);
            }
            catch (NumberFormatException e) {
                _foundVariableDelta = true;
                // Negative pts values will be estimated later when we are able to check the
                // surrounding pts values.
                pts = -1;
                _state = this::processRemaining;
            }
            _streamBuilder.accept(pts);
            _state.accept(pts);
            _prevPts = pts;
        }

        private void initPrev(long pts) {
            _firstPts = pts;
            _state = this::initDelta;
        }

        private void initDelta(long pts) {
            _delta = pts - _prevPts;
            _state = this::checkDeltas;
        }

        private void checkDeltas(long pts) {
            _isInitialized = true;
            long currDelta = pts - _prevPts;
            if (_delta != currDelta) {
                _foundVariableDelta = true;
                _state = this::processRemaining;
            }
        }

        private void processRemaining(long pts) {
        }

        public FrameTimeInfo build(Fraction fps, Fraction timeBase) {
            var timeBaseMs = timeBase.mul(1000);

            if (_isInitialized && !_foundVariableDelta) {
                int startTime = (int) timeBaseMs.mul(_firstPts).toDouble();
                int frameCount = (int) _streamBuilder.build().count();
                return FrameTimeInfo.forConstantFrameRate(
                        fps, OptionalInt.of(startTime), frameCount);
            }

            int[] frameTimes = _streamBuilder
                    .build()
                    .mapToInt(p -> (int) (timeBaseMs.mul(p).toDouble()))
                    .toArray();
            boolean requiresEstimation = estimateMissingTimes(frameTimes, fps);
            return FrameTimeInfo.forVariableFrameRate(fps, frameTimes, requiresEstimation);
        }

        public int getFrameCount() {
            return _frameCount;
        }
    }


    private static boolean estimateMissingTimes(int[] frameTimes, Fraction fps) {
        if (frameTimes.length == 0) {
            return true;
        }

        boolean requiredEstimation;
        // First entry requires special handling because it has no previous value to use for the
        // estimation.
        if (frameTimes[0] < 0) {
            requiredEstimation = true;
            frameTimes[0] = 0;
        }
        else {
            requiredEstimation = false;
        }

        // Second entry requires special handling because it can't calculate the previous delta
        // with only a single previous value.
        if (frameTimes.length > 1 && frameTimes[1] < 0) {
            requiredEstimation = true;
            if (frameTimes.length > 2 && frameTimes[2] > 0) {
                estimateWithAverage(frameTimes, 1);
            }
            else {
                int msPerFrame = (int) (1000 * fps.denominator() / fps.numerator());
                frameTimes[1] = frameTimes[0] + msPerFrame;
            }
        }

        for (int i = 2; i < (frameTimes.length - 1); i++) {
            if (frameTimes[i] >= 0) {
                continue;
            }
            requiredEstimation = true;

            if (frameTimes[i + 1] > 0) {
                estimateWithAverage(frameTimes, i);
            }
            else {
                estimateWithPrevDelta(frameTimes, i);
            }
        }

        // Last frame needs special handling (when it isn't the first or second frame) because it
        // doesn't have a following value to use to calculate the average PTS value.
        if (frameTimes.length > 2 && frameTimes[frameTimes.length - 1] <= 0) {
            requiredEstimation = true;
            estimateWithPrevDelta(frameTimes, frameTimes.length - 1);
        }

        return requiredEstimation;
    }


    private static void estimateWithAverage(int[] frameTimes, int idx) {
        frameTimes[idx] = (frameTimes[idx - 1] + frameTimes[idx + 1]) / 2;
    }

    private static void estimateWithPrevDelta(int[] frameTimes, int idx) {
        int prevDelta = frameTimes[idx - 1] - frameTimes[idx - 2];
        frameTimes[idx] = frameTimes[idx - 1] + prevDelta;
    }
}
