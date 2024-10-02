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

package org.mitre.mpf.wfm.camel.operations.subject;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.ObjLongConsumer;

import javax.inject.Inject;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.mitre.mpf.mvc.util.CloseableMdc;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.buffers.SubjectProtobuf;
import org.mitre.mpf.wfm.data.access.SubjectJobRepo;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.service.SubjectJobResultsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SubjectJobProcessors {

    private static final Logger LOG = LoggerFactory.getLogger(SubjectJobProcessors.class);

    private static final String ID_PROP = "mpfId";

    private final SubjectJobRepo _subjectJobRepo;

    private final SubjectJobResultsService _subjectJobResultsService;

    @Inject
    SubjectJobProcessors(
            SubjectJobRepo subjectJobRepo, SubjectJobResultsService subjectJobResultsService) {
        _subjectJobRepo = subjectJobRepo;
        _subjectJobResultsService = subjectJobResultsService;
    }

    public static void initExchange(SubjectProtobuf.SubjectTrackingJob job, Exchange exchange) {
        exchange.setProperty(ID_PROP, job.getJobId());
        var message = exchange.getIn();
        message.setHeader(MpfHeaders.JOB_ID, job.getJobId());
        message.setBody(job);
    }


    public Processor getNewJobRequestProcessor() {
        return processorWithJobId(
            "sending job to queue",
            this::processNewJobRequest);
    }

    public Processor getJobResponseProcessor() {
        return processorWithJobId(
                "processing subject job response",
                this::processResponse);
    }

    public Processor getCancellationProcessor() {
        return processorWithJobId(
                "cancelling job",
                (e, id) -> _subjectJobResultsService.cancel(id));
    }

    public Processor getErrorProcessor() {
        return this::processError;
    }


    private void processNewJobRequest(Exchange exchange, long jobId) {
        var job = _subjectJobRepo.findById(jobId);
        var queueName = "MPF.SUBJECT_%s_REQUEST".formatted(
                job.getComponentName().toUpperCase());

        var message = exchange.getIn();
        message.setHeader(MpfHeaders.JMS_DESTINATION, queueName);
        message.setHeader(MpfHeaders.JMS_PRIORITY, job.getPriority());

        int numTracks = getNumberOfTracks(message);
        LOG.info(
                "Sending job {} with {} tracks to {}.",
                jobId, numTracks, queueName);
    }


    private void processResponse(Exchange exchange, long jobId) {
        var body = exchange.getIn().getBody(SubjectProtobuf.SubjectTrackingResult.class);
        _subjectJobResultsService.completeJob(jobId, body);
    }


    private void processError(Exchange exchange) {
        var optJobId = tryGetJobId(exchange);
        var exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        if (optJobId.isEmpty()) {
            var message = "Unable to handle error because the job id was not set.";
            LOG.error(message, exception);
            throw new WfmProcessingException(message, exception);
        }

        long id = optJobId.getAsLong();
        try (var ctx = CloseableMdc.job(id)) {
            _subjectJobResultsService.completeWithError(
                    id, "An exception occurred during a Camel route: ", exception);
        }
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
        var opt = tryGetJobId(exchange);
        if (opt.isPresent()) {
            return opt.getAsLong();
        }
        var message = "The incoming message was missing a job id.";
        var optCause = getException(exchange);
        if (optCause.isPresent()) {
            throw new WfmProcessingException(message, optCause.get());
        }
        else {
            throw new WfmProcessingException(message);
        }
    }


    private static OptionalLong tryGetJobId(Exchange exchange) {
        Long propJobId = exchange.getProperty(ID_PROP, Long.class);
        if (propJobId != null) {
            return OptionalLong.of(propJobId);
        }
        if (exchange.hasOut()) {
            Long outJobId = exchange.getOut().getHeader(MpfHeaders.JOB_ID, Long.class);
            if (outJobId != null) {
                return OptionalLong.of(outJobId);
            }
        }
        Long inJobId = exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class);
        if (inJobId != null) {
            return OptionalLong.of(inJobId);
        }
        return OptionalLong.empty();
    }


    private static Optional<Exception> getException(Exchange exchange) {
        return Optional.ofNullable(
                        exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class))
                .or(() -> Optional.ofNullable(exchange.getException()));
    }

    private Processor processorWithJobId(
            String description,
            ObjLongConsumer<Exchange> doProcessing) {
        return exchange -> {
            var jobId = getJobId(exchange);
            try (var ctx = CloseableMdc.job(jobId)) {
                try {
                    doProcessing.accept(exchange, jobId);
                }
                catch (Exception e) {
                    var errorMsg = "An error occurred while %s: ".formatted(description);
                    _subjectJobResultsService.completeWithError(jobId, errorMsg, e);
                    exchange.setProperty(Exchange.ROUTE_STOP, true);
                }
            }
        };
    }
}
