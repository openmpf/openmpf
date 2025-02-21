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

package org.mitre.mpf.wfm.businessrules;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.rest.api.JobCreationMediaData;
import org.mitre.mpf.rest.api.JobCreationMediaRange;
import org.mitre.mpf.rest.api.JobCreationMediaSelector;
import org.mitre.mpf.rest.api.JobCreationRequest;
import org.mitre.mpf.rest.api.MediaSelectorType;
import org.mitre.mpf.rest.api.TiesDbCheckStatus;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.ActionType;
import org.mitre.mpf.rest.api.pipelines.Algorithm;
import org.mitre.mpf.rest.api.pipelines.Pipeline;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.businessrules.impl.JobRequestServiceImpl;
import org.mitre.mpf.wfm.camel.routes.JobRouterRouteBuilder;
import org.mitre.mpf.wfm.camel.routes.MediaRetrieverRouteBuilder;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.access.JobRequestDao;
import org.mitre.mpf.wfm.data.access.MarkupResultDao;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJobImpl;
import org.mitre.mpf.wfm.data.entities.persistent.DetectionProcessingError;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.MediaImpl;
import org.mitre.mpf.wfm.data.entities.persistent.SystemPropertiesSnapshot;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.service.JobStatusBroadcaster;
import org.mitre.mpf.wfm.service.TiesDbBeforeJobCheckService;
import org.mitre.mpf.wfm.service.TiesDbCheckResult;
import org.mitre.mpf.wfm.service.WorkflowPropertyService;
import org.mitre.mpf.wfm.service.pipeline.PipelineService;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.JmsUtils;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.MediaRange;
import org.mitre.mpf.wfm.util.MediaTypeUtils;
import org.mitre.mpf.wfm.util.ObjectMapperFactory;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mockito.ArgumentCaptor;

public class TestJobRequestService {

    private final PropertiesUtil _mockPropertiesUtil = mock(PropertiesUtil.class);

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil
            = new AggregateJobPropertiesUtil(_mockPropertiesUtil, mock(WorkflowPropertyService.class));

    private final PipelineService _mockPipelineService = mock(PipelineService.class);

    private final JsonUtils _jsonUtils = new JsonUtils(ObjectMapperFactory.customObjectMapper());

    private final JmsUtils _mockJmsUtils = mock(JmsUtils.class);

    private final JobRequestDao _mockJobRequestDao = mock(JobRequestDao.class);

    private final JobStatusBroadcaster _mockJobStatusBroadcaster = mock(JobStatusBroadcaster.class);

    private final MediaTypeUtils _mockMediaTypeUtils = mock(MediaTypeUtils.class);

    private final InProgressBatchJobsService _inProgressJobs = new InProgressBatchJobsService(
            _mockPropertiesUtil, null, _mockJobRequestDao, _mockJobStatusBroadcaster,
            _mockMediaTypeUtils);

    private final MarkupResultDao _mockMarkupResultDao = mock(MarkupResultDao.class);

    private final TiesDbBeforeJobCheckService _mockTiesDbBeforeJobCheckService
                    = mock(TiesDbBeforeJobCheckService.class);

    private final ProducerTemplate _mockProducerTemplate = mock(ProducerTemplate.class);

    private final JobRequestService _jobRequestService = new JobRequestServiceImpl(
                    _mockPropertiesUtil, _aggregateJobPropertiesUtil, _mockPipelineService,
                    _jsonUtils, _mockJmsUtils, _inProgressJobs, _mockJobRequestDao,
                    _mockMarkupResultDao, _mockTiesDbBeforeJobCheckService, _mockProducerTemplate,
                    TestUtil.createConstraintValidator());

    @Rule
    public TemporaryFolder _temporaryFolder = new TemporaryFolder();

    @Before
    public void init() throws IOException {
        when(_mockPropertiesUtil.getTemporaryMediaDirectory())
                .thenReturn(_temporaryFolder.newFolder("temp-media"));
    }


    private static JobCreationRequest createTestJobCreationRequest() {
        return createTestJobCreationRequest(null);
    }

