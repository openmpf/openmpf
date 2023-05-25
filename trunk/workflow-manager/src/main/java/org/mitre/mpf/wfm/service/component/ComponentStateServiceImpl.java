/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mitre.mpf.rest.api.component.ComponentState;
import org.mitre.mpf.rest.api.component.RegisterComponentModel;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@Service
public class ComponentStateServiceImpl implements ComponentStateService {

    private final PropertiesUtil propertiesUtil;

    private final ObjectMapper objectMapper;

    @Inject
    ComponentStateServiceImpl(PropertiesUtil propertiesUtil, ObjectMapper objectMapper) {
        this.propertiesUtil = propertiesUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<RegisterComponentModel> getByPackageFile(String componentPackageFileName) {
        return loadComponentModelList()
                .stream()
                .filter(rcm -> componentPackageFileName.equals(rcm.getPackageFileName()))
                .findAny();
    }

    @Override
    public Optional<RegisterComponentModel> getByComponentName(String componentName) {
        return loadComponentModelList()
                .stream()
                .filter(rcm -> componentName.equals(rcm.getComponentName()))
                .findAny();

    }

    @Override
    public synchronized void update(RegisterComponentModel updated) {
        List<RegisterComponentModel> models = loadComponentModelList();
        models.removeIf(existing -> modelsMatch(existing, updated));
        models.add(updated);
        save(models);
    }

    @Override
    public synchronized ComponentState replacePackageState(String componentPackageFileName, ComponentState newState) {
        RegisterComponentModel rcm = getByPackageFile(componentPackageFileName)
                .orElseGet(RegisterComponentModel::new);

        rcm.setPackageFileName(componentPackageFileName);
        return replaceState(rcm, newState);


    }

    @Override
    public ComponentState replaceComponentState(String componentName, ComponentState newState) {
        RegisterComponentModel rcm = getByComponentName(componentName)
                .orElseGet(RegisterComponentModel::new);

        rcm.setComponentName(componentName);
        return replaceState(rcm, newState);
    }

    private ComponentState replaceState(RegisterComponentModel rcm, ComponentState newState) {
        ComponentState oldState = rcm.getComponentState();
        rcm.setComponentState(newState);
        update(rcm);
        return oldState;
    }

    @Override
    public synchronized void removeComponent(String componentName) {
        List<RegisterComponentModel> models = loadComponentModelList();
        models.removeIf(m -> componentName.equals(m.getComponentName()));
        save(models);
    }

    @Override
    public void removePackage(String componentPackageFileName) {
        List<RegisterComponentModel> models = loadComponentModelList();
        models.removeIf(m -> componentPackageFileName.equals(m.getPackageFileName()));
        save(models);
    }

    @Override
    public void addEntryForUploadedPackage(Path componentPackagePath) {
        addNewEntryForUploadedPackage(componentPackagePath, ComponentState.UPLOADED);
    }

    @Override
    public void addEntryForDeployedPackage(Path componentPackagePath, Path descriptorPath) {
        RegisterComponentModel newModel = new RegisterComponentModel();
        newModel.setFullUploadedFilePath(componentPackagePath.toAbsolutePath().toString());
        newModel.setPackageFileName(componentPackagePath.getFileName().toString());
        newModel.setDateUploaded(Instant.now());
        newModel.setComponentState(ComponentState.DEPLOYED);
        newModel.setJsonDescriptorPath(descriptorPath.toString());
        addEntry(newModel);
    }

    @Override
    public void addUploadErrorEntry(String componentPackageFileName) {
        RegisterComponentModel model = new RegisterComponentModel();
        model.setComponentState(ComponentState.UPLOAD_ERROR);
        model.setPackageFileName(componentPackageFileName);
        addEntry(model);
    }


    @Override
    public void addRegistrationErrorEntry(Path pathToComponentPackage) {
        addNewEntryForUploadedPackage(pathToComponentPackage, ComponentState.REGISTER_ERROR);
    }


    private void addNewEntryForUploadedPackage(Path pathToComponentPackage, ComponentState componentState) {
        RegisterComponentModel newModel = new RegisterComponentModel();
        newModel.setFullUploadedFilePath(pathToComponentPackage.toAbsolutePath().toString());
        newModel.setPackageFileName(pathToComponentPackage.getFileName().toString());
        newModel.setDateUploaded(Instant.now());
        newModel.setComponentState(componentState);
        addEntry(newModel);
    }


    private void addEntry(RegisterComponentModel newModel) {
        List<RegisterComponentModel> models = loadComponentModelList();
        Optional<RegisterComponentModel> optDuplicateModel = models.stream()
                .filter(rcm -> modelsMatch(rcm, newModel))
                .findAny();

        if (optDuplicateModel.isPresent()) {
            RegisterComponentModel duplicateModel = optDuplicateModel.get();
            if (duplicateModel.getFullUploadedFilePath() != null) {
                throw new IllegalStateException(
                        "An existing component already uses the path: " + duplicateModel.getFullUploadedFilePath());
            }
            else {
                throw new IllegalStateException(
                        "An existing component already uses the name: " + duplicateModel.getComponentName());
            }
        }

        models.add(newModel);
        save(models);
    }

    @Override
    public List<RegisterComponentModel> get() {
        return new ArrayList<>(loadComponentModelList());
    }

    private static final TypeReference<List<RegisterComponentModel>> _registerModelListTypeRef
            = new TypeReference<List<RegisterComponentModel>>() {};

    private List<RegisterComponentModel> loadComponentModelList() {
        try  (InputStream inputStream = propertiesUtil.getComponentInfoFile().getInputStream()) {
            return objectMapper.readValue(inputStream, _registerModelListTypeRef);
        }
        catch (IOException ex) {
            throw new IllegalStateException("An exception occurred while trying to load component info JSON file.", ex);
        }
    }

    private void save(List<RegisterComponentModel> modelList) {
        try (OutputStream outputStream = propertiesUtil.getComponentInfoFile().getOutputStream()) {
            objectMapper.writeValue(outputStream, modelList);
        }
        catch (IOException ex) {
            throw new IllegalStateException("An exception occurred while trying to save component info JSON file.", ex);
        }
    }

    private static boolean modelsMatch(RegisterComponentModel model1, RegisterComponentModel model2) {
        String name1 = model1.getComponentName();
        String name2 = model2.getComponentName();
        if (name1 != null && name1.equals(name2)) {
            return true;
        }

        String path1 = model1.getFullUploadedFilePath();
        String path2 = model2.getFullUploadedFilePath();
        return path1 != null && path1.equals(path2);
    }

}
