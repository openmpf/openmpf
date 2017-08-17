/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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

import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.FeedForwardTopConfidenceCountType;

@Component(value = TimeUtils.REF)
@Monitored
public class TimeUtils {
	public static final String REF = "timeUtils";
	private static Logger log = LoggerFactory.getLogger(TimeUtils.class);

	// TODO there might be a bug in this method, when forming the TimePair for the last segment it looks like TimePair:endOffsetFrameInclusive is too large (by 1)
  // to support the feed-forward unit tests, had to copy method createTimePairsForTracks from DetectionSplitter, needed to form TimePair list from a set of sorted Tracks
  private List<TimePair> createTimePairsForTracks(SortedSet<Track> tracks) {
    List<TimePair> timePairs = new ArrayList<>();
    for (Track track : tracks) {
      timePairs.add(new TimePair(track.getStartOffsetFrameInclusive(), track.getEndOffsetFrameInclusive() + 1));
    }
    Collections.sort(timePairs);
    return timePairs;
  }

  /** Generate the segments for the next stage. This version of the method should be called when feed-forward is enabled, as it
   * will prevent overlapping TimePairs from being merged and will use the confidence available in the Track Detections
   * in conjunction with feedForwardTopConfidenceCount to form the TimePair segments for the next stage.
   * These segments which will each be fed into a subjob for the next stage.
   * @param trackSet Collection of feed forward tracks
   * @param feedForwardTopConfidenceCount selection of which detections are to be used from the feed forward track
   * @param targetSegmentLength The preferred size of each segment. If this value is less than or equal to 0, no segmenting will be performed.
   * When the feed-forward option is used, the segment size may exceed this length, This is because only the frames associated with
   * top-confidence detections will be processed, so only those frames will count against the desired segment size.
   * @param minSegmentLength  The minimum size of a segment.
   * @return returns the list of segment TimePairs.  The list may be empty if the input collection is empty or null.
   */
  public List<TimePair> createSegments(SortedSet<Track> trackSet, FeedForwardTopConfidenceCountType feedForwardTopConfidenceCount, int targetSegmentLength, int minSegmentLength) {
    if (trackSet == null || trackSet.size() == 0) {
      // If the input collection was empty (or null), no segments should be returned.
      return new ArrayList<TimePair>();
    }

    // form collection of start and stop times for each track
    List<TimePair> timePairs = createTimePairsForTracks(trackSet);

    // Create a copy of the input timePairs list and sort it.
    List<TimePair> tracks = new ArrayList<TimePair>(timePairs);
    Collections.sort(tracks);

    // TODO add processing required for handling feedForwardTopConfidenceCount options

    // Begin building the result list.
    List<TimePair> result = new ArrayList<TimePair>();

    TimePair current = null;
    for (TimePair nextTrack : tracks) {
      if (current == null) {
        current = nextTrack;
      } else {
        // this version of the method (i.e. for usage with feed-forward) differs from the other implementation,
        // in that segments are not checked for overlaps, and are not merged
        result.addAll(segment(current, targetSegmentLength, minSegmentLength));
        current = nextTrack;
      }
    }

    result.addAll(segment(current, targetSegmentLength, minSegmentLength));
    return result;
  }

	/** Generate the segments for the next stage. This version of the method will merge overlapping time pairs and is typically used when feed-forward is not enabled.
	 * @param inputs Collection of start and stop times for each track
   * @param targetSegmentLength The preferred size of each segment. If this value is less than or equal to 0, no segmenting will be performed.
   * When the feed-forward option is used, the segment size may exceed this length, This is because only the frames associated with
   * top-confidence detections will be processed, so only those frames will count against the desired segment size.
   * @param minSegmentLength  The minimum size of a segment.
   * @param minGapBetweenSegments Minimum gap which must appear between consecutive segments. Parameter used during the overlap check.
	 * @return returns the list of segment TimePairs.  The list may be empty if the input collection is empty or null.
	 */
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
