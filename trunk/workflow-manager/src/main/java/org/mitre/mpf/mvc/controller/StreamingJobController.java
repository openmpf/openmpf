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
import org.springframework.security.access.method.P;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    // job progress may not be required for streaming jobs
    @Autowired
    private JobProgress jobProgress;

    /*
     *	POST /jobs
     */
    //EXTERNAL
    @RequestMapping(value = {"/rest/streaming/jobs"}, method = RequestMethod.POST)
    @ApiOperation(value = "Creates and submits a streaming job using a JSON StreamingJobCreationRequest object as the request body.",
            notes = "The pipelineName should be one of the values in 'rest/pipelines'. The stream should contain a valid streamUri, " +
                    "for example: rtsp://example.com/media.mp4.  Optional segmentSize, mediaProperties, stallAlertDetectionThreshold, "+
                    "stallAlertRate, stallTimeout and stallCallbackUri may also be defined for the stream. " +
                    "Additional streaming job options include summaryReportCallbackUri, healthReportCallbackUri, newTrackAlertCallbackUri and " +
                    "callbackMethod (GET or POST). " +
                    "Other streaming job options include jobProperties object which contains String key-value pairs which override the pipeline's job properties for this streaming job. " +
                    "An optional algorithmProperties object containing <String,Map> key-value pairs can override jobProperties for a specific algorithm defined in the pipeline.  "+
                    "For algorithmProperties, the key should be the algorithm name, and the value should be a Map of String key-value pairs representing properties specific to the named algorithm. "+
                    "Note that the batch jobs and streaming jobs share a range of valid job ids.  OpenMPF guarantees that the ids of a streaming job and a batch job will be unique",
            produces = "application/json", response = StreamingJobCreationResponse.class)
    @ApiResponses(value = {
            @ApiResponse(code = 201, message = "Streaming Job created"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 401, message = "Bad credentials")})
    @ResponseBody
    public ResponseEntity <StreamingJobCreationResponse> processStreamRest(@ApiParam(required = true, value = "StreamingJobCreationRequest") @RequestBody StreamingJobCreationRequest streamingJobCreationRequest) {
        StreamingJobCreationResponse create_job_response = processStreamingJobCreationRequest(streamingJobCreationRequest);
        if (streamingJobCreationRequest.isValidRequest() ) {
            return new ResponseEntity<>(create_job_response,HttpStatus.CREATED);
        } else {
            return new ResponseEntity<>(create_job_response,HttpStatus.BAD_REQUEST);
        }
    }

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
    public ResponseEntity<StreamingJobInfoResponse> getJobStatusRest(@ApiParam(required = true, value = "Streaming Job Id") @PathVariable("id") long jobIdPathVar) {
        StreamingJobInfoResponse streamingJobInfoResponseModel = null;
        List<StreamingJobInfoResponse> streamingJobInfoResponseModels = getStreamingJobInfo(jobIdPathVar);
        if ( streamingJobInfoResponseModels != null && streamingJobInfoResponseModels.size() == 1 ) {
            streamingJobInfoResponseModel = streamingJobInfoResponseModels.get(0);
            //return 200 for successful GET and object
            return new ResponseEntity<>(streamingJobInfoResponseModel, HttpStatus.OK);
        } else {
            // return 404 for bad id (not found)
            log.error("Error retrieving the StreamingJobInfoResponse model for the streaming job with id '{}'", jobIdPathVar);
            return new ResponseEntity<>(new StreamingJobInfoResponse(-1, "streaming job "+jobIdPathVar+" doesn't exist"), HttpStatus.NOT_FOUND);
        }
    }

