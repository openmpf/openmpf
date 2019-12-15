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

package org.mitre.mpf.wfm.camel.operations.mediainspection;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultMessage;
import org.mitre.mpf.wfm.camel.WfmSplitter;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.transients.TransientJob;
import org.mitre.mpf.wfm.data.entities.transients.TransientMedia;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component(MediaInspectionSplitter.REF)
public class MediaInspectionSplitter extends WfmSplitter {
	private static final Logger log = LoggerFactory.getLogger(MediaInspectionSplitter.class);
	public static final String REF = "mediaInspectionSplitter";

	@Autowired
	private InProgressBatchJobsService inProgressJobs;

	@Override
	public String getSplitterName() { return REF; }


	@Override
	public List<Message> wfmSplit(Exchange exchange) {
		long jobId = exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class);

		log.debug(">> [JOB: {}] Starting media splitting", jobId);

		TransientJob transientJob = inProgressJobs.getJob(jobId);
		List<Message> messages = new ArrayList<>();

		if(!transientJob.isCancelled()) {
			// If the job has not been cancelled, perform the split.
			for (TransientMedia transientMedia : transientJob.getMedia()) {
				if (!transientMedia.isFailed()) {
					Message message = new DefaultMessage();
					message.setHeader(MpfHeaders.JOB_ID, jobId);
					message.setHeader(MpfHeaders.MEDIA_ID, transientMedia.getId());
					messages.add(message);
				} else {
					log.warn(">> [JOB: {}, MEDIA: {}] Skipping media '{}' that is in an error state.", jobId, transientMedia.getId(), transientMedia.getUri());
				}
			}
			if (messages.isEmpty()) {
				inProgressJobs.setJobStatus(transientJob.getId(), BatchJobStatusType.ERROR);
			}
		} else {
			log.warn(">> [JOB: {}] Media inspection will not be performed because this job has been cancelled.", jobId);
		}

		log.debug(">> [JOB: {}] Ending media splitting. Generated {} messages.", jobId, messages.size());

		return messages;
	}
}
