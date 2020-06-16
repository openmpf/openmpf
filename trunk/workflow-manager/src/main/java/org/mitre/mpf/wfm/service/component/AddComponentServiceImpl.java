/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2020 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2020 The MITRE Corporation                                       *
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.mitre.mpf.nms.xml.EnvironmentVariable;
import org.mitre.mpf.nms.xml.Service;
import org.mitre.mpf.rest.api.component.ComponentState;
import org.mitre.mpf.rest.api.component.RegisterComponentModel;
import org.mitre.mpf.rest.api.node.EnvironmentVariableModel;
import org.mitre.mpf.rest.api.pipelines.*;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.service.NodeManagerService;
import org.mitre.mpf.wfm.service.StreamingServiceManager;
import org.mitre.mpf.wfm.service.StreamingServiceModel;
import org.mitre.mpf.wfm.service.pipeline.InvalidPipelineException;
import org.mitre.mpf.wfm.service.pipeline.PipelineService;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

@org.springframework.stereotype.Service
public class AddComponentServiceImpl implements AddComponentService {

    private static final Logger _log = LoggerFactory.getLogger(AddComponentServiceImpl.class);

    private final PropertiesUtil _propertiesUtil;

    private final PipelineService _pipelineService;

    private final NodeManagerService _nodeManagerService;

    private final StreamingServiceManager _streamingServiceManager;

    private final ComponentDeploymentService _deployService;

    private final ComponentStateService _componentStateService;

    private final ComponentDescriptorValidator _componentDescriptorValidator;

    private final ExtrasDescriptorValidator _extrasDescriptorValidator;

    private final RemoveComponentService _removeComponentService;

    private final ObjectMapper _objectMapper;

    @Inject
    AddComponentServiceImpl(
            PropertiesUtil propertiesUtil,
            PipelineService pipelineService,
            Optional<NodeManagerService> nodeManagerService,
            Optional<StreamingServiceManager> streamingServiceManager,
            ComponentDeploymentService deployService,
            ComponentStateService componentStateService,
            ComponentDescriptorValidator componentDescriptorValidator,
            ExtrasDescriptorValidator extrasDescriptorValidator,
            RemoveComponentService removeComponentService,
            ObjectMapper objectMapper)
    {
        _propertiesUtil = propertiesUtil;
        _pipelineService = pipelineService;
        _nodeManagerService = nodeManagerService.orElse(null);
        _streamingServiceManager = streamingServiceManager.orElse(null);
        _deployService = deployService;
        _componentStateService = componentStateService;
        _componentDescriptorValidator = componentDescriptorValidator;
        _extrasDescriptorValidator = extrasDescriptorValidator;
        _removeComponentService = removeComponentService;
        _objectMapper = objectMapper;
    }

    @Override
    public synchronized RegisterComponentModel registerComponent(String componentPackageFileName) throws ComponentRegistrationException {
        if (_propertiesUtil.dockerProfileEnabled()) {
            throw new ManagedComponentsUnsupportedException();
        }

        ComponentState initialState = _componentStateService.getByPackageFile(componentPackageFileName)
                .map(RegisterComponentModel::getComponentState)
                .filter(Objects::nonNull)
                .orElse(ComponentState.UNKNOWN);

        if (initialState != ComponentState.UPLOADED && initialState != ComponentState.REGISTER_ERROR
                && initialState != ComponentState.DEPLOYED) {
            _componentStateService.replacePackageState(componentPackageFileName, ComponentState.REGISTER_ERROR);
            throw new ComponentRegistrationStatusException(initialState);
        }

        String descriptorPath;
        try {
            _componentStateService.replacePackageState(componentPackageFileName, ComponentState.REGISTERING);
            if (initialState == ComponentState.DEPLOYED) {
                descriptorPath = _componentStateService.getByPackageFile(componentPackageFileName)
                        .map(RegisterComponentModel::getJsonDescriptorPath)
                        .orElse(null);
            }
            else {
                descriptorPath = _deployService.deployComponent(componentPackageFileName);
            }
        }
        catch (IllegalStateException | ComponentRegistrationException ex) {
            _componentStateService.replacePackageState(componentPackageFileName, ComponentState.REGISTER_ERROR);
            throw ex;
        }

        // At this point the component package has been successfully extracted
        // so if registration fails after this point the component must be undeployed
        try {
            RegisterComponentModel currentModel = _componentStateService.getByPackageFile(componentPackageFileName)
                    .orElseThrow(() -> new ComponentRegistrationStatusException(ComponentState.UNKNOWN));
            currentModel.setJsonDescriptorPath(descriptorPath);
            currentModel.setManaged(true);
            _componentStateService.update(currentModel);

            registerDeployedComponent(loadDescriptor(descriptorPath), currentModel);

            currentModel.setDateRegistered(Instant.now());
            currentModel.setComponentState(ComponentState.REGISTERED);
            _componentStateService.update(currentModel);

            String logMsg = "Successfully registered the component from package '" + componentPackageFileName + "'.";
            _log.info(logMsg);
            return currentModel;
        }
        catch (IllegalStateException | ComponentRegistrationException | InvalidPipelineException ex) {
            _componentStateService.replacePackageState(componentPackageFileName, ComponentState.REGISTER_ERROR);
            String topLevelDirectory = Paths.get(descriptorPath).getParent().getParent().getFileName().toString();
            _deployService.undeployComponent(topLevelDirectory);
            throw ex;
        }
    }