//    /*
//     * /rest/streaming/jobs/{id}/output/detection
//     */
//    //EXTERNAL
//    @RequestMapping(value = "/rest/streaming/jobs/{id}/output/detection", method = RequestMethod.GET)
//    @ApiOperation(value = "Gets the JSON detection output object of a specific streaming job using the job id as a required path variable.",
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
    @RequestMapping(value = "/rest/streaming/jobs/{id}/cancel", method = RequestMethod.POST, params = {"doCleanup"} )
    @ApiOperation(value = "Cancels the streaming job with the supplied job id. If doCleanup is true, then the HTTP Response to this request may be delayed while OpenMPF processes the cleanup",
            produces = "application/json", response = StreamingJobCancelResponse.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful streaming job cancellation attempt"),
            @ApiResponse(code = 400, message = "Invalid id"),
            @ApiResponse(code = 401, message = "Bad credentials")})
    @ResponseBody
    @ResponseStatus(value = HttpStatus.OK) //return 200 for post in this case
    public ResponseEntity<StreamingJobCancelResponse> cancelStreamingJobRest(@ApiParam(required = true, value = "Streaming Job id") @PathVariable("id") long jobId,
                                                                             @ApiParam(name = "doCleanup", value = "doCleanup", required = false,
                                                                                     defaultValue = "false") @RequestParam("doCleanup")
                                                                                     boolean doCleanup) {
        StreamingJobCancelResponse cancelResponse = cancel(jobId,doCleanup);

        //return 200 for successful GET and object, 401 for bad credentials, 400 for bad id
        return new ResponseEntity<>( cancelResponse, ( cancelResponse.getMpfResponse().getResponseCode() == 0 ) ? HttpStatus.OK : HttpStatus.BAD_REQUEST );
    }

    //INTERNAL
    @RequestMapping(value = "/streaming/jobs/{id}/cancel", method = RequestMethod.POST, params = {"doCleanup"} )
    @ResponseBody
    @ResponseStatus(value = HttpStatus.OK) //return 200 for post in this case
    public StreamingJobCancelResponse cancelStreamingJobSession(@PathVariable("id") long jobId,
                                                                @ApiParam(name = "doCleanup", value = "doCleanup", required = false,
                                                                    defaultValue = "false") @RequestParam("doCleanup") boolean doCleanup) {
        return cancel(jobId, doCleanup);
    }

    /*
     * Private methods
     */
    private StreamingJobCreationResponse processStreamingJobCreationRequest(StreamingJobCreationRequest streamingJobCreationRequest) {

        try {
            if ( streamingJobCreationRequest.isValidRequest() ) {
                boolean fromExternalRestClient = true;
                //hack of using 'from_mpf_web_app' as the externalId to prevent duplicating a method and keeping streaming jobs
                //from the web app in the session jobs collections
                if ( streamingJobCreationRequest.getExternalId() != null && streamingJobCreationRequest.getExternalId().equals("from_mpf_web_app") ) {
                    fromExternalRestClient = false;
                    streamingJobCreationRequest.setExternalId(null);
                }
                boolean enableOutputToDisk = propertiesUtil.isOutputObjectsEnabled();
                if ( streamingJobCreationRequest.getEnableOutputToDisk() != null ) {
                  enableOutputToDisk = streamingJobCreationRequest.getEnableOutputToDisk();
                }

                int priority = propertiesUtil.getJmsPriority();
                if ( streamingJobCreationRequest.getPriority() != null ) {
                    priority = streamingJobCreationRequest.getPriority();
                }
              
                JsonStreamingInputObject json_stream = new JsonStreamingInputObject(streamingJobCreationRequest.getStreamUri(),
                        streamingJobCreationRequest.getSegmentSize(), streamingJobCreationRequest.getMediaProperties());

                JsonStreamingJobRequest jsonStreamingJobRequest = mpfService.createStreamingJob(json_stream,
                        streamingJobCreationRequest.getAlgorithmProperties(),
                        streamingJobCreationRequest.getJobProperties(),
                        streamingJobCreationRequest.getPipelineName(),
                        streamingJobCreationRequest.getExternalId(), //TODO: what do we do with this from the UI?
                        enableOutputToDisk, // Use the buildOutput value if it is provided with the streaming job, otherwise use the default value from the properties file.,
                        priority,// Use the priority value if it is provided, otherwise use the default value from the properties file.
                        streamingJobCreationRequest.getStallAlertDetectionThreshold(),
                        streamingJobCreationRequest.getStallAlertRate(),
                        streamingJobCreationRequest.getStallTimeout(),
                        streamingJobCreationRequest.getHealthReportCallbackUri(),
                        streamingJobCreationRequest.getSummaryReportCallbackUri(),
                        streamingJobCreationRequest.getNewTrackAlertCallbackUri(),
                        streamingJobCreationRequest.getCallbackMethod());

                // submit the streaming job to MPF services.  Note that the jobId of the streaming job is
                // created when the job is submitted to the MPF service because it is here when the streaming job
                // is persisted in the long term database, and the streaming jobs output object file system
                // will be created using the assigned jobId, if the creation of output objects is enabled .
                long jobId = mpfService.submitJob(jsonStreamingJobRequest);
                log.debug("Successful creation of streaming JobId: {}", jobId);

                // get the streamingJobRequest so we can pass along the output object directory in the
                // streaming job creation response.
                StreamingJobRequest streamingJobRequest = mpfService.getStreamingJobRequest(jobId);
                return new StreamingJobCreationResponse( jobId, jsonStreamingJobRequest.getExternalId(), streamingJobRequest.getOutputObjectDirectory() );

//                // create the output file system for the streaming job, then return the appropriate streaming job creation response
//                if ( mpfService.initializeOutputDirectoryForStreamingJob(jobId) ) {
//                    // get the updated streamingJobRequest so we can pass along the output object directory in the
//                    // streaming job creation response.
//                    StreamingJobRequest streamingJobRequest = mpfService.getStreamingJobRequest(jobId);
//                    return new StreamingJobCreationResponse( jobId, jsonStreamingJobRequest.getExternalId(), streamingJobRequest.getOutputObjectDirectory() );
//                } else {
//                    String errorMessage = "Failure creating output file system for streaming jobId " + jobId + ". Please check server logs for more detail";
//                    log.error(errorMessage);
//                    return new StreamingJobCreationResponse(-1, errorMessage);
//                }
            } else {
                log.error("Failure creating streaming job due to a malformed request, check the request parameters against the constraints defined in the REST API");
                return new StreamingJobCreationResponse(-1, String.format("Failure creating streaming job with External Id '%s'.  Request was not valid, confirm the job parameter constraints and resend the request.", streamingJobCreationRequest.getExternalId()));
            }
        } catch (Exception ex) { //exception handling - can't throw exception - currently an html page will be returned
            log.error("Failure creating streaming job due to an exception.", ex);
            return new StreamingJobCreationResponse(-1, String.format("Failure creating streaming job with External Id '%s' due to an exception. Please check server logs for more detail.", streamingJobCreationRequest.getExternalId()));
        }
    }

    /** Get information about all streaming jobs
     * Note: this method requires that batch and streaming jobIds be unique, with no conflicts
     * @param jobId
     * @return
     */
    private List<StreamingJobInfoResponse> getStreamingJobInfo(Long jobId) {
        List<StreamingJobInfoResponse> jobInfoList = new ArrayList<StreamingJobInfoResponse>();
        try {
            List<StreamingJobRequest> jobs = new ArrayList<StreamingJobRequest>();
            if (jobId != null) {
                StreamingJobRequest job = mpfService.getStreamingJobRequest(jobId);
                if (job != null) {
                  jobs.add(job);
                }
            } else {
              //get all of the streaming jobs
              jobs = mpfService.getAllStreamingJobRequests();
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

    private StreamingJobCancelResponse cancel(long jobId, boolean doCleanup) {
        log.debug("Attempting to cancel streaming job with id: {}.", jobId);
        StreamingJobRequest streamingJobRequest = mpfService.getStreamingJobRequest(jobId);
        if ( mpfService.cancelStreamingJob(jobId, doCleanup ) ) {
            log.debug("Successful cancellation of streaming job with id: {}",jobId);
            return new StreamingJobCancelResponse(jobId,streamingJobRequest.getExternalId(),
                    streamingJobRequest.getOutputObjectDirectory(), doCleanup);
        }
        String errorStr = "Failed to cancel the streaming job with id '" + Long.toString(jobId) + "'. Please check to make sure the streaming job exists before submitting a cancel request. "
                + "Also consider checking the server logs for more information on this error.";
        log.error(errorStr);
        return new StreamingJobCancelResponse( doCleanup, 1, errorStr);
    }


}
