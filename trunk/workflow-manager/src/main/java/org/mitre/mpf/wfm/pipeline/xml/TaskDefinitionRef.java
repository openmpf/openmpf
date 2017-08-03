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
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.wfm.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@XStreamAlias("task-ref")
public class TaskDefinitionRef {
    private static final Logger log = LoggerFactory.getLogger(TaskDefinitionRef.class);

    @XStreamAsAttribute
    private String name;
    public String getName() { return name; }

    public TaskDefinitionRef(String name) {
        this.name = TextUtils.trimAndUpper(name);
    }

    @Override
    public int hashCode() {
        return TextUtils.nullSafeHashCode(name);
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == null || (!(obj instanceof TaskDefinition) && !(obj instanceof TaskDefinitionRef) && !(obj instanceof String))) {
            return false;
        } else if(obj instanceof TaskDefinition) {
            TaskDefinition casted = (TaskDefinition)obj;
            return TextUtils.nullSafeEquals(name, casted.name);
        } else if(obj instanceof TaskDefinitionRef) {
            TaskDefinitionRef casted = (TaskDefinitionRef)obj;
            return TextUtils.nullSafeEquals(name, casted.getName());
        } else {
            String casted = TextUtils.trimAndUpper((String)obj);
            return TextUtils.nullSafeEquals(name, casted);
        }
    }

    @Override
    public String toString() {
        return String.format("%s#<name = '%s'>",
                this.getClass().getSimpleName(),
                this.getName());
    }

    public boolean isValid() {
        if(name == null) {
            log.error("name cannot be null");
            return false;
        } else {
            return true;
        }
    }
}
