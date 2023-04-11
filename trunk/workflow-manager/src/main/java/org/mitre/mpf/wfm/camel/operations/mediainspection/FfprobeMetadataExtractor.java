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

package org.mitre.mpf.wfm.camel.operations.mediainspection;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.mitre.mpf.wfm.util.ThreadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


@Component
public class FfprobeMetadataExtractor {

    private static final Logger LOG = LoggerFactory.getLogger(FfprobeMetadataExtractor.class);

    private final ObjectMapper _objectMapper;

    @Inject
    public FfprobeMetadataExtractor(ObjectMapper objectMapper) {
        _objectMapper = objectMapper;
    }


    public FfprobeMetadata getAudioVideoMetadata(Path mediaPath) {
        JsonNode ffprobeJson;
        try {
            ffprobeJson = runFfprobeOnAudioOrVideo(mediaPath);
        }
        catch (MediaInspectionException e) {
            return new FfprobeMetadata(Optional.empty(), Optional.empty());
        }

        var optVideoStream = getStreamType(ffprobeJson, "video");
        var optAudioStream = getStreamType(ffprobeJson, "audio");
        if (optVideoStream.isEmpty() && optAudioStream.isEmpty()) {
            return new FfprobeMetadata(Optional.empty(), Optional.empty());
        }


        var videoTimeBase = getTimeBase(mediaPath, optVideoStream);
        var audioTimeBase = getTimeBase(mediaPath, optAudioStream);

        OptionalLong optDurationMs;
        if (optVideoStream.isPresent() && optAudioStream.isPresent()) {
            optDurationMs = getDurationMs(
                    ffprobeJson,
                    optVideoStream.get(), videoTimeBase,
                    optAudioStream.get(), audioTimeBase);
        }
        else if (optVideoStream.isPresent()) {
            optDurationMs = getDurationMs(ffprobeJson, optVideoStream.get(), videoTimeBase);
        }
        else {
            optDurationMs = getDurationMs(ffprobeJson, optAudioStream.get(), audioTimeBase);
        }


        Optional<FfprobeMetadata.Video> optVideoMetadata = Optional.empty();
        if (optVideoStream.isPresent()) {
            var videoStream = optVideoStream.get();
            assert videoTimeBase != null;

            int width = toInt(videoStream.get("width"))
                    .orElseThrow(() -> new MediaInspectionException(
                        "Could not get width for video stream of \"%s\".".formatted(mediaPath)));
            int height = toInt(videoStream.get("height"))
                    .orElseThrow(() -> new MediaInspectionException(
                        "Could not get height for video stream of \"%s\".".formatted(mediaPath)));

            var fps = getFps(videoStream, mediaPath);
            var optFrameCount = getFrameCount(ffprobeJson, videoStream, videoTimeBase, fps);
            double rotation = getRotation(videoStream);
            optVideoMetadata = Optional.of(new FfprobeMetadata.Video(
                    width, height, fps, optFrameCount, optDurationMs, rotation, videoTimeBase));
        }

        var optAudioMetadata = optAudioStream
                .map(n -> new FfprobeMetadata.Audio(optDurationMs));

        return new FfprobeMetadata(optVideoMetadata, optAudioMetadata);
    }


    public FfprobeMetadata.Image getImageMetadata(Path mediaPath) {
        var ffProbeOutput = runFfprobeOnImage(mediaPath);
        var frames = ffProbeOutput.get("frames");
        if (frames == null || frames.isEmpty()) {
            throw new MediaInspectionException(
                "ffprobe did not produce output for \"%s\".".formatted(mediaPath));
        }
        var imgNode = frames.get(0);
        var width = toInt(imgNode.get("width"));
        var height = toInt(imgNode.get("height"));

        var exifOrientation = Optional.ofNullable(imgNode.get("tags"))
            .map(t -> toInt(t.get("Orientation")))
            .orElseGet(OptionalInt::empty);

        return new FfprobeMetadata.Image(width, height, exifOrientation);
    }


    private Optional<JsonNode> getStreamType(JsonNode ffprobeJson, String type) {
        var streams = ffprobeJson.get("streams");
        for (var stream : streams) {
            if (type.equals(stream.get("codec_type").asText())) {
                return Optional.of(stream);
            }
        }
        return Optional.empty();
    }


    // In most media files, times are not represented using seconds. In order to convert those
    // times to seconds, you must multiply the times by the file's time base.
    // If, for example, a file used milliseconds for its times, the time base would be 1/1000.
    private Fraction getTimeBase(Path mediaPath, Optional<JsonNode> optStream) {
        try {
            return optStream.map(s -> s.get("time_base").asText())
                    .map(Fraction::parse)
                    .orElse(null);
        }
        catch (NumberFormatException e) {
            var streamType = optStream.map(s -> s.get("codec_type"))
                    .map(t -> t.asText())
                    .orElse("");
            throw new MediaInspectionException(
                    "Could not time base for %s stream of \"%s\" due to: %s"
                    .formatted(streamType, mediaPath, e), e);
        }
    }