    @Override
    public synchronized RegisterComponentModel registerDeployedComponent(String descriptorPath) throws ComponentRegistrationException {
        if (_propertiesUtil.dockerProfileEnabled()) {
            throw new ManagedComponentsUnsupportedException();
        }

        JsonComponentDescriptor descriptor = loadDescriptor(descriptorPath);

        RegisterComponentModel registrationModel = _componentStateService
                .getByComponentName(descriptor.getComponentName())
                .orElseGet(RegisterComponentModel::new);
        registrationModel.setComponentName(descriptor.getComponentName());
        registrationModel.setJsonDescriptorPath(descriptorPath);
        registrationModel.setManaged(true);

        registerDeployedComponent(descriptor, registrationModel);
        registrationModel.setComponentState(ComponentState.REGISTERED);
        registrationModel.setDateRegistered(Instant.now());
        _componentStateService.update(registrationModel);
        return registrationModel;
    }

    private void registerDeployedComponent(JsonComponentDescriptor descriptor, RegisterComponentModel model)
            throws ComponentRegistrationException {

        if (descriptor.getAlgorithm() == null) {
            _log.warn("Component descriptor file is missing an Algorithm definition.");
            _log.warn("Treating as an extras descriptor file. Will register Actions, Tasks, and Pipelines only.");
            _extrasDescriptorValidator.validate(new JsonExtrasDescriptor(descriptor));
        } else {
            _componentDescriptorValidator.validate(descriptor);
        }
        model.setVersion(descriptor.getComponentVersion());

        Algorithm algorithm = descriptor.getAlgorithm();
        String algoName = null;
        if (algorithm != null) {
            algoName = saveAlgorithm(algorithm);
            model.setAlgorithmName(algoName);
        }
        model.setComponentName(descriptor.getComponentName());

        try {
            Set<String> savedActions = saveActions(descriptor, algorithm);
            model.getActions().addAll(savedActions);

            Set<String> savedTasks = saveTasks(descriptor, algorithm);
            model.getTasks().addAll(savedTasks);

            Set<String> savedPipelines = savePipelines(descriptor, algorithm);
            model.getPipelines().addAll(savedPipelines);

            if (descriptor.getAlgorithm() != null && model.isManaged()) {
                if (descriptor.supportsBatchProcessing()) {
                    String serviceName = saveBatchService(descriptor, algorithm);
                    model.setServiceName(serviceName);
                }
                if (descriptor.supportsStreamProcessing()) {
                    String streamingServiceName = saveStreamingService(descriptor, algorithm);
                    model.setStreamingServiceName(streamingServiceName);
                }
            }
        }
        catch (ComponentRegistrationSubsystemException | InvalidPipelineException ex) {
            if (descriptor.getAlgorithm() != null) {
                _log.warn("Component registration failed for {}. Removing the {} algorithm and child objects.",
                        descriptor.getComponentName(), algoName);
            } else {
                _log.warn("Component registration failed for {}. Removing child objects.",
                        descriptor.getComponentName());
            }
            _removeComponentService.deleteCustomPipelines(model);
            throw ex;
        }
    }


