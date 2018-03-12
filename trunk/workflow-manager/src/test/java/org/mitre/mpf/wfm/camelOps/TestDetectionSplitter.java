/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.camelOps;

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultExchange;
import org.apache.commons.lang3.mutable.MutableInt;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runner.notification.RunListener;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.buffers.AlgorithmPropertyProtocolBuffer;
import org.mitre.mpf.wfm.buffers.AlgorithmPropertyProtocolBuffer.AlgorithmProperty;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.camel.StageSplitter;
import org.mitre.mpf.wfm.camel.WfmProcessorInterface;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionSplitter;
import org.mitre.mpf.wfm.camel.operations.mediainspection.MediaInspectionProcessor;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.entities.transients.TransientAction;
import org.mitre.mpf.wfm.data.entities.transients.TransientJob;
import org.mitre.mpf.wfm.data.entities.transients.TransientMedia;
import org.mitre.mpf.wfm.data.entities.transients.TransientPipeline;
import org.mitre.mpf.wfm.data.entities.transients.TransientStage;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration(locations = {"classpath:applicationContext.xml"})
@RunWith(SpringJUnit4ClassRunner.class)
@RunListener.ThreadSafe
public class TestDetectionSplitter {
    private static final Logger log = LoggerFactory.getLogger(TestDetectionSplitter.class);
    private static final int MINUTES = 1000 * 60; // 1000 milliseconds/second & 60 seconds/minute.

    @Autowired
    private ApplicationContext context;

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private IoUtils ioUtils;

    @Autowired
    private PropertiesUtil propertiesUtil;

    @Autowired
    private JsonUtils jsonUtils;

    @Autowired
    @Qualifier(MediaInspectionProcessor.REF)
    private WfmProcessorInterface mediaInspectionProcessor;

    @Autowired
    private Redis redis;

    @Qualifier(DetectionSplitter.REF)
    @Autowired
    private StageSplitter detectionStageSplitter;

    private static final MutableInt SEQUENCE = new MutableInt();

