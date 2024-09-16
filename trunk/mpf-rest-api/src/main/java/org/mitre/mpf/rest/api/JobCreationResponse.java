/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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

import java.net.URI;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public class JobCreationResponse {
	private final String _jobId;  // Will be null if there is an error creating the job
	private final MpfResponse _mpfResponse;

	private final TiesDbCheckStatus _tiesDbCheckStatus;

	private final URI _outputObjectUri;


	public JobCreationResponse(int errorCode, String errorMessage) {
		_mpfResponse = new MpfResponse();
		_mpfResponse.setMessage(errorCode, errorMessage);
		_jobId = null;
		_tiesDbCheckStatus = null;
		_outputObjectUri = null;
	}

	public JobCreationResponse(String jobId,
	                           TiesDbCheckStatus tiesDbCheckStatus,
	                           URI outputObjectUri) {
		_mpfResponse = new MpfResponse();
		_mpfResponse.setResponseCode(MpfResponse.RESPONSE_CODE_SUCCESS);
		_jobId = jobId;
		_tiesDbCheckStatus = tiesDbCheckStatus;
		_outputObjectUri = outputObjectUri;
	}

	@JsonCreator
	public JobCreationResponse(
			@JsonProperty("jobId") String jobId,
			@JsonProperty("mpfResponse") MpfResponse mpfResponse,
			@JsonProperty("tiesDbCheckStatus") TiesDbCheckStatus tiesDbCheckStatus,
			@JsonProperty("outputObjectUri") URI outputObjectUri) {
		_jobId = jobId;
		_mpfResponse = mpfResponse;
		_tiesDbCheckStatus = tiesDbCheckStatus;
		_outputObjectUri = outputObjectUri;
	}

	/*
	 * Getters
	 */
	public String getJobId() {
		return _jobId;
	}

	public MpfResponse getMpfResponse() {
		return _mpfResponse;
	}

	public TiesDbCheckStatus getTiesDbCheckStatus() {
		return _tiesDbCheckStatus;
	}

	@JsonInclude(JsonInclude.Include.NON_DEFAULT)
	public URI getOutputObjectUri() {
		return _outputObjectUri;
	}
}
