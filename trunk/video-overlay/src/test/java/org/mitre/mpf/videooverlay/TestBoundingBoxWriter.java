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

package org.mitre.mpf.videooverlay;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;
import org.mitre.mpf.OverlayTestUtils;
import org.mitre.mpf.wfm.buffers.Markup;

public class TestBoundingBoxWriter {

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
        File sourceFile = new File(OverlayTestUtils.getFileResource(filePath));

        if (!sourceFile.exists()) {
            throw new IOException(String.format("File not found %s.", sourceFile.getAbsolutePath()));
        }

        File destinationFile = File.createTempFile("markedup", ".webm");
        destinationFile.deleteOnExit();

        var requestBuilder = Markup.MarkupRequest.newBuilder()
                .setMediaId(678)
                .setMediaType(Markup.MediaType.VIDEO)
                .setSourcePath(sourceFile.getAbsolutePath())
                .setDestinationPath(destinationFile.getAbsolutePath())
                .putMarkupProperties("MARKUP_LABELS_ENABLED", "true")
                .putMarkupProperties("MARKUP_BORDER_ENABLED", "true")
                .putMarkupProperties("MARKUP_VIDEO_EXEMPLAR_ICONS_ENABLED", "true")
                .putMarkupProperties("MARKUP_VIDEO_BOX_SOURCE_ICONS_ENABLED", "true")
                .putMarkupProperties("MARKUP_VIDEO_MOVING_OBJECT_ICONS_ENABLED", "true")
                .putMarkupProperties("MARKUP_VIDEO_FRAME_NUMBERS_ENABLED", "true")
                .putMarkupProperties("MARKUP_VIDEO_ENCODER", "vp9")
                // poor quality makes the test run faster
                .putMarkupProperties("MARKUP_VIDEO_VP9_CRF", "60");

        // dummy
        var box = Markup.BoundingBox.newBuilder()
                .setX(20)
                .setY(60)
                .setWidth(30)
                .setHeight(20)
                .setRotationDegrees(45)
                .setFlip(false)
                .setRed(255)
                .setGreen(0)
                .setBlue(0)
                .setLabel("some class 7.243")
                .build();

        IntStream.rangeClosed(1, 20)
                .forEach(i -> requestBuilder.putBoundingBoxes(i, bboxList(box)));

        BoundingBoxWriter.markup(requestBuilder.build().toByteArray());

        return getVideoWidthAndHeight(destinationFile.getAbsolutePath());
    }

    @Test
    public void testWriterOnRotatedImage() throws IOException {
        File sourceFile = new File(OverlayTestUtils.getFileResource("samples/Lenna-tall-rotated-with-border.jpg"));

        if (!sourceFile.exists()) {
            throw new IOException(String.format("File not found %s.", sourceFile.getAbsolutePath()));
        }

        File destinationFile = File.createTempFile("markedup", ".png");
        destinationFile.deleteOnExit();

        // Lenna's face
        var box = Markup.BoundingBox.newBuilder()
                .setX(290)
                .setY(214)
                .setWidth(170)
                .setHeight(170)
                .setRotationDegrees(270)
                .setFlip(false)
                .setRed(255)
                .setGreen(0)
                .setBlue(0)
                .setLabel("7.777")
                .build();

        var requestProtobytes = Markup.MarkupRequest.newBuilder()
                .setMediaId(678)
                .setMediaType(Markup.MediaType.IMAGE)
                .setSourcePath(sourceFile.getAbsolutePath())
                .setDestinationPath(destinationFile.getAbsolutePath())
                .putMediaMetadata("ROTATION", "270") // rotate cw this much to fix
                .putBoundingBoxes(0, bboxList(box))
                .putMarkupProperties("MARKUP_LABELS_ENABLED", "true")
                .build()
                .toByteArray();

        BoundingBoxWriter.markup(requestProtobytes);

        // Raw source file is 1024x512. Markup should be 512x1024 due to corrected orientation.
        BufferedImage bimg = ImageIO.read(destinationFile);
        Assert.assertEquals(512, bimg.getWidth());
        Assert.assertEquals(1024, bimg.getHeight());
    }

    @Test
    public void testWriterOnRotatedVideo() throws IOException, InterruptedException {
        File sourceFile = new File(OverlayTestUtils.getFileResource("samples/video_02_rotated.mp4"));

        if (!sourceFile.exists()) {
            throw new IOException(String.format("File not found %s.", sourceFile.getAbsolutePath()));
        }

        File destinationFile = File.createTempFile("markedup", ".avi");
        destinationFile.deleteOnExit();

        // Old lady's face
        var box = Markup.BoundingBox.newBuilder()
                .setX(399)
                .setY(492)
                .setWidth(83)
                .setHeight(83)
                .setRotationDegrees(270)
                .setFlip(false)
                .setRed(255)
                .setGreen(0)
                .setBlue(0)
                .setLabel("56")
                .build();

        var requestProtobytes = Markup.MarkupRequest.newBuilder()
                .setMediaId(789)
                .setMediaType(Markup.MediaType.VIDEO)
                .setSourcePath(sourceFile.getAbsolutePath())
                .setDestinationPath(destinationFile.getAbsolutePath())
                .putMediaMetadata("ROTATION", "270") // 45
                .putMarkupProperties("MARKUP_LABELS_ENABLED", "true")
                .putMarkupProperties("MARKUP_VIDEO_FRAME_NUMBERS_ENABLED", "true")
                .putMarkupProperties("MARKUP_VIDEO_ENCODER", "mjpeg")
                .putBoundingBoxes(116, bboxList(box))
                .build()
                .toByteArray();
        BoundingBoxWriter.markup(requestProtobytes);


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

    private static Markup.BoundingBoxList bboxList(Markup.BoundingBox box) {
        return Markup.BoundingBoxList.newBuilder()
                .addBoundingBoxes(box)
                .build();
    }
}
