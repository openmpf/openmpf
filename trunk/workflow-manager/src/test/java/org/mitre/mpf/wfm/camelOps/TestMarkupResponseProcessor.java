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

package org.mitre.mpf.wfm.camelOps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.junit.Test;
import org.mitre.mpf.rest.api.MediaUri;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.buffers.Markup;
import org.mitre.mpf.wfm.camel.operations.markup.MarkupResponseProcessor;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.access.MarkupResultDao;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.mitre.mpf.wfm.data.entities.persistent.MarkupResult;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.MediaImpl;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.IssueSources;
import org.mitre.mpf.wfm.enums.MarkupStatusType;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.service.StorageService;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class TestMarkupResponseProcessor extends MockitoTest.Lenient {

    @InjectMocks
    private MarkupResponseProcessor _markupResponseProcessor;

    @Mock
    private InProgressBatchJobsService _mockInProgressJobs;

    @Mock
    private MarkupResultDao _mockMarkupResultDao;

    @Mock
    private StorageService _mockStorageService;

    private static final long TEST_JOB_ID = 1236;


    @Test
    public void testMarkupResponse() {
        Markup.MarkupResponse.Builder responseBuilder = Markup.MarkupResponse.newBuilder()
                .setHasError(false)
                .clearErrorMessage();
        MarkupResult markupResult = runMarkupProcessor(responseBuilder);
        assertNull(markupResult.getMessage());
        assertEquals(MarkupStatusType.COMPLETE, markupResult.getMarkupStatus());

        verify(_mockStorageService)
                .store(markupResult);
        verify(_mockInProgressJobs, never())
                .setJobStatus(anyLong(), any());
    }


    @Test
    public void canHandleMarkupError() {
        String errorMessage = "errorMessage1";
        Markup.MarkupResponse.Builder responseBuilder = Markup.MarkupResponse.newBuilder()
                .setHasError(true)
                .setErrorMessage(errorMessage);

        MarkupResult markupResult = runMarkupProcessor(responseBuilder);
        assertEquals(errorMessage, markupResult.getMessage());
        assertEquals(MarkupStatusType.FAILED, markupResult.getMarkupStatus());

        verify(_mockStorageService, never())
                .store(any());
        verify(_mockInProgressJobs)
                .addError(TEST_JOB_ID, 1532, IssueCodes.MARKUP, errorMessage,
                          IssueSources.MARKUP);
    }


    private MarkupResult runMarkupProcessor(Markup.MarkupResponse.Builder markupResponseBuilder) {
        long mediaId = 1532;
        int taskIndex = 4;

        Markup.MarkupResponse markupResponse = markupResponseBuilder
                .setMediaId(mediaId)
                .setOutputFilePath("output.txt")
                .build();

        JobPipelineElements dummyPipeline = mock(JobPipelineElements.class);
        when(dummyPipeline.getName())
                .thenReturn("TEST_MARKUP_PIPELINE");
        var markupAction = new Action("MARKUP", "desc", "MARKUP ALGO", List.of());
        when(dummyPipeline.getAction(taskIndex, 0))
                .thenReturn(markupAction);

        var mediaUri = MediaUri.create("file:///samples/meds1.jpg");
        Media media = new MediaImpl(mediaId, mediaUri, UriScheme.get(mediaUri),
                                    Paths.get(mediaUri.get()), Map.of(), Map.of(), List.of(),
                                    List.of(), List.of(), null, null);
        var job = mock(BatchJob.class);
        when(job.getId())
                .thenReturn(TEST_JOB_ID);
        when(job.getPipelineElements())
                .thenReturn(dummyPipeline);
        when(job.getMedia(mediaId))
                .thenReturn(media);
        when(job.getMedia())
                .thenReturn(List.of(media));
        when(job.getCurrentTaskIndex())
                .thenReturn(taskIndex);

        when(_mockInProgressJobs.containsJob(TEST_JOB_ID))
                .thenReturn(true);
        when(_mockInProgressJobs.getJob(TEST_JOB_ID))
                .thenReturn(job);

        Exchange exchange = TestUtil.createTestExchange();
        exchange.getIn().getHeaders().put(MpfHeaders.JOB_ID, TEST_JOB_ID);
        long processingTime = 878;
        exchange.getIn().setHeader(MpfHeaders.PROCESSING_TIME, processingTime);
        exchange.getIn().setBody(markupResponse);

        _markupResponseProcessor.process(exchange);

        ArgumentCaptor<MarkupResult> markupCaptor = ArgumentCaptor.forClass(MarkupResult.class);
        verify(_mockMarkupResultDao)
                .persist(markupCaptor.capture());

        MarkupResult markupResult = markupCaptor.getValue();
        assertEquals(TEST_JOB_ID, markupResult.getJobId());
        assertTrue(markupResult.getMarkupUri().startsWith("file:///"));
        assertTrue(markupResult.getMarkupUri().endsWith("/output.txt"));
        assertEquals(taskIndex, markupResult.getTaskIndex());
        assertEquals(0, markupResult.getActionIndex());
        assertEquals(mediaId, markupResult.getMediaId());
        assertEquals(0, markupResult.getMediaIndex());
        assertEquals(mediaUri.toString(), markupResult.getSourceUri());
        assertEquals("TEST_MARKUP_PIPELINE", markupResult.getPipeline());

        verify(_mockInProgressJobs)
            .addProcessingTime(TEST_JOB_ID, markupAction, processingTime);

        return markupResult;
    }
}
