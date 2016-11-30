/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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

import org.javasimon.aop.Monitored;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component(value = TimeUtils.REF)
@Monitored
public class TimeUtils {
	public static final String REF = "timeUtils";
	private static Logger log = LoggerFactory.getLogger(TimeUtils.class);

	public List<TimePair> createSegments(Collection<TimePair> inputs, int targetSegmentLength, int minSegmentLength, int minGapBetweenSegments) {
		if (inputs == null || inputs.size() == 0) {
			// If the input collection was empty (or null), no segments should be returned.
			return new ArrayList<TimePair>();
		}

		// Create a copy of the input list and sort it.
		List<TimePair> tracks = new ArrayList<TimePair>(inputs);
		Collections.sort(tracks);

		// Begin building the result list.
		List<TimePair> result = new ArrayList<TimePair>();

		TimePair current = null;
		for (TimePair nextTrack : tracks) {
			if (current == null) {
				current = nextTrack;
			} else {
				if (overlaps(current, nextTrack, minGapBetweenSegments)) {
					current = merge(current, nextTrack);
				} else {
					result.addAll(segment(current, targetSegmentLength, minSegmentLength));
					current = nextTrack;
				}
			}
		}

		result.addAll(segment(current, targetSegmentLength, minSegmentLength));
		return result;
	}

	/**
	 * Divides a large segment into a collection of zero or more segments which respect the target and minimum segment length parameters.
	 *  <ul>
	 *    <li>If the length of the input is less than the provided minSegmentLength, this method will return an empty collection.</li>
	 *    <li>Provided the length of the input exceeds the provided minSegmentLength, all but the last segment in the returned collection will have a length of targetSegmentLength.</li>
	 *    <li>Provided the length of the input exceeds the provided minSegmentLength, The last segment in the returned collection will have a length between [minSegmentLength, targetSegmentLength).</li>
	 *  </ul>
	 * @param timePair The TimePair representing the inclusive start and stop times from which segments are to be created.
	 * @param targetSegmentLength The preferred size of each segment. If this value is less than or equal to 0, no segmenting will be performed.
	 * @param minSegmentLength The minimum size of a segment.
	 * @return A collection of zero or more TimePair instances which represent the inclusive start and stop times.
	 */
	private Collection<TimePair> segment(TimePair timePair, int targetSegmentLength, int minSegmentLength) {
		if (targetSegmentLength <= 0 || targetSegmentLength == Integer.MAX_VALUE) {
			// The targetSegmentLength indicates that segmenting should not be performed. Return a list containing the unmodified input segment.
			List<TimePair> singleSegment = new ArrayList<TimePair>(1);
			singleSegment.add(timePair);
			return singleSegment;
		} else {
			List<TimePair> result = new ArrayList<TimePair>(timePair.length() / targetSegmentLength);
			for (int start = timePair.getStartInclusive(); start <= timePair.getEndInclusive(); start += targetSegmentLength) {
				if (timePair.getEndInclusive() <= (start + (targetSegmentLength - 1) + minSegmentLength)) {
					result.add(new TimePair(start, timePair.getEndInclusive()));
					break;
				} else {
					result.add(new TimePair(start, start + targetSegmentLength - 1));
				}
			}
			return result;
		}
	}

	/** Returns {@link java.lang.Boolean#TRUE} iff the current and probe tracks overlap each other from a temporal context.
	 * Assumes that the current track has a start time which is less than or equal to the target track's start time. */
	public boolean overlaps(TimePair current, TimePair target, int minGapBetweenSegments) {
		// Current spans [S, E], Target spans  [S*, E*], and it is known that S <= S*.
		// The tracks overlap if S <= S* <= E or E < (S* - G)
		return (current.getStartInclusive() <= target.getStartInclusive() && target.getStartInclusive() <= current.getEndInclusive()) ||
				current.getEndInclusive() <= target.getStartInclusive() - minGapBetweenSegments;
	}

	/** Modifies the current TimePair such that it includes the entire range represented by the current and target TimePairs. */
	public TimePair merge(TimePair current, TimePair target) {
		return new TimePair(current.getStartInclusive(), Math.max(current.getEndInclusive(), target.getEndInclusive()));
	}
}
