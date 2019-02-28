/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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
import org.mitre.mpf.wfm.camel.DefaultJobErrorHandler;
import org.mitre.mpf.wfm.camel.JobCompleteProcessorImpl;
import org.mitre.mpf.wfm.camel.operations.jobcreation.JobCreationProcessor;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.MpfEndpoints;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Builds the route that is executed when a job request is submitted to the system. */
@Component
public class JobCreatorRouteBuilder extends RouteBuilder {
	private static final Logger log = LoggerFactory.getLogger(JobCreatorRouteBuilder.class);

	public static final String ENTRY_POINT = MpfEndpoints.JOB_REQUESTS;
	public static final String EXIT_POINT = MediaRetrieverRouteBuilder.ENTRY_POINT;
	public static final String ROUTE_ID = "Job Creator Route";

	private String routeId, entryPoint, exitPoint;

	/** Create a new instance of this class using the default entry and exit points (this is the constructor called by Spring). */
	public JobCreatorRouteBuilder() {
		this(ENTRY_POINT, EXIT_POINT, ROUTE_ID);
	}

	/**
	 * Create a new instance of this class using the specified entry point, exit point, and route name. This constructor
	 * is exposed to facilitate testing.
	 */
	public JobCreatorRouteBuilder(String entryPoint, String exitPoint, String routeId) {
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
			.onException(Exception.class)
				.handled(true) // Don't let this bubble up. It is sufficient that we caught it.
				.to(String.format("class:%s", DefaultJobErrorHandler.class.getName()))
			.end()
			.process(JobCreationProcessor.REF)
			.choice()
				.when(header(MpfHeaders.JOB_CREATION_ERROR).isEqualTo(Boolean.TRUE))
				.removeHeader(MpfHeaders.JOB_CREATION_ERROR)
				.setHeader(MpfHeaders.JOB_STATUS, simple(BatchJobStatusType.ERROR.name()))
				.to(JobCompleteProcessorImpl.REF)
			.otherwise()
				.to(exitPoint);
	}
}
