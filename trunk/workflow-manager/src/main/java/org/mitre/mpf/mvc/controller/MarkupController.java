/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.rest.api.MarkupPageListModel;
import org.mitre.mpf.rest.api.MarkupResultConvertedModel;
import org.mitre.mpf.rest.api.MarkupResultModel;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.access.JobRequestDao;
import org.mitre.mpf.wfm.data.access.MarkupResultDao;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.data.entities.persistent.MarkupResult;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.service.S3StorageBackend;
import org.mitre.mpf.wfm.service.StorageException;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.UriComponentsBuilder;

import io.swagger.annotations.Api;

@Api(value = "Markup", description = "Access the information of marked up media")
@Controller
@Scope("request")
public class MarkupController {
    private static final Logger log = LoggerFactory.getLogger(MarkupController.class);

    @Autowired
    private MarkupResultDao markupResultDao;

    @Autowired
    private JobRequestDao jobRequestDao;

    @Autowired
    private IoUtils ioUtils;

    @Autowired
    private JsonUtils jsonUtils;

    @Autowired
    private S3StorageBackend s3StorageBackend;

    @Autowired
    private AggregateJobPropertiesUtil aggregateJobPropertiesUtil;

    @Autowired
    private PropertiesUtil propertiesUtil;

    @Autowired
    private InProgressBatchJobsService inProgressJobs;


    @GetMapping("/markup/get-markup-results-filtered")
    @ResponseBody
    public ResponseEntity<Object> getMarkupResultsFiltered(
            @RequestParam("jobId") String jobId,
            @RequestParam("page") int page,
            @RequestParam("pageLen") int pageLen,
            @RequestParam("search") String search) {

        long internalJobId = propertiesUtil.getJobIdFromExportedId(jobId);
        JobRequest jobRequest = jobRequestDao.findById(internalJobId);
        if (jobRequest == null) {
            return ResponseEntity.notFound().build();
        }

        BatchJob job = jsonUtils.deserialize(jobRequest.getJob(), BatchJob.class);

        List<MarkupResult> markupResults = markupResultDao.findByJobId(internalJobId);

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
            if (!StringUtils.isBlank(med.getPersistentUri())) {
                Path path = IoUtils.toLocalPath(med.getPersistentUri()).orElse(null);
                if (path == null || Files.exists(path)) { // if remote media or available local media
                    String downloadUrl = UriComponentsBuilder.fromPath("server/download")
                            .queryParam("sourceUri", med.getPersistentUri())
                            .queryParam("jobId", jobId)
                            .toUriString();
                    model.setSourceDownloadUrl(downloadUrl);
                    model.setSourceFileAvailable(true);
                    med.getType()
                        .or(() -> inProgressJobs.getMediaType(internalJobId, med.getId()))
                        .ifPresent(mt -> model.setSourceMediaType(mt.toString()));
                }
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
                        (markupResult.getSourceUri() != null && markupResult.getSourceUri().toLowerCase().contains(search))) {
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


    @RequestMapping(value = "/markup/download", method = RequestMethod.GET)
    public void getFile(@RequestParam("id") long id,
                        HttpServletResponse response) throws IOException, StorageException {
        MarkupResult markupResult = markupResultDao.findById(id);
        if (markupResult == null) {
            log.error("Markup with id " + id + " download failed. Invalid id.");
            response.setStatus(404);
            response.flushBuffer();
            return;
        }

        Path localPath = IoUtils.toLocalPath(markupResult.getMarkupUri()).orElse(null);
        if (localPath != null) {
            if (!Files.exists(localPath)) {
                log.error("Markup with id " + id + " download failed. Invalid path: " + localPath);
                response.setStatus(404);
                response.flushBuffer();
                return;
            }
            ioUtils.sendBinaryResponse(localPath, response);
            return;
        }

        BatchJob job = Optional.ofNullable(jobRequestDao.findById(markupResult.getJobId()))
                .map(JobRequest::getJob)
                .map(bytes -> jsonUtils.deserialize(bytes, BatchJob.class))
                .orElse(null);
        if (job == null) {
            log.error("Markup with id " + id + " download failed. Invalid job with id " +
                    markupResult.getJobId() + ".");
            response.setStatus(404);
            response.flushBuffer();
            return;
        }

        var media = job.getMedia()
                .stream()
                .filter(m -> m.getId() == markupResult.getMediaId())
                .findAny()
                .orElse(null);
        if (media == null) {
            log.error("Markup with id " + id + " download failed. Invalid media with id " +
                    markupResult.getMediaId() + ".");
            response.setStatus(404);
            response.flushBuffer();
            return;
        }

        Function<String, String> combinedProperties = getProperties(job, media, markupResult);

        if (S3StorageBackend.requiresS3ResultUpload(combinedProperties)) {
            try {
                try (var s3Stream = s3StorageBackend.getFromS3(
                        markupResult.getMarkupUri(), combinedProperties)) {
                    var s3Response = s3Stream.response();
                    IoUtils.sendBinaryResponse(s3Stream, response,
                            s3Response.contentType(), s3Response.contentLength());
                }
                return;
            } catch (StorageException e) {
                log.error("Markup with id " + id + " download failed: " + e.getMessage());
                response.setStatus(500);
                response.flushBuffer();
                return;
            }
        }

        try {
            URL mediaUrl = IoUtils.toUrl(markupResult.getMarkupUri());
            URLConnection urlConnection = mediaUrl.openConnection();
            try (InputStream inputStream = urlConnection.getInputStream()) {
                IoUtils.sendBinaryResponse(inputStream, response, urlConnection.getContentType(),
                        urlConnection.getContentLength());
            }
        } catch (IOException e) {
            log.error("Markup with id " + id + " download failed: " + e.getMessage());
            response.setStatus(500);
            response.flushBuffer();
            return;
        }
    }

    private Function<String, String> getProperties(BatchJob job, Media media, MarkupResult markupResult) {
        var action = job.getPipelineElements().getAction(markupResult.getTaskIndex(), markupResult.getActionIndex());
        return aggregateJobPropertiesUtil.getCombinedProperties(job, media, action);
    }

    private MarkupResultModel convertMarkupResult(MarkupResult markupResult) {
        boolean isImage = false;
        if(markupResult.getMarkupUri() != null) {
            String nonUrlPath = markupResult.getMarkupUri().replace("file:", "");
            String markupContentType = ioUtils.getPathContentType(Paths.get(nonUrlPath));
            isImage = (markupContentType != null && StringUtils.startsWithIgnoreCase(markupContentType, "IMAGE"));
        }

        return new MarkupResultModel(markupResult.getId(), markupResult.getJobId(),
                                     markupResult.getPipeline(), markupResult.getMarkupUri(), isImage);
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

        Path path = IoUtils.toLocalPath(media.getPersistentUri()).orElse(null);
        if (path == null || Files.exists(path))  { // if remote media or available local media
            sourceDownloadUrl = UriComponentsBuilder
                    .fromPath("server/download")
                    .queryParam("sourceUri", media.getPersistentUri())
                    .queryParam("jobId", markupResult.getJobId())
                    .toUriString();
            sourceFileAvailable = true;
            sourceMediaType = media.getType()
                    .map(Enum::toString)
                    .orElse("");
        }

        return new MarkupResultConvertedModel(
                markupResult.getId(),
                propertiesUtil.getExportedJobId(markupResult.getJobId()),
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
}
