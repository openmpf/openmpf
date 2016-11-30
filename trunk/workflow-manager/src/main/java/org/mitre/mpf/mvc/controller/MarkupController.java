/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.mvc.model.SessionModel;
import org.mitre.mpf.rest.api.MarkupResultModel;
import org.mitre.mpf.mvc.util.ModelUtils;
import org.mitre.mpf.wfm.data.entities.persistent.MarkupResult;
import org.mitre.mpf.wfm.service.MpfService;
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

@Api( value = "Markup", description = "Access the information of marked up media")
@Controller
@Scope("request")
@Profile("website")
public class MarkupController {
	private static final Logger log = LoggerFactory.getLogger(MarkupController.class);

	@Autowired //will grab the impl
	private MpfService mpfService;

    @Autowired
    private SessionModel sessionModel;

    private List<MarkupResultModel> getMarkupResultsJsonVersionOne(Long jobId) {
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
        return getMarkupResultsJsonVersionOne(jobId);
    }

    //https://datatables.net/manual/server-side#Sent-parameters
    //draw is the counter of how many times it has called back
    //length is how many to return
    //start is offset from 0
    //search is string to filter
    @RequestMapping(value = {"/markup/get-markup-results-filtered"}, method = RequestMethod.POST)
    @ResponseBody
    public String getMarkupResultsFiltered(@RequestParam(value = "draw", required = false) int draw,
                                           @RequestParam(value = "start", required = false) int start,
                                           @RequestParam(value = "length", required = false) int length,
                                           @RequestParam(value = "search", required = false) String search,
                                           @RequestParam(value = "sort", required = false) String sort) {
        log.debug("Params draw:{} start:{},length:{},search:{}, sort{} ", draw, start, length, search, sort);

        //all MarkupResult objects
        List<MarkupResult> markupResults= mpfService.getAllMarkupResults();
        Collections.reverse(markupResults);

        List<MarkupResultModel> markupResultModels = new ArrayList<MarkupResultModel>();
        for (MarkupResult markupResult : markupResults) {
            MarkupResultModel model = ModelUtils.converMarkupResult(markupResult);

            //handle search
            if (search != null && search.length() > 0) {
                search = search.toLowerCase();
               if ((model.getJobId() + "").toLowerCase().contains(search) ||
                       (model.getSourceMedium() != null && model.getSourceMedium().toLowerCase().contains(search)) ||
                       (model.getActionHistory() != null && model.getActionHistory().toLowerCase().contains(search))) {
                    markupResultModels.add(model);
                }
            }else{
                markupResultModels.add(model);
            }
        }

        int records_total = markupResultModels.size();
        int records_filtered = records_total;// Total records, after filtering (i.e. the total number of records after filtering has been applied - not just the number of records being returned for this page of data).

        //handle paging
        int end = start + length;
        end = (end > markupResultModels.size()) ? markupResultModels.size() : end;
        start = (start <= end) ? start : end;

        MarkupResultModel[] markupArr = new MarkupResultModel[markupResultModels.size()];
        markupArr = markupResultModels.toArray(markupArr);

        MarkupResultModel[] filtered = Arrays.copyOfRange(markupArr,start,end);

        //build output
        StringBuilder sb = new StringBuilder();
        sb.append("\"data\":[ ");
        boolean atleastone = false;
        for (int i = 0; i < filtered.length; i++) {
            MarkupResultModel model = filtered[i];
            if (atleastone) sb.append(",");
            sb.append("{\"id\":\"" + model.getId() + "\",");
            sb.append("\"jobId\":\"" + model.getJobId() + "\",");
            sb.append("\"actionHistory\":\"" + model.getActionHistory() + "\",");
            sb.append("\"outputPath\":\"" + model.getOutputPath() + "\",");
            sb.append("\"jobId\":\"" + model.getJobId() + "\",");
            sb.append("\"image\":" + model.isImage() + ",");
            sb.append("\"fileExists\":" + model.fileExists() + ",");
            sb.append("\"sourceMedium\":\"" + model.getSourceMedium() + "\"}");
            atleastone = true;
        }

        String error = null;
        sb.append("]");
        String ret = "{\"draw\":" + draw + ",\"recordsTotal\":" + records_total + ",\"recordsFiltered\":" + records_filtered + ",\"error\":" + error + "," + sb.toString() + "}";
        return ret;
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
    public void getFile(
            @RequestParam(value = "id", required = true) long id,
            HttpServletResponse response) throws IOException, URISyntaxException {
        MarkupResult mediaMarkupResult = mpfService.getMarkupResult(id);
        if (mediaMarkupResult != null) {
            String nonUrlPath = mediaMarkupResult.getMarkupUri().replace("file:", "");
            File f = new File(nonUrlPath);
            if (f.canRead()) {
                //copy it to response's OutputStream
                FileUtils.copyFile(f, response.getOutputStream());
                response.flushBuffer();
            }
        }
        //TODO: what to do if file does not exist
    }

}