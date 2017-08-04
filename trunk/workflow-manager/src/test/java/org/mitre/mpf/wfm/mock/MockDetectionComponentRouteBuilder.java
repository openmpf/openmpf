/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.mock;

import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.protobuf.ProtobufDataFormat;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.enums.MpfEndpoints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Builds the route that handles detection responses. */
@Component
public class MockDetectionComponentRouteBuilder extends RouteBuilder {

	private static final Logger log = LoggerFactory.getLogger(MockDetectionComponentRouteBuilder.class);

	/** The default entry point for this route. */
	public static final String ENTRY_POINT = MpfMockEndpoints.MOCK_DETECTION_REQUEST;

	/** The default exit point for this route. */
	public static final String EXIT_POINT = MpfEndpoints.COMPLETED_DETECTIONS;

	/** The default id route. */
	public static final String ROUTE_ID = "Mock Detection Component Route";

	private final String entryPoint, exitPoint, routeId;

	/** Create a new instance of this class using the default entry and exit points (this is the constructor called by Spring). */
	public MockDetectionComponentRouteBuilder() {
		this(ENTRY_POINT, EXIT_POINT, ROUTE_ID);
	}

	/**
	 * Create a new instance of this class using the specified entry point, exit point, and route name. This constructor
	 * is exposed to facilitate testing.
	 */
	public MockDetectionComponentRouteBuilder(String entryPoint, String exitPoint, String routeId) {
		this.entryPoint = entryPoint;
		this.exitPoint = exitPoint;
		this.routeId = routeId;
	}

	@Override
	public void configure() throws Exception {
		log.debug("Configuring route '{}'.", routeId);

		from(entryPoint)
			.routeId(routeId)
			.setExchangePattern(ExchangePattern.InOnly)
			.unmarshal(new ProtobufDataFormat(DetectionProtobuf.DetectionRequest.getDefaultInstance())) // Unpack the protobuf response.
			.processRef(MockDetectionComponent.REF) // Run the response through the response processor.
			.to(exitPoint);
	}
}
