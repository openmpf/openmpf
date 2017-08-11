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

package org.mitre.mpf.wfm.camel.routes;

import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.mitre.mpf.wfm.camel.*;
import org.mitre.mpf.wfm.camel.operations.mediainspection.MediaInspectionProcessor;
import org.mitre.mpf.wfm.camel.operations.mediainspection.MediaInspectionSplitter;
import org.mitre.mpf.wfm.enums.MpfEndpoints;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.util.JniLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

/**
 * Builds the route that inspects the {@link org.mitre.mpf.wfm.data.entities.transients.TransientMedia} in a job. Once
 * inspected, metadata fields such as  {@link org.mitre.mpf.wfm.data.entities.transients.TransientMedia#type} and
 * {@link org.mitre.mpf.wfm.data.entities.transients.TransientMedia#length} should have meaningful values. If a medium
 * cannot be inspected, its {@link org.mitre.mpf.wfm.data.entities.transients.TransientMedia#failed} flag should be set.
 */
@Component
@DependsOn(JniLoader.REF)
public class MediaInspectionRouteBuilder extends RouteBuilder {
	private static final Logger log = LoggerFactory.getLogger(MediaInspectionRouteBuilder.class);

	/** The default entry point for this route. */
	public static final String ENTRY_POINT = MpfEndpoints.MEDIA_INSPECTION_ENTRY_POINT;

	/** The default exit point for this route. */
	public static final String EXIT_POINT = JobRouterRouteBuilder.ENTRY_POINT;

	/** The default identifier for this route. */
	public static final String ROUTE_ID = "Media Inspection Route";

	/** The aggregator to use for collecting inspection results. */
	@Autowired
	@Qualifier(StringCountBasedWfmAggregator.REF)
	private WfmAggregator<String> aggregator;

	private final String entryPoint, exitPoint, routeId;

	/** Create a new instance of this class using the default entry and exit points (this is the constructor called by Spring). */
	public MediaInspectionRouteBuilder() {
		this(ENTRY_POINT, EXIT_POINT, ROUTE_ID);
	}

	/**
	 * Create a new instance of this class using the specified entry point, exit point, and route name. This constructor
	 * is exposed to facilitate testing.
	 */
	public MediaInspectionRouteBuilder(String entryPoint, String exitPoint, String routeId) {
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
			.split().method(MediaInspectionSplitter.REF, "split")
				.parallelProcessing() // Perform this operation in parallel.
				.streaming() // The aggregation order of messages is not important.
			.choice()
				.when(header(MpfHeaders.EMPTY_SPLIT).isEqualTo(Boolean.TRUE))
					.removeHeader(MpfHeaders.EMPTY_SPLIT)
					.process(JobRetrievalProcessor.REF)
					.to(exitPoint)
				.otherwise()
					.to(MpfEndpoints.MEDIA_INSPECTION_WORK_QUEUE)
			.end();

		from(MpfEndpoints.MEDIA_INSPECTION_WORK_QUEUE)
			.setExchangePattern(ExchangePattern.InOnly)
			.process(MediaInspectionProcessor.REF)
			.aggregate(header(MpfHeaders.CORRELATION_ID), aggregator)
			.completionPredicate(new SplitCompletedPredicate())
			.removeHeader(MpfHeaders.SPLIT_COMPLETED)
			.process(JobRetrievalProcessor.REF)
			.to(exitPoint);
	}
}