    private OptionalLong getDurationMs(
            JsonNode ffprobeOutput,
            JsonNode videoStream, Fraction videoTimeBase,
            JsonNode audioStream, Fraction audioTimeBase) {

        var optVideoDurationTs = toLong(videoStream.get("duration_ts"));
        var optAudioDurationTs = toLong(audioStream.get("duration_ts"));
        if (optVideoDurationTs.isEmpty() || optAudioDurationTs.isEmpty()) {
            return getEstimatedDurationSec(ffprobeOutput)
                .stream()
                .mapToLong(sec -> (long) Math.ceil(sec * 1000))
                .findAny();
        }

        long videoStartPts = toLong(videoStream.get("start_pts")).orElse(0);
        var videoStartSec = videoTimeBase.mul(videoStartPts);
        var videoEndTimeSec = videoTimeBase.mul(videoStartPts + optVideoDurationTs.getAsLong());

        long audioStartPts = toLong(audioStream.get("start_pts")).orElse(0);
        var audioStartSec = audioTimeBase.mul(audioStartPts);
        var audioEndTimeSec = audioTimeBase.mul(audioStartPts + optAudioDurationTs.getAsLong());

        var startSec = videoStartSec.min(audioStartSec);
        var endSec = videoEndTimeSec.max(audioEndTimeSec);
        var durationSec = endSec.sub(startSec);
        return OptionalLong.of(durationSec.mul(1000).roundUp());
    }


    private OptionalLong getDurationMs(JsonNode ffprobeOutput, JsonNode stream, Fraction timeBase) {
        var optDurationTs = toLong(stream.get("duration_ts"));
        if (optDurationTs.isPresent()) {
            var msFraction = timeBase.mul(optDurationTs.getAsLong()).mul(1000);
            return OptionalLong.of(msFraction.roundUp());
        }
        else {
            return getEstimatedDurationSec(ffprobeOutput)
                    .stream()
                    .mapToLong(sec -> (long) Math.ceil(sec * 1000))
                    .findAny();
        }
    }

    private OptionalDouble getEstimatedDurationSec(JsonNode ffprobeOutput) {
        return toDouble(ffprobeOutput.path("format").path("duration"));
    }


    private Fraction getFps(JsonNode videoStream, Path mediaPath) {
        try {
            var avgFrameRate = Optional.ofNullable(videoStream.get("avg_frame_rate"))
                .map(n -> n.asText(null))
                .filter(s -> !s.equals("0/0"))
                .map(Fraction::parse);
            if (avgFrameRate.isPresent()) {
                return avgFrameRate.get();
            }
        }
        catch (NumberFormatException ignored) {
        }

        try {
            return Optional.ofNullable(videoStream.get("r_frame_rate"))
                .map(n -> n.asText(null))
                .filter(s -> !s.equals("0/0"))
                .map(Fraction::parse)
                .orElseThrow(() -> new MediaInspectionException(
                    "ffprobe did not output the frame rate for \"%s\".".formatted(mediaPath)));
        }
        catch (NumberFormatException e) {
            throw new MediaInspectionException(
                "Could not get frame rate for video stream of \"%s\" due to: %s"
                .formatted(mediaPath, e), e);
        }
    }

    private OptionalLong getFrameCount(
            JsonNode ffprobeOutput, JsonNode videoStream, Fraction timeBase, Fraction fps) {
        var nbFrames = toLong(videoStream.get("nb_frames"));
        if (nbFrames.isPresent()) {
            return nbFrames;
        }

        var optDurationTs = toLong(videoStream.get("duration_ts"));
        if (optDurationTs.isPresent()) {
            var framesPerPts = fps.mul(timeBase);
            var frameCount = framesPerPts.mul(optDurationTs.getAsLong());
            return OptionalLong.of(frameCount.roundUp());
        }

        var optTotalDurationSec = getEstimatedDurationSec(ffprobeOutput);
        if (optTotalDurationSec.isEmpty()) {
            return OptionalLong.empty();
        }
        long videoStartPts = toLong(videoStream.get("start_pts")).orElse(0);
        var videoStartSec = timeBase.mul(videoStartPts);
        double videoStreamDuration = optTotalDurationSec.getAsDouble() - videoStartSec.toDouble();
        long frameCount = (long) (videoStreamDuration * fps.numerator() / fps.denominator());
        return OptionalLong.of(frameCount);
    }


