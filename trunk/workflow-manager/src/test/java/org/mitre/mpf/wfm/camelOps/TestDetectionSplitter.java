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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

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

    private PropertiesUtil _mockPropertiesUtil;

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
    // overrides at various category levels (system, action, job, algorithm, and media, with media properties being the highest ranking).
//
//    private List<TransientMedia> createFrameRateCapTestTransientMedia( String mediaFilename, int mediaLength, Map<String, String> mediaProperties ) {
//        TransientMedia testMedia = new TransientMedia(next(), ioUtils.findFile(mediaFilename).toString());
//        testMedia.setLength(mediaLength);
//        testMedia.setType("VIDEO");
//        for ( Map.Entry mediaProperty : mediaProperties.entrySet() ) {
//            testMedia.addMediaSpecificProperty((String)mediaProperty.getKey(), (String)mediaProperty.getValue());
//        }
//
//        List<TransientMedia> listMedia = Lists.newArrayList(testMedia);
//        return listMedia;
//    }

    private TransientJob createFrameRateCapTestTransientJob( long jobId, String externalId,
        Map<String, String> actionProperties, Map<String, String> jobProperties,
        Map<String, Map> algorithmProperties, Map<String,String> mediaProperties) {

        log.info("createFrameRateCapTestTransientJob, debug: mediaProperties=" + mediaProperties);
        log.info("createFrameRateCapTestTransientJob, debug: algorithmProperties=" + algorithmProperties);
        log.info("createFrameRateCapTestTransientJob, debug: jobProperties=" + jobProperties);
        log.info("createFrameRateCapTestTransientJob, debug: actionProperties=" + actionProperties);

        TransientMedia testMedia = new TransientMedia(next(), ioUtils.findFile("/samples/video_01.mp4").toString());
        testMedia.setLength(300);
        testMedia.setType("VIDEO");
        for ( Map.Entry mediaProperty : mediaProperties.entrySet() ) {
            testMedia.addMediaSpecificProperty((String)mediaProperty.getKey(), (String)mediaProperty.getValue());

        }
        List<TransientMedia> listMedia = Lists.newArrayList(testMedia);
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

    // need to store mediaFPS as a class property so we can determine computed frame interval for verification of the frame rate cap tests.
    double mediaFPS;

    private DetectionProtobuf.DetectionRequest createFrameRateTestTransientJobAndPerformDetectionSplit(long jobId, String externalId,
                                    Integer frameIntervalMediaPropertyValue, Integer frameRateCapMediaPropertyValue,
                                    Integer frameIntervalAlgorithmPropertyValue, Integer frameRateCapAlgorithmPropertyValue,
                                    Integer frameIntervalJobPropertyValue, Integer frameRateCapJobPropertyValue,
                                    Integer frameIntervalActionPropertyValue, Integer frameRateCapActionPropertyValue) throws Exception {

        Map<String, String> actionProperties = new HashMap();
        Map<String, String> jobProperties = new HashMap();
        Map<String, Map> algorithmProperties = new HashMap();
        Map<String, String> mediaProperties = new HashMap();

        if ( frameIntervalActionPropertyValue != null && frameIntervalActionPropertyValue > 0 )
            actionProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameIntervalActionPropertyValue.toString());
        if ( frameRateCapActionPropertyValue != null && frameRateCapActionPropertyValue > 0 )
            actionProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, frameRateCapActionPropertyValue.toString());

        if ( frameIntervalJobPropertyValue != null && frameIntervalJobPropertyValue > 0 )
            jobProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameIntervalJobPropertyValue.toString());
        if ( frameRateCapJobPropertyValue != null && frameRateCapJobPropertyValue > 0 )
            jobProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, frameRateCapJobPropertyValue.toString());

        if ( frameIntervalAlgorithmPropertyValue != null && frameIntervalAlgorithmPropertyValue > 0 ) {
            Map<String, String> faceCvAlgorithmProperties = new HashMap();
            faceCvAlgorithmProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameIntervalAlgorithmPropertyValue.toString());
            algorithmProperties.put("FACECV", faceCvAlgorithmProperties);
        }
        if ( frameRateCapAlgorithmPropertyValue != null && frameRateCapAlgorithmPropertyValue > 0 ) {
            Map<String, String> faceCvAlgorithmProperties = algorithmProperties.get("FACECV");
            if ( faceCvAlgorithmProperties == null ) faceCvAlgorithmProperties = new HashMap();
            faceCvAlgorithmProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, frameRateCapAlgorithmPropertyValue.toString());
            algorithmProperties.put("FACECV", faceCvAlgorithmProperties);
        }

        if ( frameIntervalMediaPropertyValue != null && frameIntervalMediaPropertyValue > 0 )
            mediaProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameIntervalMediaPropertyValue.toString());
        if ( frameRateCapMediaPropertyValue != null && frameRateCapMediaPropertyValue > 0 )
            mediaProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, frameRateCapMediaPropertyValue.toString());

        TransientJob testJob = createFrameRateCapTestTransientJob( jobId, externalId,
                                                          actionProperties, jobProperties, algorithmProperties, mediaProperties );

        log.info("createFrameRateTestTransientJobAndPerformDetectionSplit, debug: jobId = " + jobId +" media(0).mediaProperties = " + testJob.getMedia().get(0).getMediaSpecificProperties());
        log.info("createFrameRateTestTransientJobAndPerformDetectionSplit, debug: jobId = " + jobId +" algProperties = " + testJob.getOverriddenAlgorithmProperties() );
        log.info("createFrameRateTestTransientJobAndPerformDetectionSplit, debug: jobId = " + jobId +" jobProperties = " + testJob.getOverriddenJobProperties() );

        // Get this jobs media FPS so it can be used later for test verification.
        TransientMedia transientMedia = redis.getJob(jobId).getMedia().get(0);
        mediaFPS = Double.valueOf(transientMedia.getMetadata("FPS"));

        // Run the DetectionSplitter on this job, and return the response for evaluation.
        log.info("createFrameRateTestTransientJobAndPerformDetectionSplit, debug: jobId = " + jobId + ", TransientStage(0) is testJob.getPipeline().getStages().get(0) =" + testJob.getPipeline().getStages().get(0));
        log.info("createFrameRateTestTransientJobAndPerformDetectionSplit, debug: jobId = " + jobId + ", num stages = " + testJob.getPipeline().getStages().size());
        log.info("createFrameRateTestTransientJobAndPerformDetectionSplit, debug: jobId = " + jobId + ", TransientStage(0).action(0) actionProperties are testJob.getPipeline().getStages().get(0).getActions().get(0).getProperties() =" + testJob.getPipeline().getStages().get(0).getActions().get(0).getProperties());
        log.info("createFrameRateTestTransientJobAndPerformDetectionSplit, debug: jobId = " + jobId + ", num stage0 actions = " + testJob.getPipeline().getStages().get(0).getActions().size());


        List<Message> responseList = detectionStageSplitter.performSplit(testJob, testJob.getPipeline().getStages().get(0));

        Assert.assertTrue(responseList.size() >= 1);
        Message message = responseList.get(0);
        Assert.assertTrue(message.getBody() instanceof DetectionProtobuf.DetectionRequest);

        return (DetectionProtobuf.DetectionRequest) message.getBody();
    }

    private int getVideoMediaExpectedComputedFrameInterval(double frameRateCapPropertyValue) {
        return (int) Math.max(1, Math.floor(mediaFPS / frameRateCapPropertyValue));
    }

    private int getVideoMediaExpectedComputedFrameInterval(long jobId, double frameRateCapPropertyValue) {
        TransientMedia transientMedia = redis.getJob(jobId).getMedia().get(0);
        double mediaFPS = Double.valueOf(transientMedia.getMetadata("FPS"));
        return (int) Math.max(1, Math.floor(mediaFPS / frameRateCapPropertyValue));
    }

