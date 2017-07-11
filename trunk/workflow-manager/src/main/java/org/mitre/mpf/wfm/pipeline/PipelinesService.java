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

package org.mitre.mpf.wfm.pipeline;

import org.mitre.mpf.interop.JsonPipeline;
import org.mitre.mpf.wfm.pipeline.xml.*;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;

public interface PipelinesService {
	JsonPipeline createJsonPipeline(String pipeline);

	SortedSet<String> getPipelineNames();

	SortedSet<String> getActionNames();

	SortedSet<String> getAlgorithmNames();

	SortedSet<String> getTaskNames();

	String getPipelineDefinitionAsJson();

	Set<AlgorithmDefinition> getAlgorithms();

	AlgorithmDefinition getAlgorithm(String name);

	AlgorithmDefinition getAlgorithm(ActionDefinition actionDefinition);

	AlgorithmDefinition getAlgorithm(ActionDefinitionRef actionDefinitionRef);

	Set<ActionDefinition> getActions();

	ActionDefinition getAction(String name);

	ActionDefinition getAction(ActionDefinitionRef name);

	Set<TaskDefinition> getTasks();

	List<TaskDefinition> getTasks(String pipelineName);

	TaskDefinition getTask(String name);

	TaskDefinition getTask(TaskDefinitionRef taskDefinitionRef);

	Set<PipelineDefinition> getPipelines();

	PipelineDefinition getPipeline(String name);

	void reset();

	void deleteAlgorithm(String algorithmName);

	void saveAlgorithm(AlgorithmDefinition algorithmDefinition);

	void deleteAction(String actionName);

	void saveAction(ActionDefinition action);

	void deleteTask(String taskName);

	void saveTask(TaskDefinition task);

	void deletePipeline(String pipelineName);

	void savePipeline(PipelineDefinition pipeline);
}
