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
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;

import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.rest.api.subject.CancellationState;
import org.mitre.mpf.rest.api.subject.Entity;
import org.mitre.mpf.rest.api.subject.Relationship;
import org.mitre.mpf.rest.api.subject.Relationship.MediaReference;
import org.mitre.mpf.rest.api.subject.SubjectJobDetails;
import org.mitre.mpf.rest.api.subject.SubjectJobRequest;
import org.mitre.mpf.rest.api.subject.SubjectJobResult;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.buffers.SubjectProtobuf;
import org.mitre.mpf.wfm.data.access.SubjectJobRepo;
import org.mitre.mpf.wfm.data.entities.persistent.DbCancellationState;
import org.mitre.mpf.wfm.data.entities.persistent.DbSubjectJob;
import org.mitre.mpf.wfm.data.entities.persistent.DbSubjectJobOutput;
import org.mitre.mpf.wfm.util.JmsUtils;
import org.mitre.mpf.wfm.util.ObjectMapperFactory;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.security.util.FieldUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSortedSet;

public class TestSubjectJobResultsService extends MockitoTest.Strict {

    private static final long JOB_ID = 321;

    private static final String COMPONENT_NAME = "MY_COMPONENT";

    private static final BiPredicate<Instant, Instant> INSTANT_EQ
            = TestSubjectJobResultsService::equalAfterTruncate;

    private SubjectJobResultsService _subjectJobResultsService;

    @Mock
    private PropertiesUtil _mockPropertiesUtil;

    private final ObjectMapper _objectMapper = ObjectMapperFactory.customObjectMapper();

    @Mock
    private SubjectJobRepo _mockSubjectJobRepo;

    @Mock
    private JmsUtils _mockJmsUtils;


    @Rule
    public TemporaryFolder _tempFolder = new TemporaryFolder();

    private Path _subjectOutputRoot;

    @Captor
    private ArgumentCaptor<DbSubjectJobOutput> _dbOutputCaptor;

    private boolean _jobShouldBeSaved = true;

    @Before
    public void setup() throws IOException {
        _subjectOutputRoot = _tempFolder.newFolder("subject").toPath();
        lenient().when(_mockPropertiesUtil.getSubjectTrackingResultsDir())
            .thenReturn(_subjectOutputRoot);

        _subjectJobResultsService = new SubjectJobResultsService(
                _mockPropertiesUtil,
                _objectMapper,
                _mockSubjectJobRepo,
                _mockJmsUtils);
    }


