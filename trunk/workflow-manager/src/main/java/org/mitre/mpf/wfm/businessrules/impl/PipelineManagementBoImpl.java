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

package org.mitre.mpf.wfm.businessrules.impl;

import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.businessrules.PipelineManagementBo;
import org.mitre.mpf.wfm.pipeline.PipelineManager;
import org.mitre.mpf.wfm.pipeline.xml.*;
import org.mitre.mpf.wfm.util.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class PipelineManagementBoImpl implements PipelineManagementBo {
    private static final Logger log = LoggerFactory.getLogger(PipelineManagementBoImpl.class);

    @Autowired
    private PipelineManager pipelineManager;

    @Override
    public SortedSet<String> getPipelineNames() { return pipelineManager.getPipelineNames(); }

    @Override
    public SortedSet<String> getAlgorithmNames() {
        Set<AlgorithmDefinition> algorithmDefinitions = pipelineManager.getAlgorithms();
        SortedSet<String> algorithmNames = new TreeSet<String>();
        for(AlgorithmDefinition algorithmDefinition : algorithmDefinitions) {
            algorithmNames.add(algorithmDefinition.getName());
        }
        return algorithmNames;
    }

    @Override
    public SortedSet<String> getActionNames() {
        Set<ActionDefinition> actionDefinitions = pipelineManager.getActions();
        SortedSet<String> actionNames = new TreeSet<String>();
        for(ActionDefinition actionDefinition : actionDefinitions) {
            actionNames.add(actionDefinition.getName());
        }
        return actionNames;
    }

    @Override
    public SortedSet<String> getTaskNames() {
        Set<TaskDefinition> taskDefinitions = pipelineManager.getTasks();
        SortedSet<String> taskNames = new TreeSet<String>();
        for(TaskDefinition taskDefinition : taskDefinitions) {
            taskNames.add(taskDefinition.getName());
        }
        return taskNames;
    }

    @Override
    public List<PropertyDefinition> getAlgorithmProperties(String algorithmName) {
        AlgorithmDefinition algorithmDefinition = pipelineManager.getAlgorithm(algorithmName);
        if(algorithmDefinition == null) {
            log.warn("Failed to find an algorithm with the name \"" + algorithmName + "\". Returning an empty set.");
            return new ArrayList<>();
        } else {
            // By contract, this will not throw a NPE.
            return algorithmDefinition.getProvidesCollection().getAlgorithmProperties();
        }
    }

    @Override
    public String getPipelineDefinitionAsJson() {
        return pipelineManager.getPipelineDefinitionAsJson();
    }

    @Override
    public Tuple<Boolean, String> addAndSaveActionDeprecated(String actionName,
                                                            String actionDescription, String algorithmName,
                                                            Map<String, String> propertySettings) {

        ActionDefinition actionDef = new ActionDefinition(actionName, algorithmName, actionDescription);
        Set<PropertyDefinitionRef> properties = new HashSet<PropertyDefinitionRef>();
        Set<String> keys = propertySettings.keySet();
        for (String key : keys) {
            PropertyDefinitionRef property = new PropertyDefinitionRef(key, propertySettings.get(key));
            properties.add(property);
        }
        actionDef.getProperties().addAll(properties);

        try {
            pipelineManager.addAction(actionDef);
        } catch (WfmProcessingException wfmEx) {
            return new Tuple<Boolean, String>(false, "Failed to add new action. Please check logs for more detail.");
        }
        return pipelineManager.saveDeprecated("action");
    }

    @Override
    public void addAndSaveAction(String actionName,
                                 String actionDescription, String algorithmName,
                                 Map<String, String> propertySettings) throws WfmProcessingException {

        ActionDefinition actionDef = new ActionDefinition(actionName, algorithmName, actionDescription);
        Set<PropertyDefinitionRef> properties = new HashSet<PropertyDefinitionRef>();
        Set<String> keys = propertySettings.keySet();
        for (String key : keys) {
            PropertyDefinitionRef property = new PropertyDefinitionRef(key, propertySettings.get(key));
            properties.add(property);
        }
        actionDef.getProperties().addAll(properties);

        if(!pipelineManager.addAction(actionDef))
        {
            throw new WfmProcessingException("Failed to add new action. Please check logs for more detail.");
        }
        pipelineManager.save("action");
    }

    @Override
    public void removeAndDeleteAction(String actionName) throws WfmProcessingException {
        pipelineManager.removeAction(actionName);
        pipelineManager.save("action");
    }

    @Override
    // TODO: Update to throw exception
    public boolean addTask(TaskDefinition task) {
        try {
            return pipelineManager.addTask(task);
        } catch (WfmProcessingException wfmEx) {
            log.warn("Invalid task.", wfmEx);
            return false;
        }
    }

    @Override
    // TODO: Update to throw exception
    public boolean addPipeline(PipelineDefinition pipeline) {
        try {
            return pipelineManager.addPipeline(pipeline);
        } catch (WfmProcessingException wfmEx) {
            log.warn("Invalid pipeline.", wfmEx);
            return false;
        }
    }

    @Override
    public Tuple<Boolean, String> savePipelineChanges(String type) {
        return pipelineManager.saveDeprecated(type);
    }

    @Override
    // TODO: Update to throw exception
    public boolean addAlgorithm(AlgorithmDefinition algorithm) {
        try {
            return pipelineManager.addAlgorithm(algorithm);
        } catch (WfmProcessingException wfmEx) {
            log.warn("Invalid algorithm.", wfmEx);
            return false;
        }
    }

    @Override
    public Tuple<Boolean, String> addAndSaveAlgorithm(AlgorithmDefinition algorithm) {
        if (!addAlgorithm(algorithm)) {
            return new Tuple<Boolean, String>(false, "failed to add new algorithm, please check logs for more detail");
        }
        return pipelineManager.saveDeprecated("algorithm");
    }

    @Override
    public void removeAndDeleteAlgorithm(String algorithmName) {
        pipelineManager.removeAlgorithm(algorithmName);
        pipelineManager.saveDeprecated("algorithm");
    }

    @Override
    public Tuple<Boolean, String> addAndSaveTaskDeprecated(TaskDefinition task) {
        if (!addTask(task)) {
            return new Tuple<Boolean, String>(false, "failed to add new task, please check logs for more detail");
        }
        return pipelineManager.saveDeprecated("task");
    }

    @Override
    public void addAndSaveTask(TaskDefinition task) throws WfmProcessingException {
        if (!pipelineManager.addTask(task)) {
            throw new WfmProcessingException("Failed to add new task. Please check logs for more detail.");
        }
        pipelineManager.save("task");
    }

    @Override
    public void removeAndDeleteTask(String taskName) {
        pipelineManager.removeTask(taskName);
        pipelineManager.saveDeprecated("task");
    }

    @Override
    public Tuple<Boolean, String> addAndSavePipelineDeprecated(PipelineDefinition pipeline) {
        if (!addPipeline(pipeline)) {
            return new Tuple<Boolean, String>(false, "failed to add new pipeline. Please check logs for more detail.");
        }
        return pipelineManager.saveDeprecated("pipeline");
    }

    @Override
    public void addAndSavePipeline(PipelineDefinition pipeline) throws WfmProcessingException {
        if (!pipelineManager.addPipeline(pipeline)) {
            throw new WfmProcessingException("Failed to add new pipeline. Please check logs for more detail.");
        }
        pipelineManager.save("pipeline");
    }


    @Override
    public void removeAndDeletePipeline(String pipelineName) {
        pipelineManager.removePipeline(pipelineName);
        pipelineManager.saveDeprecated("pipeline");
    }

}
