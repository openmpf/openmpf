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


package org.mitre.mpf.nms.util;

import org.apache.commons.lang3.text.StrLookup;
import org.apache.commons.lang3.text.StrSubstitutor;

import java.util.Map;

import static java.util.stream.Collectors.toMap;

public class EnvironmentVariableExpander {

	private static final StrSubstitutor _substitutor = new StrSubstitutor(new StrLookup<String>() {
		public String lookup(String key) {
			return System.getenv().getOrDefault(key, "");
		}
	});

	private EnvironmentVariableExpander() {
	}


	public static String expand(String str) {
		return _substitutor.replace(str);
	}

	public static Map<String, String> expandValues(Map<String, String> map) {
		return map.entrySet().stream()
				.collect(toMap(Map.Entry::getKey, e -> expand(e.getValue())));

	}
}
