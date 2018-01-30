/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.segmenting;

public class SegmentingPlan {
	/** The preferred length of a segment. This must be at least 1. */
	private int targetSegmentLength;
	public int getTargetSegmentLength() { return targetSegmentLength; }

	/** The minimum length of any given segment. This value, x, must satisfy 1 <= x <= {@link #targetSegmentLength}. */
	private int minSegmentLength;
	public int getMinSegmentLength() { return minSegmentLength; }

	/** The number of (frames | milliseconds) between samples. This value must be at least 1, but it may be no larger than {@link #targetSegmentLength}. */
	private int samplingInterval;
	public int getSamplingInterval() { return samplingInterval; }

	/** The minimum number of (frames | milliseconds) between the end of a segment and the beginning of the next segment. This must be strictly greater than {@link #samplingInterval}. */
	private int minGapBetweenSegments;
	public int getMinGapBetweenSegments() { return minGapBetweenSegments; }

	/**
	 * Creates a new instance using the provided parameters.
	 * @param targetSegmentLength The preferred length of a segment. This must be at least 1.
	 * @param minSegmentLength The minimum length of a segment clamped to the range [1, targetSegmentLength].
	 * @param samplingInterval The interval at which the media was sampled clamped to the range [1, targetSegmentLength].
	 * @param minGapBetweenSegments The minimum gap between any two segments which must be strictly greater than the sampling interval.
	 */
	public SegmentingPlan(int targetSegmentLength, int minSegmentLength, int samplingInterval, int minGapBetweenSegments) {
		this.targetSegmentLength = targetSegmentLength;
		this.minSegmentLength = Math.min(targetSegmentLength, Math.max(minSegmentLength, 1));
		this.samplingInterval = Math.min(targetSegmentLength, Math.max(samplingInterval, 1));
		this.minGapBetweenSegments = Math.max(this.samplingInterval + 1, minGapBetweenSegments);
	}

	@Override
	public String toString() {
		return String.format("%s#<targetSegmentLength=%d, minSegmentLength=%d, samplingInterval=%d, minGapBetweenSegments=%d>",
				this.getClass().getSimpleName(), targetSegmentLength, minSegmentLength, samplingInterval, minGapBetweenSegments);
	}
}
