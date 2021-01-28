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

package org.mitre.mpf.frameextractor;

import com.google.common.collect.Table;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.JniTestUtils;
import org.mitre.mpf.interop.JsonDetectionOutputObject;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static org.junit.Assert.assertEquals;

public class TestFrameExtractor {
    static {
        Assert.assertTrue(JniTestUtils.jniLibsLoaded());
    }


    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    // These tests determine whether the dimensions of the extracted artifact are the same as the
    // detection bounding box dimensions in the extraction request. The bounding boxes used in each test are arbitrary;
    // they do not necessarily correspond to actual detections in the test media.
    @Test
    public void testFrameExtractorOnVideo() throws IOException {
        SortedMap<Integer, Map<Integer, JsonDetectionOutputObject>> requestedExtractions = new TreeMap<>();
        List<Integer> trackIndices = Arrays.asList(1, 2);
        List<Integer> frameNumbers = Arrays.asList(0, 1, 2, 4, 8, 100, 1000, 1500, 1998, 1999);
        frameNumbers.stream()
                .forEach(n -> putInExtractionMap(n, trackIndices, 20, 30, 100, 150, 0.0, false, requestedExtractions));
        URI media = JniTestUtils.getFileResource("samples/five-second-marathon-clip-numbered.mp4");
        // test first with cropping, then without.
        extractFrames(media, Map.of(), true, requestedExtractions);
        extractFrames(media, Map.of(), false, requestedExtractions);
    }

    @Test
    public void testFrameExtractorOnGif() throws IOException {
        SortedMap<Integer, Map<Integer, JsonDetectionOutputObject>> requestedExtractions = new TreeMap<>();
        List<Integer> trackIndices = Arrays.asList(1);
        List<Integer> frameNumbers = Arrays.asList(2, 4, 8);
        frameNumbers.stream()
                .forEach(n -> putInExtractionMap(n, trackIndices, 100, 100, 150, 100, 0.0, false, requestedExtractions));
        URI media = JniTestUtils.getFileResource("samples/face-morphing.gif");
        extractFrames(media, Map.of(), true, requestedExtractions);
        extractFrames(media, Map.of(), false, requestedExtractions);
    }

    @Test
    public void testFrameExtractorOnImage() throws IOException {
        SortedMap<Integer, Map<Integer, JsonDetectionOutputObject>> requestedExtractions = new TreeMap<>();
        putInExtractionMap(0, Arrays.asList(3),200, 200, 150, 100, 0.0, false, requestedExtractions);
        URI media = JniTestUtils.getFileResource("samples/person_cropped_2.png");
        extractFrames(media, Map.of(), true, requestedExtractions);
        extractFrames(media, Map.of(), false, requestedExtractions);
    }

    @Test
    public void testFrameExtractorOnRotatedImage() throws IOException {
        SortedMap<Integer, Map<Integer, JsonDetectionOutputObject>> requestedExtractions = new TreeMap<>();
        putInExtractionMap(0, Arrays.asList(3), 200, 200, 150, 100, 90.0, false, requestedExtractions); // capture the subject's right eye

        // not accounting for orientation, sample media has raw dimensions of 600 width by 480 height
        // from exiftool: "Mirror horizontal and rotate 270 CW"
        URI media = JniTestUtils.getFileResource("samples/meds-aa-S001-01-exif-rotation.jpg");
        Map metaMetadata = Map.of("ROTATION", "90", "HORIZONTAL_FLIP", "true"); // TODO

        extractFrames(media, metaMetadata, true, requestedExtractions);
        Table<Integer, Integer, String> results = extractFrames(media, metaMetadata, false, requestedExtractions);

        String extraction = results.get(0, 0); // track id is set to 0 for full frame results
        BufferedImage bimg = ImageIO.read(new File(extraction));
        Assert.assertEquals(480, bimg.getWidth());
        Assert.assertEquals(600, bimg.getHeight());
    }

    @Test
    public void testFrameExtractorOnImageWithMultipleDetections() throws IOException {
        SortedMap<Integer, Map<Integer, JsonDetectionOutputObject>> requestedExtractions = new TreeMap<>();
        putInExtractionMap(0, Arrays.asList(0), 652, 212, 277, 277, 0.0, false, requestedExtractions);
        putInExtractionMap(0, Arrays.asList(1), 970, 165, 329, 329, 0.0, false, requestedExtractions);
        URI media = JniTestUtils.getFileResource("samples/girl-1741925_1920.jpg");
        extractFrames(media, Map.of(), true, requestedExtractions);
        extractFrames(media, Map.of(), false, requestedExtractions);
    }

