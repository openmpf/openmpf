/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mitre.mpf.wfm.segmenting.TestMediaSegmenter.*;

public class TestAudioMediaSegmenter {

	@Test
	public void canCreateFirstStageMessages() {
		TransientMedia media = createTestMedia();
		DetectionContext context = createTestDetectionContext(
				0, Collections.singletonMap("FEED_FORWARD_TYPE", "FRAME"), Collections.emptySet());

		List<DetectionRequest> detectionRequests = runAudioSegmenter(media, context);
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

		List<DetectionRequest> detectionRequests = runAudioSegmenter(media, context);
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

		List<DetectionRequest> detectionRequests = runAudioSegmenter(media, context);

		assertEquals(2, detectionRequests.size());
		assertContainsExpectedMediaMetadata(detectionRequests);

		assertTrue(detectionRequests.stream()
				           .allMatch(dr -> dr.getAlgorithmPropertyList().size() == 3));
		assertContainsAlgoProperty("algoKey1", "algoValue1", detectionRequests);
		assertContainsAlgoProperty("algoKey2", "algoValue2", detectionRequests);
		assertContainsAlgoProperty("FEED_FORWARD_TYPE", "FRAME", detectionRequests);


		assertContainsExpectedTrack(5, detectionRequests);
		assertContainsExpectedTrack(10, detectionRequests);
	}


	@Test
	public void noMessagesCreatedWhenNoFeedForwardTracks() {
		TransientMedia media = createTestMedia();
		DetectionContext feedForwardContext = createTestDetectionContext(
				1, Collections.singletonMap("FEED_FORWARD_TYPE", "FRAME"), Collections.emptySet());
		assertTrue(runAudioSegmenter(media, feedForwardContext).isEmpty());
	}


	private static List<DetectionRequest> runAudioSegmenter(TransientMedia media, DetectionContext context) {
		MediaSegmenter segmenter = new AudioMediaSegmenter();
		List<Message> messages = segmenter.createDetectionRequestMessages(media, context);
		return unwrapMessages(messages);
	}


	private static void assertContainsExpectedTrack(float confidence, Collection<DetectionRequest> requests) {
		int times = (int) confidence;
		DetectionRequest.AudioRequest audioRequest = requests.stream()
				.map(DetectionRequest::getAudioRequest)
				.filter(ar -> ar.getStartTime() == times)
				.findAny()
				.get();

		assertEquals(times, audioRequest.getStopTime());

		DetectionProtobuf.AudioTrack track = audioRequest.getFeedForwardTrack();
		assertEquals(confidence, track.getConfidence(), 0.01);
		assertEquals(times, track.getStartTime());
		assertEquals(times, track.getStopTime());

		assertTrue(containsExpectedDetectionProperties(times, track.getDetectionPropertiesList()));
	}


	private static TransientMedia createTestMedia() {
		TransientMedia media = new TransientMedia(1, "file:///example.jpg");
		media.setLength(1);
		media.addMetadata("mediaKey1", "mediaValue1");
		return media;
	}


	private static Set<Track> createTestTracks() {
		Detection detection1 = createDetection(5, 5);
		Track track1 = new Track(1, 1, 0, 0, 0,
		                         -1, 5, 5, "");
		track1.setExemplar(detection1);
		track1.getDetections().add(detection1);

		Detection detection2 = createDetection(10, 10);
		Track track2 = new Track(1, 1, 0, 0, 0,
		                         -1, 10, 10, "");
		track2.setExemplar(detection2);
		track2.getDetections().add(detection2);

		return ImmutableSet.of(track1, track2);
	}


	private static Detection createDetection(float confidence, int time) {
		return new Detection(0, 0, 0, 0, confidence, 0, time,
		                     createDetectionProperties(time));
	}
}
