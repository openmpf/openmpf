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
import java.util.function.LongConsumer;
import java.util.stream.LongStream;

public class FrameTimeInfoBuilder {

    private static final Logger log = LoggerFactory.getLogger(FrameTimeInfoBuilder.class);

    private FrameTimeInfoBuilder() {
    }


    public static FrameTimeInfo getFrameTimeInfo(Path mediaPath, double fps) throws IOException {
        var mediaInfoIsCfrResult = mediaInfoReportsConstantFrameRate(mediaPath);
        if (mediaInfoIsCfrResult.orElse(false)) {
            log.info("Determined that {} has a constant frame rate.", mediaPath);
            return FrameTimeInfo.forConstantFrameRate(fps, getStartTimeMs(mediaPath));
        }

        String[] command = {
                "ffprobe", "-hide_banner", "-select_streams", "v",
                "-show_entries", "frame=best_effort_timestamp",
                "-print_format", "default=noprint_wrappers=1:nokey=1", mediaPath.toString()
        };
        log.info("Getting PTS values using ffprobe with the following command: {}",
                 Arrays.toString(command));

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
            log.info("Determined that {} has a constant frame rate.", mediaPath);
        }
        else {
            log.info("Determined that {} has a variable frame rate.", mediaPath);
        }
        return timeInfo;
    }


    private static Optional<Boolean> mediaInfoReportsConstantFrameRate(Path mediaPath)
            throws IOException {
        String[] command = {
                "mediainfo", "--Output=Video;%FrameRate_Mode%", mediaPath.toString() };

        log.info("Checking for constant frame rate using mediainfo with the following command: {}",
                 Arrays.toString(command));

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
                log.warn(
                    "mediainfo was unable to determine if \"{}\" has a constant frame rate.",
                    mediaPath);
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


        public FrameTimeInfo build(Path mediaPath, double fps) throws IOException {
            if (_isInitialized && !_foundVariableDelta) {
                return FrameTimeInfo.forConstantFrameRate(fps, getStartTimeMs(mediaPath));
            }

            int[] timeBaseFraction = getTimeBase(mediaPath);
            int numerator = timeBaseFraction[0];
            // Time base is in seconds, but the output object uses milliseconds.
            int numeratorForMs = numerator * 1000;
            int denominator = timeBaseFraction[1];

            int[] frameTimes = _streamBuilder
                    .build()
                    .mapToInt(p -> (int) (p * numeratorForMs / denominator))
                    .toArray();
            boolean missingPts = fixNegativeTimes(frameTimes, fps);

            return FrameTimeInfo.forVariableFrameRate(fps, frameTimes, missingPts);
        }
    }


    private static boolean fixNegativeTimes(int[] frameTimes, double fps) {
        double msPerFrame = 1000 / fps;
        boolean foundNegativeValue = false;

        for (int i = 0; i < frameTimes.length; i++) {
            if (frameTimes[i] >= 0) {
                continue;
            }
            foundNegativeValue = true;
            if (i > 0 && i < frameTimes.length - 1) {
                int next = frameTimes[i + 1];
                if (next >= 0) {
                    frameTimes[i] = (frameTimes[i - 1] + next) / 2;
                    continue;
                }
            }
            if (i > 1) {
                int prevDelta = frameTimes[i - 1] - frameTimes[i - 2];
                frameTimes[i] = frameTimes[i - 1] + prevDelta;
                continue;
            }
            if (i > 0) {
                frameTimes[i] = (int) (frameTimes[i - 1] + msPerFrame);
                continue;
            }
            frameTimes[i] = (int) (i * msPerFrame);
        }
        return foundNegativeValue;
    }


    private static int[] getTimeBase(Path mediaPath) throws IOException {
        String[] command = {
                "ffprobe", "-hide_banner", "-select_streams", "v",
                "-show_entries", "stream=time_base",
                "-print_format", "default=noprint_wrappers=1:nokey=1", mediaPath.toString()
        };
        log.info("Getting time base using ffprobe with the following command: {}",
                 Arrays.toString(command));

        var process = new ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        process.getOutputStream().close();

        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            // Expected line format: 1001/30000
            var line = reader.readLine();
            var slashPos = line.indexOf('/');
            var numerator = Integer.parseInt(line, 0, slashPos, 10);
            var denominator = Integer.parseInt(line, slashPos + 1, line.length(), 10);
            return new int[] { numerator, denominator };
        }
    }


    private static int getStartTimeMs(Path mediaPath) throws IOException {
        String[] command = {
                "ffprobe", "-hide_banner", "-select_streams", "v",
                "-show_entries", "stream=start_time",
                "-print_format", "default=noprint_wrappers=1:nokey=1", mediaPath.toString()
        };
        log.info("Getting start time using ffprobe with the following command: {}",
                 Arrays.toString(command));

        var process = new ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        process.getOutputStream().close();
        String line;
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            line = reader.readLine();
        }
        return (int) (Double.parseDouble(line) * 1000);
    }
}
