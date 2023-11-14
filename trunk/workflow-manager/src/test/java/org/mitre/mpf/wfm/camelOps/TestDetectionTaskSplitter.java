/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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
import org.mitre.mpf.rest.api.pipelines.*;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.buffers.AlgorithmPropertyProtocolBuffer;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionTaskSplitter;
import org.mitre.mpf.wfm.data.entities.persistent.*;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import java.net.URI;
import java.nio.file.Paths;
import java.util.*;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@ContextConfiguration(locations = {"classpath:applicationContext.xml"})
@WebAppConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
@RunListener.ThreadSafe
@ActiveProfiles("jenkins")
public class TestDetectionTaskSplitter {

    @Autowired
    private IoUtils ioUtils;

    @Autowired
    private PropertiesUtil propertiesUtil;

    @Autowired
    private DetectionTaskSplitter detectionSplitter;

    @Autowired
    private AggregateJobPropertiesUtil aggregateJobPropertiesUtil;

    private static final MutableInt SEQUENCE = new MutableInt();

    public int nextId() {
        synchronized (SEQUENCE) {
            int next = SEQUENCE.getValue();
            SEQUENCE.increment();
            return next;
        }
    }

    @Test
    public void testMediaVideoLengthNotSet() {
        final long testId = 12345;
        final String testExternalId = "externID";
        final int testPriority = 4;

        // Capture a snapshot of the detection system property settings when the job is created.
        SystemPropertiesSnapshot systemPropertiesSnapshot = propertiesUtil.createSystemPropertiesSnapshot();

        URI mediaUri = ioUtils.findFile("/samples/new_face_video.avi");
        MediaImpl testMedia = new MediaImpl(
                nextId(), mediaUri.toString(), UriScheme.get(mediaUri), Paths.get(mediaUri),
                Map.of(), Map.of(), List.of(), List.of(), null);
        testMedia.setType(MediaType.VIDEO);
        testMedia.setMimeType("video/avi");
        // Video media must have FPS in metadata to support adaptive frame interval processing.
        testMedia.addMetadata("FPS", "30");
        // Note that because media length is not set, the split will result in an exception.

        Algorithm algorithm = new Algorithm(
                "detectionAlgo",
                "algo description",
                ActionType.DETECTION,
                "TEST",
                OptionalInt.empty(),
                new Algorithm.Requires(Collections.emptyList()),
                new Algorithm.Provides(Collections.emptyList(), Collections.emptyList()),
                true,
                true);
        Action action = new Action(
                "detectionAction", "detectionDescription", algorithm.getName(),
                Arrays.asList(new ActionProperty(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, "1"),
                              new ActionProperty(MpfConstants.MIN_GAP_BETWEEN_TRACKS, "1"),
                              new ActionProperty(MpfConstants.MINIMUM_SEGMENT_LENGTH_PROPERTY, "10"),
                              new ActionProperty(MpfConstants.TARGET_SEGMENT_LENGTH_PROPERTY, "25")));


        Task task = new Task("taskName", "task description",
                             Collections.singletonList(action.getName()));

        Pipeline pipeline = new Pipeline("testPipe", "testDescr",
                                         Collections.singletonList(task.getName()));
        JobPipelineElements pipelineElements = new JobPipelineElements(
                pipeline, Collections.singletonList(task), Collections.singletonList(action),
                Collections.singletonList(algorithm));

        BatchJob testJob = new BatchJobImpl(
                testId,
                testExternalId,
                systemPropertiesSnapshot,
                pipelineElements,
                testPriority,
                null,
                null,
                Collections.singletonList(testMedia),
                Collections.emptyMap(),
                Collections.emptyMap(),
                false);

        TestUtil.assertThrows(NoSuchElementException.class,
                              () -> detectionSplitter.performSplit(testJob, task));
    }

    @Test
    public void testMediaTypeHeaderSet() {
        assertMediaTypeHeaderSet(MediaType.VIDEO, "video/avi");
        assertMediaTypeHeaderSet(MediaType.IMAGE, "image/jpeg");
        assertMediaTypeHeaderSet(MediaType.AUDIO, "audio/mp3");
        assertMediaTypeHeaderSet(MediaType.UNKNOWN, "application/pdf");
    }


