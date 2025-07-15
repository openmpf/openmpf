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

package org.mitre.mpf.wfm.segmenting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mitre.mpf.wfm.segmenting.TestMediaSegmenter.assertAllHaveFeedForwardTrack;
import static org.mitre.mpf.wfm.segmenting.TestMediaSegmenter.assertContainsAlgoProperty;
import static org.mitre.mpf.wfm.segmenting.TestMediaSegmenter.assertContainsExpectedMediaMetadata;
import static org.mitre.mpf.wfm.segmenting.TestMediaSegmenter.assertNoneHaveFeedForwardTrack;
import static org.mitre.mpf.wfm.segmenting.TestMediaSegmenter.confidenceIsEqualToDimensions;
import static org.mitre.mpf.wfm.segmenting.TestMediaSegmenter.createDetection;
import static org.mitre.mpf.wfm.segmenting.TestMediaSegmenter.createTestDetectionContext;
import static org.mitre.mpf.wfm.segmenting.TestMediaSegmenter.createTrack;
import static org.mockito.Mockito.when;

import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

import org.junit.Test;
import org.mitre.mpf.rest.api.MediaUri;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionContext;
import org.mitre.mpf.wfm.camel.operations.mediainspection.FfprobeMetadata;
import org.mitre.mpf.wfm.camel.operations.mediainspection.Fraction;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.MediaImpl;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.util.FrameTimeInfoBuilder;
import org.mitre.mpf.wfm.util.MediaRange;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;


public class TestVideoMediaSegmenter extends MockitoTest.Strict {

    @Mock
    private TriggerProcessor _mockTriggerProcessor;

    @InjectMocks
    private VideoMediaSegmenter _videoMediaSegmenter;

    @Test
    public void canCreateFirstStageMessages() {
        Media media = createTestMedia();
        DetectionContext context = createTestDetectionContext(
                0,  Map.of("FEED_FORWARD_TYPE", "FRAME"), Set.of());

        var detectionRequests = _videoMediaSegmenter.createDetectionRequests(media, context);

        assertEquals(3, detectionRequests.size());
        assertContainsSegment(0, 19, detectionRequests);
        assertContainsSegment(20, 39, detectionRequests);
        assertContainsSegment(40, 49, detectionRequests);

        assertContainsExpectedMediaMetadata(detectionRequests);

        // Verify FEED_FORWARD_TYPE has been removed
        assertTrue(detectionRequests.stream()
                           .allMatch(dr -> dr.protobuf().getAlgorithmPropertiesCount() == 2));
        assertContainsAlgoProperty("algoKey1", "algoValue1", detectionRequests);
        assertContainsAlgoProperty("algoKey2", "algoValue2", detectionRequests);
        assertNoneHaveFeedForwardTrack(detectionRequests);
    }

    @Test
    public void canCreateMessagesFromUserFrameSegmentBoundaries() {
        Media media = createTestMediaWithFps(
                List.of(
                        new MediaRange(0, 7),
                        new MediaRange(15, 25),
                        new MediaRange(100, 124)
                ),
                List.of());
        DetectionContext context = createTestDetectionContext(
                0, Map.of("FEED_FORWARD_TYPE", "FRAME"), Set.of());
        var detectionRequests = _videoMediaSegmenter.createDetectionRequests(media, context);

        assertEquals(4, detectionRequests.size());
        assertContainsSegment(0, 7, detectionRequests);
        assertContainsSegment(15, 25, detectionRequests);
        assertContainsSegment(100, 119, detectionRequests);
        assertContainsSegment(120, 124, detectionRequests);
        assertNoneHaveFeedForwardTrack(detectionRequests);
    }