    private static JobCreationRequest createTestJobCreationRequest(Integer priority) {
        var jobCreationMedia1 = new JobCreationMediaData(
                "http://my_media1.mp4",
                Map.of("media_prop1", "media_val1"),
                Map.of(),
                List.of(new JobCreationMediaRange(0, 50), new JobCreationMediaRange(100, 300)),
                List.of(),
                List.of(),
                Optional.empty());

        var jobCreationMedia2 = new JobCreationMediaData(
                "http://my_media2.mp4",
                Map.of("media_prop2", "media_val2"),
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                Optional.empty());

        return new JobCreationRequest(
            List.of(jobCreationMedia1, jobCreationMedia2),
            Map.of("job_prop1", "job_val1"),
            Map.of("TEST ALGO", Map.of("algo_prop1", "algo_val1")),
            "external_id",
            "TEST PIPELINE",
            null,
            true,
            priority,
            "http://callback",
            "GET");
    }


    private static JobPipelineElements createJobPipelineElements() {
        var algorithm = new Algorithm("TEST ALGO", "desc", ActionType.DETECTION, "TEST",
                                      OptionalInt.empty(),
                                      new Algorithm.Requires(List.of()),
                                      new Algorithm.Provides(List.of(), List.of()),
                                      true, true);
        var action = new Action("TEST ACTION", "descr", algorithm.name(), List.of());
        var task = new Task("Test Task", "desc", List.of(action.name()));
        var pipeline = new Pipeline("TEST PIPELINE", "desc", List.of(task.name()));
        return new JobPipelineElements(pipeline, List.of(task), List.of(action), List.of(algorithm));
    }


    private static void assertSegmentBoundariesEqual(
            Collection<JobCreationMediaRange> creationBoundaries,
            Collection<MediaRange> jobBoundaries) {
        assertEquals(creationBoundaries.size(), jobBoundaries.size());

        var creationBoundariesIter = creationBoundaries
                .stream()
                .sorted(Comparator.comparingInt(JobCreationMediaRange::start)
                                .thenComparingInt(JobCreationMediaRange::stop))
                .iterator();

        var jobBoundariesIter = jobBoundaries
                .stream()
                .sorted()
                .iterator();

        while (jobBoundariesIter.hasNext()) {
            MediaRange jobBoundary = jobBoundariesIter.next();
            JobCreationMediaRange creationBoundary = creationBoundariesIter.next();
            assertEquals(jobBoundary.getStartInclusive(), creationBoundary.start());
            assertEquals(jobBoundary.getEndInclusive(), creationBoundary.stop());
        }
    }


