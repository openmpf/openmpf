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

package org.mitre.mpf.wfm.camel;

import org.apache.camel.Exchange;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.RedisImpl;
import org.mitre.mpf.wfm.data.entities.transients.TransientJob;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * This processor retrieves the {@link org.mitre.mpf.wfm.data.entities.transients.TransientJob} associated with the
 * incoming message's {@link org.mitre.mpf.wfm.enums.MpfHeaders#JOB_ID} header, serializes it to JSON, and assigns
 * the resulting JSON to the outgoing message's body.
 */
@Component(JobRetrievalProcessor.REF)
public class JobRetrievalProcessor extends WfmProcessor {
	public static final String REF = "jobRetrievingProcessor";

	@Autowired
	@Qualifier(RedisImpl.REF)
	private Redis redis;

	@Override
	public void wfmProcess(Exchange exchange) throws WfmProcessingException {
		long jobId = exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class);

		// Retrieve the job from the transient data store.
		TransientJob transientJob = redis.getJob(jobId);
		assert transientJob != null : String.format("A job with id %d could not be found.", jobId);

		// Serialize the transient job and assign it to the body of the outgoing message.
		byte[] binaryJson = jsonUtils.serialize(transientJob);
		exchange.getOut().setBody(binaryJson);
	}
}
