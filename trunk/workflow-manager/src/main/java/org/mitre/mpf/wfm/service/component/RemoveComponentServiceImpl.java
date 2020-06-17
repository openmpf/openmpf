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

import com.google.common.io.MoreFiles;
import org.mitre.mpf.rest.api.component.ComponentState;
import org.mitre.mpf.rest.api.component.RegisterComponentModel;
import org.mitre.mpf.rest.api.node.NodeManagerModel;
import org.mitre.mpf.wfm.service.pipeline.PipelineService;
import org.mitre.mpf.wfm.service.NodeManagerService;
import org.mitre.mpf.wfm.service.StreamingServiceManager;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Service
public class RemoveComponentServiceImpl implements RemoveComponentService {

    private static final Logger _log = LoggerFactory.getLogger(RemoveComponentServiceImpl.class);

    private final NodeManagerService _nodeManagerService;

    private final StreamingServiceManager _streamingServiceManager;

    private final ComponentDeploymentService _deployService;

    private final ComponentStateService _componentStateService;

    private final PipelineService _pipelineService;

    private final PropertiesUtil _propertiesUtil;

    @Inject
    RemoveComponentServiceImpl(
            Optional<NodeManagerService> nodeManagerService,
            Optional<StreamingServiceManager> streamingServiceManager,
            ComponentDeploymentService deployService,
            ComponentStateService componentStateService,
            PipelineService pipelineService,
            PropertiesUtil propertiesUtil) {
        _nodeManagerService = nodeManagerService.orElse(null);
        _streamingServiceManager = streamingServiceManager.orElse(null);
        _deployService = deployService;
        _componentStateService = componentStateService;
        _pipelineService = pipelineService;
        _propertiesUtil = propertiesUtil;
    }

    @Override
    public synchronized void removeComponent(String componentName) throws ManagedComponentsUnsupportedException {
        RegisterComponentModel registerModel = _componentStateService
                .getByComponentName(componentName)
                .orElseThrow(() -> new IllegalStateException(String.format(
                        "Couldn't remove %s because it is not registered as a component", componentName)));

        if (_propertiesUtil.dockerProfileEnabled() && registerModel.isManaged()) {
            throw new ManagedComponentsUnsupportedException();
        }

        try {
            if (registerModel.isManaged()) {
                removeManagedComponent(registerModel, true);
            }
            else {
                removeUnmanagedComponent(registerModel);
            }
        }
        catch (Exception ex) {
            _componentStateService.replaceComponentState(componentName, ComponentState.REGISTER_ERROR);
            throw ex;
        }
    }

    private void removeManagedComponent(RegisterComponentModel registerModel,
                                        boolean deletePackage) throws ManagedComponentsUnsupportedException {
        deleteCustomPipelines(registerModel);

        if (registerModel.getJsonDescriptorPath() == null) {
            _log.warn("Couldn't find the JSON descriptor for {}", registerModel.getComponentName());
        }
        else {
            String componentTld = getComponentTopLevelDirName(registerModel.getJsonDescriptorPath());
            _deployService.undeployComponent(componentTld);
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
                _componentStateService.replaceComponentState(
                        registerModel.getComponentName(), ComponentState.REGISTER_ERROR);
                throw new IllegalStateException(
                        "Couldn't delete the component package at: " + componentPackage.toAbsolutePath(), ex);
            }
        }

