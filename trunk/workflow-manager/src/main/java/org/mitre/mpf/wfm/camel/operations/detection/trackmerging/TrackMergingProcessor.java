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

package org.mitre.mpf.wfm.camel.operations.detection.trackmerging;

import org.apache.camel.Exchange;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.camel.WfmProcessor;
import org.mitre.mpf.wfm.data.RedisImpl;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.entities.transients.TransientAction;
import org.mitre.mpf.wfm.data.entities.transients.TransientJob;
import org.mitre.mpf.wfm.data.entities.transients.TransientMedia;
import org.mitre.mpf.wfm.data.entities.transients.TransientStage;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Merges tracks in a video.
 *
 * When a video is segmented to produce detection requests between frames [M, N], there is a risk that an object will
 * appear somewhere in the segment and remain in the video beyond Frame N. If this happens, the detector which processes
 * frame N+1 will likely find and begin a new track for this same object. The purpose of the TrackMergingProcessor
 * is to find and merge any tracks of the same type which are chronologically adjacent and and sufficiently overlapping.
 *
 * Consider a ball that is last seen in Frame 10 at the position (10, 10) and with size 100x100. In Frame 11, if another
 * ball is detected at (11, 10) with size 100x100, it is likely that these two tracks are of the same object, and so
 * the tracks are merged. Once merged, the track is updated to keep only one exemplar.
 *
 * "Chronologically adjacent" depends on the properties associated with the detection action that created the tracks.
 * Specifically, if the detection frame interval is set to 20 (meaning one frame is sampled and then 19 frames are
 * skipped), Frames 0 and 20 are considered adjacent.
 *
 * The {@link TrackMergingPlan} provides all of the information
 * necessary to modify the default behavior of the track merging algorithm.
 */
@Component(TrackMergingProcessor.REF)
public class TrackMergingProcessor extends WfmProcessor {
	public static final String REF = "trackMergingProcessor";
	private static final Logger log = LoggerFactory.getLogger(TrackMergingProcessor.class);

	@Autowired
	@Qualifier(RedisImpl.REF)
	private Redis redis;

	@Autowired
	private PropertiesUtil propertiesUtil;

