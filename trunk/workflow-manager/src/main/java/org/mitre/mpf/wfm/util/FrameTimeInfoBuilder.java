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


    private FrameTimeInfoBuilder() {
    }


    public static FrameTimeInfo getFrameTimeInfo(Path mediaPath, double fps) {
        var mediaInfoIsCfrResult = mediaInfoReportsConstantFrameRate(mediaPath);
        if (mediaInfoIsCfrResult.orElse(false)) {
            LOG.info("Determined that {} has a constant frame rate.", mediaPath);
            return getTimeInfoForConstantFrameRate(mediaPath, fps);
        }

        try {
            var timeInfo = new PtsBuilder(mediaPath).build(fps);
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
            return getEstimatedFrameTimeInfoAssumingVfr(mediaPath, fps);
        }
    }


    public static FrameTimeInfo getFrameTimeInfoAssumingVfr(Path mediaPath, double fps) {
        try {
            return new PtsBuilder(mediaPath).buildAssumeVfr(fps);
        }
        catch (IOException e) {
            LOG.error(String.format(
                    "An error occurred while trying to get timestamps for %s: %s",
                    mediaPath, e.getMessage()), e);
            return getEstimatedFrameTimeInfoAssumingVfr(mediaPath, fps);
        }
    }


    public static FrameTimeInfo getEstimatedTimes(Path mediaPath, double fps) {
        var mediaInfoIsCfrResult = mediaInfoReportsConstantFrameRate(mediaPath);
        if (mediaInfoIsCfrResult.orElse(false)) {
            LOG.info("Determined that {} has a constant frame rate.", mediaPath);
            return getTimeInfoForConstantFrameRate(mediaPath, fps);
        }

        try {
            var timeInfo = new PtsBuilder(mediaPath).buildEstimated(fps);
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
            return getEstimatedFrameTimeInfoAssumingVfr(mediaPath, fps);
        }
    }


    public static FrameTimeInfo getEstimatedFrameTimeInfoAssumingVfr(Path mediaPath, double fps) {
        return FrameTimeInfo.forVariableFrameRateWithEstimatedTimes(
                fps, getStartTimeMs(mediaPath).orElse(0));
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


    private static FrameTimeInfo getTimeInfoForConstantFrameRate(Path mediaPath, double fps) {
        OptionalInt optStartTime = getStartTimeMs(mediaPath);
        if (optStartTime.isPresent()) {
            return FrameTimeInfo.forConstantFrameRate(fps, optStartTime.getAsInt());
        }
        else {
            return FrameTimeInfo.forConstantFrameRateWithEstimatedTimes(fps, 0);
        }
    }



    private static class PtsBuilder {
        private static final String FFPROBE_THREADS = String.valueOf(
                Math.min(8, Runtime.getRuntime().availableProcessors()));

        private long _prevPts = -1;
        private long _delta = -1;
        private boolean _isInitialized;
        private boolean _foundVariableDelta;
        private final LongStream.Builder _streamBuilder = LongStream.builder();

        private LongConsumer _state = this::initPrev;

        private final Path _mediaPath;

        public PtsBuilder(Path mediaPath) throws IOException {
            _mediaPath = mediaPath;

            String[] command = {
                    "ffprobe", "-threads", FFPROBE_THREADS, "-hide_banner", "-select_streams", "v",
                    "-show_entries", "frame=best_effort_timestamp",
                    "-print_format", "default=noprint_wrappers=1:nokey=1", mediaPath.toString()
            };
            LOG.info("Getting PTS values using ffprobe with the following command: {}",
                     Arrays.toString(command));

            var process = new ProcessBuilder(command)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
            process.getOutputStream().close();

            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                reader.lines().forEachOrdered(this::accept);
            }
        }


        private void accept(String line) {
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


        public FrameTimeInfo build(double fps) {
            if (_isInitialized && !_foundVariableDelta) {
                return getTimeInfoForConstantFrameRate(_mediaPath, fps);
            }
            else {
                return buildAssumeVfr(fps);
            }
        }


        public FrameTimeInfo buildAssumeVfr(double fps) {
            int[] timeBaseFraction;
            try {
                timeBaseFraction = getTimeBase(_mediaPath);
            }
            catch (IOException | NumberFormatException | IllegalStateException e) {
                LOG.error(String.format(
                        "An error occurred while trying to get time base for %s: %s",
                        _mediaPath, e.getMessage()), e);
                return FrameTimeInfo.forVariableFrameRateWithEstimatedTimes(fps, 0);
            }

            int numerator = timeBaseFraction[0];
            // Time base is in seconds, but the output object uses milliseconds.
            int numeratorForMs = numerator * 1000;
            int denominator = timeBaseFraction[1];

            int[] frameTimes = _streamBuilder
                    .build()
                    .mapToInt(p -> (int) (p * numeratorForMs / denominator))
                    .toArray();
            boolean missingPts = estimateMissingTimes(frameTimes, fps);

            return FrameTimeInfo.forVariableFrameRate(fps, frameTimes, missingPts);
        }


        public FrameTimeInfo buildEstimated(double fps) {
            if (_isInitialized && !_foundVariableDelta) {
                return getTimeInfoForConstantFrameRate(_mediaPath, fps);
            }
            else {
                return FrameTimeInfo.forVariableFrameRateWithEstimatedTimes(
                        fps, getStartTimeMs(_mediaPath).orElse(0));
            }
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
                frameTimes[1] = (frameTimes[0] + frameTimes[2]) / 2;
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
                frameTimes[i] = (frameTimes[i - 1] + frameTimes[i + 1]) / 2;
            }
            else {
                int prevDelta = frameTimes[i - 1] - frameTimes[i - 2];
                frameTimes[i] = frameTimes[i - 1] + prevDelta;
            }
        }

        // Last frame needs special handling because it doesn't have a following value to use to
        // calculate the average PTS value.
        if (frameTimes.length > 2 && frameTimes[frameTimes.length - 1] <= 0) {
            requiredEstimation = true;
            int prevDelta = frameTimes[frameTimes.length - 2] - frameTimes[frameTimes.length - 3];
            frameTimes[frameTimes.length - 1] = frameTimes[frameTimes.length - 2] + prevDelta;
        }

        return requiredEstimation;
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
        String[] command = {
                "ffprobe", "-read_intervals", "%30" ,"-hide_banner", "-select_streams", "v",
                "-show_entries", "frame=best_effort_timestamp_time",
                "-print_format", "default=noprint_wrappers=1:nokey=1", mediaPath.toString()
        };
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
            LOG.error("Assuming start time of %s is 0 because the following error occurred " +
                              "while checking the start time: " + e.getMessage(), e);
            return OptionalInt.empty();
        }
    }
}
