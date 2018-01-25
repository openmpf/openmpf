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

package org.mitre.mpf.wfm.camel;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.mitre.mpf.wfm.enums.JobStatus;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DefaultJobErrorHandler implements Processor {
	private static final Logger log = LoggerFactory.getLogger(DefaultJobErrorHandler.class);

	@Override
	public void process(Exchange exchange) throws Exception {
		// May be null.
		Long jobId = exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class);

		// May be null.
		Throwable throwable = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);

		if(jobId == null) {
			log.warn("An error was encountered while processing a job, but not enough information has been provided to determine which job produced this error.", throwable);
		} else {
			log.warn("An error was encountered while processing Job #{}. The Job will be marked as {}.", jobId, JobStatus.ERROR, throwable);
		}
	}
}
