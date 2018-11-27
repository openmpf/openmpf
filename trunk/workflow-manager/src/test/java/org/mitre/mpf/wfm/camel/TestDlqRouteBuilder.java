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
import org.junit.*;
import org.junit.runners.MethodSorters;
import org.mitre.mpf.component.executor.detection.MPFDetectionMain;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionDeadLetterProcessor;
import org.mitre.mpf.wfm.camel.routes.DlqRouteBuilder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.jms.*;

import java.util.UUID;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestDlqRouteBuilder {

    public static final String ENTRY_POINT = "jms://MPF.TEST.ActiveMQ.DLQ";
    public static final String EXIT_POINT = "jms:MPF.TEST.COMPLETED_DETECTIONS";
    public static final String AUDIT_EXIT_POINT = "jms://MPF.TEST.DLQ_PROCESSED_MESSAGES";
    public static final String INVALID_EXIT_POINT = "jms:MPF.TEST.DLQ_INVALID_MESSAGES";
    public static final String ROUTE_ID = "Test DLQ Route";
    public static final String SELECTOR_REPLY_TO = "queue://MPF.TEST.COMPLETED_DETECTIONS";

    public static final String BAD_SELECTOR_REPLY_TO = "queue://MPF.TEST.BAD.COMPLETED_DETECTIONS";

    public static final String DLQ_DELIVERY_FAILURE_CAUSE_PROPERTY = "dlqDeliveryFailureCause";
    public static final String DLQ_DUPLICATE_FAILURE_CAUSE =
            "java.lang.Throwable: duplicate from store for queue://MPF.DETECTION_DUMMY_REQUEST";
    public static final String DLQ_OTHER_FAILURE_CAUSE = "SOME OTHER FAILURE";

    public static final String TEST_BODY = "THIS IS A TEST MESSAGE";

    public static final int SLEEP_TIME_MILLISEC = 500;
    public static final int RECEIVE_TIMEOUT_MILLISEC = 500;

    private CamelContext camelContext;
    private ConnectionFactory connectionFactory;
    private JmsComponent activeMQComponent;
    private Connection connection;
    private Session session;

    @Mock
    private DetectionDeadLetterProcessor mockDetectionDeadLetterProcessor;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        camelContext = new DefaultCamelContext();
        connectionFactory = new ActiveMQConnectionFactory(MPFDetectionMain.ACTIVEMQHOST);
        activeMQComponent = ActiveMQComponent.jmsComponentAutoAcknowledge(connectionFactory);
        camelContext.addComponent("jms", activeMQComponent);
        camelContext.start();

        // TODO: Add converter and tests for DetectionRequest protobuf message body.

        connection = connectionFactory.createConnection();
        connection.start();

        session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);

        DlqRouteBuilder dlqRouteBuilder = new DlqRouteBuilder(ENTRY_POINT, EXIT_POINT, AUDIT_EXIT_POINT,
                INVALID_EXIT_POINT, ROUTE_ID, SELECTOR_REPLY_TO, false);

        dlqRouteBuilder.setDeadLetterProcessor(mockDetectionDeadLetterProcessor);
        dlqRouteBuilder.setContext(camelContext);
        camelContext.addRoutes(dlqRouteBuilder);

        clearoutMessages(ENTRY_POINT);
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

    private void clearoutMessages(String dest) throws JMSException {
        Destination dlqDestination = session.createQueue(getJmsOrQueueShortName(dest));
        MessageConsumer messageConsumer = session.createConsumer(dlqDestination);

        Message message;
        while ((message = messageConsumer.receive(RECEIVE_TIMEOUT_MILLISEC)) != null) { // don't wait
            if (message.getJMSCorrelationID() != null) {
                System.out.println("Clearing out message from " + dest + " with JMSCorrelationID " + message.getJMSCorrelationID());
            } else {
                System.out.println("Clearing out message from " + dest);
            }
        }

        session.commit();
        messageConsumer.close();
    }

    private Message receiveMessage(String dest) throws JMSException {
        Destination dlqDestination = session.createQueue(getJmsOrQueueShortName(dest));
        MessageConsumer messageConsumer = session.createConsumer(dlqDestination);
        Message message = messageConsumer.receive(RECEIVE_TIMEOUT_MILLISEC);
        session.commit();
        messageConsumer.close();
        return message;
    }

    private void consumeLeftover(String dest, String replyTo, String deliveryFailureCause, String jmsCorrelationId)
            throws JMSException {
        Message message = receiveMessage(dest);
        Assert.assertNotNull(message);

        if (replyTo == null) {
            Assert.assertNull(message.getJMSReplyTo());
        } else {
            Assert.assertEquals(replyTo, message.getJMSReplyTo().toString());
        }

        Assert.assertEquals(deliveryFailureCause, message.getStringProperty(DLQ_DELIVERY_FAILURE_CAUSE_PROPERTY));
        Assert.assertEquals(TEST_BODY, ((ObjectMessage)message).getObject().toString());
        Assert.assertEquals(jmsCorrelationId, message.getJMSCorrelationID());
    }

    private String sendDlqMessage(String dest, String replyTo, String deliveryFailureCause) throws JMSException {
        ObjectMessage message = session.createObjectMessage();
        message.setObject(TEST_BODY);

        if (replyTo != null) {
            Destination replyToDestination = session.createQueue(getJmsOrQueueShortName(replyTo));
            message.setJMSReplyTo(replyToDestination);
        }

        if (deliveryFailureCause != null) {
            message.setStringProperty(DLQ_DELIVERY_FAILURE_CAUSE_PROPERTY, deliveryFailureCause);
        }

        String jmsCorrelationId = UUID.randomUUID().toString();
        message.setJMSCorrelationID(jmsCorrelationId);

        Destination dlqDestination = session.createQueue(getJmsOrQueueShortName(dest));
        MessageProducer messageProducer = session.createProducer(dlqDestination);
        messageProducer.send(message);
        session.commit();

        return jmsCorrelationId;
    }

    private void runTest(String dest, String replyTo, String deliveryFailureCause, boolean isHandled) throws Exception {
        String jmsCorrelationId = sendDlqMessage(dest, replyTo, deliveryFailureCause);
        Thread.sleep(SLEEP_TIME_MILLISEC);
        verify(mockDetectionDeadLetterProcessor, times(isHandled ? 1 : 0)).process(any());
        if (!isHandled) {
            consumeLeftover(dest, replyTo, deliveryFailureCause, jmsCorrelationId);
        }
    }

    // No reply-to tests

    @Test
    public void ignoreNoReplyToAndDupFailure() throws Exception {
        runTest(ENTRY_POINT, null, DLQ_DUPLICATE_FAILURE_CAUSE, false);
    }

    @Test
    public void ignoreNoReplyToAndNoFailure() throws Exception {
        runTest(ENTRY_POINT, null, null, false);
    }

    @Test
    public void ignoreNoReplyToAndNonDupFailure() throws Exception {
        runTest(ENTRY_POINT, null, DLQ_OTHER_FAILURE_CAUSE, false);
    }

    // Bad reply-to tests

    @Test
    public void ignoreBadReplyToAndDupFailure() throws Exception {
        runTest(ENTRY_POINT, BAD_SELECTOR_REPLY_TO, DLQ_DUPLICATE_FAILURE_CAUSE, false);
    }

    @Test
    public void ignoreBadReplyToAndNoFailure() throws Exception {
        runTest(ENTRY_POINT, BAD_SELECTOR_REPLY_TO, null, false);
    }

    @Test
    public void ignoreBadReplyToAndNonDupFailure() throws Exception {
        runTest(ENTRY_POINT, BAD_SELECTOR_REPLY_TO, DLQ_OTHER_FAILURE_CAUSE, false);
    }

    // Good reply-to tests

    @Test
    public void ignoreDupFailure() throws Exception {
        runTest(ENTRY_POINT, SELECTOR_REPLY_TO, DLQ_DUPLICATE_FAILURE_CAUSE, false);
    }

    @Test
    public void handleNoFailure() throws Exception {
        runTest(ENTRY_POINT, SELECTOR_REPLY_TO, null, true);
    }

    @Test
    public void handleNonDupFailure() throws Exception {
        runTest(ENTRY_POINT, SELECTOR_REPLY_TO, DLQ_OTHER_FAILURE_CAUSE, true);
    }
}
