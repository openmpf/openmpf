/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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

import io.swagger.annotations.*;
import org.mitre.mpf.interop.JsonJobRequest;
import org.mitre.mpf.interop.JsonMediaInputObject;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.mvc.model.SessionModel;
import org.mitre.mpf.mvc.util.ModelUtils;
import org.mitre.mpf.rest.api.*;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.data.entities.persistent.MarkupResult;
import org.mitre.mpf.wfm.event.JobProgress;
import org.mitre.mpf.wfm.exceptions.InvalidPipelineObjectWfmProcessingException;
import org.mitre.mpf.wfm.service.MpfService;
import org.mitre.mpf.wfm.service.PipelineService;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

// swagger includes

@Api(value = "Jobs",
        description = "Job create, status, cancel, resubmit, output")
@Controller
@Scope("request")
@Profile("website")
public class JobController {
    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    @Autowired
    private PropertiesUtil propertiesUtil;

    @Autowired //will grab the impl
    private MpfService mpfService;

    @Autowired
    private SessionModel sessionModel;

    @Autowired
    private JobProgress jobProgress;

    @Autowired
    private PipelineService pipelineService;

    /*
     *	POST /jobs
     */
    //EXTERNAL
    @RequestMapping(value = {"/rest/jobs"}, method = RequestMethod.POST)
    @ApiOperation(value = "Creates and submits a job using a JSON JobCreationRequest object as the request body.",
            notes = "The pipelineName should be one of the values in 'rest/pipelines'. The media array should contain objects with a valid mediaUri.  " +
                    "For example: http://localhost/images/image.png. Another example: file:///home/user/images/image.jpg." +
                    " A callbackURL (optional) and callbackMethod (GET or POST) may be added. When the job completes, the callback will perform a GET or POST to the callbackURL with " +
                    "the parameters 'jobId' and 'externalId' of the completed job." +
                    " For example, on a GET to a callbackURL: /api.example.com/foo?jobId=1&externalId=1. Another example: /api.example.com/foo?someparam=something&jobId=1&externalId=1." +
                    " Example when no externalId is provided: /api.example.com/foo?jobId=1. The body of a POST callback will always include the 'jobId' and 'externalId', even if the latter is 'null'." +
                    " An optional jobProperties object contains String key-value pairs which override the pipeline's job properties for this job." +
                    " An optional algorithmProperties object containing <String,Map> key-value pairs can override jobProperties for a specific algorithm defined in the pipeline.  "+
                    "For algorithmProperties, the key should be the algorithm name, and the value should be a Map of String key-value pairs representing properties specific to the named algorithm. "+
                    "Note that the batch jobs and streaming jobs share a range of valid job ids.  OpenMPF guarantees that the ids of a streaming job and a batch job will be unique.",
            produces = "application/json", response = JobCreationResponse.class)
    @ApiResponses(value = {
            @ApiResponse(code = 201, message = "Job created"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 401, message = "Bad credentials")})
    @ResponseBody
    @ResponseStatus(value = HttpStatus.CREATED) //return 201 for successful post
    public ResponseEntity<JobCreationResponse> createJobRest(@ApiParam(required = true, value = "JobCreationRequest") @RequestBody JobCreationRequest jobCreationRequest) {
        JobCreationResponse createResponse = createJobInternal(jobCreationRequest, false);
        if (createResponse.getMpfResponse().getResponseCode() == MpfResponse.RESPONSE_CODE_SUCCESS) {
            return new ResponseEntity<>(createResponse, HttpStatus.CREATED);
        } else {
            log.error("Error creating job");
            return new ResponseEntity<>(createResponse, HttpStatus.BAD_REQUEST);
        }
    }

    //INTERNAL
    @RequestMapping(value = {"/jobs"}, method = RequestMethod.POST)
    @ResponseBody
    @ResponseStatus(value = HttpStatus.CREATED) //return 201 for successful post
    public JobCreationResponse createJob(@RequestBody JobCreationRequest jobCreationRequest) {
        return createJobInternal(jobCreationRequest, true);
    }

    /*
     * GET /jobs
     */
    //INTERNAL
    @RequestMapping(value = "/jobs", method = RequestMethod.GET)
    @ResponseBody
    public List<SingleJobInfo> getJobStatus(@RequestParam(value = "useSession", required = false) boolean useSession) {
        if (useSession) {
            return sessionModel.getSessionJobs().stream()
                    .map(id -> convertJob(mpfService.getJobRequest(id)))
                    .collect(toList());
        }
        else {
            return mpfService.getAllJobRequests().stream()
                    .map(this::convertJob)
                    .collect(toList());
        }
    }

