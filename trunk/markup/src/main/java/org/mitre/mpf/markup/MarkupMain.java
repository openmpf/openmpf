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

package org.mitre.mpf.markup;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MarkupMain {
    private static final Logger LOG = LoggerFactory.getLogger(MarkupMain.class);

    // Set reconnect attempts so that about 5 minutes will be spent attempting to reconnect.
    private static final String DEFAULT_AMQ_URI =
            "failover:(tcp://localhost:61616)?maxReconnectAttempts=13&startupMaxReconnectAttempts=21";

    private static final String REQUEST_QUEUE = "MPF.MARKUP_MARKUPCV_REQUEST";


    public static void main(String[] args) throws JMSException {
        var connection = getConnection(args);
        var standardInWatcher = StandardInWatcher.start(connection, Thread.currentThread());
        try {
            var session = connection.createSession(true, Session.SESSION_TRANSACTED);
            LOG.info("Creating ActiveMQ consumer for queue: {}", REQUEST_QUEUE);
            var jmsConsumer = session.createConsumer(session.createQueue(REQUEST_QUEUE));
            connection.start();

            var markupConsumer = new MarkupRequestConsumer(session);
            Message message;
            while ((message = jmsConsumer.receive()) != null) {
                markupConsumer.onMessage(message);
            }
            if (!standardInWatcher.quitReceived()) {
                LOG.info("Received null message indicating that the ActiveMQ connection was closed. Shutting down...");
            }
        }
        catch (Exception e) {
            if (!standardInWatcher.quitReceived()) {
                LOG.error(e.getMessage(), e);
                throw e;
            }
        }
        finally {
            connection.close();
        }
    }

    private static Connection getConnection(String[] args) throws JMSException {
        String brokerUri;
        if (args.length > 0) {
            brokerUri = args[0];
        }
        else {
            var envUri = System.getenv("ACTIVE_MQ_BROKER_URI");
            if (envUri != null && !envUri.isBlank()) {
                brokerUri = envUri;
            }
            else {
                brokerUri = DEFAULT_AMQ_URI;
            }
        }
        LOG.info("Attempting to connect to broker at: {}", brokerUri);
        return new ActiveMQConnectionFactory(brokerUri).createConnection();
    }
}
