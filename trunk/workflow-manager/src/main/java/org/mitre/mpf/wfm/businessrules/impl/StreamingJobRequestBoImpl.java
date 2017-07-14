/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
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

package org.mitre.mpf.wfm.businessrules.impl;

import org.apache.camel.EndpointInject;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.mitre.mpf.interop.JsonStreamingJobRequest;
import org.mitre.mpf.interop.JsonStreamingInputObject;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.businessrules.StreamingJobRequestBo;
import org.mitre.mpf.wfm.camel.routes.StreamingJobCreatorRouteBuilder;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.RedisImpl;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateDao;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateStreamingJobRequestDaoImpl;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobRequest;
import org.mitre.mpf.wfm.enums.JobStatus;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.pipeline.PipelineManager;
import org.mitre.mpf.wfm.util.JmsUtils;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Component
public class StreamingJobRequestBoImpl implements StreamingJobRequestBo {
	/**
	 * Internal enumeration which is used to indicate whether a resubmitted streaming job should use the original
	 * priority value or a new value which has been provided.
	 */
	private static enum PriorityPolicy {
		/** Resubmits the streaming job with a different priority. */
		PROVIDED,

		/** DEFAULT: Resubmits the streaming job using the same priority as its first run. */
		EXISTING;

		/** The default action is to re-use the original priority. */
		public static final PriorityPolicy DEFAULT = EXISTING;
	}

	private static final Logger log = LoggerFactory.getLogger(StreamingJobRequestBoImpl.class);
	public static final String REF = "streamingJobRequestBoImpl";

	@Autowired
	private PipelineManager pipelineManager;

	@Autowired
	private PropertiesUtil propertiesUtil;

	@Autowired
	private JsonUtils jsonUtils;

	@Autowired
	private JmsUtils jmsUtils;

	@Autowired
	@Qualifier(RedisImpl.REF)
	private Redis redis;

	@Autowired
	@Qualifier(HibernateStreamingJobRequestDaoImpl.REF)
	private HibernateDao<StreamingJobRequest> streamingJobRequestDao;

	// streaming jobs do not yet support markup, commenting out for now
//	@Autowired
//	@Qualifier(HibernateMarkupResultDaoImpl.REF)
//	private MarkupResultDao markupResultDao;

	@EndpointInject(uri = StreamingJobCreatorRouteBuilder.ENTRY_POINT)
	private ProducerTemplate streamingJobRequestProducerTemplate;

	/** method to create and initialize a JSON representation of a streaming job request given the raw parameters
	 * This version of the method allows for callbacks to be defined, use null to disable.  This method also
	 * does value checks, and sets additional parameters for the streaming job given the current state of streaming
	 * job request parameters
	 * @param externalId
	 * @param pipelineName
	 * @param stream
	 * @param algorithmProperties
	 * @param jobProperties
	 * @param buildOutput if true, output objects will be stored and this method will assign the output object directory
	 * @param priority
	 * @param stallAlertDetectionThreshold
	 * @param stallAlertRate
	 * @param stallTimeout
	 * @param healthReportCallbackUri callback for health reports, pass null to disable health reports
	 * @param summaryReportCallbackUri	callback for summary reports, pass null to disable summary reports
	 * @param newTrackAlertCallbackUri callback for new track alerts, pass null to disable new track alerts
	 * @param callbackMethod callback method should be GET or POST.  If null, default will be GET
	 * @return JSON representation of the streaming job request
	 */
	@Override
	public JsonStreamingJobRequest createRequest(String externalId, String pipelineName, JsonStreamingInputObject stream,
												 Map<String,Map<String,String>> algorithmProperties,
												 Map<String, String> jobProperties, boolean buildOutput, int priority,
												 long stallAlertDetectionThreshold, long stallAlertRate, long stallTimeout,
												 String healthReportCallbackUri, String summaryReportCallbackUri, String newTrackAlertCallbackUri, String callbackMethod) {
		log.debug("[streaming createRequest] externalId:"+externalId +", pipeline:"+pipelineName + ", buildOutput:"+buildOutput+", priority:"+priority+
				  ", healthReportCallbackUri:"+healthReportCallbackUri + ", summaryReportCallbackUri:"+summaryReportCallbackUri +
				  ", newTrackAlertCallbackUri:"+newTrackAlertCallbackUri +", callbackMethod:"+callbackMethod);
		String jsonHealthReportCallbackUri = "";
		String jsonSummaryReportCallbackUri = "";
		String jsonNewTrackAlertCallbackUri = "";
		String jsonCallbackMethod = "GET";
		if ( healthReportCallbackUri != null && TextUtils.trim(healthReportCallbackUri) != null ) {
			jsonHealthReportCallbackUri = TextUtils.trim(healthReportCallbackUri);
		}
		if ( summaryReportCallbackUri != null && TextUtils.trim(summaryReportCallbackUri) != null ) {
			jsonSummaryReportCallbackUri = TextUtils.trim(summaryReportCallbackUri);
		}
		if ( newTrackAlertCallbackUri != null && TextUtils.trim(newTrackAlertCallbackUri) != null ) {
			jsonNewTrackAlertCallbackUri = TextUtils.trim(newTrackAlertCallbackUri);
		}
		if ( callbackMethod != null && TextUtils.trim(callbackMethod) != null && !TextUtils.trim(callbackMethod).equals("GET") ){ //only GET or POST allowed
			jsonCallbackMethod = "POST";
		}

		String outputObjectPath = ""; // initialize output output object to empty string, the path will be set after the streaming job is submitted
		JsonStreamingJobRequest jsonStreamingJobRequest = new JsonStreamingJobRequest(TextUtils.trim(externalId), buildOutput, outputObjectPath,
				pipelineManager.createJsonPipeline(pipelineName), priority, stream,
				stallAlertDetectionThreshold, stallAlertRate, stallTimeout,
				jsonHealthReportCallbackUri,jsonSummaryReportCallbackUri,jsonNewTrackAlertCallbackUri,jsonCallbackMethod,
        algorithmProperties, jobProperties);

		return jsonStreamingJobRequest;
	}

