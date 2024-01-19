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

package org.mitre.mpf.wfm.camel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import javax.jms.BytesMessage;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionDeadLetterProcessor;
import org.mitre.mpf.wfm.camel.routes.DlqRouteBuilder;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.ProtobufDataFormatFactory;
import org.mockito.Mock;

import com.google.protobuf.InvalidProtocolBufferException;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestDlqRouteBuilder extends MockitoTest.Lenient {

    private static final String ENTRY_POINT = "activemq:MPF.TEST.ActiveMQ.DLQ";
    private static final String EXIT_POINT = "activemq:MPF.TEST.COMPLETED_DETECTIONS";
    private static final String AUDIT_EXIT_POINT = "activemq://MPF.TEST.DLQ_PROCESSED_MESSAGES";
    private static final String INVALID_EXIT_POINT = "activemq:MPF.TEST.DLQ_INVALID_MESSAGES";
    private static final String ROUTE_ID_PREFIX = "Test DLQ Route";
    private static final String SELECTOR_REPLY_TO = "queue://MPF.TEST.COMPLETED_DETECTIONS";

    private static final String BAD_SELECTOR_REPLY_TO = "queue://MPF.TEST.BAD.COMPLETED_DETECTIONS";

    private static final String DUPLICATE_FROM_STORE_FAILURE_CAUSE =
            "java.lang.Throwable: duplicate from store for queue://MPF.DETECTION_DUMMY_REQUEST";
    private static final String DUPLICATE_FROM_CURSOR_FAILURE_CAUSE =
            "java.lang.Throwable: duplicate paged in from cursor for queue://MPF.DETECTION_DUMMY_REQUEST";
    private static final String SUPPRESSING_DUPLICATE_FAILURE_CAUSE =
            "java.lang.Throwable: Suppressing duplicate delivery on connection, consumer ID:dummy";
    private static final String OTHER_FAILURE_CAUSE = "some other failure";

    private static final String RUN_ID_PROPERTY_KEY = "runId";

    private static final int NUM_MESSAGES_PER_TEST = 5;
    private static final int NUM_LEFTOVER_RECEIVE_RETRIES = 5;

    // time for all messages to be processed by the DetectionDeadLetterProcessor
    private static final int HANDLE_TIMEOUT_MILLISEC = 30_000;

    // time to wait for DetectionDeadLetterProcessor to see if it processes any messages when it shouldn't do anything
    private static final int NOT_HANDLE_TIMEOUT_MILLISEC = 500;

    // time for leftover message to be received from queue
    private static final int LEFTOVER_RECEIVE_TIMEOUT_MILLISEC = 10_000;

    private static int runId = -1;

    private CamelContext camelContext;
    private ConnectionFactory connectionFactory;
    private ActiveMQConnection connection;
    private Session session;

    @Mock
    private DetectionDeadLetterProcessor mockDetectionDeadLetterProcessor;


    @Before
    public void setup() throws Exception {
        SimpleRegistry simpleRegistry = new SimpleRegistry();
        simpleRegistry.put(DetectionDeadLetterProcessor.REF, mockDetectionDeadLetterProcessor);
        camelContext = new DefaultCamelContext(simpleRegistry);

        connectionFactory = new ActiveMQConnectionFactory(
                "vm://test_dlq?broker.persistent=false");
        ActiveMQComponent activeMqComponent = ActiveMQComponent.activeMQComponent();
        activeMqComponent.setConnectionFactory(connectionFactory);
        camelContext.addComponent("activemq", activeMqComponent);
        camelContext.start();

        connection = (ActiveMQConnection) connectionFactory.createConnection();
        connection.start();

        session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);

        removeQueues(); // clean up last test run

        var mockPropertiesUtil = mock(PropertiesUtil.class);
        when(mockPropertiesUtil.getProtobufSizeLimit())
                .thenReturn(10 * 1024 * 1024);

        DlqRouteBuilder dlqRouteBuilder = new DlqRouteBuilder(
                ENTRY_POINT, EXIT_POINT, AUDIT_EXIT_POINT, INVALID_EXIT_POINT, ROUTE_ID_PREFIX,
                SELECTOR_REPLY_TO, new ProtobufDataFormatFactory(mockPropertiesUtil));

        dlqRouteBuilder.setContext(camelContext);
        camelContext.addRoutes(dlqRouteBuilder);

        runId += 1;
    }

    @After
    public void cleanup() throws Exception {
        if (camelContext != null) {
            camelContext.stop();
            camelContext = null;
        }

        if (connection != null) {
            removeQueues();
            connection.stop();
            connection = null;
        }
    }


    private void removeQueues() {
        removeQueueQuietly(ENTRY_POINT);
        removeQueueQuietly(EXIT_POINT);
        removeQueueQuietly(AUDIT_EXIT_POINT);
        removeQueueQuietly(INVALID_EXIT_POINT);
    }

    private void removeQueueQuietly(String longName) {
        try {
            javax.jms.Queue queue = session.createQueue(getQueueShortName(longName));
            connection.destroyDestination((ActiveMQDestination) queue);
        } catch (Exception e) {
            // Sometimes destroying a destination can cause an exception like this:
            //   "javax.jms.JMSException: Destination still has an active subscription: queue://MPF.TEST.ActiveMQ.DLQ"
            // Ignore these exceptions since they are not what's being tested.
        }
    }

    private String getQueueShortName(String fullName) {
        return fullName.replaceAll(".*//", "").replaceAll(".*:", "");
    }

    private void closeQuietly(MessageConsumer messageConsumer) {
        try {
            messageConsumer.close();
        } catch(Exception e) {
            // Sometimes closing a MessageConsumer can cause a NullPointerException, possibly due to a race condition.
            // Ignore these exceptions since they are not what's being tested.
        }
    }

    private Queue<Message> receiveMessages(String dest, boolean expectingMessages) throws JMSException {
        Queue<Message> messages = new LinkedList<>();

        Destination dlqDestination = session.createQueue(getQueueShortName(dest));
        MessageConsumer messageConsumer = session.createConsumer(dlqDestination, RUN_ID_PROPERTY_KEY + " = " + runId);

        if (expectingMessages) {
            for (int i = 0; i < NUM_MESSAGES_PER_TEST; i++) {

                Message message = null;
                for (int j = 0; message == null && j < NUM_LEFTOVER_RECEIVE_RETRIES; j++) {
                    message = messageConsumer.receive(LEFTOVER_RECEIVE_TIMEOUT_MILLISEC);
                    session.commit(); // ACK message so next can be dispatched
                }

                if (message != null) {
                    messages.add(message);
                } else {
                    // Failed to receive message. Abort.
                    closeQuietly(messageConsumer);
                    return messages;
                }
            }
        }

        // This should be a null message.
        // If not expecting messages, we should not have to wait since dropped messages are prefetched.
        messages.add(messageConsumer.receiveNoWait());
        session.commit(); // ACK message

        closeQuietly(messageConsumer);
        return messages;
    }

    private void checkLeftover(String dest, String replyTo, String deliveryFailureCause, Set<String> jmsCorrelationIds,
                                 boolean isLeftover) throws JMSException, InvalidProtocolBufferException {
        Set<String> unseenJmsCorrelationIds = new HashSet<>(jmsCorrelationIds);

        Queue<Message> messages = receiveMessages(dest, isLeftover);

        Assert.assertEquals(isLeftover ? NUM_MESSAGES_PER_TEST + 1 : 1, messages.size());

        if (isLeftover) {
            for (int i = 0; i < NUM_MESSAGES_PER_TEST; i++) {
                BytesMessage bytesMessage = (BytesMessage) messages.poll();

                Assert.assertNotNull(bytesMessage);

                if (replyTo == null) {
                    Assert.assertNull(bytesMessage.getJMSReplyTo());
                } else {
                    Assert.assertEquals(replyTo, bytesMessage.getJMSReplyTo().toString());
                }

                Assert.assertEquals(deliveryFailureCause,
                        bytesMessage.getStringProperty(DlqRouteBuilder.DLQ_DELIVERY_FAILURE_CAUSE_PROPERTY));

                byte[] protoBytes = new byte[(int) bytesMessage.getBodyLength()];
                bytesMessage.readBytes(protoBytes);
                DetectionProtobuf.DetectionRequest detectionRequest = DetectionProtobuf.DetectionRequest.parseFrom(protoBytes);
                Assert.assertEquals("TEST ACTION", detectionRequest.getActionName());

                Assert.assertTrue(unseenJmsCorrelationIds.remove(bytesMessage.getJMSCorrelationID()));
            }

            Assert.assertTrue(unseenJmsCorrelationIds.isEmpty());
        }

        // The last message should always be null.
        Assert.assertNull(messages.poll());
    }

    private Set<String> sendDlqMessages(String dest, String replyTo, String deliveryFailureCause) throws JMSException {
        Destination dlqDestination = session.createQueue(getQueueShortName(dest));
        MessageProducer messageProducer = session.createProducer(dlqDestination);

        Destination replyToDestination = null;
        if (replyTo != null) {
            replyToDestination = session.createQueue(getQueueShortName(replyTo));
        }

        Set<String> jmsCorrelationIds = new HashSet<>();

        for (int i = 0; i < NUM_MESSAGES_PER_TEST; i++) {
            BytesMessage message = session.createBytesMessage();
            DetectionProtobuf.DetectionRequest detectionRequest = DetectionProtobuf.DetectionRequest.newBuilder()
                    .setRequestId(123)
                    .setDataUri("file:///test")
                    .setTaskIndex(1)
                    .setActionIndex(1)
                    .setActionName("TEST ACTION")
                    .build();
            message.writeBytes(detectionRequest.toByteArray());

            if (replyTo != null) {
                message.setJMSReplyTo(replyToDestination);
            }

            if (deliveryFailureCause != null) {
                message.setStringProperty(DlqRouteBuilder.DLQ_DELIVERY_FAILURE_CAUSE_PROPERTY, deliveryFailureCause);
            }

            message.setIntProperty(RUN_ID_PROPERTY_KEY, runId);

            String jmsCorrelationId = UUID.randomUUID().toString();
            jmsCorrelationIds.add(jmsCorrelationId);
            message.setJMSCorrelationID(jmsCorrelationId);

            messageProducer.send(message);
            session.commit();
        }

        messageProducer.close();
        return jmsCorrelationIds;
    }

    private void runTest(String dest, String replyTo, String deliveryFailureCause, boolean isHandled,
                         boolean isLeftover) throws Exception {
        Set<String> jmsCorrelationIds = sendDlqMessages(dest, replyTo, deliveryFailureCause);

        if (isHandled) {
            verify(mockDetectionDeadLetterProcessor,
                timeout(HANDLE_TIMEOUT_MILLISEC).times(NUM_MESSAGES_PER_TEST)).process(any());
        } else {
            verify(mockDetectionDeadLetterProcessor, after(NOT_HANDLE_TIMEOUT_MILLISEC).never()).process(any());
        }

        checkLeftover(dest, replyTo, deliveryFailureCause, jmsCorrelationIds, isLeftover);
    }

    // No reply-to tests

    @Test
    public void dropNoReplyToAndDupFailure() throws Exception {
        runTest(ENTRY_POINT, null, DUPLICATE_FROM_STORE_FAILURE_CAUSE, false, false);
    }

    @Test
    public void ignoreNoReplyToAndNoFailure() throws Exception {
        runTest(ENTRY_POINT, null, null, false, true);
    }

    @Test
    public void ignoreNoReplyToAndNonDupFailure() throws Exception {
        runTest(ENTRY_POINT, null, OTHER_FAILURE_CAUSE, false, true);
    }

    // Bad reply-to tests

    @Test
    public void dropBadReplyToAndDupFailure() throws Exception {
        runTest(ENTRY_POINT, BAD_SELECTOR_REPLY_TO, DUPLICATE_FROM_STORE_FAILURE_CAUSE, false, false);
    }

    @Test
    public void ignoreBadReplyToAndNoFailure() throws Exception {
        runTest(ENTRY_POINT, BAD_SELECTOR_REPLY_TO, null, false, true);
    }

    @Test
    public void ignoreBadReplyToAndNonDupFailure() throws Exception {
        runTest(ENTRY_POINT, BAD_SELECTOR_REPLY_TO, OTHER_FAILURE_CAUSE, false, true);
    }

    // Good reply-to tests

    @Test
    public void dropGoodReplyToAndDupFromStoreFailure() throws Exception {
        runTest(ENTRY_POINT, SELECTOR_REPLY_TO, DUPLICATE_FROM_STORE_FAILURE_CAUSE, false, false);
    }

    @Test
    public void dropGoodReplyToAndDupFromCursorFailure() throws Exception {
        runTest(ENTRY_POINT, SELECTOR_REPLY_TO, DUPLICATE_FROM_CURSOR_FAILURE_CAUSE, false, false);
    }

    @Test
    public void dropGoodReplyToAndSuppressingDupFailure() throws Exception {
        runTest(ENTRY_POINT, SELECTOR_REPLY_TO, SUPPRESSING_DUPLICATE_FAILURE_CAUSE, false, false);
    }

    @Test
    public void handleGoodReplyToAndNoFailure() throws Exception {
        runTest(ENTRY_POINT, SELECTOR_REPLY_TO, null, true, false);
    }

    @Test
    public void handleGoodReplyToAndNonDupFailure() throws Exception {
        runTest(ENTRY_POINT, SELECTOR_REPLY_TO, OTHER_FAILURE_CAUSE, true, false);
    }
}
