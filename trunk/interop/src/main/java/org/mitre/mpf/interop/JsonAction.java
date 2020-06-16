/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2020 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2020 The MITRE Corporation                                       *
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
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

@JsonTypeName("Action")
public class JsonAction {

	@JsonProperty("algorithm")
	@JsonPropertyDescription("The name of the underlying algorithm used to execute this action.")
	private String algorithm;
	public String getAlgorithm() { return algorithm; }

	@JsonProperty("name")
	@JsonPropertyDescription("The name which identifies this action.")
	private String name;
	public String getName() { return name; }

	@JsonProperty("description")
	@JsonPropertyDescription("A brief summary describing the purpose of this action.")
	private String description;
	public String getDescription() { return description; }

	@JsonProperty("properties")
	@JsonPropertyDescription("A map which may be used to override the default parameters associated with the underlying action.")
	private Map<String, String> properties;
	public Map<String, String> getProperties() { return properties; }

	public JsonAction(String algorithm, String name, String description) {
		this.algorithm = algorithm;
		this.name = name;
		this.description = description;
		this.properties = new HashMap<String, String>();
	}

	@JsonCreator
	public static JsonAction factory(@JsonProperty("algorithm") String algorithm,
	                                 @JsonProperty("name") String name,
	                                 @JsonProperty("description") String description,
	                                 @JsonProperty("properties") Map<String, String> properties) {
		JsonAction jsonAction = new JsonAction(algorithm, name, description);
		if(properties != null) {
			jsonAction.properties.putAll(properties);
		}
		return jsonAction;
	}
}
