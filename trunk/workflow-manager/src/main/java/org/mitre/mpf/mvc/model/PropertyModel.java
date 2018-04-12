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

package org.mitre.mpf.mvc.model;


public class PropertyModel {

	private String _key;

	private String _value;

    private boolean _isValueChanged = false;
	private boolean _needsRestartIfChanged = true;

	// Storage of initial value for the set of properties that may be changed without restart (required for RESET)
	private static String _initialValue = null;

    // _isInitialValueChanged will only be true if the current property value is not the same as it was when OpenMPF started up.
    private static boolean _isInitialValueChanged = false;

	public PropertyModel() {

	}


	public PropertyModel( String key, String value, boolean isValueChanged, boolean needsRestartIfChanged) {
		_key = key;
		_value = value;
        _isValueChanged = isValueChanged;

        // Store property value at OpenMPF startup (making it available for later reset for the set of properties that may be changed without OpenMPF restart).
        if ( _initialValue == null ) {
            _initialValue = value;
        }

        // If the value changed, is it currently the same as the initial value?
        if ( isValueChanged && ! _value.equals(_initialValue) ) {
            _isInitialValueChanged = true;
        }

        _needsRestartIfChanged = needsRestartIfChanged;
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

	public boolean getIsValueChanged() { return _isValueChanged; }
	public boolean getNeedsRestartIfChanged() {
		return _needsRestartIfChanged;
	}

    /**
     * Check to see if this property was changed by the admin to a value different than the initial value sometime after OpenMPF started.
     * @return Returns true if the current property value is not the same as it was when OpenMPF started up, false otherwise.
     */
	public boolean getIsInitialValueChanged() { return _isInitialValueChanged; }

    /**
     * Get the OpenMPF startup (initial) value of this property.
     * @return the OpenMPF startup (initial) value of this property.
     */
	public String getInitialValue() { return _initialValue; }

	public void setNeedsRestartIfChanged(boolean needsRestart) {
        _needsRestartIfChanged = needsRestart;
	}
}