    @Test
    public void canCreateMessagesFromUserTimeSegmentBoundaries() {
        Media media = createTestMediaWithFps(
                List.of(),
                List.of(
                        new MediaRange(100, 800),
                        new MediaRange(1500, 2500),
                        new MediaRange(3000, 4240)
                )
        );
        DetectionContext context = createTestDetectionContext(
                0,  Map.of(), Set.of());
        var detectionRequests = _videoMediaSegmenter.createDetectionRequests(media, context);

        assertEquals(6, detectionRequests.size());
        assertContainsSegment(2, 21, detectionRequests);
        assertContainsSegment(22, 22, detectionRequests);
        assertContainsSegment(43, 62, detectionRequests);
        assertContainsSegment(63, 73, detectionRequests);
        assertContainsSegment(88, 107, detectionRequests);
        assertContainsSegment(108, 126, detectionRequests);
        assertNoneHaveFeedForwardTrack(detectionRequests);
    }


    @Test
    public void doesNotCombineNonAdjacentUserRanges() {
        var segmentingPlan = new SegmentingPlan(100, 50, 1, 5);
        var context = new DetectionContext(
                1, 0, "STAGE_NAME", 0, "ACTION_NAME",
                true, Map.of(), Set.of(),
                segmentingPlan, null);

        var media = createTestMediaWithFps(
                List.of(
                        new MediaRange(5, 9),
                        new MediaRange(10, 30),
                        new MediaRange(32, 40)
                ),
                List.of()
        );

        var detectionRequests = _videoMediaSegmenter.createDetectionRequests(media, context);

        assertEquals(2, detectionRequests.size());
        assertContainsSegment(5, 30, detectionRequests);
        assertContainsSegment(32, 40, detectionRequests);
        assertNoneHaveFeedForwardTrack(detectionRequests);
    }

    @Test
    public void canLimitSegmentBoundariesToMediaLength() {
        Media media = createTestMediaWithFps(
                List.of(new MediaRange(100, 250)),
                List.of()
        );
        DetectionContext context = createTestDetectionContext(0, Map.of(), Set.of());

        var detectionRequests = _videoMediaSegmenter.createDetectionRequests(media, context);

        assertEquals(5, detectionRequests.size());
        assertContainsSegment(100, 119, detectionRequests);
        assertContainsSegment(120, 139, detectionRequests);
        assertContainsSegment(140, 159, detectionRequests);
        assertContainsSegment(160, 179, detectionRequests);
        assertContainsSegment(180, 199, detectionRequests);
        assertNoneHaveFeedForwardTrack(detectionRequests);
    }

    @Test
    public void canCreateNonFeedForwardMessages() {
        Media media = createTestMedia();

        Set<Track> tracks = createTestTracks();

        DetectionContext context = createTestDetectionContext(1, Map.of(), tracks, "CONFIDENCE");

        var detectionRequests = _videoMediaSegmenter.createDetectionRequests(media, context);

        // range 2 -> 50
        assertEquals(3, detectionRequests.size());
        assertContainsSegment(2, 21, detectionRequests);
        assertContainsSegment(22, 41, detectionRequests);
        assertContainsSegment(42, 50, detectionRequests);

        assertContainsExpectedMediaMetadata(detectionRequests);

        assertTrue(detectionRequests.stream()
                           .allMatch(dr -> dr.protobuf().getAlgorithmPropertiesCount() == 2));
        assertContainsAlgoProperty("algoKey1", "algoValue1", detectionRequests);
        assertContainsAlgoProperty("algoKey2", "algoValue2", detectionRequests);
        assertNoneHaveFeedForwardTrack(detectionRequests);
    }


