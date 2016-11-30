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

package org.mitre.mpf.wfm.data.entities.transients;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.mitre.mpf.wfm.util.TextUtils;

import java.util.ArrayList;
import java.util.List;

public class TransientPipeline {
	private String name;
	public String getName() { return name; }

	private String description;
	public String getDescription() { return description; }

	private List<TransientStage> stages;
	public List<TransientStage> getStages() { return stages; }
	public void setStages(List<TransientStage> stages) { this.stages = stages; }

	@JsonCreator
	public TransientPipeline(@JsonProperty("name") String name, @JsonProperty("description") String description) {
		this.name = TextUtils.trimAndUpper(name);
		this.description = TextUtils.trim(description);
		this.stages = new ArrayList<>();

		assert this.name != null : "name cannot be null";
	}

	public String toString() { return String.format("%s#<name='%s', description='%s'>", this.getClass().getSimpleName(), name, description); }
}
