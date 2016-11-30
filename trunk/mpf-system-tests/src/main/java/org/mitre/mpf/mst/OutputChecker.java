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

package org.mitre.mpf.mst;

import com.google.common.base.Joiner;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.interop.JsonMediaOutputObject;
import org.mitre.mpf.interop.JsonTrackOutputObject;
import org.mitre.mpf.interop.JsonDetectionOutputObject;

import java.util.*;

@Component(OutputChecker.REF)
public class OutputChecker {

	public static final String REF = "OutputChecker";
	private static final Logger log = LoggerFactory.getLogger(OutputChecker.class);

	// when comparing confidence, results can differ by this much (absolute value in comparison
	private static final double delta = .01;

	// when doing a fuzzy match and comparing confidence, results can differ by this much (scale is 1-10)
	private static final double deltaFuzzy = 3.0;

	// when doing a fuzzy match, overlap can differ by this much (scale is 1 -100)
	private static final int fudgeFactor = 30;       // for when running on Jenkins
//    private static final int fudgeFactor = 40;     // for when running on local VM & comparing with jenkins run

	/**
	 * For a given job, compare the actual output JSON object to the expected output JSON object
	 *
	 * @param expectedOutputJson
	 * @param actualOutputJson
	 * @param pipeline
	 */
	public void compareOutputs(JsonOutputObject expectedOutputJson, JsonOutputObject actualOutputJson, String pipeline) {
		// Expected and actual outputs will differ in several ways, like job id, start time, stop time and other ids.
		// However other elements should be the same IF they were both run on the same server
		Set<JsonMediaOutputObject> expMedias = expectedOutputJson.getMedia();
		Set<JsonMediaOutputObject> actMedias = actualOutputJson.getMedia();

		// the number of media in the actual media group should be the same as the number in the expected media group
		Assert.assertEquals(String.format("Expected MediaGroup size=%s doesn't match actual MediaGroup size=%s",
				expMedias.size(), actMedias.size()), expMedias.size(), actMedias.size());

		Iterator<JsonMediaOutputObject> expIter = expMedias.iterator();
		Iterator<JsonMediaOutputObject> actIter = actMedias.iterator();
		while(expIter.hasNext()){
			compareMedia(expIter.next(), actIter.next(), pipeline);
		}
	}

	/**
	 * For a given media item, compare the actual output to the expected output
	 *
	 * @param expMedia
	 * @param actMedia
	 * @param pipeline
	 */
	private void compareMedia(JsonMediaOutputObject expMedia,
	                          JsonMediaOutputObject actMedia, String pipeline) {
        log.debug("Comparing expected output={} to actual output={}", expMedia.getPath(), actMedia.getPath());
		// compare two Media items
		Assert.assertEquals(String.format("Expected media Type=%s doesn't match actual media Type=%s", expMedia.getMimeType(),
				actMedia.getMimeType()), expMedia.getMimeType(), actMedia.getMimeType());
		Assert.assertEquals(expMedia.getMimeType(), actMedia.getMimeType());
		log.debug("Comparing expected media properties=({}) to actual media properties({})", expMedia.getMediaMetadata().size(),
				actMedia.getMediaMetadata().size());
		Assert.assertEquals(String.format("Expected Media Properties=%s doesn't match actual Media Properties=%s",
						Joiner.on(";").withKeyValueSeparator("=").join(expMedia.getMediaMetadata()),
						Joiner.on(";").withKeyValueSeparator("=").join(actMedia.getMediaMetadata())),
				expMedia.getMediaMetadata(), actMedia.getMediaMetadata());
		Map<String, SortedSet<JsonTrackOutputObject>> expExtrResults = expMedia.getTracks();
		Map<String, SortedSet<JsonTrackOutputObject>> actExtrResults = actMedia.getTracks();
        //log.info("expExtrResults size is {}", expExtrResults.size());
        //log.info("actExtrResults size is {}", actExtrResults.size());
		Assert.assertEquals(String.format("Expected ExtractionResults size=%d doesn't match actual ExtractionResults size=%d",
				expExtrResults.size(), actExtrResults.size()), expExtrResults.size(), actExtrResults.size());

		Iterator<Map.Entry<String,SortedSet<JsonTrackOutputObject>>> expectedEntries = expExtrResults.entrySet().iterator();
		Iterator<Map.Entry<String,SortedSet<JsonTrackOutputObject>>> actualEntries = actExtrResults.entrySet().iterator();

		while (expectedEntries.hasNext()) {
			compareExtractionSets(expectedEntries.next(), actualEntries.next(), pipeline);
		}
	}