	/** Create a new StreamingJobRequest using the provided JSON streaming job request and persist it in the database for long-term storage
	 * and will send the streaming job request to the components using the ActiveMQ routes
	 * Upon return, the streaming job will be persisted in the long-term database
	 * @param jsonStreamingJobRequest JSON representation of the job request
	 * @return initialized streaming job request
	 * @throws WfmProcessingException
	 */
	@Override
	public StreamingJobRequest run(JsonStreamingJobRequest jsonStreamingJobRequest) throws WfmProcessingException {
		StreamingJobRequest streamingJobRequestEntity = initialize(jsonStreamingJobRequest);
		return runInternal( streamingJobRequestEntity, jsonStreamingJobRequest, (jsonStreamingJobRequest == null ) ? propertiesUtil.getJmsPriority() : jsonStreamingJobRequest.getPriority() );
	}

	/** Create a new StreamingJobRequest using the provided JSON job request and persist it in the database for long-term storage
	 * Upon return, the job will be persisted in the long-term database
	 * @param jsonStreamingJobRequest JSON representation of the job request
	 * @return initialized job request
	 * @throws WfmProcessingException
	 */
	@Override
	public StreamingJobRequest initialize(JsonStreamingJobRequest jsonStreamingJobRequest) throws WfmProcessingException {
		StreamingJobRequest streamingJobRequestEntity = new StreamingJobRequest();
		return initializeInternal(streamingJobRequestEntity, jsonStreamingJobRequest);
	}

//	@Override
//	public StreamingJobRequest resubmit(long jobId) throws WfmProcessingException {
//		return resubmitInternal(jobId, PriorityPolicy.EXISTING, 0);
//	}
//
//	@Override
//	public StreamingJobRequest resubmit(long jobId, int priority) throws WfmProcessingException {
//		return resubmitInternal(jobId, PriorityPolicy.PROVIDED, priority);
//	}

