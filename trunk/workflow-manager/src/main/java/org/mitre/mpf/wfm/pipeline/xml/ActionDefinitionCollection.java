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

package org.mitre.mpf.wfm.pipeline.xml;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;

import java.util.HashSet;
import java.util.Set;

/**
 * The ActionDefinitionCollection serves as a container for zero or more ActionDefinition instances.
 */
@XStreamAlias("actions")
//@Component(ActionDefinitionCollection.REF)
public class ActionDefinitionCollection {
    //public static final String REF = "actionDefinitionCollection";

    @XStreamImplicit
    private Set<ActionDefinition> actionDefinitions;

    /** Returns a copy of the ActionDefinitions contained in this collection. */
    public Set<ActionDefinition> getActionDefinitions() { return actionDefinitions; }

    /** Creates a new instance of this class. */
    public ActionDefinitionCollection() {
        this.actionDefinitions = new HashSet<ActionDefinition>();
    }

	/** While not explicitly called, this method is used by XStream when deserializing an object. */
	public Object readResolve() {
		// WARNING!
		// Collections, if omitted, must be initialized here. It is not sufficient to initialize them
		// in the declaration or constructor.
		if(actionDefinitions == null) {
			actionDefinitions = new HashSet<ActionDefinition>();
		}
		return this;
	}
}
