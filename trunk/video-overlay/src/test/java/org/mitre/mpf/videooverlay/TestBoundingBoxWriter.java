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

package org.mitre.mpf.videooverlay;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class TestBoundingBoxWriter {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(TestBoundingBoxWriter.class);

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

    @Test
    public void testWriterOnVideo() {
        writeBoxOnFrames("video-overlay/src/test/resources/samples/five-second-marathon-clip-numbered.mp4");
    }

    @Test
    public void testWriterOnGif() {
        writeBoxOnFrames("video-overlay/src/test/resources/samples/face-morphing.gif");
    }

    private void writeBoxOnFrames(String filePath) {
        try {
            File sourceFile = new File(filePath);

            if (!sourceFile.exists()) {
                throw new IOException(String.format("File not found %s.", sourceFile.getAbsolutePath()));
            }

            File destinationFile = File.createTempFile("markedup", ".avi");
            destinationFile.deleteOnExit();

            BoundingBoxMap map = new BoundingBoxMap();

            BoundingBox box = new BoundingBox();
            box.setX(5);
            box.setY(5);
            box.setWidth(15);
            box.setHeight(15);
            box.setColor(255, 0, 0);
            map.putOnFrames(1, 20, box);

            BoundingBoxWriter writer = new BoundingBoxWriter();
            writer.setSourceMedium(sourceFile.toURI());
            writer.setDestinationMedium(destinationFile.toURI());
            writer.setBoundingBoxMap(map);
            writer.markupVideo();

            // Test that something was written.
            Assert.assertTrue("The size of the output video must be greater than 4096.", destinationFile.length() > 4096);

        } catch (IOException ioe) {
            Assert.fail(String.format("Encountered an exception when none was expected. %s", ioe));
        }
    }
}
