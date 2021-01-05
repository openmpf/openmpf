/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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

import org.mitre.mpf.nms.ServiceDescriptor;

public class DeployedServiceModel {
	private String name;
	private int rank;
	private String /*States*/ lastKnownState;

	private boolean unlaunchable;
	private String kind;
	private int serviceCount;
	private int restartCount;

    public DeployedServiceModel() {}

	public DeployedServiceModel(ServiceDescriptor sd /*String name, Integer rank, States lastKnownState*/) {
		//serviceName
		this.name = sd.getName();
		this.rank = sd.getRank();
		this.lastKnownState = sd.getLastKnownState().name();
		this.unlaunchable = sd.getFatalIssueFlag();
		if(sd.getService() != null) {
			this.kind = sd.getService().getLauncher();
			this.serviceCount = sd.getService().getCount();
		}
		this.restartCount = sd.getRestarts();
	}

	public String getName() { return name; }
	public Integer getRank() { return rank; }
	public String getLastKnownState() { return lastKnownState; }
	public boolean isUnlaunchable() { return unlaunchable; }
	public String getKind() { return kind; }
	public int getServiceCount() { return serviceCount; }
	public int getRestartCount() { return restartCount; }
}
