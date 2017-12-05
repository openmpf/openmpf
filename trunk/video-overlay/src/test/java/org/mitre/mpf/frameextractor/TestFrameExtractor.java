/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class TestFrameExtractor {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(TestFrameExtractor.class);

    static {
        String libraryFile = new File("install/lib/libmpfopencvjni.so").getAbsolutePath();
        log.info("Loading libmpfopencvjni library from '{}'.", libraryFile);
        System.load(libraryFile);
    }

    @Test
    public void testFrameExtractorOnVideo() {
        extractFrames("video-overlay/src/test/resources/samples/five-second-marathon-clip-numbered.mp4",
                Arrays.asList(0, 1, 2, 4, 8, 100, 1000, 1500, 1998, 1999));
    }

    @Test
    public void testFrameExtractorOnGif() {
        extractFrames("video-overlay/src/test/resources/samples/face-morphing.gif", Arrays.asList(2, 4, 8));
    }

    private void extractFrames(String filePath, List<Integer> frames) {
        try {
            File sourceFile = new File(filePath);

            if (!sourceFile.exists()) {
                throw new IOException(String.format("File not found %s.", sourceFile.getAbsolutePath()));
            }

            File outputDirectory = new File("/tmp", UUID.randomUUID().toString());
            outputDirectory.mkdir();

            FrameExtractor extractor = new FrameExtractor(sourceFile.toURI(), outputDirectory.toURI());
            extractor.getFrames().addAll(frames);
            extractor.execute();

            Assert.assertEquals("Output directory \"" + outputDirectory.getAbsolutePath() +
                            "\" does not contain the proper number of extracted artifacts: ",
                    frames.size(), outputDirectory.listFiles().length);

        } catch (IOException ioe) {
            Assert.fail(String.format("Encountered an exception when none was expected. %s", ioe));
        }
    }
}
