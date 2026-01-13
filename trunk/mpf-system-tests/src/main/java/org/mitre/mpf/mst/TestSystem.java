
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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TestWatcher;
import org.junit.runner.RunWith;
import org.mitre.mpf.interop.JsonDetectionOutputObject;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.rest.api.JobCreationMediaData;
import org.mitre.mpf.rest.api.JobCreationRequest;
import org.mitre.mpf.rest.api.JobCreationStreamData;
import org.mitre.mpf.rest.api.MediaUri;
import org.mitre.mpf.rest.api.StreamingJobCreationRequest;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.ActionProperty;
import org.mitre.mpf.rest.api.pipelines.Pipeline;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.rest.api.pipelines.transients.TransientPipelineDefinition;
import org.mitre.mpf.wfm.WfmStartup;
import org.mitre.mpf.wfm.businessrules.JobRequestService;
import org.mitre.mpf.wfm.businessrules.StreamingJobRequestService;
import org.mitre.mpf.wfm.camel.JobCompleteProcessor;
import org.mitre.mpf.wfm.camel.JobCompleteProcessorImpl;
import org.mitre.mpf.wfm.event.JobCompleteNotification;
import org.mitre.mpf.wfm.event.NotificationConsumer;
import org.mitre.mpf.wfm.service.pipeline.PipelineService;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import java.awt.*;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.*;

@WebAppConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext // Make sure TestStreamingJobStartStop does not use same application context as other tests.
@ActiveProfiles("jenkins")
public abstract class TestSystem {

    private static final int INIT_TIME_MILLIS = 5000;
    protected static final int MINUTES = 1000*60; // 1000 milliseconds/second & 60 seconds/minute.

