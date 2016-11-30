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

package org.mitre.mpf.mvc.model;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/*
 * represents all of the read properties and other necessary information
 * for displaying the properties in the web ui
 */
public class CompleteMpfPropertiesModel {
	private boolean propertiesModified = false;
	private String errorMessage = "";
	
	private Map<String,Object> readPropertiesMap = new TreeMap<String,Object>();
	
	private Map<String, Object> modifiedPropertiesMap = new HashMap<String, Object>();

	public CompleteMpfPropertiesModel() {}	
	
	public boolean isPropertiesModified() {
		return propertiesModified;
	}
	public void setPropertiesModified(boolean propertiesModified) {
		this.propertiesModified = propertiesModified;
	}

	public String getErrorMessage() {
		return errorMessage;
	}
	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public Map<String,Object> getReadPropertiesMap() {
		return readPropertiesMap;
	}
	public void setReadPropertiesMap(Map<String,Object> readPropertiesMap) {
		this.readPropertiesMap = readPropertiesMap;
	}
	
	public Map<String, Object> getModifiedPropertiesMap() {
		return modifiedPropertiesMap;
	}
	public void setModifiedPropertiesMap(Map<String, Object> modifiedPropertiesMap) {
		this.modifiedPropertiesMap = modifiedPropertiesMap;
	}
}
