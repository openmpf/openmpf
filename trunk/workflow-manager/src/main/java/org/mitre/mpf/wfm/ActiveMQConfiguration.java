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

import java.util.concurrent.TimeUnit;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.region.policy.PolicyEntry;
import org.apache.activemq.broker.region.policy.PolicyMap;
import org.apache.camel.ThreadPoolRejectedPolicy;
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
        // setMaxMessagesPerTask makes threads stop after about
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

        // Configures the thread pool so that when a new task arrives it takes the first 
        // appropriate action in the list below:
        //  - If no threads are running, start one to run the task. The thread will remain in the 
        //    pool until the pool is shutdown.
        //  - If there is an idle thread, use it to run the task.
        //  - If there are fewer than getAmqConcurrentConsumers threads in the pool, create a new 
        //    thread to run the task.
        //  - Run the task on the calling thread. This slows down task producers to prevent over 
        //    loading the thread pool.
        // All threads, except for the first, will remain idle for 1 minute after completing a task. 
        // If no task arrives during that time, the thread will exit.
        return new ThreadPoolProfileBuilder(SPLITTER_THREAD_POOL_REF)
            // Always have at least 1 thread in the pool.
            .poolSize(1)
            .maxPoolSize(propertiesUtil.getAmqConcurrentConsumers())
            .keepAliveTime(1L, TimeUnit.MINUTES)
            // We do not use the default queue size because the thread pool prefers to queue jobs 
            // rather than start new threads after poolSize threads have been started. There is no 
            // way to both use a non-zero sized queue and only queue after maxPoolSize threads have 
            // been started.
            .maxQueueSize(0)
            // When the thread pool is at the maximum size and all threads in the pool are busy,
            // the task will run on the calling thread rather than on a pool thread. This is useful 
            // because it effectively rate limits task producers.
            .rejectedPolicy(ThreadPoolRejectedPolicy.CallerRuns)
            // When combined with the other settings here, enabling core thread time out would have 
            // the same effect as setting the pool size to 0. 
            .allowCoreThreadTimeOut(false)
            .build();
    }
}
