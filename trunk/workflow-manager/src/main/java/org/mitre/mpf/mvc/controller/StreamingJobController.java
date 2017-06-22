/******************************************************************************
 * NOTICE                                                                     *
 * *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 * *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 * <p>
 * Copyright 2016 The MITRE Corporation                                       *
 * *
 * Licensed under the Apache License, Version 2.0 (the "License");            *
 * you may not use this file except in compliance with the License.           *
 * You may obtain a copy of the License at                                    *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                              *
 * *
 * Unless required by applicable law or agreed to in writing, software        *
 * distributed under the License is distributed on an "AS IS" BASIS,          *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.   *
 * See the License for the specific language governing permissions and        *
 * limitations under the License.                                             *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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
import org.mitre.mpf.interop.JsonStreamingJobRequest;
import org.mitre.mpf.interop.JsonStreamingInputObject;
import org.mitre.mpf.mvc.model.SessionModel;
import org.mitre.mpf.mvc.util.ModelUtils;
import org.mitre.mpf.rest.api.StreamingJobCancelResponse;
import org.mitre.mpf.rest.api.StreamingJobInfoResponse;
import org.mitre.mpf.rest.api.StreamingJobCreationRequest;
import org.mitre.mpf.rest.api.StreamingJobCreationResponse;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobRequest;
import org.mitre.mpf.wfm.enums.JobStatus;
import org.mitre.mpf.wfm.event.JobProgress;
import org.mitre.mpf.wfm.service.MpfService;
import org.mitre.mpf.wfm.util.JsonUtils;
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

import java.util.*;

import static java.util.stream.Collectors.joining;

// swagger includes

@Api(value = "StreamingJobs",
        description = "Streaming Job create, cancel, pause, resume, getinfo")
@Controller
@Scope("request")
@Profile("website")
public class StreamingJobController {
    private static final Logger log = LoggerFactory.getLogger(StreamingJobController.class);

    @Autowired
    private PropertiesUtil propertiesUtil;

    @Autowired //will grab the impl
    private MpfService mpfService;

    @Autowired
    private SessionModel sessionModel;

    // job progress may not be required for streaming jobs
    @Autowired
    private JobProgress jobProgress;

    // temporary - jsonUtils used for debugging
    @Autowired
    private JsonUtils jsonUtils;


    /*
     *	POST /jobs
     */
    //EXTERNAL
    @RequestMapping(value = {"/rest/streaming/jobs"}, method = RequestMethod.POST)
    @ApiOperation(value = "Creates and submits a streaming job using a JSON StreamingJobCreationRequest object as the request body.",
            notes = "The pipelineName should be one of the values in 'rest/pipelines'. The stream should contain a valid streamURI, " +
                    "for example: rtsp://example.com/media.mp4.  Optional segmentSize, mediaProperties, stallAlertDetectionThreshold, "+
                    "stallAlertRate, stallTimeout and stallCallbackURI may also be defined for the stream. " +
                    "Addtional streaming job options include summaryReportCallbackURI, healthReportCallbackURI, newTrackAlertCallbackUIR and " +
                    "callbackMethod (GET or POST). " +
                    "Other streaming job options include jobProperties object contains String key-value pairs which override the pipeline's job properties for this streaming job. " +
                    "An optional algorithmProperties object containing <String,Map> key-value pairs can override jobProperties for a specific algorithm defined in the pipeline.  "+
                    "For algorithmProperties, the key should be the algorithm name, and the value should be a Map of String key-value pairs representing properties specific to the named algorithm."+
                    "Note that the batch jobs and streaming jobs share a range of valid job ids.  MPF does not guarantee that the ids of a streaming job and a batch job would be unique",
            produces = "application/json", response = StreamingJobCreationResponse.class)
    @ApiResponses(value = {
            @ApiResponse(code = 201, message = "Streaming Job created"),
            @ApiResponse(code = 401, message = "Bad credentials")})
    @ResponseBody
    @ResponseStatus(value = HttpStatus.CREATED) //return 201 for successful post
    public StreamingJobCreationResponse processStreamRest(@ApiParam(required = true, value = "StreamingJobCreationRequest") @RequestBody StreamingJobCreationRequest streamingJobCreationRequest) {
        return processStreamingJobCreationRequest(streamingJobCreationRequest);
    }

    //INTERNAL
    @RequestMapping(value = {"/streaming/jobs"}, method = RequestMethod.POST)
    @ResponseBody
    @ResponseStatus(value = HttpStatus.CREATED) //return 201 for successful post
    public StreamingJobCreationResponse processStreamSession(@RequestBody StreamingJobCreationRequest streamingJobCreationRequest) {
        return processStreamingJobCreationRequest(streamingJobCreationRequest);
    }

    /*
     * GET /streaming/jobs
     */
    //INTERNAL
    @RequestMapping(value = "/streaming/jobs", method = RequestMethod.GET)
    @ResponseBody
    public List<StreamingJobInfoResponse> getJobStatusSession(@RequestParam(value = "useSession", required = false) boolean useSession) {
        return getStreamingJobInfo(null, useSession);
    }


