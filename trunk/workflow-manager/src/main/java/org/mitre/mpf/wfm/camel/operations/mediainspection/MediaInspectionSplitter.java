/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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
import org.mitre.mpf.wfm.data.entities.transients.TransientJob;
import org.mitre.mpf.wfm.data.entities.transients.TransientMedia;
import org.mitre.mpf.wfm.util.JsonUtils;
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
	private JsonUtils jsonUtils;

	@Override
	public String getSplitterName() { return REF; }

	public List<Message> wfmSplit(Exchange exchange) throws Exception {
		List<Message> messages = new ArrayList<>();
		TransientJob transientJob = jsonUtils.deserialize(exchange.getIn().getBody(byte[].class), TransientJob.class);

		if(!transientJob.isCancelled()) {
			// If the job has not been cancelled, perform the split.
			for (TransientMedia transientMedia : transientJob.getMedia()) {
				if (!transientMedia.isFailed()) {
					Message message = new DefaultMessage();
					message.setBody(jsonUtils.serialize(transientMedia));
					messages.add(message);
				} else {
					log.warn("Skipping '{}' ({}). It is in an error state.", transientMedia.getUri(), transientMedia.getId());
				}
			}
		} else {
			log.warn("[Job {}|*|*] Media inspection will not be performed because this job has been cancelled.", transientJob.getId());
		}

		return messages;
	}
}
