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

public class TransientAction {
	private final String _name;
	public String getName() { return _name; }

	private final String _description;
	public String getDescription() { return _description; }

	private final String _algorithm;
	public String getAlgorithm() { return _algorithm; }

	private final ImmutableMap<String, String> _properties;
	public ImmutableMap<String, String> getProperties() { return _properties; }


	public TransientAction(String name, String description, String algorithm, Map<String, String> properties) {
		_name = TextUtils.trimAndUpper(name);
		_description = TextUtils.trim(description);
		_algorithm = TextUtils.trimAndUpper(algorithm);
		_properties = ImmutableMap.copyOf(properties);
	}


	public static TransientAction from(JsonAction action) {
		Map<String, String> properties = action.getProperties()
				.entrySet()
				.stream()
				.filter(p -> StringUtils.isNotBlank(p.getKey()) && StringUtils.isNotBlank(p.getValue()))
				.collect(ImmutableMap.toImmutableMap(p -> p.getKey().toUpperCase(), Map.Entry::getValue));

		return new TransientAction(
                action.getName(),
                action.getDescription(),
                action.getAlgorithm(),
				properties);
	}
}
