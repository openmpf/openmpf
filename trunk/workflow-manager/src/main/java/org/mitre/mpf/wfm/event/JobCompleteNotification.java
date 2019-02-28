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

package org.mitre.mpf.wfm.event;

public class JobCompleteNotification {
	private long jobId;
	public long getJobId() { return jobId; }

	private boolean consumed;
	public boolean isConsumed() { return consumed; }
	public void setConsumed(boolean consumed) { this.consumed = consumed; }

	public JobCompleteNotification(long jobId) {
		this.jobId = jobId;
		consumed = false;
	}

	@Override
	public int hashCode() {
		return (int)((jobId) | (int)(jobId >> 32));
	}

	@Override
	public boolean equals(Object obj) {
		if(obj == null || !(obj instanceof JobCompleteNotification)) {
			return false;
		} else {
			return jobId == ((JobCompleteNotification)obj).jobId;
		}
	}

	@Override
	public String toString() {
		return String.format("%s#<jobId=%d, consumed='%s'>", this.getClass().getSimpleName(), jobId, Boolean.toString(consumed));
	}
}
