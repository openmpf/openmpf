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

import com.fasterxml.jackson.annotation.JsonProperty;
import org.mitre.mpf.wfm.util.TextUtils;

import java.util.HashMap;
import java.util.Map;

public class TransientAction {
	private String name;
	public String getName() { return name; }

	private String description;
	public String getDescription() { return description; }

	private String algorithm;
	public String getAlgorithm() { return algorithm; }

	private Map<String, String> properties;
	public Map<String, String> getProperties() { return properties; }
	public void setProperties(Map<String, String> properties) { this.properties = properties; }

	public TransientAction(@JsonProperty("name") String name, @JsonProperty("description") String description, @JsonProperty("algorithm") String algorithm) {
		this.name = TextUtils.trimAndUpper(name);
		this.description = TextUtils.trim(description);
		this.algorithm = TextUtils.trimAndUpper(algorithm);
		this.properties = new HashMap<>();

		assert this.name != null : "name must not be null";
		assert this.algorithm != null : "algorithm must not be null";
	}
}
