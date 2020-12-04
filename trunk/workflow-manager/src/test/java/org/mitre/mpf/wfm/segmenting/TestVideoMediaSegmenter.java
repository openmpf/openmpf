/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2020 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2020 The MITRE Corporation                                       *
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.camel.Message;
import org.junit.Test;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionRequest;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionContext;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.MediaImpl;
import org.mitre.mpf.wfm.enums.UriScheme;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mitre.mpf.wfm.segmenting.TestMediaSegmenter.*;

public class TestVideoMediaSegmenter {


	@Test
	public void canCreateFirstStageMessages() {
		Media media = createTestMedia();
		DetectionContext context = createTestDetectionContext(
				0,  Collections.singletonMap("FEED_FORWARD_TYPE", "FRAME"), Collections.emptySet());

		List<DetectionRequest> detectionRequests = runSegmenter(media, context);

		assertEquals(3, detectionRequests.size());
		assertContainsSegment(0, 19, detectionRequests);
		assertContainsSegment(20, 39, detectionRequests);
		assertContainsSegment(40, 49, detectionRequests);

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

		List<DetectionRequest> detectionRequests = runSegmenter(media, context);

		// range 2 -> 40
		assertEquals(2, detectionRequests.size());
		assertContainsSegment(2, 21, detectionRequests);
		assertContainsSegment(22, 40, detectionRequests);

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

		List<DetectionRequest> detectionRequests = runSegmenter(media, context);

		assertEquals(2, detectionRequests.size());
		assertContainsExpectedMediaMetadata(detectionRequests);

		assertTrue(detectionRequests.stream()
				           .allMatch(dr -> dr.getAlgorithmPropertyList().size() == 3));
		assertContainsAlgoProperty("algoKey1", "algoValue1", detectionRequests);
		assertContainsAlgoProperty("algoKey2", "algoValue2", detectionRequests);
		assertContainsAlgoProperty("FEED_FORWARD_TYPE", "FRAME", detectionRequests);


		DetectionProtobuf.VideoTrack shortTrack;
		DetectionProtobuf.VideoTrack longTrack;
		// The protobuf should contain both tracks, but we don't know what order they will be in.
		if (detectionRequests.get(0).getVideoRequest().getFeedForwardTrack().getFrameLocationsCount() == 1) {
			shortTrack = detectionRequests.get(0).getVideoRequest().getFeedForwardTrack();
			longTrack = detectionRequests.get(1).getVideoRequest().getFeedForwardTrack();
		}
		else {
			shortTrack = detectionRequests.get(1).getVideoRequest().getFeedForwardTrack();
			longTrack = detectionRequests.get(0).getVideoRequest().getFeedForwardTrack();
		}

		assertEquals(3, longTrack.getFrameLocationsCount());
		assertContainsFrameLocation(2, longTrack);
		assertContainsFrameLocation(20, longTrack);
		assertContainsFrameLocation(40, longTrack);
		assertEquals(2, longTrack.getStartFrame());
		assertEquals(40, longTrack.getStopFrame());

		assertEquals(1, shortTrack.getFrameLocationsCount());
		assertContainsFrameLocation(5, shortTrack);
		assertEquals(5, shortTrack.getStartFrame());
		assertEquals(5, shortTrack.getStopFrame());
	}


