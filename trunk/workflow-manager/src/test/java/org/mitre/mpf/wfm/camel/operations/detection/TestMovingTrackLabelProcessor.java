/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.TrackCache;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.MediaImpl;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TestMovingTrackLabelProcessor {

    @Test
    public void testLabelNoMotion() {
        {
            var track = createTrack(0, 0,
                                    List.of(createDetection(0, 479, 480, 640, 0)));
            var results = runProcessor(List.of(track));
            assertEquals(1, results.size());
            assertNoneMoving(results.get(0));
        }
        {
            var track = createTrack(0, 2, List.of(
                    createDetection(0, 479, 480, 640, 0),
                    createDetection(0, 479, 480, 640, 1),
                    createDetection(0, 479, 480, 640, 2)));
            var results = runProcessor(List.of(track));
            assertEquals(1, results.size());
            assertNoneMoving(results.get(0));
        }
        {
            var track = createTrack(0, 2, List.of(
                    createDetection(0, 479, 470, 630, 0),
                    createDetection(0, 479, 450, 610, 1),
                    createDetection(0, 479, 460, 620, 2)));
            var results = runProcessor(List.of(track));
            assertEquals(1, results.size());
            assertNoneMoving(results.get(0));
        }
    }


    @Test
    public void testLabelMotion() {
        var track = createTrack(0, 2, List.of(
                createDetection(300, 179, 48, 64, 0),
                createDetection(0, 29, 50, 60, 1),
                createDetection(500, 509, 100, 100, 2)));
        var results = runProcessor(List.of(track));
        assertEquals(1, results.size());
        assertAllMoving(results.get(0));
    }


    @Test
    public void testKeepAllTracks() {
        var track1 = createTrack(0, 2, List.of(
                createDetection(300, 179, 48, 64, 0),
                createDetection(0, 29, 50, 60, 1),
                createDetection(500, 509, 100, 100, 2)));

        var track2 = createTrack(0, 2, List.of(
                createDetection(0, 479, 470, 630, 0),
                createDetection(0, 479, 450, 610, 1),
                createDetection(0, 479, 460, 620, 2)));

        var updatedTracks = runProcessor(List.of(track1, track2));
        assertEquals(2, updatedTracks.size());
        assertExpectedTrackCount(1, 1, updatedTracks);
    }


    @Test
    public void testDropNotMovingTracks() {
        var track1 = createTrack(0, 2, List.of(
                createDetection(300, 179, 48, 64, 0),
                createDetection(0, 29, 50, 60, 1),
                createDetection(500, 509, 100, 100, 2)));

        var track2 = createTrack(0, 2, List.of(
                createDetection(0, 479, 470, 630, 0),
                createDetection(0, 479, 450, 610, 1),
                createDetection(0, 479, 460, 620, 2)));

        var updatedTracks = runProcessor(true, 0.7, 2, List.of(track1, track2));
        assertEquals(1, updatedTracks.size());
        assertExpectedTrackCount(1, 0, updatedTracks);
        assertAllMoving(updatedTracks.get(0));
    }


    @Test
    public void testPartiallyMovingTrack() {
        var track = createTrack(0, 3, List.of(
                createDetection(1, 1, 10, 10, 0),
                createDetection(1, 1, 10, 10, 1),
                createDetection(7, 7, 7, 5, 2),
                createDetection(7, 8, 6, 5, 3)));

        {
            var updatedTracks = runProcessor(false, 0.38, 3, List.of(track));
            assertEquals(1, updatedTracks.size());
            var updatedTrack = updatedTracks.get(0);
            assertNotMoving(updatedTrack.getTrackProperties());

            var detections = List.copyOf(updatedTrack.getDetections());
            assertNotMoving(detections.get(0).getDetectionProperties());
            assertNotMoving(detections.get(1).getDetectionProperties());
            assertMoving(detections.get(2).getDetectionProperties());
            assertMoving(detections.get(3).getDetectionProperties());
        }
        {
            var updatedTracks = runProcessor(false, 0.38, 2, List.of(track));
            assertEquals(1, updatedTracks.size());
            var updatedTrack = updatedTracks.get(0);
            assertMoving(updatedTrack.getTrackProperties());

            var detections = List.copyOf(updatedTrack.getDetections());
            assertNotMoving(detections.get(0).getDetectionProperties());
            assertNotMoving(detections.get(1).getDetectionProperties());
            assertMoving(detections.get(2).getDetectionProperties());
            assertMoving(detections.get(3).getDetectionProperties());
        }
    }



    private static List<Track> runProcessor(Collection<Track> inputTracks) {
        return runProcessor(false, 0.7, 2, inputTracks);
    }


    private static List<Track> runProcessor(
            boolean movingTracksOnly, double maxIou,
            int minDetections, Collection<Track> inputTracks) {

        var mockInProgressJobs = mock(InProgressBatchJobsService.class);

        var exchange = TestUtil.createTestExchange();
        long jobId = 43232;
        var trackCache = new TrackCache(jobId, 0, mockInProgressJobs);
        exchange.getIn().setBody(trackCache);

        var mockJob = mock(BatchJob.class, RETURNS_DEEP_STUBS);
        when(mockInProgressJobs.getJob(jobId))
                .thenReturn(mockJob);
        when(mockJob.getId())
                .thenReturn(jobId);
        when(mockJob.getPipelineElements().getTask(0).getActions().size())
                .thenReturn(1);

        long mediaId = 3242;
        var mockMedia = mock(MediaImpl.class);
        when(mockMedia.getId())
                .thenReturn(mediaId);
        when(mockJob.getMedia())
                .thenAnswer(inv -> ImmutableList.of(mockMedia));
        when(mockMedia.isFailed())
                .thenReturn(false);
        when(mockMedia.matchesType(MediaType.VIDEO))
                .thenReturn(true);

        var mockAggregateJobPropertiesUtil = mock(AggregateJobPropertiesUtil.class);

        var props = Map.of(
                MpfConstants.MOVING_TRACK_LABELS_ENABLED, "true",
                MpfConstants.MOVING_TRACKS_ONLY, String.valueOf(movingTracksOnly),
                MpfConstants.MOVING_TRACK_MAX_IOU, String.valueOf(maxIou),
                MpfConstants.MOVING_TRACK_MIN_DETECTIONS, String.valueOf(minDetections));

        when(mockAggregateJobPropertiesUtil.getCombinedProperties(
                same(mockJob), same(mockMedia), any(Action.class)))
                .thenReturn(props::get);

        when(mockInProgressJobs.getTracks(jobId, mediaId, 0, 0))
                .thenReturn(new TreeSet<>(inputTracks));

        new MovingTrackLabelProcessor(mockInProgressJobs, mockAggregateJobPropertiesUtil)
                .wfmProcess(exchange);
        assertSame(exchange.getIn().getBody(), exchange.getOut().getBody());

        return List.copyOf(trackCache.getTracks(mediaId, 0));
    }


    private static void assertAllMoving(Track track) {
        assertMoving(track.getTrackProperties());
        for (var detection : track.getDetections()) {
            assertMoving(detection.getDetectionProperties());
        }
    }

    private static void assertMoving(Map<String, String> properties) {
        assertEquals("TRUE", properties.get("MOVING"));
    }


    private static void assertNoneMoving(Track track)  {
        assertNotMoving(track.getTrackProperties());
        for (var detection : track.getDetections()) {
            assertNotMoving(detection.getDetectionProperties());
        }
    }

    private static void assertNotMoving(Map<String, String> properties) {
        assertEquals("FALSE", properties.get("MOVING"));
    }

    private static void assertExpectedTrackCount(int expectedNumMoving, int expectedNumNotMoving,
                                                 Iterable<Track> tracks) {
        int numMoving = 0;
        int numNotMoving = 0;
        for (var track : tracks) {
            String propVal = track.getTrackProperties().get("MOVING");
            assertNotNull(propVal);
            if (propVal.equals("TRUE")) {
                numMoving++;
            }
            else if (propVal.equals("FALSE")) {
                numNotMoving++;
            }
        }
        assertEquals(expectedNumMoving, numMoving);
        assertEquals(expectedNumNotMoving, numNotMoving);
    }


    private static Detection createDetection(int x, int y, int width, int height, int frame) {
        return new Detection(x, y, width, height, 1, frame, 0, Map.of());
    }

    private static Track createTrack(int startFrame, int endFrame, Iterable<Detection> detections) {
        return new Track(
                0, //jobId
                0, //mediaId
                0, //taskIndex
                0, //actionIndex
                startFrame, //startOffsetFrameInclusive
                endFrame,   //endOffsetFrameInclusive
                0, //startOffsetTimeInclusive
                1, //endOffsetTimeInclusive
                0,
                -1, //confidence
                detections, //detections
                Map.of(), //trackProperties
                "");
    }
}
