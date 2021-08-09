/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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

package org.mitre.mpf.rest.api;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public class SingleJobInfo {
    private long jobId;
    private String pipelineName;
    private int jobPriority;
    private String jobStatus;
    private float jobProgress;
    private Instant startDate;
    private Instant endDate;
    private String outputObjectPath;
    private boolean hasCallbacksInProgress;
    private boolean terminal;
    private List<String> mediaUris;

    public SingleJobInfo() { }

    public SingleJobInfo(
            long jobId,
            String pipelineName,
            int jobPriority,
            String jobStatus,
            float jobProgress,
            Instant startDate,
            Instant endDate,
            String outputObjectPath,
            boolean hasCallbacksInProgress,
            boolean terminal,
            Collection<String> mediaUris) {
        this.jobId = jobId;
        this.pipelineName = pipelineName;
        this.jobPriority = jobPriority;
        this.jobStatus = jobStatus;
        this.jobProgress = jobProgress;
        this.startDate = startDate;
        this.endDate = endDate;
        this.outputObjectPath = outputObjectPath;
        this.hasCallbacksInProgress = hasCallbacksInProgress;
        this.terminal = terminal;
        this.mediaUris = List.copyOf(mediaUris);
    }

    public long getJobId() {
        return jobId;
    }

    public String getPipelineName() {
        return pipelineName;
    }

    public int getJobPriority() {
        return jobPriority;
    }

    public String getJobStatus() {
        return jobStatus;
    }

    public float getJobProgress() {
        return jobProgress;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public String getOutputObjectPath() {
        return outputObjectPath;
    }

    public boolean getHasCallbacksInProgress() {
        return hasCallbacksInProgress;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public List<String> getMediaUris() {
        return mediaUris;
    }
}
