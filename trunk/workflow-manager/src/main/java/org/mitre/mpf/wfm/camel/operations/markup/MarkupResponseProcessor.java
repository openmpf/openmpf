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

package org.mitre.mpf.wfm.camel.operations.markup;

import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.buffers.Markup;
import org.mitre.mpf.wfm.camel.ResponseProcessor;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionResponseProcessor;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.access.MarkupResultDao;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.MarkupResult;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.IssueSources;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.Map;

@Component(MarkupResponseProcessor.REF)
public class MarkupResponseProcessor extends ResponseProcessor<Markup.MarkupResponse> {
    public static final String REF = "markupResponseProcessor";
    private static final Logger log = LoggerFactory.getLogger(DetectionResponseProcessor.class);

    private final InProgressBatchJobsService _inProgressJobs;
    private final MarkupResultDao _markupResultDao;
    private final StorageService _storageService;

    @Inject
    public MarkupResponseProcessor(InProgressBatchJobsService inProgressJobs,
                                   MarkupResultDao markupResultDao,
                                   StorageService storageService) {
        super(inProgressJobs, Markup.MarkupResponse.class);
        _inProgressJobs = inProgressJobs;
        _markupResultDao = markupResultDao;
        _storageService = storageService;
    }

    @Override
    public Object processResponse(long jobId, Markup.MarkupResponse markupResponse, Map<String, Object> headers) throws WfmProcessingException {
        log.debug("Received response for Media {} (Index = {}). Error? {}",
                  markupResponse.getMediaId(), markupResponse.getMediaIndex(),
                  markupResponse.getHasError() ? markupResponse.getErrorMessage() : "None.");
        MarkupResult markupResult = new MarkupResult();
        markupResult.setTaskIndex(markupResponse.getTaskIndex());
        markupResult.setActionIndex(markupResponse.getActionIndex());
        markupResult.setMediaId(markupResponse.getMediaId());
        markupResult.setMediaIndex(markupResponse.getMediaIndex());
        markupResult.setJobId(jobId);
        markupResult.setMarkupUri(markupResponse.getOutputFileUri());

        if (markupResponse.getHasError()) {
            if (markupResponse.hasErrorMessage()) {
                if (markupResponse.getErrorMessage().equals(MpfConstants.REQUEST_CANCELLED)) {
                    markupResult.setMarkupStatus(markupResult.getMarkupStatus().onCancel());
                    markupResult.setMessage("Successfully cancelled.");
                } else {
                    markupResult.setMarkupStatus(markupResult.getMarkupStatus().onError());
                    markupResult.setMessage(markupResponse.getErrorMessage());
                }
            } else {
                markupResult.setMarkupStatus(markupResult.getMarkupStatus().onError());
                markupResult.setMessage("FAILED");
            }
        } else {
            markupResult.setMarkupStatus(markupResult.getMarkupStatus().onComplete());
        }

        BatchJob job = _inProgressJobs.getJob(jobId);
        Media media = job.getMedia(markupResponse.getMediaId());
        markupResult.setPipeline(job.getPipelineElements().getName());
        markupResult.setSourceUri(media.getUri());

        switch (markupResult.getMarkupStatus()) {
            case FAILED:
                _inProgressJobs.addError(jobId, markupResult.getMediaId(), IssueCodes.MARKUP,
                        markupResult.getMessage(), IssueSources.MARKUP);
                break;
            case CANCELLED:
                _inProgressJobs.handleMarkupCancellation(jobId, markupResult.getMediaId());
                break;
            case COMPLETE_WITH_WARNING:
                var warningMessage = markupResponse.hasErrorMessage()
                        ? markupResponse.getErrorMessage()
                        : "COMPLETE_WITH_WARNING";
                _inProgressJobs.addWarning(jobId, markupResult.getMediaId(), IssueCodes.MARKUP,
                        warningMessage, IssueSources.MARKUP);
        }

        if (!markupResponse.getHasError()) {
            _storageService.store(markupResult); // may change markup status
        }
        _markupResultDao.persist(markupResult);

        job.setProcessedAction(markupResponse.getMediaId(), markupResponse.getTaskIndex(), markupResponse.getActionIndex());

        return null;
    }
}
