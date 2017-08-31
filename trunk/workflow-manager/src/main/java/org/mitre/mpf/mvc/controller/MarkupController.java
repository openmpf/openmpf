/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.nio.file.Paths;
import java.util.*;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.interop.JsonJobRequest;
import org.mitre.mpf.interop.JsonMediaInputObject;
import org.mitre.mpf.mvc.util.NIOUtils;
import org.mitre.mpf.rest.api.MarkupPageListModel;
import org.mitre.mpf.rest.api.MarkupResultConvertedModel;
import org.mitre.mpf.rest.api.MarkupResultModel;
import org.mitre.mpf.mvc.util.ModelUtils;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.data.entities.persistent.MarkupResult;
import org.mitre.mpf.wfm.service.MpfService;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Api(value = "Markup", description = "Access the information of marked up media")
@Controller
@Scope("request")
@Profile("website")
public class MarkupController {
    private static final Logger log = LoggerFactory.getLogger(MarkupController.class);

    @Autowired //will grab the impl
    private MpfService mpfService;

    @Autowired
    private JsonUtils jsonUtils;

    private List<MarkupResultModel> getMarkupResultsJson(Long jobId) {
        //all MarkupResult objects
        List<MarkupResultModel> markupResultModels = new ArrayList<MarkupResultModel>();
        for (MarkupResult markupResult : mpfService.getAllMarkupResults()) {
            if (jobId != null) {
                if (markupResult.getJobId() == jobId) {
                    markupResultModels.add(ModelUtils.converMarkupResult(markupResult));
                }
            } else {
                markupResultModels.add(ModelUtils.converMarkupResult(markupResult));
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

        //all MarkupResult objects
        List<MarkupResult> markupResults = mpfService.getMarkupResultsForJob(jobId);
        Collections.reverse(markupResults);

        //convert markup objects
        List<MarkupResultConvertedModel> markupResultModels = new ArrayList<MarkupResultConvertedModel>();
        for (MarkupResult markupResult : markupResults) {
            MarkupResultConvertedModel model = ModelUtils.convertMarkupResultWithContentType(markupResult);
            markupResultModels.add(model);
        }

        //add job media that may exist without markup
        JobRequest jobRequest = mpfService.getJobRequest(jobId);
        if (jobRequest != null) {
            JsonJobRequest req = jsonUtils.deserialize(jobRequest.getInputObject(), JsonJobRequest.class);

            List<JsonMediaInputObject> media_list = req.getMedia();
            for (int i = 0; i < media_list.size(); i++) {
                JsonMediaInputObject med = media_list.get(i);
                MarkupResultConvertedModel model = new MarkupResultConvertedModel();
                model.setJobId(jobId);
                model.setPipeline(req.getPipeline().getName());
                model.setSourceUri(med.getMediaUri());
                model.setSourceFileAvailable(false);
                if (med.getMediaUri() != null) {
                    String nonUrlPath = med.getMediaUri();
                    File f = new File(URI.create(nonUrlPath));
                    if (f != null && f.exists()) {
                        model.setSourceURIContentType(NIOUtils.getPathContentType(Paths.get(URI.create(nonUrlPath))));
                        model.setSourceImgUrl( "server/node-image?nodeFullPath=" + Paths.get(URI.create(nonUrlPath)));
                        model.setSourceDownloadUrl( "server/download?fullPath=" + Paths.get(URI.create(nonUrlPath)));
                        model.setSourceFileAvailable(true);
                    }
                }
                //add to the list
                boolean found = false;
                for (MarkupResultConvertedModel existing : markupResultModels) {
                    if(existing.getSourceUri().equals(model.getSourceUri())) found = true;
                }
                if(!found) markupResultModels.add(model);
            }
        }

        //handle search
        List<MarkupResultConvertedModel> markupResultModelsFinal = new ArrayList<MarkupResultConvertedModel>();
        for (MarkupResultConvertedModel markupResult : markupResultModels) {
            if (search != null && search.length() > 0) {
                search = search.toLowerCase();
                if ((markupResult.getJobId() + "").toLowerCase().contains(search) ||
                        (markupResult.getMarkupUri() != null && markupResult.getMarkupUri().toLowerCase().contains(search)) ||
                        (markupResult.getSourceUri() != null && markupResult.getSourceUri().toLowerCase().contains(search))) {
                    markupResultModelsFinal.add(markupResult);
                }
            } else {
                markupResultModelsFinal.add(markupResult);
            }
        }

        int records_total = markupResultModelsFinal.size();
        int records_filtered = records_total;// Total records, after filtering (i.e. the total number of records after filtering has been applied - not just the number of records being returned for this page of data).

        //handle paging
        int end = start + length;
        end = (end > markupResultModelsFinal.size()) ? markupResultModelsFinal.size() : end;
        start = (start <= end) ? start : end;
        List<MarkupResultConvertedModel> modelsFiltered = markupResultModelsFinal.subList(start,end);

        //build output
        String error = null;
        MarkupPageListModel model = new MarkupPageListModel(draw,records_total,records_filtered,error,modelsFiltered);

        return model;
    }

    @RequestMapping(value = "/markup/content", method = RequestMethod.GET, produces = MediaType.IMAGE_JPEG_VALUE)
    @ResponseBody
    public void serve(HttpServletResponse response, @RequestParam(value = "id", required = true) long id) throws IOException, URISyntaxException {
        MarkupResult mediaMarkupResult = mpfService.getMarkupResult(id);
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
    public void getFile(@RequestParam(value = "id", required = true) long id, HttpServletResponse response) throws IOException, URISyntaxException {
        MarkupResult mediaMarkupResult = mpfService.getMarkupResult(id);
        if (mediaMarkupResult != null) {
            String nonUrlPath = mediaMarkupResult.getMarkupUri().replace("file:", "");
            File f = new File(nonUrlPath);
            if (f.exists() && f.canRead()) {
                String mimeType = URLConnection.guessContentTypeFromName(f.getName());
                if (mimeType == null) {
                    System.out.println("mimetype is not detectable, will take default");
                    mimeType = "application/octet-stream";
                }
                response.setContentType(mimeType);
                response.setHeader("Content-Disposition", String.format("attachment; filename=\"%s\"", f.getName()));
                response.setContentLength((int) f.length());
                //copy it to response's OutputStream
                FileUtils.copyFile(f, response.getOutputStream());
                response.flushBuffer();
            }else {
                log.debug("server download file failed for markup id= "+id);
                response.setStatus(404);
            }
        }else {
            log.debug("server download file failed for markup id= "+id);
            response.setStatus(404);
        }
    }

}