    @Test
    public void canCreateJob() {
        int defaultPriority = 8;
        when(_mockPropertiesUtil.getJmsPriority())
                .thenReturn(defaultPriority);

        var pipelineElements = createJobPipelineElements();
        when(_mockPipelineService.getBatchPipelineElements("TEST PIPELINE"))
                .thenReturn(pipelineElements);

        var systemPropsSnapshot = new SystemPropertiesSnapshot(Map.of());
        when(_mockPropertiesUtil.createSystemPropertiesSnapshot())
                .thenReturn(systemPropsSnapshot);

        when(_mockTiesDbBeforeJobCheckService.checkTiesDbBeforeJob(any(), any(), any(), any()))
                .thenReturn(TiesDbCheckResult.noResult(TiesDbCheckStatus.NO_MATCH));

        when(_mockJobRequestDao.getNextId())
                .thenReturn(123L);

        var jobRequestEntityCaptor = ArgumentCaptor.forClass(JobRequest.class);
        when(_mockJobRequestDao.persist(jobRequestEntityCaptor.capture()))
                .thenAnswer(i -> i.getArgument(0));

        var jobCreationRequest = createTestJobCreationRequest();
        var creationResult = _jobRequestService.run(jobCreationRequest);
        var jobRequestEntity = jobRequestEntityCaptor.getValue();

        verify(_mockJobStatusBroadcaster)
                .broadcast(123, BatchJobStatusType.IN_PROGRESS);
        verifyNoMoreInteractions(_mockJobStatusBroadcaster);

        assertEquals(123, creationResult.jobId());
        assertEquals(123, jobRequestEntity.getId());
        assertEquals(BatchJobStatusType.IN_PROGRESS, jobRequestEntity.getStatus());
        assertEquals("TEST PIPELINE", jobRequestEntity.getPipeline());
        assertEquals(defaultPriority, jobRequestEntity.getPriority());

        verify(_mockProducerTemplate)
                .sendBodyAndHeaders(eq(MediaRetrieverRouteBuilder.ENTRY_POINT), eq(ExchangePattern.InOnly),
                                    isNull(), eq(Map.of(MpfHeaders.JOB_ID, 123L,
                                                        MpfHeaders.JMS_PRIORITY, defaultPriority)));
        verify(_mockJobRequestDao)
                .updateStatus(123, BatchJobStatusType.IN_PROGRESS);


        BatchJob job = _inProgressJobs.getJob(123);
        assertEquals(123,
                     _jsonUtils.deserialize(jobRequestEntity.getJob(), BatchJob.class).getId());

        assertEquals(123, job.getId());
        assertEquals(jobCreationRequest.externalId(), job.getExternalId().get());
        assertEquals(-1, job.getCurrentTaskIndex());
        assertSame(systemPropsSnapshot, job.getSystemPropertiesSnapshot());
        assertSame(pipelineElements, job.getPipelineElements());
        assertEquals(defaultPriority, job.getPriority());
        assertEquals(jobCreationRequest.callbackURL(), job.getCallbackUrl().get());
        assertEquals(jobCreationRequest.callbackMethod(), job.getCallbackMethod().get());

        assertEquals(2, job.getMedia().size());
        Media media1 = job.getMedia()
                .stream()
                .filter(m -> m.getUri().equals("http://my_media1.mp4"))
                .findAny()
                .get();
        assertEquals("media_val1", media1.getMediaSpecificProperty("media_prop1"));

        Media media2 = job.getMedia()
                .stream()
                .filter(m -> m.getUri().equals("http://my_media2.mp4"))
                .findAny()
                .get();
        assertEquals("media_val2", media2.getMediaSpecificProperty("media_prop2"));

        assertEquals(jobCreationRequest.jobProperties(), job.getJobProperties());
        assertEquals(jobCreationRequest.algorithmProperties(), job.getOverriddenAlgorithmProperties());

        assertFalse(media1.getFrameRanges().isEmpty());
        assertTrue(media1.getTimeRanges().isEmpty());

        assertTrue(media2.getFrameRanges().isEmpty());
        assertTrue(media2.getTimeRanges().isEmpty());

        var jobCreationMedia1 = jobCreationRequest.media()
                .stream()
                .filter(m -> m.mediaUri().equals("http://my_media1.mp4"))
                .findAny()
                .orElseThrow();

        assertSegmentBoundariesEqual(jobCreationMedia1.frameRanges(),
                                     media1.getFrameRanges());
    }


