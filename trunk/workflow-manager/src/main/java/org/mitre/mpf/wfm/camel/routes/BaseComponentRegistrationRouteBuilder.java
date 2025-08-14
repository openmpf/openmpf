/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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

import java.io.IOException;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.service.component.ComponentRegistrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectReader;

public abstract class BaseComponentRegistrationRouteBuilder<TDescriptor> extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(
            BaseComponentRegistrationRouteBuilder.class);

    private final ObjectReader _objectReader;

    private final String _entryPoint;

    private final String _routeId;

    protected BaseComponentRegistrationRouteBuilder(
            String entryPoint,
            String routeId,
            ObjectReader objectReader) {
        _objectReader = objectReader;
        _entryPoint = entryPoint;
        _routeId = routeId;
    }

    public abstract String registerComponent(TDescriptor descriptor)
            throws ComponentRegistrationException;


    @Override
    public void configure() {
        from(_entryPoint + "?concurrentConsumers=1&maxConcurrentConsumers=1")
            .routeId(_routeId)
            .setExchangePattern(ExchangePattern.InOut)
            .process(this::processRequest);
    }


    private void processRequest(Exchange exchange) {
        var response = exchange.getOut();
        try {
            var requestBody = exchange.getIn().getBody(String.class);
            TDescriptor descriptor = _objectReader.readValue(requestBody);
            var registrationResult = registerComponent(descriptor);
            response.setHeader("success", true);
            response.setHeader("detail", registrationResult);
        }
        catch (IOException | WfmProcessingException | ComponentRegistrationException e) {
            response.setHeader("success", false);
            response.setHeader("detail", "Failed to register due to: " + e);
            LOG.error("An error occurred while trying to register component: " + e, e);
        }
    }
}
