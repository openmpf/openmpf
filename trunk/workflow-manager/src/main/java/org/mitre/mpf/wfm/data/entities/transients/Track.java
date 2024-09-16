/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.data.entities.transients;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import org.mitre.mpf.interop.util.CompareUtils;
import org.mitre.mpf.wfm.util.ExemplarPolicyUtil;

import java.util.*;

/**
 * A track is the logical grouping of detections of the same object as it appears at different offsets in a medium.
 *
 * In the context of a fixed-position video of a person walking across the street, a track would represent the person's
 * location in each frame of the video.
 *
 * In the context of an image, a track is effectively equivalent to a detection.
 *
 * A track in the context of an audio file is loosely defined based on times.
 */
public class Track implements Comparable<Track> {

    /** The numeric identifier for the job with which this track is associated. */
    private final long _jobId;
    public long getJobId() { return _jobId; }

    /** The numeric identifier for the media with which this track is associated. */
    private final long _mediaId;
    public long getMediaId() { return _mediaId; }

    /** The zero-based task index in the pipeline containing the action which produced this track. */
    private final int _taskIndex;
    public int getTaskIndex() { return _taskIndex; }

    /** The zero-based action index in the task of the pipeline which produced this track. */
    private final int _actionIndex;
    public int getActionIndex() { return _actionIndex; }

    private int artifactExtractionTrackIndex;
    public int getArtifactExtractionTrackIndex() {return artifactExtractionTrackIndex; }
    public void setArtifactExtractionTrackIndex(int trackNumber) { artifactExtractionTrackIndex = trackNumber; }

    /** The zero-based index where the track begins in the medium, given in frames. */
    private final int _startOffsetFrameInclusive;
    public int getStartOffsetFrameInclusive() { return _startOffsetFrameInclusive; }

    /** The zero-based and inclusive final index where the track ends in the medium, given in frames. */
    private final int _endOffsetFrameInclusive;
    public int getEndOffsetFrameInclusive() { return _endOffsetFrameInclusive; }

    /** The zero-based index where the track begins in the medium, given in milliseconds. */
    private final int _startOffsetTimeInclusive;
    public int getStartOffsetTimeInclusive() { return _startOffsetTimeInclusive; }

    /** The zero-based and inclusive final index where the track ends in the medium, given in milliseconds. */
    private final int _endOffsetTimeInclusive;
    public int getEndOffsetTimeInclusive() { return _endOffsetTimeInclusive; }

    /**
     * The index of the task that should be reported after applying task merging. If task
     * merging does not apply to this track, this field will contain the index of the task that
     * actually generated this track.
     */
    private final int _mergedTaskIndex;
    public int getMergedTaskIndex() { return _mergedTaskIndex; }

    private final float _confidence;
    public float getConfidence() { return _confidence; }

    private final ImmutableSortedMap<String, String> _trackProperties;
    public ImmutableSortedMap<String, String> getTrackProperties() { return _trackProperties; }

    /**
     * The natural ordered (by start index) collection of detections which correspond to the position of the object
     * as it moves through the track.
     */
    private final ImmutableSortedSet<Detection> _detections;
    public ImmutableSortedSet<Detection> getDetections() { return _detections; }

    /** The detection with the highest confidence in the track. */
    private final Detection _exemplar;
    @JsonIgnore
    public Detection getExemplar() { return _exemplar; }

    private final String _exemplarPolicy;
    public String getExemplarPolicy() { return _exemplarPolicy; }

    private final String _qualitySelectionProperty;
    public String getQualitySelectionProperty() { return _qualitySelectionProperty; }

