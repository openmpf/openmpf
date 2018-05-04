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

package org.mitre.mpf.wfm.service.component;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.mitre.mpf.nms.xml.EnvironmentVariable;
import org.mitre.mpf.nms.xml.Service;
import org.mitre.mpf.rest.api.component.ComponentState;
import org.mitre.mpf.rest.api.component.RegisterComponentModel;
import org.mitre.mpf.rest.api.node.EnvironmentVariableModel;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.pipeline.xml.*;
import org.mitre.mpf.wfm.service.NodeManagerService;
import org.mitre.mpf.wfm.service.PipelineService;
import org.mitre.mpf.wfm.service.StreamingServiceManager;
import org.mitre.mpf.wfm.service.StreamingServiceModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

@org.springframework.stereotype.Service
public class AddComponentServiceImpl implements AddComponentService {

    private static final Logger _log = LoggerFactory.getLogger(AddComponentServiceImpl.class);

    private final PipelineService pipelineService;

    private final NodeManagerService nodeManagerService;

    private final StreamingServiceManager streamingServiceManager;

    private final ComponentDeploymentService deployService;

    private final ComponentStateService componentStateService;

    private final ComponentDescriptorValidator componentDescriptorValidator;

    private final ExtrasDescriptorValidator extrasDescriptorValidator;

    private final CustomPipelineValidator customPipelineValidator;

    private final RemoveComponentService removeComponentService;

    private final ObjectMapper objectMapper;

    @Inject
    AddComponentServiceImpl(
            PipelineService pipelineService,
            NodeManagerService nodeManagerService,
            StreamingServiceManager streamingServiceManager,
            ComponentDeploymentService deployService,
            ComponentStateService componentStateService,
            ComponentDescriptorValidator componentDescriptorValidator,
            ExtrasDescriptorValidator extrasDescriptorValidator,
            CustomPipelineValidator customPipelineValidator,
            RemoveComponentService removeComponentService,
            ObjectMapper objectMapper)
    {
        this.pipelineService = pipelineService;
        this.nodeManagerService = nodeManagerService;
        this.streamingServiceManager = streamingServiceManager;
        this.deployService = deployService;
        this.componentStateService = componentStateService;
        this.componentDescriptorValidator = componentDescriptorValidator;
        this.extrasDescriptorValidator = extrasDescriptorValidator;
        this.customPipelineValidator = customPipelineValidator;
        this.removeComponentService = removeComponentService;
        this.objectMapper = objectMapper;
    }

    @Override
    public synchronized RegisterComponentModel registerComponent(String componentPackageFileName) throws ComponentRegistrationException {
        ComponentState initialState = componentStateService.getByPackageFile(componentPackageFileName)
                .map(RegisterComponentModel::getComponentState)
                .filter(Objects::nonNull)
                .orElse(ComponentState.UNKNOWN);

        if (initialState != ComponentState.UPLOADED && initialState != ComponentState.REGISTER_ERROR
                && initialState != ComponentState.DEPLOYED) {
            componentStateService.replacePackageState(componentPackageFileName, ComponentState.REGISTER_ERROR);
            throw new ComponentRegistrationStatusException(initialState);
        }

        String descriptorPath;
        try {
            componentStateService.replacePackageState(componentPackageFileName, ComponentState.REGISTERING);
            if (initialState == ComponentState.DEPLOYED) {
                descriptorPath = componentStateService.getByPackageFile(componentPackageFileName)
                        .map(RegisterComponentModel::getJsonDescriptorPath)
		                .orElse(null);
            }
            else {
                descriptorPath = deployService.deployComponent(componentPackageFileName);
            }
        }
        catch (IllegalStateException | ComponentRegistrationException ex) {
            componentStateService.replacePackageState(componentPackageFileName, ComponentState.REGISTER_ERROR);
            throw ex;
        }

        // At this point the component package has been successfully extracted
        // so if registration fails after this point the component must be undeployed
        try {
            RegisterComponentModel currentModel = componentStateService.getByPackageFile(componentPackageFileName)
                    .orElseThrow(() -> new ComponentRegistrationStatusException(ComponentState.UNKNOWN));
            currentModel.setJsonDescriptorPath(descriptorPath);
            componentStateService.update(currentModel);

            registerDeployedComponent(loadDescriptor(descriptorPath), currentModel);

            currentModel.setDateRegistered(new Date());
            currentModel.setComponentState(ComponentState.REGISTERED);
            componentStateService.update(currentModel);

            String logMsg = "Successfully registered the component from package '" + componentPackageFileName + "'.";
            _log.info(logMsg);
            return currentModel;
        }
        catch (IllegalStateException | ComponentRegistrationException ex) {
            componentStateService.replacePackageState(componentPackageFileName, ComponentState.REGISTER_ERROR);
            String topLevelDirectory = Paths.get(descriptorPath).getParent().getParent().getFileName().toString();
            deployService.undeployComponent(topLevelDirectory);
            throw ex;
        }
    }

