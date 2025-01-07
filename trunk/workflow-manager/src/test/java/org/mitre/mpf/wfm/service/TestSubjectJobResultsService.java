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

package org.mitre.mpf.wfm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.nio.file.NoSuchFileException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.http.HttpResponse;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.interop.subject.CallbackMethod;
import org.mitre.mpf.interop.subject.CancellationState;
import org.mitre.mpf.interop.subject.Entity;
import org.mitre.mpf.interop.subject.Relationship;
import org.mitre.mpf.interop.subject.SubjectJobDetails;
import org.mitre.mpf.interop.subject.SubjectJobRequest;
import org.mitre.mpf.interop.subject.SubjectJobResult;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.buffers.SubjectProtobuf;
import org.mitre.mpf.wfm.data.access.SubjectJobRepo;
import org.mitre.mpf.wfm.data.entities.persistent.DbCancellationState;
import org.mitre.mpf.wfm.data.entities.persistent.DbSubjectJob;
import org.mitre.mpf.wfm.util.CallbackStatus;
import org.mitre.mpf.wfm.util.JmsUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.security.util.FieldUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.google.common.collect.ImmutableSortedSet;

public class TestSubjectJobResultsService extends MockitoTest.Strict {

    private static final long JOB_ID = 321;

    private static final String COMPONENT_NAME = "MY_COMPONENT";

    private static final URI OUTPUT_URI = URI.create("file:///fake/output/321.json");

    private SubjectJobResultsService _subjectJobResultsService;

    @Mock
    private PropertiesUtil _mockPropertiesUtil;

    @Mock
    private StorageService _mockStorageService;

    @Mock
    private SubjectJobRepo _mockSubjectJobRepo;

    @Mock
    private JmsUtils _mockJmsUtils;

    @Mock
    private JobCompleteCallbackService _mockCallbackService;

    @Mock
    private TransactionTemplate _mockTransactionTemplate;

    @Captor
    private ArgumentCaptor<SubjectJobResult> _storedResultCaptor;

    private boolean _jobShouldBeSaved = true;

    @Before
    public void setup() {
        _subjectJobResultsService = new SubjectJobResultsService(
                _mockPropertiesUtil,
                _mockStorageService,
                _mockSubjectJobRepo,
                _mockJmsUtils,
                _mockCallbackService,
                _mockTransactionTemplate);
    }


    @After
    public void after() {
        if (_jobShouldBeSaved) {
            verify(_mockSubjectJobRepo)
                .save(argThat(j -> j.getId() == JOB_ID));
            verify(_mockJmsUtils)
                .deleteSubjectCancelRoute(JOB_ID, COMPONENT_NAME);
        }
        else {
            verify(_mockSubjectJobRepo, never())
                .save(any());
        }
    }

    private static void assertJobCompletedInThePast(DbSubjectJob job) {
        assertThat(job.getTimeCompleted())
            .as("Job is not complete.")
            .get(Assertions.INSTANT)
            .as("Job completion time was not in the past")
            .isInThePast();
    }


    @Test
    public void canStoreResults() throws IOException {
        setupVersion();
        var dbJob = createDbJob();
        dbJob.setRetrievedDetectionJobs(true);
        var pbResult = createResultProtobuf();
        var responseFuture = setupCallbackService(dbJob);
        var transactionDoneFuture = setupTransaction();
        setUpStorageService();

        _subjectJobResultsService.completeJob(JOB_ID, pbResult);

        assertCorrectJobResultsSaved(
                dbJob,
                URI.create("http://localhost:4814"),
                CallbackMethod.POST);
        assertCallbackSent(dbJob, responseFuture, transactionDoneFuture);
    }


    @Test
    public void canStoreResultsNoCallback() throws IOException {
        setupVersion();
        var dbJob = createDbJob(null);
        dbJob.setRetrievedDetectionJobs(true);
        var pbResult = createResultProtobuf();
        setUpStorageService();

        _subjectJobResultsService.completeJob(JOB_ID, pbResult);

        assertThat(dbJob.getCallbackStatus()).isEqualTo(CallbackStatus.notRequested());
        assertCorrectJobResultsSaved(dbJob, null ,null);
        verifyNoInteractions(_mockCallbackService);
    }


    private DbSubjectJob createDbJob() {
        return createDbJob(URI.create("http://localhost:4814"));
    }

