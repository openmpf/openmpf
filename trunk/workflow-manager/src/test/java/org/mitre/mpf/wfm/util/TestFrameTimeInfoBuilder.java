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

import org.junit.Test;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.camel.operations.mediainspection.FfprobeMetadata;
import org.mitre.mpf.wfm.camel.operations.mediainspection.Fraction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.OptionalLong;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

public class TestFrameTimeInfoBuilder {

    private static final Fraction NTSC_FPS = new Fraction(30_000, 1_001);

    @Test
    public void testMkv() throws IOException {
        var videoPath = TestUtil.findFilePath("/samples/five-second-marathon-clip.mkv");
        // Something is wrong with last frame in the video. All frames have the same pts delta
        // except the last frame has double the pts delta.
        compareTimes(
                videoPath, NTSC_FPS, new Fraction(1, 1000), false,
                "video/x-matroska");
    }

    @Test
    public void testVideo1() throws IOException {
        var videoPath = TestUtil.findFilePath("/samples/video_01.mp4");
        compareTimes(
                videoPath, NTSC_FPS, new Fraction(1, 30_000), true,
                "video/mp4");
    }

    @Test
    public void testVideo2() throws IOException {
        var videoPath = TestUtil.findFilePath("/samples/video_02.mp4");
        compareTimes(
                videoPath, NTSC_FPS, new Fraction(1, 30_000), true,
                "video/mp4");
    }

    @Test
    public void testTs() throws IOException {
        var videoPath = TestUtil.findFilePath("/samples/bbb24p_00_short.ts");
        compareTimes(
                videoPath, new Fraction(24, 1),
                new Fraction(1, 90000), false,
                "video/vnd.dlna.mpeg-tts");
    }

    @Test
    public void testAvi() throws IOException {
        var videoPath = TestUtil.findFilePath("/samples/new_face_video.avi");
        compareTimes(
                videoPath, NTSC_FPS, new Fraction(1_001, 30_000),
                false, "video/x-msvideo");
    }


    @Test
    public void testStruck() throws IOException {
        var videoPath = TestUtil.findFilePath("/samples/STRUCK_Test_720p.mp4");
        compareTimes(
                videoPath, new Fraction(132000, 5221),
                new Fraction(1, 12000), false, "video/mp4");
    }


    @Test
    public void testVideoWithMissingTimes() {
        // ffprobe output:
        // 41
        // N/A
        // N/A

        var fps = new Fraction(223, 12);
        var timeBase = new Fraction(12, 223);
        var videoPath = TestUtil.findFilePath("/samples/text-test-video-detection.avi");
        var ffprobeMetdata = new FfprobeMetadata.Video(
                -1, -1, fps, OptionalLong.empty(), OptionalLong.empty(), 0, timeBase);
        var timeInfo = FrameTimeInfoBuilder.getFrameTimeInfo(
                videoPath, ffprobeMetdata, "video/x-msvideo");

        assertEquals(2206, timeInfo.getTimeMsFromFrame(0));
        assertEquals(0, timeInfo.getFrameFromTimeMs(2206));
        // Use frame rate to guess time
        // prev + (1000 / fps)
        // 2206 + 1000 / 18.58
        assertEquals(2259, timeInfo.getTimeMsFromFrame(1));
        assertEquals(1, timeInfo.getFrameFromTimeMs(2259));
        // Use previous pts delta to guess time
        // prev + prev - prev_prev
        // 2259 + 2259 - 2206
        assertEquals(2312, timeInfo.getTimeMsFromFrame(2));
        assertEquals(2, timeInfo.getFrameFromTimeMs(2312));

        assertFalse(timeInfo.hasConstantFrameRate());
        assertTrue(timeInfo.requiresTimeEstimation());
        assertEquals(3, timeInfo.getExactFrameCount().orElse(0));
    }


    private static void compareTimes(
            Path video, Fraction fps, Fraction timeBase, boolean isCfr, String mimeType)
            throws IOException {
        var ffprobeMetdata = new FfprobeMetadata.Video(
                -1, -1, fps, OptionalLong.of(-1), OptionalLong.of(-1), 0, timeBase);
        var timeInfo = FrameTimeInfoBuilder.getFrameTimeInfo(video, ffprobeMetdata, mimeType);
        assertEquals(isCfr, timeInfo.hasConstantFrameRate());
        assertFalse(timeInfo.requiresTimeEstimation());

        var ffmpegTimes = getTimes(video);
        if (!isCfr) {
            assertEquals(ffmpegTimes.length, timeInfo.getExactFrameCount().orElse(-1));
        }

        for (int i = 0; i < ffmpegTimes.length; i++) {
            assertTrue("frame " + i,
                       Math.abs(ffmpegTimes[i] - timeInfo.getTimeMsFromFrame(i)) <= 1);
            assertTrue("time " + ffmpegTimes[i],
                       i - timeInfo.getFrameFromTimeMs(ffmpegTimes[i]) <= 1);
        }
    }


    private static int[] getTimes(Path video) throws IOException {
        // In the actual code we use best_effort_timestamp instead of best_effort_timestamp_time
        // (like below) because when collecting the timestamps, we are also checking for a variable
        // frame rate. best_effort_timestamp (normally PTS values) is always an integer, unlike
        // best_effort_timestamp_time which is a floating point value. Using best_effort_timestamp
        // to check for variable frame rate prevents the loss of precision that occurs with
        // floating point values. The rounding would likely cause constant frame rate videos to be
        // detected as having a variable frame rate.

        String[] command = {
                "ffprobe", "-hide_banner", "-select_streams", "v",
                "-show_entries", "frame=best_effort_timestamp_time",
                "-print_format", "default=noprint_wrappers=1:nokey=1", video.toString()
        };
        var process = new ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        process.getOutputStream().close();

        var times = IntStream.builder();
        try (var br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                try {
                    double time = Double.parseDouble(line) * 1000;
                    times.accept((int) time);
                }
                catch (NumberFormatException e) {
                    times.accept(-1);
                }
            }
        }
        return times.build().toArray();
    }
}