    @Test
    public void canResubmitJob() throws IOException {
        var originalJobPipelineElements = createJobPipelineElements();
        var originalJob = new BatchJobImpl(
                321,
                "external_id",
                new SystemPropertiesSnapshot(Map.of("my.property", "5")),
                originalJobPipelineElements,
                3,
                "http://callback",
                "POST",
                List.of(new MediaImpl(567, "http://media.mp4", UriScheme.HTTP, Paths.get("temp"),
                                      Map.of("media_prop1", "media_val1"), Map.of(),
                                      List.of(), List.of(), List.of(), null, "error")),
                Map.of("job_prop1", "job_val1"),
                Map.of("TEST ALGO" , Map.of("algo_prop1", "algo_val1")),
                false);
        originalJob.addDetectionProcessingError(
            new DetectionProcessingError(321, 1, 0, 0, 0, 10, 0, 10,
                                             "error", "errorMessage"));
        originalJob.addWarning(1, "TEST ALGO", null, "warning");


        var jobRequestEntity = new JobRequest();
        jobRequestEntity.setId(321);
        jobRequestEntity.setStatus(BatchJobStatusType.COMPLETE);
        jobRequestEntity.setJob(_jsonUtils.serialize(originalJob));

        when(_mockJobRequestDao.findById(321))
                .thenReturn(jobRequestEntity);

        var newJobPipelineElements = createJobPipelineElements();
        when(_mockPipelineService.getBatchPipelineElements(originalJobPipelineElements.getName()))
                .thenReturn(newJobPipelineElements);

        when(_mockPropertiesUtil.createSystemPropertiesSnapshot())
                .thenReturn(new SystemPropertiesSnapshot(Map.of("my.property", "10")));

        when(_mockJobRequestDao.persist(jobRequestEntity))
                .thenReturn(jobRequestEntity);

        File artifactsDir = _temporaryFolder.newFolder("artifacts");
        Files.writeString(artifactsDir.toPath().resolve("artifact.bin"), "hello world");
        when(_mockPropertiesUtil.getJobArtifactsDirectory(anyLong()))
                .thenReturn(artifactsDir);

        File outputObjectsDir = _temporaryFolder.newFolder("output_objects");
        Files.writeString(outputObjectsDir.toPath().resolve("outputobject.json"), "hello world");
        when(_mockPropertiesUtil.getJobOutputObjectsDirectory(anyLong()))
                .thenReturn(outputObjectsDir);

        File markupDir = _temporaryFolder.newFolder("markup");
        Files.writeString(markupDir.toPath().resolve("markup.bin"), "hello world");
        when(_mockPropertiesUtil.getJobMarkupDirectory(anyLong()))
                .thenReturn(markupDir);


        _jobRequestService.resubmit(321, -1);

        verify(_mockJobRequestDao)
                .persist(jobRequestEntity);

        verify(_mockJobStatusBroadcaster)
                .broadcast(321, BatchJobStatusType.IN_PROGRESS);
        verifyNoMoreInteractions(_mockJobStatusBroadcaster);

        verify(_mockProducerTemplate)
                .sendBodyAndHeaders(eq(MediaRetrieverRouteBuilder.ENTRY_POINT), eq(ExchangePattern.InOnly),
                                    isNull(), eq(Map.of(MpfHeaders.JOB_ID, 321L,
                                                        MpfHeaders.JMS_PRIORITY, 3)));

        verify(_mockJobRequestDao)
                .updateStatus(321, BatchJobStatusType.IN_PROGRESS);


        BatchJob newJob = _inProgressJobs.getJob(321);
        assertEquals(321,
                     _jsonUtils.deserialize(jobRequestEntity.getJob(), BatchJob.class).getId());
        assertEquals(BatchJobStatusType.IN_PROGRESS, jobRequestEntity.getStatus());

        assertEquals(BatchJobStatusType.IN_PROGRESS, newJob.getStatus());
        assertNotSame(newJob.getPipelineElements(), originalJob.getPipelineElements());
        assertEquals(newJob.getPipelineElements().getName(), originalJob.getPipelineElements().getName());
        assertEquals("10", newJob.getSystemPropertiesSnapshot().lookup("my.property"));
        assertEquals(-1, newJob.getCurrentTaskIndex());
        assertEquals(newJob.getExternalId().get(), originalJob.getExternalId().get());
        assertEquals(newJob.getPriority(), originalJob.getPriority());
        assertEquals(newJob.getOverriddenAlgorithmProperties(), originalJob.getOverriddenAlgorithmProperties());
        assertEquals(newJob.getJobProperties(), originalJob.getJobProperties());
        assertEquals(newJob.getCallbackUrl().get(), originalJob.getCallbackUrl().get());
        assertEquals(newJob.getCallbackMethod().get(), originalJob.getCallbackMethod().get());
        assertTrue(newJob.getWarnings().isEmpty());
        assertTrue(newJob.getDetectionProcessingErrors().isEmpty());

        assertEquals(newJob.getMedia().size(), originalJob.getMedia().size());
        assertEquals(1, newJob.getMedia().size());

        Media newMedia = newJob.getMedia().iterator().next();
        Media originalMedia = originalJob.getMedia().iterator().next();
        assertEquals(newMedia.getUri(), originalMedia.getUri());
        assertEquals(newMedia.getMediaSpecificProperties(), originalMedia.getMediaSpecificProperties());
        assertNull(newMedia.getErrorMessage());

        assertFalse(Files.exists(artifactsDir.toPath()));
        assertFalse(Files.exists(outputObjectsDir.toPath()));
        assertFalse(Files.exists(markupDir.toPath()));

        verifyNoInteractions(_mockTiesDbBeforeJobCheckService);
    }



