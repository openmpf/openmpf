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
import org.mitre.mpf.interop.JsonJobRequest;
import org.mitre.mpf.interop.JsonMediaInputObject;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.businessrules.JobRequestBo;
import org.mitre.mpf.wfm.camel.routes.JobCreatorRouteBuilder;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.RedisImpl;
import org.mitre.mpf.wfm.data.access.MarkupResultDao;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateDao;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateJobRequestDaoImpl;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateMarkupResultDaoImpl;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.enums.JobStatus;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.service.PipelineService;
import org.mitre.mpf.wfm.util.JmsUtils;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class JobRequestBoImpl implements JobRequestBo {
	/**
	 * Internal enumeration which is used to indicate whether a resubmitted job should use the original
	 * priority value or a new value which has been provided.
	 */
	private static enum PriorityPolicy {
		/** Resubmits the job with a different priority. */
		PROVIDED,

		/** DEFAULT: Resubmits the job using the same priority as its first run. */
		EXISTING;

		/** The default action is to re-use the original priority. */
		public static final PriorityPolicy DEFAULT = EXISTING;
	}

	private static final Logger log = LoggerFactory.getLogger(JobRequestBoImpl.class);
	public static final String REF = "jobRequestBoImpl";

	@Autowired
	private PipelineService pipelineService;

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
	@Qualifier(HibernateJobRequestDaoImpl.REF)
	private HibernateDao<JobRequest> jobRequestDao;

	@Autowired
	@Qualifier(HibernateMarkupResultDaoImpl.REF)
	private MarkupResultDao markupResultDao;

	@EndpointInject(uri = JobCreatorRouteBuilder.ENTRY_POINT)
	private ProducerTemplate jobRequestProducerTemplate;

	@Override
	public JsonJobRequest createRequest(String externalId, String pipelineName, List<JsonMediaInputObject> media, Map<String,Map> algorithmProperties, Map<String, String> jobProperties, boolean buildOutput, int priority) {

		JsonJobRequest jsonJobRequest = new JsonJobRequest(TextUtils.trim(externalId), buildOutput, pipelineService.createJsonPipeline(pipelineName), priority);
		if(media != null) {
            jsonJobRequest.getMedia().addAll(media);
		}

		// update to add the job algorithm-specific-properties, supporting the priority:
		// action-property defaults (lowest) -> action-properties -> job-properties -> algorithm-properties -> media-properties (highest)
		if ( algorithmProperties != null ) {
			for (Map.Entry<String,Map> property : algorithmProperties.entrySet()) {
				jsonJobRequest.getAlgorithmProperties().put(property.getKey().toUpperCase(), property.getValue());
			}
		}

		if (jobProperties != null) {
			for (Map.Entry<String,String> property : jobProperties.entrySet()) {
				jsonJobRequest.getJobProperties().put(property.getKey().toUpperCase(), property.getValue());
			}
		}
		return jsonJobRequest;
	}

	@Override
	public JsonJobRequest createRequest(String externalId, String pipelineName, List<JsonMediaInputObject> media, Map<String,Map> algorithmProperties, Map<String, String> jobProperties, boolean buildOutput, int priority, String callbackURL, String callbackMethod) {
		log.debug("[createRequest] externalId:"+externalId +" pipeline:"+pipelineName + " buildOutput:"+buildOutput+" priority:"+priority+" callbackURL:"+callbackURL + " callbackMethod:"+callbackMethod);
		String jsonCallbackURL = "";
		String jsonCallbackMethod = "GET";
		if(callbackURL != null && TextUtils.trim(callbackURL).length() > 0) {
			jsonCallbackURL = TextUtils.trim(callbackURL);
		}
		if(callbackMethod != null && !TextUtils.trim(callbackMethod).equals("GET") ){//only GET or POST allowed
			jsonCallbackMethod = "POST";
		}

		JsonJobRequest jsonJobRequest = new JsonJobRequest(TextUtils.trim(externalId), buildOutput, pipelineService.createJsonPipeline(pipelineName), priority, jsonCallbackURL, jsonCallbackMethod);
		if(media != null) {
			jsonJobRequest.getMedia().addAll(media);
		}

		// update to add the job algorithm-specific-properties, supporting the priority:
		// action-property defaults (lowest) -> action-properties -> job-properties -> algorithm-properties -> media-properties (highest)
		if ( algorithmProperties != null ) {
			for (Map.Entry<String,Map> property : algorithmProperties.entrySet()) {
				jsonJobRequest.getAlgorithmProperties().put(property.getKey().toUpperCase(), property.getValue());
			}
		}

		if (jobProperties != null) {
			for (Map.Entry<String,String> property : jobProperties.entrySet()) {
				jsonJobRequest.getJobProperties().put(property.getKey().toUpperCase(), property.getValue());
			}
		}
		return jsonJobRequest;
	}

	@Override
	public JobRequest run(JsonJobRequest jobRequest) throws WfmProcessingException {
		JobRequest jobRequestEntity = initialize(jobRequest);
		return runInternal(jobRequestEntity, jobRequest, (jobRequest == null) ? propertiesUtil.getJmsPriority() : jobRequest.getPriority());
	}

	@Override
	public JobRequest initialize(JsonJobRequest jsonJobRequest) throws WfmProcessingException {
		JobRequest jobRequestEntity = new JobRequest();
		return initializeInternal(jobRequestEntity, jsonJobRequest);
	}

	@Override
	public JobRequest resubmit(long jobId) throws WfmProcessingException {
		return resubmitInternal(jobId, PriorityPolicy.EXISTING, 0);
	}

	@Override
	public JobRequest resubmit(long jobId, int priority) throws WfmProcessingException {
		return resubmitInternal(jobId, PriorityPolicy.PROVIDED, priority);
	}

	@Override
	public synchronized boolean cancel(long jobId) throws WfmProcessingException {
		log.debug("[Job {}:*:*] Received request to cancel this job.", jobId);
		JobRequest jobRequest = jobRequestDao.findById(jobId);
		if(jobRequest == null) {
			throw new WfmProcessingException(String.format("A job with the id %d is not known to the system.", jobId));
		}

		assert jobRequest.getStatus() != null : "Jobs must not have a null status.";

		if(jobRequest.getStatus().isTerminal() || jobRequest.getStatus() == JobStatus.CANCELLING) {
			log.warn("[Job {}:*:*] This job is in the state of '{}' and cannot be cancelled at this time.", jobId, jobRequest.getStatus().name());
			return false;
		} else {
			log.info("[Job {}:*:*] Cancelling job.", jobId);

			// Mark the job as cancelled in Redis so that future steps in the workflow will know not to continue processing.
			if(redis.cancel(jobId)) {
				try {
					// Try to move any pending work items on the queues to the appropriate cancellation queues.
					// If this operation fails, any remaining pending items will continue to process, but
					// the future splitters should not create any new work items. In short, if this fails,
					// the system should not be affected, but the job may not complete any faster.
					jmsUtils.cancel(jobId);
				} catch (Exception exception) {
					log.warn("[Job {}:*:*] Failed to remove the pending work elements in the message broker for this job. The job must complete the pending work elements before it will cancel the job.", jobId, exception);
				}
				jobRequest.setStatus(JobStatus.CANCELLING);
				jobRequestDao.persist(jobRequest);
			} else {
				// Warn of the race condition where Redis and the persistent database reflect different states.
				log.warn("[Job {}:*:*] The job is not in progress and cannot be cancelled at this time.", jobId);
			}
			return true;
		}
	}

	private JobRequest resubmitInternal(long jobId, PriorityPolicy priorityPolicy, int priority) throws WfmProcessingException {
		priorityPolicy = (priorityPolicy == null) ? PriorityPolicy.DEFAULT : priorityPolicy;

		log.debug("Attempting to resubmit job {} using {} priority of {}.", jobId, priorityPolicy.name(), priority);

		JobRequest jobRequest = jobRequestDao.findById(jobId);
		if(jobRequest == null) {
			throw new WfmProcessingException(String.format("A job with id %d is not known to the system.", priority));
		} else if(!jobRequest.getStatus().isTerminal()) {
			throw new WfmProcessingException(String.format("The job with id %d is in the non-terminal state of '%s'. Only jobs in a terminal state may be resubmitted.",
					jobId, jobRequest.getStatus().name()));
		} else {
			JsonJobRequest jsonJobRequest = jsonUtils.deserialize(jobRequest.getInputObject(), JsonJobRequest.class);

			// If the priority should be changed during resubmission, make that change now.
			if(priorityPolicy == PriorityPolicy.PROVIDED) {

				// Create a copy of this job's media in order to add it to the new instance we're about to create.
				final List<JsonMediaInputObject> originalMedia = new LinkedList<>();
				for (JsonMediaInputObject media : jsonJobRequest.getMedia()) {
					JsonMediaInputObject originalMedium = new JsonMediaInputObject(media.getMediaUri());
					originalMedium.getProperties().putAll(media.getProperties());
					originalMedia.add(originalMedium);
				};

				jsonJobRequest = new JsonJobRequest(jsonJobRequest.getExternalId(), jsonJobRequest.isOutputObjectEnabled(), jsonJobRequest.getPipeline(), priority) {{
					getMedia().addAll(originalMedia);
				}};

			}

			jobRequest = initializeInternal(jobRequest, jsonJobRequest);
			markupResultDao.deleteByJobId(jobId);
			return runInternal(jobRequest, jsonJobRequest, priority);
		}
	}

	private JobRequest initializeInternal(JobRequest jobRequest, JsonJobRequest jsonJobRequest) throws WfmProcessingException {
		jobRequest.setPriority(jsonJobRequest.getPriority());
		jobRequest.setStatus(JobStatus.INITIALIZED);
		jobRequest.setTimeReceived(new Date());
		jobRequest.setInputObject(jsonUtils.serialize(jsonJobRequest));
		jobRequest.setPipeline(jsonJobRequest.getPipeline() == null ? null : TextUtils.trimAndUpper(jsonJobRequest.getPipeline().getName()));

		// Reset output object paths.
		jobRequest.setOutputObjectPath(null);

		// Set output object version to null.
		jobRequest.setOutputObjectVersion(null);
		return jobRequestDao.persist(jobRequest);
	}

	private JobRequest runInternal(JobRequest jobRequest, JsonJobRequest jsonJobRequest, int priority) throws WfmProcessingException {
		Map<String, Object> headers = new HashMap<String, Object>();
		headers.put(MpfHeaders.JOB_ID, jobRequest.getId());
		headers.put(MpfHeaders.JMS_PRIORITY, Math.max(0, Math.min(9, priority)));
		log.info("[Job {}|*|*] Job has started and is running at priority {}.", jobRequest.getId(), headers.get(MpfHeaders.JMS_PRIORITY));
		jobRequestProducerTemplate.sendBodyAndHeaders(JobCreatorRouteBuilder.ENTRY_POINT, ExchangePattern.InOnly, jsonUtils.serialize(jsonJobRequest), headers);
		return jobRequest;
	}
}
