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
import org.mitre.mpf.wfm.businessrules.StreamingJobRequestBo;
import org.mitre.mpf.wfm.businessrules.impl.StreamingJobRequestBoImpl;
import org.mitre.mpf.wfm.camel.WfmProcessor;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.RedisImpl;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateDao;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateStreamingJobRequestDaoImpl;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobRequest;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.enums.JobStatus;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * The first step in the Workflow Manager is to translate a JSON job request into an internal
 * representation which can be passed through the workflow. This class performs this critical
 * first step.
 */
@Component(StreamingJobCreationProcessor.REF)
public class StreamingJobCreationProcessor extends WfmProcessor {
	private static final Logger log = LoggerFactory.getLogger(StreamingJobCreationProcessor.class);
	public static final String REF = "streamingJobCreationProcessor";
	private static final String INVALID_PIPELINE_MESSAGE = "INVALID_PIPELINE_MESSAGE";

	@Autowired
	@Qualifier(RedisImpl.REF)
	private Redis redis;

	@Autowired
	private JsonUtils jsonUtils;

	@Autowired
	@Qualifier(StreamingJobRequestBoImpl.REF)
	private StreamingJobRequestBo streamingJobRequestBo;

	@Autowired
	@Qualifier(HibernateStreamingJobRequestDaoImpl.REF)
	private HibernateDao<StreamingJobRequest> streamingJobRequestDao;

	/** Converts a pipeline represented in JSON to a {@link TransientPipeline} instance. */
	private TransientPipeline buildPipeline(JsonPipeline jsonPipeline) {

		if ( jsonPipeline == null ) {
			return null;
		}

		String name = jsonPipeline.getName();
		String description = jsonPipeline.getDescription();
		TransientPipeline transientPipeline = new TransientPipeline(name, description);

		// Iterate through the pipeline's stages and add them to the pipeline protocol buffer.
		for ( JsonStage stage : jsonPipeline.getStages() ) {
			transientPipeline.getStages().add(buildStage(stage));
		}

		return transientPipeline;
	}

	/** Maps a stage (represented in JSON) to a {@link TransientStage} instance. */
	private TransientStage buildStage(JsonStage stage) {
		String name = stage.getName();
		String description = stage.getDescription();
		String operation = stage.getActionType();

		TransientStage transientStage = new TransientStage(name, description, ActionType.valueOf(TextUtils.trimAndUpper(operation)));

		// Iterate through the stage's actions and add them to the stage protocol buffer.
		for ( JsonAction action : stage.getActions() ) {
			transientStage.getActions().add(buildAction(action));
		}

		return transientStage;
	}

