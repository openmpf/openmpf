/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.camel.operations.mediaretrieval;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultMessage;
import org.mitre.mpf.wfm.camel.WfmLocalSplitter;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Retrieves a remote HTTP or HTTPS media file. */
@Component(RemoteMediaSplitter.REF)
public class RemoteMediaSplitter extends WfmLocalSplitter {
	private static final Logger log = LoggerFactory.getLogger(RemoteMediaSplitter.class);
	public static final String REF = "remoteMediaSplitter";

	private final InProgressBatchJobsService _inProgressJobs;

    @Inject
    RemoteMediaSplitter(InProgressBatchJobsService inProgressJobs) {
        super(inProgressJobs);
        _inProgressJobs = inProgressJobs;
    }

	@Override
	public String getSplitterName() { return REF; }

	@Override
	public List<Message> wfmSplit(Exchange exchange) {
	    long jobId = exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class);
		BatchJob job = _inProgressJobs.getJob(jobId);
		log.debug("Message received. Job ID: {}. Media Count: {}.", job.getId(), job.getMedia().size());

		// Initialize a collection of work units produced by the splitter.
		List<Message> messages = new ArrayList<Message>();

		if(!job.isCancelled()) {
			// If the job has not been cancelled, iterate through the collection of media associated with the job. If the
			// media's original URI looks like an HTTP or HTTPS URL, create a work unit for that URI.
			for (Media media : job.getMedia()) {

				// Check that the media has not previously failed. This will happen if the input URIs are invalid.
				if (!media.isFailed() && media.getUriScheme() != UriScheme.FILE && media.getUriScheme() != UriScheme.UNDEFINED) {
					var message = new DefaultMessage(exchange.getContext());
					message.setHeader(MpfHeaders.MEDIA_ID, media.getId());
					message.setHeader(MpfHeaders.JOB_ID, jobId);
					messages.add(message);
				}
			}
		} else {
			log.warn("Remote media inspection will not be performed because this job has been " +
					         "cancelled.");
		}

		return messages;
	}
}
