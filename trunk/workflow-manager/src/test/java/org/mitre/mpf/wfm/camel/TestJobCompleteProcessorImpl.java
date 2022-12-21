package org.mitre.mpf.wfm.camel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Optional;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.message.BasicHttpResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
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
import org.mitre.mpf.wfm.service.JobStatusBroadcaster;
import org.mitre.mpf.wfm.service.StorageService;
import org.mitre.mpf.wfm.service.TiesDbService;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.CallbackStatus;
import org.mitre.mpf.wfm.util.HttpClientUtils;
import org.mitre.mpf.wfm.util.JmsUtils;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TestJobCompleteProcessorImpl {

    private AutoCloseable _closeable;

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

    @InjectMocks
    private JobCompleteProcessorImpl _jobCompleteProcessorImpl;

    @Rule
    public TemporaryFolder _tempDir = new TemporaryFolder();


    @Before
    public void init() throws IOException {
        _closeable = MockitoAnnotations.openMocks(this);
        when(_mockPropertiesUtil.getJobMarkupDirectory(anyLong()))
            .thenReturn(_tempDir.newFolder("markup"));

        when(_mockPropertiesUtil.getJobArtifactsDirectory(anyLong()))
            .thenReturn(_tempDir.newFolder("artifacts"));

        when(_mockPropertiesUtil.getJobOutputObjectsDirectory(anyLong()))
            .thenReturn(_tempDir.newFolder("output-objects"));

        when(_mockPropertiesUtil.getHttpCallbackRetryCount())
            .thenReturn(3);

        when(_mockPropertiesUtil.getExportedJobId(anyLong()))
            .thenAnswer(inv -> "test-" + inv.getArgument(0));
    }

    @After
    public void close() throws Exception {
        _closeable.close();
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
        when(job.getStatus())
            .thenReturn(BatchJobStatusType.IN_PROGRESS);
        var callbackUrl = "http://localhost:2000/callback";
        when(job.getCallbackUrl())
            .thenReturn(Optional.of(callbackUrl));
        when(job.getCallbackMethod())
            .thenReturn(Optional.of("GET"));

        when(_mockInProgressBatchJobs.getJob(jobId))
            .thenReturn(job);

        var serializedJob = new byte[] {1, 2, 3};
        when(_mockJsonUtils.serialize(job))
            .thenReturn(serializedJob);

        var callbackRequestCaptor = ArgumentCaptor.forClass(HttpGet.class);
        var httpResponseFuture = ThreadUtil.<HttpResponse>newFuture();
        when(_mockHttpClientUtils.executeRequest(callbackRequestCaptor.capture(), eq(3)))
            .thenReturn(httpResponseFuture);

        var notificationFuture = ThreadUtil.<Long>newFuture();
        _jobCompleteProcessorImpl.subscribe(new NotificationConsumer<>() {
            public void onNotification(Object source, JobCompleteNotification notification) {
                notificationFuture.complete(notification.getJobId());
            }
        });

        _jobCompleteProcessorImpl.wfmProcess(exchange);

        assertNotNull(jobRequestEntity.getTimeCompleted());
        assertEquals(outputUri, jobRequestEntity.getOutputObjectPath());
        assertEquals(BatchJobStatusType.COMPLETE, jobRequestEntity.getStatus());
        assertEquals(serializedJob, jobRequestEntity.getJob());

        verify(_mockJobProgressStore)
            .setJobProgress(jobId, 100);

        verify(_mockInProgressBatchJobs)
            .setJobStatus(jobId, BatchJobStatusType.COMPLETE);

        verify(_mockInProgressBatchJobs)
            .setCallbacksInProgress(jobId);
        verify(_mockJobStatusBroadcaster)
            .callbackStatusChanged(jobId, CallbackStatus.inProgress());

        verify(_mockJobRequestDao)
            .persist(jobRequestEntity);

        verify(_mockJobStatusBroadcaster)
            .broadcast(eq(jobId), eq(100.0), any(BatchJobStatusType.class));

        verify(_mockJmsUtils)
            .destroyCancellationRoutes(jobId);

        var callbackRequest = callbackRequestCaptor.getValue();
        assertTrue(callbackRequest.getURI().toString().contains(String.valueOf(jobId)));

        assertFalse(notificationFuture.isDone());
        httpResponseFuture.complete(new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK"));
        assertEquals(jobId, (long) notificationFuture.join());

        verify(_mockInProgressBatchJobs)
            .clearJob(jobId);
        verify(_mockJobProgressStore)
            .removeJob(jobId);

        verifyNoInteractions(
                _mockMarkupResultDao,
                _mockStorageService,
                _mockCensorPropertiesService,
                _mockAggregateJobPropertiesUtil,
                _mockTiesDbService);
    }
}