    private DbSubjectJob createDbJob(URI callbackUri) {
        var job = new DbSubjectJob(
                COMPONENT_NAME, 6, List.of(4L, 5L, 6L),
                Map.of("TEST_PROP", "TEST_VALUE"),
                callbackUri,
                null,
                null);
        FieldUtils.setProtectedFieldValue("id", job, JOB_ID);

        when(_mockSubjectJobRepo.findById(JOB_ID))
            .thenReturn(job);
        return job;
    }


    private void assertCorrectJobResultsSaved(
            DbSubjectJob dbJob,
            URI callbackUri,
            CallbackMethod callbackMethod) {
        var expectedRequest = new SubjectJobRequest(
                COMPONENT_NAME,
                OptionalInt.of(6),
                Set.of(4L, 5L, 6L),
                Map.of("TEST_PROP", "TEST_VALUE"),
                Optional.ofNullable(callbackUri),
                Optional.ofNullable(callbackMethod),
                Optional.empty());

        var expectedJobDetails = new SubjectJobDetails(
                JOB_ID,
                expectedRequest,
                dbJob.getTimeReceived(),
                dbJob.getTimeCompleted(),
                true,
                CancellationState.NOT_CANCELLED,
                ImmutableSortedSet.of("COMPONENT_ERROR"),
                ImmutableSortedSet.of(),
                Optional.empty(),
                "JOB RUNNING");

        var expectedEntity = new Entity(
                "ENTITY_1_ID",
                0.9,
                Map.of("TEST_TRACK_TYPE", List.of("FIRST_TRACK_ID", "SECOND_TRACK_ID")),
                Map.of("PROP", "VALUE"));

        var expectedMediaRef = new Relationship.MediaReference(
                "MEDIA_ID", List.of(5_000L, 5_001L));
        var expectedRelationship = new Relationship(
                List.of("ENTITY_1_ID", "ENTITY_2_ID"),
                List.of(expectedMediaRef),
                Map.of("RELATIONSHIP_PROP", "RELATIONSHIP_VALUE"));

        var expectedResult = new SubjectJobResult(
                expectedJobDetails,
                Map.of("JOB_OUTPUT_PROP", "JOB_OUTPUT_VALUE"),
                Map.of("SUBJECT", List.of(expectedEntity)),
                Map.of("PROXIMITY", List.of(expectedRelationship)),
                "X.Y.Z");

        var jobResults = _storedResultCaptor.getValue();
        assertThat(jobResults).usingRecursiveComparison()
                .withEqualsForType(TestUtil::equalAfterTruncate, Instant.class)
                .isEqualTo(expectedResult);

        verify(_mockSubjectJobRepo)
            .save(same(dbJob));
        assertThat(dbJob.getOutputUri()).hasValue(OUTPUT_URI);
    }


    private static SubjectProtobuf.SubjectTrackingResult createResultProtobuf() {
        var builder = SubjectProtobuf.SubjectTrackingResult.newBuilder();

        var tracks = SubjectProtobuf.TrackIdList.newBuilder()
                .addTrackIds("FIRST_TRACK_ID")
                .addTrackIds("SECOND_TRACK_ID")
                .build();

        var entity = SubjectProtobuf.Entity.newBuilder()
            .setId("ENTITY_1_ID")
            .setScore(0.9)
            .putAllTracks(Map.of("TEST_TRACK_TYPE", tracks))
            .putAllProperties(Map.of("PROP", "VALUE"))
            .build();

        var entityList = SubjectProtobuf.EntityList.newBuilder().addEntities(entity).build();
        builder.putEntityGroups("SUBJECT", entityList);

        var frameList = SubjectProtobuf.FrameList.newBuilder()
                .addFrames(5_000)
                .addFrames(5_001)
                .build();

        var relationship = SubjectProtobuf.Relationship.newBuilder()
                .addEntities("ENTITY_1_ID")
                .addEntities("ENTITY_2_ID")
                .putFrames("MEDIA_ID", frameList)
                .putProperties("RELATIONSHIP_PROP", "RELATIONSHIP_VALUE")
                .build();
        var relationshipList = SubjectProtobuf.RelationshipList
                .newBuilder()
                .addRelationships(relationship)
                .build();
        builder.putRelationshipGroups("PROXIMITY", relationshipList);

        builder.putProperties("JOB_OUTPUT_PROP", "JOB_OUTPUT_VALUE");

        builder.addErrors("COMPONENT_ERROR");

        return builder.build();
    }


