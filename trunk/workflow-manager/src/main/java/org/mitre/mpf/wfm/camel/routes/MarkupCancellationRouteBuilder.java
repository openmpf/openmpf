/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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
import org.mitre.mpf.wfm.camel.operations.markup.MarkupCancellationProcessor;
import org.mitre.mpf.wfm.enums.MpfEndpoints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MarkupCancellationRouteBuilder extends RouteBuilder {
    private static final Logger log = LoggerFactory.getLogger(MarkupCancellationRouteBuilder.class);

    public static final String ENTRY_POINT = MpfEndpoints.CANCELLED_MARKUPS;
    public static final String EXIT_POINT = MpfEndpoints.COMPLETED_MARKUP;
    public static final String ROUTE_ID = "Markup Cancellation Route";

    private final String entryPoint, exitPoint, routeId;

    public MarkupCancellationRouteBuilder() {
        this(ENTRY_POINT, EXIT_POINT, ROUTE_ID);
    }

    public MarkupCancellationRouteBuilder(String entryPoint, String exitPoint, String routeId) {
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
            .process(MarkupCancellationProcessor.REF)
            .to(exitPoint);
    }
}
