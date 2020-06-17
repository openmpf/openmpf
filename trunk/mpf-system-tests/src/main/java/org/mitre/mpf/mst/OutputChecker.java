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

package org.mitre.mpf.mst;

import org.mitre.mpf.interop.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

public class OutputChecker {

    private static final Logger log = LoggerFactory.getLogger(OutputChecker.class);

    // when comparing confidence, results can differ by this much (absolute value in comparison
    private static final double delta = .01;

    // when doing a fuzzy match and comparing confidence, results can differ by this much (scale is 1-10)
    private static final double deltaFuzzy = 3.0;

    private final MpfErrorCollector _errorCollector;

    public OutputChecker(MpfErrorCollector errorCollector) {
        _errorCollector = errorCollector;
    }

    /**
     * For a given job, compare the actual output JSON object to the expected output JSON object
     *
     * @param expectedOutputJson
     * @param actualOutputJson
     * @param pipeline
     */
    public void compareJsonOutputObjects(JsonOutputObject expectedOutputJson, JsonOutputObject actualOutputJson, String pipeline) {
        // Expected and actual outputs will differ in several ways, like job id, start time, stop time and other ids.
        // However other elements should be the same IF they were both run on the same server
        Set<JsonMediaOutputObject> expMedias = expectedOutputJson.getMedia();
        Set<JsonMediaOutputObject> actMedias = actualOutputJson.getMedia();

        // the number of media in the actual media group should be the same as the number in the expected media group
        _errorCollector.checkThat("MediaGroup size", actMedias.size(), is(expMedias.size()));

        Iterator<JsonMediaOutputObject> expIter = expMedias.iterator();
        Iterator<JsonMediaOutputObject> actIter = actMedias.iterator();
        while(expIter.hasNext()){
            compareJsonMediaOutputObjects(expIter.next(), actIter.next(), pipeline);
        }
    }

    /**
     * For a given media item, compare the actual output to the expected output
     *
     * @param expMedia
     * @param actMedia
     * @param pipeline
     */
    private void compareJsonMediaOutputObjects(JsonMediaOutputObject expMedia,
                                               JsonMediaOutputObject actMedia, String pipeline) {
        log.debug("Comparing expected output={} to actual output={}", expMedia.getPath(), actMedia.getPath());
        // compare two Media items

        _errorCollector.checkThat("Media Type", actMedia.getMimeType(), is(expMedia.getMimeType()));
        log.debug("Comparing expected media properties=({}) to actual media properties({})", expMedia.getMediaMetadata().size(),
                actMedia.getMediaMetadata().size());
        _errorCollector.checkThat("MediaMetadata", actMedia.getMediaMetadata(), is(expMedia.getMediaMetadata()));

        Map<String, SortedSet<JsonActionOutputObject>> expExtrResults = expMedia.getTypes();
        Map<String, SortedSet<JsonActionOutputObject>> actExtrResults = actMedia.getTypes();
        // Check now to avoid NoSuchElementException during iteration
        _errorCollector.checkNowThat("ActionOutputs size", actExtrResults.size(), is(expExtrResults.size()));

        Iterator<Map.Entry<String,SortedSet<JsonActionOutputObject>>> expectedEntries = expExtrResults.entrySet().iterator();
        Iterator<Map.Entry<String,SortedSet<JsonActionOutputObject>>> actualEntries = actExtrResults.entrySet().iterator();

        while (expectedEntries.hasNext()) {
            compareJsonActionOutputObjectSets(expectedEntries.next(), actualEntries.next(), pipeline);
        }
    }

    private void compareJsonActionOutputObjectSets(Map.Entry<String, SortedSet<JsonActionOutputObject>> expectedTypeEntry,
                                                   Map.Entry<String, SortedSet<JsonActionOutputObject>> actualTypeEntry,
                                                   String pipeline){
        Iterator<JsonActionOutputObject> expIter = expectedTypeEntry.getValue().iterator();
        Iterator<JsonActionOutputObject> actIter = actualTypeEntry.getValue().iterator();

        JsonActionOutputObject expTrackOutput;
        JsonActionOutputObject actTrackOutput;
        _errorCollector.checkThat("Expected Type", actualTypeEntry.getKey(), is(expectedTypeEntry.getKey()));
        while (expIter.hasNext()){
            expTrackOutput = expIter.next();
            actTrackOutput = actIter.next();
            log.debug("Comparing expected actions at Source={} to actual actions at Source={}", expTrackOutput.getSource(),
                    actTrackOutput.getSource());
            compareJsonTrackOutputObjects(expTrackOutput.getTracks(), actTrackOutput.getTracks(), pipeline);
        }
    }

