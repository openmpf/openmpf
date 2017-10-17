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

package org.mitre.mpf.interop;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.HashMap;
import java.util.Map;

@JsonTypeName("StreamingJobRequest")
public class JsonStreamingJobRequest {

	@JsonPropertyDescription("The OPTIONAL user-submitted identifier to use for this streaming job. This identifier is referenced in certain output objects.")
	private String externalId;
	public String getExternalId() { return externalId; }

	@JsonPropertyDescription("Information about the stream to process in this streaming job. This is presented as an object which contains the stream URI, media properties and other parameters about the stream.")
	private JsonStreamingInputObject stream;
	public JsonStreamingInputObject getStream() { return stream; }

	@JsonPropertyDescription("The stall alert detection threshold to be defined for this stream, milliseconds.")
	private long stallAlertDetectionThreshold;
	public long getStallAlertDetectionThreshold() { return stallAlertDetectionThreshold; }

	@JsonPropertyDescription("The stall alert rate to be defined for this stream, milliseconds.")
	private long stallAlertRate;
	public long getStallAlertRate() { return stallAlertRate; }

	@JsonPropertyDescription("The stall timeout to be defined for this stream, milliseconds.")
	private long stallTimeout;
	public long getStallTimeout() { return stallTimeout; }

	@JsonPropertyDescription("Properties to apply to this streaming job's algorithms overriding default, job and pipeline properties.")
	private Map<String, Map<String,String>> algorithmProperties;
	public Map<String, Map<String,String>> getAlgorithmProperties() { return algorithmProperties; }

	@JsonPropertyDescription("Properties to apply to this streaming job, overriding default and pipeline properties.")
	private Map<String, String> jobProperties;
	public Map<String, String> getJobProperties() { return jobProperties; }

	@JsonPropertyDescription("A boolean flag indicating if output objects should be stored for this streaming job.")
	private boolean outputObjectEnabled;
	public boolean isOutputObjectEnabled() { return outputObjectEnabled; }

	@JsonPropertyDescription("The root directory for all output objects created by this streaming job. May be empty string if output object storage is disabled.")
	private String outputObjectDirectory;
	public String getOutputObjectDirectory() { return outputObjectDirectory; }

	@JsonPropertyDescription("The pipeline (or workflow) that media and derived information will pass through during the streaming job.")
	private JsonPipeline pipeline;
	public JsonPipeline getPipeline() { return pipeline; }

	@JsonPropertyDescription("The relative priority of the streaming job which may be in the range 1-9.")
	private int priority;
	public int getPriority() { return priority; }

	@JsonPropertyDescription("The OPTIONAL URI to make a callback of the health reports for this streaming job.")
	private String healthReportCallbackUri;
	public String getHealthReportCallbackUri() { return healthReportCallbackUri; }

	@JsonPropertyDescription("The OPTIONAL URI to make a callback of the summary reports for this streaming job.")
	private String summaryReportCallbackUri;
	public String getSummaryReportCallbackUri() { return summaryReportCallbackUri; }

	@JsonPropertyDescription("The OPTIONAL URI to make a callback for a new track alert within this streaming job.")
	private String newTrackAlertCallbackUri;
	public String getNewTrackAlertCallbackUri() { return newTrackAlertCallbackUri; }

	@JsonPropertyDescription("The OPTIONAL method to connect to the callback URIs. GET or POST.")
	private String callbackMethod;
	public String getCallbackMethod() { return callbackMethod; }

  @JsonCreator
  public JsonStreamingJobRequest(@JsonProperty("externalId") String externalId,
      @JsonProperty("outputObjectEnabled") boolean outputObjectEnabled,
      @JsonProperty("outputObjectDirectory") String outputObjectDirectory,
      @JsonProperty("pipeline") JsonPipeline pipeline,
      @JsonProperty("priority") int priority,
      @JsonProperty("stream") JsonStreamingInputObject jsonStream,
      @JsonProperty("stallAlertDetectionThreshold") long stallAlertDetectionThreshold,
      @JsonProperty("stallAlertRate") long stallAlertRate,
      @JsonProperty("stallTimeout") long stallTimeout,
      @JsonProperty("healthReportCallbackUri") String healthReportCallbackUri,
      @JsonProperty("summaryReportCallbackUri") String summaryReportCallbackUri,
      @JsonProperty("newTrackAlertCallbackUri") String newTrackAlertCallbackUri,
      @JsonProperty("callbackMethod") String callbackMethod,
      @JsonProperty("algorithmProperties") Map<String, Map<String,String>> algorithmProperties,
      @JsonProperty("jobProperties") Map<String, String> jobProperties) {

		this.externalId = externalId;
		this.outputObjectEnabled = outputObjectEnabled;
		this.outputObjectDirectory = outputObjectDirectory;
		this.pipeline = pipeline;
		this.priority = priority;
		this.stallAlertDetectionThreshold = stallAlertDetectionThreshold;
		this.stallAlertRate = stallAlertRate;
		this.stallTimeout = stallTimeout;
		this.healthReportCallbackUri = healthReportCallbackUri;
		this.summaryReportCallbackUri = summaryReportCallbackUri;
		this.newTrackAlertCallbackUri = newTrackAlertCallbackUri;
		this.callbackMethod = callbackMethod;
		this.stream = jsonStream;
		this.algorithmProperties = new HashMap<>();
		this.jobProperties = new HashMap<>();

    // Putting algorithm properties as defined here supports the priority scheme (from lowest to highest):
    // action-property defaults (lowest) -> action-properties -> job-properties -> algorithm-properties -> media-properties (highest)
    if ( algorithmProperties != null ) {
      for ( Map.Entry<String,Map<String,String>> property : algorithmProperties.entrySet() ) {
        this.getAlgorithmProperties().put(property.getKey().toUpperCase(), property.getValue());
      }
    }

    if (jobProperties != null) {
      for ( Map.Entry<String,String> property : jobProperties.entrySet() ) {
        this.getJobProperties().put(property.getKey().toUpperCase(), property.getValue());
      }
    }

  }

}