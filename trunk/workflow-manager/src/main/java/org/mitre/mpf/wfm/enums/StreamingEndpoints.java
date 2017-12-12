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

package org.mitre.mpf.wfm.enums;

public enum StreamingEndpoints {
	//TODO: For future use.
//	DONE_WITH_FRAME,
//	WFM_STREAMING_JOB_NEW_TRACK_ALERTS,

	WFM_STREAMING_JOB_STATUS,
	WFM_STREAMING_JOB_ACTIVITY,
	WFM_STREAMING_JOB_SUMMARY_REPORTS;


	public static final String QUEUE_NAME_PREFIX = "MPF.";

	public String endpointName() {
		return "jms:" + queueName();
	}

	public String queueName() {
		return QUEUE_NAME_PREFIX + name();
	}
}
