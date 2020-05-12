/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

import org.junit.Assert;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.JniTestUtils;
import org.mitre.mpf.interop.JsonDetectionOutputObject;

import com.google.common.collect.Table;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import static java.util.stream.Collectors.toSet;

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
        List<Integer> trackIds = Arrays.asList(1, 2);
        List<Integer> frameNumbers = Arrays.asList(0, 1, 2, 4, 8, 100, 1000, 1500, 1998, 1999);
        frameNumbers.stream()
                .forEach(n -> putInExtractionMap(n, trackIds, 20, 30, 100, 150, 0.0, requestedExtractions));
        URI media = JniTestUtils.getFileResource("samples/five-second-marathon-clip-numbered.mp4");
        // test first with cropping, then without.
        extractFrames(media, true, requestedExtractions);
        extractFrames(media, false, requestedExtractions);
    }

    @Test
    public void testFrameExtractorOnGif() throws IOException {
        SortedMap<Integer, Map<Integer, JsonDetectionOutputObject>> requestedExtractions = new TreeMap<>();
        List<Integer> trackIds = Arrays.asList(1);
        List<Integer> frameNumbers = Arrays.asList(2, 4, 8);
        frameNumbers.stream()
                .forEach(n -> putInExtractionMap(n, trackIds, 100, 100, 150, 100, 0.0, requestedExtractions));
        URI media = JniTestUtils.getFileResource("samples/face-morphing.gif");
        extractFrames(media, true, requestedExtractions);
        extractFrames(media, false, requestedExtractions);
    }

    @Test
    public void testFrameExtractorOnImage() throws IOException {
        SortedMap<Integer, Map<Integer, JsonDetectionOutputObject>> requestedExtractions = new TreeMap<>();
        putInExtractionMap(0, Arrays.asList(3),200, 200, 150, 100, 0.0, requestedExtractions);
        URI media = JniTestUtils.getFileResource("samples/person_cropped_2.png");
        extractFrames(media, true, requestedExtractions);
        extractFrames(media, false, requestedExtractions);
    }

    @Test
    public void testFrameExtractorOnRotatedImage() throws IOException {
        SortedMap<Integer, Map<Integer, JsonDetectionOutputObject>> requestedExtractions = new TreeMap<>();
        putInExtractionMap(0, Arrays.asList(3), 200, 200, 150, 100, 90.0, requestedExtractions);
        URI media = JniTestUtils.getFileResource("samples/meds-aa-S001-01-exif-rotation.jpg");
        extractFrames(media, true, requestedExtractions);
        extractFrames(media, false, requestedExtractions);
    }

    @Test
    public void testFrameExtractorOnImageWithMultipleDetections() throws IOException {
        SortedMap<Integer, Map<Integer, JsonDetectionOutputObject>> requestedExtractions = new TreeMap<>();
        putInExtractionMap(0, Arrays.asList(0), 652, 212, 277, 277, 0.0, requestedExtractions);
        putInExtractionMap(0, Arrays.asList(1), 970, 165, 329, 329, 0.0, requestedExtractions);
        URI media = JniTestUtils.getFileResource("samples/girl-1741925_1920.jpg");
        extractFrames(media, true, requestedExtractions);
        extractFrames(media, false, requestedExtractions);
    }

    @Test
    public void testFrameExtractorMultipleTracks() throws IOException {
        SortedMap<Integer, Map<Integer, JsonDetectionOutputObject>> requestedExtractions = new TreeMap<>();
        List<Integer> trackIds = Arrays.asList(1, 2);
        List<Integer> frameNumbers = Arrays.asList(0, 1, 2, 4, 8, 100, 1000, 1500, 1998, 1999);
        frameNumbers.stream()
                .forEach(n -> putInExtractionMap(n, trackIds, 50, 50, 100, 150, 0.0, requestedExtractions));
        List<Integer> nextTrackIds = Arrays.asList(3, 4);
        frameNumbers = Arrays.asList(0, 1, 2, 4, 8, 100, 1000, 1500, 1998, 1999);
        frameNumbers.stream()
                .forEach(n -> putInExtractionMap(n, nextTrackIds,0, 0, 90, 125, 0.0, requestedExtractions));

        URI media = JniTestUtils.getFileResource("samples/five-second-marathon-clip-numbered.mp4");
        extractFrames(media, true, requestedExtractions);
        extractFrames(media, false, requestedExtractions);
    }


    private void extractFrames(URI media, boolean cropFlag,
            SortedMap<Integer, Map<Integer, JsonDetectionOutputObject>> requestedExtractions) throws IOException {

        Path outputDirectory = tempFolder.newFolder().toPath().toAbsolutePath();
        FrameExtractor extractor = new FrameExtractor(media, outputDirectory.toUri(), cropFlag);
        extractor.getExtractionsMap().putAll(requestedExtractions);
        Table<Integer, Integer, String> results = extractor.execute();

        Set<Integer> frames = requestedExtractions.keySet();
        if (cropFlag) {
            for (Integer frame : frames) {
                for (Integer trackId : requestedExtractions.get(frame).keySet()) {
                    assertSizesMatch(requestedExtractions.get(frame).get(trackId), results.get(trackId, frame));
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
    }


    private void putInExtractionMap(Integer frameNumber, List<Integer> trackIds,
                                    int x, int y, int width, int height, double rotation,
                                    SortedMap<Integer, Map<Integer, JsonDetectionOutputObject>> requestedExtractions) {

        if (requestedExtractions.get(frameNumber) == null) {
            requestedExtractions.put(frameNumber, new TreeMap<Integer, JsonDetectionOutputObject>());
        }

        SortedMap<String, String> props = new TreeMap<>();
        props.put("ROTATION", Double.toString(rotation));

        for (Integer trackId : trackIds) {
            requestedExtractions.get(frameNumber).put(trackId, new JsonDetectionOutputObject(x, y, width, height,
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

