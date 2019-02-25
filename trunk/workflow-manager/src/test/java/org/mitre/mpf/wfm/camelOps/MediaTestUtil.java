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


package org.mitre.mpf.wfm.camelOps;

import com.google.common.collect.ImmutableMap;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultMessage;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.transients.TransientJob;
import org.mitre.mpf.wfm.data.entities.transients.TransientMediaImpl;
import org.mitre.mpf.wfm.enums.MpfHeaders;

import static org.mitre.mpf.test.TestUtil.nonBlank;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class MediaTestUtil {


    public static Exchange setupExchange(long jobId, TransientMediaImpl media,
                                         InProgressBatchJobsService mockInProgressJobs) {
        TransientJob job = mock(TransientJob.class);
        when(job.getMedia(media.getId()))
                .thenReturn(media);
        when(job.getOverriddenJobProperties())
                .thenReturn(ImmutableMap.of());
        when(mockInProgressJobs.getJob(jobId))
                .thenReturn(job);

        Message inMessage = new DefaultMessage();
        inMessage.setHeader(MpfHeaders.JOB_ID, jobId);
        inMessage.setHeader(MpfHeaders.MEDIA_ID, media.getId());

        Message outMessage = new DefaultMessage();

        Exchange exchange = mock(Exchange.class);
        when(exchange.getIn())
                .thenReturn(inMessage);
        when(exchange.getOut())
                .thenReturn(outMessage);

        doAnswer(invocation -> {
            media.setFailed(true);
            return null;
        }).when(mockInProgressJobs)
                .addMediaError(eq(jobId), eq(media.getId()), nonBlank());

        return exchange;
    }


    private MediaTestUtil() {
    }
}
