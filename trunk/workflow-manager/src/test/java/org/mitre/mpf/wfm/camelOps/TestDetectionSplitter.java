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

package org.mitre.mpf.wfm.camelOps;

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
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.pipeline.Action;
import org.mitre.mpf.wfm.pipeline.Algorithm;
import org.mitre.mpf.wfm.pipeline.Pipeline;
import org.mitre.mpf.wfm.pipeline.Task;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.net.URI;
import java.nio.file.Paths;
import java.util.*;

import static java.util.stream.Collectors.toList;

@ContextConfiguration(locations = {"classpath:applicationContext.xml"})
@RunWith(SpringJUnit4ClassRunner.class)
@RunListener.ThreadSafe
public class TestDetectionSplitter {

    @Autowired
    private IoUtils ioUtils;

    @Autowired
    private PropertiesUtil propertiesUtil;

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
    public void testDetectionSplitter() {
        final long testId = 12345;
        final String testExternalId = "externID";
        final int testPriority = 4;
        final boolean testOutputEnabled = true;

        // Capture a snapshot of the detection system property settings when the job is created.
        SystemPropertiesSnapshot systemPropertiesSnapshot = propertiesUtil.createSystemPropertiesSnapshot();

        URI mediaUri = ioUtils.findFile("/samples/new_face_video.avi");
        TransientMediaImpl testMedia = new TransientMediaImpl(
                nextId(), mediaUri.toString(), UriScheme.get(mediaUri), Paths.get(mediaUri), Collections.emptyMap(),
                null);
        testMedia.setType("video/avi");
        // Video media must have FPS in metadata to support adaptive frame interval processing.
        testMedia.addMetadata("FPS", "30");

        Algorithm algorithm = new Algorithm(
                "detectionAlgo",
                "algo description",
                ActionType.DETECTION,
                new Algorithm.Requires(Collections.emptyList()),
                new Algorithm.Provides(Collections.emptyList(), Collections.emptyList()),
                true,
                true);
        Action action = new Action(
                "detectionAction", "detectionDescription", algorithm.getName(),
                Arrays.asList(new Action.Property(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, "1"),
                              new Action.Property(MpfConstants.MIN_GAP_BETWEEN_TRACKS, "1"),
                              new Action.Property(MpfConstants.MINIMUM_SEGMENT_LENGTH_PROPERTY, "10"),
                              new Action.Property(MpfConstants.TARGET_SEGMENT_LENGTH_PROPERTY, "25")));


        Task task = new Task("taskName", "task description",
                             Collections.singletonList(action.getName()));

        Pipeline pipeline = new Pipeline("testPipe", "testDescr",
                                         Collections.singletonList(task.getName()));
        TransientPipeline transientPipeline = new TransientPipeline(
                pipeline, Collections.singletonList(task), Collections.singletonList(action),
                Collections.singletonList(algorithm));

        TransientJob testJob = new TransientJobImpl(
                testId,
                testExternalId,
                systemPropertiesSnapshot,
                transientPipeline,
                testPriority,
                testOutputEnabled,
                null,
                null,
                Collections.singletonList(testMedia),
                Collections.emptyMap(),
                Collections.emptyMap());

        List<Message> responseList = detectionStageSplitter.performSplit(testJob, task);
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
        TransientJob testJob = createSimpleJobForTest(actionProperties, jobProperties, Collections.emptyMap(),
                                                      "/samples/new_face_video.avi", "video/avi");
        List<Message> responseList = detectionStageSplitter.performSplit(
                testJob, testJob.getTransientPipeline().getTask(0));

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
        TransientJob testJob = createSimpleJobForTest(
                Collections.emptyMap(), jobProperties, mediaProperties,
                "/samples/new_face_video.avi", "video/avi");
        List<Message> responseList = detectionStageSplitter.performSplit(
                testJob, testJob.getTransientPipeline().getTask(0));

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
        TransientJob testJob = createSimpleJobForTest(
                Collections.emptyMap(), jobProperties, mediaProperties,
                "/samples/meds-aa-S001-01-exif-rotation.jpg", "image/jpeg");
        List<Message> responseList = detectionStageSplitter.performSplit(
                testJob, testJob.getTransientPipeline().getTask(0));
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
        TransientJob testJob = createSimpleJobForTest(
                actionProperties, jobProperties, Collections.emptyMap(),
                "/samples/meds-aa-S001-01-exif-rotation.jpg", "image/jpeg");
        List<Message> responseList = detectionStageSplitter.performSplit(
                testJob, testJob.getTransientPipeline().getTask(0));
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
        TransientJob testJob = createSimpleJobForTest(
                actionProperties, Collections.emptyMap(), mediaProperties,
                "/samples/meds-aa-S001-01-exif-rotation.jpg", "image/jpeg");
        List<Message> responseList = detectionStageSplitter.performSplit(
                testJob, testJob.getTransientPipeline().getTask(0));
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

    private TransientJob createSimpleJobForTest(
            Map<String, String> actionProperties,
            Map<String, String> jobProperties,
            Map<String, String>  mediaProperties,
            String mediaUri,
            String mediaType) throws WfmProcessingException {
        long testId = 12345;
        String testExternalId = "externID";

        Algorithm algorithm = new Algorithm(
                "detectionAlgo",
                "algo description",
                ActionType.DETECTION,
                new Algorithm.Requires(Collections.emptyList()),
                new Algorithm.Provides(Collections.emptyList(), Collections.emptyList()),
                true,
                true);

        List<Action.Property> actionPropList = actionProperties.entrySet()
                .stream()
                .map(e -> new Action.Property(e.getKey(), e.getValue()))
                .collect(toList());
        Action action = new Action("detectionAction", "detectionDescription",
                                   algorithm.getName(), actionPropList);

        Task task = new Task("stageName", "stageDescr", Collections.singletonList(action.getName()));

        Pipeline pipeline = new Pipeline("testPipe", "testDescr",
                                         Collections.singletonList(task.getName()));
        TransientPipeline transientPipeline = new TransientPipeline(
                pipeline,
                Collections.singletonList(task),
                Collections.singletonList(action),
                Collections.singletonList(algorithm));

        return createSimpleJobForTest(testId, testExternalId, transientPipeline, jobProperties, mediaProperties,
                                      mediaUri, mediaType);
    }


    private TransientJob createSimpleJobForTest(
            long testJobId,
            String testExternalId,
            TransientPipeline testPipe,
            Map<String, String> jobProperties,
            Map<String, String> mediaProperties,
            String mediaUri,
            String mediaType) {
        final int testStage = 0;
        final int testPriority = 4;
        final boolean testOutputEnabled = true;

        // Capture a snapshot of the detection system property settings when the job is created.
        SystemPropertiesSnapshot systemPropertiesSnapshot = propertiesUtil.createSystemPropertiesSnapshot();

        URI fullMediaUri = ioUtils.findFile(mediaUri);
        TransientMediaImpl testMedia = new TransientMediaImpl(
                nextId(), fullMediaUri.toString(), UriScheme.get(fullMediaUri), Paths.get(fullMediaUri),
                mediaProperties, null);
        testMedia.setLength(300);
        testMedia.setType(mediaType);
        // Video media must have FPS in metadata to support adaptive frame interval processing.
        if ( testMedia.getMediaType() == MediaType.VIDEO ) {
            testMedia.addMetadata("FPS", "30");
        }

        TransientJob testJob = new TransientJobImpl(
                testJobId,
                testExternalId,
                systemPropertiesSnapshot,
                testPipe,
                testPriority,
                testOutputEnabled,
                null,
                null,
                Collections.singletonList(testMedia),
                jobProperties,
                Collections.emptyMap());

        return testJob;
    }

    // The following set of DetectionSplitter tests specifically test FRAME_RATE_CAP vs. FRAME_INTERVAL property
    // overrides at various category levels (system, action, job, algorithm, and media, with media properties being the highest ranking).

    private TransientJob createSimpleJobForFrameRateCapTest(
        Map<String, String> actionProperties, Map<String, String> jobProperties,
        Map<String, Map<String, String>> algorithmProperties, Map<String,String> mediaProperties) {

        URI mediaUri = URI.create("file:///path/to/dummy/media");
        TransientMediaImpl testMedia = new TransientMediaImpl(
                nextId(), mediaUri.toString(), UriScheme.get(mediaUri), Paths.get(mediaUri), mediaProperties,
                null);
        testMedia.setType("mime/dummy");


        Algorithm algorithm = new Algorithm(
                "FACECV", "description", ActionType.DETECTION,
                new Algorithm.Requires(Collections.emptyList()),
                new Algorithm.Provides(Collections.emptyList(), Collections.emptyList()),
                true, true);

        List<Action.Property> actionPropertyList = actionProperties.entrySet()
                .stream()
                .map(e -> new Action.Property(e.getKey(), e.getValue()))
                .collect(toList());

        Action action = new Action("FACECV", "dummyDescriptionFACECV", algorithm.getName(),
                                   actionPropertyList);
        Task task = new Task("Test task", "task description",
                             Collections.singletonList(action.getName()));
        Pipeline pipeline = new Pipeline("OCV FACE DETECTION PIPELINE",
                                         "TestDetectionSplitter Pipeline",
                                         Collections.singletonList(task.getName()));
        TransientPipeline transientPipeline = new TransientPipeline(
                pipeline, Collections.singletonList(task), Collections.singletonList(action),
                Collections.singletonList(algorithm));

        // Capture a snapshot of the detection system property settings when the job is created.
        SystemPropertiesSnapshot systemPropertiesSnapshot = propertiesUtil.createSystemPropertiesSnapshot();

        return new TransientJobImpl(
                nextId(),
                null,
                systemPropertiesSnapshot,
                transientPipeline,
                0,
                false,
                null,
                null,
                Collections.singletonList(testMedia),
                jobProperties,
                algorithmProperties);

    }

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

        Map<String, String> actionProps = new HashMap<>();
        putStringInMapIfNotNull(actionProps, MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameIntervalActionPropVal);
        putStringInMapIfNotNull(actionProps, MpfConstants.FRAME_RATE_CAP_PROPERTY, frameRateCapActionPropVal);

        Map<String, String> jobProps = new HashMap<>();
        putStringInMapIfNotNull(jobProps, MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameIntervalJobPropVal);
        putStringInMapIfNotNull(jobProps, MpfConstants.FRAME_RATE_CAP_PROPERTY, frameRateCapJobPropVal);

        Map<String, String> algProps = new HashMap<>();
        putStringInMapIfNotNull(algProps, MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameIntervalAlgPropVal);
        putStringInMapIfNotNull(algProps, MpfConstants.FRAME_RATE_CAP_PROPERTY, frameRateCapAlgPropVal);

        Map<String, Map<String, String>> metaAlgProps = new HashMap<>();
        metaAlgProps.put("FACECV", algProps);

        Map<String, String> mediaProps = new HashMap<>();
        putStringInMapIfNotNull(mediaProps, MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, frameIntervalMediaPropVal);
        putStringInMapIfNotNull(mediaProps, MpfConstants.FRAME_RATE_CAP_PROPERTY, frameRateCapMediaPropVal);

        TransientJob testJob = createSimpleJobForFrameRateCapTest(actionProps, jobProps, metaAlgProps, mediaProps);

        String calcFrameInterval = AggregateJobPropertiesUtil.calculateFrameInterval(
                testJob.getTransientPipeline().getAction(0, 0),
                testJob,
                testJob.getMedia().stream().findFirst().get(),
                frameIntervalSystemPropVal, frameRateCapSystemPropVal, mediaFPS);

        Assert.assertEquals(expectedFrameInterval.toString(), calcFrameInterval);
    }

