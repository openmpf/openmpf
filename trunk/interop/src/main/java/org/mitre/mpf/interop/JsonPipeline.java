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

package org.mitre.mpf.interop;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.ArrayList;
import java.util.List;

@JsonTypeName("Pipeline")
public class JsonPipeline {

	@JsonProperty("name")
	@JsonPropertyDescription("The name of this pipeline.")
	private String name;
	public String getName() { return name; }

	@JsonProperty("description")
	@JsonPropertyDescription("The description indicating the purpose of this pipeline.")
	private String description;
	public String getDescription() { return description; }

	@JsonProperty("tasks")
	@JsonPropertyDescription("The ordered listing of tasks performed by this pipeline.")
	private List<JsonTask> tasks;
	public List<JsonTask> getTasks() { return tasks; }

    public JsonPipeline(){}

	public JsonPipeline(String name, String description) {
		this.name = name;
		this.description = description;
		this.tasks = new ArrayList<JsonTask>();
	}

	@JsonCreator
	public static JsonPipeline factory(@JsonProperty("name") String name,
	                                   @JsonProperty("description") String description,
	                                   @JsonProperty("tasks") List<JsonTask> tasks) {
		JsonPipeline jsonPipeline = new JsonPipeline(name, description);
		if(tasks != null) {
			jsonPipeline.tasks.addAll(tasks);
		}
		return jsonPipeline;
	}
}