    @Test
    public void canHandleIoExceptionWhenSavingJson() throws IOException {
        var dbJob = createDbJob();
        var callbackFuture = setupCallbackService(dbJob);
        var transactionFuture = setupTransaction();

        when(_mockStorageService.store(_storedResultCaptor.capture(), any()))
            .thenAnswer(inv -> {
                Function<String, SubjectJobResult> addWarningFunc = inv.getArgument(1);
                addWarningFunc.apply("<test-warning>");
                throw new NoSuchFileException("file");
            });

        var protobuf =  SubjectProtobuf.SubjectTrackingResult.getDefaultInstance();
        assertThatThrownBy(() -> _subjectJobResultsService.completeJob(JOB_ID, protobuf))
            .isInstanceOf(WfmProcessingException.class)
            .hasCauseInstanceOf(NoSuchFileException.class);

        assertThat(dbJob.getErrors())
            .singleElement(Assertions.STRING)
            .contains("Failed to write output object");

        assertThat(dbJob.getWarnings())
            .singleElement(Assertions.STRING)
            .contains("<test-warning>");

        assertCallbackSent(dbJob, callbackFuture, transactionFuture);
    }


    @Test
    public void canStoreCancellationResult() throws IOException {
        setupVersion();
        var dbJob = createDbJob();
        dbJob.setRetrievedDetectionJobs(true);
        var callbackFuture = setupCallbackService(dbJob);
        var transactionFuture = setupTransaction();
        setUpStorageService();

        _subjectJobResultsService.cancel(JOB_ID);

        assertThat(dbJob.getCancellationState())
                .isEqualTo(DbCancellationState.CANCELLED_BY_USER);
        assertJobCompletedInThePast(dbJob);

        var jobResult = _storedResultCaptor.getValue();

        var expectedRequest = new SubjectJobRequest(
                COMPONENT_NAME,
                OptionalInt.of(6),
                Set.of(4L, 5L, 6L),
                Map.of("TEST_PROP", "TEST_VALUE"),
                Optional.of(URI.create("http://localhost:4814")),
                Optional.of(CallbackMethod.POST),
                Optional.empty());

        var expectedJobDetails = new SubjectJobDetails(
                JOB_ID,
                expectedRequest,
                dbJob.getTimeReceived(),
                dbJob.getTimeCompleted(),
                true,
                CancellationState.CANCELLED_BY_USER,
                ImmutableSortedSet.of(),
                ImmutableSortedSet.of(),
                Optional.empty(),
                "JOB RUNNING");

        assertCallbackSent(dbJob, callbackFuture, transactionFuture);

        var expectedJobResult = new SubjectJobResult(expectedJobDetails, null, null, null, "X.Y.Z");
        assertThat(jobResult).usingRecursiveComparison()
                .withEqualsForType(TestUtil::equalAfterTruncate, Instant.class)
                .isEqualTo(expectedJobResult);
        assertThat(dbJob.getOutputUri()).hasValue(OUTPUT_URI);
    }


    @Test
    public void doesNothingWhenAlreadyCancelled() {
        _jobShouldBeSaved = false;

        var job = createDbJob();
        var timeCompleted = Instant.now().minusSeconds(60);
        job.setTimeCompleted(timeCompleted);
        job.setCancellationState(DbCancellationState.CANCELLED_BY_USER);

        _subjectJobResultsService.cancel(JOB_ID);

        assertThat(job.getTimeCompleted()).contains(timeCompleted);
        assertThat(job.getCancellationState()).isEqualTo(DbCancellationState.CANCELLED_BY_USER);
    }

    @Test
    public void throwsWhenCancelCompleteJob() {
        _jobShouldBeSaved = false;

        var job = createDbJob();
        var timeCompleted = Instant.now().minusSeconds(60);
        job.setTimeCompleted(timeCompleted);

        assertThatThrownBy(() -> _subjectJobResultsService.cancel(JOB_ID))
            .isInstanceOf(WfmProcessingException.class)
            .hasMessageContaining("already complete");
        assertThat(job.getCancellationState()).isEqualTo(DbCancellationState.NOT_CANCELLED);
    }

