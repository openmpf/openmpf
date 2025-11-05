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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mitre.mpf.wfm.segmenting.TestMediaSegmenter.assertAllHaveFeedForwardTrack;
import static org.mitre.mpf.wfm.segmenting.TestMediaSegmenter.assertContainsAlgoProperty;
import static org.mitre.mpf.wfm.segmenting.TestMediaSegmenter.assertContainsExpectedMediaMetadata;
import static org.mitre.mpf.wfm.segmenting.TestMediaSegmenter.assertNoneHaveFeedForwardTrack;
import static org.mitre.mpf.wfm.segmenting.TestMediaSegmenter.containsExpectedDetectionProperties;
import static org.mitre.mpf.wfm.segmenting.TestMediaSegmenter.createDetectionProperties;
import static org.mitre.mpf.wfm.segmenting.TestMediaSegmenter.createTestDetectionContext;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.Test;
import org.mitre.mpf.rest.api.MediaSelectorType;
import org.mitre.mpf.rest.api.MediaUri;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionContext;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.MediaImpl;
import org.mitre.mpf.wfm.data.entities.persistent.MediaSelector;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.service.MediaSelectorsSegmenter;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

public class TestDefaultMediaSegmenter extends MockitoTest.Strict {

    @Mock
    private TriggerProcessor _mockTriggerProcessor;

    @Mock
    private MediaSelectorsSegmenter _mockSelectorSegmenter;

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
				1, Collections.singletonMap("FEED_FORWARD_TYPE", "FRAME"), tracks, "CONFIDENCE");

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
				1, Collections.singletonMap("FEED_FORWARD_TYPE", "FRAME"), Collections.emptySet(), "CONFIDENCE");
		assertTrue(_defaultMediaSegmenter.createDetectionRequests(media, feedForwardContext).isEmpty());
	}


    @Test
    public void testJsonPathSegmenting() {
        var media = createMediaWithJsonSelector();
        var context = createTestDetectionContext(0, Map.of(), Set.of());

        var selectorSegmentResult = new ArrayList<DetectionRequest>();
        when(_mockSelectorSegmenter.segmentMedia(media, context))
                .thenReturn(selectorSegmentResult);

        var results = _defaultMediaSegmenter.createDetectionRequests(media, context);
        // createDetectionRequests just returns the list that the MediaSelectorsSegmenter
        // returns, so the actual content of the list does not matter for this test.
        assertThat(results).isSameAs(selectorSegmentResult);
    }


    @Test
    public void mediaSelectorsSegmenterOnlyApplyToFirstStage() {
        var media = createMediaWithJsonSelector();
        var context = createTestDetectionContext(1, Map.of(), Set.of());
        _defaultMediaSegmenter.createDetectionRequests(media, context);
        verifyNoInteractions(_mockSelectorSegmenter);
    }


    @Test
    public void mediaSelectorInfoCopiedDuringFeedForward() {
        var media = createMediaWithJsonSelector();

        var selector1Id = UUID.randomUUID();
        var content1 = "<content1>";
        var track1 = mock(Track.class);
        when(track1.getSelectorId())
            .thenReturn(Optional.of(selector1Id));
        when(track1.getSelectedInput())
            .thenReturn(Optional.of(content1));
        when(track1.getExemplar())
            .thenReturn(createDetection(1));

        var selector2Id = UUID.randomUUID();
        var content2 = "<content2>";
        var track2 = mock(Track.class);
        when(track2.getSelectorId())
            .thenReturn(Optional.of(selector2Id));
        when(track2.getSelectedInput())
            .thenReturn(Optional.of(content2));
        when(track2.getExemplar())
            .thenReturn(createDetection(1));

        var context = createTestDetectionContext(
                1,
                Map.of(MediaSegmenter.FEED_FORWARD_TYPE, "REGION"),
                Set.of(track1, track2));

        when(_mockTriggerProcessor.getTriggeredTracks(media, context))
                .thenAnswer(i -> Stream.of(track1, track2));

        var detectionRequests = _defaultMediaSegmenter.createDetectionRequests(media, context);

        assertThat(detectionRequests).satisfiesExactlyInAnyOrder(
            dr -> {
                assertThat(dr.headers())
                    .isEqualTo(Map.of(MpfHeaders.MEDIA_SELECTOR_ID, selector1Id.toString()));
                assertThat(dr.feedForwardTracks()).isEqualTo(List.of(track1));
                assertThat(dr.protobuf().getMediaMetadataMap())
                    .containsOnly(
                        Map.entry("mediaKey1", "mediaValue1"),
                        Map.entry(MpfConstants.SELECTED_CONTENT, content1));
            },
            dr -> {
                assertThat(dr.headers())
                    .isEqualTo(Map.of(MpfHeaders.MEDIA_SELECTOR_ID, selector2Id.toString()));
                assertThat(dr.feedForwardTracks()).isEqualTo(List.of(track2));
                assertThat(dr.protobuf().getMediaMetadataMap())
                    .containsOnly(
                        Map.entry("mediaKey1", "mediaValue1"),
                        Map.entry(MpfConstants.SELECTED_CONTENT, content2));
            }
        );
        verifyNoInteractions(_mockSelectorSegmenter);
    }


    private static Media createMediaWithJsonSelector() {
        var selector = new MediaSelector(
                "$.*",
                MediaSelectorType.JSON_PATH,
                Map.of("key1", "value2"),
                "OUT_PROP");
        return createTestMedia(List.of(selector));
    }


	private static void assertContainsExpectedTrack(float confidence, Collection<DetectionRequest> requests) {
		DetectionProtobuf.GenericTrack genericTrack = requests.stream()
				.map(dr -> dr.protobuf().getGenericRequest().getFeedForwardTrack())
				.filter(gt -> gt.getConfidence() == confidence)
				.findAny()
				.get();

		assertTrue(containsExpectedDetectionProperties((int)(confidence * 10), genericTrack.getDetectionPropertiesMap()));
	}


	private static Media createTestMedia(Collection<MediaSelector> mediaSelectors) {
		var mediaUri = MediaUri.create("file:///example.foo");
		MediaImpl media = new MediaImpl(
				1, mediaUri, UriScheme.get(mediaUri), Paths.get(mediaUri.get()), Map.of(),
				Map.of(), List.of(), List.of(), mediaSelectors, null, null);
		media.setLength(1);
		media.addMetadata("mediaKey1", "mediaValue1");
		return media;
	}

	private static Media createTestMedia() {
        return createTestMedia(List.of());
    }

	private static Set<Track> createTestTracks() {
		Detection detection1 = createDetection(0.00f);
		Track track1 = new Track(
                1, 1, 0, 0, 0, -1, 0, 0,
                List.of(), 0,
                ImmutableSortedSet.of(detection1), Collections.emptyMap(),
                "", "", null, null);

		Detection detection2 = createDetection(0.10f);
		Track track2 = new Track(
                1, 1, 0, 0, 0, -1, 0, 0,
                List.of(), 0.10f,
                ImmutableSortedSet.of(detection2), Collections.emptyMap(),
                "", "", null, null);

		return ImmutableSet.of(track1, track2);
	}


	private static Detection createDetection(float confidence) {
		return new Detection(0, 0, 0, 0, confidence, 0, 0,
		                     createDetectionProperties((int)(confidence * 10)));
	}
}
