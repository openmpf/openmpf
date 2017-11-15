/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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
import org.mitre.mpf.rest.api.node.EnvironmentVariableModel;
import org.mitre.mpf.wfm.service.component.ComponentLanguage;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class StreamingServiceModel {

	private String serviceName;

	private String algorithmName;

	private ComponentLanguage sourceLanguage;

	private String libraryPath;

	private List<EnvironmentVariableModel> environmentVariables;


	public StreamingServiceModel() {

	}

	public StreamingServiceModel(
			String serviceName,
			String algorithmName,
			ComponentLanguage sourceLanguage,
			String libraryPath,
			Collection<EnvironmentVariableModel> environmentVariables) {
		this.serviceName = serviceName;
		this.algorithmName = algorithmName;
		this.sourceLanguage = sourceLanguage;
		this.libraryPath = libraryPath;
		this.environmentVariables = ImmutableList.copyOf(environmentVariables);
	}

	public String getServiceName() {
		return serviceName;
	}

	public String getAlgorithmName() {
		return algorithmName;
	}

	public ComponentLanguage getSourceLanguage() {
		return sourceLanguage;
	}

	public String getLibraryPath() {
		return libraryPath;
	}

	public List<EnvironmentVariableModel> getEnvironmentVariables() {
		return Collections.unmodifiableList(environmentVariables);
	}
}
