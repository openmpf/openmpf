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
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runner.notification.RunListener;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.camel.ResponseProcessor;
import org.mitre.mpf.wfm.camel.WfmProcessorInterface;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionResponseProcessor;
import org.mitre.mpf.wfm.camel.operations.detection.trackmerging.TrackMergingContext;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.enums.JobStatus;
import org.mitre.mpf.wfm.enums.MpfConstants;
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

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@ContextConfiguration(locations = {"classpath:applicationContext.xml"})
@RunWith(SpringJUnit4ClassRunner.class)
@RunListener.ThreadSafe
public class TestDetectionResponseProcessor {

    private static final Logger log = LoggerFactory.getLogger(TestDetectionResponseProcessor.class);
    private static final int MINUTES = 1000 * 60; // 1000 milliseconds/second & 60 seconds/minute.

    @Autowired
    private ApplicationContext context;

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private JsonUtils jsonUtils;

    @Autowired
    private IoUtils ioUtils;

    @Autowired
    private Redis redis;

    @Autowired
    @Qualifier(DetectionResponseProcessor.REF)
    private WfmProcessorInterface detectionResponseProcessor;

    private static final MutableInt SEQUENCE = new MutableInt();

    public int next() {
        synchronized (SEQUENCE) {
            int next = SEQUENCE.getValue();
            SEQUENCE.increment();
            return next;
        }
    }
    //	public Object processResponse(long jobId, Extraction.ExtractionResponse extractionResponse, Map<String, Object> headers) throws WfmProcessingException {
    @Ignore
    @Test
    public void testDetectionResponse() throws Exception {
        final int testJobId = next();
        final long testMediaId = 12345678;
        DetectionProtobuf.DetectionResponse response = DetectionProtobuf.DetectionResponse.newBuilder()
            .setError(DetectionProtobuf.DetectionError.NO_DETECTION_ERROR)
            .setMediaId(testMediaId)
            .setStartIndex(0)
            .setStopIndex(10)
            .setStageName("theWorld")
            .setStageIndex(1)
            .setActionName("howLikeAnAngel")
            .setActionIndex(1)
            .setRequestId(123456)
            .build();

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().getHeaders().put(MpfHeaders.JOB_ID, testJobId);
        exchange.getIn().setBody(response);

        detectionResponseProcessor.process(exchange);

        Object responseBody = exchange.getOut().getBody();
        TrackMergingContext processorResponse = jsonUtils.deserialize((byte[])responseBody, TrackMergingContext.class);

        Assert.assertTrue(processorResponse.getJobId() == testJobId);
        Assert.assertTrue(processorResponse.getStageIndex() == 1);

        Assert.assertTrue(responseBody != null);
    }

    @Test
    public void testSettingErrors() throws WfmProcessingException {
        long jobId = 123123;
        long testMediaId = 234234;
        DetectionProtobuf.DetectionResponse detectionResponse = DetectionProtobuf.DetectionResponse.newBuilder()
                .setError(DetectionProtobuf.DetectionError.BOUNDING_BOX_SIZE_ERROR)
                .setMediaId(testMediaId)
                .setStartIndex(0)
                .setStopIndex(10)
                .setStageIndex(1)
                .setActionIndex(1)
                .setRequestId(123456)
                .build();

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().getHeaders().put(MpfHeaders.JOB_ID, jobId);
        exchange.getIn().setBody(detectionResponse);

        TransientPipeline detectionPipeline = new TransientPipeline("detectionPipeline", "detectionDescription");
        TransientStage detectionStageDet = new TransientStage("detectionDetection", "detectionDescription", ActionType.DETECTION);
        TransientAction detectionAction = new TransientAction("detectionAction", "detectionDescription", "detectionAlgo");
        detectionAction.setProperties(new HashMap<>());
        detectionStageDet.getActions().add(detectionAction);

        detectionPipeline.getStages().add(detectionStageDet);
        TransientJob detectionJob = new TransientJob(jobId, "234234", detectionPipeline, 0, 1, false, false);
        TransientMedia media = new TransientMedia(234234,ioUtils.findFile("/samples/video_01.mp4").toString());
        media.addMetadata("DURATION","3004");
        media.addMetadata("FPS","29.97");
        detectionJob.getMedia().add(media);

        redis.persistJob(detectionJob);

        detectionResponseProcessor.wfmProcess(exchange);
        Assert.assertEquals(JobStatus.IN_PROGRESS_ERRORS,redis.getJobStatus(jobId));

    }
}

