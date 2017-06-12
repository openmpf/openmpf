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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class StreamingJobCreationRequest {
//	private List<JobCreationMediaData> media = new LinkedList<>();
	private Map<String, String> jobProperties = new HashMap<>();
	private Map<String, Map> algorithmProperties = new HashMap<>();
	private String externalId = null;
	private String pipelineName = null;
//	private Boolean buildOutput = null; //will use a server side property if null
	private Integer priority = null; //will be set to 4 (default) on the server side if null
	private JobCreationStreamData streamData = new JobCreationStreamData();
	private Boolean enableOutputToDisk = null;     // true or false to write video segments to disk.  Note that this is called buildOutput in other code
	private String healthReportCallbackURI = null; // the URL to send a health report to
	private String summaryReportCallbackURI = null; // the URL to send a summary report to
	private String newTrackAlertCallbackURI = null; // the URL to send a new track alert report to
	private String callbackMethod = "POST"; // the method to send the response back after a job completes


	public JobCreationStreamData getStreamData() {
		return streamData;
	}
	public String getStreamURI() {return streamData.getStreamURI();}
	public Map<String, String> getMediaProperties() { return streamData.getMediaProperties(); }
	public int getSegmentSize() {
		return streamData.getSegmentSize();
	}
	public String getStallCallbackURI() {
		return streamData.getStallCallbackURI();
	}
	public long getStallAlertDetectionThreshold() {
		return streamData.getStallAlertDetectionThreshold();
	}
	public long getStallTimeout() {
		return streamData.getStallTimeout();
	}
	public long getStallAlertRate() { return streamData.getStallAlertRate(); }
	public void setStreamData(JobCreationStreamData streamData) {
		this.streamData = streamData;
	}

	public Map<String, String> getJobProperties() {
		return jobProperties;
	}
	public void setJobProperties(Map<String, String> jobProperties) {
		this.jobProperties = jobProperties;
	}

	public Map<String, Map> getAlgorithmProperties() {
		return algorithmProperties;
	}
	public void setAlgorithmProperties(Map<String, Map> algorithmProperties) {
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

//	public Boolean getBuildOutput() {
//		return buildOutput;
//	}
//	public void setBuildOutput(Boolean buildOutput) {
//		this.buildOutput = buildOutput;
//	}

	public Boolean getBuildOutput() {
		return enableOutputToDisk;
	}
	public void setBuildOutput(Boolean enableOutputToDisk) {
		this.enableOutputToDisk = enableOutputToDisk;
	}

	public Integer getPriority() {
		return priority;
	}
	public void setPriority(Integer priority) {
		this.priority = priority;
	}

	/** return the HealthReportCallbackURI.  May be null if not defined.
	 * @return return the specified callback URI.  May be null if not defined
	 */
	public String getHealthReportCallbackURI() {return healthReportCallbackURI;	}
	public void setHealthReportCallbackURI(String callbackURI) {this.healthReportCallbackURI = callbackURI;	}

	/** return the SummaryReportCallbackURI.  May be null if not defined.
	 * @return return the specified callback URI.  May be null if not defined
	 */
	public String getSummaryReportCallbackURI() {return summaryReportCallbackURI;	}
	public void setSummaryReportCallbackURL(String callbackURI) {this.summaryReportCallbackURI = callbackURI; }

	/** return the NewTrackAlertCallbackURI.  May be null if not defined.
	 * @return return the specified callback URI.  May be null if not defined
	 */
	public String getNewTrackAlertCallbackURI() {return newTrackAlertCallbackURI;	}
	public void setNewTrackAlertCallbackURI(String callbackURI) {this.newTrackAlertCallbackURI = callbackURI; }

	/** Method will return the HTTP method to be used for the callbacks.
	 * @return will return SET or POST or null if the method is not defined
	 */
	public String getCallbackMethod() {return callbackMethod;}
	public void setCallbackMethod(String callbackMethod) {this.callbackMethod = callbackMethod;	}

}