    @Override
    public boolean registerUnmanagedComponent(JsonComponentDescriptor descriptor)
            throws ComponentRegistrationException {

        RegisterComponentModel existingComponent
                = _componentStateService.getByComponentName(descriptor.getComponentName()).orElse(null);
        if (existingComponent != null) {
            if (existingComponent.isManaged()) {
                throw new DuplicateComponentException(String.format(
                        "Unable to register %s because there is an existing managed component with the same name.",
                        descriptor.getComponentName()));
            }

            try {
                JsonComponentDescriptor existingDescriptor = loadDescriptor(existingComponent.getJsonDescriptorPath());
                if (existingDescriptor.equals(descriptor)) {
                    return false;
                }
            }
            catch (FailedToParseDescriptorException e) {
                if (e.getCause() instanceof FileNotFoundException) {
                    _log.warn("An existing descriptor for the \"{}\" component was not found. " +
                                      "The newly received descriptor will be used.",
                              descriptor.getComponentName());
                }
                else {
                    _log.warn(String.format("Failed to parse existing descriptor for the \"%s\" component. " +
                                                    "It will be replaced with the newly received descriptor.",
                                            descriptor.getComponentName()), e);
                }
            }
            _removeComponentService.removeComponent(descriptor.getComponentName());
        }

        RegisterComponentModel registrationModel = new RegisterComponentModel();
        registrationModel.setComponentName(descriptor.getComponentName());
        registrationModel.setManaged(false);
        registrationModel.setDateUploaded(Instant.now());

        registerDeployedComponent(descriptor, registrationModel);
        try {
            Path descriptorDir = _propertiesUtil.getPluginDeploymentPath()
                    .resolve(descriptor.getComponentName())
                    .resolve("descriptor");
            Files.createDirectories(descriptorDir);
            Path descriptorPath = descriptorDir.resolve("descriptor.json");
            _objectMapper.writeValue(descriptorPath.toFile(), descriptor);
            registrationModel.setJsonDescriptorPath(descriptorPath.toString());

            registrationModel.setComponentState(ComponentState.REGISTERED);
            registrationModel.setDateRegistered(Instant.now());
            _componentStateService.update(registrationModel);
            return true;
        }
        catch (IOException e) {
            _removeComponentService.deleteCustomPipelines(registrationModel);
            throw new UncheckedIOException(e);
        }
        catch (Exception e) {
            _removeComponentService.deleteCustomPipelines(registrationModel);
            throw e;
        }
    }


    private JsonComponentDescriptor loadDescriptor(String descriptorPath) throws FailedToParseDescriptorException {
        try {
            return _objectMapper.readValue(new File(descriptorPath), JsonComponentDescriptor.class);
        }
        catch (UnrecognizedPropertyException ex) {
            if (ex.getPropertyName().equals("value")
                    && ex.getReferringClass().equals(AlgorithmProperty.class)) {
                throw new FailedToParseDescriptorException(
                        "algorithm.providesCollection.properties.value has been renamed to defaultValue. " +
                                "The JSON descriptor must be updated in order to register the component.",
                        ex);
            }
            throw new FailedToParseDescriptorException(ex);
        }
        catch (IOException ex) {
            throw new FailedToParseDescriptorException(ex);
        }
    }


    private static List<EnvironmentVariable> convertJsonEnvVars(JsonComponentDescriptor descriptor) {
        return descriptor
                .getEnvironmentVariables()
                .stream()
                .map(AddComponentServiceImpl::convertJsonEnvVar)
                .collect(toList());
    }

    private static EnvironmentVariable convertJsonEnvVar(JsonComponentDescriptor.EnvironmentVariable jsonEnvVar) {
        EnvironmentVariable newEnvVar = new EnvironmentVariable();
        newEnvVar.setKey(jsonEnvVar.getName());
        newEnvVar.setValue(jsonEnvVar.getValue());
        newEnvVar.setSep(jsonEnvVar.getSep());
        return newEnvVar;
    }


