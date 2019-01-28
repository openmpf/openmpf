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
import org.apache.commons.lang3.mutable.MutableInt;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runner.notification.RunListener;
import org.mitre.mpf.wfm.buffers.Markup;
import org.mitre.mpf.wfm.camel.WfmProcessorInterface;
import org.mitre.mpf.wfm.camel.operations.markup.MarkupResponseProcessor;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.access.MarkupResultDao;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateMarkupResultDaoImpl;
import org.mitre.mpf.wfm.data.entities.persistent.MarkupResult;
import org.mitre.mpf.wfm.data.entities.transients.SystemPropertiesSnapshot;
import org.mitre.mpf.wfm.data.entities.transients.TransientMedia;
import org.mitre.mpf.wfm.data.entities.transients.TransientPipeline;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Collections;
import java.util.List;

@ContextConfiguration(locations = {"classpath:applicationContext.xml"})
@RunWith(SpringJUnit4ClassRunner.class)
@RunListener.ThreadSafe
public class TestMarkupResponseProcessor {
    private static final int MINUTES = 1000*60; // 1000 milliseconds/second & 60 seconds/minute.

    @Autowired
    private CamelContext camelContext;

    @Autowired
    @Qualifier(MarkupResponseProcessor.REF)
    private WfmProcessorInterface markupResponseProcessor;

    @Autowired
    @Qualifier(HibernateMarkupResultDaoImpl.REF)
    private MarkupResultDao markupResultDao;

    @Autowired
    private InProgressBatchJobsService inProgressJobs;

    @Autowired
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
        final int priority = 5;
        final long mediaId = 0;
        TransientPipeline dummyPipeline = new TransientPipeline(
                "testMarkupPipeline", "testMarkupPipelineDescription", Collections.emptyList());

        // Capture a snapshot of the detection system property settings when the job is created.
        SystemPropertiesSnapshot systemPropertiesSnapshot = propertiesUtil.createSystemPropertiesSnapshot();

        inProgressJobs.addJob(
                jobId,
                Long.toString(jobId),
                systemPropertiesSnapshot,
                dummyPipeline,
                priority,
                false,
                null,
                null,
                Collections.singletonList(new TransientMedia(mediaId, "/samples/meds1.jpg")),
                Collections.emptyMap(),
                Collections.emptyMap());
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

        markupResponseProcessor.process(exchange);

        List<MarkupResult> markupResults = markupResultDao.findByJobId(jobId);
        MarkupResult markupResult = markupResults.get(0);
        Assert.assertTrue(markupResult.getJobId() == jobId);//markup result is not null and is correctly found
        Assert.assertTrue(markupResult.getMarkupUri().equals("output.txt"));
        Assert.assertNull(markupResult.getMessage());//error message

        markupResultDao.deleteByJobId(jobId);
    }
}