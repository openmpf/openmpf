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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.ActionType;
import org.mitre.mpf.rest.api.pipelines.Algorithm;
import org.mitre.mpf.rest.api.pipelines.Pipeline;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionResponseProcessor;
import org.mitre.mpf.wfm.camel.operations.mediainspection.MediaInspectionHelper;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJobImpl;
import org.mitre.mpf.wfm.data.entities.persistent.DetectionProcessingError;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.mitre.mpf.wfm.data.entities.persistent.MediaImpl;
import org.mitre.mpf.wfm.data.entities.persistent.SystemPropertiesSnapshot;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.service.TaskMergingManager;
import org.mitre.mpf.wfm.service.pipeline.PipelineService;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.FrameTimeInfo;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;


public class TestDetectionResponseProcessor extends MockitoTest.Lenient {

    @Mock
    private PipelineService mockPipelineService;

    @Mock
    private InProgressBatchJobsService mockInProgressJobs;

    @Mock
    private AggregateJobPropertiesUtil mockAggregateJobPropertiesUtil;

    @Mock
    private MediaInspectionHelper mockMediaInspectionHelper;

    @Mock
    private TaskMergingManager mockTaskMergingManager;

    private DetectionResponseProcessor detectionResponseProcessor;

    private final IoUtils ioUtils = new IoUtils();

    private static final long JOB_ID = 111;
    private static final long MEDIA_ID = 222;

    private static final float FPS = 29.97f;
    private static final int DURATION = 300_000_000;

    private static final int START_FRAME = 10;
    private static final int STOP_FRAME = 30;

    private static final int START_TIME = 333;
    private static final int STOP_TIME  = 1001;

    private static final String DETECTION_RESPONSE_ALG_NAME = "TEST_DETECTION_RESPONSE_ALG";
    private static final String DETECTION_RESPONSE_ACTION_NAME = "TEST_DETECTION_RESPONSE_ACTION";
    private static final String DETECTION_RESPONSE_PIPELINE_NAME = "TEST_DETECTION_RESPONSE_PIPELINE";
    private static final String DETECTION_RESPONSE_TASK_NAME = "TEST_DETECTION_RESPONSE_TASK";

    @Before
    public void init() {
        detectionResponseProcessor = new DetectionResponseProcessor(
                mockAggregateJobPropertiesUtil,
                mockInProgressJobs,
                mockMediaInspectionHelper,
                mockTaskMergingManager);

        Algorithm algorithm = new Algorithm(
                DETECTION_RESPONSE_ALG_NAME, "algorithm description", ActionType.DETECTION,
                OptionalInt.empty(),
                new Algorithm.Requires(Collections.emptyList()),
                new Algorithm.Provides(Collections.emptyList(), Collections.emptyList()),
                true, true);

        when(mockPipelineService.getAlgorithm(algorithm.getName()))
                .thenReturn(algorithm);

        Action action = new Action(DETECTION_RESPONSE_ACTION_NAME, "action description", algorithm.getName(),
                                   Collections.emptyList());
        when(mockPipelineService.getAction(action.getName()))
                .thenReturn(action);

        Task task = new Task(DETECTION_RESPONSE_TASK_NAME, "task description", Collections.singleton(action.getName()));
        Pipeline pipeline = new Pipeline(DETECTION_RESPONSE_PIPELINE_NAME, "pipeline description",
                                         Collections.singleton(task.getName()));
        JobPipelineElements pipelineElements = new JobPipelineElements(
                pipeline, Collections.singleton(task), Collections.singleton(action),
                Collections.singleton(algorithm));

        Map<String, String> propertiesMap = new HashMap<>();
        propertiesMap.put("detection.confidence.threshold", "-1");
        SystemPropertiesSnapshot systemPropertiesSnapshot = new SystemPropertiesSnapshot(propertiesMap);
        URI mediaUri = ioUtils.findFile("/samples/video_01.mp4");

        MediaImpl media = new MediaImpl(
                MEDIA_ID, mediaUri.toString(), UriScheme.get(mediaUri), Paths.get(mediaUri),
                Map.of(), Map.of(), List.of(), List.of(), null);
        media.addMetadata("FPS", String.valueOf(FPS));
        media.addMetadata("DURATION", String.valueOf(DURATION));
        media.setFrameTimeInfo(FrameTimeInfo.forConstantFrameRate(
                FPS, OptionalInt.of(0), (int) (FPS * DURATION)));

        BatchJobImpl job = new BatchJobImpl(
            JOB_ID,
            "external id",
            systemPropertiesSnapshot,
            pipelineElements,
            1,
            null,
            null,
            List.of(media),
            Map.of(),
            Map.of(),
            false);
        job.setCurrentTaskIndex(1);

        when(mockInProgressJobs.containsJob(JOB_ID))
                .thenReturn(true);

        when(mockInProgressJobs.getJob(JOB_ID))
                .thenReturn(job);

        when(mockAggregateJobPropertiesUtil.getValue(MpfConstants.CONFIDENCE_THRESHOLD_PROPERTY, job, media, action))
                .thenReturn(String.valueOf(0.1));
    }


