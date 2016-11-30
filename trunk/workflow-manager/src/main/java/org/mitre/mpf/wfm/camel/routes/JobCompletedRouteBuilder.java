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
import org.mitre.mpf.wfm.camel.JobCompleteProcessor;
import org.mitre.mpf.wfm.camel.JobCompleteProcessorImpl;
import org.mitre.mpf.wfm.camel.NoOpProcessor;
import org.mitre.mpf.wfm.camel.OutputObjectSenderProcessor;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Builds the route that is executed when a job has completed. In the context of this route, */
@Component
public class JobCompletedRouteBuilder extends RouteBuilder {
	private static final Logger log = LoggerFactory.getLogger(JobCompletedRouteBuilder.class);

	@Autowired
	@Qualifier(PropertiesUtil.REF)
	private PropertiesUtil propertiesUtil;

	public static final String ENTRY_POINT = "direct:jobCompleted";
	public static final String EXIT_POINT = String.format("class:%s", NoOpProcessor.class.getName());
	public static final String ROUTE_ID = "Job Completed Endpoint";

	protected final String entryPoint, exitPoint, routeId;

	/** Create a new instance of this class using the default entry and exit points (this is the constructor called by Spring). */
	public JobCompletedRouteBuilder() {
		this(ENTRY_POINT, EXIT_POINT, ROUTE_ID);
	}

	/**
	 * Create a new instance of this class using the specified entry point, exit point, and route name. This constructor
	 * is exposed to facilitate testing.
	 */
	public JobCompletedRouteBuilder(String entryPoint, String exitPoint, String routeId) {
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
			.processRef(JobCompleteProcessorImpl.REF)
			.processRef(OutputObjectSenderProcessor.REF)
			.choice()
				.when(header(MpfHeaders.SEND_OUTPUT_OBJECT).isEqualTo(Boolean.TRUE))
					.to(String.format("output:queue:%s", propertiesUtil.getOutputQueueName()), exitPoint)
				.otherwise()
					.to(exitPoint)
			.endChoice()
		.end();
	}
}