    @After
    public void after() {
        if (_jobShouldBeSaved) {
            verify(_mockSubjectJobRepo)
                .save(argThat(j -> j.getId() == JOB_ID));

            verify(_mockSubjectJobRepo)
                .saveOutput(argThat(j -> !j.getOutput().isBlank()));

            verify(_mockJmsUtils)
                .deleteSubjectCancelRoute(JOB_ID, COMPONENT_NAME);
        }
        else {
            verify(_mockSubjectJobRepo, never())
                .save(any());
            verify(_mockSubjectJobRepo, never())
                .saveOutput(any());
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
        var dbJob = createDbJob();
        dbJob.setRetrievedDetectionJobs(true);
        var pbResult = createResultProtobuf();

        _subjectJobResultsService.completeJob(JOB_ID, pbResult);

        var jobResults = verifyOutputOnDiskAndInDb();

        var expectedRequest = new SubjectJobRequest(
                COMPONENT_NAME,
                6,
                List.of(4L, 5L, 6L),
                Map.of("TEST_PROP", "TEST_VALUE"));

        var expectedJobDetails = new SubjectJobDetails(
                JOB_ID,
                expectedRequest,
                dbJob.getTimeReceived(),
                dbJob.getTimeCompleted(),
                true,
                CancellationState.NOT_CANCELLED,
                ImmutableSortedSet.of("COMPONENT_ERROR"),
                ImmutableSortedSet.of());

        var expectedEntity = new Entity(
                "ENTITY_1_ID",
                0.9,
                Map.of("TEST_TRACK_TYPE", List.of("FIRST_TRACK_ID", "SECOND_TRACK_ID")),
                Map.of("PROP", "VALUE"));

        var expectedMediaRef = new MediaReference("MEDIA_ID", List.of(5_000L, 5_001L));
        var expectedRelationship = new Relationship(
                List.of("ENTITY_1_ID", "ENTITY_2_ID"),
                List.of(expectedMediaRef),
                Map.of("RELATIONSHIP_PROP", "RELATIONSHIP_VALUE"));

        var expectedResult = new SubjectJobResult(
                expectedJobDetails,
                Map.of("JOB_OUTPUT_PROP", "JOB_OUTPUT_VALUE"),
                Map.of("SUBJECT", List.of(expectedEntity)),
                Map.of("PROXIMITY", List.of(expectedRelationship)));

        assertThat(jobResults).usingRecursiveComparison()
                .withEqualsForType(INSTANT_EQ, Instant.class)
                .isEqualTo(expectedResult);

        verify(_mockSubjectJobRepo)
            .save(same(dbJob));
    }


    private DbSubjectJob createDbJob() {
        var job = new DbSubjectJob(
                COMPONENT_NAME, 6, List.of(4L, 5L, 6L),
                Map.of("TEST_PROP", "TEST_VALUE"));
        FieldUtils.setProtectedFieldValue("id", job, JOB_ID);

        when(_mockSubjectJobRepo.findById(JOB_ID))
            .thenReturn(Optional.of(job));
        return job;
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
        Files.delete(_subjectOutputRoot);
        var dbJob = createDbJob();

        var protobuf =  SubjectProtobuf.SubjectTrackingResult.getDefaultInstance();
        assertThatThrownBy(() -> _subjectJobResultsService.completeJob(JOB_ID, protobuf))
            .isInstanceOf(WfmProcessingException.class)
            .hasCauseInstanceOf(NoSuchFileException.class);

        verify(_mockSubjectJobRepo)
            .saveOutput(_dbOutputCaptor.capture());

        var dbJobOutput = _dbOutputCaptor.getValue();
        var dbResult = _objectMapper.readValue(dbJobOutput.getOutput(), SubjectJobResult.class);

        assertThat(dbResult.job().errors())
            .singleElement(Assertions.STRING)
            .contains("Failed to write output object");

        assertThat(dbJob.getErrors()).containsExactlyInAnyOrderElementsOf(dbResult.job().errors());
    }


    @Test
    public void canStoreCancellationResult() throws IOException {
        var dbJob = createDbJob();
        dbJob.setRetrievedDetectionJobs(true);

        _subjectJobResultsService.cancel(JOB_ID);

        assertThat(dbJob.getCancellationState())
                .isEqualTo(DbCancellationState.CANCELLED_BY_USER);
        assertJobCompletedInThePast(dbJob);

        var jobResult = verifyOutputOnDiskAndInDb();

        var expectedRequest = new SubjectJobRequest(
                COMPONENT_NAME,
                6,
                List.of(4L, 5L, 6L),
                Map.of("TEST_PROP", "TEST_VALUE"));

        var expectedJobDetails = new SubjectJobDetails(
                JOB_ID,
                expectedRequest,
                dbJob.getTimeReceived(),
                dbJob.getTimeCompleted(),
                true,
                CancellationState.CANCELLED_BY_USER,
                ImmutableSortedSet.of(),
                ImmutableSortedSet.of());

        var expectedJobResult = new SubjectJobResult(expectedJobDetails, null, null, null);

        assertThat(jobResult).usingRecursiveComparison()
                .withEqualsForType(INSTANT_EQ, Instant.class)
                .isEqualTo(expectedJobResult);
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


    private SubjectJobResult verifyOutputOnDiskAndInDb() throws IOException {
        var fileContent = Files.readString(_subjectOutputRoot.resolve(JOB_ID + ".json"));
        verify(_mockSubjectJobRepo)
            .saveOutput(argThat(a -> a.getOutput().equals(fileContent)));
        return _objectMapper.readValue(fileContent, SubjectJobResult.class);
    }


    @Test
    public void canHandleCompleteWithError() throws IOException {
        var job = createDbJob();
        job.addError("TEST ERROR");
        _subjectJobResultsService.completeWithError(
                JOB_ID, "TEST ERROR 2", new IllegalStateException("<error2>"));

        assertThat(job.getCancellationState()).isEqualTo(DbCancellationState.NOT_CANCELLED);
        assertJobCompletedInThePast(job);

        var jobResult = verifyOutputOnDiskAndInDb();

        assertThat(jobResult.entities()).isNull();
        assertThat(jobResult.relationships()).isNull();

        assertThat(jobResult.job().id()).isEqualTo(JOB_ID);

        assertThat(jobResult.job().errors()).satisfiesExactlyInAnyOrder(
            s -> assertThat(s).isEqualTo("TEST ERROR"),
            s -> assertThat(s).startsWith("TEST ERROR 2")
                    .contains("IllegalStateException", "<error2>"));
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

    private static boolean equalAfterTruncate(Instant x, Instant y) {
        return x.truncatedTo(ChronoUnit.MILLIS).equals(y.truncatedTo(ChronoUnit.MILLIS));
    }
}
