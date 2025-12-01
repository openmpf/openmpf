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

package org.mitre.mpf.wfm.camel.operations;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.Collection;

import javax.inject.Inject;

import org.apache.camel.Exchange;
import org.mitre.mpf.rest.api.MediaSelectorType;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.camel.WfmProcessor;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.TrackCache;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.MediaSelectorsDuplicatePolicy;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.service.CsvColSelectorService;
import org.mitre.mpf.wfm.service.JsonPathService;
import org.mitre.mpf.wfm.service.StorageException;
import org.mitre.mpf.wfm.service.StorageService;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.JobPart;
import org.mitre.mpf.wfm.util.JobPartsIter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component(MediaSelectorsOutputFileProcessorImpl.REF)
public class MediaSelectorsOutputFileProcessorImpl
        extends WfmProcessor implements MediaSelectorsOutputFileProcessor {

    public static final String REF = "mediaSelectorsOutputFileProcessor";

    private static final Logger LOG = LoggerFactory.getLogger(
            MediaSelectorsOutputFileProcessorImpl.class);

    private final InProgressBatchJobsService _inProgressJobs;

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;

    private final StorageService _storageService;

    private final JsonPathService _jsonPathService;

    private final CsvColSelectorService _csvColSelectorService;

    @Inject
    MediaSelectorsOutputFileProcessorImpl(
            InProgressBatchJobsService inProgressJobs,
            AggregateJobPropertiesUtil aggregateJobPropertiesUtil,
            StorageService storageService,
            JsonPathService jsonPathService,
            CsvColSelectorService csvColSelectorService) {
        _inProgressJobs = inProgressJobs;
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
        _storageService = storageService;
        _jsonPathService = jsonPathService;
        _csvColSelectorService = csvColSelectorService;
    }

    @Override
    public void wfmProcess(Exchange exchange) {
        exchange.getOut().setBody(exchange.getIn().getBody());
        var trackCache = exchange.getIn().getBody(TrackCache.class);
        try {
            var job = _inProgressJobs.getJob(trackCache.getJobId());
            JobPartsIter.taskStream(job, trackCache.getTaskIndex())
                .filter(jp -> isOutputAction(jp))
                .forEach(jp -> createUpdatedOutputDocument(jp, trackCache.getTracks(jp)));
        }
        catch (Exception e) {
            var msg = "Failed to create document with updated selections due to: " + e;
            LOG.error(msg, e);
            _inProgressJobs.addJobError(trackCache.getJobId(), IssueCodes.OTHER, msg);
        }
    }


    private static boolean isOutputAction(JobPart jobPart) {
        if (jobPart.media().getMediaSelectors().isEmpty()) {
            return false;
        }
        return jobPart.media()
                .getMediaSelectorsOutputAction()
                .map(jobPart.action().name()::equals)
                .orElseGet(() -> jobPart.job().getPipelineElements().getAllActions().size() == 1);
    }


    private void createUpdatedOutputDocument(JobPart jobPart, Collection<Track> tracks) {
        var mapper = getMapper(jobPart, tracks);
        createOutputDocument(jobPart.id(), jobPart.media().getId(), () -> {
            var selectorType = jobPart.media().getMediaSelectors().get(0).type();
            return switch (selectorType) {
                case JSON_PATH -> createJsonOutputDocument(jobPart, mapper);
                case CSV_COLS -> createCsvOutputDocument(jobPart, mapper);
                // No default case so that compilation fails if a new enum value is added and a new
                // case is not added here.
            };
        });
    }

    @Override
    public void createNoMatchOutputDocument(long jobId, Media media) {
        createOutputDocument(jobId, media.getId(), () -> {
            var job = _inProgressJobs.getJob(jobId);
            var selectorType = media.getMediaSelectors().get(0).type();
            return _storageService.storeMediaSelectorsOutput(
                    job, media,
                    selectorType,
                    out -> Files.copy(media.getProcessingPath(), out));
        });
    }


    @FunctionalInterface
    private static interface OutputFileCreationStrategy {
        public URI create() throws IOException, StorageException;
    }

    private void createOutputDocument(
                long jobId, long mediaId, OutputFileCreationStrategy fileCreator)  {
        try {
            var uri = fileCreator.create();
            _inProgressJobs.setMediaSelectorsOutputUri(jobId, mediaId, uri);
        }
        catch (StorageException | WfmProcessingException | IOException e) {
            var msg = "Failed to create document with updated selections due to: " + e;
            LOG.error(msg, e);
            _inProgressJobs.addError(jobId, mediaId, IssueCodes.OTHER, msg);
        }
    }


    private URI createJsonOutputDocument(JobPart jobPart, MediaSelectorsMapper mapper)
            throws StorageException, IOException {
        var jsonPathEval = _jsonPathService.load(jobPart.media().getProcessingPath());
        for (var entry : mapper) {
            jsonPathEval.replaceStrings(entry.selector().expression(), entry);
        }
        return _storageService.storeMediaSelectorsOutput(
                jobPart.job(), jobPart.media(),
                MediaSelectorType.JSON_PATH,
                jsonPathEval::writeTo);
    }


    private URI createCsvOutputDocument(JobPart jobPart, MediaSelectorsMapper mapper)
            throws IOException, StorageException {
        return _storageService.storeMediaSelectorsOutput(
                jobPart.job(),
                jobPart.media(),
                MediaSelectorType.CSV_COLS,
                os -> _csvColSelectorService.createOutputDocument(
                        jobPart.job(), jobPart.media(), jobPart.action(), os, mapper));
    }


    private MediaSelectorsMapper getMapper(JobPart jobPart, Collection<Track> tracks) {
        var delimeter = _aggregateJobPropertiesUtil.getValue(
                MpfConstants.MEDIA_SELECTORS_DELIMETER, jobPart);
        return new MediaSelectorsMapper(jobPart, tracks, delimeter, getDuplicatePolicy(jobPart));
    }



    private MediaSelectorsDuplicatePolicy getDuplicatePolicy(JobPart jobPart) {
        var duplicatePolicyName = _aggregateJobPropertiesUtil.getValue(
                MpfConstants.MEDIA_SELECTORS_DUPLICATE_POLICY, jobPart);
        if (duplicatePolicyName == null || duplicatePolicyName.isBlank()) {
            return MediaSelectorsDuplicatePolicy.LONGEST;
        }
        return MediaSelectorsDuplicatePolicy.valueOf(duplicatePolicyName.toUpperCase());
    }
}