    public int next() {
        synchronized (SEQUENCE) {
            int next = SEQUENCE.getValue();
            SEQUENCE.increment();
            return next;
        }
    }
    @Test
    public void testDetectionSplitter() throws Exception {
        final long testId = 12345;
        final String testExternalId = "externID";
        final TransientPipeline testPipe = new TransientPipeline("testPipe", "testDescr");
        final int testStage = 0;
        final int testPriority = 4;
        final boolean testOutputEnabled = true;
        TransientJob testJob = new TransientJob(testId, testExternalId, testPipe, testStage, testPriority, testOutputEnabled, false);
        TransientMedia testMedia = new TransientMedia(next(), ioUtils.findFile("/samples/new_face_video.avi").toString());
        testMedia.setType("VIDEO");
        List<TransientMedia> listMedia = Lists.newArrayList(testMedia);
        testJob.setMedia(listMedia);
        TransientStage testTransientStage = new TransientStage("stageName", "stageDescr", ActionType.DETECTION);
        testPipe.setStages(Collections.singletonList(testTransientStage));

        Map<String, String> mergeProp = new HashMap<>();
        mergeProp.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, "1");
        mergeProp.put(MpfConstants.MIN_GAP_BETWEEN_TRACKS, "1");
        mergeProp.put(MpfConstants.MINIMUM_SEGMENT_LENGTH_PROPERTY, "10");
        mergeProp.put(MpfConstants.TARGET_SEGMENT_LENGTH_PROPERTY, "25");
        TransientAction detectionAction = new TransientAction("detectionAction", "detectionDescription", "detectionAlgo");
        detectionAction.setProperties(mergeProp);
        testTransientStage.getActions().add(detectionAction);
        List<Message> responseList = detectionStageSplitter.performSplit(testJob, testTransientStage);
        Assert.assertTrue(responseList.size() == 0);

    }

    @Test
    public void testJobPropertiesOverride() throws Exception {
        HashMap<String, String> jobProperties = new HashMap<>();
        String propertyName = "TEST";
        String propertyValue = "VALUE";
        jobProperties.put(propertyName, propertyValue);
        jobProperties.put(MpfConstants.MIN_GAP_BETWEEN_TRACKS, "0");
        Map<String, String> actionProperties = new HashMap<>();
        actionProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, "1");
        actionProperties.put(MpfConstants.MIN_GAP_BETWEEN_TRACKS, "1");
        actionProperties.put(MpfConstants.MINIMUM_SEGMENT_LENGTH_PROPERTY, "10");
        actionProperties.put(MpfConstants.TARGET_SEGMENT_LENGTH_PROPERTY, "25");
        TransientJob testJob = createSimpleJobForTest(actionProperties, "/samples/new_face_video.avi", "VIDEO");
        testJob.getOverriddenJobProperties().putAll(jobProperties);
        List<Message> responseList = detectionStageSplitter.performSplit(testJob, testJob.getPipeline().getStages().get(0));

        Assert.assertEquals(12, responseList.size());
        Message message = responseList.get(0);
        Assert.assertTrue(message.getBody() instanceof DetectionProtobuf.DetectionRequest);


        DetectionProtobuf.DetectionRequest request = (DetectionProtobuf.DetectionRequest) message.getBody();
        boolean propertyExists = false;
        for (AlgorithmPropertyProtocolBuffer.AlgorithmProperty prop : request.getAlgorithmPropertyList()) {
            if (propertyName.equals(prop.getPropertyName())) {
                Assert.assertEquals(propertyValue, prop.getPropertyValue());
                propertyExists = true;
            }
            else if (MpfConstants.MIN_GAP_BETWEEN_TRACKS.equals(prop.getPropertyName())) {
                Assert.assertEquals("0", prop.getPropertyValue());
            }
        }
        Assert.assertTrue(propertyExists);
    }

    @Test
    public void testMediaSpecificPropertiesOverride() throws Exception {
        HashMap<String, String> mediaProperties = new HashMap<>();
        String propertyName = "TEST";
        String propertyValue = "VALUE";
        mediaProperties.put(propertyName, propertyValue);
        mediaProperties.put(MpfConstants.MIN_GAP_BETWEEN_TRACKS, "0");
        Map<String, String> jobProperties = new HashMap<>();
        jobProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, "1");
        jobProperties.put(MpfConstants.MIN_GAP_BETWEEN_TRACKS, "1");
        jobProperties.put(MpfConstants.MINIMUM_SEGMENT_LENGTH_PROPERTY, "10");
        jobProperties.put(MpfConstants.TARGET_SEGMENT_LENGTH_PROPERTY, "25");
        TransientJob testJob = createSimpleJobForTest(jobProperties, "/samples/new_face_video.avi", "VIDEO");
        testJob.getMedia().get(0).getMediaSpecificProperties().putAll(mediaProperties);
        List<Message> responseList = detectionStageSplitter.performSplit(testJob, testJob.getPipeline().getStages().get(0));

        Assert.assertEquals(12, responseList.size());
        Message message = responseList.get(0);
        Assert.assertTrue(message.getBody() instanceof DetectionProtobuf.DetectionRequest);

        DetectionProtobuf.DetectionRequest request = (DetectionProtobuf.DetectionRequest) message.getBody();
        boolean propertyExists = false;
        for (AlgorithmPropertyProtocolBuffer.AlgorithmProperty prop : request.getAlgorithmPropertyList()) {
            if (propertyName.equals(prop.getPropertyName())) {
                Assert.assertEquals(propertyValue,prop.getPropertyValue());
                propertyExists = true;
            }
            else if (MpfConstants.MIN_GAP_BETWEEN_TRACKS.equals(prop.getPropertyName())) {
                Assert.assertEquals("0", prop.getPropertyValue());
            }
        }
        Assert.assertTrue(propertyExists);
    }

    /**
     * Tests to be sure that a media-specific property for rotation, flip, or any ROI property disables
     * auto-rotate and auto-flip on the action, and others leave them alone.
     *
     * @throws Exception
     */
    @Test
    public void testMediaSpecificPropertiesOverrideWithExif() throws Exception {
        testMediaSpecificPropertiesOverrideWithFrameTransforms(MpfConstants.HORIZONTAL_FLIP_PROPERTY, "TRUE", true);
        testMediaSpecificPropertiesOverrideWithFrameTransforms(MpfConstants.ROTATION_PROPERTY, "90", true);
        testMediaSpecificPropertiesOverrideWithFrameTransforms(MpfConstants.SEARCH_REGION_TOP_LEFT_X_DETECTION_PROPERTY, "-1", true);
        testMediaSpecificPropertiesOverrideWithFrameTransforms(MpfConstants.SEARCH_REGION_TOP_LEFT_Y_DETECTION_PROPERTY, "-1", true);
        testMediaSpecificPropertiesOverrideWithFrameTransforms(MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_X_DETECTION_PROPERTY, "-1", true);
        testMediaSpecificPropertiesOverrideWithFrameTransforms(MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_Y_DETECTION_PROPERTY, "-1",true);
        testMediaSpecificPropertiesOverrideWithFrameTransforms(MpfConstants.AUTO_FLIP_PROPERTY, "TRUE",true);
        testMediaSpecificPropertiesOverrideWithFrameTransforms(MpfConstants.AUTO_ROTATE_PROPERTY, "TRUE",true);
        testMediaSpecificPropertiesOverrideWithFrameTransforms(MpfConstants.MIN_GAP_BETWEEN_TRACKS, "0", false);
        testMediaSpecificPropertiesOverrideWithFrameTransforms(MpfConstants.MINIMUM_SEGMENT_LENGTH_PROPERTY, "100",false);
    }

    /**
     * Tests to be sure that a media-specific property for rotation, flip, or any ROI property disables
     * auto-rotate and auto-flip on job properties, and others leave them alone.
     *
     * @throws Exception
     */
    @Test
    public void testMediaSpecificPropertiesOverrideJobProperties() throws Exception {
        testMediaSpecificPropertiesResettingJobProperty(MpfConstants.HORIZONTAL_FLIP_PROPERTY, "TRUE", true);
        testMediaSpecificPropertiesResettingJobProperty(MpfConstants.ROTATION_PROPERTY, "90", true);
        testMediaSpecificPropertiesResettingJobProperty(MpfConstants.SEARCH_REGION_TOP_LEFT_X_DETECTION_PROPERTY, "-1", true);
        testMediaSpecificPropertiesResettingJobProperty(MpfConstants.SEARCH_REGION_TOP_LEFT_Y_DETECTION_PROPERTY, "-1", true);
        testMediaSpecificPropertiesResettingJobProperty(MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_X_DETECTION_PROPERTY, "-1", true);
        testMediaSpecificPropertiesResettingJobProperty(MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_Y_DETECTION_PROPERTY, "-1", true);
        testMediaSpecificPropertiesResettingJobProperty(MpfConstants.AUTO_FLIP_PROPERTY, "TRUE",true);
        testMediaSpecificPropertiesResettingJobProperty(MpfConstants.AUTO_ROTATE_PROPERTY, "TRUE", true);
        testMediaSpecificPropertiesResettingJobProperty(MpfConstants.MIN_GAP_BETWEEN_TRACKS, "0", false);
        testMediaSpecificPropertiesResettingJobProperty(MpfConstants.MINIMUM_SEGMENT_LENGTH_PROPERTY, "100", false);
    }

    /**
     * Tests to be sure that a media-specific property for rotation, flip, or any ROI property disables
     * auto-rotate and auto-flip on the action, and others leave them alone.
     *
     * @throws Exception
     */
    @Test
    public void testMediaSpecificPropertiesOverrideAlgorithmProperties() throws Exception {
        testJobPropertiesResettingActionProperties(MpfConstants.HORIZONTAL_FLIP_PROPERTY, "TRUE", true);
        testJobPropertiesResettingActionProperties(MpfConstants.ROTATION_PROPERTY, "90", true);
        testJobPropertiesResettingActionProperties(MpfConstants.SEARCH_REGION_TOP_LEFT_X_DETECTION_PROPERTY, "-1", true);
        testJobPropertiesResettingActionProperties(MpfConstants.SEARCH_REGION_TOP_LEFT_Y_DETECTION_PROPERTY, "-1", true);
        testJobPropertiesResettingActionProperties(MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_X_DETECTION_PROPERTY, "-1", true);
        testJobPropertiesResettingActionProperties(MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_Y_DETECTION_PROPERTY, "-1", true);
        testJobPropertiesResettingActionProperties(MpfConstants.AUTO_FLIP_PROPERTY, "TRUE", true);
        testJobPropertiesResettingActionProperties(MpfConstants.AUTO_ROTATE_PROPERTY, "TRUE", true);
        testJobPropertiesResettingActionProperties(MpfConstants.MIN_GAP_BETWEEN_TRACKS, "0", false);
        testJobPropertiesResettingActionProperties(MpfConstants.MINIMUM_SEGMENT_LENGTH_PROPERTY, "100",false);
    }

    private void testMediaSpecificPropertiesResettingJobProperty(String propertyName, String propertyValue, boolean shouldOverride) throws Exception {
        HashMap<String, String> mediaProperties = new HashMap<>();
        mediaProperties.put(propertyName, propertyValue);
        Map<String, String> jobProperties = new HashMap<>();
        jobProperties.put(MpfConstants.AUTO_ROTATE_PROPERTY, "TRUE");
        jobProperties.put(MpfConstants.AUTO_FLIP_PROPERTY, "TRUE");
        jobProperties.put(MpfConstants.ROTATION_PROPERTY, "270");
        jobProperties.put(MpfConstants.HORIZONTAL_FLIP_PROPERTY, "TRUE");
        jobProperties.put(MpfConstants.SEARCH_REGION_TOP_LEFT_X_DETECTION_PROPERTY, "20");
        jobProperties.put(MpfConstants.SEARCH_REGION_TOP_LEFT_Y_DETECTION_PROPERTY, "20");
        jobProperties.put(MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_X_DETECTION_PROPERTY, "20");
        jobProperties.put(MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_Y_DETECTION_PROPERTY, "20");
        TransientJob testJob = createSimpleJobForTest(Collections.emptyMap(), "/samples/meds-aa-S001-01-exif-rotation.jpg", "IMAGE");
        testJob.getOverriddenJobProperties().putAll(jobProperties);
        testJob.getMedia().get(0).getMediaSpecificProperties().putAll(mediaProperties);
        List<Message> responseList = detectionStageSplitter.performSplit(testJob, testJob.getPipeline().getStages().get(0));
        Assert.assertEquals(1, responseList.size());
        Message message = responseList.get(0);
        Assert.assertTrue(message.getBody() instanceof DetectionProtobuf.DetectionRequest);

        DetectionProtobuf.DetectionRequest request = (DetectionProtobuf.DetectionRequest) message.getBody();
        for (AlgorithmPropertyProtocolBuffer.AlgorithmProperty prop : request.getAlgorithmPropertyList()) {
            if (!propertyName.equals(prop.getPropertyName())) {
                if (shouldOverride) {
                    Assert.fail("Property " + prop.getPropertyName() + " should be cleared.");
                } else {
                    Assert.assertEquals(jobProperties.get(prop.getPropertyName()), prop.getPropertyValue());
                }
            }
        }
    }

    private void testJobPropertiesResettingActionProperties(String propertyName, String propertyValue, boolean shouldOverride) throws Exception {
        HashMap<String, String> jobProperties = new HashMap<>();
        jobProperties.put(propertyName, propertyValue);
        Map<String, String> actionProperties = new HashMap<>();
        actionProperties.put(MpfConstants.AUTO_ROTATE_PROPERTY, "TRUE");
        actionProperties.put(MpfConstants.AUTO_FLIP_PROPERTY, "TRUE");
        actionProperties.put(MpfConstants.ROTATION_PROPERTY, "270");
        actionProperties.put(MpfConstants.HORIZONTAL_FLIP_PROPERTY, "TRUE");
        actionProperties.put(MpfConstants.SEARCH_REGION_TOP_LEFT_X_DETECTION_PROPERTY, "20");
        actionProperties.put(MpfConstants.SEARCH_REGION_TOP_LEFT_Y_DETECTION_PROPERTY, "20");
        actionProperties.put(MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_X_DETECTION_PROPERTY, "20");
        actionProperties.put(MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_Y_DETECTION_PROPERTY, "20");
        TransientJob testJob = createSimpleJobForTest(actionProperties, "/samples/meds-aa-S001-01-exif-rotation.jpg", "IMAGE");
        testJob.getOverriddenJobProperties().putAll(jobProperties);
        List<Message> responseList = detectionStageSplitter.performSplit(testJob, testJob.getPipeline().getStages().get(0));
        Assert.assertEquals(1, responseList.size());
        Message message = responseList.get(0);
        Assert.assertTrue(message.getBody() instanceof DetectionProtobuf.DetectionRequest);

        DetectionProtobuf.DetectionRequest request = (DetectionProtobuf.DetectionRequest) message.getBody();
        for (AlgorithmPropertyProtocolBuffer.AlgorithmProperty prop : request.getAlgorithmPropertyList()) {
            if (!propertyName.equals(prop.getPropertyName())) {
                if (shouldOverride) {
                    Assert.fail("Property " + prop.getPropertyName() + " should be cleared.");
                } else {
                    Assert.assertEquals(actionProperties.get(prop.getPropertyName()), prop.getPropertyValue());
                }
            }
        }
    }

    private void testMediaSpecificPropertiesOverrideWithFrameTransforms(String propertyName, String propertyValue, boolean shouldOverride) throws Exception {
        HashMap<String, String> mediaProperties = new HashMap<>();
        mediaProperties.put(propertyName, propertyValue);
        Map<String, String> actionProperties = new HashMap<>();
        actionProperties.put(MpfConstants.AUTO_ROTATE_PROPERTY, "TRUE");
        actionProperties.put(MpfConstants.AUTO_FLIP_PROPERTY, "TRUE");
        actionProperties.put(MpfConstants.ROTATION_PROPERTY, "270");
        actionProperties.put(MpfConstants.HORIZONTAL_FLIP_PROPERTY, "TRUE");
        actionProperties.put(MpfConstants.SEARCH_REGION_TOP_LEFT_X_DETECTION_PROPERTY, "20");
        actionProperties.put(MpfConstants.SEARCH_REGION_TOP_LEFT_Y_DETECTION_PROPERTY, "20");
        actionProperties.put(MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_X_DETECTION_PROPERTY, "20");
        actionProperties.put(MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_Y_DETECTION_PROPERTY, "20");
        TransientJob testJob = createSimpleJobForTest(actionProperties, "/samples/meds-aa-S001-01-exif-rotation.jpg", "IMAGE");
        testJob.getMedia().get(0).getMediaSpecificProperties().putAll(mediaProperties);
        List<Message> responseList = detectionStageSplitter.performSplit(testJob, testJob.getPipeline().getStages().get(0));
        Assert.assertEquals(1, responseList.size());
        Message message = responseList.get(0);
        Assert.assertTrue(message.getBody() instanceof DetectionProtobuf.DetectionRequest);

        DetectionProtobuf.DetectionRequest request = (DetectionProtobuf.DetectionRequest) message.getBody();
        for (AlgorithmPropertyProtocolBuffer.AlgorithmProperty prop : request.getAlgorithmPropertyList()) {
            if (!propertyName.equals(prop.getPropertyName())) {
                if (shouldOverride) {
                    Assert.fail("Property " + prop.getPropertyName() + " should be cleared.");
                } else {
                    Assert.assertEquals(actionProperties.get(prop.getPropertyName()), prop.getPropertyValue());
                }
            }
        }
    }

    private TransientJob createSimpleJobForTest(Map<String, String> actionProperties, String mediaUri, String mediaType) throws WfmProcessingException {
        final long testId = 12345;
        final String testExternalId = "externID";
        final TransientPipeline testPipe = new TransientPipeline("testPipe", "testDescr");
        return createSimpleJobForTest(testId, testExternalId, testPipe, actionProperties, mediaUri, mediaType);
    }

    private TransientJob createSimpleJobForTest(long testJobId, String testExternalId, TransientPipeline testPipe, Map<String, String> actionProperties, String mediaUri, String mediaType) throws WfmProcessingException {
        final int testStage = 0;
        final int testPriority = 4;
        final boolean testOutputEnabled = true;
        TransientJob testJob = new TransientJob(testJobId, testExternalId, testPipe, testStage, testPriority, testOutputEnabled, false);
        TransientMedia testMedia = new TransientMedia(next(), ioUtils.findFile(mediaUri).toString());
        testMedia.setLength(300);
        testMedia.setType(mediaType);

        List<TransientMedia> listMedia = Lists.newArrayList(testMedia);
        testJob.setMedia(listMedia);
        TransientStage testTransientStage = new TransientStage("stageName", "stageDescr", ActionType.DETECTION);
        testPipe.getStages().add(testTransientStage);

        TransientAction detectionAction = new TransientAction("detectionAction", "detectionDescription", "detectionAlgo");
        detectionAction.setProperties(actionProperties);
        testTransientStage.getActions().add(detectionAction);
        return testJob;
    }


    // The following set of DetectionSplitter tests specifically test FRAME_RATE_CAP vs. FRAME_INTERVAL property
    // overrides at various category levels (system, action, job, algorithm, and media (with media properties being the highest ranking).

    private List<TransientMedia> createFrameRateCapTestTransientMedia( String mediaFilename, int mediaLength, Map<String, String> mediaProperties ) {
        TransientMedia testMedia = new TransientMedia(next(),
            ioUtils.findFile(mediaFilename).toString());
        testMedia.setLength(mediaLength);
        testMedia.setType("VIDEO");
        for ( Map.Entry mediaProperty : mediaProperties.entrySet() ) {
            testMedia.addMediaSpecificProperty((String)mediaProperty.getKey(), (String)mediaProperty.getValue());
        }

        List<TransientMedia> listMedia = Lists.newArrayList(testMedia);
        return listMedia;
    }

    private TransientJob createFrameRateCapTestTransientJob( long jobId, String externalId, List<TransientMedia> listMedia,
        Map<String, String> actionProperties, Map<String, String> jobProperties, Map<String, Map> algorithmProperties) {

        TransientPipeline dummyPipeline = new TransientPipeline("OCV FACE DETECTION PIPELINE", "TestDetectionSplitter Pipeline");
        TransientStage dummyStageDet = new TransientStage("DETECTION", "dummyDetectionDescription", ActionType.DETECTION);
        TransientAction dummyAction = new TransientAction("FACECV", "dummyDescriptionFACECV", "FACECV");
        dummyAction.setProperties(actionProperties);
        dummyStageDet.getActions().add(dummyAction);
        dummyPipeline.getStages().add(dummyStageDet);

        final int testStage = 0;
        final int testPriority = 4;
        final boolean testOutputEnabled = true;
        TransientJob testJob = new TransientJob(jobId, externalId, dummyPipeline, testStage,
            testPriority, testOutputEnabled, false);

        testJob.setMedia(listMedia);
        testJob.getOverriddenJobProperties().putAll(jobProperties);
        testJob.getOverriddenAlgorithmProperties().putAll(algorithmProperties);
        redis.persistJob(testJob);

        // Need to run MediaInspectionProcessor on the video media, so that inspectMedia method will be called to add FPS and other metadata to the TransientMedia.
        // This is needed for this test to work.
        for (TransientMedia media : redis.getJob(jobId).getMedia()) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.getIn().getHeaders().put(MpfHeaders.JOB_ID, jobId);
            exchange.getIn().setBody(jsonUtils.serialize(media));
            exchange.getOut().setHeader(MpfHeaders.JOB_ID, jobId);
            mediaInspectionProcessor.wfmProcess(exchange);
        }

        // Return the transient job refreshed from REDIS since the mediaInspectionProcessor may have updated the media that was associated with the job.
        return redis.getJob(jobId);
    }

    private DetectionProtobuf.DetectionRequest createFrameRateTestTransientJobAndPerformDetectionSplit(long jobId, String externalId,
                                    Map<String, String> actionProperties, Map<String, String> jobProperties,
                                    Map<String, Map> algorithmProperties, Map<String, String> mediaProperties) throws Exception {

        List<TransientMedia> listMedia = createFrameRateCapTestTransientMedia( "/samples/video_01.mp4", 300, mediaProperties );
        TransientJob testJob = createFrameRateCapTestTransientJob( jobId, externalId, listMedia,
                                                          actionProperties, jobProperties, algorithmProperties);

        // Run the DetectionSplitter on this job, and return the response for evaluation.
        List<Message> responseList = detectionStageSplitter.performSplit(testJob, testJob.getPipeline().getStages().get(0));

        Assert.assertTrue(responseList.size() >= 1);
        Message message = responseList.get(0);
        Assert.assertTrue(message.getBody() instanceof DetectionProtobuf.DetectionRequest);

        return (DetectionProtobuf.DetectionRequest) message.getBody();
    }

    private Double getVideoMediaExpectedComputedFrameInterval(long jobId, double frameRateCapPropertyValue) {
        TransientMedia transientMedia = redis.getJob(jobId).getMedia().get(0);
        double mediaFPS = Double.valueOf(transientMedia.getMetadata("FPS"));
        return Math.max(1, Math.floor(mediaFPS / frameRateCapPropertyValue));
    }

    private Double getVideoMediaExpectedComputedFrameInterval(long jobId, String frameRateCapPropertyValue) {
        return getVideoMediaExpectedComputedFrameInterval(jobId, Double.valueOf(frameRateCapPropertyValue));
    }


    @Test
    // Baseline test: testing override result using current FRAME_INTERVAL and FRAME_RATE_CAP system property settings.
    public void testFrameRateCapTestOverrideOfSystemProperties() throws Exception {
        long localJobId = next();
        String externalId = "baselineFrameRateCapOverrideTest";

        // Testing override of FRAME_INTERVAL and FRAME_RATE_CAP as system properties.
        // Note: to test all use cases, the caller would have to change the defaults in the mpf property file.
        Map<String, String> actionProperties = Collections.emptyMap();
        Map<String, String> jobProperties = Collections.emptyMap();
        Map<String, Map> algorithmProperties = Collections.emptyMap();
        Map<String, String> mediaProperties = Collections.emptyMap();

        // For a job with video media, this test is successful if the sub-job algorithm properties contains COMPUTED_FRAME_INTERVAL property
        // that is equal to the default FRAME_INTERVAL system property (if FRAME_RATE_CAP system property is disabled). If not disabled,
        // then COMPUTED_FRAME_INTERVAL should be derived from FRAME_RATE_CAP system property.
        DetectionProtobuf.DetectionRequest request1 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId, externalId,
                                                                actionProperties, jobProperties, algorithmProperties, mediaProperties);

        // Need to find COMPUTED_FRAME_INTERVAL property, this test passes when:
        // If system property detection.frame.rate.cap is disabled (i.e. set to -1), then the value of
        // COMPUTED_FRAME_INTERVAL is the same as the value of the system property detection.sampling.interval
        // Otherwise, if system property detection.frame.rate.cap is not disabled, then the value of
        // COMPUTED_FRAME_INTERVAL should be derived from FPS and system property detection.frame.rate.cap.

        log.info("testing FrameRateCap overrides, propertiesUtil.getFrameRateCap() = {},  propertiesUtil.getSamplingInterval() = {}.",
                 propertiesUtil.getFrameRateCap(), propertiesUtil.getSamplingInterval());

        if ( propertiesUtil.getFrameRateCap() < 0 ) {
            // System property detection.frame.rate.cap is disabled, value should be same as FRAME_INTERVAL system property.
            Assert.assertTrue(request1.getAlgorithmPropertyList().stream().anyMatch(prop -> {
                return (prop.getPropertyName().equals(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY) &&
                        Double.valueOf(prop.getPropertyValue()) == propertiesUtil.getSamplingInterval());
            }));
        } else {
            // System property detection.frame.rate.cap is not disabled, check value as derived from FPS and system property detection.frame.rate.cap.
            Double expectedComputedFrameInterval = getVideoMediaExpectedComputedFrameInterval(localJobId, propertiesUtil.getFrameRateCap());
            Assert.assertTrue(request1.getAlgorithmPropertyList().stream().anyMatch(prop -> {
                return (prop.getPropertyName().equals(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY) &&
                    Double.valueOf(prop.getPropertyValue()).equals(expectedComputedFrameInterval) );
            }));
        }

    }

    // This general purpose method allows for more easily testing the action property override hierarchy.
    // This method runs the 5-test-cases for various combinations of FRAME_INTERVAL and FRAME_RATE_CAP
    // action property override of system properties.
    public void checkActionPropertyFrameRateCapOverrideTestCases(String externalId, Double frameInterval, Map<String, String> jobProperties,
                                                                 Map<String, Map> algorithmProperties,
                                                                 Map<String, String> mediaProperties) throws Exception {

        // Run 5 FRAME_INTERVAL, FRAME_RATE_CAP media property combinations.
        // 4 test cases will be run. In 5th test case will run with both properties disabled, in this case will fall back to system property values.
        long localJobId1 = next(); // job id for action test case #1
        long localJobId2 = next(); // job id for action test case #2
        long localJobId3 = next(); // job id for action test case #3
        long localJobId4 = next(); // job id for action test case #4
        long localJobId5 = next(); // job id for action test case #5


        // Action properties will take on 5 setting combinations.
        Map<String, String> actionProperties = new HashMap<>();

        // Run actionProperty test case #1: just FRAME_INTERVAL is specified and not disabled.
        actionProperties.clear();
        actionProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameInterval.toString());

        // This test is successful if the sub-job algorithm properties contain COMPUTED_FRAME_INTERVAL property
        // whose value is the same as the input FRAME_INTERVAL action property value. The sub-job algorithm properties
        // should not contain FRAME_INTERVAL property.
        DetectionProtobuf.DetectionRequest request1 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId1, externalId,
            actionProperties, jobProperties, algorithmProperties, mediaProperties);

        // The sub-job algorithm properties should not contain the FRAME_INTERVAL property.
        Assert.assertTrue(request1.getAlgorithmPropertyList().stream()
            .map( p -> p.getPropertyName() )
            .noneMatch(k -> k.equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)));

        // COMPUTED_FRAME_INTERVAL property value should be the same as the action FRAME_INTERVAL property value.
        Assert.assertTrue(request1.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY) &&
                    Double.valueOf(prop.getPropertyValue()).equals(Double.valueOf(actionProperties.get(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY))) );
        }));

        // Run actionProperty test case #2: both FRAME_RATE_CAP and FRAME_INTERVAL are specified and not disabled.
        actionProperties.clear();
        actionProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameInterval.toString());
        actionProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, "10");

        DetectionProtobuf.DetectionRequest request2 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId2, externalId,
            actionProperties, jobProperties, algorithmProperties, mediaProperties);

        // If this test is successful, the sub-job algorithm properties won't contain the FRAME_INTERVAL property and the value of COMPUTED_FRAME_INTERVAL is derived from FRAME_RATE_CAP action property.
        Assert.assertTrue(request2.getAlgorithmPropertyList().stream()
            .map( p -> p.getPropertyName() )
            .noneMatch(k -> k.equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)));

        Assert.assertTrue(request2.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY) &&
                    Double.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(localJobId2, actionProperties.get(MpfConstants.FRAME_RATE_CAP_PROPERTY))) );
        }));

        // Run actionProperty test case #3: FRAME_RATE_CAP is disabled and FRAME_INTERVAL is specified and not disabled.
        actionProperties.clear();
        actionProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameInterval.toString());
        actionProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, "-1");

        // This test is successful if the sub-job algorithm properties contain COMPUTED_FRAME_INTERVAL property
        // whose value is the same as action property FRAME_INTERVAL.
        // The sub-job algorithm properties should not contain FRAME_INTERVAL property.
        DetectionProtobuf.DetectionRequest request3 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId3, externalId,
            actionProperties, jobProperties, algorithmProperties, mediaProperties);

        // The sub-job algorithm properties should not contain the FRAME_INTERVAL property.
        Assert.assertTrue(request3.getAlgorithmPropertyList().stream()
            .map( p -> p.getPropertyName() )
            .noneMatch(k -> k.equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)));

        // COMPUTED_FRAME_INTERVAL property value should be the same as the overridden FRAME_INTERVAL property value.
        Assert.assertTrue(request3.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY) &&
                    Double.valueOf(prop.getPropertyValue()).equals(Double.valueOf(actionProperties.get(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY))) );
        }));

        // Run actionProperty test case #4: FRAME_INTERVAL is disabled and FRAME_RATE_CAP_PROPERTY specified and not disabled.
        actionProperties.clear();
        actionProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, "10");
        actionProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, "-1");

        DetectionProtobuf.DetectionRequest request4 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId4, externalId,
            actionProperties, jobProperties, algorithmProperties, mediaProperties);

        // This test is successful if the sub-job algorithm properties does not contain the FRAME_INTERVAL property.
        Assert.assertTrue(request4.getAlgorithmPropertyList().stream()
            .map( p -> p.getPropertyName() )
            .noneMatch(k -> k.equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)));

        // This test is successful if the value of COMPUTED_FRAME_INTERVAL is the same as the value derived from action properties FRAME_RATE_CAP.
        Assert.assertTrue(request4.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY) &&
                Double.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(localJobId4, actionProperties.get(MpfConstants.FRAME_RATE_CAP_PROPERTY))) );
        }));

        // Run actionProperty test case #5: both FRAME_RATE_CAP and FRAME_INTERVAL are disabled.
        // In this case setting should fall back to system property values.
        actionProperties.clear();
        actionProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, "-1");
        actionProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, "-1");

        // This test is successful if the sub-job algorithm properties contain COMPUTED_FRAME_INTERVAL property
        // whose value is the same as system property FRAME_INTERVAL (if system property detection.frame.rate.cap is disabled).
        // Otherwise, COMPUTED_FRAME_INTERVAL property should be derived from detection.frame.rate.cap.
        // The sub-job algorithm properties should never contain FRAME_INTERVAL property.
        DetectionProtobuf.DetectionRequest request5 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId5, externalId,
            actionProperties, jobProperties, algorithmProperties, mediaProperties);

        // The sub-job algorithm properties should not contain the FRAME_INTERVAL property.
        Assert.assertTrue(request5.getAlgorithmPropertyList().stream()
            .map( p -> p.getPropertyName() )
            .noneMatch(k -> k.equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)));

        // COMPUTED_FRAME_INTERVAL property value should be the same as the overridden FRAME_INTERVAL system property value.
        Assert.assertTrue(request5.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY) &&
                Double.valueOf(prop.getPropertyValue()) == propertiesUtil.getSamplingInterval() );
        }));

        // When done with these tests, clear the test jobs from REDIS.
        redis.clearJob(localJobId1);
        redis.clearJob(localJobId2);
        redis.clearJob(localJobId3);
        redis.clearJob(localJobId4);
        redis.clearJob(localJobId5);
    }

    @Test
    // Test for action (i.e. pipeline) properties override of system properties
    public void testActionPropertyFrameRateCapOverrideOfProperties() throws Exception {
        String externalId = "actionFrameRateCapOverrideTest";
        Double frameInterval = 20.0;

        // Note: at this test level, the following property maps should be empty (override using these property maps are evaluated elsewhere).
        Map<String, String> jobProperties = Collections.emptyMap();
        Map<String, Map> algorithmProperties = Collections.emptyMap();
        Map<String, String> mediaProperties = Collections.emptyMap();

        // Call method to test FRAME_RATE_CAP or FRAME_INTERVAL in various combinations to test action property override of
        // these two properties over system properties.
        checkActionPropertyFrameRateCapOverrideTestCases(externalId, frameInterval,
                                                         jobProperties, algorithmProperties, mediaProperties);

    }

    // This general purpose method allows for more easily testing the job property override hierarchy.
    // This method runs the 5-test-cases for various combinations of FRAME_INTERVAL and FRAME_RATE_CAP
    // job property override of action properties.
    public void checkJobPropertyFrameRateCapOverrideTestCases(String externalId, Double frameInterval,
                                                              Map<String, String> actionProperties,
                                                              Map<String, Map> algorithmProperties,
                                                              Map<String, String> mediaProperties) throws Exception {

        // Run 5 FRAME_INTERVAL, FRAME_RATE_CAP job property combinations.
        // 4 test cases will be run with 5th test case to be run with both properties disabled - override will fall back to action properties.
        long localJobId1 = next(); // job property test case #1
        long localJobId2 = next(); // job property test case #2
        long localJobId3 = next(); // job property test case #3
        long localJobId4 = next(); // job property test case #4

        // Job properties will be varied for all test cases.
        Map<String, String> jobProperties = new HashMap<>();

        // Run jobProperty test case #1: just FRAME_INTERVAL job property is specified and not disabled.
        jobProperties.clear();
        jobProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameInterval.toString());

        // This test is successful if the sub-job algorithm properties contain COMPUTED_FRAME_INTERVAL property
        // whose value is the same as the input FRAME_INTERVAL job property value. The sub-job algorithm properties
        // should not contain FRAME_INTERVAL property.
        DetectionProtobuf.DetectionRequest request1 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId1, externalId,
            actionProperties, jobProperties, algorithmProperties, mediaProperties);

        // The sub-job algorithm properties should not contain the FRAME_INTERVAL property.
        Assert.assertTrue(request1.getAlgorithmPropertyList().stream()
            .map( p -> p.getPropertyName() )
            .noneMatch(k -> k.equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)));

        // COMPUTED_FRAME_INTERVAL property value should be the same as the overridden FRAME_INTERVAL job property value.
        Assert.assertTrue(request1.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY) &&
                    Double.valueOf(prop.getPropertyValue()).equals(Double.valueOf(jobProperties.get(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY))) );
        }));

        // Run jobProperty test case #2: both FRAME_RATE_CAP and FRAME_INTERVAL are specified and not disabled.
        jobProperties.clear();
        jobProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameInterval.toString());
        jobProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, "20");

        // This test is successful if the sub-job algorithm properties contain COMPUTED_FRAME_INTERVAL property
        // whose value is expectedJobPropertyComputedFrameInterval.
        // The sub-job algorithm properties should not contain FRAME_INTERVAL property.
        DetectionProtobuf.DetectionRequest request2 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId2, externalId,
            actionProperties, jobProperties, algorithmProperties, mediaProperties);

        // The sub-job algorithm properties should not contain the FRAME_INTERVAL property.
        Assert.assertTrue(request2.getAlgorithmPropertyList().stream()
            .map( p -> p.getPropertyName() )
            .noneMatch(k -> k.equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)));

        // The value of COMPUTED_FRAME_INTERVAL should be derived from job properties FRAME_RATE_CAP
        Assert.assertTrue(request2.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY) &&
                Double.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(localJobId2, jobProperties.get(MpfConstants.FRAME_RATE_CAP_PROPERTY))) );
        }));

        // Run jobProperty test case #3: FRAME_RATE_CAP is disabled and FRAME_INTERVAL is specified and not disabled.
        jobProperties.clear();
        jobProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameInterval.toString());
        jobProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, "-1");

        // This test is successful if the sub-job algorithm properties contain COMPUTED_FRAME_INTERVAL property
        // whose value is the same as job property FRAME_INTERVAL.
        // The sub-job algorithm properties should not contain FRAME_INTERVAL property.
        DetectionProtobuf.DetectionRequest request3 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId3, externalId,
            actionProperties, jobProperties, algorithmProperties, mediaProperties);

        // The sub-job algorithm properties should not contain the FRAME_INTERVAL property.
        Assert.assertTrue(request3.getAlgorithmPropertyList().stream()
            .map( p -> p.getPropertyName() )
            .noneMatch(k -> k.equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)));

        // COMPUTED_FRAME_INTERVAL property value should be the same as the overridden FRAME_INTERVAL property value.
        Assert.assertTrue(request3.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY) &&
                    Double.valueOf(prop.getPropertyValue()).equals(Double.valueOf(jobProperties.get(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY))) );
        }));

        // Run jobProperty test case #4: FRAME_RATE_CAP is specified and not disabled and FRAME_INTERVAL disabled.
        jobProperties.clear();
        jobProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, "-1");
        jobProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, "20");

        DetectionProtobuf.DetectionRequest request4 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId4, externalId,
            actionProperties, jobProperties, algorithmProperties, mediaProperties);

        // The sub-job algorithm properties should not contain the FRAME_INTERVAL property.
        Assert.assertTrue(request4.getAlgorithmPropertyList().stream()
            .map( p -> p.getPropertyName() )
            .noneMatch(k -> k.equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)));

        // COMPUTED_FRAME_INTERVAL property value should be derived from job property FRAME_RATE_CAP and the videos FPS.
        Assert.assertTrue(request4.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY) &&
                Double.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(localJobId4, jobProperties.get(MpfConstants.FRAME_RATE_CAP_PROPERTY))) );
        }));

        // Run jobProperty test case #5: both FRAME_RATE_CAP and FRAME_INTERVAL are disabled.
        // With both these properties disabled, in this case should fall back to action property overrides.
        jobProperties.clear();
        jobProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, "-1");
        jobProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, "-1");

        // Run the job property override of action properties tests against jobProperty test case #5 (both properties disabled).
        checkActionPropertyFrameRateCapOverrideTestCases(externalId, frameInterval, jobProperties, algorithmProperties, mediaProperties);

        // When done with these tests, clear the test jobs from REDIS.
        redis.clearJob(localJobId1);
        redis.clearJob(localJobId2);
        redis.clearJob(localJobId3);
        redis.clearJob(localJobId4);

    }

    @Test
    // job properties can override action (i.e. pipeline) or system properties
    public void testJobPropertyFrameRateCapOverrideOfProperties() throws Exception {

        String externalId = "jobFrameRateCapOverrideTest";
        Double frameInterval = 30.0;

        // Note: at this test level, the following property maps should be empty (override using these property maps are evaluated elsewhere).
        Map<String, String> actionProperties = Collections.emptyMap();
        Map<String, Map> algorithmProperties = Collections.emptyMap();
        Map<String, String> mediaProperties = Collections.emptyMap();

        // Call method to test FRAME_RATE_CAP or FRAME_INTERVAL in various combinations to test job property override of
        // these two properties over action properties.
        checkJobPropertyFrameRateCapOverrideTestCases(externalId, frameInterval,
                                                      actionProperties, algorithmProperties, mediaProperties);
    }

    // This general purpose method allows for more easily testing the algorithm property override hierarchy.
    // This method runs the 5-test-cases for various combinations of FRAME_INTERVAL and FRAME_RATE_CAP
    // algorithm property override of job properties.
    public void checkAlgorithmPropertyFrameRateCapOverrideTestCases( String externalId, Double frameInterval,
                                                        Map<String, String> actionProperties,
                                                        Map<String, String> jobProperties,
                                                        Map<String, String> mediaProperties) throws Exception {

        // Run 5 FRAME_INTERVAL, FRAME_RATE_CAP algorithm property combinations.
        // 4 test cases will be run with 5th test case to be run with both properties disabled - override will fall back to job properties.
        long localJobId1 = next(); // algorithm property test case #1
        long localJobId2 = next(); // algorithm property test case #2
        long localJobId3 = next(); // algorithm property test case #3
        long localJobId4 = next(); // algorithm property test case #4

        // This method will test FRAME_RATE_CAP or FRAME_INTERVAL in various combinations to test algorithm property override of
        // these two properties over job properties.
        Map<String, Map> algorithmProperties = new HashMap();

        // Run algorithmProperty test case #1: just FRAME_INTERVAL algorithm property is specified and not disabled.
        algorithmProperties.clear();
        Map<String, String> faceCvAlgorithmProperties = new HashMap();
        faceCvAlgorithmProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameInterval.toString());
        algorithmProperties.put("FACECV", faceCvAlgorithmProperties);

        // This test is successful if the sub-job algorithm properties contains COMPUTED_FRAME_INTERVAL property whose
        // value is the same as the FRAME_INTERVAL property specified in the algorithm properties. The sub-job algorithm properties
        // should not contain the FRAME_INTERVAL property
        DetectionProtobuf.DetectionRequest request1 =  createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId1, externalId,
            actionProperties, jobProperties, algorithmProperties, mediaProperties);

        // Test the sub-job algorithm properties don't contain FRAME_INTERVAL.
        Assert.assertTrue(request1.getAlgorithmPropertyList().stream()
            .map( p -> p.getPropertyName() )
            .noneMatch(k -> k.equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)));

        // COMPUTED_FRAME_INTERVAL property value should be the same as the overridden FRAME_INTERVAL property value.
        Assert.assertTrue(request1.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY) &&
                    Double.valueOf(prop.getPropertyValue()).equals(frameInterval) );
        }));

        // Run algorithmProperty test case #2: FRAME_RATE_CAP and FRAME_INTERVAL are both specified and not disabled.
        algorithmProperties.clear();
        faceCvAlgorithmProperties = new HashMap();
        faceCvAlgorithmProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, "10");
        faceCvAlgorithmProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameInterval.toString());
        algorithmProperties.put("FACECV", faceCvAlgorithmProperties);

        DetectionProtobuf.DetectionRequest request2 =  createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId2, externalId,
            actionProperties, jobProperties, algorithmProperties, mediaProperties);

        // Test the sub-job algorithm properties don't contain FRAME_INTERVAL.
        Assert.assertTrue(request2.getAlgorithmPropertyList().stream()
            .map( p -> p.getPropertyName() )
            .noneMatch(k -> k.equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)));

        // COMPUTED_FRAME_INTERVAL property value should be derived from algorithm properties FACECV FRAME_RATE_CAP.
        String frameRateCapPropertyValue = faceCvAlgorithmProperties.get(MpfConstants.FRAME_RATE_CAP_PROPERTY);
        Double expectedValue2 = getVideoMediaExpectedComputedFrameInterval(localJobId2,frameRateCapPropertyValue);
        Assert.assertTrue(request2.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY) &&
                    Double.valueOf(prop.getPropertyValue()).equals(expectedValue2));
        }));

        // Run algorithmProperty test case #3: FRAME_RATE_CAP is disabled and FRAME_INTERVAL is specified and not disabled.
        algorithmProperties.clear();
        faceCvAlgorithmProperties = new HashMap();
        faceCvAlgorithmProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, "-1");
        faceCvAlgorithmProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameInterval.toString());
        algorithmProperties.put("FACECV", faceCvAlgorithmProperties);

        // This test is successful if the sub-job algorithm properties contain COMPUTED_FRAME_INTERVAL property
        // whose value is the same as algorithm property FRAME_INTERVAL.
        // The sub-job algorithm properties should not contain FRAME_INTERVAL property.
        DetectionProtobuf.DetectionRequest request3 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId3, externalId,
            actionProperties, jobProperties, algorithmProperties, mediaProperties);

        // The sub-job algorithm properties should not contain the FRAME_INTERVAL property.
        Assert.assertTrue(request3.getAlgorithmPropertyList().stream()
            .map( p -> p.getPropertyName() )
            .noneMatch(k -> k.equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)));

        // COMPUTED_FRAME_INTERVAL property value should be the same as the overridden FRAME_INTERVAL property value.
        Assert.assertTrue(request3.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY) &&
                Double.valueOf(prop.getPropertyValue()).equals(frameInterval) );
        }));

        // Run algorithmProperty test case #4: FRAME_RATE_CAP is specified and not disabled and FRAME_INTERVAL disabled.
        algorithmProperties.clear();
        faceCvAlgorithmProperties = new HashMap();
        faceCvAlgorithmProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, "10");
        faceCvAlgorithmProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, "-1");
        algorithmProperties.put("FACECV", faceCvAlgorithmProperties);

        DetectionProtobuf.DetectionRequest request4 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId4, externalId,
            actionProperties, jobProperties, algorithmProperties, mediaProperties);

        // The sub-job algorithm properties should not contain the FRAME_INTERVAL property.
        Assert.assertTrue(request4.getAlgorithmPropertyList().stream()
            .map( p -> p.getPropertyName() )
            .noneMatch(k -> k.equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)));

        // COMPUTED_FRAME_INTERVAL property value should be derived from algorithm properties FACECV FRAME_RATE_CAP.
        frameRateCapPropertyValue = faceCvAlgorithmProperties.get(MpfConstants.FRAME_RATE_CAP_PROPERTY);
        Double expectedValue4 = getVideoMediaExpectedComputedFrameInterval(localJobId4,frameRateCapPropertyValue);
        Assert.assertTrue(request4.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY) &&
                Double.valueOf(prop.getPropertyValue()).equals(expectedValue4) );
        }));

        // Run algorithmProperty test case #5: both FRAME_RATE_CAP and FRAME_INTERVAL are disabled.
        // With both these properties disabled, in this case should fall back to job property overrides.
        algorithmProperties.clear();
        faceCvAlgorithmProperties = new HashMap();
        faceCvAlgorithmProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, "-1");
        faceCvAlgorithmProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, "-1");
        algorithmProperties.put("FACECV", faceCvAlgorithmProperties);

        // Run the algorithm property override test of job properties tests against algorithmProperty test case #5 (both FRAME_RATE_CAP and FRAME_INTERVAL disabled).
        checkJobPropertyFrameRateCapOverrideTestCases(externalId, frameInterval,
                                                      actionProperties, algorithmProperties, mediaProperties);

        // When done with these tests, clear the test jobs from REDIS.
        redis.clearJob(localJobId1);
        redis.clearJob(localJobId2);
        redis.clearJob(localJobId3);
        redis.clearJob(localJobId4);
    }

    @Test
    public void testAlgorithmPropertyFrameRateCapOverrideOfProperties() throws Exception {

        String externalId = "algFrameRateCapOverrideTest";
        Double frameInterval = 5.0;

        // Note: at this test level, the following property maps should be empty (override using these property maps are evaluated elsewhere).
        Map<String, String> actionProperties = Collections.emptyMap();
        Map<String, String> jobProperties = Collections.emptyMap();
        Map<String, String> mediaProperties = Collections.emptyMap();

        // Call method to test FRAME_RATE_CAP or FRAME_INTERVAL in various combinations to test algorithm property override of
        // these two properties over job properties.
        checkAlgorithmPropertyFrameRateCapOverrideTestCases(externalId,  frameInterval,
                                                            actionProperties, jobProperties, mediaProperties);

    }

    // This general purpose method allows for more easily testing the media property override hierarchy.
    // This method runs the 5-test-cases for various combinations of FRAME_INTERVAL and FRAME_RATE_CAP
    // media property override of algorithm properties.
    public void checkMediaPropertyFrameRateCapOverrideTestCases(String externalId, Double frameInterval,
                                                                Map<String, String> actionProperties,
                                                                Map<String, String> jobProperties,
                                                                Map<String, Map> algorithmProperties) throws Exception {

        // Run 5 FRAME_INTERVAL, FRAME_RATE_CAP media property combinations.
        // 4 test cases will be run with 5th test case to be run with both properties disabled - override will fall back to algorithm properties.
        long localJobId1 = next(); // media property test case #1
        long localJobId2 = next(); // media property test case #2
        long localJobId3 = next(); // media property test case #3
        long localJobId4 = next(); // media property test case #4

        // This method will test FRAME_RATE_CAP or FRAME_INTERVAL in various combinations to test media property override of
        // these two properties over algorithm properties.
        Map<String, String> mediaProperties = new HashMap();

        // Run mediaProperty test case #1: FRAME_INTERVAL is specified and FRAME_RATE_CAP is not specified.
        mediaProperties.clear();
        mediaProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameInterval.toString());

        // This test is successful if the sub-job algorithm properties contains COMPUTED_FRAME_INTERVAL property whose
        // value is the same as the FRAME_INTERVAL property specified in the media properties. The sub-job algorithm properties
        // should not contain the FRAME_INTERVAL property
        DetectionProtobuf.DetectionRequest request1 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId1, externalId,
                                                            actionProperties, jobProperties, algorithmProperties, mediaProperties);

        // Test the sub-job algorithm properties don't contain FRAME_INTERVAL.
        Assert.assertTrue(request1.getAlgorithmPropertyList().stream()
            .map(p -> p.getPropertyName())
            .noneMatch(k -> k.equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)));

        // COMPUTED_FRAME_INTERVAL property value should be the same as the overridden FRAME_INTERVAL property value.
        Assert.assertTrue(request1.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY) &&
                Double.valueOf(prop.getPropertyValue()).equals(frameInterval));
        }));

        // Run mediaProperty test case #2: FRAME_RATE_CAP and FRAME_INTERVAL are both specified and not disabled.
        mediaProperties.clear();
        mediaProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameInterval.toString());
        mediaProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, "10");

        DetectionProtobuf.DetectionRequest request2 = createFrameRateTestTransientJobAndPerformDetectionSplit(
            localJobId2, externalId,
            actionProperties, jobProperties, algorithmProperties, mediaProperties);

        // Test the sub-job algorithm properties don't contain FRAME_INTERVAL.
        Assert.assertTrue(request2.getAlgorithmPropertyList().stream()
            .map(p -> p.getPropertyName())
            .noneMatch(k -> k.equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)));

        // COMPUTED_FRAME_INTERVAL property value should be derived from media property FRAME_RATE_CAP.
        Assert.assertTrue(request2.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY) &&
                Double.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(localJobId2, mediaProperties.get(MpfConstants.FRAME_RATE_CAP_PROPERTY))) );
        }));

        // Run mediaProperty test case #3: FRAME_RATE_CAP is disabled and FRAME_INTERVAL is specified and not disabled.
        mediaProperties.clear();
        mediaProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameInterval.toString());
        mediaProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, "-1");

        // This test is successful if the sub-job algorithm properties contain COMPUTED_FRAME_INTERVAL property
        // whose value is the same as media property FRAME_INTERVAL.
        // The sub-job algorithm properties should not contain FRAME_INTERVAL property.
        DetectionProtobuf.DetectionRequest request3 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId3, externalId,
                                                        actionProperties, jobProperties, algorithmProperties, mediaProperties);

        // The sub-job algorithm properties should not contain the FRAME_INTERVAL property.
        Assert.assertTrue(request3.getAlgorithmPropertyList().stream()
            .map(p -> p.getPropertyName())
            .noneMatch(k -> k.equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)));

        // COMPUTED_FRAME_INTERVAL property value should be the same as the media FRAME_INTERVAL property value.
        Assert.assertTrue(request3.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY) &&
                Double.valueOf(prop.getPropertyValue()).equals(frameInterval));
        }));

        // Run mediaProperty test case #4: FRAME_RATE_CAP specified and not disabled and FRAME_INTERVAL disabled.
        mediaProperties.clear();
        mediaProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, "-1");
        mediaProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, "10");

        DetectionProtobuf.DetectionRequest request4 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId4, externalId,
                                                        actionProperties, jobProperties, algorithmProperties, mediaProperties);

        // The sub-job algorithm properties should not contain the FRAME_INTERVAL property.
        Assert.assertTrue(request4.getAlgorithmPropertyList().stream()
            .map(p -> p.getPropertyName())
            .noneMatch(k -> k.equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)));

        // COMPUTED_FRAME_INTERVAL property value should be derived from media property FRAME_RATE_CAP .
        Assert.assertTrue(request4.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY) &&
                Double.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(localJobId4, mediaProperties.get(MpfConstants.FRAME_RATE_CAP_PROPERTY))) );
        }));

        // Run mediaProperty test case #5: both FRAME_RATE_CAP and FRAME_INTERVAL are disabled.
        // With both these properties disabled, in this case should fall back to algorithm property overrides.
        mediaProperties.clear();
        mediaProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, "-1");
        mediaProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, "-1");

        // Run the algorithm property override test of job properties tests against algorithmProperty test case #5 (both FRAME_RATE_CAP and FRAME_INTERVAL disabled).
        checkAlgorithmPropertyFrameRateCapOverrideTestCases(externalId, frameInterval,
                                                            actionProperties, jobProperties, mediaProperties);

        // When done with these tests, clear the test jobs from REDIS.
        redis.clearJob(localJobId1);
        redis.clearJob(localJobId2);
        redis.clearJob(localJobId3);
        redis.clearJob(localJobId4);

    }

    @Test
    public void testMediaPropertyFrameRateCapOverrideOfProperties() throws Exception {

        String externalId = "mediaFrameRateCapOverrideTest";
        Double frameInterval = 3.0;

        // Note: at this test level, the following property maps should be empty (override using these property maps are evaluated elsewhere).
        Map<String, String> actionProperties = Collections.emptyMap();
        Map<String, String> jobProperties = Collections.emptyMap();
        Map<String, Map> algorithmProperties = Collections.emptyMap();

        // Call method to test FRAME_RATE_CAP or FRAME_INTERVAL in various combinations to test media property override of
        // these two properties over algorithm properties.
        checkMediaPropertyFrameRateCapOverrideTestCases(externalId,  frameInterval,
                                                        actionProperties, jobProperties, algorithmProperties);

    }

}
