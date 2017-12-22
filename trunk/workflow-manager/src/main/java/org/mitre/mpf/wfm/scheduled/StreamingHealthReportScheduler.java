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

package org.mitre.mpf.wfm.scheduled;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mitre.mpf.wfm.service.MpfService;

@Component
public class StreamingHealthReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(StreamingHealthReportScheduler.class);

    @Autowired //will grab the impl
    private MpfService mpfService;

    // TODO streaming.healthReport.callbackRate is defined in PropertiesUtil. Need to test varying this parameter in the mpf.property file.
    @Scheduled(fixedDelayString = "${streaming.healthReport.callbackRate}" )
    public void sendHealthReports() {
        boolean isActive = true; // only send periodic health reports for streaming jobs that are current and active.
        mpfService.sendStreamingJobHealthReports(isActive);
    }

}
