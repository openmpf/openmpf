
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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mitre.mpf.interop.*;
import org.mitre.mpf.wfm.WfmStartup;
import org.mitre.mpf.wfm.businessrules.JobRequestBo;
import org.mitre.mpf.wfm.businessrules.StreamingJobRequestBo;
import org.mitre.mpf.wfm.businessrules.impl.JobRequestBoImpl;
import org.mitre.mpf.wfm.businessrules.impl.StreamingJobRequestBoImpl;
import org.mitre.mpf.wfm.camel.JobCompleteProcessor;
import org.mitre.mpf.wfm.camel.JobCompleteProcessorImpl;
import org.mitre.mpf.wfm.event.JobCompleteNotification;
import org.mitre.mpf.wfm.event.NotificationConsumer;
import org.mitre.mpf.wfm.pipeline.xml.*;
import org.mitre.mpf.wfm.service.MpfService;
import org.mitre.mpf.wfm.service.PipelineService;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.annotation.PostConstruct;
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

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext // Make sure TestStreamingJobStartStop does not use same application context as other tests.
public abstract class TestSystem {

    protected static final int MINUTES = 1000*60; // 1000 milliseconds/second & 60 seconds/minute.


    protected static int testCtr = 0;

    // is this running on Jenkins and/or is output checking desired?
    protected static boolean jenkins = false;
    static {
        String prop = System.getProperty("jenkins");
        if (prop != null){
            jenkins = Boolean.valueOf(prop);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(TestSystem.class);


    @Autowired
    @Qualifier(IoUtils.REF)
    protected IoUtils ioUtils;

    @Autowired
    protected PropertiesUtil propertiesUtil;

    @Autowired
    @Qualifier(JobRequestBoImpl.REF)
    protected JobRequestBo jobRequestBo;

    @Autowired
    @Qualifier(StreamingJobRequestBoImpl.REF)
    protected StreamingJobRequestBo streamingJobRequestBo;

    @Autowired
    protected MpfService mpfService;

    @Autowired
    protected PipelineService pipelineService;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    @Qualifier(JobCompleteProcessorImpl.REF)
    private JobCompleteProcessor jobCompleteProcessor;


    @Rule
    public TestName testName = new TestName();

    @Rule
    public MpfErrorCollector errorCollector = new MpfErrorCollector();


    private OutputChecker outputChecker = new OutputChecker(errorCollector);
    private Set<Long> completedJobs = new HashSet<>();
    private Object lock = new Object();
    private boolean hasInitialized = false;


    @PostConstruct
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

                log.info("Starting the tests from _setupContext");
                hasInitialized = true;
            }
        }
    }


    protected void addAction(String actionName, String algorithmName, Map<String, String> propertySettings) {
        if (!pipelineService.getActionNames().contains(actionName.toUpperCase())) {
            ActionDefinition actionDef = new ActionDefinition(actionName, algorithmName, actionName);
            for (Map.Entry<String, String> entry : propertySettings.entrySet()) {
                actionDef.getProperties().add(new PropertyDefinitionRef(entry.getKey(), entry.getValue()));
            }
            pipelineService.saveAction(actionDef);
        }
    }


    protected void addTask(String taskName, String... actions) {
        if (!pipelineService.getTaskNames().contains(taskName.toUpperCase())) {
            TaskDefinition taskDef = new TaskDefinition(taskName, taskName);
            for (String actionName : actions) {
                taskDef.getActions().add(new ActionDefinitionRef(actionName));
            }
            pipelineService.saveTask(taskDef);
        }
    }

    protected void addPipeline(String pipelineName, String... tasks) {
        if (!pipelineService.getPipelineNames().contains(pipelineName.toUpperCase())) {
            PipelineDefinition pipelineDef = new PipelineDefinition(pipelineName, pipelineName);
            for (String taskName : tasks) {
                pipelineDef.getTaskRefs().add(new TaskDefinitionRef(taskName));
            }
            pipelineService.savePipeline(pipelineDef);
        }
    }

    /*
     * This method simply checks that the number of media in the input matches the number of media in the output
     *
     * @param actualOutputPath
     * @param numInputMedia
     * @throws javax.xml.bind.JAXBException
     */
    public void checkOutput(URI actualOutputPath, int numInputMedia) throws IOException {
        log.debug("Deserializing actual output {}", actualOutputPath);

        JsonOutputObject actualOutput = objectMapper.readValue(actualOutputPath.toURL(), JsonOutputObject.class);
        Assert.assertTrue(String.format("Actual output size=%d doesn't match number of input media=%d",
                                        actualOutput.getMedia().size(), numInputMedia), actualOutput.getMedia().size() == numInputMedia);
    }

    protected List<JsonMediaInputObject> toMediaObjectList(URI... uris) {
        List<JsonMediaInputObject> media = new ArrayList<>(uris.length);
        for (URI uri : uris) {
            media.add(new JsonMediaInputObject(uri.toString()));
        }
        return media;
    }

    protected long runPipelineOnMedia(String pipelineName, List<JsonMediaInputObject> media) {
        return runPipelineOnMedia(pipelineName, media, Collections.emptyMap(), true,
                                  propertiesUtil.getJmsPriority());
    }

    protected long runPipelineOnMedia(String pipelineName,
                                      Map<String, String> jobProperties,
                                      List<JsonMediaInputObject> media) {
        return runPipelineOnMedia(pipelineName, media, jobProperties, true, propertiesUtil.getJmsPriority());
    }


    protected long runPipelineOnMedia(String pipelineName, List<JsonMediaInputObject> media, boolean buildOutput,
                                      int priority) {
        return runPipelineOnMedia(pipelineName, media, Collections.emptyMap(), buildOutput, priority);
    }


    protected long runPipelineOnMedia(String pipelineName, List<JsonMediaInputObject> media, Map<String, String> jobProperties, boolean buildOutput, int priority) {
        JsonJobRequest jsonJobRequest = jobRequestBo.createRequest(UUID.randomUUID().toString(), pipelineName, media, Collections.emptyMap(), jobProperties,
                                                                   buildOutput, priority);
        long jobRequestId = mpfService.submitJob(jsonJobRequest);
        Assert.assertTrue(waitFor(jobRequestId));
        return jobRequestId;
    }

    protected long runPipelineOnStream(String pipelineName, JsonStreamingInputObject stream, Map<String, String> jobProperties, boolean buildOutput, int priority,
                                       long stallTimeout) throws Exception {
        JsonStreamingJobRequest jsonStreamingJobRequest = streamingJobRequestBo.createRequest(UUID.randomUUID().toString(), pipelineName, stream,
                                                                                              Collections.emptyMap(), jobProperties,
                                                                                              buildOutput, priority,
                                                                                              stallTimeout,
                                                                                              null,null);
        long jobRequestId = mpfService.submitJob(jsonStreamingJobRequest);
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

    protected void runSystemTest(String pipelineName, String expectedOutputJsonPath, String... testMediaFiles) throws Exception {
        testCtr++;
        log.info("Beginning test #{} {}()", testCtr, testName.getMethodName());
        List<JsonMediaInputObject> mediaPaths = new LinkedList<>();
        for (String filePath : testMediaFiles) {
            mediaPaths.add(new JsonMediaInputObject(ioUtils.findFile(filePath).toString()));
        }

        long jobId = runPipelineOnMedia(pipelineName, mediaPaths, Collections.emptyMap(), propertiesUtil.isOutputObjectsEnabled(),
                                        propertiesUtil.getJmsPriority());
        if (jenkins) {
            URL expectedOutputPath = getClass().getClassLoader().getResource(expectedOutputJsonPath);
            log.info("Deserializing expected output {} and actual output for job {}", expectedOutputPath, jobId);

            JsonOutputObject expectedOutputJson = objectMapper.readValue(expectedOutputPath, JsonOutputObject.class);
            JsonOutputObject actualOutputJson = getJobOutputObject(jobId);

            outputChecker.compareJsonOutputObjects(expectedOutputJson, actualOutputJson, pipelineName);
        }
        log.info("Finished test {}()", testName.getMethodName());
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

        double corner2X = detection.getX() + detection.getWidth() * cosVal;
        double corner2Y = detection.getY() - detection.getWidth() * sinVal;

        double corner3X = corner2X + detection.getHeight() * sinVal;
        double corner3Y = corner2Y + detection.getHeight() * cosVal;

        double corner4X = detection.getX() + detection.getHeight() * sinVal;
        double corner4Y = detection.getY() + detection.getHeight() * cosVal;

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