    private void assertMediaTypeHeaderSet(MediaType mediaType, String mimeType) {

        BatchJob testJob = createSimpleJobForTest(Map.of(), Map.of(), Map.of(),
                                                  "/samples/new_face_video.avi", mediaType, mimeType);

        List<Message> responseList = detectionSplitter.performSplit(
                testJob, testJob.getPipelineElements().getTask(0));

        assertFalse(responseList.isEmpty());

        for (Message response : responseList) {
            assertEquals(mediaType.toString(), response.getHeader(MpfHeaders.MEDIA_TYPE));
        }
    }


    @Test
    public void testJobPropertiesOverride() {
        HashMap<String, String> jobProperties = new HashMap<>();
        String propertyName = "TEST";
        String propertyValue = "VALUE";
        jobProperties.put(propertyName, propertyValue);
        jobProperties.put(MpfConstants.MIN_GAP_BETWEEN_TRACKS, "0");
        Map<String, String> actionProperties = new HashMap<>();
        actionProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, "1");
        actionProperties.put(MpfConstants.MIN_GAP_BETWEEN_TRACKS, "1");
        actionProperties.put(MpfConstants.MINIMUM_SEGMENT_LENGTH_PROPERTY, "10");
        actionProperties.put(MpfConstants.VFR_TARGET_SEGMENT_LENGTH_PROPERTY, "25");
        BatchJob testJob = createSimpleJobForTest(actionProperties, jobProperties, Collections.emptyMap(),
                                                      "/samples/new_face_video.avi", MediaType.VIDEO, "video/avi");
        List<Message> responseList = detectionSplitter.performSplit(
                testJob, testJob.getPipelineElements().getTask(0));

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
    public void testMediaSpecificPropertiesOverride() {
        HashMap<String, String> mediaProperties = new HashMap<>();
        String propertyName = "TEST";
        String propertyValue = "VALUE";
        mediaProperties.put(propertyName, propertyValue);
        mediaProperties.put(MpfConstants.MIN_GAP_BETWEEN_TRACKS, "0");
        Map<String, String> jobProperties = new HashMap<>();
        jobProperties.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, "1");
        jobProperties.put(MpfConstants.MIN_GAP_BETWEEN_TRACKS, "1");
        jobProperties.put(MpfConstants.MINIMUM_SEGMENT_LENGTH_PROPERTY, "10");
        jobProperties.put(MpfConstants.VFR_TARGET_SEGMENT_LENGTH_PROPERTY, "25");
        BatchJob testJob = createSimpleJobForTest(
                Collections.emptyMap(), jobProperties, mediaProperties,
                "/samples/new_face_video.avi", MediaType.VIDEO, "video/avi");
        List<Message> responseList = detectionSplitter.performSplit(
                testJob, testJob.getPipelineElements().getTask(0));

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

    @Test
    public void testMediaSpecificPropertiesOverrideActionProperties() {
        testMediaSpecificPropertyOverridesActionProperty(MpfConstants.HORIZONTAL_FLIP_PROPERTY, "TRUE");
        testMediaSpecificPropertyOverridesActionProperty(MpfConstants.ROTATION_PROPERTY, "90");
        testMediaSpecificPropertyOverridesActionProperty(MpfConstants.SEARCH_REGION_TOP_LEFT_X_DETECTION_PROPERTY, "-1");
        testMediaSpecificPropertyOverridesActionProperty(MpfConstants.SEARCH_REGION_TOP_LEFT_Y_DETECTION_PROPERTY, "-1");
        testMediaSpecificPropertyOverridesActionProperty(MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_X_DETECTION_PROPERTY, "-1");
        testMediaSpecificPropertyOverridesActionProperty(MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_Y_DETECTION_PROPERTY, "-1");
        testMediaSpecificPropertyOverridesActionProperty(MpfConstants.AUTO_FLIP_PROPERTY, "TRUE");
        testMediaSpecificPropertyOverridesActionProperty(MpfConstants.AUTO_ROTATE_PROPERTY, "TRUE");
        testMediaSpecificPropertyOverridesActionProperty(MpfConstants.MIN_GAP_BETWEEN_TRACKS, "0");
        testMediaSpecificPropertyOverridesActionProperty(MpfConstants.MINIMUM_SEGMENT_LENGTH_PROPERTY, "100");
    }

