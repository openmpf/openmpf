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

public class StreamingJobCancelResponse {
	private Long jobId;
	private boolean doCleanup = false;
	private String externalId = null;
	private String outputObjectPath;
	private MpfResponse mpfResponse = new MpfResponse();

	public StreamingJobCancelResponse() {}

	public StreamingJobCancelResponse(boolean doCleanup, int errorCode, String errorMessage) {
		this.mpfResponse.setMessage(errorCode, errorMessage);
		this.jobId = -1L;
		this.doCleanup = doCleanup;
	}

	/**
	 * @param jobId
	 * @param externalId
	 * @param outputObjectPath
	 */
	public StreamingJobCancelResponse(Long jobId, String externalId, String outputObjectPath, boolean doCleanup) {
		this.jobId = jobId;
		this.externalId = externalId;
		this.outputObjectPath = outputObjectPath;
		this.doCleanup = doCleanup;
		this.mpfResponse.setMessage(0,"success");
	}
	
	public Long getJobId() {
		return jobId;
	}

	public String getExternalId() { return externalId; }

	public boolean getDoCleanup() { return doCleanup; }

	public String getOutputObjectPath() {
		return outputObjectPath;
	}

	public MpfResponse getMpfResponse() {
		return mpfResponse;
	}

}
