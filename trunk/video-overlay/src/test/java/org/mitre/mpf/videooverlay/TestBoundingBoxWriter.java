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
        writeBoxOnFrames("samples/lp-ferrari-texas-shortened.mp4");
    }

    @Test
    public void testWriterOnGif() {
        writeBoxOnFrames("samples/face-morphing.gif");
    }


    private static void writeBoxOnFrames(String filePath) {
        try {
            // File sourceFile = new File("/media/SANDISK/SAMPLES/4kSampleFiles/News_H264_short.mp4"); // JniTestUtils.getFileResource(filePath));
            File sourceFile = new File("/home/mpf/Desktop/SAMPLES/new_face_video.avi");
            // File sourceFile = new File("/home/mpf/git/openmpf-projects/openmpf-components/cpp/OcvYoloDetection/test/data/dog-100x100.jpg");

            if (!sourceFile.exists()) {
                throw new IOException(String.format("File not found %s.", sourceFile.getAbsolutePath()));
            }

            File destinationFile = File.createTempFile("markedup", ".webm");
            // File destinationFile = File.createTempFile("markedup", ".png");
            destinationFile.deleteOnExit();

            BoundingBoxMap map = new BoundingBoxMap();

            {
                for (int i = 1; i <= 10; i += 2) {
                    BoundingBox box = new BoundingBox(10, i * 80, 100, 50, 0, false, 255, 0, 0, BoundingBoxSource.DETECTION_ALGORITHM,
                            true, false, Optional.of("WWWWWWWWWW 888.888"));
                    map.putOnFrame(0, box);
                }
            }
            /*
            {
                BoundingBox box = new BoundingBox(130, 30, 100, 50, 0, false, 0, 255, 0, BoundingBoxSource.TRACKING_FILLED_GAP,
                        true, false, Optional.of("wwwwwwwwww 7.243")); // "WWWWWWWWWW... 7.243"
                map.putOnFrames(6, 10, box);
            }

            {
                BoundingBox box = new BoundingBox(130, 30, 100, 50, 0, false, 255, 255, 0, BoundingBoxSource.DETECTION_ALGORITHM,
                        false, false, Optional.of("wwwwwwwwww 7.243")); // "WWWWWWWWWW... 7.243"
                map.putOnFrames(11, 14, box);

                box = new BoundingBox(130, 30, 100, 50, 0, false, 255, 255, 0, BoundingBoxSource.DETECTION_ALGORITHM,
                        false, true, Optional.of("wwwwwwwwww 7.243")); // "WWWWWWWWWW... 7.243"
                map.putOnFrame(15, box);
            }
            {
                BoundingBox box = new BoundingBox(130, 30, 100, 50, 0, false, 0, 255, 255, BoundingBoxSource.DETECTION_ALGORITHM,
                        false, false, Optional.of("wwwwwwwwww 7.243")); // "WWWWWWWWWW... 7.243"
                map.putOnFrames(16, 20, box);
            }
            */

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
                    "MARKUP_VIDEO_VP9_CRF", "31" // poor quality makes the test run faster
            ));

            writer.setBoundingBoxMap(map);
            writer.markupVideo();
            // writer.markupImage();

            // Test that something was written.
            Assert.assertTrue("The size of the output video must be greater than 4096.", destinationFile.length() > 4096);

        } catch (IOException ioe) {
            Assert.fail(String.format("Encountered an exception when none was expected. %s", ioe));
        }
    }
}
