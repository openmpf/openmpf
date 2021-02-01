/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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
import org.mitre.mpf.wfm.enums.MpfHeaders;

/**
 * The Workflow Manager relies on certain headers. When a message passes through a processor,
 * the message on the outgoing side of the processor will not, by default, have any of the headers
 * that the incoming message had.
 *
 * The purpose of this class is to copy the essential headers from the incoming message
 * to the outgoing message so that subclasses are not forced to do it. In doing so, this class
 * overrides and seals the process method. Any subclass must instead override the wfmProcess method.
 */
public abstract class WfmProcessor implements WfmProcessorInterface {

	@Override
	public abstract void wfmProcess(Exchange exchange) throws WfmProcessingException;

	@Override
	public void process(Exchange exchange) {
		// Assertions
		assert exchange.getIn().getHeaders().containsKey(MpfHeaders.JOB_ID) : String.format("The '%s' header must be set on messages handled by this processor.", MpfHeaders.JOB_ID);
		assert exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class) != null : String.format("The '%s' header (value=%s) header be convertible to Long.", MpfHeaders.JOB_ID, exchange.getIn().getHeader(MpfHeaders.JOB_ID));

		// Copy any essential headers.
		exchange.getOut().getHeaders().put(MpfHeaders.JOB_ID, exchange.getIn().getHeader(MpfHeaders.JOB_ID));
		exchange.getOut().getHeaders().put(MpfHeaders.JMS_PRIORITY, exchange.getIn().getHeader(MpfHeaders.JMS_PRIORITY));

		// Execute the processor.
		wfmProcess(exchange);
	}


	@Override
	public String toString() {
		return this.getClass().getSimpleName();
	}
}
