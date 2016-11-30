/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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

package org.mitre.mpf.helloWorld;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQPrefetchPolicy;
import org.mitre.mpf.wfm.buffers.AlgorithmPropertyProtocolBuffer;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by mpf on 9/28/15.
 */
public class HelloMessenger implements MessageListener {

    private static final Logger LOG = LoggerFactory.getLogger(HelloMessenger.class);
    private static int ackMode = Session.AUTO_ACKNOWLEDGE;
    private static boolean transacted = true;
    private static Connection connection;
    private static Session session;

    public void createConnection(final String msgQueueName) throws JMSException {
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(HelloMain.ACTIVEMQHOST);
        ActiveMQPrefetchPolicy policy = new ActiveMQPrefetchPolicy();
        policy.setQueuePrefetch(1);
        policy.setTopicPrefetch(1);
        connectionFactory.setPrefetchPolicy(policy);
        connectionFactory.setCloseTimeout(1);
        connectionFactory.setOptimizeAcknowledge(true);
        connection = connectionFactory.createConnection();
        connection.start();
        session = connection.createSession(transacted, ackMode);
        Destination requestQueue = session.createQueue(msgQueueName);
        MessageConsumer requestConsumer = session.createConsumer(requestQueue);
        requestConsumer.setMessageListener(this);
    }

    // TODO: This code is very similar to the speech detector code.
    // We should use a dependency injection approach to enable better code reuse (which will result in better parity
    // with the Component Logic shared libraries on the C++ side).
    @Override
    public void onMessage(Message message) {

        byte[] requestBytesMessage;
        DetectionRequestInfo msgInfo;

        try {
            LOG.info("Detection request received with message length = " + ((BytesMessage) message).getBodyLength());

            requestBytesMessage = new byte[(int) ((BytesMessage) message).getBodyLength()];
            ((BytesMessage) message).readBytes(requestBytesMessage);

            Map<String, Object> headerProperties = copyMsgProperties(message);
            msgInfo = unpackMessage(requestBytesMessage);
            Destination out = message.getJMSReplyTo();

            if (msgInfo != null) {
                LOG.debug(requestBytesMessage.toString());
                LOG.info("requestId = " + msgInfo.requestId +
                        " dataUri = " + msgInfo.dataUri +
                        " dataType = " + msgInfo.dataType +
                        " size of algorithmProperties = " + msgInfo.algorithmProperties.size());
                byte[] responseBytes = packMessage(msgInfo);
                BytesMessage responseBytesMessage = null;
                try {
                    responseBytesMessage = session.createBytesMessage();
                    responseBytesMessage.writeBytes(responseBytes);
                    setMsgProperties(headerProperties, responseBytesMessage);
                    MessageProducer replyProducer = session.createProducer(out);
                    replyProducer.send(responseBytesMessage);
                    session.commit();
                    LOG.info("Detection response sent for request ID " + msgInfo.requestId);
                    LOG.debug(responseBytesMessage.toString());
                } catch (JMSException e) {
                    LOG.error("Failed to send detection response message due to Exception ", e);
                }

            } else {
                LOG.error("Could not parse contents of Detection Request message");
            }
        } catch (JMSException e) {
            LOG.error("Could not process detection request message due to Exception ", e);
        }

    }

    public static void shutdown() {
        try {
            session.close();
            connection.close();
        } catch (JMSException e) {
            // swallow exceptions since we're exiting anyway
        }
    }

    public DetectionRequestInfo unpackMessage(final byte[] requestContents) {

        DetectionProtobuf.DetectionRequest detectionRequest = null;
        DetectionRequestInfo inputs = null;

        try {
            detectionRequest = DetectionProtobuf.DetectionRequest.parseFrom(requestContents);

            String dataUri = detectionRequest.getDataUri();
            int dataType = detectionRequest.getDataType().ordinal();
            long requestId = detectionRequest.getRequestId();
            long mediaId = detectionRequest.getMediaId();
            String stageName = detectionRequest.getStageName();
            int stageIndex = detectionRequest.getStageIndex();
            String actionName = detectionRequest.getActionName();
            int actionIndex = detectionRequest.getActionIndex();

            Map<String, String> algorithmProperties = new HashMap<String, String>();
            for (int i = 0; i < detectionRequest.getAlgorithmPropertyList().size(); i++) {
                AlgorithmPropertyProtocolBuffer.AlgorithmProperty algProp = detectionRequest.getAlgorithmProperty(i);
                algorithmProperties.put(algProp.getPropertyName(), algProp.getPropertyValue());
            }

            inputs = new DetectionRequestInfo(
                    dataUri,
                    dataType,
                    algorithmProperties,
                    requestId,
                    mediaId,
                    stageName,
                    stageIndex,
                    actionName,
                    actionIndex);

        } catch (InvalidProtocolBufferException e) {
            LOG.error("Failed to parse the request protocol buffer due to Exception ", e);
            e.printStackTrace();
        }

        return inputs;

    }

