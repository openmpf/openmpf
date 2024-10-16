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

public record MpfResponse(int responseCode, String message) {

	// No error and message will be null
	public static final int RESPONSE_CODE_SUCCESS = 0;
    // Error and message will be populated
	public static final int RESPONSE_CODE_ERROR = 1;
    // Warning and message will be populated
	public static final int RESPONSE_CODE_WARNING = 2;

    private static MpfResponse _successSingleton = new MpfResponse(RESPONSE_CODE_SUCCESS, null);
    public static MpfResponse success() {
        return _successSingleton;
    }

    public static MpfResponse error(String message) {
        return new MpfResponse(RESPONSE_CODE_ERROR, message);
    }

    public static MpfResponse warning(String message) {
        return new MpfResponse(RESPONSE_CODE_WARNING, message);
    }

    public boolean isSuccessful() {
        return responseCode == RESPONSE_CODE_SUCCESS;
    }
}
