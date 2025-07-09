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

package org.mitre.mpf.wfm.camel.operations.detection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.TrackCache;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.JobPart;
import org.mitre.mpf.wfm.util.ObjectMapperFactory;
import org.mockito.Mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Streams;


public class TestRollUpProcessor extends MockitoTest.Strict {

    private static final String ROLL_UP_PATH
            = TestUtil.findFilePath("/test-rollup.json").toString();

    private static final long JOB_ID = 123;

    private static final long MEDIA_ID = 456;

    @Mock
    private InProgressBatchJobsService _mockInProgressJobs;

    @Mock
    private AggregateJobPropertiesUtil _mockAggregateJobPropertiesUtil;

    private final ObjectMapper _objectMapper = ObjectMapperFactory.customObjectMapper();

    private RollUpProcessor _rollUpProcessor;

    @Rule
    public TemporaryFolder _tempFolder = new TemporaryFolder();


    @Before
    public void init() {
        _rollUpProcessor = new RollUpProcessor(
                _mockInProgressJobs, _mockAggregateJobPropertiesUtil, _objectMapper);
    }

    @Test
    public void testRollUpDisabled() {
        var track = createTrack(Map.of("CLASSIFICATION", "truck"));
        var trackCache = runTest("", track);
        assertNoUpdatedTracksStored(trackCache);
        var outputTrack = trackCache.getTracks(MEDIA_ID, 0).iterator().next();
        assertSame(outputTrack, track);
    }


    @Test
    public void testNoTracks() {
        var trackCache = runTest(ROLL_UP_PATH, null);
        assertNoUpdatedTracksStored(trackCache);
    }

    @Test
    public void testNoMatchingProperty() {
        var track = createTrack(
                Map.of("A", "B"), List.of(Map.of("A", "B"), Map.of("A", "B")));
        var trackCache = runTest(ROLL_UP_PATH, track);
        assertNoUpdatedTracksStored(trackCache);
    }


    @Test
    public void testCopyEnabledButNoRollUpMemberFound() {
        var inputTrack = createTrack(
                Map.of("CLASSIFICATION", "B"), List.of(Map.of("A", "B")));
        var trackCache = runTest(ROLL_UP_PATH, inputTrack);
        var updatedTracks = trackCache.getTracks(MEDIA_ID, 0);
        assertEquals(1, updatedTracks.size());
        var track = updatedTracks.first();

        assertEquals(
                Map.of("CLASSIFICATION", "B", "CLASSIFICATION NO ROLL UP", "B"),
                track.getTrackProperties());

        assertSame(inputTrack.getDetections(), track.getDetections());
    }


    @Test
    public void testNoCopyAndNoRollUpMember() {
        var inputTrack = createTrack(Map.of("NO COPY PROP", "NOT IN MEMBER LIST"));
        var trackCache = runTest(ROLL_UP_PATH, inputTrack);
        assertNoUpdatedTracksStored(trackCache);
    }


    @Test
    public void testAllEnabled() {
        var inputTrack = createTrack(Map.of(
                "CLASSIFICATION", "motorbike",
                "NOT PROCESSED", "bus",
                "NO COPY PROP", "g1member1",
                "COPY ONLY", "COPY ONLY VALUE"), List.of(
                    Map.of("CLASSIFICATION", "bicycle", "NO COPY PROP", "NOT IN MEMBER LIST1"),
                    Map.of("NO COPY PROP", "NOT IN MEMBER LIST2")
                ));
        var detection2Input = inputTrack.getDetections().last();

        var trackCache = runTest(ROLL_UP_PATH, inputTrack);

        var outputTrack = trackCache.getTracks(MEDIA_ID, 0).first();
        var expectedTrackProperties = Map.of(
            "CLASSIFICATION", "vehicle",
            "CLASSIFICATION NO ROLL UP", "motorbike",
            "NOT PROCESSED", "bus",
            "NO COPY PROP", "group1",
            "COPY ONLY", "COPY ONLY VALUE",
            "COPY ONLY TARGET", "COPY ONLY VALUE"
        );
        assertEquals(expectedTrackProperties, outputTrack.getTrackProperties());

        var expectedDetection1Properties = Map.of(
            "CLASSIFICATION", "vehicle",
            "CLASSIFICATION NO ROLL UP", "bicycle",
            "NO COPY PROP", "NOT IN MEMBER LIST1");
        assertEquals(
                expectedDetection1Properties,
                outputTrack.getDetections().first().getDetectionProperties());
        assertSame(detection2Input, outputTrack.getDetections().last());
    }



    @Test
    public void testMultipleMemberMapping() throws IOException {
        assertInvalidRollUpJson("""
            [
                {
                    "propertyToProcess": "CLASSIFICATION",
                    "groups": [
                        {
                            "rollUp": "vehicle",
                            "members": [
                                "truck",
                                "car"
                            ]
                        }
                    ]
                },
                {
                    "propertyToProcess": "CLASSIFICATION",
                    "groups": [
                        {
                            "rollUp": "other",
                            "members": [
                                "truck",
                                "asdf"
                            ]
                        }
                    ]
                }

            ]""");
    }


