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

package org.mitre.mpf.rest.api.component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class RegisterComponentModel {
	private String _componentName;
	private String _packageFileName;
	private String _fullUploadedFilePath;
    private String _jsonDescriptorPath;
	private Instant _dateUploaded;
	private Instant _dateRegistered;
	private ComponentState _componentState = ComponentState.UNKNOWN;
    private String _algorithmName;
    private String _serviceName;
    private String _streamingServiceName;
	private List<String> _actions = new ArrayList<>();
	private List<String> _tasks = new ArrayList<>();
	private List<String> _pipelines = new ArrayList<>();
	private boolean _isManaged = true;
	private String _version;


	public String getPackageFileName() {
		return _packageFileName;
	}
	public void setPackageFileName(String packageFileName) {
		_packageFileName = packageFileName;
	}


	public String getFullUploadedFilePath() {
		return _fullUploadedFilePath;
	}
	public void setFullUploadedFilePath(String fullUploadedFilePath) {
		_fullUploadedFilePath = fullUploadedFilePath;
	}

	public Instant getDateUploaded() {
		return _dateUploaded;
	}
	public void setDateUploaded(Instant dateUploaded) {
		_dateUploaded = dateUploaded;
	}

	public Instant getDateRegistered() {
		return _dateRegistered;
	}
	public void setDateRegistered(Instant dateRegistered) {
		_dateRegistered = dateRegistered;
	}

	public ComponentState getComponentState() {
		return _componentState;
	}
	public void setComponentState(ComponentState componentState) {
		_componentState = componentState;
	}

	public String getComponentName() {
		return _componentName;
	}

	public void setComponentName(String componentName) {
		_componentName = componentName;
	}

    public String getAlgorithmName() {
        return _algorithmName;
    }

    public void setAlgorithmName(String algorithmName) {
		_algorithmName = algorithmName;
    }

    public String getServiceName() {
        return _serviceName;
    }

    public void setServiceName(String serviceName) {
		_serviceName = serviceName;
    }

    public String getStreamingServiceName() {
		return _streamingServiceName;
    }

    public void setStreamingServiceName(String streamingServiceName) {
		_streamingServiceName = streamingServiceName;
    }

    public String getJsonDescriptorPath() {
        return _jsonDescriptorPath;
    }

    public void setJsonDescriptorPath(String jsonDescriptorPath) {
		_jsonDescriptorPath = jsonDescriptorPath;
    }


	public List<String> getActions() {
		return _actions;
	}

	public void setActions(List<String> actions) {
		_actions = actions;
	}

	public List<String> getTasks() {
		return _tasks;
	}

	public void setTasks(List<String> tasks) {
		_tasks = tasks;
	}

	public List<String> getPipelines() {
		return _pipelines;
	}

	public void setPipelines(List<String> pipelines) {
		_pipelines = pipelines;
	}

	public boolean isManaged() {
		return _isManaged;
	}

	public void setManaged(boolean isManaged) {
		_isManaged = isManaged;
	}

	public String getVersion() {
		return _version;
	}

	public void setVersion(String version) {
		_version = version;
	}
}