	private void compareExtractionSets(  Map.Entry<String, SortedSet<JsonTrackOutputObject>> expectedTracksEntry,
	                                     Map.Entry<String, SortedSet<JsonTrackOutputObject>> actualTracksEntry,
	                                     String pipeline){
		Iterator<JsonTrackOutputObject> expIter = expectedTracksEntry.getValue().iterator();
		Iterator<JsonTrackOutputObject> actIter = actualTracksEntry.getValue().iterator();

        JsonTrackOutputObject expTrackOutput;
        JsonTrackOutputObject actTrackOutput;
		while (expIter.hasNext()){
            expTrackOutput = expIter.next();
            actTrackOutput = actIter.next();
            log.debug("Comparing expected track at StartOffsetFrame={} to actual track at StartOffsetFrame={}", expTrackOutput.getStartOffsetFrame(),
                    actTrackOutput.getStartOffsetFrame());
			compareExtrResult(expTrackOutput, actTrackOutput, pipeline);
		}
	}

	/**
	 * For the results for a given track, compare the actual output to the expected output.  Logic varies depending on
	 * the pipeline.
	 *
	 * @param expExtrResult
	 * @param actExtrResult
	 * @param pipeline
	 */
	private void compareExtrResult(JsonTrackOutputObject expExtrResult,
	                               JsonTrackOutputObject actExtrResult,
	                               String pipeline) {

		Assert.assertEquals(String.format("Expected StartOffsetFrame=%s doesn't match actual StartOffsetFrame=%s",
						expExtrResult.getStartOffsetFrame(), actExtrResult.getStartOffsetFrame()), expExtrResult.getStartOffsetFrame(),
				actExtrResult.getStartOffsetFrame());
		Assert.assertEquals(String.format("Expected StopOffsetFrame=%s doesn't match actual StopOffsetFrame=%s",
						expExtrResult.getStopOffsetFrame(), actExtrResult.getStopOffsetFrame()), expExtrResult.getStopOffsetFrame(),
				actExtrResult.getStopOffsetFrame());
		SortedSet<JsonDetectionOutputObject> expObjLocations = expExtrResult.getDetections();
		SortedSet<JsonDetectionOutputObject> actObjLocations = actExtrResult.getDetections();
		Iterator<JsonDetectionOutputObject> expObjIter = expObjLocations.iterator();
		Iterator<JsonDetectionOutputObject> actObjIter = actObjLocations.iterator();

        //log.info("expObjLocations size is {}", expObjLocations.size());
        //log.info("actObjLocations size is {}", actObjLocations.size());
		Assert.assertEquals(String.format("Expected ObjectLocations size=%d doesn't match actual ObjectLocations size=%d",
				expObjLocations.size(), actObjLocations.size()), expObjLocations.size(), actObjLocations.size());

		// compare exemplar frames
		if ( ! (pipeline.equals("DEFAULT_EXTRACTION_FACE_OCV_PIPELINE") || pipeline.equals("DEFAULT_EXTRACTION_MOTION_MOG_PIPELINE")) ) {
			Assert.assertEquals(String.format("Expected BestFrame=%s doesn't match actual BestFrame=%s",
							expExtrResult.getExemplar().getOffsetFrame(), actExtrResult.getExemplar().getOffsetFrame()),
					expExtrResult.getExemplar().getOffsetFrame(), actExtrResult.getExemplar().getOffsetFrame());
		}

		while (expObjIter.hasNext()) {
			compareObjLocation(expObjIter.next(), actObjIter.next(), pipeline);
		}

		// There is NO check for BestFrame on the aforementioned pipelines because the best frames in the actual and
		// expected output may differ, although on very limited test runs

		// check that best frame has the highest confidence value in the track, for those pipelines that have Confidence calculated
//        log.info("Actual BestFrame Confidence={}, highest Confidence in the track={}", actObjLocs.get(actExtrResult.getBestFrame().getPos()).getConfidence(),
//                bestActConfidence);
		switch(pipeline) {
			case "DEFAULT_EXTRACTION_MOTION_MOG_PIPELINE":
			case "DEFAULT_EXTRACTION_PERSON_OCV_PIPELINE":
			case "DEFAULT_EXTRACTION_SPEECH_SPHINX_PIPELINE":
				break;
			default:
				Assert.assertEquals(String.format("Actual BestFrame Confidence=%s doesn't match the highest Confidence (=%s) in this track",
								expExtrResult.getExemplar().getConfidence(), actExtrResult.getExemplar().getConfidence()),
						expExtrResult.getExemplar().getConfidence(), actExtrResult.getExemplar().getConfidence(), delta);
		}
	}