    @Test
    public void testMediaSpecificPropertiesOverrideJobProperties() {
        testMediaSpecifcPropertyOverridesJobProperty(MpfConstants.HORIZONTAL_FLIP_PROPERTY, "TRUE");
        testMediaSpecifcPropertyOverridesJobProperty(MpfConstants.ROTATION_PROPERTY, "90");
        testMediaSpecifcPropertyOverridesJobProperty(MpfConstants.SEARCH_REGION_TOP_LEFT_X_DETECTION_PROPERTY, "-1");
        testMediaSpecifcPropertyOverridesJobProperty(MpfConstants.SEARCH_REGION_TOP_LEFT_Y_DETECTION_PROPERTY, "-1");
        testMediaSpecifcPropertyOverridesJobProperty(MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_X_DETECTION_PROPERTY, "-1");
        testMediaSpecifcPropertyOverridesJobProperty(MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_Y_DETECTION_PROPERTY, "-1");
        testMediaSpecifcPropertyOverridesJobProperty(MpfConstants.AUTO_FLIP_PROPERTY, "TRUE");
        testMediaSpecifcPropertyOverridesJobProperty(MpfConstants.AUTO_ROTATE_PROPERTY, "TRUE");
        testMediaSpecifcPropertyOverridesJobProperty(MpfConstants.MIN_GAP_BETWEEN_TRACKS, "0");
        testMediaSpecifcPropertyOverridesJobProperty(MpfConstants.MINIMUM_SEGMENT_LENGTH_PROPERTY, "100");
    }


    @Test
    public void testJobPropertiesOverrideActionProperties() {
        testJobPropertyOverridesActionProperty(MpfConstants.HORIZONTAL_FLIP_PROPERTY, "TRUE");
        testJobPropertyOverridesActionProperty(MpfConstants.ROTATION_PROPERTY, "90");
        testJobPropertyOverridesActionProperty(MpfConstants.SEARCH_REGION_TOP_LEFT_X_DETECTION_PROPERTY, "-1");
        testJobPropertyOverridesActionProperty(MpfConstants.SEARCH_REGION_TOP_LEFT_Y_DETECTION_PROPERTY, "-1");
        testJobPropertyOverridesActionProperty(MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_X_DETECTION_PROPERTY, "-1");
        testJobPropertyOverridesActionProperty(MpfConstants.SEARCH_REGION_BOTTOM_RIGHT_Y_DETECTION_PROPERTY, "-1");
        testJobPropertyOverridesActionProperty(MpfConstants.AUTO_FLIP_PROPERTY, "TRUE");
        testJobPropertyOverridesActionProperty(MpfConstants.AUTO_ROTATE_PROPERTY, "TRUE");
        testJobPropertyOverridesActionProperty(MpfConstants.MIN_GAP_BETWEEN_TRACKS, "0");
        testJobPropertyOverridesActionProperty(MpfConstants.MINIMUM_SEGMENT_LENGTH_PROPERTY, "100");
    }

    private void testMediaSpecifcPropertyOverridesJobProperty(String propertyName, String propertyValue) {
        Map<String, String> mediaProperties = new HashMap<>();
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

        Map<String, String> expectedProperties = new HashMap<>(jobProperties);
        expectedProperties.putAll(mediaProperties);

        BatchJob testJob = createSimpleJobForTest(
                Collections.emptyMap(), jobProperties, mediaProperties,
                "/samples/meds-aa-S001-01-exif-rotation.jpg", MediaType.IMAGE, "image/jpeg");
        assertProtobufHasExpectedProperties(propertyName, propertyValue, expectedProperties, testJob);
    }

