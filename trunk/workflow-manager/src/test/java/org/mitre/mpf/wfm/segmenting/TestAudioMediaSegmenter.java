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
import static org.junit.Assert.assertTrue;
import static org.mitre.mpf.wfm.segmenting.TestMediaSegmenter.assertAllHaveFeedForwardTrack;
import static org.mitre.mpf.wfm.segmenting.TestMediaSegmenter.assertContainsAlgoProperty;
import static org.mitre.mpf.wfm.segmenting.TestMediaSegmenter.assertContainsExpectedMediaMetadata;
import static org.mitre.mpf.wfm.segmenting.TestMediaSegmenter.assertNoneHaveFeedForwardTrack;
import static org.mitre.mpf.wfm.segmenting.TestMediaSegmenter.containsExpectedDetectionProperties;
import static org.mitre.mpf.wfm.segmenting.TestMediaSegmenter.createDetectionProperties;
import static org.mitre.mpf.wfm.segmenting.TestMediaSegmenter.createTestDetectionContext;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionContext;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.MediaImpl;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;


public class TestAudioMediaSegmenter extends MockitoTest.Strict {

    @Mock
    private TriggerProcessor _mockTriggerProcessor;

    @InjectMocks
    private AudioMediaSegmenter _audioMediaSegmenter;

	@Test
	public void canCreateFirstStageMessages() {
		Media media = createTestMedia();
		DetectionContext context = createTestDetectionContext(
				0, Collections.singletonMap("FEED_FORWARD_TYPE", "FRAME"), Collections.emptySet());

		var detectionRequests = _audioMediaSegmenter.createDetectionRequests(media, context);
		assertEquals(1, detectionRequests.size());

		assertContainsExpectedMediaMetadata(detectionRequests);

		// Verify FEED_FORWARD_TYPE has been removed
		assertTrue(detectionRequests.stream()
				           .allMatch(dr -> dr.protobuf().getAlgorithmPropertiesCount() == 2));
		assertContainsAlgoProperty("algoKey1", "algoValue1", detectionRequests);
		assertContainsAlgoProperty("algoKey2", "algoValue2", detectionRequests);
        assertNoneHaveFeedForwardTrack(detectionRequests);
	}


	@Test
	public void canCreateNonFeedForwardRequests() {
		Media media = createTestMedia();

		Set<Track> tracks = createTestTracks();

		DetectionContext context = createTestDetectionContext(1, Collections.emptyMap(), tracks);

		var detectionRequests = _audioMediaSegmenter.createDetectionRequests(media, context);
		assertEquals(1, detectionRequests.size());

		assertContainsExpectedMediaMetadata(detectionRequests);

		assertTrue(detectionRequests.stream()
				           .allMatch(dr -> dr.protobuf().getAlgorithmPropertiesCount() == 2));
		assertContainsAlgoProperty("algoKey1", "algoValue1", detectionRequests);
		assertContainsAlgoProperty("algoKey2", "algoValue2", detectionRequests);
        assertNoneHaveFeedForwardTrack(detectionRequests);
	}


	@Test
	public void canCreateFeedForwardRequests() {
		Media media = createTestMedia();

		Set<Track> tracks = createTestTracks();

		DetectionContext context = createTestDetectionContext(
				1, Collections.singletonMap("FEED_FORWARD_TYPE", "FRAME"), tracks, "CONFIDENCE");

        when(_mockTriggerProcessor.getTriggeredTracks(media, context))
                .thenReturn(tracks.stream());

		var detectionRequests = _audioMediaSegmenter.createDetectionRequests(media, context);

		assertEquals(2, detectionRequests.size());
		assertContainsExpectedMediaMetadata(detectionRequests);

		assertTrue(detectionRequests.stream()
				           .allMatch(dr -> dr.protobuf().getAlgorithmPropertiesCount() == 3));
		assertContainsAlgoProperty("algoKey1", "algoValue1", detectionRequests);
		assertContainsAlgoProperty("algoKey2", "algoValue2", detectionRequests);
		assertContainsAlgoProperty("FEED_FORWARD_TYPE", "FRAME", detectionRequests);


		assertContainsExpectedTrack(5, 5, 10, detectionRequests);
		assertContainsExpectedTrack(15, 15, 30, detectionRequests);
        assertAllHaveFeedForwardTrack(detectionRequests);
	}


	@Test
	public void noRequestsCreatedWhenNoFeedForwardTracks() {
		Media media = createTestMedia();
		DetectionContext feedForwardContext = createTestDetectionContext(
				1, Collections.singletonMap("FEED_FORWARD_TYPE", "FRAME"), Collections.emptySet(), "CONFIDENCE");
        var detectionRequests = _audioMediaSegmenter.createDetectionRequests(
                media, feedForwardContext);
		assertTrue(detectionRequests.isEmpty());
	}


	private static void assertContainsExpectedTrack(
            float confidence, int startTime, int stopTime,
            Collection<DetectionRequest> requests) {
        var audioRequest = requests.stream()
				.map(r -> r.protobuf().getAudioRequest())
				.filter(ar -> ar.getStartTime() == startTime)
				.findAny()
				.get();

		assertEquals(stopTime, audioRequest.getStopTime());

		DetectionProtobuf.AudioTrack track = audioRequest.getFeedForwardTrack();
		assertEquals(confidence, track.getConfidence(), 0.01);
		assertEquals(startTime, track.getStartTime());
		assertEquals(stopTime, track.getStopTime());

		assertTrue(containsExpectedDetectionProperties((int) confidence, track.getDetectionPropertiesMap()));
	}


	private static Media createTestMedia() {
		URI mediaUri = URI.create("file:///example.wav");
		MediaImpl media = new MediaImpl(
				1, mediaUri.toString(), UriScheme.get(mediaUri), Paths.get(mediaUri), Map.of(),
				Map.of(), List.of(), List.of(), List.of(), null, null);
		media.setLength(1);
		media.addMetadata("mediaKey1", "mediaValue1");
		return media;
	}


	private static Set<Track> createTestTracks() {
		Detection detection1 = createDetection(5, 5);
		Track track1 = new Track(1, 1, 0, 0, 0,
		                         -1, 5, 10, 5,
		                         ImmutableSortedSet.of(detection1), Collections.emptyMap(),
		                         "", "", null, null);

		Detection detection2 = createDetection(15, 15);
		Track track2 = new Track(1, 1, 0, 0, 0,
		                         -1, 15, 30, 15,
		                         ImmutableSortedSet.of(detection2), Collections.emptyMap(),
		                         "", "", null, null);

		return ImmutableSet.of(track1, track2);
	}


	private static Detection createDetection(float confidence, int time) {
		return new Detection(0, 0, 0, 0, confidence, 0, time,
		                     createDetectionProperties(time));
	}
}