    /**
     * Creates a new track instance with the given immutable parameters.
     *
     * @param jobId The job with which this track is associated.
     * @param mediaId The medium with which this track is associated.
     * @param taskIndex The task of the pipeline containing the action with which this track is associated.
     * @param actionIndex The index of the action in the task of the pipeline which created this track.
     * @param startOffsetFrameInclusive The zero-based index where the track begins in the medium.
     *                                      Frame number is relevant for image and video files.
     * @param endOffsetFrameInclusive The zero-based and inclusive stop index where the track ends in the medium.
     *                                      Frame number is relevant for image and video files.
     * @param startOffsetTimeInclusive The zero-based index where the track begins in the medium.
     *                                      Time is given in milliseconds, and is relevant for video and audio files.
     * @param endOffsetTimeInclusive The zero-based and inclusive stop index where the track ends in the medium.
     *                                      Time is given in milliseconds, and is relevant for video and audio files.
     * @param mergedTaskIndex The index of the task that should be reported after applying task merging.
     * @param confidence The track confidence
     * @param detections The collection of detections which correspond to the position of the object as it
     *                   moves through the track.
     * @param trackProperties Map containing the track level properties.
     */
    @JsonCreator
    public Track(
            @JsonProperty("jobId") long jobId,
            @JsonProperty("mediaId") long mediaId,
            @JsonProperty("taskIndex") int taskIndex,
            @JsonProperty("actionIndex") int actionIndex,
            @JsonProperty("startOffsetFrameInclusive") int startOffsetFrameInclusive,
            @JsonProperty("endOffsetFrameInclusive") int endOffsetFrameInclusive,
            @JsonProperty("startOffsetTimeInclusive") int startOffsetTimeInclusive,
            @JsonProperty("endOffsetTimeInclusive") int endOffsetTimeInclusive,
            @JsonProperty("mergedTaskIndex") int mergedTaskIndex,
            @JsonProperty("confidence") float confidence,
            @JsonProperty("detections") Iterable<Detection> detections,
            @JsonProperty("trackProperties") Map<String, String> trackProperties,
            @JsonProperty("exemplarPolicy") String exemplarPolicy,
            @JsonProperty("qualitySelectionProperty") String qualitySelectionProperty) {
        _jobId = jobId;
        _mediaId = mediaId;
        _taskIndex = taskIndex;
        _actionIndex = actionIndex;
        _startOffsetFrameInclusive = startOffsetFrameInclusive;
        _endOffsetFrameInclusive = endOffsetFrameInclusive;
        _startOffsetTimeInclusive = startOffsetTimeInclusive;
        _endOffsetTimeInclusive = endOffsetTimeInclusive;
        _mergedTaskIndex = mergedTaskIndex;
        _confidence = confidence;
        _detections = ImmutableSortedSet.copyOf(detections);
        _trackProperties = ImmutableSortedMap.copyOf(trackProperties);
        _exemplarPolicy = exemplarPolicy;
        _qualitySelectionProperty = qualitySelectionProperty;
        _exemplar = ExemplarPolicyUtil.getExemplar(
                    _exemplarPolicy,
                    _qualitySelectionProperty,
                    _startOffsetFrameInclusive,
                    _endOffsetFrameInclusive,
                    _detections);
    }



    @Override
    public int hashCode() {
        return Objects.hash(
                _jobId, _mediaId, _taskIndex, _actionIndex, _startOffsetFrameInclusive,
                _endOffsetFrameInclusive, _startOffsetTimeInclusive, _endOffsetTimeInclusive,
                _mergedTaskIndex, _confidence, _trackProperties, _exemplar,
                _detections);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Track && compareTo((Track) obj) == 0;
    }



    private static final Comparator<Set<Detection>> DETECTION_SET_COMPARATOR = Comparator
            .nullsFirst(Comparator
                                .<Set<Detection>>comparingInt(Set::size))
            .thenComparing((s1, s2) -> {
                Iterator<Detection> it1 = s1.iterator();
                Iterator<Detection> it2 = s2.iterator();
                while (it1.hasNext()) {
                    int comp = it1.next().compareTo(it2.next());
                    if (comp != 0) {
                        return comp;
                    }
                }
                return 0;
            });

    private static final Comparator<Track> DEFAULT_COMPARATOR = Comparator
            .nullsFirst(Comparator
                .comparingLong(Track::getJobId)
                .thenComparingLong(Track::getMediaId)
                .thenComparingInt(Track::getTaskIndex)
                .thenComparingInt(Track::getActionIndex)
                .thenComparingInt(Track::getStartOffsetFrameInclusive)
                .thenComparingInt(Track::getEndOffsetFrameInclusive)
                .thenComparingInt(Track::getStartOffsetTimeInclusive)
                .thenComparingInt(Track::getEndOffsetTimeInclusive)
                .thenComparingInt(Track::getMergedTaskIndex)
                .thenComparingDouble(Track::getConfidence)
                .thenComparing(Track::getTrackProperties, CompareUtils.MAP_COMPARATOR)
                .thenComparing(Track::getExemplar, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(Track::getDetections, DETECTION_SET_COMPARATOR));

    @Override
    public int compareTo(Track other) {
        //noinspection ObjectEquality - Just an optimization to avoid comparing all fields when compared to itself.
        return this == other ? 0 : DEFAULT_COMPARATOR.compare(this, other);
    }



    @Override
    public String toString() {
        return String.format(
                "%s#<startOffsetFrameInclusive=%d, endOffsetFrameInclusive=%d>#<startOffsetTimeInclusive=%d, endOffsetTimeInclusive=%d>",
                getClass().getSimpleName(),
                _startOffsetFrameInclusive,
                _endOffsetFrameInclusive,
                _startOffsetTimeInclusive,
                _endOffsetTimeInclusive);
    }
}
