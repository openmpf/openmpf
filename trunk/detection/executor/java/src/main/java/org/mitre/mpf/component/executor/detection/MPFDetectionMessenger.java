/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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
import org.mitre.mpf.component.api.detection.*;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.lang.IllegalStateException;
import java.time.Duration;
import java.time.Instant;

public class MPFDetectionMessenger {

    private static final Logger LOG = LoggerFactory.getLogger(MPFDetectionMessenger.class);
	private static final String usePreprocessorPropertyName = "USE_PREPROCESSOR";

	private static final ImmutableMap<String, String> environmentJobProperties
			= getEnvironmentJobProperties();

	private final MPFDetectionComponentInterface component;
	private final Session session;
	private final MessageProducer replyProducer;

    public MPFDetectionMessenger(MPFDetectionComponentInterface component, Session session) throws JMSException {
        this.component = component;
        this.session = session;
		this.replyProducer = session.createProducer(null);
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

			LOG.debug(" correlationId = " + msgMetadata.getCorrelationId() +
						" breadcrumbId = " + msgMetadata.getBreadcrumbId() +
						" splitSize = " + msgMetadata.getSplitSize() +
						" jobId = " + msgMetadata.getJobId() +
						" mediaPath = " + msgMetadata.getMediaPath() +
						" mediaId = " + msgMetadata.getMediaId() +
						" taskIndex = " + msgMetadata.getTaskIndex() +
						" actionIndex = " + msgMetadata.getActionIndex() +
						" dataType = " + msgMetadata.getDataType() +
						" size of algorithmProperties = " + msgMetadata.getAlgorithmProperties().size() +
						" size of mediaProperties = " + msgMetadata.getMediaProperties().size());

			LOG.info("Detection request received with job ID " + msgMetadata.getJobId() +
						" for media file " + msgMetadata.getMediaPath());

			var startTime = Instant.now();
			if(component.supports(msgMetadata.getDataType())) {

				byte[] responseBytes = null;

				if (MPFDataType.AUDIO == msgMetadata.getDataType()) {
					MPFDetectionAudioRequest audioRequest = detectionBuffer.getAudioRequest();
					try {
						List<MPFAudioTrack> tracks = new ArrayList<>();
						tracks = component.getDetections(new MPFAudioJob(msgMetadata.getJobName(),
																		msgMetadata.getMediaPath(),
																		msgMetadata.getAlgorithmProperties(),
																		msgMetadata.getMediaProperties(),
																		audioRequest.getStartTime(),
																		audioRequest.getStopTime(),
																		audioRequest.getFeedForwardTrack()));
						responseBytes = detectionBuffer.createAudioResponseMessage(
								msgMetadata,
								audioRequest.getStartTime(), audioRequest.getStopTime(),
								tracks, MPFDetectionError.MPF_DETECTION_SUCCESS,
								"");
					} catch (MPFComponentDetectionError e) {
						responseBytes = detectionBuffer.createAudioResponseMessage(
								msgMetadata,
								audioRequest.getStartTime(), audioRequest.getStopTime(),
								Collections.<MPFAudioTrack>emptyList(), e.getDetectionError(),
								e.getMessage());
					}
				} else if (MPFDataType.IMAGE == msgMetadata.getDataType()) {
					MPFDetectionImageRequest imageRequest = detectionBuffer.getImageRequest();
					List<MPFImageLocation> locations = new ArrayList<>();
					try {
						locations = component.getDetections(new MPFImageJob(msgMetadata.getJobName(),
																			msgMetadata.getMediaPath(),
																			msgMetadata.getAlgorithmProperties(),
																			msgMetadata.getMediaProperties(),
																			imageRequest.getFeedForwardLocation()));
						responseBytes = detectionBuffer.createImageResponseMessage(
								msgMetadata, locations, MPFDetectionError.MPF_DETECTION_SUCCESS,
								"");
					} catch (MPFComponentDetectionError e) {
						responseBytes = detectionBuffer.createImageResponseMessage(
								msgMetadata, locations, e.getDetectionError(), e.getMessage());
					}

				} else if (MPFDataType.VIDEO == msgMetadata.getDataType()) {
					MPFDetectionVideoRequest videoRequest = detectionBuffer.getVideoRequest();
					List<MPFVideoTrack> tracks = new ArrayList<>();
					try {
						tracks = component.getDetections(new MPFVideoJob(msgMetadata.getJobName(),
																		msgMetadata.getMediaPath(),
																		msgMetadata.getAlgorithmProperties(),
																		msgMetadata.getMediaProperties(),
																		videoRequest.getStartFrame(),
																		videoRequest.getStopFrame(),
																		videoRequest.getFeedForwardTrack()));
						responseBytes = detectionBuffer.createVideoResponseMessage(
								msgMetadata,
								videoRequest.getStartFrame(), videoRequest.getStopFrame(),
								tracks, MPFDetectionError.MPF_DETECTION_SUCCESS,
								"");
					} catch (MPFComponentDetectionError e) {
						responseBytes = detectionBuffer.createVideoResponseMessage(
								msgMetadata,
								videoRequest.getStartFrame(), videoRequest.getStopFrame(),
								tracks, e.getDetectionError(), e.getMessage());
					}
				} else if (MPFDataType.UNKNOWN == msgMetadata.getDataType()) {
					MPFDetectionGenericRequest genericRequest = detectionBuffer.getGenericRequest();
					try {
						List<MPFGenericTrack> tracks = new ArrayList<>();
						tracks = component.getDetections(new MPFGenericJob(msgMetadata.getJobName(),
								msgMetadata.getMediaPath(),
								msgMetadata.getAlgorithmProperties(),
								msgMetadata.getMediaProperties(),
								genericRequest.getFeedForwardTrack()));
						responseBytes = detectionBuffer.createGenericResponseMessage(
								msgMetadata, tracks, MPFDetectionError.MPF_DETECTION_SUCCESS,
								"");
					} catch (MPFComponentDetectionError e) {
						responseBytes = detectionBuffer.createGenericResponseMessage(
								msgMetadata, Collections.<MPFGenericTrack>emptyList(), e.getDetectionError(),
								e.getMessage());
					}
				}
				// for debugging purposes
				LOG.debug("Detection results for file " + msgMetadata.getMediaPath() + ":\n" + responseBytes.toString());

				BytesMessage responseBytesMessage;
				try {
					responseBytesMessage = session.createBytesMessage();
					responseBytesMessage.writeBytes(responseBytes);
					ProtoUtils.setMsgProperties(headerProperties, responseBytesMessage);
					setProcessingTime(responseBytesMessage, startTime);
					replyProducer.setPriority(message.getJMSPriority());
					replyProducer.send(message.getJMSReplyTo(), responseBytesMessage);
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

				buildAndSend(
						responseBuilder.build(), message.getJMSReplyTo(), headerProperties,
						startTime);
			}
        } catch (Exception e) {
			// TODO: Send error message.
            LOG.error("Could not process detection request message due to Exception ", e);
			rollback();
        }
    }

