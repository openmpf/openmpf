/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

@XStreamAlias("requires")
public class RequiresCollection {
    private static final Logger log = LoggerFactory.getLogger(RequiresCollection.class);

    @XStreamAlias("state-refs")
    private Set<StateDefinitionRef> stateRefs;
    public Set<StateDefinitionRef> getStateRefs() { return stateRefs; }

    public RequiresCollection() {
        this.stateRefs = new HashSet<StateDefinitionRef>();
    }

    public boolean isValid() {
        // Check each of the references individually...
        for(StateDefinitionRef stateRef : stateRefs) {
            if(stateRef == null || !stateRef.isValid()) {
                log.error("{}: invalid stateRef", stateRef);
                return false;
            }
        }

        return true;
    }

	/** While not explicitly called, this method is used by XStream when deserializing an object. */
	public Object readResolve() {
		// WARNING!
		// Collections, if omitted, must be initialized here. It is not sufficient to initialize them
		// in the declaration or constructor.
		if(stateRefs == null) {
			stateRefs = new HashSet<StateDefinitionRef>();
		}
		return this;
	}
}
