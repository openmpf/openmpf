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

package org.mitre.mpf.nms;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mitre.mpf.nms.xml.Service;
import org.mitre.mpf.rest.api.node.NodeManagerModel;
import org.mitre.mpf.rest.api.node.ServiceModel;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.nodeManager.NodeManagerStatus;
import org.mitre.mpf.wfm.service.NodeManagerService;
import org.mitre.mpf.wfm.util.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Arrays;
import java.util.List;
import java.util.Map;


@ContextConfiguration(locations = {"classpath:applicationContext.xml"})
@RunWith(SpringJUnit4ClassRunner.class)
@ActiveProfiles("jenkins")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestNodeService {

    private static final Logger log = LoggerFactory.getLogger(TestNodeService.class);

    private static final String TEST_NODE_HOST_NAME = "testhost.testdomain";
    private static final String SERVICE_NAME = "Markup";
    private static final String TEST_SERVICE_NAME = "SomeTestDetection";

    @Autowired
    private NodeManagerService nodeManagerService;

    @Autowired
    private NodeManagerStatus nodeManagerStatus;

    @BeforeClass
    public static void classInit() {
        TestUtil.assumeNodeManagerEnabled();
    }

    @Test
    public void testAddAndRemoveNode() throws Exception {

        // ADD TEST NODE

        // Get current configured node manager node list, also get a count of the models returned
        List <NodeManagerModel> nodeManagerModelList = nodeManagerService.getNodeManagerModels();
        int origNodeCount = nodeManagerModelList.size();

        // Make a new node manager model
        NodeManagerModel newNode = new NodeManagerModel();
        newNode.setHost(TEST_NODE_HOST_NAME);

        // Add to our list
        nodeManagerModelList.add(newNode);

        // Can we commit the new node through the service?
        Assert.assertTrue(nodeManagerService.saveAndReloadNodeManagerConfig(nodeManagerModelList));

        // Extra credit! Can we confirm the call to getNodeManagerModels increments by 1 after the add?
        nodeManagerModelList = nodeManagerService.getNodeManagerModels();
        int addedNodeCtr = nodeManagerModelList.size();

        Assert.assertTrue(addedNodeCtr == origNodeCount + 1);


        // REMOVE TEST NODE

        NodeManagerModel nodeManagerModel = getNodeManagerModelByHost(nodeManagerModelList, TEST_NODE_HOST_NAME);
        Assert.assertNotNull(nodeManagerModel);

        Assert.assertTrue(nodeManagerModelList.remove(nodeManagerModel));

        Assert.assertTrue(nodeManagerService.saveAndReloadNodeManagerConfig(nodeManagerModelList));
        int removedNodeCount = nodeManagerModelList.size();

        // Result should equal the original count (because we added and removed one node)
        Assert.assertTrue(removedNodeCount == origNodeCount);
    }

    @Test
    public void testIncrementAndDecrementExistingService() throws Exception {
        // INCREMENT EXISTING SERVICE

        List<NodeManagerModel> nodeManagerModels = nodeManagerService.getNodeManagerModels();

        // Note that the name of the master node is "localhost.localdomain" on the development VM, but it's
        // something else on Jenkins. Assume that the first node manager model is the master node.
        NodeManagerModel nodeManagerModel = nodeManagerModels.get(0);
        String hostName = nodeManagerModel.getHost();

        List<ServiceModel> serviceModelList = nodeManagerModel.getServices();
        ServiceModel serviceModel = getServiceByName(serviceModelList, SERVICE_NAME);

        Assert.assertNotNull(serviceModel);


        int origServiceModelCount = serviceModel.getServiceCount();
        serviceModel.setServiceCount(origServiceModelCount + 1); // create one more instance

        nodeManagerModel.setServices(serviceModelList);

        Assert.assertTrue(nodeManagerService.saveAndReloadNodeManagerConfig(nodeManagerModels));

        nodeManagerStatus.reloadNodeManagerConfig();


        nodeManagerModels = nodeManagerService.getNodeManagerModels(); // should contain one more instance of the serviceModel (in the CONFIGURED / RUNNING state)
        nodeManagerModel = getNodeManagerModelByHost(nodeManagerModels, hostName);

        serviceModelList = nodeManagerModel.getServices();
        serviceModel = getServiceByName(serviceModelList, SERVICE_NAME);
        int addedServiceModelCount = serviceModel.getServiceCount();

        Assert.assertTrue(addedServiceModelCount == origServiceModelCount + 1);


        String serviceDescriptorName = nodeManagerModel.getHost() + ":" + serviceModel.getServiceName() + ":" + addedServiceModelCount;

        Map<String, ServiceDescriptor> serviceDescriptorMap = nodeManagerStatus.getServiceDescriptorMap();
        ServiceDescriptor serviceDescriptor = serviceDescriptorMap.get(serviceDescriptorName);

        /* NOTE: This isn't necessary
        nodeManagerStatus.startService(serviceDescriptorName);
        serviceDescriptorMap = nodeManagerStatus.getServiceDescriptorMap();
        serviceDescriptor = serviceDescriptorMap.get(serviceDescriptorName);
        */

        Assert.assertTrue(serviceDescriptor.isAlive());
        Assert.assertTrue(serviceDescriptor.getLastKnownState().equals(NodeManagerConstants.States.Running));


        // DECREMENT EXISTING SERVICE

        serviceModel.setServiceCount(origServiceModelCount); // remove new instance
        nodeManagerModel.setServices(serviceModelList);

        Assert.assertTrue(nodeManagerService.saveAndReloadNodeManagerConfig(nodeManagerModels));

        nodeManagerStatus.reloadNodeManagerConfig();


        nodeManagerModels = nodeManagerService.getNodeManagerModels(); // should contain one more instance of the serviceModel (in the CONFIGURED / RUNNING state)
        nodeManagerModel = getNodeManagerModelByHost(nodeManagerModels, hostName);

        serviceModelList = nodeManagerModel.getServices();
        serviceModel = getServiceByName(serviceModelList, SERVICE_NAME);
        int removedServiceModelCount = serviceModel.getServiceCount();

        Assert.assertTrue(removedServiceModelCount == origServiceModelCount);
    }

    @Test
    public void testAddAndRemoveNewService() throws Exception {

        // NOTE: ITComponentLifecycle tests the registration, deployment, and usage of a brand new service.
        // This test just checks to make sure the service palette file is updated with a new entry.


        // ADD NEW SERVICE

        Service testService = new Service(TEST_SERVICE_NAME, "SomeTestPath");
        testService.setLauncher("simple");
        List<String> argsList = Arrays.asList("SomeTestArg", "MPF.DETECTION_TEST_REQUEST");
        testService.setArgs(argsList);

        // Check the palette map and make sure the new service doesn't already exist
        int origServiceModelCount = getServiceModelCount(testService.getName());
        Assert.assertTrue(origServiceModelCount == 0);

        // Add the service
        Assert.assertTrue(nodeManagerService.addService(testService)); // add the service to the palette map (not the node configuration XML file)

        // nodeManagerStatus.serviceChange(new ServiceDescriptor(testService, "localhost:localdomain", origCount + 1));

        // Check the palette map and note the addition of the new service
        int addedServiceModelCount = getServiceModelCount(testService.getName());
        Assert.assertTrue(addedServiceModelCount == origServiceModelCount+ 1);


        // NOTE: This is a test service, so don't try to create an instance of it; it will fail to start.


        // REMOVE NEW SERVICE

        // Remove the service
        Tuple<Boolean, String> result = nodeManagerService.removeService(testService);
        Assert.assertTrue(result.getFirst());

        // Check palette map and make sure the new service doesn't already exist
        int removedServiceModelCount = getServiceModelCount(testService.getName());
        Assert.assertTrue(removedServiceModelCount == origServiceModelCount);
    }

    private NodeManagerModel getNodeManagerModelByHost(List<NodeManagerModel> nodeManagerModels, String hostName) {
        NodeManagerModel nodeManagerModel = null;

        // Iterate through given NodeManagerModels collection to select one to return, matching on host name
        for(NodeManagerModel model : nodeManagerModels) {
            if (model.getHost().equals(hostName)) {
                nodeManagerModel = model;
                break;
            }
        }

        return nodeManagerModel;
    }

    private ServiceModel getServiceByName(List<ServiceModel> serviceModelList, String serviceName) {
        ServiceModel serviceModel = null;

        for (ServiceModel model : serviceModelList) {
            if (model.getServiceName().equals(serviceName)) {
                serviceModel = model;
                break;
            }
        }

        return serviceModel;
    }

    private int getDescriptorMapServiceCount(String serviceName) {

        int serviceCount = 0;

        for (Map.Entry<String, ServiceDescriptor> entry : nodeManagerStatus.getServiceDescriptorMap().entrySet()) {
            ServiceDescriptor sd = entry.getValue();

            if (sd.getName().contains(serviceName)) {
                //Assert.assertTrue(sd.isAlive());
                serviceCount++;
            }
        }
        return serviceCount;
    }

    private int getServiceModelCount(String serviceName) {

        Map<String, ServiceModel> serviceModelMap = nodeManagerService.getServiceModels();
        int serviceCount = 0;

        for (Map.Entry<String, ServiceModel> entry : serviceModelMap.entrySet()) {
            if (entry.getValue().getServiceName().contains(serviceName)) {
                serviceCount = entry.getValue().getServiceCount();
            }
        }

        return serviceCount;
    }
}