    @Test
    public void testCopyOverwritesRollUp() throws IOException {
        assertInvalidRollUpJson("""
            [
                {
                    "propertyToProcess": "CLASSIFICATION",
                    "groups": [
                        {
                            "rollUp": "vehicle",
                            "members": [
                                "truck",
                                "car"
                            ]
                        }
                    ]
                },
                {
                    "propertyToProcess": "CLASSIFICATION2",
                    "originalPropertyCopy": "CLASSIFICATION",
                    "groups": [
                        {
                            "rollUp": "other",
                            "members": [
                                "asdf"
                            ]
                        }
                    ]
                }

            ]""");
    }


    @Test
    public void testCopyOverwritesOtherCopy() throws IOException {
        assertInvalidRollUpJson("""
            [
                {
                    "propertyToProcess": "CLASSIFICATION",
                    "originalPropertyCopy": "CLASSIFICATION NO ROLL UP",
                    "groups": [
                        {
                            "rollUp": "vehicle",
                            "members": [
                                "truck",
                                "car"
                            ]
                        }
                    ]
                },
                {
                    "propertyToProcess": "CLASSIFICATION2",
                    "originalPropertyCopy": "CLASSIFICATION NO ROLL UP",
                    "groups": [
                        {
                            "rollUp": "other",
                            "members": [
                                "asdf"
                            ]
                        }
                    ]
                }

            ]""");
    }


    private void assertInvalidRollUpJson(String json) throws IOException {
        var rollUpPath = _tempFolder.newFile().toPath();
        Files.writeString(rollUpPath, json);
        var trackCache = runTest(rollUpPath.toString(), null);
        verify(_mockInProgressJobs)
                .addError(eq(JOB_ID), eq(MEDIA_ID), eq(IssueCodes.OTHER), anyString());
        assertNoUpdatedTracksStored(trackCache);

    }


    @Test
    public void testMissingRollUpFile() {
        var trackCache = runTest("no_file.json", null);
        verify(_mockInProgressJobs)
                .addError(eq(JOB_ID), eq(MEDIA_ID), eq(IssueCodes.OTHER), anyString());
        assertNoUpdatedTracksStored(trackCache);
    }


    private TrackCache runTest(String rollUpPath, Track track) {
        var testTask = new Task("", "", List.of("ACTION"));
        var pipelineElements = mock(JobPipelineElements.class);
        when(pipelineElements.getTask(0))
                .thenReturn(testTask);

        var job = mock(BatchJob.class);
        lenient().when(job.getId())
                .thenReturn(JOB_ID);
        when(job.getPipelineElements())
                .thenReturn(pipelineElements);

        when(_mockInProgressJobs.getJob(JOB_ID))
                .thenReturn(job);

        var media = mock(Media.class);
        lenient().when(media.getId())
            .thenReturn(MEDIA_ID);

        when(job.getMedia())
            .thenReturn(List.of(media));

        when(_mockAggregateJobPropertiesUtil.getValue(eq(MpfConstants.ROLL_UP_FILE), any(JobPart.class)))
            .thenReturn(rollUpPath);

        if (track != null) {
            when(_mockInProgressJobs.getTracks(JOB_ID, MEDIA_ID, 0, 0))
                .thenReturn(ImmutableSortedSet.of(track));
        }

        var trackCache = new TrackCache(JOB_ID, 0, _mockInProgressJobs);
        var exchange = TestUtil.createTestExchange();
        exchange.getIn().setBody(trackCache);

        _rollUpProcessor.wfmProcess(exchange);
        return exchange.getOut().getBody(TrackCache.class);
    }


    private void assertNoUpdatedTracksStored(TrackCache trackCache) {
        trackCache.commit();
        verify(_mockInProgressJobs, never())
            .setTracks(anyLong(), anyLong(), anyInt(), anyInt(), any());
    }


    private Track createTrack(Map<String, String> trackProperties) {
        return createTrack(trackProperties, List.of(Map.of()));
    }

    private static Track createTrack(
            Map<String, String> trackProperties,
            List<Map<String, String>> detectionPropertyMaps) {

        var detections = Streams.mapWithIndex(
                detectionPropertyMaps.stream(), TestRollUpProcessor::createDetection)
                .toList();
        return new Track(
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                detections, trackProperties, null, null, null, null);
    }


    private static Detection createDetection(Map<String, String> detectionProperties, long index) {
        // Use index to set mediaOffsetFrame so that the detections will be sorted in a
        // predictable way.
        return new Detection(0, 0, 0, 0, 0, (int) index, 0, detectionProperties);
    }
}