    // is this running on Jenkins and/or is output checking desired?
    protected static boolean DISABLE_OUTPUT_CHECKING = false;
    static {
        // Needs to be set on command line with: -Ddisable.output.checking=true
        String prop = System.getProperty("disable.output.checking");
        if (prop != null){
            DISABLE_OUTPUT_CHECKING = Boolean.valueOf(prop);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(TestSystem.class);


    @Autowired
    protected IoUtils ioUtils;

    @Autowired
    protected PropertiesUtil propertiesUtil;

    @Autowired
    protected JobRequestService jobRequestService;

    @Autowired(required = false)
    protected StreamingJobRequestService streamingJobRequestService;

    @Autowired
    protected PipelineService pipelineService;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    @Qualifier(JobCompleteProcessorImpl.REF)
    private JobCompleteProcessor jobCompleteProcessor;


    @Rule
    public MpfErrorCollector errorCollector = new MpfErrorCollector();

    @ClassRule
    public static TestInfoLoggerClassRule testInfoLoggerClassRule = new TestInfoLoggerClassRule();
    @Rule
    public TestWatcher testInfoMethodRule = testInfoLoggerClassRule.methodRule();



    private OutputChecker outputChecker = new OutputChecker(errorCollector);
    private Set<Long> completedJobs = new HashSet<>();
    private Object lock = new Object();
    private boolean hasInitialized = false;

    @BeforeClass
    private void init() throws Exception {
        synchronized (lock) {
            if (!hasInitialized) {
                completedJobs = new HashSet<Long>();
                jobCompleteProcessor.subscribe(new NotificationConsumer<JobCompleteNotification>() {
                    @Override
                    public void onNotification(Object source, JobCompleteNotification notification) {
                        log.info("JobCompleteProcessorSubscriber: Source={}, Notification={}", source, notification);
                        synchronized (lock) {
                            completedJobs.add(notification.getJobId());
                            lock.notifyAll();
                        }
                        log.info("JobCompleteProcessorSubscriber COMPLETE");
                    }
                });

                log.info("Sleeping for {} milliseconds before starting the tests from _setupContext", INIT_TIME_MILLIS);
                try {
                    Thread.sleep(INIT_TIME_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                hasInitialized = true;
            }
        }
    }


    protected void addAction(String actionName, String algorithmName, Map<String, String> propertySettings) {
        if (pipelineService.getAction(actionName) != null) {
            return;
        }

        List<ActionProperty> propertyList = propertySettings.entrySet()
                .stream()
                .map(e -> new ActionProperty(e.getKey(), e.getValue()))
                .collect(toList());
        Action action = new Action(actionName, actionName, algorithmName, propertyList);
        pipelineService.save(action);
    }


    protected void addTask(String taskName, String... actions) {
        if (pipelineService.getTask(taskName) != null) {
            return;
        }

        Task task = new Task(taskName, taskName, Arrays.asList(actions));
        pipelineService.save(task);
    }


    protected void addPipeline(String pipelineName, String... tasks) {
        if (pipelineService.getPipeline(pipelineName) != null) {
            return;
        }

        Pipeline pipeline = new Pipeline(pipelineName, pipelineName, Arrays.asList(tasks));
        pipelineService.save(pipeline);
    }

    /*
     * This method simply checks that the number of media in the input matches the number of media in the output
     *
     * @param actualOutputPath
     * @param numInputMedia
     */
    public void checkOutput(URI actualOutputPath, int numInputMedia) throws IOException {
        log.debug("Deserializing actual output {}", actualOutputPath);

        JsonOutputObject actualOutput = objectMapper.readValue(actualOutputPath.toURL(), JsonOutputObject.class);
        Assert.assertTrue(String.format("Actual output size=%d doesn't match number of input media=%d",
                                        actualOutput.getMedia().size(), numInputMedia), actualOutput.getMedia().size() == numInputMedia);
    }

    protected List<JobCreationMediaData> toMediaObjectList(URI... uris) {
        return Stream.of(uris)
            .map(this::toMediaObject)
            .toList();
    }

    protected JobCreationMediaData toMediaObject(URI uri, Map<String, String> properties) {
        return new JobCreationMediaData(
                new MediaUri(uri),
                properties,
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                Optional.empty());
    }

    protected JobCreationMediaData toMediaObject(URI uri) {
        return toMediaObject(uri, Map.of());
    }

    protected long runPipelineOnMedia(String pipelineName,
                                      List<JobCreationMediaData> media) {
        return runPipelineOnMedia(
                pipelineName,
                media,
                Collections.emptyMap(),
                Collections.emptyMap(),
                propertiesUtil.getJmsPriority());
    }

    protected long runPipelineOnMedia(String pipelineName,
                                      List<JobCreationMediaData> media,
                                      Map<String, String> jobProperties) {
        return runPipelineOnMedia(
                pipelineName,
                media,
                Collections.emptyMap(),
                jobProperties,
                propertiesUtil.getJmsPriority());
    }

    protected long runPipelineOnMedia(
            String pipelineName,
            List<JobCreationMediaData> media,
            Map<String, Map<String, String>> algorithmProperties,
            Map<String, String> jobProperties,
            int priority) {
        var jobRequest = new JobCreationRequest(
                media,
                jobProperties,
                algorithmProperties,
                UUID.randomUUID().toString(),
                pipelineName,
                null,
                true,
                priority,
                null,
                null);
        return runJob(jobRequest);
    }

    protected long runPipelineOnMedia(
            TransientPipelineDefinition pipelineDef,
            List<JobCreationMediaData> media,
            Map<String, String> jobProperties,
            int priority) {

        var jobRequest = new JobCreationRequest(
                media,
                jobProperties,
                Map.of(),
                UUID.randomUUID().toString(),
                null,
                pipelineDef,
                true,
                priority,
                null,
                null);

        return runJob(jobRequest);
    }

    protected long runJob(JobCreationRequest jobRequest) {
        long jobRequestId = jobRequestService.run(jobRequest).jobId();
        Assert.assertTrue(waitFor(jobRequestId));
        return jobRequestId;
    }


    protected long runPipelineOnStream(
            String pipelineName, JobCreationStreamData stream, Map<String, String> jobProperties,
            boolean buildOutput, int priority, long stallTimeout) {

        Assume.assumeTrue("Skipping test because StreamingJobRequestService was not configured.",
                          streamingJobRequestService != null);
        var jobCreationRequest = new StreamingJobCreationRequest();
        jobCreationRequest.setExternalId(UUID.randomUUID().toString());
        jobCreationRequest.setPipelineName(pipelineName);
        jobCreationRequest.setJobProperties(jobProperties);
        jobCreationRequest.setEnableOutputToDisk(buildOutput);
        jobCreationRequest.setPriority(priority);
        jobCreationRequest.setStallTimeout(stallTimeout);
        jobCreationRequest.setStream(stream);

        long jobRequestId = streamingJobRequestService.run(jobCreationRequest).getId();
        Assert.assertTrue(waitFor(jobRequestId));
        return jobRequestId;
    }

    public boolean waitFor(long jobRequestId) {
        synchronized (lock) {
            while (!completedJobs.contains(jobRequestId)) {
                try {
                    lock.wait();
                } catch (Exception exception) {
                    log.warn("Exception occurred while waiting. Assuming that the job has completed (but failed)", exception);
                    completedJobs.add(jobRequestId);
                    return false;
                }
                log.info("Woken up. Checking to see if {} has completed", jobRequestId);
            }
            log.info("{} has completed!", jobRequestId);
            return true;
        }
    }

    protected JsonOutputObject runSystemTest(String pipelineName,
                                 String expectedOutputJsonPath,
                                 String... testMediaFiles) throws Exception {
        return runSystemTest(
                pipelineName,
                expectedOutputJsonPath,
                Collections.emptyMap(),
                Collections.emptyMap(),
                testMediaFiles);
    }

    protected JsonOutputObject runSystemTest(
            String pipelineName,
            String expectedOutputJsonPath,
            JobCreationMediaData... testMediaFiles) throws IOException {
        return runSystemTest(
                pipelineName,
                expectedOutputJsonPath,
                Collections.emptyMap(),
                Collections.emptyMap(),
                testMediaFiles);
    }

    protected JsonOutputObject runSystemTest(String pipelineName,
                                 String expectedOutputJsonPath,
                                 Map<String, Map<String, String>> algorithmProperties,
                                 Map<String, String> jobProperties,
                                 String... testMediaFiles) throws IOException {
        var media = Stream.of(testMediaFiles)
            .map(f -> toMediaObject(ioUtils.findFile(f)))
            .toArray(JobCreationMediaData[]::new);
        return runSystemTest(
                pipelineName, expectedOutputJsonPath, algorithmProperties, jobProperties, media);
    }

    protected JsonOutputObject runSystemTest(
            String pipelineName,
            String expectedOutputJsonPath,
            Map<String, Map<String, String>> algorithmProperties,
            Map<String, String> jobProperties,
            JobCreationMediaData... testMedia) throws IOException {
        var jobRequest = new JobCreationRequest(
                List.of(testMedia),
                jobProperties,
                algorithmProperties,
                UUID.randomUUID().toString(),
                pipelineName,
                null,
                true,
                propertiesUtil.getJmsPriority(),
                null,
                null);
        return runSystemTest(jobRequest, expectedOutputJsonPath);
    }

    protected JsonOutputObject runSystemTest(
            JobCreationRequest jobRequest, String expectedOutputJsonPath) throws IOException {
        long jobId = runJob(jobRequest);
        if (DISABLE_OUTPUT_CHECKING) {
            return getJobOutputObject(jobId);
        }

        URL expectedOutputPath = getClass().getClassLoader().getResource(expectedOutputJsonPath);
        log.info(
                "Deserializing expected output {} and actual output for job {}",
                expectedOutputPath, jobId);
        var expectedOutputJson = objectMapper.readValue(expectedOutputPath, JsonOutputObject.class);
        var actualOutputJson = getJobOutputObject(jobId);

        outputChecker.compareJsonOutputObjects(
                expectedOutputJson, actualOutputJson, jobRequest.pipelineName());
        return actualOutputJson;
    }


    protected JsonOutputObject getJobOutputObject(long jobId) {
        try {
            File outputObjectFile = propertiesUtil.createDetectionOutputObjectFile(jobId).toFile();
            return objectMapper.readValue(outputObjectFile, JsonOutputObject.class);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    public List<Point2D.Double> getCorners(JsonDetectionOutputObject detection) {
        double rotationDegrees = Double.parseDouble(detection.getDetectionProperties()
                                                            .getOrDefault("ROTATION", "0"));
        double radians = Math.toRadians(rotationDegrees);
        double sinVal = Math.sin(radians);
        double cosVal = Math.cos(radians);

        // Need to subtract one because if you have a Rect(x=0, y=0, width=10, height=10),
        // the bottom right corner is (9, 9) not (10, 10)
        double width = detection.getWidth() - 1;
        double height = detection.getHeight() - 1;

        double corner2X = detection.getX() + width * cosVal;
        double corner2Y = detection.getY() - width * sinVal;

        double corner3X = corner2X + height * sinVal;
        double corner3Y = corner2Y + height * cosVal;

        double corner4X = detection.getX() + height * sinVal;
        double corner4Y = detection.getY() + height * cosVal;

        return Arrays.asList(
                new Point2D.Double(detection.getX(), detection.getY()),
                new Point2D.Double(corner2X, corner2Y),
                new Point2D.Double(corner3X, corner3Y),
                new Point2D.Double(corner4X, corner4Y));
    }


    public void assertAllInExpectedRegion(int[] xPoints, int[] yPoints,
                                          Collection<JsonDetectionOutputObject> detections) {

        assertEquals(xPoints.length, yPoints.length);
        assertFalse(detections.isEmpty());

        Polygon expectedRegion = new Polygon(xPoints, yPoints, xPoints.length);
        String outOfRangePoints = detections.stream()
                .flatMap(d -> getCorners(d).stream())
                .filter(pt -> !expectedRegion.contains(pt))
                .distinct()
                .map(Object::toString)
                .collect(Collectors.joining("\n"));

        assertTrue("The following points were not in the expected region:\n" + outOfRangePoints,
                   outOfRangePoints.isEmpty());
    }


    /**
     * This class loads the workflow manager's applicationContext.xml and adds an additional properties file
     * to the application context.
     *
     * Note that the {@link ContextConfiguration#locations()} annotation parameter allows you to
     * specify an XML file through the annotation, but there is no way to specify that file should be loaded
     * from a different class's class path. Thus, we don't use that annotation.
     *
     * Subclasses of this class must provide a no-arg constructor since they will be used in
     * {@link ContextConfiguration#initializers()}, which doesn't allow constructor parameters.
     */
    protected static class BaseAppCtxInit implements ApplicationContextInitializer<GenericApplicationContext> {

        private final String _additionalPropertiesFile;


        protected BaseAppCtxInit(String additionalPropertiesFile) {
            _additionalPropertiesFile = additionalPropertiesFile;
        }

        @Override
        public final void initialize(GenericApplicationContext applicationContext) {
            ThreadUtil.start();
            addWfmAppCtx(applicationContext);
            addPropertiesFile(applicationContext, _additionalPropertiesFile);
        }


        private static void addWfmAppCtx(GenericApplicationContext applicationCtx) {
            Resource wfmAppCtx = new ClassPathResource(
                    "/applicationContext.xml",
                    // Passes reference to a workflow manager class to ensure the that workflow manager's
                    // applicationContext.xml is used.
                    // Any class that is part of the workflow manager project will work.
                    WfmStartup.class);
            BeanDefinitionReader xmlReader = new XmlBeanDefinitionReader(applicationCtx);
            xmlReader.loadBeanDefinitions(wfmAppCtx);
        }


        private static void addPropertiesFile(GenericApplicationContext applicationCtx, String additionalPropsFile) {
            BeanDefinition propFilesDef = applicationCtx.getBeanDefinition("propFiles");
            MutablePropertyValues propertyValues = propFilesDef.getPropertyValues();
            List<Object> sourceList = (List<Object>) propertyValues.get("sourceList");

            // add new file to the front of the list so it overrides other files
            sourceList.add(0, new TypedStringValue(additionalPropsFile, Resource.class.getName()));
        }
    }
}
