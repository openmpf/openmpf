/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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

import java.util.*;

public class JobCreationRequest {
	private List<JobCreationMediaData> media = new LinkedList<>();
	private Map<String, String> jobProperties = new HashMap<>();
	private Map<String, Map<String, String>> algorithmProperties = new HashMap<>();
	// One or the other of these segment boundary lists will be set, not both.
	private List<JobCreationSegmentBoundary> segmentFrameBoundaries = new ArrayList<>();
	private List<JobCreationSegmentBoundary> segmentTimeBoundaries = new ArrayList<>();
	private String externalId = null;
	private String pipelineName = null;
	private Boolean buildOutput = null; //will use a server side property if null
	private Integer priority = null; //will be set to 4 (default) on the server side if null
	private String callbackURL = null; // the URL to send a response after the job completes
	private String callbackMethod = "POST"; // the method to send the response back after a job completes


	public List<JobCreationMediaData> getMedia() {
		return media;
	}
	public void setMedia(List<JobCreationMediaData> media) {
		this.media = media;
	}

	public Map<String, String> getJobProperties() {
		return jobProperties;
	}
	public void setJobProperties(Map<String, String> jobProperties) {
		this.jobProperties = jobProperties;
	}

	public Map<String, Map<String, String>> getAlgorithmProperties() {
		return algorithmProperties;
	}
	public void setAlgorithmProperties(Map<String, Map<String, String>> algorithmProperties) {
		this.algorithmProperties = algorithmProperties;
	}

	public List<JobCreationSegmentBoundary> getSegmentFrameBoundaries() { return segmentFrameBoundaries; }
	public void setSegmentFrameBoundaries(List<JobCreationSegmentBoundary> segments) { this.segmentFrameBoundaries = segments; }

	public List<JobCreationSegmentBoundary> getSegmentTimeBoundaries() { return segmentTimeBoundaries; }
	public void setSegmentTimeBoundaries(List<JobCreationSegmentBoundary> segments) { this.segmentTimeBoundaries = segments; }

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

	public Boolean getBuildOutput() {
		return buildOutput;
	}
	public void setBuildOutput(Boolean buildOutput) {
		this.buildOutput = buildOutput;
	}

	public Integer getPriority() {
		return priority;
	}
	public void setPriority(Integer priority) {
		this.priority = priority;
	}

	public String getCallbackURL() {return callbackURL;	}
	public void setCallbackURL(String callbackURL) {this.callbackURL = callbackURL;	}

	public String getCallbackMethod() {return callbackMethod;}
	public void setCallbackMethod(String callbackMethod) {this.callbackMethod = callbackMethod;	}

}
