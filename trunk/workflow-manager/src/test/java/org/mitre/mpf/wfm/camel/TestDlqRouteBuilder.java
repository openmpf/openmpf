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

import com.uwyn.jhighlight.tools.StringUtils;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.camel.CamelContext;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.component.executor.detection.MPFDetectionMain;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionDeadLetterProcessor;
import org.mitre.mpf.wfm.camel.routes.DlqRouteBuilder;
import org.mitre.mpf.wfm.enums.MpfEndpoints;
import org.mockito.Mock;

import javax.jms.*;

//@ContextConfiguration(locations = {"classpath:applicationContext.xml"})
//@RunWith(SpringJUnit4ClassRunner.class)
//@RunListener.ThreadSafe
public class TestDlqRouteBuilder {

    private CamelContext camelContext = new DefaultCamelContext();

    private ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(MPFDetectionMain.ACTIVEMQHOST);

    @Mock
    private DlqRouteBuilder mockDlqRouteBuilder;

    @Mock
    private DetectionDeadLetterProcessor mockDetectionDeadLetterProcessor;

    @Before
    public void init() throws Exception {
        // MockitoAnnotations.initMocks(this);

        camelContext = new DefaultCamelContext();
        JmsComponent activeMQComponent = ActiveMQComponent.jmsComponentAutoAcknowledge(connectionFactory);
        camelContext.addComponent("jms", activeMQComponent);


        DlqRouteBuilder dlqRouteBuilder = new DlqRouteBuilder();
        dlqRouteBuilder.setD(new DetectionDeadLetterProcessor()); // TODO
        dlqRouteBuilder.setContext(camelContext);
        camelContext.addRoutes(dlqRouteBuilder); // mockDlqRouteBuilder


        camelContext.start();
    }

    @After
    public void cleanup() throws Exception {
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    private void sendDlqMessage() throws JMSException {
        Connection connection = null;
        try {
            connection = connectionFactory.createConnection();
            connection.start();
            Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);

            BytesMessage message = session.createBytesMessage();
            message.writeBytes(DetectionProtobuf.DetectionRequest.getDefaultInstance().toByteArray());
            Destination replyTo = session.createQueue(StringUtils.replace(MpfEndpoints.COMPLETED_DETECTIONS, "jms:", ""));
            message.setJMSReplyTo(replyTo);
            // message.setStringProperty("dlqDeliveryFailureCause", "java.lang.Throwable: duplicate from store for queue://MPF.DETECTION_DUMMY_REQUEST");

            // Destination dlq = session.createQueue("DUMMY"); //StringUtils.replace(MpfEndpoints.DEAD_LETTER_QUEUE, "activemq:", ""));
            Destination dlq = session.createQueue(StringUtils.replace(MpfEndpoints.DEAD_LETTER_QUEUE, "activemq:", ""));

            MessageProducer messageProducer = session.createProducer(dlq);
            messageProducer.send(message);
            session.commit();
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    @Test
    public void testHandleDuplicateMessage() throws Exception {

        sendDlqMessage();

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
