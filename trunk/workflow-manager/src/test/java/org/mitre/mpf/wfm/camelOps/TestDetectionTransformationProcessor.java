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

package org.mitre.mpf.wfm.camelOps;

import org.junit.Test;
import org.mitre.mpf.wfm.camel.operations.detection.transformation.DetectionTransformationProcessor;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.TrackCache;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.ObjectMapperFactory;
import org.mockito.ArgumentCaptor;

import java.util.*;

import static org.apache.commons.collections4.CollectionUtils.isEqualCollection;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class TestDetectionTransformationProcessor {

    //////////////////////////////////////////////////////////////////////
    // Test padding and clipping
    //////////////////////////////////////////////////////////////////////

    @Test
    public void testPaddingNoRotation() {
        assertPadding(3, 3, 4, 4, "25%", "25%", 10, 10, 2, 2, 6, 6);   // expand uniformly
        assertPadding(3, 3, 4, 4, "0%", "25%", 10, 10, 3, 2, 4, 6);    // expand height only
        assertPadding(3, 3, 4, 4, "25%", "0%", 10, 10, 2, 3, 6, 4);    // expand width only
        assertPadding(3, 3, 4, 4, "0", "0", 10, 10, 3, 3, 4, 4);       // no-op

        assertPadding(5, 5, 4, 4, "50%", "50%", 10, 10, 3, 3, 7, 7);   // over-expand towards bottom right
        assertPadding(1, 1, 4, 4, "50%", "50%", 10, 10, 0, 0, 7, 7);   // over-expand towards top left
        assertPadding(1, 5, 4, 4, "50%", "50%", 10, 10, 0, 3, 7, 7);   // over-expand towards bottom left
        assertPadding(5, 1, 4, 4, "50%", "50%", 10, 10, 3, 0, 7, 7);   // over-expand towards top right

        assertPadding(5, 5, 4, 4, "25%", "75%", 10, 10, 4, 2, 6, 8);   // over-expand towards bottom
        assertPadding(5, 5, 4, 4, "25%", "300%", 10, 10, 4, 0, 6, 10); // over-expand towards top and bottom

        assertPadding(5, 5, 4, 4, "75%", "25%", 10, 10, 2, 4, 8, 6);   // over-expand towards right
        assertPadding(5, 5, 4, 4, "300%", "25%", 10, 10, 0, 4, 10, 6); // over-expand towards left and right

        assertPadding(3, 3, 4, 4, "1", "25%", 10, 10, 2, 2, 6, 6); // expand uniformly
        assertPadding(40, 40, 20, 20, "200%", "200%", 100, 100, 0, 0, 100, 100); // expand uniformly
        assertPadding(40, 40, 20, 20, "40", "40", 100, 100, 0, 0, 100, 100);     // expand uniformly
        assertPadding(40, 40, 20, 20, "200%", "40", 100, 100, 0, 0, 100, 100);   // expand uniformly

        assertPadding(40, 40, 20, 20, "-25%", "-25%", 100, 100, 45, 45, 10, 10); // shrink uniformly
        assertPadding(40, 40, 20, 20, "-25%", "-5", 100, 100, 45, 45, 10, 10);   // shrink uniformly
        assertShrinkToNothing(40, 40, 20, 20, "-50%", "-50%", 100, 100, 50, 50, 1, 1);   // shrink to nothing
        assertShrinkToNothing(40, 40, 20, 20, "-100%", "-277", 100, 100, 50, 50, 1, 1);  // shrink beyond nothing
        assertShrinkToNothing(40, 40, 20, 20, "-500%", "-500%", 100, 100, 50, 50, 1, 1); // shrink beyond nothing
        assertShrinkToNothing(40, 40, 20, 20, "-500%", "-25%", 100, 100, 50, 45, 1, 10); // shrink beyond nothing (width)
        assertShrinkToNothing(40, 40, 20, 20, "-25%", "-500%", 100, 100, 45, 50, 10, 1); // shrink beyond nothing (height)

        assertPadding(40, 40, 20, 20, "100%", "-5", 100, 100, 20, 45, 60, 10); // expand and shrink

        assertShrinkToNothing(20, 20, 7, 7, "-100%", "-100%", 40, 40, 24, 24, 1, 1); // shrink beyond nothing (not exact)
        assertShrinkToNothing(20, 20, 7, 7, "-7", "-7", 40, 40, 24, 24, 1, 1);       // shrink beyond nothing (not exact)
        assertPadding(20, 20, 7, 7, "25%", "25%", 40, 40, 18, 18, 11, 11); // expand uniformly (not exact)
        assertPadding(20, 20, 7, 7, "21%", "21%", 40, 40, 19, 19, 10, 10); // expand uniformly (not exact)
        assertPadding(20, 20, 7, 7, "-21%", "-21%", 40, 40, 21, 21, 5, 5); // shrink uniformly (not exact)

        assertPadding(6, 4, 4, 6, "50%", "33.3%", 10, 20, 4, 2, 6, 10); // over-expand towards right (height != width)
        assertPadding(6, 4, 4, 6, "50%", "33.3%", 20, 10, 4, 2, 8, 8);  // over-expand towards bottom (height != width)
    }

    @Test
    public void testPaddingOrthogonalRotation() {
        {
            Detection input = createDetection(0, 480, 480, 640, "90");
            Detection actual = DetectionTransformationProcessor.padDetection("200%", "200%", 640, 480, input);
            // Input detection is already at max possible size.
            assertEquals(input, actual);
        }
        {
            Detection input = createDetection(300, 100, 400, 100, "90");
            Detection actual = DetectionTransformationProcessor.padDetection("50%", "0", 500, 500, input);
            Detection expected = createDetection(300, 300, 300, 100, "90");
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testPaddingNonOrthogonalRotation() {
        {
            Detection input = createDetection(30, 40, 580, 400, "20");
            Detection padded = DetectionTransformationProcessor.padDetection("1000%", "1000%", 640, 480, input);
            Detection expected = createDetection(-154, 56, 766, 670, "20");
            assertEquals(expected, padded);
        }
        {
            Detection input = createDetection(300, 100, 400, 100, "45");
            Detection padded = DetectionTransformationProcessor.padDetection("0", "0", 500, 500, input);
            Detection expected = createDetection(300, 100, 213, 100, "45");
            assertEquals(expected, padded);
        }
        {
            Detection input = createDetection(300, 100, 400, 100, "45");
            Detection actual = DetectionTransformationProcessor.padDetection("50%", "0", 500, 500, input);
            Detection expected = createDetection(159, 241, 413, 100, "45");
            assertEquals(expected, actual);
        }
        {
            Detection input = createDetection(300, 100, 400, 100, "225");
            Detection actual = DetectionTransformationProcessor.padDetection("100%", "0", 500, 500, input);
            Detection expected = createDetection(450, -50, 708, 100, "225");
            assertEquals(expected, actual);
        }
        {
            Detection input = createDetection(300, 100, 400, 100, "225");
            Detection actual = DetectionTransformationProcessor.padDetection("50%", "0", 500, 500, input);
            Detection expected = createDetection(441, -41, 695, 100, "225");
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testPaddingNonOrthogonalRotationNoClipping() {
        Detection input = createDetection(96, 140, 190, 42, "18.74");
        {
            Detection expected = createDetection(6, 171, 380, 42, "18.74");
            Detection actual = DetectionTransformationProcessor.padDetection("50%", "0", 1000, 1000, input);
            assertEquals(expected, actual);
        }
        {
            Detection expected = createDetection(89, 120, 190, 84, "18.74");
            Detection actual = DetectionTransformationProcessor.padDetection("0%", "50%", 1000, 1000, input);
            assertEquals(expected, actual);
        }
        {
            Detection expected = createDetection(-1, 151, 380, 84, "18.74");
            Detection actual = DetectionTransformationProcessor.padDetection("50%", "50%", 1000, 1000, input);
            assertEquals(expected, actual);
        }
        {
            Detection actual = DetectionTransformationProcessor.padDetection("0", "0", 500, 500, input);
            assertEquals(input, actual);
        }
    }

    @Test
    public void testPaddingOrthogonalRotationNoClipping() {
        Detection input = createDetection(50, 60, 20, 40, "90");
        {
            Detection expected = createDetection(26, 60, 20, 88, "90");
            Detection actual = DetectionTransformationProcessor.padDetection("0", "60%", 1000, 1000, input);
            assertEquals(expected, actual);
        }
        {
            Detection expected = createDetection(50, 58, 16, 40, "90");
            Detection actual = DetectionTransformationProcessor.padDetection("-10%", "0", 1000, 1000, input);
            assertEquals(expected, actual);
        }
        {
            Detection expected = createDetection(26, 58, 16, 88, "90");
            Detection actual = DetectionTransformationProcessor.padDetection("-10%", "60%", 1000, 1000, input);
            assertEquals(expected, actual);
        }
    }



    @Test
    public void testPaddingNotIllformedFlipped() {
        {   // top left: no flip
            Detection input = createDetection(30, 40, 50, 60, null, false);
            Detection padded = DetectionTransformationProcessor.padDetection("300%", "400%", 640, 480, input);
            Detection expected = createDetection(0, 0, 230, 340, null, false);
            assertEquals(expected, padded);
            assertNotIllformed(padded, 640, 480);
        }
        {   // top left: flip into frame
            Detection input = createDetection(30, 40, 50, 60, null, true);
            Detection padded = DetectionTransformationProcessor.padDetection("300%", "400%", 640, 480, input);
            Detection expected = createDetection(180, 0, 180, 340, null, true);
            assertEquals(expected, padded);
            assertNotIllformed(padded, 640, 480);
        }
        {   // bottom right: flip
            Detection input = createDetection(560, 380, 50, 60, null, true);
            Detection padded = DetectionTransformationProcessor.padDetection("300%", "400%", 640, 480, input);
            Detection expected = createDetection(640, 140, 280, 340, null, true);
            assertEquals(expected, padded);
            assertNotIllformed(padded, 640, 480);
        }
    }

    @Test
    public void testPaddingNotIllformedNonOrthogonalRotation() {
        {   // top left 45: no clip
            Detection input = createDetection(30, 40, 50, 60, "45", false);
            Detection padded = DetectionTransformationProcessor.padDetection("50%", "50%", 640, 480, input);
            Detection expected = createDetection(-9, 36, 100, 120, "45", false);
            assertEquals(expected, padded);
            assertNotIllformed(padded, 640, 480);
        }
        {   // top left 45: clip
            Detection input = createDetection(30, 40, 50, 60, "45", false);
            Detection padded = DetectionTransformationProcessor.padDetection("200%", "200%", 640, 480, input);
            Detection expected = createDetection(-76, 76, 250, 230, "45", false);
            assertEquals(expected, padded);
            assertNotIllformed(padded, 640, 480);
        }
        {   // top left 45: clip top and bottom
            Detection input = createDetection(30, 40, 50, 60, "45", false);
            Detection padded = DetectionTransformationProcessor.padDetection("0%", "1200%", 640, 480, input);
            Detection expected = createDetection(-5, 5, 50, 792, "45", false);
            assertEquals(expected, padded);
            assertNotIllformed(padded, 640, 480);
        }
        {   // capture entire frame
            Detection input = createDetection(30, 40, 50, 60, "45", false);
            Detection padded = DetectionTransformationProcessor.padDetection("50000%", "50000%", 640, 480, input);
            Detection expected = createDetection(-240, 240, 792, 792, "45", false);
            assertEquals(expected, padded);
            assertNotIllformed(padded, 640, 480);
        }
        {   // top left 225: clip
            Detection input = createDetection(30, 40, 50, 60, "225", false);
            Detection padded = DetectionTransformationProcessor.padDetection("200%", "400%", 640, 480, input);
            Detection expected = createDetection(270, 139, 250, 290, "225", false);
            assertEquals(expected, padded);
            assertNotIllformed(padded, 640, 480);
        }
    }

    @Test
    public void testPaddingNotIllformedNonOrthogonalRotationFlipped() {
        {   // top left: no clip, no rotation, flip
            Detection input = createDetection(130, 140, 50, 60, "0", true);
            Detection padded = DetectionTransformationProcessor.padDetection("50%", "50%", 640, 480, input);
            Detection expected = createDetection(155, 110, 100, 120, "0", true);
            assertEquals(expected, padded);
            assertNotIllformed(padded, 640, 480);
        }
        {   // top left: clip, no rotation, flip
            Detection input = createDetection(30, 40, 50, 60, "0", true);
            Detection padded = DetectionTransformationProcessor.padDetection("50%", "50%", 640, 480, input);
            Detection expected = createDetection(55, 10, 55, 120, "0", true);
            assertEquals(expected, padded);
            assertNotIllformed(padded, 640, 480);
        }
        {   // top left 45: clip, rotation, flip
            Detection input = createDetection(30, 40, 50, 60, "45", true);
            Detection padded = DetectionTransformationProcessor.padDetection("50%", "50%", 640, 480, input);
            Detection expected = createDetection(69, 36, 75, 120, "45", true);
            assertEquals(expected, padded);
            assertNotIllformed(padded, 640, 480);
        }
        {   // top left 225: clip, rotation, flip
            Detection input = createDetection(30, 40, 50, 60, "225", true);
            Detection padded = DetectionTransformationProcessor.padDetection("200%", "400%", 640, 480, input);
            Detection expected = createDetection(-175, 175, 200, 540, "225", true);
            assertEquals(expected, padded);
            assertNotIllformed(padded, 640, 480);
        }
        {   // bottom right 90: no clip, rotation, no flip
            Detection input = createDetection(580, 400, 50, 60, "90", false);
            Detection padded = DetectionTransformationProcessor.padDetection("200%", "400%", 640, 480, input);
            Detection expected = createDetection(340, 480, 230, 300, "90", false);
            assertEquals(expected, padded);
            assertNotIllformed(padded, 640, 480);
        }
        {   // bottom right -45: clip, rotation, flip
            Detection input = createDetection(580, 400, 50, 60, "-45", true);
            Detection padded = DetectionTransformationProcessor.padDetection("200%", "400%", 640, 480, input);
            Detection expected = createDetection(481, 160, 250, 339, "-45", true);
            assertEquals(expected, padded);
            assertNotIllformed(padded, 640, 480);
        }
        {   // top right 60: clip, rotation, flip
            Detection input = createDetection(580, 40, 50, 60, "60", true);
            Detection padded = DetectionTransformationProcessor.padDetection("200%", "400%", 640, 480, input);
            Detection expected = createDetection(692, 91, 250, 372, "60", true);
            assertEquals(expected, padded);
            assertNotIllformed(padded, 640, 480);
        }
    }

    @Test
    public void testPaddingNotIllformedSinglePixelOverlap() {
        {   // top left 1 pixel overlap: x and y
            Detection input = createDetection(-50, -80, 51, 81);
            Detection padded = DetectionTransformationProcessor.padDetection("0%", "0%", 640, 480, input);
            Detection expected = createDetection(0, 0, 1, 1);
            assertEquals(expected, padded);
            assertNotIllformed(padded, 640, 480);
        }
        {   // top left 1 pixel overlap: y only
            Detection input = createDetection(-50, -80, 50, 81);
            Detection padded = DetectionTransformationProcessor.padDetection("0%", "0%", 640, 480, input);
            Detection expected = createShrunkDetection(0, 0, 1, 1);
            assertEquals(expected, padded);
            assertNotIllformed(padded, 640, 480);
        }
        {   // top left 1 pixel overlap: x only
            Detection input = createDetection(-50, -80, 51, 80);
            Detection padded = DetectionTransformationProcessor.padDetection("0%", "0%", 640, 480, input);
            Detection expected = createShrunkDetection(0, 0, 1, 1);
            assertEquals(expected, padded);
            assertNotIllformed(padded, 640, 480);
        }

        {   // bottom right 1 pixel overlap: x and y
            Detection input = createDetection(199, 399, 500, 50);
            Detection padded = DetectionTransformationProcessor.padDetection("0%", "0%", 200, 400, input);
            Detection expected = createDetection(199, 399, 1, 1);
            assertEquals(expected, padded);
            assertNotIllformed(padded, 640, 480);
        }
        {   // bottom right 1 pixel overlap: y only
            Detection input = createDetection(200, 399, 500, 50);
            Detection padded = DetectionTransformationProcessor.padDetection("0%", "0%", 200, 400, input);
            Detection expected = createShrunkDetection(200, 399, 1, 1);
            assertEquals(expected, padded);
            assertNotIllformed(padded, 640, 480);
        }
        {   // bottom right 1 pixel overlap: x only
            Detection input = createDetection(199, 400, 500, 50);
            Detection padded = DetectionTransformationProcessor.padDetection("0%", "0%", 200, 400, input);
            Detection expected = createShrunkDetection(199, 400, 1, 1);
            assertEquals(expected, padded);
            assertNotIllformed(padded, 640, 480);
        }
    }


    private static void assertPadding(int x, int y, int width, int height, String xPadding, String yPadding,
                                      int frameWidth, int frameHeight,
                                      int expectedX, int expectedY, int expectedWidth, int expectedHeight) {
        assertPadding(x, y, width, height, xPadding, yPadding, frameWidth, frameHeight,
                expectedX, expectedY, expectedWidth, expectedHeight, false);
    }

    private static void assertShrinkToNothing(int x, int y, int width, int height, String xPadding, String yPadding,
                                              int frameWidth, int frameHeight,
                                              int expectedX, int expectedY, int expectedWidth, int expectedHeight) {
        assertPadding(x, y, width, height, xPadding, yPadding, frameWidth, frameHeight,
                expectedX, expectedY, expectedWidth, expectedHeight, true);
    }

    private static void assertPadding(int x, int y, int width, int height, String xPadding, String yPadding,
                                      int frameWidth, int frameHeight,
                                      int expectedX, int expectedY, int expectedWidth, int expectedHeight,
                                      boolean shrinkToNothing) {
        Detection detection = new Detection(x, y, width, height, -1, 0, 0, Collections.emptyMap());
        Detection expectedDetection = new Detection(expectedX, expectedY, expectedWidth, expectedHeight, -1, 0, 0,
                shrinkToNothing ? Collections.singletonMap("SHRUNK_TO_NOTHING", "TRUE") : Collections.emptyMap());
        Detection newDetection = DetectionTransformationProcessor.padDetection(xPadding, yPadding, frameWidth, frameHeight,
                detection);
        assertEquals(expectedDetection, newDetection);
    }


    //////////////////////////////////////////////////////////////////////
    // Test illformed detections
    //////////////////////////////////////////////////////////////////////

    @Test
    public void testRemoveIllformedZeroSizeDetectionsNoZeroDetections() {
        SortedSet<Detection> detections = new TreeSet<>();
        Detection detection1 = new Detection(50, 60, 20, 40, 1, 1, 1, Collections.emptyMap());
        Detection detection2 = new Detection(50, 60, 20, 40, 1, 2, 2, Collections.emptyMap());
        Detection detection3 = new Detection(50, 60, 20, 40, 1, 3, 3, Collections.emptyMap());
        detections.add(detection1);
        detections.add(detection2);
        detections.add(detection3);

        Track track1 = new Track(1, 2, 1, 1, 1, 3,
                1, 3, 1, 1,
                 detections, Collections.emptyMap(), "", "");

        SortedSet<Track> tracks = new TreeSet<>();
        tracks.add(track1);

        Collection<Track> filteredTracks = runRemoveIllFormedDetections(tracks, 200, 400);

        assertEquals(1, filteredTracks.size());
        Track filteredTrack = filteredTracks.iterator().next();
        SortedSet<Detection> filteredDetections = filteredTrack.getDetections();
        assertEquals(3, filteredDetections.size());
        assertEquals(1, filteredTrack.getStartOffsetFrameInclusive());
        assertEquals(3, filteredTrack.getEndOffsetFrameInclusive());
        assertEquals(1, filteredTrack.getStartOffsetTimeInclusive());
        assertEquals(3, filteredTrack.getEndOffsetTimeInclusive());
    }

    @Test
    public void testRemoveIllformedZeroSizeDetectionsOneZeroDetection() {
        SortedSet<Detection> detections = new TreeSet<>();
        Detection detection1 = new Detection(50, 60, 20, 40, 1, 1, 1, Collections.emptyMap());
        Detection detection2 = new Detection(50, 60, 0, 0, 1, 2, 2, Collections.emptyMap());
        Detection detection3 = new Detection(50, 60, 20, 40, 1, 3, 3, Collections.emptyMap());
        detections.add(detection1);
        detections.add(detection2);
        detections.add(detection3);

        Track track1 = new Track(1, 2, 1, 1, 1, 3,
                1, 3, 1, 1,
                detections, Collections.emptyMap(), "", "");

        SortedSet<Track> tracks = new TreeSet<>();
        tracks.add(track1);

        Collection<Track> filteredTracks = runRemoveIllFormedDetections(tracks, 200, 400, true, false);
        assertEquals(1, filteredTracks.size());
        Track filteredTrack = filteredTracks.iterator().next();
        SortedSet<Detection> filteredDetections = filteredTrack.getDetections();
        assertEquals(2, filteredDetections.size());
        assertEquals(1, filteredTrack.getStartOffsetFrameInclusive());
        assertEquals(3, filteredTrack.getEndOffsetFrameInclusive());
        assertEquals(1, filteredTrack.getStartOffsetTimeInclusive());
        assertEquals(3, filteredTrack.getEndOffsetTimeInclusive());
    }

    @Test
    public void testRemoveIllformedOutsideFrameDetectionsOneDetection() {
        SortedSet<Detection> detections = new TreeSet<>();
        Detection detection1 = new Detection(-25, 60, 20, 40, 1, 1, 1, Collections.emptyMap());
        Detection detection2 = new Detection(50, 60, 20, 40, 1, 2, 2, Collections.emptyMap());
        Detection detection3 = new Detection(50, 60, 20, 40, 1, 3, 3, Collections.emptyMap());
        detections.add(detection1);
        detections.add(detection2);
        detections.add(detection3);

        Track track1 = new Track(1, 2, 1, 1, 1, 3,
                1, 3, 1, 1, detections, Collections.emptyMap(), "", "");

        SortedSet<Track> tracks = new TreeSet<>();
        tracks.add(track1);

        Collection<Track> filteredTracks = runRemoveIllFormedDetections(tracks, 200, 400, false, true);
        assertEquals(1, filteredTracks.size());
        Track filteredTrack = filteredTracks.iterator().next();
        SortedSet<Detection> filteredDetections = filteredTrack.getDetections();
        assertEquals(2, filteredDetections.size());
        assertEquals(2, filteredTrack.getStartOffsetFrameInclusive());
        assertEquals(3, filteredTrack.getEndOffsetFrameInclusive());
        assertEquals(2, filteredTrack.getStartOffsetTimeInclusive());
        assertEquals(3, filteredTrack.getEndOffsetTimeInclusive());
    }

    @Test
    public void testKeepDetectionPartiallyInFrame() {
        SortedSet<Detection> detections = new TreeSet<>();
        Detection detection1 = new Detection(-10, 60, 20, 40, 1, 1, 1, Collections.emptyMap());
        Detection detection2 = new Detection(50, 60, 20, 40, 1, 2, 2, Collections.emptyMap());
        Detection detection3 = new Detection(50, 60, 20, 40, 1, 3, 3, Collections.emptyMap());
        detections.add(detection1);
        detections.add(detection2);
        detections.add(detection3);

        Track track1 = new Track(1, 2, 1, 1, 1, 3,
                1, 3, 1, 1,detections, Collections.emptyMap(), "", "");

        SortedSet<Track> tracks = new TreeSet<>();
        tracks.add(track1);

        Collection<Track> filteredTracks = runRemoveIllFormedDetections(tracks, 200, 400);
        assertEquals(1, filteredTracks.size());
        Track filteredTrack = filteredTracks.iterator().next();
        SortedSet<Detection> filteredDetections = filteredTrack.getDetections();
        assertEquals(3, filteredDetections.size());
        assertEquals(1, filteredTrack.getStartOffsetFrameInclusive());
        assertEquals(3, filteredTrack.getEndOffsetFrameInclusive());
        assertEquals(1, filteredTrack.getStartOffsetTimeInclusive());
        assertEquals(3, filteredTrack.getEndOffsetTimeInclusive());
    }

    @Test
    public void testRemoveIllformedDetectionsOneOfEach() {
        SortedSet<Detection> detections = new TreeSet<>();
        Detection detection1 = new Detection(-25, 60, 20, 40, 1, 1, 1, Collections.emptyMap()); // out of frame
        Detection detection2 = new Detection(50, 60, 0, 0, 1, 2, 2, Collections.emptyMap()); // zero width and height
        Detection detection3 = new Detection(50, 60, 20, 40, 1, 3, 3, Collections.emptyMap());
        detections.add(detection1);
        detections.add(detection2);
        detections.add(detection3);

        Track track1 = new Track(1, 2, 1, 1, 1, 3,
                1, 3, 1, 1, detections, Collections.emptyMap(), "", "");

        SortedSet<Track> tracks = new TreeSet<>();
        tracks.add(track1);

        Collection<Track> filteredTracks = runRemoveIllFormedDetections(tracks, 200, 400, true, true);
        assertEquals(1, filteredTracks.size());
        Track filteredTrack = filteredTracks.iterator().next();
        SortedSet<Detection> filteredDetections = filteredTrack.getDetections();
        assertEquals(1, filteredDetections.size());
        assertEquals(3, filteredTrack.getStartOffsetFrameInclusive());
        assertEquals(3, filteredTrack.getEndOffsetFrameInclusive());
        assertEquals(3, filteredTrack.getStartOffsetTimeInclusive());
        assertEquals(3, filteredTrack.getEndOffsetTimeInclusive());
    }

    @Test
    public void testKeepDetectionsFromExemptTypes() {
        SortedSet<Detection> detections = new TreeSet<>();
        Detection detection1 = new Detection(-25, 60, 20, 40, 1, 1, 1, Collections.emptyMap());
        Detection detection2 = new Detection(50, 60, 0, 0, 1, 2, 2, Collections.emptyMap());
        Detection detection3 = new Detection(50, 60, 20, 40, 1, 3, 3, Collections.emptyMap());
        detections.add(detection1);
        detections.add(detection2);
        detections.add(detection3);

        Track track1 = new Track(1, 2, 1, 1, 1, 3,
                1, 3, 1, 1, detections, Collections.emptyMap(), "", "");

        SortedSet<Track> tracks = new TreeSet<>();
        tracks.add(track1);

        Collection<Track> filteredTracks = runRemoveIllFormedDetections(
                tracks, "SPEECH", 200, 400, false, false);
        assertEquals(1, filteredTracks.size());
        Track filteredTrack = filteredTracks.iterator().next();
        SortedSet<Detection> filteredDetections = filteredTrack.getDetections();
        assertEquals(3, filteredDetections.size());
        assertEquals(1, filteredTrack.getStartOffsetFrameInclusive());
        assertEquals(3, filteredTrack.getEndOffsetFrameInclusive());
        assertEquals(1, filteredTrack.getStartOffsetTimeInclusive());
        assertEquals(3, filteredTrack.getEndOffsetTimeInclusive());
    }

    @Test
    public void testRemoveIllformedZeroSizeDetectionsSingleDetectionTrack() {
        SortedSet<Detection> detections = new TreeSet<>();
        Detection detection1 = new Detection(50, 60, 0, 0, 1, 1, 1, Collections.emptyMap());
        detections.add(detection1);

        Track track1 = new Track(1, 2, 1, 1, 1, 1,
                1, 1, 1, 1, detections, Collections.emptyMap(), "", "");

        SortedSet<Track> tracks = new TreeSet<>();
        tracks.add(track1);

        Collection<Track> filteredTracks = runRemoveIllFormedDetections(tracks, 200, 400, true, false);

        assertTrue(filteredTracks.isEmpty());
    }

    @Test
    public void testRemoveIllformedZeroSizeDetectionsOneZeroDetectionAtEnd() {
        SortedSet<Detection> detections = new TreeSet<>();
        Detection detection1 = new Detection(50, 60, 20, 40, 1, 1, 1, Collections.emptyMap());
        Detection detection2 = new Detection(50, 60, 20, 40, 1, 2, 2, Collections.emptyMap());
        Detection detection3 = new Detection(50, 60, 10, 0, 1, 3, 3, Collections.emptyMap());
        detections.add(detection1);
        detections.add(detection2);
        detections.add(detection3);

        Track track1 = new Track(1, 2, 1, 1, 1, 3,
                1, 3, 1, 1, detections, Collections.emptyMap(), "", "");

        SortedSet<Track> tracks = new TreeSet<>();
        tracks.add(track1);

        Collection<Track> filteredTracks = runRemoveIllFormedDetections(tracks, 200, 400, true, false);

        assertEquals(1, filteredTracks.size());
        Track filteredTrack = filteredTracks.iterator().next();
        SortedSet<Detection> filteredDetections = filteredTrack.getDetections();
        assertEquals(2, filteredDetections.size());
        assertEquals(1, filteredTrack.getStartOffsetFrameInclusive());
        assertEquals(2, filteredTrack.getEndOffsetFrameInclusive());
        assertEquals(1, filteredTrack.getStartOffsetTimeInclusive());
        assertEquals(2, filteredTrack.getEndOffsetTimeInclusive());
    }

    @Test
    public void testIllformedNonOrthogonalRotationFlipped() {
        {   // top left: out of frame
            Detection detection = createDetection(-100, -100, 500, 50);
            assertIllformed(detection, 200, 400);
        }
        {   // top left 45: no flip
            Detection detection = createDetection(-100, -100, 500, 50, "-45");
            assertNotIllformed(detection, 200, 400);
        }
        {   // top right: flip
            Detection detection = createDetection(250, 0, 500, 50, null, true);
            assertNotIllformed(detection, 200, 400);
        }
        {   // bottom left 125: flip
            Detection detection = createDetection(-100, 500, 500, 50, "125", true);
            assertNotIllformed(detection, 200, 400);
        }
        {   // bottom right 125: no flip
            Detection detection = createDetection(200, 500, 500, 50, "125");
            assertNotIllformed(detection, 200, 400);
        }
        {   // bottom right: out of frame
            Detection detection = createDetection(200, 500, 500, 50);
            assertIllformed(detection, 200, 400);
        }
        {   // bottom right 45: flip
            Detection detection = createDetection(200, 500, 500, 50, "45", true);
            assertNotIllformed(detection, 200, 400);
        }

        {   // Detection is close enough to be within the rotated frame region bounding box (like the one used during
            // clipping), but does not actually intersect with the real frame region.
            Detection detection = createDetection(150, -150, 100, 50, "-45");
            assertIllformed(detection, 200, 400);
        }
    }

    @Test
    public void testIllformedSinglePixelOverlap() {
        {   // top left 1 pixel overlap: x and y
            Detection detection = createDetection(-50, -80, 51, 81);
            assertNotIllformed(detection, 200, 400);
        }
        {   // top left 1 pixel overlap: y only
            Detection detection = createDetection(-50, -80, 50, 81);
            assertIllformed(detection, 200, 400);
        }
        {   // top left 1 pixel overlap: x only
            Detection detection = createDetection(-50, -80, 51, 80);
            assertIllformed(detection, 200, 400);
        }

        {   // bottom right 1 pixel overlap: x and y
            Detection detection = createDetection(199, 399, 500, 50);
            assertNotIllformed(detection, 200, 400);
        }
        {   // bottom right 1 pixel overlap: y only
            Detection detection = createDetection(200, 399, 500, 50);
            assertIllformed(detection, 200, 400);
        }
        {   // bottom right 1 pixel overlap: x only
            Detection detection = createDetection(199, 400, 500, 50);
            assertIllformed(detection, 200, 400);
        }
    }


    private static Collection<Track> runRemoveIllFormedDetections(SortedSet<Track> tracks, int frameWidth, int frameHeight) {
        return runRemoveIllFormedDetections(tracks, frameWidth, frameHeight, false, false);
    }

    private static Collection<Track> runRemoveIllFormedDetections(SortedSet<Track> tracks,
                                                                  int frameWidth, int frameHeight,
                                                                  boolean hasWidthHeightWarning,
                                                                  boolean hasOutsideFrameWarning) {
        return runRemoveIllFormedDetections(
                tracks, "FACE", frameWidth, frameHeight, hasWidthHeightWarning,
                hasOutsideFrameWarning);
    }

    private static Collection<Track> runRemoveIllFormedDetections(SortedSet<Track> tracks,
                                                                  String trackType,
                                                                  int frameWidth, int frameHeight,
                                                                  boolean hasWidthHeightWarning,
                                                                  boolean hasOutsideFrameWarning) {
        JsonUtils _jsonUtils = new JsonUtils(ObjectMapperFactory.customObjectMapper());
        InProgressBatchJobsService _mockInProgressJobs = mock(InProgressBatchJobsService.class);
        AggregateJobPropertiesUtil _mockAggregateJobPropertiesUtil = mock(AggregateJobPropertiesUtil.class);
        when(_mockAggregateJobPropertiesUtil.isExemptFromIllFormedDetectionRemoval("SPEECH")).thenReturn(true);
        DetectionTransformationProcessor _detectionTransformationProcessor = new DetectionTransformationProcessor(
                _jsonUtils,
                _mockInProgressJobs,
                _mockAggregateJobPropertiesUtil);
        long jobId = 123;
        long mediaId = 5321;

        var trackCache = new TrackCache(jobId, 0, _mockInProgressJobs);

        Collection<Track> new_tracks = _detectionTransformationProcessor.removeIllFormedDetections(
                trackCache, mediaId, 0, frameWidth, frameHeight, trackType, tracks);

        if (!hasWidthHeightWarning && !hasOutsideFrameWarning) {
            verify(_mockInProgressJobs, times(0))
                    .addWarning(eq(jobId), eq(mediaId), eq(IssueCodes.INVALID_DETECTION), anyString());
            trackCache.commit();
            verify(_mockInProgressJobs, never())
                    .setTracks(anyLong(), anyLong(), anyInt(), anyInt(), any());
            assertEquals(0, trackCache.getTracks(mediaId, 0).size());
        } else {
            if (hasWidthHeightWarning) {
                verify(_mockInProgressJobs, times(1))
                        .addWarning(eq(jobId), eq(mediaId), eq(IssueCodes.INVALID_DETECTION),
                                contains("width or height equal to 0"));
            }
            if (hasOutsideFrameWarning) {
                verify(_mockInProgressJobs, times(1))
                        .addWarning(eq(jobId), eq(mediaId), eq(IssueCodes.INVALID_DETECTION),
                                contains("completely outside frame"));
            }

            trackCache.commit();
            var captor = ArgumentCaptor.forClass(SortedSet.class);
            verify(_mockInProgressJobs)
                    .setTracks(eq(jobId), eq(mediaId), eq(0), eq(0), captor.capture());
            assertTrue(isEqualCollection(new_tracks, captor.getValue()));
        }
        return new_tracks;
    }

    private static void assertNotIllformed(Detection detection, int frameWidth, int frameHeight) {
        SortedSet<Track> tracks = createTracks(detection);
        Collection<Track> filteredTracks = runRemoveIllFormedDetections(tracks, frameWidth, frameHeight, false, false);
        assertEquals(1, filteredTracks.size());
    }

    private static void assertIllformed(Detection detection, int frameWidth, int frameHeight) {
        SortedSet<Track> tracks = createTracks(detection);
        Collection<Track> filteredTracks = runRemoveIllFormedDetections(tracks, frameWidth, frameHeight, false, true);
        assertEquals(0, filteredTracks.size());
    }


    //////////////////////////////////////////////////////////////////////
    // Helper creation methods
    //////////////////////////////////////////////////////////////////////

    private static Detection createDetection(int x, int y, int width, int height) {
        return createDetection(x, y, width, height, null, false, false);
    }

    private static Detection createShrunkDetection(int x, int y, int width, int height) {
        return createDetection(x, y, width, height, null, false, true);
    }

    private static Detection createDetection(int x, int y, int width, int height, String rotation) {
        return createDetection(x, y, width, height, rotation, false, false);
    }

    private static Detection createDetection(int x, int y, int width, int height, String rotation, boolean flip) {
        return createDetection(x, y, width, height, rotation, flip, false);
    }

    private static Detection createDetection(int x, int y, int width, int height, String rotation, boolean flip,
                                             boolean shrunk) {
        Map<String, String> detectionProperties = new HashMap<>();
        if (rotation != null) {
            detectionProperties.put("ROTATION", rotation);
        }
        if (flip) {
            detectionProperties.put("HORIZONTAL_FLIP", "TRUE");
        }
        if (shrunk) {
            detectionProperties.put("SHRUNK_TO_NOTHING", "TRUE");
        }
        return new Detection(x, y, width, height, 1, 0, 0,
                detectionProperties);
    }

    private static SortedSet<Track> createTracks(Detection detection) {
        SortedSet<Detection> detections = new TreeSet<>();
        detections.add(detection);

        Track track = new Track(1, 2, 1, 1, 0, 0,
                0, 0, 1, 1, detections, Collections.emptyMap(), "", "");
        SortedSet<Track> tracks = new TreeSet<>();
        tracks.add(track);

        return tracks;
    }
}
