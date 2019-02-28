/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mitre.mpf.interop.*;
import org.mitre.mpf.wfm.WfmProcessingException;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.junit.Assert.*;

/**
 *
 * NOTE: Please keep the tests in this class in alphabetical order.  While they will automatically run that way regardless
 * of the order in the source code, keeping them in that order helps when correlating jenkins-produced output, which is
 * by job number, with named output, e.g., .../share/output-objects/5/detection.json and motion/runMotionMogDetectVideo.json
 *
 * This class contains tests that were formerly in TestEndToEnd.  The main changes are 3 inputs vs. 2 inputs, inputs
 * are tailored to the type of detection at hand and there is output checking (comparing previously saved expected results
 * against actual results).  Because for some tests, the output as produced on Jenkins is different than the output
 * produced in a local VM, we included a way to run the tests without output checking.  Output checking is not done
 * by default; it is done when run on Jenkins via the addition of '-Pjenkins' in the maven properties for the run; it
 * can be done locally if desired by adding '-Djenkins=true' to the command line (if running via command line) or by
 * adding the same to the Run Configuration, if running via IntelliJ.  To verify if output checking is running or not: if
 * you see 'Deserializing ...' in the console or log output, it IS running; if you don't see it, it IS NOT running.
 *
 * If output checking fails, it can be hard to identify where or why.  To better see where the failure was, uncomment
 * this line in logback-test.xml:
 *      <!-- <logger name="org.mitre.mpf.mst" level="DEBUG"/> -->
 *
 * If the structure of the output changes or if an algorithm changes, output checking will undoubtedly fail.  Once it is
 * confirmed that the failure is expected, the outputs should just be regenerated and committed to the repository for
 * future checking. Output checking is designed to catch unintentional or erroneous changes. There are two scripts in the
 * bin directory to help 'ease the pain' of having to do redo expected output files. Note that if tests are added or deleted,
 * the correspondence between output file and expected results file may change, so that should be verified (or just use
 * the scripts to cut and paste the desired command).
 */

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestSystemOnDiff extends TestSystemWithDefaultConfig {

	@Test(timeout = 5 * MINUTES)
	public void runFaceOcvDetectImage() throws Exception {
		runSystemTest("OCV FACE DETECTION PIPELINE", "output/face/runFaceOcvDetectImage.json",
		              "/samples/face/meds-aa-S001-01.jpg",
		              "/samples/face/meds-aa-S029-01.jpg",
		              "/samples/face/meds-af-S419-01.jpg");
	}


    @Test(timeout = 5 * MINUTES)
    public void runFaceOcvDetectImageWithAutoOrientation() throws Exception {
        runSystemTest("OCV FACE DETECTION (WITH AUTO-ORIENTATION) PIPELINE",
                "output/face/runFaceOcvDetectImageWithAutoOrientation.json",
                "/samples/face/meds-aa-S001-01-exif-rotation.jpg");
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


    @Test
    public void runOcvFaceWithUseKeyFrames() {
		String baseName = "TEST OCV FACE WITH USE_KEY_FRAMES";
		String actionName = baseName + " ACTION";
		addAction(actionName, "FACECV", ImmutableMap.of("USE_KEY_FRAMES", "true"));

	    String taskName = baseName + " TASK";
	    addTask(taskName, actionName);

	    String pipelineName = baseName + " PIPELINE";
	    addPipeline(pipelineName, taskName);

	    List<JsonMediaInputObject> media = toMediaObjectList(ioUtils.findFile("/samples/face/video_01.mp4"));

	    long jobId = runPipelineOnMedia(pipelineName, media);
	    JsonOutputObject outputObject = getJobOutputObject(jobId);

	    assertEquals(1, outputObject.getMedia().size());
	    JsonMediaOutputObject outputMedia = outputObject.getMedia().first();

	    SortedSet<JsonActionOutputObject> actionOutputObjects = outputMedia.getTypes().get("FACE");
	    assertNotNull("Output object did not contain expected detection type: FACE", actionOutputObjects);

	    List<JsonDetectionOutputObject> detections = actionOutputObjects.stream()
			    .flatMap(outputObj -> outputObj.getTracks().stream())
			    .flatMap(track -> track.getDetections().stream())
			    .collect(toList());

	    assertFalse(detections.isEmpty());

	    Set<Integer> keyFrames = ImmutableSet.of(0, 30, 60);

	    assertTrue("Found detection in non-keyframe", detections.stream()
			               .allMatch(o -> keyFrames.contains(o.getOffsetFrame())));
    }


	@Test(timeout = 15 * MINUTES)
	public void runDarknetDetectVideo() throws Exception {
		runSystemTest("TINY YOLO OBJECT DETECTION PIPELINE",
		              "output/object/runDarknetDetectVideo.json",
		              "/samples/face/video_01.mp4");
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
	public void runMogThenOcvPersonFeedForwardRegionTest() {
		String actionTaskName = "TEST OCV PERSON WITH FEED FORWARD SUPERSET REGION";

		String actionName = actionTaskName + " ACTION";
		addAction(actionName, "PERSONCV",
		          ImmutableMap.of("FEED_FORWARD_TYPE", "SUPERSET_REGION"));

		String taskName = actionTaskName + " TASK";
		addTask(taskName, actionName);

		String pipelineName = "MOG FEED SUPERSET REGION TO OCV PERSON PIPELINE";
		addPipeline(pipelineName, "MOG MOTION DETECTION (WITH TRACKING) TASK", taskName);

		int firstMotionFrame = 31; // The first 30 frames of the video are identical so there shouldn't be motion.
		int maxXMotion = 320 / 2; // Video is 320 x 300 and only the person on the left side of the frame moves.

		runFeedForwardRegionTest(pipelineName, "/samples/person/ff-region-motion-person.avi",
		                         "PERSON", firstMotionFrame, maxXMotion);
	}

	private static final Map<String, String> TINY_YOLO_CONFIG = ImmutableMap.of(
			"MODEL_NAME", "tiny yolo");


	private static Map<String, String> getTinyYoloConfig(Map<String, String> otherProperties) {
		Map<String, String> result = new HashMap<>(otherProperties);
		result.putAll(TINY_YOLO_CONFIG);
		return result;
	}


	@Test(timeout = 5 * MINUTES)
	public void runMogThenDarknetFeedForwardRegionTest() {
		String actionTaskName = "TEST DARKNET WITH FEED FORWARD SUPERSET REGION";

		String actionName = actionTaskName + " ACTION";
		addAction(actionName, "DARKNET",
		          getTinyYoloConfig(ImmutableMap.of("FEED_FORWARD_TYPE", "SUPERSET_REGION")));

		String taskName = actionTaskName + " TASK";
		addTask(taskName, actionName);

		String pipelineName = "MOG FEED SUPERSET REGION TO DARKNET PIPELINE";
		addPipeline(pipelineName, "MOG MOTION DETECTION (WITH TRACKING) TASK", taskName);

		int firstMotionFrame = 31; // The first 30 frames of the video are identical so there shouldn't be motion.
		int maxXMotion = 320 / 2; // Video is 320 x 300 and only the person on the left side of the frame moves.

		runFeedForwardRegionTest(pipelineName, "/samples/person/ff-region-motion-person.avi",
		                         "CLASS", firstMotionFrame, maxXMotion);
	}


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
	public void runMogThenCaffeFeedForwardSupersetRegionTest() {
		String actionTaskName = "TEST CAFFE WITH FEED FORWARD SUPERSET REGION";

		String actionName = actionTaskName + " ACTION";
		addAction(actionName, "CAFFE",
		          ImmutableMap.of("FEED_FORWARD_TYPE", "SUPERSET_REGION"));

		String taskName = actionTaskName + " TASK";
		addTask(taskName, actionName);

		String pipelineName = "MOG FEED SUPERSET REGION TO CAFFE PIPELINE";
		addPipeline(pipelineName, "MOG MOTION DETECTION (WITH TRACKING) TASK", taskName);

		int firstMotionFrame = 31; // The first 30 frames of the video are identical so there shouldn't be motion.
		int maxXMotion = 680 / 2; // Video is 680 x 320 and only the object on the left side of the frame moves.

		runFeedForwardRegionTest(pipelineName, "/samples/object/ff-region-object-motion.avi",
		                         "CLASS", firstMotionFrame, maxXMotion);
	}


	@Test(timeout = 5 * MINUTES)
	public void runMogThenCaffeFeedForwardExactRegionTest() {
		String actionTaskName = "TEST CAFFE WITH FEED FORWARD EXACT REGION";

		String actionName = actionTaskName + " ACTION";
		addAction(actionName, "CAFFE",
		          ImmutableMap.of("FEED_FORWARD_TYPE", "REGION"));

		String taskName = actionTaskName + " TASK";
		addTask(taskName, actionName);

		String pipelineName = "MOG FEED EXACT REGION TO CAFFE PIPELINE";
		addPipeline(pipelineName, "MOG MOTION DETECTION (WITH TRACKING) TASK", taskName);


		List<JsonMediaInputObject> media = toMediaObjectList(
				ioUtils.findFile("/samples/object/ff-exact-region-object-motion.avi"));

		long jobId = runPipelineOnMedia(pipelineName, media);
		JsonOutputObject outputObject = getJobOutputObject(jobId);

		assertEquals(1, outputObject.getMedia().size());
		JsonMediaOutputObject outputMedia = outputObject.getMedia().first();

		SortedSet<JsonActionOutputObject> caffeOutputObjects = outputMedia.getTypes().get("CLASS");
		assertNotNull(caffeOutputObjects);

		SortedSet<JsonActionOutputObject> motionOutputObjects = outputMedia.getTypes().get("MOTION");
		assertNotNull(motionOutputObjects);

		List<JsonTrackOutputObject> caffeTracks = caffeOutputObjects.stream()
				.flatMap(o -> o.getTracks().stream())
				.collect(toList());

		List<JsonTrackOutputObject> motionTracks = motionOutputObjects.stream()
				.flatMap(o -> o.getTracks().stream())
				.collect(toList());

		assertTracksMatch(caffeTracks, motionTracks);


		List<JsonDetectionOutputObject> caffeDetections = caffeTracks.stream()
				.flatMap(t -> t.getDetections().stream())
				.collect(toList());


		assertTrue(caffeDetections.stream()
				           .allMatch(d -> d.getOffsetFrame() >= 31));

		assertTrue(caffeDetections.stream()
				           .allMatch(d -> d.getX() + d.getWidth() <= 180));


		Set<String> detectedObjects = caffeDetections.stream()
				.map(d -> d.getDetectionProperties().get("CLASSIFICATION"))
				// Not sure why Caffe detects an envelope,
				// but the important thing is that Caffe doesn't find Granny Smith.
				.filter(c -> !c.equalsIgnoreCase("envelope"))
				.collect(toSet());

		assertEquals(1, detectedObjects.size());
		assertTrue(detectedObjects.contains("digital clock"));
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
			assertEquals(d1.getOffsetFrame(), d2.getOffsetFrame());
			assertEquals(d1.getX(), d2.getX());
			assertEquals(d1.getY(), d2.getY());
			assertEquals(d1.getWidth(), d2.getWidth());
			assertEquals(d1.getHeight(), d2.getHeight());
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
	public void runMogThenOcvPersonFeedForwardFullFrameTest() {
		String actionTaskName = "TEST OCV PERSON WITH FEED FORWARD FULL FRAME";

		String actionName = actionTaskName + " ACTION";
		addAction(actionName, "PERSONCV",
		          ImmutableMap.of("FEED_FORWARD_TYPE", "FRAME"));

		String taskName = actionTaskName + " TASK";
		addTask(taskName, actionName);

		String pipelineName = "MOG FEED FULL FRAME TO OCV PERSON PIPELINE";
		addPipeline(pipelineName, "MOG MOTION DETECTION (WITH TRACKING) TASK", taskName);

		int firstMotionFrame = 31; // The first 30 frames of the video are identical so there shouldn't be motion.
		int maxXLeftDetection = 320 / 2;  // Video is 320x300 and there is a person on the left side of the frame.
		int minXRightDetection = 320 / 2;  // Video is 320x300 and there is a person on the right side of the frame.
		runFeedForwardFullFrameTest(pipelineName, "/samples/person/ff-region-motion-person.avi",
		                            "PERSON", firstMotionFrame, maxXLeftDetection, minXRightDetection);
	}


	@Test(timeout = 5 * MINUTES)
	public void runMogThenDarknetFeedForwardFullFrameTest() {
		String actionTaskName = "TEST DARKNET WITH FEED FORWARD FULL FRAME";

		String actionName = actionTaskName + " ACTION";
		addAction(actionName, "DARKNET",
		          getTinyYoloConfig(ImmutableMap.of("FEED_FORWARD_TYPE", "FRAME")));

		String taskName = actionTaskName + " TASK";
		addTask(taskName, actionName);

		String pipelineName = "MOG FEED FULL FRAME TO DARKNET PIPELINE";
		addPipeline(pipelineName, "MOG MOTION DETECTION (WITH TRACKING) TASK", taskName);

		int firstMotionFrame = 31; // The first 30 frames of the video are identical so there shouldn't be motion.
		int maxXLeftDetection = 320 / 2;  // Video is 320x300 and there is a person on the left side of the frame.
		int minXRightDetection = 320 / 2;  // Video is 320x300 and there is a person on the right side of the frame.
		runFeedForwardFullFrameTest(pipelineName, "/samples/person/ff-region-motion-person.avi",
		                            "CLASS", firstMotionFrame, maxXLeftDetection, minXRightDetection);
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
	public void runMogThenCaffeFeedForwardFullFrameTest() {
		String actionTaskName = "TEST CAFFE WITH FEED FORWARD FULL FRAME";

		String actionName = actionTaskName + " ACTION";
		addAction(actionName, "CAFFE",
		          ImmutableMap.of("FEED_FORWARD_TYPE", "FRAME"));

		String taskName = actionTaskName + " TASK";
		addTask(taskName, actionName);

		String pipelineName = "MOG FEED FULL FRAME TO CAFFE PIPELINE";
		addPipeline(pipelineName, "MOG MOTION DETECTION (WITH TRACKING) TASK", taskName);

		int firstMotionFrame = 31; // The first 30 frames of the video are identical so there shouldn't be motion.
		int maxXLeftDetection = 680;  // Video is 680x320 and Caffe reports entire frame.
		int minXRightDetection = 0;  //  Video is 680x320 and Caffe reports entire frame.
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


	private void runFeedForwardRegionTest(String pipelineName, String mediaPath, String detectionType,
	                                      int firstDetectionFrame, int maxXDetection) {

		List<JsonDetectionOutputObject> detections = runFeedForwardTest(pipelineName, mediaPath, detectionType,
		                                                                firstDetectionFrame);

		assertTrue("Found detection in stage 2 in region without a detection from stage 1.",
		           detections.stream()
				           .allMatch(d -> d.getX() + d.getWidth() < maxXDetection));
	}


	private void runFeedForwardFullFrameTest(String pipelineName, String mediaPath, String detectionType,
	                                         int firstDetectionFrame, int maxXLeftDetection, int minXRightDetection) {

		List<JsonDetectionOutputObject> detections = runFeedForwardTest(pipelineName, mediaPath, detectionType,
		                                                                firstDetectionFrame);
		boolean foundLeftDetection = detections.stream()
				.anyMatch(d -> d.getX() + d.getWidth() <= maxXLeftDetection);

		assertTrue("Did not find expected detection on left side on frame.", foundLeftDetection);

		boolean foundRightDetection = detections.stream()
				.anyMatch(d -> d.getX() >= minXRightDetection);
		assertTrue("Did not find expected detection on right side on frame.", foundRightDetection);
	}


	private List<JsonDetectionOutputObject> runFeedForwardTest(
			String pipelineName, String mediaPath, String detectionType, int firstDetectionFrame) {

		List<JsonMediaInputObject> media = toMediaObjectList(ioUtils.findFile(mediaPath));

		long jobId = runPipelineOnMedia(pipelineName, media);
		JsonOutputObject outputObject = getJobOutputObject(jobId);

		assertEquals(1, outputObject.getMedia().size());
		JsonMediaOutputObject outputMedia = outputObject.getMedia().first();

		SortedSet<JsonActionOutputObject> actionOutputObjects = outputMedia.getTypes().get(detectionType);
		assertNotNull("Output object did not contain expected detection type: " + detectionType,
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
        String multipleTaskName = "TEST MULTIPLE FACE DETECTION TASK";
        addTask(multipleTaskName, "OCV FACE DETECTION ACTION", "DLIB FACE DETECTION ACTION", "OCV PERSON DETECTION ACTION");

        String pipelineName = "TEST MULTIPLE FACE DETECTION PIPELINE";
        addPipeline(pipelineName, multipleTaskName);

        runSystemTest(pipelineName,
                "output/face/runMultipleDetectionAlgorithmsImage.json",
                "/samples/person/race.jpg",
                "/samples/person/homewood-bank-robbery.jpg");
    }

    @Test(timeout = 15 * MINUTES)
    public void runMultipleDetectionAlgorithmsVideo() throws Exception {
        String multipleTaskName = "TEST MULTIPLE FACE DETECTION TASK 2";
        addTask(multipleTaskName, "OCV FACE DETECTION ACTION", "DLIB FACE DETECTION ACTION", "OCV PERSON DETECTION ACTION");

        String pipelineName = "TEST MULTIPLE FACE DETECTION PIPELINE 2";
        addPipeline(pipelineName, multipleTaskName);

        runSystemTest(pipelineName,
                "output/face/runMultipleDetectionAlgorithmsVideo.json",
                "/samples/person/video_02.mp4");
    }

    @Test(timeout = 5 * MINUTES)
    public void runPersonOcvDetectImage() throws Exception {
        runSystemTest("OCV PERSON DETECTION PIPELINE", "output/person/runPersonOcvDetectImage.json",
                "/samples/face/person_cropped_2.png",
                "/samples/person/race.jpg",
                "/samples/person/homewood-bank-robbery.jpg");
    }

/*
        @Test(timeout = 15*MINUTES)
        public void runPersonOcvDetectVideo() throws Exception {
        runSystemTest("OCV PERSON DETECTION PIPELINE", "output/person/runPersonOcvDetectVideo.json",
                "/samples/person/video_02.mp4",
                "/samples/person/obama-basketball.mp4");
        }
*/

    @Test(timeout = 5 * MINUTES)
    public void runSpeechSphinxDetectAudio() throws Exception {
        runSystemTest("SPHINX SPEECH DETECTION PIPELINE", "output/speech/runSpeechSphinxDetectAudio.json",
                "/samples/speech/green.wav",
                "/samples/speech/left.wav",
                "/samples/speech/10001-90210-01803.wav");
    }

    @Test(timeout = 5 * MINUTES)
    public void runSpeechSphinxDetectVideo() throws Exception {
        runSystemTest("SPHINX SPEECH DETECTION PIPELINE", "output/speech/runSpeechSphinxDetectVideo.json",
                "/samples/speech/green.mov",
                "/samples/speech/left.avi",
                "/samples/speech/10001-90210-01803.mp4"
        );
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

}
