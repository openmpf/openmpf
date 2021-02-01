/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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

import com.google.common.base.Stopwatch;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.mitre.mpf.videooverlay.BoundingBox;
import org.mitre.mpf.videooverlay.BoundingBoxMap;
import org.mitre.mpf.videooverlay.BoundingBoxWriter;
import org.mitre.mpf.wfm.buffers.Markup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.jms.*;
import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service(MarkupRequestConsumer.REF)
public class MarkupRequestConsumer implements MessageListener {
    public static final String REF = "markupRequestConsumer";
    private static final Logger log = LoggerFactory.getLogger(MarkupRequestConsumer.class);

    static {
        log.debug("Loading JNI library.");
        System.loadLibrary("mpfopencvjni");
    }

    @PostConstruct
    public void init() {
        log.info("Markup initialization complete");
    }


    @Autowired
    @Qualifier("markupResponseTemplate")
    private JmsTemplate markupResponseTemplate;

    private Markup.MarkupResponse.Builder initializeResponse(Markup.MarkupRequest request) {
        Markup.MarkupResponse.Builder responseBuilder = Markup.MarkupResponse.newBuilder()
                .setMediaIndex(request.getMediaIndex())
                .setMediaId(request.getMediaId())
                .setRequestId(request.getRequestId())
                .setTaskIndex(request.getTaskIndex())
                .setActionIndex(request.getActionIndex())
                .setHasError(false);
        return responseBuilder;
    }

    private void finishWithError(Markup.MarkupResponse.Builder markupResponseBuilder, Exception exception) {
        markupResponseBuilder.setHasError(true);
        markupResponseBuilder.setErrorMessage(exception.getMessage());

        StringWriter writer = new StringWriter(5120);
        exception.printStackTrace(new PrintWriter(writer));
        markupResponseBuilder.setErrorStackTrace(writer.toString());
    }

    private boolean markup(Markup.MarkupRequest markupRequest) throws Exception {
        log.info("[Markup Request #{}] Source: '{}' Destination: '{}'.",
                markupRequest.getRequestId(), markupRequest.getSourceUri(), markupRequest.getDestinationUri());

        BoundingBoxWriter writer = new BoundingBoxWriter();
        writer.setSourceMedium(URI.create(markupRequest.getSourceUri()));
        writer.setDestinationMedium(URI.create(markupRequest.getDestinationUri()));

        Map mediaMetadata = new HashMap<String, String>();
        markupRequest.getMediaMetadataList().stream().forEach(e -> mediaMetadata.put(e.getKey(), e.getValue()) );
        writer.setMediaMetadata(mediaMetadata);

        Map requestProperties = new HashMap<String, String>();
        markupRequest.getMarkupPropertiesList().stream().forEach(e -> requestProperties.put(e.getKey(), e.getValue()) );
        writer.setMediaMetadata(requestProperties);

        BoundingBoxMap map = new BoundingBoxMap();
        int boxesAdded = 0;
        for(Markup.BoundingBoxMapEntry boundingBoxMapEntry : markupRequest.getMapEntriesList()) {
            Optional<String> classification = boundingBoxMapEntry.getBoundingBox().hasClassification() ?
                    Optional.of(boundingBoxMapEntry.getBoundingBox().getClassification()) : Optional.empty();
            BoundingBox boundingBox = new BoundingBox(
                    boundingBoxMapEntry.getBoundingBox().getX(),
                    boundingBoxMapEntry.getBoundingBox().getY(),
                    boundingBoxMapEntry.getBoundingBox().getWidth(),
                    boundingBoxMapEntry.getBoundingBox().getHeight(),
                    boundingBoxMapEntry.getBoundingBox().getRotationDegrees(),
                    boundingBoxMapEntry.getBoundingBox().getFlip(),
                    boundingBoxMapEntry.getBoundingBox().getRed(),
                    boundingBoxMapEntry.getBoundingBox().getGreen(),
                    boundingBoxMapEntry.getBoundingBox().getBlue(),
                    boundingBoxMapEntry.getBoundingBox().getAnimated(),
                    boundingBoxMapEntry.getBoundingBox().getExemplar(),
                    boundingBoxMapEntry.getBoundingBox().getConfidence(),
                    classification);
            map.putOnFrame(boundingBoxMapEntry.getFrameNumber(), boundingBox);
            boxesAdded++;
        }

        log.info("[Markup Request #{}] Marking up {} detections on '{}'.",
                markupRequest.getRequestId(), markupRequest.getMapEntriesCount(), markupRequest.getDestinationUri());

        if(boxesAdded > 0) {
            writer.setBoundingBoxMap(map);
            if (markupRequest.getMediaType() == Markup.MediaType.IMAGE) {
                writer.markupImage();
            } else {
                writer.markupVideo();
            }
        }

        return boxesAdded > 0;
    }

