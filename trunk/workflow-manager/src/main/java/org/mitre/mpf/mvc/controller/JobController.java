/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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

import com.amazonaws.services.s3.model.S3Object;
import com.google.common.collect.ImmutableMap;
import io.swagger.annotations.*;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.mvc.model.SessionModel;
import org.mitre.mpf.mvc.util.CloseableMdc;
import org.mitre.mpf.mvc.util.MdcUtil;
import org.mitre.mpf.rest.api.*;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.businessrules.JobRequestService;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.access.JobRequestDao;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.event.JobProgress;
import org.mitre.mpf.wfm.service.S3StorageBackend;
import org.mitre.mpf.wfm.service.StorageException;
import org.mitre.mpf.wfm.util.InvalidJobIdException;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

// swagger includes

@Api(value = "Jobs",
        description = "Job create, status, cancel, resubmit, output")
@Controller
@Scope("request")
public class JobController {
    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    @Autowired
    private PropertiesUtil propertiesUtil;

    @Autowired
    private JobRequestService jobRequestService;

    @Autowired
    private JobRequestDao jobRequestDao;

    @Autowired
    private SessionModel sessionModel;

    @Autowired
    private JobProgress jobProgress;

    @Autowired
    private JsonUtils jsonUtils;

    @Autowired
    private S3StorageBackend s3StorageBackend;

    @Autowired
    private InProgressBatchJobsService inProgressJobs;

    @ExceptionHandler(InvalidJobIdException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public MessageModel invalidJobIdHandler(InvalidJobIdException ex) {
        log.error(ex.getMessage(), ex);
        return new MessageModel(ex.getMessage());
    }

    /*
     *	POST /jobs
     */
    //EXTERNAL
    @RequestMapping(value = {"/rest/jobs"}, method = RequestMethod.POST)
    @ApiOperation(value = "Creates and submits a job using a JSON JobCreationRequest object as the request body.",
            notes = "The pipelineName should be one of the values in 'rest/pipelines'. The media array should" +
                    " contain objects with a valid mediaUri. \n\nFor example: http://localhost/images/image.png. " +
                    " \nAnother example: file:///home/user/images/image.jpg. \n\nA callbackURL (optional) and" +
                    " callbackMethod (GET or POST) may be added. When the job completes, the callback will perform" +
                    " a GET or POST to the callbackURL with the jobId, externalId, and outputObjectUri parameters." +
                    " \n\nFor example, if the callbackURL provided is 'http://api.example.com/foo', the jobId is 'hostname-1'," +
                    " and the externalId is 'someid', then a GET callback will be: " +
                    " http://api.example.com/foo?jobid=hostname-1&externalid=someid&outputobjecturi=file%3A%2F%2F%2Fpath%2Fto%2F1%2Fdetection.json." +
                    " \n\nIf callbackURL ends in 'foo?someparam=something', then a GET callback will be:" +
                    " http://api.example.com/foo?someparam=something&jobid=hostname-1&externalid=someid&outputobjecturi=file%3A%2F%2F%2Fpath%2Fto%2F1%2Fdetection.json. " +
                    " \n\nIf no externalId is provided, then a GET callback will be:" +
                    " http://api.example.com/foo?jobid=hostname-1&outputobjecturi=file%3A%2F%2F%2Fpath%2Fto%2F1%2Fdetection.json." +
                    " \n\nThe body of a POST callback will always include the 'jobId', 'externalId', and" +
                    " 'outputObjectUri', even if the latter two are null." +
                    " \n\nAlso, note that all provided URIs must be properly encoded." +
                    " \n\nThe job id that is reported from OpenMPF is a string consisting of the hostname where the job was run plus the" +
                    " numeric job id used internally by OpenMPF." +
                    " \n\nAn optional jobProperties object contains String key-value pairs which override the pipeline's" +
                    " job properties for this job." +
                    " \n\nAn optional algorithmProperties object containing <String,Map> key-value pairs can override" +
                    " jobProperties for a specific algorithm defined in the pipeline." +
                    " \nFor algorithmProperties, the key should be the algorithm name, and the value should be a" +
                    " Map of String key-value pairs representing properties specific to the named algorithm." +
                    " \n\n For videos, an optional list of frame ranges or time ranges to limit which sections " +
                    "of the video will be processed. Regular segmenting will be applied, except " +
                    "that no gaps between user specified ranges will be filled. The ranges can be " +
                    "specified as frame ranges or time ranges in milliseconds." +
                    " \n\nWithin media, an optional metadata object containing String key-value pairs can override" +
                    " media inspection once the required metadata information is provided for audio, image, generic, and video jobs." +
                    " \nFor media metadata, note that optional parameters like `ROTATION` and `HORIZONTAL_FLIP` can also be provided.",
            produces = "application/json", response = JobCreationResponse.class)
    @ApiResponses({
            @ApiResponse(code = 201, message = "Job created"),
            @ApiResponse(code = 400, message = "Bad request") })
    @ResponseBody
    @ResponseStatus(value = HttpStatus.CREATED) //return 201 for successful post
    public ResponseEntity<JobCreationResponse> createJobRest(
            @ApiParam(required = true, value = "JobCreationRequest") @RequestBody
                    JobCreationRequest jobCreationRequest) {

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
                    .map(id -> convertToSingleJobInfo(jobRequestDao.findById(id)))
                    .collect(toList());
        }
        else {
            return jobRequestDao.findAll()
                    .stream()
                    .map(this::convertToSingleJobInfo)
                    .collect(toList());
        }
    }


