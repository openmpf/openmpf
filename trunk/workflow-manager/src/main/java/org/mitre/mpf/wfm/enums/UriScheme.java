/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.enums;

import java.net.URI;
import java.util.stream.Stream;

public enum UriScheme {

	/** Default: The URI scheme is either unknown or undefined. */
	UNDEFINED(false),

	FILE(false),
	HTTP(true),
	HTTPS(true),
	RTSP(true);

	private final boolean remote;
	public boolean isRemote() { return remote; }

	UriScheme(boolean remote) { this.remote = remote; }

	/** Gets the enumerated value which maps to the case-insensitive input; if no value exists, {@link #UNDEFINED} is returned. */
	public static UriScheme parse(String schemeStr) {
		return Stream.of(values())
				.filter(schemeEnum -> schemeEnum.name().equalsIgnoreCase(schemeStr))
				.findAny()
				.orElse(UNDEFINED);
	}

	public static UriScheme get(URI uri) {
		return parse(uri.getScheme());
	}
}
