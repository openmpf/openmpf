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

import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultExchange;
import org.mitre.mpf.wfm.buffers.SubjectProtobuf;
import org.mitre.mpf.wfm.buffers.SubjectProtobuf.SubjectTrackingJob;
import org.mitre.mpf.wfm.camel.operations.subject.SubjectJobProcessors;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.util.ProtobufDataFormatFactory;
import org.springframework.stereotype.Component;

@Component
public class SubjectJobRouteBuilder extends RouteBuilder {

    private static final String ENTRY_POINT = "direct:BEGIN_SUBJECT_TRACKING";

    private final ProtobufDataFormatFactory _pbFormatFactory;


    private final SubjectJobProcessors _subjectJobProcessors;

    @Inject
    public SubjectJobRouteBuilder(
            ProtobufDataFormatFactory pbFormatFactory,
            SubjectJobProcessors subjectJobProcessors) {
        _pbFormatFactory = pbFormatFactory;
        _subjectJobProcessors = subjectJobProcessors;
    }


    @Override
    public void configure() {

        onException(Exception.class)
            .process(_subjectJobProcessors.getErrorProcessor())
            .handled(true);

        from(ENTRY_POINT)
            .routeId("Subject Job Route")
            .setExchangePattern(ExchangePattern.InOut)
            .process(_subjectJobProcessors.getNewJobRequestProcessor())
            .marshal().protobuf()
            .to("activemq:queue:subject")
            .choice()
                .when(header(MpfHeaders.CANCELLED).isEqualTo(true))
                    .process(_subjectJobProcessors.getCancellationProcessor())
                .otherwise()
                    .unmarshal(_pbFormatFactory.create(
                            SubjectProtobuf.SubjectTrackingResult.parser()))
                    .process(_subjectJobProcessors.getJobResponseProcessor());
    }


    public static CompletableFuture<Exchange> submit(
            SubjectTrackingJob job, ProducerTemplate producerTemplate) {
        // asyncSendBody needs to be used, otherwise the submitting thread will block until the
        // response from the subject tracking component is received. This behavior occurs because
        // of how the route was set up, so the submitter should not need to know that asyncSendBody
        // is required.
        var exchange = new DefaultExchange(producerTemplate.getCamelContext());
        SubjectJobProcessors.initExchange(job, exchange);
        return producerTemplate.asyncSend(ENTRY_POINT, exchange);
    }
}
