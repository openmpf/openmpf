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

package org.mitre.mpf.wfm.camelOps;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultMessage;
import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.camel.operations.mediainspection.MediaInspectionSplitter;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.transients.TransientJob;
import org.mitre.mpf.wfm.data.entities.transients.TransientJobImpl;
import org.mitre.mpf.wfm.data.entities.transients.TransientMediaImpl;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class TestMediaInspectionSplitter {

    @InjectMocks
    private MediaInspectionSplitter mediaInspectionSplitter;

    @Mock
    private InProgressBatchJobsService mockInProgressJobs;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }


    @Test
    public void testMediaInspectionSplitter() throws Exception {
        final long jobId = 54328;

        Message inMessage = new DefaultMessage();
        inMessage.setHeader(MpfHeaders.JOB_ID, jobId);
        Exchange exchange = mock(Exchange.class);
        when(exchange.getIn())
                .thenReturn(inMessage);

        final String testExternalId = "externID";

        final long testMediaId = 123456;
        URI testURI = TestUtil.findFile("/samples/new_face_video.avi");
        TransientMediaImpl testMedia = new TransientMediaImpl(
                testMediaId, testURI.toString(), UriScheme.FILE, Paths.get(testURI), Collections.emptyMap(),
                null);

        TransientJob testJob = new TransientJobImpl(
                jobId,
                testExternalId,
                null,
                null,
                5,
                false,
                null,
                null,
                Collections.singletonList(testMedia),
                Collections.emptyMap(),
                Collections.emptyMap());
        when(mockInProgressJobs.getJob(jobId))
                .thenReturn(testJob);

        List<Message> responseList = mediaInspectionSplitter.split(exchange);
        assertEquals(1, responseList.size());
        Message response = responseList.get(0);
        assertEquals(jobId, (long) response.getHeader(MpfHeaders.JOB_ID, Long.class));
        assertEquals(testMediaId, (long) response.getHeader(MpfHeaders.MEDIA_ID, Long.class));
    }
}