	@Override
	public void wfmProcess(Exchange exchange) throws WfmProcessingException {
		assert exchange.getIn().getBody() != null : "The body must not be null.";
		assert exchange.getIn().getBody(byte[].class) != null : "The body must be convertible to a String.";

		TrackMergingContext trackMergingContext = jsonUtils.deserialize(exchange.getIn().getBody(byte[].class), TrackMergingContext.class);

		assert trackMergingContext != null : "The TrackMergingContext instance must never be null.";

		TransientJob transientJob = redis.getJob(trackMergingContext.getJobId());

		assert transientJob != null : String.format("Redis failed to retrieve a job with ID %d.", trackMergingContext.getJobId());

		TransientStage transientStage = transientJob.getPipeline().getStages().get(trackMergingContext.getStageIndex());
		for(int actionIndex = 0; actionIndex < transientStage.getActions().size(); actionIndex++) {
			TransientAction transientAction = transientStage.getActions().get(actionIndex);

			for (TransientMedia transientMedia : transientJob.getMedia()) {
				if (!transientMedia.isFailed()) {
					// If there exist media-specific properties for track merging, use them.
					String samplingInterval = AggregateJobPropertiesUtil.calculateValue(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY,
							transientAction.getProperties(), transientJob.getOverriddenJobProperties(),
							transientAction, transientJob.getOverriddenAlgorithmProperties(),
							transientMedia.getMediaSpecificProperties());

					String minTrackLength = AggregateJobPropertiesUtil.calculateValue(MpfConstants.MIN_TRACK_LENGTH,
							transientAction.getProperties(), transientJob.getOverriddenJobProperties(),
							transientAction, transientJob.getOverriddenAlgorithmProperties(),
							transientMedia.getMediaSpecificProperties());

					String mergeTracks = AggregateJobPropertiesUtil.calculateValue(MpfConstants.MERGE_TRACKS_PROPERTY,
							transientAction.getProperties(), transientJob.getOverriddenJobProperties(),
							transientAction, transientJob.getOverriddenAlgorithmProperties(),
							transientMedia.getMediaSpecificProperties());

					String minGapBetweenTracks = AggregateJobPropertiesUtil.calculateValue(MpfConstants.MIN_GAP_BETWEEN_TRACKS,
							transientAction.getProperties(), transientJob.getOverriddenJobProperties(),
							transientAction, transientJob.getOverriddenAlgorithmProperties(),
							transientMedia.getMediaSpecificProperties());

					TrackMergingPlan trackMergingPlan = createTrackMergingPlan(samplingInterval, minTrackLength, mergeTracks, minGapBetweenTracks);

					if (trackMergingPlan.isMergeTracks()) {
						SortedSet<Track> tracks = redis.getTracks(trackMergingContext.getJobId(), transientMedia.getId(), trackMergingContext.getStageIndex(), actionIndex);
						SortedSet<Track> newTracks = new TreeSet<Track>(combine(tracks, trackMergingPlan, propertiesUtil.getTrackOverlapThreshold()));
						log.debug("[Job {}|{}|{}] Merging {} tracks down to {} in Media {}.", trackMergingContext.getJobId(), trackMergingContext.getStageIndex(), actionIndex, tracks.size(), newTracks.size(), transientMedia.getId());
						redis.setTracks(trackMergingContext.getJobId(), transientMedia.getId(), trackMergingContext.getStageIndex(), actionIndex, newTracks);

					} else {
						log.debug("[Job {}|{}|{}] Track merging has not been requested for this action and media {}.", trackMergingContext.getJobId(), trackMergingContext.getStageIndex(), actionIndex, transientMedia.getId());
					}

					if (trackMergingPlan.getMinTrackLength() > 1) {
						SortedSet<Track> tracks = redis.getTracks(trackMergingContext.getJobId(), transientMedia.getId(), trackMergingContext.getStageIndex(), actionIndex);
						SortedSet<Track> newTracks = new TreeSet<Track>();
						for (Track track : tracks) {
							// Since both offset frames are inclusive, the actual track length is one greater than the delta.
							if (track.getEndOffsetFrameInclusive() - track.getStartOffsetFrameInclusive() >= trackMergingPlan.getMinTrackLength() - 1) {
								newTracks.add(track);
							}
						}
						log.debug("[Job {}|{}|{}] Pruning {} tracks down to {} tracks at least {} frames long in Media {}.", trackMergingContext.getJobId(), trackMergingContext.getStageIndex(), actionIndex, tracks.size(), newTracks.size(), trackMergingPlan.getMinTrackLength(), transientMedia.getId());
						redis.setTracks(trackMergingContext.getJobId(), transientMedia.getId(), trackMergingContext.getStageIndex(), actionIndex, newTracks);

					} else {
						log.debug("[Job {}|{}|{}] Minimum track length has not been enabled for this action and media {}.", trackMergingContext.getJobId(), trackMergingContext.getStageIndex(), actionIndex, transientMedia.getId());
					}
				} else {
					log.debug("[Job {}|{}|{}] Media {} is in an error state and is not a candidate for merging.", trackMergingContext.getJobId(), trackMergingContext.getStageIndex(), actionIndex, transientMedia.getId());
				}
			}
		}

		exchange.getOut().setBody(jsonUtils.serialize(trackMergingContext));
	}

	private TrackMergingPlan createTrackMergingPlan(String samplingIntervalProperty, String minTrackLengthProperty, String mergeTracksProperty, String minGapBetweenTracksProperty
	) {
		int samplingInterval = 1;
		int minTrackLength = 1;
		boolean mergeTracks = false;
		int minGapBetweenTracks = 2;
		if (samplingIntervalProperty != null) {
			try {
				samplingInterval = Integer.valueOf(samplingIntervalProperty);
				if (samplingInterval < 1) {
					throw new IllegalArgumentException(String.format("%s is not an acceptable " + MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY + " value", samplingIntervalProperty));
				}
			} catch (Exception exception) {
				log.warn("Attempted to parse " + MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY + " value of '{}' but encountered an exception. Defaulting to 1 and disabling track merging.", samplingIntervalProperty, exception);
				return new TrackMergingPlan(1, 1, false, 1);
			}
		}
		if (minTrackLengthProperty != null) {
			try {
				minTrackLength = Integer.valueOf(minTrackLengthProperty);
			} catch (Exception exception) {
				log.warn("Attempted to parse " + MpfConstants.MIN_TRACK_LENGTH + " value of '{}' but encountered an exception. Defaulting to 1.", mergeTracksProperty, exception);
				return new TrackMergingPlan(1, 1, false, 1);
			}
		}
		if (mergeTracksProperty != null) {
			try {
				mergeTracks = Boolean.valueOf(mergeTracksProperty);
			} catch (Exception exception) {
				log.warn("Attempted to parse " + MpfConstants.MERGE_TRACKS_PROPERTY + " value of '{}' but encountered an exception. Defaulting to false.", mergeTracksProperty, exception);
				return new TrackMergingPlan(1, 1, false, 1);
			}
		}
		if (minGapBetweenTracksProperty != null) {
			try {
				minGapBetweenTracks = Integer.valueOf(minGapBetweenTracksProperty);
			} catch (Exception exception) {
				log.warn("Attempted to parse " + MpfConstants.MIN_GAP_BETWEEN_TRACKS + " value of '{}' but encountered an exception. Defaulting to 1.", mergeTracksProperty, exception);
				return new TrackMergingPlan(1, 1, false, 1);
			}
		}
		return new TrackMergingPlan(samplingInterval, minTrackLength, mergeTracks, minGapBetweenTracks);
	}

