/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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

public class MpfResponse {
	//0 = no error and message will be null, 1 = error and message will be populated, 2 = warning and message will be populated
	public static final int RESPONSE_CODE_SUCCESS = 0;
	public static final int RESPONSE_CODE_ERROR = 1;
	public static final int RESPONSE_CODE_WARNING = 2;

	/*
	 * Fields and getters
	 */
	private int responseCode = RESPONSE_CODE_SUCCESS;

    /**
     * Get the response code associated with this MpfResponse.
     * @return response code, which should be one of the predefined response codes defined for a MpfResponse.
     */
	public int getResponseCode() {
		return responseCode;
	}

    /**
     * Set the mpf response code
     * @param responseCode one of the pre-defined mpfResponse response codes
     */
	public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
        //force null message on response code of 0
        if (this.responseCode == RESPONSE_CODE_SUCCESS) {
            this.message = null;
        }
    }

	private String message = null;

    /**
     * Get the message associated with this MpfResponse.
     * @return message associated with this MpfResponse.
     */
	public String getMessage() {
		return message;
	}
    /**
     * Set the mpf response code and message for this MpfResponse. Cannot set the message without a responseCode.
     * @param responseCode one of the pre-defined mpfResponse response codes
     * @param message mpf response message
     */
	public void setMessage(int responseCode, String message) {
        this.responseCode = responseCode;
        this.message = message;
    }
	
	/*
	 * Constructors
	 */
	public MpfResponse() { }

    /**
     * Construct a mpf response with a response code and response message
     * @param responseCode one of the pre-defined mpfResponse response codes
     * @param message mpf response message
     */
	public MpfResponse(int responseCode, String message) {
	    setMessage(responseCode, message);
	}
}
