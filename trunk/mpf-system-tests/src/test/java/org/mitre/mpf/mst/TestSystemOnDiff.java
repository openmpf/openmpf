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

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

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
public class TestSystemOnDiff extends TestSystem {

	@Test(timeout = 5*MINUTES)
	public void runFaceOcvDetectImage() throws Exception {
        runSystemTest("DEFAULT_EXTRACTION_FACE_OCV_PIPELINE", "output/face/runFaceOcvDetectImage.json",
                "/samples/face/meds-aa-S001-01.jpg",
                "/samples/face/meds-aa-S029-01.jpg",
                "/samples/face/meds-af-S419-01.jpg");
	}


    @Test(timeout = 15*MINUTES)
    public void runFaceOcvDetectVideo() throws Exception {
        runSystemTest("DEFAULT_EXTRACTION_FACE_OCV_PIPELINE", "output/face/runFaceOcvDetectVideo.json",
                    "/samples/face/new_face_video.avi",
                    "/samples/person/video_02.mp4",
                    "/samples/face/video_01.mp4");
    }


    @Test(timeout = 5*MINUTES)
    public void runFaceOcvDetectImageWithAutoOrientation() throws Exception {
        runSystemTest("AUTO_ORIENT_EXTRACTION_FACE_OCV_PIPELINE",
                "output/face/runFaceOcvDetectImageWithAutoOrientation.json",
                "/samples/face/meds-aa-S001-01-exif-rotation.jpg");
    }

    @Test(timeout = 5 * MINUTES)
    public void runFaceOcvDetectVideoWithRegionOfInterest() throws Exception {
        runSystemTest("ROI_EXTRACTION_FACE_OCV_PIPELINE",
                "output/face/runFaceOcvDetectVideoWithRegionOfInterest.json",
                "/samples/face/new_face_video.avi");

    }

    @Test(timeout = 5*MINUTES)
    public void runMotionMogDetectVideo() throws Exception {
        runSystemTest("DEFAULT_EXTRACTION_MOTION_MOG_PIPELINE", "output/motion/runMotionMogDetectVideo.json",
                "/samples/motion/five-second-marathon-clip.mkv",
                "/samples/person/video_02.mp4");
    }

    @Test(timeout = 5*MINUTES)
	public void runPersonOcvDetectImage() throws Exception {
        runSystemTest("DEFAULT_EXTRACTION_PERSON_OCV_PIPELINE", "output/person/runPersonOcvDetectImage.json",
                "/samples/face/person_cropped_2.png",
                "/samples/person/race.jpg",
                "/samples/person/homewood-bank-robbery.jpg");
	}
/*
	@Test(timeout = 15*MINUTES)
	public void runPersonOcvDetectVideo() throws Exception {
        runSystemTest("DEFAULT_EXTRACTION_PERSON_OCV_PIPELINE", "output/person/runPersonOcvDetectVideo.json",
                "/samples/person/video_02.mp4",
                "/samples/person/obama-basketball.mp4");
	}
*/
    @Test(timeout = 5*MINUTES)
    public void runSpeechSphinxDetectAudio() throws Exception {
        runSystemTest("DEFAULT_EXTRACTION_SPEECH_SPHINX_PIPELINE", "output/speech/runSpeechSphinxDetectAudio.json",
                "/samples/speech/green.wav",
                "/samples/speech/left.wav",
                "/samples/speech/10001-90210-01803.wav");
    }

    @Test(timeout = 5*MINUTES)
    public void runSpeechSphinxDetectVideo() throws Exception {
        runSystemTest("DEFAULT_EXTRACTION_SPEECH_SPHINX_PIPELINE", "output/speech/runSpeechSphinxDetectVideo.json",
                "/samples/speech/green.mov",
                "/samples/speech/left.avi",
                "/samples/speech/10001-90210-01803.mp4"
        );
    }

    @Test(timeout = 5*MINUTES)
    public void runTextOalprDetectImage() throws Exception {
        runSystemTest("DEFAULT_EXTRACTION_TEXT_OALPR_PIPELINE", "output/text/runTextOalprDetectImage.json",
                "/samples/text/lp-bmw.jpg",
                "/samples/text/lp-police-car.jpg",
                "/samples/text/lp-police-cars.jpg");
    }

    @Test(timeout = 10*MINUTES)
	public void runTextOalprDetectVideo() throws Exception {
        runSystemTest("DEFAULT_EXTRACTION_TEXT_OALPR_PIPELINE", "output/text/runTextOalprDetectVideo.json",
                "/samples/text/lp-ferrari-texas.mp4",
                "/samples/text/lp-v8-texas.mp4");
    }



}
