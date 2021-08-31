/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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
import org.mitre.mpf.rest.api.pipelines.ActionType;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionTaskSplitter;
import org.mitre.mpf.wfm.camel.operations.markup.MarkupSplitter;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Given that all actions in a task share a common operation type (e.g., DETECTION), this class is used to
 * call the correct operation-specific splitter. If an unrecognized operation is associated with the task, this
 * splitter will return an empty split so that the job will continue without hanging or throwing an exception.
 */
@Component(DefaultTaskSplitter.REF)
public class DefaultTaskSplitter extends WfmSplitter {
    private static final Logger log = LoggerFactory.getLogger(DefaultTaskSplitter.class);
    public static final String REF = "defaultTaskSplitter";

    @Autowired
    private DetectionTaskSplitter detectionSplitter;

    @Autowired
    private MarkupSplitter markupSplitter;

    @Autowired
    private InProgressBatchJobsService inProgressJobs;

    @Override
    public String getSplitterName() { return REF; }


    @Override
    public List<Message> wfmSplit(Exchange exchange) {
        BatchJob job = inProgressJobs.getJob(exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class));
        Task task = job.getPipelineElements().getTask(job.getCurrentTaskIndex());
        ActionType actionType = job.getPipelineElements()
                .getAlgorithm(job.getCurrentTaskIndex(), 0)
                .getActionType();
        log.info("Task {}/{} - Operation: {} - ActionType: {}.",
                 job.getCurrentTaskIndex() + 1,
                 job.getPipelineElements().getTaskCount(),
                 actionType,
                 actionType.name());

        if (job.isCancelled()) {
            // Check if this job has been cancelled prior to performing the split.
            // If it has been, do not produce any work units.
            log.warn("This job has been cancelled. No work will be performed in task {}.",
                     job.getCurrentTaskIndex());
            return List.of();
        }

        switch (actionType) {
            case DETECTION:
                return detectionSplitter.performSplit(job, task);
            case MARKUP:
                return markupSplitter.performSplit(job, task);
            default:
                log.warn("Task {} calls an unsupported operation '{}'. " +
                                 "No work will be performed in this task.",
                         job.getCurrentTaskIndex(), task.getName());
                return List.of();
        }
    }
}
