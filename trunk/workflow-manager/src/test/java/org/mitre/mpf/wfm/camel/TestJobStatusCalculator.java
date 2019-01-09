/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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
import org.apache.camel.impl.DefaultMessage;
import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.transients.TransientJob;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class TestJobStatusCalculator {

    @InjectMocks
    private JobStatusCalculator jobStatusCalculator;

    @Mock
    private InProgressBatchJobsService inProgressJobs;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }


    @Test
    public void testCalculateStatusComplete() {
        verifyStateTransition(BatchJobStatusType.IN_PROGRESS, BatchJobStatusType.COMPLETE);
    }

    @Test
    public void testCalculateStatusErrors() {
        verifyStateTransition(BatchJobStatusType.IN_PROGRESS_ERRORS, BatchJobStatusType.COMPLETE_WITH_ERRORS);
    }

    @Test
    public void testCalculateStatusWarnings() {
        verifyStateTransition(BatchJobStatusType.IN_PROGRESS_WARNINGS, BatchJobStatusType.COMPLETE_WITH_WARNINGS);
    }


    private Exchange createExchange(long jobId, BatchJobStatusType initialStatus) {
        TransientJob job = mock(TransientJob.class);
        when(job.getStatus())
                .thenReturn(initialStatus);
        when(job.getId())
                .thenReturn(jobId);

        when(inProgressJobs.getJob(jobId))
                .thenReturn(job);


        Message inMessage = new DefaultMessage();
        inMessage.setHeader(MpfHeaders.JOB_ID, jobId);
        Exchange exchange = mock(Exchange.class);
        when(exchange.getIn())
                .thenReturn(inMessage);
        return exchange;
    }


    private void verifyStateTransition(BatchJobStatusType from, BatchJobStatusType to) {
        long jobId = 112235;
        Exchange exchange = createExchange(jobId, from);
        BatchJobStatusType finalState = jobStatusCalculator.calculateStatus(exchange);
        assertEquals(to, finalState);

        verify(inProgressJobs)
                .setJobStatus(jobId, to);
    }
}
