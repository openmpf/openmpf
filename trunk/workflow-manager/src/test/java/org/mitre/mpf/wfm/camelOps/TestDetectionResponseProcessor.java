/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runner.notification.RunListener;
import org.mitre.mpf.rest.api.pipelines.*;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.camel.WfmProcessorInterface;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionResponseProcessor;
import org.mitre.mpf.wfm.camel.operations.detection.trackmerging.TrackMergingContext;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.transients.SystemPropertiesSnapshot;
import org.mitre.mpf.wfm.data.entities.persistent.MediaImpl;
import org.mitre.mpf.wfm.data.entities.transients.TransientPipeline;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Collections;

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
    private InProgressBatchJobsService inProgressJobs;

    @Autowired
    private PropertiesUtil propertiesUtil;

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

        Algorithm algorithm = new Algorithm(
                "detectionAlgo", "description", ActionType.DETECTION,
                new Algorithm.Requires(Collections.emptyList()),
                new Algorithm.Provides(Collections.emptyList(), Collections.emptyList()),
                true, true);
        Action action = new Action("detectionAction", "description", algorithm.getName(),
                                   Collections.emptyList());
        Task task = new Task("detectionTask", "description", Collections.singleton(action.getName()));
        Pipeline pipeline = new Pipeline("detectionPipeline", "description",
                                         Collections.singleton(task.getName()));
        TransientPipeline transientPipeline = new TransientPipeline(
                pipeline, Collections.singleton(task), Collections.singleton(action),
                Collections.singleton(algorithm));

        // Capture a snapshot of the detection system property settings when the job is created.
        SystemPropertiesSnapshot systemPropertiesSnapshot = propertiesUtil.createSystemPropertiesSnapshot();

        URI mediaUri = ioUtils.findFile("/samples/video_01.mp4");
        MediaImpl media = new MediaImpl(
                234234, mediaUri.toString(), UriScheme.get(mediaUri), Paths.get(mediaUri),
                Collections.emptyMap(), null);
        media.addMetadata("DURATION", "3004");
        media.addMetadata("FPS", "29.97");

        inProgressJobs.addJob(
                jobId,
                "234234",
                systemPropertiesSnapshot,
                transientPipeline,
                1,
                false,
                null,
                null,
                Collections.singletonList(media),
                Collections.emptyMap(),
                Collections.emptyMap());

        detectionResponseProcessor.wfmProcess(exchange);
        Assert.assertEquals(BatchJobStatusType.IN_PROGRESS_ERRORS, inProgressJobs.getJob(jobId).getStatus());

    }
}

