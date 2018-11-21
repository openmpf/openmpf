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
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mitre.mpf.component.executor.detection.MPFDetectionMain;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionDeadLetterProcessor;
import org.mitre.mpf.wfm.camel.routes.DlqRouteBuilder;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.jms.*;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestDlqRouteBuilder {

    private CamelContext camelContext;

    private ConnectionFactory connectionFactory;

    private JmsComponent activeMQComponent;

    public static final String ENTRY_POINT = "jms://MPF.TEST.ActiveMQ.DLQ";
    public static final String EXIT_POINT = "jms:MPF.TEST.COMPLETED_DETECTIONS";
    public static final String AUDIT_EXIT_POINT = "jms://MPF.TEST.DLQ_PROCESSED_MESSAGES";
    public static final String ROUTE_ID = "Test DLQ Route";
    public static final String SELECTOR_REPLY_TO = "queue://MPF.TEST.COMPLETED_DETECTIONS";

    public static final String DUPLICATE_FROM_STORE_CAUSE =
            "java.lang.Throwable: duplicate from store for queue://MPF.DETECTION_DUMMY_REQUEST";

    public static final int SLEEP_TIME_MILLISEC = 500;

    @Mock
    private DetectionDeadLetterProcessor mockDetectionDeadLetterProcessor;

    @Before
    public void init() throws Exception {
        // System.setProperty("org.apache.activemq.SERIALIZABLE_PACKAGES", "com.google.protobuf");

        MockitoAnnotations.initMocks(this);

        camelContext = new DefaultCamelContext();
        connectionFactory = new ActiveMQConnectionFactory(MPFDetectionMain.ACTIVEMQHOST);
        activeMQComponent = ActiveMQComponent.jmsComponentAutoAcknowledge(connectionFactory);
        camelContext.addComponent("jms", activeMQComponent);

        // camelContext.getTypeConverterRegistry().addTypeConverter(java.io.InputStream.class,
        //        org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionRequest.class, new DetectionRequestConverter());

        DlqRouteBuilder dlqRouteBuilder =
                new DlqRouteBuilder(ENTRY_POINT, EXIT_POINT, AUDIT_EXIT_POINT, ROUTE_ID, SELECTOR_REPLY_TO, false);
        // dlqRouteBuilder.setDeadLetterProcessor(new DetectionDeadLetterProcessor()); // TODO
        dlqRouteBuilder.setDeadLetterProcessor(mockDetectionDeadLetterProcessor);
        dlqRouteBuilder.setContext(camelContext);
        camelContext.addRoutes(dlqRouteBuilder);

        // camelContext.getTypeConverter().

        camelContext.start();
    }

    @After
    public void cleanup() throws Exception {
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    private void sendDlqMessageBack(String dest, String replyTo, boolean withDeliveryFailureCause) throws JMSException {
        //Connection connection = null;
        try {
            //connection = connectionFactory.createConnection();
            //connection.start();
            //Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);

            // BytesMessage message = session.createBytesMessage();
            // message.writeBytes(DetectionProtobuf.DetectionRequest.getDefaultInstance().toByteArray());

            activeMQComponent.getConfiguration().setReplyToOverride(replyTo);

            DefaultMessage message = new DefaultMessage();
            message.setBody(DetectionProtobuf.DetectionRequest.getDefaultInstance());
            message.setHeader(MpfHeaders.JMS_REPLY_TO, replyTo); // NOTE: This does nothing when using ExchangePattern.InOnly

            if (withDeliveryFailureCause) {
                message.setHeader("dlqDeliveryFailureCause",
                        "java.lang.Throwable: duplicate from store for queue://MPF.DETECTION_DUMMY_REQUEST");
            }

            // Destination replyToDestination = session.createQueue(replyTo);
            // message.setJMSReplyTo(replyToDestination);

            Exchange exchange = new DefaultExchange(camelContext);
            exchange.setProperty(MpfHeaders.JMS_REPLY_TO, replyTo);
            exchange.setPattern(ExchangePattern.OutOnly); // InOnly
            exchange.setIn(message);
            ProducerTemplate producerTemplate = camelContext.createProducerTemplate();
            // producerTemplate.setDefaultEndpointUri(dest);
            // producerTemplate.sendBodyAndHeader(DetectionProtobuf.DetectionRequest.getDefaultInstance(), MpfHeaders.JOB_ID, replyTo);
            producerTemplate.send(dest, exchange);

            // Destination dlq = session.createQueue("DUMMY"); //StringUtils.replace(MpfEndpoints.DEAD_LETTER_QUEUE, "activemq:", ""));
            //Destination dlqDestination = session.createQueue(StringUtils.replace(MpfEndpoints.DEAD_LETTER_QUEUE, "activemq:", ""));

            //MessageProducer messageProducer = session.createProducer(dlqDestination);
            //messageProducer.send(message);
            //session.commit();
        } finally {
            //if (connection != null) {
            //    connection.close();
            //}
        }
    }

    private String getJmsOrQueueShortName(String fullName) {
        return fullName.replaceAll(".*//", "");
    }

    private void sendDlqMessage(String dest, String replyTo, String deliveryFailureCause) throws JMSException {
        Connection connection = null;
        try {
            connection = connectionFactory.createConnection();
            connection.start();
            Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);

            // BytesMessage message = session.createBytesMessage();
            // message.writeBytes(DetectionProtobuf.DetectionRequest.getDefaultInstance().toByteArray());

            ObjectMessage message = session.createObjectMessage();
            // message.setObject(DetectionProtobuf.DetectionRequest.getDefaultInstance());
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

            session.
        } finally {
            if (connection != null) {
               connection.close();
            }
        }
    }

    @Test
    public void testIgnoreBadReplyToDupFailure() throws Exception {
        sendDlqMessage(ENTRY_POINT, "BAD_REPLY_TO", DUPLICATE_FROM_STORE_CAUSE);
        Thread.sleep(SLEEP_TIME_MILLISEC);
        verify(mockDetectionDeadLetterProcessor, never()).process(any());
    }

    @Test
    public void testIgnoreBadReplyToNoFailure() throws Exception {
        sendDlqMessage(ENTRY_POINT, "BAD_REPLY_TO", null);
        Thread.sleep(SLEEP_TIME_MILLISEC);
        verify(mockDetectionDeadLetterProcessor, never()).process(any());
    }

    @Test
    public void testIgnoreBadReplyToNonDupFailure() throws Exception {
        sendDlqMessage(ENTRY_POINT, "BAD_REPLY_TO", "OTHER FAILURE");
        Thread.sleep(SLEEP_TIME_MILLISEC);
        verify(mockDetectionDeadLetterProcessor, never()).process(any());
    }

    @Test
    public void testIgnoreDupFailure() throws Exception {
        sendDlqMessage(ENTRY_POINT, SELECTOR_REPLY_TO, DUPLICATE_FROM_STORE_CAUSE);
        Thread.sleep(SLEEP_TIME_MILLISEC);
        verify(mockDetectionDeadLetterProcessor, never()).process(any());
    }

    @Test
    public void testHandleNonDupFailure() throws Exception {
        sendDlqMessage(ENTRY_POINT, SELECTOR_REPLY_TO, "OTHER FAILURE");
        Thread.sleep(SLEEP_TIME_MILLISEC);
        verify(mockDetectionDeadLetterProcessor, atLeastOnce()).process(any());
    }

    @Test
    public void testHandleNoFailure() throws Exception {
        sendDlqMessage(ENTRY_POINT, SELECTOR_REPLY_TO, null);
        Thread.sleep(SLEEP_TIME_MILLISEC);
        verify(mockDetectionDeadLetterProcessor, atLeastOnce()).process(any());


        // NOTE: reply-to not populated
        //sendDlqMessage(StringUtils.replace(MpfEndpoints.DEAD_LETTER_QUEUE, "activemq:", ""),
        //        StringUtils.replace(MpfEndpoints.COMPLETED_DETECTIONS, "jms:", ""), true);
        //Thread.sleep(5000);
        //verify(mockDetectionDeadLetterProcessor, atLeastOnce()).process(any());

        // NOTE: reply-to populated
        //sendDlqMessage("jms://DUMMY", MpfEndpoints.COMPLETED_DETECTIONS_REPLY_TO, true); // MpfEndpoints.DEAD_LETTER_QUEUE
        //Thread.sleep(5000);
        //verify(mockDetectionDeadLetterProcessor, atLeastOnce()).process(any());


        // NOTE: reply-to not populated
        //sendDlqMessageBack(MpfEndpoints.DEAD_LETTER_QUEUE, MpfEndpoints.COMPLETED_DETECTIONS_REPLY_TO, true);
        //Thread.sleep(5000);
        //verify(mockDetectionDeadLetterProcessor, atLeastOnce()).process(any());

        // NOTE: reply-to populated
        //sendDlqMessageBack("jms://DUMMY", MpfEndpoints.COMPLETED_DETECTIONS_REPLY_TO, true); // MpfEndpoints.DEAD_LETTER_QUEUE
        //Thread.sleep(5000);
        //verify(mockDetectionDeadLetterProcessor, atLeastOnce()).process(any());



        //Exchange exchange = new DefaultExchange(camelContext);
        //exchange.setPattern(ExchangePattern.InOnly);
        //camelContext.createProducerTemplate().send


        return; // DEBUG
//
//        /*
//        JsonJobRequest jsonJobRequest = jobRequestBo.createRequest(UUID.randomUUID().toString(), "OCV FACE DETECTION PIPELINE", // TODO: Replace with dummy
//                Arrays.asList(new JsonMediaInputObject("file:///dummy.png")), Collections.emptyMap(), Collections.emptyMap(), false, 0);
//        */
//
//        // camelContext.createProducerTemplate().sendBodyAndHeader(MpfEndpoints.COMPLETED_DETECTIONS, ExchangePattern.InOnly, targetResponse.toByteArray(), MpfHeaders.JOB_ID, jobId);
//        // Exchange exchange = camelContext.createConsumerTemplate().receive(MpfEndpoints.UNSOLICITED_MESSAGES + "?selector=" + MpfHeaders.JOB_ID + "%3D" + jobId, 10000);
//
//        Exchange exchange = new DefaultExchange(camelContext);
//        exchange.setPattern(ExchangePattern.InOptionalOut); // InOptionalOut // "Reply To" header is dropped when using ExchangePattern.In
//
//        // exchange.getIn().setBody(jsonUtils.serialize(jsonJobRequest));
//
//        /*
//        DetectionProtobuf.DetectionRequest detectionRequest = MediaSegmenter.initializeRequest(media, context)
//                .setDataType(DetectionProtobuf.DetectionRequest.DataType.VIDEO)
//                .setVideoRequest(videoRequest)
//                .build();
//        */
//
//
//        Map<String, Object> headers = new HashMap<>();
//        headers.put(MpfHeaders.JOB_ID, 112236);
//        headers.put(MpfHeaders.JMS_PRIORITY, 0);
//        // headers.put(MpfHeaders.JMS_REPLY_TO, MpfEndpoints.COMPLETED_DETECTIONS_REPLY_TO);
//        headers.put("dlqDeliveryFailureCause", "java.lang.Throwable: duplicate from store for queue://MPF.DETECTION_DUMMY_REQUEST");
//        exchange.getIn().setHeaders(headers);
//
//        // exchange.getIn().setBody("HELLO THERE");
//        // camelContext.createProducerTemplate().send("jms:DUMMY", exchange);
//
//        exchange.getIn().setBody(DetectionProtobuf.DetectionRequest.getDefaultInstance());
//        camelContext.createProducerTemplate().send(DlqRouteBuilder.ENTRY_POINT, exchange);
//
//        /*
//        // DEBUG
//        Exchange consumerExchange = camelContext.createConsumerTemplate().receive("jms:DUMMY");
//        System.out.println("BODY: " + consumerExchange.getIn().getBody()); // DEBUG
//        System.out.println("HEADERS: " + consumerExchange.getIn().getHeaders()); // DEBUG
//        System.out.println("PROPERTIES: " + consumerExchange.getProperties()); // DEBUG
//        */
    }
}
