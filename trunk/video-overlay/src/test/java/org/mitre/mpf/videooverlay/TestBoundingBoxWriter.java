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

package org.mitre.mpf.videooverlay;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;
import org.mitre.mpf.JniTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class TestBoundingBoxWriter {

    static {
        Assert.assertTrue(JniTestUtils.jniLibsLoaded());
    }

    @Test
    public void testWriterOnVideo() throws IOException, InterruptedException {
        var widthAndHeight = writeBoxOnFrames("samples/lp-ferrari-texas-shortened.mp4");
        int width = widthAndHeight.getLeft();
        int height = widthAndHeight.getRight();

        // Raw source file is 1920x1280. Markup should be larger along each dimension due to border.
        Assert.assertTrue(width > height);
        Assert.assertTrue(width > 1920);
        Assert.assertTrue(height > 1080);
    }

    @Test
    public void testWriterOnGif() throws IOException, InterruptedException {
        var widthAndHeight = writeBoxOnFrames("samples/face-morphing.gif");
        int width = widthAndHeight.getLeft();
        int height = widthAndHeight.getRight();

        // Raw source file is 308x400. Markup should be larger along each dimension due to border.
        Assert.assertTrue(height > width);
        Assert.assertTrue(width > 308);
        Assert.assertTrue(height > 400);
    }

    private static Pair<Integer, Integer> writeBoxOnFrames(String filePath)
            throws IOException, InterruptedException {
        File sourceFile = new File(JniTestUtils.getFileResource(filePath));

        if (!sourceFile.exists()) {
            throw new IOException(String.format("File not found %s.", sourceFile.getAbsolutePath()));
        }

        File destinationFile = File.createTempFile("markedup", ".webm");
        destinationFile.deleteOnExit();

        BoundingBoxMap map = new BoundingBoxMap();

        // dummy
        BoundingBox box = new BoundingBox(20, 60, 30, 20, 45, false, 255, 0, 0, Optional.of("some class 7.243"));
        map.putOnFrames(1, 20, box);

        BoundingBoxWriter writer = new BoundingBoxWriter();
        writer.setSourceMedium(sourceFile.toURI());
        writer.setDestinationMedium(destinationFile.toURI());

        writer.setRequestProperties(Map.of(
                "MARKUP_LABELS_ENABLED", "true",
                "MARKUP_BORDER_ENABLED", "true",
                "MARKUP_VIDEO_EXEMPLAR_ICONS_ENABLED", "true",
                "MARKUP_VIDEO_BOX_SOURCE_ICONS_ENABLED", "true",
                "MARKUP_VIDEO_MOVING_OBJECT_ICONS_ENABLED", "true",
                "MARKUP_VIDEO_FRAME_NUMBERS_ENABLED", "true",
                "MARKUP_VIDEO_ENCODER", "vp9",
                "MARKUP_VIDEO_VP9_CRF", "60" // poor quality makes the test run faster
        ));

        writer.setBoundingBoxMap(map);
        writer.markupVideo();

        return getVideoWidthAndHeight(destinationFile.getAbsolutePath());
    }

    @Test
    public void testWriterOnRotatedImage() throws IOException {
        File sourceFile = new File(JniTestUtils.getFileResource("samples/Lenna-tall-rotated-with-border.jpg"));

        if (!sourceFile.exists()) {
            throw new IOException(String.format("File not found %s.", sourceFile.getAbsolutePath()));
        }

        File destinationFile = File.createTempFile("markedup", ".png");
        destinationFile.deleteOnExit();

        BoundingBoxMap map = new BoundingBoxMap();

        // Lenna's face
        BoundingBox box = new BoundingBox(290, 214, 170, 170,  270, false, 255, 0, 0, Optional.of("7.777"));
        map.putOnFrames(0, 0, box);

        BoundingBoxWriter writer = new BoundingBoxWriter();
        writer.setSourceMedium(sourceFile.toURI());
        writer.setDestinationMedium(destinationFile.toURI());
        writer.setMediaMetadata(Map.of("ROTATION", "270")); // rotate cw this much to fix

        writer.setRequestProperties(Map.of(
                "MARKUP_LABELS_ENABLED", "true"
        ));

        writer.setBoundingBoxMap(map);
        writer.markupImage();

        // Raw source file is 1024x512. Markup should be 512x1024 due to corrected orientation.
        BufferedImage bimg = ImageIO.read(destinationFile);
        Assert.assertEquals(512, bimg.getWidth());
        Assert.assertEquals(1024, bimg.getHeight());
    }

    @Test
    public void testWriterOnRotatedVideo() throws IOException, InterruptedException {
        File sourceFile = new File(JniTestUtils.getFileResource("samples/video_02_rotated.mp4"));

        if (!sourceFile.exists()) {
            throw new IOException(String.format("File not found %s.", sourceFile.getAbsolutePath()));
        }

        File destinationFile = File.createTempFile("markedup", ".avi");
        destinationFile.deleteOnExit();

        BoundingBoxMap map = new BoundingBoxMap();

        // Old lady's face
        BoundingBox box = new BoundingBox(399, 492, 83, 83,  270, false, 255, 0, 0, Optional.of("56"));
        map.putOnFrames(116, 116, box);

        BoundingBoxWriter writer = new BoundingBoxWriter();
        writer.setSourceMedium(sourceFile.toURI());
        writer.setDestinationMedium(destinationFile.toURI());
        writer.setMediaMetadata(Map.of("ROTATION", "270")); // 45

        writer.setRequestProperties(Map.of(
                "MARKUP_LABELS_ENABLED", "true",
                "MARKUP_VIDEO_FRAME_NUMBERS_ENABLED", "true",
                "MARKUP_VIDEO_ENCODER", "mjpeg"
        ));

        writer.setBoundingBoxMap(map);
        writer.markupVideo();

        var widthAndHeight = getVideoWidthAndHeight(destinationFile.getAbsolutePath());
        int width = widthAndHeight.getLeft();
        int height = widthAndHeight.getRight();

        // Raw source file is 480x640. Markup should be 640x480 due to corrected orientation.
        Assert.assertEquals(640, width);
        Assert.assertEquals(480, height);
    }

    private static Pair<Integer, Integer> getVideoWidthAndHeight(String filePath)
            throws IOException, InterruptedException {
        int width, height;
        String[] command = { "ffprobe", "-hide_banner", "-select_streams", "v", "-show_entries",
                "stream=width,height", "-of", "default=nw=1:nk=1", filePath };
        Process process = new ProcessBuilder(command).start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            width = Integer.parseInt(reader.readLine());
            height = Integer.parseInt(reader.readLine());
        } catch (IOException e) {
            throw new UncheckedIOException(
                    String.format("Exception while running process: %s", String.join(" ", command)), e);
        }
        Assert.assertTrue(process.waitFor(5, TimeUnit.SECONDS));
        return Pair.of(width, height);
    }
}
