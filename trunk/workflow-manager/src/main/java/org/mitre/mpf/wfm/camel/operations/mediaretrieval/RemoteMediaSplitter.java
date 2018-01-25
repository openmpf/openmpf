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

package org.mitre.mpf.wfm.camel.operations.mediaretrieval;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultMessage;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.camel.WfmSplitter;
import org.mitre.mpf.wfm.data.entities.transients.TransientJob;
import org.mitre.mpf.wfm.data.entities.transients.TransientMedia;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Retrieves a remote HTTP or HTTPS media file. */
@Component(RemoteMediaSplitter.REF)
public class RemoteMediaSplitter extends WfmSplitter {
	private static final Logger log = LoggerFactory.getLogger(RemoteMediaSplitter.class);
	public static final String REF = "remoteMediaSplitter";

	@Autowired
	private PropertiesUtil propertiesUtil;

	@Autowired
	private JsonUtils jsonUtils;

	@Override
	public String getSplitterName() { return REF; }

	public List<Message> wfmSplit(Exchange exchange) throws Exception {
		assert exchange.getIn().getBody() != null : "The body must not be null.";
		assert exchange.getIn().getBody(byte[].class) != null : "The body must be convertible to String.";

		// Extract the TransientJob from the body of the incoming message on this exchange.
		TransientJob transientJob = jsonUtils.deserialize(exchange.getIn().getBody(byte[].class), TransientJob.class);
		log.debug("Message received. Job ID: {}. Media Count: {}.", transientJob.getId(), transientJob.getMedia().size());

		// Initialize a collection of work units produced by the splitter.
		List<Message> messages = new ArrayList<Message>();

		if(!transientJob.isCancelled()) {
			// If the job has not been cancelled, iterate through the collection of media associated with the job. If the
			// media's original URI looks like an HTTP or HTTPS URL, create a work unit for that URI.
			for (TransientMedia transientMedia : transientJob.getMedia()) {

				// Check that the media has not previously failed. This will happen if the input URIs are invalid.
				if (!transientMedia.isFailed() && transientMedia.getUriScheme() != UriScheme.FILE && transientMedia.getUriScheme() != UriScheme.UNDEFINED) {
					messages.add(createMessage(transientMedia));
				}
			}
		} else {
			log.warn("[Job {}|*|*] Remote media inspection will not be performed because this job has been cancelled.", transientJob.getId());
		}

		return messages;
	}

	private Message createMessage(TransientMedia sourceMessage) throws WfmProcessingException {
		String localFilePath = new File(propertiesUtil.getRemoteMediaCacheDirectory(), UUID.randomUUID().toString()).getAbsolutePath();
		log.debug("The remote file '{}' will be downloaded and stored to '{}'.", sourceMessage.getUri(), localFilePath);

		Message message = new DefaultMessage();
		sourceMessage.setLocalPath(localFilePath);
		message.setBody(jsonUtils.serialize(sourceMessage));
		return message;
	}
}
