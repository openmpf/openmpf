/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class TestFrameExtractor {
    private static final Logger log = LoggerFactory.getLogger(TestFrameExtractor.class);

    static {
        String libraryFile = new File("install/lib/libmpfopencvjni.so").getAbsolutePath();
        try {
            System.load(libraryFile);
        }
        catch (UnsatisfiedLinkError e) {
            String mpfHome = System.getenv("MPF_HOME");
            if (mpfHome == null) {
                throw e;
            }
            libraryFile = new File(mpfHome, "lib/libmpfopencvjni.so").getAbsolutePath();
            System.load(libraryFile);
        }
        log.info("Loaded libmpfopencvjni library from '{}'.", libraryFile);
    }

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();


    @Test
    public void testFrameExtractorOnVideo() throws IOException, URISyntaxException {
        extractFrames("samples/five-second-marathon-clip.mkv");
    }

    @Test
    public void testFrameExtractorOnGif() throws URISyntaxException, IOException {
        extractFrames("samples/face-morphing.gif");
    }



    private void extractFrames(String resourcePath) throws IOException, URISyntaxException {
        URL resource = TestFrameExtractor.class.getClassLoader().getResource(resourcePath);
        Assert.assertNotNull(resource);

        Path outputDirectory = tempFolder.newFolder().toPath().toAbsolutePath();

        FrameExtractor extractor = new FrameExtractor(resource.toURI(), outputDirectory.toUri());
        extractor.getFrames().add(2);
        extractor.getFrames().add(4);
        extractor.getFrames().add(8);
        extractor.execute();

        List<String> fileNames;
        try(Stream<Path> paths = Files.list(outputDirectory)) {
            fileNames = paths.map(p -> p.getFileName().toString())
                    .collect(toList());
        }
        Assert.assertEquals(3, fileNames.size());
        Assert.assertTrue(fileNames.containsAll(Arrays.asList("frame-2.png", "frame-4.png", "frame-8.png")));
    }
}
