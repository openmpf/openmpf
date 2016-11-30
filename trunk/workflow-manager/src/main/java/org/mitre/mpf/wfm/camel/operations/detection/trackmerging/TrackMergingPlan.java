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

public class TrackMergingPlan {
	/** Indicates whether track merging is to be performed. */
	private final boolean mergeTracks;
	public boolean isMergeTracks() { return mergeTracks; }

	/** For images and videos, the number of frames at which the input medium was sampled. For audio, the number seconds between samples. */
	private final int samplingInterval;
	public int getSamplingInterval() { return samplingInterval; }

	public TrackMergingPlan(int samplingInterval, boolean mergeTracks) {
		this.samplingInterval = samplingInterval;
		this.mergeTracks = mergeTracks;
	}

	@Override
	public int hashCode() {
		int rValue = 37;
		rValue = 37 * rValue + (mergeTracks ? 0 : 1);
		rValue = 37 * rValue + samplingInterval;
		return rValue;
	}

	@Override
	public boolean equals(Object obj) {
		if(obj == null || !(obj instanceof TrackMergingPlan)) {
			return false;
		} else {
			TrackMergingPlan casted = (TrackMergingPlan)obj;
			return (mergeTracks == casted.mergeTracks) && (samplingInterval == casted.samplingInterval);
		}
	}

	@Override
	public String toString() {
		return String.format("%s#<mergeTracks=%s, samplingInterval=%d>",
				this.getClass().getSimpleName(), Boolean.toString(mergeTracks), samplingInterval);
	}
}
