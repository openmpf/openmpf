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

package org.mitre.mpf.mvc.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.HttpGet;
import org.mitre.mpf.rest.api.MarkupPageListModel;
import org.mitre.mpf.rest.api.MarkupResultConvertedModel;
import org.mitre.mpf.rest.api.MediaUri;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.access.JobRequestDao;
import org.mitre.mpf.wfm.data.access.MarkupResultDao;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.data.entities.persistent.MarkupResult;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.service.S3StorageBackend;
import org.mitre.mpf.wfm.service.StorageException;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.AuditEventLogger;
import org.mitre.mpf.wfm.util.ForwardHttpResponseUtil;
import org.mitre.mpf.wfm.util.HttpClientUtils;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.PathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import io.swagger.annotations.Api;

@Api(value = "Markup", description = "Access the information of marked up media")
@RestController
@Scope("request")
public class MarkupController {
    private static final Logger log = LoggerFactory.getLogger(MarkupController.class);

    private final MarkupResultDao _markupResultDao;

    private final JobRequestDao _jobRequestDao;

    private final JsonUtils _jsonUtils;

    private final S3StorageBackend _s3StorageBackend;

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;

    private final PropertiesUtil _propertiesUtil;

    private final InProgressBatchJobsService _inProgressJobs;

    private final HttpClientUtils _httpClientUtils;

    private final AuditEventLogger _auditEventLogger;

    @Inject
    MarkupController(
            MarkupResultDao markupResultDao,
            JobRequestDao jobRequestDao,
            JsonUtils jsonUtils,
            S3StorageBackend s3StorageBackend,
            AggregateJobPropertiesUtil aggregateJobPropertiesUtil,
            PropertiesUtil propertiesUtil,
            InProgressBatchJobsService inProgressJobs,
            HttpClientUtils httpClientUtils,
            AuditEventLogger auditEventLogger) {
        _markupResultDao = markupResultDao;
        _jobRequestDao = jobRequestDao;
        _jsonUtils = jsonUtils;
        _s3StorageBackend = s3StorageBackend;
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
        _propertiesUtil = propertiesUtil;
        _inProgressJobs = inProgressJobs;
        _httpClientUtils = httpClientUtils;
        _auditEventLogger = auditEventLogger;
    }