    @Test
    public void reportsCallbackFailure() {
        var job = createDbJob();
        var callbackFuture = setupCallbackService(job);
        var transactionFuture = setupTransaction();

        var protobuf = SubjectProtobuf.SubjectTrackingResult.getDefaultInstance();
        _subjectJobResultsService.completeJob(JOB_ID, protobuf);

        assertThat(job.getCallbackStatus()).isEqualTo(CallbackStatus.inProgress());
        callbackFuture.completeExceptionally(new IllegalStateException("<TEST-ERROR>"));

        assertThat(transactionFuture).succeedsWithin(TestUtil.FUTURE_DURATION);
        assertThat(job.getCallbackStatus())
                .startsWith("ERROR:")
                .contains("<TEST-ERROR>");
    }


    @Test
    public void canHandleCompleteWithError() throws IOException {
        var job = createDbJob();
        job.addError("TEST ERROR");
        var responseFuture = setupCallbackService(job);
        var transactionFuture = setupTransaction();
        setUpStorageService();

        _subjectJobResultsService.completeWithError(
                JOB_ID, "TEST ERROR 2", new IllegalStateException("<error2>"));

        assertThat(job.getCancellationState()).isEqualTo(DbCancellationState.NOT_CANCELLED);
        assertJobCompletedInThePast(job);

        var jobResult = _storedResultCaptor.getValue();

        assertThat(jobResult.entities()).isNull();
        assertThat(jobResult.relationships()).isNull();

        assertThat(jobResult.job().id()).isEqualTo(JOB_ID);

        assertThat(jobResult.job().errors()).satisfiesExactlyInAnyOrder(
            s -> assertThat(s).isEqualTo("TEST ERROR"),
            s -> assertThat(s).startsWith("TEST ERROR 2")
                    .contains("IllegalStateException", "<error2>"));

        assertCallbackSent(job, responseFuture, transactionFuture);
        assertThat(job.getOutputUri()).hasValue(OUTPUT_URI);
    }


    @Test
    public void canNotCompleteWithErrorIfAlreadyComplete() {
        _jobShouldBeSaved = false;
        var job = createDbJob();
        job.addError("TEST ERROR");
        job.setTimeCompleted(Instant.now());

        var ex = new IllegalStateException();
        assertThatThrownBy(() -> _subjectJobResultsService.completeWithError(
                    JOB_ID, "TEST ERROR2", ex))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already complete");
        assertJobCompletedInThePast(job);
    }


    private CompletionStage<Void> setupTransaction() {
        var transactionDoneFuture = ThreadUtil.<Void>newFuture();
        doAnswer(i -> {
                var transactionStatus = new SimpleTransactionStatus();
                i.<Consumer<TransactionStatus>>getArgument(0).accept(transactionStatus);
                transactionDoneFuture.complete(null);
                return null;
            }).when(_mockTransactionTemplate)
                .executeWithoutResult(any());
        // minimalCompletionStage() is being used to prevent the caller from completing the future.
        // The caller should only be using the returned value to know when the transaction has been
        // invoked.
        return transactionDoneFuture.minimalCompletionStage();
    }

    private void setUpStorageService() throws IOException {
        when(_mockStorageService.store(_storedResultCaptor.capture(), notNull()))
                .thenReturn(OUTPUT_URI);
    }


    private CompletableFuture<HttpResponse> setupCallbackService(DbSubjectJob job) {
        var responseFuture = ThreadUtil.<HttpResponse>newFuture();
        when(_mockCallbackService.sendCallback(job))
                .thenReturn(responseFuture);
        return responseFuture;
    }

    private static void assertCallbackSent(
                DbSubjectJob dbJob,
                CompletableFuture<HttpResponse> callbackFuture,
                CompletionStage<Void> transactionFuture) {
        assertThat(dbJob.getCallbackStatus()).isEqualTo(CallbackStatus.inProgress());
        callbackFuture.complete(null);

        assertThat(transactionFuture).succeedsWithin(TestUtil.FUTURE_DURATION);
        assertThat(dbJob.getCallbackStatus()).isEqualTo(CallbackStatus.complete());
    }

    private void setupVersion() {
        when(_mockPropertiesUtil.getSemanticVersion())
                .thenReturn("X.Y.Z");
    }
}
