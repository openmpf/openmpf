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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mitre.mpf.rest.api.JobCreationMediaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestSystemStress extends TestSystemWithDefaultConfig {

    private static final Logger log = LoggerFactory.getLogger(TestSystemStress.class);

    //passed at 1min 47sec
    @Test(timeout = 15*MINUTES)
    public void runMotionMogDetectVideo() throws Exception {
        List<JobCreationMediaData> media = new LinkedList<>();

        // for testing on local VM only
        // mediaPaths.add(ioUtils.findFile("/samples/face/new_face_video.avi").toString());

        // for testing on Jenkins
        media.add(new JobCreationMediaData(ioUtils.findFile("/mpfdata/datasets/ChicagoMarathon/2.mp4").toString())); // 220MG, 2 mins
        media.add(new JobCreationMediaData(ioUtils.findFile("/mpfdata/datasets/ChicagoMarathon/6.mp4").toString())); // 341MG, 3 mins
        media.add(new JobCreationMediaData(ioUtils.findFile("/mpfdata/datasets/systemTests/stress/motion/malaysia-scaled.mp4").toString())); // 13MG, 1 min
        // rejects
        //        mediaPaths.add(ioUtils.findFile("/mpfdata/datasets/systemTests/stress/face/092515_VPOTUS_HD.mp4").toString());  // 1.25G

        String pipelineName = addDefaultMotionMogPipeline();
        long jobId = runPipelineOnMedia(pipelineName, media, Collections.emptyMap(), propertiesUtil.isOutputObjectsEnabled(), propertiesUtil.getJmsPriority());
        URI actualOutputPath = propertiesUtil.createDetectionOutputObjectFile(jobId).toUri();
        checkOutput(actualOutputPath, media.size());
    }

    // passed at 2hr 35min
    @Test(timeout = 170*MINUTES)
    public void runSpeechSphinxDetectAudio() throws Exception {
        // 28MG
        List<JobCreationMediaData> media = toMediaObjectList(ioUtils.findFile("/samples/speech/obamastateoftheunion2015.mp3"));
        long jobId = runPipelineOnMedia("SPHINX SPEECH DETECTION PIPELINE", media, Collections.emptyMap(), propertiesUtil.isOutputObjectsEnabled(), propertiesUtil.getJmsPriority());
        URI actualOutputPath = propertiesUtil.createDetectionOutputObjectFile(jobId).toUri();
        checkOutput(actualOutputPath, 1);
    }

    // has worked in the past at 26 mins
    @Test(timeout = 60*MINUTES)
    public void runFaceOcvDetectImage() throws Exception {
        List<JobCreationMediaData> media = new LinkedList<>();
        IOFileFilter fileFilter = FileFilterUtils.and(FileFilterUtils.fileFileFilter(),
                FileFilterUtils.suffixFileFilter(".jpg"));

        // for testing on local VM only
        // Collection<File> files = FileUtils.listFiles(new File(getClass().getClassLoader().getResource("samples/face").getFile()), fileFilter, null);

        // for testing on Jenkins
        // 10,000 jpgs
        Collection<File> files = FileUtils.listFiles(new File("/mpfdata/datasets/mugshots_10000"), fileFilter, null);
        int i = 0;
        for (File file : files) {
            media.add(new JobCreationMediaData(file.getAbsoluteFile().toPath().toUri().toString()));
            i++;
        }
        long jobId = runPipelineOnMedia("OCV FACE DETECTION PIPELINE", media, Collections.emptyMap(),
                propertiesUtil.isOutputObjectsEnabled(), propertiesUtil.getJmsPriority());
        URI actualOutputPath = propertiesUtil.createDetectionOutputObjectFile(jobId).toUri();
        checkOutput(actualOutputPath, i);
    }

    // passed at 1hr 59min
    @Test(timeout = 135*MINUTES)
    public void runFaceOcvDetectVideo() throws Exception {
        List<JobCreationMediaData> media = new LinkedList<>();

        // for testing on local VM only
        // mediaPaths.add(ioUtils.findFile("/samples/face/new_face_video.avi").toString());

        // for testing on Jenkins
        media.add(new JobCreationMediaData(ioUtils.findFile("/mpfdata/datasets/ChicagoMarathon/2.mp4").toString())); // 220MG, 2 mins
        media.add(new JobCreationMediaData(ioUtils.findFile("/mpfdata/datasets/ChicagoMarathon/6.mp4").toString())); // 341MG, 3 mins
        media.add(new JobCreationMediaData(ioUtils.findFile("/mpfdata/datasets/4kSampleFiles/News_H264.mp4").toString())); // 42MG, 10 secs
        // rejects:
        //        mediaPaths.add(ioUtils.findFile("/mpfdata/datasets/systemTests/stress/face/092515_VPOTUS_HD.mp4").toString();  // 1.25G, times out at 5 hours

        long jobId = runPipelineOnMedia("OCV FACE DETECTION PIPELINE", media, Collections.emptyMap(),
                propertiesUtil.isOutputObjectsEnabled(), propertiesUtil.getJmsPriority());
        URI actualOutputPath = propertiesUtil.createDetectionOutputObjectFile(jobId).toUri();
        checkOutput(actualOutputPath, media.size());
    }


    // passed but at 3hr 51min
    @Test(timeout = 255*MINUTES)
    public void runPersonOcvDetectVideo() throws Exception {
        List<JobCreationMediaData> media = new LinkedList<>();

        // for testing on local VM only
        // mediaPaths.add(ioUtils.findFile("/samples/person/video_02.mp4").toString());

        // for testing on Jenkins
        media.add(new JobCreationMediaData(ioUtils.findFile("/mpfdata/datasets/ChicagoMarathon/2.mp4").toString())); // 220MG, 2 mins
        media.add(new JobCreationMediaData(ioUtils.findFile("/mpfdata/datasets/ChicagoMarathon/6.mp4").toString())); // 341MG, 3 mins
        media.add(new JobCreationMediaData(ioUtils.findFile("/mpfdata/datasets/systemTests/stress/motion/malaysia-scaled.mp4").toString())); // 13MG, 1 min

        long jobId = runPipelineOnMedia("OCV PERSON DETECTION PIPELINE", media, Collections.emptyMap(),
                propertiesUtil.isOutputObjectsEnabled(), propertiesUtil.getJmsPriority());
        URI actualOutputPath = propertiesUtil.createDetectionOutputObjectFile(jobId).toUri();
        checkOutput(actualOutputPath, media.size());
    }


    private int manyJobsNumFilesProcessed = 0;

    // This test intentionally runs one file per job
    @Test(timeout = 180*MINUTES)
    public void runFaceOcvDetectImageManyJobs() throws Exception {
        IOFileFilter fileFilter = FileFilterUtils.and(FileFilterUtils.fileFileFilter(),
                FileFilterUtils.suffixFileFilter(".jpg"));

        int numExtractors = 6;  // number of extractors on Jenkins (* number of nodes, now 1)
//        int numExtractors = 2;  // number of extractors on local VM * 1 node

        // for testing on local VM only
//        Collection<File> files = FileUtils.listFiles(new File(getClass().getClassLoader().getResource("samples/face").getFile()),
//            fileFilter, null);

        // for testing on Jenkins
        // 10,000 jpgs
        Collection<File> files = FileUtils.listFiles(new File("/mpfdata/datasets/mugshots_10000"), fileFilter, null);

        BlockingQueue<File> fQueue = new ArrayBlockingQueue<File>(files.size());
        for (File file : files) {
            fQueue.put(file);
        }
        ExecutorService executor = Executors.newFixedThreadPool(numExtractors);
        JobRunner[] jobRunners = new JobRunner[numExtractors];
        for (int i = 0; i < numExtractors; i++) {
            jobRunners[i] = new JobRunner(fQueue);
            executor.submit(jobRunners[i]);
        }
        executor.shutdown();
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);

        Assert.assertEquals("Number of files to process doesn't match actual number of jobs run (one job/file):",
                files.size(), manyJobsNumFilesProcessed);
        log.info("Successfully ran {} jobs for {} files, one file per job.",
                manyJobsNumFilesProcessed, files.size());
    }

    class JobRunner implements Runnable {
        private final BlockingQueue<File> fQueue;

        public JobRunner(BlockingQueue fQueue) {
            this.fQueue = fQueue;
        }

        @Override
        public void run() {
            try {
                while (!fQueue.isEmpty()) {
                    List<JobCreationMediaData> media = new LinkedList<>();
                    media.add(new JobCreationMediaData(fQueue.take().getAbsoluteFile().toPath().toUri().toString()));
                    runPipelineOnMedia("OCV FACE DETECTION PIPELINE", media, Collections.emptyMap(),
                            propertiesUtil.isOutputObjectsEnabled(), propertiesUtil.getJmsPriority());
                    manyJobsNumFilesProcessed++;
                }
            } catch (InterruptedException ie) {
                log.error("Failed to finish test=runFaceOcvDetectImageManyJobs due to an interruption.", ie);
            } catch (Exception e) {
                log.error("Encountered an exception when running test=runFaceOcvDetectImageManyJobs", e);
            }
        }
    }
}
