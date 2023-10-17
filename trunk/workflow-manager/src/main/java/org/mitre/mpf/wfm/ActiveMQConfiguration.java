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

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.region.policy.PolicyEntry;
import org.apache.activemq.broker.region.policy.PolicyMap;
import org.apache.camel.builder.ThreadPoolProfileBuilder;
import org.apache.camel.spi.ThreadPoolProfile;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ActiveMQConfiguration {

    @Bean("activemqBroker")
    public BrokerService activemqBroker(PropertiesUtil propertiesUtil) throws Exception {
        var broker = new BrokerService();
        broker.addConnector(propertiesUtil.getAmqOpenWireBindAddress());
        broker.setPersistent(false);
        // Remove memory limit.
        broker.getSystemUsage().getMemoryUsage().setLimit(0);

        var policy = new PolicyEntry();
        policy.setQueuePrefetch(0);
        policy.setPrioritizedMessages(true);
        var policyMap = new PolicyMap();
        policyMap.setDefaultEntry(policy);
        broker.setDestinationPolicy(policyMap);

        broker.start();
        return broker;
    }


    @Bean
    public org.apache.activemq.camel.component.ActiveMQConfiguration activemqConfiguration(
            BrokerService broker, PropertiesUtil propertiesUtil) {
        var amqConfig = new org.apache.activemq.camel.component.ActiveMQConfiguration();
        amqConfig.setBrokerURL(broker.getVmConnectorURI().toString());
        amqConfig.setTransacted(false);
        amqConfig.setDeliveryPersistent(false);
        amqConfig.setAcknowledgementModeName("CLIENT_ACKNOWLEDGE");
        amqConfig.setPreserveMessageQos(true);

        amqConfig.setConcurrentConsumers(2);
        amqConfig.setMaxConcurrentConsumers(propertiesUtil.getAmqConcurrentConsumers());
        // Make threads stop after about
        // (amqConfig.getReceiveTimeout() * amqConfig.getMaxMessagesPerTask() * 1000) seconds of
        // inactivity.
        amqConfig.setMaxMessagesPerTask(60);
        return amqConfig;
    }


    public static final String SPLITTER_THREAD_POOL_REF = "splitterThreadPoolProfile";

    @Bean(SPLITTER_THREAD_POOL_REF)
    public ThreadPoolProfile splitterThreadPoolProfile(PropertiesUtil propertiesUtil) {
        // The default thread pool profile for splits with parallelProcessing only allows for 10
        // parallel threads.
        return new ThreadPoolProfileBuilder(SPLITTER_THREAD_POOL_REF)
            .poolSize(propertiesUtil.getAmqConcurrentConsumers())
            .maxPoolSize(propertiesUtil.getAmqConcurrentConsumers())
            .allowCoreThreadTimeOut(true)
            .build();
    }

}
