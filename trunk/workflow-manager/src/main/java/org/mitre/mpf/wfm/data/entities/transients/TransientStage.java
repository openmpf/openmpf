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

public class TransientStage {
	private final String _name;
	public String getName() { return _name; }

	private final String _description;
	public String getDescription() { return _description; }

	private final ActionType _actionType;
	public ActionType getActionType() { return _actionType; }

	private final ImmutableList<TransientAction> _actions;
	public ImmutableList<TransientAction> getActions() { return _actions; }


	public TransientStage(String name, String description, ActionType actionType, Collection<TransientAction> actions) {
		_name = TextUtils.trimAndUpper(name);
		_description = TextUtils.trim(description);
		_actionType = actionType;
		_actions = ImmutableList.copyOf(actions);

		assert _name != null : "name must not be null";
		assert _actionType != null : "operation must not be null";
	}


	public static TransientStage from(JsonStage stage) {
		List<TransientAction> actions = stage.getActions()
				.stream()
				.map(TransientAction::from)
				.collect(ImmutableList.toImmutableList());

		return new TransientStage(
				stage.getName(),
                stage.getDescription(),
				ActionType.valueOf(TextUtils.trimAndUpper(stage.getActionType())),
				actions);
	}


	@Override
	public String toString() {
		return String.format("%s#<name='%s', description='%s', actionType='%s'>", getClass().getSimpleName(),
		                     _name, _description, _actionType);
	}
}
