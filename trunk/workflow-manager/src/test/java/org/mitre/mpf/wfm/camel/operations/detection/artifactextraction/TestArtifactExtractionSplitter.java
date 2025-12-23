/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.ActionType;
import org.mitre.mpf.rest.api.pipelines.Algorithm;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.TrackCache;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.SystemPropertiesSnapshot;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.ArtifactExtractionPolicy;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.service.TaskAnnotatorService;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;

import java.nio.file.Paths;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class TestArtifactExtractionSplitter {

    private final InProgressBatchJobsService _mockInProgressJobs = mock(InProgressBatchJobsService.class);

    private final AggregateJobPropertiesUtil _mockAggregateJobPropertiesUtil = mock(AggregateJobPropertiesUtil.class);

    private final TaskAnnotatorService _mockTaskAnnotatorService = mock(TaskAnnotatorService.class);


    private final ArtifactExtractionSplitterImpl _artifactExtractionSplitter = new ArtifactExtractionSplitterImpl(
            _mockInProgressJobs,
            _mockAggregateJobPropertiesUtil,
            _mockTaskAnnotatorService);



    ////////////////////////////////////////////////////////
    @Test
    public void canGetFirstFrame() {
        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                createExtractionPropertySnapshot(-1, true, false, false, 0, "", ""),
                10, // Exemplar
                Arrays.asList(5, 9, 10), // Detection frames
                Arrays.asList(5)); // Expected artifact frames
    }

    @Test
    public void canGetMiddleFrame() {
        SystemPropertiesSnapshot extractionProps = createExtractionPropertySnapshot(
                -1, false, true, false, 0, "", "");

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10, // Exemplar
                Arrays.asList(5, 9, 10), // Detection frames
                Arrays.asList(9)); // Expected artifact frames

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
                createExtractionPropertySnapshot(-1, false, false, true, 0, "", ""),
                10, // Exemplar
                Arrays.asList(5, 9, 10), // Detection frames
                Arrays.asList(10)); // Expected artifact frames
    }

    //////////////////////////////////////////////////////////
    @Test
    public void canGetFirstAndMiddleFrame() {
        SystemPropertiesSnapshot extractionProps = createExtractionPropertySnapshot(
                -1, true, true, false, 0, "", "");

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                Arrays.asList(5, 9, 10),
                Arrays.asList(5, 9));

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                Arrays.asList(5, 9, 10, 20),
                Arrays.asList(5, 10));
    }


    @Test
    public void canGetFirstAndLastFrame() {
        SystemPropertiesSnapshot extractionProps = createExtractionPropertySnapshot(
                -1, true, false, true, 0, "", "");

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
                -1, false, true, true, 0, "", "");

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                Arrays.asList(5, 9, 10),
                Arrays.asList(9, 10));

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
                createExtractionPropertySnapshot(0, true, false, false, 0, "CONFIDENCE", ""),
                10,
                Arrays.asList(5, 9, 10),
                Arrays.asList(5, 10));
    }

    @Test
    public void canGetMiddleFrameAndExemplar() {
        SystemPropertiesSnapshot extractionProps = createExtractionPropertySnapshot(
                0, false, true, false, 0, "CONFIDENCE", "");

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                Arrays.asList(5, 9, 10),
                Arrays.asList(9, 10));

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10, // Exemplar
                Arrays.asList(5, 6, 7, 8, 9, 10, 11),
                Arrays.asList(8, 10));
    }

    @Test
    public void canGetLastFrameAndExemplar() {
        SystemPropertiesSnapshot extractionProps = createExtractionPropertySnapshot(
                0, false, false, true, 0, "CONFIDENCE", "");

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
                -1, false, false, false, 2, "CONFIDENCE", "");

        ImmutableMap<Integer, Float> detectionFramesAndConfidences = ImmutableMap.of(
                5, 0.5f,
                9, 0.0f,
                10, 1.0f,
                11, 0.9f,
                14, 0.9f);

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                detectionFramesAndConfidences,
                Arrays.asList(10, 11));


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


    /////////////////////////////////////////////////////////
    @Test
    public void canGetBestDetectionOnly() {
        SystemPropertiesSnapshot extractionProps = createExtractionPropertySnapshot(
                -1, false, false, false, 0, "CONFIDENCE", "BEST_SIZE");

        ImmutableMap<Integer, Float> detectionFramesAndConfidences = ImmutableMap.of(
                5, 0.5f,
                9, 0.0f,
                10, 1.0f,
                11, 0.9f,
                14, 0.9f);

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                detectionFramesAndConfidences,
                Map.of(10, "BEST_SIZE"),
                Arrays.asList(10));
    }

    /////////////////////////////////////////////////////////
    @Test
    public void canGetTwoBestDetections() {
        SystemPropertiesSnapshot extractionProps = createExtractionPropertySnapshot(
                -1, false, false, false, 0, "CONFIDENCE", "BEST_SIZE; BEST_COLOR");

        ImmutableMap<Integer, Float> detectionFramesAndConfidences = ImmutableMap.of(
                5, 0.5f,
                9, 0.0f,
                10, 1.0f,
                11, 0.9f,
                14, 0.9f);

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                detectionFramesAndConfidences,
                Map.of(10, "BEST_SIZE", 9, "BEST_COLOR"),
                Arrays.asList(9, 10));
    }


    /////////////////////////////////////////////////////////
    @Test
    public void canGetTopQualityCountAndBestDetection() {
        SystemPropertiesSnapshot extractionProps = createExtractionPropertySnapshot(
                -1, false, false, false, 2, "CONFIDENCE", "BEST_SIZE");

        ImmutableMap<Integer, Float> detectionFramesAndConfidences = ImmutableMap.of(
                5, 0.5f,
                9, 0.0f,
                10, 1.0f,
                11, 0.9f,
                14, 0.9f);

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                detectionFramesAndConfidences,
                Map.of(5, "BEST_SIZE"),
                Arrays.asList(5, 10, 11));


        detectionFramesAndConfidences = ImmutableMap.of(
                5, 0.95f,
                9, 0.0f,
                10, 1.0f,
                14, 0.9f);

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                detectionFramesAndConfidences,
                Map.of(14, "BEST_SIZE"),
                Arrays.asList(5, 10, 14));


        detectionFramesAndConfidences = ImmutableMap.of(
            5, 0.95f,
            9, 0.0f,
            10, 1.0f,
            11, 0.9f,
            14, 0.9f);

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
            extractionProps,
            10,
            detectionFramesAndConfidences,
            Map.of(5, "BEST_SIZE"),
            Arrays.asList(5, 10));

    }


    @Test
    public void canGetFirstFrameAndConfidenceCount() {
        SystemPropertiesSnapshot extractionProps = createExtractionPropertySnapshot(
                -1, true, false, false, 2, "CONFIDENCE", "");

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
                -1, false, true, false, 2, "CONFIDENCE", "");

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
                -1, false, false, true, 2, "CONFIDENCE", "");

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
    public void canGetExemplarFramePlus() {
        SystemPropertiesSnapshot extractionProps = createExtractionPropertySnapshot(
                2, false, false, false, 0, "CONFIDENCE", "");

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                Arrays.asList(5, 9, 10, 20),
                Arrays.asList(5, 9, 10, 20));

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                Arrays.asList(8, 10, 12, 15, 16),
                Arrays.asList(8, 10, 12, 15));

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                6,
                Arrays.asList(5, 6, 9, 10, 11),
                Arrays.asList(5, 6, 9, 10));

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                11,
                Arrays.asList(5, 9, 10, 11),
                Arrays.asList(9, 10, 11));

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                5,
                Arrays.asList(5, 9, 10, 11),
                Arrays.asList(5, 9, 10));

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                5,
                Arrays.asList(5),
                Arrays.asList(5));
    }


    @Test
    public void canGetFirstFrameAndExemplarFramePlus() {
        SystemPropertiesSnapshot extractionProps = createExtractionPropertySnapshot(
                2, true, false, false, 0, "CONFIDENCE", "");

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                Arrays.asList(5, 9, 10, 20),
                Arrays.asList(5, 9, 10, 20));

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                5,
                Arrays.asList(5, 6, 9, 12),
                Arrays.asList(5, 6, 9));

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                13,
                Arrays.asList(5, 6, 9, 12, 13, 14, 15, 16),
                Arrays.asList(5, 9, 12, 13, 14, 15));
    }

    @Test
    public void canGetMiddleFrameAndExemplarFramePlus() {
        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                createExtractionPropertySnapshot(2, false, true, false, 0, "CONFIDENCE", ""),
                16,
                Arrays.asList(5, 9, 10, 16, 20),
                Arrays.asList(9, 10, 16, 20));

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                createExtractionPropertySnapshot(1, false, true, false, 0, "CONFIDENCE", ""),
                22,
                Arrays.asList(5, 9, 10, 16, 20, 21, 22, 23),
                Arrays.asList(16, 21, 22, 23));
    }

    @Test
    public void canGetLastFrameAndExemplarFramePlus() {
        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                createExtractionPropertySnapshot(2, false, false, true, 0, "CONFIDENCE", ""),
                16,
                Arrays.asList(5, 9, 10, 16, 20),
                Arrays.asList(9, 10, 16, 20));

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                createExtractionPropertySnapshot(1, false, false, true, 0, "CONFIDENCE", ""),
                9,
                Arrays.asList(5, 9, 10, 16, 20),
                Arrays.asList(5, 9, 10, 20));
    }

    @Test
    public void canGetNone() {
        runTest(ArtifactExtractionPolicy.NONE,
                createExtractionPropertySnapshot(2, false, false, true, 2, "", "BEST_SIZE"),
                16,
                Arrays.asList(5, 9, 10, 16, 20),
                Collections.emptyList());
    }


    //////////////////////////////////////////////////////////
    @Test
    public void canGetAllDetections() {
        SystemPropertiesSnapshot extractionProps = createExtractionPropertySnapshot(
                -1, false, false, false, 0, "", "");

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
                createExtractionPropertySnapshot(2, true, false, false, 1, "", ""),
                11,
                Arrays.asList(5, 9, 10, 11, 20),
                Arrays.asList(5, 9, 10, 11, 20));
    }

    //////////////////////////////////////////////////////////
    /// Test that setting the top confidence count to a value larger than the number of
    /// detections does not throw an exception, and extracts all detections.
    @Test
    public void topConfidenceCountTooLarge() {
        SystemPropertiesSnapshot extractionProps = createExtractionPropertySnapshot(
            -1, false, false, false, 12, "CONFIDENCE", "");

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
    @Test
    public void noMarkedDetectionsExtractsNothing() {
        SystemPropertiesSnapshot extractionProps = createExtractionPropertySnapshot(
            -1, false, false, false, 0, "CONFIDENCE", "BEST_SIZE");

        ImmutableMap<Integer, Float> detectionFramesAndConfidences = ImmutableMap.of(
                5, 0.5f,
                9, 0.0f,
                10, 1.0f,
                14, 0.0f);

        runTest(ArtifactExtractionPolicy.ALL_TYPES,
                extractionProps,
                10,
                detectionFramesAndConfidences,
                Collections.emptyList());

    }


    //////////////////////////////////////////////////////////

    private static SystemPropertiesSnapshot createExtractionPropertySnapshot(
            int framePlus, boolean first, boolean middle, boolean last, int qualityCount,
            String qualityProp, String bestDetectionNamesProp) {
        ImmutableMap<String, String> properties = new ImmutableMap.Builder<String,String>()
                .put("detection.artifact.extraction.policy.exemplar.frame.plus", String.valueOf(framePlus))
                .put("detection.artifact.extraction.policy.first.frame", String.valueOf(first))
                .put("detection.artifact.extraction.policy.middle.frame", String.valueOf(middle))
                .put("detection.artifact.extraction.policy.last.frame", String.valueOf(last))
                .put("detection.artifact.extraction.policy.top.quality.count", String.valueOf(qualityCount))
                .put("detection.quality.selection.prop", qualityProp)
                .put(ArtifactExtractionProcessor.BEST_DETECTION_PROP_NAMES, bestDetectionNamesProp)
                .build();
        return new SystemPropertiesSnapshot(properties);
    }


    // Simplified arguments for test cases where neither confidence nor best detection property matter.
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
                Map.of(), expectedFrames);
    }

    // Simplified arguments for test cases where best detection property doesn't matter.
    private void runTest(ArtifactExtractionPolicy extractionPolicy,
                         SystemPropertiesSnapshot systemPropertiesSnapshot,
                         int exemplarFrame,
                         Map<Integer, Float> detectionFramesAndConfidences,
                         Collection<Integer> expectedFrames) {

        runTest(extractionPolicy, systemPropertiesSnapshot, exemplarFrame, detectionFramesAndConfidences,
                Map.of(), expectedFrames);
    }

    // Full set of parameters for test cases that involve confidence values.
    private void runTest(ArtifactExtractionPolicy extractionPolicy,
                         SystemPropertiesSnapshot systemPropertiesSnapshot,
                         int exemplarFrame,
                         Map<Integer, Float> detectionFramesAndConfidences,
                         Map<Integer, String> bestDetectionFrames,
                         Collection<Integer> expectedFrames) {

        long jobId = 123;
        long mediaId = 5321;
        BatchJob job = mock(BatchJob.class, RETURNS_DEEP_STUBS);
        when(job.getId())
                .thenReturn(jobId);
        when(job.getSystemPropertiesSnapshot())
                .thenReturn(systemPropertiesSnapshot);
        when(_mockInProgressJobs.getJob(jobId))
                .thenReturn(job);

        ImmutableSortedSet<Track> tracks = ImmutableSortedSet.of(createTrack(exemplarFrame,
                                                                             detectionFramesAndConfidences,
                                                                             bestDetectionFrames));
        when(_mockInProgressJobs.getTracks(jobId, mediaId, 0, 0))
                .thenReturn(tracks);

        Media media = mock(Media.class);
        when(media.getId())
                .thenReturn(mediaId);
        when(media.getType())
                .thenReturn(Optional.of(MediaType.VIDEO));
        when(media.matchesType(MediaType.VIDEO, MediaType.IMAGE))
                .thenReturn(true);
        when(media.getLength())
                .thenReturn(OptionalInt.of(1000));
        when(media.getProcessingPath())
                .thenReturn(Paths.get("/test/path"));
        when(job.getMedia())
                .then(i -> ImmutableList.of(media));

        when(_mockTaskAnnotatorService.createIsAnnotatedChecker(job, media))
                .thenReturn(t -> false);

        var algorithm = new Algorithm(
                "Test Algo", null, ActionType.DETECTION, null, null, null, null, false, false);
        var action = new Action("Test Action", null, algorithm.name(), List.of());
        var task = new Task(null, null, List.of(action.name()));

        JobPipelineElements pipelineElements = mock(JobPipelineElements.class, RETURNS_DEEP_STUBS);
        when(job.getPipelineElements())
                .thenReturn(pipelineElements);
        when(pipelineElements.getAction(anyInt(), anyInt()))
             .thenReturn(action);
        when(pipelineElements.getTask(anyInt()))
                .thenReturn(task);


        when(pipelineElements.getTaskCount())
                .thenReturn(1);

        when(pipelineElements.getAlgorithm(anyInt(), anyInt()))
                .thenReturn(algorithm);

        when(_mockAggregateJobPropertiesUtil.getCombinedProperties(job, media, action))
                .thenReturn(pName -> pName.equals(ArtifactExtractionProcessor.POLICY)
                            ? extractionPolicy.name()
                            : null);

        when(_mockAggregateJobPropertiesUtil.getValue(eq(ArtifactExtractionProcessor.EXEMPLAR_FRAME_PLUS_POLICY), any(BatchJob.class), any(Media.class), any(Action.class)))
        .thenReturn(systemPropertiesSnapshot.lookup("detection.artifact.extraction.policy.exemplar.frame.plus"));
        when(_mockAggregateJobPropertiesUtil.getValue(eq(ArtifactExtractionProcessor.FIRST_FRAME_POLICY), any(BatchJob.class), any(Media.class), any(Action.class)))
        .thenReturn(systemPropertiesSnapshot.lookup("detection.artifact.extraction.policy.first.frame"));
        when(_mockAggregateJobPropertiesUtil.getValue(eq(ArtifactExtractionProcessor.MIDDLE_FRAME_POLICY), any(BatchJob.class), any(Media.class), any(Action.class)))
        .thenReturn(systemPropertiesSnapshot.lookup("detection.artifact.extraction.policy.middle.frame"));
        when(_mockAggregateJobPropertiesUtil.getValue(eq(ArtifactExtractionProcessor.LAST_FRAME_POLICY), any(BatchJob.class), any(Media.class), any(Action.class)))
        .thenReturn(systemPropertiesSnapshot.lookup("detection.artifact.extraction.policy.last.frame"));
        when(_mockAggregateJobPropertiesUtil.getValue(eq(ArtifactExtractionProcessor.TOP_QUALITY_COUNT), any(BatchJob.class), any(Media.class), any(Action.class)))
        .thenReturn(systemPropertiesSnapshot.lookup("detection.artifact.extraction.policy.top.quality.count"));
        when(_mockAggregateJobPropertiesUtil.getValue(eq("QUALITY_SELECTION_PROPERTY"), any(BatchJob.class), any(Media.class), any(Action.class)))
        .thenReturn(systemPropertiesSnapshot.lookup("detection.quality.selection.prop"));
        when(_mockAggregateJobPropertiesUtil.getValue(eq(ArtifactExtractionProcessor.BEST_DETECTION_PROP_NAMES), any(BatchJob.class), any(Media.class), any(Action.class)))
        .thenReturn(systemPropertiesSnapshot.lookup(ArtifactExtractionProcessor.BEST_DETECTION_PROP_NAMES));


        Exchange exchange = TestUtil.createTestExchange();
        var trackCache = new TrackCache(jobId, 0, _mockInProgressJobs);
        exchange.getIn().setBody(trackCache);


        List<Message> resultMessages = _artifactExtractionSplitter.wfmSplit(exchange);

        ImmutableSet<Integer> actualFrameNumbers = resultMessages.stream()
                .map(m -> m.getBody(ArtifactExtractionRequest.class))
                .flatMap(req -> req.getExtractionsMap().keySet().stream())
                .collect(ImmutableSet.toImmutableSet());

        assertEquals(ImmutableSet.copyOf(expectedFrames), actualFrameNumbers);
    }


    private static Track createTrack(int exemplarFrame,
                                     Map<Integer, Float> detectionFramesAndConfidences,
                                     Map<Integer, String> bestDetections) {
        Track track = mock(Track.class);

        ImmutableSortedSet.Builder<Detection> detectionsBuilder = ImmutableSortedSet.naturalOrder();
        Detection exemplar = null;

        for (Map.Entry<Integer, Float> entry : detectionFramesAndConfidences.entrySet()) {
            int frameNumber = entry.getKey();
            float confidence = entry.getValue();
            Map<String, String> bestDetectionProp = new HashMap<>();
            if (bestDetections.keySet().contains(frameNumber)) {
                bestDetectionProp.put(bestDetections.get(frameNumber), "true");
            }
            if (frameNumber == exemplarFrame) {
                exemplar = createDetection(frameNumber, confidence, bestDetectionProp);
                detectionsBuilder.add(exemplar);
            }
            else {
                detectionsBuilder.add(createDetection(frameNumber, confidence, bestDetectionProp));
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

    private static Detection createDetection(int frameNumber, float confidence, Map<String,String> props) {
        return new Detection(0, 0, 0, 0, confidence, frameNumber, 0, props);
    }
}