    private static double getRotation(JsonNode videoStream) {
        var sideDataList = videoStream.get("side_data_list");
        if (sideDataList != null) {
            for (var sideData : sideDataList) {
                var sideDataType = sideData.get("side_data_type");
                if (sideDataType != null && "Display Matrix".equals(sideDataType.asText())) {
                    var rotation = toDouble(sideData.get("rotation"));
                    if (rotation.isPresent()) {
                        return normalizeRotation(360 - rotation.getAsDouble());
                    }
                }
            }
        }

        return Optional.ofNullable(videoStream.get("tags"))
            .map(t -> t.get("rotate"))
            .map(r -> toDouble(r).orElse(0))
            .map(d -> normalizeRotation(d))
            .orElse(0.0);
    }


    private static double normalizeRotation(double rotation) {
        if (0 <= rotation && rotation < 360) {
            return rotation;
        }
        rotation = rotation % 360;
        if (rotation >= 0) {
            return rotation;
        }
        return 360 + rotation;
    }


    private static OptionalDouble toDouble(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return OptionalDouble.empty();
        }
        if (node.isNumber()) {
            return OptionalDouble.of(node.asDouble());
        }
        if (!node.isTextual()) {
            return OptionalDouble.empty();
        }
        try {
            var text = node.asText().strip();
            return OptionalDouble.of(Double.parseDouble(text));
        }
        catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }

    private OptionalLong toLong(JsonNode node) {
        if (node == null) {
            return OptionalLong.empty();
        }
        if (node.isLong() || node.isInt()) {
            return OptionalLong.of(node.asLong());
        }
        if (!node.isTextual()) {
            return OptionalLong.empty();
        }
        try {
            var text = node.asText().strip();
            return OptionalLong.of(Long.parseLong(text));
        }
        catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    private static OptionalInt toInt(JsonNode node) {
        if (node == null) {
            return OptionalInt.empty();
        }
        if (node.isInt()) {
            return OptionalInt.of(node.asInt());
        }
        if (!node.isTextual()) {
            return OptionalInt.empty();
        }
        try {
            var text = node.asText().strip();
            return OptionalInt.of(Integer.parseInt(text));
        }
        catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }


    private JsonNode runFfprobeOnImage(Path mediaPath) {
        var command = new String[] {
            "ffprobe", "-hide_banner", "-show_frames", "-print_format", "json",
            mediaPath.toString() };
        return runFfprobe(command, mediaPath);
    }


    private JsonNode runFfprobeOnAudioOrVideo(Path mediaPath) {
        var command = new String[] {
            "ffprobe", "-hide_banner", "-show_streams", "-show_format", "-print_format", "json",
            mediaPath.toString() };
        return runFfprobe(command, mediaPath);
    }


    private JsonNode runFfprobe(String[] command, Path mediaPath) {
        if (LOG.isInfoEnabled()) {
            LOG.info("Getting ffmpeg metadata with the following command: {}",
                    String.join(" ", command));
        }

        Process ffprobeProcess;
        CompletableFuture<String> stdErrorFuture;
        try {
            ffprobeProcess = new ProcessBuilder(command).start();
            stdErrorFuture = ThreadUtil.callAsync(() -> collectStdError(ffprobeProcess));
            ffprobeProcess.getOutputStream().close();
        }
        catch (IOException e) {
            throw new MediaInspectionException("Failed to start ffprobe due to: " + e, e);
        }

        try (var stdout = ffprobeProcess.getInputStream()) {
            var output = _objectMapper.readTree(stdout);
            int exitCode = ffprobeProcess.waitFor();
            if (exitCode != 0) {
                var message = "ffprobe returned exit code %s on \"%s\"".formatted(exitCode, mediaPath);
                logFfprobeStdError(message, stdErrorFuture);
                throw new MediaInspectionException(message);
            }
            return output;
        }
        catch (IOException e) {
            var message = "Running ffprobe on \"%s\" failed due to: " + e;
            logFfprobeStdError(message, stdErrorFuture);
            throw new MediaInspectionException(message, e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private String collectStdError(Process process) {
        return process.errorReader()
            .lines()
            .collect(Collectors.joining("\n"));
    }

    private static void logFfprobeStdError(String message, CompletableFuture<String> stdErrorFuture) {
        try {
            var stdError = stdErrorFuture.join();
            LOG.error("{} and the following was outputted on stderr: {}", message, stdError);
        }
        catch (CompletionException ignored) {
            // We only look at ffprobe's stderr when we already failed, so we should throw the
            // original exception instead of the CompletionException here.
        }
    }
}