	/** Cancel a streaming job
	 * This method will mark the job as cancelled in both REDIS and in the long-term MySQL database.  The job cancel request will also be sent
	 * along to the components via ActiveMQ using the StreamingJobCreatorRouteBuilder.ENTRY_POINT
	 * @param jobId
	 * @return true if the streaming job was successfully cancelled, false otherwise
	 * @throws WfmProcessingException
	 */
	@Override
	public synchronized boolean cancel(long jobId) throws WfmProcessingException {
		log.debug("[Job {}:*:*] Received request to cancel this streaming job.", jobId);
		StreamingJobRequest streamingJobRequest = streamingJobRequestDao.findById(jobId);
		if(streamingJobRequest == null) {
			throw new WfmProcessingException(String.format("A streaming job with the id %d is not known to the system.", jobId));
		}

		assert streamingJobRequest.getStatus() != null : "Streaming jobs must not have a null status.";

		if ( streamingJobRequest.getStatus().isTerminal() || streamingJobRequest.getStatus() == JobStatus.CANCELLING ) {
			log.warn("[Job {}:*:*] This streaming job is in the state of '{}' and cannot be cancelled at this time.", jobId, streamingJobRequest.getStatus().name());
			return false;
		} else {
			log.info("[Job {}:*:*] Cancelling streaming job.", jobId);

			// Mark the streaming job as cancelled in Redis so that future steps in the workflow will know not to continue processing.
			if ( redis.cancel(jobId) ) {
				try {
					// Try to move any pending work items on the queues to the appropriate cancellation queues.
					// If this operation fails, any remaining pending items will continue to process, but
					// the future splitters should not create any new work items. In short, if this fails,
					// the system should not be affected, but the streaming job may not complete any faster.
          // TODO tell the master node manager to cancel the streaming job
          log.warn("[Job {}:*:*] Cancellation of streaming job via master node manager not yet implemented.", jobId);
				} catch ( Exception exception ) {
					log.warn("[Job {}:*:*] Failed to remove the pending work elements in the message broker for this streaming job. The job must complete the pending work elements before it will cancel the job.", jobId, exception);
				}
				streamingJobRequest.setStatus(JobStatus.CANCELLING);
				streamingJobRequestDao.persist(streamingJobRequest);
			} else {
				// Warn of the race condition where Redis and the persistent database reflect different states.
				log.warn("[Job {}:*:*] The streaming job is not in progress and cannot be cancelled at this time.", jobId);
			}
			return true;
		}
	}

	/** Create the output object file system for the specified streaming job and store parameters describing
	 * the output object file system within the streaming job
	 * @param jobId The unique job id of the streaming job
	 * @throws WfmProcessingException
	 */
	@Override
	public synchronized void initializeOutputDirectory(long jobId) throws WfmProcessingException {
		StreamingJobRequest streamingJobRequest = streamingJobRequestDao.findById(jobId);
		if ( streamingJobRequest == null ) {
			throw new WfmProcessingException(String.format("A streaming job with id %d is not known to the system.", jobId));
		} else {
			try {
				// create the output object directory for this streaming job and store the absolute path to that directory
				// (as a String) in the streaming job request
				File outputObjectsDirName = propertiesUtil.createStreamingOutputObjectsDirectory(jobId);
				streamingJobRequest.setOutputObjectDirectory(outputObjectsDirName.getAbsolutePath());
				streamingJobRequest.setOutputObjectVersion(propertiesUtil.getOutputObjectVersion());

				// update the streaming job request in the MySQL long-term database
				streamingJobRequestDao.persist(streamingJobRequest);

			} catch( IOException wpe ) {
			  String errorMessage = "Failed to create the output object file directory for streaming job " + jobId + " due to IO exception.";
				log.error(errorMessage);
				throw new WfmProcessingException(errorMessage,wpe);
			}
		}
	}

//	private StreamingJobRequest resubmitInternal(long jobId, PriorityPolicy priorityPolicy, int priority) throws WfmProcessingException {
//		priorityPolicy = (priorityPolicy == null) ? PriorityPolicy.DEFAULT : priorityPolicy;
//
//		log.debug("Attempting to resubmit streaming job {} using {} priority of {}.", jobId, priorityPolicy.name(), priority);
//
//		StreamingJobRequest streamingJobRequest = streamingJobRequestDao.findById(jobId);
//		if ( streamingJobRequest == null ) {
//			throw new WfmProcessingException(String.format("A streaming job with id %d is not known to the system.", jobId));
//		} else if ( !streamingJobRequest.getStatus().isTerminal() ) {
//			throw new WfmProcessingException(String.format("The streaming job with id %d is in the non-terminal state of '%s'. Only jobs in a terminal state may be resubmitted.",
//					jobId, streamingJobRequest.getStatus().name()));
//		} else {
//			JsonStreamingJobRequest jsonStreamingJobRequest = jsonUtils.deserialize(streamingJobRequest.getInputObject(), JsonStreamingJobRequest.class);
//
//			// If the priority should be changed during resubmission, make that change now.
//			if( priorityPolicy == PriorityPolicy.PROVIDED ) {
//
//				// Get a copy of this streaming job's stream in order to add it to the new instance we're about to create.
//				jsonStreamingJobRequest = new JsonStreamingJobRequest(jsonStreamingJobRequest.getExternalId(),
//						jsonStreamingJobRequest.isOutputObjectEnabled(), jsonStreamingJobRequest.getOutputObjectDirectory(),
//						jsonStreamingJobRequest.getPipeline(), priority, jsonStreamingJobRequest.getStream(),
//						jsonStreamingJobRequest.getStallAlertDetectionThreshold(),
//						jsonStreamingJobRequest.getStallAlertRate(),
//						jsonStreamingJobRequest.getStallTimeout(),
//            jsonStreamingJobRequest.getHealthReportCallbackUri(),
//            jsonStreamingJobRequest.getSummaryReportCallbackUri(),
//            jsonStreamingJobRequest.getNewTrackAlertCallbackUri(),
//            jsonStreamingJobRequest.getCallbackMethod(),
//            jsonStreamingJobRequest.getAlgorithmProperties(), jsonStreamingJobRequest.getJobProperties());
//
//			}
//
//			streamingJobRequest = initializeInternal(streamingJobRequest, jsonStreamingJobRequest);
//
//			// streaming jobs do not yet support markup, commenting out for now
////			markupResultDao.deleteByJobId(jobId);
//
//			return runInternal(streamingJobRequest, jsonStreamingJobRequest, priority);
//		}
//	}

