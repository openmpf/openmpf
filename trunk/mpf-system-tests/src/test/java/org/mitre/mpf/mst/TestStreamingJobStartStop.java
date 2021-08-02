/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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


package org.mitre.mpf.mst;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.tuple.Pair;
import org.jgroups.Address;
import org.junit.*;
import org.junit.rules.TestWatcher;
import org.junit.runner.RunWith;
import org.mitre.mpf.interop.*;
import org.mitre.mpf.nms.AddressParser;
import org.mitre.mpf.nms.MasterNode;
import org.mitre.mpf.nms.NodeTypes;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.Pipeline;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.wfm.businessrules.StreamingJobRequestService;
import org.mitre.mpf.wfm.data.entities.persistent.*;
import org.mitre.mpf.wfm.enums.StreamingJobStatusType;
import org.mitre.mpf.wfm.service.StreamingJobMessageSender;
import org.mitre.mpf.wfm.service.component.ComponentStateService;
import org.mitre.mpf.wfm.service.pipeline.PipelineService;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.*;
import static org.mockito.AdditionalMatchers.geq;
import static org.mockito.AdditionalMatchers.gt;
import static org.mockito.Mockito.*;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(initializers = TestSystemWithDefaultConfig.AppCtxInit.class)
@DirtiesContext // Make sure TestStreamingJobStartStop does not use same application context as other tests.
public class TestStreamingJobStartStop {

    private static final Logger LOG = LoggerFactory.getLogger(TestStreamingJobStartStop.class);

    private static final StreamingJobRequestService _mockStreamingJobRequestService
            = mock(StreamingJobRequestService.class);

    @Configuration
    public static class TestConfig {

        @Bean
        @Primary
        public StreamingJobRequestService streamingJobRequestService() {
            return _mockStreamingJobRequestService;
        }
    }

    @Autowired(required = false)
    private StreamingJobMessageSender _jobSender;

    @Autowired(required = false)
    private MasterNode _masterNode;

    @Autowired
    private ObjectMapper _objectMapper;

    @Autowired
    private IoUtils _ioUtils;

    @Autowired
    private PipelineService _pipelineService;

    @Autowired
    private ComponentStateService _componentStateService;

    @ClassRule
    public static TestInfoLoggerClassRule _testInfoLoggerClassRule = new TestInfoLoggerClassRule();
    @Rule
    public TestWatcher _testInfoMethodRule = _testInfoLoggerClassRule.methodRule();


    @Before
    public void init() {
        reset(_mockStreamingJobRequestService);
        assumeTrue("Skipping tests in TestStreamingJobStartStop because StreamingJobMessageSender is not configured.",
                   _jobSender != null);
        assumeTrue("Skipping tests in TestStreamingJobStartStop because MasterNode is not configured.",
                   _masterNode != null);
    }


    @Test(timeout = 5 * 60_000)
    public void testJobStartStop() throws InterruptedException {
        var subsenseRcm = _componentStateService.getByComponentName("SubsenseMotionDetection")
                .orElse(null);
        assumeFalse("Skipping testJobStartStop because SubsenseMotionDetection component is not registered.",
                    subsenseRcm == null);
        assumeTrue(
                "Skipping testJobStartStop because SubsenseMotionDetection is registered as an unmanaged component.",
                subsenseRcm.isManaged());

        long jobId = 43231;
        long test_start_time = System.currentTimeMillis();

        waitForCorrectNodes();

        StreamingJob streamingJob = createJob(
                jobId,
                "SUBSENSE",
                "SUBSENSE MOTION DETECTION (WITH TRACKING) PIPELINE",
                "/samples/face/new_face_video.avi",
                10,
                1);

        _jobSender.launchJob(streamingJob);

        verify(_mockStreamingJobRequestService, timeout(30_000).atLeastOnce())
                .handleNewActivityAlert(eq(jobId), geq(0L), gt(test_start_time));

        _jobSender.stopJob(jobId);


        verify(_mockStreamingJobRequestService, timeout(30_000))
                .handleJobStatusChange(eq(jobId),
                                       hasStatus(StreamingJobStatusType.TERMINATED, StreamingJobStatusType.CANCELLED),
                                       gt(test_start_time));

        ArgumentCaptor<JsonSegmentSummaryReport> reportCaptor = ArgumentCaptor.forClass(JsonSegmentSummaryReport.class);

        verify(_mockStreamingJobRequestService, timeout(30_000).atLeastOnce())
                .handleNewSummaryReport(reportCaptor.capture());

        JsonSegmentSummaryReport summaryReport = reportCaptor.getValue();
        assertEquals(jobId, summaryReport.getJobId());

        boolean hasNonEmptyDetection = summaryReport.getDetectionTypes()
                .values()
                .stream()
                .flatMap(Collection::stream)
                .flatMap(t -> t.getDetections().stream())
                .anyMatch(d -> d.getHeight() > 0 && d.getWidth() > 0);

        assertTrue(hasNonEmptyDetection);
    }


