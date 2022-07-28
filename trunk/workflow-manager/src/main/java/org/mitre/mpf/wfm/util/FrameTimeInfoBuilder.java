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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.LongConsumer;
import java.util.stream.LongStream;

public class FrameTimeInfoBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(FrameTimeInfoBuilder.class);

    private static final String FFPROBE_THREADS = String.valueOf(
            Math.min(8, Runtime.getRuntime().availableProcessors()));

    private FrameTimeInfoBuilder() {
    }


    public static FrameTimeInfo getFrameTimeInfo(Path mediaPath, double fps) {
        var mediaInfoIsCfrResult = mediaInfoReportsConstantFrameRate(mediaPath);
        if (mediaInfoIsCfrResult.orElse(false)) {
            LOG.info("Determined that {} has a constant frame rate.", mediaPath);
            var optStartTimes = getStartTimeMs(mediaPath);
            return FrameTimeInfo.forConstantFrameRate(fps, optStartTimes.orElse(0),
                                                      optStartTimes.isEmpty());
        }

        String[] command = {
                "ffprobe", "-threads", FFPROBE_THREADS, "-hide_banner", "-select_streams", "v",
                "-show_entries", "frame=best_effort_timestamp",
                "-print_format", "default=noprint_wrappers=1:nokey=1", mediaPath.toString()
        };
        LOG.info("Getting PTS values using ffprobe with the following command: {}",
                 Arrays.toString(command));

        try {
            var process = new ProcessBuilder(command)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            process.getOutputStream().close();

            var ptsValuesBuilder = new PtsBuilder();

            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    ptsValuesBuilder.accept(line);
                }
            }

            var timeInfo = ptsValuesBuilder.build(mediaPath, fps);
            if (timeInfo.hasConstantFrameRate()) {
                LOG.info("Determined that {} has a constant frame rate.", mediaPath);
            }
            else {
                LOG.info("Determined that {} has a variable frame rate.", mediaPath);
            }
            return timeInfo;
        }
        catch (IOException e) {
            LOG.error(String.format(
                    "An error occurred while trying to get timestamps for %s: %s",
                    mediaPath, e.getMessage()), e);
            return FrameTimeInfo.forVariableFrameRateWithEstimatedTimes(fps);
        }
    }


    private static Optional<Boolean> mediaInfoReportsConstantFrameRate(Path mediaPath) {
        String[] command = {
                "mediainfo", "--Output=Video;%FrameRate_Mode%", mediaPath.toString() };

        LOG.info("Checking for constant frame rate using mediainfo with the following command: {}",
                 Arrays.toString(command));

        try {
            var process = new ProcessBuilder(command)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();

            String line;
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                line = Optional.ofNullable(reader.readLine())
                        .map(s -> s.strip().toUpperCase())
                        .orElse("");
            }

            switch (line) {
                case "CFR":
                    return Optional.of(true);
                case "VFR":
                    return Optional.of(false);
                default:
                    LOG.warn(
                            "mediainfo was unable to determine if \"{}\" has a constant frame rate.",
                            mediaPath);
                    return Optional.empty();
            }
        }
        catch (IOException e) {
            LOG.error(String.format(
                    "An error occurred while trying to determine if %s has a variable frame rate " +
                            "using mediainfo: %s", mediaPath, e.getMessage()), e);
            return Optional.empty();
        }
    }


    private static class PtsBuilder {
        private long _prevPts = -1;
        private long _delta = -1;
        private boolean _isInitialized;
        private boolean _foundVariableDelta;
        private final LongStream.Builder _streamBuilder = LongStream.builder();

        private LongConsumer _state = this::initPrev;

        public void accept(String line) {
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


        public FrameTimeInfo build(Path mediaPath, double fps) {
            if (_isInitialized && !_foundVariableDelta) {
                var optStartTimes = getStartTimeMs(mediaPath);
                return FrameTimeInfo.forConstantFrameRate(fps, optStartTimes.orElse(0),
                                                          optStartTimes.isEmpty());
            }

            int[] timeBaseFraction;
            try {
                timeBaseFraction = getTimeBase(mediaPath);
            }
            catch (IOException | NumberFormatException | IllegalStateException e) {
                LOG.error(String.format(
                        "An error occurred while trying to get time base for %s: %s",
                        mediaPath, e.getMessage()), e);
                return FrameTimeInfo.forVariableFrameRateWithEstimatedTimes(fps);
            }

            int numerator = timeBaseFraction[0];
            // Time base is in seconds, but the output object uses milliseconds.
            int numeratorForMs = numerator * 1000;
            int denominator = timeBaseFraction[1];

            int[] frameTimes = _streamBuilder
                    .build()
                    .mapToInt(p -> (int) (p * numeratorForMs / denominator))
                    .toArray();
            boolean requiresEstimation = estimateMissingTimes(frameTimes, fps);

            return FrameTimeInfo.forVariableFrameRate(fps, frameTimes, requiresEstimation);
        }
    }

    private static boolean estimateMissingTimes(int[] frameTimes, double fps) {
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
                int msPerFrame = (int) (1000 / fps);
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


    private static int[] getTimeBase(Path mediaPath) throws IOException {
        String[] command = {
                "ffprobe", "-hide_banner", "-select_streams", "v",
                "-show_entries", "stream=time_base",
                "-print_format", "default=noprint_wrappers=1:nokey=1", mediaPath.toString()
        };
        LOG.info("Getting time base using ffprobe with the following command: {}",
                 Arrays.toString(command));

        var process = new ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        process.getOutputStream().close();

        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            // Expected line format: 1001/30000
            var line = reader.readLine();
            if (line == null) {
                throw new IllegalStateException(String.format(
                        "Failed to get time base for %s because ffprobe produced no output.",
                        mediaPath));
            }
            var slashPos = line.indexOf('/');
            if (slashPos < 0) {
                throw new IllegalStateException(String.format(
                        "Failed to get time base for %s because ffprobe did not output " +
                                "a fraction as expected.",
                        mediaPath));

            }
            var numerator = Integer.parseInt(line, 0, slashPos, 10);
            var denominator = Integer.parseInt(line, slashPos + 1, line.length(), 10);
            return new int[] { numerator, denominator };
        }
    }


    private static OptionalInt getStartTimeMs(Path mediaPath) {
        // Uses -read_intervals %30 to get timestamps for the first 30 seconds of the video.
        // We really only need the very first timestamp, however %1 (1 second) doesn't work
        // for some videos. We use 30 seconds to over estimate the actual amount of time required.
        // It is unlikely that ffprobe will ever make it all the way to 30 seconds because we
        // close ffprobe's standard out after reading the first line causing ffprobe to exit.
        String[] command = {
                "ffprobe", "-read_intervals", "%30" ,"-hide_banner", "-select_streams", "v",
                "-show_entries", "frame=best_effort_timestamp_time",
                "-print_format", "default=noprint_wrappers=1:nokey=1", mediaPath.toString()
        };
        LOG.info("Getting start time using ffprobe with the following command: {}",
                 Arrays.toString(command));
        try {
            var process = new ProcessBuilder(command)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
            process.getOutputStream().close();
            String line;
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                line = reader.readLine();
            }
            if (line == null) {
                LOG.error("ffprobe produced no output when checking the start time of {}. " +
                                  "Assuming start time is 0.", mediaPath);
                return OptionalInt.empty();
            }
            return OptionalInt.of((int) (Double.parseDouble(line) * 1000));
        }
        catch (IOException | NumberFormatException e) {
            LOG.error(String.format(
                    "Assuming start time of %s is 0 because the following error occurred " +
                            "while checking the start time: %s",
                    mediaPath, e.getMessage()), e);
            return OptionalInt.empty();
        }
    }
}
