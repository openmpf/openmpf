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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.mitre.mpf.videooverlay.BoundingBoxWriter;
import org.mitre.mpf.wfm.buffers.Markup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MarkupRequestConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(MarkupRequestConsumer.class);

    private final Session _session;

    private final MessageProducer _messageProducer;

    static {
        LOG.debug("Loading JNI library.");
        System.loadLibrary("mpfopencvjni");
    }

    public MarkupRequestConsumer(Session session) throws JMSException {
        _session = session;
        _messageProducer = session.createProducer(null);
    }


    public void onMessage(Message message) {
        var startTime = Instant.now();

        byte[] protobytes;
        Markup.MarkupRequest markupRequest;
        try {
            protobytes = getMessageBody(message);
            markupRequest = Markup.MarkupRequest.parseFrom(protobytes);
        }
        catch (Exception e) {
            LOG.error("Unable to parse the MarkupRequest protobuf due to: " + e, e);
            rollback();
            return;
        }

        var responseBuilder = Markup.MarkupResponse.newBuilder()
                .setMediaId(markupRequest.getMediaId());
        try {
            int numBoxes = markupRequest.getBoundingBoxesMap()
                    .values()
                    .stream()
                    .mapToInt(bbl -> bbl.getBoundingBoxesCount())
                    .sum();
            LOG.info("Processing markup request. Media ID = {}, Type = {}, "
                    + "Number of detections = {}, Source path = \"{}\", Destination path = \"{}\".",
                    markupRequest.getMediaId(), markupRequest.getMediaType(), numBoxes,
                    markupRequest.getSourcePath(), markupRequest.getDestinationPath());
            onMarkupRequest(markupRequest, protobytes);
            responseBuilder.setOutputFilePath(markupRequest.getDestinationPath());
        }
        catch (Exception e) {
            LOG.error("Markup failed due to: " + e, e);
            responseBuilder.setHasError(true);
            responseBuilder.setErrorMessage("Markup failed due to: " + e);
        }

        try {
            var response = responseBuilder.build();
            sendResponse(message, response, startTime);
            if (!response.getHasError()) {
                LOG.info("Markup request for media {} completed successfully.",
                        markupRequest.getMediaId());
            }
        }
        catch (Exception e) {
            LOG.error("Sending markup response failed due to: " + e, e);
            rollback();
        }
    }

    private static byte[] getMessageBody(Message message) throws JMSException {
        var bytesMessage = (BytesMessage) message;
        var protobytes = new byte[(int) bytesMessage.getBodyLength()];
        bytesMessage.readBytes(protobytes);
        return protobytes;
    }



    private void rollback() {
        try {
            // Rollback is used in cases where it is not possible to create a
            // Markup.MarkupResponse protobuf message. The invalid request messages will end up
            // in a dead letter queue after redelivery fails.
            _session.rollback();
        }
        catch (JMSException e) {
            throw new IllegalStateException("Rollback failed due to: " + e, e);
        }
    }

    private void onMarkupRequest(Markup.MarkupRequest markupRequest, byte[] protobytes) throws IOException {
        var destinationPath = Path.of(markupRequest.getDestinationPath());
        if (!Files.isWritable(destinationPath.getParent())) {
            throw new IllegalStateException(
                    "The target path '%s' is not writable."
                    .formatted(markupRequest.getDestinationPath()));
        }

        var sourcePath = Path.of(markupRequest.getSourcePath());
        if (!Files.isReadable(sourcePath)) {
            throw new IllegalStateException(
                    "The target path '%s' is not readable."
                    .formatted(markupRequest.getSourcePath()));
        }
        boolean boxesPresent = markupRequest.getBoundingBoxesMap().values()
            .stream()
            .anyMatch(bbl -> bbl.getBoundingBoxesCount() > 0);
        if (boxesPresent) {
            BoundingBoxWriter.markup(protobytes);
        }
        else {
            Files.copy(sourcePath, destinationPath);
        }
    }


    private void sendResponse(
            Message requestMessage, Markup.MarkupResponse markupResponse, Instant startTime)
                throws JMSException {
        var responseMessage = _session.createBytesMessage();
        responseMessage.setJMSPriority(requestMessage.getJMSPriority());
        responseMessage.writeBytes(markupResponse.toByteArray());

        var propertyNames = requestMessage.getPropertyNames();
        while (propertyNames.hasMoreElements()) {
            if (propertyNames.nextElement() instanceof String propName) {
                var propValue = requestMessage.getObjectProperty(propName);
                responseMessage.setObjectProperty(propName, propValue);
            }
        }
        setProcessingTime(responseMessage, startTime);
        _messageProducer.send(requestMessage.getJMSReplyTo(), responseMessage);
        _session.commit();
    }


    private static final double NANOS_PER_MS = Duration.ofMillis(1).toNanos();

	private static void setProcessingTime(Message message, Instant startTime)
            throws JMSException {
		var duration = Duration.between(startTime, Instant.now());
		// Manually convert nanoseconds to milliseconds so that the value can be rounded.
		// Duration.toMillis() always rounds down.
		long millis = Math.round(duration.toNanos() / NANOS_PER_MS);
		message.setLongProperty("ProcessingTime", millis);
	}
}
