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
import org.apache.camel.Message;
import org.apache.commons.lang3.mutable.MutableInt;
import org.junit.Assert;
import org.junit.Test;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.buffers.AlgorithmPropertyProtocolBuffer;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.camel.StageSplitter;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionSplitter;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO: Re-enable the following three annotations.
// Disabled for now because the testFrameRateCapOverrideXyzLevel() methods don't need to load the app context.

// @ContextConfiguration(locations = {"classpath:applicationContext.xml"})
// @RunWith(SpringJUnit4ClassRunner.class)
// @RunListener.ThreadSafe
public class TestDetectionSplitter {
    private static final Logger log = LoggerFactory.getLogger(TestDetectionSplitter.class);
    private static final int MINUTES = 1000 * 60; // 1000 milliseconds/second & 60 seconds/minute.

    // TODO: Re-enable the following annotation.
    // Disabled for now because the testFrameRateCapOverrideXyzLevel() methods don't need to load the app context.
    // @Autowired
    // private ApplicationContext context;

    @Autowired
    private IoUtils ioUtils;

    @Qualifier(DetectionSplitter.REF)
    @Autowired
    private StageSplitter detectionStageSplitter;

    private static final MutableInt SEQUENCE = new MutableInt();

    public int nextId() {
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
        TransientMedia testMedia = new TransientMedia(nextId(), ioUtils.findFile("/samples/new_face_video.avi").toString());
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
        TransientMedia testMedia = new TransientMedia(nextId(), ioUtils.findFile(mediaUri).toString());
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

    private TransientJob createFrameRateCapTestTransientJob(
        Map<String, String> actionProperties, Map<String, String> jobProperties,
        Map<String, Map> algorithmProperties, Map<String,String> mediaProperties) {

        TransientMedia testMedia = new TransientMedia(nextId(), "/path/to/dummy/media");
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

        TransientJob testJob = new TransientJob(nextId(), null, dummyPipeline, 0, 0, false, false);

        testJob.setMedia(listMedia);
        testJob.getOverriddenJobProperties().putAll(jobProperties);
        testJob.getOverriddenAlgorithmProperties().putAll(algorithmProperties);

        return testJob;

        // redis.persistJob(testJob);

        /*
        // Need to run MediaInspectionProcessor on the video media, so that inspectMedia method will be called to add FPS and other metadata to the TransientMedia.
        // This is needed for this test to work.
        for (TransientMedia media : redis.getJob(jobId).getMedia()) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.getIn().getHeaders().put(MpfHeaders.JOB_ID, jobId);
            exchange.getIn().setBody(jsonUtils.serialize(media));
            exchange.getOut().setHeader(MpfHeaders.JOB_ID, jobId);
            mediaInspectionProcessor.wfmProcess(exchange);
        }
        */

        // Return the transient job refreshed from REDIS since the mediaInspectionProcessor may have updated the media that was associated with the job.
        // return redis.getJob(jobId);
    }

    /* DJV
    // need to store mediaFPS as a class property so we can determine computed frame interval for verification of the frame rate cap tests.
    double mediaFPS;

    private DetectionProtobuf.DetectionRequest createFrameRateTestTransientJobAndPerformDetectionSplit(
                                    Integer frameIntervalMediaPropertyValue, Integer frameRateCapMediaPropertyValue,
                                    Integer frameIntervalAlgorithmPropertyValue, Integer frameRateCapAlgorithmPropertyValue,
                                    Integer frameIntervalJobPropertyValue, Integer frameRateCapJobPropertyValue,
                                    Integer frameIntervalActionPropertyValue, Integer frameRateCapActionPropertyValue) throws Exception {

        long jobId = next();
        String externalId = "frameRateCapTest-" + Long.toString(jobId);

        Map<String, String> actionProperties = new HashMap();
        Map<String, String> jobProperties = new HashMap();
        Map<String, Map> algorithmProperties = new HashMap();
        Map<String, String> mediaProperties = new HashMap();

        if ( frameIntervalActionPropertyValue != null )
            actionProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameIntervalActionPropertyValue.toString());
        if ( frameRateCapActionPropertyValue != null )
            actionProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, frameRateCapActionPropertyValue.toString());

        if ( frameIntervalJobPropertyValue != null )
            jobProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameIntervalJobPropertyValue.toString());
        if ( frameRateCapJobPropertyValue != null )
            jobProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, frameRateCapJobPropertyValue.toString());

        if ( frameIntervalAlgorithmPropertyValue != null ) {
            Map<String, String> faceCvAlgorithmProperties = new HashMap();
            faceCvAlgorithmProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameIntervalAlgorithmPropertyValue.toString());
            algorithmProperties.put("FACECV", faceCvAlgorithmProperties);
        }
        if ( frameRateCapAlgorithmPropertyValue != null ) {
            Map<String, String> faceCvAlgorithmProperties = algorithmProperties.get("FACECV");
            if ( faceCvAlgorithmProperties == null ) faceCvAlgorithmProperties = new HashMap();
            faceCvAlgorithmProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, frameRateCapAlgorithmPropertyValue.toString());
            algorithmProperties.put("FACECV", faceCvAlgorithmProperties);
        }

        if ( frameIntervalMediaPropertyValue != null )
            mediaProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameIntervalMediaPropertyValue.toString());
        if ( frameRateCapMediaPropertyValue != null )
            mediaProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, frameRateCapMediaPropertyValue.toString());

        TransientJob testJob = createFrameRateCapTestTransientJob( jobId, externalId,
                                                          actionProperties, jobProperties, algorithmProperties, mediaProperties );

        // Get this jobs media FPS so it can be used later for test verification.
        TransientMedia transientMedia = redis.getJob(jobId).getMedia().get(0);
        mediaFPS = Double.valueOf(transientMedia.getMetadata("FPS"));

        // Run the DetectionSplitter on this job, and return the response for evaluation.
        List<Message> responseList = detectionStageSplitter.performSplit(testJob, testJob.getPipeline().getStages().get(0));

        Assert.assertTrue(responseList.size() >= 1);
        Message message = responseList.get(0);
        Assert.assertTrue(message.getBody() instanceof DetectionProtobuf.DetectionRequest);

        // Once we've formed the request, remove the test job from REDIS.
        redis.clearJob(jobId);

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
    */

    private void putStringInMapIfNotNull(Map map, String key, Object value) {
        if (value != null) {
            map.put(key, value.toString());
        }
    }

    private void checkCalcFrameInterval(Integer frameIntervalSystemPropVal, Integer frameRateCapSystemPropVal,
                                    Integer frameIntervalActionPropVal, Integer frameRateCapActionPropVal,
                                    Integer frameIntervalJobPropVal, Integer frameRateCapJobPropVal,
                                    Integer frameIntervalAlgPropVal, Integer frameRateCapAlgPropVal,
                                    Integer frameIntervalMediaPropVal, Integer frameRateCapMediaPropVal,
                                    double mediaFPS, Integer expectedFrameInterval) {

        Map<String, String> actionProps = new HashMap();
        putStringInMapIfNotNull(actionProps, MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameIntervalActionPropVal);
        putStringInMapIfNotNull(actionProps, MpfConstants.FRAME_RATE_CAP_PROPERTY, frameRateCapActionPropVal);

        Map<String, String> jobProps = new HashMap();
        putStringInMapIfNotNull(jobProps, MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameIntervalJobPropVal);
        putStringInMapIfNotNull(jobProps, MpfConstants.FRAME_RATE_CAP_PROPERTY, frameRateCapJobPropVal);

        Map<String, String> algProps = new HashMap();
        putStringInMapIfNotNull(algProps, MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameIntervalAlgPropVal);
        putStringInMapIfNotNull(algProps, MpfConstants.FRAME_RATE_CAP_PROPERTY, frameRateCapAlgPropVal);

        Map<String, Map> metaAlgProps = new HashMap();
        metaAlgProps.put("FACECV", algProps);

        Map<String, String> mediaProps = new HashMap();
        putStringInMapIfNotNull(mediaProps, MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameIntervalMediaPropVal);
        putStringInMapIfNotNull(mediaProps, MpfConstants.FRAME_RATE_CAP_PROPERTY, frameRateCapMediaPropVal);

        TransientJob testJob = createFrameRateCapTestTransientJob(actionProps, jobProps, metaAlgProps, mediaProps);

        String calcFrameInterval = AggregateJobPropertiesUtil.calculateFrameInterval(
                testJob.getPipeline().getStages().get(0).getActions().get(0),
                testJob,
                testJob.getMedia().get(0),
                frameIntervalSystemPropVal, frameRateCapSystemPropVal, mediaFPS);

        Assert.assertEquals(expectedFrameInterval.toString(), calcFrameInterval);
    }

    /* DJV
    @Test
    // Baseline test: testing override result using current FRAME_INTERVAL and FRAME_RATE_CAP system property settings.
    public void testFrameRateCapTestOverrideOfSystemProperties() throws Exception {

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
        DetectionProtobuf.DetectionRequest request1 = createFrameRateTestTransientJobAndPerformDetectionSplit(
            frameIntervalActionPropertyValue, frameRateCapActionPropertyValue, frameIntervalJobPropertyValue, frameRateCapJobPropertyValue,
            frameIntervalAlgorithmPropertyValue, frameRateCapAlgorithmPropertyValue, frameIntervalMediaPropertyValue, frameRateCapMediaPropertyValue);

        if ( propertiesUtil.getFrameRateCap() < 0 ) {
            // System property detection.frame.rate.cap is disabled, value should be same as FRAME_INTERVAL system property.
            Assert.assertTrue(request1.getAlgorithmPropertyList().stream().anyMatch(prop -> {
                return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                        Double.valueOf(prop.getPropertyValue()) == propertiesUtil.getSamplingInterval());
            }));
        } else {
            // System property detection.frame.rate.cap is not disabled, check value as derived from FPS and system property detection.frame.rate.cap.
            int expectedComputedFrameInterval = getVideoMediaExpectedComputedFrameInterval(propertiesUtil.getFrameRateCap());
            Assert.assertTrue(request1.getAlgorithmPropertyList().stream().anyMatch(prop -> {
                return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                        Integer.valueOf(prop.getPropertyValue()).equals(expectedComputedFrameInterval) );
            }));
        }
    }
    */

    @Test
    public void testFrameRateCapOverrideSystemLevel() throws Exception {
        // Tests 1-4: test 4 combinations of system property FRAME_INTERVAL and FRAME_RATE_CAP.

        // Test1: system level test with both FRAME_INTERVAL and FRAME_RATE_CAP specified (FRAME_RATE_CAP override applies).
        checkCalcFrameInterval(7,5, null,null, null,null, null,null, null,null, 30, 6);

        // Test2: system level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP specified.
        checkCalcFrameInterval(-1,5, null,null, null,null, null,null, null,null, 30, 6);

        // Test3: system level test with FRAME_INTERVAL specified and FRAME_RATE_CAP disabled.
        checkCalcFrameInterval(7,-1, null,null, null,null, null,null, null,null, 30, 7);

        // Test4: system level test with both FRAME_INTERVAL and FRAME_RATE_CAP disabled.
        checkCalcFrameInterval(-1,-1, null,null, null,null, null,null, null,null, 30, 1);
    }

    // TODO: Add other testFrameRateCapOverrideXyzLevel() methods here

    @Test
    public void testFrameRateCapOverrideMediaLevel() {
        // Tests 1-9: test 9 combinations of media property FRAME_INTERVAL and FRAME_RATE_CAP with the lower property levels not specified.

        // Test1: media level test with both FRAME_INTERVAL and FRAME_RATE_CAP specified (FRAME_RATE_CAP override applies) with the lower property level not specified.
        checkCalcFrameInterval(-1,-1, null,null, null,null, null,null, 7,5, 30, 6);

        // Test2: media level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP specified with the lower property level not specified.
        checkCalcFrameInterval(-1,-1, null,null, null,null, null,null, null,5, 30, 6);

        // Test3: media level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP specified with the lower property level not specified.
        checkCalcFrameInterval(-1,-1, null,null, null,null, null,null, -1,5, 30, 6);

        // Test4: media level test with FRAME_INTERVAL specified and FRAME_RATE_CAP not specified with the lower property level not specified.
        checkCalcFrameInterval(-1,-1, null,null, null,null, null,null, 7,null, 30, 7);

        // Test5: media level test with neither FRAME_INTERVAL nor FRAME_RATE_CAP specified with the lower property level not specified.
        checkCalcFrameInterval(-1,-1, null,null, null,null, null,null, null,null, 30, 1);

        // Test6: media level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP not specified with the lower property level not specified.
        checkCalcFrameInterval(-1,-1, null,null, null,null, null,null, -1,null, 30, 1);

        // Test7: media level test with FRAME_INTERVAL specified and FRAME_RATE_CAP disabled with the lower property level not specified.
        checkCalcFrameInterval(-1,-1, null,null, null,null, null,null, 7,-1, 30, 7);

        // Test8: media level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP disabled with the lower property level not specified.
        checkCalcFrameInterval(-1,-1, null,null, null,null, null,null, null,-1, 30, 1);

        // Test9: media level test with both FRAME_INTERVAL and FRAME_RATE_CAP disabled with the lower property level not specified.
        checkCalcFrameInterval(-1,-1, null,null, null,null, null,null, -1,-1, 30, 1);

        // Tests 10-18: repeat the last 9 test combinations of media property FRAME_INTERVAL and FRAME_RATE_CAP
        // with the one-level-down FRAME_INTERVAL property specified (we won't be including any tests for one-level-down properties disabled)

        checkCalcFrameInterval(-1,-1, null,null, null,null, 3,null, 7,5, 30, 6);
        checkCalcFrameInterval(-1,-1, null,null, null,null, 3,null, null,5, 30, 6);
        checkCalcFrameInterval(-1,-1, null,null, null,null, 3,null, -1,5, 30, 6);
        checkCalcFrameInterval(-1,-1, null,null, null,null, 3,null, 7,null, 30, 7);
        checkCalcFrameInterval(-1,-1, null,null, null,null, 3,null, null,null, 30, 3);
        checkCalcFrameInterval(-1,-1, null,null, null,null, 3,null, -1,null, 30, 1);
        checkCalcFrameInterval(-1,-1, null,null, null,null, 3,null, 7,-1, 30, 7);
        checkCalcFrameInterval(-1,-1, null,null, null,null, 3,null, null,-1, 30, 3);
        checkCalcFrameInterval(-1,-1, null,null, null,null, 3,null, -1,-1, 30, 1);

        // Tests 19-28: repeat the first 9 test combinations of media property FRAME_INTERVAL and FRAME_RATE_CAP
        // with the one-level-down FRAME_RATE_CAP property specified (we won't be including any tests for one-level-down properties disabled)

        checkCalcFrameInterval(-1,-1, null,null, null,null, null,6, 7,5, 30, 6);
        checkCalcFrameInterval(-1,-1, null,null, null,null, null,6, null,5, 30, 6);
        checkCalcFrameInterval(-1,-1, null,null, null,null, null,6, -1,5, 30, 6);
        checkCalcFrameInterval(-1,-1, null,null, null,null, null,6, 7,null, 30, 7);
        checkCalcFrameInterval(-1,-1, null,null, null,null, null,6, null,null, 30, 5);
        checkCalcFrameInterval(-1,-1, null,null, null,null, null,6, -1,null, 30, 5);
        checkCalcFrameInterval(-1,-1, null,null, null,null, null,6, 7,-1, 30, 7);
        checkCalcFrameInterval(-1,-1, null,null, null,null, null,6, null,-1, 30, 1);
        checkCalcFrameInterval(-1,-1, null,null, null,null, null,6, -1,-1, 30, 1);

        // Tests 29-37: repeat the first 9 test combinations of media property FRAME_INTERVAL and FRAME_RATE_CAP
        // with the one-level-down with both FRAME_INTERVAL and FRAME_RATE_CAP properties specified (we won't be including any tests for one-level-down properties disabled)

        checkCalcFrameInterval(-1,-1, null,null, null,null, 3,6, 7,5, 30, 6);
        checkCalcFrameInterval(-1,-1, null,null, null,null, 3,6, null,5, 30, 6);
        checkCalcFrameInterval(-1,-1, null,null, null,null, 3,6, -1,5, 30, 6);
        checkCalcFrameInterval(-1,-1, null,null, null,null, 3,6, 7,null, 30, 7);
        checkCalcFrameInterval(-1,-1, null,null, null,null, 3,6, null,null, 30, 5);
        checkCalcFrameInterval(-1,-1, null,null, null,null, 3,6, -1,null, 30, 5);
        checkCalcFrameInterval(-1,-1, null,null, null,null, 3,6, 7,-1, 30, 7);
        checkCalcFrameInterval(-1,-1, null,null, null,null, 3,6, null,-1, 30, 3);
        checkCalcFrameInterval(-1,-1, null,null, null,null, 3,6, -1,-1, 30, 1);
    }

    /* DJV
    // Assign unique values based upon property level so we can better track application of property values. Defining these properties outside of
    // the methods we are using them in below so we can be sure that the property level values are unique and easily tracked.

    int actLevelFrameInterval = 2;
    int jobLevelFrameInterval = 3;
    int algLevelFrameInterval = 5;
    int medLevelFrameInterval = 7;
    int actLevelFrameRateCap = 10;
    int jobLevelFrameRateCap = 15;
    int algLevelFrameRateCap = 20;
    int medLevelFrameRateCap = 25;

    // Keeping two distinct variable names for marking a property as disabled or not specified so the frame rate cap tests will be more readable.
    Integer frameIntervalParameterDisable = -1;
    Integer frameRateCapParameterDisable = -1;
    Integer frameIntervalParameterNotSpecified = null;
    Integer frameRateCapParameterNotSpecified = null;


    // This method runs the 9 FRAME_RATE_CAP and FRAME_INTERVAL combinations at the media property level, plus includes an addition 9 tests
    // where a one-property-level-down (i.e. algorithm level) FRAME_INTERVAL is added to the 9 test cases, plus
    // includes an addition 9 tests where a one-property-level-down (i.e. algorithm level) FRAME_RATE_CAP
    // is added to the 9 test cases. This is a total of 27 media property level tests, 9 baseline plus 18 with one-level-down fallback (no disable) testing.
    @Test
    public void testFrameRateCapOverrideMediaLevelTest() throws Exception {

        DetectionProtobuf.DetectionRequest request;

        // TODO use Mock to test system property combinations. For now, just use what is available in PropertiesUtil. Question though,
        // Mock can change the system properties in the TestDetectionSplitter class, but PropertiesUtil which is used in DetectionSplitter class
        // will still be using settings from mpf.properties - so I'm not sure if using Mock will work to support changing combinations of
        // system properties test.

        // Tests 1-9: test 9 combinations of media property FRAME_INTERVAL and FRAME_RATE_CAP with the lower property levels not specified.

        // Test1: media level test with both FRAME_INTERVAL and FRAME_RATE_CAP specified (FRAME_RATE_CAP override applies) with the lower property level not specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(medLevelFrameInterval, medLevelFrameRateCap, // media level property values
                                                                          frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified, // algorithm level property values
                                                                          frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified, // job level property values
                                                                          frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified); // action level property values.

        // In this case, resultant FRAME_INTERVAL property value should be derived from the frame rate cap media property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(medLevelFrameRateCap)) );
        }));

        // Test2: media level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP specified with the lower property level not specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, medLevelFrameRateCap,
                                                                          frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                                          frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                                          frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from frame rate cap media property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(medLevelFrameRateCap)) );
        }));

        // Test3: media level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP specified with the lower property level not specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterDisable, medLevelFrameRateCap,
                                                                          frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                                          frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                                          frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from frame rate cap media property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(medLevelFrameRateCap)) );
        }));

        // Test4: media level test with FRAME_INTERVAL specified and FRAME_RATE_CAP not specified with the lower property level not specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(medLevelFrameInterval, frameRateCapParameterNotSpecified,
                                                                          frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                                          frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                                          frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be same as FRAME_INTERVAL media property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(medLevelFrameInterval) );
        }));

        // Test5: media level test with neither FRAME_INTERVAL nor FRAME_RATE_CAP specified with the lower property level not specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                                             frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                                             frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                                             frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from FRAME_RATE_CAP system property if not disabled, otherwise
        // the value should be the same as the FRAME_INTERVAL system property if not disabled, default to a value of 1 if both FRAME_RATE_CAP and
        // FRAME_INTERVAL system properties are disabled.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return ( prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    ( ( propertiesUtil.getFrameRateCap() > 0 && Integer.valueOf(prop.getPropertyValue()) == getVideoMediaExpectedComputedFrameInterval(propertiesUtil.getFrameRateCap()) ) ||
                        ( propertiesUtil.getSamplingInterval() > 0 && Integer.valueOf(prop.getPropertyValue()) == (int)propertiesUtil.getSamplingInterval() ) ||
                        ( propertiesUtil.getFrameRateCap() <= 0 && propertiesUtil.getSamplingInterval() <= 0 && Integer.valueOf(prop.getPropertyValue()) == 1 ) ) );
        }));

        // Test6: media level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP not specified with the lower property level not specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterDisable, frameRateCapParameterNotSpecified,
                                                                             frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                                             frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                                             frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from FRAME_RATE_CAP system property if not disabled, otherwise
        // the value should default to a value of 1 since FRAME_INTERVAL is disabled at the higher property level.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return ( prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                ( ( propertiesUtil.getFrameRateCap() > 0 && Integer.valueOf(prop.getPropertyValue()) == getVideoMediaExpectedComputedFrameInterval(propertiesUtil.getFrameRateCap()) ) ||
                    ( propertiesUtil.getFrameRateCap() <= 0 && Integer.valueOf(prop.getPropertyValue()) == 1 ) ) );
        }));


        // Test7: media level test with FRAME_INTERVAL specified and FRAME_RATE_CAP disabled with the lower property level not specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(medLevelFrameInterval, frameRateCapParameterDisable,
                                                                          frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                                          frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                                          frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be same as FRAME_INTERVAL media property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(medLevelFrameInterval) );
        }));

        // Test8: media level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP disabled with the lower property level not specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterDisable,
                                                                             frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                                             frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                                             frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, since FRAME_RATE_CAP is disabled at the higher property level, the resultant FRAME_INTERVAL property value
        // should be the same as the FRAME_INTERVAL system property if not disabled, default to a value of 1 if the
        // FRAME_INTERVAL system property is disabled.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return ( prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                ( ( propertiesUtil.getSamplingInterval() > 0 && Integer.valueOf(prop.getPropertyValue()) == (int)propertiesUtil.getSamplingInterval() ) ||
                    ( propertiesUtil.getSamplingInterval() <= 0 && Integer.valueOf(prop.getPropertyValue()) == 1 ) ) );
        }));

        // Test9: media level test with both FRAME_INTERVAL and FRAME_RATE_CAP disabled with the lower property level not specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterDisable, frameRateCapParameterDisable,
                                                                             frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                                             frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                                             frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, since both FRAME_RATE_CAP and FRAME_INTERVAL are disabled at the higher property level,
        // the resultant FRAME_INTERVAL property value default to a value of 1.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return ( prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) && Integer.valueOf(prop.getPropertyValue()) == 1 );
        }));


        // Tests 10-18: repeat the last 9 test combinations of media property FRAME_INTERVAL and FRAME_RATE_CAP
        // with the one-level-down FRAME_INTERVAL property specified (we won't be including any tests for one-level-down properties disabled)

        // Test10: media level test with both FRAME_INTERVAL and FRAME_RATE_CAP specified (FRAME_RATE_CAP override applies) with the one-level-down FRAME_INTERVAL property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(medLevelFrameInterval, medLevelFrameRateCap, // media level property values
            algLevelFrameInterval, frameRateCapParameterNotSpecified, // algorithm level property values
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified, // job level property values
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified); // action level property values.

        // In this case, resultant FRAME_INTERVAL property value should be derived from the frame rate cap media property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(medLevelFrameRateCap)) );
        }));

        // Test11: media level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP specified with the one-level-down FRAME_INTERVAL property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, medLevelFrameRateCap,
            algLevelFrameInterval, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from frame rate cap media property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(medLevelFrameRateCap)) );
        }));

        // Test12: media level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP specified with the one-level-down FRAME_INTERVAL property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterDisable, medLevelFrameRateCap,
            algLevelFrameInterval, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from frame rate cap media property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(medLevelFrameRateCap)) );
        }));

        // Test13: media level test with FRAME_INTERVAL specified and FRAME_RATE_CAP not specified with the one-level-down FRAME_INTERVAL property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(medLevelFrameInterval, frameRateCapParameterNotSpecified,
            algLevelFrameInterval, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be same as the FRAME_INTERVAL media property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(medLevelFrameInterval) );
        }));

        // Test14: media level test with neither FRAME_INTERVAL nor FRAME_RATE_CAP specified with the one-level-down FRAME_INTERVAL property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            algLevelFrameInterval, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be the same as FRAME_INTERVAL algorithm property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(algLevelFrameInterval) );
        }));

        // Test15: media level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP not specified with the one-level-down FRAME_INTERVAL property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterDisable, frameRateCapParameterNotSpecified,
            algLevelFrameInterval, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from FRAME_RATE_CAP system property if not disabled, otherwise
        // the value should default to a value of 1 since FRAME_INTERVAL is disabled at the higher property level.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return ( prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                ( ( propertiesUtil.getFrameRateCap() > 0 && Integer.valueOf(prop.getPropertyValue()) == getVideoMediaExpectedComputedFrameInterval(propertiesUtil.getFrameRateCap()) ) ||
                    ( propertiesUtil.getFrameRateCap() <= 0 && Integer.valueOf(prop.getPropertyValue()) == 1 ) ) );
        }));

        // Test16: media level test with FRAME_INTERVAL specified and FRAME_RATE_CAP disabled with the one-level-down FRAME_INTERVAL property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(medLevelFrameInterval, frameRateCapParameterDisable,
            algLevelFrameInterval, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be same as FRAME_INTERVAL media property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(medLevelFrameInterval) );
        }));

        // Test17: media level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP disabled with the one-level-down FRAME_INTERVAL property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterDisable,
            algLevelFrameInterval, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be same as FRAME_INTERVAL algorithm property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(algLevelFrameInterval) );
        }));

        // Test18: media level test with both FRAME_INTERVAL and FRAME_RATE_CAP disabled with the one-level-down FRAME_INTERVAL property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterDisable, frameRateCapParameterDisable,
            algLevelFrameInterval, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, since both FRAME_RATE_CAP and FRAME_INTERVAL are disabled at the higher property level,
        // the resultant FRAME_INTERVAL property value default to a value of 1.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return ( prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) && Integer.valueOf(prop.getPropertyValue()) == 1 );
        }));


        // Tests 19-28: repeat the first 9 test combinations of media property FRAME_INTERVAL and FRAME_RATE_CAP
        // with the one-level-down FRAME_RATE_CAP property specified (we won't be including any tests for one-level-down properties disabled)

        // Test19: media level test with both FRAME_INTERVAL and FRAME_RATE_CAP specified (FRAME_RATE_CAP override applies) with the one-level-down FRAME_RATE_CAP property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(medLevelFrameInterval, medLevelFrameRateCap, // media level property values
            frameIntervalParameterNotSpecified, algLevelFrameRateCap, // algorithm level property values
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified, // job level property values
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified); // action level property values.

        // In this case, resultant FRAME_INTERVAL property value should be derived from the frame rate cap media property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(medLevelFrameRateCap)) );
        }));

        // Test20: media level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP specified with the one-level-down FRAME_RATE_CAP property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, medLevelFrameRateCap,
            frameIntervalParameterNotSpecified, algLevelFrameRateCap,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from frame rate cap media property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(medLevelFrameRateCap)) );
        }));

        // Test21: media level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP specified with the one-level-down FRAME_RATE_CAP property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterDisable, medLevelFrameRateCap,
            frameIntervalParameterNotSpecified, algLevelFrameRateCap,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from frame rate cap media property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(medLevelFrameRateCap)) );
        }));

        // Test22: media level test with FRAME_INTERVAL specified and FRAME_RATE_CAP not specified with the one-level-down FRAME_RATE_CAP property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(medLevelFrameInterval, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, algLevelFrameRateCap,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be same as the FRAME_INTERVAL media property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(medLevelFrameInterval) );
        }));

        // Test23: media level test with neither FRAME_INTERVAL nor FRAME_RATE_CAP specified with the one-level-down FRAME_RATE_CAP property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, algLevelFrameRateCap,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from FRAME_RATE_CAP algorithm property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(algLevelFrameRateCap)) );
        }));

        // Test24: media level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP not specified with the one-level-down FRAME_RATE_CAP property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterDisable, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, algLevelFrameRateCap,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from FRAME_RATE_CAP algorithm property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(algLevelFrameRateCap)) );
        }));

        // Test25: media level test with FRAME_INTERVAL specified and FRAME_RATE_CAP disabled with the one-level-down FRAME_RATE_CAP property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(medLevelFrameInterval, frameRateCapParameterDisable,
            frameIntervalParameterNotSpecified, algLevelFrameRateCap,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be same as FRAME_INTERVAL media property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(medLevelFrameInterval) );
        }));

        // Test26: media level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP disabled with the one-level-down FRAME_RATE_CAP property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterDisable,
            frameIntervalParameterNotSpecified, algLevelFrameRateCap,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, since FRAME_RATE_CAP is disabled at the higher property level, the resultant FRAME_INTERVAL property value
        // should be the same as the FRAME_INTERVAL system property if not disabled, default to a value of 1 if the
        // FRAME_INTERVAL system property is disabled.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return ( prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                ( ( propertiesUtil.getSamplingInterval() > 0 && Integer.valueOf(prop.getPropertyValue()) == (int)propertiesUtil.getSamplingInterval() ) ||
                    ( propertiesUtil.getSamplingInterval() <= 0 && Integer.valueOf(prop.getPropertyValue()) == 1 ) ) );
        }));

        // Test27: media level test with both FRAME_INTERVAL and FRAME_RATE_CAP disabled with the one-level-down FRAME_RATE_CAP property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterDisable, frameRateCapParameterDisable,
            frameIntervalParameterNotSpecified, algLevelFrameRateCap,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, since both FRAME_RATE_CAP and FRAME_INTERVAL are disabled at the higher property level,
        // the resultant FRAME_INTERVAL property value default to a value of 1.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return ( prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) && Integer.valueOf(prop.getPropertyValue()) == 1 );
        }));

    } // End of method testFrameRateCapOverrideMediaLevelTest


    // This method runs the 9 FRAME_RATE_CAP and FRAME_INTERVAL combinations at the algorithm property level, plus includes an addition 9 tests
    // where a one-property-level-down (i.e. job level) FRAME_INTERVAL is added to the 9 test cases, plus
    // includes an addition 9 tests where a one-property-level-down (i.e. job level) FRAME_RATE_CAP
    // is added to the 9 test cases. This is a total of 27 algorithm property level tests, 9 baseline plus 18 with one-level-down fallback (no disable) testing.
    @Test
    public void testFrameRateCapOverrideAlgorithmLevelTest() throws Exception {

        DetectionProtobuf.DetectionRequest request;

        // TODO use Mock to test system property combinations. For now, just use what is available in PropertiesUtil. Question though,
        // Mock can change the system properties in the TestDetectionSplitter class, but PropertiesUtil which is used in DetectionSplitter class
        // will still be using settings from mpf.properties - so I'm not sure if using Mock will work to support changing combinations of
        // system properties test.

        // Tests 1-9: test 9 combinations of algorithm property FRAME_INTERVAL and FRAME_RATE_CAP with the lower property levels not specified.

        // Test1: algorithm level test with both FRAME_INTERVAL and FRAME_RATE_CAP specified (FRAME_RATE_CAP override applies) with the lower property level not specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified, // media level property values
                                                        algLevelFrameInterval, algLevelFrameRateCap, // algorithm level property values
                                                        frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified, // job level property values
                                                        frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified); // action level property values.

        // In this case, resultant FRAME_INTERVAL property value should be derived from the frame rate cap algorithm property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(algLevelFrameRateCap)));
        }));

        // Test2: algorithm level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP specified with the lower property level not specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                        frameIntervalParameterNotSpecified, algLevelFrameRateCap,
                                                        frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                        frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from frame rate cap algorithm property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue())
                    .equals(getVideoMediaExpectedComputedFrameInterval(algLevelFrameRateCap)));
        }));

        // Test3: algorithm level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP specified with the lower property level not specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                        frameIntervalParameterDisable, algLevelFrameRateCap,
                                                        frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                        frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from frame rate cap algorithm property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(algLevelFrameRateCap)));
        }));

        // Test4: algorithm level test with FRAME_INTERVAL specified and FRAME_RATE_CAP not specified with the lower property level not specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                        algLevelFrameInterval, frameRateCapParameterNotSpecified,
                                                        frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                        frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be same as FRAME_INTERVAL algorithm property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(algLevelFrameInterval));
        }));

        // Test5: algorithm level test with neither FRAME_INTERVAL nor FRAME_RATE_CAP specified with the lower property level not specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                         frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                         frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                         frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from FRAME_RATE_CAP system property if not disabled, otherwise
        // the value should be the same as the FRAME_INTERVAL system property if not disabled, default to a value of 1 if both FRAME_RATE_CAP and
        // FRAME_INTERVAL system properties are disabled.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                ((propertiesUtil.getFrameRateCap() > 0 && Integer.valueOf(prop.getPropertyValue())
                    == getVideoMediaExpectedComputedFrameInterval(propertiesUtil.getFrameRateCap()))
                    ||
                    (propertiesUtil.getSamplingInterval() > 0
                        && Integer.valueOf(prop.getPropertyValue()) == (int) propertiesUtil
                        .getSamplingInterval()) ||
                    (propertiesUtil.getFrameRateCap() <= 0
                        && propertiesUtil.getSamplingInterval() <= 0
                        && Integer.valueOf(prop.getPropertyValue()) == 1)));
        }));

        // Test6: algorithm level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP not specified with the lower property level not specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                    frameIntervalParameterDisable, frameRateCapParameterNotSpecified,
                                                    frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                    frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from FRAME_RATE_CAP system property if not disabled, otherwise
        // the value should default to a value of 1 since FRAME_INTERVAL is disabled at the higher property level.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                ((propertiesUtil.getFrameRateCap() > 0 && Integer.valueOf(prop.getPropertyValue())
                    == getVideoMediaExpectedComputedFrameInterval(propertiesUtil.getFrameRateCap()))
                    ||
                    (propertiesUtil.getFrameRateCap() <= 0
                        && Integer.valueOf(prop.getPropertyValue()) == 1)));
        }));

        // Test7: algorithm level test with FRAME_INTERVAL specified and FRAME_RATE_CAP disabled with the lower property level not specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                    algLevelFrameInterval, frameRateCapParameterDisable,
                                                    frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                    frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be same as FRAME_INTERVAL algorithm property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(algLevelFrameInterval));
        }));

        // Test8: algorithm level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP disabled with the lower property level not specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                     frameIntervalParameterNotSpecified, frameRateCapParameterDisable,
                                                     frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                     frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, since FRAME_RATE_CAP is disabled at the higher property level, the resultant FRAME_INTERVAL property value
        // should be the same as the FRAME_INTERVAL system property if not disabled, default to a value of 1 if the
        // FRAME_INTERVAL system property is disabled.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                ((propertiesUtil.getSamplingInterval() > 0
                    && Integer.valueOf(prop.getPropertyValue()) == (int) propertiesUtil
                    .getSamplingInterval()) ||
                    (propertiesUtil.getSamplingInterval() <= 0
                        && Integer.valueOf(prop.getPropertyValue()) == 1)));
        }));

        // Test9: algorithm level test with both FRAME_INTERVAL and FRAME_RATE_CAP disabled with the lower property level not specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                        frameIntervalParameterDisable, frameRateCapParameterDisable,
                                                        frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                        frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, since both FRAME_RATE_CAP and FRAME_INTERVAL are disabled at the algorithm property level,
        // the resultant FRAME_INTERVAL property value default to a value of 1.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)
                && Integer.valueOf(prop.getPropertyValue()) == 1);
        }));

        // Tests 10-18: repeat the last 9 test combinations of algorithm property FRAME_INTERVAL and FRAME_RATE_CAP
        // with the one-level-down FRAME_INTERVAL job property specified (we won't be including any tests for one-level-down properties disabled)

        // Test10: algorithm level test with both FRAME_INTERVAL and FRAME_RATE_CAP specified (FRAME_RATE_CAP override applies) with the one-lower-down property level specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified, // media level property values
            algLevelFrameInterval, algLevelFrameRateCap, // algorithm level property values
            jobLevelFrameInterval, frameRateCapParameterNotSpecified, // job level property values
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified); // action level property values.

        // In this case, resultant FRAME_INTERVAL property value should be derived from the frame rate cap algorithm property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(algLevelFrameRateCap)));
        }));

        // Test11: algorithm level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP specified with the with the one-lower-down property level specified
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, algLevelFrameRateCap,
            jobLevelFrameInterval, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from frame rate cap algorithm property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue())
                    .equals(getVideoMediaExpectedComputedFrameInterval(algLevelFrameRateCap)));
        }));

        // Test12: algorithm level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP specified with the one-lower-down property level specified
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterDisable, algLevelFrameRateCap,
            jobLevelFrameInterval, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from frame rate cap algorithm property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(algLevelFrameRateCap)));
        }));

        // Test13: algorithm level test with FRAME_INTERVAL specified and FRAME_RATE_CAP not specified with the one-lower-down property level specified
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            algLevelFrameInterval, frameRateCapParameterNotSpecified,
            jobLevelFrameInterval, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be same as FRAME_INTERVAL algorithm property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(algLevelFrameInterval));
        }));

        // Test14: algorithm level test with neither FRAME_INTERVAL nor FRAME_RATE_CAP specified with the one-lower-down property level specified
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            jobLevelFrameInterval, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be same as FRAME_INTERVAL job property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(jobLevelFrameInterval));
        }));

        // Test15: algorithm level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP not specified with the one-lower-down property level specified
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterDisable, frameRateCapParameterNotSpecified,
            jobLevelFrameInterval, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from FRAME_RATE_CAP system property if not disabled, otherwise
        // the value should default to a value of 1 since FRAME_INTERVAL is disabled at the higher property level.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                ((propertiesUtil.getFrameRateCap() > 0 && Integer.valueOf(prop.getPropertyValue())
                    == getVideoMediaExpectedComputedFrameInterval(propertiesUtil.getFrameRateCap()))
                    ||
                    (propertiesUtil.getFrameRateCap() <= 0
                        && Integer.valueOf(prop.getPropertyValue()) == 1)));
        }));

        // Test16: algorithm level test with FRAME_INTERVAL specified and FRAME_RATE_CAP disabled with the one-lower-down property level specified
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            algLevelFrameInterval, frameRateCapParameterDisable,
            jobLevelFrameInterval, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be same as FRAME_INTERVAL algorithm property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(algLevelFrameInterval));
        }));

        // Test17: algorithm level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP disabled with the one-lower-down property level specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterDisable,
            jobLevelFrameInterval, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, since FRAME_RATE_CAP is disabled at the higher property level, the resultant FRAME_INTERVAL property value
        // should be the same as the FRAME_INTERVAL job property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(jobLevelFrameInterval));
        }));

        // Test18: algorithm level test with both FRAME_INTERVAL and FRAME_RATE_CAP disabled with the one-lower-down property level specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterDisable, frameRateCapParameterDisable,
            jobLevelFrameInterval, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, since both FRAME_RATE_CAP and FRAME_INTERVAL are disabled at the algorithm property level,
        // the resultant FRAME_INTERVAL property value default to a value of 1.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)
                && Integer.valueOf(prop.getPropertyValue()) == 1);
        }));

        // Tests 19-27: repeat the last 9 test combinations of algorithm property FRAME_INTERVAL and FRAME_RATE_CAP
        // with the one-level-down FRAME_RATE_CAP job property specified (we won't be including any tests for one-level-down properties disabled)


        // Test19: algorithm level test with both FRAME_INTERVAL and FRAME_RATE_CAP specified (FRAME_RATE_CAP override applies) with the one-lower-down property level specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified, // media level property values
            algLevelFrameInterval, algLevelFrameRateCap, // algorithm level property values
            frameIntervalParameterNotSpecified, jobLevelFrameRateCap, // job level property values
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified); // action level property values.

        // In this case, resultant FRAME_INTERVAL property value should be derived from the frame rate cap algorithm property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(algLevelFrameRateCap)));
        }));

        // Test20: algorithm level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP specified with the one-lower-down property level specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, algLevelFrameRateCap,
            frameIntervalParameterNotSpecified, jobLevelFrameRateCap,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from frame rate cap algorithm property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue())
                    .equals(getVideoMediaExpectedComputedFrameInterval(algLevelFrameRateCap)));
        }));

        // Test21: algorithm level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP specified with the one-lower-down property level specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterDisable, algLevelFrameRateCap,
            frameIntervalParameterNotSpecified, jobLevelFrameRateCap,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from frame rate cap algorithm property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(algLevelFrameRateCap)));
        }));

        // Test22: algorithm level test with FRAME_INTERVAL specified and FRAME_RATE_CAP not specified with the one-lower-down property level specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            algLevelFrameInterval, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, jobLevelFrameRateCap,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be same as FRAME_INTERVAL algorithm property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(algLevelFrameInterval));
        }));

        // Test23: algorithm level test with neither FRAME_INTERVAL nor FRAME_RATE_CAP specified with the one-lower-down property level specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, jobLevelFrameRateCap,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from frame rate cap job property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(jobLevelFrameRateCap)));
        }));

        // Test24: algorithm level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP not specified with the one-lower-down property level specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterDisable, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, jobLevelFrameRateCap,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from frame rate cap job property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(jobLevelFrameRateCap)));
        }));

        // Test25: algorithm level test with FRAME_INTERVAL specified and FRAME_RATE_CAP disabled with the one-lower-down property level specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            algLevelFrameInterval, frameRateCapParameterDisable,
            frameIntervalParameterNotSpecified, jobLevelFrameRateCap,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be same as FRAME_INTERVAL algorithm property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(algLevelFrameInterval));
        }));

        // Test26: algorithm level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP disabled wwith the one-lower-down property level specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterDisable,
            frameIntervalParameterNotSpecified, jobLevelFrameRateCap,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, since FRAME_RATE_CAP is disabled at the higher property level, the resultant FRAME_INTERVAL property value
        // should be the same as the FRAME_INTERVAL system property if not disabled, default to a value of 1 if the
        // FRAME_INTERVAL system property is disabled.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                ((propertiesUtil.getSamplingInterval() > 0
                    && Integer.valueOf(prop.getPropertyValue()) == (int) propertiesUtil
                    .getSamplingInterval()) ||
                    (propertiesUtil.getSamplingInterval() <= 0
                        && Integer.valueOf(prop.getPropertyValue()) == 1)));
        }));

        // Test27: algorithm level test with both FRAME_INTERVAL and FRAME_RATE_CAP disabled with the one-lower-down property level specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterDisable, frameRateCapParameterDisable,
            frameIntervalParameterNotSpecified, jobLevelFrameRateCap,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, since both FRAME_RATE_CAP and FRAME_INTERVAL are disabled at the algorithm property level,
        // the resultant FRAME_INTERVAL property value default to a value of 1.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)
                && Integer.valueOf(prop.getPropertyValue()) == 1);
        }));


    } // end of testFrameRateCapOverrideAlgorithmLevelTest


    // This method runs the 9 FRAME_RATE_CAP and FRAME_INTERVAL combinations at the job property level, plus includes an addition 9 tests
    // where a one-property-level-down (i.e. action level) FRAME_INTERVAL is added to the 9 test cases, plus
    // includes an addition 9 tests where a one-property-level-down (i.e. action level) FRAME_RATE_CAP
    // is added to the 9 test cases. This is a total of 27 job property level tests, 9 baseline plus 18 with one-level-down fallback (no disable) testing.
    @Test
    public void testFrameRateCapOverrideJobLevelTest() throws Exception {

        DetectionProtobuf.DetectionRequest request;

        // TODO use Mock to test system property combinations. For now, just use what is available in PropertiesUtil. Question though,
        // Mock can change the system properties in the TestDetectionSplitter class, but PropertiesUtil which is used in DetectionSplitter class
        // will still be using settings from mpf.properties - so I'm not sure if using Mock will work to support changing combinations of
        // system properties test.

        // Tests 1-9: test 9 combinations of job property FRAME_INTERVAL and FRAME_RATE_CAP with the lower property levels not specified.

        // Test1: job level test with both FRAME_INTERVAL and FRAME_RATE_CAP specified (FRAME_RATE_CAP override applies) with the lower property level not specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,  // media level property values
                                            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified, // algorithm level property values
                                            jobLevelFrameInterval, jobLevelFrameRateCap, // job level property values
                                            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified); // action level property values.

        // In this case, resultant FRAME_INTERVAL property value should be derived from the frame rate cap job property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(jobLevelFrameRateCap)));
        }));

        // Test2: job level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP specified with the lower property level not specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                frameIntervalParameterNotSpecified, jobLevelFrameRateCap,
                                                frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from frame rate cap job property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(jobLevelFrameRateCap)));
        }));

        // Test3: job level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP specified with the lower property level not specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                    frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                    frameIntervalParameterDisable, jobLevelFrameRateCap,
                                                    frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from frame rate cap job property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(jobLevelFrameRateCap)));
        }));

        // Test4: job level test with FRAME_INTERVAL specified and FRAME_RATE_CAP not specified with the lower property level not specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                       frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                       jobLevelFrameInterval, frameRateCapParameterNotSpecified,
                                                       frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be same as FRAME_INTERVAL job property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(jobLevelFrameInterval));
        }));

        // Test5: job level test with neither FRAME_INTERVAL nor FRAME_RATE_CAP specified with the lower property level not specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                           frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                           frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                           frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from FRAME_RATE_CAP system property if not disabled, otherwise
        // the value should be the same as the FRAME_INTERVAL system property if not disabled, default to a value of 1 if both FRAME_RATE_CAP and
        // FRAME_INTERVAL system properties are disabled.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                ((propertiesUtil.getFrameRateCap() > 0 && Integer.valueOf(prop.getPropertyValue())
                    == getVideoMediaExpectedComputedFrameInterval(propertiesUtil.getFrameRateCap()))
                    ||
                    (propertiesUtil.getSamplingInterval() > 0
                        && Integer.valueOf(prop.getPropertyValue()) == (int) propertiesUtil
                        .getSamplingInterval()) ||
                    (propertiesUtil.getFrameRateCap() <= 0
                        && propertiesUtil.getSamplingInterval() <= 0
                        && Integer.valueOf(prop.getPropertyValue()) == 1)));
        }));

        // Test6: job level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP not specified with the lower property level not specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                            frameIntervalParameterDisable, frameRateCapParameterNotSpecified,
                                                            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from FRAME_RATE_CAP system property if not disabled, otherwise
        // the value should default to a value of 1 since FRAME_INTERVAL is disabled at the higher property level.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                ((propertiesUtil.getFrameRateCap() > 0 && Integer.valueOf(prop.getPropertyValue()) == getVideoMediaExpectedComputedFrameInterval(propertiesUtil.getFrameRateCap())) ||
                    (propertiesUtil.getFrameRateCap() <= 0 && Integer.valueOf(prop.getPropertyValue()) == 1)));
        }));

        // Test7: job level test with FRAME_INTERVAL specified and FRAME_RATE_CAP disabled with the lower property level not specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                            jobLevelFrameInterval, frameRateCapParameterDisable,
                                                            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be same as FRAME_INTERVAL job property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(jobLevelFrameInterval));
        }));

        // Test8: job level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP disabled with the lower property level not specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                             frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                             frameIntervalParameterNotSpecified, frameRateCapParameterDisable,
                                                             frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, since FRAME_RATE_CAP is disabled at the higher property level, the resultant FRAME_INTERVAL property value
        // should be the same as the FRAME_INTERVAL system property if not disabled, default to a value of 1 if the
        // FRAME_INTERVAL system property is disabled.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                ((propertiesUtil.getSamplingInterval() > 0 && Integer.valueOf(prop.getPropertyValue()) == (int) propertiesUtil.getSamplingInterval()) ||
                    (propertiesUtil.getSamplingInterval() <= 0 && Integer.valueOf(prop.getPropertyValue()) == 1)));
        }));

        // Test9: job level test with both FRAME_INTERVAL and FRAME_RATE_CAP disabled with the lower property level not specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                              frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                              frameIntervalParameterDisable, frameRateCapParameterDisable,
                                                              frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, since both FRAME_RATE_CAP and FRAME_INTERVAL are disabled at the job property level,
        // the resultant FRAME_INTERVAL property value default to a value of 1.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) && Integer.valueOf(prop.getPropertyValue()) == 1);
        }));


        // Tests 10-18: test 9 combinations of job property FRAME_INTERVAL and FRAME_RATE_CAP with the one-level-down FRAME_INTERVAL property specified.

        // Test10: job level test with both FRAME_INTERVAL and FRAME_RATE_CAP specified (FRAME_RATE_CAP override applies) with the one-level-down FRAME_INTERVAL property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,  // media level property values
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified, // algorithm level property values
            jobLevelFrameInterval, jobLevelFrameRateCap, // job level property values
            actLevelFrameInterval, frameRateCapParameterNotSpecified); // action level property values.

        // In this case, resultant FRAME_INTERVAL property value should be derived from the frame rate cap job property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(jobLevelFrameRateCap)));
        }));

        // Test11: job level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP specified with the one-level-down FRAME_INTERVAL property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, jobLevelFrameRateCap,
            actLevelFrameInterval, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from frame rate cap job property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(jobLevelFrameRateCap)));
        }));

        // Test12: job level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP specified with the one-level-down FRAME_INTERVAL property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterDisable, jobLevelFrameRateCap,
            actLevelFrameInterval, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from frame rate cap job property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(jobLevelFrameRateCap)));
        }));

        // Test13: job level test with FRAME_INTERVAL specified and FRAME_RATE_CAP not specified with the one-level-down FRAME_INTERVAL property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            jobLevelFrameInterval, frameRateCapParameterNotSpecified,
            actLevelFrameInterval, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be same as FRAME_INTERVAL job property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(jobLevelFrameInterval));
        }));

        // Test14: job level test with neither FRAME_INTERVAL nor FRAME_RATE_CAP specified with the one-level-down FRAME_INTERVAL property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            actLevelFrameInterval, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be same as FRAME_INTERVAL action property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(actLevelFrameInterval));
        }));

        // Test15: job level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP not specified with the one-level-down FRAME_INTERVAL property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterDisable, frameRateCapParameterNotSpecified,
            actLevelFrameInterval, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from FRAME_RATE_CAP system property if not disabled, otherwise
        // the value should default to a value of 1 since FRAME_INTERVAL is disabled at the higher property level.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                ((propertiesUtil.getFrameRateCap() > 0 && Integer.valueOf(prop.getPropertyValue()) == getVideoMediaExpectedComputedFrameInterval(propertiesUtil.getFrameRateCap())) ||
                    (propertiesUtil.getFrameRateCap() <= 0 && Integer.valueOf(prop.getPropertyValue()) == 1)));
        }));

        // Test16: job level test with FRAME_INTERVAL specified and FRAME_RATE_CAP disabled with the one-level-down FRAME_INTERVAL property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            jobLevelFrameInterval, frameRateCapParameterDisable,
            actLevelFrameInterval, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be same as FRAME_INTERVAL job property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(jobLevelFrameInterval));
        }));

        // Test17: job level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP disabled with the one-level-down FRAME_INTERVAL property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterDisable,
            actLevelFrameInterval, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be same as FRAME_INTERVAL action property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(actLevelFrameInterval));
        }));

        // Test18: job level test with both FRAME_INTERVAL and FRAME_RATE_CAP disabled with the one-level-down FRAME_INTERVAL property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterDisable, frameRateCapParameterDisable,
            actLevelFrameInterval, frameRateCapParameterNotSpecified);

        // In this case, since both FRAME_RATE_CAP and FRAME_INTERVAL are disabled at the job property level,
        // the resultant FRAME_INTERVAL property value default to a value of 1.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) && Integer.valueOf(prop.getPropertyValue()) == 1);
        }));


        // Tests 19-27: test 9 combinations of job property FRAME_INTERVAL and FRAME_RATE_CAP with the one-level-down FRAME_RATE_CAP property specified.

        // Test19: job level test with both FRAME_INTERVAL and FRAME_RATE_CAP specified (FRAME_RATE_CAP override applies) with the one-level-down FRAME_RATE_CAP property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,  // media level property values
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified, // algorithm level property values
            jobLevelFrameInterval, jobLevelFrameRateCap, // job level property values
            frameIntervalParameterNotSpecified, actLevelFrameRateCap); // action level property values.

        // In this case, resultant FRAME_INTERVAL property value should be derived from the frame rate cap job property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(jobLevelFrameRateCap)));
        }));

        // Test20: job level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP specified with the one-level-down FRAME_RATE_CAP property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, jobLevelFrameRateCap,
            frameIntervalParameterNotSpecified, actLevelFrameRateCap);

        // In this case, resultant FRAME_INTERVAL property value should be derived from frame rate cap job property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(jobLevelFrameRateCap)));
        }));

        // Test21: job level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP specified with the one-level-down FRAME_RATE_CAP property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterDisable, jobLevelFrameRateCap,
            frameIntervalParameterNotSpecified, actLevelFrameRateCap);

        // In this case, resultant FRAME_INTERVAL property value should be derived from frame rate cap job property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(jobLevelFrameRateCap)));
        }));

        // Test22: job level test with FRAME_INTERVAL specified and FRAME_RATE_CAP not specified with the one-level-down FRAME_RATE_CAP property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            jobLevelFrameInterval, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, actLevelFrameRateCap);

        // In this case, resultant FRAME_INTERVAL property value should be same as FRAME_INTERVAL job property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(jobLevelFrameInterval));
        }));

        // Test23: job level test with neither FRAME_INTERVAL nor FRAME_RATE_CAP specified with the one-level-down FRAME_RATE_CAP property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, actLevelFrameRateCap);

        // In this case, resultant FRAME_INTERVAL property value should be derived from frame rate cap action property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(actLevelFrameRateCap)));
        }));

        // Test24: job level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP not specified with the one-level-down FRAME_RATE_CAP property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterDisable, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, actLevelFrameRateCap);

        // In this case, resultant FRAME_INTERVAL property value should be derived from frame rate cap action property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(actLevelFrameRateCap)));
        }));

        // Test25: job level test with FRAME_INTERVAL specified and FRAME_RATE_CAP disabled with the one-level-down FRAME_RATE_CAP property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            jobLevelFrameInterval, frameRateCapParameterDisable,
            frameIntervalParameterNotSpecified, actLevelFrameRateCap);

        // In this case, resultant FRAME_INTERVAL property value should be same as FRAME_INTERVAL job property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue()).equals(jobLevelFrameInterval));
        }));

        // Test26: job level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP disabled with the one-level-down FRAME_RATE_CAP property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterDisable,
            frameIntervalParameterNotSpecified, actLevelFrameRateCap);

        // In this case, since FRAME_RATE_CAP is disabled at the higher property level, the resultant FRAME_INTERVAL property value
        // should be the same as the FRAME_INTERVAL system property if not disabled, default to a value of 1 if the
        // FRAME_INTERVAL system property is disabled.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                ((propertiesUtil.getSamplingInterval() > 0 && Integer.valueOf(prop.getPropertyValue()) == (int) propertiesUtil.getSamplingInterval()) ||
                    (propertiesUtil.getSamplingInterval() <= 0 && Integer.valueOf(prop.getPropertyValue()) == 1)));
        }));

        // Test27: job level test with both FRAME_INTERVAL and FRAME_RATE_CAP disabled with the one-level-down FRAME_RATE_CAP property specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
            frameIntervalParameterDisable, frameRateCapParameterDisable,
            frameIntervalParameterNotSpecified, actLevelFrameRateCap);

        // In this case, since both FRAME_RATE_CAP and FRAME_INTERVAL are disabled at the job property level,
        // the resultant FRAME_INTERVAL property value default to a value of 1.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) && Integer.valueOf(prop.getPropertyValue()) == 1);
        }));



    } // end of method testFrameRateCapOverrideJobLevelTest



    // This method runs the 9 FRAME_RATE_CAP and FRAME_INTERVAL combinations at the action property level.
    @Test
    public void testFrameRateCapOverrideActionLevelTest() throws Exception {

        DetectionProtobuf.DetectionRequest request;

        // TODO use Mock to test system property combinations. For now, just use what is available in PropertiesUtil. Question though,
        // Mock can change the system properties in the TestDetectionSplitter class, but PropertiesUtil which is used in DetectionSplitter class
        // will still be using settings from mpf.properties - so I'm not sure if using Mock will work to support changing combinations of
        // system properties test.

        // Tests 1-9: test 9 combinations of action property FRAME_INTERVAL and FRAME_RATE_CAP.

        // Test1: action level test with both FRAME_INTERVAL and FRAME_RATE_CAP specified (FRAME_RATE_CAP override applies).
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified, // media level property values
                                                    frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified, // algorithm level property values
                                                    frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified, // job level property values
                                                    actLevelFrameInterval, actLevelFrameRateCap); // action level property values.

        // In this case, resultant FRAME_INTERVAL property value should be derived from the frame rate cap action property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(actLevelFrameRateCap)));
        }));

        // Test2: action level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                     frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                     frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                     frameIntervalParameterNotSpecified, actLevelFrameRateCap);

        // In this case, resultant FRAME_INTERVAL property value should be derived from frame rate cap action property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                Integer.valueOf(prop.getPropertyValue())
                    .equals(getVideoMediaExpectedComputedFrameInterval(actLevelFrameRateCap)));
        }));

        // Test3: action level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                       frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                       frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                       frameIntervalParameterDisable, actLevelFrameRateCap);

        // In this case, resultant FRAME_INTERVAL property value should be derived from frame rate cap action property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(getVideoMediaExpectedComputedFrameInterval(actLevelFrameRateCap)));
        }));

        // Test4: action level test with FRAME_INTERVAL specified and FRAME_RATE_CAP not specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                        frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                        frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                        actLevelFrameInterval, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be same as FRAME_INTERVAL actioin property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(actLevelFrameInterval));
        }));

        // Test5: action level test with neither FRAME_INTERVAL nor FRAME_RATE_CAP specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                         frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                         frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                         frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from FRAME_RATE_CAP system property if not disabled, otherwise
        // the value should be the same as the FRAME_INTERVAL system property if not disabled, default to a value of 1 if both FRAME_RATE_CAP and
        // FRAME_INTERVAL system properties are disabled.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                ((propertiesUtil.getFrameRateCap() > 0 && Integer.valueOf(prop.getPropertyValue()) == getVideoMediaExpectedComputedFrameInterval(propertiesUtil.getFrameRateCap())) ||
                    (propertiesUtil.getSamplingInterval() > 0 && Integer.valueOf(prop.getPropertyValue()) == (int) propertiesUtil.getSamplingInterval()) ||
                    (propertiesUtil.getFrameRateCap() <= 0 && propertiesUtil.getSamplingInterval() <= 0 && Integer.valueOf(prop.getPropertyValue()) == 1)));
        }));

        // Test6: action level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP not specified.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                        frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                        frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                        frameIntervalParameterDisable, frameRateCapParameterNotSpecified);

        // In this case, resultant FRAME_INTERVAL property value should be derived from FRAME_RATE_CAP system property if not disabled, otherwise
        // the value should default to a value of 1 since FRAME_INTERVAL is disabled at the higher property level.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                ((propertiesUtil.getFrameRateCap() > 0 && Integer.valueOf(prop.getPropertyValue())
                    == getVideoMediaExpectedComputedFrameInterval(propertiesUtil.getFrameRateCap()))
                    ||
                    (propertiesUtil.getFrameRateCap() <= 0
                        && Integer.valueOf(prop.getPropertyValue()) == 1)));
        }));

        // Test7: job level test with FRAME_INTERVAL specified and FRAME_RATE_CAP disabled.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                       frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                       frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                       actLevelFrameInterval, frameRateCapParameterDisable);

        // In this case, resultant FRAME_INTERVAL property value should be same as FRAME_INTERVAL action property.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                    Integer.valueOf(prop.getPropertyValue()).equals(actLevelFrameInterval));
        }));

        // Test8: action level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP disabled.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                        frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                        frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                        frameIntervalParameterNotSpecified, frameRateCapParameterDisable);

        // In this case, since FRAME_RATE_CAP is disabled at the action property level, the resultant FRAME_INTERVAL property value
        // should be the same as the FRAME_INTERVAL system property if not disabled, default to a value of 1 if the
        // FRAME_INTERVAL system property is disabled.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY) &&
                ((propertiesUtil.getSamplingInterval() > 0
                    && Integer.valueOf(prop.getPropertyValue()) == (int) propertiesUtil
                    .getSamplingInterval()) ||
                    (propertiesUtil.getSamplingInterval() <= 0
                        && Integer.valueOf(prop.getPropertyValue()) == 1)));
        }));

        // Test9: action level test with both FRAME_INTERVAL and FRAME_RATE_CAP disabled.
        request = createFrameRateTestTransientJobAndPerformDetectionSplit(frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                         frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                         frameIntervalParameterNotSpecified, frameRateCapParameterNotSpecified,
                                                         frameIntervalParameterDisable, frameRateCapParameterDisable);

        // In this case, since both FRAME_RATE_CAP and FRAME_INTERVAL are disabled at the action property level,
        // the resultant FRAME_INTERVAL property value default to a value of 1.
        Assert.assertTrue(request.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)
                && Integer.valueOf(prop.getPropertyValue()) == 1);
        }));
    } // end of method testFrameRateCapOverrideActionLevelTest
    */

}
