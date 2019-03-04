/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.util;

public class MethodStatus {

    public enum StatusCode {UNDEFINED, SUCCESS, ERROR, WARNING };
	private StatusCode statusCode=StatusCode.UNDEFINED;
	public StatusCode getStatus() { return statusCode; };

	private String summary;
	public String getSummary() { return summary; }

	private String detail;
	public String getDetail() { return detail; }

    public MethodStatus(StatusCode statusCode, String summary) { this(statusCode, summary, null); }
    public MethodStatus(StatusCode statusCode, String summary, String detail) {
	    this.statusCode = statusCode;
        this.summary = summary;
        this.detail = detail;
    }

    public boolean isSuccess() { return statusCode == StatusCode.SUCCESS; }
    public boolean isError() { return statusCode == StatusCode.ERROR; }
    public boolean isWarning() { return statusCode == StatusCode.WARNING; }
    public boolean isUndefined() { return statusCode == StatusCode.UNDEFINED; }

    @Override
    public String toString() {
	    return "statusCode: " + statusCode + ", summary: " + summary + ", detail: " + detail;
    }

}