    //INTERNAL
    @RequestMapping(value = {"/jobs-paged"}, method = RequestMethod.POST)
    @ResponseBody
    public JobPageListModel getJobStatusFiltered(@RequestParam(value = "draw", required = false) int draw,
                                                 @RequestParam(value = "start", required = false) int start,
                                                 @RequestParam(value = "length", required = false) int length,
                                                 @RequestParam(value = "search", required = false) String search,
                                                 @RequestParam(value = "sort", required = false) String sort) {
        log.debug("Params draw:{} start:{},length:{},search:{},sort:{} ", draw, start, length, search, sort);

        List<SingleJobInfo> jobInfoModels = getJobStatus(false);
        Collections.reverse(jobInfoModels);//newest first

        //handle search
        DateFormat df = new SimpleDateFormat("MM/dd/yyyy hh:mm a");
        if (search != null && search.length() > 0) {
            search = search.toLowerCase();
            List<SingleJobInfo> search_results = new ArrayList<SingleJobInfo>();
            for (int i = 0; i < jobInfoModels.size(); i++) {
                SingleJobInfo info = jobInfoModels.get(i);
                //apply search to id,pipeline name, status, dates
                if (info.getJobId().toString().contains(search) ||
                        (info.getPipelineName() != null && info.getPipelineName().toLowerCase().contains(search)) ||
                        (info.getJobStatus() != null && info.getJobStatus().toLowerCase().contains(search)) ||
                        (info.getEndDate() != null && df.format(info.getEndDate()).toLowerCase().contains(search)) ||
                        (info.getStartDate() != null && df.format(info.getStartDate()).toLowerCase().contains(search))) {
                    search_results.add(info);
                }
            }
            jobInfoModels = search_results;
        }

        JobPageListModel model = new JobPageListModel();
        model.setRecordsTotal(jobInfoModels.size());
        model.setRecordsFiltered(jobInfoModels.size());// Total records, after filtering (i.e. the total number of records after filtering has been applied - not just the number of records being returned for this page of data).

        //handle paging
        int end = start + length;
        end = (end > model.getRecordsTotal()) ? model.getRecordsTotal() : end;
        start = (start <= end) ? start : end;
        List<SingleJobInfo> jobInfoModelsFiltered = jobInfoModels.subList(start, end);

        //convert for output
        for (int i = 0; i < jobInfoModelsFiltered.size(); i++) {
            SingleJobInfo job = jobInfoModelsFiltered.get(i);
            JobPageModel job_model = new JobPageModel(job);
            List<MarkupResult> markupResults = mpfService.getMarkupResultsForJob(job.getJobId());
            job_model.setMarkupCount(markupResults.size());
            if(job_model.getOutputObjectPath() != null) {
                File f = new File(job_model.getOutputObjectPath());
                job_model.outputFileExists = (f != null && f.exists());
            }
            model.addData(job_model);
        }

        return model;
    }

