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

package org.mitre.mpf.component.executor.detection;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.mitre.mpf.component.api.detection.MPFDetectionComponentInterface;

import javax.jms.*;

public abstract class MPFMessengerBase implements MessageListener {

    private static int ackMode = Session.AUTO_ACKNOWLEDGE; 
    private static String msgQueueName;
    private static Connection connection;
    private static Destination requestQueue;
    private static boolean transacted = true;
    private static MessageConsumer requestConsumer;

    protected static Session session;
    protected static MessageProducer replyProducer;
    protected static MPFDetectionComponentInterface detector;
    
    public MPFMessengerBase(MPFDetectionComponentInterface detector, final String msgQueueName) throws JMSException {
        this.detector = detector;
        this.msgQueueName = msgQueueName;

        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(MPFDetectionMain.ACTIVEMQHOST);
        connectionFactory.setCloseTimeout(1);
        connectionFactory.setOptimizeAcknowledge(true);
        
        connection = connectionFactory.createConnection();
        connection.start();
        session = connection.createSession(transacted, ackMode);
        requestQueue = session.createQueue(msgQueueName);
        requestConsumer = session.createConsumer(requestQueue);
        requestConsumer.setMessageListener(this);
    }
	
    public void shutdown() {
        try {
            session.close();
            connection.close();
        } catch (JMSException e) {
            // swallow exceptions since we're exiting anyway
        }
    }

    public abstract void onMessage(Message message);
	
}
