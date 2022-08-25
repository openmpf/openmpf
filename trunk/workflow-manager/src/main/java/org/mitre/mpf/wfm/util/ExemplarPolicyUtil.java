/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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

import org.mitre.mpf.wfm.data.entities.transients.Detection;

import java.util.Comparator;
import java.util.SortedSet;

public class ExemplarPolicyUtil {

    public static final String PROPERTY = "EXEMPLAR_POLICY";

    private ExemplarPolicyUtil() {
    }

    public static Detection getExemplar(String policy, int begin, int end,
                                        SortedSet<Detection> detections) {
        if (detections.isEmpty()) {
            return null;
        }
        else if (detections.size() == 1 || "FIRST".equalsIgnoreCase(policy)) {
            return detections.first();
        }
        else if ("LAST".equalsIgnoreCase(policy)) {
            return detections.last();
        }
        else if ("MIDDLE".equalsIgnoreCase(policy)) {
            return findMiddle(begin, end, detections);
        }
        else {
            return detections.stream()
                    .max(Comparator.comparingDouble(Detection::getConfidence))
                    .orElse(null);
        }
    }

    private static Detection findMiddle(int begin, int end, SortedSet<Detection> detections) {
        if (detections.isEmpty()) {
            return null;
        }

        int middleFrame = (begin + end) / 2;
        var iter = detections.iterator();
        var minDet = iter.next();
        int minDist = Math.abs(minDet.getMediaOffsetFrame() - middleFrame);

        while (iter.hasNext()) {
            var current = iter.next();
            int currentDist = Math.abs(current.getMediaOffsetFrame() - middleFrame);
            if (currentDist < minDist) {
                minDet = current;
                minDist = currentDist;
            }
            else {
                break;
            }
        }
        return minDet;
    }
}