    @Override
    public synchronized void registerDeployedComponent(String descriptorPath) throws ComponentRegistrationException {
        JsonComponentDescriptor descriptor = loadDescriptor(descriptorPath);

        RegisterComponentModel registrationModel = componentStateService
                .getByComponentName(descriptor.componentName)
                .orElseGet(RegisterComponentModel::new);
        registrationModel.setComponentName(descriptor.componentName);
        registrationModel.setJsonDescriptorPath(descriptorPath);

        registerDeployedComponent(descriptor, registrationModel);
        registrationModel.setComponentState(ComponentState.REGISTERED);
        registrationModel.setDateRegistered(new Date());
        componentStateService.update(registrationModel);
    }

    private void registerDeployedComponent(JsonComponentDescriptor descriptor, RegisterComponentModel model)
            throws ComponentRegistrationException {

        if (descriptor.algorithm == null) {
            _log.warn("Component descriptor file is missing an Algorithm definition.");
            _log.warn("Treating as an extras descriptor file. Will register Actions, Tasks, and Pipelines only.");
            extrasDescriptorValidator.validate(new JsonExtrasDescriptor(descriptor));
        } else {
            componentDescriptorValidator.validate(descriptor);
        }

        customPipelineValidator.validate(descriptor);

        AlgorithmDefinition algorithmDef = null;
        String algoName = null;
        if (descriptor.algorithm != null) {
            algorithmDef = convertJsonAlgo(descriptor);
            algoName = saveAlgorithm(algorithmDef);
            model.setAlgorithmName(algoName);
        }
        model.setComponentName(descriptor.componentName);

        try {
            Set<String> savedActions = saveActions(descriptor, algorithmDef);
            model.getActions().addAll(savedActions);

            Set<String> savedTasks = saveTasks(descriptor, algorithmDef);
            model.getTasks().addAll(savedTasks);

            Set<String> savedPipelines = savePipelines(descriptor, algorithmDef);
            model.getPipelines().addAll(savedPipelines);

            if (descriptor.algorithm != null) {
                if (descriptor.supportsBatchProcessing()) {
                    String serviceName = saveBatchService(descriptor, algorithmDef);
                    model.setServiceName(serviceName);
                }
                if (descriptor.supportsStreamProcessing()) {
                    String streamingServiceName = saveStreamingService(descriptor, algorithmDef);
                    model.setStreamingServiceName(streamingServiceName);
                }
            }
        }
        catch (ComponentRegistrationSubsystemException ex) {
            if (descriptor.algorithm != null) {
                _log.warn("Component registration failed for {}. Removing the {} algorithm and child objects.",
                        descriptor.componentName, algoName);
            } else {
                _log.warn("Component registration failed for {}. Removing child objects.",
                        descriptor.componentName);
            }
            removeComponentService.deleteCustomPipelines(model, true);
            throw ex;
        }
    }

