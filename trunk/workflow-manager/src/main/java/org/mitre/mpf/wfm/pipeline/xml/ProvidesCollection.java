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

package org.mitre.mpf.wfm.pipeline.xml;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import org.mitre.mpf.wfm.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ProvidesCollection {
    private static final Logger log = LoggerFactory.getLogger(ProvidesCollection.class);

    @XStreamAlias("properties")
    private List<PropertyDefinition> algorithmProperties;
    public List<PropertyDefinition> getAlgorithmProperties() { return algorithmProperties; }

    private Set<StateDefinition> states;
    public Set<StateDefinition> getStates() { return states; }

    @XStreamOmitField
    private Map<String, PropertyDefinition> algorithmPropertyNameMap;

    public PropertyDefinition getAlgorithmProperty(String propertyName) {
        if(algorithmPropertyNameMap == null) {
            initializePropertyNameMap();
        }
        return algorithmPropertyNameMap.get(TextUtils.trimAndUpper(propertyName));
    }

    private void initializePropertyNameMap() {
        algorithmPropertyNameMap = new HashMap<String, PropertyDefinition>();
        for(PropertyDefinition property : algorithmProperties) {
            algorithmPropertyNameMap.put(property.getName(), property);
        }
    }

    public ProvidesCollection() {
        this.algorithmProperties = new ArrayList<>();
        this.states = new HashSet<StateDefinition>();
    }

    public boolean isValid() {
        // Check each of the properties individually...
        for(PropertyDefinition property : algorithmProperties) {
            if(property == null || !property.isValid()) {
                log.error("{}: invalid property", property);
                return false;
            }
        }

        // Check each of the states individually...
        for(StateDefinition state : states) {
            if(state == null || !state.isValid()) {
                log.error("{}: invalid state", state);
                return false;
            }
        }

        return true;
    }

	/** While not explicitly called, this method is used by XStream when deserializing an object. */
	public Object readResolve() {
		// WARNING!
		// If there were no properties associated with the serialized object, we need to assign the default
		// value here. Assigning it in the constructor or in the member's declaration is not sufficient!
		if(states == null) {
			states = new HashSet<StateDefinition>();
		}

		if(algorithmProperties == null) {
			algorithmProperties = new ArrayList<>();
		}
		return this;
	}
}
