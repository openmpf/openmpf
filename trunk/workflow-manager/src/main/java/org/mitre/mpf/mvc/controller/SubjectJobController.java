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

package org.mitre.mpf.mvc.controller;

import java.util.stream.Stream;

import javax.inject.Inject;

import org.mitre.mpf.mvc.util.MdcUtil;
import org.mitre.mpf.mvc.util.PostResponseUtil;
import org.mitre.mpf.rest.api.MessageModel;
import org.mitre.mpf.rest.api.subject.SubjectJobDetails;
import org.mitre.mpf.rest.api.subject.SubjectJobRequest;
import org.mitre.mpf.rest.api.subject.SubjectJobResult;
import org.mitre.mpf.rest.api.subject.SubjectJobSummary;
import org.mitre.mpf.wfm.businessrules.InvalidJobRequestException;
import org.mitre.mpf.wfm.businessrules.SubjectJobRequestService;
import org.mitre.mpf.wfm.data.access.SubjectJobRepo;
import org.mitre.mpf.wfm.data.entities.persistent.DbCancellationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import software.amazon.awssdk.http.HttpStatusCode;

@Api("SubjectTrackingJobs")
@RestController
@RequestMapping(path = "/subject/jobs", produces = "application/json")
public class SubjectJobController {

    private static final Logger LOG = LoggerFactory.getLogger(SubjectJobController.class);

    private final SubjectJobRequestService _subjectJobRequestService;

    private final SubjectJobRepo _subjectJobRepo;

    @Inject
    SubjectJobController(
            SubjectJobRequestService subjectJobRequestService,
            SubjectJobRepo subjectJobDao) {
        _subjectJobRequestService = subjectJobRequestService;
        _subjectJobRepo = subjectJobDao;
    }


    @GetMapping
    @ApiOperation("Gets subject tracking jobs")
    @ExposedMapping
    public Stream<SubjectJobSummary> getJobs(
            @RequestParam(defaultValue = "1")
            @ApiParam(
                    value = "Page number of results to get.",
                    defaultValue = "1",
                    allowableValues = "range[1, infinity]")
            int page,
            @RequestParam(defaultValue = "100")
            @ApiParam(
                    value = "Number of results to get.",
                    defaultValue = "100",
                    allowableValues = "range[1, infinity]")
            int pageLen) {
        return _subjectJobRepo.getPage(page, pageLen)
                .map(j -> new SubjectJobSummary(
                        j.getId(), j.getComponentName(),
                        j.getTimeReceived(), j.getTimeCompleted()));
    }


    @GetMapping("{jobId}")
    @ExposedMapping
    @ApiOperation("Gets a subject tracking job")
    @ApiResponses({@ApiResponse(code = 404, message = "Job does not exist.")})
    public ResponseEntity<SubjectJobDetails> getJob(@PathVariable long jobId) {
        return MdcUtil.job(jobId, () -> ResponseEntity.of(_subjectJobRepo.getJobDetails(jobId)));
    }


    @PostMapping
    @ExposedMapping
    @ApiOperation("Creates a new subject tracking job")
    public ResponseEntity<Object> createJob(
            @RequestBody SubjectJobRequest jobRequest) {
        try {
            var jobId = _subjectJobRequestService.runJob(jobRequest);
            return MdcUtil.job(jobId, PostResponseUtil::createdResponse);
        }
        catch (InvalidJobRequestException e) {
            var msg = "Failed to create job due to: " + e;
            LOG.error(msg, e);
            return ResponseEntity.badRequest().body(new MessageModel(msg));
        }
    }


    @GetMapping("{jobId}/output")
    @ExposedMapping
    @ApiOperation("Gets a subject tracking job's output.")
    @ApiResponses({
        @ApiResponse(code = HttpStatusCode.NOT_FOUND, message = "Job output does not exist."),
    })
    public ResponseEntity<SubjectJobResult> getOutput(@PathVariable long jobId) {
        return MdcUtil.job(jobId, () -> ResponseEntity.of(_subjectJobRepo.getOutput(jobId)));
    }



    @PostMapping("{jobId}/cancel")
    @ExposedMapping
    @ApiOperation("Cancels a subject tracking job.")
    @ApiResponses({
        @ApiResponse(code = HttpStatusCode.OK, message = "The job was successfully cancelled."),
        @ApiResponse(code = HttpStatusCode.ACCEPTED, message = "Cancellation request started."),
        @ApiResponse(code = HttpStatusCode.NOT_FOUND, message = "Job does not exist."),
        @ApiResponse(code = 409, message = "The job has already completed.")
    })
    public ResponseEntity<MessageModel> cancel(@PathVariable long jobId) {
        return MdcUtil.job(jobId, this::cancelInternal);
    }

    private ResponseEntity<MessageModel> cancelInternal(long jobId) {
        var optJob = _subjectJobRepo.tryFindById(jobId);
        if (optJob.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new MessageModel("Could not find job with id %s.".formatted(jobId)));
        }

        var job = optJob.get();
        if (job.isComplete()) {
            if (job.getCancellationState() == DbCancellationState.CANCELLED_BY_USER) {
                return ResponseEntity.ok(
                        new MessageModel("Job %s was cancelled.".formatted(jobId)));
            }
            else {
                return ResponseEntity
                        .status(HttpStatus.CONFLICT)
                        .body(new MessageModel("Job %s is already complete.".formatted(jobId)));
            }
        }
        _subjectJobRequestService.cancel(jobId);
        return ResponseEntity.accepted().body(
                new MessageModel("Starting cancellation of job %s.".formatted(jobId)));
    }
}
