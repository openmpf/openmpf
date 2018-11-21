/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.camel;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.camel.CamelContext;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mitre.mpf.component.executor.detection.MPFDetectionMain;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionDeadLetterProcessor;
import org.mitre.mpf.wfm.camel.routes.DlqRouteBuilder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.jms.*;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestDlqRouteBuilder {

    public static final String ENTRY_POINT = "jms://MPF.TEST.ActiveMQ.DLQ";
    public static final String EXIT_POINT = "jms:MPF.TEST.COMPLETED_DETECTIONS";
    public static final String AUDIT_EXIT_POINT = "jms://MPF.TEST.DLQ_PROCESSED_MESSAGES";
    public static final String ROUTE_ID = "Test DLQ Route";
    public static final String SELECTOR_REPLY_TO = "queue://MPF.TEST.COMPLETED_DETECTIONS";

    public static final String DUPLICATE_FROM_STORE_CAUSE =
            "java.lang.Throwable: duplicate from store for queue://MPF.DETECTION_DUMMY_REQUEST";

    public static final int SLEEP_TIME_MILLISEC = 500;

    private CamelContext camelContext;
    private ConnectionFactory connectionFactory;
    private JmsComponent activeMQComponent;
    private Connection connection;
    private Session session;

    @Mock
    private DetectionDeadLetterProcessor mockDetectionDeadLetterProcessor;

    @Before
    public void init() throws Exception {
        MockitoAnnotations.initMocks(this);

        camelContext = new DefaultCamelContext();
        connectionFactory = new ActiveMQConnectionFactory(MPFDetectionMain.ACTIVEMQHOST);
        activeMQComponent = ActiveMQComponent.jmsComponentAutoAcknowledge(connectionFactory);
        camelContext.addComponent("jms", activeMQComponent);
        camelContext.start();

        connection = connectionFactory.createConnection();
        connection.start();

        session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
        
        DlqRouteBuilder dlqRouteBuilder =
                new DlqRouteBuilder(ENTRY_POINT, EXIT_POINT, AUDIT_EXIT_POINT, ROUTE_ID, SELECTOR_REPLY_TO, false);

        dlqRouteBuilder.setDeadLetterProcessor(mockDetectionDeadLetterProcessor);
        dlqRouteBuilder.setContext(camelContext);
        camelContext.addRoutes(dlqRouteBuilder);
    }

    @After
    public void cleanup() throws Exception {
        if (connection != null) {
            connection.stop();
        }
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    private String getJmsOrQueueShortName(String fullName) {
        return fullName.replaceAll(".*//", "");
    }

    private void sendDlqMessage(String dest, String replyTo, String deliveryFailureCause) throws JMSException {
        ObjectMessage message = session.createObjectMessage();
        message.setObject("TEST BODY");

        if (deliveryFailureCause != null) {
            message.setStringProperty("dlqDeliveryFailureCause", deliveryFailureCause);
        }

        Destination replyToDestination = session.createQueue(getJmsOrQueueShortName(replyTo));
        message.setJMSReplyTo(replyToDestination);

        Destination dlqDestination = session.createQueue(getJmsOrQueueShortName(dest));
        MessageProducer messageProducer = session.createProducer(dlqDestination);
        messageProducer.send(message);
        session.commit();
    }

    @Test
    public void ignoreBadReplyToDupFailure() throws Exception {
        sendDlqMessage(ENTRY_POINT, "BAD_REPLY_TO", DUPLICATE_FROM_STORE_CAUSE);
        Thread.sleep(SLEEP_TIME_MILLISEC);
        verify(mockDetectionDeadLetterProcessor, never()).process(any());
    }

    @Test
    public void ignoreBadReplyToNoFailure() throws Exception {
        sendDlqMessage(ENTRY_POINT, "BAD_REPLY_TO", null);
        Thread.sleep(SLEEP_TIME_MILLISEC);
        verify(mockDetectionDeadLetterProcessor, never()).process(any());
    }

    @Test
    public void ignoreBadReplyToNonDupFailure() throws Exception {
        sendDlqMessage(ENTRY_POINT, "BAD_REPLY_TO", "OTHER FAILURE");
        Thread.sleep(SLEEP_TIME_MILLISEC);
        verify(mockDetectionDeadLetterProcessor, never()).process(any());
    }

    @Test
    public void ignoreDupFailure() throws Exception {
        sendDlqMessage(ENTRY_POINT, SELECTOR_REPLY_TO, DUPLICATE_FROM_STORE_CAUSE);
        Thread.sleep(SLEEP_TIME_MILLISEC);
        verify(mockDetectionDeadLetterProcessor, never()).process(any());
    }

    @Test
    public void handleNonDupFailure() throws Exception {
        sendDlqMessage(ENTRY_POINT, SELECTOR_REPLY_TO, "OTHER FAILURE");
        Thread.sleep(SLEEP_TIME_MILLISEC);
        verify(mockDetectionDeadLetterProcessor, atLeastOnce()).process(any());
    }

    @Test
    public void handleNoFailure() throws Exception {
        sendDlqMessage(ENTRY_POINT, SELECTOR_REPLY_TO, null);
        Thread.sleep(SLEEP_TIME_MILLISEC);
        verify(mockDetectionDeadLetterProcessor, atLeastOnce()).process(any());
    }
}
