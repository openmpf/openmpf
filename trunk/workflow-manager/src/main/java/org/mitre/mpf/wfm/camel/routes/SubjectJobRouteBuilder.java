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
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.buffers.SubjectProtobuf;
import org.mitre.mpf.wfm.buffers.SubjectProtobuf.SubjectTrackingJob;
import org.mitre.mpf.wfm.data.access.SubjectJobRepo;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.service.SubjectJobResultsService;
import org.mitre.mpf.wfm.util.ProtobufDataFormatFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SubjectJobRouteBuilder extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(SubjectJobRouteBuilder.class);

    private static final String ENTRY_POINT = "direct:BEGIN_SUBJECT_TRACKING";

    private static final String ID_PROP = "mpfId";

    private final ProtobufDataFormatFactory _protobufDataFormatFactory;

    private final SubjectJobResultsService _subjectTrackingService;

    private final SubjectJobRepo _subjectJobRepo;

    @Inject
    SubjectJobRouteBuilder(
            ProtobufDataFormatFactory protobufDataFormatFactory,
            SubjectJobResultsService subjectTrackingService,
            SubjectJobRepo subjectJobRepo) {
        _protobufDataFormatFactory = protobufDataFormatFactory;
        _subjectTrackingService = subjectTrackingService;
        _subjectJobRepo = subjectJobRepo;
    }


    @Override
    public void configure() throws Exception {
        from(ENTRY_POINT)
            .routeId("Subject Tracking Route")
            .setExchangePattern(ExchangePattern.InOut)
            .process(this::processRequest)
            .marshal().protobuf()
            .to("activemq:queue:subject")
            .filter(header(MpfHeaders.CANCELLED).isEqualTo(true))
                .process(e -> _subjectTrackingService.cancel(getJobId(e)))
                .stop()
            .end()
            .unmarshal(_protobufDataFormatFactory.create(
                    SubjectProtobuf.SubjectTrackingResult.parser()))
            .process(this::processResponse);
    }


    public static CompletableFuture<Object> submit(
            SubjectTrackingJob job, ProducerTemplate producerTemplate) {
        // asyncSendBody needs to be used, otherwise the submitting thread will block until the
        // response from the subject tracking component is received. This behavior occurs because
        // of how the route was set up, so the submitter should not need to know that asyncSendBody
        // is required.
        return producerTemplate.asyncSendBody(ENTRY_POINT, job);
    }


    private void processRequest(Exchange exchange) {
        var message = exchange.getIn();
        var pbRequest = message.getBody(SubjectProtobuf.SubjectTrackingJob.class);
        setJobId(pbRequest.getJobId(), exchange);

        var job = _subjectJobRepo.findById(pbRequest.getJobId()).orElseThrow();
        var queueName = "MPF.SUBJECT_%s_REQUEST".formatted(job.getComponentName().toUpperCase());
        message.setHeader(MpfHeaders.JMS_DESTINATION, queueName);
        message.setHeader(MpfHeaders.JMS_PRIORITY, job.getPriority());

        int numTracks = getNumberOfTracks(message);
        LOG.info(
                "Sending job {} with {} tracks to {}.",
                pbRequest.getJobId(), numTracks, queueName);
    }

    private void processResponse(Exchange exchange) {
        long id = getJobId(exchange);
        var body = exchange.getIn().getBody(SubjectProtobuf.SubjectTrackingResult.class);
        _subjectTrackingService.completeJob(id, body);
    }


    private static int getNumberOfTracks(Message message) {
        var body = message.getBody(SubjectProtobuf.SubjectTrackingJob.class);
        int numVideoTracks = body.getVideoJobResultsList().stream()
                .mapToInt(v -> v.getResultsCount())
                .sum();
        int numImageTracks = body.getImageJobResultsList().stream()
                .mapToInt(i -> i.getResultsCount())
                .sum();
        return numVideoTracks + numImageTracks;
    }


    private static long getJobId(Exchange exchange) {
        Long propJobId = exchange.getProperty(ID_PROP, Long.class);
        if (propJobId != null) {
            return propJobId;
        }
        if (exchange.hasOut()) {
            Long outJobId = exchange.getOut().getHeader(MpfHeaders.JOB_ID, Long.class);
            if (outJobId != null) {
                return outJobId;
            }
        }
        Long inJobId = exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class);
        if (inJobId != null) {
            return inJobId;
        }
        throw new WfmProcessingException("The incoming message was missing a job id.");
    }

    private static void setJobId(long jobId, Exchange exchange) {
        exchange.setProperty(ID_PROP, jobId);
        exchange.getIn().setHeader(MpfHeaders.JOB_ID, jobId);
    }
}
