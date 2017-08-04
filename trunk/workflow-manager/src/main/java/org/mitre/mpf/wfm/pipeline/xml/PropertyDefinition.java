/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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
import org.mitre.mpf.wfm.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

@XStreamAlias("property")
public class PropertyDefinition {
	private static final Logger log = LoggerFactory.getLogger(PropertyDefinition.class);

	/** The REQUIRED name of this property. This is always trimmed and stored in uppercase characters. */
	@XStreamAsAttribute
	protected String name;
	public String getName() { return name; }

	/** The REQUIRED description of this property. */
	protected String description;
	public String getDescription() { return description; }

	/** The REQUIRED type of data of this property. */
	@XStreamAsAttribute
	protected ValueType type;
	public ValueType getType() { return type; }

	@XStreamAsAttribute
	protected String defaultValue;
	public String getDefaultValue() { return defaultValue; }

	@XStreamAsAttribute
	protected String propertiesKey;


	public PropertyDefinition(String name, ValueType type, String description, String defaultValue, String propertiesKey) {
		this.name = TextUtils.trimAndUpper(name);
		this.type = type;
		this.description = TextUtils.trim(description);
		this.defaultValue = defaultValue;
		this.propertiesKey = propertiesKey;
	}

	public PropertyDefinition(String name, ValueType type, String description, String defaultValue) {
		this(name, type, description, defaultValue, null);
	}

	public void setDefaultValue(Properties properties) {
		if (propertiesKey != null) {
			defaultValue = properties.getProperty(propertiesKey);
		}
	}

	@Override
	public int hashCode() {
		return (name == null) ? 17 : name.hashCode();
	}

	/*
		AlgorithmProperty instances are equal if they are both null or if the names are the same. Further, an
		AlgorithmPropertyValue can be equivalent to an AlgorithmProperty if they share a name.
	 */
	@Override
	public boolean equals(Object obj) {
		if(obj == null || (!(obj instanceof PropertyDefinition) && !(obj instanceof PropertyDefinitionRef) && !(obj instanceof String))) {
			return false;
		} else if(obj instanceof PropertyDefinitionRef) {
			PropertyDefinitionRef casted = (PropertyDefinitionRef)obj;
			return TextUtils.nullSafeEquals(name, casted.name);
		} else if(obj instanceof PropertyDefinition) {
			PropertyDefinition casted = (PropertyDefinition)obj;
			return TextUtils.nullSafeEquals(name, casted.getName());
		} else {
			String casted = TextUtils.trimAndUpper((String)obj);
			return TextUtils.nullSafeEquals(name, casted);
		}
	}

	public boolean isValid() {
		if (name == null) {
			log.error("property name cannot be null");
			return false;
		} else if (type == null) {
			log.error("{}: type cannot be null", name);
			return false;
		} else if( description == null) {
			log.error("{}: description cannot be null", name);
			return false;
		}

		return true;
	}

	@Override
	public String toString() {
		return String.format("%s#<name = '%s', type = '%s'>",
				this.getClass().getSimpleName(),
				this.getName(),
				this.getType());
	}
}
