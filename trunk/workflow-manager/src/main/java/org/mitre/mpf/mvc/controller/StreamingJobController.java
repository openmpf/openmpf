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
import org.mitre.mpf.interop.JsonStreamingInputObject;
import org.mitre.mpf.interop.JsonStreamingJobRequest;
import org.mitre.mpf.mvc.util.ModelUtils;
import org.mitre.mpf.rest.api.*;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobRequest;
import org.mitre.mpf.wfm.data.entities.transients.TransientStream;
import org.mitre.mpf.wfm.event.JobProgress;
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

import java.util.ArrayList;
import java.util.List;

// swagger includes

@Api(value = "streaming-jobs",
        description = "Streaming job create, status, cancel")
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

    @Autowired
    private PipelineService pipelineService;

    /*
     *	POST /streaming/jobs
     */
    //EXTERNAL
    @RequestMapping(value = {"/rest/streaming/jobs"}, method = RequestMethod.POST)
    @ApiOperation(value = "Creates and submits a streaming job using a JSON StreamingJobCreationRequest object as the request body.",
            notes = " The pipelineName should be one of the values in 'rest/pipelines'. The stream should contain a valid streamUri," +
                    " for example: rtsp://example.com/media.mp4.  Optional segmentSize, mediaProperties,"+
                    " stallTimeout and stallCallbackUri may also be defined for the stream." +
                    " Additional streaming job options include summaryReportCallbackUri and healthReportCallbackUri. Health and summary reports" +
                    " will always be sent to the callback URIs using the HTTP POST method." +
                    " Other streaming job options include jobProperties object which contains String key-value pairs which override the pipeline's job properties for this streaming job." +
                    " An optional algorithmProperties object containing <String,Map> key-value pairs can override jobProperties for a specific algorithm defined in the pipeline." +
                    " For algorithmProperties, the key should be the algorithm name, and the value should be a Map of String key-value pairs representing properties specific to the named algorithm." +
                    " Note that the batch jobs and streaming jobs share a range of valid job ids.  OpenMPF guarantees that the ids of a streaming job and a batch job will be unique." +
                    " Also, note that all provided URIs must be properly encoded.",
            produces = "application/json", response = StreamingJobCreationResponse.class)
    @ApiResponses(value = {
            @ApiResponse(code = 201, message = "Streaming Job created"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 401, message = "Bad credentials")})
    @ResponseBody
    public ResponseEntity<StreamingJobCreationResponse> createStreamingJobRest(@ApiParam(required = true, value = "StreamingJobCreationRequest") @RequestBody StreamingJobCreationRequest streamingJobCreationRequest) {
        StreamingJobCreationResponse createResponse = createStreamingJobInternal(streamingJobCreationRequest);
        if (createResponse.getMpfResponse().getResponseCode() == MpfResponse.RESPONSE_CODE_SUCCESS) {
            return new ResponseEntity<>(createResponse, HttpStatus.CREATED);
        } else {
            log.error("Error creating streaming job");
            return new ResponseEntity<>(createResponse, HttpStatus.BAD_REQUEST);
        }
    }

    /*
     * GET /streaming/jobs/{id}
     */
    //EXTERNAL
    @RequestMapping(value = "/rest/streaming/jobs/{id}", method = RequestMethod.GET)
    @ApiOperation(value = "Gets a StreamingJobInfo model for the streaming job with the id provided as a path variable.",
            produces = "application/json", response = StreamingJobInfo.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful response"),
            @ApiResponse(code = 400, message = "Invalid id"),
            @ApiResponse(code = 401, message = "Bad credentials")})
    @ResponseBody
    public ResponseEntity<StreamingJobInfo> getStreamingJobStatusRest(@ApiParam(required = true, value = "Streaming Job Id") @PathVariable("id") long jobId) {
        List<StreamingJobInfo> streamingJobInfoModels = getStreamingJobStatusInternal(jobId);
        if (streamingJobInfoModels != null && streamingJobInfoModels.size() == 1) {
            return new ResponseEntity<>(streamingJobInfoModels.get(0), HttpStatus.OK);
        } else {
            log.error("Error retrieving the StreamingJobInfo model for the streaming job with id '{}'", jobId);
            return new ResponseEntity<>((StreamingJobInfo) null, HttpStatus.BAD_REQUEST);
        }
    }

    /*
     * GET /streaming/jobs
     */
    //EXTERNAL
    @RequestMapping(value = "/rest/streaming/jobs", method = RequestMethod.GET)
    @ApiOperation(value = "Gets a list of job ids for streaming jobs. If isActive is true, don't include streaming jobs that are terminated or cancelled. If false, return all streaming jobs.",
        produces = "application/json", response=Long.class, responseContainer="List")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successful response"),
        @ApiResponse(code = 401, message = "Bad credentials")})
    @ResponseBody
    public List<Long> getStreamingJobsInfoRest(@ApiParam(name = "isActive", value = "isActive", required = false, defaultValue = "true")
                                               @RequestParam(value = "isActive", required = false) boolean isActive ) {
        //get a list of all of the streaming job ids
        return mpfService.getAllStreamingJobIds(isActive);
    }

