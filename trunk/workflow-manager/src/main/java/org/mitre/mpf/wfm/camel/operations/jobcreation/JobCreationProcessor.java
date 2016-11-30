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

package org.mitre.mpf.wfm.camel.operations.jobcreation;

import org.apache.camel.Exchange;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.interop.*;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.businessrules.impl.JobRequestBoImpl;
import org.mitre.mpf.wfm.camel.WfmProcessor;
import org.mitre.mpf.wfm.data.*;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateDao;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateJobRequestDaoImpl;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.JobStatus;
import org.mitre.mpf.wfm.businessrules.JobRequestBo;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * The first step in the Workflow Manager is to translate a JSON job request into an internal
 * representation which can be passed through the workflow. This class performs this critical
 * first step.
 */
@Component(JobCreationProcessor.REF)
public class JobCreationProcessor extends WfmProcessor {
	private static final Logger log = LoggerFactory.getLogger(JobCreationProcessor.class);
	public static final String REF = "jobCreationProcessor";
	private static final String INVALID_PIPELINE_MESSAGE = "INVALID_PIPELINE_MESSAGE";

	@Autowired
	@Qualifier(RedisImpl.REF)
	private Redis redis;

	@Autowired
	private JsonUtils jsonUtils;

	@Autowired
	@Qualifier(JobRequestBoImpl.REF)
	private JobRequestBo jobRequestBo;

	@Autowired
	@Qualifier(HibernateJobRequestDaoImpl.REF)
	private HibernateDao<JobRequest> jobRequestDao;

	/** Converts a pipeline represented in JSON to a {@link org.mitre.mpf.wfm.data.entities.transients.TransientPipeline} instance. */
	private TransientPipeline buildPipeline(JsonPipeline jsonPipeline) {

		if(jsonPipeline == null) {
			return null;
		}

		String name = jsonPipeline.getName();
		String description = jsonPipeline.getDescription();
		TransientPipeline transientPipeline = new TransientPipeline(name, description);

		// Iterate through the pipeline's stages and add them to the pipeline protocol buffer.
		for(JsonStage stage : jsonPipeline.getStages()) {
			transientPipeline.getStages().add(buildStage(stage));
		}

		return transientPipeline;
	}

	/** Maps a stage (represented in JSON) to a {@link org.mitre.mpf.wfm.data.entities.transients.TransientStage} instance. */
	private TransientStage buildStage(JsonStage stage) {
		String name = stage.getName();
		String description = stage.getDescription();
		String operation = stage.getActionType();

		TransientStage transientStage = new TransientStage(name, description, ActionType.valueOf(TextUtils.trimAndUpper(operation)));

		// Iterate through the stage's actions and add them to the stage protocol buffer.
		for(JsonAction action : stage.getActions()) {
			transientStage.getActions().add(buildAction(action));
		}

		return transientStage;
	}

	/** Maps an action (represented in JSON) to a {@link org.mitre.mpf.wfm.data.entities.transients.TransientAction} instance. */
	private TransientAction buildAction(JsonAction action) {
		String name = action.getName();
		String description = action.getDescription();
		String algorithm = action.getAlgorithm();

		TransientAction transientAction = new TransientAction(name, description, algorithm);

		// Finally, iterate through all of the properties in this action and copy them to the protocol buffer.
		for(Map.Entry<String, String> property : action.getProperties().entrySet()) {
			if(StringUtils.isNotBlank(property.getKey()) && StringUtils.isNotBlank(property.getValue())) {
				transientAction.getProperties().put(property.getKey(), property.getValue());
			}
		}

		return transientAction;
	}

	@Override
	public void wfmProcess(Exchange exchange) throws WfmProcessingException {
		assert exchange.getIn().getBody() != null : "The body must not be null.";
		assert exchange.getIn().getBody(byte[].class) != null : "The body must be convertible to a String, but it is not.";

		Long jobId = exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class);
		JobRequest jobRequestEntity = new JobRequest();

		try {
			// Try to parse the JSON request. If this fails, the job must fail.
			JsonJobRequest jobRequest = jsonUtils.deserialize(exchange.getIn().getBody(byte[].class), JsonJobRequest.class);

			if(jobId == null) {
				// A persistent representation of the object has not yet been created, so do that now.
				jobRequestEntity = jobRequestBo.initialize(jobRequest);
			} else {
				// The persistent representation already exists - retrieve it.
				jobRequestEntity = jobRequestDao.findById(jobId);
			}

			TransientPipeline transientPipeline = buildPipeline(jobRequest.getPipeline());

			TransientJob transientJob = new TransientJob(jobRequestEntity.getId(), jobRequest.getExternalId(), transientPipeline, 0, jobRequest.getPriority(), jobRequest.isOutputObjectEnabled(), false,jobRequest.getCallbackURL(),jobRequest.getCallbackMethod());
			transientJob.getMedia().addAll(buildMedia(jobRequest.getMedia()));
			redis.persistJob(transientJob);

			if(transientPipeline == null) {
				redis.setJobStatus(jobId, JobStatus.IN_PROGRESS_ERRORS);
				throw new WfmProcessingException(INVALID_PIPELINE_MESSAGE);
			}

			// Everything has been good so far. Update the job status.
			jobRequestEntity.setStatus(JobStatus.IN_PROGRESS);
			redis.setJobStatus(jobId, JobStatus.IN_PROGRESS);
			jobRequestEntity = jobRequestDao.persist(jobRequestEntity);

			exchange.getOut().setBody(jsonUtils.serialize(transientJob));
			exchange.getOut().getHeaders().put(MpfHeaders.JOB_ID, jobRequestEntity.getId());
		} catch(WfmProcessingException exception) {
			try {
				// Make an effort to mark the job as failed.
				if(INVALID_PIPELINE_MESSAGE.equals(exception.getMessage())) {
					log.warn("Job #{} did not specify a valid pipeline.", jobId);
				} else {
					log.warn("Failed to parse the input object for Job #{} due to an exception.", jobRequestEntity.getId(), exception);
				}
				jobRequestEntity.setStatus(JobStatus.JOB_CREATION_ERROR);
				jobRequestEntity.setTimeCompleted(new Date());
				jobRequestEntity = jobRequestDao.persist(jobRequestEntity);
			} catch(Exception persistException) {
				log.warn("Failed to mark Job #{} as failed due to an exception. It will remain it its current state until manually changed.", jobRequestEntity, persistException);
			}

			// Set a flag so that the routing logic knows that the job has completed.
			exchange.getOut().setHeader(MpfHeaders.JOB_CREATION_ERROR, Boolean.TRUE);
			exchange.getOut().getHeaders().put(MpfHeaders.JOB_ID, (jobRequestEntity.getId() >= 0 ? jobRequestEntity.getId() : Long.MIN_VALUE));
		}
	}

	private List<TransientMedia> buildMedia(List<JsonMediaInputObject> inputMedia) throws WfmProcessingException {
		List<TransientMedia> transientMedia = new ArrayList<>(inputMedia.size());
		for(JsonMediaInputObject inputObject : inputMedia) {
			TransientMedia media = new TransientMedia(redis.getNextSequenceValue(), inputObject.getMediaUri());
			media.getMediaSpecificProperties().putAll(inputObject.getProperties());
			transientMedia.add(media);
		}
		return transientMedia;
	}
}
