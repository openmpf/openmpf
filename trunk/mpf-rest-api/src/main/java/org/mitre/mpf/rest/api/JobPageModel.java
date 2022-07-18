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

public class JobPageModel {
    private final long jobId;
    private final String pipelineName;
    private final int jobPriority;
    private final String jobStatus;
    private final float jobProgress;
    private final Instant startDate;
    private final Instant endDate;
    private final String outputObjectPath;
    private final boolean terminal;
    private final String tiesDbStatus;
    private final String callbackStatus;
    private final List<String> mediaUris;
    private boolean outputFileExists = false;

    public JobPageModel(
            long jobId,
            String pipelineName,
            int jobPriority,
            String jobStatus,
            float jobProgress,
            Instant startDate,
            Instant endDate,
            String outputObjectPath,
            boolean terminal,
            String tiesDbStatus,
            String callbackStatus,
            Collection<String> mediaUris) {
        this.jobId = jobId;
        this.pipelineName = pipelineName;
        this.jobPriority = jobPriority;
        this.jobStatus = jobStatus;
        this.jobProgress = jobProgress;
        this.startDate = startDate;
        this.endDate = endDate;
        this.outputObjectPath = outputObjectPath;
        this.terminal = terminal;
        this.tiesDbStatus = tiesDbStatus;
        this.callbackStatus = callbackStatus;
        this.mediaUris = List.copyOf(mediaUris);
    }


    public boolean isOutputFileExists() {
        return outputFileExists;
    }

    public void setOutputFileExists(boolean outputFileExists) {
        this.outputFileExists = outputFileExists;
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

    public String getTiesDbStatus() {
        return tiesDbStatus;
    }

    public String getCallbackStatus() {
        return callbackStatus;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public List<String> getMediaUris() {
        return mediaUris;
    }
}
