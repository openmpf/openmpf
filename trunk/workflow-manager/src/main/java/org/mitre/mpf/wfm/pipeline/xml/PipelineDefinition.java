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
import org.mitre.mpf.wfm.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@XStreamAlias("pipeline")
public class PipelineDefinition {
    private static final Logger log = LoggerFactory.getLogger(PipelineDefinition.class);

    @XStreamAsAttribute
    private String name;
    public String getName() { return name; }

    private String description;
    public String getDescription() { return description; }

    @XStreamAlias("task-refs")
    private List<TaskDefinitionRef> taskRefs;
    public List<TaskDefinitionRef> getTaskRefs() { return taskRefs; }

    public PipelineDefinition(String name, String description) {
        this.name = TextUtils.trimAndUpper(name);
        this.description = TextUtils.trim(description);
        this.taskRefs = new ArrayList<TaskDefinitionRef>();
    }

    public boolean isValid() {
        if(name == null ) {
            log.error("name cannot be blank");
            return false;
        } else if(description == null) {
            log.error("{}: description cannot be blank", name);
            return false;
        } else if(taskRefs.size() == 0) {
            log.error("{}: one or more tasks must be added to a pipeline", name);
            return false;
        } else {
            for(TaskDefinitionRef taskRef : taskRefs) {
                if(taskRef == null || !taskRef.isValid()) {
                    log.error("{}: taskRef {} is invalid", name, taskRef);
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public int hashCode() {
        return TextUtils.nullSafeHashCode(name);
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == null || (!(obj instanceof PipelineDefinition) && !(obj instanceof String))) {
            return false;
        } else if(obj instanceof PipelineDefinition) {
            PipelineDefinition casted = (PipelineDefinition)obj;
            return TextUtils.nullSafeEquals(name, casted.name);
        } else {
            String casted = TextUtils.trimAndUpper((String)obj);
            return TextUtils.nullSafeEquals(name, casted);
        }
    }

    @Override
    public String toString() {
        return String.format("%s<name = '%s'>",
                this.getClass().getSimpleName(),
                this.name);
    }

	/** While not explicitly called, this method is used by XStream when deserializing an object. */
	public Object readResolve() {
		// WARNING!
		// Collections, if omitted, must be initialized here. It is not sufficient to initialize them
		// in the declaration or constructor.
		if(taskRefs == null) {
			taskRefs = new ArrayList<TaskDefinitionRef>();
		}
		return this;
	}
}
