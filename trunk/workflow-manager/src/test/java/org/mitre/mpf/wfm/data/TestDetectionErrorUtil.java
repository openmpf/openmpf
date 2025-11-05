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


package org.mitre.mpf.wfm.data;

import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.interop.JsonIssueDetails;
import org.mitre.mpf.rest.api.pipelines.Algorithm;
import org.mitre.mpf.wfm.data.entities.persistent.*;
import org.mitre.mpf.wfm.enums.MediaType;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestDetectionErrorUtil {

    private static final String _TEST_ALGO1_NAME = "TEST ALGORITHM 1";
    private static final String _TEST_ALGO2_NAME = "TEST ALGORITHM 2";

    private static final long _VIDEO_1_ID = 694;
    private static final long _VIDEO_2_ID = 987;
    private static final long _IMAGE_1_ID = 963;
    private static final long _AUDIO_1_ID = 417;
    private static final long _GENERIC_1_ID = 874;


    private BatchJobImpl _testJob;


    @Before
    public void init() {
        var video1 = mock(MediaImpl.class);
        when(video1.getId())
                .thenReturn(_VIDEO_1_ID);
        when(video1.matchesType(MediaType.VIDEO))
                .thenReturn(true);

        var video2 = mock(MediaImpl.class);
        when(video2.getId())
                .thenReturn(_VIDEO_2_ID);
        when(video2.matchesType(MediaType.VIDEO))
                .thenReturn(true);

        var image1 = mock(MediaImpl.class);
        when(image1.getId())
                .thenReturn(_IMAGE_1_ID);
        when(image1.matchesType(MediaType.IMAGE))
                .thenReturn(true);


        var audio1 = mock(MediaImpl.class);
        when(audio1.getId())
                .thenReturn(_AUDIO_1_ID);
        when(audio1.matchesType(MediaType.AUDIO))
                .thenReturn(true);

        var generic1 = mock(MediaImpl.class);
        when(generic1.getId())
                .thenReturn(_GENERIC_1_ID);
        when(generic1.matchesType(MediaType.UNKNOWN))
                .thenReturn(true);

        var mockPipelineElements = mock(JobPipelineElements.class);
        when(mockPipelineElements.getName())
                .thenReturn("TEST PIPELINE");

        var algorithm1 = new Algorithm(
                _TEST_ALGO1_NAME, null, null, null, null, null, null, false, false);
        when(mockPipelineElements.getAlgorithm(0, 0))
                .thenReturn(algorithm1);

        var algorithm2 = new Algorithm(
                _TEST_ALGO2_NAME, null, null, null, null, null, null, false, false);
        when(mockPipelineElements.getAlgorithm(1, 0))
                .thenReturn(algorithm2);


        _testJob = new BatchJobImpl(
                753, "ext id", mock(SystemPropertiesSnapshot.class), mockPipelineElements,
                4, null, null,
                List.of(video1, video2, image1, audio1, generic1), Map.of(), Map.of(), false);
    }


    private void addError(long mediaId, int startFrame, int stopFrame, String errorCode, String errorMessage) {
        _testJob.addDetectionProcessingError(new DetectionProcessingError(
                _testJob.getId(), mediaId, 0, 0, startFrame, stopFrame, 0, 0,
                errorCode, errorMessage));
    }




    @Test
    public void canMergeAdjacentSegments() {
        addError(_VIDEO_1_ID, 0, 9, "ec1", "em1");
        addError(_VIDEO_1_ID, 10, 19, "ec1", "em1");
        addError(_VIDEO_1_ID, 20, 29, "ec1", "em1");

        var mergedErrors = DetectionErrorUtil.getMergedDetectionErrors(_testJob);
        assertEquals(1, mergedErrors.size());

        JsonIssueDetails issue = mergedErrors.get(_VIDEO_1_ID).iterator().next();
        assertEquals("ec1", issue.getCode());
        assertEquals(_TEST_ALGO1_NAME, issue.getSource());
        assertEquals("em1 (Frames: 0 - 29)", issue.getMessage());
    }


    @Test
    public void canMergeDisconnectedSegments() {
        addError(_VIDEO_1_ID, 0, 9, "ec1", "em1");
        addError(_VIDEO_1_ID, 10, 19, "ec1", "em1");

        addError(_VIDEO_1_ID, 30, 39, "ec1", "em1");
        addError(_VIDEO_1_ID, 40, 49, "ec1", "em1");

        var mergedErrors = DetectionErrorUtil.getMergedDetectionErrors(_testJob);
        assertEquals(1, mergedErrors.size());

        JsonIssueDetails issue = mergedErrors.get(_VIDEO_1_ID).iterator().next();
        assertEquals("ec1", issue.getCode());
        assertEquals(_TEST_ALGO1_NAME, issue.getSource());
        assertEquals("em1 (Frames: 0 - 19, 30 - 49)", issue.getMessage());
    }


    @Test
    public void doesNotMergeWhenDifferentMedia() {
        addError(_VIDEO_1_ID, 0, 9, "ec1", "em1");
        addError(_VIDEO_2_ID, 10, 19, "ec1", "em1");

        var mergedErrors = DetectionErrorUtil.getMergedDetectionErrors(_testJob);
        assertEquals(2, mergedErrors.size());

        var video1Issue = mergedErrors.get(_VIDEO_1_ID).iterator().next();
        assertEquals("ec1", video1Issue.getCode());
        assertEquals(_TEST_ALGO1_NAME, video1Issue.getSource());
        assertEquals("em1 (Frames: 0 - 9)", video1Issue.getMessage());

        var video2Issue = mergedErrors.get(_VIDEO_2_ID).iterator().next();
        assertEquals("ec1", video2Issue.getCode());
        assertEquals(_TEST_ALGO1_NAME, video2Issue.getSource());
        assertEquals("em1 (Frames: 10 - 19)", video2Issue.getMessage());
    }


    @Test
    public void doesNotMergeWhenErrorCodeDifferent() {
        addError(_VIDEO_1_ID, 0, 9, "ec1", "em1");
        addError(_VIDEO_1_ID, 10, 19, "ec2", "em1");

        var mergedErrors = DetectionErrorUtil.getMergedDetectionErrors(_testJob);
        assertEquals(2, mergedErrors.size());

        var issue1 = mergedErrors.get(_VIDEO_1_ID)
                .stream()
                .filter(j -> j.getCode().equals("ec1"))
                .findAny()
                .orElseThrow();
        assertEquals("em1 (Frames: 0 - 9)", issue1.getMessage());
        assertEquals(_TEST_ALGO1_NAME, issue1.getSource());


        var issue2 = mergedErrors.get(_VIDEO_1_ID)
                .stream()
                .filter(j -> j.getCode().equals("ec2"))
                .findAny()
                .orElseThrow();
        assertEquals("em1 (Frames: 10 - 19)", issue2.getMessage());
        assertEquals(_TEST_ALGO1_NAME, issue2.getSource());
    }


    @Test
    public void doesNotMergeWhenErrorMessageDifferent() {
        addError(_VIDEO_1_ID, 0, 9, "ec1", "em1");
        addError(_VIDEO_1_ID, 10, 19, "ec1", "em2");

        var mergedErrors = DetectionErrorUtil.getMergedDetectionErrors(_testJob);
        assertEquals(2, mergedErrors.size());

        var issue1 = mergedErrors.get(_VIDEO_1_ID)
                .stream()
                .filter(j -> j.getMessage().equals("em1 (Frames: 0 - 9)"))
                .findAny()
                .orElseThrow();
        assertEquals("ec1", issue1.getCode());
        assertEquals(_TEST_ALGO1_NAME, issue1.getSource());


        var issue2 = mergedErrors.get(_VIDEO_1_ID)
                .stream()
                .filter(j -> j.getMessage().equals("em2 (Frames: 10 - 19)"))
                .findAny()
                .orElseThrow();
        assertEquals("ec1", issue2.getCode());
        assertEquals(_TEST_ALGO1_NAME, issue2.getSource());
    }


    @Test
    public void doesNotMergeWhenSourceDifferent() {
        addError(_VIDEO_1_ID, 0, 9, "ec1", "em1");

        _testJob.addDetectionProcessingError(new DetectionProcessingError(
                _testJob.getId(), _VIDEO_1_ID, 1, 0, 10, 19, 0, 0,
                "ec1", "em1"));



        var mergedErrors = DetectionErrorUtil.getMergedDetectionErrors(_testJob);
        assertEquals(2, mergedErrors.size());

        var issue1 = mergedErrors.get(_VIDEO_1_ID)
                .stream()
                .filter(j -> j.getSource().equals(_TEST_ALGO1_NAME))
                .findAny()
                .orElseThrow();
        assertEquals("em1 (Frames: 0 - 9)", issue1.getMessage());
        assertEquals("ec1", issue1.getCode());


        var issue2 = mergedErrors.get(_VIDEO_1_ID)
                .stream()
                .filter(j -> j.getSource().equals(_TEST_ALGO2_NAME))
                .findAny()
                .orElseThrow();
        assertEquals("em1 (Frames: 10 - 19)", issue2.getMessage());
        assertEquals("ec1", issue2.getCode());
    }


    @Test
    public void doesNotAddFrameNumbersToImageError() {
        addError(_IMAGE_1_ID, 0, 0, "ec1", "em1");
        addError(_IMAGE_1_ID, 0, 0, "ec1", "em1");

        var mergedErrors = DetectionErrorUtil.getMergedDetectionErrors(_testJob);
        assertEquals(1, mergedErrors.size());

        JsonIssueDetails issue = mergedErrors.get(_IMAGE_1_ID).iterator().next();
        assertEquals("ec1", issue.getCode());
        assertEquals(_TEST_ALGO1_NAME, issue.getSource());
        assertEquals("em1", issue.getMessage());
    }


    @Test
    public void doesNotAddFrameNumbersToAudioError() {
        addError(_AUDIO_1_ID, 0, 0, "ec1", "em1");
        addError(_AUDIO_1_ID, 0, 0, "ec1", "em1");

        var mergedErrors = DetectionErrorUtil.getMergedDetectionErrors(_testJob);
        assertEquals(1, mergedErrors.size());

        JsonIssueDetails issue = mergedErrors.get(_AUDIO_1_ID).iterator().next();
        assertEquals("ec1", issue.getCode());
        assertEquals(_TEST_ALGO1_NAME, issue.getSource());
        assertEquals("em1", issue.getMessage());
    }


    @Test
    public void doesNotAddFrameNumbersToGenericError() {
        addError(_GENERIC_1_ID, 0, 0, "ec1", "em1");
        addError(_GENERIC_1_ID, 0, 0, "ec1", "em1");

        var mergedErrors = DetectionErrorUtil.getMergedDetectionErrors(_testJob);
        assertEquals(1, mergedErrors.size());

        JsonIssueDetails issue = mergedErrors.get(_GENERIC_1_ID).iterator().next();
        assertEquals("ec1", issue.getCode());
        assertEquals(_TEST_ALGO1_NAME, issue.getSource());
        assertEquals("em1", issue.getMessage());
    }


    @Test
    public void canMergeSetOfFrames() {
        var frameRangeString = Stream.of(15, 5, 0, 1, 2, 3, 4, 5, 7, 8, 9, 10, 99, 17, 18)
                .collect(DetectionErrorUtil.toFrameRangesString());
        assertEquals("(Frames: 0 - 5, 7 - 10, 15, 17, 18, 99)", frameRangeString.get());


        frameRangeString = Stream.of(99).collect(DetectionErrorUtil.toFrameRangesString());
        assertEquals("(Frames: 99)", frameRangeString.get());

        frameRangeString = Stream.<Integer>empty().collect(DetectionErrorUtil.toFrameRangesString());
        assertTrue(frameRangeString.isEmpty());
    }
}
