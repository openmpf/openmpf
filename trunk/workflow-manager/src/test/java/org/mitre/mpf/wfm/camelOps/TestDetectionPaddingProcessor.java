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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestDetectionPaddingProcessor {
    
    @Test
    public void testPaddingWithClipToFrame() {
        assertPadding(3, 3, 4, 4,  "25%", "25%", 10, 10, true,  2, 2, 6, 6);   // expand uniformly
        assertPadding(3, 3, 4, 4,  "0%", "25%", 10, 10, true,  3, 2, 4, 6);    // expand height only
        assertPadding(3, 3, 4, 4,  "25%", "0%", 10, 10, true,  2, 3, 6, 4);    // expand width only
        assertPadding(3, 3, 4, 4,  "0", "0", 10, 10, true,  3, 3, 4, 4);       // no-op

        assertPadding(5, 5, 4, 4,  "50%", "50%", 10, 10, true,  3, 3, 7, 7);   // over-expand towards bottom right
        assertPadding(1, 1, 4, 4,  "50%", "50%", 10, 10, true,  0, 0, 7, 7);   // over-expand towards top left
        assertPadding(1, 5, 4, 4,  "50%", "50%", 10, 10, true,  0, 3, 7, 7);   // over-expand towards bottom left
        assertPadding(5, 1, 4, 4,  "50%", "50%", 10, 10, true,  3, 0, 7, 7);   // over-expand towards top right

        assertPadding(5, 5, 4, 4,  "25%", "75%", 10, 10, true,  4, 2, 6, 8);   // over-expand towards bottom
        assertPadding(5, 5, 4, 4,  "25%", "300%", 10, 10, true,  4, 0, 6, 10); // over-expand towards top and bottom

        assertPadding(5, 5, 4, 4,  "75%", "25%", 10, 10, true,  2, 4, 8, 6);   // over-expand towards right
        assertPadding(5, 5, 4, 4,  "300%", "25%", 10, 10, true,  0, 4, 10, 6); // over-expand towards left and right

        assertPadding(3, 3, 4, 4,  "1", "25%", 10, 10, true,  2, 2, 6, 6); // expand uniformly
        assertPadding(40, 40, 20, 20,  "200%", "200%", 100, 100, true,  0, 0, 100, 100); // expand uniformly
        assertPadding(40, 40, 20, 20,  "40", "40", 100, 100, true,  0, 0, 100, 100);     // expand uniformly
        assertPadding(40, 40, 20, 20,  "200%", "40", 100, 100, true,  0, 0, 100, 100);   // expand uniformly

        assertPadding(40, 40, 20, 20,  "-25%", "-25%", 100, 100, true,  45, 45, 10, 10); // shrink uniformly
        assertPadding(40, 40, 20, 20,  "-25%", "-5", 100, 100, true,  45, 45, 10, 10);   // shrink uniformly
        assertShrinkToNothing(40, 40, 20, 20,  "-50%", "-50%", 100, 100, true);          // shrink to nothing
        assertShrinkToNothing(40, 40, 20, 20,  "-100%", "-277", 100, 100, true);         // shrink beyond nothing
        assertShrinkToNothing(40, 40, 20, 20,  "-500%", "-500%", 100, 100, true);        // shrink beyond nothing

        assertPadding(40, 40, 20, 20,  "100%", "-5", 100, 100, true,  20, 45, 60, 10); // expand and shrink

        assertShrinkToNothing(20, 20, 7, 7,  "-100%", "-100%", 40, 40, true);      // shrink beyond nothing (not exact)
        assertShrinkToNothing(20, 20, 7, 7,  "-7", "-7", 40, 40, true);            // shrink beyond nothing (not exact)
        assertPadding(20, 20, 7, 7,  "25%", "25%", 40, 40, true,  18, 18, 11, 11); // expand uniformly (not exact)
        assertPadding(20, 20, 7, 7,  "21%", "21%", 40, 40, true,  18, 18, 11, 11); // expand uniformly (not exact)
        assertPadding(20, 20, 7, 7,  "-21%", "-21%", 40, 40, true,  22, 22, 3, 3); // shrink uniformly (not exact)

        assertPadding(6, 4, 4, 6,  "50%", "33.3%", 10, 20, true,  4, 2, 6, 10); // over-expand towards right (height != width)
        assertPadding(6, 4, 4, 6,  "50%", "33.3%", 20, 10, true,  4, 2, 8, 8);  // over-expand towards bottom (height != width)
    }


    @Test
    public void testPaddingWithNoClipToFrame() {
        assertPadding(3, 3, 4, 4,  "25%", "25%", 10, 10, false,  2, 2, 6, 6);   // expand uniformly
        assertPadding(3, 3, 4, 4,  "0%", "25%", 10, 10, false,  3, 2, 4, 6);    // expand height only
        assertPadding(3, 3, 4, 4,  "25%", "0%", 10, 10, false,  2, 3, 6, 4);    // expand width only
        assertPadding(3, 3, 4, 4,  "0", "0", 10, 10, false,  3, 3, 4, 4);       // no-op

        assertPadding(5, 5, 4, 4,  "50%", "50%", 10, 10, false,  3, 3, 8, 8);   // over-expand towards bottom right
        assertPadding(1, 1, 4, 4,  "50%", "50%", 10, 10, false,  -1, -1, 8, 8); // over-expand towards top left
        assertPadding(1, 5, 4, 4,  "50%", "50%", 10, 10, false,  -1, 3, 8, 8);  // over-expand towards bottom left
        assertPadding(5, 1, 4, 4,  "50%", "50%", 10, 10, false,  3, -1, 8, 8);  // over-expand towards top right

        assertPadding(5, 5, 4, 4,  "25%", "75%", 10, 10, false,  4, 2, 6, 10);   // over-expand towards bottom
        assertPadding(5, 5, 4, 4,  "25%", "300%", 10, 10, false,  4, -7, 6, 28); // over-expand towards top and bottom

        assertPadding(5, 5, 4, 4,  "75%", "25%", 10, 10, false,  2, 4, 10, 6);   // over-expand towards right
        assertPadding(5, 5, 4, 4,  "300%", "25%", 10, 10, false,  -7, 4, 28, 6); // over-expand towards left and right

        assertPadding(3, 3, 4, 4,  "1", "25%", 10, 10, false,  2, 2, 6, 6); // expand uniformly
        assertPadding(40, 40, 20, 20,  "200%", "200%", 100, 100, false,  0, 0, 100, 100); // expand uniformly
        assertPadding(40, 40, 20, 20,  "40", "40", 100, 100, false,  0, 0, 100, 100);     // expand uniformly
        assertPadding(40, 40, 20, 20,  "200%", "40", 100, 100, false,  0, 0, 100, 100);   // expand uniformly

        assertPadding(40, 40, 20, 20,  "-25%", "-25%", 100, 100, false,  45, 45, 10, 10); // shrink uniformly
        assertPadding(40, 40, 20, 20,  "-25%", "-5", 100, 100, false,  45, 45, 10, 10);   // shrink uniformly
        assertShrinkToNothing(40, 40, 20, 20,  "-50%", "-50%", 100, 100, false);          // shrink to nothing
        assertShrinkToNothing(40, 40, 20, 20,  "-100%", "-277", 100, 100, false);         // shrink beyond nothing
        assertShrinkToNothing(40, 40, 20, 20,  "-500%", "-500%", 100, 100, false);        // shrink beyond nothing

        assertPadding(40, 40, 20, 20,  "100%", "-5", 100, 100, false,  20, 45, 60, 10); // expand and shrink

        assertShrinkToNothing(20, 20, 7, 7,  "-100%", "-100%", 40, 40, false);      // shrink beyond nothing (not exact)
        assertShrinkToNothing(20, 20, 7, 7,  "-7", "-7", 40, 40, false);            // shrink beyond nothing (not exact)
        assertPadding(20, 20, 7, 7,  "25%", "25%", 40, 40, false,  18, 18, 11, 11); // expand uniformly (not exact)
        assertPadding(20, 20, 7, 7,  "21%", "21%", 40, 40, false,  18, 18, 11, 11); // expand uniformly (not exact)
        assertPadding(20, 20, 7, 7,  "-21%", "-21%", 40, 40, false,  22, 22, 3, 3); // shrink uniformly (not exact)

        assertPadding(6, 4, 4, 6,  "50%", "33.3%", 10, 20, false,  4, 2, 8, 10); // over-expand towards right (height != width)
        assertPadding(6, 4, 4, 6,  "50%", "33.3%", 20, 10, false,  4, 2, 8, 10);  // over-expand towards bottom (height != width)
    }


    private static void assertPadding(int x, int y, int width, int height, String xPadding, String yPadding,
                                                 int frameWidth, int frameHeight, boolean clipToFrame,
                                                 int expectedX, int expectedY, int expectedWidth, int expectedHeight) {
        Detection detection = new Detection(x, y, width, height, -1, 0, 0, Collections.emptyMap());
        Detection expectedDetection = new Detection(expectedX, expectedY, expectedWidth, expectedHeight, -1, 0, 0,
                Collections.emptyMap());
        Detection newDetection = DetectionPaddingProcessor.padDetection(xPadding, yPadding, frameWidth, frameHeight,
                detection, clipToFrame);
        assertEquals(expectedDetection, newDetection);
    }


    private static void assertShrinkToNothing(int x, int y, int width, int height, String xPadding, String yPadding,
                                      int frameWidth, int frameHeight, boolean clipToFrame) {
        Detection detection = new Detection(x, y, width, height, -1, 0, 0, Collections.emptyMap());
        Detection newDetection = DetectionPaddingProcessor.padDetection(xPadding, yPadding, frameWidth, frameHeight,
                detection, clipToFrame);
        assertTrue("Expected SHRINK_TO_NOTHING.",
                newDetection.getDetectionProperties().containsKey("SHRUNK_TO_NOTHING"));
    }
}
