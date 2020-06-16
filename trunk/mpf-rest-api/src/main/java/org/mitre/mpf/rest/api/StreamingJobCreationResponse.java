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

public class StreamingJobCreationResponse {
	private long jobId = -1L; //will be -1 if there is an error creating the job
	private String outputObjectDirectory = null;
	private MpfResponse mpfResponse = new MpfResponse();

	/*
	 * Constructors
	 */
	public StreamingJobCreationResponse() { }

	public StreamingJobCreationResponse(int errorCode, String errorMessage) {
		this.mpfResponse.setMessage(errorCode, errorMessage);
		this.jobId = -1L;
	}

	public StreamingJobCreationResponse(long jobId, String outputObjectDirectory) {
		this.mpfResponse.setMessage(MpfResponse.RESPONSE_CODE_SUCCESS, null);
		this.jobId = jobId;
		this.outputObjectDirectory = outputObjectDirectory;
	}
	
	/*
	 * Getters
	 */
	public long getJobId() {
		return jobId;
	}
	public String getOutputObjectDirectory() { return outputObjectDirectory; }
	public MpfResponse getMpfResponse() {
		return mpfResponse;
	}
}
