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

package org.mitre.mpf.wfm.camel.routes;

import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.protobuf.ProtobufDataFormat;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.camel.*;
import org.mitre.mpf.wfm.camel.operations.detection.artifactextraction.ArtifactExtractionProcessor;
import org.mitre.mpf.wfm.camel.operations.detection.artifactextraction.ArtifactExtractionSplitter;
import org.mitre.mpf.wfm.camel.operations.detection.trackmerging.TrackMergingProcessor;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionResponseProcessor;
import org.mitre.mpf.wfm.enums.MpfEndpoints;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Builds the route that handles detection responses. */
@Component
public class DetectionResponseRouteBuilder extends RouteBuilder {

	private static final Logger log = LoggerFactory.getLogger(DetectionResponseRouteBuilder.class);

	/** The default entry point for this route. */
	public static final String ENTRY_POINT = MpfEndpoints.COMPLETED_DETECTIONS;

	/** The default exit point for this route. */
	public static final String EXIT_POINT = MpfEndpoints.STAGE_RESULTS_AGGREGATOR;

	/** The default id route. */
	public static final String ROUTE_ID = "Detection Response Route";

	@Autowired
	@Qualifier(BroadcastEnabledStringCountBasedWfmAggregator.REF)
	private WfmAggregator<String> aggregator;

	private final String entryPoint, exitPoint, routeId;

	/** Create a new instance of this class using the default entry and exit points (this is the constructor called by Spring). */
	public DetectionResponseRouteBuilder() {
		this(ENTRY_POINT, EXIT_POINT, ROUTE_ID);
	}

	/**
	 * Create a new instance of this class using the specified entry point, exit point, and route name. This constructor
	 * is exposed to facilitate testing.
	 */
	public DetectionResponseRouteBuilder(String entryPoint, String exitPoint, String routeId) {
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
			.unmarshal(new ProtobufDataFormat(DetectionProtobuf.DetectionResponse.getDefaultInstance())) // Unpack the protobuf response.
			.processRef(DetectionResponseProcessor.REF) // Run the response through the response processor.
			.choice()
				.when(header(MpfHeaders.UNSOLICITED).isEqualTo(Boolean.TRUE.toString()))
					.to(MpfEndpoints.UNSOLICITED_MESSAGES)
				.otherwise()
					.aggregate(header(MpfHeaders.CORRELATION_ID), aggregator)
					.completionPredicate(new SplitCompletedPredicate(true)) // We need to forward the body of the last message on to the next processor.
					.removeHeader(MpfHeaders.SPLIT_COMPLETED)
					.processRef(TrackMergingProcessor.REF) // Track merging is trivial. If it becomes a heavy lift, put in a splitter/aggregator to divide the work.
					.split().method(ArtifactExtractionSplitter.REF, "split")
						.parallelProcessing() // Create work units and process them in any order.
						.streaming() // Aggregate responses in any order.
						.choice()
							.when(header(MpfHeaders.EMPTY_SPLIT).isEqualTo(Boolean.TRUE))
								.removeHeader(MpfHeaders.EMPTY_SPLIT)
								.to(exitPoint)
							.otherwise()
								.to(MpfEndpoints.ARTIFACT_EXTRACTION_WORK_QUEUE)
						.endChoice()
					.end()
			.end();

		from(MpfEndpoints.ARTIFACT_EXTRACTION_WORK_QUEUE)
			.processRef(ArtifactExtractionProcessor.REF)
			.setExchangePattern(ExchangePattern.InOnly)
			.setHeader(MpfHeaders.SUPPRESS_BROADCAST, constant(Boolean.TRUE))
			.to(exitPoint);
	}
}