    @GetMapping("/markup/get-markup-results-filtered")
    public ResponseEntity<Object> getMarkupResultsFiltered(
            @RequestParam("jobId") String jobId,
            @RequestParam("page") int page,
            @RequestParam("pageLen") int pageLen,
            @RequestParam("search") String search) {

        long internalJobId = _propertiesUtil.getJobIdFromExportedId(jobId);
        JobRequest jobRequest = _jobRequestDao.findById(internalJobId);
        if (jobRequest == null) {
            _auditEventLogger.readEvent()
                .withSecurityTag()
                .error("Failed to retrieve markup results: Invalid job ID %s", jobId);
            return ResponseEntity.notFound().build();
        }

        BatchJob job = _jsonUtils.deserialize(jobRequest.getJob(), BatchJob.class);

        List<MarkupResult> markupResults = _markupResultDao.findByJobId(internalJobId);

        //convert markup objects
        Map<Long, MarkupResultConvertedModel> markupResultModels = new HashMap<>();
        for (MarkupResult markupResult : markupResults) {
            MarkupResultConvertedModel model =
                    convertMarkupResultWithContentType(markupResult, job.getMedia(markupResult.getMediaId()));
            markupResultModels.put(model.getMediaId(), model);
        }

        //add media that doesn't have markup
        for (Media med : job.getMedia()) {
            if (markupResultModels.containsKey(med.getId())) {
                continue;
            }

            MarkupResultConvertedModel model = new MarkupResultConvertedModel();
            model.setJobId(jobId);
            model.setMediaId(med.getId());
            model.setParentMediaId(med.getParentId());
            model.setPipeline(job.getPipelineElements().getName());
            model.setSourceUri(med.getPersistentUri());
            model.setSourceFileAvailable(false);

            if (isRemoteOrLocallyAvailable(med.getPersistentUri())) {
                getSourceDownloadUrl(internalJobId, med)
                    .ifPresent(model::setSourceDownloadUrl);
                model.setSourceFileAvailable(true);
                med.getType()
                    .or(() -> _inProgressJobs.getMediaType(internalJobId, med.getId()))
                    .ifPresent(mt -> model.setSourceMediaType(mt.toString()));
            }
            markupResultModels.put(med.getId(), model);
        }

        //handle search
        List<MarkupResultConvertedModel> markupResultModelsFiltered = new ArrayList<>();
        for (MarkupResultConvertedModel markupResult : markupResultModels.values()) {
            if (search != null && search.length() > 0) {
                search = search.toLowerCase();
                if ((markupResult.getJobId() + "").contains(search) ||
                        (markupResult.getParentMediaId() + "").contains(search) ||
                        (markupResult.getMediaId() + "").contains(search) ||
                        (markupResult.getMarkupUri() != null && markupResult.getMarkupUri().toLowerCase().contains(search)) ||
                        (markupResult.getSourceUri() != null && markupResult.getSourceUri().fullString().toLowerCase().contains(search))) {
                    markupResultModelsFiltered.add(markupResult);
                }
            } else {
                markupResultModelsFiltered.add(markupResult);
            }
        }

                //handle paging
        List<MarkupResultConvertedModel> markupResultModelsFinal = markupResultModelsFiltered
            .stream()
            .sorted((m1, m2) -> { // group by parent media id first, then by media id
                boolean isParent1 = m1.getParentMediaId() == -1;
                boolean isParent2 = m2.getParentMediaId() == -1;
                long m1Group = isParent1 ? m1.getMediaId() : m1.getParentMediaId();
                long m2Group = isParent2 ? m2.getMediaId() : m2.getParentMediaId();
                if (m1Group != m2Group) {
                    return Long.compare(m1Group, m2Group);
                }
                if (isParent1) {
                    return -1;
                }
                if (isParent2) {
                    return 1;
                }
                return Long.compare(m1.getMediaId(), m2.getMediaId());
            })
            .skip((page - 1) * pageLen)
            .limit(pageLen)
            .collect(Collectors.toList());

        return ResponseEntity.ok(new MarkupPageListModel(
                markupResultModelsFinal,
                markupResultModelsFiltered.size(),
                markupResultModels.size()));
    }


    @GetMapping("/markup/download")
    public Object getFile(@RequestParam("id") long id) throws StorageException, IOException {
        var markupResult = _markupResultDao.findById(id);
        if (markupResult == null) {
            log.error("Markup with id {} download failed. Invalid id.", id);
            _auditEventLogger.extractEvent()
                .withSecurityTag()
                .error("Markup download failed: Invalid markup ID %s", id);
            return ResponseEntity.notFound().build();
        }

        var localPath = IoUtils.toLocalPath(markupResult.getMarkupUri());
        if (localPath.isPresent()) {
            if (Files.exists(localPath.get())) {
                _auditEventLogger.extractEvent()
                    .withSecurityTag()
                    .allowed("Downloaded markup file: uri=%s", markupResult.getMarkupUri());
                return new PathResource(localPath.get());
            }
            log.error("Markup with id {} download failed. Invalid path: {}", id, localPath);
            _auditEventLogger.extractEvent()
                .withSecurityTag()
                .error("Markup download failed: File not found at path %s for markup ID %s", localPath, id);
            return ResponseEntity.notFound().build();
        }

        var job = Optional.ofNullable(_jobRequestDao.findById(markupResult.getJobId()))
                .map(JobRequest::getJob)
                .map(bytes -> _jsonUtils.deserialize(bytes, BatchJob.class));
        if (job.isEmpty()) {
            log.error(
                    "Markup with id {} download failed. Invalid job with id {}.",
                    id, markupResult.getJobId());
            _auditEventLogger.extractEvent()
                .withSecurityTag()
                .error("Markup download failed: Invalid job ID %s for markup ID %s", markupResult.getJobId(), id);
            return ResponseEntity.notFound().build();
        }

        var media = job.get()
                .getMedia()
                .stream()
                .filter(m -> m.getId() == markupResult.getMediaId())
                .findAny();
        if (media.isEmpty()) {
            log.error(
                    "Markup with id {} download failed. Invalid media with id {}.",
                    id, markupResult.getMediaId());
            _auditEventLogger.extractEvent()
                .withSecurityTag()
                .error("Markup download failed: Invalid media ID %s for markup ID %s", markupResult.getMediaId(), id);
            return ResponseEntity.notFound().build();
        }

        var combinedProperties = getProperties(job.get(), media.get(), markupResult);
        if (S3StorageBackend.requiresS3ResultUpload(combinedProperties)) {
            try {
                var s3Stream = _s3StorageBackend.getFromS3(
                        markupResult.getMarkupUri(), combinedProperties);
                _auditEventLogger.extractEvent()
                    .withSecurityTag()
                    .allowed("Downloaded markup file: uri=%s", markupResult.getMarkupUri());
                return ForwardHttpResponseUtil.createResponseEntity(s3Stream);
            }
            catch (StorageException e) {
                log.error("Markup with id " + id + " download failed: " + e.getMessage(), e);
                _auditEventLogger.extractEvent()
                    .withSecurityTag()
                    .error("Markup download failed: S3 error for markup ID %s : %s", id, e.getMessage());
                return ResponseEntity.internalServerError().build();
            }
        }

        var request = new HttpGet(markupResult.getMarkupUri());
        var markupResponse = _httpClientUtils.executeRequestSync(request, 0);
        _auditEventLogger.extractEvent()
            .withSecurityTag()
            .allowed("Downloaded markup file: uri=%s", markupResult.getMarkupUri());
        return ForwardHttpResponseUtil.createResponseEntity(markupResponse);
    }


