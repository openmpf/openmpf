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

package org.mitre.mpf.wfm.data.access;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.transaction.Transactional;

import org.mitre.mpf.rest.api.subject.SubjectJobDetails;
import org.mitre.mpf.rest.api.subject.SubjectJobRequest;
import org.mitre.mpf.rest.api.subject.SubjectJobResult;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.entities.persistent.DbCancellationState;
import org.mitre.mpf.wfm.data.entities.persistent.DbSubjectJob;
import org.mitre.mpf.wfm.data.entities.persistent.DbSubjectJobOutput;
import org.mitre.mpf.wfm.data.entities.persistent.QDbSubjectJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QPageRequest;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSortedSet;
import com.querydsl.jpa.JPQLQueryFactory;

@Component
@Transactional
public class SubjectJobRepo {
    private final JobRepository _jobRepo;

    private final JobOutputRepository _outputRepo;

    private final ObjectMapper _objectMapper;

    private final JPQLQueryFactory _queryFactory;

    @Inject
    SubjectJobRepo(
            JobRepository jobRepo,
            JobOutputRepository outputRepo,
            ObjectMapper objectMapper,
            JPQLQueryFactory queryFactory) {
        _jobRepo = jobRepo;
        _outputRepo = outputRepo;
        _objectMapper = objectMapper;
        _queryFactory = queryFactory;
    }

    public DbSubjectJob findById(long jobId) {
        return tryFindById(jobId).orElseThrow(() -> new WfmProcessingException(
                "Could not find subject tracking job with id " + jobId));
    }

    public Optional<DbSubjectJob> tryFindById(long jobId) {
        return _jobRepo.findById(jobId);
    }

    public DbSubjectJob save(DbSubjectJob job) {
        return _jobRepo.save(job);
    }

    public Optional<SubjectJobDetails> getJobDetails(long jobId) {
        return tryFindById(jobId).map(SubjectJobRepo::getJobDetails);
    }

    public static SubjectJobDetails getJobDetails(DbSubjectJob job) {
        return new SubjectJobDetails(
                job.getId(),
                new SubjectJobRequest(
                        job.getComponentName(),
                        OptionalInt.of(job.getPriority()),
                        job.getDetectionJobIds(),
                        job.getJobProperties(),
                        job.getCallbackUri(),
                        job.getCallbackMethod(),
                        job.getExternalId()),
                job.getTimeReceived(),
                job.getTimeCompleted(),
                job.getRetrievedDetectionJobs(),
                job.getCancellationState().convert(),
                ImmutableSortedSet.copyOf(job.getErrors()),
                ImmutableSortedSet.copyOf(job.getWarnings()),
                job.getOutputUri(),
                job.getCallbackStatus());
    }

    public Stream<DbSubjectJob> getPage(int page, int pageLen) {
        var job = QDbSubjectJob.dbSubjectJob;
        var pageRequest = QPageRequest.of(page - 1, pageLen, job.id.desc());
        return _jobRepo.findAll(pageRequest).stream();
    }

    public void saveOutput(DbSubjectJobOutput subjectTrackingJobOutput) {
        _outputRepo.save(subjectTrackingJobOutput);
    }


    public Optional<SubjectJobResult> getOutput(long jobId) {
        return getOutputString(jobId)
            .map(s -> {
                try {
                    return _objectMapper.readValue(s, SubjectJobResult.class);
                }
                catch (JsonProcessingException e) {
                    throw new WfmProcessingException(e);
                }
            });
    }

    public Optional<String> getOutputString(long jobId) {
        return _outputRepo.findById(jobId).map(j -> j.getOutput());
    }

    public void cancelIncompleteJobs() {
        var job = QDbSubjectJob.dbSubjectJob;
        _queryFactory
                .update(job)
                .where(
                    job.timeCompleted.isNull(),
                    job.cancellationState.eq(DbCancellationState.NOT_CANCELLED))
                .set(job.cancellationState, DbCancellationState.CANCELLED_BY_SHUTDOWN)
                .set(job.timeCompleted, Instant.now())
                .execute();
    }

    @Repository
    private static interface JobRepository extends
            JpaRepository<DbSubjectJob, Long>,
            QuerydslPredicateExecutor<DbSubjectJob> {
    }

    // Store output in different table so that job information can be retrieved without also
    // getting the job output.
    @Repository
    private static interface JobOutputRepository extends JpaRepository<DbSubjectJobOutput, Long> {
    }
}
