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

import org.mitre.mpf.rest.api.component.RegisterComponentModel;
import org.mitre.mpf.rest.api.node.NodeManagerModel;
import org.mitre.mpf.rest.api.node.ServiceModel;
import org.mitre.mpf.wfm.service.NodeManagerService;
import org.springframework.context.annotation.Profile;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

@Named
@Profile("!docker")
public class ComponentReRegisterServiceImpl implements ComponentReRegisterService {

    private final ComponentStateService _componentStateService;

    private final NodeManagerService _nodeManagerService;

    private final AddComponentService _addComponentService;

    private final RemoveComponentService _removeComponentService;


    @Inject
    public ComponentReRegisterServiceImpl(
            ComponentStateService componentStateService,
            NodeManagerService nodeManagerService,
            AddComponentService addComponentService,
            RemoveComponentService removeComponentService) {
        _componentStateService = componentStateService;
        _nodeManagerService = nodeManagerService;
        _addComponentService = addComponentService;
        _removeComponentService = removeComponentService;
    }



    @Override
    public RegisterComponentModel reRegisterComponent(String componentPackage) throws ComponentRegistrationException {
        RegisterComponentModel existingComponent = _componentStateService.getByPackageFile(componentPackage)
                .orElse(null);
        if (existingComponent == null) {
            return _addComponentService.registerComponent(componentPackage);
        }

        int existingServiceCount = Optional.ofNullable(existingComponent.getServiceName())
                .map(sn -> _nodeManagerService.getServiceModels().get(sn))
                .map(ServiceModel::getServiceCount)
                .orElse(0);

        Map<String, Integer> hostServiceCounts = Optional.ofNullable(existingComponent.getServiceName())
                .map(this::getHostServiceCounts)
                .orElse(Collections.emptyMap());

        _removeComponentService.unregisterRetainPackage(componentPackage);
        RegisterComponentModel addedComponent = _addComponentService.registerComponent(componentPackage);
        if (addedComponent.getServiceName() != null) {
            updateServiceCount(addedComponent.getServiceName(), existingServiceCount);
            addServicesToNodes(addedComponent.getServiceName(), hostServiceCounts);
        }

        return addedComponent;
    }


    private Map<String, Integer> getHostServiceCounts(String serviceName) {
        List<NodeManagerModel> nodes = _nodeManagerService.getNodeManagerModels();
        Map<String, Integer> hostServiceCounts = new HashMap<>();
        for (NodeManagerModel node : nodes) {
            node.getServices().stream()
                    .filter(sm -> sm.getServiceName().equals(serviceName))
                    .findAny()
                    .map(ServiceModel::getServiceCount)
                    .filter(count -> count > 0)
                    .ifPresent(count -> hostServiceCounts.put(node.getHost(), count));
        }
        return hostServiceCounts;
    }


    private void addServicesToNodes(String serviceName, Map<String, Integer> hostServiceCounts) {
        boolean noServicesToAdd = hostServiceCounts.values().stream()
                .noneMatch(i -> i > 0);
        if (noServicesToAdd) {
            return;
        }

        List<NodeManagerModel> nodes = _nodeManagerService.getNodeManagerModels();

        for (Map.Entry<String, Integer> entry : hostServiceCounts.entrySet()) {
            int serviceCount = entry.getValue();
            if (serviceCount > 0) {
                ServiceModel serviceModel = _nodeManagerService.getServiceModels().get(serviceName);
                serviceModel.setServiceCount(entry.getValue());
                nodes.stream()
                        .filter(nm -> nm.getHost().equals(entry.getKey()))
                        .findAny()
                        .ifPresent(nm -> nm.getServices().add(serviceModel));
            }
        }
        try {
            _nodeManagerService.saveAndReloadNodeManagerConfig(nodes);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    private void updateServiceCount(String serviceName, int numServices) {
        if (numServices < 1) {
            return;
        }

        Map<String, ServiceModel> serviceModels = _nodeManagerService.getServiceModels();
        ServiceModel service = serviceModels.get(serviceName);
        if (service.getServiceCount() < numServices) {
            service.setServiceCount(numServices);
            _nodeManagerService.setServiceModels(serviceModels);
        }
    }
}
