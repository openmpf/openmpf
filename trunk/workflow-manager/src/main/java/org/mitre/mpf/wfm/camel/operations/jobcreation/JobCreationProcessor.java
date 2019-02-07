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

import com.google.common.collect.ImmutableList;
import org.apache.camel.Exchange;
import org.mitre.mpf.interop.JsonJobRequest;
import org.mitre.mpf.interop.JsonMediaInputObject;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.businessrules.JobRequestBo;
import org.mitre.mpf.wfm.businessrules.impl.JobRequestBoImpl;
import org.mitre.mpf.wfm.camel.WfmProcessor;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateDao;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateJobRequestDaoImpl;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.data.entities.transients.SystemPropertiesSnapshot;
import org.mitre.mpf.wfm.data.entities.transients.TransientJob;
import org.mitre.mpf.wfm.data.entities.transients.TransientMedia;
import org.mitre.mpf.wfm.data.entities.transients.TransientPipeline;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.service.JobStatusBroadcaster;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
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
	private InProgressBatchJobsService inProgressBatchJobs;

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
				jobId = jobRequestEntity.getId();
			} else {
				// The persistent representation already exists - retrieve it.
				jobRequestEntity = jobRequestDao.findById(jobId);
			}

            // Capture the current state of the detection system properties at the time when this job is created.
			// Since the detection system properties may be changed by an administrator, we must ensure that the job
			// uses a consistent set of detection system properties through all stages of the job's pipeline.
            SystemPropertiesSnapshot systemPropertiesSnapshot = propertiesUtil.createSystemPropertiesSnapshot();

            TransientPipeline transientPipeline = TransientPipeline.from(jobRequest.getPipeline());

			TransientJob transientJob = inProgressBatchJobs.addJob(
					jobRequestEntity.getId(),
					jobRequest.getExternalId(),
					systemPropertiesSnapshot,
					transientPipeline,
					jobRequest.getPriority(),
					jobRequest.isOutputObjectEnabled(),
					jobRequest.getCallbackURL(),
					jobRequest.getCallbackMethod(),
					buildMedia(jobRequest.getMedia()),
					jobRequest.getJobProperties(),
					(Map) jobRequest.getAlgorithmProperties());

			if (transientPipeline == null) {
				inProgressBatchJobs.setJobStatus(jobId, BatchJobStatusType.IN_PROGRESS_ERRORS);
				throw new WfmProcessingException(INVALID_PIPELINE_MESSAGE);
			}

			BatchJobStatusType jobStatus;
			if (transientJob.getMedia().stream().anyMatch(TransientMedia::isFailed)) {
				jobStatus = BatchJobStatusType.ERROR;
				// allow the job to run since some of the media may be good
			} else {
				jobStatus = BatchJobStatusType.IN_PROGRESS;
			}

			jobRequestEntity.setStatus(jobStatus);
			inProgressBatchJobs.setJobStatus(jobId, jobStatus);
			jobStatusBroadcaster.broadcast(jobId, 0, jobStatus);

			jobRequestEntity = jobRequestDao.persist(jobRequestEntity);

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

	private List<TransientMedia> buildMedia(Collection<JsonMediaInputObject> inputMedia) {
		return inputMedia.stream()
				.map(in -> inProgressBatchJobs.initMedia(in.getMediaUri(), in.getProperties()))
				.collect(ImmutableList.toImmutableList());
	}
}