    @Test
    public void testHappyPath() {
        var trackType = "TEST_TYPE";
        DetectionProtobuf.DetectionResponse detectionResponse =
                DetectionProtobuf.DetectionResponse.newBuilder()
                .setMediaId(MEDIA_ID)
                .addVideoResponses(DetectionProtobuf.DetectionResponse.VideoResponse.newBuilder()
                                   .setDetectionType(trackType)
                                   .setStartFrame(START_FRAME)
                                   .setStopFrame(STOP_FRAME)
                                   .addVideoTracks(DetectionProtobuf.VideoTrack.newBuilder()
                                                   .setStartFrame(5)
                                                   .setStopFrame(5)
                                                   .setConfidence(0.5f)
                                                   .addDetectionProperties(DetectionProtobuf.PropertyMap.newBuilder()
                                                                           .setKey("TRACK_TEST_PROP_KEY")
                                                                           .setValue("TRACK_TEST_PROP_VALUE"))
                                                   .addFrameLocations(DetectionProtobuf.VideoTrack.FrameLocationMap.newBuilder()
                                                                      .setFrame(5)
                                                                      .setImageLocation(DetectionProtobuf.ImageLocation.newBuilder()
                                                                                        .setConfidence(0.5f)
                                                                                        .setXLeftUpper(0)
                                                                                        .setYLeftUpper(10)
                                                                                        .setHeight(10)
                                                                                        .setWidth(10)
                                                                                        .addDetectionProperties(DetectionProtobuf.PropertyMap.newBuilder()
                                                                                                                .setKey("DETECTION_TEST_PROP_KEY")
                                                                                                                .setValue("DETECTION_TEST_PROP_VALUE"))))))
                .setTaskName(DETECTION_RESPONSE_TASK_NAME)
                .setTaskIndex(1)
                .setActionName(DETECTION_RESPONSE_ACTION_NAME)
                .setActionIndex(1)
                .setRequestId(123456)
                .build();

        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().getHeaders().put(MpfHeaders.JOB_ID, JOB_ID);
        exchange.getIn().setBody(detectionResponse);


        var mergedType = "MERGED_TYPE";
        var mergedAlgo = "MERGED_ALGO";
        var taskMergeRespCtx = mock(TaskMergingManager.ResponseTaskMergingContext.class);
        when(taskMergeRespCtx.getDetectionType())
                .thenReturn(mergedType);
        when(taskMergeRespCtx.getAlgorithm())
                .thenReturn(mergedAlgo);

        when(mockTaskMergingManager.getResponseContext(
                argThat(j -> j.getId() == JOB_ID),
                argThat(m -> m.getId() == MEDIA_ID),
                eq(1),
                eq(1),
                eq(trackType),
                eq(exchange.getIn().getHeaders())))
            .thenReturn(taskMergeRespCtx);

        detectionResponseProcessor.wfmProcess(exchange);
        Assert.assertEquals(JOB_ID, exchange.getOut().getHeader(MpfHeaders.JOB_ID));
        Assert.assertEquals(1, exchange.getOut().getHeader(MpfHeaders.TASK_INDEX));

        verify(taskMergeRespCtx).close();
        verify(mockInProgressJobs, never())
                .setJobStatus(eq(JOB_ID), any(BatchJobStatusType.class)); // job is already IN_PROGRESS at this point
        verify(mockInProgressJobs, never())
                .addDetectionProcessingError(any());
        verify(mockInProgressJobs, never())
                .addJobWarning(eq(JOB_ID), any(), any());

        var trackCaptor = ArgumentCaptor.forClass(Track.class);
        verify(mockInProgressJobs, times(1))
                .addTrack(trackCaptor.capture());
        var track = trackCaptor.getValue();
        assertEquals(JOB_ID, track.getJobId());
        assertEquals(5, track.getStartOffsetFrameInclusive());
        assertEquals(trackType, track.getType());
        assertEquals(mergedType, track.getMergedType());
        assertEquals(mergedAlgo, track.getMergedAlgorithm());
    }

