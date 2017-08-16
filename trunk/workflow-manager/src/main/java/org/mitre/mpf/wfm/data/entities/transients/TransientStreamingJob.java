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

package org.mitre.mpf.wfm.data.entities.transients;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransientStreamingJob {
	private long id;
	public long getId() { return id; }

	private TransientPipeline pipeline;
	public TransientPipeline getPipeline() { return pipeline; }

	private String externalId;
	public String getExternalId() { return externalId; }

	private int priority;
	public int getPriority() { return  priority; }

	private long stallAlertDetectionThreshold;
	public void setStallAlertDetectionThreshold(long value) { stallAlertDetectionThreshold = value; }
	public long getStallAlertDetectionThreshold() { return stallAlertDetectionThreshold; }

	private long stallAlertRate;
	public void setStallAlertRate(long value) { stallAlertRate = value; }
	public long getStallAlertRate() { return stallAlertRate; }

	private long stallTimeout;
	public void setStallTimeout(long value) { stallTimeout = value; }
	public long getStallTimeout() { return stallTimeout; }

	private boolean outputEnabled;
	public void setOutputEnabled(boolean enabled) { outputEnabled = enabled; }
	public boolean isOutputEnabled() { return outputEnabled; }

	private String outputObjectDirectory;
	public void setOutputObjectDirectory(String outputObjectDirectory_s) { outputObjectDirectory = outputObjectDirectory_s; }
	public String getOutputObjectDirectory() { return outputObjectDirectory; }

	private TransientStream stream;
	public TransientStream getStream() { return stream; }
	public void setStream(TransientStream stream) { this.stream = stream; }
	public void addStreamMetaData(String key, String value) { getStream().addMetadata(key, value); }

	private Map<String, Map> overriddenAlgorithmProperties;
	public Map<String, Map> getOverriddenAlgorithmProperties() { return overriddenAlgorithmProperties; }
	public void setOverriddenAlgorithmProperties(Map<String, Map> overriddenAlgorithmProperties) { this.overriddenAlgorithmProperties = overriddenAlgorithmProperties; }

	private Map<String, String> overriddenJobProperties;
	public Map<String, String> getOverriddenJobProperties() { return overriddenJobProperties; }
	public void setOverriddenJobProperties(Map<String, String> overriddenJobProperties) { this.overriddenJobProperties = overriddenJobProperties; }

	private boolean cancelled;
	public boolean isCancelled() { return cancelled; }

	private String healthReportCallbackURI;
	public String getHealthReportCallbackURI() { return healthReportCallbackURI; }
	private String summaryReportCallbackURI;
	public String getSummaryReportCallbackURI() { return summaryReportCallbackURI; }
	private String newTrackAlertCallbackURI;
	public String getNewTrackAlertCallbackURI() { return newTrackAlertCallbackURI; }

	private String callbackMethod;
	public String getCallbackMethod() { return callbackMethod; }

	public TransientStreamingJob(@JsonProperty("id") long id,
                                 @JsonProperty("externalId") String externalId,
                                 @JsonProperty("pipeline") TransientPipeline pipeline,
                                 @JsonProperty("priority") int priority,
								 @JsonProperty("stallAlertDetectionThreshold") long stallAlertDetectionThreshold,
								 @JsonProperty("stallAlertRate") long stallAlertRate,
								 @JsonProperty("stallTimeout") long stallTimeout,
								 @JsonProperty("outputEnabled") boolean outputEnabled,
								 @JsonProperty("outputObjectDirectory") String outputObjectDirectory,
                                 @JsonProperty("cancelled") boolean cancelled) {
		this.id = id;
		this.externalId = externalId;
		this.pipeline = pipeline;
		this.priority = priority;
		this.stallAlertDetectionThreshold = stallAlertDetectionThreshold;
		this.stallAlertRate = stallAlertRate;
		this.stallTimeout = stallTimeout;
		this.outputEnabled = outputEnabled;
		this.outputObjectDirectory = outputObjectDirectory;
		this.cancelled = cancelled;
		this.stream = null;
		this.overriddenAlgorithmProperties = new HashMap<>();
		this.overriddenJobProperties = new HashMap<>();
	}

	@JsonCreator
	public TransientStreamingJob(@JsonProperty("id") long id,
                                 @JsonProperty("externalId") String externalId,
                                 @JsonProperty("pipeline") TransientPipeline pipeline,
                                 @JsonProperty("priority") int priority,
								 @JsonProperty("stallAlertDetectionThreshold") long stallAlertDetectionThreshold,
								 @JsonProperty("stallAlertRate") long stallAlertRate,
								 @JsonProperty("stallTimeout") long stallTimeout,
								 @JsonProperty("outputEnabled") boolean outputEnabled,
								 @JsonProperty("outputObjectDirectory") String outputObjectDirectory,
                                 @JsonProperty("cancelled") boolean cancelled,
								 @JsonProperty("healthReportCallbackURI") String healthReportCallbackURI,
								 @JsonProperty("summaryReportCallbackURI") String summaryReportCallbackURI,
								 @JsonProperty("newTrackAlertCallbackURI") String newTrackAlertCallbackURI,
                                 @JsonProperty("callbackMethod") String callbackMethod) {
		this(id,externalId,pipeline,priority,
				stallAlertDetectionThreshold,stallAlertRate,stallTimeout,outputEnabled,outputObjectDirectory,cancelled);
		this.healthReportCallbackURI = healthReportCallbackURI;
		this.summaryReportCallbackURI = summaryReportCallbackURI;
		this.newTrackAlertCallbackURI = newTrackAlertCallbackURI;
		this.callbackMethod = callbackMethod;
	}
}
