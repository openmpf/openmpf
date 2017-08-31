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
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component(value = TimeUtils.REF)
@Monitored
public class TimeUtils {

	public static final String REF = "timeUtils";
	private static Logger log = LoggerFactory.getLogger(TimeUtils.class);

  /** Generate a sorted list of TimePairs from a set of sorted Tracks.
   * This version of the method is generally used when feed-forward track is not enabled.
   * @param tracks sorted set of Tracks.
   * @return sorted list of TimePairs.
   */
  public List<TimePair> createTimePairsForTracks(SortedSet<Track> tracks) {
    List<TimePair> timePairs = new ArrayList<>();
    for (Track track : tracks) {
      // form TimePairs for the set of tracks. Note that frame offsets are inclusive so no adjustments are necessary
      timePairs.add(new TimePair(track.getStartOffsetFrameInclusive(), track.getEndOffsetFrameInclusive()));
    }
    Collections.sort(timePairs);
    return timePairs;
  }

  /** Generate a sorted list of TimePairs from a set of sorted Tracks, considering only those top frames/detections within each track
   * sorted by Detection confidence. The number of top Detections may be limited by the topConfidenceCount value.
   * This version of the method is generally used when feed-forward track is enabled.
   * @param topConfidenceCount specification of which Detections are to be used from the feed forward track. Valid values are:
   *  <ul>
   *    <li>If topConfidenceCount is null or <=0, this method will use all of the detections in the feed-forward track.</li>
   *    <li>If topConfidenceCount is equal to 1, this method will only use the exemplar in the feed-forward track (which, by definition, is the detection with the highest confidence).</li>
   *    <li>If topConfidenceCount >1, this method will only use that many detections from the feed-forward track with the highest confidence. If the number
   *    of Detections in the Track are less than or equal to topConfidenceCount, then all Detections will be used.</li>
   *  </ul>
   * @param targetSegmentLength preferred target segment length.
   * @param tracks set of sorted Tracks.
   * @return sorted list of TimePairs.
   */
  public List<TimePair> createTimePairsForTracks(Integer topConfidenceCount, int targetSegmentLength, SortedSet<Track> tracks) {
    List<TimePair> timePairs = new ArrayList<>();
    for (Track track : tracks) {
      // form TimePairs for the set of tracks. Note that frame offsets are inclusive so no adjustments are necessary
      if ( topConfidenceCount == null || topConfidenceCount.intValue()<=0 || track.getDetections().size() < topConfidenceCount.intValue() ) {
        // If topConfidenceCount is not set, or <= 0, or if number of detections in the track < topConfidenceCount, then use all of the detections in the feed-forward track.
        timePairs.add(new TimePair(track.getStartOffsetFrameInclusive(), track.getEndOffsetFrameInclusive()));
      } else if ( topConfidenceCount.intValue() == 1 ) {
        // only use the exemplar to form the TimePair for the feed-forward track
        Detection exemplarDetection = track.getExemplar();
        int exemplarFrameOffsetInclusive = track.getStartOffsetFrameInclusive() + exemplarDetection.getMediaOffsetFrame();
        timePairs.add(new TimePair(exemplarFrameOffsetInclusive,exemplarFrameOffsetInclusive));
      } else {
        // only use that many detections from the feed-forward track with the highest confidence
        // the priority queue prioritizes Detections based on confidence, sorting high to low.
        SortedSet<Detection> detections = track.getDetections();
        PriorityQueue <Detection> priorityQueue = new PriorityQueue<>(
        		detections.size(),
		        Comparator.comparingDouble(Detection::getConfidence).reversed()
				        .thenComparing(Comparator.naturalOrder()));
        priorityQueue.addAll(detections);

        // Get the top <topConfidenceCount> detections from the priority queue or all detections from the priority queue if less than topConfidenceCount.
        // Also, allow for creation of multiple TimePairs if topConfidenceCount>targetSegmentLength.

        // First, poll highest confidence Detection from priority queue and remove it from the queue.
        Detection highestConfidenceDetection = priorityQueue.poll();
        int detectionCounter = 1;

        // Initiate an unbounded TimePair from the highest confidence Detection, then loop over the lesser confidence Detections to close the TimePair
        TimePair timePair = new TimePair(track.getStartOffsetFrameInclusive() + highestConfidenceDetection.getMediaOffsetFrame());

        // Process the remaining high confidence Detections, processing from highest to lowest confidence order
        Detection nextTopDetection = null;
        while (!priorityQueue.isEmpty() && detectionCounter < topConfidenceCount.intValue()) {

          // poll the remaining Detections from priority queue from highest to lowest confidence
          // until we process <topConfidenceCount> of the top detections.
          // Note that polling with removal seems to be our only option,
          // Iterator doesn't guarantee ordering and there isn't a "get highest priority" method available for a priority queue.
          nextTopDetection = priorityQueue.poll();
          detectionCounter += 1;
          // if the last current TimePair was closed during the last iteration, start a new TimePair using this next Detection.
          if ( timePair.isClosedTimePair() ) {
            timePair = new TimePair(track.getStartOffsetFrameInclusive() + nextTopDetection.getMediaOffsetFrame());
          }
          // once the counter has reached the target segment length, do TimePair processing.
          if (detectionCounter % targetSegmentLength == 0) {
            // close a current TimePair if unbounded, otherwise start a new TimePair.
            if (timePair.isUnboundedTimePair()) {
              // close the current TimePair and add it to the list of TimePairs
              timePair.setEndInclusive(track.getStartOffsetFrameInclusive() + nextTopDetection.getMediaOffsetFrame());
              timePairs.add(timePair);
              // Do an early break if topConfidenceCount<=targetSegmentLength, we only want to form a single TimePair in this case
              if ( topConfidenceCount.intValue() <= targetSegmentLength ) {
                break;
              }
            }
          }

        } // end of processing from priority queue loop

        // check the last TimePair that was created, close it if it is unbounded and add it to the list of TimePairs.
        if (timePair.isUnboundedTimePair()) {
          // close the last TimePair using the last top detection and add it to the set of TimePairs
          timePair.setEndInclusive(track.getStartOffsetFrameInclusive() + nextTopDetection.getMediaOffsetFrame());
          timePairs.add(timePair);
        }
      }
    }
    // sort the TimePairs and return
    Collections.sort(timePairs);
    return timePairs;
  }

