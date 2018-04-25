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

import com.google.common.collect.Lists;
import java.util.List;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultExchange;
import org.apache.commons.configuration2.ImmutableConfiguration;
import org.apache.commons.lang3.mutable.MutableInt;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runner.notification.RunListener;
import org.mitre.mpf.wfm.camel.WfmSplitterInterface;
import org.mitre.mpf.wfm.camel.operations.mediainspection.MediaInspectionSplitter;
import org.mitre.mpf.wfm.data.entities.transients.TransientJob;
import org.mitre.mpf.wfm.data.entities.transients.TransientMedia;
import org.mitre.mpf.wfm.data.entities.transients.TransientPipeline;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
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
public class TestMediaInspectionSplitter {
    private static final Logger log = LoggerFactory.getLogger(TestMediaInspectionSplitter.class);
    private static final int MINUTES = 1000 * 60; // 1000 milliseconds/second & 60 seconds/minute.

    @Autowired
    private ApplicationContext context;

    @Autowired
    private CamelContext camelContext;

    @Autowired
    @Qualifier(JsonUtils.REF)
    private JsonUtils jsonUtils;

    @Autowired
    @Qualifier(PropertiesUtil.REF)
    private PropertiesUtil propertiesUtil;

    @Autowired
    @Qualifier(MediaInspectionSplitter.REF)
    private WfmSplitterInterface mediaInspectionSplitter;

    private static final MutableInt SEQUENCE = new MutableInt();

    public int next() {
        synchronized (SEQUENCE) {
            int next = SEQUENCE.getValue();
            SEQUENCE.increment();
            return next;
        }
    }
    @Test
    public void testMediaInspectionSplitter() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        final long jobId = next();
        final String testExternalId = "externID";
        final TransientPipeline testPipe = new TransientPipeline("testPipe", "testDescr");
        final int testStage = 1;
        final int testPriority = 4;
        final boolean testOutputEnabled = true;
        ImmutableConfiguration detectionSystemPropertiesSnapshot = propertiesUtil.getDetectionConfiguration();
        TransientJob testJob = new TransientJob(jobId, testExternalId, detectionSystemPropertiesSnapshot, testPipe, testStage, testPriority, testOutputEnabled, false);
        final long testMediaId = 123456;
        final String testURI = "/samples/new_face_video.avi";
        TransientMedia testMedia = new TransientMedia(testMediaId, testURI);
        testMedia.setFailed(false);
        List<TransientMedia> listMedia = Lists.newArrayList(testMedia);
        testJob.setMedia(listMedia);
        Assert.assertNotNull(testJob);
        Assert.assertNotNull(testMedia);
        Assert.assertNotNull(listMedia);
        Assert.assertNotNull(exchange);
        Assert.assertNotNull(jsonUtils.serialize(testJob));
        exchange.getIn().setBody(jsonUtils.serialize(testJob));
        exchange.getIn().setHeader(MpfHeaders.JOB_ID, jobId);

        List<Message> responseList = mediaInspectionSplitter.split(exchange);
        Assert.assertTrue(responseList.size() != 0); //messageList was populated which means that the splitter did not fail to serialize the data
    }
}
