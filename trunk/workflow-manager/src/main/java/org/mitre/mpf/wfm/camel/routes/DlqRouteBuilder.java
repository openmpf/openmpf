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
import org.apache.camel.builder.RouteBuilder;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionDeadLetterProcessor;
import org.mitre.mpf.wfm.enums.MpfEndpoints;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * This builds the routes which read messages from the dead letter queue (DLQ).
 */
@Component
public class DlqRouteBuilder extends RouteBuilder {
	private static final Logger log = LoggerFactory.getLogger(DlqRouteBuilder.class);

	public static final String ENTRY_POINT = MpfEndpoints.DEAD_LETTER_QUEUE;
	public static final String EXIT_POINT = MpfEndpoints.PROCESSED_DLQ_MESSAGES_QUEUE;
	public static final String ROUTE_ID = "DLQ Route";

	private static final String TAP_POINT = "direct:dlqTap";

	private final String entryPoint, exitPoint, tapPoint, routeId;

	public DlqRouteBuilder() {
		this(ENTRY_POINT, EXIT_POINT, TAP_POINT, ROUTE_ID);
	}

	public DlqRouteBuilder(String entryPoint, String exitPoint, String tapPoint, String routeId) {
		this.entryPoint = entryPoint;
		this.exitPoint = exitPoint;
		this.tapPoint = tapPoint;
		this.routeId = routeId;
	}

	@Override
	public void configure() throws Exception {
		log.debug("Configuring route '{}'.", routeId);

		// only process detection messages sent to components; otherwise leave messages on the default DLQ (for auditing)
		String selector = "?selector=" + java.net.URLEncoder.encode(MpfHeaders.JMS_REPLY_TO + "='" + MpfEndpoints.COMPLETED_DETECTIONS_REPLY_TO + "'","UTF-8");

		from(entryPoint + selector)
			.routeId(routeId)
			.setExchangePattern(ExchangePattern.InOnly)
			.multicast()
				.pipeline()
					// TODO: Test me!
					.doTry()
						// deserialize protobuf message for readability
						.unmarshal().protobuf(DetectionProtobuf.DetectionRequest.getDefaultInstance()).convertBodyTo(String.class)
						.to(exitPoint) // send to the exit point to indicate it has been processed (and for auditing)
					.doCatch(InvalidProtocolBufferException.class)
						// handled(true) by default
						.to("log:" + DlqRouteBuilder.class.getName() + "?level=ERROR&showCaughtException=true&showStackTrace=true")
				.end()
				.pipeline()
					.process(DetectionDeadLetterProcessor.REF) // generate a detection response protobuf message with an error status
					.to(MpfEndpoints.COMPLETED_DETECTIONS) // send protobuf message to the intended destination to increment the job count
				.end()
			.end();
	}
}
