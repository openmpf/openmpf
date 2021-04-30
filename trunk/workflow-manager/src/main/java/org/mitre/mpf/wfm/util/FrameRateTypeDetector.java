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
import java.util.regex.Pattern;

public class FrameRateTypeDetector {
    private static final Logger log = LoggerFactory.getLogger(FrameRateTypeDetector.class);

    private FrameRateTypeDetector() {
    }


    public static boolean hasConstantFrameRate(Path mediaPath) throws IOException {
        var optHasCfr = mediaInfoReportsConstantFrameRate(mediaPath);
        if (optHasCfr.isEmpty()) {
            optHasCfr = ffmpegReportsConstantFrameRate(mediaPath);
        }

        if (optHasCfr.isEmpty()) {
            log.warn("Unable to determine if {} has a constant frame rate. Assuming it does not.",
                     mediaPath);
            return false;
        }
        else if (optHasCfr.get()) {
            log.info("Determined that {} has a constant frame rate.", mediaPath);
            return true;
        }
        else {
            log.info("Determined that {} has a variable frame rate.", mediaPath);
            return false;
        }
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


    // Example of constant frame rate:
    // [Parsed_vfrdet_0 @ 0x758c700] VFR:0.000000 (0/225)
    // Example of variable frame rate:
    // [Parsed_vfrdet_0 @ 0x764a4c0] VFR:0.010914 (218/19757) min: 1 max: 423 avg: 320
    private static final Pattern FFMPEG_CFR_PATTERN = Pattern.compile("VFR:0+\\.0+(?:\\D|$)");

    private static Optional<Boolean> ffmpegReportsConstantFrameRate(Path mediaPath)
            throws IOException {
        String[] command = {
                "ffmpeg", "-i", mediaPath.toString(),
                "-vf", "vfrdet", "-an", "-f", "null", "-",
                "-nostats", "-hide_banner"
        };
        log.info("Checking for constant frame rate using ffmpeg with the following command: {}",
                 Arrays.toString(command));

        var process = new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .start();

        try (var reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("[Parsed_vfrdet")) {
                    return Optional.of(FFMPEG_CFR_PATTERN.matcher(line).find());
                }
                else if (line.contains("No such filter: 'vfrdet'")) {
                    throw new IllegalStateException(
                            "The currently installed version of ffmpeg does not support the " +
                                    "\"vfrdet\" filter. ffmpeg needs to be updated to a " +
                                    "version supporting the \"vfrdet\" filter.");
                }
            }
        }
        return Optional.empty();
    }
}
