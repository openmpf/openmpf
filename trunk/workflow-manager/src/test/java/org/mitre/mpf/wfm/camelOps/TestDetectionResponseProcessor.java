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

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionResponseProcessor;
import org.mitre.mpf.wfm.camel.operations.detection.trackmerging.TrackMergingContext;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.pipeline.xml.ActionDefinition;
import org.mitre.mpf.wfm.pipeline.xml.AlgorithmDefinition;
import org.mitre.mpf.wfm.service.PipelineService;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mockito.InjectMocks;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

public class TestDetectionResponseProcessor {

    @Mock
    private PipelineService mockPipelineService;

    @Mock
    private Redis mockRedis;

    @Mock
    private JsonUtils mockJsonUtils; // used by DetectionResponseProcessor

    @InjectMocks
    private DetectionResponseProcessor mockDetectionResponseProcessor;

    private static final long JOB_ID = 111;
    private static final long MEDIA_ID = 222;
    private static final String DETECTION_RESPONSE_ALG_NAME = "TEST_DETECTION_RESPONSE_ALG";
    private static final String DETECTION_RESPONSE_ACTION_NAME = "TEST_DETECTION_RESPONSE_ACTION";
    private static final String DETECTION_RESPONSE_PIPELINE_NAME = "TEST_DETECTION_RESPONSE_PIPELINE";
    private static final String DETECTION_RESPONSE_STAGE_NAME = "TEST_DETECTION_RESPONSE_STAGE";

    @Before
    public void init() throws Exception {

        MockitoAnnotations.initMocks(this);

        AlgorithmDefinition algorithmDefinition = new AlgorithmDefinition(ActionType.DETECTION,
                DETECTION_RESPONSE_ALG_NAME, DETECTION_RESPONSE_ALG_NAME + "_DESC", true, false);

        when(mockPipelineService.getAlgorithm(algorithmDefinition.getName()))
                .thenReturn(algorithmDefinition);

        ActionDefinition actionDefinition = new ActionDefinition(DETECTION_RESPONSE_ACTION_NAME,
                DETECTION_RESPONSE_ALG_NAME, DETECTION_RESPONSE_ALG_NAME + "_DESC");

        when(mockPipelineService.getAction(actionDefinition.getName()))
                .thenReturn(actionDefinition);

        when(mockPipelineService.getAlgorithm(actionDefinition))
                .thenReturn(algorithmDefinition);

        TransientPipeline detectionPipeline = new TransientPipeline(DETECTION_RESPONSE_PIPELINE_NAME,
                DETECTION_RESPONSE_PIPELINE_NAME + "_DESC");

        TransientStage detectionStage = new TransientStage(DETECTION_RESPONSE_STAGE_NAME,
                DETECTION_RESPONSE_STAGE_NAME + "_DESC", ActionType.DETECTION);

        TransientAction detectionAction = new TransientAction(actionDefinition.getName(),
                actionDefinition.getDescription(), actionDefinition.getAlgorithmRef());

        detectionStage.getActions().add(detectionAction);
        detectionPipeline.getStages().add(detectionStage);

        Map<String, String> propertiesMap = new HashMap<>();
        propertiesMap.put("detection.confidence.threshold", "-1");
        TransientDetectionSystemProperties propertiesSnapshot = new TransientDetectionSystemProperties(propertiesMap);

        TransientJob detectionJob = new TransientJob(JOB_ID, "externalId", propertiesSnapshot, detectionPipeline, 0, 1,
                false, false);
        TransientMedia media = new TransientMedia(MEDIA_ID, "/some/path");
        media.addMetadata("DURATION", "3004");
        media.addMetadata("FPS", "29.97");
        detectionJob.getMedia().add(media);

        when(mockRedis.containsJob(JOB_ID))
                .thenReturn(true);

        when(mockRedis.getJob(JOB_ID, MEDIA_ID))
                .thenReturn(detectionJob);

        when(mockJsonUtils.serialize(any()))
                .thenCallRealMethod();

        when(mockJsonUtils.deserialize(any(), any()))
                .thenCallRealMethod();

        Method postConstruct =  JsonUtils.class.getDeclaredMethod("init");
        postConstruct.setAccessible(true);
        postConstruct.invoke(mockJsonUtils);
    }


