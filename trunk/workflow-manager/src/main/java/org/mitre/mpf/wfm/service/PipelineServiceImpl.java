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

package org.mitre.mpf.wfm.service;

import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.businessrules.PipelineManagementBo;
import org.mitre.mpf.wfm.pipeline.xml.AlgorithmDefinition;
import org.mitre.mpf.wfm.pipeline.xml.PipelineDefinition;
import org.mitre.mpf.wfm.pipeline.xml.PropertyDefinition;
import org.mitre.mpf.wfm.pipeline.xml.TaskDefinition;
import org.mitre.mpf.wfm.util.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PipelineServiceImpl implements PipelineService {
	private static final Logger log = LoggerFactory.getLogger(PipelineServiceImpl.class);

	@Autowired
	private PipelineManagementBo pipelineManagementBo;

	@Override
	public List<String> getPipelineNames() {
		return new ArrayList<String>(pipelineManagementBo.getPipelineNames());
	}

	@Override
	public List<String> getTaskNames() {
		return new ArrayList<String>(pipelineManagementBo.getTaskNames());
	}

	@Override
	public List<String> getActionNames() {
		return new ArrayList<String>(pipelineManagementBo.getActionNames());
	}

	@Override
	public List<String> getAlgorithmNames() {
		return new ArrayList<String>(pipelineManagementBo.getAlgorithmNames());
	}

	@Override
	public List<PropertyDefinition> getAlgorithmProperties(String algorithmName) {
		return pipelineManagementBo.getAlgorithmProperties(algorithmName);
	}

	@Override
	public String getPipelineDefinitionAsJson() {
		return pipelineManagementBo.getPipelineDefinitionAsJson();
	}

	@Override
	public boolean addAlgorithm(AlgorithmDefinition algorithm) {
		return pipelineManagementBo.addAlgorithm(algorithm);
	}

	@Override
	public Tuple<Boolean, String> addAndSaveAlgorithm(AlgorithmDefinition algorithm) {
		return pipelineManagementBo.addAndSaveAlgorithm(algorithm);
	}

	@Override
	public void addAndSaveAction(String actionName, String actionDescription, String algorithmName, Map<String, String> propertySettings) throws WfmProcessingException {
		pipelineManagementBo.addAndSaveAction(actionName, actionDescription, algorithmName, propertySettings);
	}

	@Override
	public Tuple<Boolean,String> addAndSaveActionDeprecated(String actionName, String actionDescription, String algorithmName, Map<String, String> propertySettings) {
		return pipelineManagementBo.addAndSaveActionDeprecated(actionName, actionDescription, algorithmName, propertySettings);
	}
	
	@Override
	public boolean addTaskDeprecated(TaskDefinition task) {
		return pipelineManagementBo.addTask(task);
	}
	
	@Override
	public Tuple<Boolean, String> addAndSaveTaskDeprecated(TaskDefinition task) {
		return pipelineManagementBo.addAndSaveTaskDeprecated(task);
	}

	@Override
	public void addAndSaveTask(TaskDefinition task) throws WfmProcessingException {
		pipelineManagementBo.addAndSaveTask(task);
	}

	@Override
	public boolean addPipeline(PipelineDefinition pipeline) {
		return pipelineManagementBo.addPipeline(pipeline);
	}

	@Override
	public Tuple<Boolean, String> addAndSavePipelineDeprecated(PipelineDefinition pipeline) {
		return pipelineManagementBo.addAndSavePipelineDeprecated(pipeline);
	}

	@Override
	public void addAndSavePipeline(PipelineDefinition pipeline) throws WfmProcessingException {
		pipelineManagementBo.addAndSavePipeline(pipeline);
	}

	@Override
	public void removeAndDeleteAlgorithm(String algorithmName) {
		pipelineManagementBo.removeAndDeleteAlgorithm(algorithmName);
	}

	@Override
	public void removeAndDeleteAction(String actionName) throws WfmProcessingException {
		pipelineManagementBo.removeAndDeleteAction(actionName);
	}

	@Override
	public void removeAndDeleteTask(String taskName) {
		pipelineManagementBo.removeAndDeleteTask(taskName);
	}

	@Override
	public void removeAndDeletePipeline(String pipelineName) {
		pipelineManagementBo.removeAndDeletePipeline(pipelineName);
	}

	@Override
	public Tuple<Boolean,String> savePipelineChanges(String type) {
		return pipelineManagementBo.savePipelineChanges(type);
	}
}

