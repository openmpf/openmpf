/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2020 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2020 The MITRE Corporation                                       *
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

package org.mitre.mpf.rest.api.node;

import java.util.ArrayList;
import java.util.List;

public class NodeManagerModel {
	private String host;
	private boolean core; // spare if false
	private boolean online; // offline if false
	private boolean autoConfigured;
	private List<ServiceModel> services = new ArrayList<ServiceModel>();
	
	public NodeManagerModel() { }
	
	public NodeManagerModel(String host) {
		this.host = host;
	}

	public String getHost() {
		return host;
	}
	public void setHost(String host) {
		this.host = host;
	}

	public boolean isOnline() {
		return online;
	}
	public void setOnline(boolean online) {
		this.online = online;
	}

	public boolean isCoreNode() {
		return core;
	}
	public void setCoreNode(boolean core) {
		this.core = core;
	}

	public boolean isAutoConfigured() {
		return autoConfigured;
	}
	public void setAutoConfigured(boolean autoConfigured) {
		this.autoConfigured = autoConfigured;
	}
	
	public List<ServiceModel> getServices() {
		return services;
	}
	public void setServices(List<ServiceModel> services) {
		this.services = services;
	}
}