	/**
	 * For a given ObjectLocation, compare the actual output to the expected output
	 *
	 * @param expObjLocation
	 * @param actObjLocation
	 * @param pipeline
	 */
	private void compareObjLocation(JsonDetectionOutputObject expObjLocation,
	                                JsonDetectionOutputObject actObjLocation,
	                                String pipeline) {
		switch (pipeline) {
			case "DEFAULT_EXTRACTION_FACE_OCV_PIPELINE":
			case "DEFAULT_EXTRACTION_MOTION_MOG_PIPELINE":
                log.debug("Comparing expected x={} y={} width={} height={} to actual x={} y={} width={} height={}",
                        expObjLocation.getX(), expObjLocation.getY(),expObjLocation.getWidth(), expObjLocation.getHeight(),
                        actObjLocation.getX(), actObjLocation.getY(), actObjLocation.getWidth(), actObjLocation.getHeight());

				// these pipelines are non-deterministic, so use fuzzy logic to compare ObjectLocations
				double overlap = calculateOverlap(expObjLocation.getX(), expObjLocation.getY(),
						expObjLocation.getWidth(), expObjLocation.getHeight(), actObjLocation.getX(),
						actObjLocation.getY(), actObjLocation.getWidth(), actObjLocation.getHeight());
//            Assert.assertEquals(overlap, 100, 10);
				Assert.assertEquals(overlap, 100, fudgeFactor);
				Assert.assertEquals(String.format("Expected Confidence=%f doesn't match actual Confidence=%f",
								expObjLocation.getConfidence(), actObjLocation.getConfidence()), expObjLocation.getConfidence(),
						actObjLocation.getConfidence(), deltaFuzzy);
			case "DEFAULT_EXTRACTION_SPEECH_SPHINX_PIPELINE":
				// Only check detection properties, which should have the speech hypothesis.
				log.debug("Comparing expected detection properties=({}) to actual detection properties({})", expObjLocation.getDetectionProperties().size(),
						actObjLocation.getDetectionProperties().size());
				Assert.assertEquals(String.format("Expected Detection Properties=%s doesn't match actual Detection Properties=%s",
								Joiner.on(";").withKeyValueSeparator("=").join(expObjLocation.getDetectionProperties()),
								Joiner.on(";").withKeyValueSeparator("=").join(actObjLocation.getDetectionProperties())),
						expObjLocation.getDetectionProperties(), actObjLocation.getDetectionProperties());
				break;
			default:
                log.debug("Comparing expected width={} height={} offset={} x={} y={} detectionProperties({}( confidence={} to actual width={} height={} offset={} x={} y={} detectionProperties({}) confidence={}",
                        expObjLocation.getWidth(), expObjLocation.getHeight(), expObjLocation.getOffsetFrame(), expObjLocation.getX(),
                        expObjLocation.getY(), expObjLocation.getDetectionProperties().size(), expObjLocation.getConfidence(),
                        actObjLocation.getWidth(), actObjLocation.getHeight(), actObjLocation.getX(), actObjLocation.getY(),
                        actObjLocation.getDetectionProperties().size(), actObjLocation.getConfidence());
				// for all other cases, compare everything
				// ? only speech and text have MetaData; should we not check it in other cases ?
				Assert.assertEquals(String.format("Expected Width=%d doesn't match actual Width=%d", expObjLocation.getWidth(),
						actObjLocation.getWidth()), expObjLocation.getWidth(), actObjLocation.getWidth());
				Assert.assertEquals(String.format("Expected Height=%d doesn't match actual Height=%d", expObjLocation.getHeight(),
						actObjLocation.getHeight()), expObjLocation.getHeight(), actObjLocation.getHeight());
				Assert.assertEquals(String.format("Expected Offset=%d doesn't match actual Offset=%d",
								expObjLocation.getOffsetFrame(), actObjLocation.getOffsetFrame()), expObjLocation.getOffsetFrame(),
						actObjLocation.getOffsetFrame());
				Assert.assertEquals(String.format("Expected X=%d doesn't match actual X=%d",
								expObjLocation.getX(), actObjLocation.getX()), expObjLocation.getX(),
						actObjLocation.getX());
				Assert.assertEquals(String.format("Expected Y=%d doesn't match actual Y=%d",
								expObjLocation.getY(), actObjLocation.getY()), expObjLocation.getY(),
						actObjLocation.getY());
				Assert.assertEquals(String.format("Expected Detection Properties='%s' doesn't match actual Detection Properties='%s'",
								Joiner.on(";").withKeyValueSeparator("=").join(expObjLocation.getDetectionProperties()),
								Joiner.on(";").withKeyValueSeparator("=").join(actObjLocation.getDetectionProperties())),
						expObjLocation.getDetectionProperties(), actObjLocation.getDetectionProperties());
				Assert.assertEquals(String.format("Expected Confidence=%f doesn't match actual Confidence=%f",
								expObjLocation.getConfidence(), actObjLocation.getConfidence()), expObjLocation.getConfidence(),
						actObjLocation.getConfidence(), delta);
				break;
		}
	}

