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

package org.mitre.mpf.wfm.camelOps;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;
import org.apache.commons.lang3.mutable.MutableInt;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runner.notification.RunListener;
import org.mitre.mpf.interop.JsonJobRequest;
import org.mitre.mpf.interop.JsonMediaInputObject;
import org.mitre.mpf.interop.JsonPipeline;
import org.mitre.mpf.wfm.businessrules.JobRequestBo;
import org.mitre.mpf.wfm.businessrules.impl.JobRequestBoImpl;
import org.mitre.mpf.wfm.camel.WfmProcessorInterface;
import org.mitre.mpf.wfm.camel.operations.jobcreation.JobCreationProcessor;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateDao;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateJobRequestDaoImpl;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.data.entities.transients.TransientJob;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration(locations = {"classpath:applicationContext.xml"})
@RunWith(SpringJUnit4ClassRunner.class)
@RunListener.ThreadSafe
public class TestJobCreationProcessor {
    private static final Logger log = LoggerFactory.getLogger(TestJobCreationProcessor.class);
    private static final int MINUTES = 1000*60; // 1000 milliseconds/second & 60 seconds/minute.

    @Autowired
    private ApplicationContext context;

    @Autowired
    private CamelContext camelContext;

    @Autowired
    @Qualifier(JobCreationProcessor.REF)
    private WfmProcessorInterface jobCreationProcessor;

    @Autowired
    @Qualifier(JobRequestBoImpl.REF)
    private JobRequestBo jobRequestBo;

    @Autowired
    @Qualifier(HibernateJobRequestDaoImpl.REF)
    private HibernateDao<JobRequest> jobRequestDao;

    @Autowired
    private IoUtils ioUtils;

    @Autowired
    private JsonUtils jsonUtils;

    @Autowired
    private Redis redis;

    private static final MutableInt SEQUENCE = new MutableInt();
    public int next() {
        synchronized (SEQUENCE) {
            int next = SEQUENCE.getValue();
            SEQUENCE.increment();
            return next;
        }
    }

    @Test(timeout = 5 * MINUTES)
    public void testJobCreation() throws Exception {
        final int testPriority = 5;
        final long testId = 12345;
        log.info("Starting image inspection test.");
        JsonPipeline pipeline = new JsonPipeline("OCV FACE DETECTION PIPELINE", "face pipeline woo");
        JsonJobRequest jobRequest = new JsonJobRequest(Long.toString(testId), true, pipeline, testPriority);
        jobRequest.getMedia().add(new JsonMediaInputObject("/samples/meds1.jpg"));
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setBody(jsonUtils.serialize(jobRequest));
        JobRequest returnedRequest = jobRequestBo.initialize(jobRequest);
        returnedRequest.getId();
        exchange.getIn().getHeaders().put(MpfHeaders.JOB_ID, returnedRequest.getId());
        jobCreationProcessor.process(exchange);

        Object responseBody = exchange.getOut().getBody();
        Assert.assertTrue("A response body must be set.", responseBody != null);
        Assert.assertTrue(String.format("Response body must be a byte[]. Actual: %s.", responseBody.getClass()),  responseBody instanceof byte[]);
        JobRequest returnedResponse = jobRequestDao.findById(returnedRequest.getId());
        TransientJob tjob = redis.getJob(returnedResponse.getId());
        Assert.assertTrue(tjob.getExternalId().equals(Long.toString(testId)));

        jobRequestDao.deleteById(returnedRequest.getId());
    }
}