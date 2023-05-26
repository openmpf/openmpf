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

import org.mitre.mpf.rest.api.component.ComponentState;
import org.mitre.mpf.rest.api.component.RegisterComponentModel;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface ComponentStateService {

    public List<RegisterComponentModel> get();

    public Optional<RegisterComponentModel> getByPackageFile(String componentPackageFileName);

    public Optional<RegisterComponentModel> getByComponentName(String componentName);

    public void update(RegisterComponentModel model);

    public ComponentState replacePackageState(String componentPackageFileName, ComponentState newState);

    public ComponentState replaceComponentState(String componentName, ComponentState newState);

    public void removeComponent(String componentName);

    public void removePackage(String componentPackageFileName);

    public void addEntryForUploadedPackage(Path componentPackagePath);

    public void addEntryForDeployedPackage(Path componentPackagePath, Path descriptorPath);

    public void addUploadErrorEntry(String componentPackageFileName);

    public void addRegistrationErrorEntry(Path pathToComponentPackage);
}