    private void testJobPropertyOverridesActionProperty(String propertyName, String propertyValue) {
        Map<String, String> jobProperties = new HashMap<>();
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

        Map<String, String> expectedProperties = new HashMap<>(actionProperties);
        expectedProperties.putAll(jobProperties);

        BatchJob testJob = createSimpleJobForTest(
                actionProperties, jobProperties, Collections.emptyMap(),
                "/samples/meds-aa-S001-01-exif-rotation.jpg", MediaType.IMAGE, "image/jpeg");

        assertProtobufHasExpectedProperties(propertyName, propertyValue, expectedProperties, testJob);
    }

    private void testMediaSpecificPropertyOverridesActionProperty(String propertyName, String propertyValue) {
        Map<String, String> mediaProperties = new HashMap<>();
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

        Map<String, String> expectedProperties = new HashMap<>(actionProperties);
        expectedProperties.putAll(mediaProperties);

        BatchJob testJob = createSimpleJobForTest(
                actionProperties, Collections.emptyMap(), mediaProperties,
                "/samples/meds-aa-S001-01-exif-rotation.jpg", MediaType.IMAGE, "image/jpeg");

        assertProtobufHasExpectedProperties(propertyName, propertyValue, expectedProperties, testJob);
    }

    private void assertProtobufHasExpectedProperties(
            String propertyName, String propertyValue, Map<String, String> expectedProperties, BatchJob testJob) {

        List<Message> responseList = detectionSplitter.performSplit(
                testJob, testJob.getPipelineElements().getTask(0));

        Assert.assertEquals(1, responseList.size());
        Message message = responseList.get(0);
        Assert.assertTrue(message.getBody() instanceof DetectionProtobuf.DetectionRequest);

        DetectionProtobuf.DetectionRequest request = (DetectionProtobuf.DetectionRequest) message.getBody();

        AlgorithmPropertyProtocolBuffer.AlgorithmProperty matchingProtoProp = request.getAlgorithmPropertyList()
                .stream()
                .filter(p -> p.getPropertyName().equals(propertyName))
                .findAny()
                .orElse(null);
        Assert.assertNotNull("Expected there to be a protobuf property named " + propertyName,
                             matchingProtoProp);
        Assert.assertEquals(String.format("Expected the protobuf property %s to be %s", propertyName, propertyValue),
                            propertyValue, matchingProtoProp.getPropertyValue());

        for (Map.Entry<String, String> actionPropEntry : expectedProperties.entrySet()) {
            AlgorithmPropertyProtocolBuffer.AlgorithmProperty protoProp = request.getAlgorithmPropertyList()
                    .stream()
                    .filter(p -> p.getPropertyName().equals(actionPropEntry.getKey()))
                    .findAny()
                    .orElse(null);

            Assert.assertNotNull(
                    "Expected there to be a protobuf property named " + actionPropEntry.getKey(),
                    protoProp);

            Assert.assertEquals(String.format("Expected the protobuf property %s to be %s",
                                              actionPropEntry.getKey(), actionPropEntry.getValue()),
                                actionPropEntry.getValue(), protoProp.getPropertyValue());
        }
    }


    private BatchJob createSimpleJobForTest(
            Map<String, String> actionProperties,
            Map<String, String> jobProperties,
            Map<String, String>  mediaProperties,
            String mediaUri,
            MediaType mediaType,
            String mimeType) throws WfmProcessingException {
        long testId = 12345;
        String testExternalId = "externID";

        Algorithm algorithm = new Algorithm(
                "detectionAlgo",
                "algo description",
                ActionType.DETECTION,
                "TEST",
                OptionalInt.empty(),
                new Algorithm.Requires(Collections.emptyList()),
                new Algorithm.Provides(Collections.emptyList(), Collections.emptyList()),
                true,
                true);

        List<ActionProperty> actionPropList = actionProperties.entrySet()
                .stream()
                .map(e -> new ActionProperty(e.getKey(), e.getValue()))
                .collect(toList());
        Action action = new Action("detectionAction", "detectionDescription",
                                   algorithm.getName(), actionPropList);

        Task task = new Task("taskName", "taskDescr", Collections.singletonList(action.getName()));

        Pipeline pipeline = new Pipeline("testPipe", "testDescr",
                                         Collections.singletonList(task.getName()));
        JobPipelineElements pipelineElements = new JobPipelineElements(
                pipeline,
                Collections.singletonList(task),
                Collections.singletonList(action),
                Collections.singletonList(algorithm));

        return createSimpleJobForTest(testId, testExternalId, pipelineElements, jobProperties, mediaProperties,
                                      mediaUri, mediaType, mimeType);
    }


