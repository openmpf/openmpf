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

package org.mitre.mpf.wfm.data.entities.transients;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.interop.JsonAction;
import org.mitre.mpf.wfm.util.TextUtils;

import java.util.Map;

import static java.util.stream.Collectors.toMap;

public class TransientAction {
	private final String name;
	public String getName() { return name; }

	private final String description;
	public String getDescription() { return description; }

	private final String algorithm;
	public String getAlgorithm() { return algorithm; }

	private final ImmutableMap<String, String> properties;
	public ImmutableMap<String, String> getProperties() { return properties; }


	public TransientAction(String name, String description, String algorithm, Map<String, String> properties) {
		this.name = TextUtils.trimAndUpper(name);
		this.description = TextUtils.trim(description);
		this.algorithm = TextUtils.trimAndUpper(algorithm);
		this.properties = ImmutableMap.copyOf(properties);

		assert this.name != null : "name must not be null";
		assert this.algorithm != null : "algorithm must not be null";
	}


	public static TransientAction from(JsonAction action) {
		Map<String, String> properties = action.getProperties()
				.entrySet()
				.stream()
				.filter(p -> StringUtils.isNotBlank(p.getKey()) && StringUtils.isNotBlank(p.getValue()))
				.collect(toMap(p -> p.getKey().toUpperCase(), Map.Entry::getValue));

		return new TransientAction(
                action.getName(),
                action.getDescription(),
                action.getAlgorithm(),
				properties);
	}
}