    @Test
    public void canCancel() throws Exception {
        long jobId = 5465;
        var jobRequestEntity = new JobRequest() {
            @Override
            public long getId() {
                return jobId;
            }
        };

        _inProgressJobs.addJob(
                jobId, null, new SystemPropertiesSnapshot(Map.of()), createJobPipelineElements(),
                3, null, null,
                List.of(new MediaImpl(323, "http://example.mp4", UriScheme.HTTP, Path.of("temp"),
                                      Map.of(), Map.of(), List.of(), List.of(), List.of(), null, null)),
                Map.of(), Map.of(), false);

        jobRequestEntity.setStatus(BatchJobStatusType.IN_PROGRESS);

        when(_mockJobRequestDao.findById(jobId))
                .thenReturn(jobRequestEntity);


        _jobRequestService.cancel(jobId);

        assertTrue(_inProgressJobs.getJob(jobId).isCancelled());

        verify(_mockJmsUtils)
                .cancelDetectionJob(jobId);


        var persistedRequestCaptor = ArgumentCaptor.forClass(JobRequest.class);
        verify(_mockJobRequestDao)
                .persist(persistedRequestCaptor.capture());

        BatchJob job = _inProgressJobs.getJob(jobId);
        assertTrue(job.isCancelled());

        JobRequest persistedRequest = persistedRequestCaptor.getValue();
        assertEquals(BatchJobStatusType.CANCELLING, persistedRequest.getStatus());
        assertArrayEquals(persistedRequest.getJob(), _jsonUtils.serialize(job));
    }


    @Test
    public void testTiesDbCheck() {
        var pipelineElements = createJobPipelineElements();
        when(_mockPipelineService.getBatchPipelineElements("TEST PIPELINE"))
                .thenReturn(pipelineElements);

        var systemPropsSnapshot = new SystemPropertiesSnapshot(Map.of());
        when(_mockPropertiesUtil.createSystemPropertiesSnapshot())
                .thenReturn(systemPropsSnapshot);

        when(_mockJobRequestDao.getNextId())
                .thenReturn(123L);

        var jobRequestEntityCaptor = ArgumentCaptor.forClass(JobRequest.class);
        when(_mockJobRequestDao.persist(jobRequestEntityCaptor.capture()))
                .thenAnswer(i -> i.getArgument(0));

        var jobCreationRequest = createTestJobCreationRequest(5);

        var tiesDbCheckResult = new TiesDbCheckResult(
                TiesDbCheckStatus.FOUND_MATCH,
                Optional.of(new TiesDbCheckResult.CheckInfo(
                        URI.create("file:///opt/mpf/share/1.json"),
                        BatchJobStatusType.COMPLETE,
                        Instant.ofEpochSecond(1667480850),
                        false)));

        when(_mockTiesDbBeforeJobCheckService.checkTiesDbBeforeJob(
                        eq(jobCreationRequest),
                        eq(systemPropsSnapshot),
                        argThat(x -> x.size() == 2),
                        eq(pipelineElements)))
                .thenReturn(tiesDbCheckResult);

        var creationResult = _jobRequestService.run(jobCreationRequest);

        assertEquals(tiesDbCheckResult, creationResult.tiesDbCheckResult());

        var expectedHeaders = Map.<String, Object>of(
                MpfHeaders.JOB_ID, 123L,
                MpfHeaders.JMS_PRIORITY, 5,
                MpfHeaders.JOB_COMPLETE, true,
                MpfHeaders.OUTPUT_OBJECT_URI_FROM_TIES_DB, "file:///opt/mpf/share/1.json");

        // Verify job is routed past media inspection.
        verify(_mockProducerTemplate)
                .sendBodyAndHeaders(
                        JobRouterRouteBuilder.ENTRY_POINT, ExchangePattern.InOnly, null,
                        expectedHeaders);
        verifyNoMoreInteractions(_mockProducerTemplate);

        // Verify job data is persisted in the database like a regular job.
        var jobRequestEntity = jobRequestEntityCaptor.getValue();
        assertEquals(123L, jobRequestEntity.getId());
        assertEquals(BatchJobStatusType.IN_PROGRESS, jobRequestEntity.getStatus());
        assertTrue(Instant.now().compareTo(jobRequestEntity.getTimeReceived()) >= 0);
        assertNull(jobRequestEntity.getTimeCompleted());
    }


    @Test
    public void testValidator() {
        var selector = new JobCreationMediaSelector(
                "", MediaSelectorType.JSON_PATH, Map.of(), "out");
        var media = new JobCreationMediaData(null, null, null, null, null, List.of(selector), Optional.of("test"));
        var job = new JobCreationRequest(
                List.of(media), null, null, null, null, null, null, null, null, null);
        assertThatExceptionOfType(WfmProcessingException.class)
            .isThrownBy(() -> _jobRequestService.run(job))
            .withMessageContaining("media[0].mediaSelectors[0].expression=\"\": may not be empty");
        verifyNoInteractions(_mockProducerTemplate);
    }