    @Test
    public void canCreateFeedForwardMessages() {
        Media media = createTestMedia();

        Set<Track> tracks = createTestTracks();

        var detectionContext = createTestDetectionContext(
                1, Map.of("FEED_FORWARD_TYPE", "FRAME"), tracks, "CONFIDENCE");

        when(_mockTriggerProcessor.getTriggeredTracks(media, detectionContext))
                .thenReturn(tracks.stream());

        var detectionRequests = _videoMediaSegmenter.createDetectionRequests(media, detectionContext);

        assertEquals(2, detectionRequests.size());
        assertContainsExpectedMediaMetadata(detectionRequests);

        assertTrue(detectionRequests.stream()
                           .allMatch(dr -> dr.protobuf().getAlgorithmPropertiesCount() == 3));
        assertContainsAlgoProperty("algoKey1", "algoValue1", detectionRequests);
        assertContainsAlgoProperty("algoKey2", "algoValue2", detectionRequests);
        assertContainsAlgoProperty("FEED_FORWARD_TYPE", "FRAME", detectionRequests);


        var track1 = detectionRequests.get(0).protobuf()
                .getVideoRequest().getFeedForwardTrack();
        var track2 = detectionRequests.get(1).protobuf()
                .getVideoRequest().getFeedForwardTrack();
        DetectionProtobuf.VideoTrack shortTrack;
        DetectionProtobuf.VideoTrack longTrack;
        // The protobuf should contain both tracks, but we don't know what order they will be in.
        if (track1.getFrameLocationsCount() == 1) {
            shortTrack = track1;
            longTrack = track2;
        }
        else {
            shortTrack = track2;
            longTrack = track1;
        }

        assertEquals(4, longTrack.getFrameLocationsCount());
        assertContainsFrameLocation(2, longTrack);
        assertContainsFrameLocation(20, longTrack);
        assertContainsFrameLocation(40, longTrack);
        assertEquals(2, longTrack.getStartFrame());
        assertEquals(50, longTrack.getStopFrame());

        assertEquals(1, shortTrack.getFrameLocationsCount());
        assertContainsFrameLocation(5, shortTrack);
        assertEquals(5, shortTrack.getStartFrame());
        assertEquals(5, shortTrack.getStopFrame());
        assertAllHaveFeedForwardTrack(detectionRequests);
    }


    @Test
    public void canCreateFeedForwardMessagesWithTopQualityCount() {
        Media media = createTestMedia();

        Set<Track> tracks = createTestTracks();

        var detectionContext = createTestDetectionContext(
                1,
                ImmutableMap.of("FEED_FORWARD_TYPE", "FRAME", "FEED_FORWARD_TOP_QUALITY_COUNT", "2"),
                tracks, "CONFIDENCE");

        when(_mockTriggerProcessor.getTriggeredTracks(media, detectionContext))
                .thenReturn(tracks.stream());

        var detectionRequests = _videoMediaSegmenter.createDetectionRequests(media, detectionContext);

        assertEquals(2, detectionRequests.size());
        assertContainsExpectedMediaMetadata(detectionRequests);

        assertTrue(detectionRequests.stream()
                           .allMatch(dr -> dr.protobuf().getAlgorithmPropertiesCount() == 4));
        assertContainsAlgoProperty("algoKey1", "algoValue1", detectionRequests);
        assertContainsAlgoProperty("algoKey2", "algoValue2", detectionRequests);
        assertContainsAlgoProperty("FEED_FORWARD_TYPE", "FRAME", detectionRequests);


        var track1 = detectionRequests.get(0).protobuf()
                .getVideoRequest().getFeedForwardTrack();
        var track2 = detectionRequests.get(1).protobuf()
                .getVideoRequest().getFeedForwardTrack();
        DetectionProtobuf.VideoTrack shortTrack;
        DetectionProtobuf.VideoTrack longTrack;
        // The protobuf should contain both tracks, but we don't know what order they will be in.
        if (track1.getFrameLocationsCount() == 1) {
            shortTrack = track1;
            longTrack = track2;
        }
        else {
            shortTrack = track2;
            longTrack = track1;
        }

        assertEquals(2, longTrack.getFrameLocationsCount());
        assertContainsFrameLocation(20, longTrack);
        assertContainsFrameLocation(40, longTrack);
        assertEquals(20, longTrack.getStartFrame());
        assertEquals(40, longTrack.getStopFrame());

        assertEquals(1, shortTrack.getFrameLocationsCount());
        assertContainsFrameLocation(5, shortTrack);
        assertEquals(5, shortTrack.getStartFrame());
        assertEquals(5, shortTrack.getStopFrame());
        assertAllHaveFeedForwardTrack(detectionRequests);
    }

