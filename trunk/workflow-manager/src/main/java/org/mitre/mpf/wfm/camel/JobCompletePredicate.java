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
 * This predicate will match when the target exchange's incoming message's {@link org.mitre.mpf.wfm.enums.MpfHeaders#JOB_COMPLETE}
 * header has a value of {@link Boolean#TRUE}.
 */
public class JobCompletePredicate implements Predicate {
	private static final Logger log = LoggerFactory.getLogger(JobCompletePredicate.class);

	@Override
	public boolean matches(Exchange exchange) {
		boolean isComplete = Boolean.TRUE.equals(exchange.getIn().getHeader(MpfHeaders.JOB_COMPLETE, Boolean.class));
		log.debug("[Job {}|*|*] Job Complete Predicate: {}", exchange.getIn().getHeader(MpfHeaders.JOB_ID), isComplete);
		return isComplete;
	}
}
