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

package org.mitre.mpf.wfm.camel;

import org.apache.camel.Exchange;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.RedisImpl;
import org.mitre.mpf.wfm.data.access.MarkupResultDao;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateMarkupResultDaoImpl;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.JobStatus;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * The Job Status Calculator is a tool to calculate the terminal status of a job.
 */
@Component(JobStatusCalculator.REF)
public class JobStatusCalculator {
    public static final String REF = "jobStatusCalculator";
    private static final Logger log = LoggerFactory.getLogger(JobStatusCalculator.class);

    @Autowired
    @Qualifier(RedisImpl.REF)
    private Redis redis;

    @Autowired
    @Qualifier(HibernateMarkupResultDaoImpl.REF)
    private MarkupResultDao markupResultDao;

    @Autowired
    private JsonUtils jsonUtils;

    /**
     * Calculates the terminal status of a job
     * @param exchange  An incoming job exchange
     * @return  The terminal JobStatus for the job.
     * @throws WfmProcessingException
     */
    public JobStatus calculateStatus(Exchange exchange) throws WfmProcessingException {
        TransientJob job = jsonUtils.deserialize(exchange.getIn().getBody(byte[].class), TransientJob.class);

        JobStatus statusFromRedis = redis.getJobStatus(job.getId());

        if (statusFromRedis.equals(JobStatus.IN_PROGRESS_WARNINGS)) {
            redis.setJobStatus(job.getId(), JobStatus.COMPLETE_WITH_WARNINGS);
            return JobStatus.COMPLETE_WITH_WARNINGS;
        } else if (statusFromRedis.equals(JobStatus.IN_PROGRESS_ERRORS)) {
            redis.setJobStatus(job.getId(), JobStatus.COMPLETE_WITH_ERRORS);
            return JobStatus.COMPLETE_WITH_ERRORS;
        } else {
            redis.setJobStatus(job.getId(), JobStatus.COMPLETE);
            return JobStatus.COMPLETE;
        }
    }
}