    private UnaryOperator<String> getProperties(BatchJob job, Media media, MarkupResult markupResult) {
        var action = job.getPipelineElements().getAction(markupResult.getTaskIndex(), markupResult.getActionIndex());
        return _aggregateJobPropertiesUtil.getCombinedProperties(job, media, action);
    }


    private MarkupResultConvertedModel convertMarkupResultWithContentType(MarkupResult markupResult, Media media) {
        String markupMediaType = "";
        String markupDownloadUrl = "";
        String sourceMediaType = "";
        String sourceDownloadUrl ="";
        boolean markupFileAvailable = false;
        boolean sourceFileAvailable = false;

        if (!StringUtils.isBlank(markupResult.getMarkupUri())) {
            Path path = IoUtils.toLocalPath(markupResult.getMarkupUri()).orElse(null);
            if (path == null || Files.exists(path)) { // if remote markup or available local markup
                markupDownloadUrl = "markup/download?id=" + markupResult.getId();
                markupFileAvailable = true;
                // markup type is the same as media type
                markupMediaType = media.getType()
                        .map(Enum::toString)
                        .orElse("");
            }
        }

        if (isRemoteOrLocallyAvailable(media.getPersistentUri()))  {
            sourceDownloadUrl = getSourceDownloadUrl(markupResult.getJobId(), media)
                    .orElse("");
            sourceFileAvailable = true;
            sourceMediaType = media.getType()
                    .map(Enum::toString)
                    .orElse("");
        }

        return new MarkupResultConvertedModel(
                markupResult.getId(),
                _propertiesUtil.getExportedJobId(markupResult.getJobId()),
                markupResult.getMediaId(),
                media.getParentId(),
                markupResult.getPipeline(),
                markupResult.getMarkupUri(),
                markupMediaType,
                markupDownloadUrl,
                markupFileAvailable,
                media.getPersistentUri(),
                sourceMediaType,
                sourceDownloadUrl,
                sourceFileAvailable);
    }


    private static Optional<String> getSourceDownloadUrl(long jobId, Media media) {
        if (media.getUriScheme() == UriScheme.DATA) {
            return Optional.empty();
        }
        var downloadUrl = UriComponentsBuilder.fromPath("server/download")
                .queryParam("sourceUri", media.getPersistentUri())
                .queryParam("jobId", jobId)
                .toUriString();
        return Optional.of(downloadUrl);
    }

    private static boolean isRemoteOrLocallyAvailable(MediaUri uri) {
        if (UriScheme.get(uri) == UriScheme.FILE) {
            return Files.exists(Path.of(uri.get()));
        }
        else {
            return true;
        }
    }
}
