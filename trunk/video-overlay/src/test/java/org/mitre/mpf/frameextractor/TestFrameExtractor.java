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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.JniTestUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;

public class TestFrameExtractor {
    static {
        Assert.assertTrue(JniTestUtils.jniLibsLoaded());
    }


    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();


    @Test
    public void testFrameExtractorOnVideo() throws IOException {
        extractFrames("samples/five-second-marathon-clip-numbered.mp4",
                Arrays.asList(0, 1, 2, 4, 8, 100, 1000, 1500, 1998, 1999));
    }

    @Test
    public void testFrameExtractorOnGif() throws IOException {
        extractFrames("samples/face-morphing.gif", Arrays.asList(2, 4, 8));
    }

    private void extractFrames(String resourcePath, List<Integer> frames) throws IOException {
        URI resource = JniTestUtils.getFileResource(resourcePath);

        Path outputDirectory = tempFolder.newFolder().toPath().toAbsolutePath();
        FrameExtractor extractor = new FrameExtractor(resource, outputDirectory.toUri());
        extractor.getFrames().addAll(frames);
        extractor.execute();

        Set<String> fileNames;
        try(Stream<Path> paths = Files.list(outputDirectory)) {
            fileNames = paths.map(p -> p.getFileName().toString())
                    .collect(toSet());
        }

        Assert.assertEquals(frames.size(), fileNames.size());
        frames.forEach(f -> Assert.assertTrue(fileNames.contains("frame-" + f + ".png")));
    }
}
