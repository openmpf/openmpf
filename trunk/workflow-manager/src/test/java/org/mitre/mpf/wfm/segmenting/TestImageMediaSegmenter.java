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

import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionContext;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.MediaImpl;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mitre.mpf.wfm.segmenting.TestMediaSegmenter.*;
import static org.mockito.Mockito.when;

public class TestImageMediaSegmenter extends MockitoTest.Strict {

    @Mock
    private TriggerProcessor _mockTriggerProcessor;


    @InjectMocks
    private ImageMediaSegmenter _imageMediaSegmenter;

	@Test
	public void canCreateFirstStageMessages() {
		Media media = createTestMedia();
		DetectionContext context = createTestDetectionContext(
				0, Collections.singletonMap("FEED_FORWARD_TYPE", "FRAME"), Collections.emptySet());

		var detectionRequests = _imageMediaSegmenter.createDetectionRequests(media, context);
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

		var detectionRequests = _imageMediaSegmenter.createDetectionRequests(media, context);
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
				1, Collections.singletonMap("FEED_FORWARD_TYPE", "FRAME"), tracks, "CONFIDENCE");

        when(_mockTriggerProcessor.getTriggeredTracks(media, context))
                .thenReturn(tracks.stream());

		var detectionRequests = _imageMediaSegmenter.createDetectionRequests(media, context);

		assertEquals(2, detectionRequests.size());
		assertContainsExpectedMediaMetadata(detectionRequests);

		assertTrue(detectionRequests.stream()
				           .allMatch(dr -> dr.protobuf().getAlgorithmPropertiesCount() == 3));
		assertContainsAlgoProperty("algoKey1", "algoValue1", detectionRequests);
		assertContainsAlgoProperty("algoKey2", "algoValue2", detectionRequests);
		assertContainsAlgoProperty("FEED_FORWARD_TYPE", "FRAME", detectionRequests);

        detectionRequests.stream().forEach(dr -> System.out.println(dr.protobuf().getImageRequest().getFeedForwardLocation().getConfidence()));
		assertTrue(detectionRequests.stream()
				           .anyMatch(dr -> confidenceIsEqualToDimensions(
						           5, dr.protobuf().getImageRequest().getFeedForwardLocation())));

		assertTrue(detectionRequests.stream()
				           .anyMatch(dr -> confidenceIsEqualToDimensions(
						           10, dr.protobuf().getImageRequest().getFeedForwardLocation())));
        assertAllHaveFeedForwardTrack(detectionRequests);
	}


	@Test
	public void noMessagesCreatedWhenNoTracks() {
		Media media = createTestMedia();

		DetectionContext feedForwardContext = createTestDetectionContext(
				1, Collections.singletonMap("FEED_FORWARD_TYPE", "FRAME"), Collections.emptySet());
		assertTrue(_imageMediaSegmenter.createDetectionRequests(media, feedForwardContext).isEmpty());

		DetectionContext context = createTestDetectionContext(1, Collections.emptyMap(), Collections.emptySet());
		assertTrue(_imageMediaSegmenter.createDetectionRequests(media, context).isEmpty());
	}


	private static Media createTestMedia() {
		URI uri = URI.create("file:///example.jpg");
		MediaImpl media = new MediaImpl(
				1, uri.toString(), UriScheme.get(uri), Paths.get(uri),
				Map.of(), Map.of(), List.of(), List.of(), List.of(), null);
		media.setLength(1);
		media.addMetadata("mediaKey1", "mediaValue1");
		return media;
	}


	private static Set<Track> createTestTracks() {
		return ImmutableSet.of(
				createTrack(createDetection(0, 5, "CONFIDENCE", 0.1f)),
				createTrack(createDetection(0, 10, "CONFIDENCE", 0.5f)));

	}

}
