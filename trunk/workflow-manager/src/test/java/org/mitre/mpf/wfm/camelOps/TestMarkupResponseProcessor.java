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

package org.mitre.mpf.wfm.camelOps;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;
import org.apache.commons.configuration2.ImmutableConfiguration;
import org.apache.commons.lang3.mutable.MutableInt;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runner.notification.RunListener;
import org.mitre.mpf.wfm.buffers.Markup;
import org.mitre.mpf.wfm.camel.WfmProcessorInterface;
import org.mitre.mpf.wfm.camel.operations.markup.MarkupResponseProcessor;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.access.MarkupResultDao;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateMarkupResultDaoImpl;
import org.mitre.mpf.wfm.data.entities.persistent.MarkupResult;
import org.mitre.mpf.wfm.data.entities.transients.TransientDetectionSystemProperties;
import org.mitre.mpf.wfm.data.entities.transients.TransientJob;
import org.mitre.mpf.wfm.data.entities.transients.TransientMedia;
import org.mitre.mpf.wfm.data.entities.transients.TransientPipeline;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;

@ContextConfiguration(locations = {"classpath:applicationContext.xml"})
@RunWith(SpringJUnit4ClassRunner.class)
@RunListener.ThreadSafe
public class TestMarkupResponseProcessor {
    private static final Logger log = LoggerFactory.getLogger(TestMarkupResponseProcessor.class);
    private static final int MINUTES = 1000*60; // 1000 milliseconds/second & 60 seconds/minute.

    @Autowired
    private ApplicationContext context;

    @Autowired
    private CamelContext camelContext;

    @Autowired
    @Qualifier(MarkupResponseProcessor.REF)
    private WfmProcessorInterface markupResponseProcessor;

    @Autowired
    @Qualifier(HibernateMarkupResultDaoImpl.REF)
    private MarkupResultDao markupResultDao;

    @Autowired
    private Redis redis;

    @Autowired
    @Qualifier(PropertiesUtil.REF)
    private PropertiesUtil propertiesUtil;

    private static final MutableInt SEQUENCE = new MutableInt();
    public int next() {
        synchronized (SEQUENCE) {
            int next = SEQUENCE.getValue();
            SEQUENCE.increment();
            return next;
        }
    }

    @Test(timeout = 5 * MINUTES)
    public void testMarkupResponse() throws Exception {

        final int jobId = next();
        final int currentStage = 1;
        final int priority = 5;
        final long mediaId = 0;
        TransientPipeline dummyPipeline = new TransientPipeline("testMarkupPipeline", "testMarkupPipelineDescription");
        ImmutableConfiguration detectionSystemPropertiesSnapshot = propertiesUtil.getDetectionConfiguration();
        TransientJob dummyJob = new TransientJob(jobId, Long.toString(jobId), detectionSystemPropertiesSnapshot, dummyPipeline, currentStage, priority, false, false);
        dummyJob.getMedia().add(new TransientMedia(mediaId, "/samples/meds1.jpg"));
        Markup.MarkupResponse.Builder builder = Markup.MarkupResponse.newBuilder();
        builder.setMediaId(mediaId);
        builder.setMediaIndex(0);
        builder.setTaskIndex(0);
        builder.setActionIndex(0);
        builder.setRequestId(mediaId);
        builder.setHasError(false);
        builder.setOutputFileUri("output.txt");
        builder.clearErrorMessage();
        Markup.MarkupResponse response = builder.build();
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().getHeaders().put(MpfHeaders.JOB_ID, jobId);
        exchange.getIn().setBody(response);
        redis.persistJob(dummyJob);

        markupResponseProcessor.process(exchange);

        List<MarkupResult> markupResults = markupResultDao.findByJobId(jobId);
        MarkupResult markupResult = markupResults.get(0);
        Assert.assertTrue(markupResult.getJobId() == jobId);//markup result is not null and is correctly found
        Assert.assertTrue(markupResult.getMarkupUri().equals("output.txt"));
        Assert.assertNull(markupResult.getMessage());//error message

        markupResultDao.deleteByJobId(jobId);
    }
}