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

package org.mitre.mpf.wfm.camel.operations.jobcreation;

import org.apache.camel.Exchange;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.interop.*;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.businessrules.JobRequestBo;
import org.mitre.mpf.wfm.businessrules.impl.JobRequestBoImpl;
import org.mitre.mpf.wfm.camel.WfmProcessor;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.RedisImpl;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateDao;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateJobRequestDaoImpl;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.service.JobStatusBroadcaster;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private PropertiesUtil propertiesUtil;

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

	@Autowired
	private JobStatusBroadcaster jobStatusBroadcaster;

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
				transientAction.getProperties().put(property.getKey().toUpperCase(), property.getValue());
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

            // Capture the current state of the detection system properties at the time when this job is created. Since the
            // detection system properties may be changed by an administrator, we must ensure that the job uses a consistent set of detection system
            // properties through all stages of the jobs pipeline by storing these detection system property values in REDIS.
            TransientDetectionSystemProperties transientDetectionSystemProperties = propertiesUtil.createDetectionSystemPropertiesSnapshot();

            TransientPipeline transientPipeline = buildPipeline(jobRequest.getPipeline());

			TransientJob transientJob = new TransientJob(jobRequestEntity.getId(), jobRequest.getExternalId(), transientDetectionSystemProperties, transientPipeline,
                                                0, jobRequest.getPriority(), jobRequest.isOutputObjectEnabled(), false,jobRequest.getCallbackURL(),jobRequest.getCallbackMethod());

			transientJob.getOverriddenJobProperties().putAll(jobRequest.getJobProperties());

			// algorithm properties should override any previously set properties (note: priority scheme enforced in DetectionSplitter)
			transientJob.getOverriddenAlgorithmProperties().putAll(jobRequest.getAlgorithmProperties());

			transientJob.getMedia().addAll(buildMedia(jobRequest.getMedia()));

            redis.persistJob(transientJob);

			if (transientPipeline == null) {
				redis.setJobStatus(jobId, BatchJobStatusType.IN_PROGRESS_ERRORS);
				throw new WfmProcessingException(INVALID_PIPELINE_MESSAGE);
			}

			BatchJobStatusType jobStatus;
			if (transientJob.getMedia().stream().anyMatch(m -> m.isFailed())) {
				jobStatus = BatchJobStatusType.IN_PROGRESS_ERRORS;
				// allow the job to run since some of the media may be good
			} else {
				jobStatus = BatchJobStatusType.IN_PROGRESS;
			}

			jobRequestEntity.setStatus(jobStatus);
			redis.setJobStatus(jobId, jobStatus);
			jobStatusBroadcaster.broadcast(jobId, 0, jobStatus);

			jobRequestEntity = jobRequestDao.persist(jobRequestEntity);

			exchange.getOut().setBody(jsonUtils.serialize(transientJob));
			exchange.getOut().getHeaders().put(MpfHeaders.JOB_ID, jobRequestEntity.getId());

		} catch(WfmProcessingException exception) {
			try {
				// Make an effort to mark the job as failed.
				if(INVALID_PIPELINE_MESSAGE.equals(exception.getMessage())) {
					log.warn("Batch Job #{} did not specify a valid pipeline.", jobId);
				} else {
					log.warn("Failed to parse the input object for Batch Job #{} due to an exception.", jobRequestEntity.getId(), exception);
				}
				jobRequestEntity.setStatus(BatchJobStatusType.JOB_CREATION_ERROR);
				jobRequestEntity.setTimeCompleted(Instant.now());
				jobRequestEntity = jobRequestDao.persist(jobRequestEntity);
			} catch(Exception persistException) {
				log.warn("Failed to mark Batch Job #{} as failed due to an exception. It will remain it its current state until manually changed.", jobRequestEntity, persistException);
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



