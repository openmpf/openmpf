/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.service;

import com.google.common.collect.ImmutableList;
import org.mitre.mpf.mvc.util.JsonDropLeadingUnderscore;
import org.mitre.mpf.rest.api.node.EnvironmentVariableModel;
import org.mitre.mpf.wfm.service.component.ComponentLanguage;

import java.util.Collection;
import java.util.Collections;
import java.util.List;


@JsonDropLeadingUnderscore
public class StreamingServiceModel {

	private String _serviceName;

	private String _algorithmName;

	private ComponentLanguage _sourceLanguage;

	private String _libraryPath;

	private List<EnvironmentVariableModel> _environmentVariables;


	public StreamingServiceModel() {

	}

	public StreamingServiceModel(
			String serviceName,
			String algorithmName,
			ComponentLanguage sourceLanguage,
			String libraryPath,
			Collection<EnvironmentVariableModel> environmentVariables) {
		_serviceName = serviceName;
		_algorithmName = algorithmName;
		_sourceLanguage = sourceLanguage;
		_libraryPath = libraryPath;
		_environmentVariables = ImmutableList.copyOf(environmentVariables);
	}

	public String getServiceName() {
		return _serviceName;
	}

	public String getAlgorithmName() {
		return _algorithmName;
	}

	public ComponentLanguage getSourceLanguage() {
		return _sourceLanguage;
	}

	public String getLibraryPath() {
		return _libraryPath;
	}

	public List<EnvironmentVariableModel> getEnvironmentVariables() {
		return Collections.unmodifiableList(_environmentVariables);
	}
}

