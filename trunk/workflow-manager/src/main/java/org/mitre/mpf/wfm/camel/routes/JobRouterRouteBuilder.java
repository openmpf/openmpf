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

package org.mitre.mpf.wfm.camel.routes;

import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.mitre.mpf.wfm.camel.DefaultTaskSplitter;
import org.mitre.mpf.wfm.ActiveMQConfiguration;
import org.mitre.mpf.wfm.camel.BeginTaskProcessor;
import org.mitre.mpf.wfm.camel.JobCompleteProcessorImpl;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * This builds the routes which replace the MPF Action Router.
 */
@Component
public class JobRouterRouteBuilder extends RouteBuilder {
	private static final Logger log = LoggerFactory.getLogger(JobRouterRouteBuilder.class);

	public static final String ENTRY_POINT = "activemq:MPF.JOB_ROUTER";
	public static final String ROUTE_ID = "Job Router Route";

	private final String entryPoint, routeId;

	public JobRouterRouteBuilder() {
		this(ENTRY_POINT, ROUTE_ID);
	}

	public JobRouterRouteBuilder(String entryPoint, String routeId) {
		this.entryPoint = entryPoint;
		this.routeId = routeId;
	}


	@Override
	public void configure() {
		log.debug("Configuring route '{}'.", routeId);

		from(entryPoint)
			.routeId(routeId)
			.setExchangePattern(ExchangePattern.InOnly)
            .filter(header(MpfHeaders.JOB_COMPLETE).isNotEqualTo(true))
                .process(BeginTaskProcessor.REF)
            .end()
            .filter(header(MpfHeaders.JOB_COMPLETE).isEqualTo(true))
                .process(JobCompleteProcessorImpl.REF)
                .stop()
            .end()
            .split(method(DefaultTaskSplitter.REF, "split"))
                .parallelProcessing()
                .streaming()
                .executorServiceRef(ActiveMQConfiguration.SPLITTER_THREAD_POOL_REF)
                .marshal().protobuf()
                // Splitter will set the "CamelJmsDestinationName" header to
                // specify the destination.
                // Adapted from: https://camel.apache.org/components/3.20.x/jms-component.html#_reuse_endpoint_and_send_to_different_destinations_computed_at_runtime
                .to("activemq:queue:dummy")
            .end()
            .filter(exchangeProperty(MpfHeaders.EMPTY_SPLIT))
                .to(entryPoint)
            .end();
	}
}
