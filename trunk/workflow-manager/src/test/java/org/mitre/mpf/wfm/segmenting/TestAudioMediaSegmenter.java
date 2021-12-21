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

package org.mitre.mpf.wfm.segmenting;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import org.apache.camel.Message;
import org.junit.Test;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionRequest;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionContext;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.MediaImpl;
import org.mitre.mpf.wfm.enums.UriScheme;

import java.net.URI;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mitre.mpf.wfm.segmenting.TestMediaSegmenter.*;

public class TestAudioMediaSegmenter {

	@Test
	public void canCreateFirstStageMessages() {
		Media media = createTestMedia();
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
		Media media = createTestMedia();

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
		Media media = createTestMedia();

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


		assertContainsExpectedTrack(5, 5, 10, detectionRequests);
		assertContainsExpectedTrack(15, 15, 30, detectionRequests);
	}


	@Test
	public void noMessagesCreatedWhenNoFeedForwardTracks() {
		Media media = createTestMedia();
		DetectionContext feedForwardContext = createTestDetectionContext(
				1, Collections.singletonMap("FEED_FORWARD_TYPE", "FRAME"), Collections.emptySet());
		assertTrue(runAudioSegmenter(media, feedForwardContext).isEmpty());
	}


	private static List<DetectionRequest> runAudioSegmenter(Media media, DetectionContext context) {
		MediaSegmenter segmenter = new AudioMediaSegmenter();
		List<Message> messages = segmenter.createDetectionRequestMessages(media, context);
		return unwrapMessages(messages);
	}


	private static void assertContainsExpectedTrack(float confidence, int startTime, int stopTime,
	                                                Collection<DetectionRequest> requests) {
		DetectionRequest.AudioRequest audioRequest = requests.stream()
				.map(DetectionRequest::getAudioRequest)
				.filter(ar -> ar.getStartTime() == startTime)
				.findAny()
				.get();

		assertEquals(stopTime, audioRequest.getStopTime());

		DetectionProtobuf.AudioTrack track = audioRequest.getFeedForwardTrack();
		assertEquals(confidence, track.getConfidence(), 0.01);
		assertEquals(startTime, track.getStartTime());
		assertEquals(stopTime, track.getStopTime());

		assertTrue(containsExpectedDetectionProperties((int) confidence, track.getDetectionPropertiesList()));
	}


	private static Media createTestMedia() {
		URI mediaUri = URI.create("file:///example.wav");
		MediaImpl media = new MediaImpl(
				1, mediaUri.toString(), UriScheme.get(mediaUri), Paths.get(mediaUri), Map.of(),
				Map.of(), List.of(), List.of(), null);
		media.setLength(1);
		media.addMetadata("mediaKey1", "mediaValue1");
		return media;
	}


	private static Set<Track> createTestTracks() {
		Detection detection1 = createDetection(5, 5);
		Track track1 = new Track(1, 1, 0, 0, 0,
		                         -1, 5, 10, "", 5,
		                         ImmutableSortedSet.of(detection1), Collections.emptyMap());

		Detection detection2 = createDetection(15, 15);
		Track track2 = new Track(1, 1, 0, 0, 0,
		                         -1, 15, 30, "", 15,
		                         ImmutableSortedSet.of(detection2), Collections.emptyMap());

		return ImmutableSet.of(track1, track2);
	}


	private static Detection createDetection(float confidence, int time) {
		return new Detection(0, 0, 0, 0, confidence, 0, time,
		                     createDetectionProperties(time));
	}
}
