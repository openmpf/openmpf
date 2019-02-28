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
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.wfm.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An ActionDefinitionRef can be used to refer to an ActionDefinition with a given name.
 */
@XStreamAlias("action-ref")
public class ActionDefinitionRef {
    private static final Logger log = LoggerFactory.getLogger(ActionDefinitionRef.class);

    @XStreamAsAttribute
    private String name;
    public String getName() { return name; }

    /** Creates a new instance of this class which refers to the target ActionDefinition. */
    public ActionDefinitionRef(String actionDefinitionName) {
        this.name = TextUtils.trimAndUpper(actionDefinitionName);
    }

    @Override
    public String toString() {
        return String.format("%s#<name = '%s'>",
                this.getClass().getSimpleName(),
                name);
    }

    /** Gets the hashCode for this ActionDefinitionRef which is simply the hashCode of the ActionDefinition name associated with this instance. */
    @Override
    public int hashCode() {
        return TextUtils.nullSafeHashCode(name);
    }

    /** Determines if this ActionDefinitionRef is equivalent to a given obj according to the following algorithm:
     * <ul>
     *     <li>If the target object is null, and the target object is not an ActionDefinition, ActionDefinitionRef, or a String, return false.</li>
     *     <li>If the target object is an ActionDefinition, return true iff the target ActionDefinition has the same (case-insensitive) name as this ActionDefinitionRef's referenced ActionDefinition's name.</li>
     *     <li>If the target object is an ActionDefinitionRef, return true iff the target ActionDefinitionRef's referenced ActionDefinition name has the same (case-insensitive) name as this ActionDefinitionRef's referenced ActionDefinition's name.</li>
     *     <li>If the target object is a String, return true iff the target String is the same (case-insensitive) as this ActionDefinitionRef's referenced ActionDefinition's name.</li>
     * </ul>
     * @param obj An object to compare.
     */
    @Override
    public boolean equals(Object obj) {
        if(obj == null || (!(obj instanceof ActionDefinition) && !(obj instanceof ActionDefinitionRef) && !(obj instanceof String))) {
            return false;
        } else if(obj instanceof ActionDefinition) {
            ActionDefinition casted = (ActionDefinition)obj;
            return TextUtils.nullSafeEquals(name, casted.getName());
        } else if(obj instanceof ActionDefinitionRef) {
            ActionDefinitionRef casted = (ActionDefinitionRef)obj;
            return TextUtils.nullSafeEquals(name, casted.name);
        } else {
            String casted = TextUtils.trimAndUpper((String)obj);
            return TextUtils.nullSafeEquals(name, casted);
        }
    }

    /** An ActionDefinitionRef instance is valid iff the name of the referenced ActionDefinition is not blank. */
    public boolean isValid() {
        if(name == null) {
            log.error("actionDefinitionName cannot be null");
            return false;
        } else {
            return true;
        }
    }
}
