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
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An AlgorithmDefinition is used to expose an Algorithm which has been integrated into the Media Processing Framework.
 */
@XStreamAlias("algorithm")
public class AlgorithmDefinition {
    private static final Logger log = LoggerFactory.getLogger(AlgorithmDefinition.class);

	/** The REQUIRED name of this algorithm. This is always trimmed and stored in uppercase characters. */
    @XStreamAsAttribute
    private String name;
	public String getName() { return name; }

	/** The REQUIRED description of this algorithm. */
    private String description;
	public String getDescription() { return description; }

	/** The REQUIRED actionType performed by this algorithm. */
    @XStreamAsAttribute
    private ActionType actionType;
	public ActionType getActionType() { return actionType; }

	/** The OPTIONAL collection of states which are provided by the successful execution of this algorithm. */
    @XStreamAlias("provides")
    private ProvidesCollection providesCollection;
	public ProvidesCollection getProvidesCollection() { return providesCollection; }

	/** The OPTIONAL collection of states which are required prior to the execution of this algorithm. */
    @XStreamAlias("requires")
    private RequiresCollection requiresCollection;
	public RequiresCollection getRequiresCollection() { return requiresCollection; }

	/** Creates a new instance of this class using the specified parameters.
	 *
	 * @param actionType The REQUIRED non-null type of operation performed by this algorithm.
	 * @param name The REQUIRED non-null name of the algorithm. This is preprocessed using {@link org.mitre.mpf.wfm.util.TextUtils#trimAndUpper(String)}.
	 * @param description The REQUIRED non-null description of the algorithm. This is preprocessed using {@link org.mitre.mpf.wfm.util.TextUtils#trim(String)}.
	 */
    public AlgorithmDefinition(ActionType actionType, String name, String description) {
        this.actionType = actionType;
	    this.name = TextUtils.trimAndUpper(name);
        this.description = TextUtils.trim(description);
	    this.providesCollection = new ProvidesCollection();
	    this.requiresCollection = new RequiresCollection();
    }

    @Override
    public int hashCode() { return TextUtils.nullSafeHashCode(name); }

    @Override
    public boolean equals(Object obj) {
        if(obj == null || (!(obj instanceof AlgorithmDefinition) && !(obj instanceof String))) {
            return false;
        } else if(obj instanceof AlgorithmDefinition) {
            AlgorithmDefinition casted = (AlgorithmDefinition)obj;
            return TextUtils.nullSafeEquals(name, casted.name);
        } else {
            String casted = TextUtils.trimAndUpper((String)obj);
            return TextUtils.nullSafeEquals(name, casted);
        }
    }

    @Override
    public String toString() {
        return String.format("%s#<name = '%s', operation = '%s'>",
                this.getClass().getSimpleName(),
                name,
                actionType.name());
    }

    /** An AlgorithmDefinition is valid iff...
     * <ul>
     *     <li>Its name is not null.</li>
     *     <li>Its description is not null.</li>
     *     <li>Its operation is not null.</li>
     *     <li>Its ProvidesCollection is valid.</li>
     *     <li>Its RequiresCollection is valid.</li>
     * </ul>
     * @return
     */
    public boolean isValid() {
        if (name == null) {
            log.error("name cannot be blank");
            return false;
        } else if(description == null) {
            log.error("{}: description cannot be blank", name);
            return false;
        } else if(actionType == null) {
	        log.error("{}: actionType cannot be null", name);
	        return false;
        } else if(!providesCollection.isValid()) {
            log.error("{}: the providesCollection is not valid.", name);
            return false;
        } else if(!requiresCollection.isValid()) {
            log.error("{}: the requiresCollection is not valid.", name);
            return false;
        } else {
            return true;
        }
    }

	/** While not explicitly called, this method is used by XStream when deserializing an object. */
	public Object readResolve() {
		// WARNING!
		// Collections, if omitted, must be initialized here. It is not sufficient to initialize them
		// in the declaration or constructor.
		if(providesCollection == null) {
			providesCollection = new ProvidesCollection();
		}

		if(requiresCollection == null) {
			requiresCollection = new RequiresCollection();
		}
		return this;
	}
}
