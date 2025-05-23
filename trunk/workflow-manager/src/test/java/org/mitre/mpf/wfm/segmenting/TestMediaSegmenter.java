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

import org.junit.Test;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf.ImageLocation;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionContext;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.util.TopQualitySelectionUtil;
import org.mitre.mpf.wfm.util.MediaRange;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestMediaSegmenter {

	@Test
	public void testNoSplits() {
		List<MediaRange> results = MediaSegmenter.createSegments(
                Collections.singletonList(new MediaRange(0, 24)), 25, 1, 100);
		assertEquals(0, results.get(0).getStartInclusive());
		assertEquals(24,  results.get(0).getEndInclusive());
	}

	@Test
	public void testSplits() {
		List<MediaRange> results = MediaSegmenter.createSegments(
                Collections.singletonList(new MediaRange(0, 24)), 10, 1, 100);

		assertEquals(0, results.get(0).getStartInclusive());
		assertEquals(9, results.get(0).getEndInclusive());

		assertEquals(10, results.get(1).getStartInclusive());
		assertEquals(19, results.get(1).getEndInclusive());

		assertEquals(20, results.get(2).getStartInclusive());
		assertEquals(24, results.get(2).getEndInclusive());


		// "Segmenting Media" example from User Guide
		List<MediaRange> inputs = new ArrayList<>();
		inputs.add(new MediaRange(25, 150));
		inputs.add(new MediaRange(175, 275));
		inputs.add(new MediaRange(25, 125));
		inputs.add(new MediaRange(0, 175));

		results = MediaSegmenter.createSegments(
				inputs, 100, 5, 0);

		assertEquals(3, results.size());

		assertEquals(0, results.get(0).getStartInclusive());
		assertEquals(99, results.get(0).getEndInclusive());

		assertEquals(100, results.get(1).getStartInclusive());
		assertEquals(199, results.get(1).getEndInclusive());

		assertEquals(200, results.get(2).getStartInclusive());
		assertEquals(275, results.get(2).getEndInclusive());
	}

	@Test
	public void testShortFirstSegment() {
		List<MediaRange> results = MediaSegmenter.createSegments(
                Collections.singletonList(new MediaRange(0, 3)), 10, 5, 0);

		assertEquals(1, results.size());

		assertEquals(0, results.get(0).getStartInclusive());
		assertEquals(3, results.get(0).getEndInclusive());
	}

	@Test
	public void testShortAdjacentSegment() {
		List<MediaRange> inputs = new ArrayList<>();
		inputs.add(new MediaRange(0, 9));
		inputs.add(new MediaRange(10, 13));

		List<MediaRange> results = MediaSegmenter.createSegments(
				inputs, 10, 5, 0);

		assertEquals(2, results.size());

		assertEquals(0, results.get(0).getStartInclusive());
		assertEquals(9, results.get(0).getEndInclusive());

		assertEquals(10, results.get(1).getStartInclusive());
		assertEquals(13, results.get(1).getEndInclusive());


		// "Adjacent Segment Present" example from User Guide
		inputs.clear();
		inputs.add(new MediaRange(0, 99));
		inputs.add(new MediaRange(100, 199));
		inputs.add(new MediaRange(200, 249));

		results = MediaSegmenter.createSegments(
				inputs, 100, 75, 50);

		assertEquals(2, results.size());

		assertEquals(0, results.get(0).getStartInclusive());
		assertEquals(99, results.get(0).getEndInclusive());

		assertEquals(100, results.get(1).getStartInclusive());
		assertEquals(249, results.get(1).getEndInclusive());
	}

	@Test
	public void testShortNonAdjacentSegment() {
		List<MediaRange> inputs = new ArrayList<>();
		inputs.add(new MediaRange(0, 11));
		inputs.add(new MediaRange(13, 13));

		List<MediaRange> results = MediaSegmenter.createSegments(
				inputs, 10, 5, 0);

		assertEquals(2, results.size());

		assertEquals(0, results.get(0).getStartInclusive());
		assertEquals(11, results.get(0).getEndInclusive());

		assertEquals(13, results.get(1).getStartInclusive());
		assertEquals(13, results.get(1).getEndInclusive());


		// "No Adjacent Segment" example from User Guide
		inputs.clear();
		inputs.add(new MediaRange(0, 99));
		inputs.add(new MediaRange(100, 199));
		inputs.add(new MediaRange(200, 249));
		inputs.add(new MediaRange(325, 349));

		results = MediaSegmenter.createSegments(
				inputs, 100, 75, 50);

		assertEquals(3, results.size());

		assertEquals(0, results.get(0).getStartInclusive());
		assertEquals(99, results.get(0).getEndInclusive());

		assertEquals(100, results.get(1).getStartInclusive());
		assertEquals(249, results.get(1).getEndInclusive());

		assertEquals(325, results.get(2).getStartInclusive());
		assertEquals(349, results.get(2).getEndInclusive());
	}

	@Test
	public void testSegmentLengthAtLeastMinLength() {
		List<MediaRange> results = MediaSegmenter.createSegments(
                Collections.singletonList(new MediaRange(0, 199)), 100, 10, 0);

		assertEquals(2, results.size());

		assertEquals(0, results.get(0).getStartInclusive());
		assertEquals(99, results.get(0).getEndInclusive());

		assertEquals(100, results.get(1).getStartInclusive());
		assertEquals(199, results.get(1).getEndInclusive()); // perfect split


		results = MediaSegmenter.createSegments(
                Collections.singletonList(new MediaRange(0, 200)), 100, 10, 0);

		assertEquals(2, results.size());

		assertEquals(0, results.get(0).getStartInclusive());
		assertEquals(99, results.get(0).getEndInclusive());

		assertEquals(100, results.get(1).getStartInclusive());
		assertEquals(200, results.get(1).getEndInclusive()); // short segment (lower bound) merged into preceding segment


		results = MediaSegmenter.createSegments(
                Collections.singletonList(new MediaRange(0, 208)), 100, 10, 0);

		assertEquals(2, results.size());

		assertEquals(0, results.get(0).getStartInclusive());
		assertEquals(99, results.get(0).getEndInclusive());

		assertEquals(100, results.get(1).getStartInclusive());
		assertEquals(208, results.get(1).getEndInclusive()); // short segment (upper bound) merged into preceding segment


		results = MediaSegmenter.createSegments(
                Collections.singletonList(new MediaRange(0, 209)), 100, 10, 0);

		assertEquals(3, results.size());

		assertEquals(0, results.get(0).getStartInclusive());
		assertEquals(99, results.get(0).getEndInclusive());

		assertEquals(100, results.get(1).getStartInclusive());
		assertEquals(199, results.get(1).getEndInclusive());

		assertEquals(200, results.get(2).getStartInclusive());
		assertEquals(209, results.get(2).getEndInclusive()); // short segment (lower bound) stands by itself


		results = MediaSegmenter.createSegments(
                Collections.singletonList(new MediaRange(0, 298)), 100, 10, 0);

		assertEquals(3, results.size());

		assertEquals(0, results.get(0).getStartInclusive());
		assertEquals(99, results.get(0).getEndInclusive());

		assertEquals(100, results.get(1).getStartInclusive());
		assertEquals(199, results.get(1).getEndInclusive());

		assertEquals(200, results.get(2).getStartInclusive());
		assertEquals(298, results.get(2).getEndInclusive()); // short segment (upper bound) stands by itself
	}

	@Test
	public void testMinGapBetweenSegments() {
		List<MediaRange> inputs = new ArrayList<>();
		inputs.add(new MediaRange(0, 149));
		inputs.add(new MediaRange(175, 399));
		inputs.add(new MediaRange(500, 899));

		List<MediaRange> results = MediaSegmenter.createSegments(
				inputs, 75, 25, 100);

		assertEquals(12, results.size());

		int offset = 0;
		for (MediaRange result : results.subList(0, 5)) {
			assertEquals(offset, result.getStartInclusive());
			assertEquals(offset+74, result.getEndInclusive());
			offset += 75;
		}

		assertEquals(offset, results.get(5).getStartInclusive());
		assertEquals(offset+24, results.get(5).getEndInclusive());

		offset = 500;
		for (MediaRange result : results.subList(6, 11)) {
			assertEquals(offset, result.getStartInclusive());
			assertEquals(offset+74, result.getEndInclusive());
			offset += 75;
		}

		assertEquals(offset, results.get(11).getStartInclusive());
		assertEquals(offset+24, results.get(11).getEndInclusive());


		// "MIN_GAP_BETWEEN_SEGMENTS" Property" example from User Guide (after track merging)
		inputs.clear();
		inputs.add(new MediaRange(0, 149));
		inputs.add(new MediaRange(175, 399));
		inputs.add(new MediaRange(500, 899));

		results = MediaSegmenter.createSegments(
				inputs, 75, 26, 100);

		assertEquals(10, results.size());

		offset = 0;
		for (MediaRange result : results.subList(0, 4)) {
			assertEquals(offset, result.getStartInclusive());
			assertEquals(offset+74, result.getEndInclusive());
			offset += 75;
		}

		assertEquals(offset, results.get(4).getStartInclusive());
		assertEquals(offset+99, results.get(4).getEndInclusive());

		offset = 500;
		for (MediaRange result : results.subList(5, 9)) {
			assertEquals(offset, result.getStartInclusive());
			assertEquals(offset+74, result.getEndInclusive());
			offset += 75;
		}

		assertEquals(offset, results.get(9).getStartInclusive());
		assertEquals(offset+99, results.get(9).getEndInclusive());
	}

	@Test
	public void testOverlap() {
		assertTrue(MediaSegmenter.overlaps(new MediaRange(0, 15), new MediaRange(10, 20), 0));
		assertTrue(MediaSegmenter.overlaps(new MediaRange(0, 15), new MediaRange(10, 20), 1));
		assertTrue(MediaSegmenter.overlaps(new MediaRange(0, 15), new MediaRange(10, 20), 10));

		assertTrue(MediaSegmenter.overlaps(new MediaRange(0, 15), new MediaRange(15, 20), 0));
		assertTrue(MediaSegmenter.overlaps(new MediaRange(0, 15), new MediaRange(15, 20), 1));
		assertTrue(MediaSegmenter.overlaps(new MediaRange(0, 15), new MediaRange(15, 20), 10));

		assertFalse(MediaSegmenter.overlaps(new MediaRange(0, 15), new MediaRange(16, 20), 0));
		assertTrue(MediaSegmenter.overlaps(new MediaRange(0, 15), new MediaRange(16, 20), 1));
		assertTrue(MediaSegmenter.overlaps(new MediaRange(0, 15), new MediaRange(16, 20), 10));


		assertFalse(MediaSegmenter.overlaps(new MediaRange(0, 15), new MediaRange(20, 40), 0));
		assertFalse(MediaSegmenter.overlaps(new MediaRange(0, 15), new MediaRange(20, 40), 1));
		assertFalse(MediaSegmenter.overlaps(new MediaRange(0, 15), new MediaRange(20, 40), 4));
		assertTrue(MediaSegmenter.overlaps(new MediaRange(0, 15), new MediaRange(20, 40), 5));
		assertTrue(MediaSegmenter.overlaps(new MediaRange(0, 15), new MediaRange(20, 40), 6));
		assertTrue(MediaSegmenter.overlaps(new MediaRange(0, 15), new MediaRange(20, 40), 10));
	}

	@Test
	public void testMerge() {
		assertEquals(new MediaRange(0, 20), MediaSegmenter.merge(new MediaRange(0, 15), new MediaRange(10, 20)));
	}



	protected static void assertContainsAlgoProperty(
            String key, String value, Collection<DetectionRequest> requests) {
		for (var request : requests) {
            assertEquals(
                    "Expected request to contain algorithm property: %s: %s".formatted(key, value),
                    value,
                    request.protobuf().getAlgorithmPropertiesMap().get(key));
		}
	}


	protected static void assertContainsExpectedMediaMetadata(
            Collection<DetectionRequest> requests) {
		assertTrue("Expected each request to contain 1 media metadata field",
                requests.stream()
                        .allMatch(dr -> dr.protobuf().getMediaMetadataCount() == 1));
		assertContainsMediaMetadata("mediaKey1", "mediaValue1", requests);
	}


	protected static void assertContainsMediaMetadata(
            String key, String value, Collection<DetectionRequest> requests) {
		for (var request : requests) {
            assertEquals(
                    "Expected request to contain media metadata: %s: %s".formatted(key, value),
                    value,
                    request.protobuf().getMediaMetadataMap().get(key));
		}
	}


	protected static boolean confidenceIsEqualToDimensions(float confidence, ImageLocation imageLocation) {
		int dimensions = (int) confidence;
		return TestUtil.almostEqual(imageLocation.getConfidence(), confidence)
				&& dimensions == imageLocation.getXLeftUpper()
				&& dimensions == imageLocation.getYLeftUpper()
				&& dimensions == imageLocation.getWidth()
				&& dimensions == imageLocation.getHeight()
				&& containsExpectedDetectionProperties(dimensions, imageLocation.getDetectionPropertiesMap());
	}


    protected static DetectionContext createTestDetectionContext(
            int stage, Map<String, String> additionalAlgoProps, Set<Track> tracks) {
        return createTestDetectionContext(stage, additionalAlgoProps, tracks, null);
    }

	protected static DetectionContext createTestDetectionContext(
            int stage, Map<String, String> additionalAlgoProps, Set<Track> tracks,
            String qualitySelectionProperty) {
		return new DetectionContext(
				1, stage, "STAGE_NAME", 0, "ACTION_NAME", stage == 0,
				createTestAlgorithmProperties(additionalAlgoProps), tracks,
				createTestSegmentingPlan(), qualitySelectionProperty);
	}


	private static Map<String, String> createTestAlgorithmProperties(
			Map<String, String> additionalAlgoProps) {
		Map<String, String> algoProps = new HashMap<>();
		algoProps.put("algoKey1", "algoValue1");
		algoProps.put("algoKey2", "algoValue2");
		algoProps.putAll(additionalAlgoProps);
        return algoProps;
	}


	private static SegmentingPlan createTestSegmentingPlan() {
		return new SegmentingPlan(20, 1, 1,1);
	}



	protected static SortedMap<String, String> createDetectionProperties(int detectionNumber) {
		return createDetectionProperties(detectionNumber, null, -1);
	}

    protected static SortedMap<String, String> createDetectionProperties(
            int detectionNumber, String qualitySelectionProperty, float qualitySelectionValue) {
        var props = new TreeMap<String, String>();
        props.put("detectionKey" + detectionNumber, "detectionValue" + detectionNumber);
        if (qualitySelectionProperty != null) {
            props.put(qualitySelectionProperty, String.valueOf(qualitySelectionValue));
        }
        return props;
    }


	protected static boolean containsExpectedDetectionProperties(
			int detectionNumber, Map<String, String> actualProps) {
        var expectedProps = createDetectionProperties(detectionNumber);
        return expectedProps.entrySet().stream()
                .allMatch(ep -> Objects.equals(actualProps.get(ep.getKey()), ep.getValue()));
	}


    protected static Detection createDetection(int frame, float confidence) {
        return createDetection(frame, confidence, null, -1);
    }

	protected static Detection createDetection(
            int frame, float confidence,
            String qualitySelectionProperty, float qualitySelectionValue) {
		int dimensions = (int) confidence;
        var properties = createDetectionProperties(
                dimensions, qualitySelectionProperty, qualitySelectionValue);
		return new Detection(
                dimensions, dimensions, dimensions, dimensions, confidence, frame, 1,
				properties);
	}


	protected static Track createTrack(Detection... detections) {
		List<Detection> detectionList = Arrays.asList(detections);
		int start = detectionList.stream()
				.mapToInt(Detection::getMediaOffsetFrame)
				.min()
				.getAsInt();
		int stop = detectionList.stream()
				.mapToInt(Detection::getMediaOffsetFrame)
				.max()
				.getAsInt();

        Detection exemplar = TopQualitySelectionUtil.getTopQualityItem(
                detectionList, "CONFIDENCE");

		Track track = new Track(1, 1, 1, 0, start, stop, 0, 0, 1,
				exemplar.getConfidence(), detectionList, Collections.emptyMap(), "", "CONFIDENCE");
		return track;
	}


    protected static void assertAllHaveFeedForwardTrack(Collection<DetectionRequest> requests) {
        assertTrue(requests.stream().allMatch(r -> r.feedForwardTrack().isPresent()));
    }

    protected static void assertNoneHaveFeedForwardTrack(Collection<DetectionRequest> requests) {
        assertTrue(requests.stream().allMatch(r -> r.feedForwardTrack().isEmpty()));
    }
}