    private static final Map<String, String> JOB_TABLE_COLUMN_NAMES
            = ImmutableMap.<String, String>builder()
            .put("0", "id")
            .put("1", "pipeline")
            .put("2", "timeReceived")
            .put("3", "timeCompleted")
            .put("4", "status")
            .put("5", "priority")
            .build();


    // INTERNAL
    // Parameters come from DataTables library: https://datatables.net/manual/server-side
    @RequestMapping(value = {"/jobs-paged"}, method = RequestMethod.POST)
    @ResponseBody
    public JobPageListModel getJobStatusFiltered(
            @RequestParam(value = "draw", required = false) Integer draw,
            @RequestParam(value = "start", required = false) int start,
            @RequestParam(value = "length", required = false) int length,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "order[0][column]", defaultValue = "0") String orderByColumn,
            @RequestParam(value = "order[0][dir]", defaultValue = "desc") String orderDirection) {
        log.debug("Params draw:{} start:{},length:{},search:{}", draw, start, length, search);

        String sortColumn = JOB_TABLE_COLUMN_NAMES.get(orderByColumn);
        String sortOrderDirection = orderDirection.equals("desc") ? orderDirection : "asc";

        //handle paging
        List<JobPageModel> jobPageModels = jobRequestDao
                .findByPage(length, start, search, sortColumn, sortOrderDirection)
                .stream()
                .map(this::convertToJobPageModel)
                .collect(toList());
        int recordsTotal = (int) jobRequestDao.countAll();
        int recordsFiltered = search.equals("") ? recordsTotal :
                (int)jobRequestDao.countFiltered(search);
        return new JobPageListModel(draw, recordsTotal, recordsFiltered, null, jobPageModels);
    }


    /*
     * GET /jobs/{id}
     */
    //EXTERNAL
    @RequestMapping(value = "/rest/jobs/{id}", method = RequestMethod.GET)
    @ApiOperation(value = "Gets a SingleJobInfo model for the job with the id provided as a path variable.",
            produces = "application/json", response = SingleJobInfo.class)
    @ApiResponses({
            @ApiResponse(code = 200, message = "Successful response"),
            @ApiResponse(code = 400, message = "Invalid id") })
    @ResponseBody
    public ResponseEntity<SingleJobInfo> getJobStatusRest(@ApiParam(required = true, value = "Job Id") @PathVariable("id") String jobId) {
        long internalJobId = propertiesUtil.getJobIdFromExportedId(jobId);
        try (var mdc = CloseableMdc.job(internalJobId)) {
            JobRequest jobRequest = jobRequestDao.findById(internalJobId);
            if (jobRequest == null) {
                log.error("getJobStatusRest: Error retrieving the SingleJobInfo model for the job " +
                                  "with id '{}'", jobId);
                return ResponseEntity.badRequest().body(null);
            }
            return ResponseEntity.ok(convertToSingleJobInfo(jobRequest));
        }
    }

    //INTERNAL
    @RequestMapping(value = "/jobs/{id}", method = RequestMethod.GET)
    @ResponseBody
    public SingleJobInfo getJobStatus(@PathVariable("id") String jobId,
                                      @RequestParam(value = "useSession", required = false) boolean useSession) {
        long internalJobId = propertiesUtil.getJobIdFromExportedId(jobId);

        try (var mdc = CloseableMdc.job(internalJobId)) {
            if (useSession && !sessionModel.getSessionJobs().contains(internalJobId)) {
                return null;
            }

            JobRequest jobRequest = jobRequestDao.findById(internalJobId);
            if (jobRequest == null) {
                log.error("getJobStatus: Error retrieving the SingleJobInfo model for the job " +
                                  "with id '{}'", jobId);
                return null;
            }
            return convertToSingleJobInfo(jobRequest);
        }
    }

    /*
     * GET /jobs/{id}/output/detection
     */
    //EXTERNAL
    @RequestMapping(value = { "/rest/jobs/{id}/output/detection", "/jobs/{id}/output/detection"},
                    method = RequestMethod.GET)
    @ApiOperation(value = "Gets the JSON detection output object of a specific job using the job id as a required path variable.",
            produces = "application/json", response = JsonOutputObject.class)
    @ApiResponses({
            @ApiResponse(code = 200, message = "Successful response"),
            @ApiResponse(code = 404, message = "Invalid id")})
    @ResponseBody
    public ResponseEntity<?> getSerializedDetectionOutputRest(@ApiParam(required = true, value = "Job id") @PathVariable("id") String jobId) throws IOException {
        long internalJobId = propertiesUtil.getJobIdFromExportedId(jobId);
        try (var mdc = CloseableMdc.job(internalJobId)) {
            //return 200 for successful GET and object; 404 for bad id
            JobRequest jobRequest = jobRequestDao.findById(internalJobId);
            if (jobRequest == null || jobRequest.getOutputObjectPath() == null) {
                return ResponseEntity.notFound().build();
            }
            try {
                URI outputObjectUri = URI.create(jobRequest.getOutputObjectPath());
                if ("file".equalsIgnoreCase(outputObjectUri.getScheme())) {
                    return ResponseEntity.ok(new FileSystemResource(new File(outputObjectUri)));
                }

                var job = jsonUtils.deserialize(jobRequest.getJob(), BatchJob.class);
                InputStreamResource inputStreamResource;
                if (S3StorageBackend.requiresS3ResultUpload(job.getJobProperties()::get)) {
                    S3Object s3Object = s3StorageBackend.getFromS3(jobRequest.getOutputObjectPath(),
                                                                   job.getJobProperties()::get);
                    inputStreamResource = new InputStreamResource(s3Object.getObjectContent());
                }
                else {
                    inputStreamResource = new InputStreamResource(IoUtils.openStream(
                            jobRequest.getOutputObjectPath()));
                }
                return ResponseEntity.ok(inputStreamResource);
            }
            catch (NoSuchFileException e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(String.format("The output object for job %s does not exist.", jobId));
            }
            catch (StorageException e) {
                var msg = String.format(
                        "An error occurred while trying to access the output object for job %s: %s",
                        jobId, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(msg);
            }
        }
    }


    //INTERNAL
    @RequestMapping(value = "/jobs/output-object", method = RequestMethod.GET)
    public ModelAndView getOutputObject(@RequestParam(value = "id", required = true) String idParam) {
        long internalJobId = propertiesUtil.getJobIdFromExportedId(idParam);
        return MdcUtil.job(internalJobId, () ->
                new ModelAndView("output_object", "jobId", idParam));
    }


    /*
     * POST /jobs/{id}/resubmit
     */
    //EXTERNAL
    @RequestMapping(value = "/rest/jobs/{id}/resubmit", method = RequestMethod.POST)
    @ApiOperation(value = "Resubmits the job with the provided job id. If the job priority parameter is not set the default value will be used.",
            produces = "application/json", response = JobCreationResponse.class)
    @ApiResponses({
            @ApiResponse(code = 200, message = "Successful resubmission request"),
            @ApiResponse(code = 400, message = "Invalid id") })
    @ResponseBody
    @ResponseStatus(value = HttpStatus.OK) //return 200 for post in this case
    public ResponseEntity<JobCreationResponse> resubmitJobRest(@ApiParam(required = true, value = "Job id") @PathVariable("id") String jobId,
                                                               @ApiParam(value = "Job priority (0-9 with 0 being the lowest) - OPTIONAL") @RequestParam(value = "jobPriority", required = false) Integer jobPriorityParam) {
        long internalJobId = propertiesUtil.getJobIdFromExportedId(jobId);
        try (var mdc = CloseableMdc.job(internalJobId)) {
            JobCreationResponse resubmitResponse = resubmitJobInternal(internalJobId, jobPriorityParam);
            if (resubmitResponse.getMpfResponse()
                    .getResponseCode() == MpfResponse.RESPONSE_CODE_SUCCESS) {
                return new ResponseEntity<>(resubmitResponse, HttpStatus.OK);
            }
            else {
                log.error("Error resubmitting job with id '{}'", jobId);
                return new ResponseEntity<>(resubmitResponse, HttpStatus.BAD_REQUEST);
            }
        }
    }

    //INTERNAL
    @RequestMapping(value = "/jobs/{id}/resubmit", method = RequestMethod.POST)
    @ResponseBody
    @ResponseStatus(value = HttpStatus.OK) //return 200 for post in this case
    public JobCreationResponse resubmitJob(@PathVariable("id") String jobId,
                                           @RequestParam(value = "jobPriority", required = false) Integer jobPriorityParam) {
        long internalJobId = propertiesUtil.getJobIdFromExportedId(jobId);
        try (var mdc = CloseableMdc.job(internalJobId)) {
            JobCreationResponse response = resubmitJobInternal(internalJobId, jobPriorityParam);
            sessionModel.getSessionJobs().add(propertiesUtil.getJobIdFromExportedId(response.getJobId()));
            return response;
        }
    }

    /*
     * POST /jobs/{id}/cancel
     */
    //EXTERNAL
    @RequestMapping(value = "/rest/jobs/{id}/cancel", method = RequestMethod.POST)
    @ApiOperation(value = "Cancels the job with the supplied job id.",
            produces = "application/json", response = MpfResponse.class)
    @ApiResponses({
            @ApiResponse(code = 200, message = "Successful cancellation attempt"),
            @ApiResponse(code = 400, message = "Invalid id") })
    @ResponseBody
    @ResponseStatus(value = HttpStatus.OK) //return 200 for post in this case
    public ResponseEntity<MpfResponse> cancelJobRest(
            @ApiParam(required = true, value = "Job id") @PathVariable("id") String jobId) {
        long internalJobId = propertiesUtil.getJobIdFromExportedId(jobId);
        try (var mdc = CloseableMdc.job(internalJobId)) {
            MpfResponse mpfResponse = cancelJobInternal(internalJobId);
            if (mpfResponse.getResponseCode() == MpfResponse.RESPONSE_CODE_SUCCESS) {
                return new ResponseEntity<>(mpfResponse, HttpStatus.OK);
            } else {
                log.error("Error cancelling job with id '{}'", internalJobId);
                return new ResponseEntity<>(mpfResponse, HttpStatus.BAD_REQUEST);
            }
        }
    }

    //INTERNAL
    @RequestMapping(value = "/jobs/{id}/cancel", method = RequestMethod.POST)
    @ResponseBody
    @ResponseStatus(value = HttpStatus.OK) //return 200 for post in this case
    public MpfResponse cancelJob(@PathVariable("id") String jobId) {
        long internalJobId = propertiesUtil.getJobIdFromExportedId(jobId);
        return MdcUtil.job(internalJobId, () -> cancelJobInternal(internalJobId));
    }

    private JobCreationResponse createJobInternal(JobCreationRequest jobCreationRequest, boolean useSession) {
        try {
            long jobId = jobRequestService.run(jobCreationRequest).getId();

            if (useSession) {
                sessionModel.getSessionJobs().add(jobId);
            }
            String exportedJobId = propertiesUtil.getExportedJobId(jobId);
            // the job request has been successfully parsed, construct the job creation response
            return new JobCreationResponse(exportedJobId);
        }
        catch (Exception ex) {
            String err = createErrorString(jobCreationRequest, ex.getMessage());
            log.error(err, ex);
            return new JobCreationResponse(MpfResponse.RESPONSE_CODE_ERROR, err);
        }
    }

    private static String createErrorString(JobCreationRequest jobCreationRequest, String message) {
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

    private List<String> getMediaUris(JobRequest job) {

        List<String> mediaUris;
        try {
            // Currently, it is not possible for job.getJob() to be null, but in previous
            // versions it was possible. We don't want the jobs page to become unusable if a
            // user has a job from an older version.
            if (job.getJob() == null)  {
                log.error("Unable to determine mediaUris for job {}.", job.getId());
                mediaUris = List.of();
            }
            else {
                var batchJob = jsonUtils.deserialize(job.getJob(), BatchJob.class);
                mediaUris = batchJob.getMedia().stream()
                        .map(Media::getUri)
                        .collect(toList());
            }
        }
        catch (WfmProcessingException e) {
            log.error(String.format(
                    "Unable to determine mediaUris for job %s due to: %s", job.getId(), e), e);
            mediaUris = List.of();
        }
        return mediaUris;
    }

    private SingleJobInfo convertToSingleJobInfo(JobRequest job) {
        try (var mdc = CloseableMdc.job(job.getId())) {
            float jobProgressVal = jobProgress.getJobProgress(job.getId())
                    .orElseGet(() -> job.getStatus().isTerminal() ? 100 : 0.0f);

            List<String> mediaUris = getMediaUris(job);

            return new SingleJobInfo(
                    propertiesUtil.getExportedJobId(job.getId()),
                    job.getPipeline(),
                    job.getPriority(),
                    job.getStatus().toString(),
                    jobProgressVal,
                    job.getTimeReceived(),
                    job.getTimeCompleted(),
                    job.getOutputObjectPath(),
                    inProgressJobs.jobHasCallbacksInProgress(job.getId()),
                    job.getStatus().isTerminal(),
                    mediaUris);
        }
    }


    private JobPageModel convertToJobPageModel(JobRequest job) {
        try (var mdc = CloseableMdc.job(job.getId())) {
            float jobProgressVal = jobProgress.getJobProgress(job.getId())
                    .orElseGet(() -> job.getStatus().isTerminal() ? 100 : 0.0f);

            List<String> mediaUris = getMediaUris(job);

            JobPageModel jobModel = new JobPageModel(job.getId(),
                                    job.getPipeline(),
                                    job.getPriority(),
                                    job.getStatus().toString(),
                                    jobProgressVal,
                                    job.getTimeReceived(),
                                    job.getTimeCompleted(),
                                    job.getOutputObjectPath(),
                                    inProgressJobs.jobHasCallbacksInProgress(job.getId()),
                                    job.getStatus().isTerminal(),
                                    mediaUris);

            if(job.getOutputObjectPath() != null) {
                jobModel.setOutputFileExists(
                        IoUtils.toLocalPath(job.getOutputObjectPath())
                                .map(Files::exists)
                                .orElse(true));
            }
            return jobModel;
        }
    }


    private JobCreationResponse resubmitJobInternal(long jobId, Integer jobPriorityParam) {
        log.debug("Attempting to resubmit job with id: {}.", jobId);
        //if there is a priority param passed then use it, if not, use the default
        int jobPriority = (jobPriorityParam != null) ? jobPriorityParam : propertiesUtil.getJmsPriority();
        try {
            jobRequestService.resubmit(jobId, jobPriority);
            //make sure to reset the value in the job progress map to handle manual refreshes that will display
            //the old progress value (100 in most cases converted to 99 because of the INCOMPLETE STATE)!
            jobProgress.setJobProgress(jobId, 0);
            log.debug("Successful resubmission of Job Id: {}", jobId);
            String exportedJobId = propertiesUtil.getExportedJobId(jobId);
            return new JobCreationResponse(exportedJobId);
        } catch (Exception wpe) {
            String errorStr = "Failed to resubmit the job with id '" + jobId + "'. " + wpe.getMessage();
            log.error(errorStr, wpe);
            return new JobCreationResponse(MpfResponse.RESPONSE_CODE_ERROR, errorStr);
        }
    }

    private MpfResponse cancelJobInternal(long jobId) {
        log.debug("Attempting to cancel job with id: {}.", jobId);
        boolean wasCancelled;
        try {
            wasCancelled = jobRequestService.cancel(jobId);
        } catch ( WfmProcessingException wpe ) {
            log.error("Failed to cancel Batch Job #{} due to an exception.", jobId, wpe);
            wasCancelled = false;
        }
        if (wasCancelled) {
            log.debug("Successful cancellation of job with id: {}", jobId);
            return new MpfResponse(MpfResponse.RESPONSE_CODE_SUCCESS, null);
        }
        String errorStr = "Failed to cancel the job with id '" + jobId + "'. Please check to make sure the job exists before submitting a cancel request. "
                + "Also consider checking the server logs for more information on this error.";
        log.error(errorStr);
        return new MpfResponse(MpfResponse.RESPONSE_CODE_ERROR, errorStr);
    }
}
