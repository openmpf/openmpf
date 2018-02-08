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

package org.mitre.mpf.nms;

import org.apache.commons.lang3.tuple.Pair;
import org.jgroups.Address;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class AddressParser {

	private static final Logger LOG = LoggerFactory.getLogger(AddressParser.class);

	public static final char FQN_SEP = ':';

	private static final Pattern FQN_PATTERN = Pattern.compile("^([^:]+):([^:]+):(.*)");

	private AddressParser() {
	}


	public static Pair<String, NodeTypes> parse(Address address) {
		String addrString = address.toString();
		Matcher matcher = FQN_PATTERN.matcher(addrString);

		if (!matcher.matches() || matcher.groupCount() < 3) {
			LOG.warn("Address {} is not well formed.", address);
			return null;
		}

		NodeTypes nodeType = NodeTypes.lookup(matcher.group(1));
		if (nodeType == null) {
			LOG.warn("Unknown Node Type: {}", matcher.group(1));
			return null;
		}
		String addrHost = matcher.group(2);
		return Pair.of(addrHost, nodeType);
	}


	public static String createFqn(NodeTypes nodeType, String host, String description) {
		return nodeType.name() + FQN_SEP + host + FQN_SEP + description;
	}
}
