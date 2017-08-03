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

package org.mitre.mpf.wfm.businessrules;

import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.pipeline.xml.AlgorithmDefinition;
import org.mitre.mpf.wfm.pipeline.xml.PipelineDefinition;
import org.mitre.mpf.wfm.pipeline.xml.PropertyDefinition;
import org.mitre.mpf.wfm.pipeline.xml.TaskDefinition;
import org.mitre.mpf.wfm.util.Tuple;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.SortedSet;

@Component
public interface PipelineManagementBo {
	/** Gets the ordered collection of pipeline names. */
    public SortedSet<String> getPipelineNames();

	/** Gets the ordered collection of algorithm names. */
    public SortedSet<String> getAlgorithmNames();

	/** Gets the ordered collection of action names. */
    public SortedSet<String> getActionNames();

	/** Gets the ordered collection of task (stage) names. */
    public SortedSet<String> getTaskNames();

	/**
	 * Gets the properties associated with an algorithm. If an algorithm with the given name does not exist, an
	 * empty collection is returned.
	 */
    public List<PropertyDefinition> getAlgorithmProperties(String algorithmName);

    /** Gets the pipelines definition XML as an JSON string */
    public String getPipelineDefinitionAsJson();

        /** Adds a new algorithm */
    public boolean addAlgorithm(AlgorithmDefinition algorithm);

    /** Adds a new algorithm and saves it to XML */
    public Tuple<Boolean, String> addAndSaveAlgorithm(AlgorithmDefinition algorithm);

    /** Adds a new action and saves it to XML */
    @Deprecated
    public Tuple<Boolean, String> addAndSaveActionDeprecated(String actionName,
                                                             String actionDescription, String algorithmName,
                                                             Map<String, String> propertySettings);

    public void addAndSaveAction(String actionName,
                                 String actionDescription, String algorithmName,
                                 Map<String, String> propertySettings) throws WfmProcessingException;

    /** Adds a new task */
	public boolean addTask(TaskDefinition task);

    /** Adds a new task and saves it to XML */
    @Deprecated
    public Tuple<Boolean, String> addAndSaveTaskDeprecated(TaskDefinition task);

    /** Adds a new task and saves it to XML */
    public void addAndSaveTask(TaskDefinition task) throws WfmProcessingException;

    /** Adds a new pipeline */
    public boolean addPipeline(PipelineDefinition pipeline);

    /** Adds a new pipeline and saves it to XML */
    @Deprecated
    public Tuple<Boolean, String> addAndSavePipelineDeprecated(PipelineDefinition pipeline);

    /** Creates a new pipeline based on the provided pipeline definition and saves it to XML */
    public void addAndSavePipeline(PipelineDefinition pipeline) throws WfmProcessingException;

    /** Removes an algorithm from memory and XML */
    public void removeAndDeleteAlgorithm(String algorithmName);

    /** Removes an action from memory and XML */
    public void removeAndDeleteAction(String actionName) throws WfmProcessingException;

    /** Removes a task from memory and XML */
    public void removeAndDeleteTask(String taskName);

    /** Removes a pipeline from memory and XML */
    public void removeAndDeletePipeline(String pipelineName);

    public Tuple<Boolean, String> savePipelineChanges(String type);
}