  /** Generate the segments for the next stage. This version of the method should be called when feed-forward is enabled, as it
   * will prevent overlapping TimePairs from being merged and will use the confidence available in the Track Detections
   * in conjunction with topConfidenceCount in order to form the TimePair segments for the next stage.
   * These segments which will each be used to create a sub job for the next stage.
   * @param topConfidenceCount specification of which top confidence detections are to be used from the feed forward track.  Valid values are:
   *  <ul>
   *    <li>If topConfidenceCount is null or <=0, this method will use all of the detections in the feed-forward track.</li>
   *    <li>If topConfidenceCount is equal to 1, this method will only use the exemplar in the feed-forward track (which, by definition, is the detection with the highest confidence).</li>
   *    <li>If topConfidenceCount >1, this method will only use that many detections from the feed-forward track with the highest confidence.</li>
   *  </ul>
   * @param trackSet sorted set of feed forward tracks.
   * @param targetSegmentLength The preferred size of each segment. If this value is less than or equal to 0, no segmenting will be performed.
   * Note that when the feed-forward option is used, the segment size may exceed this length, This is because only the frames associated with
   * top-confidence detections will be processed, so only those frames will count against the desired segment size.
   * @param minSegmentLength  The minimum size of a segment.
   * @return returns the list of segment TimePairs.  The list may be empty if the input collection is empty or null.
   */
  public List<TimePair> createSegments(Integer topConfidenceCount, SortedSet<Track> trackSet, int targetSegmentLength, int minSegmentLength) {
    if (trackSet == null || trackSet.size() == 0) {
      // If the input collection was empty (or null), no segments should be returned.
      return new ArrayList<TimePair>();
    }

    // form collection of start and stop times for each track, using topConfidenceCount to reduce the set of frames within each track as specified by the topConfidenceCount value
    List<TimePair> timePairs = createTimePairsForTracks(topConfidenceCount, targetSegmentLength, trackSet);

    // Create a copy of the input timePairs list and sort it.
    List<TimePair> tracks = new ArrayList<TimePair>(timePairs);
    Collections.sort(tracks);

    // Begin building the result list.
    List<TimePair> result = new ArrayList<TimePair>();

    // The processing in this version of createSegments differs from the other implementation of createSegments.
    // To support feed-forward, the segments are not checked for overlap so they are not merged.
    // Also for feed-forward, if topConfidenceCount>1 then only the <topConfidenceCount> detections with the highest confidence will be processed.
    // We can effectively just process the track as a single segment containing all of the top detections,
    // since the lower confidence detections should be ignored.
    // To do this, we adjust the preferred size of each segment accordingly.
    TimePair current = null;
    for (TimePair nextTrack : tracks) {
      if (current == null) {
        current = nextTrack;
      } else {
        if ( topConfidenceCount.intValue() > 1 && topConfidenceCount.intValue() >= targetSegmentLength ) {
          result.addAll(segment(current, current.getEndInclusive()-current.getStartInclusive(), minSegmentLength));
        } else {
          result.addAll(segment(current, targetSegmentLength, minSegmentLength));
        }
        current = nextTrack;
      }
    }

    if ( topConfidenceCount.intValue() > 1 && topConfidenceCount.intValue() >= targetSegmentLength ) {
      result.addAll(segment(current, current.getEndInclusive()-current.getStartInclusive(), minSegmentLength));
    } else {
      result.addAll(segment(current, targetSegmentLength, minSegmentLength));
    }

    return result;
  }

	/** Generate the segments for the next stage. This version of the method will merge overlapping time pairs and should be used
   * when feed-forward is not enabled.
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
