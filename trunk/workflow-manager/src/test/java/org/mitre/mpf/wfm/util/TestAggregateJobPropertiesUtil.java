/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mitre.mpf.rest.api.pipelines.*;
import org.mitre.mpf.wfm.data.entities.persistent.*;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.service.WorkflowProperty;
import org.mitre.mpf.wfm.service.WorkflowPropertyService;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// There are also integration tests in TestDetectionTaskSplitter that test AggregateJobPropertiesUtil.
public class TestAggregateJobPropertiesUtil {

    @Test
    public void testPropertyOverriding() throws IOException {

        var overriddenUpToMedia = Map.entry("OVERRIDDEN TO MEDIA", "MEDIA VALUE");
        var overriddenUpToOverriddenAlgo = Map.entry("OVERRIDDEN TO OVERRIDDEN ALGO", "OVERRIDDEN ALGO VALUE");
        var overriddenUpToJob = Map.entry("OVERRIDDEN TO JOB", "JOB VALUE");
        var overriddenUpToAction = Map.entry("OVERRIDDEN TO ACTION", "ACTION VALUE");
        var overriddenUpToAlgo = Map.entry("OVERRIDDEN TO ALGO", "ALGO VALUE");
        var overriddenUpToWorkflow = Map.entry("OVERRIDDEN TO WORKFLOW", "WORKFLOW VALUE");

        var algoPropertyFromSnapshot = Map.entry("ALGO SNAPSHOT PROPERTY", "ALGO SNAPSHOT VALUE");
        var workflowPropertyFromSnapshot = Map.entry("WORKFLOW SNAPSHOT PROPERTY", "WORKFLOW SNAPSHOT VALUE");

        var algoPropertyFromPropUtil = Map.entry("ALGO PROP UTIL PROPERTY", "ALGO PROP UTIL VALUE");
        var workflowPropertyFromPropUtil = Map.entry("WORKFLOW PROP UTIL PROPERTY", "WORKFLOW PROP UTIL VALUE");

        var expectedProperties = Map.ofEntries(
                overriddenUpToMedia, overriddenUpToOverriddenAlgo, overriddenUpToJob, overriddenUpToAction,
                overriddenUpToAlgo, overriddenUpToWorkflow, algoPropertyFromSnapshot, workflowPropertyFromSnapshot,
                algoPropertyFromPropUtil, workflowPropertyFromPropUtil);

        var mediaProperties = Map.ofEntries(overriddenUpToMedia);

        var overriddenAlgoProperties = Map.of(
                overriddenUpToMedia.getKey(), "WRONG",
                overriddenUpToOverriddenAlgo.getKey(), overriddenUpToOverriddenAlgo.getValue());

        var jobProperties = Map.of(
                overriddenUpToMedia.getKey(), "WRONG",
                overriddenUpToOverriddenAlgo.getKey(), "WRONG",
                overriddenUpToJob.getKey(), overriddenUpToJob.getValue()
        );

        var actionPropertyList = List.of(
                new ActionProperty(overriddenUpToMedia.getKey(), "WRONG"),
                new ActionProperty(overriddenUpToOverriddenAlgo.getKey(), "WRONG"),
                new ActionProperty(overriddenUpToJob.getKey(), "WRONG"),
                new ActionProperty(overriddenUpToAction.getKey(), overriddenUpToAction.getValue())
        );


        var algoPropertyList = List.of(
                new AlgorithmProperty(overriddenUpToMedia.getKey(), "descr",
                                      ValueType.STRING, "WRONG", null),
                new AlgorithmProperty(overriddenUpToOverriddenAlgo.getKey(), "descr",
                                      ValueType.STRING, "WRONG", null),
                new AlgorithmProperty(overriddenUpToJob.getKey(), "descr",
                                      ValueType.STRING, "WRONG", null),
                new AlgorithmProperty(overriddenUpToAction.getKey(), "descr",
                                      ValueType.STRING, "WRONG", null),
                new AlgorithmProperty(overriddenUpToAlgo.getKey(), "descr",
                                      ValueType.STRING, overriddenUpToAlgo.getValue(), null),
                new AlgorithmProperty(algoPropertyFromSnapshot.getKey(), "descr",
                                      ValueType.STRING, null, "algo.snapshot.property"),
                new AlgorithmProperty(algoPropertyFromPropUtil.getKey(), "descr",
                                      ValueType.STRING, null, "algo.prop.util.property")
        );

        var workflowProperties = List.of(
                new WorkflowProperty(overriddenUpToMedia.getKey(), "descr", ValueType.STRING,
                                     "WRONG", null, List.of(MediaType.VIDEO)),
                new WorkflowProperty(overriddenUpToOverriddenAlgo.getKey(), "descr", ValueType.STRING,
                                     "WRONG", null, List.of(MediaType.VIDEO)),
                new WorkflowProperty(overriddenUpToJob.getKey(), "descr", ValueType.STRING,
                                     "WRONG", null, List.of(MediaType.VIDEO)),
                new WorkflowProperty(overriddenUpToAction.getKey(), "descr", ValueType.STRING,
                                     "WRONG", null, List.of(MediaType.VIDEO)),
                new WorkflowProperty(overriddenUpToAlgo.getKey(), "descr", ValueType.STRING,
                                     "WRONG", null, List.of(MediaType.VIDEO)),
                new WorkflowProperty(overriddenUpToWorkflow.getKey(), "descr", ValueType.STRING,
                                     overriddenUpToWorkflow.getValue(), null, List.of(MediaType.VIDEO)),
                new WorkflowProperty(algoPropertyFromSnapshot.getKey(), "descr", ValueType.STRING,
                                     "WRONG", null, List.of(MediaType.VIDEO)),
                new WorkflowProperty(workflowPropertyFromSnapshot.getKey(), "descr", ValueType.STRING,
                                     null, "workflow.snapshot.property", List.of(MediaType.VIDEO)),
                new WorkflowProperty(algoPropertyFromPropUtil.getKey(), "descr", ValueType.STRING,
                                     "WRONG", null, List.of(MediaType.VIDEO)),
                new WorkflowProperty(workflowPropertyFromPropUtil.getKey(), "descr", ValueType.STRING,
                                     null, "workflow.prop.util.property", List.of(MediaType.VIDEO))
        );

        var snapshotContent = Map.of(
                "algo.snapshot.property", algoPropertyFromSnapshot.getValue(),
                "workflow.snapshot.property", workflowPropertyFromSnapshot.getValue());

        var propertiesUtilContent = Map.of(
                "algo.prop.util.property", algoPropertyFromPropUtil.getValue(),
                "workflow.prop.util.property", workflowPropertyFromPropUtil.getValue()
        );


        var algorithm = new Algorithm(
                "ALGO", "descr", ActionType.DETECTION, "TEST", OptionalInt.empty(),
                new Algorithm.Requires(List.of()),
                new Algorithm.Provides(List.of(), algoPropertyList),
                true, true);



        var action = new Action("ACTION", "descr", algorithm.name(), actionPropertyList);

        var task = new Task("TASK", "descr", List.of(action.name()));
        var pipeline = new Pipeline("PIPELINE", "descr", List.of(task.name()));

        var pipelineElements = new JobPipelineElements(pipeline, List.of(task), List.of(action), List.of(algorithm));

        // Test with batch job
        {
            var media = new MediaImpl(
                    2, "file:/example.mp4", UriScheme.FILE,
                    Path.of("remote-media", "example.mp4"), mediaProperties, Map.of(),
                    List.of(), List.of(), null);
            media.setType(MediaType.VIDEO);
            media.setMimeType("video/mp4");


            var job = new BatchJobImpl(1, null, new SystemPropertiesSnapshot(snapshotContent),
                                       pipelineElements, 1, null,
                                       null, List.of(media), jobProperties,
                                       Map.of(algorithm.name(), overriddenAlgoProperties),
                                       false);


            var aggregateJobPropertiesUtil = getAggregateJobPropertiesUtil(workflowProperties, propertiesUtilContent);
            var combinedProps = aggregateJobPropertiesUtil.getCombinedProperties(job, media, action);

            for (var expectedPropertyEntry : expectedProperties.entrySet()) {
                assertEquals(expectedPropertyEntry.getValue(), combinedProps.apply(expectedPropertyEntry.getKey()));
            }

            assertEquals(expectedProperties, aggregateJobPropertiesUtil.getPropertyMap(job, media, action));
        }


        // Test with streaming job
        {
            // StreamingJobs don't have SystemPropertiesSnapshot
            var combinedSnapshotAndPropUtilsProps = new HashMap<>(snapshotContent);
            combinedSnapshotAndPropUtilsProps.putAll(propertiesUtilContent);

            var aggregateJobPropertiesUtil = getAggregateJobPropertiesUtil(workflowProperties,
                                                                           combinedSnapshotAndPropUtilsProps);

            var mediaStreamInfo = new MediaStreamInfo(3, "rtsp://test", 1, mediaProperties);

            var streamingJob = new StreamingJobImpl(
                    2, null, pipelineElements, mediaStreamInfo, 1, 1, true,
                    "somedir", null, null, jobProperties,
                    Map.of(algorithm.name(), overriddenAlgoProperties));

            assertEquals(expectedProperties, aggregateJobPropertiesUtil.getPropertyMap(streamingJob, action));
        }
    }


    private static AggregateJobPropertiesUtil getAggregateJobPropertiesUtil(
            List<WorkflowProperty> workflowProperties, Map<String, String> propertiesUtilContent) throws IOException {

        var mockResource = mock(Resource.class);
        when(mockResource.getInputStream())
                .thenReturn(mock(InputStream.class));

        var mockPropertiesUtil = mock(PropertiesUtil.class);

        when(mockPropertiesUtil.getWorkflowPropertiesFile())
                .thenReturn(mockResource);

        when(mockPropertiesUtil.lookup(anyString()))
                .then(i -> propertiesUtilContent.getOrDefault(i.getArgument(0, String.class), "WRONG"));

        var mockObjectMapper = mock(ObjectMapper.class);

        when(mockObjectMapper.readValue((InputStream) any(), (TypeReference<List<WorkflowProperty>>) any()))
                .thenReturn(workflowProperties);

        var workflowPropertyService = new WorkflowPropertyService(mockPropertiesUtil, mockObjectMapper);

        return new AggregateJobPropertiesUtil(mockPropertiesUtil, workflowPropertyService);
    }
}
