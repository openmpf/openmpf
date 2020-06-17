/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2020 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2020 The MITRE Corporation                                       *
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
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component(StringCountBasedWfmAggregator.REF)
public class StringCountBasedWfmAggregator extends CountBasedWfmAggregator<String> {
	private static final Logger log = LoggerFactory.getLogger(StringCountBasedWfmAggregator.class);
	public static final String REF = "stringCountBasedWfmAggregator";

	public StringCountBasedWfmAggregator() {
		super(String.class);
	}



	public void onResponse(Exchange newExchange) {
		log.debug("[Job {}:*:*] Received response {}/{}.",
				newExchange.getOut().getHeader(MpfHeaders.JOB_ID),
				newExchange.getOut().getHeader(MpfHeaders.AGGREGATED_COUNT),
				newExchange.getOut().getHeader(MpfHeaders.SPLIT_SIZE));
	}
}
