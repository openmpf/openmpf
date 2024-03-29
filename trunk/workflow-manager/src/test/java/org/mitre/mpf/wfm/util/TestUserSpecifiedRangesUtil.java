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

import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.mitre.mpf.wfm.camel.operations.mediainspection.Fraction;
import org.mitre.mpf.wfm.data.entities.persistent.Media;

import java.util.OptionalInt;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestUserSpecifiedRangesUtil {

    @Test
    public void returnsFullVideoWhenNoSpecifiedSegments() {
        var results = getCombinedSegments(ImmutableSet.of(), ImmutableSet.of());
        var expected = ImmutableSet.of(new MediaRange(0, 10));
        assertEquals(expected, results);
    }


    @Test
    public void returnsFullVideoWhenTimesBeyondLimits() {
        var timeRanges = ImmutableSet.of(new MediaRange(1, 1000));
        var results = getCombinedSegments(ImmutableSet.of(), timeRanges);
        var expected = ImmutableSet.of(new MediaRange(0, 10));
        assertEquals(expected, results);
    }


    @Test
    public void canCombineSegments() {
        var frameRanges = ImmutableSet.of(
                new MediaRange(1, 3),
                new MediaRange(9, 100),
                new MediaRange(14, 16)
        );
        var timeRanges = ImmutableSet.of(
                new MediaRange(167, 200), // frames 4 - 5
                new MediaRange(1000, 2000) // Beyond end of video
        );

        var results = getCombinedSegments(frameRanges, timeRanges);

        var expected = ImmutableSet.of(
                new MediaRange(1, 5),
                new MediaRange(9, 10));

        assertEquals(expected, results);
    }


    @Test
    public void doesNotCombineNonAdjacentRanges() {
        {
            var frameRanges = ImmutableSet.of(
                    new MediaRange(1, 3),
                    new MediaRange(5, 6)
            );
            var results = getCombinedSegments(frameRanges, ImmutableSet.of());
            assertEquals(frameRanges, results);
        }
        {
            var frameRanges = ImmutableSet.of(new MediaRange(1, 3));
            var timeRanges = ImmutableSet.of(new MediaRange(200, 400));
            var results = getCombinedSegments(frameRanges, timeRanges);
            var expected = ImmutableSet.of(
                    new MediaRange(1, 3),
                    new MediaRange(5, 10)
            );
            assertEquals(expected, results);
        }
    }


    @Test
    public void canHandleTimeBeforeStart() {
        {
            var timeRanges = ImmutableSet.of(
                    new MediaRange(0, 10)
            );
            var results = getCombinedSegments(
                    ImmutableSet.of(), timeRanges);
            var expected = ImmutableSet.of(new MediaRange(0, 0));
            assertEquals(expected, results);
        }
        {
            var timeRanges = ImmutableSet.of(
                    new MediaRange(0, 40)
            );
            var results = getCombinedSegments(
                    ImmutableSet.of(), timeRanges);
            var expected = ImmutableSet.of(new MediaRange(0, 0));
            assertEquals(expected, results);
        }
        {
            var timeRanges = ImmutableSet.of(
                    new MediaRange(0, 80)
            );
            var results = getCombinedSegments(ImmutableSet.of(), timeRanges);
            var expected = ImmutableSet.of(new MediaRange(0, 1));
            assertEquals(expected, results);
        }
    }


    private static Set<MediaRange> getCombinedSegments(
            ImmutableSet<MediaRange> frameRanges,
            ImmutableSet<MediaRange> timeRanges) {

        var media = createTestMedia(frameRanges, timeRanges);
        return UserSpecifiedRangesUtil.getCombinedRanges(media);
    }


    private static Media createTestMedia(
            ImmutableSet<MediaRange> frameRanges,
            ImmutableSet<MediaRange> timeRanges) {
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

        var timeInfo = FrameTimeInfo.forVariableFrameRate(
                new Fraction(30_000, 1_001), frameTimes, false);

        var media = mock(Media.class);
        when(media.getLength())
                .thenReturn(OptionalInt.of(frameTimes.length));
        when(media.getFrameRanges())
                .thenReturn(frameRanges);
        when(media.getTimeRanges())
                .thenReturn(timeRanges);
        when(media.getFrameTimeInfo())
                .thenReturn(timeInfo);
        return media;
    }
}
