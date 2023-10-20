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

import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.mitre.mpf.videooverlay.BoundingBox;
import org.mitre.mpf.videooverlay.BoundingBoxMap;
import org.mitre.mpf.videooverlay.BoundingBoxSource;
import org.mitre.mpf.videooverlay.BoundingBoxWriter;
import org.mitre.mpf.wfm.buffers.Markup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.InvalidProtocolBufferException;

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
        long startTimeMs = System.currentTimeMillis();

        Markup.MarkupRequest markupRequest;
        Markup.MarkupResponse.Builder responseBuilder;
        try {
            markupRequest = getMarkupRequest(message);
            responseBuilder = initializeResponse(message, markupRequest);
        }
        catch (Exception e) {
            LOG.error("Unable to parse the MarkupRequest protobuf due to: " + e, e);
            rollback();
            return;
        }

        try {
            LOG.info("Processing markup request. Media ID = {}, Type = {}, "
                    + "Number of detections = {}, Source URI = \"{}\", Destination URI = \"{}\".",
                    markupRequest.getMediaId(), markupRequest.getMediaType(),
                    markupRequest.getMapEntriesCount(),
                    markupRequest.getSourceUri(), markupRequest.getDestinationUri());
            onMarkupRequest(markupRequest);
            responseBuilder.setOutputFileUri(markupRequest.getDestinationUri());
        }
        catch (Exception e) {
            LOG.error("Markup failed due to: " + e, e);
            setErrorFields(responseBuilder, e);
        }
        responseBuilder.setTimeProcessing(System.currentTimeMillis() - startTimeMs);

        try {
            var response = responseBuilder.build();
            sendResponse(message, response);
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

    private Markup.MarkupRequest getMarkupRequest(Message message)
            throws JMSException, InvalidProtocolBufferException {
        // The caller will catch the possible ClassCastException and handle it.
        var bytesMessage = (BytesMessage) message;
        var protobytes = new byte[(int) bytesMessage.getBodyLength()];
        bytesMessage.readBytes(protobytes);
        return Markup.MarkupRequest.parseFrom(protobytes);
    }


    private static Markup.MarkupResponse.Builder initializeResponse(
            Message message, Markup.MarkupRequest request) {
        var result = Markup.MarkupResponse.newBuilder()
                .setMediaIndex(request.getMediaIndex())
                .setMediaId(request.getMediaId())
                .setRequestId(request.getRequestId())
                .setTaskIndex(request.getTaskIndex())
                .setActionIndex(request.getActionIndex())
                .setHasError(false);
        try {
            result.setRequestTimestamp(message.getJMSTimestamp());
        }
        catch (JMSException e) {
            // Ignored because setting the timestamp is not required.
        }
        return result;
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

    private void onMarkupRequest(Markup.MarkupRequest markupRequest) throws IOException {
        var destinationPath = Path.of(URI.create(markupRequest.getDestinationUri()));
        if (!Files.isWritable(destinationPath.getParent())) {
            throw new IllegalStateException(
                    "The target URI '%s' is not writable."
                    .formatted(markupRequest.getDestinationUri()));
        }

        var sourcePath = Path.of(URI.create(markupRequest.getSourceUri()));
        if (!Files.isReadable(sourcePath)) {
            throw new IllegalStateException(
                    "The target URI '%s' is not readable."
                    .formatted(markupRequest.getSourceUri()));
        }
        if (markupRequest.getMapEntriesList().isEmpty()) {
            Files.copy(sourcePath, destinationPath);
        }
        else {
            markup(markupRequest);
        }
    }

    private static void markup(Markup.MarkupRequest markupRequest) throws IOException {
        var writer = new BoundingBoxWriter();
        writer.setSourceMedium(URI.create(markupRequest.getSourceUri()));
        writer.setDestinationMedium(URI.create(markupRequest.getDestinationUri()));

        var mediaMetadata = markupRequest.getMediaMetadataList()
                .stream()
                .collect(toMap(Markup.MetadataMap::getKey, Markup.MetadataMap::getValue));
        writer.setMediaMetadata(mediaMetadata);

        var requestProperties = markupRequest.getMarkupPropertiesList()
                .stream()
                .collect(toMap(Markup.MarkupRequestPropertyMap::getKey,
                               Markup.MarkupRequestPropertyMap::getValue));
        writer.setRequestProperties(requestProperties);

        var map = new BoundingBoxMap();
        for (var boundingBoxMapEntry : markupRequest.getMapEntriesList()) {
            var srcBox = boundingBoxMapEntry.getBoundingBox();
            var label = srcBox.hasLabel()
                    ? Optional.of(srcBox.getLabel())
                    : Optional.<String>empty();
            var dstBox = new BoundingBox(
                    srcBox.getX(),
                    srcBox.getY(),
                    srcBox.getWidth(),
                    srcBox.getHeight(),
                    srcBox.getRotationDegrees(),
                    srcBox.getFlip(),
                    srcBox.getRed(),
                    srcBox.getGreen(),
                    srcBox.getBlue(),
                    BoundingBoxSource.valueOf(srcBox.getSource().toString()),
                    srcBox.getMoving(),
                    srcBox.getExemplar(),
                    label);
            map.putOnFrame(boundingBoxMapEntry.getFrameNumber(), dstBox);
        }

        writer.setBoundingBoxMap(map);
        if (markupRequest.getMediaType() == Markup.MediaType.IMAGE) {
            writer.markupImage();
        }
        else {
            writer.markupVideo();
        }
    }

    private static void setErrorFields(
            Markup.MarkupResponse.Builder responseBuilder, Exception exception) {
        responseBuilder.setHasError(true);
        responseBuilder.setErrorMessage("Markup failed due to: " + exception);
        var writer = new StringWriter();
        try (var pw = new PrintWriter(writer)) {
            exception.printStackTrace(pw);
        }
        responseBuilder.setErrorStackTrace(writer.toString());
    }


    private void sendResponse(Message requestMessage, Markup.MarkupResponse markupResponse)
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
        _messageProducer.send(requestMessage.getJMSReplyTo(), responseMessage);
        _session.commit();
    }
}