//    private Double getVideoMediaExpectedComputedFrameInterval(long jobId, String frameRateCapPropertyValue) {
//        return getVideoMediaExpectedComputedFrameInterval(jobId, Double.valueOf(frameRateCapPropertyValue));
//    }


    @Test
    // Baseline test: testing override result using current FRAME_INTERVAL and FRAME_RATE_CAP system property settings.
    public void testFrameRateCapTestOverrideOfSystemProperties() throws Exception {
        long localJobId = next();
        String externalId = "baselineFrameRateCapOverrideTest";

        // Testing override of FRAME_INTERVAL and FRAME_RATE_CAP as system properties.
        // Note: to test all use cases, the caller would have to change the defaults in the mpf property file.
        Integer frameIntervalActionPropertyValue = null;
        Integer frameRateCapActionPropertyValue = null;
        Integer frameIntervalJobPropertyValue = null;
        Integer frameRateCapJobPropertyValue = null;
        Integer frameIntervalAlgorithmPropertyValue = null;
        Integer frameRateCapAlgorithmPropertyValue = null;
        Integer frameIntervalMediaPropertyValue = null;
        Integer frameRateCapMediaPropertyValue = null;

        // For a job with video media, this test is successful if the sub-job algorithm properties contains FRAME_INTERVAL property
        // that is equal to the default FRAME_INTERVAL system property (if FRAME_RATE_CAP system property is disabled). If not disabled,
        // then FRAME_INTERVAL should be derived from FRAME_RATE_CAP system property.
        DetectionProtobuf.DetectionRequest request1 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId, externalId,
            frameIntervalActionPropertyValue, frameRateCapActionPropertyValue, frameIntervalJobPropertyValue, frameRateCapJobPropertyValue,
            frameIntervalAlgorithmPropertyValue, frameRateCapAlgorithmPropertyValue, frameIntervalMediaPropertyValue, frameRateCapMediaPropertyValue);

        log.info("testing FrameRateCap overrides, propertiesUtil.getFrameRateCap() = {},  propertiesUtil.getSamplingInterval() = {}.",
                 propertiesUtil.getFrameRateCap(), propertiesUtil.getSamplingInterval());

        if ( propertiesUtil.getFrameRateCap() < 0 ) {
            // System property detection.frame.rate.cap is disabled, value should be same as FRAME_INTERVAL system property.
            Assert.assertTrue(request1.getAlgorithmPropertyList().stream().anyMatch(prop -> {
                return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                        Double.valueOf(prop.getPropertyValue()) == propertiesUtil.getSamplingInterval());
            }));
        } else {
            // System property detection.frame.rate.cap is not disabled, check value as derived from FPS and system property detection.frame.rate.cap.
            int expectedComputedFrameInterval = getVideoMediaExpectedComputedFrameInterval(localJobId, propertiesUtil.getFrameRateCap());
            Assert.assertTrue(request1.getAlgorithmPropertyList().stream().anyMatch(prop -> {
                return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                        Integer.valueOf(prop.getPropertyValue()).equals(expectedComputedFrameInterval) );
            }));
        }

    }
    
    // This method runs the FRAME_RATE_CAP and FRAME_INTERVAL in 2x2=4 combinations at the system property level and 3x9=9 combinations at each 
    // higher property level (i.e. action, job, algorithm, media), for a total of 4 + 9 + 9 + 9 + 9 = 40 test cases
    @Test
    public void testFrameRateCapOverride() throws Exception {
        String baselineExternalId = "frameRateCapOverrideTest";
        
        // assign values based upon property level so we can better track application of property values
        int actLevelFrameInterval = 2;
        int jobLevelFrameInterval = 3;
        int algLevelFrameInterval = 5;
        int medLevelFrameInterval = 7;
        int actLevelFrameRateCap = 10;
        int jobLevelFrameRateCap = 15;
        int algLevelFrameRateCap = 20;
        int medLevelFrameRateCap = 25;

        Integer parameterDisable = -1;
        Integer parameterNotSpecified = null;

        long localJobId;
        DetectionProtobuf.DetectionRequest request;

        // TODO use Mock to test system property combinations. For now, just use what is available in PropertiesUtil

        // Tests 1-3: vary combination of media property FRAME_INTERVAL keeping FRAME_RATE_CAP consistent. Lower property levels not specified

        // Test1: media level test with both FRAME_INTERVAL and FRAME_RATE_CAP specified (FRAME_RATE_CAP override applies). Lower property level not specified.
        localJobId = next();
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId, baselineExternalId+Long.toString(next()),
                                                                                                                medLevelFrameInterval, medLevelFrameRateCap,
                                                                                                                parameterNotSpecified, parameterNotSpecified,
                                                                                                                parameterNotSpecified, parameterNotSpecified,
                                                                                                                parameterNotSpecified, parameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from the frame rate cap media property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(medLevelFrameRateCap)) );
        }));

        // Once done with the test, remove the test job from REDIS.
        redis.clearJob(localJobId);

        // Test2: media level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP specified. Lower property level not specified.
        localJobId = next();
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId, baselineExternalId+Long.toString(next()),
                                                                            parameterNotSpecified, medLevelFrameRateCap,
                                                                            parameterNotSpecified, parameterNotSpecified,
                                                                            parameterNotSpecified, parameterNotSpecified,
                                                                            parameterNotSpecified, parameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from frame rate cap media property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(medLevelFrameRateCap)) );
        }));

        // Once done with the test, remove the test job from REDIS.
        redis.clearJob(localJobId);

        // Test3: media level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP specified. Lower property level not specified.
        localJobId = next();
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId, baselineExternalId+Long.toString(next()),
                                                                            parameterDisable, medLevelFrameRateCap,
                                                                            parameterNotSpecified, parameterNotSpecified,
                                                                            parameterNotSpecified, parameterNotSpecified,
                                                                            parameterNotSpecified, parameterNotSpecified);
        // In this case, resultant FRAME_INTERVAL property value should be derived from frame rate cap media property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(medLevelFrameRateCap)) );
        }));

        // Once done with the test, remove the test job from REDIS.
        redis.clearJob(localJobId);

        // Tests 4-6: vary combination of media property FRAME_INTERVAL keeping FRAME_RATE_CAP consistent, with one-down-lower property level specified.

        // Test4: media level test with both FRAME_INTERVAL and FRAME_RATE_CAP specified (FRAME_RATE_CAP override applies), with one-down-lower property level specified.

        localJobId = next();

        request = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId, baselineExternalId+Long.toString(next()),
            medLevelFrameInterval, medLevelFrameRateCap,
            algLevelFrameInterval, parameterNotSpecified,
            parameterNotSpecified, parameterNotSpecified,
            parameterNotSpecified, parameterNotSpecified);


        // In this case, resultant FRAME_INTERVAL property value should be derived from the frame rate cap media property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(medLevelFrameRateCap)) );
        }));

        // Once done with the test, remove the test job from REDIS.
        redis.clearJob(localJobId);

        // Test5: media level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP specified, with one-down-lower property level specified.
        localJobId = next();
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId, baselineExternalId+Long.toString(next()),
            parameterNotSpecified, medLevelFrameRateCap,
            algLevelFrameInterval, parameterNotSpecified,
            parameterNotSpecified, parameterNotSpecified,
            parameterNotSpecified, parameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from frame rate cap media property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(medLevelFrameRateCap)) );
        }));

        // Once done with the test, remove the test job from REDIS.
        redis.clearJob(localJobId);

        // Test6: media level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP specified, with one-down-lower property level specified.
        localJobId = next();
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId, baselineExternalId+Long.toString(next()),
            parameterDisable, medLevelFrameRateCap,
            algLevelFrameInterval, parameterNotSpecified,
            parameterNotSpecified, parameterNotSpecified,
            parameterNotSpecified, parameterNotSpecified);
        // In this case, resultant FRAME_INTERVAL property value should be derived from frame rate cap media property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(medLevelFrameRateCap)) );
        }));

        // Once done with the test, remove the test job from REDIS.
        redis.clearJob(localJobId);



        // Tests 7-12: keep media property FRAME_INTERVAL consistent and vary combination of FRAME_RATE_CAP. Lower property levels not specified

        // Test7: media level test with both FRAME_INTERVAL and FRAME_RATE_CAP specified (FRAME_RATE_CAP override applies). Lower property level not specified.
        localJobId = next();
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId, baselineExternalId+Long.toString(next()),
            medLevelFrameInterval, medLevelFrameRateCap,
            parameterNotSpecified, parameterNotSpecified,
            parameterNotSpecified, parameterNotSpecified,
            parameterNotSpecified, parameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from the frame rate cap media property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(medLevelFrameRateCap)) );
        }));

        // Once done with the test, remove the test job from REDIS.
        redis.clearJob(localJobId);

        // Test8: media level test with FRAME_INTERVAL specified and FRAME_RATE_CAP not specified. Lower property level not specified.
        localJobId = next();
        log.info("TestDetectionSplitter running Test8, localJobId = " + localJobId);
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId, baselineExternalId+Long.toString(next()),
            medLevelFrameInterval, parameterNotSpecified,
            parameterNotSpecified, parameterNotSpecified,
            parameterNotSpecified, parameterNotSpecified,
            parameterNotSpecified, parameterNotSpecified);

        log.info("TestDetectionSplitter running Test8, medLevelFrameInterval = " + medLevelFrameInterval);
        log.info("TestDetectionSplitter running Test8, mediaFPS = " + mediaFPS);