    private String saveAlgorithm(Algorithm algorithm)
            throws ComponentRegistrationSubsystemException {
        try {
            _pipelineService.save(algorithm);
            _log.info("Successfully added the " + algorithm.getName() + " algorithm");
            return algorithm.getName().toUpperCase();
        }
        catch (WfmProcessingException ex) {
            throw new ComponentRegistrationSubsystemException(
                    String.format("Could not add the \"%s\" algorithm.", algorithm.getName()), ex);
        }
    }

    private Set<String> saveActions(JsonComponentDescriptor descriptor, Algorithm algorithm)
            throws ComponentRegistrationSubsystemException {

        if (descriptor.getActions().isEmpty()) {
            if (algorithm == null) {
                return Collections.emptySet();
            }

            // add a default action associated with the algorithm
            String actionDescription = "Default action for the " + algorithm.getName() + " algorithm.";
            String actionName = getDefaultActionName(algorithm);
            saveAction(new Action(actionName, actionDescription, algorithm.getName(), Collections.emptyList()));
            return Collections.singleton(actionName);
        }

        for (Action action : descriptor.getActions()) {
            saveAction(action);
        }
        return descriptor.getActions()
                .stream()
                .map(a -> a.getName().toUpperCase())
                .collect(toSet());
    }

    private void saveAction(Action action)
            throws ComponentRegistrationSubsystemException {
        try {
            _pipelineService.save(action);
            _log.info("Successfully added the {} action for the {} algorithm", action.getName(), action.getAlgorithm());
        }
        catch (WfmProcessingException ex) {
            throw new ComponentRegistrationSubsystemException(String.format(
                    "Could not add the %s action", action.getName()), ex);
        }
    }

    private Set<String> saveTasks(JsonComponentDescriptor descriptor, Algorithm algorithm)
            throws ComponentRegistrationSubsystemException {

        if (descriptor.getTasks().isEmpty()) {
            if (algorithm == null) {
                return Collections.emptySet();
            }

            String actionName = getDefaultActionName(algorithm);
            // add a default task associated with the action
            String taskName = getDefaultTaskName(algorithm);
            String taskDescription = "Default task for the " + actionName + " action.";
            saveTask(new Task(taskName, taskDescription, Collections.singletonList(actionName)));
            return Collections.singleton(taskName);
        }

        for (Task task : descriptor.getTasks()) {
            saveTask(task);
        }
        return descriptor.getTasks()
                .stream()
                .map(Task::getName)
                .collect(toSet());
    }

    private void saveTask(Task task)
            throws ComponentRegistrationSubsystemException {
        try {
            _pipelineService.save(task);
            _log.info("Successfully added the {} task", task.getName());
        }
        catch (WfmProcessingException ex) {
            throw new ComponentRegistrationSubsystemException(
                    String.format("Could not add the %s task", task.getName()), ex);
        }
    }


    private Set<String> savePipelines(JsonComponentDescriptor descriptor, Algorithm algorithm)
            throws ComponentRegistrationSubsystemException {

        // add a single action default pipeline associated with the task
        // note, can't do this if a required state must be reached by a previous
        // stage in a pipeline that uses this algorithm
        if (descriptor.getPipelines().isEmpty()) {
            if (algorithm != null && algorithm.getRequiresCollection().getStates().isEmpty()) {
                String pipelineName = getDefaultPipelineName(algorithm);
                String taskName = getDefaultTaskName(algorithm);
                String pipelineDescription = "Default pipeline for the " + taskName + " task.";
                savePipeline(new Pipeline(pipelineName, pipelineDescription, Collections.singleton(taskName)));
                return Collections.singleton(pipelineName);
            }

            return Collections.emptySet();
        }


        for (Pipeline pipeline: descriptor.getPipelines()) {
            savePipeline(pipeline);
        }
        return descriptor.getPipelines()
                .stream()
                .map(Pipeline::getName)
                .collect(toSet());
    }

    private void savePipeline(Pipeline pipeline)
            throws ComponentRegistrationSubsystemException {

        try {
            _pipelineService.save(pipeline);
            _log.info("Successfully added the {} pipeline.", pipeline.getName());
        }
        catch (WfmProcessingException ex) {
            throw new ComponentRegistrationSubsystemException(String.format(
                    "Failed to add the %s pipeline", pipeline.getName()), ex);
        }
    }

