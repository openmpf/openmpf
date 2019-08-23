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

package org.mitre.mpf.wfm.camel.operations.detection.artifactextraction;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.Test;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.camel.operations.detection.trackmerging.TrackMergingContext;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.ArtifactExtractionPolicy;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.ObjectMapperFactory;
import org.mitre.mpf.wfm.util.PropertiesUtil;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class TestArtifactExtractionSplitter {

    private final JsonUtils _jsonUtils = new JsonUtils(ObjectMapperFactory.customObjectMapper());
    private final InProgressBatchJobsService _mockInProgressJobs = mock(InProgressBatchJobsService.class);

    private final PropertiesUtil _mockPropertiesUtil = mock(PropertiesUtil.class);

    private final AggregateJobPropertiesUtil _mockAggregateJobPropertiesUtil = mock(AggregateJobPropertiesUtil.class);

    private final ArtifactExtractionSplitterImpl _artifactExtractionSplitter = new ArtifactExtractionSplitterImpl(
            _jsonUtils,
            _mockInProgressJobs,
            _mockPropertiesUtil,
            _mockAggregateJobPropertiesUtil);

    ////////////////////////////////////////////////////////
    @Test
    @Before
    public void init() {
        _jsonUtils.init();
    }
    public void canGetFirstFrame() {
        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                createExtractionPropertySnapshot(-1, true, false, false, 0),
                10, // Exemplar
                Arrays.asList(5, 9, 10), // Detection frames
                Arrays.asList(5)); // Expected artifact frames
    }

    @Test
    public void canGetMiddleFrame() {
        SystemPropertiesSnapshot extractionProps = createExtractionPropertySnapshot(
                -1, false, true, false, 0);

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10, // Exemplar
                Arrays.asList(5, 9, 10), // Detection frames
                Arrays.asList(5)); // Expected artifact frames

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10, // Exemplar
                Arrays.asList(5, 6, 9, 10), // Detection frames
                Arrays.asList(6)); // Expected artifact frames

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10, // Exemplar
                Arrays.asList(5, 6, 9, 10, 11), // Detection frames
                Arrays.asList(9)); // Expected artifact frames

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10, // Exemplar
                Arrays.asList(5, 6, 7, 8, 9, 10), // Detection frames
                Arrays.asList(7)); // Expected artifact frames

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10, // Exemplar
                Arrays.asList(5, 6, 7, 8, 9, 10, 11), // Detection frames
                Arrays.asList(8)); // Expected artifact frames
    }

    @Test
    public void canGetLastFrame() {
        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                createExtractionPropertySnapshot(-1, false, false, true, 0),
                10, // Exemplar
                Arrays.asList(5, 9, 10), // Detection frames
                Arrays.asList(10)); // Expected artifact frames
    }

    //////////////////////////////////////////////////////////
    @Test
    public void canGetFirstAndMiddleFrame() {
        SystemPropertiesSnapshot extractionProps = createExtractionPropertySnapshot(
                -1, true, true, false, 0);

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                Arrays.asList(5, 9, 10),
                Arrays.asList(5));

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                Arrays.asList(5, 9, 10, 20),
                Arrays.asList(5, 10));
    }


    @Test
    public void canGetFirstAndLastFrame() {
        SystemPropertiesSnapshot extractionProps = createExtractionPropertySnapshot(
                -1, true, false, true, 0);

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                Arrays.asList(5, 9, 10),
                Arrays.asList(5, 10));

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                Arrays.asList(5, 10),
                Arrays.asList(5, 10));

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                5,
                Arrays.asList(5),
                Arrays.asList(5));
    }


    @Test
    public void canGetMiddleAndLastFrame() {
        SystemPropertiesSnapshot extractionProps = createExtractionPropertySnapshot(
                -1, false, true, true, 0);

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                Arrays.asList(5, 9, 10),
                Arrays.asList(5, 10));

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                Arrays.asList(5, 9, 10, 20),
                Arrays.asList(10, 20));
    }

    //////////////////////////////////////////////////////////
    @Test
    public void canGetFirstFrameAndExemplar() {
        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                createExtractionPropertySnapshot(0, true, false, false, 0),
                10,
                Arrays.asList(5, 9, 10),
                Arrays.asList(5, 10));
    }

    @Test
    public void canGetMiddleFrameAndExemplar() {
        SystemPropertiesSnapshot extractionProps = createExtractionPropertySnapshot(
                0, false, true, false, 0);

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                Arrays.asList(5, 9, 10),
                Arrays.asList(5, 10));

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10, // Exemplar
                Arrays.asList(5, 6, 7, 8, 9, 10, 11),
                Arrays.asList(8, 10));
    }

    @Test
    public void canGetLastFrameAndExemplar() {
        SystemPropertiesSnapshot extractionProps = createExtractionPropertySnapshot(
                0, false, false, true, 0);

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                Arrays.asList(5, 9, 10),
                Arrays.asList(10));

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                9,
                Arrays.asList(5, 9, 10),
                Arrays.asList(9, 10));
    }

    /////////////////////////////////////////////////////////
    @Test
    public void canGetTopConfidenceCount() {
        SystemPropertiesSnapshot extractionProps = createExtractionPropertySnapshot(
                -1, false, false, false, 2);

        ImmutableMap<Integer, Float> detectionFramesAndConfidences = ImmutableMap.of(
                5, 0.5f,
                9, 0.0f,
                10, 1.0f,
                14, 0.9f);

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                detectionFramesAndConfidences,
                Arrays.asList(10, 14));


        detectionFramesAndConfidences = ImmutableMap.of(
                5, 0.95f,
                9, 0.0f,
                10, 1.0f,
                14, 0.9f);

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                detectionFramesAndConfidences,
                Arrays.asList(5, 10));

        extractionProps = createExtractionPropertySnapshot(
                -1, false, false, false, 5);

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                detectionFramesAndConfidences,
                Arrays.asList(5, 9, 10, 14));

    }


    @Test
    public void canGetFirstFrameAndConfidenceCount() {
        SystemPropertiesSnapshot extractionProps = createExtractionPropertySnapshot(
                -1, true, false, false, 2);

        ImmutableMap<Integer, Float> detectionFramesAndConfidences = ImmutableMap.of(
                5, 0.5f,
                9, 0.0f,
                10, 1.0f,
                14, 0.9f);

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                detectionFramesAndConfidences,
                Arrays.asList(5, 10, 14));


        detectionFramesAndConfidences = ImmutableMap.of(
                5, 0.95f,
                9, 0.0f,
                10, 1.0f,
                14, 0.9f);

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                detectionFramesAndConfidences,
                Arrays.asList(5, 10));
    }

    @Test
    public void canGetMiddleFrameAndConfidenceCount() {
        SystemPropertiesSnapshot extractionProps = createExtractionPropertySnapshot(
                -1, false, true, false, 2);

        ImmutableMap<Integer, Float> detectionFramesAndConfidences = ImmutableMap.of(
                5, 0.5f,
                9, 0.0f,
                10, 1.0f,
                14, 0.9f);

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                detectionFramesAndConfidences,
                Arrays.asList(9, 10, 14));


        detectionFramesAndConfidences = ImmutableMap.of(
                5, 0.0f,
                9, 0.95f,
                10, 1.0f,
                14, 0.9f);

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                detectionFramesAndConfidences,
                Arrays.asList(9, 10));
    }


    @Test
    public void canGetLastFrameAndConfidenceCount() {
        SystemPropertiesSnapshot extractionProps = createExtractionPropertySnapshot(
                -1, false, false, true, 2);

        ImmutableMap<Integer, Float> detectionFramesAndConfidences = ImmutableMap.of(
                5, 0.5f,
                9, 0.0f,
                10, 1.0f,
                14, 0.0f);

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                detectionFramesAndConfidences,
                Arrays.asList(5, 10, 14));


        detectionFramesAndConfidences = ImmutableMap.of(
                5, 0.0f,
                9, 0.0f,
                10, 1.0f,
                14, 0.9f);

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                detectionFramesAndConfidences,
                Arrays.asList(10, 14));
    }

    //////////////////////////////////////////////////////////
    @Test
    public void canGetFramePlus() {
        SystemPropertiesSnapshot extractionProps = createExtractionPropertySnapshot(
                2, false, false, false, 0);

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                Arrays.asList(5, 9, 10, 20),
                Arrays.asList(8, 9, 10, 11, 12));

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                Arrays.asList(5, 9, 10, 11),
                Arrays.asList(8, 9, 10, 11));

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                6,
                Arrays.asList(5, 6, 9, 10, 11),
                Arrays.asList(5, 6, 7, 8));

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                11,
                Arrays.asList(5, 9, 10, 11),
                Arrays.asList(9, 10, 11));

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                5,
                Arrays.asList(5, 9, 10, 11),
                Arrays.asList(5, 6, 7));

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                5,
                Arrays.asList(5),
                Arrays.asList(5));
    }


    @Test
    public void canGetFirstFrameAndFramePlus() {
        SystemPropertiesSnapshot extractionProps = createExtractionPropertySnapshot(
                2, true, false, false, 0);

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                Arrays.asList(5, 9, 10, 20),
                Arrays.asList(5, 8, 9, 10, 11, 12));

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                5,
                Arrays.asList(5, 6),
                Arrays.asList(5, 6));
    }

    @Test
    public void canGetMiddleFrameAndFramePlus() {
        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                createExtractionPropertySnapshot(2, false, true, false, 0),
                16,
                Arrays.asList(5, 9, 10, 16, 20),
                Arrays.asList(10, 14, 15, 16, 17, 18));
    }

    @Test
    public void canGetLastFrameAndFramePlus() {
        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                createExtractionPropertySnapshot(2, false, false, true, 0),
                16,
                Arrays.asList(5, 9, 10, 16, 20),
                Arrays.asList(14, 15, 16, 17, 18, 20));
    }

    //////////////////////////////////////////////////////////
    @Test
    public void canGetAllFrames() {
        SystemPropertiesSnapshot extractionProps = createExtractionPropertySnapshot(
                -1, false, false, false, 0);

        runTest(ArtifactExtractionPolicy.ALL_DETECTIONS,
                extractionProps,
                10,
                Arrays.asList(5, 9, 10, 20),
                Arrays.asList(5, 9, 10, 20));

        runTest(ArtifactExtractionPolicy.ALL_DETECTIONS,
                extractionProps,
                11,
                Arrays.asList(11),
                Arrays.asList(11));


        runTest(ArtifactExtractionPolicy.ALL_DETECTIONS,
                createExtractionPropertySnapshot(2, true, false, false, 1),
                11,
                Arrays.asList(5, 9, 10, 11, 20),
                Arrays.asList(5, 9, 10, 11, 20));
    }

    //////////////////////////////////////////////////////////
    /// Test that setting the top confidence count to a value larger than the number of
    /// detections does not throw an exception, and extracts all detections.
    @Test(expected = Test.None.class)
    public void TopConfidenceCountTooLarge() {
        SystemPropertiesSnapshot extractionProps = createExtractionPropertySnapshot(
            -1, false, false, false, 12);

        ImmutableMap<Integer, Float> detectionFramesAndConfidences = ImmutableMap.of(
                5, 0.5f,
                9, 0.0f,
                10, 1.0f,
                14, 0.0f);

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                detectionFramesAndConfidences,
                Arrays.asList(5, 9, 10, 14));

    }

    //////////////////////////////////////////////////////////

    private static SystemPropertiesSnapshot createExtractionPropertySnapshot(
            int framePlus, boolean first, boolean middle, boolean last, int confidenceCount) {
        ImmutableMap<String, String> properties = ImmutableMap.of(
                "detection.artifact.extraction.policy.exemplar.frame.plus", String.valueOf(framePlus),
                "detection.artifact.extraction.policy.first.frame", String.valueOf(first),
                "detection.artifact.extraction.policy.middle.frame", String.valueOf(middle),
                "detection.artifact.extraction.policy.last.frame", String.valueOf(last),
                "detection.artifact.extraction.policy.top.confidence.count", String.valueOf(confidenceCount)
        );
        return new SystemPropertiesSnapshot(properties);
    }


    // Simplified arguments for test cases where confidences don't matter.
    private void runTest(ArtifactExtractionPolicy extractionPolicy,
                         SystemPropertiesSnapshot systemPropertiesSnapshot,
                         int exemplarFrame,
                         Collection<Integer> detectionFrames,
                         Collection<Integer> expectedFrames) {

        Map<Integer, Float> detectionFramesAndConfidences = new HashMap<>();
        for (int detectionFrame : detectionFrames) {
            detectionFramesAndConfidences.put(detectionFrame, 0.5f);
        }
        detectionFramesAndConfidences.put(exemplarFrame, 1.0f);
        runTest(extractionPolicy, systemPropertiesSnapshot, exemplarFrame, detectionFramesAndConfidences,
                expectedFrames);
    }


    // Full set of parameters for test cases that involve confidence values.
    private void runTest(ArtifactExtractionPolicy extractionPolicy,
                         SystemPropertiesSnapshot systemPropertiesSnapshot,
                         int exemplarFrame,
                         Map<Integer, Float> detectionFramesAndConfidences,
                         Collection<Integer> expectedFrames) {

        long jobId = 123;
        long mediaId = 5321;
        TransientJob job = mock(TransientJob.class, RETURNS_DEEP_STUBS);
        when(job.getId())
                .thenReturn(jobId);
        when(job.getSystemPropertiesSnapshot())
                .thenReturn(systemPropertiesSnapshot);
        when(_mockInProgressJobs.getJob(jobId))
                .thenReturn(job);

        ImmutableSortedSet<Track> tracks = ImmutableSortedSet.of(createTrack(exemplarFrame, detectionFramesAndConfidences));
        when(_mockInProgressJobs.getTracks(jobId, mediaId, 0, 0))
                .thenReturn(tracks);

        TransientMedia media = mock(TransientMedia.class);
        when(media.getId())
                .thenReturn(mediaId);
        when(media.getMediaType())
                .thenReturn(MediaType.VIDEO);
        when(media.getLength())
                .thenReturn(1000);
        when(job.getMedia())
                .then(i -> ImmutableList.of(media));

        TransientStage stage = mock(TransientStage.class, RETURNS_DEEP_STUBS);
        when(job.getPipeline().getStages().get(0))
                .thenReturn(stage);
        when(stage.getActions().size())
                .thenReturn(1);

        when(_mockAggregateJobPropertiesUtil.getCombinedProperties(job, mediaId, 0, 0))
                .thenReturn(pName -> pName.equals(MpfConstants.ARTIFACT_EXTRACTION_POLICY_PROPERTY)
                        ? extractionPolicy.name()
                        : null);

        Exchange exchange = TestUtil.createTestExchange();
        TrackMergingContext trackMergingContext = new TrackMergingContext(jobId, 0);
        exchange.getIn().setBody(_jsonUtils.serialize(trackMergingContext));


        List<Message> resultMessages = _artifactExtractionSplitter.wfmSplit(exchange);

        ImmutableSet<Integer> actualFrameNumbers = resultMessages.stream()
                .map(m -> _jsonUtils.deserialize(m.getBody(byte[].class), ArtifactExtractionRequest.class))
                .flatMap(req -> req.getFrameNumbers().stream())
                .collect(ImmutableSet.toImmutableSet());

        assertEquals(ImmutableSet.copyOf(expectedFrames), actualFrameNumbers);

    }


    private static Track createTrack(int exemplarFrame, Map<Integer, Float> detectionFramesAndConfidences) {
        Track track = mock(Track.class);

        ImmutableSortedSet.Builder<Detection> detectionsBuilder = ImmutableSortedSet.naturalOrder();
        Detection exemplar = null;

        for (Map.Entry<Integer, Float> entry : detectionFramesAndConfidences.entrySet()) {
            int frameNumber = entry.getKey();
            float confidence = entry.getValue();
            if (frameNumber == exemplarFrame) {
                exemplar = createDetection(frameNumber, confidence);
                detectionsBuilder.add(exemplar);
            }
            else {
                detectionsBuilder.add(createDetection(frameNumber, confidence));
            }
        }

        ImmutableSortedSet<Detection> detections = detectionsBuilder.build();

        when(track.getDetections())
                .thenReturn(detections);

        when(track.getExemplar())
                .thenReturn(exemplar);

        IntSummaryStatistics summaryStats = detections.stream()
                .mapToInt(Detection::getMediaOffsetFrame)
                .summaryStatistics();
        when(track.getStartOffsetFrameInclusive())
                .thenReturn(summaryStats.getMin());
        when(track.getEndOffsetFrameInclusive())
                .thenReturn(summaryStats.getMax());

        return track;
    }


    private static Detection createDetection(int frameNumber, float confidence) {
        return new Detection(0, 0, 0, 0, confidence, frameNumber, 0,
                             Collections.emptyMap());
    }
}