    //	TODO: public Object processResponse(long jobId, Extraction.ExtractionResponse extractionResponse, Map<String, Object> headers) throws WfmProcessingException


    @Test
    public void testNoDetectionProcessingError() {
        DetectionProtobuf.DetectionResponse detectionResponse = DetectionProtobuf.DetectionResponse.newBuilder()
                .setMediaId(MEDIA_ID)
                .addVideoResponses(DetectionProtobuf.DetectionResponse.VideoResponse.newBuilder()
                        .setDetectionType("TEST")
                        .setStartFrame(0)
                        .setStopFrame(10))
                .setStageName(DETECTION_RESPONSE_STAGE_NAME)
                .setStageIndex(1)
                .setActionName(DETECTION_RESPONSE_ACTION_NAME)
                .setActionIndex(1)
                .setRequestId(123456)
                .build();

        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().getHeaders().put(MpfHeaders.JOB_ID, JOB_ID);
        exchange.getIn().setBody(detectionResponse);

        mockDetectionResponseProcessor.wfmProcess(exchange);

        Object responseBody = exchange.getOut().getBody();
        TrackMergingContext processorResponse =
                mockJsonUtils.deserialize((byte[])responseBody, TrackMergingContext.class);

        Assert.assertEquals(JOB_ID, processorResponse.getJobId());
        Assert.assertEquals(1, processorResponse.getStageIndex());

        verify(mockRedis, never())
                .setJobStatus(eq(JOB_ID), any(BatchJobStatusType.class)); // job is already IN_PROGRESS at this point

        verify(mockRedis, never())
                .addDetectionProcessingError(any());

        verify(mockRedis, never())
                .addDetectionProcessingError(any());

        verify(mockRedis, never())
                .addJobError(eq(JOB_ID), any());

        verify(mockRedis, never())
                .addJobWarning(eq(JOB_ID), any());
    }

    @Test
    public void testDetectionProcessingError() {
        DetectionProtobuf.DetectionError error = DetectionProtobuf.DetectionError.BOUNDING_BOX_SIZE_ERROR;

        DetectionProtobuf.DetectionResponse detectionResponse = DetectionProtobuf.DetectionResponse.newBuilder()
                .setError(error)
                .setMediaId(MEDIA_ID)
                .addVideoResponses(DetectionProtobuf.DetectionResponse.VideoResponse.newBuilder()
                        .setDetectionType("TEST")
                        .setStartFrame(0)
                        .setStopFrame(10))
                .setStageName(DETECTION_RESPONSE_STAGE_NAME)
                .setStageIndex(1)
                .setActionName(DETECTION_RESPONSE_ACTION_NAME)
                .setActionIndex(1)
                .setRequestId(123456)
                .build();

        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().getHeaders().put(MpfHeaders.JOB_ID, JOB_ID);
        exchange.getIn().setBody(detectionResponse);

        mockDetectionResponseProcessor.wfmProcess(exchange);

        verify(mockRedis, times(1))
                .setJobStatus(JOB_ID, BatchJobStatusType.IN_PROGRESS_ERRORS);

        verify(mockRedis, times(1))
                .addDetectionProcessingError(detectionProcessingError(JOB_ID, error));

        // TODO: https://github.com/openmpf/openmpf/issues/780
        /*
        verify(mockRedis, times(1))
                .addJobError(eq(JOB_ID), any());

        verify(mockRedis, never())
                .addJobWarning(eq(JOB_ID), any());

        verify(mockRedis, times(1))
                .setJobStatus(JOB_ID, BatchJobStatusType.COMPLETE_WITH_ERRORS);
        */
    }

    private static DetectionProcessingError detectionProcessingError(long jobId, DetectionProtobuf.DetectionError error) {
        return Matchers.argThat(new BaseMatcher<DetectionProcessingError>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("DetectionProcessingError { jobId = ").appendValue(jobId);
                if (error != null) {
                    description.appendText(", error = ").appendValue(error.toString());
                }
                description.appendText(" }");
            }

            @Override
            public boolean matches(Object obj) {
                DetectionProcessingError other = (DetectionProcessingError) obj;
                return jobId == other.getJobId() && (error == null || error.toString().equals(other.getError()));
            }
        });
    }
}

