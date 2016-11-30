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

package org.mitre.mpf.wfm.camel.operations.detection;

import org.mitre.mpf.wfm.buffers.AlgorithmPropertyProtocolBuffer;
import org.mitre.mpf.wfm.segmenting.SegmentingPlan;
import org.mitre.mpf.wfm.util.TimePair;

import java.util.ArrayList;
import java.util.List;

public class DetectionContext {
	private SegmentingPlan segmentingPlan;
	public SegmentingPlan getSegmentingPlan() { return segmentingPlan; }

	private long jobId;
	public long getJobId() { return jobId; }

	private int stageIndex;
	public int getStageIndex() { return stageIndex; }

	private String stageName;
	public String getStageName() { return stageName; }

	private int actionIndex;
	public int getActionIndex() { return actionIndex; }

	private String actionName;
	public String getActionName() { return actionName; }

	private boolean isFirstDetectionStage;
	public boolean isFirstDetectionStage() { return isFirstDetectionStage; }

	private List<AlgorithmPropertyProtocolBuffer.AlgorithmProperty> algorithmProperties;
	public List<AlgorithmPropertyProtocolBuffer.AlgorithmProperty> getAlgorithmProperties() { return algorithmProperties; }

	private List<TimePair> previousTrackTimePairs;
	public List<TimePair> getPreviousTrackTimePairs() { return previousTrackTimePairs; }

	public DetectionContext(long jobId, int stageIndex, String stageName, int actionIndex, String actionName, boolean isFirstDetectionStage, List<AlgorithmPropertyProtocolBuffer.AlgorithmProperty> algorithmProperties, List<TimePair> previousTrackTimePairs, SegmentingPlan segmentingPlan) {
		this.jobId = jobId;
		this.stageIndex = stageIndex;
		this.stageName = stageName;
		this.actionIndex = actionIndex;
		this.actionName = actionName;
		this.isFirstDetectionStage = isFirstDetectionStage;
		this.algorithmProperties = (algorithmProperties != null) ? algorithmProperties : new ArrayList<AlgorithmPropertyProtocolBuffer.AlgorithmProperty>();
		this.previousTrackTimePairs = (previousTrackTimePairs != null) ? previousTrackTimePairs : new ArrayList<TimePair>();
		this.segmentingPlan = segmentingPlan;
	}
}
