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

@XStreamAlias("state-ref")
public class StateDefinitionRef {
    private static final Logger log = LoggerFactory.getLogger(StateDefinitionRef.class);

    @XStreamAsAttribute
    private String name;
    public String getName() { return name; }

    public StateDefinitionRef(String name) {
        this.name = TextUtils.trimAndUpper(name);
    }

    @Override
    public int hashCode() {
        return TextUtils.nullSafeHashCode(name);
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == null || (!(obj instanceof StateDefinition) && !(obj instanceof StateDefinitionRef) && !(obj instanceof String))) {
            return false;
        } else if(obj instanceof StateDefinition) {
            StateDefinition casted = (StateDefinition)obj;
            return TextUtils.nullSafeEquals(name, casted.getName());
        } else if (obj instanceof StateDefinitionRef) {
            StateDefinitionRef casted = (StateDefinitionRef)obj;
            return TextUtils.nullSafeEquals(name, casted.name);
        } else {
            String casted = TextUtils.trimAndUpper((String)obj);
            return TextUtils.nullSafeEquals(name, casted);
        }
    }

    public boolean isValid() {
        if(name == null) {
            log.error("name cannot be blank");
            return false;
        } else {
            return true;
        }
    }

    @Override
    public String toString() {
        return String.format("%s#<name = '%s'>",
                this.getClass().getSimpleName(),
                name);
    }
}
