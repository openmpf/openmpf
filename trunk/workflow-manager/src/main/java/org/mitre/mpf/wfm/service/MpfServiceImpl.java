/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

import org.mitre.mpf.interop.JsonJobRequest;
import org.mitre.mpf.interop.JsonMediaInputObject;
import org.mitre.mpf.interop.JsonStreamingInputObject;
import org.mitre.mpf.interop.JsonStreamingJobRequest;
import org.mitre.mpf.mvc.controller.AtmosphereController;
import org.mitre.mpf.mvc.model.AtmosphereChannel;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.businessrules.JobRequestBo;
import org.mitre.mpf.wfm.businessrules.StreamingJobRequestBo;
import org.mitre.mpf.wfm.businessrules.impl.JobRequestBoImpl;
import org.mitre.mpf.wfm.businessrules.impl.StreamingJobRequestBoImpl;
import org.mitre.mpf.wfm.data.access.MarkupResultDao;
import org.mitre.mpf.wfm.data.access.SystemMessageDao;
import org.mitre.mpf.wfm.data.access.hibernate.*;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.data.entities.persistent.MarkupResult;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobRequest;
import org.mitre.mpf.wfm.data.entities.persistent.SystemMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MpfServiceImpl implements MpfService {
    private static final Logger log = LoggerFactory.getLogger(MpfServiceImpl.class);

    @Autowired
    @Qualifier(HibernateMarkupResultDaoImpl.REF)
    private MarkupResultDao markupResultDao;

    @Autowired
    @Qualifier(HibernateJobRequestDaoImpl.REF)
    private HibernateJobRequestDao jobRequestDao;

    @Autowired
    @Qualifier(HibernateStreamingJobRequestDaoImpl.REF)
    private HibernateDao<StreamingJobRequest> streamingJobRequestDao;

    @Autowired
    @Qualifier(JobRequestBoImpl.REF)
    private JobRequestBo jobRequestBo;

    @Autowired
    @Qualifier(StreamingJobRequestBoImpl.REF)
    private StreamingJobRequestBo streamingJobRequestBo;

    @Autowired
    @Qualifier(HibernateSystemMessageDaoImpl.REF)
    private SystemMessageDao systemMessageDao;

    /** Create a batch job using the raw parameters for the job
     * This version of the method does not allow for callbacks to be defined for this job
     * @param media
     * @param algorithmProperties
     * @param jobProperties
     * @param pipelineName
     * @param externalId
     * @param buildOutput
     * @param priority
     * @return JSON representation of the batch job request.
     */
    @Override
    public JsonJobRequest createJob(List<JsonMediaInputObject> media, Map<String, Map<String, String>> algorithmProperties, Map<String,String> jobProperties, String pipelineName, String externalId, boolean buildOutput, int priority) {
        log.debug("createJob: MediaUris: {}, Pipeline: {}, Build Output: {}, Priority: {}", (media == null) ? null : media.size(), pipelineName, buildOutput, priority);
        return jobRequestBo.createRequest(externalId, pipelineName, media, algorithmProperties, jobProperties, buildOutput, priority);
    }

    /** Create a batch job using the raw parameters for the job
     * This version of the method allows for a callback to be defined for this job
     * @param media
     * @param algorithmProperties
     * @param jobProperties
     * @param pipelineName
     * @param externalId
     * @param buildOutput
     * @param priority
     * @param callbackUrl
     * @param method
     * @return JSON representation of the batch job request.
     */
    @Override
    public JsonJobRequest createJob(List<JsonMediaInputObject> media, Map<String, Map<String, String>> algorithmProperties, Map<String,String> jobProperties, String pipelineName, String externalId, boolean buildOutput, int priority, String callbackUrl, String method) {
        log.debug("createJob: MediaUris: {}, Pipeline: {}, Build Output: {}, Priority: {}, Callback: {}, Method: {}", (media == null) ? null : media.size(), pipelineName, buildOutput, priority, callbackUrl, method);
        return jobRequestBo.createRequest(externalId, pipelineName, media, algorithmProperties, jobProperties, buildOutput, priority, callbackUrl, method);
    }

    /**
     * Asynchronously submits a JSON-based batch job request and returns the identifier associated with the persistent job request which was created.
     * Note: this method will initialize the jobId for this newly created batch job
     * @param jobRequest The batch job to execute.
     * @return The jobId of the batch job which was created.
     */
    @Override
    public long submitJob(JsonJobRequest jobRequest) {
        return jobRequestBo.run(jobRequest).getId();
    }

    /**
     * Asynchronously submits a JSON-based streaming job request and returns the identifier associated with the persistent job request which was created.
     * Note: this method will initialize the jobId for this newly created streaming job.  Upon return, this method will also
     * create the output file system for this streaming job, if creation of output objects is enabled.
     * @param streamingJobRequest The streaming job to execute.
     * @return The jobId of the streaming job which was created.
     */
    @Override
    public long submitJob(JsonStreamingJobRequest streamingJobRequest) {
        return streamingJobRequestBo.run(streamingJobRequest).getId();
    }

    /**
     * Resubmit a batch job. Note that resubmission of streaming jobs is not supported.
     * @param jobId jobId of a batch job
     * @return
     */
    @Override
    public long resubmitJob(long jobId) {
        return jobRequestBo.resubmit(jobId).getId();
    }

    /**
     * Resubmit a batch job with revised priority. Note that resubmission of streaming jobs is not supported
     * @param jobId The MPF-assigned identifier for the original batch job.
     * @param newPriority The new priority to assign to this job. Note: Future resubmissions will use this priority value.
     * @return
     */
    @Override
    public long resubmitJob(long jobId, int newPriority) {
        return jobRequestBo.resubmit(jobId, newPriority).getId();
    }

    /** Create a new streaming job which will execute the specified pipeline on the stream.
     * @param json_stream JSON representation of the stream data
     * @param algorithmProperties A map of properties which will override the job properties on this job for a particular algorithm.
     * @param jobProperties A map of properties which will override the default and pipeline properties on this job.
     * @param pipelineName The name of the pipeline to execute.
     * @param externalId A user-defined and optional external identifier for the job.
     * @param buildOutput {@literal true} to build output objects, {@literal false} to suppress output objects.
     * @param priority The priority to assign to this job.
     * @param stallTimeout
     * @param healthReportCallbackURI The health report callback URI or null to disable health reports
     * @param summaryReportCallbackURI The summary callback URI or null to disable summary reports
     * @return A {@link org.mitre.mpf.interop.JsonStreamingJobRequest} which summarizes this request
     */
    @Override
    public JsonStreamingJobRequest createStreamingJob(JsonStreamingInputObject json_stream,
                                                      Map<String,Map<String,String>> algorithmProperties,
                                                      Map<String,String> jobProperties, String pipelineName, String externalId,
                                                      boolean buildOutput, int priority,
                                                      long stallTimeout,
                                                      String healthReportCallbackURI,
                                                      String summaryReportCallbackURI) {

        log.debug("createStreamingJob: stream: {}, Pipeline: {}, Build Output: {}, Priority: {}, healthReportCallbackUri: {}, summaryReportCallbackUri: {}", json_stream,
                  pipelineName, buildOutput, priority, healthReportCallbackURI, summaryReportCallbackURI);
        return streamingJobRequestBo.createRequest(externalId, pipelineName, json_stream, algorithmProperties, jobProperties, buildOutput, priority,
                                                   stallTimeout, healthReportCallbackURI, summaryReportCallbackURI);
    }

    /**
     * Cancel a batch job
     * @param jobId The OpenMPF-assigned identifier for the batch job. The job must be a batch job.
     * @return
     */
    @Override
    public boolean cancel(long jobId) {
        try {
            return jobRequestBo.cancel(jobId);
        } catch ( WfmProcessingException wpe ) {
            log.error("Failed to cancel Batch Job #{} due to an exception.", jobId, wpe);
            return false;
        }
    }

    /**
     * Marks a streaming job as CANCELLING in both the TransientStreamingJob and in the long-term database.
     * @param jobId     The OpenMPF-assigned identifier for the streaming job. The job must be a streaming job.
     * @param doCleanup if true, delete the streaming job files from disk as part of cancelling the streaming job.
     * @exception WfmProcessingException may be thrown if a warning or error occurs.
     */
    @Override
    public void cancelStreamingJob(long jobId, boolean doCleanup) throws WfmProcessingException {
        streamingJobRequestBo.cancel(jobId, doCleanup);
    }

    @Override
    public MarkupResult getMarkupResult(long id) {
        return markupResultDao.findById(id);
    }

    @Override
    public List<MarkupResult> getMarkupResultsForJob(long jobId) {
        return markupResultDao.findByJobId(jobId);
    }

    @Override
    public List<MarkupResult> getAllMarkupResults() {
        return markupResultDao.findAll();
    }

    @Override
    public JobRequest getJobRequest(long jobId) {
        return jobRequestDao.findById(jobId);
    }

    @Override
    public StreamingJobRequest getStreamingJobRequest(long jobId) {
        return streamingJobRequestDao.findById(jobId);
    }

    /**
     * Get the list of all batch job requests
     * @return
     */
    @Override
    public List<JobRequest> getAllJobRequests() {
        return jobRequestDao.findAll();
    }

    /**
     * Get the list of batch job requests by page
     * @return
     */
    @Override
    public List<JobRequest> getPagedJobRequests(int pageSize, int offset, String searchTerm, String sortColumn,
                                                String sortOrderDirection) {
        return jobRequestDao.findByPage(pageSize, offset, searchTerm, sortColumn, sortOrderDirection);
    }

    /**
     * Get the count of batch job requests
     * @return
     */
    @Override
    public Long getJobRequestCount() {
        return jobRequestDao.countAll();
    }

    /**
     * Get the count of batch job requests after filter was applied
     * @return
     */
    @Override
    public Long getJobRequestCountFiltered(String searchTerm) {
        return jobRequestDao.countFiltered(searchTerm);
    }

    /**
     * Get the list of all streaming job requests from the long term database.
     * @return List of all streaming job requests from the long term database.
     */
    @Override
    public List<StreamingJobRequest> getAllStreamingJobRequests() {
        return streamingJobRequestDao.findAll();
    }

    /**
     * Get the list of all streaming job ids from the long term database.
     * @return list of all streaming job ids from the long term database.
     */
    @Override
    public List<Long> getAllStreamingJobIds() {
        // use a Java8 stream to map the streaming job ids and collect them into a list,
        return getAllStreamingJobRequests().stream().map(StreamingJobRequest::getId).collect(Collectors.toList());
    }

    /**
     * Get the list of all streaming job ids from the long term database.
     * @param isActive If true, then streaming jobs which have terminal JobStatus will be
     * filtered out. Otherwise, all streaming jobs will be returned.
     * @return list of all streaming job ids from the long term database.
     */
    @Override
    public List<Long> getAllStreamingJobIds(boolean isActive) {
        // use a Java8 stream to map the streaming job ids (filtered by terminal status if isActive is enabled) and collect them into a list,
        return getAllStreamingJobRequests().stream().filter( request -> {
            if (isActive) {
                return !request.getStatus().isTerminal(); // include only jobs which have non-terminal status
            } else {
                return true; // include all jobs
            }
        }).map(StreamingJobRequest::getId).collect(Collectors.toList());
    }

    /**
     * Send health report for all streaming jobs to the health report callback associated with each streaming job.
     * This method will just return if there are no streaming jobs.
     * @param isActive If true, then streaming jobs which have terminal JobStatus will be
     * filtered out. Otherwise, all current streaming jobs will be processed.
     * @throws WfmProcessingException thrown if an error occurs
     */
    @Override
    public void sendStreamingJobHealthReports() throws WfmProcessingException {
        streamingJobRequestBo.sendHealthReports();
    }

    /* ***** System Messages ***** */

    private void broadcastSystemMessageChanged( String operation, SystemMessage obj ) {
        HashMap<String,Object> datamap = new HashMap<String,Object>();
        datamap.put( "operation", operation );
        datamap.put( "msgType", (obj!=null) ? obj.getMsgType() : "unknown" );
        datamap.put( "msgID", (obj!=null) ? obj.getId() : "unknown" );
        AtmosphereController.broadcast( AtmosphereChannel.SSPC_SYSTEMMESSAGE, "OnSystemMessagesChanged", datamap );
    }

    @Override
    public List<SystemMessage> getSystemMessagesByType(String filterByType ) {
        if ( filterByType==null || filterByType.equalsIgnoreCase("all") ) {
            return systemMessageDao.findAll();
        }
        else {
            return systemMessageDao.findByType( filterByType );
        }
    }

    @Override
    public List<SystemMessage> getSystemMessagesByRemoveStrategy(String filterbyRemoveStrategy ) {
        if ( filterbyRemoveStrategy==null || filterbyRemoveStrategy.equalsIgnoreCase("all") ) {
            return this.getSystemMessagesByType( null );
        }
        else {
            return systemMessageDao.findByRemoveStrategy( filterbyRemoveStrategy );
        }
    }

    @Override
    public SystemMessage addSystemMessage( SystemMessage obj ) {
        SystemMessage msg = systemMessageDao.add( obj );
        broadcastSystemMessageChanged( "added", obj );
        return msg;
    }

    @Override
    public SystemMessage addStandardSystemMessage( String msgEnum ) {
        SystemMessage obj = systemMessageDao.addStandard( msgEnum );
        broadcastSystemMessageChanged( "added", obj );
        return obj;
    }

    @Override
    public SystemMessage deleteSystemMessage(long msgId ) {
        SystemMessage obj = systemMessageDao.delete( msgId );
        if ( obj != null ) {
            broadcastSystemMessageChanged("deleted", obj);
        }
        return obj;
    }

    @Override
    public SystemMessage deleteStandardSystemMessage(String msgEnum ) {
        SystemMessage obj = systemMessageDao.delete( msgEnum );
        if ( obj != null ) {
            broadcastSystemMessageChanged("deleted", obj);
        }
        return obj;
    }

}

