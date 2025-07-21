/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2025 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2025 The MITRE Corporation                                       *
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

import javax.inject.Inject;

import org.mitre.mpf.wfm.service.component.AddComponentService;
import org.mitre.mpf.wfm.service.component.ComponentRegistrationException;
import org.mitre.mpf.wfm.service.component.JsonComponentDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class DetectionComponentRegistrationRouteBuilder extends
        BaseComponentRegistrationRouteBuilder<JsonComponentDescriptor> {

    private static final Logger LOG = LoggerFactory.getLogger(
            DetectionComponentRegistrationRouteBuilder.class);


    private static final String ENTRY_POINT = "activemq:MPF.DETECTION_COMPONENT_REGISTRATION";

    private final AddComponentService _addComponentService;

    @Inject
    DetectionComponentRegistrationRouteBuilder(
            ObjectMapper objectMapper,
            AddComponentService addComponentService) {
        super(
            ENTRY_POINT,
            "Detection Tracking Component Registration",
            objectMapper.readerFor(JsonComponentDescriptor.class));
        _addComponentService = addComponentService;
    }


    @Override
    public String registerComponent(JsonComponentDescriptor descriptor)
            throws ComponentRegistrationException {
        LOG.info(
                "Received detection component registration request for \"{}\".",
                descriptor.componentName());
        return _addComponentService.registerUnmanagedComponent(descriptor).getDescription();
    }
}
