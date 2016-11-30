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
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mitre.mpf.interop.JsonMediaInputObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestSystemStress3 extends TestSystem {

    protected static final Logger log = LoggerFactory.getLogger(TestSystemStress3.class);

    private int manyJobsNumFilesProcessed = 0;

    /**
     * This test intentionally runs one file per job
     */
    @Test(timeout = 180*MINUTES)
    public void runFaceOcvDetectImageManyJobs() throws Exception {
        testCtr++;
        log.info("Beginning test #{} runFaceOcvDetectImageManyJobs()", testCtr);
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

        Assert.assertEquals("Number of files to process={} doesn't match actual number of jobs run={} (one job/file)",
                files.size(), manyJobsNumFilesProcessed);
        log.info("Successfully ran {} jobs for {} files, one file per job, without a hiccup",
                manyJobsNumFilesProcessed, files.size());
        log.info("Finished test runFaceOcvDetectImageManyJobs()");
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
                    List<JsonMediaInputObject> media = new LinkedList<>();
                    media.add(new JsonMediaInputObject(fQueue.take().getAbsoluteFile().toURI().toString()));
                    runPipelineOnMedia("DEFAULT_EXTRACTION_FACE_OCV_PIPELINE", media,
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
