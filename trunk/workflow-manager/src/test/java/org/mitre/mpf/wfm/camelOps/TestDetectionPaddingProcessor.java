/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2020 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2020 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.camelOps;

import org.junit.Test;
import org.mitre.mpf.wfm.camel.operations.detection.padding.DetectionPaddingProcessor;
import org.mitre.mpf.wfm.data.entities.transients.Detection;

import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class TestDetectionPaddingProcessor {

    @Test
    public void testPaddingWithoutRotation() {
        assertPadding(3, 3, 4, 4, "25%", "25%", 10, 10, 2, 2, 6, 6);   // expand uniformly
        assertPadding(3, 3, 4, 4, "0%", "25%", 10, 10, 3, 2, 4, 6);    // expand height only
        assertPadding(3, 3, 4, 4, "25%", "0%", 10, 10, 2, 3, 6, 4);    // expand width only
        assertPadding(3, 3, 4, 4, "0", "0", 10, 10, 3, 3, 4, 4);       // no-op

        assertPadding(5, 5, 4, 4, "50%", "50%", 10, 10, 3, 3, 7, 7);   // over-expand towards bottom right
        assertPadding(1, 1, 4, 4, "50%", "50%", 10, 10, 0, 0, 7, 7);   // over-expand towards top left
        assertPadding(1, 5, 4, 4, "50%", "50%", 10, 10, 0, 3, 7, 7);   // over-expand towards bottom left
        assertPadding(5, 1, 4, 4, "50%", "50%", 10, 10, 3, 0, 7, 7);   // over-expand towards top right

        assertPadding(5, 5, 4, 4, "25%", "75%", 10, 10, 4, 2, 6, 8);   // over-expand towards bottom
        assertPadding(5, 5, 4, 4, "25%", "300%", 10, 10, 4, 0, 6, 10); // over-expand towards top and bottom

        assertPadding(5, 5, 4, 4, "75%", "25%", 10, 10, 2, 4, 8, 6);   // over-expand towards right
        assertPadding(5, 5, 4, 4, "300%", "25%", 10, 10, 0, 4, 10, 6); // over-expand towards left and right

        assertPadding(3, 3, 4, 4, "1", "25%", 10, 10, 2, 2, 6, 6); // expand uniformly
        assertPadding(40, 40, 20, 20, "200%", "200%", 100, 100, 0, 0, 100, 100); // expand uniformly
        assertPadding(40, 40, 20, 20, "40", "40", 100, 100, 0, 0, 100, 100);     // expand uniformly
        assertPadding(40, 40, 20, 20, "200%", "40", 100, 100, 0, 0, 100, 100);   // expand uniformly

        assertPadding(40, 40, 20, 20, "-25%", "-25%", 100, 100, 45, 45, 10, 10); // shrink uniformly
        assertPadding(40, 40, 20, 20, "-25%", "-5", 100, 100, 45, 45, 10, 10);   // shrink uniformly
        assertShrinkToNothing(40, 40, 20, 20, "-50%", "-50%", 100, 100, 50, 50, 1, 1);   // shrink to nothing
        assertShrinkToNothing(40, 40, 20, 20, "-100%", "-277", 100, 100, 50, 50, 1, 1);  // shrink beyond nothing
        assertShrinkToNothing(40, 40, 20, 20, "-500%", "-500%", 100, 100, 50, 50, 1, 1); // shrink beyond nothing
        assertShrinkToNothing(40, 40, 20, 20, "-500%", "-25%", 100, 100, 50, 45, 1, 10); // shrink beyond nothing (width)
        assertShrinkToNothing(40, 40, 20, 20, "-25%", "-500%", 100, 100, 45, 50, 10, 1); // shrink beyond nothing (height)

        assertPadding(40, 40, 20, 20, "100%", "-5", 100, 100, 20, 45, 60, 10); // expand and shrink

        assertShrinkToNothing(20, 20, 7, 7, "-100%", "-100%", 40, 40, 23, 23, 1, 1); // shrink beyond nothing (not exact)
        assertShrinkToNothing(20, 20, 7, 7, "-7", "-7", 40, 40, 23, 23, 1, 1);       // shrink beyond nothing (not exact)
        assertPadding(20, 20, 7, 7, "25%", "25%", 40, 40, 18, 18, 11, 11); // expand uniformly (not exact)
        assertPadding(20, 20, 7, 7, "21%", "21%", 40, 40, 18, 18, 10, 10); // expand uniformly (not exact)
        assertPadding(20, 20, 7, 7, "-21%", "-21%", 40, 40, 21, 21, 5, 5); // shrink uniformly (not exact)

        assertPadding(6, 4, 4, 6, "50%", "33.3%", 10, 20, 4, 2, 6, 10); // over-expand towards right (height != width)
        assertPadding(6, 4, 4, 6, "50%", "33.3%", 20, 10, 4, 2, 8, 8);  // over-expand towards bottom (height != width)
    }


    @Test
    public void testRotatedClippingOrthogonal() {
        {
            Detection input = createDetection(0, 479, 480, 640, "90");
            Detection actual = DetectionPaddingProcessor.padDetection(
                    "200%", "200%", 640, 480, input);
            // Input detection is already at max possible size.
            assertEquals(input, actual);
        }

        {
            Detection input = createDetection(300, 100, 400, 100, "90");
            Detection actual = DetectionPaddingProcessor.padDetection(
                    "50%", "0", 500, 500, input);
            Detection expected= createDetection(300, 300, 301, 100, "90");
            assertEquals(expected, actual);
        }
    }


    @Test
    public void testRotatedClippingNonOrthogonal() {
        {
            Detection input = createDetection(30, 40, 580, 400, "20");
            Detection padded = DetectionPaddingProcessor.padDetection(
                    "1000%", "1000%", 640, 480, input);
            Detection expected = createDetection(-153, 56, 766, 670, "20");
            assertEquals(expected, padded);
        }

        {
            Detection input = createDetection(300, 100, 400, 100, "45");
            Detection padded = DetectionPaddingProcessor.padDetection(
                    "0", "0", 500, 500, input);
            Detection expected = createDetection(300, 100, 213, 100, "45");
            assertEquals(expected, padded);
        }

        {
            Detection input = createDetection(300, 100, 400, 100, "45");
            Detection actual = DetectionPaddingProcessor.padDetection(
                    "50%", "0", 500, 500, input);
            Detection expected = createDetection(158, 241, 413, 100, "45");
            assertEquals(expected, actual);
        }

        {
            Detection input = createDetection(300, 100, 400, 100, "225");
            Detection actual = DetectionPaddingProcessor.padDetection(
                    "100%", "0", 500, 500, input);
            Detection expected = createDetection(449, -49, 707, 100, "225");
            assertEquals(expected, actual);
        }

        {
            Detection input = createDetection(300, 100, 400, 100, "225");
            Detection actual = DetectionPaddingProcessor.padDetection(
                    "50%", "0", 500, 500, input);
            Detection expected = createDetection(441, -41, 696, 100, "225");
            assertEquals(expected, actual);
        }
    }


    @Test
    public void testRotationWithoutClippingNonOrthogonal() {

        Detection input = createDetection(96, 140, 190, 42, "18.74");
        {
            Detection expected = createDetection(6, 170, 380, 42, "18.74");
            Detection actual = DetectionPaddingProcessor.padDetection(
                    "50%", "0", 1000, 1000, input);
            assertEquals(expected, actual);
        }

        {
            Detection expected = createDetection(89, 120, 190, 84, "18.74");
            Detection actual = DetectionPaddingProcessor.padDetection(
                    "0%", "50%", 1000, 1000, input);
            assertEquals(expected, actual);
        }

        {
            Detection expected = createDetection(0, 150, 380, 84, "18.74");
            Detection actual = DetectionPaddingProcessor.padDetection(
                    "50%", "50%", 1000, 1000, input);
            assertEquals(expected, actual);
        }

        {
            Detection actual = DetectionPaddingProcessor.padDetection(
                    "0", "0", 500, 500, input);
            assertEquals(input, actual);
        }
    }


    @Test
    public void testRotationWithoutClippingOrthogonal() {

        Detection input = createDetection(50, 60, 20, 40, "90");
        {
            Detection expected = createDetection(26, 60, 20, 88, "90");
            Detection actual = DetectionPaddingProcessor.padDetection(
                    "0", "60%", 1000, 1000, input);
            assertEquals(expected, actual);
        }

        {
            Detection expected = createDetection(50, 58, 16, 40, "90");
            Detection actual = DetectionPaddingProcessor.padDetection(
                    "-10%", "0", 1000, 1000, input);
            assertEquals(expected, actual);
        }

        {
            Detection expected = createDetection(26, 58, 16, 88, "90");
            Detection actual = DetectionPaddingProcessor.padDetection(
                    "-10%", "60%", 1000, 1000, input);
            assertEquals(expected, actual);
        }
    }


    private static Detection createDetection(int x, int y, int width, int height, String rotation) {

        Map<String, String> detectionProperties = rotation == null
                ? Collections.emptyMap()
                : Collections.singletonMap("ROTATION", rotation);

        return new Detection(x, y, width, height, 1, 0, 0,
                             detectionProperties);
    }


    private static void assertPadding(int x, int y, int width, int height, String xPadding, String yPadding,
                                      int frameWidth, int frameHeight,
                                      int expectedX, int expectedY, int expectedWidth, int expectedHeight) {
        assertPadding(x, y, width, height, xPadding, yPadding, frameWidth, frameHeight,
                expectedX, expectedY, expectedWidth, expectedHeight, false);
    }


    private static void assertShrinkToNothing(int x, int y, int width, int height, String xPadding, String yPadding,
                                              int frameWidth, int frameHeight,
                                              int expectedX, int expectedY, int expectedWidth, int expectedHeight) {
        assertPadding(x, y, width, height, xPadding, yPadding, frameWidth, frameHeight,
                expectedX, expectedY, expectedWidth, expectedHeight, true);
    }


    private static void assertPadding(int x, int y, int width, int height, String xPadding, String yPadding,
                                      int frameWidth, int frameHeight,
                                      int expectedX, int expectedY, int expectedWidth, int expectedHeight,
                                      boolean shrinkToNothing) {
        Detection detection = new Detection(x, y, width, height, -1, 0, 0, Collections.emptyMap());
        Detection expectedDetection = new Detection(expectedX, expectedY, expectedWidth, expectedHeight, -1, 0, 0,
                shrinkToNothing ? Collections.singletonMap("SHRUNK_TO_NOTHING", "TRUE") : Collections.emptyMap());
        Detection newDetection = DetectionPaddingProcessor.padDetection(xPadding, yPadding, frameWidth, frameHeight,
                detection);
        assertEquals(expectedDetection, newDetection);
    }
}
