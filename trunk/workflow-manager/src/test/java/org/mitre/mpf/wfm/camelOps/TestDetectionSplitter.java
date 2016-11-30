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

package org.mitre.mpf.wfm.camelOps;

import com.google.common.collect.Lists;
import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.commons.lang3.mutable.MutableInt;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runner.notification.RunListener;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.buffers.AlgorithmPropertyProtocolBuffer;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.camel.StageSplitter;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionSplitter;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        final int testStage = 1;
        final int testPriority = 4;
        final boolean testOutputEnabled = true;
        TransientJob testJob = new TransientJob(testId, testExternalId, testPipe, testStage, testPriority, testOutputEnabled, false);
        TransientMedia testMedia = new TransientMedia(next(), ioUtils.findFile("/samples/new_face_video.avi").toString());
        testMedia.setType("VIDEO");
        List<TransientMedia> listMedia = Lists.newArrayList(testMedia);
        testJob.setMedia(listMedia);
        TransientStage testTransientStage = new TransientStage("stageName", "stageDescr", ActionType.DETECTION);

        Map<String, String> mergeProp = new HashMap<>();
        mergeProp.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, "1");
        mergeProp.put(MpfConstants.MERGE_TRACKS_PROPERTY, "TRUE");
        mergeProp.put(MpfConstants.MINIMUM_SEGMENT_LENGTH_PROPERTY, "10");
        mergeProp.put(MpfConstants.TARGET_SEGMENT_LENGTH_PROPERTY, "25");
        TransientAction detectionAction = new TransientAction("detectionAction", "detectionDescription", "detectionAlgo");
        detectionAction.setProperties(mergeProp);
        testTransientStage.getActions().add(detectionAction);
        List<Message> responseList = detectionStageSplitter.performSplit(testJob, testTransientStage);
        Assert.assertTrue(responseList.size() == 0);
    }

    @Test
    public void testMediaSpecificPropertiesOverride() throws Exception {
        HashMap<String,String> mediaProperties = new HashMap<>();
        String propertyName = "TEST";
        String propertyValue = "VALUE";
        mediaProperties.put(propertyName, propertyValue);
        mediaProperties.put(MpfConstants.MERGE_TRACKS_PROPERTY, "FALSE");
        Map<String, String> jobProps = new HashMap<>();
        jobProps.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, "1");
        jobProps.put(MpfConstants.MERGE_TRACKS_PROPERTY, "TRUE");
        jobProps.put(MpfConstants.MINIMUM_SEGMENT_LENGTH_PROPERTY, "10");
        jobProps.put(MpfConstants.TARGET_SEGMENT_LENGTH_PROPERTY, "25");
        TransientJob testJob = createSimpleJobForTest(jobProps, "/samples/new_face_video.avi","VIDEO",mediaProperties);
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
            else if (MpfConstants.MERGE_TRACKS_PROPERTY.equals(prop.getPropertyName())) {
                Assert.assertEquals("FALSE",prop.getPropertyValue());
            }
        }
        Assert.assertTrue(propertyExists);
    }

    /**
     * Tests to be sure that a media-specific property for rotation, flip, or any ROI property disables
     * auto-rotate and auto-flip, and others leave them alone.
     *
     * @throws Exception
     */
    @Test
    public void testMediaSpecificPropertiesOverrideWithExif() throws Exception {
        testExifWithSpecificProperty(MpfConstants.HORIZONTAL_FLIP_PROPERTY, "TRUE", true);
        testExifWithSpecificProperty(MpfConstants.ROTATION_PROPERTY, "90", true);
        testExifWithSpecificProperty(MpfConstants.SEARCH_REGION_TOP_LEFT_X_DETECTION_PROPERTY, "-1", true);
        testExifWithSpecificProperty(MpfConstants.SEARCH_REGION_TOP_LEFT_Y_DETECTION_PROPERTY, "-1", true);
        testExifWithSpecificProperty(MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_X_DETECTION_PROPERTY, "-1", true);
        testExifWithSpecificProperty(MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_Y_DETECTION_PROPERTY, "-1",true);
        testExifWithSpecificProperty(MpfConstants.AUTO_FLIP_PROPERTY, "TRUE",true);
        testExifWithSpecificProperty(MpfConstants.AUTO_ROTATE_PROPERTY, "TRUE",true);
        testExifWithSpecificProperty(MpfConstants.MERGE_TRACKS_PROPERTY,"FALSE", false);
        testExifWithSpecificProperty(MpfConstants.MINIMUM_SEGMENT_LENGTH_PROPERTY,"100",false);
    }

    private void testExifWithSpecificProperty(String propertyName, String propertyValue, boolean shouldOverride) throws Exception {
        HashMap<String,String> mediaProperties = new HashMap<>();
        mediaProperties.put(propertyName,propertyValue);
        Map<String, String> jobProps = new HashMap<>();
        jobProps.put(MpfConstants.AUTO_ROTATE_PROPERTY, "TRUE");
        jobProps.put(MpfConstants.AUTO_FLIP_PROPERTY, "TRUE");
        jobProps.put(MpfConstants.ROTATION_PROPERTY,"270");
        jobProps.put(MpfConstants.HORIZONTAL_FLIP_PROPERTY,"TRUE");
        jobProps.put(MpfConstants.SEARCH_REGION_TOP_LEFT_X_DETECTION_PROPERTY,"20");
        jobProps.put(MpfConstants.SEARCH_REGION_TOP_LEFT_Y_DETECTION_PROPERTY,"20");
        jobProps.put(MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_X_DETECTION_PROPERTY,"20");
        jobProps.put(MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_Y_DETECTION_PROPERTY,"20");
        TransientJob testJob = createSimpleJobForTest(jobProps, "/samples/meds-aa-S001-01-exif-rotation.jpg","IMAGE",mediaProperties);
        List<Message> responseList = detectionStageSplitter.performSplit(testJob, testJob.getPipeline().getStages().get(0));
        Assert.assertEquals(1, responseList.size());
        Message message = responseList.get(0);
        Assert.assertTrue(message.getBody() instanceof DetectionProtobuf.DetectionRequest);

        DetectionProtobuf.DetectionRequest request = (DetectionProtobuf.DetectionRequest) message.getBody();
        boolean rotatePropertyExists = false;
        boolean flipPropertyExists = false;
        String expectedAutoValue = shouldOverride ? "FALSE" : "TRUE";
        for (AlgorithmPropertyProtocolBuffer.AlgorithmProperty prop : request.getAlgorithmPropertyList()) {
            if (!propertyName.equals(prop.getPropertyName())) {
                if (MpfConstants.AUTO_ROTATE_PROPERTY.equals(prop.getPropertyName())) {
                    Assert.assertEquals(expectedAutoValue, prop.getPropertyValue());
                } else if (MpfConstants.AUTO_FLIP_PROPERTY.equals(prop.getPropertyName())) {
                    Assert.assertEquals(expectedAutoValue, prop.getPropertyValue());
                } else if (MpfConstants.ROTATION_PROPERTY.equals(prop.getPropertyName())) {
                    Assert.assertEquals(shouldOverride ? "0" : "270", prop.getPropertyValue());
                } else if (MpfConstants.HORIZONTAL_FLIP_PROPERTY.equals(prop.getPropertyName())) {
                    Assert.assertEquals(shouldOverride ? "FALSE" : "TRUE", prop.getPropertyValue());
                } else if (MpfConstants.SEARCH_REGION_ENABLE_DETECTION_PROPERTY.equals(prop.getPropertyName())) {
                    Assert.assertEquals(shouldOverride ? "FALSE" : "TRUE", prop.getPropertyValue());
                } else if (MpfConstants.SEARCH_REGION_TOP_LEFT_X_DETECTION_PROPERTY.equals(prop.getPropertyName())) {
                    Assert.assertEquals(shouldOverride ? "-1" : "20", prop.getPropertyValue());
                } else if (MpfConstants.SEARCH_REGION_TOP_LEFT_Y_DETECTION_PROPERTY.equals(prop.getPropertyName())) {
                    Assert.assertEquals(shouldOverride ? "-1" : "20", prop.getPropertyValue());
                } else if (MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_X_DETECTION_PROPERTY.equals(prop.getPropertyName())) {
                    Assert.assertEquals(shouldOverride ? "-1" : "20", prop.getPropertyValue());
                } else if (MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_Y_DETECTION_PROPERTY.equals(prop.getPropertyName())) {
                    Assert.assertEquals(shouldOverride ? "-1" : "20", prop.getPropertyValue());
                }
            }
        }
    }

    private TransientJob createSimpleJobForTest(Map<String,String> jobProperties, String mediaUri, String mediaType, Map<String,String> mediaSpecificProperties) throws WfmProcessingException {
        final long testId = 12345;
        final String testExternalId = "externID";
        final TransientPipeline testPipe = new TransientPipeline("testPipe", "testDescr");
        final int testStage = 0;
        final int testPriority = 4;
        final boolean testOutputEnabled = true;
        TransientJob testJob = new TransientJob(testId, testExternalId, testPipe, testStage, testPriority, testOutputEnabled, false);
        TransientMedia testMedia = new TransientMedia(next(), ioUtils.findFile(mediaUri).toString());
        testMedia.setLength(300);
        testMedia.setType(mediaType);
        testMedia.getMediaSpecificProperties().putAll(mediaSpecificProperties);

        List<TransientMedia> listMedia = Lists.newArrayList(testMedia);
        testJob.setMedia(listMedia);
        TransientStage testTransientStage = new TransientStage("stageName", "stageDescr", ActionType.DETECTION);
        testPipe.getStages().add(testTransientStage);

        TransientAction detectionAction = new TransientAction("detectionAction", "detectionDescription", "detectionAlgo");
        detectionAction.setProperties(jobProperties);
        testTransientStage.getActions().add(detectionAction);
        return testJob;
    }
}
