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

import com.google.common.base.Stopwatch;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.junit.*;
import org.junit.runners.MethodSorters;
import org.mitre.mpf.interop.JsonActionOutputObject;
import org.mitre.mpf.interop.JsonMediaOutputObject;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.interop.JsonTrackOutputObject;
import org.mitre.mpf.rest.api.JobCreationMediaData;
import org.mitre.mpf.rest.api.JobCreationRequest;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.businessrules.JobRequestService;
import org.mitre.mpf.wfm.enums.MarkupStatusType;
import org.mitre.mpf.wfm.event.JobProgress;
import org.mitre.mpf.wfm.service.pipeline.InvalidPipelineException;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestSystemNightly extends TestSystemWithDefaultConfig {
    private static final Logger log = LoggerFactory.getLogger(TestSystemNightly.class);

    @Autowired
    private JsonUtils jsonUtils;

    @Autowired
    private JobRequestService jobRequestService;

    @Autowired
    private JobProgress jobProgress;

    @Test(timeout = 20*MINUTES)
    public void runDetectMarkupMultipleMediaTypes() throws Exception {
        runSystemTest("OCV FACE DETECTION (WITH MARKUP) PIPELINE",
                "output/face/runDetectMarkupMultipleMediaTypes.json",
                "/samples/motion/five-second-marathon-clip.mkv",
                "/samples/person/video_02.mp4",
                "/samples/face/new_face_video.avi",
                "/samples/face/meds-aa-S001-01.jpg",
                "/samples/face/meds-aa-S029-01.jpg");
    }

    @Test(timeout = 5*MINUTES)
    public void runFaceCombinedDetectImage() throws Exception {
        // This tests the handling of more than one output with the same data type ("FACE").
        String taskName = "TEST OCVFACE COMBINED TASK";
        addTask(taskName, "OCV FACE DETECTION ACTION", "OCV FACE DETECTION (WITH AUTO-ORIENTATION) ACTION");

        String pipelineName = "TEST OCVFACE COMBINED PIPELINE";
        addPipeline(pipelineName, taskName);

        runSystemTest(pipelineName, "output/face/runFaceCombinedDetectImage.json",
                "/samples/face/meds-aa-S001-01.jpg",
                "/samples/face/meds-aa-S029-01.jpg",
                "/samples/face/meds-af-S419-01.jpg");
    }


    @Test(timeout = 6*MINUTES)
    public void runMogMotionPreprocessing() throws Exception {
        String actionName = "TEST PREPROCESSOR MOTION ACTION";
        addAction(actionName, "MOG", Collections.singletonMap("USE_PREPROCESSOR", "1"));

        String taskName = "TEST PREPROCESSING MOTION EXTRACTION MOTION MOG TASK";
        addTask(taskName, actionName);

        String pipelineName = "TEST PREPROCESSING MOTION EXTRACTION MOTION MOG TASK";
        addPipeline(pipelineName, taskName);

        runSystemTest(pipelineName,
                "output/motion/runMogMotionPreprocessing.json",
                "/samples/motion/five-second-marathon-clip.mkv",
                "/samples/person/video_02.mp4");
    }

    @Test(timeout = 5*MINUTES)
    public void runMogMotionPreprocessingFaceDetectionMarkup() throws Exception {
        runSystemTest("OCV FACE DETECTION (WITH MOG MOTION PREPROCESSOR AND MARKUP) PIPELINE",
                "output/motion/runMogMotionPreprocessingFaceDetectionMarkup.json",
                Map.of(),
                Map.of("SUPPRESS_TRACKS", "false"),
                "/samples/person/video_02.mp4");
    }

    @Test(timeout = 10*MINUTES)
    public void runMotionTracking1() throws Exception {
        List<JobCreationMediaData> media = toMediaObjectList(
                ioUtils.findFile("/samples/motion/five-second-marathon-clip.mkv"),
                ioUtils.findFile("/samples/person/video_02.mp4"));

        runPipelineOnMedia("MOG MOTION DETECTION (WITH TRACKING) PIPELINE", media);
    }

    @Test(timeout = 5*MINUTES)
    public void runMotionTracking2() throws Exception {
        runSystemTest("MOG MOTION DETECTION (WITH TRACKING) PIPELINE", "output/motion/runMotionTracking.json",
                "/samples/motion/STRUCK_Test_720p.mp4");
    }

    @Test(timeout = 4*MINUTES, expected = InvalidPipelineException.class)
    public void testBadPipeline() throws Exception {
        List<JobCreationMediaData> media = toMediaObjectList(ioUtils.findFile("/samples/face/meds-aa-S001-01.jpg"));
        var jobRequest = new JobCreationRequest(
                media,
                Map.of(),
                Map.of(),
                UUID.randomUUID().toString(),
                "X",
                null,
                true,
                4,
                null,
                null);
        jobRequestService.run(jobRequest).jobId();
    }

    @Test(timeout = 8*MINUTES)
    public void testEmptyMarkupRequest() throws Exception {
        List<JobCreationMediaData> media = toMediaObjectList(
                ioUtils.findFile("/samples/face/meds-aa-S001-01.jpg"),
                ioUtils.findFile("/samples/motion/ocv_motion_video.avi"));
        long jobId = runPipelineOnMedia("OCV TINY YOLO VEHICLE DETECTION (WITH MARKUP) PIPELINE", media);
        URI outputPath = propertiesUtil.createDetectionOutputObjectFile(jobId).toUri();
//        JsonOutputObject outputObject = objectMapper.readValue(Files.readAllBytes(Paths.get(outputPath)), JsonOutputObject.class);
        JsonOutputObject outputObject = jsonUtils.deserializeFromText(FileUtils.readFileToByteArray(new File(outputPath)), JsonOutputObject.class);
        for(JsonMediaOutputObject mediaOutputObject : outputObject.getMedia()) {
            // Check that the hashes of the source media are equal to the hashes of the markup media result. That is, check that the file was copied successfully.
            Assert.assertEquals(
                    DigestUtils.sha256Hex(FileUtils.readFileToByteArray(new File(URI.create(mediaOutputObject.getPath())))),
                    DigestUtils.sha256Hex(FileUtils.readFileToByteArray(new File(URI.create(mediaOutputObject.getMarkupResult().getPath())))));
        }
    }

    @Test(timeout = 5*MINUTES, expected = WfmProcessingException.class)
    public void testNonUri() throws Exception {
        var media = toMediaObjectList(URI.create("/not/a/file.txt"));
        runPipelineOnMedia("OCV FACE DETECTION PIPELINE", media);
    }

    @Ignore // TODO: fix me!
    @Test(timeout = 4*MINUTES)
    public void testTiffImageMarkup() throws Exception {
        List<JobCreationMediaData> media = toMediaObjectList(ioUtils.findFile("/samples/face/meds-aa-S001-01.tif"));
        long jobId = runPipelineOnMedia("OCV FACE DETECTION (WITH MARKUP) PIPELINE", media);
        URI outputPath = propertiesUtil.createDetectionOutputObjectFile(jobId).toUri();
//        JsonOutputObject outputObject = objectMapper.readValue(Files.readAllBytes(Paths.get(outputPath)), JsonOutputObject.class)
        JsonOutputObject outputObject = jsonUtils.deserializeFromText(FileUtils.readFileToByteArray(new File(outputPath)), JsonOutputObject.class);
        for(JsonMediaOutputObject mediaOutputObject : outputObject.getMedia()) {
            // Check that the hashes of the source media are equal to the hashes of the markup media result. That is, check that the file was copied successfully.
            Assert.assertNotEquals(
                    DigestUtils.sha256Hex(FileUtils.readFileToByteArray(new File(URI.create(mediaOutputObject.getPath())))),
                    DigestUtils.sha256Hex(FileUtils.readFileToByteArray(new File(URI.create(mediaOutputObject.getMarkupResult().getPath())))));
            Assert.assertEquals(MarkupStatusType.COMPLETE.toString(), mediaOutputObject.getMarkupResult().getStatus());
        }
    }

// This test can also be verified manually (and visually) by running the comparable default pipeline with markup, and a comparable
// custom pipeline with min face size = 100 and markup. The chokepoint video gets 16 faces detected with the default pipeline
// and 14 with the custom pipeline
    @Test(timeout = 20*MINUTES)
    public void runFaceOcvCustomDetectVideo() throws Exception {
        // set property MIN_FACE_SIZE=100 on the custom action "TEST X OCV FACE MIN FACE SIZE 100" to run the custom pipeline standard nightly test.
        // Note that this statement can be left as is when the default output object is created (i.e. when the default pipeline of
        // "OCV FACE DETECTION PIPELINE" is specified).  It doesn't have to be commented out
        // because that pipeline doen't use the custom action TEST X OCV FACE MIN FACE SIZE 100", it will
        // be using instead whatever task&action is defined for "OCV FACE DETECTION PIPELINE" pipeline in the component descriptor file
        String actionName = "TEST X OCV FACE MIN FACE SIZE 100";
        addAction(actionName, "FACECV", Collections.singletonMap("MIN_FACE_SIZE", "100"));

        String taskName = "TEST OCV FACE MIN FACE SIZE 100 TASK";
        addTask(taskName, actionName);

        String pipelineName = "TEST OCV FACE MIN FACE SIZE 100 PIPELINE";
        addPipeline(pipelineName, taskName);

        List<JobCreationMediaData> media = toMediaObjectList(ioUtils.findFile("/samples/person/video_02.mp4"));
        long jobId = runPipelineOnMedia(pipelineName, media); // use this line to generate output using the custom pipeline
//      long jobId = runPipelineOnMedia("OCV FACE DETECTION PIPELINE", media); // use this line to generate default output
        // Compare the normal Ocv pipeline output with this output.  The custom pipeline output should have fewer track sets
        // on this video (requires a video with some small faces)
        URI defaultOutputPath = (getClass().getClassLoader().getResource("output/face/runFaceOcvCustomDetectVideo-defaultCompare.json")).toURI();

        JsonOutputObject defaultOutput = objectMapper.readValue(Files.readAllBytes(Paths.get(defaultOutputPath)), JsonOutputObject.class);
        JsonOutputObject customOutput = getJobOutputObject(jobId);

        Set<JsonMediaOutputObject> defMedias = defaultOutput.getMedia();
        Set<JsonMediaOutputObject> custMedias = customOutput.getMedia();

        // the number of media in the custom media group should be the same as the number in the default media group
        Assert.assertEquals(String.format("default MediaGroup size=%s doesn't match custom MediaGroup size=%s",
                defMedias.size(), custMedias.size()), defMedias.size(), custMedias.size());

        Iterator<JsonMediaOutputObject> defIter = defMedias.iterator();
        Iterator<JsonMediaOutputObject> custIter = custMedias.iterator();
        while(defIter.hasNext()){
            compareMedia(defIter.next(), custIter.next());
        }
    }

    /**
     * For a given media item, compare the number of tracks from the default pipeline and the custom pipeline
     *
     * @param defaultMedia
     * @param customMedia
     */
    private void compareMedia(JsonMediaOutputObject defaultMedia, JsonMediaOutputObject customMedia) {

        Iterator<Map.Entry<String,SortedSet<JsonActionOutputObject>>> defaultEntries =
                defaultMedia.getTrackTypes().entrySet().iterator();
        Iterator<Map.Entry<String,SortedSet<JsonActionOutputObject>>> customEntries =
                customMedia.getTrackTypes().entrySet().iterator();

        while (defaultEntries.hasNext()) {

            Map.Entry<String, SortedSet<JsonActionOutputObject>> defaultAction = defaultEntries.next();
            Map.Entry<String, SortedSet<JsonActionOutputObject>> customAction = customEntries.next();
            Assert.assertEquals(String.format("Default action type %s does not match custom action type %s",
                    defaultAction.getKey(), customAction.getKey()),
                    defaultAction.getKey(),
                    customAction.getKey());

            Iterator<JsonActionOutputObject> defaultTracks = defaultAction.getValue().iterator();
            Iterator<JsonActionOutputObject> customTracks = defaultAction.getValue().iterator();

            Assert.assertEquals(String.format("Default track entries size=%d doesn't match custom track entries size=%d",
                    defaultAction.getValue().size(), defaultAction.getValue().size()),
                    defaultAction.getValue().size(), defaultAction.getValue().size());
            while (customEntries.hasNext()) {
                SortedSet<JsonTrackOutputObject> cusTrackSet = customTracks.next().getTracks();
                SortedSet<JsonTrackOutputObject> defTrackSet = defaultTracks.next().getTracks();
                int cusTrackSetSize = cusTrackSet.size();
                int defTrackSetSize = defTrackSet.size();
                log.debug("custom number of tracks={}", cusTrackSetSize);
                log.debug("default number of tracks={}", defTrackSetSize);
                Assert.assertTrue(String.format("Custom number of tracks=%d is not less than default number of tracks=%d",
                        cusTrackSetSize, defTrackSetSize), cusTrackSetSize < defTrackSetSize);
            }
        }
    }

    @Ignore // TODO: fix me!
    @Test(timeout = 20*MINUTES)
    public void testPriorities() throws Exception {

        // an assumption failure causes the test to be ignored;
        // only run this test on a machine where /mpfdata/datasets is mapped
        Assume.assumeTrue("Skipping test. It should only run when /mpfdata/datasets is mapped.",
                           Files.exists(Paths.get("/mpfdata/datasets")));

        log.info("Beginning testPriorities()");

        int TIMEOUT_MILLIS = 15*MINUTES;
        ExecutorService executor = Executors.newFixedThreadPool(4);

        String path = "/mpfdata/datasets/mugshots_2000";
        Assert.assertTrue(String.format("%s does not exist.", path), new File(path).exists());

        PriorityRunner busyWorkRunner = new PriorityRunner(path, 9);
        PriorityRunner lowRunner = new PriorityRunner(path, 1);
        PriorityRunner highRunner = new PriorityRunner(path, 9);

        // wait until busy work is in progress; fill service message queue(s)
        executor.submit(busyWorkRunner);
        Assert.assertTrue("The busy work job is not in progress. Job may have failed to start.", busyWorkRunner.waitForSomeProgress());

        executor.submit(lowRunner);
        executor.submit(highRunner);

        List<PriorityRunner> priorityRunners = new LinkedList<PriorityRunner>();
        priorityRunners.add(busyWorkRunner);
        priorityRunners.add(lowRunner);
        priorityRunners.add(highRunner);

        PriorityMonitor priorityMonitor = new PriorityMonitor(priorityRunners);
        executor.submit(priorityMonitor);

        executor.shutdown();
        executor.awaitTermination(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        Assert.assertTrue("The busy work job did not complete.", busyWorkRunner.completed);
        Assert.assertTrue("The LOW priority job did not complete.", lowRunner.completed);
        Assert.assertTrue("The HIGH priority job did not complete.", highRunner.completed);

        priorityMonitor.terminate(); // just in case

        Assert.assertFalse("The busy work job failed.", busyWorkRunner.hadError);
        Assert.assertFalse("The LOW priority job failed.", lowRunner.hadError);
        Assert.assertFalse("The HIGH priority job failed.", highRunner.hadError);

        Assert.assertTrue(String.format("The LOW priority job was expected to take longer than the HIGH priority job. (LOW = %d ms, HIGH = %d ms)",
                lowRunner.elapsed, highRunner.elapsed), lowRunner.elapsed > highRunner.elapsed);
    }

    class PriorityRunner implements Runnable {
        public String path;
        public int priority;

        public long jobRequestId = -1;
        public long elapsed = 0;
        public boolean completed = false;
        public boolean hadError = false;

        private Stopwatch stopWatch = null;

        public PriorityRunner(String path, int priority) {
            this.priority = priority;
            this.path = path;
        }

        @Override
        public void run() {
            tick();
            try {
                // NOTE: Process lots of images so that we get frequent progress updates;
                // one update after each image is processed.
                List<JobCreationMediaData> media = new LinkedList<>();

                File dir = new File(path);
                for (File file : dir.listFiles()) {
                    media.add(toMediaObject(ioUtils.findFile(file.getAbsolutePath())));
                }

                var jobRequest = new JobCreationRequest(
                    media,
                    Map.of(),
                    Map.of(),
                    UUID.randomUUID().toString(),
                    "OCV FACE DETECTION PIPELINE",
                    null,
                    true,
                    priority,
                    null,
                    null);

                jobRequestId = INVALID_PIPELINE_RETRY.execute(
                    ctx -> jobRequestService.run(jobRequest).jobId());
                completed = waitFor(jobRequestId); // blocking
            } catch (Exception exception) {
                log.error(String.format("Failed to run job %d due to an exception.", jobRequestId), exception);
                hadError = true;
            }
            tock();
        }

        public boolean waitForSomeProgress() {
            int TIMEOUT_MILLIS = 60 * 1000;
            int WAIT_TIME_MILLIS = 5000;

            try {
                for (int t = 0; t < TIMEOUT_MILLIS; t += WAIT_TIME_MILLIS) {
                    log.info("waitForSomeProgress: {}/{} ms, jobRequestId: {}", t, TIMEOUT_MILLIS, jobRequestId); // DEBUG
                    if (jobRequestId != -1) {
                        boolean hasProgress = jobProgress.getJobProgress(jobRequestId)
                                .map(p -> p > 0)
                                .orElse(false);
                        if (hasProgress) {
                            return true;
                        }
                    }
                    Thread.sleep(WAIT_TIME_MILLIS);
                }
            } catch (InterruptedException exception) {
                log.error("Failed while waiting for job progress.", exception);
            }

            return false;
        }

        private void tick() {
            stopWatch = Stopwatch.createStarted();
        }

        private void tock() {
            stopWatch.stop();
            elapsed = stopWatch.elapsed(TimeUnit.MILLISECONDS);
        }
    }

    class PriorityMonitor implements Runnable {
        public List<PriorityRunner> priorityRunners = null;
        public PriorityMonitor(List<PriorityRunner> priorityRunners) {
            this.priorityRunners = priorityRunners;
        }

        private boolean terminate = false;
        public void terminate() {
            this.terminate = true;
        }

        @Override
        public void run() {
            int WAIT_TIME_MILLIS = 5000;

            try {
                int time = 0;
                while (!terminate) {
                    int totalProgress = 0;
                    for (PriorityRunner runner : priorityRunners) {
                        float progress = -1f;
                        if (runner.jobRequestId != -1) {
                            Optional<Float> optProgress = jobProgress.getJobProgress(runner.jobRequestId);
                            if (optProgress.isPresent()) {
                                progress = optProgress.get();
                                totalProgress += progress;
                            }
                        }
                        log.info("time: {} ms, jobRequestId: {}, progress: {}", time, runner.jobRequestId, progress);
                    }
                    if (totalProgress == priorityRunners.size() * 100) {
                        log.info("PriorityMonitor self-terminating. All jobs complete.");
                        return;
                    }
                    Thread.sleep(WAIT_TIME_MILLIS);
                    time += WAIT_TIME_MILLIS;
                }
            } catch (InterruptedException exception) {
                log.error("Failed while monitoring job progress.", exception);
            }
        }
    }
}