    public byte[] packMessage(final DetectionRequestInfo inputs) {
        DetectionProtobuf.DetectionResponse.Builder detectionResponseBuilder = DetectionProtobuf.DetectionResponse.newBuilder()
                .setRequestId(inputs.requestId)
                .setDataType(DetectionProtobuf.DetectionResponse.DataType.values()[inputs.dataType])
                .setMediaId(inputs.mediaId)
                .setStageName(inputs.stageName)
                .setStageIndex(inputs.stageIndex)
                .setActionName(inputs.actionName)
                .setActionIndex(inputs.actionIndex);

        DetectionProtobuf.DetectionResponse.VideoResponse.Builder videoResponseBuilder = detectionResponseBuilder.addVideoResponsesBuilder();
        videoResponseBuilder.setDetectionType("HELLO");

        DetectionProtobuf.DetectionResponse.ImageLocation imageLocation = DetectionProtobuf.DetectionResponse.ImageLocation.newBuilder()
                .setHeight(0)
                .setWidth(0)
                .setYLeftUpper(0)
                .setXLeftUpper(0)
                .addDetectionProperties(DetectionProtobuf.PropertyMap.newBuilder().setKey("METADATA").setValue("Hello World!"))
                .build();

        DetectionProtobuf.DetectionResponse.VideoResponse.VideoTrack.FrameLocationMap frameLocation =
                DetectionProtobuf.DetectionResponse.VideoResponse.VideoTrack.FrameLocationMap.newBuilder()
                        .setFrame(0)
                        .setImageLocation(imageLocation)
                        .build();

        DetectionProtobuf.DetectionResponse.VideoResponse.VideoTrack track= videoResponseBuilder.addVideoTracksBuilder()
                .setStartFrame(0)
                .setStopFrame(1)
                .addFrameLocations(frameLocation)
                .build();

        byte[] responseContents = detectionResponseBuilder
                .build()
                .toByteArray();

        return responseContents;
    }

    public static Map<String, Object> copyMsgProperties(Message src) throws JMSException {
        Map<String, Object> objectProperties = new HashMap<String, Object>();
        @SuppressWarnings("rawtypes")
        Enumeration e = src.getPropertyNames();
        while (e.hasMoreElements()) {
            String k = (String) e.nextElement();
            objectProperties.put(k, src.getObjectProperty(k));
        }
        return objectProperties;
    }

    public static void setMsgProperties(Map<String, Object> properties, Message dsc) throws JMSException {
        for (Map.Entry<String, Object> e : properties.entrySet()) {
            dsc.setObjectProperty(e.getKey(), e.getValue());
        }
    }

    class DetectionRequestInfo {

        public final String dataUri;
        public final int dataType;
        public final Map<String, String> algorithmProperties;
        public final long requestId;
        public final long mediaId;
        public final String stageName;
        public final int stageIndex;
        public final String actionName;
        public final int actionIndex;

        DetectionRequestInfo(
                final String dataUri,
                final int dataType,
                final Map<String, String> algorithmProperties,
                final long requestId,
                final long mediaId,
                final String stageName,
                final int stageIndex,
                final String actionName,
                final int actionIndex
        ) {
            this.dataUri = dataUri;
            this.dataType = dataType;
            this.requestId = requestId;
            this.mediaId = mediaId;
            this.stageName = stageName;
            this.stageIndex = stageIndex;
            this.actionName = actionName;
            this.actionIndex = actionIndex;

            // TODO: may need to deep copy map depending on the types of properties it contains
            if(algorithmProperties == null) {
                this.algorithmProperties = new HashMap<String, String>(); // Treat a null properties map as an empty map.
            } else {
                this.algorithmProperties = new HashMap<String, String>(algorithmProperties);
            }
        }

    }

}