	/**
	 * Calculate the overlap between bounding boxes A & B using x/y coordinates, width and height
	 *
	 * @param xA1
	 * @param yA1
	 * @param wA
	 * @param hA
	 * @param xB1
	 * @param yB1
	 * @param wB
	 * @param hB
	 * @return overlap
	 */
	private double calculateOverlap(int xA1, int yA1, int wA, int hA, int xB1, int yB1, int wB, int hB) {
        //log.info("Calculating overlap with xA1={} yA1={} wA={} hA={} xB1={} yB1={} wB={} hB={}", xA1, yA1, wA, hA, xB1, yB1, wB, hB);
		int xA2 = xA1 + wA;
		int yA2 = yA1 + hA;
		int xB2 = xB1 + wB;
		int yB2 = yB1 + hB;

		//debugging variables
		//int xSI = Math.max(0, Math.min(xA2, xB2) - Math.max(xA1, xB1)); 
		//int ySI = Math.max(0, Math.min(yA2, yB2) - Math.max(yA1, yB1));

		// intersection
		int SI = Math.max(0, Math.min(xA2, xB2) - Math.max(xA1, xB1)) * Math.max(0, Math.min(yA2, yB2) - Math.max(yA1, yB1));
		//int SI = xSI * ySI;
		
		//log.info("Intersection = SI = xSI*ySI = {} = {}*{}", SI, xSI, ySI);

		// area of A
		int SA = wA * hA;
                
		// area of B
		int SB = wB * hB;

		// union
		int S = SA + SB - SI;
	//log.info("S = SA + SB - SI = {}+{}-{}={}", SA, SB, SI, S);
		
		// ratio
		double overlap = ((double) SI / S) * 100;
        //log.info("Overlap = {}/{}={}", SI, S, overlap);
		return overlap;
	}
}
