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

import com.google.protobuf.MessageLite;
import org.apache.camel.Exchange;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.RedisImpl;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Map;

public abstract class ResponseProcessor<T extends MessageLite> extends WfmProcessor {
	private static final Logger log = LoggerFactory.getLogger(ResponseProcessor.class);

	@Autowired
	@Qualifier(RedisImpl.REF)
	protected Redis redis;

	protected Class<T> clazz;

	/**
	 * When overridden, processes the response received by a component.
	 *
	 * @param jobId The persistent identifier of the job associated with this response.
	 * @param response The response which was returned from a component.
	 * @return (Optional) The value which should be assigned to the outgoing message body.
	 */
	public abstract Object processResponse(long jobId, T response, Map<String, Object> headers) throws WfmProcessingException;

	/**
	 * Copies the JOB_ID (from WfmProcessor), CORRELATION_ID, and SPLIT_SIZE headers to the output exchange and then calls processResponse.
	 */
	@Override
	public final void wfmProcess(Exchange exchange) throws WfmProcessingException {
		assert exchange.getIn().getBody() != null : "The message body must not be null.";
		assert clazz != null : "The clazz member must not be null.";
		assert clazz.isAssignableFrom(exchange.getIn().getBody().getClass()) : String.format("The message body must be convertible to '%s'.", clazz.getSimpleName());

		long jobId = exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class); // previously assert'd to not be null
		exchange.getOut().getHeaders().put(MpfHeaders.CORRELATION_ID, exchange.getIn().getHeader(MpfHeaders.CORRELATION_ID));
		exchange.getOut().getHeaders().put(MpfHeaders.SPLIT_SIZE, exchange.getIn().getHeader(MpfHeaders.SPLIT_SIZE));
		exchange.getOut().getHeaders().put(MpfHeaders.JMS_PRIORITY, exchange.getIn().getHeader(MpfHeaders.JMS_PRIORITY));

		if(!redis.containsJob(jobId)) {
			// No such job. Repackage the response and send it to the unsolicited responses queue for future analysis.
			log.warn("[Job {}|*|*] A job with this ID is not known to the system. This message will be ignored.", jobId);
			exchange.getIn().setHeader(MpfHeaders.UNSOLICITED, Boolean.TRUE.toString());
			exchange.getOut().setHeader(MpfHeaders.UNSOLICITED, Boolean.TRUE.toString());
			exchange.getOut().setBody(((T)(exchange.getIn().getBody())).toByteArray());
		} else {
			Object newBody = processResponse(jobId, exchange.getIn().getBody(clazz), exchange.getIn().getHeaders());
			if (newBody != null) {
				exchange.getOut().setBody(newBody);
			}
		}
	}
}
