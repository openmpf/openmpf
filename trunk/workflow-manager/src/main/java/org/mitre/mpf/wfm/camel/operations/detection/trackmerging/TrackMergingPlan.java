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

package org.mitre.mpf.wfm.camel.operations.detection.trackmerging;

public class TrackMergingPlan {
	// Indicates whether to merge tracks. If track merging is turned off, minGapBetweenTracks is invalid,
	// but minTrackLength is still respected.
	private final boolean mergeTracks;

	// The allowable distance between similar tracks without merging.
	private final int minGapBetweenTracks;

	// Indicates the shortest track length to keep.
	private final int minTrackLength;

	// Indicates the minimum amount of frame region overlap to merge tracks.
	private final double minTrackOverlap;

	public boolean isMergeTracks() { return mergeTracks; }

	public int getMinGapBetweenTracks() {
		return minGapBetweenTracks;
	}

	public int getMinTrackLength() {
		return minTrackLength;
	}

	public double getMinTrackOverlap() {
		return minTrackOverlap;
	}

	public TrackMergingPlan(boolean mergeTracks, int minGapBetweenTracks, int minTrackLength, double minTrackOverlap) {
		this.mergeTracks = mergeTracks;
		this.minGapBetweenTracks = minGapBetweenTracks;
		this.minTrackLength = minTrackLength;
		this.minTrackOverlap = minTrackOverlap;
	}

	@Override
	public String toString() {
		return String.format("%s#<mergeTracks=%s, minGapBetweenTracks=%d, minTrackLength=%d, minTrackOverlap=%d>",
				this.getClass().getSimpleName(), Boolean.toString(mergeTracks), minGapBetweenTracks, minTrackLength, minTrackOverlap);
	}
}