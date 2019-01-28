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

import com.google.common.collect.ImmutableList;
import org.mitre.mpf.interop.JsonStage;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.util.TextUtils;

import java.util.Collection;
import java.util.List;

import static java.util.stream.Collectors.toList;

public class TransientStage {
	private final String name;
	public String getName() { return name; }

	private final String description;
	public String getDescription() { return description; }

	private final ActionType actionType;
	public ActionType getActionType() { return actionType; }

	private final ImmutableList<TransientAction> actions;
	public ImmutableList<TransientAction> getActions() { return actions; }

	public TransientStage(String name, String description, ActionType actionType, Collection<TransientAction> actions) {
		this.name = TextUtils.trimAndUpper(name);
		this.description = TextUtils.trim(description);
		this.actionType = actionType;
		this.actions = ImmutableList.copyOf(actions);

		assert this.name != null : "name must not be null";
		assert this.actionType != null : "operation must not be null";
	}


	public static TransientStage from(JsonStage stage) {
		List<TransientAction> actions = stage.getActions()
				.stream()
				.map(TransientAction::from)
				.collect(toList());

		return new TransientStage(
				stage.getName(),
                stage.getDescription(),
				ActionType.valueOf(TextUtils.trimAndUpper(stage.getActionType())),
				actions);
	}


	@Override
	public String toString() {
		return String.format("%s#<name='%s', description='%s', actionType='%s'>", this.getClass().getSimpleName(),
		                     name, description, actionType);
	}
}