//        log.info("TestDetectionSplitter running Test8, getVideoMediaExpectedComputedFrameInterval(medLevelFrameRateCap) = " + getVideoMediaExpectedComputedFrameInterval(medLevelFrameRateCap));

        // In this case, resultant FRAME_INTERVAL property value should be same as FRAME_INTERVAL media property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            if ( prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) ) {
                log.info("TestDetectionSplitter done running Test8, prop FRAME_INTERVAL has value " + prop.getPropertyValue());
            }
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(medLevelFrameInterval) );
        }));

        // Once done with the test, remove the test job from REDIS.
        redis.clearJob(localJobId);

        // Test9: media level test with FRAME_INTERVAL specified and FRAME_RATE_CAP disabled. Lower property level not specified.
        localJobId = next();
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId, baselineExternalId+Long.toString(next()),
            medLevelFrameInterval, parameterDisable,
            parameterNotSpecified, parameterNotSpecified,
            parameterNotSpecified, parameterNotSpecified,
            parameterNotSpecified, parameterNotSpecified);
        // In this case, resultant FRAME_INTERVAL property value should be same as FRAME_INTERVAL media property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(medLevelFrameInterval) );
        }));

        // Once done with the test, remove the test job from REDIS.
        redis.clearJob(localJobId);

        // Tests 10-12: keep media property FRAME_INTERVAL consistent and vary combination of FRAME_RATE_CAP with one-down-lower property level specified.

        // Test10: media level test with both FRAME_INTERVAL and FRAME_RATE_CAP specified (FRAME_RATE_CAP override applies), with one-down-lower property level specified.
        localJobId = next();
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId, baselineExternalId+Long.toString(next()),
            medLevelFrameInterval, medLevelFrameRateCap,
            parameterNotSpecified, algLevelFrameRateCap,
            parameterNotSpecified, parameterNotSpecified,
            parameterNotSpecified, parameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from the frame rate cap media property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(medLevelFrameRateCap)) );
        }));

        // Once done with the test, remove the test job from REDIS.
        redis.clearJob(localJobId);

        // Test11: media level test with FRAME_INTERVAL specified and FRAME_RATE_CAP not specified, with one-down-lower property level specified.
        localJobId = next();
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId, baselineExternalId+Long.toString(next()),
            medLevelFrameInterval, parameterNotSpecified,
            parameterNotSpecified, algLevelFrameRateCap,
            parameterNotSpecified, parameterNotSpecified,
            parameterNotSpecified, parameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be same as FRAME_INTERVAL media property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(medLevelFrameInterval) );
        }));

        // Once done with the test, remove the test job from REDIS.
        redis.clearJob(localJobId);

        // Test12: media level test with FRAME_INTERVAL specified and FRAME_RATE_CAP disabled, with one-down-lower property level specified.
        localJobId = next();
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId, baselineExternalId+Long.toString(next()),
            medLevelFrameInterval, parameterDisable,
            parameterNotSpecified, algLevelFrameRateCap,
            parameterNotSpecified, parameterNotSpecified,
            parameterNotSpecified, parameterNotSpecified);
        // In this case, resultant FRAME_INTERVAL property value should be same as FRAME_INTERVAL media property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(medLevelFrameInterval) );
        }));

        // Once done with the test, remove the test job from REDIS.
        redis.clearJob(localJobId);




        // TODO, repeat Tests 1-6: vary combination of job property FRAME_INTERVAL keeping FRAME_RATE_CAP consistent, without and without specifying lower ranked properties
        // TODO, repeat Tests 7-12: keep job property FRAME_INTERVAL consistent and vary FRAME_RATE_CAP, with and without specifying lower ranked properties
    }
    
