/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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

import org.mitre.mpf.wfm.util.ExemplarFinder;

/**
 * @param mergeTracks Indicates whether to merge tracks. If track merging is turned off,
 *                     minGapBetweenTracks is invalid, but minTrackLength is still respected.
 * @param minGapBetweenTracks The allowable distance between similar tracks without merging.
 * @param minTrackLength Indicates the shortest track length to keep.
 * @param minTrackOverlap Indicates the minimum amount of frame region overlap to merge tracks.
 */
public record TrackMergingPlan(boolean mergeTracks, int minGapBetweenTracks, int minTrackLength,
                               double minTrackOverlap,
                               ExemplarFinder exemplarFinder) {
}