    @Test
    public void canCreateFeedForwardMessagesWithBestDetections() {
        Media media = createTestMedia();

        Detection d1 = new Detection(0, 0, 100, 100, (float)0.1, 2, 2,
                                     Map.of("BEST_SIZE", "true"));
        Detection d2 = new Detection(0, 0, 10, 10, (float)0.9, 0, 0,
                                    Map.of());
        Detection d3 = new Detection(0, 0, 10, 10, (float)0.8, 1, 1,
                                    Map.of());
        Detection d4 = new Detection(0, 0, 10, 10, (float)0.7, 3, 3,
                                    Map.of());

        Track trackInput = createTrack(d1, d2, d3, d4);
        Set<Track> tracks = ImmutableSet.of(trackInput);
        Map<String, String> propMap = new HashMap<>();
        propMap.put("FEED_FORWARD_TYPE", "FRAME");
        propMap.put("FEED_FORWARD_TOP_QUALITY_COUNT", "2");
        propMap.put("FEED_FORWARD_BEST_DETECTION_PROP_NAMES_LIST", "BEST_SIZE");

        var detectionContext = createTestDetectionContext(1, propMap, tracks, "CONFIDENCE");

        when(_mockTriggerProcessor.getTriggeredTracks(media, detectionContext))
                .thenReturn(tracks.stream());

        var detectionRequests = _videoMediaSegmenter.createDetectionRequests(media, detectionContext);

        assertEquals(1, detectionRequests.size());
        assertContainsExpectedMediaMetadata(detectionRequests);

        var request = detectionRequests.get(0).protobuf();
        assertTrue(request.getAlgorithmPropertiesCount() == 5);
        assertContainsAlgoProperty("algoKey1", "algoValue1", detectionRequests);
        assertContainsAlgoProperty("algoKey2", "algoValue2", detectionRequests);
        assertContainsAlgoProperty("FEED_FORWARD_TYPE", "FRAME", detectionRequests);
        assertContainsAlgoProperty("FEED_FORWARD_TOP_QUALITY_COUNT", "2", detectionRequests);
        assertContainsAlgoProperty("FEED_FORWARD_BEST_DETECTION_PROP_NAMES_LIST", "BEST_SIZE", detectionRequests);


        var track = request.getVideoRequest().getFeedForwardTrack();

        assertEquals(3, track.getFrameLocationsCount());
        var frameLocations = track.getFrameLocationsMap().keySet();
        assertTrue(frameLocations.contains(0));
        assertTrue(frameLocations.contains(1));
        assertTrue(frameLocations.contains(2));
        assertFalse(frameLocations.contains(3));
    }



    @Test
    public void noMessagesCreatedWhenNoTracks() {
        Media media = createTestMedia();

        DetectionContext context = createTestDetectionContext(1, Map.of(), Set.of());
        assertTrue(_videoMediaSegmenter.createDetectionRequests(media, context).isEmpty());

        DetectionContext feedForwardContext = createTestDetectionContext(
                1, Map.of("FEED_FORWARD_TYPE", "FRAME"), Set.of());
        assertTrue(_videoMediaSegmenter.createDetectionRequests(media, feedForwardContext).isEmpty());
    }


