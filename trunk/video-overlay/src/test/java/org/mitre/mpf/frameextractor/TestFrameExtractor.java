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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.JniTestUtils;
import org.mitre.mpf.interop.JsonDetectionOutputObject;
import org.mitre.mpf.interop.JsonTrackOutputObject;

import com.google.common.collect.Table;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
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


    @Test
    public void testFrameExtractorOnVideo() throws IOException {
        JsonTrackOutputObject track = createTrackObject(1, Arrays.asList(0, 1, 2, 4, 8, 100, 1000, 1500, 1998, 1999),
                                                        100, 150);
        extractFrames("samples/five-second-marathon-clip-numbered.mp4", Arrays.asList(track));
    }

    @Test
    public void testFrameExtractorOnGif() throws IOException {
        JsonTrackOutputObject track = createTrackObject(2, Arrays.asList(2, 4, 8),
                                                        150, 100);
        extractFrames("samples/face-morphing.gif", Arrays.asList(track));
    }

    @Test
    public void testFrameExtractorOnImage() throws IOException {
        JsonTrackOutputObject track = createTrackObject(3, Arrays.asList(0),
                                                        400, 300);
        extractFrames("samples/person_cropped_2.png", Arrays.asList(track));
    }

    @Test
    public void testFrameExtractorMultipleTracks() throws IOException {
        List<JsonTrackOutputObject> tracks = new ArrayList<>();
        tracks.add(createTrackObject(4, Arrays.asList(0, 1, 2, 4, 8, 12), 90, 90));
        tracks.add(createTrackObject(5, Arrays.asList(5, 10, 20), 120, 120));

        extractFrames("samples/five-second-marathon-clip-numbered.mp4", tracks);
    }

    private void extractFrames(String resourcePath, List<JsonTrackOutputObject> tracks) throws IOException {
        URI resource = JniTestUtils.getFileResource(resourcePath);

        Path outputDirectory = tempFolder.newFolder().toPath().toAbsolutePath();
        FrameExtractor extractor = new FrameExtractor(resource, outputDirectory.toUri());
        extractor.getTracksToExtract().addAll(tracks);
        Table<Integer, Integer, String> results = extractor.execute();

        Set<String> fileNames = new TreeSet<>();
        for (JsonTrackOutputObject track : tracks) {
            Path trackPath = outputDirectory.resolve(track.getId());
            try(Stream<Path> paths = Files.list(trackPath)) {
                Set<String> tmpNames = paths.map(p -> p.getFileName().toString())
                                .collect(toSet());
                fileNames.addAll(tmpNames);
            }
        }
        List<Integer> frames = tracks.stream()
                .flatMap(t -> t.getDetections().stream())
                .map(d -> d.getOffsetFrame())
                .collect(Collectors.toList());
        Assert.assertEquals(frames.size(), fileNames.size());
        frames.forEach(f -> Assert.assertTrue(fileNames.contains("frame-" + f + ".png")));

        for (JsonTrackOutputObject track : tracks) {
            track.getDetections().stream()
            .forEach(d -> assertSizesMatch(d, results.get(Integer.parseInt(track.getId()), d.getOffsetFrame())));
        }
    }


    private JsonTrackOutputObject createTrackObject(Integer trackId, List<Integer> frames,
                                                    int width, int height) {
        SortedSet<JsonDetectionOutputObject> detections = new TreeSet<>();
        for (int i = 0; i < frames.size(); ++i) {
            detections.add(new JsonDetectionOutputObject(0, 0, width, height,
                    (float)0.0, Collections.emptySortedMap(), frames.get(i).intValue(),
                    0, "NOT_ATTEMPTED", ""));
        }
        return new JsonTrackOutputObject(Integer.toString(trackId),
                frames.get(0), frames.get(frames.size()-1), 0, 0, "", "", (float)0.0,
                Collections.emptySortedMap(), detections.first(), detections);

    }

    private void assertSizesMatch(JsonDetectionOutputObject detection, String uri) {
        int expectedWidth = detection.getWidth();
        int expectedHeight = detection.getHeight();
        int actualWidth = 0;
        int actualHeight = 0;
        File file = new File(uri);
        try {
            ImageInputStream imageStream = ImageIO.createImageInputStream(file);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageStream);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                reader.setInput(imageStream);
                actualWidth = reader.getWidth(0);
                actualHeight = reader.getHeight(0);
            }
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        }
        assertTrue((actualWidth == expectedWidth) && (actualHeight == expectedHeight));
    }
}

