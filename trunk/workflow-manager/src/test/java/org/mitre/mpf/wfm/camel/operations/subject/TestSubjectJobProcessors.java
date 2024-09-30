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

package org.mitre.mpf.wfm.camel.operations.subject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.buffers.SubjectProtobuf;
import org.mitre.mpf.wfm.data.access.SubjectJobRepo;
import org.mitre.mpf.wfm.data.entities.persistent.DbSubjectJob;
import org.mitre.mpf.wfm.service.SubjectJobResultsService;
import org.mockito.InjectMocks;
import org.mockito.Mock;


public class TestSubjectJobProcessors extends MockitoTest.Strict {

    @Mock
    private SubjectJobRepo _mockSubjectJobRepo;

    @Mock
    private SubjectJobResultsService _mockSubjectJobResultsService;

    @InjectMocks
    private SubjectJobProcessors _subjectJobProcessors;


    @Test
    public void testInitExchange() {
        var exchange = TestUtil.createTestExchange();
        var pbJob = SubjectProtobuf.SubjectTrackingJob.newBuilder()
                .setJobId(123)
                .build();
        SubjectJobProcessors.initExchange(pbJob, exchange);

        assertThat(exchange.getProperty("mpfId")).isEqualTo(123L);
        assertThat(exchange.getIn().getHeader("JobId")).isEqualTo(123L);
        assertThat(exchange.getIn().getBody()).isSameAs(pbJob);
        assertThat(exchange.hasOut()).isFalse();
    }

    @Test
    public void newJobProcessorCanHandleMissingJob() throws Exception {
        var exchange = TestUtil.createTestExchange();
        var pbJob = SubjectProtobuf.SubjectTrackingJob.newBuilder()
                .setJobId(123)
                .build();
        SubjectJobProcessors.initExchange(pbJob, exchange);

        var exception = new WfmProcessingException("job missing");
        when(_mockSubjectJobRepo.findById(123))
                .thenThrow(exception);

        _subjectJobProcessors.getNewJobRequestProcessor().process(exchange);

        verify(_mockSubjectJobResultsService)
                .completeWithError(
                        eq(123L),
                        contains("sending job to queue"),
                        same(exception));
        assertThat(exchange.getProperty("CamelRouteStop")).isEqualTo(true);
    }

    @Test
    public void testNewJobProcessor() throws Exception {
        var exchange = TestUtil.createTestExchange();
        var pbJob = SubjectProtobuf.SubjectTrackingJob.newBuilder()
                .setJobId(123)
                .build();
        SubjectJobProcessors.initExchange(pbJob, exchange);

        var dbJob = new DbSubjectJob(
                "TEST_COMPONENT", 3, List.of(7L, 8L), Map.of(),
                null, null, null);

        when(_mockSubjectJobRepo.findById(123))
            .thenReturn(dbJob);

        _subjectJobProcessors.getNewJobRequestProcessor().process(exchange);

        var message = exchange.getIn();
        assertThat(message.getHeader("JMSPriority")).isEqualTo(3);
        assertThat(message.getHeader("CamelJmsDestinationName"))
            .isEqualTo("MPF.SUBJECT_TEST_COMPONENT_REQUEST");
    }


    @Test
    public void testMessageMissingJobId() {
        var processors = List.of(
                _subjectJobProcessors.getNewJobRequestProcessor(),
                _subjectJobProcessors.getJobResponseProcessor(),
                _subjectJobProcessors.getCancellationProcessor());
        for (var processor : processors) {
            var exchange = TestUtil.createTestExchange();
            assertThatThrownBy(() -> processor.process(exchange))
                .isInstanceOf(WfmProcessingException.class)
                .hasMessageContaining("missing a job id");
        }
        verifyNoInteractions(_mockSubjectJobResultsService);
    }


    @Test
    public void testResponseProcessor() throws Exception {
        var exchange = TestUtil.createTestExchange();
        exchange.setProperty("mpfId", 123L);
        var pbResult = SubjectProtobuf.SubjectTrackingResult.newBuilder().build();
        exchange.getIn().setBody(pbResult);

        _subjectJobProcessors.getJobResponseProcessor().process(exchange);

        verify(_mockSubjectJobResultsService)
            .completeJob(eq(123L), same(pbResult));
    }


    @Test
    public void testCancellationProcessor() throws Exception {
        var exchange = TestUtil.createTestExchange();
        exchange.setProperty("mpfId", 123L);

        _subjectJobProcessors.getCancellationProcessor().process(exchange);
        verify(_mockSubjectJobResultsService)
                .cancel(123L);
    }


    @Test
    public void errorProcessorHandlesMissingJobId() {
        var exchange = TestUtil.createTestExchange();
        var cause = new IllegalStateException("<test-error>");
        exchange.setProperty("CamelExceptionCaught", cause);

        var processor = _subjectJobProcessors.getErrorProcessor();
        assertThatThrownBy(() -> processor.process(exchange))
            .isInstanceOf(WfmProcessingException.class)
            .hasMessageContaining("Unable to handle error because the job id was not set.")
            .hasCauseReference(cause);
    }


    @Test
    public void testErrorProcessor() throws Exception {
        var exchange = TestUtil.createTestExchange();
        exchange.setProperty("mpfId", 123L);
        var cause = new IllegalStateException("<test-error>");
        exchange.setProperty("CamelExceptionCaught", cause);

        _subjectJobProcessors.getErrorProcessor().process(exchange);
        verify(_mockSubjectJobResultsService).completeWithError(
                eq(123L),
                contains("exception occurred during a Camel route"),
                same(cause));
    }
}
