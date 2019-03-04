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

package org.mitre.mpf.wfm.camel;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This predicate matches when the target exchange's incoming message's {@link MpfHeaders#SPLIT_COMPLETED} header
 * has a value of {@link java.lang.Boolean#TRUE}.
 *
 * Note: By default, this predicate will not assign a value to the target exchange's outgoing message's body.
 */
public class SplitCompletedPredicate implements Predicate {
	private static final Logger log = LoggerFactory.getLogger(SplitCompletedPredicate.class);

	private boolean copyBody;
	public boolean isCopyBody() { return copyBody; }

	/** Alias for {@link SplitCompletedPredicate#SplitCompletedPredicate(boolean) SplitCompletedPredicate(false)}. */
	public SplitCompletedPredicate() { this(false); }

	/** Creates an instance of the predicate which will copy the body of the last received exchange to the target exchange's outgoing message's body when the input parameter is {@literal true}. */
	public SplitCompletedPredicate(boolean copyBody) { this.copyBody = copyBody; }

	@Override
	public boolean matches(Exchange exchange) {
		log.debug("[Split Completed Predicate for {}] Split completed? {}", exchange.getIn().getHeader(MpfHeaders.CORRELATION_ID), exchange.getOut().getHeader(MpfHeaders.SPLIT_COMPLETED) == Boolean.TRUE);
		if(copyBody) {
			exchange.getOut().setBody(exchange.getIn().getBody());
		}
		return exchange.getOut().getHeader(MpfHeaders.SPLIT_COMPLETED) == Boolean.TRUE;
	}
}
