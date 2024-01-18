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

package org.mitre.mpf.wfm.camel;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.javasimon.aop.Monitored;
import org.mitre.mpf.mvc.util.CloseableMdc;
import org.mitre.mpf.rest.api.pipelines.ActionType;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionTaskSplitter;
import org.mitre.mpf.wfm.camel.operations.markup.MarkupSplitter;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

/**
 * Given that all actions in a task share a common operation type (e.g., DETECTION), this class is used to
 * call the correct operation-specific splitter. If an unrecognized operation is associated with the task, this
 * splitter will return an empty split so that the job will continue without hanging or throwing an exception.
 */
@Monitored
@Component(DefaultTaskSplitter.REF)
public class DefaultTaskSplitter {
    public static final String REF = "defaultTaskSplitter";

    private static final Logger LOG = LoggerFactory.getLogger(DefaultTaskSplitter.class);

    private DetectionTaskSplitter _detectionSplitter;

    private MarkupSplitter _markupSplitter;

    private InProgressBatchJobsService _inProgressJobs;


    @Inject
    DefaultTaskSplitter(
            DetectionTaskSplitter detectionSplitter,
            MarkupSplitter markupSplitter,
            InProgressBatchJobsService inProgressJobs) {
        _detectionSplitter = detectionSplitter;
        _markupSplitter = markupSplitter;
        _inProgressJobs = inProgressJobs;
    }


    public List<Message> split(Exchange exchange) {
        long jobId = exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class);
        try (var ctx = CloseableMdc.job(jobId)) {
            var messages = doSplit(jobId, exchange);
            if (messages.isEmpty()) {
                exchange.setProperty(MpfHeaders.EMPTY_SPLIT, true);
            }
            return messages;
        }
    }


    private List<Message> doSplit(long jobId, Exchange exchange) {
        try {
            BatchJob job = _inProgressJobs.getJob(jobId);
            Task task = job.getPipelineElements().getTask(job.getCurrentTaskIndex());
            ActionType actionType = job.getPipelineElements()
                    .getAlgorithm(job.getCurrentTaskIndex(), 0)
                    .actionType();
            LOG.info("Task {}/{} - Operation: {} - ActionType: {}.",
                    job.getCurrentTaskIndex() + 1,
                    job.getPipelineElements().getTaskCount(),
                    actionType,
                    actionType.name());

            if (job.isCancelled()) {
                // Check if this job has been cancelled prior to performing the split.
                // If it has been, do not produce any work units.
                LOG.warn("This job has been cancelled. No work will be performed in task {}.",
                        job.getCurrentTaskIndex());
                return List.of();
            }

            var messages = switch (actionType) {
                case DETECTION -> _detectionSplitter.performSplit(job, task);
                case MARKUP -> _markupSplitter.performSplit(job, task);
                default -> {
                    LOG.warn("Task {} calls an unsupported operation '{}'. " +
                                    "No work will be performed in this task.",
                            job.getCurrentTaskIndex(), task.name());
                    yield List.<Message>of();
                }
            };

            // Create a correlation id to associate with all messages produced by this split.
            var correlationId = jobId + ":" + UUID.randomUUID();
            var headers = Map.of(
                MpfHeaders.SPLIT_SIZE, messages.size(),
                MpfHeaders.JOB_ID, jobId,
                MpfHeaders.JMS_PRIORITY, exchange.getIn().getHeader(MpfHeaders.JMS_PRIORITY),
                MpfHeaders.CORRELATION_ID, correlationId
            );
            messages.forEach(m -> m.getHeaders().putAll(headers));

            LOG.info(
                "DefaultTaskSplitter produced {} work units with correlation id '{}'.",
                messages.size(),
                correlationId);
            return messages;
        }
        catch (Exception e) {
            var errorMsg = String.format(
                "Failed to complete the split operation for Job %s due to : %s",
                jobId, e);
            LOG.error(errorMsg, e);
            _inProgressJobs.addFatalError(jobId, IssueCodes.OTHER, errorMsg);
            return List.of();
        }
    }
}
