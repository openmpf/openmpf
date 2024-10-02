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

package org.mitre.mpf.wfm.camel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.message.BasicHttpResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.access.JobRequestDao;
import org.mitre.mpf.wfm.data.access.MarkupResultDao;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.event.JobCompleteNotification;
import org.mitre.mpf.wfm.event.JobProgress;
import org.mitre.mpf.wfm.event.NotificationConsumer;
import org.mitre.mpf.wfm.service.CensorPropertiesService;
import org.mitre.mpf.wfm.service.JobCompleteCallbackService;
import org.mitre.mpf.wfm.service.JobStatusBroadcaster;
import org.mitre.mpf.wfm.service.StorageService;
import org.mitre.mpf.wfm.service.TiesDbBeforeJobCheckService;
import org.mitre.mpf.wfm.service.TiesDbService;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.CallbackStatus;
import org.mitre.mpf.wfm.util.HttpClientUtils;
import org.mitre.mpf.wfm.util.JmsUtils;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class TestJobCompleteProcessorImpl extends MockitoTest.Strict {

    @Mock
    private JobRequestDao _mockJobRequestDao;

    @Mock
    private MarkupResultDao _mockMarkupResultDao;

    @Mock
    private PropertiesUtil _mockPropertiesUtil;

    @Mock
    private JsonUtils _mockJsonUtils;

    @Mock
    private InProgressBatchJobsService _mockInProgressBatchJobs;

    @Mock
    private JobProgress _mockJobProgressStore;

    @Mock
    private StorageService _mockStorageService;

    @Mock
    private JobStatusBroadcaster _mockJobStatusBroadcaster;

    @Mock
    private CensorPropertiesService _mockCensorPropertiesService;

    @Mock
    private AggregateJobPropertiesUtil _mockAggregateJobPropertiesUtil;

    @Mock
    private HttpClientUtils _mockHttpClientUtils;

    @Mock
    private JmsUtils _mockJmsUtils;

    @Mock
    private TiesDbService _mockTiesDbService;

    @Mock
    private TiesDbBeforeJobCheckService _mockTiesDbBeforeJobCheckService;

    @Mock
    private JobCompleteCallbackService _mockJobCompleteCallbackService;

    @InjectMocks
    private JobCompleteProcessorImpl _jobCompleteProcessorImpl;


    @Rule
    public TemporaryFolder _tempDir = new TemporaryFolder();


    @Before
    public void init() throws IOException {
        when(_mockPropertiesUtil.getJobMarkupDirectory(anyLong()))
            .thenReturn(_tempDir.newFolder("markup"));

        when(_mockPropertiesUtil.getJobArtifactsDirectory(anyLong()))
            .thenReturn(_tempDir.newFolder("artifacts"));

        when(_mockPropertiesUtil.getJobOutputObjectsDirectory(anyLong()))
            .thenReturn(_tempDir.newFolder("output-objects"));
    }


    @Test
    public void testSkippedJobDueToTiesDbCheck() throws Exception {
        var exchange = TestUtil.createTestExchange();
        long jobId = 123;
        exchange.getIn().setHeader(MpfHeaders.JOB_ID, jobId);
        var outputUri = "file:///opt/mpf/share/output/1.json";
        exchange.getIn().setHeader(MpfHeaders.OUTPUT_OBJECT_URI_FROM_TIES_DB, outputUri);

        var jobRequestEntity = new JobRequest();
        jobRequestEntity.setId(jobId);
        when(_mockJobRequestDao.findById(jobId))
            .thenReturn(jobRequestEntity);

        var job = mock(BatchJob.class);
        when(job.getId())
            .thenReturn(jobId);

        // When a job is skipped because it is in TiesDb, we should use the status from TiesDb.
        // Use IN_PROGRESS as the status in this test because when a job is not skipped
        // the status will change to COMPLETE.
        when(job.getStatus())
            .thenReturn(BatchJobStatusType.IN_PROGRESS);
        var callbackUrl = "http://localhost:2000/callback";
        when(job.getCallbackUrl())
            .thenReturn(Optional.of(callbackUrl));

        when(_mockInProgressBatchJobs.getJob(jobId))
            .thenReturn(job);

        var serializedJob = new byte[] {1, 2, 3};
        when(_mockJsonUtils.serialize(job))
            .thenReturn(serializedJob);

        var newOutputUri = new URI("http://example.com/bucket/key");
        when(_mockTiesDbBeforeJobCheckService.updateOutputObject(
                job, new URI(outputUri), jobRequestEntity))
            .thenReturn(newOutputUri);

        var httpResponseFuture = ThreadUtil.<HttpResponse>newFuture();
        when(_mockJobCompleteCallbackService.sendCallback(job, newOutputUri))
            .thenReturn(httpResponseFuture);

        var notificationFuture = ThreadUtil.<Long>newFuture();
        _jobCompleteProcessorImpl.subscribe(new NotificationConsumer<>() {
            public void onNotification(Object source, JobCompleteNotification notification) {
                notificationFuture.complete(notification.getJobId());
            }
        });

        _jobCompleteProcessorImpl.wfmProcess(exchange);

        assertNotNull(jobRequestEntity.getTimeCompleted());
        assertEquals(newOutputUri.toString(), jobRequestEntity.getOutputObjectPath());
        assertEquals(BatchJobStatusType.IN_PROGRESS, jobRequestEntity.getStatus());
        assertEquals(serializedJob, jobRequestEntity.getJob());

        verify(_mockJobProgressStore)
            .setJobProgress(jobId, 100);

        verify(_mockInProgressBatchJobs)
            .setJobStatus(jobId, BatchJobStatusType.IN_PROGRESS);

        verify(_mockInProgressBatchJobs)
            .setCallbacksInProgress(jobId);
        verify(_mockJobStatusBroadcaster)
            .callbackStatusChanged(jobId, CallbackStatus.inProgress());

        verify(_mockJobRequestDao)
            .persist(jobRequestEntity);

        verify(_mockJobStatusBroadcaster)
            .broadcast(
                eq(jobId), eq(100.0), any(BatchJobStatusType.class),
                eq(jobRequestEntity.getTimeCompleted()));

        verify(_mockJobStatusBroadcaster)
            .tiesDbStatusChanged(jobId, jobRequestEntity.getTiesDbStatus());

        verify(_mockJmsUtils)
            .destroyDetectionCancellationRoutes(jobId);


        assertFalse(notificationFuture.isDone());
        httpResponseFuture.complete(new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK"));
        assertEquals(jobId, (long) notificationFuture.join());

        verify(_mockJobRequestDao)
            .setCallbackSuccessful(jobId);

        verify(_mockInProgressBatchJobs)
            .clearJob(jobId);
        verify(_mockJobProgressStore)
            .removeJob(jobId);

        verifyNoInteractions(
                _mockMarkupResultDao,
                _mockStorageService,
                _mockCensorPropertiesService,
                _mockTiesDbService);
    }
}
