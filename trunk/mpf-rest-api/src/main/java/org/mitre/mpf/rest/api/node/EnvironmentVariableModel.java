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

package org.mitre.mpf.rest.api.node;

import org.mitre.mpf.nms.xml.EnvironmentVariable;

public class EnvironmentVariableModel {
	private String name; //env var vname
	private String value; //env var value
	private String sep;
	
	public EnvironmentVariableModel() { }
	
	public EnvironmentVariableModel(String name, String value, String sep) {
		this.name = name;
		this.value = value;
		this.sep = sep;
	}
	
	public EnvironmentVariableModel(EnvironmentVariable envVar) {	
		this.name = envVar.getKey();
		this.value = envVar.getValue();
		this.sep = envVar.getSep();
	}

	public String getName() {
		return name;
	}
	
	public String getValue() {
		return value;
	}

	public String getSep() {
		return sep;
	}
}
