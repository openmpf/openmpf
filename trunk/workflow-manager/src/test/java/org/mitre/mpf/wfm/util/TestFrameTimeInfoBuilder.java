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

import org.junit.Test;
import org.mitre.mpf.test.TestUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

public class TestFrameTimeInfoBuilder {

    @Test
    public void testMkv() throws IOException {
        var videoPath = TestUtil.findFilePath("/samples/five-second-marathon-clip.mkv");
        // Something is wrong with last frame in the video. All frames have the same pts delta
        // except the last frame has double the pts delta.
        compareTimes(videoPath, 29.97, true, false);
    }

    @Test
    public void testVideo1() throws IOException {
        var videoPath = TestUtil.findFilePath("/samples/video_01.mp4");
        compareTimes(videoPath, 29.97, true, true);
    }

    @Test
    public void testVideo2() throws IOException {
        var videoPath = TestUtil.findFilePath("/samples/video_02.mp4");
        compareTimes(videoPath, 29.97, true, true);
    }

    @Test
    public void testTs() throws IOException {
        var videoPath = TestUtil.findFilePath("/samples/bbb24p_00_short.ts");
        compareTimes(videoPath, 24, false, true);
    }

    @Test
    public void testAvi() throws IOException {
        var videoPath = TestUtil.findFilePath("/samples/new_face_video.avi");
        compareTimes(videoPath, 29.97, false, true);
    }


    @Test
    public void testStruck() throws IOException {
        var videoPath = TestUtil.findFilePath("/samples/STRUCK_Test_720p.mp4");
        compareTimes(videoPath, 25.28, false, true);
    }


    @Test
    public void testVideoWithMissingTimes() {
        // timebase = 12/223
        // ffprobe output:
        // 41
        // N/A
        // N/A

        var videoPath = TestUtil.findFilePath("/samples/text-test-video-detection.avi");
        var timeInfo = FrameTimeInfoBuilder.getFrameTimeInfo(videoPath, 18.58);

        assertEquals(2206, timeInfo.getFrameTimeMs(0));
        // Use frame rate to guess time
        // prev + (1000 / fps)
        // 2206 + 1000 / 18.58
        assertEquals(2259, timeInfo.getFrameTimeMs(1));
        // Use previous pts delta to guess time
        // prev + prev - prev_prev
        // 2259 + 2259 - 2206
        assertEquals(2312, timeInfo.getFrameTimeMs(2));

        assertFalse(timeInfo.hasConstantFrameRate());
        assertTrue(timeInfo.requiresTimeEstimation());
    }


    private static void compareTimes(Path video, double fps, boolean isCfr, boolean checkLastFrame)
            throws IOException {
        var timeInfo = FrameTimeInfoBuilder.getFrameTimeInfo(video, fps);
        assertEquals(isCfr, timeInfo.hasConstantFrameRate());
        assertFalse(timeInfo.requiresTimeEstimation());

        var ffmpegTimes = getTimes(video);

        int endIdx = checkLastFrame
                ? ffmpegTimes.length
                : ffmpegTimes.length - 1;

        for (int i = 0; i < endIdx; i++) {
            assertTrue("frame " + i,
                       Math.abs(ffmpegTimes[i] - timeInfo.getFrameTimeMs(i)) <= 1);
        }
    }


    private static int[] getTimes(Path video) throws IOException {
        // In the actual code we use best_effort_timestamp instead of best_effort_timestamp_time
        // (like below) because when collecting the timestamps, we are also checking for a variable
        // frame rate. best_effort_timestamp (normally PTS values) is always an  integer, unlike
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
