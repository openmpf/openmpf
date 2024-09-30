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

package org.mitre.mpf.mvc.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.interop.util.TimeUtils;
import org.mitre.mpf.rest.api.subject.CallbackMethod;
import org.mitre.mpf.rest.api.subject.CancellationState;
import org.mitre.mpf.rest.api.subject.Entity;
import org.mitre.mpf.rest.api.subject.Relationship;
import org.mitre.mpf.rest.api.subject.Relationship.MediaReference;
import org.mitre.mpf.rest.api.subject.SubjectJobDetails;
import org.mitre.mpf.rest.api.subject.SubjectJobRequest;
import org.mitre.mpf.rest.api.subject.SubjectJobResult;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.businessrules.SubjectJobRequestService;
import org.mitre.mpf.wfm.data.access.SubjectJobRepo;
import org.mitre.mpf.wfm.data.entities.persistent.DbCancellationState;
import org.mitre.mpf.wfm.data.entities.persistent.DbSubjectJob;
import org.mitre.mpf.wfm.util.CallbackStatus;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.google.common.collect.ImmutableSortedSet;


public class TestSubjectJobController extends MockitoTest.Strict {

    private MockMvc _mockMvc;

    @Mock
    private SubjectJobRequestService _mockSubjectJobRequestService;

    @Mock
    private SubjectJobRepo _mockSubjectJobRepo;

    @Before
    public void setup() {
        var controller = new SubjectJobController(
                _mockSubjectJobRequestService, _mockSubjectJobRepo);
        _mockMvc = TestUtil.initMockMvc(controller);
    }


