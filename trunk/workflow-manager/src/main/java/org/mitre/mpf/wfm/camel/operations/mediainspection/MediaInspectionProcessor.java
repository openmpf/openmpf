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

package org.mitre.mpf.wfm.camel.operations.mediainspection;

import org.apache.camel.Exchange;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.camel.WfmProcessor;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

@Component(MediaInspectionProcessor.REF)
public class MediaInspectionProcessor extends WfmProcessor {
    public static final String REF = "mediaInspectionProcessor";

    private static final Logger LOG = LoggerFactory.getLogger(MediaInspectionProcessor.class);

    private final MediaInspectionHelper _mediaInspectionHelper;

    private final InProgressBatchJobsService _inProgressJobs;

    @Inject
    public MediaInspectionProcessor(
            InProgressBatchJobsService inProgressJobs,
            MediaInspectionHelper mediaInspectionHelper) {
        _inProgressJobs = inProgressJobs;
        _mediaInspectionHelper = mediaInspectionHelper;
    }

    @Override
    public void wfmProcess(Exchange exchange) throws WfmProcessingException {
        long jobId = exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class);
        long mediaId = exchange.getIn().getHeader(MpfHeaders.MEDIA_ID, Long.class);

        Media media = _inProgressJobs.getJob(jobId).getMedia(mediaId);
        _mediaInspectionHelper.inspectMedia(media, jobId);
        setHeaders(exchange, jobId, mediaId);

        if (media.isFailed()) {
            _inProgressJobs.setJobStatus(jobId, BatchJobStatusType.ERROR);
        }
    }

    private static void setHeaders(Exchange exchange, long jobId, long mediaId) {
        // Copy these headers to the output exchange.
        exchange.getOut().setHeader(MpfHeaders.CORRELATION_ID, exchange.getIn().getHeader(MpfHeaders.CORRELATION_ID));
        exchange.getOut().setHeader(MpfHeaders.SPLIT_SIZE, exchange.getIn().getHeader(MpfHeaders.SPLIT_SIZE));
        exchange.getOut().setHeader(MpfHeaders.JMS_PRIORITY, exchange.getIn().getHeader(MpfHeaders.JMS_PRIORITY));
        exchange.getOut().setHeader(MpfHeaders.JOB_ID, jobId);
        exchange.getOut().setHeader(MpfHeaders.MEDIA_ID, mediaId);
    }
}
