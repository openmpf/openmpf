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
import org.mitre.mpf.wfm.data.entities.transients.Detection;

import java.util.Map;
import java.util.SortedSet;

import static org.junit.Assert.assertSame;

public class TestExemplarPolicyUtil {
    private final Detection _d50 = createDetection(50, 0.3);
    private final Detection _d51 = createDetection(51, 0.9);
    private final Detection _d52 = createDetection(52, 0.2);
    private final Detection _d54 = createDetection(54, 0.1);
    private final Detection _d60 = createDetection(60, 0.5);

    private final SortedSet<Detection> _detections = ImmutableSortedSet.of(
            _d50, _d51, _d52, _d54, _d60);


    @Test
    public void testMatchingBounds() {
        assertSame(_d50, ExemplarPolicyUtil.getExemplar("FIRST", "", 50, 60, _detections));
        assertSame(_d60, ExemplarPolicyUtil.getExemplar("LAST", "", 50, 60, _detections));
        assertSame(_d54, ExemplarPolicyUtil.getExemplar("MIDDLE", "", 50, 60, _detections));
        assertSame(_d51, ExemplarPolicyUtil.getExemplar("CONFIDENCE", "CONFIDENCE", 50, 60, _detections));
        assertSame(_d51, ExemplarPolicyUtil.getExemplar("", "CONFIDENCE", 50, 60, _detections));
    }


    @Test
    public void testMiddleIsFirst() {
        assertSame(_d50, ExemplarPolicyUtil.getExemplar("MIDDLE", "", 0, 70, _detections));
    }

    @Test
    public void testMiddleIsLast() {
        assertSame(_d60, ExemplarPolicyUtil.getExemplar("MIDDLE", "", 50, 100, _detections));
    }

    @Test
    public void testMiddleIsMiddleElement() {
        assertSame(_d52, ExemplarPolicyUtil.getExemplar("MIDDLE", "", 0, 104, _detections));
    }

    @Test
    public void testEqualMaxConfidence() {
        var d61 = createDetection(61, _d60.getConfidence());
        var detections = ImmutableSortedSet.of(_d50, _d52, _d54, _d60, d61);
        assertSame(_d60, ExemplarPolicyUtil.getExemplar("CONFIDENCE", "CONFIDENCE", 0, 100, detections));
    }

    private static Detection createDetection(int frame, double confidence) {
        return new Detection(1, 1, 1, 1, (float) confidence, frame, 1, Map.of());
    }
}
