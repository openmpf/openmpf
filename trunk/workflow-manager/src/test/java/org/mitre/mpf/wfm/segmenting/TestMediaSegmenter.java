/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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

import com.google.common.collect.ImmutableSortedMap;
import org.apache.camel.Message;
import org.junit.Test;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.buffers.AlgorithmPropertyProtocolBuffer;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionRequest;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf.ImageLocation;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf.PropertyMap;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionContext;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.util.TimePair;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestMediaSegmenter {

	@Test
	public void testNoSplits() throws Exception {
		List<TimePair> results = MediaSegmenter.createSegments(
				Collections.singletonList(new TimePair(0, 24)), 25, 1, 100);
		assertEquals(0, results.get(0).getStartInclusive());
		assertEquals(24,  results.get(0).getEndInclusive());
	}


	@Test
	public void testSplits() throws Exception {
		List<TimePair> results = MediaSegmenter.createSegments(
				Collections.singletonList(new TimePair(0, 24)), 10, 1, 100);

		assertEquals(0, results.get(0).getStartInclusive());
		assertEquals(9, results.get(0).getEndInclusive());

		assertEquals(10, results.get(1).getStartInclusive());
		assertEquals(19, results.get(1).getEndInclusive());

		assertEquals(20, results.get(2).getStartInclusive());
		assertEquals(24, results.get(2).getEndInclusive());
	}

	@Test
	public void testOverlap() throws Exception {
		int minGapBetweenSegments = 1;
		assertTrue(MediaSegmenter.overlaps(new TimePair(0, 15), new TimePair(10, 20), minGapBetweenSegments));
	}

	@Test
	public void testMerge() throws Exception {
		assertEquals(new TimePair(0, 20), MediaSegmenter.merge(new TimePair(0, 15), new TimePair(10, 20)));
	}



	protected static void assertContainsAlgoProperty(String key, String value, Collection<DetectionRequest> requests) {
		for (DetectionRequest request : requests) {
			assertTrue(
					String.format("Expected request to contain algorithm property: %s: %s", key, value),
					request.getAlgorithmPropertyList().stream()
							.anyMatch(ap -> ap.getPropertyName().equals(key) && ap.getPropertyValue().equals(value)));
		}
	}



	protected static void assertContainsExpectedMediaMetadata(Collection<DetectionRequest> requests) {
		assertTrue("Expected each request to contain 1 media metadata field", requests.stream()
				.allMatch(dr -> dr.getMediaMetadataList().size() == 1));
		assertContainsMediaMetadata("mediaKey1", "mediaValue1", requests);

	}

	protected static void assertContainsMediaMetadata(String key, String value, Collection<DetectionRequest> requests) {
		for (DetectionRequest request : requests) {
			assertTrue(
					String.format("Expected request to contain media metadata: %s: %s", key, value),
					request.getMediaMetadataList().stream()
							.anyMatch(mp -> mp.getKey().equals(key) && mp.getValue().equals(value)));
		}
	}


	protected static boolean confidenceIsEqualToDimensions(float confidence, ImageLocation imageLocation) {
		int dimensions = (int) confidence;
		return TestUtil.almostEqual(imageLocation.getConfidence(), confidence)
				&& dimensions == imageLocation.getXLeftUpper()
				&& dimensions == imageLocation.getYLeftUpper()
				&& dimensions == imageLocation.getWidth()
				&& dimensions == imageLocation.getHeight()
				&& containsExpectedDetectionProperties(dimensions, imageLocation.getDetectionPropertiesList());
	}

	protected static DetectionContext createTestDetectionContext(int stage, Map<String, String> additionalAlgoProps,
	                                                           Set<Track> tracks) {
		return new DetectionContext(
				1, stage, "STAGE_NAME", 0, "ACTION_NAME", stage == 0,
				createTestAlgorithmProperties(additionalAlgoProps), tracks,
				createTestSegmentingPlan());
	}


	private static List<AlgorithmPropertyProtocolBuffer.AlgorithmProperty> createTestAlgorithmProperties(
			Map<String, String> additionalAlgoProps) {
		Map<String, String> algoProps = new HashMap<>();
		algoProps.put("algoKey1", "algoValue1");
		algoProps.put("algoKey2", "algoValue2");
		algoProps.putAll(additionalAlgoProps);


		return algoProps.entrySet().stream()
				.map(e -> AlgorithmPropertyProtocolBuffer.AlgorithmProperty.newBuilder()
						.setPropertyName(e.getKey())
						.setPropertyValue(e.getValue())
						.build())
				.collect(toList());
	}


	private static SegmentingPlan createTestSegmentingPlan() {
		return new SegmentingPlan(20, 1, 1,1);
	}



	protected static SortedMap<String, String> createDetectionProperties(int detectionNumber) {
		return ImmutableSortedMap.of("detectionKey" + detectionNumber, "detectionValue" + detectionNumber);
	}


	protected static boolean containsExpectedDetectionProperties(
			int detectionNumber, Collection<PropertyMap> properties) {

		SortedMap<String, String> expectedProperties = createDetectionProperties(detectionNumber);
		if (expectedProperties.size() != properties.size()) {
			return false;
		}

		for (PropertyMap property : properties) {
			String expectedValue = expectedProperties.get(property.getKey());
			if (!expectedValue.equals(property.getValue())) {
				return false;
			}
		}
		return true;
	}


	protected static Detection createDetection(int frame, float confidence) {
		int dimensions = (int) confidence;
		return new Detection(dimensions, dimensions, dimensions, dimensions, confidence, frame, 1,
		                     createDetectionProperties(dimensions));
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

		Detection exemplar = detectionList.stream()
				.max(Comparator.comparing(Detection::getConfidence))
				.get();

		Track track = new Track(1, 1, 1, 0, start, stop, "type");
		track.getDetections().addAll(detectionList);
		track.setExemplar(exemplar);
		return track;
	}



	protected static List<DetectionRequest> unwrapMessages(Collection<Message> messages) {
		return messages
				.stream()
				.map(m -> m.getBody(DetectionRequest.class))
				.collect(toList());
	}
}