    @Test
    public void testVideoResponseError() {
        DetectionProtobuf.DetectionError error = DetectionProtobuf.DetectionError.BAD_FRAME_SIZE;

        processVideoJob(error);

        verify(mockInProgressJobs, times(1))
                .addDetectionProcessingError(detectionProcessingError(JOB_ID, error, START_FRAME, STOP_FRAME,
                        START_TIME, STOP_TIME));
        verify(mockInProgressJobs, never())
                .addJobWarning(eq(JOB_ID), any(), any());
    }

    @Test
    public void testVideoResponseCancelled() {
        DetectionProtobuf.DetectionError error = DetectionProtobuf.DetectionError.REQUEST_CANCELLED;

        processVideoJob(error);

        verify(mockInProgressJobs, times(1))
                .addDetectionProcessingError(detectionProcessingError(JOB_ID, error, START_FRAME, STOP_FRAME,
                        START_TIME, STOP_TIME));
        verify(mockInProgressJobs, never())
                .addJobWarning(eq(JOB_ID), any(), any());
    }

    private void processVideoJob(DetectionProtobuf.DetectionError error) {
        DetectionProtobuf.DetectionResponse detectionResponse = DetectionProtobuf.DetectionResponse.newBuilder()
                .setError(error)
                .setMediaId(MEDIA_ID)
                .addVideoResponses(DetectionProtobuf.DetectionResponse.VideoResponse.newBuilder()
                        .setDetectionType("TEST")
                        .setStartFrame(START_FRAME)
                        .setStopFrame(STOP_FRAME))
                .setTaskName(DETECTION_RESPONSE_TASK_NAME)
                .setTaskIndex(1)
                .setActionName(DETECTION_RESPONSE_ACTION_NAME)
                .setActionIndex(1)
                .setRequestId(123456)
                .build();

        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().getHeaders().put(MpfHeaders.JOB_ID, JOB_ID);
        exchange.getIn().setBody(detectionResponse);

        detectionResponseProcessor.wfmProcess(exchange);
    }

    @Test
    public void testAudioResponseError() {
        DetectionProtobuf.DetectionError error = DetectionProtobuf.DetectionError.COULD_NOT_READ_DATAFILE;

        DetectionProtobuf.DetectionResponse detectionResponse = DetectionProtobuf.DetectionResponse.newBuilder()
                .setError(error)
                .setMediaId(MEDIA_ID)
                .addAudioResponses(DetectionProtobuf.DetectionResponse.AudioResponse.newBuilder()
                        .setDetectionType("TEST")
                        .setStartTime(START_TIME)
                        .setStopTime(STOP_TIME))
                .setTaskName(DETECTION_RESPONSE_TASK_NAME)
                .setTaskIndex(1)
                .setActionName(DETECTION_RESPONSE_ACTION_NAME)
                .setActionIndex(1)
                .setRequestId(123456)
                .build();

        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().getHeaders().put(MpfHeaders.JOB_ID, JOB_ID);
        exchange.getIn().setBody(detectionResponse);

        detectionResponseProcessor.wfmProcess(exchange);

        verify(mockInProgressJobs, times(1))
                .addDetectionProcessingError(detectionProcessingError(JOB_ID, error, 0, 0, START_TIME, STOP_TIME));
        verify(mockInProgressJobs, never())
                .addJobWarning(eq(JOB_ID), any(), any());
    }

