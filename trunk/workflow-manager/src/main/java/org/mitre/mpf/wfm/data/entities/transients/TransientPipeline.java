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

package org.mitre.mpf.wfm.data.entities.transients;

import com.google.common.collect.ImmutableList;
import org.mitre.mpf.interop.JsonPipeline;
import org.mitre.mpf.wfm.util.TextUtils;

import java.util.List;

public class TransientPipeline {
	private final String _name;
	public String getName() { return _name; }

	private final String _description;
	public String getDescription() { return _description; }

	private final ImmutableList<TransientStage> _stages;
	public ImmutableList<TransientStage> getStages() { return _stages; }


	public TransientPipeline(String name, String description, Iterable<TransientStage> stages) {
		_name = TextUtils.trimAndUpper(name);
		_description = TextUtils.trim(description);
		_stages = ImmutableList.copyOf(stages);

		assert _name != null : "name cannot be null";
	}


	public static TransientPipeline from(JsonPipeline pipeline) {
		List<TransientStage> stages = pipeline.getStages()
				.stream()
				.map(TransientStage::from)
				.collect(ImmutableList.toImmutableList());

		return new TransientPipeline(pipeline.getName(), pipeline.getDescription(), stages);
	}


	@Override
	public String toString() {
		return String.format("%s#<name='%s', description='%s'>", getClass().getSimpleName(), _name, _description);
	}
}
