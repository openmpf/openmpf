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


package org.mitre.mpf.wfm.util;

import com.google.common.collect.BoundType;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;
import com.google.common.collect.TreeRangeSet;
import org.mitre.mpf.wfm.data.entities.persistent.Media;

import java.util.HashSet;
import java.util.Set;

public class UserSpecifiedRangesUtil {

    private UserSpecifiedRangesUtil() {
    }


    public static Set<TimePair> getCombinedRanges(Media media) {
        if (media.getFrameRanges().isEmpty()
                && media.getTimeRanges().isEmpty()) {
            return Set.of(new TimePair(0, media.getLength() - 1));
        }

        var rangeSet = TreeRangeSet.<Integer>create();
        for (var frameRange : media.getFrameRanges()) {
            rangeSet.add(createRange(frameRange.getStartInclusive(),
                                     frameRange.getEndInclusive()));
        }

        var frameTimeInfo = media.getFrameTimeInfo();
        for (var timeRange : media.getTimeRanges()) {
            int beginFrame = frameTimeInfo.getFrameFromTimeMs(timeRange.getStartInclusive());
            int endFrame = frameTimeInfo.getFrameFromTimeMs(timeRange.getEndInclusive());
            rangeSet.add(createRange(beginFrame, endFrame));
        }

        var boundedRange = rangeSet.subRangeSet(createRange(0, media.getLength() - 1));

        var segments = new HashSet<TimePair>();
        for (var range : boundedRange.asRanges()) {
            int begin = range.lowerBoundType() == BoundType.CLOSED
                    ? range.lowerEndpoint()
                    : range.lowerEndpoint() + 1;

            int end = range.upperBoundType() == BoundType.CLOSED
                    ? range.upperEndpoint()
                    : range.upperEndpoint() - 1;
            segments.add(new TimePair(begin, end));
        }
        return segments;
    }


    private static Range<Integer> createRange(int begin, int end) {
        // .canonical(DiscreteDomain.integers()) is required for the RangeSet to join adjacent
        // ranges.
        return Range.closed(begin, end).canonical(DiscreteDomain.integers());
    }
}
