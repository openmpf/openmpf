/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2020 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2020 The MITRE Corporation                                       *
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

public class SingleJobInfo {
	private Long jobId;
	private String pipelineName;
	private int jobPriority = -1;
	private String jobStatus;
	private float jobProgress = 0;
	private Instant startDate;
	private Instant endDate;
	private String outputObjectPath;
	//terminal if status is JOB_CREATION_ERROR, COMPLETE, CANCELLED, or ERROR.
	private boolean terminal;

	public SingleJobInfo() {}

	public SingleJobInfo(Long jobId, String pipelineName, int jobPriority, String jobStatus, float jobProgress,
	                     Instant startDate, Instant endDate, String outputObjectPath, boolean terminal) {
		this.jobId = jobId;
		this.pipelineName = pipelineName;
		this.jobPriority = jobPriority;
		this.jobStatus = jobStatus;
		this.jobProgress = jobProgress;
		this.startDate = startDate;
		this.endDate = endDate;
		this.outputObjectPath = outputObjectPath;
		this.terminal = terminal;
	}

	public Long getJobId() {
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
	public void setJobProgress(float jobProgress) {
		this.jobProgress = jobProgress;
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

	public boolean isTerminal() {
		return terminal;
	}
}