	@Test
	public void canCreateFeedForwardMessagesWithTopConfidenceCount() {
		Media media = createTestMedia();

		Set<Track> tracks = createTestTracks();

		DetectionContext context = createTestDetectionContext(
				1,
				ImmutableMap.of("FEED_FORWARD_TYPE", "FRAME", "FEED_FORWARD_TOP_CONFIDENCE_COUNT", "2"),
				tracks);

		List<DetectionRequest> detectionRequests = runSegmenter(media, context);

		assertEquals(2, detectionRequests.size());
		assertContainsExpectedMediaMetadata(detectionRequests);

		assertTrue(detectionRequests.stream()
				           .allMatch(dr -> dr.getAlgorithmPropertyList().size() == 4));
		assertContainsAlgoProperty("algoKey1", "algoValue1", detectionRequests);
		assertContainsAlgoProperty("algoKey2", "algoValue2", detectionRequests);
		assertContainsAlgoProperty("FEED_FORWARD_TYPE", "FRAME", detectionRequests);


		DetectionProtobuf.VideoTrack shortTrack;
		DetectionProtobuf.VideoTrack longTrack;
		// The protobuf should contain both tracks, but we don't know what order they will be in.
		if (detectionRequests.get(0).getVideoRequest().getFeedForwardTrack().getFrameLocationsCount() == 1) {
			shortTrack = detectionRequests.get(0).getVideoRequest().getFeedForwardTrack();
			longTrack = detectionRequests.get(1).getVideoRequest().getFeedForwardTrack();
		}
		else {
			shortTrack = detectionRequests.get(1).getVideoRequest().getFeedForwardTrack();
			longTrack = detectionRequests.get(0).getVideoRequest().getFeedForwardTrack();
		}

		assertEquals(2, longTrack.getFrameLocationsCount());
		assertContainsFrameLocation(20, longTrack);
		assertContainsFrameLocation(40, longTrack);
		assertEquals(2, longTrack.getStartFrame());
		assertEquals(40, longTrack.getStopFrame());

		assertEquals(1, shortTrack.getFrameLocationsCount());
		assertContainsFrameLocation(5, shortTrack);
		assertEquals(5, shortTrack.getStartFrame());
		assertEquals(5, shortTrack.getStopFrame());
	}



	@Test
	public void noMessagesCreatedWhenNoTracks() {
		Media media = createTestMedia();

		DetectionContext context = createTestDetectionContext(1, Collections.emptyMap(), Collections.emptySet());
		assertTrue(runSegmenter(media, context).isEmpty());

		DetectionContext feedForwardContext = createTestDetectionContext(
				1, Collections.singletonMap("FEED_FORWARD_TYPE", "FRAME"), Collections.emptySet());
		assertTrue(runSegmenter(media, feedForwardContext).isEmpty());
	}




	private static void assertContainsSegment(int begin, int end, Collection<DetectionRequest> requests) {
		long numMatchingSegments = requests.stream()
				.map(DetectionRequest::getVideoRequest)
				.filter(vr -> vr.getStartFrame() == begin && vr.getStopFrame() == end)
				.count();
		assertEquals(String.format(
				"Expected detection requests to contain 1 request that starts at frame: %s and ends at frame: %s",
					begin, end),
			    1, numMatchingSegments);
	}



	private static void assertContainsFrameLocation(float confidence, DetectionProtobuf.VideoTrack track) {
		int dimensions = (int) confidence;
		assertTrue(track.getFrameLocationsList().stream()
				.anyMatch(flm -> flm.getFrame() == dimensions
						&& confidenceIsEqualToDimensions(confidence, flm.getImageLocation())));
	}



	private static List<DetectionRequest> runSegmenter(Media media, DetectionContext context) {
		MediaSegmenter segmenter = new VideoMediaSegmenter();
		List<Message> messages = segmenter.createDetectionRequestMessages(media, context);
		return unwrapMessages(messages);
	}


	private static Media createTestMedia() {
		URI mediaUri = URI.create("file:///example.avi");
		MediaImpl media = new MediaImpl(
				1, mediaUri.toString(), UriScheme.get(mediaUri), Paths.get(mediaUri), Collections.emptyMap(),
				Collections.emptyMap(), null);
		media.setLength(50);
		media.addMetadata("mediaKey1", "mediaValue1");
		return media;
	}


	private static Set<Track> createTestTracks() {
		Track shortTrack = createTrack(createDetection(5, 5));

		Track longTrack = createTrack(
				createDetection(2, 2),
				createDetection(20, 20),
				createDetection(40, 40));

		return ImmutableSet.of(shortTrack, longTrack);
	}
}