    private String saveBatchService(JsonComponentDescriptor descriptor, Algorithm algorithm)
            throws ComponentRegistrationSubsystemException {
        String serviceName = descriptor.getComponentName();
        if (_nodeManagerService.getServiceModels().containsKey(serviceName)) {
            throw new ComponentRegistrationSubsystemException(String.format(
                    "Couldn't add the %s service because another service already has that name", serviceName));
        }
        String queueName = String.format("MPF.%s_%s_REQUEST", algorithm.getActionType(), algorithm.getName());
        Service algorithmService;

        switch (descriptor.getSourceLanguage()) {
            case JAVA:
                algorithmService = new Service(serviceName, "${MPF_HOME}/bin/start-java-component.sh");
                algorithmService.addArg(descriptor.getBatchLibrary());
                algorithmService.addArg(queueName);
                algorithmService.addArg(serviceName);
                algorithmService.setLauncher("generic");
                break;

            case CPP:
            case PYTHON:
                algorithmService = new Service(serviceName, "${MPF_HOME}/bin/amq_detection_component");
                algorithmService.addArg(descriptor.getBatchLibrary());
                algorithmService.addArg(queueName);
                algorithmService.addArg(descriptor.getSourceLanguage().getValue());
                algorithmService.setLauncher("simple");
                break;

            default:
                throw new IllegalStateException("Unknown component language: " + descriptor.getSourceLanguage());
        }

        algorithmService.setWorkingDirectory("${MPF_HOME}/plugins/" + descriptor.getComponentName());
        algorithmService.setDescription(algorithm.getDescription());
        algorithmService.setEnvVars(convertJsonEnvVars(descriptor));
        _log.debug("Created service definition");
        if (_nodeManagerService.addService(algorithmService)) {
            _log.info("Successfully added the {} service", serviceName);
            return serviceName;
        }
        else {
            throw new ComponentRegistrationSubsystemException(
                    String.format("Could not add service %s for deployment to nodes", serviceName));
        }
    }


    private String saveStreamingService(JsonComponentDescriptor descriptor, Algorithm algorithm)
            throws ComponentRegistrationSubsystemException {

        String serviceName = descriptor.getComponentName();
        boolean existingSvc = _streamingServiceManager.getServices().stream()
                .anyMatch(s -> s.getServiceName().equals(serviceName));
        if (existingSvc) {
            throw new ComponentRegistrationSubsystemException(String.format(
                    "Couldn't add the %s streaming service because another service already has that name.",
                    serviceName));
        }

        if (descriptor.getSourceLanguage() == ComponentLanguage.CPP) {
            String libPath = descriptor.getStreamLibrary();
            List<EnvironmentVariableModel> envVars = descriptor.getEnvironmentVariables().stream()
                    .map(descEnv -> new EnvironmentVariableModel(descEnv.getName(), descEnv.getValue(),
                                                                 descEnv.getSep()))
                    .collect(toList());

            StreamingServiceModel serviceModel = new StreamingServiceModel(
                    serviceName, algorithm.getName(), ComponentLanguage.CPP, libPath, envVars);
            _streamingServiceManager.addService(serviceModel);
            return serviceName;
        }
        else {
            // TODO: Also save services for other languages when streaming is implemented for them.
            _log.error("Streaming processing is not supported for {} components. No streaming service will be added for the {} component.",
                       descriptor.getSourceLanguage(), descriptor.getComponentName());
            return null;
        }
    }


    private static String getDefaultActionName(Algorithm algorithm) {
        return String.format("%s %s ACTION", algorithm.getName(), algorithm.getActionType().toString()).toUpperCase();
    }

    private static String getDefaultTaskName(Algorithm algorithm) {
        return String.format("%s %s TASK", algorithm.getName(), algorithm.getActionType().toString())
                .toUpperCase();
    }

    private static String getDefaultPipelineName(Algorithm algorithm) {
        return String.format("%s %s PIPELINE", algorithm.getName(), algorithm.getActionType().toString())
                .toUpperCase();
    }
}
