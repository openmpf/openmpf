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


    // The next set of DetectionSplitter tests specifically test FRAME_RATE_CAP vs. FRAME_INTERVAL property overrides.

    private List<TransientMedia> createFrameRateCapTestVideoMedia( String mediaFilename, int mediaLength, Map<String, String> mediaProperties ) {
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

    private TransientJob createFrameRateCapTestJob( long jobId, String externalId, List<TransientMedia> listMedia,
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

        // Need to run MediaInspectionProcessor on the Media, so that inspectMedia to add FPS and other metadata to the TransientMedia for this test to work
        for (TransientMedia media : redis.getJob(jobId).getMedia()) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.getIn().getHeaders().put(MpfHeaders.JOB_ID, jobId);
            exchange.getIn().setBody(jsonUtils.serialize(media));
            exchange.getOut().setHeader(MpfHeaders.JOB_ID, jobId);
            mediaInspectionProcessor.wfmProcess(exchange);
        }

        // Return the job refreshed from REDIS since the mediaInspectionProcessor may have updated the jobs media
        return redis.getJob(jobId);
    }

    public DetectionProtobuf.DetectionRequest createTransientJobAndDoDetectionSplit(long videoJobId, String externalId,
                                    Map<String, String> actionProperties, Map<String, String> jobProperties,
                                    Map<String, Map> algorithmProperties, Map<String, String> mediaProperties) throws Exception {

        List<TransientMedia> listMedia = createFrameRateCapTestVideoMedia( "/samples/video_01.mp4", 300, mediaProperties );
        TransientJob testJob = createFrameRateCapTestJob( videoJobId, externalId, listMedia,
                                                          actionProperties, jobProperties, algorithmProperties);

        // Run the DetectionSplitter on this job, and return the response for evaluation.
        List<Message> responseList = detectionStageSplitter.performSplit(testJob, testJob.getPipeline().getStages().get(0));

        Assert.assertTrue(responseList.size() >= 1);
        Message message = responseList.get(0);
        Assert.assertTrue(message.getBody() instanceof DetectionProtobuf.DetectionRequest);

        return (DetectionProtobuf.DetectionRequest) message.getBody();
    }

    @Test
    public void testFrameRateCapOverrideOfSystemProperties() throws Exception {
        long videoJobId = 312345;
        String externalId = "baselineFrameRateCapOverrideTest";

        // Testing FRAME_INTERVAL and FRAME_RATE_CAP as system properties.
        // Note: to test all use cases, the caller has to change the defaults in the property file.
        Map<String, String> actionProperties = Collections.emptyMap();
        Map<String, String> jobProperties = Collections.emptyMap();
        Map<String, Map> algorithmProperties = Collections.emptyMap();
        Map<String, String> mediaProperties = Collections.emptyMap();

        // For a job with video media, this test is successful if the sub-job algorithm properties contains COMPUTED_FRAME_INTERVAL property
        // that is equal to the default FRAME_INTERVAL system property (if FRAME_RATE_CAP system property is disabled). If not disabled,
        // then COMPUTED_FRAME_INTERVAL should be derived from FRAME_RATE_CAP system property.
        DetectionProtobuf.DetectionRequest request1 = createTransientJobAndDoDetectionSplit(videoJobId, externalId,
                                                                actionProperties, jobProperties, algorithmProperties, mediaProperties);

        // Need to find COMPUTED_FRAME_INTERVAL property, this test passes when:
        // If system property detection.frame.rate.cap is disabled (i.e. set to -1), then the value of
        // COMPUTED_FRAME_INTERVAL is the same as the value of the system property detection.sampling.interval
        // Otherwise, if system property detection.frame.rate.cap is not disabled, then the value of
        // COMPUTED_FRAME_INTERVAL should be some value >= 1.

        log.info("testing FrameRateCap overrides, propertiesUtil.getFrameRateCap() = {},  propertiesUtil.getSamplingInterval() = {}.",
                 propertiesUtil.getFrameRateCap(), propertiesUtil.getSamplingInterval());

        if ( propertiesUtil.getFrameRateCap() < 0 ) {
            // System property detection.frame.rate.cap is disabled
            Assert.assertTrue(request1.getAlgorithmPropertyList().stream().anyMatch(prop -> {
                return (prop.getPropertyName().equals(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY) &&
                        Double.valueOf(prop.getPropertyValue()) == propertiesUtil.getSamplingInterval());
            }));
        } else {
            // System property detection.frame.rate.cap is not disabled, just check for COMPUTED_FRAME_INTERVAL
            // value>=1, can't validate the actual value because we have no access to FPS at this level.
            Assert.assertTrue(request1.getAlgorithmPropertyList().stream().anyMatch(prop -> {
                return (prop.getPropertyName().equals(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY) &&
                    Double.valueOf(prop.getPropertyValue()) >= 1 );
            }));
        }

    }

    // This general purpose method allows for more easily testing the action property override hierarchy.
    // This method runs the 5-test-cases for various combinations of FRAME_INTERVAL and FRAME_RATE_CAP
    // action property override of system properties.
    public void checkActionPropertyFrameRateCapOverrideTestCases(long localJobId1, long localJobId2, long localJobId3,
                                                                 long localJobId4, long localJobId5,
                                                                 String externalId, Double frameInterval, Map<String, String> jobProperties,
                                                                 Map<String, Map> algorithmProperties,
                                                                 Map<String, String> mediaProperties) throws Exception {
        // Action properties will take on 5 setting combinations.
        Map<String, String> actionProperties = new HashMap<>();

        // Run the FRAME_INTERVAL override test. (test case #1)
        actionProperties.clear();
        actionProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameInterval.toString());

        // This test is successful if the sub-job algorithm properties contain COMPUTED_FRAME_INTERVAL property
        // whose value is the same as the input FRAME_INTERVAL action property value. The sub-job algorithm properties
        // should not contain FRAME_INTERVAL property.
        DetectionProtobuf.DetectionRequest request1 = createTransientJobAndDoDetectionSplit(localJobId1, externalId,
            actionProperties, jobProperties, algorithmProperties, mediaProperties);

        // The sub-job algorithm properties should not contain the FRAME_INTERVAL property.
        Assert.assertTrue(request1.getAlgorithmPropertyList().stream()
            .map( p -> p.getPropertyName() )
            .noneMatch(k -> k.equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)));

        // COMPUTED_FRAME_INTERVAL property value should be the same as the overridden FRAME_INTERVAL property value.
        Assert.assertTrue(request1.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY) &&
                prop.getPropertyValue().equals(frameInterval.toString()) );
        }));

        // Run the FRAME_RATE_CAP override test with both FRAME_RATE_CAP and FRAME_INTERVAL not disabled. (test case #2)
        actionProperties.clear();
        actionProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameInterval.toString());
        actionProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, "10");

        // This test is successful if the sub-job algorithm properties contain COMPUTED_FRAME_INTERVAL property
        // whose value is >= 1, can't validate the actual value because we have no access to FPS at this level.
        // The sub-job algorithm properties should not contain FRAME_INTERVAL property.
        DetectionProtobuf.DetectionRequest request2 = createTransientJobAndDoDetectionSplit(localJobId2, externalId,
            actionProperties, jobProperties, algorithmProperties, mediaProperties);

        // The sub-job algorithm properties should not contain the FRAME_INTERVAL property.
        Assert.assertTrue(request2.getAlgorithmPropertyList().stream()
            .map( p -> p.getPropertyName() )
            .noneMatch(k -> k.equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)));

        // The value of COMPUTED_FRAME_INTERVAL should be >= 1
        Assert.assertTrue(request2.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY) &&
                Double.valueOf(prop.getPropertyValue()) >= 1 );
        }));

        // Run the FRAME_RATE_CAP override test with FRAME_RATE_CAP disabled and FRAME_INTERVAL not disabled. (test case #3)
        actionProperties.clear();
        actionProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameInterval.toString());
        actionProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, "-1");

        // This test is successful if the sub-job algorithm properties contain COMPUTED_FRAME_INTERVAL property
        // whose value is the same as action property FRAME_INTERVAL.
        // The sub-job algorithm properties should not contain FRAME_INTERVAL property.
        DetectionProtobuf.DetectionRequest request3 = createTransientJobAndDoDetectionSplit(localJobId3, externalId,
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

        // Run the FRAME_RATE_CAP override test with FRAME_INTERVAL disabled and FRAME_RATE_CAP_PROPERTY not disabled. (test case #4)
        actionProperties.clear();
        actionProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, "10");
        actionProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, "-1");

        // This test is successful if the sub-job algorithm properties contain COMPUTED_FRAME_INTERVAL property
        // whose value is >= 1.
        // The sub-job algorithm properties should not contain FRAME_INTERVAL property.
        DetectionProtobuf.DetectionRequest request4 = createTransientJobAndDoDetectionSplit(localJobId4, externalId,
            actionProperties, jobProperties, algorithmProperties, mediaProperties);

        // The sub-job algorithm properties should not contain the FRAME_INTERVAL property.
        Assert.assertTrue(request4.getAlgorithmPropertyList().stream()
            .map( p -> p.getPropertyName() )
            .noneMatch(k -> k.equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)));

        // The value of COMPUTED_FRAME_INTERVAL should be >= 1
        Assert.assertTrue(request4.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY) &&
                Double.valueOf(prop.getPropertyValue()) >= 1 );
        }));

        // Run the FRAME_RATE_CAP override test with both FRAME_RATE_CAP and FRAME_INTERVAL disabled.
        // In this case setting should fall back to system property values. (test case #5)
        actionProperties.clear();
        actionProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, "-1");
        actionProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, "-1");

        // This test is successful if the sub-job algorithm properties contain COMPUTED_FRAME_INTERVAL property
        // whose value is the same as system property FRAME_INTERVAL (if system property detection.frame.rate.cap is disabled).
        // Otherwise, COMPUTED_FRAME_INTERVAL property should be derived from detection.frame.rate.cap and we should only
        // check that COMPUTED_FRAME_INTERVAL is >= 1 (since we have no access to FPS for the media, can't do any better check than that).
        // The sub-job algorithm properties should never contain FRAME_INTERVAL property.
        DetectionProtobuf.DetectionRequest request5 = createTransientJobAndDoDetectionSplit(localJobId5, externalId,
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

    }

    @Test
    // action (i.e. pipeline) properties can override system properties
    public void testActionPropertyFrameRateCapOverrideOfProperties() throws Exception {
        long jobId1 = 412341;
        long jobId2 = jobId1 + 1;
        long jobId3 = jobId1 + 2;
        long jobId4 = jobId1 + 3;
        long jobId5 = jobId1 + 4;
        String externalId = "actionFrameRateCapOverrideTest";
        Double frameInterval = 20.0;

        // Testing FRAME_RATE_CAP or FRAME_INTERVAL as an action property override of FRAME_INTERVAL and FRAME_RATE_CAP as system properties.
        // Note: to test all use cases, the caller has to change the defaults in the property file.
        Map<String, String> jobProperties = Collections.emptyMap();
        Map<String, Map> algorithmProperties = Collections.emptyMap();
        Map<String, String> mediaProperties = Collections.emptyMap();

        checkActionPropertyFrameRateCapOverrideTestCases(jobId1, jobId2, jobId3, jobId4, jobId5, externalId, frameInterval,
                                                         jobProperties, algorithmProperties, mediaProperties);

    }



    @Test
    // job properties can override action (i.e. pipeline) or system properties
    public void testJobPropertyFrameRateCapOverrideOfProperties() throws Exception {
        long jobId1 = 512341; // jobProperty test case #1
        long jobId2 = jobId1 + 1; // jobProperty test case #2
        long jobId3 = jobId1 + 2; // jobProperty test case #3
        long jobId4 = jobId1 + 3; // jobProperty test case #4
        long jobId5 = jobId1 + 5; // jobProperty test case #5, both properties disabled.
        long actionJobId1 = jobId5 + 1; // 5 action property override tests using jobProperty test case #5
        long actionJobId2 = jobId5 + 2;
        long actionJobId3 = jobId5 + 3;
        long actionJobId4 = jobId5 + 4;
        long actionJobId5 = jobId5 + 5;

        String externalId = "jobFrameRateCapOverrideTest";
        Double frameInterval = 30.0;

        // Testing FRAME_RATE_CAP or FRAME_INTERVAL as an job property override of system properties.
        // Override testing of action properties will be at the end of this test
        Map<String, String> actionProperties = Collections.emptyMap();
        Map<String, Map> algorithmProperties = Collections.emptyMap();
        Map<String, String> mediaProperties = Collections.emptyMap();

        // Job properties will be varied for all test cases.
        Map<String, String> jobProperties = new HashMap<>();

        // Run the FRAME_INTERVAL job property override of system property test. (jobProperty test case #1)
        jobProperties.clear();
        jobProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameInterval.toString());

        // This test is successful if the sub-job algorithm properties contain COMPUTED_FRAME_INTERVAL property
        // whose value is the same as the input FRAME_INTERVAL job property value. The sub-job algorithm properties
        // should not contain FRAME_INTERVAL property.
        DetectionProtobuf.DetectionRequest request1 = createTransientJobAndDoDetectionSplit(jobId1, externalId,
            actionProperties, jobProperties, algorithmProperties, mediaProperties);

        // The sub-job algorithm properties should not contain the FRAME_INTERVAL property.
        Assert.assertTrue(request1.getAlgorithmPropertyList().stream()
            .map( p -> p.getPropertyName() )
            .noneMatch(k -> k.equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)));

        // COMPUTED_FRAME_INTERVAL property value should be the same as the overridden FRAME_INTERVAL job property value.
        Assert.assertTrue(request1.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY) &&
                prop.getPropertyValue().equals(frameInterval.toString()) );
        }));

        // Run the FRAME_RATE_CAP override of system properties test with FRAME_RATE_CAP and FRAME_INTERVAL not disabled. (jobProperty test case #2)
        jobProperties.clear();
        jobProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameInterval.toString());
        jobProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, "10");

        // This test is successful if the sub-job algorithm properties contain COMPUTED_FRAME_INTERVAL property
        // whose value is >= 1, can't validate the actual value because we have no access to FPS at this level.
        // The sub-job algorithm properties should not contain FRAME_INTERVAL property.
        DetectionProtobuf.DetectionRequest request2 = createTransientJobAndDoDetectionSplit(jobId2, externalId,
            actionProperties, jobProperties, algorithmProperties, mediaProperties);

        // The sub-job algorithm properties should not contain the FRAME_INTERVAL property.
        Assert.assertTrue(request2.getAlgorithmPropertyList().stream()
            .map( p -> p.getPropertyName() )
            .noneMatch(k -> k.equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)));

        // The value of COMPUTED_FRAME_INTERVAL should be >= 1
        Assert.assertTrue(request2.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY) &&
                Double.valueOf(prop.getPropertyValue()) >= 1 );
        }));

        // Run the FRAME_RATE_CAP override test with FRAME_RATE_CAP disabled and FRAME_INTERVAL not disabled. (jobProperty test case #3)
        jobProperties.clear();
        jobProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameInterval.toString());
        jobProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, "-1");

        // This test is successful if the sub-job algorithm properties contain COMPUTED_FRAME_INTERVAL property
        // whose value is the same as action property FRAME_INTERVAL.
        // The sub-job algorithm properties should not contain FRAME_INTERVAL property.
        DetectionProtobuf.DetectionRequest request3 = createTransientJobAndDoDetectionSplit(jobId3, externalId,
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

        // Run the FRAME_RATE_CAP override test with FRAME_RATE_CAP not disabled and FRAME_INTERVAL disabled. (jobProperty test case #4)
        jobProperties.clear();
        jobProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, "-1");
        jobProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, "10");

        // This test is successful if the sub-job algorithm properties contain COMPUTED_FRAME_INTERVAL property
        // whose value is >= 1 (can't determine exact value because FPS is not known).
        // The sub-job algorithm properties should not contain FRAME_INTERVAL property.
        DetectionProtobuf.DetectionRequest request4 = createTransientJobAndDoDetectionSplit(jobId4, externalId,
            actionProperties, jobProperties, algorithmProperties, mediaProperties);

        // The sub-job algorithm properties should not contain the FRAME_INTERVAL property.
        Assert.assertTrue(request4.getAlgorithmPropertyList().stream()
            .map( p -> p.getPropertyName() )
            .noneMatch(k -> k.equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)));

        // COMPUTED_FRAME_INTERVAL property value should be derived from FRAME_RATE_CAP and the videos FPS.
        Assert.assertTrue(request4.getAlgorithmPropertyList().stream().anyMatch(prop -> {
            return (prop.getPropertyName().equals(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY) &&
                Double.valueOf(prop.getPropertyValue()) >= 1 );
        }));

        // Run the FRAME_RATE_CAP override test with both FRAME_RATE_CAP and FRAME_INTERVAL disabled. (jobProperty test case #5)
        // With both these properties disabled, in this case should fall back to action property overrides.
        jobProperties.clear();
        jobProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, "-1");
        jobProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, "-1");

        // Run the FRAME_INTERVAL job property override of action properties tests against jobProperty test case #5.
        checkActionPropertyFrameRateCapOverrideTestCases(actionJobId1, actionJobId2, actionJobId3, actionJobId4, actionJobId5, externalId, frameInterval,
            jobProperties, algorithmProperties, mediaProperties);

    }

















    @Test
    public void testAlgorithmPropertyFrameRateCapOverrideOfSystemProperties() throws Exception {
        long videoJobId = 512345;
//        long imageJobId = 512346;
        String externalId = "algOverrideTest";

        // Testing FRAME_RATE_CAP as an algorithm property override of FRAME_INTERVAL and FRAME_RATE_CAP as system properties.
        Map<String, String> actionProperties = Collections.emptyMap();
        Map<String, String> jobProperties = Collections.emptyMap();
        Map<String, Map> algorithmProperties = new HashMap();
        Map<String, String> faceCvAlgorithmProperties = new HashMap();
        faceCvAlgorithmProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, "10");
        algorithmProperties.put("FACECV", faceCvAlgorithmProperties);
        Map<String, String> mediaProperties = Collections.emptyMap();

        // For a job with video media, this test is successful if the sub-job algorithm properties contain COMPUTED_FRAME_INTERVAL property
        // and does not contain FRAME_INTERVAL property
        DetectionProtobuf.DetectionRequest request1 =  createTransientJobAndDoDetectionSplit(videoJobId, externalId,
            actionProperties, jobProperties, algorithmProperties, mediaProperties);
        List<AlgorithmProperty> algorithmPropertyList1 = request1.getAlgorithmPropertyList();

        Assert.assertTrue(algorithmPropertyList1.stream()
            .map( p -> p.getPropertyName() )
            .anyMatch(k -> k.equals(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY)));

        Assert.assertTrue(algorithmPropertyList1.stream()
            .map( p -> p.getPropertyName() )
            .noneMatch(k -> k.equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)));

//        // For a job with image media, this test is successful if the sub-job algorithm properties contains FRAME_INTERVAL property
//        // and does not contain COMPUTED_FRAME_INTERVAL property
//        DetectionProtobuf.DetectionRequest request2 = testFrameRateCapOverrideOfSystemPropertiesOnImage(imageJobId, externalId,
//            actionProperties, jobProperties, algorithmProperties, mediaProperties);
//        List<AlgorithmProperty> algorithmPropertyList2 = request2.getAlgorithmPropertyList();
//
//        Assert.assertTrue(algorithmPropertyList2.stream()
//            .map( p -> p.getPropertyName() )
//            .anyMatch(k -> k.equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)));
//
//        Assert.assertTrue(algorithmPropertyList2.stream()
//            .map( p -> p.getPropertyName() )
//            .noneMatch(k -> k.equals(MpfConstants.COMPUTED_FRAME_INTERVAL_PROPERTY)));

    }


















}