    private void compareJsonTrackOutputObjects(SortedSet<JsonTrackOutputObject> expectedTracksSet,
                                               SortedSet<JsonTrackOutputObject> actualTracksSet,
                                               String pipeline){

        if (pipeline.equals("SUBSENSE MOTION DETECTION (WITH TRACKING) PIPELINE"))  {
            _errorCollector.checkNowThat("Track Count:", actualTracksSet.size(), greaterThanOrEqualTo(expectedTracksSet.size()));
        } else {
            _errorCollector.checkNowThat("Track Count:", actualTracksSet.size(), is(expectedTracksSet.size()));
        }

        Iterator<JsonTrackOutputObject> expIter = expectedTracksSet.iterator();
        Iterator<JsonTrackOutputObject> actIter = actualTracksSet.iterator();

        JsonTrackOutputObject expTrackOutput;
        JsonTrackOutputObject actTrackOutput;
        while (expIter.hasNext()){
            expTrackOutput = expIter.next();
            actTrackOutput = actIter.next();
            log.debug("Comparing expected track at StartOffsetFrame={} to actual track at StartOffsetFrame={}", expTrackOutput.getStartOffsetFrame(),
                    actTrackOutput.getStartOffsetFrame());
            compareJsonTrackOutputObjects(expTrackOutput, actTrackOutput, pipeline);
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
    private void compareJsonTrackOutputObjects(JsonTrackOutputObject expExtrResult,
                                               JsonTrackOutputObject actExtrResult,
                                               String pipeline) {

        if (pipeline.equals("SUBSENSE MOTION DETECTION (WITH TRACKING) PIPELINE"))  {
            // Only compare exemplar frames for subsense tests
            compareJsonDetectionOutputObjects(expExtrResult.getExemplar(), actExtrResult.getExemplar(), pipeline);
            return;
        }

        SortedSet<JsonDetectionOutputObject> expObjLocations = expExtrResult.getDetections();
        SortedSet<JsonDetectionOutputObject> actObjLocations = actExtrResult.getDetections();
        Iterator<JsonDetectionOutputObject> expObjIter = expObjLocations.iterator();
        Iterator<JsonDetectionOutputObject> actObjIter = actObjLocations.iterator();

        _errorCollector.checkThat("StartOffsetFrame", actExtrResult.getStartOffsetFrame(),
                is(expExtrResult.getStartOffsetFrame()));
        _errorCollector.checkThat("StopOffsetFrame", actExtrResult.getStopOffsetFrame(),
                is(expExtrResult.getStopOffsetFrame()));

        _errorCollector.checkThat("Track Confidence", (double) actExtrResult.getConfidence(),
                closeTo(expExtrResult.getConfidence(), 0.01));

        _errorCollector.checkThat("TrackProperties", actExtrResult.getTrackProperties(),
                is(expExtrResult.getTrackProperties()));

        // Check now to avoid NoSuchElementException during iteration
        _errorCollector.checkNowThat("ObjectLocations size", actObjLocations.size(), is(expObjLocations.size()));

        // compare exemplar frames
        switch(pipeline) {
            case "OCV FACE DETECTION PIPELINE":
            case "OCV FACE DETECTION WITH AUTO ORIENTATION PIPELINE":
            case "TEST DEFAULT MOG MOTION DETECTION PIPELINE":
                break;
            default:
                _errorCollector.checkThat("BestFrame", actExtrResult.getExemplar().getOffsetFrame(),
                        is(expExtrResult.getExemplar().getOffsetFrame()));
        }

        while (expObjIter.hasNext()) {
            compareJsonDetectionOutputObjects(expObjIter.next(), actObjIter.next(), pipeline);
        }

        // There is NO check for BestFrame on the aforementioned pipelines because the best frames in the actual and
        // expected output may differ, although on very limited test runs

        // check that best frame has the highest confidence value in the track, for those pipelines that have confidence calculated
        switch(pipeline) {
            case "TEST DEFAULT MOG MOTION DETECTION PIPELINE":
            case "OCV PERSON DETECTION PIPELINE":
            case "TEST DEFAULT SPHINX SPEECH DETECTION PIPELINE":
                break;
            default:
                _errorCollector.checkThat("BestFrame Confidence", (double) actExtrResult.getExemplar().getConfidence(),
                        closeTo(expExtrResult.getExemplar().getConfidence(), delta));
        }
    }

    /**
     * For a given ObjectLocation, compare the actual output to the expected output
     *
     * @param expObjLocation
     * @param actObjLocation
     * @param pipeline
     */
    private void compareJsonDetectionOutputObjects(JsonDetectionOutputObject expObjLocation,
                                                   JsonDetectionOutputObject actObjLocation,
                                                   String pipeline) {
        switch (pipeline) {
            case "OCV FACE DETECTION PIPELINE":
            case "OCV FACE DETECTION WITH AUTO ORIENTATION PIPELINE":
            case "TEST DEFAULT MOG MOTION DETECTION PIPELINE":
            case "SUBSENSE MOTION DETECTION (WITH TRACKING) PIPELINE":
                log.debug("Comparing expected x={} y={} width={} height={} to actual x={} y={} width={} height={}",
                        expObjLocation.getX(), expObjLocation.getY(),expObjLocation.getWidth(), expObjLocation.getHeight(),
                        actObjLocation.getX(), actObjLocation.getY(), actObjLocation.getWidth(), actObjLocation.getHeight());

                // these pipelines are non-deterministic, so use fuzzy logic to compare ObjectLocations
                double overlap = calculateOverlap(expObjLocation.getX(), expObjLocation.getY(),
                        expObjLocation.getWidth(), expObjLocation.getHeight(), actObjLocation.getX(),
                        actObjLocation.getY(), actObjLocation.getWidth(), actObjLocation.getHeight());
                _errorCollector.checkThat("Overlap", overlap, greaterThan(0.0));


                _errorCollector.checkThat("Confidence", (double) actObjLocation.getConfidence(),
                        closeTo(expObjLocation.getConfidence(), deltaFuzzy));
                break;
            case "TEST DEFAULT SPHINX SPEECH DETECTION PIPELINE":
                // Only check detection properties, which should have the speech hypothesis.
                log.debug("Comparing expected detection properties=({}) to actual detection properties({})", expObjLocation.getDetectionProperties().size(),
                        actObjLocation.getDetectionProperties().size());
                _errorCollector.checkThat("Detection Properties", actObjLocation.getDetectionProperties(),
                        is(expObjLocation.getDetectionProperties()));
                break;
            default:
                log.debug("Comparing expected width={} height={} offset={} x={} y={} detectionProperties({}( confidence={} to actual width={} height={} x={} y={} detectionProperties({}) confidence={}",
                        expObjLocation.getWidth(), expObjLocation.getHeight(), expObjLocation.getOffsetFrame(), expObjLocation.getX(),
                        expObjLocation.getY(), expObjLocation.getDetectionProperties().size(), expObjLocation.getConfidence(),
                        actObjLocation.getWidth(), actObjLocation.getHeight(), actObjLocation.getX(), actObjLocation.getY(),
                        actObjLocation.getDetectionProperties().size(), actObjLocation.getConfidence());
                // for all other cases, compare everything
                _errorCollector.checkThat("Width", actObjLocation.getWidth(), is(expObjLocation.getWidth()));
                _errorCollector.checkThat("Height", actObjLocation.getHeight(), is(expObjLocation.getHeight()));
                _errorCollector.checkThat("OffsetFrame", actObjLocation.getOffsetFrame(),
                        is(expObjLocation.getOffsetFrame()));
                _errorCollector.checkThat("X", actObjLocation.getX(), is(expObjLocation.getX()));
                _errorCollector.checkThat("Y", actObjLocation.getY(), is(expObjLocation.getY()));
                _errorCollector.checkThat("Detection Properties", actObjLocation.getDetectionProperties(),
                        is(expObjLocation.getDetectionProperties()));
                _errorCollector.checkThat("Confidence", (double) actObjLocation.getConfidence(),
                        closeTo(expObjLocation.getConfidence(), delta));
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

