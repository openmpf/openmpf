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

package org.mitre.mpf.rest.api.node;

import org.mitre.mpf.nms.xml.EnvironmentVariable;
import org.mitre.mpf.nms.xml.Service;

import java.util.ArrayList;
import java.util.List;

public class ServiceModel {
	private String serviceName;	
	private String cmd;	
    private List<String> args = new ArrayList<String>();
    private String workingDirectory;
	private int serviceCount;
	private String serviceLauncher;
	
	private String serviceDescription;
	
	List<EnvironmentVariableModel> environmentVariables = new ArrayList<EnvironmentVariableModel>();
	
	public ServiceModel() { }
	
	public ServiceModel(String serviceName, String cmd,
			List<String> args, String workingDirectory,
			int serviceCount, String serviceLauncher, 
			boolean serviceRestart, String serviceDescription,
			List<EnvironmentVariableModel> environmentVariables) {
		this.serviceName = serviceName;
		this.cmd = cmd;
	    this.args = args;
	    this.workingDirectory = workingDirectory;		
		this.serviceCount = serviceCount;
		this.serviceLauncher = serviceLauncher;
		this.serviceDescription = serviceDescription;
		this.environmentVariables = environmentVariables;
	}
	
	public ServiceModel(Service service) {
    	this.serviceName = service.getName();
		this.cmd = service.getCmdPath();
	    this.args = service.getArgs();
	    this.workingDirectory = service.getWorkingDirectory();	
    	this.serviceCount = service.getCount();
    	this.serviceLauncher = service.getLauncher();    	
    	this.serviceDescription = service.getDescription();
    	
    	for (EnvironmentVariable envVar : service.getEnvVars()) {
    		this.environmentVariables.add(new EnvironmentVariableModel(envVar));
    	}
	}

	public String getServiceName() {
		return serviceName;
	}

	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}

	public int getServiceCount() {
		return serviceCount;
	}

	public void setServiceCount(int serviceCount) {
		this.serviceCount = serviceCount;
	}

	public String getServiceLauncher() {
		return serviceLauncher;
	}

	public void setServiceLauncher(String serviceLauncher) {
		this.serviceLauncher = serviceLauncher;
	}

	public String getServiceDescription() {
		return serviceDescription;
	}

	public void setServiceDescription(String serviceDescription) {
		this.serviceDescription = serviceDescription;
	}

	public List<EnvironmentVariableModel> getEnvironmentVariables() {
		return environmentVariables;
	}

	public void setEnvironmentVariables(
			List<EnvironmentVariableModel> environmentVariables) {
		this.environmentVariables = environmentVariables;
	}

	public String getCmd() {
		return cmd;
	}

	public void setCmd(String cmd) {
		this.cmd = cmd;
	}

	public List<String> getArgs() {
		return args;
	}

	public void setArgs(List<String> args) {
		this.args = args;
	}

	public String getWorkingDirectory() {
		return workingDirectory;
	}

	public void setWorkingDirectory(String workingDirectory) {
		this.workingDirectory = workingDirectory;
	}
}