//    @RequestMapping(value = {"/streaming/jobs-paged"}, method = RequestMethod.POST)
//    @ResponseBody
//    public JobPageListModel getJobStatusSessionFiltered(@RequestParam(value = "useSession", required = false) boolean useSession,
//                                                        @RequestParam(value = "draw", required = false) int draw,
//                                                        @RequestParam(value = "start", required = false) int start,
//                                                        @RequestParam(value = "length", required = false) int length,
//                                                        @RequestParam(value = "search", required = false) String search,
//                                                        @RequestParam(value = "sort", required = false) String sort) {
//        log.debug("Params useSession:{} draw:{} start:{},length:{},search:{},sort:{} ", useSession, draw, start, length, search, sort);
//
//        List<StreamingJobInfoResponse> streamingJobInfoModels = getStreamingJobInfo(null, useSession);
//        Collections.reverse(streamingJobInfoModels);//newest first
//
//        //handle search
//        DateFormat df = new SimpleDateFormat("MM/dd/yyyy hh:mm a");
//        if (search != null && search.length() > 0) {
//            search = search.toLowerCase();
//            List<StreamingJobInfoResponse> search_results = new ArrayList<StreamingJobInfoResponse>();
//            for (int i = 0; i < streamingJobInfoModels.size(); i++) {
//                StreamingJobInfoResponse info = streamingJobInfoModels.get(i);
//                //apply search to id,pipeline name, status, dates
//                if (info.getJobId().toString().contains(search) ||
//                        (info.getPipelineName() != null && info.getPipelineName().toLowerCase().contains(search)) ||
//                        (info.getJobStatus() != null && info.getJobStatus().toLowerCase().contains(search)) ||
//                        (info.getEndDate() != null && df.format(info.getEndDate()).toLowerCase().contains(search)) ||
//                        (info.getStartDate() != null && df.format(info.getStartDate()).toLowerCase().contains(search))) {
//                    search_results.add(info);
//                }
//            }
//            streamingJobInfoModels = search_results;
//        }
//
//        JobPageListModel model = new JobPageListModel();
//        model.setRecordsTotal(streamingJobInfoModels.size());
//        model.setRecordsFiltered(streamingJobInfoModels.size());// Total records, after filtering (i.e. the total number of records after filtering has been applied - not just the number of records being returned for this page of data).
//
//        //handle paging
//        int end = start + length;
//        end = (end > model.getRecordsTotal()) ? model.getRecordsTotal() : end;
//        start = (start <= end) ? start : end;
//        List<StreamingJobInfoResponse> streamingJobInfoModelsFiltered = streamingJobInfoModels.subList(start, end);
//
//        //convert for output
//        for (int i = 0; i < streamingJobInfoModelsFiltered.size(); i++) {
//            StreamingJobInfoResponse job = streamingJobInfoModelsFiltered.get(i);
//            JobPageModel job_model = new JobPageModel(job);
//            List<MarkupResult> markupResults = mpfService.getMarkupResultsForJob(job.getJobId());
//            job_model.setMarkupCount(markupResults.size());
//            if(job_model.getOutputObjectPath() != null) {
//                File f = new File(job_model.getOutputObjectPath());
//                job_model.outputFileExists = (f != null && f.exists());
//            }
//            model.addData(job_model);
//        }
//
//        return model;
//    }

    /*
     * GET /streaming/jobs/{id}
     */
    //EXTERNAL
    @RequestMapping(value = "/rest/streaming/jobs/{id}", method = RequestMethod.GET)
    @ApiOperation(value = "Gets a StreamingJobInfoResponse model for the streaming job with the id provided as a path variable.",
            produces = "application/json", response = StreamingJobInfoResponse.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful response"),
            @ApiResponse(code = 400, message = "Invalid id"),
            @ApiResponse(code = 401, message = "Bad credentials")})
    @ResponseBody
    public ResponseEntity<StreamingJobInfoResponse> getJobStatusRest(/* @ApiParam(value="The version of this request - NOT IMPLEMENTED - NOT REQUIRED")
            @RequestParam(value = "v", required = false) String v, */
                                                          @ApiParam(required = true, value = "Streaming Job Id") @PathVariable("id") long jobIdPathVar) {
        StreamingJobInfoResponse streamingJobInfoResponseModel = null;
        List<StreamingJobInfoResponse> streamingJobInfoResponseModels = getStreamingJobInfo(jobIdPathVar, false);
        if (streamingJobInfoResponseModels != null && streamingJobInfoResponseModels.size() == 1) {
            streamingJobInfoResponseModel = streamingJobInfoResponseModels.get(0);
        } else {
            log.error("Error retrieving the StreamingJobInfoResponse model for the streaming job with id '{}'", jobIdPathVar);
        }

        //return 200 for successful GET and object, 401 for bad credentials, 400 for bad id
        return new ResponseEntity<>(streamingJobInfoResponseModel, (streamingJobInfoResponseModel != null) ? HttpStatus.OK : HttpStatus.BAD_REQUEST);
    }

    //INTERNAL
    @RequestMapping(value = "/streaming/jobs/{id}", method = RequestMethod.GET)
    @ResponseBody
    public StreamingJobInfoResponse getStreamingJobStatusWithIdSession(@PathVariable("id") long jobIdPathVar,
                                                                       @RequestParam(value = "useSession", required = false) boolean useSession) {
        List<StreamingJobInfoResponse> streamingJobInfoResponseModels = getStreamingJobInfo(jobIdPathVar, useSession);
        if (streamingJobInfoResponseModels != null && streamingJobInfoResponseModels.size() == 1) {
            return streamingJobInfoResponseModels.get(0);
        }
        log.error("Error retrieving the StreamingJobInfoResponse model for the streaming job with id '{}'", jobIdPathVar);
        return null;
    }