	/** Maps an action (represented in JSON) to a {@link TransientAction} instance. */
	private TransientAction buildAction(JsonAction action) {
		String name = action.getName();
		String description = action.getDescription();
		String algorithm = action.getAlgorithm();

		TransientAction transientAction = new TransientAction(name, description, algorithm);

		// Finally, iterate through all of the properties in this action and copy them to the protocol buffer.
		for ( Map.Entry<String, String> property : action.getProperties().entrySet() ) {
			if ( StringUtils.isNotBlank(property.getKey()) && StringUtils.isNotBlank(property.getValue()) ) {
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
		StreamingJobRequest streamingJobRequestEntity = new StreamingJobRequest();

		try {
			// Try to parse the JSON request. If this fails, the streaming job must fail.
			JsonStreamingJobRequest json_streamingJobRequest = jsonUtils.deserialize(exchange.getIn().getBody(byte[].class), JsonStreamingJobRequest.class);
			JsonStreamingInputObject json_stream = json_streamingJobRequest.getStream();

			if ( jobId == null ) {
				// A persistent representation of the object has not yet been created, so do that now.
				streamingJobRequestEntity = streamingJobRequestBo.initialize(json_streamingJobRequest);
			} else {
				// The persistent representation already exists - retrieve it.
				streamingJobRequestEntity = streamingJobRequestDao.findById(jobId);
			}

			TransientPipeline transientPipeline = buildPipeline(json_streamingJobRequest.getPipeline());

			TransientStreamingJob transientStreamingJob = new TransientStreamingJob(streamingJobRequestEntity.getId(),
					json_streamingJobRequest.getExternalId(), transientPipeline, 0, json_streamingJobRequest.getPriority(),
					json_streamingJobRequest.getStallAlertDetectionThreshold(),
					json_streamingJobRequest.getStallAlertRate(), json_streamingJobRequest.getStallTimeout(),
					json_streamingJobRequest.isOutputObjectEnabled(),
					json_streamingJobRequest.getOutputObjectDirectory(),
					false,
					json_streamingJobRequest.getHealthReportCallbackUri(),json_streamingJobRequest.getSummaryReportCallbackUri(),
					json_streamingJobRequest.getHealthReportCallbackUri(),json_streamingJobRequest.getCallbackMethod());

			transientStreamingJob.getOverriddenJobProperties().putAll(json_streamingJobRequest.getJobProperties());

			// algorithm properties should override any previously set properties (note: priority scheme enforced in DetectionSplitter)
			transientStreamingJob.getOverriddenAlgorithmProperties().putAll(json_streamingJobRequest.getAlgorithmProperties());

			// add the transient stream to this transient streaming job
			transientStreamingJob.setStream(buildTransientStream(json_streamingJobRequest.getStream()));

			// add the transient streaming job to REDIS
			redis.persistJob(transientStreamingJob);

			if ( transientPipeline == null ) {
				redis.setJobStatus(jobId, JobStatus.IN_PROGRESS_ERRORS);
				throw new WfmProcessingException(INVALID_PIPELINE_MESSAGE);
			}

			// Everything has been good so far. Update the job status using running status for a streaming job
			streamingJobRequestEntity.setStatus(JobStatus.RUNNING);
			redis.setJobStatus(jobId, JobStatus.RUNNING);
			streamingJobRequestEntity = streamingJobRequestDao.persist(streamingJobRequestEntity);

			exchange.getOut().setBody(jsonUtils.serialize(transientStreamingJob));
			exchange.getOut().getHeaders().put(MpfHeaders.JOB_ID, streamingJobRequestEntity.getId());
		} catch ( WfmProcessingException exception ) {
			try {
				// Make an effort to mark the streaming job as failed.
				if ( INVALID_PIPELINE_MESSAGE.equals(exception.getMessage()) ) {
					log.warn("Streaming Job #{} did not specify a valid pipeline.", jobId);
				} else {
					log.warn("Failed to parse the input object for Streaming Job #{} due to an exception.", streamingJobRequestEntity.getId(), exception);
				}
				streamingJobRequestEntity.setStatus(JobStatus.JOB_CREATION_ERROR);
				streamingJobRequestEntity.setTimeCompleted(new Date());
				streamingJobRequestEntity = streamingJobRequestDao.persist(streamingJobRequestEntity);
			} catch ( Exception persistException ) {
				log.warn("Failed to mark Streaming Job #{} as failed due to an exception. It will remain it its current state until manually changed.", streamingJobRequestEntity, persistException);
			}

			// Set a flag so that the routing logic knows that the streaming job has completed.
			exchange.getOut().setHeader(MpfHeaders.JOB_CREATION_ERROR, Boolean.TRUE);
			exchange.getOut().getHeaders().put(MpfHeaders.JOB_ID, (streamingJobRequestEntity.getId() >= 0 ? streamingJobRequestEntity.getId() : Long.MIN_VALUE));
		}
	}

	private TransientStream buildTransientStream(JsonStreamingInputObject json_input_stream) throws WfmProcessingException {
		TransientStream transientStream = new TransientStream(redis.getNextSequenceValue(),
				json_input_stream.getStreamUri());
		transientStream.setSegmentSize(json_input_stream.getSegmentSize());
		transientStream.setMediaProperties(json_input_stream.getMediaProperties());
		return transientStream;
	}
}
