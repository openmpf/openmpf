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
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.wfm.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@XStreamAlias("property-ref")
public class PropertyDefinitionRef {
    private static final Logger log = LoggerFactory.getLogger(PropertyDefinitionRef.class);

	@XStreamAsAttribute
    protected String name;
    public String getName() { return name; }

    protected String value;
    public String getValue() { return value; }

	/**
	 * Creates a new reference to an existing property.
	 * @param name The name of the existing property. This is preprocessed using {@link org.mitre.mpf.wfm.util.TextUtils#trimAndUpper(String)}.
	 * @param value The value for this property. No preprocessing is performed on this value.
	 */
    public PropertyDefinitionRef(String name, String value) {
        this.name = TextUtils.trimAndUpper(name);
        this.value = value;
    }

    @Override
    public int hashCode() {
        return (name == null) ? 17 : name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == null || (!(obj instanceof PropertyDefinitionRef) && !(obj instanceof PropertyDefinition) && !(obj instanceof String))) {
            return false;
        } else if(obj instanceof PropertyDefinitionRef) {
            PropertyDefinitionRef casted = (PropertyDefinitionRef)obj;
            return TextUtils.nullSafeEquals(name, casted.name);
        }
        else if(obj instanceof PropertyDefinition) {
            PropertyDefinition casted = (PropertyDefinition)obj;
            return TextUtils.nullSafeEquals(name, casted.getName());
        } else {
            String casted = TextUtils.trimAndUpper((String)obj);
            return TextUtils.nullSafeEquals(name, casted);
        }
    }

    @Override
    public String toString() {
        return String.format("%s#<name= '%s', value= '%s'>",
                this.getClass().getSimpleName(),
                this.getName(),
                this.getValue());
    }

    public boolean isValid() {
        if(name == null) {
            log.error("name cannot be null");
            return false;
        } else if(value == null) {
            log.error("{}: value is null", name);
            return false;
        } else {
            return true;
        }
    }
}
