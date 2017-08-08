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

import org.apache.camel.Exchange;
import org.mitre.mpf.mvc.controller.AtmosphereController;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.entities.transients.TransientJob;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * A count-based aggregator naively counts the number of exchanges which pass through it. When the count
 * exceeds the size of the split (as measured by the {@link MpfHeaders#SPLIT_SIZE} header), the
 * aggregator sets the {@link MpfHeaders#SPLIT_COMPLETED} header to {@link Boolean#TRUE}. The body of the
 * out exchange is left blank.
 *
 * @param <T> The type of object contained in each in exchange received.
 */
public class CountBasedWfmAggregator<T> extends DefaultWfmAggregator<T> {
	private static final Logger log = LoggerFactory.getLogger(CountBasedWfmAggregator.class);

	public CountBasedWfmAggregator(Class<T> bodyClass) {
		super(bodyClass);
	}

	@Override
	public final Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
		int aggregateCount = 1, splitSize = newExchange.getIn().getHeader(MpfHeaders.SPLIT_SIZE, Integer.class);

		if(oldExchange == null) {
			// The first time through, the output exchange's AGGREGATED_COUNT will be 1.
			newExchange.getOut().setHeader(MpfHeaders.AGGREGATED_COUNT, aggregateCount);
		} else {
			// After the first time through, increment the output exchange's AGGREGATED_COUNT.
			aggregateCount = oldExchange.getIn().getHeader(MpfHeaders.AGGREGATED_COUNT, Integer.class) + 1;
			newExchange.getOut().getHeaders().put(MpfHeaders.AGGREGATED_COUNT, aggregateCount);
		}

		// Regardless of whether or not this is the first time through the aggregator, copy the JOB_ID, JMS_PRIORITY, CORRELATION_ID, and SPLIT_SIZE
		// ids to the output exchange. Without these, we'll never know if the job has completed!
		newExchange.getOut().getHeaders().put(MpfHeaders.JOB_ID, newExchange.getIn().getHeader(MpfHeaders.JOB_ID));
		newExchange.getOut().getHeaders().put(MpfHeaders.JMS_PRIORITY, newExchange.getIn().getHeader(MpfHeaders.JMS_PRIORITY));
		newExchange.getOut().getHeaders().put(MpfHeaders.CORRELATION_ID, newExchange.getIn().getHeader(MpfHeaders.CORRELATION_ID));
		newExchange.getOut().getHeaders().put(MpfHeaders.SPLIT_SIZE, splitSize);

		// Copy the body of the incoming message into the outgoing message.
		newExchange.getOut().setBody(newExchange.getIn().getBody());

		if(aggregateCount == splitSize) {
			// When the AGGREGATED_COUNT matches the SPLIT_SIZE, we set the SPLIT_COMPLETED header.
			newExchange.getOut().setHeader(MpfHeaders.SPLIT_COMPLETED, Boolean.TRUE);
		}

		try {
			onResponse(newExchange);
		} catch (Exception exception) {
			log.warn("[Job {}:*:*] Exception encountered while executing onResponse.",
					newExchange.getOut().getHeader(MpfHeaders.JOB_ID),
					exception);
		}
		return newExchange;
	}
}

