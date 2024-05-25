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

package org.mitre.mpf.mvc.model;


public class PropertyModel {

	private String _key;

	private String _value;

	private boolean _needsRestart;

	public PropertyModel() {

	}

    public PropertyModel( String key, String value, boolean needsRestart) {
        _key = key;
        _value = value;
        _needsRestart = needsRestart;
    }


	public String getKey() {
		return _key;
	}
	public void setKey(String key) {
		_key = key;
	}

	public String getValue() {
		return _value;
	}
	public void setValue(String value) {
		_value = value;
	}

	public boolean getNeedsRestart() { return _needsRestart; }

	public String toString() {
        return "_key: " + _key + ", _value: " + _value + ", _needsRestart: " + _needsRestart;
    }
}
