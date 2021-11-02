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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.mitre.mpf.rest.api.AllJobsStatisticsModel;
import org.mitre.mpf.wfm.data.access.JobRequestDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;

@Api(value = "Statistics",
     description = "Job statistics")
@Controller
@Scope("request")
public class AdminStatisticsController {
    private static final Logger log = LoggerFactory.getLogger(AdminStatisticsController.class);

    @Autowired
    private JobRequestDao jobRequestDao;

    @RequestMapping(value = "/adminStatistics", method = RequestMethod.GET)
    public ModelAndView getAdminStatistics(HttpServletRequest request) {
        ModelAndView mv = new ModelAndView("admin_statistics");
        return mv;
    }

    /*
     * GET jobs/stats
     */
    //EXTERNAL
    @RequestMapping(value = "/rest/jobs/stats", method = RequestMethod.GET)
    @ApiOperation(value = "Compiles the AllJobsStatisticsModel using all of the submitted jobs.",
            produces = "application/json", response = AllJobsStatisticsModel.class)
    @ApiResponses(@ApiResponse(code = 200, message = "Successful response"))
    @ResponseBody
    public AllJobsStatisticsModel getAllJobsStatsRest() {
        log.debug("[/rest/jobs/stats]");
        return jobRequestDao.getJobStats();
    }


    //INTERNAL
    @RequestMapping(value = "/jobs/stats", method = RequestMethod.GET)
    @ResponseBody
    public AllJobsStatisticsModel getAllJobsStats() {
        log.debug("[/jobs/stats]");
        return jobRequestDao.getJobStats();
    }
}
