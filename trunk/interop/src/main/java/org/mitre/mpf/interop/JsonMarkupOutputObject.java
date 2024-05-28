/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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

@JsonTypeName("MarkupOutputObject")
public class JsonMarkupOutputObject {

	@JsonProperty("id")
	@JsonPropertyDescription("The identifier which uniquely identifies this marked-up medium.")
	private long id;
	public long getId() { return id; }

	@JsonProperty("path")
	@JsonPropertyDescription("The URI to the marked-up medium.")
	private String path;
	public String getPath() { return path; }

	@JsonProperty("status")
	@JsonPropertyDescription("The result of the markup operation.")
	private String status;
	public String getStatus() { return status; }

	@JsonProperty("message")
	@JsonPropertyDescription("An optional message containing information about the markup operation.")
	private String message;
	public String getMessage() { return message; }

	@JsonCreator
	public JsonMarkupOutputObject(@JsonProperty("id") long id,
	                              @JsonProperty("path") String path,
	                              @JsonProperty("status") String status,
	                              @JsonProperty("message") String message) {
		this.id = id;
		this.path = path;
		this.status = status;
		this.message = message;
	}
}