	private Set<Track> combine(Set<Track> sourceTracks, TrackMergingPlan plan, double segMinOverlap) {
		// Do not attempt to merge an empty or null set.
		if (CollectionUtils.isEmpty(sourceTracks)) {
			return sourceTracks;
		}

		int minGap = plan.getMinGapBetweenTracks();
		List<Track> tracks = new LinkedList<Track>(sourceTracks);
		Collections.sort(tracks);

		List<Track> mergedTracks = new LinkedList<Track>();

		while (tracks.size() > 0) {
			// Pop off the track with the earliest start time.
			Track merged = tracks.remove(0);
			boolean performedMerge = false;
			Track trackToRemove = null;

			for (Track candidate : tracks) {
				// Iterate through the remaining tracks until a track is found which has a starting time exactly samplingInterval units after the stop frame of the current track AND sufficient overlap.
				boolean track1BeforeTrack2 = merged.getEndOffsetFrameInclusive() < candidate.getStartOffsetFrameInclusive();
				boolean trackGapWithinLimit = merged.getEndOffsetFrameInclusive() >= candidate.getStartOffsetFrameInclusive() - minGap + 1;
				if (track1BeforeTrack2 && trackGapWithinLimit && intersects(merged, candidate, segMinOverlap)) {
					// If one is found, merge them and then push this track back to the beginning of the collection.
					tracks.add(0, merge(merged, candidate));
					performedMerge = true;

					// Keep a reference to the track which was merged into the original - it will be removed.
					trackToRemove = candidate;
					break;
				}
			}

			if (performedMerge) {
				// A merge was performed, so it is necessary to remove the merged track.
				tracks.remove(trackToRemove);
			} else {
				// No merge was performed. The current track is no longer a candidate for merging.
				mergedTracks.add(merged);
			}
		}

		log.trace("Track merging complete. The {} input tracks were merged as appropriate to form {} output tracks.", sourceTracks.size(), mergedTracks.size());
		return new HashSet<Track>(mergedTracks);
	}

	/** Combines two tracks. This is a destructive method. The contents of track1 reflect the merged track. */
	private Track merge(Track track1, Track track2){
		Track merged = new Track(track1.getJobId(), track1.getMediaId(), track1.getStageIndex(), track1.getActionIndex(), track1.getStartOffsetFrameInclusive(), track2.getEndOffsetFrameInclusive(), track1.getType());
		merged.getDetections().addAll(track1.getDetections());
		merged.getDetections().addAll(track2.getDetections());

		Detection exemplar = null;

		for(Detection detection : merged.getDetections()) {
			if(exemplar == null || exemplar.getConfidence() < detection.getConfidence()) {
				exemplar = detection;
			}
		}

		merged.setExemplar(exemplar);
		return merged;
	}

	private boolean intersects(Track track1, Track track2, double segMinOverlap) {
		if (!StringUtils.equalsIgnoreCase(track1.getType(), track2.getType())) {
			// Tracks of different types should not be candidates for merger. Ex: It would make no sense to merge a motion and speech track.
			return false;
		} else if (StringUtils.equalsIgnoreCase(track1.getType(), "SPEECH")) {
			// Speech tracks should not be candidates for merger.
			return false;
		}

		Detection track1End = track1.getDetections().last();
		Detection track2End = track2.getDetections().first();

		Detection first = (track1End.getMediaOffsetFrame() < track2End.getMediaOffsetFrame()) ? track1End : track2End;
		Detection second = (first == track1End) ? track2End : track1End;

		Rectangle rectangle1 = new Rectangle(first.getX(), first.getY(), first.getWidth(), first.getHeight());
		Rectangle rectangle2 = new Rectangle(second.getX(), second.getY(), second.getWidth(), second.getHeight());

		if (rectangle1.getWidth() == 0 || rectangle2.getWidth() == 0 || rectangle1.getHeight() == 0 || rectangle1.getHeight() == 0) {
			return false;
		}

		Rectangle intersection = rectangle1.intersection(rectangle2);
		if (intersection.isEmpty()) {
			return false;
		}

		double intersectArea = intersection.getHeight() * intersection.getWidth();
		double unionArea = (rectangle2.getHeight() * rectangle2.getWidth()) + (rectangle1.getHeight() * rectangle1.getWidth()) - intersectArea;
		double percentOverlap = intersectArea / unionArea;

		return percentOverlap > segMinOverlap;
	}
}