    @Test
    public void testFrameRateCapOverrideSystemLevel() throws Exception {
        // Tests 1-4: test 4 combinations of system property FRAME_INTERVAL and FRAME_RATE_CAP.

        // Argument order for checkCalcFrameInterval is: frameInterval, frameRateCap pair for property levels in this order: system, action, job, algorithm, media -
        // with the last two arguments being: mediaFPS and the expected value for adaptive frame interval.

        // Test1: system level test with both FRAME_INTERVAL and FRAME_RATE_CAP specified (FRAME_RATE_CAP override applies).
        checkCalcFrameInterval(7,5, null,null, null,null, null,null, null,null, 30, 6);

        // Test2: system level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP specified.
        checkCalcFrameInterval(-1,5, null,null, null,null, null,null, null,null, 30, 6);

        // Test3: system level test with FRAME_INTERVAL specified and FRAME_RATE_CAP disabled.
        checkCalcFrameInterval(7,-1, null,null, null,null, null,null, null,null, 30, 7);

        // Test4: system level test with both FRAME_INTERVAL and FRAME_RATE_CAP disabled.
        checkCalcFrameInterval(-1,-1, null,null, null,null, null,null, null,null, 30, 1);
    }

    @Test
    public void testFrameRateCapOverrideActionLevel() {

        // Tests 1-9: test 9 combinations of action property FRAME_INTERVAL and FRAME_RATE_CAP with the lower property levels not specified.

        // Test1: action level test with both FRAME_INTERVAL and FRAME_RATE_CAP specified (FRAME_RATE_CAP override applies) with the lower property level not specified.
        checkCalcFrameInterval(-1, -1, 14, 10, null, null, null, null, null, null, 30, 3);

        // Test2: action level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP specified with the lower property level not specified.
        checkCalcFrameInterval(-1, -1, null, 10, null, null, null, null, null, null, 30, 3);

        // Test3: action level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP specified with the lower property level not specified.
        checkCalcFrameInterval(-1, -1, -1, 10, null, null, null, null, null, null, 30, 3);

        // Test4: action level test with FRAME_INTERVAL specified and FRAME_RATE_CAP not specified with the lower property level not specified.
        checkCalcFrameInterval(-1, -1, 14, null, null, null, null, null, null, null, 30, 14);

        // Test5: action level test with neither FRAME_INTERVAL nor FRAME_RATE_CAP specified with the lower property level not specified.
        checkCalcFrameInterval(-1, -1, null, null, null, null, null, null, null, null, 30, 1);

        // Test6: action level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP not specified with the lower property level not specified.
        checkCalcFrameInterval(-1, -1, -1, null, null, null, null, null, null, null, 30, 1);

        // Test7: action level test with FRAME_INTERVAL specified and FRAME_RATE_CAP disabled with the lower property level not specified.
        checkCalcFrameInterval(-1, -1, 14, -1, null, null, null, null, null, null, 30, 14);

        // Test8: action level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP disabled with the lower property level not specified.
        checkCalcFrameInterval(-1, -1, null, -1, null, null, null, null, null, null, 30, 1);

        // Test9: action level test with both FRAME_INTERVAL and FRAME_RATE_CAP disabled with the lower property level not specified.
        checkCalcFrameInterval(-1, -1, -1, -1, null, null, null, null, null, null, 30, 1);

        // Tests 10-18: repeat the last 9 test combinations of action property FRAME_INTERVAL and FRAME_RATE_CAP
        // with the one-level-down FRAME_INTERVAL property specified (we won't be including any tests for both one-level-down properties disabled)
        checkCalcFrameInterval(7, -1, 14, 10, null, null, null, null, null, null, 30, 3);
        checkCalcFrameInterval(7, -1, null, 10, null, null, null, null, null, null, 30, 3);
        checkCalcFrameInterval(7, -1, -1, 10, null, null, null, null, null, null, 30, 3);
        checkCalcFrameInterval(7, -1, 14, null, null, null, null, null, null, null, 30, 14);
        checkCalcFrameInterval(7, -1, null, null, null, null, null, null, null, null, 30, 7);
        checkCalcFrameInterval(7, -1, -1, null, null, null, null, null, null, null, 30, 1);
        checkCalcFrameInterval(7, -1, 14, -1, null, null, null, null, null, null, 30, 14);
        checkCalcFrameInterval(7, -1, null, -1, null, null, null, null, null, null, 30, 7);
        checkCalcFrameInterval(7, -1, -1, -1, null, null, null, null, null, null, 30, 1);

        // Tests 19-28: repeat the first 9 test combinations of action property FRAME_INTERVAL and FRAME_RATE_CAP
        // with the one-level-down FRAME_RATE_CAP property specified (we won't be including any tests for both one-level-down properties disabled)
        checkCalcFrameInterval(-1, 5, 14, 10, null, null, null, null, null, null, 30, 3);
        checkCalcFrameInterval(-1, 5, null, 10, null, null, null, null, null, null, 30, 3);
        checkCalcFrameInterval(-1, 5, -1, 10, null, null, null, null, null, null, 30, 3);
        checkCalcFrameInterval(-1, 5, 14, null, null, null, null, null, null, null, 30, 14);
        checkCalcFrameInterval(-1, 5, null, null, null, null, null, null, null, null, 30, 6);
        checkCalcFrameInterval(-1, 5, -1, null, null, null, null, null, null, null, 30, 6);
        checkCalcFrameInterval(-1, 5, 14, -1, null, null, null, null, null, null, 30, 14);
        checkCalcFrameInterval(-1, 5, null, -1, null, null, null, null, null, null, 30, 1);
        checkCalcFrameInterval(-1, 5, -1, -1, null, null, null, null, null, null, 30, 1);

        // Tests 29-37: repeat the first 9 test combinations of action property FRAME_INTERVAL and FRAME_RATE_CAP
        // with the one-level-down with both FRAME_INTERVAL and FRAME_RATE_CAP properties specified (we won't be including any tests for both one-level-down properties disabled)
        checkCalcFrameInterval(7, 5, 14, 10, null, null, null, null, null, null, 30, 3);
        checkCalcFrameInterval(7, 5, null, 10, null, null, null, null, null, null, 30, 3);
        checkCalcFrameInterval(7, 5, -1, 10, null, null, null, null, null, null, 30, 3);
        checkCalcFrameInterval(7, 5, 14, null, null, null, null, null, null, null, 30, 14);
        checkCalcFrameInterval(7, 5, null, null, null, null, null, null, null, null, 30, 6);
        checkCalcFrameInterval(7, 5, -1, null, null, null, null, null, null, null, 30, 6);
        checkCalcFrameInterval(7, 5, 14, -1, null, null, null, null, null, null, 30, 14);
        checkCalcFrameInterval(7, 5, null, -1, null, null, null, null, null, null, 30, 7);
        checkCalcFrameInterval(7, 5, -1, -1, null, null, null, null, null, null, 30, 1);

    } // end of method testFrameRateCapOverrideActionLevel

