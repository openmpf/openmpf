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
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultMessage;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.RedisImpl;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * This class implements much of the common functionality associated with Camel splitters. Specifically, instances
 * of this class can assume that:
 * <ul>
 *     <li>Key headers (JOB_ID, SPLIT_SIZE, CORRELATION_ID, et.c) are always associated with any messages produced by the split.</li>
 *     <li>Splits which do not produce any messages will have an EMPTY_SPLIT header value set to TRUE.</li>
 * </ul>
 */
public abstract class WfmSplitter implements WfmSplitterInterface {

	private static final Logger log = LoggerFactory.getLogger(WfmSplitter.class);

	@Autowired
	@Qualifier(RedisImpl.REF)
	private Redis redis;

	/**
	 * In an implementing class, returns the list of work units which are to be performed.
	 *
	 * @param exchange The originating exchange for this split.
	 * @return A collection of work units to be performed.
	 * @throws Exception
	 */
	public abstract List<Message> wfmSplit(Exchange exchange) throws Exception;
	public abstract String getSplitterName();

	public final List<Message> split(Exchange exchange) throws Exception {
		// Assume that the Job ID has been provided as a Long.
		assert exchange.getIn().getHeaders().containsKey(MpfHeaders.JOB_ID) : String.format("The '%s' header must be provided.", MpfHeaders.JOB_ID);
		assert exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class) != null : String.format("The '%s' header (value=%s) must be provided and convertible to Long.", MpfHeaders.JOB_ID, exchange.getIn().getHeader(MpfHeaders.JOB_ID));

		// Get the Job ID from the headers.
		Long jobId = exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class);

		// Create a correlation id to associate with all messages produced by this split.
		String correlationId = String.format("%d:%s", jobId, UUID.randomUUID().toString());

		// Declare a collection to hold all of the messages produced by the split.
		List<Message> messages = null;
		boolean failed = false;

		try {
			messages = wfmSplit(exchange);
		} catch (Exception exception) {
			// The split operation failed. The job cannot continue.
			// Print out the stack trace since no details will be reported in the JSON output.
			log.error("Failed to complete the split operation for Job {} due to an exception.", jobId, exception);
			redis.setJobStatus(jobId, BatchJobStatusType.ERROR);
			failed = true;
		}

		// Initialize the collection if it does not already have a value.
		messages = (messages != null) ? messages : new ArrayList<Message>(0);

		// Were any messages produced from the split?
		boolean emptySplit = messages.size() == 0;

		if (emptySplit) {
			// No messages were produced. Unless a dummy message is produced, the workflow will hang.
			Message defaultMessage = new DefaultMessage();
			defaultMessage.getHeaders().put(MpfHeaders.EMPTY_SPLIT, Boolean.TRUE);
			defaultMessage.getHeaders().put(MpfHeaders.JMS_PRIORITY, exchange.getIn().getHeader(MpfHeaders.JMS_PRIORITY));
			messages.add(defaultMessage);
		}

		for (Message message : messages) {
			message.getHeaders().put(MpfHeaders.SPLIT_SIZE, messages.size());
			message.getHeaders().put(MpfHeaders.JOB_ID, jobId);
			message.getHeaders().put(MpfHeaders.JMS_PRIORITY, exchange.getIn().getHeader(MpfHeaders.JMS_PRIORITY));
			message.getHeaders().put(MpfHeaders.CORRELATION_ID, correlationId);
		}

		log.info("[Job {}|*|*] Produced {} work units with correlation id '{}' (error = {}).", jobId, emptySplit ? 0 : messages.size(), correlationId, failed);
		log.info("WfmSplitter class: {}|{}", this.getSplitterName(), this.getClass().getName());
		return messages;
	}
}
