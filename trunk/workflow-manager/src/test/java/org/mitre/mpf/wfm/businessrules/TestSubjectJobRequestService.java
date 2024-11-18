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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import javax.validation.Validator;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Test;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.rest.api.subject.CallbackMethod;
import org.mitre.mpf.rest.api.subject.SubjectJobRequest;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.buffers.SubjectProtobuf;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.access.SubjectComponentRepo;
import org.mitre.mpf.wfm.data.access.SubjectJobRepo;
import org.mitre.mpf.wfm.data.entities.persistent.DbSubjectJob;
import org.mitre.mpf.wfm.service.PastJobResultsService;
import org.mitre.mpf.wfm.service.SubjectJobResultsService;
import org.mitre.mpf.wfm.util.JmsUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.util.FieldUtils;
import org.springframework.transaction.support.TransactionTemplate;

public class TestSubjectJobRequestService extends MockitoTest.Strict {

    private static final Duration FUTURE_DURATION = TestUtil.FUTURE_DURATION;

    @Mock
    private SubjectJobRepo _mockSubjectJobRepo;

    @Mock
    private SubjectJobResultsService _mockSubjectJobResultsService;

    @Mock
    private InProgressBatchJobsService _mockInProgressDetectionJobsService;

    @Mock
    private PastJobResultsService _mockPastJobResultsService;

    @Mock
    private SubjectJobToProtobufConverter _mockSubjectJobToProtobufConverter;

    @Mock
    private SubjectComponentRepo _mockSubjectComponentRepo;

    @Mock
    private Validator _mockValidator;

    @Mock
    private JmsUtils _mockJmsUtils;

    @Mock
    private PropertiesUtil _mockPropertiesUtil;

    @Mock
    private ProducerTemplate _mockProducerTemplate;

    @Mock
    private TransactionTemplate _mockTransactionTemplate;

    @InjectMocks
    private SubjectJobRequestService _subjectJobRequestService;


    @Captor
    private ArgumentCaptor<Collection<CompletableFuture<JsonOutputObject>>>
            _outputObjectFuturesCaptor;

    @Captor
    private ArgumentCaptor<DbSubjectJob> _dbJobSaveCaptor;


    private SubjectJobRequest _jobRequest = new SubjectJobRequest(
            "TEST_COMPONENT",
            OptionalInt.of(2),
            List.of(101L, 102L),
            Map.of("JOB_PROP", "JOB_VALUE"),
            Optional.of(URI.create("http://localhost:1234")),
            Optional.of(CallbackMethod.POST),
            Optional.of("EXTERNAL_ID"));


    private CompletableFuture<Optional<JsonOutputObject>> _detectionFuture1
            = ThreadUtil.newFuture();

    private CompletableFuture<Optional<JsonOutputObject>> _detectionFuture2
            = ThreadUtil.newFuture();


    @Test
    public void testCancel() {
        var dbJob = mock(DbSubjectJob.class);
        when(dbJob.getComponentName())
            .thenReturn("TEST_COMPONENT");
        when(_mockSubjectJobRepo.findById(129))
            .thenReturn(dbJob);

        _subjectJobRequestService.cancel(129);

        verify(_mockJmsUtils)
            .cancelSubjectJob(129, "TEST_COMPONENT");
    }


    @Test
    public void testRunJob() {
        setupTransaction();
        setupMockSave();
        setupComponentRepo();

        var jobSentToCamelFuture = ThreadUtil.<Exchange>newFuture();
        when(_mockProducerTemplate.asyncSend(
                    eq("direct:BEGIN_SUBJECT_TRACKING"),
                    any(Exchange.class)))
                .thenAnswer(i -> {
                    jobSentToCamelFuture.complete(i.getArgument(1, Exchange.class));
                    return ThreadUtil.<Exchange>newFuture();
                });

        when(_mockProducerTemplate.getCamelContext())
                .thenReturn(new DefaultCamelContext());

        setupDetectionFutures();

        var output101 = mock(JsonOutputObject.class);

        var output102 = mock(JsonOutputObject.class);
        when(_mockPastJobResultsService.getJobResults(102L))
                .thenReturn(output102);

        var pbJobFuture = setupConverter();

        var returnedJobId = _subjectJobRequestService.runJob(_jobRequest);
        assertThat(returnedJobId).isEqualTo(130L);
        TestUtil.assertNotDone(jobSentToCamelFuture);

        var expectedDbJob = new DbSubjectJob(
                "TEST_COMPONENT",
                2,
                List.of(101L, 102L),
                Map.of("JOB_PROP", "JOB_VALUE"),
                URI.create("http://localhost:1234"),
                CallbackMethod.POST,
                "EXTERNAL_ID");

        var actualDbJob = _dbJobSaveCaptor.getValue();
        assertDbJobsEqual(actualDbJob, expectedDbJob);
        when(_mockSubjectJobRepo.findById(130L))
            .thenReturn(actualDbJob);


        var outputObjectFutures = _outputObjectFuturesCaptor.getValue();
        assertThat(outputObjectFutures)
                .hasSize(2)
                .allSatisfy(TestUtil::assertNotDone);


        _detectionFuture1.complete(Optional.of(output101));
        assertThat(outputObjectFutures)
            .satisfiesOnlyOnce(o -> assertThat(o).succeedsWithin(FUTURE_DURATION))
            .satisfiesOnlyOnce(o -> assertThat(o).isCompletedWithValue(output101));

        _detectionFuture2.complete(Optional.empty());
        assertThat(outputObjectFutures)
            .allSatisfy(o -> assertThat(o).succeedsWithin(FUTURE_DURATION))
            .anySatisfy(o -> assertThat(o).isCompletedWithValue(output102));

        assertThat(actualDbJob.getRetrievedDetectionJobs())
            .as("getRetrievedDetectionJobs should be false until the protobuf job future completes.")
            .isFalse();

        var pbSubjectJob = SubjectProtobuf.SubjectTrackingJob.newBuilder()
                .setJobId(130)
                .build();
        TestUtil.assertNotDone(jobSentToCamelFuture);
        pbJobFuture.complete(pbSubjectJob);

        assertThat(jobSentToCamelFuture).succeedsWithin(FUTURE_DURATION)
                .satisfies(e -> assertThat(e.getProperty("mpfId")).isEqualTo(130L))
                .extracting(Exchange::getIn)
                .satisfies(m -> assertThat(m.getHeader("JobId")).isEqualTo(130L))
                .satisfies(m -> assertThat(m.getBody()).isSameAs(pbSubjectJob));
        assertThat(actualDbJob.getRetrievedDetectionJobs()).isTrue();
    }