     @Test
     public void testFrameRateCapOverrideJobLevel() {

         // Tests 1-9: test 9 combinations of job property FRAME_INTERVAL and FRAME_RATE_CAP with the lower property levels not specified.

         // Test1: job level test with both FRAME_INTERVAL and FRAME_RATE_CAP specified (FRAME_RATE_CAP override applies) with the lower property level not specified.
         checkCalcFrameInterval(-1, -1, null, null, 14, 10, null, null, null, null, 30, 3);

         // Test2: job level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP specified with the lower property level not specified.
         checkCalcFrameInterval(-1, -1, null, null, null, 10, null, null, null, null, 30, 3);

         // Test3: job level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP specified with the lower property level not specified.
         checkCalcFrameInterval(-1, -1, null, null, -1, 10, null, null, null, null, 30, 3);

         // Test4: job level test with FRAME_INTERVAL specified and FRAME_RATE_CAP not specified with the lower property level not specified.
         checkCalcFrameInterval(-1, -1, null, null, 14, null, null, null, null, null, 30, 14);

         // Test5: job level test with neither FRAME_INTERVAL nor FRAME_RATE_CAP specified with the lower property level not specified.
         checkCalcFrameInterval(-1, -1, null, null, null, null, null, null, null, null, 30, 1);

         // Test6: job level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP not specified with the lower property level not specified.
         checkCalcFrameInterval(-1, -1, null, null, -1, null, null, null, null, null, 30, 1);

         // Test7: job level test with FRAME_INTERVAL specified and FRAME_RATE_CAP disabled with the lower property level not specified.
         checkCalcFrameInterval(-1, -1, null, null, 14, -1, null, null, null, null, 30, 14);

         // Test8: job level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP disabled with the lower property level not specified.
         checkCalcFrameInterval(-1, -1, null, null, null, -1, null, null, null, null, 30, 1);

         // Test9: job level test with both FRAME_INTERVAL and FRAME_RATE_CAP disabled with the lower property level not specified.
         checkCalcFrameInterval(-1, -1, null, null, -1, -1, null, null, null, null, 30, 1);

         // Tests 10-18: repeat the last 9 test combinations of job property FRAME_INTERVAL and FRAME_RATE_CAP
         // with the one-level-down FRAME_INTERVAL property specified (we won't be including any tests for one-level-down properties disabled)
         checkCalcFrameInterval(-1, -1, 7,null, 14,10,     null,null, null,null, 30, 3);
         checkCalcFrameInterval(-1, -1, 7,null, null,10,   null,null, null,null, 30, 3);
         checkCalcFrameInterval(-1, -1, 7,null, -1,10,     null,null, null,null, 30, 3);
         checkCalcFrameInterval(-1, -1, 7,null, 14,null,   null,null, null,null, 30, 14);
         checkCalcFrameInterval(-1, -1, 7,null, null,null, null,null, null,null, 30, 7);
         checkCalcFrameInterval(-1, -1, 7,null, -1,null,   null,null, null,null, 30, 1);
         checkCalcFrameInterval(-1, -1, 7,null, 14,-1,     null,null, null,null, 30, 14);
         checkCalcFrameInterval(-1, -1, 7,null, null,-1,   null,null, null,null, 30, 7);
         checkCalcFrameInterval(-1, -1, 7,null, -1,-1,     null,null, null,null, 30, 1);

         // Tests 19-28: repeat the first 9 test combinations of job property FRAME_INTERVAL and FRAME_RATE_CAP
         // with the one-level-down FRAME_RATE_CAP property specified (we won't be including any tests for one-level-down properties disabled)
         checkCalcFrameInterval(-1, -1, null,5, 14, 10,     null, null, null, null, 30, 3);
         checkCalcFrameInterval(-1, -1, null,5, null, 10,   null, null, null, null, 30, 3);
         checkCalcFrameInterval(-1, -1, null,5, -1, 10,     null, null, null, null, 30, 3);
         checkCalcFrameInterval(-1, -1, null,5, 14, null,   null, null, null, null, 30, 14);
         checkCalcFrameInterval(-1, -1, null,5, null, null, null, null, null, null, 30, 6);
         checkCalcFrameInterval(-1, -1, null,5, -1, null,   null, null, null, null, 30, 6);
         checkCalcFrameInterval(-1, -1, null,5, 14, -1,     null, null, null, null, 30, 14);
         checkCalcFrameInterval(-1, -1, null,5, null, -1,   null, null, null, null, 30, 1);
         checkCalcFrameInterval(-1, -1, null,5, -1, -1,     null, null, null, null, 30, 1);

         // Tests 29-37: repeat the first 9 test combinations of job property FRAME_INTERVAL and FRAME_RATE_CAP
         // with the one-level-down with both FRAME_INTERVAL and FRAME_RATE_CAP properties specified (we won't be including any tests for one-level-down properties disabled)
         checkCalcFrameInterval(-1, -1, 7, 5,  14, 10,     null, null, null, null, 30, 3);
         checkCalcFrameInterval(-1, -1, 7, 5,  null, 10,   null, null, null, null, 30, 3);
         checkCalcFrameInterval(-1, -1, 7, 5,  -1, 10,     null, null, null, null, 30, 3);
         checkCalcFrameInterval(-1, -1, 7, 5,  14, null,   null, null, null, null, 30, 14);
         checkCalcFrameInterval(-1, -1, 7, 5,  null, null, null, null, null, null, 30, 6);
         checkCalcFrameInterval(-1, -1, 7, 5,  -1, null,   null, null, null, null, 30, 6);
         checkCalcFrameInterval(-1, -1, 7, 5,  14, -1,     null, null, null, null, 30, 14);
         checkCalcFrameInterval(-1, -1, 7, 5,  null, -1,   null, null, null, null, 30, 7);
         checkCalcFrameInterval(-1, -1, 7, 5,  -1, -1,     null, null, null, null, 30, 1);

     } // end of method testFrameRateCapOverrideJobLevel

