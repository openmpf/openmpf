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

import java.util.ArrayList;
import java.util.List;

@JsonTypeName("Stage")
public class JsonStage {

	@JsonProperty("actionType")
	@JsonPropertyDescription("The type of operation performed in this stage (e.g., DETECTION, MARKUP, etc.).")
	private String actionType;
	public String getActionType() { return actionType; }

	@JsonProperty("name")
	@JsonPropertyDescription("The name associated with this stage.")
	private String name;
	public String getName() { return name; }

	@JsonProperty("description")
	@JsonPropertyDescription("The description indicating the purpose of this stage.")
	private String description;
	public String getDescription() { return description; }

	@JsonProperty("actions")
	@JsonPropertyDescription("The collection of actions which are executed in parallel during this stage.")
	private List<JsonAction> actions;
	public List<JsonAction> getActions() { return actions; }

	public JsonStage(String actionType, String name, String description) {
		this.actionType = actionType;
		this.name = name;
		this.description = description;
		this.actions = new ArrayList<JsonAction>();
	}

    public JsonStage(){}

	@JsonCreator
	public static JsonStage factory(@JsonProperty("actionType") String actionType,
	                                @JsonProperty("name") String name,
	                                @JsonProperty("description") String description,
	                                @JsonProperty("actions") List<JsonAction> actions) {
		JsonStage jsonStage = new JsonStage(actionType, name, description);
		if(actions != null) {
			jsonStage.actions.addAll(actions);
		}
		return jsonStage;
	}
}
