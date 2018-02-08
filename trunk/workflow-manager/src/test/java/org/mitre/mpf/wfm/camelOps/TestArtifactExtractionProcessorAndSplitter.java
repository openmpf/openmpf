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
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultExchange;
import org.apache.commons.lang3.mutable.MutableInt;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runner.notification.RunListener;
import org.mitre.mpf.frameextractor.FrameExtractor;
import org.mitre.mpf.interop.JsonAction;
import org.mitre.mpf.interop.JsonJobRequest;
import org.mitre.mpf.interop.JsonPipeline;
import org.mitre.mpf.interop.JsonStage;
import org.mitre.mpf.wfm.camel.WfmSplitterInterface;
import org.mitre.mpf.wfm.camel.operations.detection.artifactextraction.ArtifactExtractionProcessorImpl;
import org.mitre.mpf.wfm.camel.operations.detection.artifactextraction.ArtifactExtractionProcessorInterface;
import org.mitre.mpf.wfm.camel.operations.detection.artifactextraction.ArtifactExtractionRequest;
import org.mitre.mpf.wfm.camel.operations.detection.artifactextraction.ArtifactExtractionSplitterImpl;
import org.mitre.mpf.wfm.camel.operations.detection.trackmerging.TrackMergingContext;
import org.mitre.mpf.wfm.data.entities.transients.TransientMedia;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.*;

import static org.mockito.Mockito.when;

@ContextConfiguration(locations = {"classpath:applicationContext.xml"})
@RunWith(SpringJUnit4ClassRunner.class)
@RunListener.ThreadSafe
public class TestArtifactExtractionProcessorAndSplitter {
    private static final Logger log = LoggerFactory.getLogger(TestArtifactExtractionProcessorAndSplitter.class);
    private static final int MINUTES = 1000*60; // 1000 milliseconds/second & 60 seconds/minute.

    @Autowired
    private ApplicationContext context;

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private IoUtils ioUtils;

    @Autowired
    private JsonUtils jsonUtils;

    @Mock
    private FrameExtractor mockFrameExtractor;

    @Autowired
    @Qualifier(ArtifactExtractionProcessorImpl.REF)
    private ArtifactExtractionProcessorInterface artifactExtractionProcessor;

    @Autowired
    @Qualifier(ArtifactExtractionSplitterImpl.REF)
    private WfmSplitterInterface artifactExtractionSplitter;

    final int mediaId = 12345;

    private static final MutableInt SEQUENCE = new MutableInt();
    public int next() {
        synchronized (SEQUENCE) {
            int next = SEQUENCE.getValue();
            SEQUENCE.increment();
            return next;
        }
    }

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test(timeout = 5 * MINUTES)
    public void testArtifactExtractionProcessorHeader() throws Exception {
        final long jobId = next();
        final String path = "/samples/five-second-marathon-clip.mkv";
        final int stageIndex = 1;
        Exchange exchange = new DefaultExchange(camelContext);
        ArtifactExtractionRequest request = new ArtifactExtractionRequest(jobId, mediaId, path, MediaType.VIDEO, stageIndex);
        exchange.getIn().getHeaders().put(MpfHeaders.JOB_ID, jobId);
        exchange.getIn().setBody(jsonUtils.serialize(request));
        artifactExtractionProcessor.process(exchange);
        Assert.assertTrue("JOB_ID header did not match", exchange.getIn().getHeader(MpfHeaders.JOB_ID).equals(jobId));
    }

    @Test(timeout = 5 * MINUTES)
    public void testArtifactExtractionProcessorError() throws Exception {
        final long jobId = next();
        final String path = "/samples/five-second-marathon-clip.mkv";
        final int stageIndex = 1;
        ArtifactExtractionRequest request = new ArtifactExtractionRequest(jobId, mediaId, path, MediaType.VIDEO, stageIndex);

        HashSet<Integer> extractableMediaIndexes = new HashSet<>();
        extractableMediaIndexes.add(0);
        extractableMediaIndexes.add(1);
        extractableMediaIndexes.add(2);
        extractableMediaIndexes.add(3);

        Map<Integer, Set<Integer>> actionIndexToMediaIndexes = new HashMap<>();
        actionIndexToMediaIndexes.put(0, new HashSet<>());
        actionIndexToMediaIndexes.get(0).addAll(extractableMediaIndexes);
        actionIndexToMediaIndexes.get(0).add(4); // unextractable

        request.setActionIndexToMediaIndexes(actionIndexToMediaIndexes);

        Map<Integer, String> mockResults = new HashMap<>();
        mockResults.put(0, "/dummy/path/0");
        mockResults.put(1, "/dummy/path/1");
        mockResults.put(2, "/dummy/path/2");
        mockResults.put(3, "/dummy/path/3");
        // 4th extraction is missing

        when(mockFrameExtractor.execute())
                .thenReturn(mockResults);

        Map<Integer, String> results = artifactExtractionProcessor.processVideoRequest(request, mockFrameExtractor);

        Assert.assertTrue("Could not extract media indexes.", results.keySet().containsAll(extractableMediaIndexes));

        for (Integer mediaIndex : mockResults.keySet()) {
            Assert.assertEquals("Wrong extraction path:", mockResults.get(mediaIndex), results.get(mediaIndex));
        }

        Assert.assertEquals("No error for bad media index:", ArtifactExtractionProcessorImpl.ERROR_PATH, results.get(4));
    }

    // TODO: Fix me!
    //@Test(timeout = 5 * MINUTES)
    public void testArtifactExtractionSplitter() throws Exception{
        final long jobId = next();
        Exchange exchange = new DefaultExchange(camelContext);
        final int stageIndex = 1;
        final int testPriority = 5;
        JsonAction action = new JsonAction("test_algorithm", "test_action_name", "test_action_description");
        JsonStage stage = new JsonStage("test_action_type", "test_stage_name", "test_stage_description");
        stage.getActions().add(action);
        JsonPipeline pipeline = new JsonPipeline("test_name", "test_description");
        pipeline.getStages().add(stage);
        JsonJobRequest jobRequest = new JsonJobRequest("test_id", true, pipeline, testPriority);
        TransientMedia transientMedia = new TransientMedia(next(), ioUtils.findFile("/samples/meds1.jpg").toString());
        //add redis job
        TrackMergingContext testContext = new TrackMergingContext(jobId, stageIndex);
        exchange.getIn().getHeaders().put(MpfHeaders.JOB_ID, next());
        exchange.getIn().setBody(jsonUtils.serialize(testContext));

        List<Message> responseList = artifactExtractionSplitter.split(exchange);

        Assert.assertTrue(responseList.size() != 0);
    }
}