    private BatchJob createSimpleJobForTest(
            long testJobId,
            String testExternalId,
            JobPipelineElements testPipe,
            Map<String, String> jobProperties,
            Map<String, String> mediaProperties,
            String mediaUri,
            MediaType mediaType,
            String mimeType) {
        final int testPriority = 4;

        // Capture a snapshot of the detection system property settings when the job is created.
        SystemPropertiesSnapshot systemPropertiesSnapshot = propertiesUtil.createSystemPropertiesSnapshot();

        URI fullMediaUri = ioUtils.findFile(mediaUri);
        MediaImpl testMedia = new MediaImpl(
                nextId(), fullMediaUri.toString(), UriScheme.get(fullMediaUri), Paths.get(fullMediaUri),
                mediaProperties, Map.of(), List.of(), List.of(), null);
        testMedia.setLength(300);
        testMedia.setType(mediaType);
        testMedia.setMimeType(mimeType);
        // Video media must have FPS in metadata to support adaptive frame interval processing.
        if (testMedia.matchesType(MediaType.VIDEO)) {
            testMedia.addMetadata("FPS", "30");
        }

        var testJob = new BatchJobImpl(
                testJobId,
                testExternalId,
                systemPropertiesSnapshot,
                testPipe,
                testPriority,
                null,
                null,
                Collections.singletonList(testMedia),
                jobProperties,
                Collections.emptyMap(),
                false);
        testJob.setCurrentTaskIndex(0);
        return testJob;
    }

    // The following set of DetectionSplitter tests specifically test FRAME_RATE_CAP vs. FRAME_INTERVAL property
    // overrides at various category levels (system, action, job, algorithm, and media, with media properties being the highest ranking).

    private BatchJob createSimpleJobForFrameRateCapTest(
            Map<String, String> systemProperties, Map<String, String> actionProperties,
            Map<String, String> jobProperties, Map<String, Map<String, String>> algorithmProperties,
            Map<String,String> mediaProperties) {

        URI mediaUri = URI.create("file:///path/to/dummy/media");
        MediaImpl testMedia = new MediaImpl(
                nextId(), mediaUri.toString(), UriScheme.get(mediaUri), Paths.get(mediaUri),
                mediaProperties, Map.of(), List.of(), List.of(), null);
        testMedia.setType(MediaType.VIDEO);
        testMedia.setMimeType("video/dummy");

        Algorithm algorithm = new Algorithm(
                "FACECV", "description", ActionType.DETECTION, "FACE", OptionalInt.empty(),
                new Algorithm.Requires(Collections.emptyList()),
                new Algorithm.Provides(Collections.emptyList(), Collections.emptyList()),
                true, true);

        List<ActionProperty> actionPropertyList = actionProperties.entrySet()
                .stream()
                .map(e -> new ActionProperty(e.getKey(), e.getValue()))
                .collect(toList());

        Action action = new Action("FACECV", "dummyDescriptionFACECV", algorithm.getName(),
                                   actionPropertyList);
        Task task = new Task("Test task", "task description",
                             Collections.singletonList(action.getName()));
        Pipeline pipeline = new Pipeline("OCV FACE DETECTION PIPELINE",
                                         "TestDetectionSplitter Pipeline",
                                         Collections.singletonList(task.getName()));
        JobPipelineElements pipelineElements = new JobPipelineElements(
                pipeline, Collections.singletonList(task), Collections.singletonList(action),
                Collections.singletonList(algorithm));

        // Capture a snapshot of the detection system property settings when the job is created.
        Map<String, String> allSystemProperties =
                new HashMap<>(propertiesUtil.createSystemPropertiesSnapshot().getProperties());
        allSystemProperties.putAll(systemProperties);
        SystemPropertiesSnapshot systemPropertiesSnapshot = new SystemPropertiesSnapshot(allSystemProperties);

        return new BatchJobImpl(
                nextId(),
                null,
                systemPropertiesSnapshot,
                pipelineElements,
                0,
                null,
                null,
                Collections.singletonList(testMedia),
                jobProperties,
                algorithmProperties,
                false);

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

        Map<String, String> systemProps = new HashMap<>();
        putStringInMapIfNotNull(systemProps, "detection.sampling.interval", frameIntervalSystemPropVal);
        putStringInMapIfNotNull(systemProps, "detection.frame.rate.cap", frameRateCapSystemPropVal);

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

        BatchJob testJob = createSimpleJobForFrameRateCapTest(systemProps, actionProps, jobProps, metaAlgProps, mediaProps);

        String calcFrameInterval = aggregateJobPropertiesUtil.calculateFrameInterval(
                testJob.getPipelineElements().getAction(0, 0),
                testJob,
                testJob.getMedia().stream().findFirst().get(),
                frameIntervalSystemPropVal, frameRateCapSystemPropVal, mediaFPS);

        Assert.assertEquals(expectedFrameInterval.toString(), calcFrameInterval);
    }

