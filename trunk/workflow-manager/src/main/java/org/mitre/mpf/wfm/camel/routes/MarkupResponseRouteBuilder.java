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
import org.mitre.mpf.wfm.buffers.Markup;
import org.mitre.mpf.wfm.camel.BroadcastEnabledAggregator;
import org.mitre.mpf.wfm.camel.WfmAggregator;
import org.mitre.mpf.wfm.camel.operations.markup.MarkupResponseProcessor;
import org.mitre.mpf.wfm.enums.MpfEndpoints;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.util.ProtobufDataFormatFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class MarkupResponseRouteBuilder extends RouteBuilder {
	private static final Logger log = LoggerFactory.getLogger(MarkupResponseRouteBuilder.class);

    public static final String JMS_DESTINATION = "MPF.COMPLETED_MARKUP";
	public static final String ENTRY_POINT = "activemq:" + JMS_DESTINATION;
	public static final String EXIT_POINT = JobRouterRouteBuilder.ENTRY_POINT;
	public static final String ROUTE_ID = "Markup Response Route";

	@Autowired
	private ProtobufDataFormatFactory protobufDataFormatFactory;

    @Autowired
    @Qualifier(BroadcastEnabledAggregator.REF)
	private WfmAggregator aggregator;

	private final String entryPoint, exitPoint, routeId;

	public MarkupResponseRouteBuilder() {
		this(ENTRY_POINT, EXIT_POINT, ROUTE_ID);
	}

	public MarkupResponseRouteBuilder(String entryPoint, String exitPoint, String routeId) {
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
			.unmarshal(protobufDataFormatFactory.create(Markup.MarkupResponse::newBuilder))
			.process(MarkupResponseProcessor.REF)
			.choice()
				.when(header(MpfHeaders.UNSOLICITED).isEqualTo(true))
					.to(MpfEndpoints.UNSOLICITED_MESSAGES)
				.otherwise()
                    .aggregate(header(MpfHeaders.CORRELATION_ID), aggregator)
                    .completionSize(header(MpfHeaders.SPLIT_SIZE))
					.to(exitPoint)
			.endChoice();
	}
}
