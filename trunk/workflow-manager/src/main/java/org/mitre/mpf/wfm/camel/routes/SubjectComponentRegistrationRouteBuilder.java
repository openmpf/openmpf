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

import javax.inject.Inject;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.service.component.subject.SubjectComponentDescriptor;
import org.mitre.mpf.wfm.service.component.subject.SubjectComponentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class SubjectComponentRegistrationRouteBuilder extends RouteBuilder {

    public static final String ENTRY_POINT = "activemq:MPF.SUBJECT_COMPONENT_REGISRATION";

    private static final Logger LOG = LoggerFactory.getLogger(
            SubjectComponentRegistrationRouteBuilder.class);

    private final SubjectComponentService _componentService;

    private final ObjectMapper _objectMapper;

    @Inject
    SubjectComponentRegistrationRouteBuilder(
            SubjectComponentService componentService,
            ObjectMapper objectMapper) {
        _componentService = componentService;
        _objectMapper = objectMapper;
    }

    @Override
    public void configure() throws Exception {
        from(ENTRY_POINT)
            .routeId("Subject Tracking Component Registration")
            .setExchangePattern(ExchangePattern.InOut)
            .process(this::processRequest);
    }

    private void processRequest(Exchange exchange) {
        var response = exchange.getOut();
        try {
            var descriptor = _objectMapper.readValue(
                    exchange.getIn().getBody(String.class),
                    SubjectComponentDescriptor.class);
            var registrationResult = _componentService.registerComponent(descriptor);
            response.setHeader("success", true);
            response.setHeader("detail", registrationResult.description);
        }
        catch (IOException | WfmProcessingException e) {
            response.setHeader("success", false);
            response.setHeader("detail", "Failed to register due to: " + e);
            LOG.error("An error occurred while trying to register component: " + e, e);
        }
    }
}