    @Test
    public void mediaSelectorsActionIsOptionalWhenSingleAction() {
        var selector = new JobCreationMediaSelector(
                "<expr>", MediaSelectorType.JSON_PATH, Map.of(), "out");
        var pipeline = createJobPipelineElements();
        var job = setUpMediaSelectorsActionValidation(pipeline, List.of(selector), null);
        _jobRequestService.run(job);
        verifyJobSentToCamel();
    }

    @Test
    public void mediaSelectorsActionIsRequiredWhenMultipleActions() {
        var selector = new JobCreationMediaSelector(
                "<expr>", MediaSelectorType.JSON_PATH, Map.of(), "out");
        var pipeline = createMultiStagePipelineElements();
        var job = setUpMediaSelectorsActionValidation(
                pipeline, List.of(selector), "TEST ACTION1");
        _jobRequestService.run(job);
        verifyJobSentToCamel();
    }


    private void verifyJobSentToCamel() {
        verify(_mockProducerTemplate)
                .sendBodyAndHeaders(
                        eq(MediaRetrieverRouteBuilder.ENTRY_POINT),
                        eq(ExchangePattern.InOnly),
                        isNull(),
                        TestUtil.nonEmptyMap());
    }


    @Test
    public void missingMediaSelectorsActionIsErrorWhenMultipleActions() {
        var selector = new JobCreationMediaSelector(
                "<expr>", MediaSelectorType.JSON_PATH, Map.of(), "out");
        var pipeline = createMultiStagePipelineElements();
        var job = setUpMediaSelectorsActionValidation(pipeline, List.of(selector), null);
        assertThatExceptionOfType(WfmProcessingException.class)
            .isThrownBy(() -> _jobRequestService.run(job))
            .withMessageContaining("\"mediaSelectorsOutputAction\" was not set");
        verifyNoInteractions(_mockProducerTemplate);
    }


    @Test
    public void isErrorWhenMediaSelectorsActionNotInPipeline() {
        var selector = new JobCreationMediaSelector(
                "<expr>", MediaSelectorType.JSON_PATH, Map.of(), "out");
        var pipeline = createMultiStagePipelineElements();
        var job = setUpMediaSelectorsActionValidation(
                pipeline, List.of(selector), "<MISSING ACTION>");
        assertThatExceptionOfType(WfmProcessingException.class)
            .isThrownBy(() -> _jobRequestService.run(job))
            .withMessageContaining("pipeline does not contain an action with that name");
        verifyNoInteractions(_mockProducerTemplate);
    }


    private JobCreationRequest setUpMediaSelectorsActionValidation(
            JobPipelineElements pipeline,
            List<JobCreationMediaSelector> mediaSelectors,
            String outputAction) {
        URI mediaUri;
        try {
            mediaUri = _temporaryFolder.newFile().toURI();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        var media = new JobCreationMediaData(
                mediaUri.toString(), null, null, null, null,
                mediaSelectors,
                Optional.ofNullable(outputAction));

        var job = new JobCreationRequest(
                List.of(media), null, null, null, pipeline.getName(), null, null, null, null, null);

        when(_mockPipelineService.getBatchPipelineElements(pipeline.getName()))
            .thenReturn(pipeline);

        when(_mockTiesDbBeforeJobCheckService.checkTiesDbBeforeJob(eq(job), any(), any(), any()))
                .thenReturn(TiesDbCheckResult.noResult(TiesDbCheckStatus.NOT_REQUESTED));

        when(_mockJobRequestDao.persist(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        return job;
    }

    private static JobPipelineElements createMultiStagePipelineElements() {
        var algos = new ArrayList<Algorithm>();
        var actions = new ArrayList<Action>();
        var tasks = new ArrayList<Task>();

        for (int i = 1; i <= 3; i++) {
            var algorithm = new Algorithm("TEST ALGO" + i, "desc", ActionType.DETECTION, "TEST",
                                        OptionalInt.empty(),
                                        new Algorithm.Requires(List.of()),
                                        new Algorithm.Provides(List.of(), List.of()),
                                        true, true);
            algos.add(algorithm);
            var action = new Action("TEST ACTION" + i, "desc", algorithm.name(), List.of());
            actions.add(action);
            var task = new Task("Test Task" + i, "desc", List.of(action.name()));
            tasks.add(task);
        }
        var taskNames = tasks.stream().map(Task::name).toList();
        var pipeline = new Pipeline("TEST PIPELINE", "desc", taskNames);
        return new JobPipelineElements(pipeline, tasks, actions, algos);
    }
}
