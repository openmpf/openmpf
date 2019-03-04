/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

import org.mitre.mpf.rest.api.component.ComponentState;
import org.mitre.mpf.rest.api.component.RegisterComponentModel;
import org.mitre.mpf.rest.api.node.NodeManagerModel;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.service.PipelineService;
import org.mitre.mpf.wfm.pipeline.xml.*;
import org.mitre.mpf.wfm.service.NodeManagerService;
import org.mitre.mpf.wfm.service.StreamingServiceManager;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Service
public class RemoveComponentServiceImpl implements RemoveComponentService {

    private static final Logger _log = LoggerFactory.getLogger(RemoveComponentServiceImpl.class);

    private final NodeManagerService nodeManagerService;

    private final StreamingServiceManager streamingServiceManager;

    private final ComponentDeploymentService deployService;

    private final ComponentStateService componentStateService;

    private final PipelineService pipelineService;

    private final PropertiesUtil propertiesUtil;

    @Inject
    RemoveComponentServiceImpl(
            NodeManagerService nodeManagerService,
            StreamingServiceManager streamingServiceManager,
            ComponentDeploymentService deployService,
            ComponentStateService componentStateService,
            PipelineService pipelineService,
            PropertiesUtil propertiesUtil) {
        this.nodeManagerService = nodeManagerService;
        this.streamingServiceManager = streamingServiceManager;
        this.deployService = deployService;
        this.componentStateService = componentStateService;
        this.pipelineService = pipelineService;
        this.propertiesUtil = propertiesUtil;
    }

    @Override
    public synchronized void removeComponent(String componentName) {
        try {
            RegisterComponentModel registerModel = componentStateService
                    .getByComponentName(componentName)
                    .orElseThrow(() -> new IllegalStateException(String.format(
                            "Couldn't remove %s because it is not registered as a component", componentName)));

            removeComponent(registerModel, true, true);
        }
        catch (Exception ex) {
            componentStateService.replaceComponentState(componentName, ComponentState.REGISTER_ERROR);
            throw ex;
        }
    }

    private void removeComponent(RegisterComponentModel registerModel, boolean deletePackage, boolean recursive) {
        deleteCustomPipelines(registerModel, recursive);

        if (registerModel.getJsonDescriptorPath() == null) {
            _log.warn("Couldn't find the JSON descriptor for {}", registerModel.getComponentName());
        }
        else {
            String componentTld = getComponentTldFromDescriptorPath(registerModel.getJsonDescriptorPath());
            deployService.undeployComponent(componentTld);
        }


        if (registerModel.getFullUploadedFilePath() == null) {
            _log.warn("No component package found while removing the {} component", registerModel.getComponentName());
        }
        else if (deletePackage) {
            Path componentPackage = Paths.get(registerModel.getFullUploadedFilePath());
            try {
                Files.deleteIfExists(componentPackage);
            }
            catch (IOException ex) {
                componentStateService.replaceComponentState(
                        registerModel.getComponentName(), ComponentState.REGISTER_ERROR);
                throw new IllegalStateException(
                        "Couldn't delete the component package at: " + componentPackage.toAbsolutePath(), ex);
            }
        }

        if (registerModel.getComponentName() != null) {
            componentStateService.removeComponent(registerModel.getComponentName());
        }
        else if (registerModel.getPackageFileName() != null) {
            componentStateService.removePackage(registerModel.getPackageFileName());
        }
    }

    @Override
    public synchronized void unregisterViaFile(String jsonDescriptorPath, boolean deletePackage, boolean recursive) {
        Optional<RegisterComponentModel> optRegisterModel = componentStateService.get()
                .stream()
                .filter(rcm -> jsonDescriptorPath.equals(rcm.getJsonDescriptorPath()))
                .findAny();

        if (optRegisterModel.isPresent()) {
            try {
                removeComponent(optRegisterModel.get(), deletePackage, recursive);
            }
            catch (Exception ex) {
               componentStateService.replaceComponentState(
                       optRegisterModel.get().getComponentName(), ComponentState.REGISTER_ERROR);
                throw ex;
            }
        }
        else {
            String componentTld = getComponentTldFromDescriptorPath(jsonDescriptorPath);
            deployService.undeployComponent(componentTld);
        }
    }

