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

package org.mitre.mpf.wfm.segmenting;

import com.google.common.collect.ImmutableSet;
import org.apache.camel.Message;
import org.junit.Test;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionRequest;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionContext;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.data.entities.transients.TransientMedia;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mitre.mpf.wfm.segmenting.TestMediaSegmenter.*;

public class TestDefaultMediaSegmenter {

	@Test
	public void canCreateFirstStageMessages() {
		TransientMedia media = createTestMedia();
		DetectionContext context = createTestDetectionContext(
				0, Collections.singletonMap("FEED_FORWARD_TYPE", "FRAME"), Collections.emptySet());

		List<DetectionRequest> detectionRequests = runDefaultSegmenter(media, context);
		assertEquals(1, detectionRequests.size());

		assertContainsExpectedMediaMetadata(detectionRequests);

		// Verify FEED_FORWARD_TYPE has been removed
		assertTrue(detectionRequests.stream()
				           .allMatch(dr -> dr.getAlgorithmPropertyList().size() == 2));
		assertContainsAlgoProperty("algoKey1", "algoValue1", detectionRequests);
		assertContainsAlgoProperty("algoKey2", "algoValue2", detectionRequests);
	}


	@Test
	public void canCreateNonFeedForwardMessages() {
		TransientMedia media = createTestMedia();

		Set<Track> tracks = createTestTracks();

		DetectionContext context = createTestDetectionContext(1, Collections.emptyMap(), tracks);

		List<DetectionRequest> detectionRequests = runDefaultSegmenter(media, context);
		assertEquals(1, detectionRequests.size());

		assertContainsExpectedMediaMetadata(detectionRequests);

		assertTrue(detectionRequests.stream()
				           .allMatch(dr -> dr.getAlgorithmPropertyList().size() == 2));
		assertContainsAlgoProperty("algoKey1", "algoValue1", detectionRequests);
		assertContainsAlgoProperty("algoKey2", "algoValue2", detectionRequests);
	}


	@Test
	public void canCreateFeedForwardMessages() {
		TransientMedia media = createTestMedia();

		Set<Track> tracks = createTestTracks();

		DetectionContext context = createTestDetectionContext(
				1, Collections.singletonMap("FEED_FORWARD_TYPE", "FRAME"), tracks);

		List<DetectionRequest> detectionRequests = runDefaultSegmenter(media, context);

		assertEquals(2, detectionRequests.size());
		assertContainsExpectedMediaMetadata(detectionRequests);

		assertTrue(detectionRequests.stream()
				           .allMatch(dr -> dr.getAlgorithmPropertyList().size() == 3));
		assertContainsAlgoProperty("algoKey1", "algoValue1", detectionRequests);
		assertContainsAlgoProperty("algoKey2", "algoValue2", detectionRequests);
		assertContainsAlgoProperty("FEED_FORWARD_TYPE", "FRAME", detectionRequests);


		assertContainsExpectedTrack(0.00f, detectionRequests);
		assertContainsExpectedTrack(0.10f, detectionRequests);
	}


	@Test
	public void noMessagesCreatedWhenNoFeedForwardTracks() {
		TransientMedia media = createTestMedia();
		DetectionContext feedForwardContext = createTestDetectionContext(
				1, Collections.singletonMap("FEED_FORWARD_TYPE", "FRAME"), Collections.emptySet());
		assertTrue(runDefaultSegmenter(media, feedForwardContext).isEmpty());
	}


	private static List<DetectionRequest> runDefaultSegmenter(TransientMedia media, DetectionContext context) {
		MediaSegmenter segmenter = new DefaultMediaSegmenter();
		List<Message> messages = segmenter.createDetectionRequestMessages(media, context);
		return unwrapMessages(messages);
	}


	private static void assertContainsExpectedTrack(float confidence, Collection<DetectionRequest> requests) {
		DetectionProtobuf.GenericTrack genericTrack = requests.stream()
				.map(dr -> dr.getGenericRequest().getFeedForwardTrack())
				.filter(gt -> gt.getConfidence() == confidence)
				.findAny()
				.get();

		assertTrue(containsExpectedDetectionProperties((int)(confidence * 10), genericTrack.getDetectionPropertiesList()));
	}


	private static TransientMedia createTestMedia() {
		TransientMedia media = new TransientMedia(1, "file:///example.foo");
		media.setLength(1);
		media.addMetadata("mediaKey1", "mediaValue1");
		return media;
	}


	private static Set<Track> createTestTracks() {
		Detection detection1 = createDetection(0.00f);
		Track track1 = new Track(1, 1, 0, 0, 0,
		                         -1, 0, 0, "", 0);
		track1.setExemplar(detection1);
		track1.getDetections().add(detection1);

		Detection detection2 = createDetection(0.10f);
		Track track2 = new Track(1, 1, 0, 0, 0,
		                         -1, 0, 0, "", 0.10f);
		track2.setExemplar(detection2);
		track2.getDetections().add(detection2);

		return ImmutableSet.of(track1, track2);
	}


	private static Detection createDetection(float confidence) {
		return new Detection(0, 0, 0, 0, confidence, 0, 0,
		                     createDetectionProperties((int)(confidence * 10)));
	}
}
