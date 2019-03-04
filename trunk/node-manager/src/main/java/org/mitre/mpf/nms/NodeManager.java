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
package org.mitre.mpf.nms;

import org.mitre.mpf.nms.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class NodeManager implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(NodeManager.class);

    private final ChildNodeStateManager nodeStateManager;


    @Autowired
    public NodeManager(ChildNodeStateManager nodeStateManager) {
        this.nodeStateManager = nodeStateManager;
    }


    @Override
    public void run() {
        nodeStateManager.startReceiving(NodeTypes.NodeManager, "NodeManager");
        nodeStateManager.run();

        nodeStateManager.shutdown();
    }

    
    public static void main(String[] args) {
        LOG.info("NodeManager started");

        // Log that we are being shutdown, but more hooks are found during process launches in BaseNodeLauncher
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> LOG.info("NodeManager shutdown")));


        try (ClassPathXmlApplicationContext context
                     = new ClassPathXmlApplicationContext("applicationContext-nm.xml")) {
            context.registerShutdownHook();

            PropertiesUtil propertiesUtil = context.getBean(PropertiesUtil.class);
            if (propertiesUtil.isNodeStatusPageEnabled()) {
                context.getBean(NodeStatusHttpServer.class).start();
            }

            context.getBean(NodeManager.class).run();
        }
    }
}
