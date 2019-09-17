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
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The Job Status Calculator is a tool to calculate the terminal status of a job.
 */
@Component(JobStatusCalculator.REF)
public class JobStatusCalculator {
    public static final String REF = "jobStatusCalculator";

    @Autowired
    private InProgressBatchJobsService inProgressJobs;

    /**
     * Calculates the terminal status of a batch job
     * @param exchange  An incoming job exchange
     * @return  The terminal JobStatus for the batch job.
     * @throws WfmProcessingException
     */
    public BatchJobStatusType calculateStatus(Exchange exchange) throws WfmProcessingException {
        BatchJob job = inProgressJobs.getJob(exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class));
        BatchJobStatusType initialStatus = job.getStatus();
        BatchJobStatusType newStatus = nextStatus(initialStatus);
        inProgressJobs.setJobStatus(job.getId(), newStatus);
        return newStatus;
    }


    private static BatchJobStatusType nextStatus(BatchJobStatusType initialStatus) {
        switch (initialStatus) {
            case ERROR:
            case UNKNOWN:
            case COMPLETE_WITH_ERRORS:
            case COMPLETE_WITH_WARNINGS:
                return initialStatus;
            case IN_PROGRESS_WARNINGS:
                return BatchJobStatusType.COMPLETE_WITH_WARNINGS;
            case IN_PROGRESS_ERRORS:
                return BatchJobStatusType.COMPLETE_WITH_ERRORS;
            case CANCELLING:
                return BatchJobStatusType.CANCELLED;
            default:
                return BatchJobStatusType.COMPLETE;
        }
    }
}
