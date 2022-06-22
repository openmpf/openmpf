/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mitre.mpf.nms.xml.EnvironmentVariable;
import org.mitre.mpf.nms.xml.NodeManager;
import org.mitre.mpf.nms.xml.NodeManagers;
import org.mitre.mpf.nms.xml.Service;
import org.mitre.mpf.rest.api.node.EnvironmentVariableModel;
import org.mitre.mpf.rest.api.node.NodeManagerModel;
import org.mitre.mpf.rest.api.node.ServiceModel;
import org.mitre.mpf.wfm.nodeManager.NodeManagerStatus;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.WritableResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.*;

import static java.util.stream.Collectors.toCollection;

@org.springframework.stereotype.Service
@Profile("!docker")
public class NodeManagerServiceImpl implements NodeManagerService {

    public static final Logger log = LoggerFactory.getLogger(NodeManagerService.class);

    @Autowired
    private PropertiesUtil propertiesUtil;

    @Autowired
    private NodeManagerStatus nodeManagerStatus;

    @Autowired
    private ObjectMapper objectMapper;


    @Override
    public boolean saveAndReloadNodeManagerConfig(List<NodeManagerModel> nodeManagerModels) throws IOException {
        NodeManagers managers = convertFromModels(nodeManagerModels);

        try (OutputStream outputStream = propertiesUtil.getNodeManagerConfigResource().getOutputStream()) {
            NodeManagers.toJson(managers, outputStream);
        }

        //could produce an InterruptedException from Thread.sleep - will be caught by the ErrorController super class
        nodeManagerStatus.reloadNodeManagerConfig();

        return true;
    }


    private static NodeManagers convertFromModels(Collection<NodeManagerModel> models) {
        NodeManagers managers = new NodeManagers();
        models.stream()
                .map(NodeManagerServiceImpl::convertFromModel)
                .forEach(managers::add);
        return managers;
    }


    private static NodeManager convertFromModel(NodeManagerModel model) {
        NodeManager manager = new NodeManager(model.getHost());
        manager.setAutoConfigured(model.isAutoConfigured());
        model.getServices().stream()
                .map(NodeManagerServiceImpl::convertFromModel)
                .forEach(manager::add);
        return manager;
    }


    private static Service convertFromModel(ServiceModel serviceModel) {
        Service service = new Service(serviceModel.getServiceName(), serviceModel.getCmd());
        service.setArgs(serviceModel.getArgs());
        service.setWorkingDirectory(serviceModel.getWorkingDirectory());
        service.setCount(serviceModel.getServiceCount());
        service.setLauncher(serviceModel.getServiceLauncher());
        service.setDescription(serviceModel.getServiceDescription());

        serviceModel.getEnvironmentVariables().stream()
                .map(NodeManagerServiceImpl::convertFromModel)
                .forEach(ev -> service.getEnvVars().add(ev));

        return service;
    }


    private static EnvironmentVariable convertFromModel(EnvironmentVariableModel envVarModel) {
        EnvironmentVariable envVar = new EnvironmentVariable();
        envVar.setKey(envVarModel.getName());
        envVar.setValue(envVarModel.getValue());
        envVar.setSep(envVarModel.getSep());
        return envVar;
    }



    private NodeManagerModel convertToModel(NodeManager nodeManager, Set<String> availableNodes) {
        NodeManagerModel model = new NodeManagerModel(nodeManager.getTarget());
        model.setCoreNode(isCoreNode(model.getHost()));
        model.setOnline(availableNodes.contains(model.getHost()));
        model.setAutoConfigured(nodeManager.isAutoConfigured());
        if (nodeManager.getServices() != null) {
            nodeManager.getServices().stream()
                .map(ServiceModel::new)
                .forEach(sm -> model.getServices().add(sm));
        }
        return model;
    }


    @Override
    public synchronized List<NodeManagerModel> getNodeManagerModels() {
        try (InputStream inputStream = propertiesUtil.getNodeManagerConfigResource().getInputStream()) {
            NodeManagers managers = NodeManagers.fromJson(inputStream);

            // get the current view once, and then update all the models
            Set<String> availableNodes = getAvailableNodes();

            List<NodeManagerModel> nodeManagerModels = managers.getAll()
                    .stream()
                    .map(m -> convertToModel(m, availableNodes))
                    .collect(toCollection(ArrayList::new));

            return nodeManagerModels;
        }
        catch (IOException ex) {
            throw new UncheckedIOException("Unable to load node manager models", ex);
        }
    }


