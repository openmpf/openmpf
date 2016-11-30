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
import org.mitre.mpf.interop.JsonMediaInputObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestSystemStress extends TestSystem {

    private static final Logger log = LoggerFactory.getLogger(TestSystemStress.class);

//passed at 1min 47sec
    @Test(timeout = 15*MINUTES)
    public void runMotionMogDetectVideo() throws Exception {
        testCtr++;
        log.info("Beginning test #{} runMotionMogDetectVideo()", testCtr);
        List<JsonMediaInputObject> media = new LinkedList<>();

        // for testing on local VM only
//        mediaPaths.add(ioUtils.findFile("/samples/face/new_face_video.avi").toString());

        // for testing on Jenkins
        media.add(new JsonMediaInputObject(ioUtils.findFile("/mpfdata/datasets/ChicagoMarathon/2.mp4").toString())); // 220MG, 2 mins
        media.add(new JsonMediaInputObject(ioUtils.findFile("/mpfdata/datasets/ChicagoMarathon/6.mp4").toString())); // 341MG, 3 mins
        media.add(new JsonMediaInputObject(ioUtils.findFile("/mpfdata/datasets/systemTests/stress/motion/malaysia-scaled.mp4").toString())); // 13MG, 1 min
        // rejects
        //        mediaPaths.add(ioUtils.findFile("/mpfdata/datasets/systemTests/stress/face/092515_VPOTUS_HD.mp4").toString());  // 1.25G

        long jobId = runPipelineOnMedia("DEFAULT_EXTRACTION_MOTION_MOG_PIPELINE", media, propertiesUtil.isOutputObjectsEnabled(), propertiesUtil.getJmsPriority());
        URI actualOutputPath = propertiesUtil.createDetectionOutputObjectFile(jobId).toURI();
        checkOutput(actualOutputPath, media.size());
        log.info("Finished test runMotionMogDetectVideo()");
    }

// passed at 2hr 35min
    @Test(timeout = 170*MINUTES)
    public void runSpeechSphinxDetectAudio() throws Exception {
        testCtr++;
        log.info("Beginning test #{} runSpeechSphinxDetectAudio()", testCtr);

        // 28MG
        List<JsonMediaInputObject> media = toMediaObjectList(ioUtils.findFile("/samples/speech/obamastateoftheunion2015.mp3"));
        long jobId = runPipelineOnMedia("DEFAULT_EXTRACTION_SPEECH_SPHINX_PIPELINE", media, propertiesUtil.isOutputObjectsEnabled(), propertiesUtil.getJmsPriority());
        URI actualOutputPath = propertiesUtil.createDetectionOutputObjectFile(jobId).toURI();
        checkOutput(actualOutputPath, 1);
        log.info("Finished test runSpeechSphinxDetectAudio()");
    }

}
