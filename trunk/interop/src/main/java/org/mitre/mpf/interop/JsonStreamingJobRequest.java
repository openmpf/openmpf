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

package org.mitre.mpf.interop;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.HashMap;
import java.util.Map;

@JsonTypeName("StreamingJobRequest")
public class JsonStreamingJobRequest {

	@JsonProperty("externalId")
	@JsonPropertyDescription("The OPTIONAL user-submitted identifier to use for this streaming job. This identifier is referenced in certain output objects.")
	private String externalId;
	public String getExternalId() { return externalId; }

	@JsonProperty("stream")
	@JsonPropertyDescription("Information about the stream to process in this streaming job. This is presented as an object which contains the stream URI, media properties and other parameters about the stream.")
	private JsonStreamingInputObject stream;
	public void setStream(JsonStreamingInputObject stream) { this.stream=stream; }
	public JsonStreamingInputObject getStream() { return stream; }

	@JsonProperty("algorithmProperties")
	@JsonPropertyDescription("Properties to apply to this streaming job's algorithms overriding default, job and pipeline properties.")
	private Map<String, Map> algorithmProperties;
	public Map<String, Map> getAlgorithmProperties() { return algorithmProperties; }

	@JsonProperty("jobProperties")
	@JsonPropertyDescription("Properties to apply to this streaming job, overriding default and pipeline properties.")
	private Map<String, String> jobProperties;
	public Map<String, String> getJobProperties() { return jobProperties; }

	@JsonProperty("outputObjectEnabled")
	@JsonPropertyDescription("A boolean flag indicating if output objects should be created for this streaming job.")
	private boolean outputObjectEnabled;
	public boolean isOutputObjectEnabled() { return outputObjectEnabled; }

	@JsonProperty("pipeline")
	@JsonPropertyDescription("The pipeline (or workflow) that media and derived information will pass through during the streaming job.")
	private JsonPipeline pipeline;
	public JsonPipeline getPipeline() { return pipeline; }

	@JsonProperty("priority")
	@JsonPropertyDescription("The relative priority of the streaming job which may be in the range 1-9.")
	private int priority;
	public int getPriority() { return priority; }

	@JsonProperty("healthReportCallbackURI")
	@JsonPropertyDescription("The OPTIONAL URI to make a callback of the health reports for this streaming job.")
	private String healthReportCallbackURI;
	public String getHealthReportCallbackURI() { return healthReportCallbackURI; }

	@JsonProperty("summaryReportCallbackURI")
	@JsonPropertyDescription("The OPTIONAL URI to make a callback of the summary reports for this streaming job.")
	private String summaryReportCallbackURI;
	public String getSummaryReportCallbackURI() { return summaryReportCallbackURI; }

	@JsonProperty("newTrackAlertCallbackURI")
	@JsonPropertyDescription("The OPTIONAL URI to make a callback for a new track alert within this streaming job.")
	private String newTrackAlertCallbackURI;
	public String getNewTrackAlertCallbackURI() { return newTrackAlertCallbackURI; }


	@JsonProperty("callbackMethod")
	@JsonPropertyDescription("The OPTIONAL method to connect to the callback URIs. GET or POST.")
	private String callbackMethod;
	public String getCallbackMethod() { return callbackMethod; }

	public JsonStreamingJobRequest(String externalId, boolean outputObjectEnabled, JsonPipeline pipeline, int priority) {
		this(externalId, outputObjectEnabled, pipeline, priority,null, null, null,null);
	}

	public JsonStreamingJobRequest(String externalId, boolean outputObjectEnabled, JsonPipeline pipeline, int priority,
								   String healthReportCallbackURI, String summaryReportCallbackURI, String newTrackAlertCallbackURI,
								   String callbackMethod) {
		this.externalId = externalId;
		this.outputObjectEnabled = outputObjectEnabled;
		this.pipeline = pipeline;
		this.priority = priority;
		this.healthReportCallbackURI = healthReportCallbackURI;
		this.summaryReportCallbackURI = summaryReportCallbackURI;
		this.newTrackAlertCallbackURI = newTrackAlertCallbackURI;
		this.callbackMethod = callbackMethod;
		this.stream = null;
		this.algorithmProperties = new HashMap<>();
		this.jobProperties = new HashMap<>();
	}

	@JsonCreator
	public static JsonStreamingJobRequest factory(@JsonProperty("externalId") String externalId,
                                                  @JsonProperty("outputObjectEnabled") boolean outputObjectEnabled,
                                                  @JsonProperty("pipeline") JsonPipeline pipeline,
                                                  @JsonProperty("priority") int priority,
												  @JsonProperty("healthReportCallbackURI") String healthReportCallbackURI,
												  @JsonProperty("summaryReportCallbackURI") String summaryReportCallbackURI,
												  @JsonProperty("newTrackAlertCallbackURI") String newTrackAlertCallbackURI,
                                                  @JsonProperty("callbackMethod") String callbackMethod,
                                                  @JsonProperty("stream") JsonStreamingInputObject stream,
                                                  @JsonProperty("algorithmProperties") Map<String, Map> algorithmProperties,
                                                  @JsonProperty("jobProperties") Map<String, String> jobProperties) {
		JsonStreamingJobRequest jsonStreamingJobRequest = new JsonStreamingJobRequest(externalId, outputObjectEnabled, pipeline, priority,
																healthReportCallbackURI,summaryReportCallbackURI,newTrackAlertCallbackURI,callbackMethod);
		if(stream != null) {
			jsonStreamingJobRequest.stream = stream;
		}

		// update to support the priority scheme (from lowest to highest):
		// action-property defaults (lowest) -> action-properties -> job-properties -> algorithm-properties -> media-properties (highest)
		if(algorithmProperties != null) {
			jsonStreamingJobRequest.algorithmProperties.putAll(algorithmProperties);
		}

		if(jobProperties != null) {
			jsonStreamingJobRequest.jobProperties.putAll(jobProperties);
		}
		return jsonStreamingJobRequest;
	}
}