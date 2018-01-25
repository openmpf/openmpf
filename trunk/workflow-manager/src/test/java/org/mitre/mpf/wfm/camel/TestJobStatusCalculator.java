/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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

import junit.framework.Assert;
import junit.framework.TestCase;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runner.notification.RunListener;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.enums.JobStatus;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.HashMap;

@ContextConfiguration(locations = {"classpath:applicationContext.xml"})
@RunWith(SpringJUnit4ClassRunner.class)
@RunListener.ThreadSafe
public class TestJobStatusCalculator extends TestCase {

    @Autowired
    JobStatusCalculator jobStatusCalculator;

    @Autowired
    private JsonUtils jsonUtils;

    @Autowired
    private IoUtils ioUtils;

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private Redis redis;


    @Test
    public void testCalculateStatusComplete() throws Exception {
        final long jobId = 112233;
        Exchange exchange = new DefaultExchange(camelContext);
        TransientJob job = TestUtil.setupJob(jobId, redis, ioUtils);
        exchange.getIn().setBody(jsonUtils.serialize(job));
        redis.setJobStatus(jobId, JobStatus.IN_PROGRESS);
        Assert.assertEquals(JobStatus.IN_PROGRESS,redis.getJobStatus(jobId));
        Assert.assertEquals(JobStatus.COMPLETE, jobStatusCalculator.calculateStatus(exchange));
    }

    @Test
    public void testCalculateStatusErrors() throws Exception {
        final long jobId = 112234;
        Exchange exchange = new DefaultExchange(camelContext);
        TransientJob job = TestUtil.setupJob(jobId, redis, ioUtils);
        exchange.getIn().setBody(jsonUtils.serialize(job));
        redis.setJobStatus(jobId, JobStatus.IN_PROGRESS_ERRORS);
        Assert.assertEquals(JobStatus.COMPLETE_WITH_ERRORS,jobStatusCalculator.calculateStatus(exchange));
    }

    @Test
    public void testCalculateStatusWarnings() throws Exception {
        final long jobId = 112235;
        Exchange exchange = new DefaultExchange(camelContext);
        TransientJob job = TestUtil.setupJob(jobId, redis, ioUtils);
        exchange.getIn().setBody(jsonUtils.serialize(job));
        redis.setJobStatus(jobId, JobStatus.IN_PROGRESS_WARNINGS);
        Assert.assertEquals(JobStatus.COMPLETE_WITH_WARNINGS,jobStatusCalculator.calculateStatus(exchange));
    }


}