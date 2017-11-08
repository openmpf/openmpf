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

package org.mitre.mpf.rest.api;

import java.util.Date;

public class StreamingJobInfo {
	private Long jobId;
	public Long getJobId() {
		return jobId;
	}
	private String pipelineName;
	public String getPipelineName() {
		return pipelineName;
	}
	// TODO jobPriority may be included in a later release
//	private int jobPriority = -1;
//	public int getJobPriority() { return jobPriority; }
	private String /*JobStatus*/ jobStatus;
	public String /*JobStatus*/ getJobStatus() {
		return jobStatus;
	}
	// TODO jobProgress (alternative name jobRunTime) may be included in a later release
// 	private float jobProgress;
//	public float getJobProgress() { return jobProgress; }
	private Date startDate;
	public Date getStartDate() {
		return startDate;
	}
	private Date endDate;
	public Date getEndDate() {
		return endDate;
	}
	private String outputObjectDirectory;
	public String getOutputObjectDirectory() {
		return outputObjectDirectory;
	}
	private String streamUri = null;
	public String getStreamUri() { return streamUri; }

	//terminal if status is JOB_CREATION_ERROR, COMPLETE, CANCELLED, or ERROR - will be set in ModelUtils
	//to maintain the use of only standard Java in the model.api classes
	private boolean terminal;
	public boolean isTerminal() {
		return terminal;
	}

	public StreamingJobInfo() {}

	/** Constructor
	 * @param jobId job id of this streaming job
	 * @param pipelineName name of the pipeline defined for this job
	 * @param jobPriority priority associated with this job
	 * @param jobStatus status of this job
	 * @param jobProgress progress of this job
	 * @param startDate date when this job was started
	 * @param endDate endDate may be null if the streaming job is still active
	 * @param outputObjectDirectory directory where objects from this streaming job are created
	 * @param streamUri URI of the streaming data
	 * @param terminal if true, marks a terminal error
	 */
	public StreamingJobInfo(Long jobId, String pipelineName, int jobPriority, String /*JobStatus*/ jobStatus, float jobProgress,
									Date startDate, Date endDate, String outputObjectDirectory,
									String streamUri, boolean terminal) {
		this.jobId = jobId;
		this.pipelineName = pipelineName;
		this.jobStatus = jobStatus;
		// TODO jobPriority and jobProgress (alternate name jobRunTime) may be included in a later release
//		this.jobPriority = jobPriority;
//		this.jobProgress = jobProgress;
		this.startDate = startDate;
		this.endDate = endDate;
		this.outputObjectDirectory = outputObjectDirectory;
		this.terminal = terminal;
		this.streamUri = streamUri;
	}
	
}
