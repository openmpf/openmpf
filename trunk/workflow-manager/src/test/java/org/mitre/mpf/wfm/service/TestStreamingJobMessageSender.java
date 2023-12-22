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

package org.mitre.mpf.wfm.service;


import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.nms.MasterNode;
import org.mitre.mpf.nms.streaming.messages.LaunchStreamingJobMessage;
import org.mitre.mpf.nms.streaming.messages.StopStreamingJobMessage;
import org.mitre.mpf.rest.api.node.EnvironmentVariableModel;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.ActionProperty;
import org.mitre.mpf.rest.api.pipelines.ActionType;
import org.mitre.mpf.rest.api.pipelines.Algorithm;
import org.mitre.mpf.rest.api.pipelines.AlgorithmProperty;
import org.mitre.mpf.rest.api.pipelines.Pipeline;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.rest.api.pipelines.ValueType;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.mitre.mpf.wfm.data.entities.persistent.MediaStreamInfo;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobImpl;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.enums.StreamingEndpoints;
import org.mitre.mpf.wfm.service.component.ComponentLanguage;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;


public class TestStreamingJobMessageSender extends MockitoTest.Lenient {

    private StreamingJobMessageSenderImpl _messageSender;

    @Mock
    private PropertiesUtil _mockProperties;

    @Mock
    private MasterNode _mockMasterNode;

    @Mock
    private StreamingServiceManager _mockServiceManager;

    @Mock
    private WorkflowPropertyService _mockWorkflowPropertyService;


    @Before
    public void init() {
        AggregateJobPropertiesUtil aggregateJobPropertiesUtil
                = new AggregateJobPropertiesUtil(_mockProperties, _mockWorkflowPropertyService);
        _messageSender = new StreamingJobMessageSenderImpl(_mockProperties, aggregateJobPropertiesUtil,
                                                           _mockMasterNode, _mockServiceManager);
    }


    @Test
    public void testLaunch() {
        long stallAlertThreshold = 234;
        when(_mockProperties.getStreamingJobStallAlertThreshold())
                .thenReturn(stallAlertThreshold);

        var activeMqUri = "failover:(tcp://localhost.localdomain:61616)?jms.prefetchPolicy.all=0&startupMaxReconnectAttempts=1";
        when(_mockProperties.getAmqUri())
                .thenReturn(activeMqUri);

        var workflowProp1 = new WorkflowProperty("WORKFLOW_PROP", "descr", ValueType.INT,
                                                 "1", null, List.of(MediaType.VIDEO));
        var workflowProp2 = new WorkflowProperty("OVERRIDDEN JOB PROPERTY", "descr", ValueType.STRING,
                                                 "INVALID", null, List.of(MediaType.VIDEO));
        when(_mockWorkflowPropertyService.getProperties(MediaType.VIDEO))
                .thenReturn(ImmutableList.of(workflowProp1, workflowProp2));
        when(_mockWorkflowPropertyService.getPropertyValue(workflowProp1.getName(), MediaType.VIDEO, null))
                .thenReturn(workflowProp1.getDefaultValue());
        when(_mockWorkflowPropertyService.getPropertyValue(workflowProp2.getName(), MediaType.VIDEO, null))
                .thenReturn(workflowProp2.getDefaultValue());

        var algoProperties = List.of(
                new AlgorithmProperty("OVERRIDDEN ACTION PROPERTY", "desc", ValueType.STRING,
                                       "Bad Value", null),
                new AlgorithmProperty("ALGO PROPERTY2", "desc", ValueType.STRING,
                                       "Algo Value2", null),
                new AlgorithmProperty("OVERRIDDEN JOB PROPERTY", "desc", ValueType.STRING,
                                       "Bad Value", null),
                new AlgorithmProperty("OVERRIDDEN STREAM PROPERTY", "desc", ValueType.STRING,
                                       "Bad Value", null)
        );

        var algorithm = new Algorithm(
                "TEST ALGO",
                "Algo Description",
                ActionType.DETECTION,
                "TEST",
                OptionalInt.empty(),
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), algoProperties),
                true,
                true);


        var actionProperties = List.of(
                new ActionProperty("OVERRIDDEN ACTION PROPERTY", "ACTION VAL"),
                new ActionProperty("OVERRIDDEN JOB PROPERTY", "Bad Value")
        );
        var action = new Action("ActionName", "Action description", algorithm.name(),
                                actionProperties);

        var task = new Task("TaskName", "Task description",
                             List.of(action.name()));

        var pipeline = new Pipeline("MyStreamingPipeline", "Pipeline description",
                                    List.of(task.name()));
        var pipelineElements = new JobPipelineElements(
                pipeline,
                List.of(task),
                List.of(action),
                List.of(algorithm));



        var stream = new MediaStreamInfo(
                5234,
                "stream://myStream",
                543,
                ImmutableMap.of("OVERRIDDEN STREAM PROPERTY", "Stream Specific Value"));

        long jobId = 1234;
        var job = new StreamingJobImpl(
                jobId,
                "external id",
                pipelineElements,
                stream,
                5,
                100,
                true,
                "output-dir",
                null,
                null,
                Map.of("OVERRIDDEN JOB PROPERTY", "Job Overridden Value"),
                Map.of());

        var msgCaptor = ArgumentCaptor.forClass(LaunchStreamingJobMessage.class);

        var envVars = List.of(
                new EnvironmentVariableModel("LD_LIB_PATH", "/opt/mpf", null),
                new EnvironmentVariableModel("var2", "val2", null));

        var streamingServiceModel = new StreamingServiceModel(
                "MyService", algorithm.name(), ComponentLanguage.CPP,
                "my-component/lib/libComponent.so", envVars);

        when(_mockServiceManager.getServices())
                .thenReturn(Arrays.asList(
                        new StreamingServiceModel(
                                "Wrong Service", "Wrong Algo", ComponentLanguage.JAVA, "bad-path/lib",
                                List.of()),
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
        assertEquals("1", launchMessage.jobProperties.get("WORKFLOW_PROP"));


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

        var msgCaptor = ArgumentCaptor.forClass(StopStreamingJobMessage.class);
        verify(_mockMasterNode)
                .stopStreamingJob(msgCaptor.capture());

        StopStreamingJobMessage stopMessage = msgCaptor.getValue();
        assertEquals(jobId, stopMessage.jobId);
    }
}
