/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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

import com.fasterxml.jackson.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

@JsonTypeName("OutputObject")
@JsonPropertyOrder({"jobId", "jobErrors", "jobWarnings"})
public class JsonOutputObject {

	@JsonProperty("jobId")
	@JsonPropertyDescription("The unique identifier assigned to this job by the system.")
	private long jobId;
	public long getJobId() { return jobId; }

	@JsonProperty("priority")
	@JsonPropertyDescription("The relative priority of this job given as a value between 1 and 9 (inclusive).")
	private int priority;
	public int getPriority() { return priority; }

	@JsonProperty("objectId")
	@JsonPropertyDescription("The unique identifier assigned to this output object.")
	private String objectId;
	public String getObjectId() { return objectId; }

	@JsonProperty("pipeline")
	@JsonPropertyDescription("The pipeline (or workflow) that media and derived information passed through during this job.")
	private JsonPipeline pipeline;
	public JsonPipeline getPipeline() { return pipeline; }

	@JsonProperty("externalJobId")
	@JsonPropertyDescription("The OPTIONAL external identifier assigned to this job by the submitter.")
	private String externalJobId;
	public String getExternalJobId() { return externalJobId; }

	@JsonProperty("siteId")
	@JsonPropertyDescription("The identifier of the system which executed this job and generated this output object.")
	private String siteId;
	public String getSiteId() { return siteId; }

	@JsonProperty("timeStart")
	@JsonPropertyDescription("The timestamp indicating when this job was received.")
	private String timeStart;
	public String getTimeStart() { return timeStart; }

	@JsonProperty("timeStop")
	@JsonPropertyDescription("The timestamp indicating when this job completed.")
	private String timeStop;
	public String getTimeStop() { return timeStop; }

	@JsonProperty("status")
	@JsonPropertyDescription("The high-level summary status of this job. The expected value is COMPLETE.")
	private String status;
	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }

	@JsonProperty("algorithmProperties")
	@JsonPropertyDescription("A map which may be used to override the parameters associated with the pipeline or underlying action for a particular algorithm.")
	private Map<String, Map> algorithmProperties;
	public Map<String, Map> getAlgorithmProperties() { return algorithmProperties; }

	@JsonProperty("jobProperties")
	@JsonPropertyDescription("A map which may be used to override the parameters associated with the pipeline or underlying action.")
	private Map<String, String> jobProperties;
	public Map<String, String> getJobProperties() { return jobProperties; }

	@JsonProperty("media")
	@JsonPropertyDescription("The collection of media processed by this job.")
	private SortedSet<JsonMediaOutputObject> media;
	public SortedSet<JsonMediaOutputObject> getMedia() { return media; }

	@JsonProperty("jobErrors")
	@JsonPropertyDescription("The collection of error messages generated during job execution.")
	private SortedSet<String> jobErrors;
	public SortedSet<String> getJobErrors() { return jobErrors; }

	@JsonProperty("jobWarnings")
	@JsonPropertyDescription("The collection of warning messages generated during job execution.")
	private SortedSet<String> jobWarnings;
	public SortedSet<String> getJobWarnings() { return jobWarnings; }

    public JsonOutputObject(){}

	public JsonOutputObject(long jobId, String objectId, JsonPipeline pipeline, int priority, String siteId,
	                        String externalJobId, String timeStart, String timeStop, String status) {
		this.jobId = jobId;
		this.objectId = objectId;
		this.pipeline = pipeline;
		this.priority = priority;
		this.siteId = siteId;
		this.externalJobId = externalJobId;
		this.timeStart = timeStart;
		this.timeStop = timeStop;
		this.status = status;
		this.media = new TreeSet<>();
		this.jobProperties = new HashMap<>();
		this.algorithmProperties = new HashMap<>();
		this.jobErrors = new TreeSet<>();
		this.jobWarnings = new TreeSet<>();
	}

	@JsonCreator
	public static JsonOutputObject factory(
                @JsonProperty("jobId") long jobId,
                @JsonProperty("objectId") String objectId,
                @JsonProperty("pipeline") JsonPipeline pipeline,
                @JsonProperty("priority") int priority,
                @JsonProperty("siteId") String siteId,
                @JsonProperty("externalJobId") String externalJobId,
                @JsonProperty("timeStart") String timeStart,
                @JsonProperty("timeStop") String timeStop,
                @JsonProperty("status") String status,
                @JsonProperty("algorithmProperties") Map<String, Map> algorithmProperties,
                @JsonProperty("jobProperties") Map<String, String> jobProperties,
                @JsonProperty("media") SortedSet<JsonMediaOutputObject> media,
                @JsonProperty("jobErrors") SortedSet<String> jobErrors,
                @JsonProperty("jobWarnings") SortedSet<String> jobWarnings) {

		JsonOutputObject outputObject = new JsonOutputObject(jobId, objectId, pipeline, priority, siteId,
		                                                     externalJobId, timeStart, timeStop, status);
		if(media != null) {
			outputObject.media.addAll(media);
		}
		if (algorithmProperties != null) {
			outputObject.algorithmProperties.putAll(algorithmProperties);
		}
		if (jobProperties != null) {
			outputObject.jobProperties.putAll(jobProperties);
		}
		if (jobErrors != null) {
			outputObject.jobErrors.addAll(jobErrors);
		}
		if (jobWarnings != null) {
			outputObject.jobWarnings.addAll(jobWarnings);
		}
		return outputObject;
	}
}

