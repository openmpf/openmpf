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

package org.mitre.mpf.wfm.util;

import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.mitre.mpf.rest.api.pipelines.ActionType;
import org.mitre.mpf.rest.api.pipelines.Algorithm;
import org.mitre.mpf.wfm.camel.routes.DetectionCancellationRouteBuilder;
import org.mitre.mpf.wfm.camel.routes.MarkupCancellationRouteBuilder;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class JmsUtils {
    private static final Logger LOG = LoggerFactory.getLogger(JmsUtils.class);

    @Autowired
    private InProgressBatchJobsService _inProgressBatchJobs;

    @Autowired
    private CamelContext _camelContext;


    public void cancel(final long jobId) throws Exception {
        _camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                BatchJob job = _inProgressBatchJobs.getJob(jobId);

                for (Algorithm algorithm : job.getPipelineElements().getAllAlgorithms()) {
                    String routeName = createCancellationRouteName(jobId, algorithm.actionType().name(),
                                                                   algorithm.name(), "REQUEST");
                    String routeUri = String.format("activemq:MPF.%s_%s_REQUEST?selector=JobId%%3D%d",
                                                    algorithm.actionType().name(), algorithm.name(), jobId);
                    LOG.debug("Creating route {} with URI {}.", routeName, routeUri);
                    from(routeUri)
                            .routeId(routeName)
                            .setExchangePattern(ExchangePattern.InOnly)
                            .log(LoggingLevel.DEBUG, "Cancelling a message for ${headers.JobId}...")
                            .to(cancellationEndpointForActionType(algorithm.actionType()));
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
        _camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                List<Route> routes = new ArrayList<>(_camelContext.getRoutes());

                for(Route route : routes) {
                    if(route.getId().startsWith(createCancellationRouteName(jobId))) {
                        LOG.debug("Destroying Route: {}", route.getId());
                        _camelContext.stopRoute(route.getId());
                        _camelContext.removeRoute(route.getId());
                    }
                }
            }
        });
    }

    private static String cancellationEndpointForActionType(ActionType actionType) {
        switch (actionType) {
            case DETECTION:
                return DetectionCancellationRouteBuilder.ENTRY_POINT;
            case MARKUP:
                return MarkupCancellationRouteBuilder.ENTRY_POINT;
            default:
                return null;
        }
    }



    private static String createCancellationRouteName(long jobId, String... params) {
        // The brackets are used to avoid collisions between "CANCEL 1" and "CANCEL 100" when there are no params
        // provided. This disambiguation is necessary because the destroyCancellationRoutes method destroys any route
        // with a name like CANCEL [1] without concerning itself with the remainder of the route name.
        var prefix = String.format("CANCEL [%s]", jobId);
        if (params == null || params.length == 0) {
            return prefix;
        }
        return prefix + ' ' + String.join(" ", params);
    }
}
