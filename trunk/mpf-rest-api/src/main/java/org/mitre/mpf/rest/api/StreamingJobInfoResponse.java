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

package org.mitre.mpf.rest.api;

import java.util.Date;

public class StreamingJobInfoResponse {
	private Long jobId;
	private String externalId = null;
	private String pipelineName;
//	private int jobPriority = -1;
	private String /*JobStatus*/ jobStatus;
//	private float jobProgress = 0;
	private Date startDate;
	private Date endDate;
	private String outputObjectPath;
	private String streamURI = null;
	private String healthReportCallbackURI = null;
	//terminal if status is JOB_CREATION_ERROR, COMPLETE, CANCELLED, or ERROR - will be set in ModelUtils
	//to maintain the use of only standard Java in the model.api classes
	private boolean terminal;

	private MpfResponse mpfResponse = new MpfResponse();

	public StreamingJobInfoResponse() {}

	public StreamingJobInfoResponse(int errorCode, String errorMessage) {
		this.mpfResponse.setMessage(errorCode, errorMessage);
		this.jobId = -1L;
	}

	/**
	 * @param jobId
	 * @param pipelineName
	 * @param jobPriority
	 * @param jobStatus
	 * @param jobProgress
	 * @param startDate
	 * @param endDate endDate may be null if the streaming job is still active
	 * @param outputObjectPath
	 */
	public StreamingJobInfoResponse(Long jobId, String externalId, String pipelineName, int jobPriority, String /*JobStatus*/ jobStatus, float jobProgress,
									Date startDate, Date endDate, String outputObjectPath,
									String streamURI, String healthReportCallbackURI, boolean terminal) {
		this.mpfResponse.setMessage(0,"success");
		this.jobId = jobId;
		this.externalId = externalId;
		this.pipelineName = pipelineName;
//		this.jobPriority = jobPriority;
		this.jobStatus = jobStatus;
//		this.jobProgress = jobProgress;
		this.startDate = startDate;
		this.endDate = endDate;
		this.outputObjectPath = outputObjectPath;
		this.terminal = terminal;
		this.streamURI = streamURI;
		this.healthReportCallbackURI = healthReportCallbackURI;
	}
	
	public Long getJobId() {
		return jobId;
	}
	public String getExternalId() { return externalId; }
	public String getStreamURI() { return streamURI; }
	public String getHealthReportCallbackURI() { return healthReportCallbackURI; }
	
	public String getPipelineName() {
		return pipelineName;
	}
	
//	public int getJobPriority() {
//		return jobPriority;
//	}
	
	public String /*JobStatus*/ getJobStatus() {
		return jobStatus;
	}
	
	public Date getStartDate() {
		return startDate;
	}

	public Date getEndDate() {
		return endDate;
	}

	public String getOutputObjectPath() {
		return outputObjectPath;
	}

	public MpfResponse getMpfResponse() {
		return mpfResponse;
	}

	public boolean isTerminal() {
		return terminal;
	}
}