//    /*
//     * /jobs/{id}/output/detection
//     */
//    //EXTERNAL
//    @RequestMapping(value = "/rest/streaming/jobs/{id}/output/detection", method = RequestMethod.GET)
//    @ApiOperation(value = "Gets the JSON detection output object of a specific job using the job id as a required path variable.",
//            produces = "application/json", response = JsonOutputObject.class)
//    @ApiResponses({
//            @ApiResponse(code = 200, message = "Successful response"),
//            @ApiResponse(code = 401, message = "Bad credentials"),
//            @ApiResponse(code = 404, message = "Invalid id")})
//    @ResponseBody
//    public ResponseEntity<?> getSerializedDetectionOutputRest(
//            @ApiParam(required = true, value = "Job id") @PathVariable("id") long idPathVar) throws IOException {
//        //return 200 for successful GET and object, 401 for bad credentials, 404 for bad id
//        return getJobOutputObjectAsString(idPathVar)
//                .map(ResponseEntity::ok)
//                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
//    }
//
//
//    @RequestMapping(value = "/streaming/jobs/output-object", method = RequestMethod.GET)
//    public ModelAndView getOutputObject(@RequestParam(value = "id", required = true) long idParam) throws IOException {
//        return getJobOutputObjectAsString(idParam)
//                .map(jsonStr -> new ModelAndView("output_object", "jsonObj", jsonStr))
//                .orElseThrow(() -> new IllegalStateException(idParam + " does not appear to be a valid Job Id."));
//    }
//
//
//    private Optional<String> getJobOutputObjectAsString(long jobId) throws IOException {
//        JobRequest jobRequest = mpfService.getJobRequest(jobId);
//        if (jobRequest == null) {
//            return Optional.empty();
//        }
//        try (Stream<String> lines = Files.lines(Paths.get(jobRequest.getOutputObjectPath()))) {
//            return Optional.of(lines.collect(joining()));
//        }
//    }
//
//
//    /*
//     * /jobs/{id}/resubmit
//     */
//    //EXTERNAL
//    @RequestMapping(value = "/rest/streaming/jobs/{id}/resubmit", method = RequestMethod.POST)
//    @ApiOperation(value = "Resubmits the job with the provided job id. If the job priority parameter is not set the default value will be used.",
//            produces = "application/json", response = StreamingJobCreationResponse.class)
//    @ApiResponses(value = {
//            @ApiResponse(code = 200, message = "Successful resubmission request"),
//            @ApiResponse(code = 400, message = "Invalid id"),
//            @ApiResponse(code = 401, message = "Bad credentials")})
//    @ResponseBody
//    @ResponseStatus(value = HttpStatus.OK) //return 200 for post in this case
//    public ResponseEntity<StreamingJobCreationResponse> resubmitJobRest(@ApiParam(required = true, value = "Job id") @PathVariable("id") long jobIdPathVar,
//                                                               @ApiParam(value = "Job priority (0-9 with 0 being the lowest) - OPTIONAL") @RequestParam(value = "jobPriority", required = false) Integer jobPriorityParam) {
//        StreamingJobCreationResponse jobCreationResponse = resubmitJobVersionOne(jobIdPathVar, jobPriorityParam);
//
//        //return 200 for successful GET and object, 401 for bad credentials, 400 for bad id
//        //	job id will be -1 in the jobCreationResponse if there was an error cancelling the job
//        return new ResponseEntity<>(jobCreationResponse, (jobCreationResponse.getJobId() == -1) ? HttpStatus.BAD_REQUEST : HttpStatus.OK);
//    }
//
//    //INTERNAL
//    @RequestMapping(value = "/streaming/jobs/{id}/resubmit", method = RequestMethod.POST)
//    @ResponseBody
//    @ResponseStatus(value = HttpStatus.OK) //return 200 for post in this case
//    public StreamingJobCreationResponse resubmitJobSession(@PathVariable("id") long jobIdPathVar,
//                                                  @RequestParam(value = "jobPriority", required = false) Integer jobPriorityParam) {
//        StreamingJobCreationResponse response = resubmitJobVersionOne(jobIdPathVar, jobPriorityParam);
//        addStreamingJobToSession(response.getJobId());
//        return response;
//    }

    /*
     * /streaming/jobs/{id}/cancel
     */
    //EXTERNAL
    @RequestMapping(value = "/rest/streaming/jobs/{id}/cancel", method = RequestMethod.POST)
    @ApiOperation(value = "Cancels the streaming job with the supplied job id.",
            produces = "application/json", response = StreamingJobCancelResponse.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful streaming job cancellation attempt"),
            @ApiResponse(code = 400, message = "Invalid id"),
            @ApiResponse(code = 401, message = "Bad credentials")})
    @ResponseBody
    @ResponseStatus(value = HttpStatus.OK) //return 200 for post in this case
    public ResponseEntity<StreamingJobCancelResponse> cancelStreamingJobRest(@ApiParam(required = true, value = "Streaming Job id") @PathVariable("id") long jobId,
                                                                             @ApiParam(required = false, value = "Enable disk cleanup, if true then files associated with this streaming job will be deleted after this streaming job is cancelled") @PathVariable("doCleanup") Boolean doCleanup) {
        StreamingJobCancelResponse cancelResponse = cancel(jobId,doCleanup);

        //return 200 for successful GET and object, 401 for bad credentials, 400 for bad id
        return new ResponseEntity<>(cancelResponse, (cancelResponse.getMpfResponse().getResponseCode() == 0) ? HttpStatus.OK : HttpStatus.BAD_REQUEST);
    }

    //INTERNAL
    @RequestMapping(value = "/streaming/jobs/{id}/cancel", method = RequestMethod.POST)
    @ResponseBody
    @ResponseStatus(value = HttpStatus.OK) //return 200 for post in this case
    public StreamingJobCancelResponse cancelStreamingJobSession(@PathVariable("id") long jobId, @PathVariable("doCleanup") Boolean doCleanup) {
        return cancel(jobId, doCleanup);
    }

    /*
     * Private methods
     */
    private StreamingJobCreationResponse processStreamingJobCreationRequest(StreamingJobCreationRequest streamingJobCreationRequest) {

        try {
            boolean fromExternalRestClient = true;
            //hack of using 'from_mpf_web_app' as the externalId to prevent duplicating a method and keeping streaming jobs
            //from the web app in the session jobs collections
            if (streamingJobCreationRequest.getExternalId() != null && streamingJobCreationRequest.getExternalId().equals("from_mpf_web_app")) {
                fromExternalRestClient = false;
                streamingJobCreationRequest.setExternalId(null);
            }
            boolean buildOutput = propertiesUtil.isOutputObjectsEnabled();
            if (streamingJobCreationRequest.getBuildOutput() != null) {
                buildOutput = streamingJobCreationRequest.getBuildOutput();
            }

            int priority = propertiesUtil.getJmsPriority();
            if (streamingJobCreationRequest.getPriority() != null) {
                priority = streamingJobCreationRequest.getPriority();
            }

            JsonStreamingInputObject json_stream = new JsonStreamingInputObject(streamingJobCreationRequest.getStreamURI(),
                    streamingJobCreationRequest.getSegmentSize(),
                    streamingJobCreationRequest.getMediaProperties());

            JsonStreamingJobRequest jsonStreamingJobRequest = mpfService.createStreamingJob(json_stream,
                    streamingJobCreationRequest.getAlgorithmProperties(),
                    streamingJobCreationRequest.getJobProperties(),
                    streamingJobCreationRequest.getPipelineName(),
                    streamingJobCreationRequest.getExternalId(), //TODO: what do we do with this from the UI?
                    buildOutput, // Use the buildOutput value if it is provided with the streaming job, otherwise use the default value from the properties file.,
                    priority,// Use the priority value if it is provided, otherwise use the default value from the properties file.
                    streamingJobCreationRequest.getStallAlertDetectionThreshold(),
                    streamingJobCreationRequest.getStallAlertRate(),
                    streamingJobCreationRequest.getStallTimeout(),
                    streamingJobCreationRequest.getHealthReportCallbackURI(),
                    streamingJobCreationRequest.getSummaryReportCallbackURI(),
                    streamingJobCreationRequest.getNewTrackAlertCallbackURI(),
                    streamingJobCreationRequest.getCallbackMethod());

            long jobId = mpfService.submitJob(jsonStreamingJobRequest);
            log.debug("Successful creation of streaming JobId: {}", jobId);

            if (!fromExternalRestClient) {
                addJobToSession(jobId);
            }

            return new StreamingJobCreationResponse(jobId,jsonStreamingJobRequest.getExternalId(),"TBD");
        } catch (Exception ex) { //exception handling - can't throw exception - currently an html page will be returned
            log.error("Failure creating streaming job due to an exception.", ex);
            return new StreamingJobCreationResponse(-1, String.format("Failure creating streaming job with External Id '%s' due to an exception. Please check server logs for more detail.", streamingJobCreationRequest.getExternalId()));
        }
    }

    private void addJobToSession(long jobId) {
        StreamingJobRequest submittedStreamingJobRequest = mpfService.getStreamingJobRequest(jobId);
        boolean isComplete = submittedStreamingJobRequest.getStatus() == JobStatus.COMPLETE;
        sessionModel.getSessionJobsMap().put(submittedStreamingJobRequest.getId(), isComplete);

    }

    /** Get information about all streaming jobs
     * Note: this method requires that batch and streaming jobIds be unique, with no conflicts
     * @param jobId
     * @param useSession
     * @return
     */
    private List<StreamingJobInfoResponse> getStreamingJobInfo(Long jobId, boolean useSession) {
        List<StreamingJobInfoResponse> jobInfoList = new ArrayList<StreamingJobInfoResponse>();
        try {
            List<StreamingJobRequest> jobs = new ArrayList<StreamingJobRequest>();
            if (jobId != null) {
                StreamingJobRequest job = mpfService.getStreamingJobRequest(jobId);
                if (job != null) {
                    if (useSession) {
                        if (sessionModel.getSessionJobsMap().containsKey(jobId)) {
                            jobs.add(job);
                        }
                    } else {
                        jobs.add(job);
                    }
                }
            } else {
                if (useSession) {
                    for (Long keyId : sessionModel.getSessionJobsMap().keySet()) {
                        jobs.add(mpfService.getStreamingJobRequest(keyId));
                    }
                } else {
                    //get all of the streaming jobs
                    jobs = mpfService.getAllStreamingJobRequests();
                }
            }

            for (StreamingJobRequest job : jobs) {
                long id = job.getId();
                StreamingJobInfoResponse streamingJobInfoResponse;

                float jobProgressVal = jobProgress.getJobProgress(id) != null ? jobProgress.getJobProgress(id) : 0.0f;
                streamingJobInfoResponse = ModelUtils.convertJobRequest(job, jobProgressVal);

                jobInfoList.add(streamingJobInfoResponse);
            }
        } catch (Exception ex) {
            log.error("exception in get streaming job status with stack trace: {}", ex.getMessage());
        }

        return jobInfoList;
    }

    private StreamingJobCancelResponse cancel(long jobId, Boolean doCleanup) {
        log.debug("Attempting to cancel streaming job with id: {}.", jobId);
        StreamingJobRequest streamingJobRequest = mpfService.getStreamingJobRequest(jobId);
        if (mpfService.cancelStreamingJob(jobId,doCleanup)) {
            log.debug("Successful cancellation of streaming job with id: {}");
            return new StreamingJobCancelResponse(jobId,streamingJobRequest.getExternalId(),
                    streamingJobRequest.getOutputObjectPath(),doCleanup);
        }
        String errorStr = "Failed to cancel the streaming job with id '" + Long.toString(jobId) + "'. Please check to make sure the streaming job exists before submitting a cancel request. "
                + "Also consider checking the server logs for more information on this error.";
        log.error(errorStr);
        return new StreamingJobCancelResponse( doCleanup, 1, errorStr);
    }


}