    private JsonComponentDescriptor loadDescriptor(String descriptorPath) throws FailedToParseDescriptorException {
        try {
            return objectMapper.readValue(new File(descriptorPath), JsonComponentDescriptor.class);
        }
        catch (UnrecognizedPropertyException ex) {
            if (ex.getPropertyName().equals("value")
                    && ex.getReferringClass().equals(JsonComponentDescriptor.AlgoProvidesProp.class)) {
                throw new FailedToParseDescriptorException(
                        "algorithm.providesCollection.properties.value has been renamed to defaultValue. " +
                                "The JSON descriptor must be updated in order to register the component.",
                        ex);
            }
            throw new FailedToParseDescriptorException(ex);
        }
        catch (JsonMappingException ex) {
            throw new FailedToParseDescriptorException(ex);
        }
        catch (IOException ex) {
            throw new IllegalStateException("Failed to load json descriptor ", ex);
        }
    }

    private static AlgorithmDefinition convertJsonAlgo(JsonComponentDescriptor descriptor) {
        JsonComponentDescriptor.Algorithm jsonAlgo = descriptor.algorithm;

        AlgorithmDefinition algoDef = new AlgorithmDefinition(
                jsonAlgo.actionType,
                descriptor.algorithm.name.toUpperCase(),
                jsonAlgo.description,
                descriptor.supportsBatchProcessing(),
                descriptor.supportsStreamProcessing());

        jsonAlgo
                .requiresCollection
                .states
                .stream()
                .map(StateDefinitionRef::new)
                .forEach(sd -> algoDef.getRequiresCollection().getStateRefs().add(sd));

        jsonAlgo
                .providesCollection
                .properties
                .stream()
                .map(p -> new PropertyDefinition(p.name, p.type, p.description, p.defaultValue, p.propertiesKey))
                .forEach(pd -> algoDef.getProvidesCollection().getAlgorithmProperties().add(pd));

        jsonAlgo
                .providesCollection
                .states
                .stream()
                .map(StateDefinition::new)
                .forEach(sd -> algoDef.getProvidesCollection().getStates().add(sd));

        return algoDef;
    }

    private static List<EnvironmentVariable> convertJsonEnvVars(JsonComponentDescriptor descriptor) {
        return descriptor
                .environmentVariables
                .stream()
                .map(AddComponentServiceImpl::convertJsonEnvVar)
                .collect(toList());
    }

    private static EnvironmentVariable convertJsonEnvVar(JsonComponentDescriptor.EnvironmentVariable jsonEnvVar) {
        EnvironmentVariable newEnvVar = new EnvironmentVariable();
        newEnvVar.setKey(jsonEnvVar.name);
        newEnvVar.setValue(jsonEnvVar.value);
        newEnvVar.setSep(jsonEnvVar.sep);
        return newEnvVar;
    }

    private String saveAlgorithm(AlgorithmDefinition algoDef)
            throws ComponentRegistrationSubsystemException
    {
        try {
            pipelineService.saveAlgorithm(algoDef);
            _log.info("Successfully added the " + algoDef.getName() + " algorithm");
            return algoDef.getName();
        }
        catch (WfmProcessingException ex) {
            throw new ComponentRegistrationSubsystemException(
                    String.format("Could not add the \"%s\" algorithm.", algoDef.getName()), ex);
        }
    }

    private Set<String> saveActions(JsonComponentDescriptor descriptor, AlgorithmDefinition algorithmDef)
            throws ComponentRegistrationSubsystemException {

        if (descriptor.actions == null) {
            if (algorithmDef == null) {
                return Collections.emptySet();
            }

            // add a default action associated with the algorithm
            String actionDescription = "Default action for the " + algorithmDef.getName() + " algorithm.";
            String actionName = getDefaultActionName(algorithmDef);
            saveAction(actionName, actionDescription, algorithmDef.getName(), Collections.emptyList());
            return Collections.singleton(actionName);
        }

        for (JsonComponentDescriptor.Action action : descriptor.actions) {
            saveAction(action.name, action.description, action.algorithm, action.properties);
        }
        return descriptor.actions
                .stream()
                .map(a -> a.name)
                .collect(toSet());
    }