    @Test
    public void testImageResponseError() {
        DetectionProtobuf.DetectionError error = DetectionProtobuf.DetectionError.COULD_NOT_READ_MEDIA;

        DetectionProtobuf.DetectionResponse detectionResponse = DetectionProtobuf.DetectionResponse.newBuilder()
                .setError(error)
                .setMediaId(MEDIA_ID)
                .addImageResponses(DetectionProtobuf.DetectionResponse.ImageResponse.newBuilder()
                        .setDetectionType("TEST"))
                .setTaskName(DETECTION_RESPONSE_TASK_NAME)
                .setTaskIndex(1)
                .setActionName(DETECTION_RESPONSE_ACTION_NAME)
                .setActionIndex(1)
                .setRequestId(123456)
                .build();

        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().getHeaders().put(MpfHeaders.JOB_ID, JOB_ID);
        exchange.getIn().setBody(detectionResponse);

        detectionResponseProcessor.wfmProcess(exchange);

        verify(mockInProgressJobs, times(1))
                .addDetectionProcessingError(detectionProcessingError(JOB_ID, error, 0, 1, 0 ,0));
        verify(mockInProgressJobs, never())
                .addJobWarning(eq(JOB_ID), any(), any());
    }

    @Test
    public void testGenericResponseError() {
        DetectionProtobuf.DetectionError error = DetectionProtobuf.DetectionError.COULD_NOT_OPEN_DATAFILE;

        DetectionProtobuf.DetectionResponse detectionResponse = DetectionProtobuf.DetectionResponse.newBuilder()
                .setError(error)
                .setMediaId(MEDIA_ID)
                .addGenericResponses(DetectionProtobuf.DetectionResponse.GenericResponse.newBuilder()
                        .setDetectionType("TEST"))
                .setTaskName(DETECTION_RESPONSE_TASK_NAME)
                .setTaskIndex(1)
                .setActionName(DETECTION_RESPONSE_ACTION_NAME)
                .setActionIndex(1)
                .setRequestId(123456)
                .build();

        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().getHeaders().put(MpfHeaders.JOB_ID, JOB_ID);
        exchange.getIn().setBody(detectionResponse);

        detectionResponseProcessor.wfmProcess(exchange);

        verify(mockInProgressJobs, times(1))
                .addDetectionProcessingError(detectionProcessingError(JOB_ID, error, 0, 0, 0, 0));
        verify(mockInProgressJobs, never())
                .addJobWarning(eq(JOB_ID), any(), any());
    }

    private static DetectionProcessingError detectionProcessingError(long jobId, DetectionProtobuf.DetectionError error,
                                                                     int startFrame, int stopFrame,
                                                                     int startTime, int stopTime) {
        return ArgumentMatchers.argThat(new ArgumentMatcher<>() {

            public String toString() {
                String description = "DetectionProcessingError { jobId = " + jobId;
                if (error != null) {
                    description += ", error = " + error.toString();
                }
                description += ", startFrame = " + startFrame;
                description += ", stopFrame = " + stopFrame;
                description += ", startTime = " + startTime;
                description += ", stopTime = " + stopTime;
                description += " }";
                return description;
            }

            @Override
            public boolean matches(DetectionProcessingError obj) {
                return jobId == obj.getJobId()
                        && (error == null || error.toString().equals(obj.getErrorCode()))
                        && startFrame == obj.getStartFrame()
                        && stopFrame == obj.getStopFrame()
                        && startTime == obj.getStartTime()
                        && stopTime == obj.getStopTime();
            }
        });
    }

    private static Track track(long jobId, int startFrame) {
        return ArgumentMatchers.argThat(new ArgumentMatcher<>() {
            public String toString() {
                return "Track { jobId = " + jobId + ", startFrame = " + startFrame + " }";
            }

            public boolean matches(Track obj) {
                return jobId == obj.getJobId() && startFrame == obj.getStartOffsetFrameInclusive();
            }
        });
    }
}
