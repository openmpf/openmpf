/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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
package org.mitre.mpf.wfm.util;


import com.google.common.collect.ImmutableSortedSet;
import org.junit.Test;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.data.entities.transients.Detection;

import java.util.ArrayList;
import java.util.Map;
import java.util.SortedSet;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class TestTopQualitySelectionUtil {
    private final Detection _d50 = createDetection(50, 0.3);
    private final Detection _d51 = createDetection(51, 0.9);
    private final Detection _d52 = createDetection(52, 0.2);
    private final Detection _d54 = createDetection(54, 0.1);
    private final Detection _d60 = createDetection(60, 0.5);

    private final SortedSet<Detection> _detections = ImmutableSortedSet.of(
            _d50, _d51, _d52, _d54, _d60);

    private final Detection _d61 = createDetection(61, 0.3, "QUALITY_PROP", 0.95);
    private final Detection _d62 = createDetection(62, 0.9, "QUALITY_PROP", 0.1);
    private final Detection _d63 = createDetection(63, 0.2, "QUALITY_PROP", 0.6);
    private final Detection _d64 = createDetection(64, 0.1, "QUALITY_PROP", 0.4);
    private final Detection _d65 = createDetection(65, 0.5, "QUALITY_PROP", 0.7);

    private final SortedSet<Detection> _detectionsWithProp = ImmutableSortedSet.of(
            _d61, _d62, _d63, _d64, _d65);

    @Test
    public void testGetTopQualityItem() {
        assertSame(_d51, TopQualitySelectionUtil.getTopQualityItem(_detections, "CONFIDENCE"));
        assertSame(_d51, TopQualitySelectionUtil.getTopQualityItem(_detections, ""));
        assertSame(_d51, TopQualitySelectionUtil.getTopQualityItem(_detections, null));
        assertSame(_d61, TopQualitySelectionUtil.getTopQualityItem(_detectionsWithProp, "QUALITY_PROP"));
        assertSame(_d62, TopQualitySelectionUtil.getTopQualityItem(_detectionsWithProp, "CONFIDENCE"));
    }

    @Test
    public void testGetTopQualityWithSingleDetection() {
        ArrayList<Detection> detList = new ArrayList<>();
        detList.add(_d51);
        assertSame(_d51, TopQualitySelectionUtil.getTopQualityItem(detList, "CONFIDENCE"));
        assertSame(_d51, TopQualitySelectionUtil.getTopQualityItem(detList, ""));
        assertSame(_d51, TopQualitySelectionUtil.getTopQualityItem(detList, null));
        assertSame(detList, TopQualitySelectionUtil.getTopQualityDetections(detList, 1, "CONFIDENCE"));
    }

    @Test
    public void testEqualMaxConfidence() {
        var d61 = createDetection(61, _d60.getConfidence());
        var detections = ImmutableSortedSet.of(_d50, _d52, _d54, _d60, d61);
        assertSame(_d60, TopQualitySelectionUtil.getTopQualityItem(detections, "CONFIDENCE"));
        var d71 = createDetection(71, (double)_d61.getConfidence(), "QUALITY_PROP", 0.95);
        var detectionsWithProp = ImmutableSortedSet.of(_d61, _d62, _d63, _d64, d71);
        assertSame(_d61, TopQualitySelectionUtil.getTopQualityItem(detectionsWithProp, "QUALITY_PROP"));
    }

    @Test
    public void testGetTopQualityDetections() {
        ArrayList<Detection> subsetDetections = new ArrayList<>();
        subsetDetections.add(_d50);
        subsetDetections.add(_d51);
        subsetDetections.add(_d60);
        var resultWithConfidence = new ArrayList<Detection>(TopQualitySelectionUtil.getTopQualityDetections(_detections, 3, "CONFIDENCE"));
        assertArrayEquals(subsetDetections.toArray(), resultWithConfidence.toArray());
        var resultWithNullProp = new ArrayList<Detection>(TopQualitySelectionUtil.getTopQualityDetections(_detections, 3, null));
        assertArrayEquals(subsetDetections.toArray(), resultWithNullProp.toArray());
        var resultWithEmptyProp = new ArrayList<Detection>(TopQualitySelectionUtil.getTopQualityDetections(_detections, 3, ""));
        assertArrayEquals(subsetDetections.toArray(), resultWithEmptyProp.toArray());
        ArrayList<Detection> subsetWithProp = new ArrayList<>();
        subsetWithProp.add(_d65);
        subsetWithProp.add(_d61);
        var resultWithProp = new ArrayList<Detection>(TopQualitySelectionUtil.getTopQualityDetections(_detectionsWithProp, 2, "QUALITY_PROP"));
        assertArrayEquals(subsetWithProp.toArray(), resultWithProp.toArray());
    }

    @Test
    public void TestNumberFormatException() {
        ArrayList<Detection> detList = new ArrayList<>();
        Detection det1 = new Detection(1, 1, 1, 1, (float)0.5,
                             1, 1, Map.of("quality_prop", "foobar"));
        Detection det2 = new Detection(1, 1, 1, 1, (float)0.5,
                                                  1, 1, Map.of("quality_prop", "foobar"));
        detList.add(det1);
        detList.add(det2);

        String expectedString = "The quality selection property \"quality_prop\" could not be converted to a double value: \"foobar\"";
        try {
            var topDet = TopQualitySelectionUtil.getTopQualityDetections(detList, 1, "quality_prop");
            fail("Expected NumberFormatException to be thrown");
        }
        catch (NumberFormatException ex) {
            assertEquals(expectedString, ex.getMessage());
        }
        try {
            var topDet = TopQualitySelectionUtil.getTopQualityItem(detList, "quality_prop");
            fail("Expected NumberFormatException to be thrown");
        }
        catch (NumberFormatException ex) {
            assertEquals(expectedString, ex.getMessage());
        }
    }

    private static Detection createDetection(int frame, double confidence) {
        return new Detection(1, 1, 1, 1, (float) confidence, frame, 1, Map.of());
    }

    private static Detection createDetection(int frame, double confidence, String propName, double propValue) {
        return new Detection(1, 1, 1, 1, (float) confidence, frame, 1,
                             Map.of(propName,String.valueOf(propValue)));
    }
}
