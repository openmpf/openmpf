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

package org.mitre.mpf.mvc.controller;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiParam;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.rest.api.MarkupPageListModel;
import org.mitre.mpf.rest.api.MarkupResultConvertedModel;
import org.mitre.mpf.rest.api.MarkupResultModel;
import org.mitre.mpf.wfm.WfmProcessingException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;

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

    private List<MarkupResultModel> getMarkupResultsJson(Long jobId) {
        List<MarkupResultModel> markupResultModels = new ArrayList<>();
        if (jobId != null) {
            for (MarkupResult markupResult : markupResultDao.findByJobId(jobId)) {
                markupResultModels.add(convertMarkupResult(markupResult));
            }
        } else {
            for (MarkupResult markupResult : markupResultDao.findAll()) {
                markupResultModels.add(convertMarkupResult(markupResult));
            }
        }
        return markupResultModels;
    }

    @RequestMapping(value = "/markup/results", method = RequestMethod.GET)
    @ResponseBody
    public List<MarkupResultModel> getMarkupResultsJsonSession(@ApiParam(value = "Job id - OPTIONAL") @RequestParam(value = "jobId", required = false) Long jobId) {
        return getMarkupResultsJson(jobId);
    }

    //https://datatables.net/manual/server-side#Sent-parameters
    //draw is the counter of how many times it has called back
    //length is how many to return
    //start is offset from 0
    //search is string to filter
    @RequestMapping(value = {"/markup/get-markup-results-filtered"}, method = RequestMethod.POST)
    @ResponseBody
    public MarkupPageListModel getMarkupResultsFiltered(@RequestParam(value = "jobId", required = true) long jobId,
                                           @RequestParam(value = "draw", required = false) int draw,
                                           @RequestParam(value = "start", required = false) int start,
                                           @RequestParam(value = "length", required = false) int length,
                                           @RequestParam(value = "search", required = false) String search,
                                           @RequestParam(value = "sort", required = false) String sort) throws WfmProcessingException {
        log.debug("get-markup-results-filtered Params jobId: {}, draw:{}, start:{},length:{},search:{}, sort:{} ", jobId, draw, start, length, search, sort);

        JobRequest jobRequest = jobRequestDao.findById(jobId);
        if (jobRequest == null) {
            return new MarkupPageListModel(draw, 0, 0, null, List.of());
        }

        BatchJob job = jsonUtils.deserialize(jobRequest.getJob(), BatchJob.class);

        List<MarkupResult> markupResults = markupResultDao.findByJobId(jobId);

        //convert markup objects
        Map<Long, MarkupResultConvertedModel> markupResultModels = new HashMap();
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
            model.setSourceUri(med.getUri());
            model.setSourceFileAvailable(false);
            if (med.getUri() != null) {
                Path path = IoUtils.toLocalPath(med.getUri()).orElse(null);
                if (path == null || Files.exists(path)) {
                    String downloadUrl = UriComponentsBuilder.fromPath("server/download")
                            .queryParam("sourceUri", med.getUri())
                            .queryParam("jobId", jobId)
                            .toUriString();
                    model.setSourceDownloadUrl(downloadUrl);
                    model.setSourceFileAvailable(true);
                }
                if (path != null && Files.exists(path)) {
                    model.setSourceURIContentType(med.getMimeType());
                    model.setSourceImgUrl("server/node-image?nodeFullPath=" + path);
                }
            }
            markupResultModels.put(med.getId(), model);
        }

        //handle search
        List<MarkupResultConvertedModel> markupResultModelsFiltered = new ArrayList();
        for (MarkupResultConvertedModel markupResult : markupResultModels.values()) {
            if (search != null && search.length() > 0) {
                search = search.toLowerCase();
                if ((markupResult.getJobId() + "").toLowerCase().contains(search) ||
                        (markupResult.getMarkupUri() != null && markupResult.getMarkupUri().toLowerCase().contains(search)) ||
                        (markupResult.getSourceUri() != null && markupResult.getSourceUri().toLowerCase().contains(search))) {
                    markupResultModelsFiltered.add(markupResult);
                }
            } else {
                markupResultModelsFiltered.add(markupResult);
            }
        }

        //handle paging
        int end = start + length;
        end = (end > markupResultModelsFiltered.size()) ? markupResultModelsFiltered.size() : end;
        start = (start <= end) ? start : end;
        List<MarkupResultConvertedModel> markupResultModelsFinal = markupResultModelsFiltered.subList(start, end);

        Collections.sort(markupResultModelsFinal, Comparator.comparingLong(MarkupResultConvertedModel::getMediaId));

        return new MarkupPageListModel(draw, markupResultModels.size(), markupResultModelsFiltered.size(), null,
                markupResultModelsFinal);
    }

    @RequestMapping(value = "/markup/content", method = RequestMethod.GET, produces = MediaType.IMAGE_JPEG_VALUE)
    @ResponseBody
    public void serve(HttpServletResponse response, @RequestParam(value = "id", required = true) long id) throws IOException, URISyntaxException {
        MarkupResult mediaMarkupResult = markupResultDao.findById(id);
        if (mediaMarkupResult != null) {
            //only on image!
            if (!StringUtils.endsWithIgnoreCase(mediaMarkupResult.getMarkupUri(), "avi")) {
                String nonUrlPath = mediaMarkupResult.getMarkupUri().replace("file:", "");
                File f = new File(nonUrlPath);
                if (f.canRead()) {
                    FileUtils.copyFile(f, response.getOutputStream());
                    response.flushBuffer();
                }
            }
        }
        //TODO need a no image available if final nested if is not met
    }


    @RequestMapping(value = "/markup/download", method = RequestMethod.GET)
    public void getFile(@RequestParam("id") long id, HttpServletResponse response) throws IOException, StorageException {
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
            log.error("Markup with id " + id + " download failed. Invalid job with id " + markupResult.getJobId() + ".");
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
            log.error("Markup with id " + id + " download failed. Invalid media: " + markupResult.getSourceUri());
            response.setStatus(404);
            response.flushBuffer();
            return;
        }

        Function<String, String> combinedProperties = getProperties(job, media, markupResult);

        if (S3StorageBackend.requiresS3ResultUpload(combinedProperties)) {
            S3Object s3Object = s3StorageBackend.getFromS3(markupResult.getMarkupUri(), combinedProperties);
            try (InputStream inputStream = s3Object.getObjectContent()) {
                ObjectMetadata metadata = s3Object.getObjectMetadata();
                IoUtils.sendBinaryResponse(inputStream, response, metadata.getContentType(),
                                           metadata.getContentLength());
            }
            return;
        }

        URL mediaUrl = IoUtils.toUrl(markupResult.getMarkupUri());
        URLConnection urlConnection = mediaUrl.openConnection();
        try (InputStream inputStream = urlConnection.getInputStream()) {
            IoUtils.sendBinaryResponse(inputStream, response, urlConnection.getContentType(),
                                       urlConnection.getContentLength());
        }
    }

    private Function<String, String> getProperties(BatchJob job, Media media, MarkupResult markupResult) {
        var action = job.getPipelineElements().getAction(markupResult.getTaskIndex(), markupResult.getActionIndex());
        return aggregateJobPropertiesUtil.getCombinedProperties(job, media, action);
    }

    private MarkupResultModel convertMarkupResult(MarkupResult markupResult) {
        boolean isImage = false;
        boolean fileExists = true;
        if(markupResult.getMarkupUri() != null) {
            String nonUrlPath = markupResult.getMarkupUri().replace("file:", "");
            String markupContentType = ioUtils.getPathContentType(Paths.get(nonUrlPath));
            isImage = (markupContentType != null && StringUtils.startsWithIgnoreCase(markupContentType, "IMAGE"));
            fileExists = new File(nonUrlPath).exists();
        }

        return new MarkupResultModel(markupResult.getId(), markupResult.getJobId(),
                                     markupResult.getPipeline(), markupResult.getMarkupUri(),
                                     markupResult.getSourceUri(), isImage, fileExists);
    }

    private MarkupResultConvertedModel convertMarkupResultWithContentType(MarkupResult markupResult, Media media) {
        String markupUriContentType = "";
        String markupImgUrl = "";
        String markupDownloadUrl ="";
        String sourceUriContentType="";
        String sourceImgUrl = "";
        String sourceDownloadUrl ="";
        boolean markupFileAvailable = false;
        boolean sourceFileAvailable = false;

        if (markupResult.getMarkupUri() != null) {
            Path path = IoUtils.toLocalPath(markupResult.getMarkupUri()).orElse(null);
            if (path != null && Files.exists(path)) {
                markupUriContentType = ioUtils.getPathContentType(path);
                markupFileAvailable = true;
                markupImgUrl = "markup/content?id=" + markupResult.getId();
                markupDownloadUrl = "markup/download?id=" + markupResult.getId();
            }
            if (path == null) {
                markupFileAvailable = true;
                markupDownloadUrl = markupResult.getMarkupUri();
            }
        }

        // Derivative media may have been uploaded to remote storage,
        // so the source URI may have changed since the markup result was generated.
        String markupResultSourceUri = media.getUri();

        if (markupResultSourceUri != null) {
            Path path = IoUtils.toLocalPath(markupResultSourceUri).orElse(null);
            if (path == null || Files.exists(path)) {
                sourceDownloadUrl = UriComponentsBuilder
                        .fromPath("server/download")
                        .queryParam("sourceUri", markupResultSourceUri)
                        .queryParam("jobId", markupResult.getJobId())
                        .toUriString();
                sourceFileAvailable = true;
            }
            if (path != null && Files.exists(path))  {
                sourceUriContentType = ioUtils.getPathContentType(path);
                sourceImgUrl = "server/node-image?nodeFullPath=" + path;
            }
        }

        return new MarkupResultConvertedModel(
                markupResult.getId(),
                markupResult.getJobId(),
                markupResult.getMediaId(),
                media.getParentId(),
                markupResult.getPipeline(),
                markupResult.getMarkupUri(),
                markupUriContentType,
                markupImgUrl,
                markupDownloadUrl,
                markupFileAvailable,
                markupResultSourceUri,
                sourceUriContentType,
                sourceImgUrl,
                sourceDownloadUrl,
                sourceFileAvailable);
    }
}
