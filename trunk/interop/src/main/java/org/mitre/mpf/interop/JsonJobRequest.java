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

import java.util.*;

@JsonTypeName("JobRequest")
public class JsonJobRequest {

	@JsonProperty("externalId")
	@JsonPropertyDescription("The OPTIONAL user-submitted identifier to use for this job. This identifier is referenced in certain output objects.")
	private String externalId;
	public String getExternalId() { return externalId; }

	@JsonProperty("media")
	@JsonPropertyDescription("Information about the media to process in this job. This is presented as a list of objects which contain media URI and media properties.")
	private List<JsonMediaInputObject> media;
	public List<JsonMediaInputObject> getMedia() { return media; }

	@JsonProperty("algorithmProperties")
	@JsonPropertyDescription("Properties to apply to this job's algorithms overriding default, job and pipeline properties.")
	private Map<String, Map> algorithmProperties;
	public Map<String, Map> getAlgorithmProperties() { return algorithmProperties; }

	@JsonProperty("jobProperties")
	@JsonPropertyDescription("Properties to apply to this job, overriding default and pipeline properties.")
	private Map<String, String> jobProperties;
	public Map<String, String> getJobProperties() { return jobProperties; }

	@JsonProperty("outputObjectEnabled")
	@JsonPropertyDescription("A boolean flag indicating if output objects should be created for this job.")
	private boolean outputObjectEnabled;
	public boolean isOutputObjectEnabled() { return outputObjectEnabled; }

	@JsonProperty("pipeline")
	@JsonPropertyDescription("The pipeline (or workflow) that media and derived information will pass through during the job.")
	private JsonPipeline pipeline;
	public JsonPipeline getPipeline() { return pipeline; }

	@JsonProperty("priority")
	@JsonPropertyDescription("The relative priority of the job which may be in the range 1-9.")
	private int priority;
	public int getPriority() { return priority; }
	public void setPriority(int priority) { this.priority = priority; }

	@JsonProperty("callbackURL")
	@JsonPropertyDescription("The OPTIONAL URL to make a callback of the completed job.")
	private String callbackURL;
	public String getCallbackURL() { return callbackURL; }

	@JsonProperty("callbackMethod")
	@JsonPropertyDescription("The OPTIONAL method to connect to the callbackURL. GET or POST.")
	private String callbackMethod;
	public String getCallbackMethod() { return callbackMethod; }

	public JsonJobRequest(String externalId, boolean outputObjectEnabled, JsonPipeline pipeline, int priority) {
		this(externalId, outputObjectEnabled, pipeline, priority, null,null);
	}

	public JsonJobRequest(String externalId, boolean outputObjectEnabled, JsonPipeline pipeline, int priority, String callbackURL, String callbackMethod) {
		this.externalId = externalId;
		this.outputObjectEnabled = outputObjectEnabled;
		this.pipeline = pipeline;
		this.priority = priority;
		this.callbackURL = callbackURL;
		this.callbackMethod = callbackMethod;
		this.media = new ArrayList<>();
		this.algorithmProperties = new HashMap<>();
		this.jobProperties = new HashMap<>();
	}

	@JsonCreator
	public static JsonJobRequest factory(@JsonProperty("externalId") String externalId,
	                                     @JsonProperty("outputObjectEnabled") boolean outputObjectEnabled,
	                                     @JsonProperty("pipeline") JsonPipeline pipeline,
	                                     @JsonProperty("priority") int priority,
										 @JsonProperty("callbackURL") String callbackURL,
										 @JsonProperty("callbackMethod") String callbackMethod,
	                                     @JsonProperty("media") List<JsonMediaInputObject> media,
										 @JsonProperty("algorithmProperties") Map<String, Map> algorithmProperties,
										 @JsonProperty("jobProperties") Map<String, String> jobProperties) {
		JsonJobRequest jsonJobRequest = new JsonJobRequest(externalId, outputObjectEnabled, pipeline, priority,callbackURL,callbackMethod);
		if(media != null) {
			jsonJobRequest.media.addAll(media);
		}

		// update to support the priority scheme (from lowest to highest):
		// action-property defaults (lowest) -> action-properties -> job-properties -> algorithm-properties -> media-properties (highest)
		if(algorithmProperties != null) {
			jsonJobRequest.algorithmProperties.putAll(algorithmProperties);
		}

		if(jobProperties != null) {
			jsonJobRequest.jobProperties.putAll(jobProperties);
		}
		return jsonJobRequest;
	}
}