    private static StreamingJobStatus hasStatus(StreamingJobStatusType... statuses) {
        return argThat(status -> Stream.of(statuses)
                        .anyMatch(t -> status.getType() == t));
            }


    private StreamingJob createJob(long jobId, String algorithm, String pipelineName,
                                            String mediaPath, int segmentSize, int stallTimeout) {
        Action action = new Action("Action1", "description", algorithm, Collections.emptyList());
        Task task = new Task("Task1", "description", Collections.singleton(action.getName()));
        Pipeline pipeline = new Pipeline(pipelineName, "desc", Collections.singleton(task.getName()));
        JobPipelineElements pipelineElements = new JobPipelineElements(
                pipeline, Collections.singleton(task), Collections.singleton(action),
                Collections.singleton(_pipelineService.getAlgorithm(algorithm)));


        URI videoUri = _ioUtils.findFile(mediaPath);
        MediaStreamInfo stream = new MediaStreamInfo(124, videoUri.toString(), segmentSize, Collections.emptyMap());

        return new StreamingJobImpl(
                jobId, "ext id", pipelineElements, stream, 1, stallTimeout, false,
                "mydir", null, null,
                Collections.emptyMap(), Collections.emptyMap());
    }


    private static boolean tracksMatch(JsonTrackOutputObject expectedTrack,
                                       JsonStreamingTrackOutputObject actualTrack) {
        return actualTrack.getStartOffsetFrame() == expectedTrack.getStartOffsetFrame()
                && actualTrack.getStopOffsetFrame() == expectedTrack.getStopOffsetFrame()
                && Math.abs(actualTrack.getConfidence() - expectedTrack.getConfidence()) < 0.01
                && actualTrack.getTrackProperties().equals(expectedTrack.getTrackProperties())
                && exemplarsMatch(expectedTrack, actualTrack);
    }

    private static boolean exemplarsMatch(JsonTrackOutputObject expectedTrack,
                                          JsonStreamingTrackOutputObject actualTrack) {
        JsonDetectionOutputObject expectedExemplar = expectedTrack.getExemplar();
        JsonStreamingDetectionOutputObject actualExemplar = actualTrack.getExemplar();

        return expectedExemplar.getX() == actualExemplar.getX()
                && expectedExemplar.getY() == actualExemplar.getY()
                && expectedExemplar.getWidth() == actualExemplar.getWidth()
                && expectedExemplar.getHeight() == actualExemplar.getHeight()
                && Math.abs(expectedExemplar.getConfidence() - actualExemplar.getConfidence()) < 0.01
                && expectedExemplar.getOffsetFrame() == actualExemplar.getOffsetFrame()
                && expectedExemplar.getDetectionProperties().equals(actualExemplar.getDetectionProperties());
    }


    private void waitForCorrectNodes() throws InterruptedException {
        while (!hasCorrectNodes()) {
            Thread.sleep(1000);
        }
    }

    // Sometimes when the test starts there are 2 master nodes.
    // This makes sure there is only one master node and only one child node.
    private boolean hasCorrectNodes() {
        List<Address> currentNodes = _masterNode.getCurrentNodeManagerHosts();
        Map<NodeTypes, Long> nodeTypeCounts = currentNodes
                .stream()
                .map(AddressParser::parse)
                .collect(groupingBy(Pair::getRight, counting()));

        long masterNodeCount = nodeTypeCounts.getOrDefault(NodeTypes.MasterNode, 0L);
        long childNodeCount = nodeTypeCounts.getOrDefault(NodeTypes.NodeManager, 0L);
        if (masterNodeCount == 1 && childNodeCount == 1) {
            return true;
        }

        String currentNodeList = currentNodes.stream()
                .map(Object::toString)
                .collect(joining("\n"));

        LOG.warn("Current Nodes:\n{}", currentNodeList);

        if (masterNodeCount != 1) {
            LOG.warn("Incorrect number of master nodes. Expected 1 but there were {}.", masterNodeCount);
        }
        if (childNodeCount != 1) {
            LOG.warn("Incorrect number of child nodes. Expected 1 but there were {}.", childNodeCount);
        }
        return false;
    }
}
