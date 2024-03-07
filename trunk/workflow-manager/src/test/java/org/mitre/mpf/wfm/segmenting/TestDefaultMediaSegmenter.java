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

package org.mitre.mpf.wfm.segmenting;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import org.junit.Test;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionContext;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.MediaImpl;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.net.URI;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mitre.mpf.wfm.segmenting.TestMediaSegmenter.*;
import static org.mockito.Mockito.when;

public class TestDefaultMediaSegmenter extends MockitoTest.Strict {

    @Mock
    private TriggerProcessor _mockTriggerProcessor;

    @InjectMocks
    private DefaultMediaSegmenter _defaultMediaSegmenter;


	@Test
	public void canCreateFirstStageMessages() {
		Media media = createTestMedia();
		DetectionContext context = createTestDetectionContext(
				0, Collections.singletonMap("FEED_FORWARD_TYPE", "FRAME"), Collections.emptySet());

		var detectionRequests = _defaultMediaSegmenter.createDetectionRequests(media, context);
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
	public void canCreateNonFeedForwardMessages() {
		Media media = createTestMedia();

		Set<Track> tracks = createTestTracks();

		DetectionContext context = createTestDetectionContext(1, Collections.emptyMap(), tracks);

		var detectionRequests = _defaultMediaSegmenter.createDetectionRequests(media, context);
		assertEquals(1, detectionRequests.size());

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

		DetectionContext context = createTestDetectionContext(
				1, Collections.singletonMap("FEED_FORWARD_TYPE", "FRAME"), tracks);

        when(_mockTriggerProcessor.getTriggeredTracks(media, context))
                .thenReturn(tracks.stream());

		var detectionRequests = _defaultMediaSegmenter.createDetectionRequests(media, context);

		assertEquals(2, detectionRequests.size());
		assertContainsExpectedMediaMetadata(detectionRequests);

		assertTrue(detectionRequests.stream()
				           .allMatch(dr -> dr.protobuf().getAlgorithmPropertiesCount() == 3));
		assertContainsAlgoProperty("algoKey1", "algoValue1", detectionRequests);
		assertContainsAlgoProperty("algoKey2", "algoValue2", detectionRequests);
		assertContainsAlgoProperty("FEED_FORWARD_TYPE", "FRAME", detectionRequests);


		assertContainsExpectedTrack(0.00f, detectionRequests);
		assertContainsExpectedTrack(0.10f, detectionRequests);
        assertAllHaveFeedForwardTrack(detectionRequests);
	}


	@Test
	public void noMessagesCreatedWhenNoFeedForwardTracks() {
		Media media = createTestMedia();
		DetectionContext feedForwardContext = createTestDetectionContext(
				1, Collections.singletonMap("FEED_FORWARD_TYPE", "FRAME"), Collections.emptySet());
		assertTrue(_defaultMediaSegmenter.createDetectionRequests(media, feedForwardContext).isEmpty());
	}


	private static void assertContainsExpectedTrack(float confidence, Collection<DetectionRequest> requests) {
		DetectionProtobuf.GenericTrack genericTrack = requests.stream()
				.map(dr -> dr.protobuf().getGenericRequest().getFeedForwardTrack())
				.filter(gt -> gt.getConfidence() == confidence)
				.findAny()
				.get();

		assertTrue(containsExpectedDetectionProperties((int)(confidence * 10), genericTrack.getDetectionPropertiesMap()));
	}


	private static Media createTestMedia() {
		URI mediaUri = URI.create("file:///example.foo");
		MediaImpl media = new MediaImpl(
				1, mediaUri.toString(), UriScheme.get(mediaUri), Paths.get(mediaUri), Map.of(),
				Map.of(), List.of(), List.of(), null);
		media.setLength(1);
		media.addMetadata("mediaKey1", "mediaValue1");
		return media;
	}


	private static Set<Track> createTestTracks() {
		Detection detection1 = createDetection(0.00f);
		Track track1 = new Track(1, 1, 0, 0, 0,
		                         -1, 0, 0, 0, 0,
		                         ImmutableSortedSet.of(detection1), Collections.emptyMap(),
		                         "");

		Detection detection2 = createDetection(0.10f);
		Track track2 = new Track(1, 1, 0, 0, 0,
		                         -1, 0, 0, 0, 0.10f,
		                         ImmutableSortedSet.of(detection2), Collections.emptyMap(),
		                         "");

		return ImmutableSet.of(track1, track2);
	}


	private static Detection createDetection(float confidence) {
		return new Detection(0, 0, 0, 0, confidence, 0, 0,
		                     createDetectionProperties((int)(confidence * 10)));
	}
}
