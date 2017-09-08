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

public class StreamingJobCancelResponse {
	private Long jobId;
	private boolean doCleanup = false;
	private String outputObjectDirectory;
	private MpfResponse mpfResponse = new MpfResponse();

	public StreamingJobCancelResponse() {}

	public StreamingJobCancelResponse(boolean doCleanup, int errorCode, String errorMessage) {
		this.mpfResponse.setMessage(errorCode, errorMessage);
		this.jobId = -1L;
		this.doCleanup = doCleanup;
	}

	/** Constructor
	 * @param jobId job id of this streaming job
	 * @param outputObjectDirectory root directory for output objects created during this streaming job
	 * @param doCleanup if true, then the caller is requesting that the output object directory is cleaned up prior to cancelling this job
	 */
	public StreamingJobCancelResponse(Long jobId, String outputObjectDirectory, boolean doCleanup) {
		this.jobId = jobId;
		this.outputObjectDirectory = outputObjectDirectory;
		this.doCleanup = doCleanup;
		this.mpfResponse.setMessage(0,"success");
	}
	
	public Long getJobId() {
		return jobId;
	}

	public boolean getDoCleanup() { return doCleanup; }

	public String getOutputObjectDirectory() {
		return outputObjectDirectory;
	}

	public MpfResponse getMpfResponse() {
		return mpfResponse;
	}

}
