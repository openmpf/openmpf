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

package org.mitre.mpf.wfm.camel.operations.detection;

import org.mitre.mpf.wfm.buffers.AlgorithmPropertyProtocolBuffer;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.segmenting.SegmentingPlan;

import java.util.List;
import java.util.Set;

public class DetectionContext {
	private final SegmentingPlan segmentingPlan;
	public SegmentingPlan getSegmentingPlan() { return segmentingPlan; }

	private final long jobId;
	public long getJobId() { return jobId; }

	private final int stageIndex;
	public int getStageIndex() { return stageIndex; }

	private final String stageName;
	public String getStageName() { return stageName; }

	private final int actionIndex;
	public int getActionIndex() { return actionIndex; }

	private final String actionName;
	public String getActionName() { return actionName; }

	private final boolean isFirstDetectionStage;
	public boolean isFirstDetectionStage() { return isFirstDetectionStage; }

	private final List<AlgorithmPropertyProtocolBuffer.AlgorithmProperty> algorithmProperties;
	public List<AlgorithmPropertyProtocolBuffer.AlgorithmProperty> getAlgorithmProperties() {
		return algorithmProperties;
	}

	private final Set<Track> previousTrack;
	public Set<Track> getPreviousTrack() { return previousTrack; }

	public DetectionContext(
			long jobId,
			int stageIndex,
			String stageName,
			int actionIndex,
			String actionName,
			boolean isFirstDetectionStage,
			List<AlgorithmPropertyProtocolBuffer.AlgorithmProperty> algorithmProperties,
			Set<Track> previousTrack,
			SegmentingPlan segmentingPlan) {
		this.jobId = jobId;
		this.stageIndex = stageIndex;
		this.stageName = stageName;
		this.actionIndex = actionIndex;
		this.actionName = actionName;
		this.isFirstDetectionStage = isFirstDetectionStage;
		this.algorithmProperties = algorithmProperties;
		this.previousTrack = previousTrack;
		this.segmentingPlan = segmentingPlan;
	}
}
