/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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

import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.segmenting.SegmentingPlan;

import java.util.Map;
import java.util.Set;

public class DetectionContext {
    private final SegmentingPlan segmentingPlan;
    public SegmentingPlan getSegmentingPlan() { return segmentingPlan; }

    private final long jobId;
    public long getJobId() { return jobId; }

    private final int taskIndex;
    public int getTaskIndex() { return taskIndex; }

    private final String taskName;
    public String getTaskName() { return taskName; }

    private final int actionIndex;
    public int getActionIndex() { return actionIndex; }

    private final String actionName;
    public String getActionName() { return actionName; }

    private final boolean isFirstDetectionTask;
    public boolean isFirstDetectionTask() { return isFirstDetectionTask; }

    private final Map<String, String> algorithmProperties;
    public Map<String, String> getAlgorithmProperties() {
        return algorithmProperties;
    }

    private final Set<Track> previousTracks;
    public Set<Track> getPreviousTracks() { return previousTracks; }

    public DetectionContext(
            long jobId,
            int taskIndex,
            String taskName,
            int actionIndex,
            String actionName,
            boolean isFirstDetectionTask,
            Map<String, String> algorithmProperties,
            Set<Track> previousTracks,
            SegmentingPlan segmentingPlan) {
        this.jobId = jobId;
        this.taskIndex = taskIndex;
        this.taskName = taskName;
        this.actionIndex = actionIndex;
        this.actionName = actionName;
        this.isFirstDetectionTask = isFirstDetectionTask;
        this.algorithmProperties = algorithmProperties;
        this.previousTracks = previousTracks;
        this.segmentingPlan = segmentingPlan;
    }

    @Override
    public String toString() {
        return "DetectionContext: jobId: " + jobId + ", taskIndex: " + taskIndex + ", taskName: " + taskName +
                ", actionIndex: " + actionIndex + ", actionName: " + actionName + ", algorithmProperties: " + algorithmProperties;
    }
}