	private void buildAndSend(
			DetectionProtobuf.DetectionResponse detectionResponse,
			Destination destination,
			Map<String, Object> headers,
			Instant startTime) {
		try {
			// Create a new response message and re-use the incoming headers.
			BytesMessage response = session.createBytesMessage();
			ProtoUtils.setMsgProperties(headers, response);

			// Set the body of the message.
			response.writeBytes(detectionResponse.toByteArray());
			setProcessingTime(response, startTime);

			replyProducer.send(destination, response);
			session.commit();

			// Record the success.
			LOG.debug("Request built and sent response. Error: {}.", detectionResponse.getError());
		} catch(Exception e) {
			// Record the failure. This is likely irrecoverable.
			LOG.error("Failed to send the response due to an exception.", e);
			rollback();
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
		detectionResponseBuilder.setMediaId(msgMetadata.getMediaId());
		detectionResponseBuilder.setTaskIndex(msgMetadata.getTaskIndex());
		detectionResponseBuilder.setActionIndex(msgMetadata.getActionIndex());

        // TODO: Refactor this code into a more generic DetectionMessenger and handle IMAGE and VIDEO data types.

		// try {
			// BufferedImage image = ImageIO.read(new File(detectionRequestInfo.dataUri));

			detectionResponseBuilder.getAudioResponseBuilder().addAudioTracksBuilder()
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
		detectionResponseBuilder.setError(DetectionProtobuf.DetectionError.UNSUPPORTED_DATA_TYPE);
		detectionResponseBuilder.setMediaId(msgMetadata.getMediaId());
		detectionResponseBuilder.setTaskIndex(msgMetadata.getTaskIndex());
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


	private static final double NANOS_PER_MS = Duration.ofMillis(1).toNanos();

	private static void setProcessingTime(Message message, Instant startTime) throws JMSException {
		var duration = Duration.between(startTime, Instant.now());
		// Manually convert nanoseconds to milliseconds so that the value can be rounded.
		// Duration.toMillis() always rounds down.
		long millis = Math.round(duration.toNanos() / NANOS_PER_MS);
		message.setLongProperty("ProcessingTime", millis);
	}

	private void rollback() {
		try {
			session.rollback();
		}
		catch (JMSException e) {
			throw new IllegalStateException("Rollback failed due to: " + e, e);
		}
	}
}