    // this method is used by the ComponentRegistrationController but should not be
    // publicly exposed as part of the REST API
    @Override
    public boolean addService(Service service) {
        Map<String, ServiceModel> nodeManagerFilePaletteMap = getServiceModels();
        log.debug("Read nodeServicesPalette file");
        // add an entry to the map for new service
        // nodeManagerFilePaletteMap will get written back to the json file
        // nodeManagerPaletteMap is what's currently held in memory for display
        // note, user needs to refresh page in order to see new service
        ServiceModel newService = new ServiceModel(service);
        log.debug("Created new service");
        nodeManagerFilePaletteMap.put(newService.getServiceName(), newService);
        log.debug("Added service to file map");
        //TODO this may have weird effect if map has not been populated yet
        //TODO better solution is to reload JSON file whenever page is loaded
        //nodeManagerPaletteMap.put(newService.getServiceName(), newService);
        log.debug("Added service to memory map");
        //set the service counts to 1 to be consistent with the hardcoded json file
        for (ServiceModel serviceModel : nodeManagerFilePaletteMap.values()) {
            serviceModel.setServiceCount(1);
        }
        // write the map back out to json file
        return setServiceModels(nodeManagerFilePaletteMap);
    }

    // this method is used by the ComponentRegistrationController but should not be
    // publicly exposed as part of the REST API
    @Override
    public Tuple<Boolean, String> removeService(Service service) {
        Tuple<Boolean, String> returnTuple;
        String returnMessage = "removed the " + service.getName() + " service";
        Boolean returnValue = true;
        log.debug("service name is " + service.getName());
        // if service entry exists in nodeManagerPaletteMap, remove it
        ServiceModel theService = new ServiceModel(service);
        log.debug("the service name is " + theService.getServiceName());

        Map<String, ServiceModel> nodeManagerFilePaletteMap = getServiceModels();
        if (nodeManagerFilePaletteMap != null) {
            if (nodeManagerFilePaletteMap.containsKey(theService.getServiceName())) {
                nodeManagerFilePaletteMap.remove(theService.getServiceName());
            } else {
                returnMessage = "The " + service.getName() + " service does not appear to be registered";
                returnValue = false;
            }
        }
        // write the map back out to json file
        if (setServiceModels(nodeManagerFilePaletteMap) == false) {
            returnMessage = "Could not remove the " + service.getName() + " service from the nodeServicesPalette.json file";
            returnValue = false;
        }
        returnTuple = new Tuple<Boolean, String>(returnValue, returnMessage);
        return returnTuple;
    }

    @Override
    public Tuple<Boolean, String> removeService(String serviceName) {
        return removeService(new Service(serviceName, ""));
    }


    private static final TypeReference<TreeMap<String, ServiceModel>> _serviceModelMapTypeRef
            = new TypeReference<TreeMap<String, ServiceModel>>() { };

    @Override
    public Map<String, ServiceModel> getServiceModels() {
        try (InputStream inputStream = propertiesUtil.getNodeManagerPalette().getInputStream()){
            return objectMapper.readValue(inputStream, _serviceModelMapTypeRef);
        }
        catch (IOException ex) {
            throw new UncheckedIOException("Failed to load service models", ex);
        }
    }

    @Override
    public boolean setServiceModels(Map<String, ServiceModel> nodeManagerFilePaletteMap) {
        WritableResource paletteResource = propertiesUtil.getNodeManagerPalette();
        try (OutputStream outStream = paletteResource.getOutputStream()) {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(outStream, nodeManagerFilePaletteMap);
            return true;
        }
        catch (IOException ex) {
            log.error("Failed to save node manager palette JSON", ex);
            return false;
        }
    }

    @Override
    public Set<String> getCoreNodes() {
        return propertiesUtil.getCoreMpfNodes();
    }

    @Override
    public boolean isCoreNode(String host) {
        return propertiesUtil.getCoreMpfNodes().contains(host);
    }

    @Override
    public Set<String> getAvailableNodes() {
        return nodeManagerStatus.getAvailableNodes();
    }

    @Override
    public synchronized void autoConfigureNewNode(String host) throws IOException {
        // Add all services to the new node
        NodeManagerModel newNode = new NodeManagerModel();
        newNode.setHost(host);
        newNode.setAutoConfigured(true);

        List<ServiceModel> serviceModels = new ArrayList<>(getServiceModels().values());
        serviceModels.forEach(n -> n.setServiceCount(propertiesUtil.getNodeAutoConfigNumServices()));
        newNode.setServices(serviceModels);

        List<NodeManagerModel> nodeManagerModelList = getNodeManagerModels();
        nodeManagerModelList.add(newNode);
        saveAndReloadNodeManagerConfig(nodeManagerModelList);
    }

    @Override
    public synchronized void unconfigureIfAutoConfiguredNode(String host) throws IOException {
        List<NodeManagerModel> nodeManagerModelList = getNodeManagerModels();
        if (nodeManagerModelList.removeIf(node -> (node.getHost().equals(host) && node.isAutoConfigured()))) {
            saveAndReloadNodeManagerConfig(nodeManagerModelList);
        }
    }
}
