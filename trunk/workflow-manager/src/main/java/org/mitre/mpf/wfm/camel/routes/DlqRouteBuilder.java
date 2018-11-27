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

package org.mitre.mpf.wfm.camel.routes;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ProcessorDefinition;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionDeadLetterProcessor;
import org.mitre.mpf.wfm.enums.MpfEndpoints;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * This builds the routes which read messages from the dead letter queue (DLQ).
 */
@Component
public class DlqRouteBuilder extends RouteBuilder {
	private static final Logger log = LoggerFactory.getLogger(DlqRouteBuilder.class);

	public static final String ENTRY_POINT = MpfEndpoints.DEAD_LETTER_QUEUE;
	public static final String EXIT_POINT = MpfEndpoints.COMPLETED_DETECTIONS;
	public static final String AUDIT_EXIT_POINT = MpfEndpoints.DLQ_PROCESSED_MESSAGES;
	public static final String INVALID_EXIT_POINT = MpfEndpoints.DLQ_INVALID_MESSAGES;
	public static final String ROUTE_ID = "DLQ Route";
	public static final String SELECTOR_REPLY_TO = MpfEndpoints.COMPLETED_DETECTIONS_REPLY_TO;

	private String entryPoint, exitPoint, auditExitPoint, invalidExitPoint, routeId, selectorReplyTo;
	private boolean unmarshalProtobuf = true; // for testing purposes

	@Autowired
	@Qualifier(DetectionDeadLetterProcessor.REF)
	private DetectionDeadLetterProcessor detectionDeadLetterProcessor;

	public DlqRouteBuilder() {
		this(ENTRY_POINT, EXIT_POINT, AUDIT_EXIT_POINT, INVALID_EXIT_POINT, ROUTE_ID, SELECTOR_REPLY_TO, true);
	}

	public DlqRouteBuilder(String entryPoint, String exitPoint, String auditExitPoint, String invalidExitPoint,
						   String routeId, String selectorReplyTo, boolean unmarshalProtobuf) {
		this.entryPoint = entryPoint;
		this.exitPoint = exitPoint;
		this.auditExitPoint = auditExitPoint;
		this.invalidExitPoint = invalidExitPoint;
		this.routeId = routeId;
		this.selectorReplyTo = selectorReplyTo;
		this.unmarshalProtobuf = unmarshalProtobuf;
	}

	public void setDeadLetterProcessor(DetectionDeadLetterProcessor deadLetterProcessor) {
		this.detectionDeadLetterProcessor = deadLetterProcessor;
	}

	@Override
	public void configure() throws Exception {
		log.debug("Configuring route '{}'.", routeId);

		// Only process detection messages sent to components and not duplicates; otherwise leave messages on the
		// default DLQ for auditing.
        String selector = "?selector=" + java.net.URLEncoder.encode(MpfHeaders.JMS_REPLY_TO + "='" + selectorReplyTo + "'"
                + " AND (dlqDeliveryFailureCause IS NULL OR dlqDeliveryFailureCause NOT LIKE '%duplicate from store%')", "UTF-8");

        ProcessorDefinition def = from(entryPoint + selector)
			.routeId(routeId)
			.setExchangePattern(ExchangePattern.InOnly);

				if (unmarshalProtobuf) {
					// Configure exception handler(s) before behaviors that can cause exceptions.
					def = def.onException(InvalidProtocolBufferException.class)
						.log(LoggingLevel.ERROR, DlqRouteBuilder.class.getName(),
							"Cannot parse message into a DetectionRequest. Cannot mark job ${header." + MpfHeaders.JOB_ID + "} as completed.")
						// ActiveMQ DefaultErrorHandler will print out message details.
						.to(invalidExitPoint) // send possibly corrupted message to a separate queue for auditing
						.handled(true) // prevent further camel processing
					.end()

					// Attempt to deserialize the protobuf message now. Failure to deserialize will trigger the exception
					// handler and prevent further exceptions and redelivery attempts for this message.

					// On at least one occasion, the following exception was observed:
					//     "com.google.protobuf.InvalidProtocolBufferException: Message missing required fields: data_uri"
					// Further debugging is necessary to determine the root cause.

					.unmarshal().protobuf(DetectionProtobuf.DetectionRequest.getDefaultInstance()).convertBodyTo(String.class);
				}

				def.multicast()
					.pipeline()
						.to(auditExitPoint) // send deserialized (readable) message to the exit point to indicate it has been processed, and for auditing
					.end()
					.pipeline()
						.process(detectionDeadLetterProcessor) // generate a detection response protobuf message with an error status
						.to(exitPoint) // send protobuf message to the intended destination to increment the job count
					.end()
				.end()
			.end();
	}
}