    @Test
    public void testFrameRateCapOverrideSystemLevel() {
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

    @Test
    public void testSourceMediaOnlyAndDerivativeMediaOnly() {
        var parentMedia = new MediaImpl(700, "file:///parent", UriScheme.FILE, Paths.get("/local/path/parent"),
                Map.of(), Map.of(), List.of(), List.of(), null);
        parentMedia.setType(MediaType.UNKNOWN);
        parentMedia.setMimeType("application/pdf");

        var algo1 = new Algorithm("EXTRACT_ALGO", null, ActionType.DETECTION, "MEDIA",
                OptionalInt.empty(),
                new Algorithm.Requires(Collections.emptyList()),
                new Algorithm.Provides(Collections.emptyList(), Collections.emptyList()), true, false);
        var algo2 = new Algorithm("PARENT_ALGO", null, ActionType.DETECTION, "PARENT",
                OptionalInt.empty(),
                new Algorithm.Requires(Collections.emptyList()),
                new Algorithm.Provides(Collections.emptyList(), Collections.emptyList()), true, false);
        var algo3 = new Algorithm("CHILD_ALGO", null, ActionType.DETECTION, "CHILD",
                OptionalInt.empty(),
                new Algorithm.Requires(Collections.emptyList()),
                new Algorithm.Provides(Collections.emptyList(), Collections.emptyList()), true, false);
        var algo4 = new Algorithm("SHARED_ALGO", null, ActionType.DETECTION, "SHARED",
                OptionalInt.empty(),
                new Algorithm.Requires(Collections.emptyList()),
                new Algorithm.Provides(Collections.emptyList(), Collections.emptyList()), true, false);

        var action1 = new Action("EXTRACT_ACTION", null, algo1.getName(), List.of());
        var action2 = new Action("PARENT_ACTION", null, algo2.getName(),
                List.of(new ActionProperty("SOURCE_MEDIA_ONLY", "TRUE")));
        var action3 = new Action("CHILD_ACTION", null, algo3.getName(),
                List.of(new ActionProperty("DERIVATIVE_MEDIA_ONLY", "TRUE")));
        var action4 = new Action("SHARED_ACTION", null, algo4.getName(), List.of());

        var task1 = new Task("TASK1", null, List.of(action1.getName()));
        var task2 = new Task("TASK2", null, List.of(action2.getName()));
        var task3 = new Task("TASK3", null, List.of(action3.getName()));
        var task4 = new Task("TASK4", null, List.of(action4.getName()));

        var pipeline = new Pipeline("PIPELINE", null,
                List.of(task1.getName(), task2.getName(), task3.getName(), task4.getName()));
        var pipelineElements = new JobPipelineElements(
                pipeline,
                List.of(task1, task2, task3, task4),
                List.of(action1, action2, action3, action4),
                List.of(algo1, algo2, algo3, algo4));

        BatchJobImpl job = new BatchJobImpl(
                123, null, null, pipelineElements, 4,
                null, null, List.of(parentMedia),
                Map.of(), Map.of(), false);

        job.setCurrentTaskIndex(0);
        List<Message> responseList = detectionSplitter.performSplit(job, task1);
        Assert.assertEquals(1, responseList.size()); // parent only

        // Children will be added after the extraction task in a real job.
        var childMedia1 = new MediaImpl(701, 700, 0, "file:///child1", UriScheme.FILE, Paths.get("/local/path/child1"),
                Map.of(), Map.of(), null, Map.of(MpfConstants.IS_DERIVATIVE_MEDIA, "TRUE"),
                List.of(), List.of(), null);
        childMedia1.setType(MediaType.IMAGE);
        childMedia1.setMimeType("image/png");

        var childMedia2 = new MediaImpl(702, 700, 0, "file:///child2", UriScheme.FILE, Paths.get("/local/path/child2"),
                Map.of(), Map.of(), null, Map.of(MpfConstants.IS_DERIVATIVE_MEDIA, "TRUE"),
                List.of(), List.of(), null);
        childMedia2.setType(MediaType.IMAGE);
        childMedia2.setMimeType("image/jpeg");

        job.addDerivativeMedia(childMedia1);
        job.addDerivativeMedia(childMedia2);

        responseList = detectionSplitter.performSplit(job, task2);
        Assert.assertEquals(1, responseList.size()); // parent only

        responseList = detectionSplitter.performSplit(job, task3);
        Assert.assertEquals(2, responseList.size()); // children only

        responseList = detectionSplitter.performSplit(job, task4);
        Assert.assertEquals(3, responseList.size()); // parent and children

        // nothing run yet
        job.setCurrentTaskIndex(0);
        Assert.assertEquals(-1, detectionSplitter.getLastProcessedTaskIndex(job, parentMedia));
        Assert.assertEquals(-1, detectionSplitter.getLastProcessedTaskIndex(job, childMedia1));
        Assert.assertEquals(-1, detectionSplitter.getLastProcessedTaskIndex(job, childMedia2));

        // after parent only extraction
        job.setCurrentTaskIndex(1);
        Assert.assertEquals(0, detectionSplitter.getLastProcessedTaskIndex(job, parentMedia));
        Assert.assertEquals(-1, detectionSplitter.getLastProcessedTaskIndex(job, childMedia1));
        Assert.assertEquals(-1, detectionSplitter.getLastProcessedTaskIndex(job, childMedia2));

        // after parent only detection
        job.setCurrentTaskIndex(2);
        Assert.assertEquals(1, detectionSplitter.getLastProcessedTaskIndex(job, parentMedia));
        Assert.assertEquals(-1, detectionSplitter.getLastProcessedTaskIndex(job, childMedia1));
        Assert.assertEquals(-1, detectionSplitter.getLastProcessedTaskIndex(job, childMedia2));

        // after child only detection
        job.setCurrentTaskIndex(3);
        Assert.assertEquals(1, detectionSplitter.getLastProcessedTaskIndex(job, parentMedia));
        Assert.assertEquals(2, detectionSplitter.getLastProcessedTaskIndex(job, childMedia1));
        Assert.assertEquals(2, detectionSplitter.getLastProcessedTaskIndex(job, childMedia2));

        // after parent and child detection
        job.setCurrentTaskIndex(4);
        Assert.assertEquals(3, detectionSplitter.getLastProcessedTaskIndex(job, parentMedia));
        Assert.assertEquals(3, detectionSplitter.getLastProcessedTaskIndex(job, childMedia1));
        Assert.assertEquals(3, detectionSplitter.getLastProcessedTaskIndex(job, childMedia2));
    }
}
