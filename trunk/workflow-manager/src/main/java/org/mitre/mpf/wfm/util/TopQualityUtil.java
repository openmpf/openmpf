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

import java.util.Collection;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.function.ToDoubleFunction;

import org.mitre.mpf.wfm.data.entities.transients.Detection;

public class TopQualityUtil {

    private TopQualityUtil() {
    }


    public static <T extends Comparable<T>> T getTopQualityItem(
            Collection<T> items, ToDoubleFunction<T> qualityGetter) {
        return items.stream()
            .max(getMaxQualityComparator(qualityGetter))
            .orElse(null);
    }


    public static Collection<Detection> getTopQualityDetections(
            Collection<Detection> allDetections, int topQualityCount, String qualityProp) {
        if (topQualityCount <= 0 || topQualityCount >= allDetections.size()) {
            return allDetections;
        }

        var qualityComparator = getMaxQualityComparator(Detection::getConfidence);
        var topDetections = new PriorityQueue<>(topQualityCount, qualityComparator);

        var allDetectionsIter = allDetections.iterator();
        for (int i = 0; i < topQualityCount; i++) {
            topDetections.add(allDetectionsIter.next());
        }

        while (allDetectionsIter.hasNext()) {
            Detection detection = allDetectionsIter.next();
            // Check if current detection is less than the minimum top detection so far
            if (qualityComparator.compare(detection, topDetections.peek()) > 0) {
                topDetections.poll();
                topDetections.add(detection);
            }
        }
        return topDetections;
    }


    private static <T extends Comparable<T>>
            Comparator<T> getMaxQualityComparator(ToDoubleFunction<T> qualityGetter) {
        return Comparator.comparingDouble(qualityGetter)
                .thenComparing(Comparator.reverseOrder());
    }
}