    @Test
    public void testGetSubjectJobs() throws Exception {
        _mockMvc.perform(get("/subject/jobs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());

        var jobs = Stream.of(
            new DbSubjectJob("name1", 1, List.of(), Map.of(), null, null, null),
            new DbSubjectJob("name2", 1, List.of(), Map.of(), null, null, null),
            new DbSubjectJob("name3", 1, List.of(), Map.of(), null, null, null));

        when(_mockSubjectJobRepo.getPage(1, 100))
            .thenReturn(jobs);

        _mockMvc.perform(get("/subject/jobs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("[0]componentName").value("name1"))
            .andExpect(jsonPath("[1]componentName").value("name2"))
            .andExpect(jsonPath("[2]componentName").value("name3"));
    }


    @Test
    public void testGetMissingJob() throws Exception {
        _mockMvc.perform(get("/subject/jobs/123"))
            .andExpect(status().isNotFound());
    }


    @Test
    public void testGetJob() throws Exception {
        when(_mockSubjectJobRepo.getJobDetails(123L))
            .thenReturn(Optional.of(createJobDetails()));

        var resultActions = _mockMvc.perform(get("/subject/jobs/123"))
            .andExpect(status().isOk());
        matchJobDetails(resultActions, "$");
    }


    @Test
    public void testCreateJob() throws Exception {
        var requestJson = """
        {
            "componentName": "ExampleComponent",
            "priority": 7,
            "detectionJobIds": [1, 2, 3],
            "jobProperties": { "PROP": "VALUE" },
            "callbackURL": "http://localhost:4321",
            "callbackMethod": "POST",
            "externalId": "EXTERNAL_ID"
        }""";

        var captor = ArgumentCaptor.forClass(SubjectJobRequest.class);
        when(_mockSubjectJobRequestService.runJob(captor.capture()))
            .thenReturn(987L);

        _mockMvc.perform(postJson("/subject/jobs", requestJson))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", endsWith("/subject/jobs/987")));

        var actualRequest = captor.getValue();
        var expectedRequest = new SubjectJobRequest(
                "ExampleComponent",
                OptionalInt.of(7),
                List.of(1L, 2L, 3L),
                Map.of("PROP", "VALUE"),
                Optional.of(URI.create("http://localhost:4321")),
                Optional.of(CallbackMethod.POST),
                Optional.of("EXTERNAL_ID"));

        assertThat(actualRequest).isEqualTo(expectedRequest);
    }


    @Test
    public void testCanNotCancelJob() throws Exception {
        _mockMvc.perform(post("/subject/jobs/456/cancel"))
            .andExpect(status().isNotFound());

        var dbJob = new DbSubjectJob(
                "ExampleComponent", 8,
                List.of(1L, 2L, 3L), Map.of("PROP", "VALUE"),
                null, null, null);

        when(_mockSubjectJobRepo.tryFindById(456))
            .thenReturn(Optional.of(dbJob));

        dbJob.setTimeCompleted(Instant.now());
        _mockMvc.perform(post("/subject/jobs/456/cancel"))
            .andExpect(status().isConflict());


        dbJob.setCancellationState(DbCancellationState.CANCELLED_BY_USER);
        _mockMvc.perform(post("/subject/jobs/456/cancel"))
            .andExpect(status().isOk());

        verifyNoInteractions(_mockSubjectJobRequestService);
    }

    @Test
    public void testCancelJob() throws Exception {
        var dbJob = new DbSubjectJob(
                "ExampleComponent", 8,
                List.of(1L, 2L, 3L), Map.of("PROP", "VALUE"),
                null,
                null,
                null);

        when(_mockSubjectJobRepo.tryFindById(789))
            .thenReturn(Optional.of(dbJob));

        _mockMvc.perform(post("/subject/jobs/789/cancel"))
            .andExpect(status().isAccepted());

        verify(_mockSubjectJobRequestService)
            .cancel(789);
    }


    @Test
    public void testGetOutput() throws Exception {
        _mockMvc.perform(get("/subject/jobs/654/output"))
            .andExpect(status().isNotFound());

        var tracks = Map.of("TRACK_TYPE", List.of("TRACK1", "TRACK2"));
        var entity = new Entity(
            "TEST_ID",
            0.9,
            tracks,
            Map.of("ENTITY_PROP", "ENTITY_VALUE"));

        var relationship = new Relationship(
                List.of("ENTITY_1", "ENTITY_2"),
                List.of(new MediaReference("MEDIA_1", List.of(10L, 11L, 12L))),
                Map.of("RELATIONSHIP_PROP", "RELATIONSHIP_VALUE"));

        var jobResult = new SubjectJobResult(
                createJobDetails(),
                Map.of("JOB_OUT_PROP", "JOB_OUT_VALUE"),
                Map.of("ENTITY_TYPE", List.of(entity)),
                Map.of("PROXIMITY", List.of(relationship)));

        when(_mockSubjectJobRepo.getOutput(654))
            .thenReturn(Optional.of(jobResult));


        var resultActions = _mockMvc.perform(get("/subject/jobs/654/output"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("properties.JOB_OUT_PROP").value("JOB_OUT_VALUE"))
            .andExpect(jsonPath("entities.ENTITY_TYPE[0].id").value("TEST_ID"))
            .andExpect(
                    jsonPath("entities.ENTITY_TYPE[0].properties.ENTITY_PROP")
                    .value("ENTITY_VALUE"))
            .andExpect(jsonPath("entities.ENTITY_TYPE[0].score").value(0.9))
            .andExpect(jsonPath(
                    "entities.ENTITY_TYPE[0].tracks.TRACK_TYPE",
                    containsInAnyOrder("TRACK1", "TRACK2")))

            .andExpect(jsonPath(
                    "relationships.PROXIMITY[0].entities",
                    containsInAnyOrder("ENTITY_1", "ENTITY_2")))
            .andExpect(
                    jsonPath("relationships.PROXIMITY[0].properties.RELATIONSHIP_PROP")
                    .value("RELATIONSHIP_VALUE"))
            .andExpect(
                    jsonPath("relationships.PROXIMITY[0].frames[0].mediaId")
                    .value("MEDIA_1"))
            .andExpect(jsonPath(
                    "relationships.PROXIMITY[0].frames[0].frames",
                    containsInAnyOrder(10, 11, 12)));
        matchJobDetails(resultActions, "job");
    }


    private static SubjectJobDetails createJobDetails() {
        return new SubjectJobDetails(
                123L,
                new SubjectJobRequest(
                        "ExampleComponent",
                        OptionalInt.of(8),
                        List.of(1L, 2L, 3L),
                        Map.of("PROP", "VALUE"),
                        Optional.of(URI.create("http://localhost:1234")),
                        Optional.of(CallbackMethod.POST),
                        Optional.of("EXTERNAL_ID")),
                Instant.now(),
                Optional.empty(),
                false,
                CancellationState.NOT_CANCELLED,
                ImmutableSortedSet.of(),
                ImmutableSortedSet.of("TEST_WARNING"),
                Optional.empty(),
                CallbackStatus.jobRunning());
    }


    private void matchJobDetails(ResultActions resultActions, String root) throws Exception {
        resultActions
            .andExpect(jsonPath("%s.id", root).value(123))
            .andExpect(jsonPath("%s.timeReceived", root).value(isInThePast()))
            .andExpect(jsonPath("%s.timeCompleted", root).value(nullValue()))
            .andExpect(jsonPath("%s.retrievedDetectionJobs", root).value(false))
            .andExpect(
                    jsonPath("%s.cancellationState", root)
                    .value(CancellationState.NOT_CANCELLED.toString()))
            .andExpect(jsonPath("%s.errors", root).isArray())
            .andExpect(jsonPath("%s.errors", root).isEmpty())
            .andExpect(jsonPath("%s.warnings.length()", root).value(1))
            .andExpect(jsonPath("%s.warnings[0]", root).value("TEST_WARNING"))
            .andExpect(jsonPath("%s.callbackStatus", root).value("JOB RUNNING"))

            .andExpect(jsonPath("%s.request.componentName", root).value("ExampleComponent"))
            .andExpect(jsonPath("%s.request.priority", root).value(8))
            .andExpect(
                    jsonPath("%s.request.detectionJobIds", root)
                    .value(containsInAnyOrder(1, 2, 3)))
            .andExpect(jsonPath("%s.request.jobProperties.PROP", root).value("VALUE"))
            .andExpect(jsonPath("%s.request.externalId", root).value("EXTERNAL_ID"))
            .andExpect(jsonPath("%s.request.callbackURL", root).value("http://localhost:1234"))
            .andExpect(jsonPath("%s.request.callbackMethod", root).value("POST"));
    }

    private static MockHttpServletRequestBuilder postJson(String uri, String jsonContent) {
        return post(uri).contentType(MediaType.APPLICATION_JSON).content(jsonContent);
    }

    private static Matcher<String> isInThePast() {
        var delegate = lessThan(Instant.now());
        return new BaseMatcher<>() {
            @Override
            public boolean matches(Object item) {
                return delegate.matches(TimeUtils.toInstant((String) item));
            }

            @Override
            public void describeTo(Description description) {
                delegate.describeTo(description);
            }
        };
    }
}