    @Test
    public void canCreateFeedForwardAllTracksMessage() {
        Media media = createTestMedia();

        Set<Track> tracks = createTestTracks();

        DetectionContext context = createTestDetectionContext(
                1,  
                Map.of("FEED_FORWARD_TYPE", "FRAME", "FEED_FORWARD_ALL_TRACKS", "true"),
                tracks,
                "CONFIDENCE");

        when(_mockTriggerProcessor.getTriggeredTracks(media, context))
                .thenReturn(tracks.stream());

        var detectionRequests = _videoMediaSegmenter.createDetectionRequests(media, context);

        assertEquals(1, detectionRequests.size());

        var request = detectionRequests.get(0).protobuf().getAllVideoTracksRequest();
        assertEquals(2, request.getStartFrame());
        assertEquals(50, request.getStopFrame());

        assertContainsExpectedMediaMetadata(detectionRequests);

        assertTrue(detectionRequests.stream()
                .allMatch(dr -> dr.protobuf().getAlgorithmPropertiesCount() == 4));
        assertContainsAlgoProperty("algoKey1", "algoValue1", detectionRequests);
        assertContainsAlgoProperty("algoKey2", "algoValue2", detectionRequests);
        assertContainsAlgoProperty("FEED_FORWARD_TYPE", "FRAME", detectionRequests);
        assertContainsAlgoProperty("FEED_FORWARD_ALL_TRACKS", "true", detectionRequests);

        var ffTracks = request.getFeedForwardTracksList();

        var ffTrack1 = ffTracks.get(0);
        var ffTrack2 = ffTracks.get(1);
        DetectionProtobuf.VideoTrack shortTrack;
        DetectionProtobuf.VideoTrack longTrack;
        // The protobuf should contain both tracks, but we don't know what order they will be in.
        if (ffTrack1.getFrameLocationsCount() == 1) {
            shortTrack = ffTrack1;
            longTrack = ffTrack2;
        }
        else {
            shortTrack = ffTrack2;
            longTrack = ffTrack1;
        }

        assertEquals(4, longTrack.getFrameLocationsCount());
        assertContainsFrameLocation(2, longTrack);
        assertContainsFrameLocation(20, longTrack);
        assertContainsFrameLocation(40, longTrack);
        assertEquals(2, longTrack.getStartFrame());
        assertEquals(50, longTrack.getStopFrame());

        assertEquals(1, shortTrack.getFrameLocationsCount());
        assertContainsFrameLocation(5, shortTrack);
        assertEquals(5, shortTrack.getStartFrame());
        assertEquals(5, shortTrack.getStopFrame());
        assertAllHaveFeedForwardTrack(detectionRequests);
    }


    private static void assertContainsSegment(int begin, int end, Collection<DetectionRequest> requests) {
        long numMatchingSegments = requests.stream()
                .map(r -> r.protobuf().getVideoRequest())
                .filter(vr -> vr.getStartFrame() == begin && vr.getStopFrame() == end)
                .count();
        assertEquals(String.format(
                "Expected detection requests to contain 1 request that starts at frame: %s and ends at frame: %s",
                    begin, end),
                1, numMatchingSegments);
    }

    private static void assertContainsFrameLocation(float confidence, DetectionProtobuf.VideoTrack track) {
        int dimensions = (int) confidence;
        var imageLocation = track.getFrameLocationsMap().get(dimensions);
        assertNotNull(imageLocation);
        assertTrue(confidenceIsEqualToDimensions(confidence, imageLocation));
    }

    private static Media createTestMedia() {
        var mediaUri = MediaUri.create("file:///example.avi");
        MediaImpl media = new MediaImpl(
                1, mediaUri, UriScheme.get(mediaUri), Paths.get(mediaUri.get()), Map.of(),
                Map.of(), List.of(), List.of(), List.of(), null, null);
        media.setLength(50);
        media.addMetadata("mediaKey1", "mediaValue1");
        return media;
    }

    private static Media createTestMediaWithFps(
            List<MediaRange> frameBoundaries,
            List<MediaRange> timeBoundaries) {
        var mediaUri = new MediaUri(TestUtil.findFile("/samples/video_01.mp4"));
        MediaImpl media = new MediaImpl(
                1, mediaUri, UriScheme.get(mediaUri), Paths.get(mediaUri.get()), Map.of(),
                Map.of(), frameBoundaries, timeBoundaries, List.of(), null, null);
        media.setLength(200);
        var fps = new Fraction(30_000, 1_001);
        media.addMetadata("FPS", Double.toString(fps.toDouble()));
        var ffprobeMetadata = new FfprobeMetadata.Video(
                -1, -1, fps, OptionalLong.of(200), OptionalLong.empty(), 0,
                new Fraction(1, 30_000));
        media.setFrameTimeInfo(
                FrameTimeInfoBuilder.getFrameTimeInfo(media.getLocalPath(), ffprobeMetadata));
        return media;
    }

    private static Set<Track> createTestTracks() {
        Track shortTrack = createTrack(createDetection(5, 5));

        Track longTrack = createTrack(
                createDetection(2, 2),
                createDetection(20, 20),
                createDetection(40, 40),
                createDetection(50, 20));

        return ImmutableSet.of(shortTrack, longTrack);
    }
}