    @Override
    public void removePackage(String componentPackageFileName) {
        Optional<RegisterComponentModel> optRegisterModel =
                componentStateService.getByPackageFile(componentPackageFileName);

        if (optRegisterModel.isPresent()) {
            removeComponent(optRegisterModel.get(), true, true);
            return;
        }

        Path componentPackage = Paths.get(
                propertiesUtil.getUploadedComponentsDirectory().getAbsolutePath(), componentPackageFileName);

        try {
            Files.deleteIfExists(componentPackage);
            componentStateService.removePackage(componentPackageFileName);
        }
        catch (IOException ex) {
            componentStateService.replacePackageState(componentPackageFileName, ComponentState.REGISTER_ERROR);
            throw new IllegalStateException(
                    "Couldn't delete the component package at: " + componentPackage.toAbsolutePath(), ex);
        }
    }


    @Override
    public void unregisterRetainPackage(String componentPackageFileName) {
		componentStateService.getByPackageFile(componentPackageFileName)
                .ifPresent(rcm -> {
                    Path pathToPackage = Paths.get(rcm.getFullUploadedFilePath());
                    removeComponent(rcm, false, true);
                    componentStateService.addEntryForUploadedPackage(pathToPackage);
                });
    }


    @Override
    public void deleteCustomPipelines(RegisterComponentModel registrationModel, boolean recursive) {
        if (registrationModel.getServiceName() != null) {
            removeBatchService(registrationModel.getServiceName());
        }
        if (registrationModel.getStreamingServiceName() != null) {
            streamingServiceManager.deleteService(registrationModel.getStreamingServiceName());
        }
        if (registrationModel.getAlgorithmName() != null) {
            removeAlgorithm(registrationModel.getAlgorithmName(), recursive);
        }

        registrationModel.getPipelines()
                .forEach(this::removePipeline);

        registrationModel.getTasks()
                .forEach(tn -> removeTask(tn, recursive));

        registrationModel.getActions()
                .forEach(an -> removeAction(an, recursive));
    }

    private void removePipeline(String pipelineName) {
        pipelineService.deletePipeline(pipelineName.toUpperCase());
    }

    private void removeTask(String taskName, boolean recursive) {
        if (recursive) {
            pipelineService.getPipelines()
                    .stream()
                    .filter(pd -> referencesTask(pd, taskName))
                    .map(PipelineDefinition::getName)
                    .forEach(this::removePipeline);
        }

        pipelineService.deleteTask(taskName.toUpperCase());
    }

    private void removeAction(String actionName, boolean recursive) {
        if (recursive) {
            pipelineService.getTasks()
                    .stream()
                    .filter(td -> referencesAction(td, actionName))
                    .map(TaskDefinition::getName)
                    .forEach(tn -> removeTask(tn, true));
        }

        try {
            pipelineService.deleteAction(actionName.toUpperCase());
        } catch (WfmProcessingException e) {
            _log.error("Cannot delete action " + actionName.toUpperCase(), e);
        }
    }

    private void removeAlgorithm(String algorithmName, boolean recursive) {
        if (recursive) {
            pipelineService.getActions()
                    .stream()
                    .filter(ad -> ad.getAlgorithmRef().equalsIgnoreCase(algorithmName))
                    .map(ActionDefinition::getName)
                    .forEach(an -> removeAction(an, true));
        }

        pipelineService.deleteAlgorithm(algorithmName.toUpperCase());
    }

    private void removeBatchService(String serviceName) {
        List<NodeManagerModel> nodeModels = nodeManagerService.getNodeManagerModels();
        for (NodeManagerModel nmm : nodeModels) {
            nmm.getServices()
                    .removeIf(sm -> sm.getServiceName().equals(serviceName));
        }
        try {
            nodeManagerService.saveAndReloadNodeManagerConfig(nodeModels);
        }
        catch (IOException ex) {
            throw new IllegalStateException("Failed to save node manager config", ex);
        }
        nodeManagerService.removeService(serviceName);
    }

    private static String getComponentTldFromDescriptorPath(String pathToJsonDescriptor){
        return new File(pathToJsonDescriptor).getParentFile().getParentFile().getName();
    }

    private static boolean referencesTask(PipelineDefinition pipelineDef, String taskName) {
        return pipelineDef.getTaskRefs()
                .stream()
                .map(TaskDefinitionRef::getName)
                .anyMatch(taskName::equalsIgnoreCase);
    }

    private static boolean referencesAction(TaskDefinition taskDef, String actionName) {
        return taskDef.getActions()
                .stream()
                .map(ActionDefinitionRef::getName)
                .anyMatch(actionName::equalsIgnoreCase);
    }

}