	/** Finish initializing the StreamingJobRequest and persist it in the database for long-term storage
	 * Upon return, the streaming job will be persisted in the long-term database
	 * @param streamingJobRequest partially initialized streamingJobRequest
	 * @param jsonStreamingJobRequest JSON version of the streaming job request that will be serialized into the streamingJobRequests input object
	 * @return fully initialized streamingJobRequest
	 * @throws WfmProcessingException
	 */
	private StreamingJobRequest initializeInternal(StreamingJobRequest streamingJobRequest, JsonStreamingJobRequest jsonStreamingJobRequest) throws WfmProcessingException {
		streamingJobRequest.setPriority(jsonStreamingJobRequest.getPriority());
		streamingJobRequest.setStatus(JobStatus.INITIALIZED);
		streamingJobRequest.setTimeReceived(new Date());
		streamingJobRequest.setInputObject(jsonUtils.serialize(jsonStreamingJobRequest));
		streamingJobRequest.setPipeline(jsonStreamingJobRequest.getPipeline() == null ? null : TextUtils.trimAndUpper(jsonStreamingJobRequest.getPipeline().getName()));

		// Set output object version and path to null.  These will be set later after the job has been
		// submitted to MPF and when the
		// output objects are actually created (if enabled)
		streamingJobRequest.setOutputObjectDirectory(null);
		streamingJobRequest.setOutputObjectVersion(null);

		// set remaining items that need to be persisted
		streamingJobRequest.setExternalId(jsonStreamingJobRequest.getExternalId());
		streamingJobRequest.setHealthReportCallbackUri(jsonStreamingJobRequest.getHealthReportCallbackUri());
		streamingJobRequest.setStreamUri(jsonStreamingJobRequest.getStream().getStreamUri());

		// store the streaming job request in the MySQL long-term database
		return streamingJobRequestDao.persist(streamingJobRequest);
	}

	/** Send the streaming job request to the components via the Node Manager (TODO)
	 * @param streamingJobRequest)
	 * @param jsonStreamingJobRequest
	 * @param priority
	 * @return
	 * @throws WfmProcessingException
	 */
	private StreamingJobRequest runInternal(StreamingJobRequest streamingJobRequest, JsonStreamingJobRequest jsonStreamingJobRequest, int priority) throws WfmProcessingException {
		Map<String, Object> headers = new HashMap<String, Object>();
		headers.put(MpfHeaders.JOB_ID, streamingJobRequest.getId());
		headers.put(MpfHeaders.JMS_PRIORITY, Math.max(0, Math.min(9, priority)));
		log.info("[Streaming Job {}|*|*] is running at priority {}.", streamingJobRequest.getId(), headers.get(MpfHeaders.JMS_PRIORITY));
		log.info(this.getClass().getName()+".runInternal: TODO notification of new streaming job "+streamingJobRequest.getId()+" to Components via NodeManager (pending OpenMPF Issue #109)");
		// TODO send the streaming job to the master node manager
//		streamingJobRequestProducerTemplate.sendBodyAndHeaders(StreamingJobCreatorRouteBuilder.ENTRY_POINT, ExchangePattern.InOnly, jsonUtils.serialize(jsonStreamingJobRequest), headers);
		return streamingJobRequest;
	}
}
