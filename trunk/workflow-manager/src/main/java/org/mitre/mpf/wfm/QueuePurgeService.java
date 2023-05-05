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

package org.mitre.mpf.wfm;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.activemq.broker.jmx.BrokerViewMBean;
import org.apache.activemq.broker.jmx.QueueViewMBean;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service("QueuePurgeService")
@Singleton
public class QueuePurgeService {

    private static final Logger LOG = LoggerFactory.getLogger(QueuePurgeService.class);

    @Inject
    QueuePurgeService(PropertiesUtil propertiesUtil) throws Exception {
        if (!propertiesUtil.isAmqBrokerEnabled()) {
            return;
        }

        LOG.info("Purging MPF-owned ActiveMQ queues...");
        var connector = JMXConnectorFactory.connect(
                new JMXServiceURL(propertiesUtil.getAmqBrokerJmxUri()));
        connector.connect();
        try {
            var mBeanServerConnection = connector.getMBeanServerConnection();
            var mbean = MBeanServerInvocationHandler.newProxyInstance(
                    mBeanServerConnection,
                    new ObjectName("org.apache.activemq:brokerName=localhost,type=Broker"),
                    BrokerViewMBean.class,
                    true);
            var whitelist = propertiesUtil.getAmqBrokerPurgeWhiteList();
            LOG.debug("Whitelist contains {} queues: {}", whitelist.size(), whitelist);
            for (ObjectName name : mbean.getQueues()) {
                var queueMbean = MBeanServerInvocationHandler.newProxyInstance(
                        mBeanServerConnection,
                        name,
                        QueueViewMBean.class,
                        true);
                if (!whitelist.contains(queueMbean.getName())
                        && queueMbean.getName().startsWith("MPF.")) {
                    LOG.info("Purging {}", queueMbean.getName());
                    queueMbean.purge();
                }
            }
        }
        finally {
            connector.close();
        }
    }
}
