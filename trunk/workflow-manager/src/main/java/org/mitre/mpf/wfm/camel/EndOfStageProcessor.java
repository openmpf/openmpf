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

package org.mitre.mpf.wfm.camel;

import java.util.Date;

import org.apache.camel.Exchange;
import org.mitre.mpf.mvc.controller.AtmosphereController;
import org.mitre.mpf.mvc.model.JobStatusMessage;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.RedisImpl;
import org.mitre.mpf.wfm.data.entities.transients.TransientJob;
import org.mitre.mpf.wfm.enums.JobStatus;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.event.JobProgress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component(EndOfStageProcessor.REF)
public class EndOfStageProcessor extends WfmProcessor {
	public static final String REF = "endOfStageProcessor";
	private static final Logger log = LoggerFactory.getLogger(EndOfStageProcessor.class);

	@Autowired
	@Qualifier(RedisImpl.REF)
	private Redis redis;
	
	@Autowired
	private JobProgress jobProgressStore;

	@Override
	public void wfmProcess(Exchange exchange) throws WfmProcessingException {
		TransientJob job = jsonUtils.deserialize(exchange.getIn().getBody(byte[].class), TransientJob.class);
		job.setCurrentStage(job.getCurrentStage() + 1);

		log.info("[Job {}|{}|*] Stage Complete! Progress is now {}/{}.",
				exchange.getIn().getHeader(MpfHeaders.JOB_ID),
				job.getCurrentStage() - 1,
				job.getCurrentStage(),
				job.getPipeline().getStages().size());

		exchange.getOut().setBody(jsonUtils.serialize(job));
		redis.setCurrentTaskIndex(job.getId(), job.getCurrentStage());

		if(job.getCurrentStage() >= job.getPipeline().getStages().size()) {
			long jobId = exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class);
			//notify of completion - use
			if(!job.isOutputEnabled()) {
				AtmosphereController.broadcast(new JobStatusMessage(jobId, 100, job.isCancelled() ? JobStatus.CANCELLED : JobStatus.COMPLETE, new Date()));
				jobProgressStore.setJobProgress(jobId, 100.0f);
			} else {
				AtmosphereController.broadcast(new JobStatusMessage(jobId, 99, JobStatus.BUILDING_OUTPUT_OBJECT, new Date()));
				jobProgressStore.setJobProgress(jobId, 99.0f);
			}			
			log.debug("[Job {}|*|*] All stages have completed. Setting the {} flag.", exchange.getIn().getHeader(MpfHeaders.JOB_ID), MpfHeaders.JOB_COMPLETE);
			exchange.getOut().setHeader(MpfHeaders.JOB_COMPLETE, Boolean.TRUE);
		}

		exchange.getOut().setHeader(MpfHeaders.JMS_PRIORITY, job.getPriority());
	}
}
