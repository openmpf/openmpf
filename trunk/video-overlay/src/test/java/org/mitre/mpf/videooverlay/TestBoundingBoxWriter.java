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

package org.mitre.mpf.videooverlay;

import org.junit.Assert;
import org.junit.Test;
import org.mitre.mpf.JniTestUtils;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public class TestBoundingBoxWriter {

    static {
        Assert.assertTrue(JniTestUtils.jniLibsLoaded());
    }

    @Test
    public void testWriterOnVideo() {
        // writeBoxOnFrames("samples/five-second-marathon-clip-numbered.mp4");
        // writeBoxOnFrames("/media/SANDISK/SAMPLES/parked-on-road-4k.jpg"); // DEBUG
        // writeBoxOnFrames("/media/SANDISK/SAMPLES/4kSampleFiles/News_H264.mp4"); // DEBUG

        // writeBoxOnFrames("/home/mpf/git/openmpf-projects/openmpf/trunk/install/share/remote-media/Lenna-90ccw-exif.jpg"); // DEBUG
        // writeBoxOnFrames("/home/mpf/Desktop/SAMPLES/Lenna-flip-exif.jpg"); // DEBUG
        writeBoxOnFrames("/home/mpf/Desktop/SAMPLES/Lenna.png"); // DEBUG
        // writeBoxOnFrames("/home/mpf/Desktop/SAMPLES/Lenna-180ccw-exif.jpg"); // DEBUG
        // writeBoxOnFrames("/home/mpf/Desktop/SAMPLES/Lenna-flip-90ccw-exif.jpg"); // DEBUG
    }

    @Test
    public void testWriterOnGif() {
        writeBoxOnFrames("samples/face-morphing.gif");
    }


    private static void writeBoxOnFrames(String filePath) {
        try {
            File sourceFile = new File(filePath); // new File(JniTestUtils.getFileResource(filePath)); // DEBUG

            if (!sourceFile.exists()) {
                throw new IOException(String.format("File not found %s.", sourceFile.getAbsolutePath()));
            }

            File destinationFile = File.createTempFile("markedup", ".png"); // ".avi"
            destinationFile.deleteOnExit();

            BoundingBoxMap map = new BoundingBoxMap();

            /*
            for (int i = 0; i < 500; i++) {
                BoundingBox box1 = new BoundingBox(80, 82, 5*i + 4, 5*i + 4, 0, false, 255, 0, 0, false, false, 7.243234f,
                        Optional.empty()); // Optional.of("thing"));
                map.putOnFrames(i, i, box1);
            }
            */

            // BoundingBox box1 = new BoundingBox(156, 338, 193, 242, 90, false, 255, 0, 0, true, true, 7.243234f, Optional.empty()); // Lenna-90ccw-exif.jpg
            // BoundingBox box1 = new BoundingBox(339, 156, 194, 243, 0, true, 255, 0, 0, true, true, 7.243234f, Optional.empty()); // Lenna-flip-exif.jpg
            // BoundingBox box1 = new BoundingBox(172, 156, 194, 243, 0, false, 255, 0, 0, true, true, 7.243234f, Optional.empty()); // Lenna.png
            // BoundingBox box1 = new BoundingBox(339, 355, 194, 243, 180, false, 255, 0, 0, true, true, 7.243234f, Optional.empty()); // Lenna-180ccw-exif.png
            // BoundingBox box1 = new BoundingBox(156, 172, 194, 243, 270, true, 255, 0, 0, true, true, 7.243234f, Optional.empty()); // Lenna-flip-90ccw-exif.png
            // map.putOnFrames(0, 0, box1);

            // DEBUG
            BoundingBox box1 = new BoundingBox(150, 150, 20, 30, 194, true, 255, 0, 0, true, true, 7.243234f,
                    Optional.of("verrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrry")); // Lenna.png
            map.putOnFrames(0, 0, box1);

            BoundingBoxWriter writer = new BoundingBoxWriter();
            writer.setSourceMedium(sourceFile.toURI());
            writer.setDestinationMedium(destinationFile.toURI());

            // writer.setMediaMetadata(Map.of("ROTATION", "90", "HORIZONTAL_FLIP", "false")); // DEBUG: Lenna-90ccw-exif.jpg
            // writer.setMediaMetadata(Map.of("ROTATION", "0", "HORIZONTAL_FLIP", "true")); // DEBUG: Lenna-flip-exif.jpg
            // writer.setMediaMetadata(Map.of("ROTATION", "0", "HORIZONTAL_FLIP", "false")); // DEBUG: Lenna.png
            // writer.setMediaMetadata(Map.of("ROTATION", "180", "HORIZONTAL_FLIP", "false")); // DEBUG: Lenna-180ccw-exif.png
            // writer.setMediaMetadata(Map.of("ROTATION", "90", "HORIZONTAL_FLIP", "true")); // DEBUG: Lenna-flip-90ccw-exif.png

            // writer.setBoundingBoxMap(map);
            // writer.markupImage(); // DEBUG
            // writer.markupVideo();

            for (int i = 0; i <= 360; i+=10) {
                writer.setMediaMetadata(Map.of("ROTATION", Integer.toString(i), "HORIZONTAL_FLIP", "false")); // DEBUG
                writer.setBoundingBoxMap(map);
                writer.markupImage(); // DEBUG
            }

            for (int i = 0; i <= 360; i+=10) {
                writer.setMediaMetadata(Map.of("ROTATION", Integer.toString(i), "HORIZONTAL_FLIP", "true")); // DEBUG
                writer.setBoundingBoxMap(map);
                writer.markupImage(); // DEBUG
            }


            // Test that something was written.
            Assert.assertTrue("The size of the output video must be greater than 4096.", destinationFile.length() > 4096);

        } catch (IOException ioe) {
            Assert.fail(String.format("Encountered an exception when none was expected. %s", ioe));
        }
    }
}
