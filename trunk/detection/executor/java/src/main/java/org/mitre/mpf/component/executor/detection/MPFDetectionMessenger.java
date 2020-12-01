/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2020 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2020 The MITRE Corporation                                       *
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

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.InvalidProtocolBufferException;
import org.mitre.mpf.component.api.detection.*;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MPFDetectionMessenger {

    private static final Logger LOG = LoggerFactory.getLogger(MPFDetectionMessenger.class);
	private static final String usePreprocessorPropertyName = "USE_PREPROCESSOR";

	private static final ImmutableMap<String, String> environmentJobProperties
			= getEnvironmentJobProperties();

	private final MPFDetectionComponentInterface component;
	private final Session session;

    public MPFDetectionMessenger(MPFDetectionComponentInterface component, Session session) {
        this.component = component;
        this.session = session;
    }

    public void onMessage(Message message) {
		try {
            LOG.info("Detection request received with message length = " + ((BytesMessage) message).getBodyLength());

			byte[] requestBytesMessage = new byte[(int) ((BytesMessage) message).getBodyLength()];
            ((BytesMessage) message).readBytes(requestBytesMessage);
			Map<String, Object> headerProperties = ProtoUtils.copyMsgProperties(message);

			MPFDetectionBuffer detectionBuffer = new MPFDetectionBuffer(requestBytesMessage);
			MPFMessageMetadata msgMetadata = detectionBuffer.getMessageMetadata(message);
			msgMetadata.getAlgorithmProperties().putAll(environmentJobProperties);
            Destination out = message.getJMSReplyTo();

            if (msgMetadata != null) {

                LOG.debug("requestId = " + msgMetadata.getRequestId() +
                          " correlationId = " + msgMetadata.getCorrelationId() +
                          " breadcrumbId = " + msgMetadata.getBreadcrumbId() +
                          " splitSize = " + msgMetadata.getSplitSize() +
                          " jobId = " + msgMetadata.getJobId() +
                          " dataUri = " + msgMetadata.getDataUri() +
                          " mediaId = " + msgMetadata.getMediaId() +
                          " taskName = " + msgMetadata.getTaskName() +
                          " taskIndex = " + msgMetadata.getTaskIndex() +
                          " actionName = " + msgMetadata.getActionName() +
                          " actionIndex = " + msgMetadata.getActionIndex() +
                          " dataType = " + msgMetadata.getDataType() +
                          " size of algorithmProperties = " + msgMetadata.getAlgorithmProperties().size() +
						  " size of mediaProperties = " + msgMetadata.getMediaProperties().size());

                LOG.info("Detection request received with job ID " + msgMetadata.getJobId() +
                         " for media file " + msgMetadata.getDataUri());

				String detectionType = component.getDetectionType();

				if(component.supports(msgMetadata.getDataType())) {

					byte[] responseBytes = null;

                    // TODO: Include the exception message in the response message for more detail.
                    if (MPFDataType.AUDIO == msgMetadata.getDataType()) {
                        MPFDetectionAudioRequest audioRequest = detectionBuffer.getAudioRequest();
                        try {
                            List<MPFAudioTrack> tracks = new ArrayList<>();
                            tracks = component.getDetections(new MPFAudioJob(msgMetadata.getJobName(),
                                                                            msgMetadata.getDataUri(),
                                                                            msgMetadata.getAlgorithmProperties(),
                                                                            msgMetadata.getMediaProperties(),
                                                                            audioRequest.getStartTime(),
                                                                            audioRequest.getStopTime(),
                                                                            audioRequest.getFeedForwardTrack()));
                            responseBytes = detectionBuffer.createAudioResponseMessage(
                            		msgMetadata,
									audioRequest.getStartTime(), audioRequest.getStopTime(),
									detectionType, tracks, MPFDetectionError.MPF_DETECTION_SUCCESS,
		                            "");
                        } catch (MPFComponentDetectionError e) {
                            responseBytes = detectionBuffer.createAudioResponseMessage(
                            		msgMetadata,
									audioRequest.getStartTime(), audioRequest.getStopTime(),
									detectionType, Collections.<MPFAudioTrack>emptyList(), e.getDetectionError(),
		                            e.getMessage());
                        }
                    } else if (MPFDataType.IMAGE == msgMetadata.getDataType()) {
                        MPFDetectionImageRequest imageRequest = detectionBuffer.getImageRequest();
                        List<MPFImageLocation> locations = new ArrayList<>();
                        try {
                            locations = component.getDetections(new MPFImageJob(msgMetadata.getJobName(),
                                                                               msgMetadata.getDataUri(),
                                                                               msgMetadata.getAlgorithmProperties(),
                                                                               msgMetadata.getMediaProperties(),
                                                                               imageRequest.getFeedForwardLocation()));
                            responseBytes = detectionBuffer.createImageResponseMessage(
                            		msgMetadata, detectionType, locations, MPFDetectionError.MPF_DETECTION_SUCCESS,
		                            "");
                        } catch (MPFComponentDetectionError e) {
                            responseBytes = detectionBuffer.createImageResponseMessage(
                            		msgMetadata, detectionType, locations, e.getDetectionError(), e.getMessage());
                        }

                    } else if (MPFDataType.VIDEO == msgMetadata.getDataType()) {
                        MPFDetectionVideoRequest videoRequest = detectionBuffer.getVideoRequest();
                        List<MPFVideoTrack> tracks = new ArrayList<>();
                        try {
                            tracks = component.getDetections(new MPFVideoJob(msgMetadata.getJobName(),
                                                                            msgMetadata.getDataUri(),
                                                                            msgMetadata.getAlgorithmProperties(),
                                                                            msgMetadata.getMediaProperties(),
                                                                            videoRequest.getStartFrame(),
                                                                            videoRequest.getStopFrame(),
                                                                            videoRequest.getFeedForwardTrack()));
                            responseBytes = detectionBuffer.createVideoResponseMessage(
                            		msgMetadata,
									videoRequest.getStartFrame(), videoRequest.getStopFrame(),
									detectionType, tracks, MPFDetectionError.MPF_DETECTION_SUCCESS,
		                            "");
                        } catch (MPFComponentDetectionError e) {
                            responseBytes = detectionBuffer.createVideoResponseMessage(
                            		msgMetadata,
									videoRequest.getStartFrame(), videoRequest.getStopFrame(),
									detectionType, tracks, e.getDetectionError(), e.getMessage());
                        }
                    } else if (MPFDataType.UNKNOWN == msgMetadata.getDataType()) {
						MPFDetectionGenericRequest genericRequest = detectionBuffer.getGenericRequest();
						try {
							List<MPFGenericTrack> tracks = new ArrayList<>();
							tracks = component.getDetections(new MPFGenericJob(msgMetadata.getJobName(),
									msgMetadata.getDataUri(),
									msgMetadata.getAlgorithmProperties(),
									msgMetadata.getMediaProperties(),
									genericRequest.getFeedForwardTrack()));
							responseBytes = detectionBuffer.createGenericResponseMessage(
									msgMetadata, detectionType, tracks, MPFDetectionError.MPF_DETECTION_SUCCESS,
									"");
						} catch (MPFComponentDetectionError e) {
							responseBytes = detectionBuffer.createGenericResponseMessage(
									msgMetadata, detectionType, Collections.<MPFGenericTrack>emptyList(), e.getDetectionError(),
									e.getMessage());
						}
					}
                    // for debugging purposes
                    LOG.debug("Detection results for file " + msgMetadata.getDataUri() + ":\n" + responseBytes.toString());

                    BytesMessage responseBytesMessage;
                    try {
                        responseBytesMessage = session.createBytesMessage();
                        responseBytesMessage.writeBytes(responseBytes);
                        ProtoUtils.setMsgProperties(headerProperties, responseBytesMessage);
	                    MessageProducer replyProducer = session.createProducer(out);
                        replyProducer.send(responseBytesMessage);
                        session.commit();
                        LOG.info("Detection response sent for job ID {}", msgMetadata.getJobId());
                        LOG.debug(responseBytesMessage.toString());
                    } catch (JMSException e) {
                        LOG.error("Failed to send detection response message due to exception: " + e.getMessage(), e);
                    }

                } else {
					LOG.error("Detection cannot be performed on the " + msgMetadata.getDataType() + " data type");

					DetectionProtobuf.DetectionResponse.Builder responseBuilder = DetectionProtobuf.DetectionResponse.newBuilder();
					if (actAsPreprocessor(msgMetadata)) {
						buildPreprocessorResponse(msgMetadata, responseBuilder);
					} else {
						// Indicate that non-video and non-audio data (such as images) are not supported.
						buildUnsupportedMediaTypeResponse(msgMetadata, responseBuilder);
					}

					buildAndSend(responseBuilder.build(), message.getJMSReplyTo(), headerProperties);
				}

            } else {
				// TODO: Send error message.
                LOG.error("Could not parse contents of Detection Request message");
            }
        } catch (InvalidProtocolBufferException | JMSException e) {
			// TODO: Send error message.
            LOG.error("Could not process detection request message due to Exception ", e);
        }
    }

	private void buildAndSend(DetectionProtobuf.DetectionResponse detectionResponse, Destination destination, Map<String, Object> headers) {
		try {
			// Create a new response message and re-use the incoming headers.
			BytesMessage response = session.createBytesMessage();
			ProtoUtils.setMsgProperties(headers, response);

			// Set the body of the message.
			response.writeBytes(detectionResponse.toByteArray());

			// Create a transacted producer, send the message, and close the producer.
			MessageProducer producer = session.createProducer(destination);
			producer.send(response);
			session.commit();
			producer.close();

			// Record the success.
			LOG.debug("[Request #{}] Built and sent response. Error: {}.", detectionResponse.getRequestId(), detectionResponse.getError());
		} catch(Exception e) {
			// Record the failure. This is likely irrecoverable.
			LOG.error("[Request #{}] Failed to send the response due to an exception.", detectionResponse == null ? Long.MIN_VALUE : detectionResponse.getRequestId(), e);
		}
	}

	private boolean actAsPreprocessor(MPFMessageMetadata msgMetadata) {
		if(msgMetadata.getAlgorithmProperties().containsKey(usePreprocessorPropertyName)) {
			try {
				int value = Integer.valueOf(msgMetadata.getAlgorithmProperties().get(usePreprocessorPropertyName).toString());
				return value != 0; // Act as a preprocessor any time the parsed value is non-zero.
			} catch(NumberFormatException nfe) {
				LOG.warn("The property '{}' with value '{}' could not be parsed as an integer. A value of 0 has been assumed.",
						usePreprocessorPropertyName, msgMetadata.getAlgorithmProperties().get(usePreprocessorPropertyName));
				return false; // By default, do not act as a preprocessor.
			}
		}
		return false; // By default, do not act as a preprocessor.
	}

    private void buildPreprocessorResponse(MPFMessageMetadata msgMetadata, DetectionProtobuf.DetectionResponse.Builder detectionResponseBuilder) {
		detectionResponseBuilder.setDataType(MPFDetectionBuffer.translateMPFDetectionDataType(msgMetadata.getDataType()));
		detectionResponseBuilder.setRequestId(msgMetadata.getRequestId());

		detectionResponseBuilder.setMediaId(msgMetadata.getMediaId());
		detectionResponseBuilder.setTaskName(msgMetadata.getTaskName());
		detectionResponseBuilder.setTaskIndex(msgMetadata.getTaskIndex());
		detectionResponseBuilder.setActionName(msgMetadata.getActionName());
		detectionResponseBuilder.setActionIndex(msgMetadata.getActionIndex());

        // TODO: Refactor this code into a more generic DetectionMessenger and handle IMAGE and VIDEO data types.

		// try {
			// BufferedImage image = ImageIO.read(new File(detectionRequestInfo.dataUri));

			detectionResponseBuilder.addAudioResponsesBuilder().addAudioTracksBuilder()
					.setStartTime(0)
					.setStopTime(0)
					.setConfidence(-1f);

			/*
			detectionResponseBuilder.addAllObjectTracks(
					Arrays.asList(
							DetectionProtobuf.DetectionResponse.AudioTrack.newBuilder()
									 // .setObjectType("SPEECH")
									.setStartTime(0)
									.setStopTime(0)
									.addAllObjects(Arrays.asList(DetectionProtobuf.DetectionResponse.ObjectLocation.newBuilder()
											.setConfidence(-1f)
											.setFrameNumber(0)
											.setHeight(image.getHeight())
											.setWidth(image.getWidth())
											.setMetadata("")
											.setXLeftUpper(0)
											.setYLeftUpper(0).build()))
									.build()));
			*/

		/*
		} catch (IOException ioe) {
			LOG.warn("Failed to read the input URI '{}'. This could be due to an inaccessible file, an unsupported image format, or a corrupt image.", detectionRequestInfo.dataUri);
			detectionResponseBuilder.setError(DetectionProtobuf.DetectionError.COULD_NOT_READ_DATAFILE);
		}
		*/
	}

    private void buildUnsupportedMediaTypeResponse(MPFMessageMetadata msgMetadata, DetectionProtobuf.DetectionResponse.Builder detectionResponseBuilder) {
		detectionResponseBuilder.setDataType(MPFDetectionBuffer.translateMPFDetectionDataType(msgMetadata.getDataType()));
		detectionResponseBuilder.setRequestId(msgMetadata.getRequestId());
		detectionResponseBuilder.setError(DetectionProtobuf.DetectionError.UNSUPPORTED_DATA_TYPE);
		detectionResponseBuilder.setMediaId(msgMetadata.getMediaId());
		detectionResponseBuilder.setTaskName(msgMetadata.getTaskName());
		detectionResponseBuilder.setTaskIndex(msgMetadata.getTaskIndex());
		detectionResponseBuilder.setActionName(msgMetadata.getActionName());
		detectionResponseBuilder.setActionIndex(msgMetadata.getActionIndex());
	}

	private static ImmutableMap<String, String> getEnvironmentJobProperties() {
    	return getEnvironmentJobProperties(System.getenv());
	}

	public static ImmutableMap<String, String> getEnvironmentJobProperties(
			Map<String, String> environment) {
        var propertyPrefix = "MPF_PROP_";
        return environment
		        .entrySet()
		        .stream()
		        .filter(e -> e.getKey().length() > propertyPrefix.length()
				        && e.getKey().startsWith(propertyPrefix))
		        .collect(ImmutableMap.toImmutableMap(
		        		e -> e.getKey().substring(propertyPrefix.length()),
				        Map.Entry::getValue));
	}
}
