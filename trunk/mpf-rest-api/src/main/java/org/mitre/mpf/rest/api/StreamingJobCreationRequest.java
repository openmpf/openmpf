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

import java.util.HashMap;
import java.util.Map;

public class StreamingJobCreationRequest {
	private Map<String, String> jobProperties = new HashMap<>();
	private Map<String, Map<String,String>> algorithmProperties = new HashMap<>();
	private String externalId = null;
	private String pipelineName = null;
	private Integer priority = null; // will be set to 4 (default) on the server side if null

	private JobCreationStreamData stream = new JobCreationStreamData();

	// true or false to write video segments to disk. Note that this is called buildOutput in other code. Will use a server side property if null
	private Boolean enableOutputToDisk = null;

	private String healthReportCallbackUri = null; // the URL to send a health report to
	private String summaryReportCallbackUri = null; // the URL to send a summary report to

	private long stallTimeout = -1L;
	public void setStallTimeout(long stallTimeout) { this.stallTimeout=stallTimeout; }
	public long getStallTimeout() { return stallTimeout; }

	public JobCreationStreamData getStream() {
		return stream;
	}
	public void setStream(JobCreationStreamData streamData) { this.stream = streamData; }
	public String getStreamUri() { return stream.getStreamUri(); }

	public Map<String, String> getMediaProperties() { return stream.getMediaProperties(); }
	public int getSegmentSize() {
		return stream.getSegmentSize();
	}

	public Map<String, String> getJobProperties() {
		return jobProperties;
	}
	public void setJobProperties(Map<String, String> jobProperties) {
		this.jobProperties = jobProperties;
	}

	public Map<String, Map<String,String>> getAlgorithmProperties() {
		return algorithmProperties;
	}
	public void setAlgorithmProperties(Map<String, Map<String,String>> algorithmProperties) {
		this.algorithmProperties = algorithmProperties;
	}

	public String getExternalId() {
		return externalId;
	}
	public void setExternalId(String externalId) {
		this.externalId = externalId;
	}	
	
	public String getPipelineName() {
		return pipelineName;
	}
	public void setPipelineName(String pipelineName) {
		this.pipelineName = pipelineName;
	}

	public Boolean getEnableOutputToDisk() {
		return enableOutputToDisk;
	}
	public void setEnableOutputToDisk(Boolean enableOutputToDisk) {
		this.enableOutputToDisk = enableOutputToDisk;
	}

	public Integer getPriority() {
		return priority;
	}
	public void setPriority(Integer priority) {
		this.priority = priority;
	}

	/** return the HealthReportCallbackUri. May be null if not defined.
	 * @return return the specified callback Uri. May be null if not defined
	 */
	public String getHealthReportCallbackUri() { return healthReportCallbackUri; }

    /**
     * Set the health report callback URI. The HTTP POST method will always be used for sending health reports to this URI.
     * @param callbackUri the health report callback URI.
     */
	public void setHealthReportCallbackUri(String callbackUri) { this.healthReportCallbackUri = callbackUri; }

	/** return the SummaryReportCallbackUri. May be null if not defined.
	 * @return return the specified callback Uri. May be null if not defined
	 */
	public String getSummaryReportCallbackUri() { return summaryReportCallbackUri; }

    /**
     * Set the summary report callback URI. The HTTP POST method will always be used for sending summary reports to this URI.
     * @param callbackUri the summary report callback URI.
     */
	public void setSummaryReportCallbackUri(String callbackUri) { this.summaryReportCallbackUri = callbackUri; }

	/** this method will check the current settings within this streaming job creation request,
	 * and will return true if the current settings are set within the constraints defined for a
	 * streaming job, false otherwise
	 * @return true if settings define a valid streaming job request, false otherwise.
	 */
	public boolean isValidRequest() {
		// do error checks on the streaming job request.
		// TODO check the pipeline name specified in the create streaming job request and make sure it's streaming-capable
		if ( getStream().isValidStreamData() && getStallTimeout() != -1L ) {
			return true;
		} else {
			return false;
		}
	}

}