    /*
     * GET /jobs/{id}
     */
    //EXTERNAL
    @RequestMapping(value = "/rest/jobs/{id}", method = RequestMethod.GET)
    @ApiOperation(value = "Gets a SingleJobInfo model for the job with the id provided as a path variable.",
            produces = "application/json", response = SingleJobInfo.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful response"),
            @ApiResponse(code = 400, message = "Invalid id"),
            @ApiResponse(code = 401, message = "Bad credentials")})
    @ResponseBody
    public ResponseEntity<SingleJobInfo> getJobStatusRest(@ApiParam(required = true, value = "Job Id") @PathVariable("id") long jobId) {
        JobRequest jobRequest = mpfService.getJobRequest(jobId);
        if (jobRequest == null) {
            log.error("getJobStatusRest: Error retrieving the SingleJobInfo model for the job with id '{}'", jobId);
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.ok(convertJob(jobRequest));
    }

    //INTERNAL
    @RequestMapping(value = "/jobs/{id}", method = RequestMethod.GET)
    @ResponseBody
    public SingleJobInfo getJobStatus(@PathVariable("id") long jobId,
                                      @RequestParam(value = "useSession", required = false) boolean useSession) {
        if (useSession && !sessionModel.getSessionJobs().contains(jobId)) {
            return null;
        }

        JobRequest jobRequest = mpfService.getJobRequest(jobId);
        if (jobRequest == null) {
            log.error("getJobStatus: Error retrieving the SingleJobInfo model for the job with id '{}'", jobId);
            return null;
        }
        return convertJob(jobRequest);
    }

    /*
     * GET /jobs/{id}/output/detection
     */
    //EXTERNAL
    @RequestMapping(value = "/rest/jobs/{id}/output/detection", method = RequestMethod.GET)
    @ApiOperation(value = "Gets the JSON detection output object of a specific job using the job id as a required path variable.",
            produces = "application/json", response = JsonOutputObject.class)
    @ApiResponses({
            @ApiResponse(code = 200, message = "Successful response"),
            @ApiResponse(code = 401, message = "Bad credentials"),
            @ApiResponse(code = 404, message = "Invalid id")})
    @ResponseBody
    public ResponseEntity<?> getSerializedDetectionOutputRest(@ApiParam(required = true, value = "Job id") @PathVariable("id") long jobId) throws IOException {
        //return 200 for successful GET and object; 404 for bad id
        return getJobOutputObjectAsString(jobId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    //INTERNAL
    @RequestMapping(value = "/jobs/output-object", method = RequestMethod.GET)
    public ModelAndView getOutputObject(@RequestParam(value = "id", required = true) long idParam) throws IOException {
        return getJobOutputObjectAsString(idParam)
                .map(jsonStr -> new ModelAndView("output_object", "jsonObj", jsonStr))
                .orElseThrow(() -> new IllegalStateException(idParam + " does not appear to be a valid Job Id."));
    }

    private Optional<String> getJobOutputObjectAsString(long jobId) throws IOException {
        JobRequest jobRequest = mpfService.getJobRequest(jobId);
        if (jobRequest == null) {
            return Optional.empty();
        }
        try (Stream<String> lines = Files.lines(Paths.get(jobRequest.getOutputObjectPath()))) {
            return Optional.of(lines.collect(joining()));
        }
    }

    /*
     * POST /jobs/{id}/resubmit
     */
    //EXTERNAL
    @RequestMapping(value = "/rest/jobs/{id}/resubmit", method = RequestMethod.POST)
    @ApiOperation(value = "Resubmits the job with the provided job id. If the job priority parameter is not set the default value will be used.",
            produces = "application/json", response = JobCreationResponse.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful resubmission request"),
            @ApiResponse(code = 400, message = "Invalid id"),
            @ApiResponse(code = 401, message = "Bad credentials")})
    @ResponseBody
    @ResponseStatus(value = HttpStatus.OK) //return 200 for post in this case
    public ResponseEntity<JobCreationResponse> resubmitJobRest(@ApiParam(required = true, value = "Job id") @PathVariable("id") long jobId,
                                                               @ApiParam(value = "Job priority (0-9 with 0 being the lowest) - OPTIONAL") @RequestParam(value = "jobPriority", required = false) Integer jobPriorityParam) {
        JobCreationResponse resubmitResponse = resubmitJobInternal(jobId, jobPriorityParam);
        if (resubmitResponse.getMpfResponse().getResponseCode() == MpfResponse.RESPONSE_CODE_SUCCESS) {
            return new ResponseEntity<>(resubmitResponse, HttpStatus.OK);
        } else {
            log.error("Error resubmitting job with id '{}'", jobId);
            return new ResponseEntity<>(resubmitResponse, HttpStatus.BAD_REQUEST);
        }
    }

    //INTERNAL
    @RequestMapping(value = "/jobs/{id}/resubmit", method = RequestMethod.POST)
    @ResponseBody
    @ResponseStatus(value = HttpStatus.OK) //return 200 for post in this case
    public JobCreationResponse resubmitJob(@PathVariable("id") long jobId,
                                           @RequestParam(value = "jobPriority", required = false) Integer jobPriorityParam) {
        JobCreationResponse response = resubmitJobInternal(jobId, jobPriorityParam);
        sessionModel.getSessionJobs().add(response.getJobId());
        return response;
    }

    /*
     * POST /jobs/{id}/cancel
     */
    //EXTERNAL
    @RequestMapping(value = "/rest/jobs/{id}/cancel", method = RequestMethod.POST)
    @ApiOperation(value = "Cancels the job with the supplied job id.",
            produces = "application/json", response = MpfResponse.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful cancellation attempt"),
            @ApiResponse(code = 400, message = "Invalid id"),
            @ApiResponse(code = 401, message = "Bad credentials")})
    @ResponseBody
    @ResponseStatus(value = HttpStatus.OK) //return 200 for post in this case
    public ResponseEntity<MpfResponse> cancelJobRest(@ApiParam(required = true, value = "Job id") @PathVariable("id") long jobId) {
        MpfResponse mpfResponse = cancelJobInternal(jobId);
        if (mpfResponse.getResponseCode() == MpfResponse.RESPONSE_CODE_SUCCESS) {
            return new ResponseEntity<>(mpfResponse, HttpStatus.OK);
        } else {
            log.error("Error cancelling job with id '{}'", jobId);
            return new ResponseEntity<>(mpfResponse, HttpStatus.BAD_REQUEST);
        }
    }

    //INTERNAL
    @RequestMapping(value = "/jobs/{id}/cancel", method = RequestMethod.POST)
    @ResponseBody
    @ResponseStatus(value = HttpStatus.OK) //return 200 for post in this case
    public MpfResponse cancelJob(@PathVariable("id") long jobId) {
        return cancelJobInternal(jobId);
    }

    private JobCreationResponse createJobCreationErrorResponse(String externalId,
        String errorReason) {
        StringBuilder errBuilder = new StringBuilder("Failure creating batch job");
        if ( externalId != null ) {
            errBuilder.append(String.format(" with external id '%s'", externalId));
        }
        errBuilder.append(" due to " + errorReason + ". Please check the request parameters against the constraints defined in the REST API.");
        String err = errBuilder.toString();
        log.error(err);
        return new JobCreationResponse(MpfResponse.RESPONSE_CODE_ERROR, err);
    }

    private JobCreationResponse createJobInternal(JobCreationRequest jobCreationRequest, boolean useSession) {
        try {

            if ( !pipelineService.pipelineSupportsBatch(jobCreationRequest.getPipelineName()) ) {
                // The batch job failed the pipeline check. The requested pipeline doesn't support batch.
                // Reject the job and send an error response.
                return createJobCreationErrorResponse(jobCreationRequest.getExternalId(), "Requested pipeline doesn't support batch jobs");

            } else {

                boolean buildOutput = propertiesUtil.isOutputObjectsEnabled();
                if (jobCreationRequest.getBuildOutput() != null) {
                    buildOutput = jobCreationRequest.getBuildOutput();
                }

                int priority = propertiesUtil.getJmsPriority();
                if (jobCreationRequest.getPriority() != null) {
                    priority = jobCreationRequest.getPriority();
                }

                JsonJobRequest jsonJobRequest;
                List<JsonMediaInputObject> media = new ArrayList<>();
                // Iterate over all media in the batch job creation request.  If for any media, the media protocol check fails, then
                // that media will be ignored from the batch job
                for (JobCreationMediaData mediaRequest : jobCreationRequest.getMedia()) {
                    JsonMediaInputObject medium = new JsonMediaInputObject(
                        mediaRequest.getMediaUri());
                    if (mediaRequest.getProperties() != null) {
                        for (Map.Entry<String, String> property : mediaRequest.getProperties()
                            .entrySet()) {
                            medium.getProperties()
                                .put(property.getKey().toUpperCase(), property.getValue());
                        }
                    }
                    media.add(medium);
                }
                if (jobCreationRequest.getCallbackURL() != null
                    && jobCreationRequest.getCallbackURL().length() > 0) {
                    jsonJobRequest = mpfService.createJob(media,
                        jobCreationRequest.getAlgorithmProperties(),
                        jobCreationRequest.getJobProperties(),
                        jobCreationRequest.getPipelineName(),
                        jobCreationRequest.getExternalId(), //TODO: what do we do with this from the UI?
                        buildOutput,  // Use the buildOutput value if it is provided, otherwise use the default value from the properties file.,
                        priority,     // Use the priority value if it is provided, otherwise use the default value from the properties file.
                        jobCreationRequest.getCallbackURL(),
                        jobCreationRequest.getCallbackMethod());

                } else {
                    jsonJobRequest = mpfService.createJob(media,
                        jobCreationRequest.getAlgorithmProperties(),
                        jobCreationRequest.getJobProperties(),
                        jobCreationRequest.getPipelineName(),
                        jobCreationRequest.getExternalId(),  //TODO: what do we do with this from the UI?
                        buildOutput,  // Use the buildOutput value if it is provided, otherwise use the default value from the properties file.,
                        priority); // Use the priority value if it is provided, otherwise use the default value from the properties file.);
                }
                long jobId = mpfService.submitJob(jsonJobRequest);
                log.debug("Successful creation of batch JobId: {}", jobId);

                if (useSession) {
                    sessionModel.getSessionJobs().add(jobId);
                }
                // the job request has been successfully parsed, construct the job creation response
                return new JobCreationResponse(jobId);
            }
        } catch (InvalidPipelineObjectWfmProcessingException ex) {
            String err = createErrorString(jobCreationRequest, ex.getMessage());
            log.error(err, ex);
            return new JobCreationResponse(MpfResponse.RESPONSE_CODE_ERROR, err);
        } catch (Exception ex) { //exception handling - can't throw exception - currently an html page will be returned
            String err = createErrorString(jobCreationRequest, null);
            log.error(err, ex);
            // the job request did not parse successfully, construct the job creation response describing the error that occurred.
            return new JobCreationResponse(MpfResponse.RESPONSE_CODE_ERROR, err);
        }
    }

    private String createErrorString(JobCreationRequest jobCreationRequest, String message) {
        StringBuilder errBuilder = new StringBuilder("Failure creating job");
        if (jobCreationRequest.getExternalId() != null) {
            errBuilder.append(String.format(" with external id '%s'", jobCreationRequest.getExternalId()));
        }
        if (message != null) {
            errBuilder.append(". ").append(message);
        } else {
            errBuilder.append(" due to an exception. Please check server logs for more detail.");
        }
        return errBuilder.toString();
    }


    private SingleJobInfo convertJob(JobRequest job) {
        float jobProgressVal = jobProgress.getJobProgress(job.getId()) != null
                ? jobProgress.getJobProgress(job.getId())
                : 0.0f;
        return ModelUtils.convertJobRequest(job, jobProgressVal);
    }


    private JobCreationResponse resubmitJobInternal(long jobId, Integer jobPriorityParam) {
        log.debug("Attempting to resubmit job with id: {}.", jobId);
        //if there is a priority param passed then use it, if not, use the default
        int jobPriority = (jobPriorityParam != null) ? jobPriorityParam : propertiesUtil.getJmsPriority();
        try {
            long newJobId = mpfService.resubmitJob(jobId, jobPriority);
            //newJobId should be equal to jobId if there are no issues and -1 if there is a problem
            if (newJobId != -1 && newJobId == jobId) {
                //make sure to reset the value in the job progress map to handle manual refreshes that will display
                //the old progress value (100 in most cases converted to 99 because of the INCOMPLETE STATE)!
                jobProgress.setJobProgress(newJobId, 0.0f);
                log.debug("Successful resubmission of Job Id: {} as new JobId: {}", jobId, newJobId);
                return new JobCreationResponse(newJobId);
            }
        } catch (WfmProcessingException wpe) {
            String errorStr = "Failed to resubmit the job with id '" + Long.toString(jobId) + "'. " + wpe.getMessage();
            log.error(errorStr);
            return new JobCreationResponse(MpfResponse.RESPONSE_CODE_ERROR, errorStr);
        }
        String errorStr = "Failed to resubmit the job with id '" + Long.toString(jobId) + "'. Please check to make sure the job exists before submitting a resubmit request. "
                + "Also consider checking the server logs for more information on this error.";
        log.error(errorStr);
        return new JobCreationResponse(MpfResponse.RESPONSE_CODE_ERROR, errorStr);
    }

    private MpfResponse cancelJobInternal(long jobId) {
        log.debug("Attempting to cancel job with id: {}.", jobId);
        if (mpfService.cancel(jobId)) {
            log.debug("Successful cancellation of job with id: {}");
            return new MpfResponse(MpfResponse.RESPONSE_CODE_SUCCESS, null);
        }
        String errorStr = "Failed to cancel the job with id '" + Long.toString(jobId) + "'. Please check to make sure the job exists before submitting a cancel request. "
                + "Also consider checking the server logs for more information on this error.";
        log.error(errorStr);
        return new MpfResponse(MpfResponse.RESPONSE_CODE_ERROR, errorStr);
    }
}
