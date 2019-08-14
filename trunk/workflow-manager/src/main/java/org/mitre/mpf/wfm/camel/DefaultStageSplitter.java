/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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
import org.mitre.mpf.wfm.camel.operations.detection.DetectionSplitter;
import org.mitre.mpf.wfm.camel.operations.markup.MarkupStageSplitter;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Given that all actions in a stage/task share a common operation type (e.g., DETECTION), this class is used to
 * call the correct operation-specific splitter. If an unrecognized operation is associated with the stage, this
 * splitter will return an empty split so that the job will continue without hanging or throwing an exception.
 */
@Component(DefaultStageSplitter.REF)
public class DefaultStageSplitter extends WfmSplitter implements StageSplitter {
    private static final Logger log = LoggerFactory.getLogger(DefaultStageSplitter.class);
    public static final String REF = "defaultStageSplitter";

    @Autowired
    @Qualifier(DetectionSplitter.REF)
    private StageSplitter detectionStageSplitter;

    @Autowired
    @Qualifier(MarkupStageSplitter.REF)
    private StageSplitter markupStageSplitter;

    @Autowired
    private InProgressBatchJobsService inProgressJobs;

    @Override
    public String getSplitterName() { return REF; }


    @Override
    public List<Message> performSplit(BatchJob job, Task task) {
        log.warn("[Job {}|{}|*] Stage {} calls an unsupported operation '{}'. No work will be performed in this stage.",
                 job.getId(), job.getCurrentTaskIndex(), job.getCurrentTaskIndex(),
                 task.getName());
        return new ArrayList<>();
    }


    @Override
    public final List<Message> wfmSplit(Exchange exchange) {
        BatchJob job = inProgressJobs.getJob(exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class));
        Task task = job.getTransientPipeline().getTask(job.getCurrentTaskIndex());
        ActionType actionType = job.getTransientPipeline()
                .getAlgorithm(job.getCurrentTaskIndex(), 0)
                .getActionType();
        log.info("[Job {}|{}|*] Stage {}/{} - Operation: {} - ActionType: {}.",
                 job.getId(),
                 job.getCurrentTaskIndex(),
                 job.getCurrentTaskIndex() + 1,
                 job.getTransientPipeline().getTaskCount(),
                 actionType,
                 actionType.name());

        if(job.isCancelled()) {
            // Check if this job has been cancelled prior to performing the split. If it has been, do not produce any work units.
            log.warn("[Job {}|{}|*] This job has been cancelled. No work will be performed in this stage.",
                     job.getId(), job.getCurrentTaskIndex());
            return new ArrayList<>(0);
        } else {
            switch (actionType) {
                case DETECTION:
                    return detectionStageSplitter.performSplit(job, task);
                case MARKUP:
                    return markupStageSplitter.performSplit(job, task);
                default:
                    return performSplit(job, task);
            }
        }
    }
}