    private void saveAction(String actionName, String description, String algoName,
                            List<JsonComponentDescriptor.ActionProperty> actionProps)
            throws ComponentRegistrationSubsystemException {

        ActionDefinition action = new ActionDefinition(actionName, algoName, description);
        actionProps.stream()
                .map(ap -> new PropertyDefinitionRef(ap.name, ap.value))
                .forEach(pdr -> action.getProperties().add(pdr));

        try {
            pipelineService.saveAction(action);
            _log.info("Successfully added the {} action for the {} algorithm", actionName, algoName);
        }
        catch (WfmProcessingException ex) {
            throw new ComponentRegistrationSubsystemException(String.format(
                    "Could not add the %s action", actionName), ex);
        }
    }

    private Set<String> saveTasks(JsonComponentDescriptor descriptor, AlgorithmDefinition algorithmDef)
            throws ComponentRegistrationSubsystemException {

        if (descriptor.tasks == null) {
            if (algorithmDef == null) {
                return Collections.emptySet();
            }

            String actionName = getDefaultActionName(algorithmDef);
            // add a default task associated with the action
            String taskName = getDefaultTaskName(algorithmDef);
            String taskDescription = "Default task for the " + actionName + " action.";
            saveTask(taskName, taskDescription, Collections.singletonList(actionName));
            return Collections.singleton(taskName);
        }

        for (JsonComponentDescriptor.Task task : descriptor.tasks) {
            saveTask(task.name, task.description, task.actions);
        }
        return descriptor.tasks
                .stream()
                .map(t -> t.name)
                .collect(toSet());
    }

    private void saveTask(String taskName, String taskDescription, Collection<String> actionNames)
            throws ComponentRegistrationSubsystemException {
        TaskDefinition task = new TaskDefinition(taskName, taskDescription);
        actionNames.stream()
                .map(ActionDefinitionRef::new)
                .forEach(adr -> task.getActions().add(adr));

        try {
            pipelineService.saveTask(task);
            _log.info("Successfully added the {} task", taskName);
        }
        catch (WfmProcessingException ex) {
            throw new ComponentRegistrationSubsystemException(String.format("Could not add the %s task", taskName), ex);
        }
    }

    private Set<String> savePipelines(JsonComponentDescriptor descriptor, AlgorithmDefinition algorithmDef)
            throws ComponentRegistrationSubsystemException {

        // add a single action default pipeline associated with the task
        // note, can't do this if a required state must be reached by a previous
        // stage in a pipeline that uses this algorithm
        if (descriptor.pipelines == null) {
            if (algorithmDef != null && algorithmDef.getRequiresCollection().getStateRefs().isEmpty()) {
                String pipelineName = getDefaultPipelineName(algorithmDef);
                String taskName = getDefaultTaskName(algorithmDef);
                String pipelineDescription = "Default pipeline for the " + taskName + " task.";
                savePipeline(pipelineName, pipelineDescription, Collections.singleton(taskName));
                return Collections.singleton(pipelineName);
            }

            return Collections.emptySet();
        }


        for (JsonComponentDescriptor.Pipeline pipeline: descriptor.pipelines) {
            savePipeline(pipeline.name, pipeline.description, pipeline.tasks);
        }
        return descriptor.pipelines
                .stream()
                .map(p -> p.name)
                .collect(toSet());
    }

    private void savePipeline(String pipelineName, String pipelineDescription, Collection<String> taskNames)
            throws ComponentRegistrationSubsystemException {
        PipelineDefinition pipeline = new PipelineDefinition(pipelineName, pipelineDescription);
        taskNames.stream()
                .map(TaskDefinitionRef::new)
                .forEach(tdr -> pipeline.getTaskRefs().add(tdr));

        try {
            pipelineService.savePipeline(pipeline);
            _log.info("Successfully added the {} pipeline.", pipeline.getName());
        }
        catch (WfmProcessingException ex) {
            throw new ComponentRegistrationSubsystemException(String.format(
                    "Failed to add the %s pipeline", pipeline.getName()), ex);
        }
    }