//    public void createFrameRateCapOverrideTestCase(String externalId,
//        Integer frameIntervalActionPropertyValue, Integer frameRateCapActionPropertyValue,
//        Integer frameIntervalJobPropertyValue, Integer frameRateCapJobPropertyValue,
//        Integer frameIntervalAlgorithmPropertyValue, Integer frameRateCapAlgorithmPropertyValue,
//        Integer frameIntervalMediaPropertyValue, Integer frameRateCapMediaPropertyValue) throws Exception {
//
//        long localJobId = next(); // job id for this test case
//
//        // This test is successful if the sub-job algorithm properties contain FRAME_INTERVAL property
//        // whose value is the same as the input FRAME_INTERVAL action property value.
//        DetectionProtobuf.DetectionRequest request1 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId, externalId,
//            frameIntervalActionPropertyValue, frameRateCapActionPropertyValue, frameIntervalJobPropertyValue, frameRateCapJobPropertyValue,
//            frameIntervalAlgorithmPropertyValue, frameRateCapAlgorithmPropertyValue, frameIntervalMediaPropertyValue, frameRateCapMediaPropertyValue);
//
//        // In this case, resultant FRAME_INTERVAL property value should be the same as the action FRAME_INTERVAL property value (which is set to frameIntervalToTest).
//        Assert.assertTrue(request1.getAlgorithmPropertyList().stream().anyMatch(prop -> {
//            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
//                Integer.valueOf(prop.getPropertyValue()).equals(frameIntervalToTest) );
//        }));
//
//
//    }
//
//    // This general purpose method allows for more easily testing the action property override hierarchy.
//    // This method runs the 5-test-cases for various combinations of FRAME_INTERVAL and FRAME_RATE_CAP
//    // action property override of system properties.
//    public void checkActionPropertyFrameRateCapOverrideTestCases(String externalId, Integer frameIntervalToTest, Integer frameRateCapToTest,
//                                                Integer frameIntervalJobPropertyValue, Integer frameRateCapJobPropertyValue,
//                                                Integer frameIntervalAlgorithmPropertyValue, Integer frameRateCapAlgorithmPropertyValue,
//                                                Integer frameIntervalMediaPropertyValue, Integer frameRateCapMediaPropertyValue) throws Exception {
//
//        // Run 5 FRAME_INTERVAL, FRAME_RATE_CAP media property combinations.
//        // 4 test cases will be run. In 5th test case will run with both properties disabled, in this case will fall back to system property values.
//        long localJobId1 = next(); // job id for action test case #1
//        long localJobId2 = next(); // job id for action test case #2
//        long localJobId3 = next(); // job id for action test case #3
//        long localJobId4 = next(); // job id for action test case #4
//        long localJobId5 = next(); // job id for action test case #5
//
//        // Action properties will take on 5 setting combinations.
//        Integer frameIntervalActionPropertyValue = null;
//        Integer frameRateCapActionPropertyValue = null;
//
//        // Run actionProperty test case #1: just FRAME_INTERVAL is specified and not disabled.
//        frameIntervalActionPropertyValue = frameIntervalToTest;
//        frameRateCapActionPropertyValue = null;
//
//        // This test is successful if the sub-job algorithm properties contain FRAME_INTERVAL property
//        // whose value is the same as the input FRAME_INTERVAL action property value.
//        DetectionProtobuf.DetectionRequest request1 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId1, externalId,
//            frameIntervalActionPropertyValue, frameRateCapActionPropertyValue, frameIntervalJobPropertyValue, frameRateCapJobPropertyValue,
//            frameIntervalAlgorithmPropertyValue, frameRateCapAlgorithmPropertyValue, frameIntervalMediaPropertyValue, frameRateCapMediaPropertyValue);
//
//        // In this case, resultant FRAME_INTERVAL property value should be the same as the action FRAME_INTERVAL property value (which is set to frameIntervalToTest).
//        Assert.assertTrue(request1.getAlgorithmPropertyList().stream().anyMatch(prop -> {
//            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
//                    Integer.valueOf(prop.getPropertyValue()).equals(frameIntervalToTest) );
//        }));
//
//        // Run actionProperty test case #2: both FRAME_RATE_CAP and FRAME_INTERVAL are specified and not disabled.
//        frameIntervalActionPropertyValue = frameIntervalToTest;
//        frameRateCapActionPropertyValue = frameRateCapToTest;
//
//        DetectionProtobuf.DetectionRequest request2 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId2, externalId,
//            frameIntervalActionPropertyValue, frameRateCapActionPropertyValue, frameIntervalJobPropertyValue, frameRateCapJobPropertyValue,
//            frameIntervalAlgorithmPropertyValue, frameRateCapAlgorithmPropertyValue, frameIntervalMediaPropertyValue, frameRateCapMediaPropertyValue);
//
//        // In this case, resultant FRAME_INTERVAL property value should be derived from the input frame rate cap action property.
//        Assert.assertTrue(request2.getAlgorithmPropertyList().stream().anyMatch(prop -> {
//            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
//                    Double.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(localJobId2, frameRateCapToTest)) );
//        }));
//
//        // Run actionProperty test case #3: FRAME_RATE_CAP is disabled and FRAME_INTERVAL is specified and not disabled.
//        frameIntervalActionPropertyValue = frameIntervalToTest;
//        frameRateCapActionPropertyValue = -1;
//
//        // FRAME_RATE_CAP is disabled at the action property level. This test is successful if the sub-job algorithm properties contains FRAME_INTERVAL property
//        // whose value is the same as action property FRAME_INTERVAL.
//        DetectionProtobuf.DetectionRequest request3 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId3, externalId,
//            frameIntervalActionPropertyValue, frameRateCapActionPropertyValue, frameIntervalJobPropertyValue, frameRateCapJobPropertyValue,
//            frameIntervalAlgorithmPropertyValue, frameRateCapAlgorithmPropertyValue, frameIntervalMediaPropertyValue, frameRateCapMediaPropertyValue);
//
//        // FRAME_INTERVAL property value should be the same as the input FRAME_INTERVAL property value.
//        Assert.assertTrue(request3.getAlgorithmPropertyList().stream().anyMatch(prop -> {
//            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
//                    Integer.valueOf(prop.getPropertyValue()).equals(frameIntervalToTest) );
//        }));
//
//        // Run actionProperty test case #4: FRAME_INTERVAL is disabled and FRAME_RATE_CAP_PROPERTY specified and not disabled.
//        frameIntervalActionPropertyValue = -1;
//        frameRateCapActionPropertyValue = frameRateCapToTest;
//
//        DetectionProtobuf.DetectionRequest request4 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId4, externalId,
//            frameIntervalActionPropertyValue, frameRateCapActionPropertyValue, frameIntervalJobPropertyValue, frameRateCapJobPropertyValue,
//            frameIntervalAlgorithmPropertyValue, frameRateCapAlgorithmPropertyValue, frameIntervalMediaPropertyValue, frameRateCapMediaPropertyValue);
//
//        // This test is successful if the value of FRAME_INTERVAL is the same as the value derived from action properties FRAME_RATE_CAP.
//        Assert.assertTrue(request4.getAlgorithmPropertyList().stream().anyMatch(prop -> {
//            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
//                Double.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(localJobId4, frameRateCapMediaPropertyValue)) );
//        }));
//
//        // Run actionProperty test case #5: both FRAME_RATE_CAP and FRAME_INTERVAL are disabled at the action property level.
//        // In this case setting should fall back to the FRAME_INTERVAL system property (assuming that system property detection.frame.rate.cap is disabled).
//        frameIntervalActionPropertyValue = -1;
//        frameRateCapActionPropertyValue = -1;
//
//        DetectionProtobuf.DetectionRequest request5 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId5, externalId,
//            frameIntervalActionPropertyValue, frameRateCapActionPropertyValue, frameIntervalJobPropertyValue, frameRateCapJobPropertyValue,
//            frameIntervalAlgorithmPropertyValue, frameRateCapAlgorithmPropertyValue, frameIntervalMediaPropertyValue, frameRateCapMediaPropertyValue);
//
//        // FRAME_INTERVAL property value should be the same as the FRAME_INTERVAL system property value.
//        Assert.assertTrue(request5.getAlgorithmPropertyList().stream().anyMatch(prop -> {
//            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
//                    Double.valueOf(prop.getPropertyValue()) == propertiesUtil.getSamplingInterval() );
//        }));
//
//        // When done with these tests, clear the test jobs from REDIS.
//        redis.clearJob(localJobId1);
//        redis.clearJob(localJobId2);
//        redis.clearJob(localJobId3);
//        redis.clearJob(localJobId4);
//        redis.clearJob(localJobId5);
//    }
//
//    @Test
//    // Test for action (i.e. pipeline) properties override of system properties
//    public void testActionPropertyFrameRateCapOverrideOfProperties() throws Exception {
//        String externalId = "actionFrameRateCapOverrideTest";
//        Integer frameIntervalToTest = 20;
//        Integer frameRateCapToTest = 10;
//
//        // Note: at this test level, the following property values should be null (override using these properties are evaluated elsewhere).
//        Integer frameIntervalJobPropertyValue = null;
//        Integer frameRateCapJobPropertyValue = null;
//        Integer frameIntervalAlgorithmPropertyValue = null;
//        Integer frameRateCapAlgorithmPropertyValue = null;
//        Integer frameIntervalMediaPropertyValue = null;
//        Integer frameRateCapMediaPropertyValue = null;
//
//        // Call method to test FRAME_RATE_CAP or FRAME_INTERVAL in various combinations to test action property override of
//        // these two properties over system properties.
//        checkActionPropertyFrameRateCapOverrideTestCases(externalId, frameIntervalToTest, frameRateCapToTest,
//                                                        frameIntervalJobPropertyValue, frameRateCapJobPropertyValue,
//                                                        frameIntervalAlgorithmPropertyValue, frameRateCapAlgorithmPropertyValue,
//                                                        frameIntervalMediaPropertyValue, frameRateCapMediaPropertyValue);
//
//    }
//
//    // This general purpose method allows for more easily testing the job property override hierarchy.
//    // This method runs the 5-test-cases for various combinations of FRAME_INTERVAL and FRAME_RATE_CAP
//    // job property override of action properties.
//    public void checkJobPropertyFrameRateCapOverrideTestCases(String externalId, Integer frameIntervalToTest, Integer frameRateCapToTest,
//                                                Integer frameIntervalActionPropertyValue, Integer frameRateCapActionPropertyValue,
//                                                Integer frameIntervalAlgorithmPropertyValue, Integer frameRateCapAlgorithmPropertyValue,
//                                                Integer frameIntervalMediaPropertyValue, Integer frameRateCapMediaPropertyValue) throws Exception {
//
//        // Run 5 FRAME_INTERVAL, FRAME_RATE_CAP job property combinations.
//        // 4 test cases will be run with 5th test case to be run with both properties disabled - override will fall back to action properties.
//        long localJobId1 = next(); // job property test case #1
//        long localJobId2 = next(); // job property test case #2
//        long localJobId3 = next(); // job property test case #3
//        long localJobId4 = next(); // job property test case #4
//
//        // Job properties will be varied for all test cases.
//        Integer frameIntervalJobPropertyValue = null;
//        Integer frameRateCapJobPropertyValue = null;
//
//        // Run job property test case #1: just FRAME_INTERVAL job property is specified and not disabled.
//        frameIntervalJobPropertyValue = frameIntervalToTest;
//        frameRateCapJobPropertyValue = null;
//
//        // This test is successful if the sub-job algorithm properties contains FRAME_INTERVAL property
//        // whose value is the same as the input FRAME_INTERVAL job property value.
//        DetectionProtobuf.DetectionRequest request1 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId1, externalId,
//                                                        frameIntervalActionPropertyValue, frameRateCapActionPropertyValue,
//                                                        frameIntervalJobPropertyValue, frameRateCapJobPropertyValue,
//                                                        frameIntervalAlgorithmPropertyValue, frameRateCapAlgorithmPropertyValue,
//                                                        frameIntervalMediaPropertyValue, frameRateCapMediaPropertyValue);
//
//
//        // FRAME_INTERVAL property value should be the same as the overridden FRAME_INTERVAL job property value.
//        Assert.assertTrue(request1.getAlgorithmPropertyList().stream().anyMatch(prop -> {
//            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
//                    Integer.valueOf(prop.getPropertyValue()).equals(frameIntervalToTest) );
//        }));
//
//        // Run job property test case #2: both FRAME_RATE_CAP and FRAME_INTERVAL are specified and not disabled.
//        frameIntervalJobPropertyValue = frameIntervalToTest;
//        frameRateCapJobPropertyValue = frameRateCapToTest;
//
//        // This test is successful if the sub-job algorithm properties contains FRAME_INTERVAL property
//        // whose value is equal to expectedJobPropertyComputedFrameInterval.
//        DetectionProtobuf.DetectionRequest request2 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId2, externalId,
//                                                        frameIntervalActionPropertyValue, frameRateCapActionPropertyValue,
//                                                        frameIntervalJobPropertyValue, frameRateCapJobPropertyValue,
//                                                        frameIntervalAlgorithmPropertyValue, frameRateCapAlgorithmPropertyValue,
//                                                        frameIntervalMediaPropertyValue, frameRateCapMediaPropertyValue);
//
//
//        // The value of FRAME_INTERVAL should be derived from job properties FRAME_RATE_CAP
//        Assert.assertTrue(request2.getAlgorithmPropertyList().stream().anyMatch(prop -> {
//            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
//                Double.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(localJobId2, frameRateCapToTest)) );
//        }));
//
//        // Run job property test case #3: FRAME_RATE_CAP is disabled at the job property level and FRAME_INTERVAL is specified and not disabled.
//        frameIntervalJobPropertyValue = frameIntervalToTest;
//        frameRateCapJobPropertyValue = -1;
//
//        // This test is successful if the sub-job algorithm properties contains FRAME_INTERVAL property
//        // whose value is the same as job property FRAME_INTERVAL.
//        DetectionProtobuf.DetectionRequest request3 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId3, externalId,
//                                                        frameIntervalActionPropertyValue, frameRateCapActionPropertyValue,
//                                                        frameIntervalJobPropertyValue, frameRateCapJobPropertyValue,
//                                                        frameIntervalAlgorithmPropertyValue, frameRateCapAlgorithmPropertyValue,
//                                                        frameIntervalMediaPropertyValue, frameRateCapMediaPropertyValue);
//
//       Assert.assertTrue(request3.getAlgorithmPropertyList().stream().anyMatch(prop -> {
//            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
//                    Integer.valueOf(prop.getPropertyValue()).equals(frameIntervalToTest) );
//        }));
//
//        // Run job property test case #4: FRAME_RATE_CAP is specified and not disabled and FRAME_INTERVAL disabled at the job property level.
//        frameIntervalJobPropertyValue = -1;
//        frameRateCapJobPropertyValue = frameRateCapToTest;
//
//        DetectionProtobuf.DetectionRequest request4 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId4, externalId,
//                                                        frameIntervalActionPropertyValue, frameRateCapActionPropertyValue,
//                                                        frameIntervalJobPropertyValue, frameRateCapJobPropertyValue,
//                                                        frameIntervalAlgorithmPropertyValue, frameRateCapAlgorithmPropertyValue,
//                                                        frameIntervalMediaPropertyValue, frameRateCapMediaPropertyValue);
//
//        // FRAME_INTERVAL property value should be derived from job property FRAME_RATE_CAP and the videos FPS.
//        Assert.assertTrue(request4.getAlgorithmPropertyList().stream().anyMatch(prop -> {
//            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
//                    Double.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(localJobId4, frameRateCapToTest)) );
//        }));
//
//        // Run job property test case #5: both FRAME_RATE_CAP and FRAME_INTERVAL are disabled.
//        // With both these properties disabled, in this case should fall back to action property overrides.
//        frameIntervalJobPropertyValue = -1;
//        frameRateCapJobPropertyValue = -1;
//
//        // Run the job property override of action properties tests against jobProperty test case #5 (both properties disabled).
//        checkActionPropertyFrameRateCapOverrideTestCases(externalId, frameIntervalToTest, frameRateCapToTest,
//                                                         frameIntervalJobPropertyValue, frameRateCapJobPropertyValue,
//                                                         frameIntervalAlgorithmPropertyValue, frameRateCapAlgorithmPropertyValue,
//                                                         frameIntervalMediaPropertyValue, frameRateCapMediaPropertyValue);
//
//        // When done with these tests, clear the test jobs from REDIS.
//        redis.clearJob(localJobId1);
//        redis.clearJob(localJobId2);
//        redis.clearJob(localJobId3);
//        redis.clearJob(localJobId4);
//
//    }
//
//    @Test
//    // job properties can override action (i.e. pipeline) or system properties
//    public void testJobPropertyFrameRateCapOverrideOfProperties() throws Exception {
//
//        String externalId = "jobFrameRateCapOverrideTest";
//        Integer frameIntervalToTest = 30;
//        Integer frameRateCapToTest = 20;
//
//        // Note: at this test level, the following property values should be null (override using these properties are evaluated elsewhere).
//        Integer frameIntervalActionPropertyValue = null;
//        Integer frameRateCapActionPropertyValue = null;
//        Integer frameIntervalAlgorithmPropertyValue = null;
//        Integer frameRateCapAlgorithmPropertyValue = null;
//        Integer frameIntervalMediaPropertyValue = null;
//        Integer frameRateCapMediaPropertyValue = null;
//
//
//        // Call method to test FRAME_RATE_CAP or FRAME_INTERVAL in various combinations to test job property override of
//        // these two properties over action properties.
//        checkJobPropertyFrameRateCapOverrideTestCases(externalId, frameIntervalToTest, frameRateCapToTest,
//                                                      frameIntervalActionPropertyValue, frameRateCapActionPropertyValue,
//                                                      frameIntervalAlgorithmPropertyValue, frameRateCapAlgorithmPropertyValue,
//                                                      frameIntervalMediaPropertyValue, frameRateCapMediaPropertyValue);
//    }
//
//    // This general purpose method allows for more easily testing the algorithm property override hierarchy.
//    // This method runs the 5-test-cases for various combinations of FRAME_INTERVAL and FRAME_RATE_CAP
//    // algorithm property override of job properties.
//    public void checkAlgorithmPropertyFrameRateCapOverrideTestCases( String externalId, Integer frameIntervalToTest, Integer frameRateCapToTest,
//                                                                     Integer frameIntervalActionPropertyValue, Integer frameRateCapActionPropertyValue,
//                                                                     Integer frameIntervalJobPropertyValue, Integer frameRateCapJobPropertyValue,
//                                                                     Integer frameIntervalMediaPropertyValue, Integer frameRateCapMediaPropertyValue) throws Exception {
//
//        // Run 5 FRAME_INTERVAL, FRAME_RATE_CAP algorithm property combinations.
//        // 4 test cases will be run with 5th test case to be run with both properties disabled - override will fall back to job properties.
//        long localJobId1 = next(); // algorithm property test case #1
//        long localJobId2 = next(); // algorithm property test case #2
//        long localJobId3 = next(); // algorithm property test case #3
//        long localJobId4 = next(); // algorithm property test case #4
//
//        // This method will test FRAME_RATE_CAP or FRAME_INTERVAL in various combinations to test algorithm property override of
//        // these two properties over job properties.
//        Integer frameIntervalAlgorithmPropertyValue = null;
//        Integer frameRateCapAlgorithmPropertyValue = null;
//
//        // Run algorithmProperty test case #1: just FRAME_INTERVAL algorithm property is specified and not disabled.
//        frameIntervalAlgorithmPropertyValue = frameIntervalToTest;
//        frameRateCapAlgorithmPropertyValue = null;
//
//        // This test is successful if the sub-job algorithm properties contains FRAME_INTERVAL property whose
//        // value is the same as the FRAME_INTERVAL property specified in the algorithm properties.
//        DetectionProtobuf.DetectionRequest request1 =  createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId1, externalId,
//                                                                frameIntervalActionPropertyValue, frameRateCapActionPropertyValue,
//                                                                frameIntervalJobPropertyValue, frameRateCapJobPropertyValue,
//                                                                frameIntervalAlgorithmPropertyValue, frameRateCapAlgorithmPropertyValue,
//                                                                frameIntervalMediaPropertyValue, frameRateCapMediaPropertyValue);
//
//        // FRAME_INTERVAL property value should be the same as the overridden FRAME_INTERVAL property value.
//        Assert.assertTrue(request1.getAlgorithmPropertyList().stream().anyMatch(prop -> {
//            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
//                    Integer.valueOf(prop.getPropertyValue()).equals(frameIntervalToTest) );
//        }));
//
//        // Run algorithmProperty test case #2: FRAME_RATE_CAP and FRAME_INTERVAL are both specified and not disabled.
//        frameIntervalAlgorithmPropertyValue = frameIntervalToTest;
//        frameRateCapAlgorithmPropertyValue = frameRateCapToTest;
//
//        DetectionProtobuf.DetectionRequest request2 =  createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId2, externalId,
//                                                                frameIntervalActionPropertyValue, frameRateCapActionPropertyValue,
//                                                                frameIntervalJobPropertyValue, frameRateCapJobPropertyValue,
//                                                                frameIntervalAlgorithmPropertyValue, frameRateCapAlgorithmPropertyValue,
//                                                                frameIntervalMediaPropertyValue, frameRateCapMediaPropertyValue);
//
//        // FRAME_INTERVAL property value should be derived from algorithm properties FRAME_RATE_CAP.
//        Assert.assertTrue(request2.getAlgorithmPropertyList().stream().anyMatch(prop -> {
//            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
//                    Double.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(localJobId2,frameRateCapToTest)));
//        }));
//
//        // Run algorithmProperty test case #3: FRAME_RATE_CAP is disabled and FRAME_INTERVAL is specified and not disabled.
//        frameIntervalAlgorithmPropertyValue = frameIntervalToTest;
//        frameRateCapAlgorithmPropertyValue = -1;
//
//        // This test is successful if the sub-job algorithm properties contain FRAME_INTERVAL property
//        // whose value is the same as algorithm property FRAME_INTERVAL.
//        DetectionProtobuf.DetectionRequest request3 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId3, externalId,
//                                                            frameIntervalActionPropertyValue, frameRateCapActionPropertyValue,
//                                                            frameIntervalJobPropertyValue, frameRateCapJobPropertyValue,
//                                                            frameIntervalAlgorithmPropertyValue, frameRateCapAlgorithmPropertyValue,
//                                                            frameIntervalMediaPropertyValue, frameRateCapMediaPropertyValue);
//
//        // FRAME_INTERVAL property value should be the same as the overridden FRAME_INTERVAL property value.
//        Assert.assertTrue(request3.getAlgorithmPropertyList().stream().anyMatch(prop -> {
//            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
//                Integer.valueOf(prop.getPropertyValue()).equals(frameIntervalToTest) );
//        }));
//
//        // Run algorithmProperty test case #4: FRAME_RATE_CAP is specified and not disabled and FRAME_INTERVAL disabled.
//        frameIntervalAlgorithmPropertyValue = -1;
//        frameRateCapAlgorithmPropertyValue = frameRateCapToTest;
//
//        DetectionProtobuf.DetectionRequest request4 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId4, externalId,
//                                                            frameIntervalActionPropertyValue, frameRateCapActionPropertyValue,
//                                                            frameIntervalJobPropertyValue, frameRateCapJobPropertyValue,
//                                                            frameIntervalAlgorithmPropertyValue, frameRateCapAlgorithmPropertyValue,
//                                                            frameIntervalMediaPropertyValue, frameRateCapMediaPropertyValue);
//
//        // FRAME_INTERVAL property value should be derived from algorithm properties FRAME_RATE_CAP.
//        Assert.assertTrue(request4.getAlgorithmPropertyList().stream().anyMatch(prop -> {
//            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
//                Double.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(localJobId4,frameRateCapToTest)) );
//        }));
//
//        // Run algorithmProperty test case #5: both FRAME_RATE_CAP and FRAME_INTERVAL are disabled.
//        // With both these properties disabled, in this case should fall back to job property overrides.
//        frameIntervalAlgorithmPropertyValue = -1;
//        frameRateCapAlgorithmPropertyValue = -1;
//
//        // Run the algorithm property override test of job properties tests against algorithmProperty test case #5 (both FRAME_RATE_CAP and FRAME_INTERVAL disabled).
//        checkJobPropertyFrameRateCapOverrideTestCases(externalId, frameIntervalToTest, frameRateCapToTest,
//                                                      frameIntervalActionPropertyValue, frameRateCapActionPropertyValue,
//                                                      frameIntervalAlgorithmPropertyValue, frameRateCapAlgorithmPropertyValue,
//                                                      frameIntervalMediaPropertyValue, frameRateCapMediaPropertyValue);
//
//        // When done with these tests, clear the test jobs from REDIS.
//        redis.clearJob(localJobId1);
//        redis.clearJob(localJobId2);
//        redis.clearJob(localJobId3);
//        redis.clearJob(localJobId4);
//    }
//
//    @Test
//    public void testAlgorithmPropertyFrameRateCapOverrideOfProperties() throws Exception {
//
//        String externalId = "algFrameRateCapOverrideTest";
//        Integer frameIntervalToTest = 5;
//        Integer frameRateCapToTest = 10;
//
//        // Note: at this test level, the following property values should be null (override using these properties are evaluated elsewhere).
//        Integer frameIntervalActionPropertyValue = null;
//        Integer frameRateCapActionPropertyValue = null;
//        Integer frameIntervalJobPropertyValue = null;
//        Integer frameRateCapJobPropertyValue = null;
//        Integer frameIntervalMediaPropertyValue = null;
//        Integer frameRateCapMediaPropertyValue = null;
//
//        // Call method to test FRAME_RATE_CAP or FRAME_INTERVAL in various combinations to test algorithm property override of
//        // these two properties over job properties.
//        checkAlgorithmPropertyFrameRateCapOverrideTestCases(externalId,  frameIntervalToTest, frameRateCapToTest,
//                                                            frameIntervalActionPropertyValue, frameRateCapActionPropertyValue,
//                                                            frameIntervalJobPropertyValue, frameRateCapJobPropertyValue,
//                                                            frameIntervalMediaPropertyValue, frameRateCapMediaPropertyValue);
//
//    }
//
//    // This general purpose method allows for more easily testing the media property override hierarchy.
//    // This method runs the 5-test-cases for various combinations of FRAME_INTERVAL and FRAME_RATE_CAP
//    // media property override of algorithm properties.
//    public void checkMediaPropertyFrameRateCapOverrideTestCases(String externalId, Integer frameIntervalToTest, Integer frameRateCapToTest,
//                                                                Integer frameIntervalActionPropertyValue, Integer frameRateCapActionPropertyValue,
//                                                                Integer frameIntervalJobPropertyValue, Integer frameRateCapJobPropertyValue,
//                                                                Integer frameIntervalAlgorithmPropertyValue, Integer frameRateCapAlgorithmPropertyValue) throws Exception {
//
//        // Run 5 FRAME_INTERVAL, FRAME_RATE_CAP media property combinations.
//        // 4 test cases will be run with 5th test case to be run with both properties disabled - override will fall back to algorithm properties.
//        long localJobId1 = next(); // media property test case #1
//        long localJobId2 = next(); // media property test case #2
//        long localJobId3 = next(); // media property test case #3
//        long localJobId4 = next(); // media property test case #4
//
//        // This method will test FRAME_RATE_CAP or FRAME_INTERVAL in various combinations to test media property override of
//        // these two properties over algorithm properties.
//        Integer frameIntervalMediaPropertyValue = null;
//        Integer frameRateCapMediaPropertyValue = null;
//
//        // Run mediaProperty test case #1: FRAME_INTERVAL is specified and FRAME_RATE_CAP is not specified.
//        frameIntervalMediaPropertyValue = frameIntervalToTest;
//        frameRateCapMediaPropertyValue = null;
//
//        // This test is successful if the sub-job algorithm properties contains FRAME_INTERVAL property whose
//        // value is the same as the FRAME_INTERVAL property specified in the media properties.
//        DetectionProtobuf.DetectionRequest request1 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId1, externalId,
//                                                            frameIntervalActionPropertyValue, frameRateCapActionPropertyValue,
//                                                            frameIntervalJobPropertyValue, frameRateCapJobPropertyValue,
//                                                            frameIntervalAlgorithmPropertyValue, frameRateCapAlgorithmPropertyValue,
//                                                            frameIntervalMediaPropertyValue, frameRateCapMediaPropertyValue);
//
//        // FRAME_INTERVAL property value should be the same as the overridden FRAME_INTERVAL property value.
//        Assert.assertTrue(request1.getAlgorithmPropertyList().stream().anyMatch(prop -> {
//            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
//                Integer.valueOf(prop.getPropertyValue()).equals(frameIntervalToTest));
//        }));
//
//        // Run mediaProperty test case #2: FRAME_RATE_CAP and FRAME_INTERVAL are both specified and not disabled.
//        frameIntervalMediaPropertyValue = frameIntervalToTest;
//        frameRateCapMediaPropertyValue = frameRateCapToTest;
//
//        DetectionProtobuf.DetectionRequest request2 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId2, externalId,
//                                                                    frameIntervalActionPropertyValue, frameRateCapActionPropertyValue,
//                                                                    frameIntervalJobPropertyValue, frameRateCapJobPropertyValue,
//                                                                    frameIntervalAlgorithmPropertyValue, frameRateCapAlgorithmPropertyValue,
//                                                                    frameIntervalMediaPropertyValue, frameRateCapMediaPropertyValue);
//
//        // FRAME_INTERVAL property value should be derived from media property FRAME_RATE_CAP.
//        Assert.assertTrue(request2.getAlgorithmPropertyList().stream().anyMatch(prop -> {
//            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
//                Double.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(localJobId2, frameRateCapToTest)) );
//        }));
//
//        // Run mediaProperty test case #3: FRAME_RATE_CAP is disabled and FRAME_INTERVAL is specified and not disabled.
//        frameIntervalMediaPropertyValue = frameIntervalToTest;
//        frameRateCapMediaPropertyValue = -1;
//
//        // This test is successful if the sub-job algorithm properties contains FRAME_INTERVAL property
//        // whose value is the same as media property FRAME_INTERVAL.
//        DetectionProtobuf.DetectionRequest request3 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId3, externalId,
//                                                                frameIntervalActionPropertyValue, frameRateCapActionPropertyValue,
//                                                                frameIntervalJobPropertyValue, frameRateCapJobPropertyValue,
//                                                                frameIntervalAlgorithmPropertyValue, frameRateCapAlgorithmPropertyValue,
//                                                                frameIntervalMediaPropertyValue, frameRateCapMediaPropertyValue);
//
//        // FRAME_INTERVAL property value should be the same as the media FRAME_INTERVAL property value.
//        Assert.assertTrue(request3.getAlgorithmPropertyList().stream().anyMatch(prop -> {
//            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
//                Integer.valueOf(prop.getPropertyValue()).equals(frameIntervalToTest));
//        }));
//
//        // Run mediaProperty test case #4: FRAME_RATE_CAP specified and not disabled and FRAME_INTERVAL disabled.
//        frameIntervalMediaPropertyValue = -1;
//        frameRateCapMediaPropertyValue = frameRateCapToTest;
//
//        DetectionProtobuf.DetectionRequest request4 = createFrameRateTestTransientJobAndPerformDetectionSplit(localJobId4, externalId,
//                                                                frameIntervalActionPropertyValue, frameRateCapActionPropertyValue,
//                                                                frameIntervalJobPropertyValue, frameRateCapJobPropertyValue,
//                                                                frameIntervalAlgorithmPropertyValue, frameRateCapAlgorithmPropertyValue,
//                                                                frameIntervalMediaPropertyValue, frameRateCapMediaPropertyValue);
//
//        // FRAME_INTERVAL property value should be derived from media property FRAME_RATE_CAP .
//        Assert.assertTrue(request4.getAlgorithmPropertyList().stream().anyMatch(prop -> {
//            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
//                Double.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(localJobId4, frameRateCapToTest)) );
//        }));
//
//        // Run mediaProperty test case #5: both FRAME_RATE_CAP and FRAME_INTERVAL are disabled.
//        // With both these properties disabled, in this case should fall back to algorithm property overrides.
//        frameIntervalMediaPropertyValue = -1;
//        frameRateCapMediaPropertyValue = -1;
//
//        // Run the algorithm property override test of job properties tests against algorithmProperty test case #5 (both FRAME_RATE_CAP and FRAME_INTERVAL disabled).
//        checkAlgorithmPropertyFrameRateCapOverrideTestCases(externalId, frameIntervalToTest, frameRateCapToTest,
//                                                            frameIntervalActionPropertyValue, frameRateCapActionPropertyValue,
//                                                            frameIntervalJobPropertyValue, frameRateCapJobPropertyValue,
//                                                            frameIntervalMediaPropertyValue, frameRateCapMediaPropertyValue);
//
//        // When done with these tests, clear the test jobs from REDIS.
//        redis.clearJob(localJobId1);
//        redis.clearJob(localJobId2);
//        redis.clearJob(localJobId3);
//        redis.clearJob(localJobId4);
//
//    }
//
//    @Test
//    public void testMediaPropertyFrameRateCapOverrideOfProperties() throws Exception {
//
//        String externalId = "mediaFrameRateCapOverrideTest";
//        Integer frameIntervalToTest = 3;
//        Integer frameRateCapToTest = 10;
//
//        // Note: at this test level, the following property values should be null (override using these properties are evaluated elsewhere).
//        Integer frameIntervalActionPropertyValue = null;
//        Integer frameRateCapActionPropertyValue = null;
//        Integer frameIntervalJobPropertyValue = null;
//        Integer frameRateCapJobPropertyValue = null;
//        Integer frameIntervalAlgorithmPropertyValue = null;
//        Integer frameRateCapAlgorithmPropertyValue = null;
//
//        // Call method to test FRAME_RATE_CAP or FRAME_INTERVAL in various combinations to test media property override of
//        // these two properties over algorithm properties.
//        checkMediaPropertyFrameRateCapOverrideTestCases(externalId,  frameIntervalToTest, frameRateCapToTest,
//                                                        frameIntervalActionPropertyValue, frameRateCapActionPropertyValue,
//                                                        frameIntervalJobPropertyValue, frameRateCapJobPropertyValue,
//                                                        frameIntervalAlgorithmPropertyValue, frameRateCapAlgorithmPropertyValue);
//
//    }

}