    public void onMessage(Message message) {
        Stopwatch stopwatch = Stopwatch.createStarted();

        if(message == null) {
            log.warn("Received a null JMS message. No action will be taken.");
            return;
        }

        if(!(message instanceof BytesMessage)) {
            log.warn("Received a JMS message, but it was not of the correct type. No action will be taken.");
            return;
        }

        try {
            log.info("Received JMS message. Type = {}. JMS Message ID = {}. JMS Correlation ID = {}.",
                    message.getClass().getName(), message.getJMSMessageID(), message.getJMSCorrelationID());

            final Map<String, Object> requestHeaders = new HashMap<String, Object>();
            Enumeration<String> properties = message.getPropertyNames();

            String propertyName = null;
            while(properties.hasMoreElements()) {
                propertyName = properties.nextElement();
                requestHeaders.put(propertyName, message.getObjectProperty(propertyName));
            }

            byte[] messageBytes = new byte[(int)(((BytesMessage)message).getBodyLength())];
            ((BytesMessage)message).readBytes(messageBytes);
            Markup.MarkupRequest markupRequest = Markup.MarkupRequest.parseFrom(messageBytes);
            Markup.MarkupResponse.Builder markupResponseBuilder = initializeResponse(markupRequest);
            markupResponseBuilder.setRequestTimestamp(message.getJMSTimestamp());

            log.debug("Processing markup request. Media Index = {}. Media ID = {} (type = {}). Request ID = {}.",
                    markupRequest.getMediaIndex(), markupRequest.getMediaId(), markupRequest.getMediaType(),
                    markupRequest.getRequestId());

	        try {
		        if (!new File(URI.create(markupRequest.getDestinationUri())).canWrite()) {
			        throw new Exception();
		        }
	        } catch (Exception exception) {
		        markupResponseBuilder.setHasError(true);
		        markupResponseBuilder.setErrorMessage(String.format("The target URI '%s' is not writable.",
                        markupRequest.getDestinationUri()));
	        }

	        try {
		        if (!new File(URI.create(markupRequest.getSourceUri())).canRead()) {
			        throw new Exception();
		        }
	        } catch (Exception exception) {
		        markupResponseBuilder.setHasError(true);
		        markupResponseBuilder.setErrorMessage(String.format("The source URI '%s' is not readable.",
                        markupRequest.getSourceUri()));
	        }

	        if(!markupResponseBuilder.getHasError()) {
		        if (markupRequest.getMapEntriesCount() == 0) {
			        try {
                        String sourceUri = markupRequest.getSourceUri();
                        String sourceExt = FilenameUtils.getExtension(sourceUri);
			            String destinationUri = markupRequest.getDestinationUri();
			            String destinationExt = FilenameUtils.getExtension(destinationUri);
			            if (destinationExt.isEmpty() && !sourceExt.isEmpty()) {
                            destinationUri += "." + sourceExt;
                        }
				        FileUtils.copyFile(new File(URI.create(sourceUri)), new File(URI.create(destinationUri)));
				        markupResponseBuilder.setOutputFileUri(destinationUri);
			        } catch (Exception exception) {
				        log.error("Failed to mark up the file '{}' because of an exception.",
                                                markupRequest.getSourceUri(), exception);
				        finishWithError(markupResponseBuilder, exception);
			        }
		        } else if (markupRequest.getMediaType() == Markup.MediaType.IMAGE) {
			        try {
				        if (markup(markupRequest)) {
					        markupResponseBuilder.setOutputFileUri(markupRequest.getDestinationUri());
				        } else {
					        markupResponseBuilder.setOutputFileUri(markupRequest.getSourceUri());
				        }
			        } catch (Exception exception) {
				        log.error("Failed to mark up the image '{}' because of an exception.",
                                                markupRequest.getSourceUri(), exception);
				        finishWithError(markupResponseBuilder, exception);
			        }
		        } else {
			        try {
				        if (markup(markupRequest)) {
					        markupResponseBuilder.setOutputFileUri(markupRequest.getDestinationUri());
				        } else {
					        markupResponseBuilder.setOutputFileUri(markupRequest.getSourceUri());
				        }
			        } catch (Exception exception) {
				        log.error("Failed to mark up the video '{}' because of an exception.",
                                                markupRequest.getSourceUri(), exception);
				        finishWithError(markupResponseBuilder, exception);
			        }
		        }
	        }

            stopwatch.stop();
            markupResponseBuilder.setTimeProcessing(stopwatch.elapsed(TimeUnit.MILLISECONDS));
            final Markup.MarkupResponse markupResponse = markupResponseBuilder.build();

	    log.info("Returning response for Media {}. Error: {}.",
                    markupResponse.getMediaId(), markupResponse.getHasError());
            markupResponseTemplate.setSessionTransacted(true);
            markupResponseTemplate.setDefaultDestination(message.getJMSReplyTo());
            markupResponseTemplate.send(new MessageCreator() {
                public Message createMessage(Session session) throws JMSException {
                    BytesMessage bytesMessage = session.createBytesMessage();
                    bytesMessage.writeBytes(markupResponse.toByteArray());
                    for (Map.Entry<String, Object> entry : requestHeaders.entrySet()) {
                        bytesMessage.setObjectProperty(entry.getKey(), entry.getValue());
                    }
                    return bytesMessage;
                }
            });

        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
}
