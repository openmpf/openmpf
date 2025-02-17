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

import static java.util.stream.Collectors.joining;

import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;

import org.apache.camel.ProducerTemplate;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.interop.subject.SubjectJobRequest;
import org.mitre.mpf.mvc.util.CloseableMdc;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.buffers.SubjectProtobuf;
import org.mitre.mpf.wfm.camel.routes.SubjectJobRouteBuilder;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.access.SubjectComponentRepo;
import org.mitre.mpf.wfm.data.access.SubjectJobRepo;
import org.mitre.mpf.wfm.data.entities.persistent.DbSubjectJob;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.service.PastJobResultsService;
import org.mitre.mpf.wfm.service.SubjectJobResultsService;
import org.mitre.mpf.wfm.util.JmsUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SubjectJobRequestService {

    private final SubjectJobRepo _subjectJobRepo;

    private final SubjectJobResultsService _subjectJobResultsService;

    private final InProgressBatchJobsService _inProgressDetectionJobsService;

    private final PastJobResultsService _pastJobResultsService;

    private final SubjectJobToProtobufConverter _subjectJobToProtobufConverter;

    private final SubjectComponentRepo _subjectComponentRepo;

    private final Validator _validator;

    private final JmsUtils _jmsUtils;

    private final PropertiesUtil _propertiesUtil;

    private final ProducerTemplate _producerTemplate;

    private final TransactionTemplate _transactionTemplate;


    @Inject
    SubjectJobRequestService(
                SubjectJobRepo subjectJobRepo,
                SubjectJobResultsService subjectJobResultsService,
                InProgressBatchJobsService inProgressDetectionJobsService,
                PastJobResultsService pastJobResultsService,
                SubjectJobToProtobufConverter subjectJobToProtobufConverter,
                SubjectComponentRepo subjectComponentRepo,
                Validator validator,
                JmsUtils jmsUtils,
                PropertiesUtil propertiesUtil,
                ProducerTemplate producerTemplate,
                TransactionTemplate transactionTemplate) {
        _subjectJobRepo = subjectJobRepo;
        _subjectJobResultsService = subjectJobResultsService;
        _inProgressDetectionJobsService = inProgressDetectionJobsService;
        _pastJobResultsService = pastJobResultsService;
        _subjectJobToProtobufConverter = subjectJobToProtobufConverter;
        _subjectComponentRepo = subjectComponentRepo;
        _validator = validator;
        _jmsUtils = jmsUtils;
        _propertiesUtil = propertiesUtil;
        _producerTemplate = producerTemplate;
        _transactionTemplate = transactionTemplate;
    }


    public long runJob(SubjectJobRequest request) {
        validateJobRequest(request);
        long jobId = addJobToDb(request).getId();
        try (var ctx = CloseableMdc.job(jobId)) {
            var futures = request.detectionJobIds().stream()
                .map(detId -> getDetectionOutputObject(jobId, detId))
                .toList();

            _subjectJobToProtobufConverter.createJob(jobId, futures, request.jobProperties())
                .thenAccept(this::runJob)
                .exceptionally(e -> addJobError(jobId, e));
        }
        return jobId;
    }


    public void cancel(long jobId) {
        var job = _subjectJobRepo.findById(jobId);
        _jmsUtils.cancelSubjectJob(jobId, job.getComponentName());
    }


    private void validateJobRequest(SubjectJobRequest request) {
        var validationErrors = _validator.validate(request);
        if (!validationErrors.isEmpty()) {
            var errorMsg = validationErrors.stream()
                .map(cv -> createViolationMessage(cv))
                .sorted()
                .collect(joining("\n", "The following fields have errors:\n", ""));
            throw new InvalidJobRequestException(errorMsg);
        }
        if (!_subjectComponentRepo.existsById(request.componentName().toUpperCase())) {
            throw new InvalidJobRequestException(
                    "No component named \"%s\" is registered.".formatted(request.componentName()));
        }
    }

    private static String createViolationMessage(ConstraintViolation<?> violation) {
        var violationPath = violation.getPropertyPath().toString();

        var errorMsgPath = violationPath.isEmpty()
                ? "<root>"
                : violationPath;
        return errorMsgPath + ": " + violation.getMessage();
    }


    private DbSubjectJob addJobToDb(SubjectJobRequest request) {
        var job = new DbSubjectJob(
                request.componentName(),
                request.priority().orElseGet(_propertiesUtil::getJmsPriority),
                request.detectionJobIds(),
                request.jobProperties(),
                request.callbackUri().orElse(null),
                request.callbackMethod().orElse(null),
                request.externalId().orElse(null));
        return _subjectJobRepo.save(job);
    }

    private Void addJobError(long jobId, Throwable error) {
        var cause = ThreadUtil.unwrap(error);
        _subjectJobResultsService.completeWithError(
                jobId, "An error occurred while creating job: ", cause);
        return null;
    }


    private void runJob(SubjectProtobuf.SubjectTrackingJob pbJob) {
        // Need to use TransactionTemplate instead of @Transactional because this method will run
        // on a different thread.
        _transactionTemplate.executeWithoutResult(t -> {
            var job = _subjectJobRepo.findById(pbJob.getJobId());
            job.setRetrievedDetectionJobs(true);
        });
        SubjectJobRouteBuilder.submit(pbJob, _producerTemplate);
    }


    private CompletableFuture<JsonOutputObject> getDetectionOutputObject(
            long subjectJobId, long detectionJobId) {
        return _inProgressDetectionJobsService.getJobResultsAvailableFuture(detectionJobId)
                .thenApplyAsync(optResult -> {
                    if (optResult.isPresent()) {
                        return optResult.get();
                    }
                    return _pastJobResultsService.getDetectionJobResults(detectionJobId);
                })
                .thenApply(o -> checkOutputObject(subjectJobId, detectionJobId, o));
    }


    private JsonOutputObject checkOutputObject(
                long subjectJobId, long detectionJobId, JsonOutputObject outputObject) {
        var jobStatus = BatchJobStatusType.parse(outputObject.getStatus());
        return switch (jobStatus) {
            case COMPLETE -> outputObject;
            case COMPLETE_WITH_WARNINGS -> {
                _transactionTemplate.executeWithoutResult(t -> {
                    var job = _subjectJobRepo.findById(subjectJobId);
                    job.addWarning("Detection job %s had warnings.".formatted(detectionJobId));
                });
                yield outputObject;
            }
            default -> throw new WfmProcessingException(
                    "Could not run subject tracking job because detection job %s had the unexpected status: %s"
                    .formatted(detectionJobId, jobStatus));
        };
    }
}
