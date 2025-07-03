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

package org.mitre.mpf.mst;

import com.google.common.collect.*;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mitre.mpf.interop.*;
import org.mitre.mpf.rest.api.JobCreationMediaData;
import org.mitre.mpf.rest.api.JobCreationMediaSelector;
import org.mitre.mpf.rest.api.MediaSelectorType;
import org.mitre.mpf.rest.api.MediaUri;
import org.mitre.mpf.rest.api.pipelines.ActionProperty;
import org.mitre.mpf.rest.api.pipelines.transients.TransientAction;
import org.mitre.mpf.rest.api.pipelines.transients.TransientPipelineDefinition;
import org.mitre.mpf.rest.api.pipelines.transients.TransientTask;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.camel.operations.detection.artifactextraction.ArtifactExtractionProcessor;

import java.util.*;
import java.util.function.Predicate;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.junit.Assert.*;


@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestSystemOnDiff extends TestSystemWithDefaultConfig {


    @Test(timeout = 5 * MINUTES)
    public void runArtifactExtractionAllDetectionsTest() {
        Map<String, String> jobProperties = new HashMap<>();
        jobProperties.put("OUTPUT_ARTIFACTS_AND_EXEMPLARS_ONLY", "true");
        jobProperties.put(ArtifactExtractionProcessor.POLICY, "ALL_DETECTIONS");
        jobProperties.put(ArtifactExtractionProcessor.CROPPING_POLICY, "false");
        List<JobCreationMediaData> media = toMediaObjectList(ioUtils.findFile("/samples/face/video_01.mp4"));

        long jobId = runPipelineOnMedia("OCV FACE DETECTION PIPELINE", media, jobProperties);
        JsonOutputObject outputObject = getJobOutputObject(jobId);
        assertEquals(1, outputObject.getMedia().size());

        JsonMediaOutputObject outputMedia = outputObject.getMedia().first();
        SortedSet<JsonActionOutputObject> actionOutputObjects = outputMedia.getTrackTypes().get("FACE");

        assertNotNull("Output object did not contain expected track type: FACE", actionOutputObjects);

        List<JsonTrackOutputObject> tracks = actionOutputObjects.stream()
                                             .flatMap(outputObj -> outputObj.getTracks().stream())
                                             .collect(toList());

        // Check that the only detections in the output object are ones that have been extracted
        boolean noUnextractedDetections = tracks.stream()
                                          .flatMap(track -> track.getDetections().stream())
                                          .allMatch(d -> d.getArtifactExtractionStatus().equalsIgnoreCase("COMPLETED"));
        assertTrue("Unextracted detections found in output", noUnextractedDetections);

        // Check that every detection in the track was extracted. For this action and this
        // video, there is a detection in every frame between the track start frame offset
        // and the track stop frame offset, so there should also be an entry in the output
        // object for every frame.
        for (JsonTrackOutputObject track : tracks) {
            Set<Integer> actualFrames = track.getDetections().stream()
                                        .map(d -> d.getOffsetFrame())
                                        .collect(toSet());
            assertTrue(actualFrames.size() > 1); // track should contain more than just exemplar
            Set<Integer> expectedFrames = ContiguousSet.create(Range.closed(track.getStartOffsetFrame(),
                                                                            track.getStopOffsetFrame()),
                                                               DiscreteDomain.integers());
            assertEquals("Expected frames and actual frames don't match", expectedFrames, actualFrames);
        }
    }


    @Test(timeout = 5 * MINUTES)
    public void runArtifactExtractionArtifactsAndExemplarsOnlyTest() {
        Map<String, String> jobProperties = new HashMap<>();
        jobProperties.put("OUTPUT_ARTIFACTS_AND_EXEMPLARS_ONLY", "true");
        jobProperties.put(ArtifactExtractionProcessor.FIRST_FRAME_POLICY, "true");
        List<JobCreationMediaData> media = toMediaObjectList(ioUtils.findFile("/samples/face/video_01.mp4"));

        long jobId = runPipelineOnMedia("OCV FACE DETECTION PIPELINE", media, jobProperties);
        JsonOutputObject outputObject = getJobOutputObject(jobId);
        assertEquals(1, outputObject.getMedia().size());

        JsonMediaOutputObject outputMedia = outputObject.getMedia().first();
        SortedSet<JsonActionOutputObject> actionOutputObjects = outputMedia.getTrackTypes().get("FACE");

        assertNotNull("Output object did not contain expected track type: FACE", actionOutputObjects);

        List<JsonTrackOutputObject> tracks = actionOutputObjects.stream()
                                             .flatMap(outputObj -> outputObj.getTracks().stream())
                                             .collect(toList());

        // Check that the only detections in the output object are ones that have been extracted
        boolean noUnextractedDetections = tracks.stream()
                                          .flatMap(track -> track.getDetections().stream())
                                          .allMatch(d -> d.getArtifactExtractionStatus().equalsIgnoreCase("COMPLETED"));
        assertTrue("Unextracted detections found in output", noUnextractedDetections);

        // Check that the exemplars were all extracted
        List<JsonDetectionOutputObject> exemplars = tracks.stream()
                                                    .map(track -> track.getExemplar())
                                                    .collect(toList());
        assertTrue(exemplars.stream().allMatch(e -> e.getArtifactExtractionStatus().equalsIgnoreCase("COMPLETED")));

        List<Integer> detections = tracks.stream()
                                   .flatMap(t -> t.getDetections().stream())
                                   .map(d -> d.getOffsetFrame())
                                   .collect(toList());
        // Check that all of the first frames were extracted, and it's not just the exemplars.
        assertFalse(detections.equals(exemplars));
        for (JsonTrackOutputObject track : tracks) {
                int firstDetectionIndex = track.getStartOffsetFrame();
                assertTrue(detections.contains(firstDetectionIndex));
        }
    }


    @Test(timeout = 5 * MINUTES)
    public void runArtifactExtractionLastTaskOnlyTest() {

        String pipelineName = "OCV FACE DETECTION (WITH MOG MOTION PREPROCESSOR) PIPELINE";
        Map<String, String> jobProperties = new HashMap<>();
        jobProperties.put("SUPPRESS_TRACKS", "true");
        List<JobCreationMediaData> media = toMediaObjectList(ioUtils.findFile("/samples/face/ff-region-motion-face.avi"));

        long jobId = runPipelineOnMedia(pipelineName, media, jobProperties);
        JsonOutputObject outputObject = getJobOutputObject(jobId);
        assertEquals(1, outputObject.getMedia().size());

        // Check that the first action (MOTION) was suppressed
        JsonMediaOutputObject outputMedia = outputObject.getMedia().first();
        SortedSet<JsonActionOutputObject> suppressedActionOutput =
                outputMedia.getTrackTypes().get(JsonActionOutputObject.TRACKS_SUPPRESSED_TYPE);

        assertNotNull("Output object did not contain TRACKS_SUPPRESSED_TYPE", suppressedActionOutput);
        // Make sure that only one task was suppressed
        assertEquals("Output contained more than one suppressed task", 1, suppressedActionOutput.size());
        // Make sure that the suppressed task was MOTION
        assertEquals("Tracks suppressed for task other than MOTION", "MOG MOTION DETECTION PREPROCESSOR ACTION",
                suppressedActionOutput.first().getAction());
    }

    @Test(timeout = 5 * MINUTES)
    public void runArtifactExtractionWithActionProperties() {
        List<JobCreationMediaData> media = toMediaObjectList(ioUtils.findFile("/samples/face/video_01.mp4"));
        String propActionName = "TEST OCV FACE WITH ARTIFACT EXTRACTION PROPERTIES ACTION";
        addAction(propActionName, "FACECV",
                  ImmutableMap.of(
                          "OUTPUT_ARTIFACTS_AND_EXEMPLARS_ONLY", "true",
                          ArtifactExtractionProcessor.EXEMPLAR_FRAME_PLUS_POLICY, "1"));
        String propTaskName = "TEST OCV FACE WITH ARTIFACT EXTRACTION PROPERTIES TASK";
        addTask(propTaskName, propActionName);

        String pipelineName = "TEST OCV FACE WITH ARTIFACT EXTRACTION PROPERTIES PIPELINE";
        addPipeline(pipelineName, propTaskName);

        long jobId = runPipelineOnMedia(pipelineName, media);
        JsonOutputObject outputObject = getJobOutputObject(jobId);
        assertEquals(1, outputObject.getMedia().size());

        JsonMediaOutputObject outputMedia = outputObject.getMedia().first();
        SortedSet<JsonActionOutputObject> actionOutputObjects = outputMedia.getTrackTypes().get("FACE");

        assertNotNull("Output object did not contain expected track type: FACE", actionOutputObjects);

        // The media used in this test generates two tracks that both have the same range of frames, but different exemplars,
        // and the set of extractions for each track do not intersect with each other.

        List<JsonTrackOutputObject> tracks = actionOutputObjects.stream()
                                             .flatMap(outputObj -> outputObj.getTracks().stream())
                                             .collect(toList());

        // Check that the only detections in the output object are ones that have been extracted
        boolean noUnextractedDetections = tracks.stream()
                                          .flatMap(track -> track.getDetections().stream())
                                          .allMatch(d -> d.getArtifactExtractionStatus().equalsIgnoreCase("COMPLETED"));
        assertTrue("Unextracted detections found in output", noUnextractedDetections);
        // Check that the exemplars were all extracted
        List<JsonDetectionOutputObject> exemplars = tracks.stream()
                                                    .map(track -> track.getExemplar())
                                                    .collect(toList());
        assertTrue(exemplars.stream().allMatch(e -> e.getArtifactExtractionStatus().equalsIgnoreCase("COMPLETED")));
        // Check that all of the expected frames were extracted
        for (JsonTrackOutputObject track : tracks) {
            List<Integer> extractedFrames = track.getDetections().stream()
                                   .map(d -> d.getOffsetFrame())
                                   .collect(toList());
            int exemplarIndex = track.getExemplar().getOffsetFrame();
            if (exemplarIndex-1 >= track.getStartOffsetFrame()) {
                assertTrue("Missing extraction before exemplar", extractedFrames.contains(exemplarIndex-1));
            }
            if (exemplarIndex+1 <= track.getStopOffsetFrame()) {
                assertTrue("Missing extraction after exemplar", extractedFrames.contains(exemplarIndex+1));
            }
        }
    }

    @Test(timeout = 5 * MINUTES)
    public void runArtifactExtractionWithMediaProperty() {
        var media = ImmutableList.of(
                toMediaObject(
                        ioUtils.findFile("/samples/face/ff-region-motion-face.avi"),
                        ImmutableMap.of("SUPPRESS_TRACKS", "true")),
                toMediaObject(ioUtils.findFile("/samples/face/ff-region-motion-face.avi")));

        String pipelineName = "OCV FACE DETECTION (WITH MOG MOTION PREPROCESSOR) PIPELINE";
        long jobId = runPipelineOnMedia(pipelineName, media);
        JsonOutputObject outputObject = getJobOutputObject(jobId);

        // Check that the first task (MOTION) was suppressed for the first media
        List<JsonMediaOutputObject> mediaOutput = outputObject.getMedia().stream().collect(toList());
        assertEquals(2, mediaOutput.size());
        SortedSet<JsonActionOutputObject> firstMediaSuppressed =
                mediaOutput.get(0).getTrackTypes().get(JsonActionOutputObject.TRACKS_SUPPRESSED_TYPE);
        assertNotNull("Output object did not contain TRACKS_SUPPRESSED_TYPE", firstMediaSuppressed);
        // Make sure that only one action was suppressed
        assertEquals("Output contained more than one suppressed action", 1, firstMediaSuppressed.size());
        // Make sure that the suppressed action was MOTION
        assertEquals("Tracks suppressed for action other than MOTION", "MOG MOTION DETECTION PREPROCESSOR ACTION",
                firstMediaSuppressed.first().getAction());

        // Check that the second media did not have a suppressed action
        assertFalse("Found an incorrectly suppressed action",
                mediaOutput.get(1).getTrackTypes().containsKey(JsonActionOutputObject.TRACKS_SUPPRESSED_TYPE));
    }

    @Test(timeout = 5 * MINUTES)
    public void runArtifactExtractionWithPolicyNoneTest() {
        Map<String, String> jobProperties = new HashMap<>();
        jobProperties.put(ArtifactExtractionProcessor.POLICY, "NONE");
        List<JobCreationMediaData> media = toMediaObjectList(ioUtils.findFile("/samples/face/video_01.mp4"));

        long jobId = runPipelineOnMedia("OCV FACE DETECTION PIPELINE", media, jobProperties);
        JsonOutputObject outputObject = getJobOutputObject(jobId);
        assertEquals(1, outputObject.getMedia().size());

        JsonMediaOutputObject outputMedia = outputObject.getMedia().first();
        SortedSet<JsonActionOutputObject> actionOutputObjects = outputMedia.getTrackTypes().get("FACE");

        assertNotNull("Output object did not contain expected track type: FACE", actionOutputObjects);

        List<JsonTrackOutputObject> tracks = actionOutputObjects.stream()
                                             .flatMap(outputObj -> outputObj.getTracks().stream())
                                             .collect(toList());

        for (JsonTrackOutputObject track : tracks) {
            // Check that the set of detections for each track is empty.
            boolean foundExtractions = track.getDetections().stream()
                                       .anyMatch(d -> d.getArtifactExtractionStatus() == "COMPLETED");
            assertFalse("Found extraction in output when the artifact extraction policy \"NONE\" was set",
                        foundExtractions);
            // Check that the exemplar was not extracted either.
            assertTrue("Exemplar was extracted when the artifact extraction policy \"NONE\" was set",
                       track.getExemplar().getArtifactExtractionStatus().equals("NOT_ATTEMPTED"));
        }
    }


    @Test(timeout = 5 * MINUTES)
    public void runMergeWithPreviousTextTaskTest() {
        String pipelineName = "TESSERACT OCR TEXT DETECTION ON EAST REGIONS WITH KEYWORD TAGGING PIPELINE";
        addPipeline(pipelineName,
                "EAST TEXT DETECTION TASK",
                "TESSERACT OCR TEXT DETECTION (WITH FF REGION) TASK",
                "KEYWORD TAGGING (WITH FF REGION) TASK"); // has OUTPUT_MERGE_WITH_PREVIOUS_TASK=TRUE

        List<JobCreationMediaData> media = toMediaObjectList(ioUtils.findFile("/samples/ocr/keyword-tagging.jpg"));
        long jobId = runPipelineOnMedia(pipelineName, media, Map.of());
        JsonOutputObject outputObject = getJobOutputObject(jobId);
        assertEquals(1, outputObject.getMedia().size());

        JsonMediaOutputObject outputMedia = outputObject.getMedia().first();
        assertEquals(2, outputMedia.getTrackTypes().size());

        SortedSet<JsonActionOutputObject> textRegionTracksOutput = outputMedia.getTrackTypes().get("TEXT REGION");
        assertEquals(1, textRegionTracksOutput.size());
        assertEquals("TEXT REGION tracks for task other than EAST", "EAST TEXT DETECTION ACTION",
                textRegionTracksOutput.first().getAction());
        assertEquals("EAST", textRegionTracksOutput.first().getAlgorithm());
        assertTrue(textRegionTracksOutput.first().getTracks().stream().allMatch(
                t -> t.getType().equals("TEXT REGION")));

        SortedSet<JsonActionOutputObject> textTracksOutput  = outputMedia.getTrackTypes().get("TEXT");
        assertEquals(1, textTracksOutput.size());
        assertEquals("TEXT tracks for task other than KEYWORD TAGGING",
                "TESSERACT OCR TEXT DETECTION (WITH FF REGION) ACTION",
                textTracksOutput.first().getAction());
        assertEquals("TESSERACTOCR", textTracksOutput.first().getAlgorithm());
        assertTrue(textTracksOutput.first().getTracks().stream().allMatch(t -> t.getType().equals("TEXT")));

        boolean allTextTracksHaveTags = textTracksOutput.stream()
                .flatMap(ja -> ja.getTracks().stream())
                .allMatch(jt -> jt.getTrackProperties().containsKey("TAGS"));
        assertTrue(
                "The keyword tagging task should have added a \"TAGS\" track property"
                        + " to all of the text tracks.",
                allTextTracksHaveTags);
    }

    @Test(timeout = 5 * MINUTES)
    public void runMergeWithPreviousSpeechTaskTest() {
        String pipelineName = "SPHINX SPEECH DETECTION WITH KEYWORD TAGGING AND MARKUP PIPELINE";
        addPipeline(pipelineName,
                "SPHINX SPEECH DETECTION TASK",
                "KEYWORD TAGGING (WITH FF REGION) TASK", // has OUTPUT_MERGE_WITH_PREVIOUS_TASK=TRUE
                "OCV GENERIC MARKUP TASK");

        List<JobCreationMediaData> media = toMediaObjectList(ioUtils.findFile("/samples/speech/green.wav"));
        long jobId = runPipelineOnMedia(pipelineName, media, Map.of());
        JsonOutputObject outputObject = getJobOutputObject(jobId);
        assertEquals(1, outputObject.getMedia().size());

        JsonMediaOutputObject outputMedia = outputObject.getMedia().first();
        assertEquals(2, outputMedia.getTrackTypes().size());

        SortedSet<JsonActionOutputObject> speechTracksOutput = outputMedia.getTrackTypes().get("SPEECH");
        assertEquals(1, speechTracksOutput.size());
        assertEquals("SPEECH tracks for task other than KEYWORD TAGGING",
                "SPHINX SPEECH DETECTION ACTION",
                speechTracksOutput.first().getAction());
        assertEquals("SPHINX", speechTracksOutput.first().getAlgorithm());
        assertTrue(speechTracksOutput.first().getTracks().stream().allMatch(t -> t.getType().equals("SPEECH")));

        boolean allSpeechTracksHaveTags = speechTracksOutput.stream()
                .flatMap(ja -> ja.getTracks().stream())
                .allMatch(jt -> jt.getTrackProperties().containsKey("TAGS"));
        assertTrue(
                "The keyword tagging task should have added a \"TAGS\" track property"
                        + " to all of the speech tracks.",
                allSpeechTracksHaveTags);

        SortedSet<JsonActionOutputObject> noTracksOutput  =
                outputMedia.getTrackTypes().get(JsonActionOutputObject.NO_TRACKS_TYPE);
        assertEquals(1, noTracksOutput.size());
        assertEquals("No tracks for task other than MARKUP",
                "OCV GENERIC MARKUP ACTION",
                noTracksOutput.first().getAction());
        assertEquals("MARKUPCV", noTracksOutput.first().getAlgorithm());
    }


    @Test(timeout = 5 * MINUTES)
    public void runFaceOcvDetectImage() throws Exception {
        runSystemTest("OCV FACE DETECTION PIPELINE", "output/face/runFaceOcvDetectImage.json",
                      "/samples/face/meds-aa-S001-01.jpg",
                      "/samples/face/meds-aa-S029-01.jpg",
                      "/samples/face/meds-af-S419-01.jpg");
    }


    @Test(timeout = 5 * MINUTES)
    public void runFaceOcvDetectImageWithAutoOrientation() throws Exception {
        runFaceOcvDetectImageWithAutoOrientation(ioUtils.findFile(
                "/samples/face/meds-aa-S001-01-exif-rotation.jpg"));
    }

    @Test(timeout = 5 * MINUTES)
    public void runFaceOcvDetectImageWithAutoOrientationDataUri() throws IOException {
        var pathToUriFile = Path.of(ioUtils.findFile(
                "/samples/face/meds-aa-S001-01-exif-rotation.jpg.datauri.txt"));
        var uriStr = Files.readString(pathToUriFile, StandardCharsets.US_ASCII).strip();
        runFaceOcvDetectImageWithAutoOrientation(URI.create(uriStr));
    }

    private void runFaceOcvDetectImageWithAutoOrientation(URI mediaUri) throws IOException {
        runSystemTest(
                "OCV FACE DETECTION (WITH AUTO-ORIENTATION) PIPELINE",
                "output/face/runFaceOcvDetectImageWithAutoOrientation.json",
                toMediaObject(mediaUri));
    }

    @Test(timeout = 15 * MINUTES)
    public void runFaceOcvDetectVideo() throws Exception {
        runSystemTest("OCV FACE DETECTION PIPELINE", "output/face/runFaceOcvDetectVideo.json",
                      "/samples/face/new_face_video.avi",
                      "/samples/person/video_02.mp4",
                      "/samples/face/video_01.mp4");
    }

    @Test(timeout = 10 * MINUTES)
    public void runFaceOcvDetectVideoWithNoDetections() throws Exception {
        runSystemTest("OCV FACE DETECTION PIPELINE",
                      "output/face/runFaceOcvDetectVideoWithNoDetections.json",
                      "/samples/speech/green.mov");
    }

    @Test(timeout = 5 * MINUTES)
    public void runFaceOcvDetectVideoWithRegionOfInterest() throws Exception {
        String roiActionName = "TEST OCV FACE WITH ROI ACTION";
        addAction(roiActionName, "FACECV",
                  ImmutableMap.of(
                          "SEARCH_REGION_ENABLE_DETECTION", "true",
                          "SEARCH_REGION_TOP_LEFT_X_DETECTION", "310",
                          "SEARCH_REGION_TOP_LEFT_Y_DETECTION", "50"));

        String roiTaskName = "TEST OCV FACE WITH ROI TASK";
        addTask(roiTaskName, roiActionName);

        String pipelineName = "TEST OCV FACE WITH ROI PIPELINE";
        addPipeline(pipelineName, roiTaskName);

        runSystemTest(pipelineName,
                      "output/face/runFaceOcvDetectVideoWithRegionOfInterest.json",
                      "/samples/face/new_face_video.avi");
    }


    @Test(timeout = 5 * MINUTES)
    public void runFaceOcvDetectVideoWithRegionOfInterestUsingPercent() throws Exception {
        String roiActionName = "TEST OCV FACE WITH ROI PERCENT ACTION";
        addAction(roiActionName, "FACECV",
                  ImmutableMap.of(
                          "SEARCH_REGION_ENABLE_DETECTION", "true",
                          "SEARCH_REGION_TOP_LEFT_X_DETECTION", "25%",
                          "SEARCH_REGION_TOP_LEFT_Y_DETECTION", "25%",
                          "SEARCH_REGION_BOTTOM_RIGHT_X_DETECTION", "75%",
                          "SEARCH_REGION_BOTTOM_RIGHT_Y_DETECTION", "75%"));

        String roiTaskName = "TEST OCV FACE WITH ROI PERCENT TASK";
        addTask(roiTaskName, roiActionName);

        String pipelineName = "TEST OCV FACE WITH ROI PERCENT PIPELINE";
        addPipeline(pipelineName, roiTaskName);

        runSystemTest(pipelineName,
                      "output/face/runFaceOcvDetectVideoWithRegionOfInterestPercent.json",
                      "/samples/face/new_face_video.avi");
    }


    @Test(timeout = 5 * MINUTES)
    public void runFaceOcvDetectVideoWithRegionOfInterestUsingPercentAndLocation() throws Exception {
        String roiActionName = "TEST OCV FACE WITH ROI PERCENT AND LOCATION ACTION";
        addAction(roiActionName, "FACECV",
                  ImmutableMap.of(
                          "SEARCH_REGION_ENABLE_DETECTION", "true",
                          "SEARCH_REGION_TOP_LEFT_X_DETECTION", "310",
                          "SEARCH_REGION_TOP_LEFT_Y_DETECTION", "50",
                          "SEARCH_REGION_BOTTOM_RIGHT_X_DETECTION", "90%",
                          "SEARCH_REGION_BOTTOM_RIGHT_Y_DETECTION", "90%"));

        String roiTaskName = "TEST OCV FACE WITH ROI PERCENT AND LOCATION TASK";
        addTask(roiTaskName, roiActionName);

        String pipelineName = "TEST OCV FACE WITH ROI PERCENT AND LOCATION PIPELINE";
        addPipeline(pipelineName, roiTaskName);

        runSystemTest(pipelineName,
                      "output/face/runFaceOcvDetectVideoWithRegionOfInterestPercentAndLocation.json",
                      "/samples/face/new_face_video.avi");
    }


    @Test
    public void runOcvFaceWithUseKeyFrames() {
        String baseName = "TEST OCV FACE WITH USE_KEY_FRAMES";
        String actionName = baseName + " ACTION";
        addAction(actionName, "FACECV", ImmutableMap.of("USE_KEY_FRAMES", "true"));

        String taskName = baseName + " TASK";
        addTask(taskName, actionName);

        String pipelineName = baseName + " PIPELINE";
        addPipeline(pipelineName, taskName);

        List<JobCreationMediaData> media = toMediaObjectList(ioUtils.findFile("/samples/face/video_01.mp4"));

        long jobId = runPipelineOnMedia(pipelineName, media);
        JsonOutputObject outputObject = getJobOutputObject(jobId);

        assertEquals(1, outputObject.getMedia().size());
        JsonMediaOutputObject outputMedia = outputObject.getMedia().first();

        SortedSet<JsonActionOutputObject> actionOutputObjects = outputMedia.getTrackTypes().get("FACE");
        assertNotNull("Output object did not contain expected track type: FACE", actionOutputObjects);

        List<JsonDetectionOutputObject> detections = actionOutputObjects.stream()
                .flatMap(outputObj -> outputObj.getTracks().stream())
                .flatMap(track -> track.getDetections().stream())
                .collect(toList());

        assertFalse(detections.isEmpty());

        Set<Integer> keyFrames = ImmutableSet.of(0, 30, 60);

        assertTrue("Found detection in non-keyframe", detections.stream()
                .allMatch(o -> keyFrames.contains(o.getOffsetFrame())));
    }


    @Test
    public void runOcvFaceOnRotatedImage() {
        String pipelineName = "OCV FACE DETECTION PIPELINE";

        var media = toMediaObject(
                ioUtils.findFile("/samples/face/meds-af-S419-01_40deg.jpg"),
                ImmutableMap.of("ROTATION", "60"));

        long jobId = runPipelineOnMedia(pipelineName, List.of(media));
        JsonOutputObject outputObject = getJobOutputObject(jobId);
        assertDetectionExistAndAllMatch(outputObject, d -> d.getX() > 480);
    }


    @Test
    public void runOcvFaceOnRotatedVideo() {
        var media = toMediaObject(
                ioUtils.findFile("/samples/face/video_01_220deg.avi"),
                ImmutableMap.of("ROTATION", "220"));

        long jobId = runPipelineOnMedia("OCV FACE DETECTION PIPELINE", List.of(media));
        JsonOutputObject outputObject = getJobOutputObject(jobId);

        assertDetectionExistAndAllMatch(outputObject, d -> d.getX() > 640);
    }


    private static void assertDetectionExistAndAllMatch(JsonOutputObject outputObject,
                                                        Predicate<JsonDetectionOutputObject> pred) {
        List<JsonDetectionOutputObject> detections = outputObject.getMedia()
                .stream()
                .flatMap(m -> m.getTrackTypes().values().stream())
                .flatMap(Collection::stream)
                .flatMap(a -> a.getTracks().stream())
                .flatMap(t -> t.getDetections().stream())
                .collect(toList());


        assertFalse("Expected at least one detection, but there weren't any.", detections.isEmpty());

        boolean allMatch = detections.stream().allMatch(pred);
        assertTrue(allMatch);
    }


    @Test(timeout = 5 * MINUTES)
    public void runMogThenOcvFaceFeedForwardRegionTest() {
        String actionTaskName = "TEST OCV FACE WITH FEED FORWARD SUPERSET REGION";

        String actionName = actionTaskName + " ACTION";
        addAction(actionName, "FACECV",
                  ImmutableMap.of("FEED_FORWARD_TYPE", "SUPERSET_REGION"));

        String taskName = actionTaskName + " TASK";
        addTask(taskName, actionName);

        String pipelineName = "MOG FEED SUPERSET REGION TO OCVFACE PIPELINE";
        addPipeline(pipelineName, "MOG MOTION DETECTION (WITH TRACKING) TASK", taskName);

        int firstMotionFrame = 31; // The first 30 frames of the video are identical so there shouldn't be motion.
        int maxXMotion = 640 / 2; // Video is 640 x 480 and only the face on the left side of the frame moves.

        runFeedForwardRegionTest(pipelineName, "/samples/face/ff-region-motion-face.avi",
                                 "FACE", firstMotionFrame, maxXMotion);
    }


    @Test(timeout = 5 * MINUTES)
    public void runMogThenOcvFaceRotated40degFeedForwardRegionTest() {
        String actionTaskName = "TEST OCV FACE WITH FEED FORWARD SUPERSET REGION";

        var ocvFaceAction = new TransientAction(
                actionTaskName,
                "FACECV",
                List.of(new ActionProperty("FEED_FORWARD_TYPE", "SUPERSET_REGION")));
        var ocvFaceTask = new TransientTask(actionTaskName, List.of(actionTaskName));
        var transientPipeline = new TransientPipelineDefinition(
                List.of("MOG MOTION DETECTION (WITH TRACKING) TASK", actionTaskName),
                List.of(ocvFaceTask),
                List.of(ocvFaceAction));

        int firstMotionFrame = 31; // The first 30 frames of the video are identical so there shouldn't be motion.

        List<JsonDetectionOutputObject> detections = runFeedForwardTest(
                transientPipeline, "/samples/face/ff-region-motion-face_40deg.avi",
                getRotationMap(40), "FACE", firstMotionFrame);

        assertFalse(detections.isEmpty());

        assertAllInExpectedRegion(
                new int[]{0, 80, 799, 0},
                new int[]{0,  0, 779, 779},
                detections);
    }


    @Test(timeout = 5 * MINUTES)
    public void runMogThenOcvFaceRotated90degFeedForwardRegionTest() {
        String actionTaskName = "TEST OCV FACE WITH FEED FORWARD SUPERSET REGION";

        String actionName = actionTaskName + " ACTION";
        addAction(actionName, "FACECV",
                  ImmutableMap.of("FEED_FORWARD_TYPE", "SUPERSET_REGION"));

        String taskName = actionTaskName + " TASK";
        addTask(taskName, actionName);

        String pipelineName = "MOG FEED SUPERSET REGION TO OCVFACE PIPELINE";
        addPipeline(pipelineName, "MOG MOTION DETECTION (WITH TRACKING) TASK", taskName);

        int firstMotionFrame = 31; // The first 30 frames of the video are identical so there shouldn't be motion.


        List<JsonDetectionOutputObject> detections = runFeedForwardTest(
                pipelineName, "/samples/face/ff-region-motion-face_90deg.avi",
                getRotationMap(90), "FACE", firstMotionFrame);

        assertFalse(detections.isEmpty());

        assertTrue(detections.stream()
                           .flatMap(d -> getCorners(d).stream())
                           .allMatch(p -> p.getY() > 320));
    }


    private static final Map<String, String> TINY_YOLO_CONFIG = ImmutableMap.of(
            "MODEL_NAME", "tiny yolo");



    @Test(timeout = 5 * MINUTES)
    public void runMogThenOalprFeedForwardRegionTest() {
        String actionTaskName = "TEST OALPR WITH FEED FORWARD SUPERSET REGION";

        String actionName = actionTaskName + " ACTION";
        addAction(actionName, "OALPR",
                  ImmutableMap.of("FEED_FORWARD_TYPE", "SUPERSET_REGION"));

        String taskName = actionTaskName + " TASK";
        addTask(taskName, actionName);

        String pipelineName = "MOG FEED SUPERSET REGION TO OALPR PIPELINE";
        addPipeline(pipelineName, "MOG MOTION DETECTION (WITH TRACKING) TASK", taskName);

        int firstMotionFrame = 31; // The first 30 frames of the video are identical so there shouldn't be motion.
        int maxXMotion = 800 / 2; // Video is 800 x 400 and only the license plate on the left side of the frame moves.

        runFeedForwardRegionTest(pipelineName, "/samples/text/ff-region-motion-lp.avi",
                                 "TEXT", firstMotionFrame, maxXMotion);
    }


    @Test(timeout = 5 * MINUTES)
    public void runOcvFaceThenMogFeedForwardRegionTest() {
        String actionTaskName = "TEST MOG WITH FEED FORWARD SUPERSET REGION";

        String actionName = actionTaskName + " ACTION";
        addAction(actionName, "MOG",
                  ImmutableMap.of("FEED_FORWARD_TYPE", "SUPERSET_REGION", "USE_MOTION_TRACKING", "1"));

        String taskName = actionTaskName + " TASK";
        addTask(taskName, actionName);

        String pipelineName = "OCVFACE FEED SUPERSET REGION TO MOG PIPELINE";
        addPipeline(pipelineName, "OCV FACE DETECTION TASK", taskName);

        int firstFaceFrame = 31; // The first 30 frames of the video do not contains any faces
        int maxXDetection = 640 / 2; // Video is 640 x 480 and only the left side of the frame contains a face.

        runFeedForwardRegionTest(pipelineName, "/samples/motion/ff-region-face-motion.avi",
                                 "MOTION", firstFaceFrame, maxXDetection);
    }


    @Test(timeout = 5 * MINUTES)
    public void runOcvFaceThenSubsenseFeedForwardRegionTest() {
        String actionTaskName = "TEST SUBSENSE WITH FEED FORWARD SUPERSET REGION";

        String actionName = actionTaskName + " ACTION";
        addAction(actionName, "SUBSENSE",
                  ImmutableMap.of("FEED_FORWARD_TYPE", "SUPERSET_REGION", "USE_MOTION_TRACKING", "1"));

        String taskName = actionTaskName + " TASK";
        addTask(taskName, actionName);

        String pipelineName = "OCVFACE FEED SUPERSET REGION TO SUBSENSE PIPELINE";
        addPipeline(pipelineName, "OCV FACE DETECTION TASK", taskName);

        int firstFaceFrame = 31; // The first 30 frames of the video do not contains any faces
        int maxXDetection = 640 / 2; // Video is 640 x 480 and only the left side of the frame contains a face.

        runFeedForwardRegionTest(pipelineName, "/samples/motion/ff-region-face-motion.avi",
                                 "MOTION", firstFaceFrame, maxXDetection);
    }


    @Test(timeout = 5 * MINUTES)
    public void runMogThenOcvDnnFeedForwardSupersetRegionTest() {
        String actionTaskName = "TEST DNNCV WITH FEED FORWARD SUPERSET REGION";

        String actionName = actionTaskName + " ACTION";
        addAction(actionName, "DNNCV",
                  ImmutableMap.of("FEED_FORWARD_TYPE", "SUPERSET_REGION"));

        String taskName = actionTaskName + " TASK";
        addTask(taskName, actionName);

        String pipelineName = "MOG FEED SUPERSET REGION TO DNNCV PIPELINE";
        addPipeline(pipelineName, "MOG MOTION DETECTION (WITH TRACKING) TASK", taskName);

        int firstMotionFrame = 31; // The first 30 frames of the video are identical so there shouldn't be motion.
        int maxXMotion = 680 / 2; // Video is 680 x 320 and only the object on the left side of the frame moves.

        runFeedForwardRegionTest(pipelineName, "/samples/object/ff-region-object-motion.avi",
                                 "CLASS", firstMotionFrame, maxXMotion);
    }


    @Test(timeout = 5 * MINUTES)
    public void runMogThenOcvDnnFeedForwardExactRegionTest() {
        String actionTaskName = "TEST DNNCV WITH FEED FORWARD EXACT REGION";

        String actionName = actionTaskName + " ACTION";
        addAction(actionName, "DNNCV",
                  ImmutableMap.of("FEED_FORWARD_TYPE", "REGION"));

        String taskName = actionTaskName + " TASK";
        addTask(taskName, actionName);

        String pipelineName = "MOG FEED EXACT REGION TO DNNCV PIPELINE";
        addPipeline(pipelineName, "MOG MOTION DETECTION (WITH TRACKING) TASK", taskName);


        List<JobCreationMediaData> media = toMediaObjectList(
                ioUtils.findFile("/samples/object/ff-exact-region-object-motion.avi"));

        long jobId = runPipelineOnMedia(pipelineName, media);
        JsonOutputObject outputObject = getJobOutputObject(jobId);

        assertEquals(1, outputObject.getMedia().size());
        JsonMediaOutputObject outputMedia = outputObject.getMedia().first();

        SortedSet<JsonActionOutputObject> ocvDnnOutputObjects = outputMedia.getTrackTypes().get("CLASS");
        assertNotNull(ocvDnnOutputObjects);

        SortedSet<JsonActionOutputObject> motionOutputObjects = outputMedia.getTrackTypes().get("MOTION");
        assertNotNull(motionOutputObjects);

        List<JsonTrackOutputObject> ocvDnnTracks = ocvDnnOutputObjects.stream()
                .flatMap(o -> o.getTracks().stream())
                .collect(toList());

        List<JsonTrackOutputObject> motionTracks = motionOutputObjects.stream()
                .flatMap(o -> o.getTracks().stream())
                .collect(toList());

        assertTracksMatch(ocvDnnTracks, motionTracks);


        List<JsonDetectionOutputObject> ocvDnnDetections = ocvDnnTracks.stream()
                .flatMap(t -> t.getDetections().stream())
                .collect(toList());


        assertTrue(ocvDnnDetections.stream()
                           .allMatch(d -> d.getOffsetFrame() >= 31));

        assertTrue(ocvDnnDetections.stream()
                           .allMatch(d -> d.getX() + d.getWidth() <= 180));


        Set<String> detectedObjects = ocvDnnDetections.stream()
                .map(d -> d.getDetectionProperties().get("CLASSIFICATION"))
                // Not sure why OcvDnn detects an envelope,
                // but the important thing is that OcvDnn doesn't find Granny Smith.
                .filter(c -> !c.equalsIgnoreCase("envelope"))
                .collect(toSet());

        assertEquals(1, detectedObjects.size());
        assertTrue(detectedObjects.contains("digital clock"));
    }


    @Test(timeout = 5 * MINUTES)
    public void runMogThenOcvDnnRotated60degFeedForwardExactRegionTest() {
        String actionTaskName = "TEST DNNCV WITH FEED FORWARD EXACT REGION";

        String actionName = actionTaskName + " ACTION";
        addAction(actionName, "DNNCV",
                  ImmutableMap.of("FEED_FORWARD_TYPE", "REGION"));

        String taskName = actionTaskName + " TASK";
        addTask(taskName, actionName);

        String pipelineName = "MOG FEED EXACT REGION TO DNNCV PIPELINE";
        addPipeline(pipelineName, "MOG MOTION DETECTION (WITH TRACKING) TASK", taskName);


        List<JobCreationMediaData> media = toMediaObjectList(
                ioUtils.findFile("/samples/object/ff-exact-region-object-motion_60deg.avi"));

        long jobId = runPipelineOnMedia(pipelineName, media, getRotationMap(60));

        JsonOutputObject outputObject = getJobOutputObject(jobId);

        assertEquals(1, outputObject.getMedia().size());
        JsonMediaOutputObject outputMedia = outputObject.getMedia().first();

        SortedSet<JsonActionOutputObject> ocvDnnOutputObjects = outputMedia.getTrackTypes().get("CLASS");
        assertNotNull(ocvDnnOutputObjects);

        SortedSet<JsonActionOutputObject> motionOutputObjects = outputMedia.getTrackTypes().get("MOTION");
        assertNotNull(motionOutputObjects);

        List<JsonTrackOutputObject> ocvDnnTracks = ocvDnnOutputObjects.stream()
                .flatMap(o -> o.getTracks().stream())
                .collect(toList());

        List<JsonTrackOutputObject> motionTracks = motionOutputObjects.stream()
                .flatMap(o -> o.getTracks().stream())
                .collect(toList());

        assertTracksMatch(ocvDnnTracks, motionTracks);


        List<JsonDetectionOutputObject> ocvDnnDetections = ocvDnnTracks.stream()
                .flatMap(t -> t.getDetections().stream())
                .collect(toList());


        assertTrue(ocvDnnDetections.stream()
                           .allMatch(d -> d.getOffsetFrame() >= 31));

        assertAllInExpectedRegion(
                new int[] {-40, 368, 368, -40},
                new int[] {40, 272, 450, 450},
                ocvDnnDetections);

        Set<String> detectedObjects = ocvDnnDetections.stream()
                .filter(d -> d.getConfidence() > 0.9)
                .map(d -> d.getDetectionProperties().get("CLASSIFICATION"))
                .collect(toSet());

        assertEquals(1, detectedObjects.size());
        assertTrue(detectedObjects.contains("digital clock"));
    }


    @Test(timeout = 5 * MINUTES)
    public void runMogThenOcvDnnRotated60degFeedForwardSupersetRegionTest() {
        String actionTaskName = "TEST DNNCV WITH FEED FORWARD SUPERSET REGION";

        String actionName = actionTaskName + " ACTION";
        addAction(actionName, "DNNCV",
                  ImmutableMap.of("FEED_FORWARD_TYPE", "SUPERSET_REGION"));

        String taskName = actionTaskName + " TASK";
        addTask(taskName, actionName);

        String pipelineName = "MOG FEED SUPERSET REGION TO DNNCV PIPELINE";
        addPipeline(pipelineName, "MOG MOTION DETECTION (WITH TRACKING) TASK", taskName);


        List<JobCreationMediaData> media = toMediaObjectList(
                ioUtils.findFile("/samples/object/ff-exact-region-object-motion_60deg.avi"));

        long jobId = runPipelineOnMedia(pipelineName, media, getRotationMap(60));

        JsonOutputObject outputObject = getJobOutputObject(jobId);

        assertEquals(1, outputObject.getMedia().size());
        JsonMediaOutputObject outputMedia = outputObject.getMedia().first();


        SortedSet<JsonActionOutputObject> ocvDnnOutputObjects = outputMedia.getTrackTypes().get("CLASS");
        assertNotNull(ocvDnnOutputObjects);


        List<JsonDetectionOutputObject> ocvDnnDetections = ocvDnnOutputObjects.stream()
                .flatMap(a -> a.getTracks().stream())
                .flatMap(t -> t.getDetections().stream())
                .collect(toList());


        assertTrue(ocvDnnDetections.stream()
                           .allMatch(d -> d.getOffsetFrame() >= 31));

        assertAllInExpectedRegion(
                new int[] {-52, 368, 368, -40},
                new int[] {36, 270, 450, 450},
                ocvDnnDetections);

        Set<String> detectedObjects = ocvDnnDetections.stream()
                .filter(d -> d.getConfidence() > 0.9)
                .map(d -> d.getDetectionProperties().get("CLASSIFICATION"))
                .collect(toSet());

        assertEquals(2, detectedObjects.size());
        assertTrue(detectedObjects.contains("digital clock"));
        assertTrue(detectedObjects.contains("Granny Smith"));
    }


    private static void assertTracksMatch(List<JsonTrackOutputObject> tracks1,
                                          List<JsonTrackOutputObject> tracks2) {
        assertEquals(tracks1.size(), tracks2.size());
        Collections.sort(tracks1);
        Collections.sort(tracks2);

        Iterator<JsonTrackOutputObject> it1 = tracks1.iterator();
        Iterator<JsonTrackOutputObject> it2 = tracks2.iterator();
        while (it1.hasNext()) {
            JsonTrackOutputObject t1 = it1.next();
            JsonTrackOutputObject t2 = it2.next();

            assertEquals(t1.getStartOffsetFrame(), t2.getStartOffsetFrame());
            assertEquals(t1.getStopOffsetFrame(), t2.getStopOffsetFrame());
            assertDetectionsMatch(t1.getDetections(), t2.getDetections());
        }
    }

    private static void assertDetectionsMatch(SortedSet<JsonDetectionOutputObject> detections1,
                                              SortedSet<JsonDetectionOutputObject> detections2) {
        assertEquals(detections1.size(), detections2.size());

        Iterator<JsonDetectionOutputObject> it1 = detections1.iterator();
        Iterator<JsonDetectionOutputObject> it2 = detections2.iterator();

        while (it1.hasNext()) {
            JsonDetectionOutputObject d1 = it1.next();
            JsonDetectionOutputObject d2 = it2.next();
            assertEquals(d1.getOffsetFrame(), d2.getOffsetFrame(), 1);
            assertEquals(d1.getX(), d2.getX(), 1);
            assertEquals(d1.getY(), d2.getY(), 1);
            assertEquals(d1.getWidth(), d2.getWidth(), 1);
            assertEquals(d1.getHeight(), d2.getHeight(), 1);

            assertEquals(
                    Double.parseDouble(d1.getDetectionProperties().getOrDefault("ROTATION", "0")),
                    Double.parseDouble(d2.getDetectionProperties().getOrDefault("ROTATION", "0")),
                    0.1);
        }
    }




    @Test(timeout = 5 * MINUTES)
    public void runMogThenOcvFaceFeedForwardFullFrameTest() {
        String actionTaskName = "TEST OCV FACE WITH FEED FORWARD FULL FRAME";

        String actionName = actionTaskName + " ACTION";
        addAction(actionName, "FACECV",
                  ImmutableMap.of("FEED_FORWARD_TYPE", "FRAME"));

        String taskName = actionTaskName + " TASK";
        addTask(taskName, actionName);

        String pipelineName = "MOG FEED FULL FRAME TO OCVFACE PIPELINE";
        addPipeline(pipelineName, "MOG MOTION DETECTION (WITH TRACKING) TASK", taskName);

        int firstMotionFrame = 31; // The first 30 frames of the video are identical so there shouldn't be motion.
        int maxXLeftDetection = 640 / 2;  // Video is 640x480 and there is face on the left side of the frame.
        int minXRightDetection = 640 / 2;  // Video is 640x480 and there is face on the right side of the frame.
        runFeedForwardFullFrameTest(pipelineName, "/samples/face/ff-region-motion-face.avi",
                                    "FACE", firstMotionFrame, maxXLeftDetection, minXRightDetection);
    }



    @Test(timeout = 5 * MINUTES)
    public void runMogThenOalprFeedForwardFullFrameTest() {
        String actionTaskName = "TEST OALPR WITH FEED FORWARD FULL FRAME";

        String actionName = actionTaskName + " ACTION";
        addAction(actionName, "OALPR",
                  ImmutableMap.of("FEED_FORWARD_TYPE", "FRAME"));

        String taskName = actionTaskName + " TASK";
        addTask(taskName, actionName);

        String pipelineName = "MOG FEED FULL FRAME TO OALPR PIPELINE";
        addPipeline(pipelineName, "MOG MOTION DETECTION (WITH TRACKING) TASK", taskName);

        int firstMotionFrame = 31; // The first 30 frames of the video are identical so there shouldn't be motion.
        int maxXLeftDetection = 800 / 2;  // Video is 800x400 and there is license plate on the left side of the frame.
        int minXRightDetection = 800 / 2;  // Video is 800x400 and there is license plate on the right side of the frame.
        runFeedForwardFullFrameTest(pipelineName, "/samples/text/ff-region-motion-lp.avi",
                                    "TEXT", firstMotionFrame, maxXLeftDetection, minXRightDetection);
    }


    @Test(timeout = 5 * MINUTES)
    public void runMogThenOcvDnnFeedForwardFullFrameTest() {
        String actionTaskName = "TEST DNNCV WITH FEED FORWARD FULL FRAME";

        String actionName = actionTaskName + " ACTION";
        addAction(actionName, "DNNCV",
                  ImmutableMap.of("FEED_FORWARD_TYPE", "FRAME"));

        String taskName = actionTaskName + " TASK";
        addTask(taskName, actionName);

        String pipelineName = "MOG FEED FULL FRAME TO DNNCV PIPELINE";
        addPipeline(pipelineName, "MOG MOTION DETECTION (WITH TRACKING) TASK", taskName);

        int firstMotionFrame = 31; // The first 30 frames of the video are identical so there shouldn't be motion.
        int maxXLeftDetection = 680;  // Video is 680x320 and OcvDnn reports entire frame.
        int minXRightDetection = 0;  //  Video is 680x320 and OcvDnn reports entire frame.
        runFeedForwardFullFrameTest(pipelineName, "/samples/object/ff-region-object-motion.avi",
                                    "CLASS", firstMotionFrame, maxXLeftDetection, minXRightDetection);
    }


    @Test(timeout = 5 * MINUTES)
    public void runOcvFaceThenMogFeedForwardFullFrameTest() {
        String actionTaskName = "TEST MOG WITH FEED FORWARD FULL FRAME";

        String actionName = actionTaskName + " ACTION";
        addAction(actionName, "MOG",
                  ImmutableMap.of("FEED_FORWARD_TYPE", "FRAME", "USE_MOTION_TRACKING", "1"));

        String taskName = actionTaskName + " TASK";
        addTask(taskName, actionName);

        String pipelineName = "OCVFACE FEED FULL FRAME TO MOG PIPELINE";
        addPipeline(pipelineName, "OCV FACE DETECTION TASK", taskName);

        int firstFaceFrame = 31; // The first 30 frames of the video do not contains any faces
        int maxXLeftDetection = 640 / 2;  // Video is 640x480 and there is motion on the left side of the frame.
        int minXRightDetection = 640 / 2;  // Video is 640x480 and there is motion on the right side of the frame.
        runFeedForwardFullFrameTest(pipelineName, "/samples/motion/ff-region-face-motion.avi",
                                    "MOTION", firstFaceFrame, maxXLeftDetection, minXRightDetection);
    }


    @Test(timeout = 5 * MINUTES)
    public void runOcvFaceThenSubsenseFeedForwardFullFrameTest() {
        String actionTaskName = "TEST SUBSENSE WITH FEED FORWARD FULL FRAME";

        String actionName = actionTaskName + " ACTION";
        addAction(actionName, "SUBSENSE",
                  ImmutableMap.of("FEED_FORWARD_TYPE", "FRAME", "USE_MOTION_TRACKING", "1"));

        String taskName = actionTaskName + " TASK";
        addTask(taskName, actionName);

        String pipelineName = "OCVFACE FEED FULL FRAME TO SUBSENSE PIPELINE";
        addPipeline(pipelineName, "OCV FACE DETECTION TASK", taskName);

        int firstFaceFrame = 31; // The first 30 frames of the video do not contains any faces
        int maxXLeftDetection = 640 / 2;  // Video is 640x480 and there is motion on the left side of the frame.
        int minXRightDetection = 640 / 2;  // Video is 640x480 and there is motion on the right side of the frame.
        runFeedForwardFullFrameTest(pipelineName, "/samples/motion/ff-region-face-motion.avi",
                                    "MOTION", firstFaceFrame, maxXLeftDetection, minXRightDetection);
    }


    private void runFeedForwardRegionTest(String pipelineName, String mediaPath, String trackType,
                                          int firstDetectionFrame, int maxXDetection) {

        List<JsonDetectionOutputObject> detections = runFeedForwardTest(pipelineName, mediaPath, trackType,
                                                                        firstDetectionFrame);

        assertTrue("Found detection in stage 2 in region without a detection from stage 1.",
                   detections.stream()
                           .allMatch(d -> d.getX() + d.getWidth() < maxXDetection));
    }


    private void runFeedForwardFullFrameTest(String pipelineName, String mediaPath, String trackType,
                                             int firstDetectionFrame, int maxXLeftDetection, int minXRightDetection) {

        List<JsonDetectionOutputObject> detections = runFeedForwardTest(pipelineName, mediaPath, trackType,
                                                                        firstDetectionFrame);
        boolean foundLeftDetection = detections.stream()
                .anyMatch(d -> d.getX() + d.getWidth() <= maxXLeftDetection);

        assertTrue("Did not find expected detection on left side on frame.", foundLeftDetection);

        boolean foundRightDetection = detections.stream()
                .anyMatch(d -> d.getX() >= minXRightDetection);
        assertTrue("Did not find expected detection on right side on frame.", foundRightDetection);
    }


    private List<JsonDetectionOutputObject> runFeedForwardTest(
            String pipelineName, String mediaPath, String trackType, int firstDetectionFrame) {
        return runFeedForwardTest(pipelineName, mediaPath, Collections.emptyMap(), trackType, firstDetectionFrame);
    }


    private static Map<String, String> getRotationMap(double rotation) {
        return Collections.singletonMap("ROTATION", String.valueOf(rotation));
    }


    private List<JsonDetectionOutputObject> runFeedForwardTest(
            String pipelineName, String mediaPath, Map<String, String> jobProperties,
            String trackType, int firstDetectionFrame) {

        List<JobCreationMediaData> media = toMediaObjectList(ioUtils.findFile(mediaPath));

        long jobId = runPipelineOnMedia(pipelineName, media, jobProperties);
        return processFeedForwardTestOutput(jobId, trackType, firstDetectionFrame);
    }


    private List<JsonDetectionOutputObject> runFeedForwardTest(
            TransientPipelineDefinition pipeline, String mediaPath, Map<String, String> jobProperties,
            String trackType, int firstDetectionFrame) {

        List<JobCreationMediaData> media = toMediaObjectList(ioUtils.findFile(mediaPath));

        long jobId = runPipelineOnMedia(pipeline, media, jobProperties, 4);
        return processFeedForwardTestOutput(jobId, trackType, firstDetectionFrame);
    }


    private List<JsonDetectionOutputObject> processFeedForwardTestOutput(
            long jobId, String trackType, int firstDetectionFrame) {

        JsonOutputObject outputObject = getJobOutputObject(jobId);

        assertEquals(1, outputObject.getMedia().size());
        JsonMediaOutputObject outputMedia = outputObject.getMedia().first();

        SortedSet<JsonActionOutputObject> actionOutputObjects = outputMedia.getTrackTypes().get(trackType);
        assertNotNull("Output object did not contain expected track type: " + trackType,
                      actionOutputObjects);

        List<JsonDetectionOutputObject> detections = actionOutputObjects.stream()
                .flatMap(outputObj -> outputObj.getTracks().stream())
                .flatMap(track -> track.getDetections().stream())
                .collect(toList());

        assertFalse(detections.isEmpty());

        assertTrue("Found detection in stage 2 before first frame with a detection from stage 1.",
                   detections.stream()
                           .allMatch(d -> d.getOffsetFrame() >= firstDetectionFrame));

        return detections;

    }



    @Test(timeout = 5 * MINUTES)
    public void runMotionMogDetectVideo() throws Exception {
        String pipelineName = addDefaultMotionMogPipeline();

        runSystemTest(pipelineName, "output/motion/runMotionMogDetectVideo.json",
                      "/samples/motion/five-second-marathon-clip.mkv",
                      "/samples/person/video_02.mp4");
    }

    //TODO: re-evaluate 5 min timeout! (timeout = 5*MINUTES)
    @Test(timeout = 20 * MINUTES)
    public void runMotionSubsenseTrackingVideo() throws Exception {
        runSystemTest("SUBSENSE MOTION DETECTION (WITH TRACKING) PIPELINE", "output/motion/runMotionSubsenseTrackingVideo.json",
                      "/samples/motion/STRUCK_Test_720p.mp4");
    }

    @Test(timeout = 5 * MINUTES)
    public void runMultipleDetectionAlgorithmsImage() throws Exception {
        String multipleActionTaskName = "TEST MULTIPLE-ACTION TASK";
        addTask(multipleActionTaskName, "OCV FACE DETECTION ACTION", "OCV TINY YOLO OBJECT DETECTION ACTION");

        String pipelineName = "TEST MULTIPLE-ACTION TASK PIPELINE";
        addPipeline(pipelineName, multipleActionTaskName);

        runSystemTest(pipelineName,
                      "output/face/runMultipleDetectionAlgorithmsImage.json",
                      "/samples/person/race.jpg",
                      "/samples/person/homewood-bank-robbery.jpg");
    }

    @Test(timeout = 15 * MINUTES)
    public void runMultipleDetectionAlgorithmsVideo() throws Exception {
        String multipleActionTaskName = "TEST MULTIPLE-ACTION TASK 2";
        addTask(multipleActionTaskName, "OCV FACE DETECTION ACTION", "OCV TINY YOLO OBJECT DETECTION ACTION");

        String pipelineName = "TEST MULTIPLE-ACTION TASK PIPELINE 2";
        addPipeline(pipelineName, multipleActionTaskName);

        runSystemTest(pipelineName,
                      "output/face/runMultipleDetectionAlgorithmsVideo.json",
                      "/samples/person/video_02.mp4");
    }

    @Test(timeout = 5 * MINUTES)
    public void runSpeechSphinxDetectAudio() throws Exception {
        runSystemTest("SPHINX SPEECH DETECTION PIPELINE", "output/speech/runSpeechSphinxDetectAudio.json",
                      "/samples/speech/10001-90210-01803.wav");
    }

    @Test(timeout = 5 * MINUTES)
    public void runSpeechSphinxDetectVideo() throws Exception {
        runSystemTest("SPHINX SPEECH DETECTION PIPELINE", "output/speech/runSpeechSphinxDetectVideo.json",
                      "/samples/speech/10001-90210-01803.mp4");
    }

    private String addDefaultOalprPipeline() throws WfmProcessingException {
        String actionName = "TEST OALPR TEXT DETECTION ACTION";
        addAction(actionName, "OALPR", Collections.emptyMap());

        String taskName = "TEST OALPR TEXT DETECTION TASK";
        addTask(taskName, actionName);

        String pipelineName = "TEST OALPR TEXT DETECTION PIPELINE";
        addPipeline(pipelineName, taskName);
        return pipelineName;
    }

    @Test(timeout = 5 * MINUTES)
    public void runTextOalprDetectImage() throws Exception {
        String pipelineName = addDefaultOalprPipeline();
        runSystemTest(pipelineName, "output/text/runTextOalprDetectImage.json",
                      "/samples/text/lp-bmw.jpg",
                      "/samples/text/lp-police-car.jpg",
                      "/samples/text/lp-trailer.png");
    }

    @Test(timeout = 10 * MINUTES)
    public void runTextOalprDetectVideo() throws Exception {
        String pipelineName = addDefaultOalprPipeline();
        runSystemTest(pipelineName, "output/text/runTextOalprDetectVideo.json",
                      "/samples/text/lp-ferrari-texas.mp4",
                      "/samples/text/lp-v8-texas.mp4");
    }


    @Test(timeout = 10 * MINUTES)
    public void runDerivativeMediaTextDetectPdf() throws Exception {
        var algorithmProperties = Map.of(
                "EAST", Map.of(
                        "QUALITY_SELECTION_PROPERTY", "CONFIDENCE",
                        "QUALITY_SELECTION_THRESHOLD", "0.2",
                        "TEMPORARY_PADDING_X", "1.0",
                        "TEMPORARY_PADDING_Y", "1.0",
                        "FINAL_PADDING", "0.5"
                ),
                "TESSERACTOCR", Map.of(
                        "ENABLE_OSD_AUTOMATION", "false",
                        "TESSERACT_LANGUAGE", "eng"
                )
        );
        runSystemTest("TIKA IMAGE DETECTION WITH DERIVATIVE MEDIA TESSERACT OCR (WITH EAST REGIONS) AND KEYWORD TAGGING AND MARKUP PIPELINE",
                "output/derivative-media/runDerivativeMediaTextDetectPdf.json",
                algorithmProperties,
                Collections.emptyMap(),
                "/samples/derivative-media/text-embedded-and-images.pdf");
    }


    @Test(timeout = 5 * MINUTES)
    public void runJsonPathTest() throws IOException, URISyntaxException {
        var mediaUrl = getClass().getResource("/samples/text/test-media-selectors.json");
        runJsonPathTest(mediaUrl.toURI());
    }


    @Test(timeout = 5 * MINUTES)
    public void runJsonPathWithPercentEncodedDataUri() throws IOException {
        // mediaUri is the percent encoded version of samples/text/test-media-selectors.json
        var mediaUri
                = "data:application/json;,%7B%22otherStuffKey%22%3A%5B%22other%20stuff%20value%22"
                + "%5D%2C%22spanishMessages%22%3A%5B%7B%22to%22%3A%22spanish%20recipient%201%22%2"
                + "C%22from%22%3A%22spanish%20sender%201%22%2C%22content%22%3A%22%C2%BFHola%2C%20"
                + "c%C3%B3mo%20est%C3%A1s%3F%22%7D%2C%7B%22to%22%3A%22spanish%20recipient%202%22%"
                + "2C%22from%22%3A%22spanish%20sender%202%22%2C%22content%22%3A%22%C2%BFDonde%20e"
                + "st%C3%A1%20el%20aeropuerto%3F%22%7D%5D%2C%22chineseMessages%22%3A%5B%7B%22to%2"
                + "2%3A%22chinese%20recipient%201%22%2C%22from%22%3A%22chinese%20sender%201%22%2C"
                + "%22content%22%3A%22%E7%8E%B0%E5%9C%A8%E6%98%AF%E5%87%A0%E5%A5%8C%EF%BC%9F%22%7"
                + "D%2C%7B%22to%22%3A%22chinese%20recipient%202%22%2C%22from%22%3A%22chinese%20se"
                + "nder%202%22%2C%22content%22%3A%22%E4%BD%A0%E5%8F%AB%E4%BB%80%E4%B9%88%E5%90%8D"
                + "%E5%AD%97%EF%BC%9F%22%7D%2C%7B%22to%22%3A%22chinese%20recipient%203%22%2C%22fr"
                + "om%22%3A%22chinese%20sender%203%22%2C%22content%22%3A%22%E4%BD%A0%E5%9C%A8%E5%"
                + "93%AA%E9%87%8C%EF%BC%9F%22%7D%5D%7D";
        runJsonPathTest(URI.create(mediaUri));
    }


    public void runJsonPathTest(URI mediaUri) throws IOException {
        var pipelineName = "TEST JSON PATH";
        addPipeline(
                pipelineName,
                "FASTTEXT LANGUAGE ID TEXT FILE TASK",
                "ARGOS TRANSLATION (WITH FF REGION AND NO TASK MERGING) TASK",
                "KEYWORD TAGGING (WITH FF REGION) TASK");

        var selector1 = new JobCreationMediaSelector(
                "$.spanishMessages.*.content",
                MediaSelectorType.JSON_PATH,
                Map.of(),
                "TRANSLATION");
        var selector2 = new JobCreationMediaSelector(
                "$.chineseMessages.*.content",
                MediaSelectorType.JSON_PATH,
                Map.of(),
                "TRANSLATION");

        var media = new JobCreationMediaData(
                new MediaUri(mediaUri),
                Map.of(),
                Map.of(),
                List.of(),
                List.of(),
                List.of(selector1, selector2),
                Optional.of("ARGOS TRANSLATION (WITH FF REGION AND NO TASK MERGING) ACTION"));

        var outputObject = runSystemTest(pipelineName, "output/text/runJsonPathTest.json", media);

        var actualMediaSelectorsUri = URI.create(
                outputObject.getMedia().first().getMediaSelectorsOutputUri());
        var actualMediaSelectorsOut = objectMapper.readTree(actualMediaSelectorsUri.toURL());

        var expectedMediaSelectorsUrl = getClass()
                .getResource("/output/text/runJsonPathTestMediaSelectorsOut.json");
        var expectedMediaSelectorsOut = objectMapper.readTree(expectedMediaSelectorsUrl);
        assertEquals(expectedMediaSelectorsOut, actualMediaSelectorsOut);
    }
}
