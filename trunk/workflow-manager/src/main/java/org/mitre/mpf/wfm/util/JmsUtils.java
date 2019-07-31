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

package org.mitre.mpf.wfm.util;

import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.enums.MpfEndpoints;
import org.mitre.mpf.wfm.pipeline.Algorithm;
import org.mitre.mpf.wfm.pipeline.PipelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class JmsUtils {
	private static final Logger log = LoggerFactory.getLogger(JmsUtils.class);

	private String createCancellationRouteName(long jobId, String... params) {
		// The brackets are used to avoid collisions between "CANCEL 1" and "CANCEL 100" when there are no params provided. This
		// disambiguation is necessary because the destroyCancellationRoutes method destroys any route with a name like
		// CANCEL [1] without concerning itself with the remainder of the route name.
		return String.format("CANCEL [%d]", jobId) + ((params == null || params.length == 0) ? "" : " " + StringUtils.join(params, " "));
	}

	@Autowired
	private PipelineService pipelineService;

	@Autowired
	private CamelContext camelContext;

	public void cancel(final long jobId) throws Exception {
		camelContext.addRoutes(new RouteBuilder() {
			@Override
			public void configure() throws Exception {
				for(final Algorithm algorithmDefinition : pipelineService.getAlgorithms()) {
					String routeName = createCancellationRouteName(jobId, algorithmDefinition.getActionType().name(), algorithmDefinition.getName(), "REQUEST");
					String routeUri = String.format("jms:MPF.%s_%s_REQUEST?selector=JobId%%3D%d", algorithmDefinition.getActionType().name(), algorithmDefinition.getName(), jobId);
					log.debug("Creating route {} with URI {}.", routeName);
					from(routeUri)
						.routeId(routeName)
						.setExchangePattern(ExchangePattern.InOnly)
						.log(LoggingLevel.DEBUG, "Cancelling a message for ${headers.JobId}...")
						.to(cancellationEndpointForActionType(algorithmDefinition.getActionType()));
				}
			}
		});
	}

	/**
	 * When a job completes, any cancellation routes associated with the Job should also be stopped and deleted.
	 * @param jobId
	 * @throws Exception
     */
	public synchronized void destroyCancellationRoutes(final long jobId) throws Exception {
		camelContext.addRoutes(new RouteBuilder() {
			@Override
			public void configure() throws Exception {
				List<Route> routes = new ArrayList<>(camelContext.getRoutes());

				for(Route route : routes) {
					if(route.getId().startsWith(createCancellationRouteName(jobId))) {
						log.debug("Destroying Route: {}", route.getId());
						camelContext.stopRoute(route.getId());
						camelContext.removeRoute(route.getId());
					}
				}
			}
		});
	}

	private String cancellationEndpointForActionType(ActionType actionType) {
		switch (actionType) {
			case DETECTION:
				return MpfEndpoints.CANCELLED_DETECTIONS;
			case MARKUP:
				return MpfEndpoints.CANCELLED_MARKUPS;
			default:
				return null;
		}
	}
}
