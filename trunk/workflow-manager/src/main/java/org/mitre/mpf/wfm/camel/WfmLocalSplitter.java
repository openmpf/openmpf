/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.javasimon.aop.Monitored;
import org.mitre.mpf.mvc.util.MdcUtil;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Monitored
public abstract class WfmLocalSplitter {

    private static final Logger LOG = LoggerFactory.getLogger(WfmLocalSplitter.class);

    private final InProgressBatchJobsService _inProgressJobs;

    protected WfmLocalSplitter(InProgressBatchJobsService inProgressJobs) {
        _inProgressJobs = inProgressJobs;
    }

    protected abstract List<Message> wfmSplit(Exchange exchange);

    protected abstract String getSplitterName();


    public List<Message> split(Exchange exchange) {
        long jobId = exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class);
        return MdcUtil.job(jobId, () -> doSplit(jobId, exchange));
    }

    private List<Message> doSplit(long jobId, Exchange exchange) {
        try {
            var messages = wfmSplit(exchange);
            int priority = exchange.getIn().getHeader(MpfHeaders.JMS_PRIORITY, 4, Integer.class);
            for (var message : messages) {
                message.setHeader(MpfHeaders.JMS_PRIORITY, priority);
                message.setHeader(MpfHeaders.JOB_ID, jobId);
            }
            LOG.info("{} produced {} work units.", getSplitterName(), messages.size());
            return messages;
        }
        catch (Exception e) {
            var errorMsg = String.format(
                "Failed to complete the split operation for Job %s due to : %s",
                jobId, e);
            LOG.error(errorMsg, e);
            _inProgressJobs.addFatalError(jobId, IssueCodes.OTHER, errorMsg);
            exchange.setProperty(MpfHeaders.SPLITTING_ERROR, true);
            return List.of();
        }
    }
}