    @Test
    public void testRunJobCanHandleFutureException() {
        setupMockSave();
        setupDetectionFutures();
        setupComponentRepo();
        var pbJobFuture = setupConverter();

        var returnedJobId = _subjectJobRequestService.runJob(_jobRequest);
        assertThat(returnedJobId).isEqualTo(130L);

        verify(_mockSubjectJobResultsService, never())
            .completeWithError(anyLong(), any(), any());

        var exception = new IllegalStateException("<test-error>");
        pbJobFuture.completeExceptionally(exception);

        verifyCompletedWithError(exception);
    }

    @Test
    public void testRunJobCanHandleDbException() {
        setupMockSave();
        setupDetectionFutures();
        setupComponentRepo();
        var pbJobFuture = setupConverter();

        var returnedJobId = _subjectJobRequestService.runJob(_jobRequest);
        assertThat(returnedJobId).isEqualTo(130L);

        var exception = new IllegalStateException("<test-error>");
        doThrow(exception)
            .when(_mockTransactionTemplate)
            .executeWithoutResult(any());

        verify(_mockSubjectJobResultsService, never())
            .completeWithError(anyLong(), any(), any());

        var pbSubjectJob = SubjectProtobuf.SubjectTrackingJob.newBuilder()
                .setJobId(130)
                .build();
        pbJobFuture.complete(pbSubjectJob);

        verifyCompletedWithError(exception);
        verifyNoInteractions(_mockProducerTemplate);
    }


    @SuppressWarnings("unchecked")
    private void setupTransaction() {
        doAnswer(a -> {a.getArgument(0, Consumer.class).accept(null); return null;})
            .when(_mockTransactionTemplate)
            .executeWithoutResult(any());
    }

    private static void assertDbJobsEqual(DbSubjectJob actualDbJob, DbSubjectJob expectedDbJob) {
        assertThat(actualDbJob).usingRecursiveComparison()
                .ignoringFields("id", "timeReceived")
                .isEqualTo(expectedDbJob);
        assertThat(actualDbJob.getId()).isEqualTo(130L);
        assertThat(actualDbJob.getTimeReceived()).isInThePast();
    }

    private void setupMockSave() {
        when(_mockSubjectJobRepo.save(_dbJobSaveCaptor.capture()))
            .thenAnswer(a -> {
                var dbJob = a.getArgument(0);
                FieldUtils.setProtectedFieldValue("id", dbJob, 130L);
                return dbJob;
            });
    }

    private void setupComponentRepo() {
        when(_mockSubjectComponentRepo.existsById("TEST_COMPONENT"))
            .thenReturn(true);
    }

    private CompletableFuture<SubjectProtobuf.SubjectTrackingJob> setupConverter() {
        var subjectJobFuture = ThreadUtil.<SubjectProtobuf.SubjectTrackingJob>newFuture();
        when(_mockSubjectJobToProtobufConverter.createJob(
                    eq(130L),
                    _outputObjectFuturesCaptor.capture(),
                    eq(Map.of("JOB_PROP", "JOB_VALUE"))))
                .thenReturn(subjectJobFuture);
        return subjectJobFuture;
    }

    private void setupDetectionFutures() {
        when(_mockInProgressDetectionJobsService.getJobResultsAvailableFuture(101L))
            .thenReturn(_detectionFuture1);

        when(_mockInProgressDetectionJobsService.getJobResultsAvailableFuture(102L))
            .thenReturn(_detectionFuture2);
    }

    private void verifyCompletedWithError(Throwable expectedError) {
        verify(_mockSubjectJobResultsService, timeout(FUTURE_DURATION.toMillis()))
                .completeWithError(eq(130L), TestUtil.nonBlank(), same(expectedError));
    }
}
