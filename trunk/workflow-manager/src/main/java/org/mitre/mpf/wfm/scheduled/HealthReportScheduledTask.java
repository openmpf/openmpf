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

import java.util.List;

import org.mitre.mpf.wfm.businessrules.StreamingJobRequestBo;
import org.mitre.mpf.wfm.businessrules.impl.StreamingJobRequestBoImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import org.mitre.mpf.wfm.service.MpfService;

@Component
public class HealthReportScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(HealthReportScheduledTask.class);

    @Autowired //will grab the impl
    private MpfService mpfService;

    @Autowired
    @Qualifier(StreamingJobRequestBoImpl.REF)
    private StreamingJobRequestBo streamingJobRequestBo;

    @Scheduled(fixedDelayString = "${streaming.healthReport.callbackRate:30000}" )
    public void sendHealthReports() {
        log.info("HealthReportScheduledTask: timestamp=" + System.currentTimeMillis() + " msec");
        List<Long> jobIds = mpfService.getAllStreamingJobIds();
        jobIds.stream().forEach( jobId -> mpfService.sendPeriodicHealthReportToCallback(jobId) );
    }

}
