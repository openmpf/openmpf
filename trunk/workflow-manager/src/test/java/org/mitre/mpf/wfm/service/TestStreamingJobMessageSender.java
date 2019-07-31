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

package org.mitre.mpf.wfm.service;


import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.nms.MasterNode;
import org.mitre.mpf.nms.streaming.messages.LaunchStreamingJobMessage;
import org.mitre.mpf.nms.streaming.messages.StopStreamingJobMessage;
import org.mitre.mpf.rest.api.node.EnvironmentVariableModel;
import org.mitre.mpf.wfm.data.entities.transients.TransientPipeline;
import org.mitre.mpf.wfm.data.entities.transients.TransientStream;
import org.mitre.mpf.wfm.data.entities.transients.TransientStreamingJob;
import org.mitre.mpf.wfm.data.entities.transients.TransientStreamingJobImpl;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.enums.StreamingEndpoints;
import org.mitre.mpf.wfm.pipeline.Action;
import org.mitre.mpf.wfm.pipeline.Algorithm;
import org.mitre.mpf.wfm.pipeline.Pipeline;
import org.mitre.mpf.wfm.pipeline.Task;
import org.mitre.mpf.wfm.pipeline.ValueType;
import org.mitre.mpf.wfm.service.component.ComponentLanguage;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class TestStreamingJobMessageSender {

    private StreamingJobMessageSenderImpl _messageSender;

    @Mock
    private PropertiesUtil _mockProperties;

    @Mock
    private MasterNode _mockMasterNode;

    @Mock
    private StreamingServiceManager _mockServiceManager;


    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        AggregateJobPropertiesUtil aggregateJobPropertiesUtil
                = new AggregateJobPropertiesUtil(_mockProperties, null, null, null);
        _messageSender = new StreamingJobMessageSenderImpl(_mockProperties, aggregateJobPropertiesUtil,
                                                           _mockMasterNode, _mockServiceManager);
    }


    @Test
    public void testLaunch() {
        long stallAlertThreshold = 234;
        when(_mockProperties.getStreamingJobStallAlertThreshold())
                .thenReturn(stallAlertThreshold);

        String activeMqUri = "failover://(tcp://localhost.localdomain:61616)?jms.prefetchPolicy.all=0&startupMaxReconnectAttempts=1";
        when(_mockProperties.getAmqUri())
                .thenReturn(activeMqUri);


        List<Algorithm.Property> algoProperties = Arrays.asList(
                new Algorithm.Property("OVERRIDDEN ACTION PROPERTY", "desc", ValueType.STRING,
                                       "Bad Value", null),
                new Algorithm.Property("ALGO PROPERTY2", "desc", ValueType.STRING,
                                       "Algo Value2", null),
                new Algorithm.Property("OVERRIDDEN JOB PROPERTY", "desc", ValueType.STRING,
                                       "Bad Value", null),
                new Algorithm.Property("OVERRIDDEN STREAM PROPERTY", "desc", ValueType.STRING,
                                       "Bad Value", null)
        );

        Algorithm algorithm = new Algorithm(
                "TEST ALGO",
                "Algo Description",
                ActionType.DETECTION,
                new Algorithm.Requires(Collections.emptyList()),
                new Algorithm.Provides(Collections.emptyList(), algoProperties),
                true,
                true);


        List<Action.Property> actionProperties = Arrays.asList(
                new Action.Property("OVERRIDDEN ACTION PROPERTY", "ACTION VAL"),
                new Action.Property("OVERRIDDEN JOB PROPERTY", "Bad Value")
        );
        Action action = new Action("ActionName", "Action description", algorithm.getName(),
                                   actionProperties);

        Task task = new Task("TaskName", "Task description",
                             Collections.singletonList(action.getName()));

        Pipeline pipeline = new Pipeline("MyStreamingPipeline", "Pipeline description",
                                         Collections.singletonList(task.getName()));
        TransientPipeline transientPipeline = new TransientPipeline(
                pipeline,
                Collections.singletonList(task),
                Collections.singletonList(action),
                Collections.singletonList(algorithm));



        TransientStream stream = new TransientStream(
                5234,
                "stream://myStream",
                543,
                ImmutableMap.of("OVERRIDDEN STREAM PROPERTY", "Stream Specific Value"),
                ImmutableMap.of("mediaProp1", "mediaVal1"));

        long jobId = 1234;
        TransientStreamingJob job = new TransientStreamingJobImpl(
                jobId,
                "external id",
                transientPipeline,
                stream,
                5,
                100,
                true,
                "output-dir",
                null,
                null,
                Collections.singletonMap("OVERRIDDEN JOB PROPERTY", "Job Overridden Value"),
                Collections.emptyMap());

        ArgumentCaptor<LaunchStreamingJobMessage> msgCaptor = ArgumentCaptor.forClass(LaunchStreamingJobMessage.class);

        List<EnvironmentVariableModel> envVars = Arrays.asList(
                new EnvironmentVariableModel("LD_LIB_PATH", "/opt/mpf", null),
                new EnvironmentVariableModel("var2", "val2", null));

        StreamingServiceModel streamingServiceModel = new StreamingServiceModel(
                "MyService", algorithm.getName(), ComponentLanguage.CPP,
                "my-component/lib/libComponent.so", envVars);

        when(_mockServiceManager.getServices())
                .thenReturn(Arrays.asList(
                        new StreamingServiceModel(
                                "Wrong Service", "Wrong Algo", ComponentLanguage.JAVA, "bad-path/lib",
                                Collections.emptyList()),
                        streamingServiceModel));



        _messageSender.launchJob(job);



        verify(_mockMasterNode)
                .startStreamingJob(msgCaptor.capture());

        LaunchStreamingJobMessage launchMessage = msgCaptor.getValue();

        assertEquals(jobId, launchMessage.jobId);
        assertEquals(stream.getUri(), launchMessage.streamUri);
        assertEquals(stream.getSegmentSize(), launchMessage.segmentSize);
        assertEquals(job.getStallTimeout(), launchMessage.stallTimeout);
        assertEquals(stallAlertThreshold, launchMessage.stallAlertThreshold);
        assertEquals(streamingServiceModel.getServiceName(), launchMessage.componentName);
        assertEquals(streamingServiceModel.getLibraryPath(), launchMessage.componentLibraryPath);

        assertEquals("val2", launchMessage.componentEnvironmentVariables.get("var2"));
        assertEquals("/opt/mpf", launchMessage.componentEnvironmentVariables.get("LD_LIB_PATH"));

        assertEquals("Algo Value2", launchMessage.jobProperties.get("ALGO PROPERTY2"));
        assertEquals("ACTION VAL", launchMessage.jobProperties.get("OVERRIDDEN ACTION PROPERTY"));
        assertEquals("Job Overridden Value", launchMessage.jobProperties.get("OVERRIDDEN JOB PROPERTY"));
        assertEquals("Stream Specific Value", launchMessage.jobProperties.get("OVERRIDDEN STREAM PROPERTY"));


        assertEquals("mediaVal1", launchMessage.mediaProperties.get("mediaProp1"));

        assertEquals(activeMqUri, launchMessage.messageBrokerUri);
        assertEquals(StreamingEndpoints.WFM_STREAMING_JOB_STATUS.queueName(), launchMessage.jobStatusQueue);
        assertEquals(StreamingEndpoints.WFM_STREAMING_JOB_ACTIVITY.queueName(), launchMessage.activityAlertQueue);
        assertEquals(StreamingEndpoints.WFM_STREAMING_JOB_SUMMARY_REPORTS.queueName(),
                     launchMessage.summaryReportQueue);

    }



    @Test
    public void testStop() {
        long jobId = 1245;
        _messageSender.stopJob(jobId);

        ArgumentCaptor<StopStreamingJobMessage> msgCaptor = ArgumentCaptor.forClass(StopStreamingJobMessage.class);
        verify(_mockMasterNode)
                .stopStreamingJob(msgCaptor.capture());

        StopStreamingJobMessage stopMessage = msgCaptor.getValue();
        assertEquals(jobId, stopMessage.jobId);
    }
}
