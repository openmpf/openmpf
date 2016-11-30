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

package org.mitre.mpf.app;

import org.apache.commons.io.FilenameUtils;
import org.mitre.mpf.rest.api.node.NodeManagerModel;
import org.mitre.mpf.rest.api.node.ServiceModel;
import org.mitre.mpf.wfm.service.NodeManagerService;
import org.mitre.mpf.wfm.service.component.AddComponentService;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class ComponentRegistrationApp {

    /**
     * Register one or more components. Updates the Algorithms.xml, Actions.xml, Tasks.xml, Pipelines.xml,
     * nodeServicesPalette.json, and nodeManagerConfig.xml files.
     *
     * @param args args[0] contains the path to the cpp component list;
     *             args[1] contains the number of services per component that should be configured
     *             args[2] contains the node manager hostname to use in nodeManagerConfig.xml
     *             args[3] (optional) contains the string of user-specified components
     */
    public static void main(String[] args) {

        // NOTE: "-DcppComponents=<blank>" is the same as not providing the option

        if (args.length != 3 && args.length != 4) {
            System.err.println("Usage: java " + ComponentRegistrationApp.class.getSimpleName() +
                    " component-list-file num-services-per-component node-manager-hostname [\"componentA,componentB,componentC,...\"]");
            System.exit(-1);
        }

        String componentListPath = args[0];
        if (!Files.exists(Paths.get(componentListPath))) {
            System.err.println("Cannot read: " + componentListPath);
            System.exit(-1);
        }

        List<String> componentPaths = null;
        try {
            componentPaths = Files.readAllLines(Paths.get(componentListPath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        int numServicesPerComponent = 1;
        try {
            numServicesPerComponent = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        String nodeManagerHostname = args[2];

        if (args.length == 4) {
            String componentsSpecified = args[3];
            List<String> componentsSpecifiedList = Arrays.asList(componentsSpecified.split(","));

            List<String> componentsSpecifiedInFileList = componentPaths.stream()
                    .map(c -> FilenameUtils.getBaseName(c))
                    .collect(Collectors.toList());

            // sanity check
            if (!componentsSpecifiedInFileList.containsAll(componentsSpecifiedList)) {
                System.err.println("The specified components " + componentsSpecifiedList
                        + " are not a subset of those in " + componentListPath + " " + componentsSpecifiedInFileList + ".");
                System.err.println("Do a full MPF clean and build.");
                System.exit(-1);
            }

            // filter out components that were built, but should not be registered
            componentPaths = componentPaths.stream()
                    .filter(c -> componentsSpecifiedList.contains(FilenameUtils.getBaseName(c)))
                    .collect(Collectors.toList());
        }

        // performance optimization: load the application context once
        ApplicationContext context = new ClassPathXmlApplicationContext("applicationContext-minimal.xml");
        AutowireCapableBeanFactory beanFactory = context.getAutowireCapableBeanFactory();

        AddComponentService addComponentService = context.getBean(AddComponentService.class);
        beanFactory.autowireBean(addComponentService);

        NodeManagerService nodeManagerService = context.getBean(NodeManagerService.class);
        beanFactory.autowireBean(nodeManagerService);

        for (String componentPath : componentPaths) {

            // TODO: Handle caffe in the same way as the other components, then remove this check.
            // TODO: Ansible should prompt the user to install each and every component in the deployment package.
            String componentName = FilenameUtils.getBaseName(componentPath);
            if (componentName.equals("caffeComponent")) {
                continue;
            }

            String descriptorPath = componentPath + "/descriptor.json";
            System.out.println("Registering: " + descriptorPath);

            try {
                // update Algorithms.xml, Actions.xml, Tasks.xml, Pipelines.xml, and nodeServicesPalette.json
                addComponentService.registerDeployedComponent(descriptorPath);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(-1); // kill the build if anything goes wrong
            }
        }

        if (numServicesPerComponent > 0) {
            Map<String, ServiceModel> nodeServiceModels = nodeManagerService.getServiceModels();

            for (ServiceModel serviceModel : nodeServiceModels.values()) {
                serviceModel.setServiceCount(numServicesPerComponent);
            }

            NodeManagerModel nodeManagerModel = new NodeManagerModel(nodeManagerHostname);
            nodeManagerModel.setServices(new ArrayList(nodeServiceModels.values()));

            List<NodeManagerModel> nodeManagerModels = new ArrayList<NodeManagerModel>();
            nodeManagerModels.add(nodeManagerModel);

            try {
                // update nodeManagerConfig.xml
                nodeManagerService.saveNodeManagerConfig(nodeManagerModels, false); // don't reload NodeManagerStatus
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(-1); // kill the build if anything goes wrong
            }
        }
    }

}