    @Test
    public void testFrameExtractorMultipleTracks() throws IOException {
        SortedMap<Integer, Map<Integer, JsonDetectionOutputObject>> requestedExtractions = new TreeMap<>();
        List<Integer> trackIndices = Arrays.asList(1, 2);
        List<Integer> frameNumbers = Arrays.asList(0, 1, 2, 4, 8, 100, 1000, 1500, 1998, 1999);
        frameNumbers.stream()
                .forEach(n -> putInExtractionMap(n, trackIndices, 50, 50, 100, 150, 0.0, false, requestedExtractions));
        List<Integer> nextTrackIndices = Arrays.asList(3, 4);
        frameNumbers = Arrays.asList(0, 1, 2, 4, 8, 100, 1000, 1500, 1998, 1999);
        frameNumbers.stream()
                .forEach(n -> putInExtractionMap(n, nextTrackIndices,0, 0, 90, 125, 0.0, false, requestedExtractions));

        URI media = JniTestUtils.getFileResource("samples/five-second-marathon-clip-numbered.mp4");
        extractFrames(media, Map.of(), true, requestedExtractions);
        extractFrames(media, Map.of(), false, requestedExtractions);
    }
    
    private Table<Integer, Integer, String> extractFrames(URI media, Map<String, String> mediaMetadata,
                boolean cropFlag, SortedMap<Integer, Map<Integer, JsonDetectionOutputObject>> requestedExtractions)
            throws IOException {

        Path outputDirectory = tempFolder.newFolder().toPath().toAbsolutePath();
        FrameExtractor extractor = new FrameExtractor(media, mediaMetadata, outputDirectory.toUri(), cropFlag, true);
        extractor.getExtractionsMap().putAll(requestedExtractions);
        Table<Integer, Integer, String> results = extractor.execute();

        Set<Integer> frames = requestedExtractions.keySet();
        if (cropFlag) {
            for (Integer frame : frames) {
                for (Integer trackIndex : requestedExtractions.get(frame).keySet()) {
                    assertSizesMatch(requestedExtractions.get(frame).get(trackIndex), results.get(trackIndex, frame));
                }
            }
        }
        else {
            Set<String> fileNames = new TreeSet<>();
            Path trackPath = outputDirectory.resolve("0");
            try(Stream<Path> paths = Files.list(trackPath)) {
                Set<String> tmpNames = paths.map(p -> p.getFileName().toString())
                        .collect(toSet());
                fileNames.addAll(tmpNames);
            }

            Assert.assertEquals(frames.size(), fileNames.size());
            frames.forEach(f -> Assert.assertTrue(fileNames.contains("frame-" + f + ".png")));
        }

        return results;
    }


    private void putInExtractionMap(Integer frameNumber, List<Integer> trackIndices,
                                    int x, int y, int width, int height, double rotation, boolean flip,
                                    SortedMap<Integer, Map<Integer, JsonDetectionOutputObject>> requestedExtractions) {

        if (requestedExtractions.get(frameNumber) == null) {
            requestedExtractions.put(frameNumber, new TreeMap<Integer, JsonDetectionOutputObject>());
        }

        SortedMap<String, String> props = new TreeMap<>();
        props.put("ROTATION", Double.toString(rotation));
        props.put("HORIZONTAL_FLIP", Boolean.toString(flip));

        for (Integer trackIndex : trackIndices) {
            requestedExtractions.get(frameNumber).put(trackIndex, new JsonDetectionOutputObject(x, y, width, height,
                    (float)0.0, props, frameNumber.intValue(),
                    0, "NOT_ATTEMPTED", ""));
        }
    }

    private void assertSizesMatch(JsonDetectionOutputObject extractedDetection, String artifactFilePath) throws IOException {
        int expectedWidth = extractedDetection.getWidth();
        int expectedHeight = extractedDetection.getHeight();
        int actualWidth = 0;
        int actualHeight = 0;
        File file = new File(artifactFilePath);

        ImageInputStream imageStream = ImageIO.createImageInputStream(file);
        Iterator<ImageReader> readers = ImageIO.getImageReaders(imageStream);
        if (readers.hasNext()) {
            ImageReader reader = readers.next();
            reader.setInput(imageStream);
            actualWidth = reader.getWidth(0);
            actualHeight = reader.getHeight(0);
        }
        else {
            Assert.fail("Could not read " + file.getAbsolutePath() + " to get width and height");
        }

        assertEquals(expectedWidth, actualWidth);
        assertEquals(expectedHeight, actualHeight);
    }


}

