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

package org.mitre.mpf.rest.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.validation.Valid;

import org.mitre.mpf.rest.api.pipelines.transients.TransientPipelineDefinition;
import org.mitre.mpf.rest.api.util.Utils;

import com.google.common.collect.ImmutableMap;

public record JobCreationRequest(
		@Valid
		List<JobCreationMediaData> media,
		Map<String, String> jobProperties,
		Map<String, Map<String, String>> algorithmProperties,
		String externalId,
		String pipelineName,
		TransientPipelineDefinition pipelineDefinition,
		Boolean buildOutput, // will use a server side property if null
		Integer priority, // will be set to default on the server side if null
		String callbackURL,
		String callbackMethod
) {
	public JobCreationRequest {
		media = Utils.toImmutableList(media);
		jobProperties = Utils.toImmutableMap(jobProperties);

		algorithmProperties = Optional.ofNullable(algorithmProperties)
				.stream()
				.flatMap(m -> m.entrySet().stream())
				.collect(ImmutableMap.toImmutableMap(
						Map.Entry::getKey,
						e -> Utils.toImmutableMap(e.getValue())));

		callbackMethod = Objects.requireNonNullElse(callbackMethod, "POST");
	}
}
