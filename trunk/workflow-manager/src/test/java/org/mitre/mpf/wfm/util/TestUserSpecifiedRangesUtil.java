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

import com.google.common.collect.ImmutableSortedSet;
import org.junit.Test;
import org.mitre.mpf.wfm.data.entities.persistent.Media;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestUserSpecifiedRangesUtil {

    @Test
    public void returnsFullVideoWhenNoSpecifiedSegments() {
        var results = getCombinedSegments(ImmutableSortedSet.of(), ImmutableSortedSet.of());
        var expected = ImmutableSortedSet.of(new TimePair(0, 10));
        assertEquals(expected, results);
    }


    @Test
    public void canCombineSegments() {
        var frameRanges = ImmutableSortedSet.of(
                new TimePair(1, 3),
                new TimePair(9, 100),
                new TimePair(14, 16)
        );
        var timeRanges = ImmutableSortedSet.of(
                new TimePair(167, 200), // frames 4 - 5
                new TimePair(1000, 2000) // Beyond end of video
        );

        var results = getCombinedSegments(frameRanges, timeRanges);

        var expected = ImmutableSortedSet.of(
                new TimePair(1, 5),
                new TimePair(9, 10));

        assertEquals(expected, results);
    }


    @Test
    public void doesNotCombineNonAdjacentRanges() {
        {
            var frameRanges = ImmutableSortedSet.of(
                    new TimePair(1, 3),
                    new TimePair(5, 6)
            );
            var results = getCombinedSegments(frameRanges, ImmutableSortedSet.of());
            assertEquals(frameRanges, results);
        }
        {
            var frameRanges = ImmutableSortedSet.of(new TimePair(1, 3));
            var timeRanges = ImmutableSortedSet.of(new TimePair(200, 400));
            var results = getCombinedSegments(frameRanges, timeRanges);
            var expected = ImmutableSortedSet.of(
                    new TimePair(1, 3),
                    new TimePair(5, 10)
            );
            assertEquals(expected, results);
        }
    }


    @Test
    public void canHandleTimeBeforeStart() {
        {
            var timeRanges = ImmutableSortedSet.of(
                    new TimePair(0, 10)
            );
            var results = getCombinedSegments(
                    ImmutableSortedSet.of(), timeRanges);
            var expected = ImmutableSortedSet.of(new TimePair(0, 0));
            assertEquals(expected, results);
        }
        {
            var timeRanges = ImmutableSortedSet.of(
                    new TimePair(0, 40)
            );
            var results = getCombinedSegments(
                    ImmutableSortedSet.of(), timeRanges);
            var expected = ImmutableSortedSet.of(new TimePair(0, 0));
            assertEquals(expected, results);
        }
        {
            var timeRanges = ImmutableSortedSet.of(
                    new TimePair(0, 80)
            );
            var results = getCombinedSegments(
                    ImmutableSortedSet.of(), timeRanges);
            var expected = ImmutableSortedSet.of(new TimePair(0, 1));
            assertEquals(expected, results);
        }
    }


    private static ImmutableSortedSet<TimePair> getCombinedSegments(
            ImmutableSortedSet<TimePair> frameRanges,
            ImmutableSortedSet<TimePair> timeRanges) {

        var media = createTestMedia(frameRanges, timeRanges);
        return UserSpecifiedRangesUtil.getCombinedRanges(media);
    }


    private static Media createTestMedia(
            ImmutableSortedSet<TimePair> frameRanges,
            ImmutableSortedSet<TimePair> timeRanges) {
        int[] frameTimes = {
                33,  // 0
                66,  // 1
                100, // 2
                133, // 3
                166, // 4
                200, // 5
                233, // 6
                266, // 7
                300, // 8
                333, // 9
                366, // 10
        };

        var timeInfo = FrameTimeInfo.forVariableFrameRate(29.97, frameTimes, false);

        var media = mock(Media.class);
        when(media.getLength())
                .thenReturn(frameTimes.length);
        when(media.getFrameRanges())
                .thenReturn(frameRanges);
        when(media.getTimeRanges())
                .thenReturn(timeRanges);
        when(media.getFrameTimeInfo())
                .thenReturn(timeInfo);
        return media;
    }
}
