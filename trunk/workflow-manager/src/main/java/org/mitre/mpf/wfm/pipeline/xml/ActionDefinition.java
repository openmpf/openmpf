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
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.wfm.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/** An ActionDefinition defines an implementation of an Algorithm. */
@XStreamAlias("action")
public class ActionDefinition {
    private static final Logger log = LoggerFactory.getLogger(ActionDefinition.class);

	/** The REQUIRED name of this action. Internally stored trimmed to null and in uppercase. */
    @XStreamAsAttribute
    private String name;
	public String getName() { return name; }

	/** The REQUIRED algorithm name referenced by this action. Internally stored trimmed to null and in uppercase. */
    @XStreamAsAttribute
    @XStreamAlias("algorithm-ref")
    private String algorithmRef;
	public String getAlgorithmRef() { return algorithmRef; }

	/** The REQUIRED description of this action. Internally stored trimmed to null. */
    private String description;
	public String getDescription() { return description; }

	/** Gets the property values which have been included in this action definition. These properties must map to properties which are defined by the referenced algorithm. */
    @XStreamAlias("property-refs")
    private Set<PropertyDefinitionRef> properties;
	public Set<PropertyDefinitionRef> getProperties() { return properties; }

    /** Creates a new instance of this class using the specified parameters.
     * @param name The REQUIRED non-null name of this action. Preprocessed using {@link org.mitre.mpf.wfm.util.TextUtils#trimAndUpper(String)}.
     * @param algorithmRef The REQUIRED non-null name of the AlgorithmDefinition from which this Action is derived. Preprocessed using {@link org.mitre.mpf.wfm.util.TextUtils#trimAndUpper(String)}.
     * @param description The REQUIRED non-null description for this Action. Preprocessed using {@link org.mitre.mpf.wfm.util.TextUtils#trim(String)}.
     */
    public ActionDefinition(String name, String algorithmRef, String description) {
        this.name = TextUtils.trimAndUpper(name);
        this.algorithmRef = TextUtils.trimAndUpper(algorithmRef);
        this.description = TextUtils.trim(description);
	    this.properties = new HashSet<PropertyDefinitionRef>();
    }

    @Override
    public int hashCode() { return TextUtils.nullSafeHashCode(name); }

    /** Determines if this ActionDefinition is equivalent to a given obj according to the following algorithm:
     * <ul>
     *     <li>If the target object is null, and the target object is not an ActionDefinition, ActionDefinitionRef, or a String, return false.</li>
     *     <li>If the target object is an ActionDefinition, return true iff the target ActionDefinition has the same (case-insensitive) name as this ActionDefinition.</li>
     *     <li>If the target object is an ActionDefinitionRef, return true iff the target ActionDefinitionRef has the same (case-insensitive) name as this ActionDefinition.</li>
     *     <li>If the target object is a String, return true iff the target String is the same (case-insensitive) as this ActionDefinition's name.</li>
     * </ul>
     * @param obj An object to compare.
     */
    @Override
    public boolean equals(Object obj) {
        if(obj == null || (!(obj instanceof ActionDefinition) && !(obj instanceof ActionDefinitionRef) && !(obj instanceof String))) {
            return false;
        } else if(obj instanceof ActionDefinition) {
            ActionDefinition casted = (ActionDefinition)obj;
            return TextUtils.nullSafeEquals(name, casted.name);
        } else if(obj instanceof ActionDefinitionRef) {
            ActionDefinitionRef casted = (ActionDefinitionRef)obj;
            return TextUtils.nullSafeEquals(name, casted.getName());
        } else {
            String casted = TextUtils.trimAndUpper((String)obj);
            return TextUtils.nullSafeEquals(name, casted);
        }
    }

    @Override
    public String toString() {
        return String.format("%s#<name = '%s', algorithmRef = '%s', description = '%s'>",
                this.getClass().getSimpleName(),
                name,
                algorithmRef,
                description);
    }

    /** An ActionDefinition instance is valid iff
     * <ul>
     *     <li>The name is not blank.</li>
     *     <li>The referenced algorithm is not blank.</li>
     *     <li>The collection of defined properties for this Action does not include any null instances, and each defined property {@link PropertyDefinitionRef#isValid() is valid}.</li>
     * </ul>
     */
    public boolean isValid() {
        if(name == null) {
            log.error("action name cannot be blank");
            return false;
        } else if(algorithmRef == null) {
            log.error("{}: action cannot reference a null algorithm", name);
            return false;
        } else if(description == null) {
            log.error("{}: description cannot be blank", name);
            return false;
        } else {
            if(this.properties != null) {
                for(PropertyDefinitionRef propertyValue : this.properties) {
                    if(propertyValue == null || !propertyValue.isValid()) {
                        log.error("{}: property {} was invalid", name, propertyValue);
                        return false;
                    }
                }
            }
            return true;
        }
    }

	/** While not explicitly called, this method is used by XStream when deserializing an object. */
	public Object readResolve() {
		// WARNING!
		// Collections, if omitted, must be initialized here. It is not sufficient to initialize them
		// in the declaration or constructor.
		if(properties == null) {
			properties = new HashSet<PropertyDefinitionRef>();
		}
		return this;
	}
}