    private String saveBatchService(JsonComponentDescriptor descriptor, AlgorithmDefinition algorithmDef)
            throws ComponentRegistrationSubsystemException {
        String serviceName = descriptor.componentName;
        if (nodeManagerService.getServiceModels().containsKey(serviceName)) {
            throw new ComponentRegistrationSubsystemException(String.format(
                    "Couldn't add the %s service because another service already has that name", serviceName));
        }
        String queueName = String.format("MPF.%s_%s_REQUEST", algorithmDef.getActionType(), algorithmDef.getName());
        Service algorithmService;

        switch (descriptor.sourceLanguage) {
            case JAVA:
                algorithmService = new Service(serviceName, "${MPF_HOME}/bin/start-java-component.sh");
                algorithmService.addArg(descriptor.batchLibrary);
                algorithmService.addArg(queueName);
                algorithmService.addArg(serviceName);
                algorithmService.setLauncher("generic");
                algorithmService.setWorkingDirectory("${MPF_HOME}/jars");
                break;

            case CPP:
            case PYTHON:
                algorithmService = new Service(serviceName, "${MPF_HOME}/bin/amq_detection_component");
                algorithmService.addArg(descriptor.batchLibrary);
                algorithmService.addArg(queueName);
                algorithmService.setLauncher("simple");
                algorithmService.setWorkingDirectory("${MPF_HOME}/plugins/" + descriptor.componentName);
                break;

            default:
                throw new IllegalStateException("Unknown component language: " + descriptor.sourceLanguage);
        }

        algorithmService.setDescription(algorithmDef.getDescription());
        algorithmService.setEnvVars(convertJsonEnvVars(descriptor));
        _log.debug("Created service definition");
        if (nodeManagerService.addService(algorithmService)) {
            _log.info("Successfully added the {} service", serviceName);
            return serviceName;
        }
        else {
            throw new ComponentRegistrationSubsystemException(
                    String.format("Could not add service %s for deployment to nodes", serviceName));
        }
    }


    private String saveStreamingService(JsonComponentDescriptor descriptor, AlgorithmDefinition algorithmDef)
            throws ComponentRegistrationSubsystemException {

        String serviceName = descriptor.componentName;
        boolean existingSvc = streamingServiceManager.getServices().stream()
		        .anyMatch(s -> s.getServiceName().equals(serviceName));
        if (existingSvc) {
            throw new ComponentRegistrationSubsystemException(String.format(
                    "Couldn't add the %s streaming service because another service already has that name.",
                    serviceName));
        }

        if (descriptor.sourceLanguage == ComponentLanguage.CPP) {
            String libPath = descriptor.streamLibrary;
            List<EnvironmentVariableModel> envVars = descriptor.environmentVariables.stream()
                    .map(descEnv -> new EnvironmentVariableModel(descEnv.name, descEnv.value, descEnv.sep))
                    .collect(toList());

            StreamingServiceModel serviceModel = new StreamingServiceModel(
                    serviceName, algorithmDef.getName(), ComponentLanguage.CPP, libPath, envVars);
            streamingServiceManager.addService(serviceModel);
            return serviceName;
        }
        else {
            // TODO: Also save services for other languages when streaming is implemented for them.
            _log.error("Streaming processing is not supported for {} components. No streaming service will be added for the {} component.",
                       descriptor.sourceLanguage, descriptor.componentName);
            return null;
        }
    }


    private static String getDefaultActionName(AlgorithmDefinition algorithmDef) {
        return String.format("%s %s ACTION", algorithmDef.getName(), algorithmDef.getActionType().toString());
    }

    private static String getDefaultTaskName(AlgorithmDefinition algorithmDef) {
        return String.format("%s %s TASK", algorithmDef.getName(), algorithmDef.getActionType().toString());
    }

    private static String getDefaultPipelineName(AlgorithmDefinition algorithmDef) {
        return String.format("%s %s PIPELINE", algorithmDef.getName(), algorithmDef.getActionType().toString());
    }
}
