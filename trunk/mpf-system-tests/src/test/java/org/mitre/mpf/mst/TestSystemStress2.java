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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mitre.mpf.interop.JsonMediaInputObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.util.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestSystemStress2 extends TestSystem {

    private static final Logger log = LoggerFactory.getLogger(TestSystemStress2.class);

// has worked in the past at 26 mins
    @Test(timeout = 60*MINUTES)
    public void runFaceOcvDetectImage() throws Exception {
        testCtr++;
        log.info("Beginning test #{} runFaceOcvDetectImage()", testCtr);
        List<JsonMediaInputObject> media = new LinkedList<>();
        IOFileFilter fileFilter = FileFilterUtils.and(FileFilterUtils.fileFileFilter(),
                FileFilterUtils.suffixFileFilter(".jpg"));

        // for testing on local VM only
//        Collection<File> files = FileUtils.listFiles(new File(getClass().getClassLoader().getResource("samples/face").getFile()),
//                fileFilter, null);

        // for testing on Jenkins
        // 10,000 jpgs
        Collection<File> files = FileUtils.listFiles(new File("/mpfdata/datasets/mugshots_10000"), fileFilter, null);
        int i = 0;
        for (File file : files) {
            media.add(new JsonMediaInputObject(file.getAbsoluteFile().toURI().toString()));
            i++;
        }
        long jobId = runPipelineOnMedia("DEFAULT_EXTRACTION_FACE_OCV_PIPELINE", media, propertiesUtil.isOutputObjectsEnabled(), propertiesUtil.getJmsPriority());
        URI actualOutputPath = propertiesUtil.createDetectionOutputObjectFile(jobId).toURI();
        checkOutput(actualOutputPath, i);
        log.info("Finished test runFaceOcvDetectImage()");
    }

//passed at 1hr 59min
    @Test(timeout = 135*MINUTES)
    public void runFaceOcvDetectVideo() throws Exception {
        testCtr++;
        log.info("Beginning test #{} runFaceOcvDetectVideo()", testCtr);
        List<JsonMediaInputObject> media = new LinkedList<>();

        // for testing on local VM only
//        mediaPaths.add(ioUtils.findFile("/samples/face/new_face_video.avi").toString());

        // for testing on Jenkins
        media.add(new JsonMediaInputObject(ioUtils.findFile("/mpfdata/datasets/ChicagoMarathon/2.mp4").toString())); // 220MG, 2 mins
        media.add(new JsonMediaInputObject(ioUtils.findFile("/mpfdata/datasets/ChicagoMarathon/6.mp4").toString())); // 341MG, 3 mins
        media.add(new JsonMediaInputObject(ioUtils.findFile("/mpfdata/datasets/4kSampleFiles/News_H264.mp4").toString())); // 42MG, 10 secs
        // rejects:
        //        mediaPaths.add(ioUtils.findFile("/mpfdata/datasets/systemTests/stress/face/092515_VPOTUS_HD.mp4").toString();  // 1.25G, times out at 5 hours

        long jobId = runPipelineOnMedia("DEFAULT_EXTRACTION_FACE_OCV_PIPELINE", media, propertiesUtil.isOutputObjectsEnabled(), propertiesUtil.getJmsPriority());
        URI actualOutputPath = propertiesUtil.createDetectionOutputObjectFile(jobId).toURI();
        checkOutput(actualOutputPath, media.size());
        log.info("Finished test runFaceOcvDetectVideo()");
    }


// passed but at 3hr 51min
    @Test(timeout = 255*MINUTES)
    public void runPersonOcvDetectVideo() throws Exception {
        testCtr++;
        log.info("Beginning test #{} runPersonOcvDetectVideo()", testCtr);
        List<JsonMediaInputObject> media = new LinkedList<>();

        // for testing on local VM only
//        mediaPaths.add(ioUtils.findFile("/samples/person/video_02.mp4").toString());

        // for testing on Jenkins
        media.add(new JsonMediaInputObject(ioUtils.findFile("/mpfdata/datasets/ChicagoMarathon/2.mp4").toString())); // 220MG, 2 mins
        media.add(new JsonMediaInputObject(ioUtils.findFile("/mpfdata/datasets/ChicagoMarathon/6.mp4").toString())); // 341MG, 3 mins
        media.add(new JsonMediaInputObject(ioUtils.findFile("/mpfdata/datasets/systemTests/stress/motion/malaysia-scaled.mp4").toString())); // 13MG, 1 min

        long jobId = runPipelineOnMedia("DEFAULT_EXTRACTION_PERSON_OCV_PIPELINE", media, propertiesUtil.isOutputObjectsEnabled(), propertiesUtil.getJmsPriority());
        URI actualOutputPath = propertiesUtil.createDetectionOutputObjectFile(jobId).toURI();
        checkOutput(actualOutputPath, media.size());
        log.info("Finished test runPersonOcvDetectVideo()");
    }


}