        if (registerModel.getComponentName() != null) {
            _componentStateService.removeComponent(registerModel.getComponentName());
        }
        else if (registerModel.getPackageFileName() != null) {
            _componentStateService.removePackage(registerModel.getPackageFileName());
        }
    }


    private void removeUnmanagedComponent(RegisterComponentModel registerModel)
            throws ManagedComponentsUnsupportedException {
        deleteCustomPipelines(registerModel);
        Path componentDir = getComponentTopLevelDir(registerModel.getJsonDescriptorPath());

        try {
            if (Files.exists(componentDir)) {
                MoreFiles.deleteRecursively(componentDir);
            }
            _componentStateService.removeComponent(registerModel.getComponentName());
        }
        catch (IOException ex) {
            _componentStateService.replaceComponentState(
                    registerModel.getComponentName(), ComponentState.REGISTER_ERROR);
            throw new IllegalStateException(
                    "Couldn't delete the component directory at: " + componentDir, ex);
        }
    }


    @Override
    public synchronized void unregisterViaFile(String jsonDescriptorPath, boolean deletePackage)
            throws ManagedComponentsUnsupportedException {

        if (_propertiesUtil.dockerProfileEnabled()) {
            throw new ManagedComponentsUnsupportedException();
        }

        Optional<RegisterComponentModel> optRegisterModel = _componentStateService.get()
                .stream()
                .filter(rcm -> jsonDescriptorPath.equals(rcm.getJsonDescriptorPath()))
                .findAny();

        if (optRegisterModel.isPresent()) {
            try {
                removeManagedComponent(optRegisterModel.get(), deletePackage);
            }
            catch (Exception ex) {
               _componentStateService.replaceComponentState(
                       optRegisterModel.get().getComponentName(), ComponentState.REGISTER_ERROR);
                throw ex;
            }
        }
        else {
            String componentTld = getComponentTopLevelDirName(jsonDescriptorPath);
            _deployService.undeployComponent(componentTld);
        }
    }

    @Override
    public void removePackage(String componentPackageFileName) throws ManagedComponentsUnsupportedException {
        if (_propertiesUtil.dockerProfileEnabled()) {
            throw new ManagedComponentsUnsupportedException();
        }

        Optional<RegisterComponentModel> optRegisterModel =
                _componentStateService.getByPackageFile(componentPackageFileName);

        if (optRegisterModel.isPresent()) {
            removeManagedComponent(optRegisterModel.get(), true);
            return;
        }

        Path componentPackage = Paths.get(
                _propertiesUtil.getUploadedComponentsDirectory().getAbsolutePath(), componentPackageFileName);

        try {
            Files.deleteIfExists(componentPackage);
            _componentStateService.removePackage(componentPackageFileName);
        }
        catch (IOException ex) {
            _componentStateService.replacePackageState(componentPackageFileName, ComponentState.REGISTER_ERROR);
            throw new IllegalStateException(
                    "Couldn't delete the component package at: " + componentPackage.toAbsolutePath(), ex);
        }
    }


    @Override
    public void unregisterRetainPackage(String componentPackageFileName) throws ManagedComponentsUnsupportedException {
        if (_propertiesUtil.dockerProfileEnabled()) {
            throw new ManagedComponentsUnsupportedException();
        }

        RegisterComponentModel rcm = _componentStateService.getByPackageFile(componentPackageFileName)
                .orElse(null);
        if (rcm != null) {
            Path pathToPackage = Paths.get(rcm.getFullUploadedFilePath());
            removeManagedComponent(rcm, false);
            _componentStateService.addEntryForUploadedPackage(pathToPackage);
        }
    }


    @Override
    public void deleteCustomPipelines(RegisterComponentModel registrationModel)
            throws ManagedComponentsUnsupportedException {
        if (registrationModel.getServiceName() != null) {
            removeBatchService(registrationModel.getServiceName());
        }
        if (registrationModel.getStreamingServiceName() != null) {
            _streamingServiceManager.deleteService(registrationModel.getStreamingServiceName());
        }
        if (registrationModel.getAlgorithmName() != null) {
            _pipelineService.deleteAlgorithm(registrationModel.getAlgorithmName());
        }

        registrationModel.getPipelines()
                .forEach(_pipelineService::deletePipeline);

        registrationModel.getTasks()
                .forEach(_pipelineService::deleteTask);

        registrationModel.getActions()
                .forEach(_pipelineService::deleteAction);
    }



    private void removeBatchService(String serviceName) throws ManagedComponentsUnsupportedException {
        if (_propertiesUtil.dockerProfileEnabled()) {
            throw new ManagedComponentsUnsupportedException();
        }

        List<NodeManagerModel> nodeModels = _nodeManagerService.getNodeManagerModels();
        for (NodeManagerModel nmm : nodeModels) {
            nmm.getServices()
                    .removeIf(sm -> sm.getServiceName().equals(serviceName));
        }
        try {
            _nodeManagerService.saveAndReloadNodeManagerConfig(nodeModels);
        }
        catch (IOException ex) {
            throw new IllegalStateException("Failed to save node manager config", ex);
        }
        _nodeManagerService.removeService(serviceName);
    }


    private static String getComponentTopLevelDirName(String pathToJsonDescriptor) {
        return getComponentTopLevelDir(pathToJsonDescriptor).getFileName().toString();
    }

    private static Path getComponentTopLevelDir(String pathToJsonDescriptor) {
        return Paths.get(pathToJsonDescriptor).getParent().getParent();
    }
}
