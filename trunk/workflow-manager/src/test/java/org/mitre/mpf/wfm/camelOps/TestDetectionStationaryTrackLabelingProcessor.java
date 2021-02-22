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

package org.mitre.mpf.wfm.camelOps;

import org.junit.Test;
import org.mitre.mpf.wfm.camel.operations.detection.stationarytracklabeling.StationaryTrackLabelingProcessor;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;

import java.util.Collections;
import java.util.Collection;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;

public class TestDetectionStationaryTrackLabelingProcessor {

    @Test
    public void testLabelTrackStationary() {
        {
            SortedSet<Detection> detections = new TreeSet<>();
            detections.add(createDetection(0, 479, 480, 640, 0));
            Track track = createTrack(0, 0, detections);

            Track updated = StationaryTrackLabelingProcessor.processTrack(1, 1, 0.6, 1, track);
            assertEquals("TRUE", updated.getTrackProperties().get("IS_STATIONARY_TRACK"));
        }

        {
            SortedSet<Detection> detections = new TreeSet<>();

            detections.add(createDetection(0, 479, 480, 640, 0));
            detections.add(createDetection(0, 479, 480, 640, 1));
            detections.add(createDetection(0, 479, 480, 640, 2));
            Track track = createTrack(0, 2, detections);

            Track updated = StationaryTrackLabelingProcessor.processTrack(1, 1, 0.6, 1, track);
            assertEquals("TRUE", updated.getTrackProperties().get("IS_STATIONARY_TRACK"));
        }

        {
            SortedSet<Detection> detections = new TreeSet<>();

            detections.add(createDetection(0, 479, 470, 630, 0));
            detections.add(createDetection(0, 479, 450, 610, 1));
            detections.add(createDetection(0, 479, 460, 620, 2));
            Track track = createTrack(0, 2, detections);

            Track updated = StationaryTrackLabelingProcessor.processTrack(1, 1, 0.6, 1, track);
            assertEquals("TRUE", updated.getTrackProperties().get("IS_STATIONARY_TRACK"));
        }
    }

    @Test
    public void testLabelTrackNonStationary() {
        {
            SortedSet<Detection> detections = new TreeSet<>();

            detections.add(createDetection(300, 179, 48, 64, 0));
            detections.add(createDetection(0, 29, 50, 60, 1));
            detections.add(createDetection(500, 509, 100, 100, 2));
            Track track = createTrack(0, 2, detections);

            Track updated = StationaryTrackLabelingProcessor.processTrack(1, 1, 0.6, 1, track);
            assertEquals("FALSE", updated.getTrackProperties().get("IS_STATIONARY_TRACK"));
        }
    }

    @Test
    public void testKeepAllTracks() {
        {
            TreeSet<Track> tracks = new TreeSet<>();
            SortedSet<Detection> detections = new TreeSet<>();

            // Add non-stationary track.
            detections.add(createDetection(300, 179, 48, 64, 0));
            detections.add(createDetection(0, 29, 50, 60, 1));
            detections.add(createDetection(500, 509, 100, 100, 2));
            tracks.add(createTrack(0, 2, detections));

            detections = new TreeSet<>();

            // Add stationary track.
            detections.add(createDetection(0, 479, 470, 630, 0));
            detections.add(createDetection(0, 479, 450, 610, 1));
            detections.add(createDetection(0, 479, 460, 620, 2));
            tracks.add(createTrack(0, 2, detections));

            Collection<Track> updated_tracks = StationaryTrackLabelingProcessor.updateStationaryTracks(0, 0, false,
                                                                                                       0.6, 1, tracks);
            assertEquals(updated_tracks.size(), 2);
            assertExpectedTrackCount(1, 1, updated_tracks);
        }
    }

    @Test
    public void testDropStationaryTracks() {
        {
            TreeSet<Track> tracks = new TreeSet<>();
            SortedSet<Detection> detections = new TreeSet<>();

            // Add non-stationary track.
            detections.add(createDetection(300, 179, 48, 64, 0));
            detections.add(createDetection(0, 29, 50, 60, 1));
            detections.add(createDetection(500, 509, 100, 100, 2));
            tracks.add(createTrack(0, 2, detections));

            detections = new TreeSet<>();

            // Add stationary track.
            detections.add(createDetection(0, 479, 470, 630, 0));
            detections.add(createDetection(0, 479, 450, 610, 1));
            detections.add(createDetection(0, 479, 460, 620, 2));
            tracks.add(createTrack(0, 2, detections));

            Collection<Track> updated_tracks = StationaryTrackLabelingProcessor.updateStationaryTracks(0, 0, true,
                                                                                                       0.6, 1, tracks);
            assertEquals(updated_tracks.size(), 1);
            assertExpectedTrackCount(0, 1, updated_tracks);
        }
    }

    private void assertExpectedTrackCount(int expectedStationary, int expectedNonStationary, Collection<Track> tracks) {
        int countStationary = 0, countNonStationary = 0;
        for (Track track: tracks) {
            if (track.getTrackProperties().get("IS_STATIONARY_TRACK") == "TRUE") {
                countStationary++;
            } else {
                countNonStationary++;
            }
        }
        assertEquals(expectedStationary, countStationary);
        assertEquals(expectedNonStationary, countNonStationary);
    }

    private static Detection createDetection(int x, int y, int width, int height, int frame) {
        Map<String, String> detectionProperties = Collections.emptyMap();
        return new Detection(x, y, width, height, 1, frame, 0, detectionProperties);
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
                "VIDEO", //type
                -1, //confidence
                detections, //detections
                Collections.emptyMap()); //trackProperties
    }
}