// TODO:
// /rest/streaming/jobs/{id}/output/detection
// /streaming/jobs/output-object
// /rest/streaming/jobs/{id}/resubmit
// /streaming/jobs/{id}/resubmit

    /*
     * POST /streaming/jobs/{id}/cancel
     */
    //EXTERNAL
    @RequestMapping(value = "/rest/streaming/jobs/{id}/cancel", method = RequestMethod.POST)
    @ApiOperation(value = "Cancels the streaming job with the supplied job id. If doCleanup is true, then the HTTP Response to this request may be delayed while OpenMPF processes the cleanup.",
            produces = "application/json", response = StreamingJobCancelResponse.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful streaming job cancellation attempt"),
            @ApiResponse(code = 400, message = "Invalid id"),
            @ApiResponse(code = 401, message = "Bad credentials")})
    @ResponseBody
    @ResponseStatus(value = HttpStatus.OK) //return 200 for post in this case
    public ResponseEntity<StreamingJobCancelResponse> cancelStreamingJobRest(@ApiParam(required = true, value = "Streaming Job id") @PathVariable("id") long jobId,
                                                                             @ApiParam(name = "doCleanup", value = "doCleanup", required = false, defaultValue = "false")
                                                                             @RequestParam(value = "doCleanup", required = false) boolean doCleanup) {
        StreamingJobCancelResponse cancelResponse = cancelStreamingJobInternal(jobId, doCleanup);
        if (cancelResponse.getMpfResponse().getResponseCode() == MpfResponse.RESPONSE_CODE_SUCCESS) {
            return new ResponseEntity<>(cancelResponse, HttpStatus.OK);
        } else {
            log.error("Error cancelling streaming job with id '{}'", jobId);
            return new ResponseEntity<>(cancelResponse, HttpStatus.BAD_REQUEST);
        }
    }

    //INTERNAL
    @RequestMapping(value = "/streaming/jobs/{id}/cancel", method = RequestMethod.POST, params = {"doCleanup"} )
    @ResponseBody
    @ResponseStatus(value = HttpStatus.OK) //return 200 for post in this case
    public StreamingJobCancelResponse cancelStreamingJob(@PathVariable("id") long jobId,
                                                         @RequestParam(value = "doCleanup", required = false, defaultValue="false") boolean doCleanup) {
        return cancelStreamingJobInternal(jobId, doCleanup);
    }

    private StreamingJobCreationResponse createStreamingJobCreationErrorResponse(String streamingJobExternalId,
                                                                                 String errorReason) {
        StringBuilder errBuilder = new StringBuilder("Failure creating streaming job");
        if ( streamingJobExternalId != null ) {
            errBuilder.append(String.format(" with external id '%s'", streamingJobExternalId));
        }
        errBuilder.append(" due to " + errorReason + ". Please check the request parameters against the constraints defined in the REST API.");
        String err = errBuilder.toString();
        log.error(err);
        return new StreamingJobCreationResponse(MpfResponse.RESPONSE_CODE_ERROR, err);
    }

    private StreamingJobCreationResponse createStreamingJobInternal(StreamingJobCreationRequest streamingJobCreationRequest) {

        try {
            if ( !streamingJobCreationRequest.isValidRequest() ) {
                // The streaming job failed the API syntax check, the job request is malformed. Reject the job and send an error response.
                return createStreamingJobCreationErrorResponse(streamingJobCreationRequest.getExternalId(), "malformed request");
            } else if ( !TransientStream.isSupportedUriScheme(streamingJobCreationRequest.getStream().getStreamUri()) ) {
                // The streaming job failed the check for supported stream protocol check, so OpenMPF can't process the requested stream URI.
                // Reject the job and send an error response.
                return createStreamingJobCreationErrorResponse(streamingJobCreationRequest.getExternalId(),
                    "malformed or unsupported stream URI: " + streamingJobCreationRequest.getStream().getStreamUri());
            } else if ( !pipelineService.pipelineSupportsStreaming(streamingJobCreationRequest.getPipelineName()) ) {
                // The streaming job failed the pipeline check. The requested pipeline doesn't support streaming.
                // Reject the job and send an error response.
                return createStreamingJobCreationErrorResponse(streamingJobCreationRequest.getExternalId(), "Requested pipeline doesn't support streaming jobs");
            } else {
                boolean enableOutputToDisk = propertiesUtil.isOutputObjectsEnabled();
                if ( streamingJobCreationRequest.getEnableOutputToDisk() != null ) {
                  enableOutputToDisk = streamingJobCreationRequest.getEnableOutputToDisk();
                }

                int priority = propertiesUtil.getJmsPriority();
                if ( streamingJobCreationRequest.getPriority() != null ) {
                    priority = streamingJobCreationRequest.getPriority();
                }

                JsonStreamingInputObject json_stream = new JsonStreamingInputObject(
                        streamingJobCreationRequest.getStream().getStreamUri(),
                        streamingJobCreationRequest.getSegmentSize(),
                        streamingJobCreationRequest.getMediaProperties());

                JsonStreamingJobRequest jsonStreamingJobRequest = mpfService.createStreamingJob(json_stream,
                        streamingJobCreationRequest.getAlgorithmProperties(),
                        streamingJobCreationRequest.getJobProperties(),
                        streamingJobCreationRequest.getPipelineName(),
                        streamingJobCreationRequest.getExternalId(), //TODO: what do we do with this from the UI?
                        enableOutputToDisk, // Use the buildOutput value if it is provided with the streaming job, otherwise use the default value from the properties file.,
                        priority,// Use the priority value if it is provided, otherwise use the default value from the properties file.
                        streamingJobCreationRequest.getStallTimeout(),
                        streamingJobCreationRequest.getHealthReportCallbackUri(),
                        streamingJobCreationRequest.getSummaryReportCallbackUri());

                // submit the streaming job to MPF services.  Note that the jobId of the streaming job is
                // created when the job is submitted to the MPF service because that is when the streaming job
                // is persisted in the long term database, and the streaming jobs output object file system
                // will be created using the assigned jobId, if the creation of output objects is enabled .
                long jobId = mpfService.submitJob(jsonStreamingJobRequest);
                log.debug("Successful creation of streaming JobId {}", jobId);

                // get the streamingJobRequest so we can pass along the output object directory in the
                // streaming job creation response.
                StreamingJobRequest streamingJobRequest = mpfService.getStreamingJobRequest(jobId);
                return new StreamingJobCreationResponse( jobId, streamingJobRequest.getOutputObjectDirectory() );
            }
        } catch (Exception ex) { //exception handling - can't throw exception - currently an html page will be returned
            StringBuilder errBuilder = new StringBuilder("Failure creating streaming job");
            if (streamingJobCreationRequest.getExternalId() != null) {
                errBuilder.append(String.format(" with external id '%s'", streamingJobCreationRequest.getExternalId()));
            }
            errBuilder.append(" due to an exception. Please check server logs for more detail.");
            String err = errBuilder.toString();

            log.error(err, ex);
            return new StreamingJobCreationResponse(MpfResponse.RESPONSE_CODE_ERROR, err);
        }
    }

    /**
     * Get information about one or all streaming jobs.
     * Note: this method requires that batch and streaming jobIds be unique, with no conflicts
     * @param jobId
     * @return
     */
    private List<StreamingJobInfo> getStreamingJobStatusInternal(Long jobId) {
        List<StreamingJobInfo> jobInfoList = new ArrayList<StreamingJobInfo>();
        try {
            List<StreamingJobRequest> jobRequests = new ArrayList<StreamingJobRequest>();
            if (jobId != null) {
                StreamingJobRequest jobRequest = mpfService.getStreamingJobRequest(jobId);
                if (jobRequest != null) {
                    jobRequests.add(jobRequest);
                }
            } else {
                // Get all of the streaming jobs from the long-term database.
                jobRequests = mpfService.getAllStreamingJobRequests();
            }

            for (StreamingJobRequest jobRequest : jobRequests) {
                long id = jobRequest.getId();
                StreamingJobInfo streamingJobInfo;

                float jobProgressVal = jobProgress.getJobProgress(id) != null ? jobProgress.getJobProgress(id) : 0.0f;
                streamingJobInfo = ModelUtils.convertJobRequest(jobRequest, jobProgressVal);

                jobInfoList.add(streamingJobInfo);
            }
        } catch (Exception ex) {
            log.error("Exception in get streaming job status with stack trace: {}", ex.getMessage());
        }

        return jobInfoList;
    }

    private StreamingJobCancelResponse cancelStreamingJobInternal(long jobId, boolean doCleanup) {
        StreamingJobCancelResponse cancelResponse = null;
        log.debug("Attempting to cancel streaming job with id {}, doCleanup {}.", jobId, doCleanup);

        StreamingJobRequest streamingJobRequest = mpfService.getStreamingJobRequest(jobId);
        if ( streamingJobRequest == null ) {
            // if the requested streaming job doesn't exist, it can't be marked for cancellation, so this is an error.
            cancelResponse = new StreamingJobCancelResponse(jobId, null, doCleanup,
                MpfResponse.RESPONSE_CODE_ERROR, "Streaming job with id " + jobId + " doesn't exist.");
        } else {
            try {
                mpfService.cancelStreamingJob(jobId, doCleanup);

                log.info("Successfully marked for cancellation streaming job with id {}", jobId);

                cancelResponse = new StreamingJobCancelResponse(jobId,
                    streamingJobRequest.getOutputObjectDirectory(), doCleanup,
                    MpfResponse.RESPONSE_CODE_SUCCESS, null);
            } catch (WfmProcessingException e) {
                log.error(String.format("Streaming job with id %s couldn't be cancelled.", jobId), e);
                cancelResponse = new StreamingJobCancelResponse(jobId,
                    streamingJobRequest.getOutputObjectDirectory(), doCleanup,
                    MpfResponse.RESPONSE_CODE_ERROR, e.getMessage());
            }
        }
        return cancelResponse;
    }
}