     @Test
     public void testFrameRateCapOverrideAlgorithmLevel() {

         // Tests 1-9: test 9 combinations of algorithm property FRAME_INTERVAL and FRAME_RATE_CAP with the lower property levels not specified.

         // Test1: algorithm level test with both FRAME_INTERVAL and FRAME_RATE_CAP specified (FRAME_RATE_CAP override applies) with the lower property level not specified.
         checkCalcFrameInterval(-1, -1, null, null, null, null, 14, 10, null, null, 30, 3);

         // Test2: algorithm level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP specified with the lower property level not specified.
         checkCalcFrameInterval(-1, -1, null, null, null, null, null, 10, null, null, 30, 3);

         // Test3: algorithm level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP specified with the lower property level not specified.
         checkCalcFrameInterval(-1, -1, null, null, null, null, -1, 10, null, null, 30, 3);

         // Test4: algorithm level test with FRAME_INTERVAL specified and FRAME_RATE_CAP not specified with the lower property level not specified.
         checkCalcFrameInterval(-1, -1, null, null, null, null, 14, null, null, null, 30, 14);

         // Test5: algorithm level test with neither FRAME_INTERVAL nor FRAME_RATE_CAP specified with the lower property level not specified.
         checkCalcFrameInterval(-1, -1, null, null, null, null, null, null, null, null, 30, 1);

         // Test6: algorithm level test with FRAME_INTERVAL disabled and FRAME_RATE_CAP not specified with the lower property level not specified.
         checkCalcFrameInterval(-1, -1, null, null, null, null, -1, null, null, null, 30, 1);

         // Test7: algorithm level test with FRAME_INTERVAL specified and FRAME_RATE_CAP disabled with the lower property level not specified.
         checkCalcFrameInterval(-1, -1, null, null, null, null, 14, -1, null, null, 30, 14);

         // Test8: algorithm level test with FRAME_INTERVAL not specified and FRAME_RATE_CAP disabled with the lower property level not specified.
         checkCalcFrameInterval(-1, -1, null, null, null, null, null, -1, null, null, 30, 1);

         // Test9: algorithm level test with both FRAME_INTERVAL and FRAME_RATE_CAP disabled with the lower property level not specified.
         checkCalcFrameInterval(-1, -1, null, null, null, null, -1, -1, null, null, 30, 1);

         // Tests 10-18: repeat the last 9 test combinations of algorithm property FRAME_INTERVAL and FRAME_RATE_CAP
         // with the one-level-down FRAME_INTERVAL property specified (we won't be including any tests for one-level-down properties disabled)
         checkCalcFrameInterval(-1, -1, null, null, 7,null, 14,10,     null,null, 30, 3);
         checkCalcFrameInterval(-1, -1, null, null, 7,null, null,10,   null,null, 30, 3);
         checkCalcFrameInterval(-1, -1, null, null, 7,null, -1,10,     null,null, 30, 3);
         checkCalcFrameInterval(-1, -1, null, null, 7,null, 14,null,   null,null, 30, 14);
         checkCalcFrameInterval(-1, -1, null, null, 7,null, null,null, null,null, 30, 7);
         checkCalcFrameInterval(-1, -1, null, null, 7,null, -1,null,   null,null, 30, 1);
         checkCalcFrameInterval(-1, -1, null, null, 7,null, 14,-1,     null,null, 30, 14);
         checkCalcFrameInterval(-1, -1, null, null, 7,null, null,-1,   null,null, 30, 7);
         checkCalcFrameInterval(-1, -1, null, null, 7,null, -1,-1,     null,null, 30, 1);

         // Tests 19-28: repeat the first 9 test combinations of algorithm property FRAME_INTERVAL and FRAME_RATE_CAP
         // with the one-level-down FRAME_RATE_CAP property specified (we won't be including any tests for one-level-down properties disabled)
         checkCalcFrameInterval(-1, -1, null, null, null,5, 14, 10,     null, null, 30, 3);
         checkCalcFrameInterval(-1, -1, null, null, null,5, null, 10,   null, null, 30, 3);
         checkCalcFrameInterval(-1, -1, null, null, null,5, -1, 10,     null, null, 30, 3);
         checkCalcFrameInterval(-1, -1, null, null, null,5, 14, null,   null, null, 30, 14);
         checkCalcFrameInterval(-1, -1, null, null, null,5, null, null, null, null, 30, 6);
         checkCalcFrameInterval(-1, -1, null, null, null,5, -1, null,   null, null, 30, 6);
         checkCalcFrameInterval(-1, -1, null, null, null,5, 14, -1,     null, null, 30, 14);
         checkCalcFrameInterval(-1, -1, null, null, null,5, null, -1,   null, null, 30, 1);
         checkCalcFrameInterval(-1, -1, null, null, null,5, -1, -1,     null, null, 30, 1);

         // Tests 29-37: repeat the first 9 test combinations of algorithm property FRAME_INTERVAL and FRAME_RATE_CAP
         // with the one-level-down with both FRAME_INTERVAL and FRAME_RATE_CAP properties specified (we won't be including any tests for one-level-down properties disabled)
         checkCalcFrameInterval(-1, -1, null, null, 7, 5,  14, 10,     null, null, 30, 3);
         checkCalcFrameInterval(-1, -1, null, null, 7, 5,  null, 10,   null, null, 30, 3);
         checkCalcFrameInterval(-1, -1, null, null, 7, 5,  -1, 10,     null, null, 30, 3);
         checkCalcFrameInterval(-1, -1, null, null, 7, 5,  14, null,   null, null, 30, 14);
         checkCalcFrameInterval(-1, -1, null, null, 7, 5,  null, null, null, null, 30, 6);
         checkCalcFrameInterval(-1, -1, null, null, 7, 5,  -1, null,   null, null, 30, 6);
         checkCalcFrameInterval(-1, -1, null, null, 7, 5,  14, -1,     null, null, 30, 14);
         checkCalcFrameInterval(-1, -1, null, null, 7, 5,  null, -1,   null, null, 30, 7);
         checkCalcFrameInterval(-1, -1, null, null, 7, 5,  -1, -1,     null, null, 30, 1);

     } // end of method testFrameRateCapOverrideAlgorithmLevel

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

}
