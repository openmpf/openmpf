/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2025 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2025 The MITRE Corporation                                       *
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

package org.mitre.mpf.mvc.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.ActionType;
import org.mitre.mpf.rest.api.pipelines.Algorithm;
import org.mitre.mpf.rest.api.pipelines.Pipeline;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJobImpl;
import org.mitre.mpf.wfm.data.entities.persistent.DbSubjectJob;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.mitre.mpf.wfm.data.entities.persistent.MediaImpl;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.JobPart;
import org.mockito.Mock;

import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;


public class TestOutgoingRequestTokenService extends MockitoTest.Strict {

    @Mock
    private AggregateJobPropertiesUtil _mockAggJobProps;

    @Mock
    private ITokenProvider _mockTokenProvider;

    private OutgoingRequestTokenService _tokenService;

    @Before
    public void init() {
        _tokenService = new OutgoingRequestTokenService(
                _mockAggJobProps, Optional.of(_mockTokenProvider));
    }


    @Test
    public void testTokensDisabled() {
        var service = new OutgoingRequestTokenService(_mockAggJobProps, Optional.empty());

        service.addTokenToJobCompleteCallback((BatchJob) null, null);
        service.addTokenToJobCompleteCallback((DbSubjectJob) null, null);
        service.addTokenToTiesDbRequest(null, null, null);
        service.addTokenToTiesDbRequest(null, null);

        var s3RequestParam = SdkHttpFullRequest.builder()
            .uri(URI.create("http://localhost"))
            .method(SdkHttpMethod.GET)
            .build();
        var s3RequestReturn = service.addTokenToS3Request(null, s3RequestParam);
        assertThat(s3RequestParam).isSameAs(s3RequestReturn);

        verifyNoInteractions(_mockAggJobProps);
    }


    @Test
    public void testBatchJobCallbackTokenDisabled() {
        _tokenService.addTokenToJobCompleteCallback(createTestBatchJob(), new HttpGet());
        verifyNoInteractions(_mockTokenProvider);
    }

    @Test
    public void testAddTokenBatchJobCallback() {
        when(_mockAggJobProps.getValue(eq(MpfConstants.CALLBACK_ADD_TOKEN), any(JobPart.class)))
                .thenReturn("true");

        var httpRequest = new HttpGet();
        _tokenService.addTokenToJobCompleteCallback(createTestBatchJob(), httpRequest);

        verify(_mockTokenProvider)
            .addToken(httpRequest);
    }


    @Test
    public void testSubjectJobCallbackTokenDisabled() {
        _tokenService.addTokenToJobCompleteCallback(new DbSubjectJob(), new HttpGet());
        verifyNoInteractions(_mockTokenProvider);
    }

    @Test
    public void testAddTokenSubjectJobCallback() {
        var job = new DbSubjectJob();
        when(_mockAggJobProps.getValue(MpfConstants.CALLBACK_ADD_TOKEN, job))
            .thenReturn(Optional.of("true"));

        var httpRequest = new HttpGet();
        _tokenService.addTokenToJobCompleteCallback(job, httpRequest);

        verify(_mockTokenProvider)
            .addToken(httpRequest);
    }


    @Test
    public void testTiesDbSupplementalTokenDisabled() {
        var media = createTestMedia();
        var job = createTestBatchJob(media);
        _tokenService.addTokenToTiesDbRequest(job, media, new HttpGet());
        verifyNoInteractions(_mockTokenProvider);
    }

    @Test
    public void testAddTokenTiesDbSupplemental() {
        when(_mockAggJobProps.getValue(eq(MpfConstants.TIES_DB_ADD_TOKEN), any(JobPart.class)))
                .thenReturn("true");

        var media = createTestMedia();
        var job = createTestBatchJob(media);
        var httpRequest = new HttpGet();
        _tokenService.addTokenToTiesDbRequest(job, media, httpRequest);

        verify(_mockTokenProvider)
            .addToken(httpRequest);
    }


    @Test
    public void testTiesDbCheckTokenDisabled() {
        _tokenService.addTokenToTiesDbRequest(s -> null, new HttpGet());
        verifyNoInteractions(_mockTokenProvider);
    }

    @Test
    public void testAddTokenTiesDbCheck() {
        UnaryOperator<String> props = s -> {
            if (s.equals(MpfConstants.TIES_DB_ADD_TOKEN)) {
                return "true";
            }
            Assert.fail();
            return "";
        };
        var httpRequest = new HttpGet();
        _tokenService.addTokenToTiesDbRequest(props, httpRequest);

        verify(_mockTokenProvider)
            .addToken(httpRequest);
    }


    @Test
    public void testS3TokenDisabled() {
        var s3RequestParam = SdkHttpFullRequest.builder()
            .uri(URI.create("http://localhost"))
            .method(SdkHttpMethod.GET)
            .build();
        var s3RequestReturn = _tokenService.addTokenToS3Request(s -> null, s3RequestParam);

        assertThat(s3RequestReturn).isSameAs(s3RequestParam);
        verifyNoInteractions(_mockTokenProvider);
    }

    @Test
    public void testAddTokenS3Request() {
        UnaryOperator<String> props = s -> {
            if (s.equals(MpfConstants.S3_ADD_TOKEN)) {
                return "true";
            }
            Assert.fail();
            return "";
        };
        var s3RequestParam = SdkHttpFullRequest.builder()
            .uri(URI.create("http://localhost"))
            .method(SdkHttpMethod.GET)
            .build();
        var s3RequestReturn = _tokenService.addTokenToS3Request(props, s3RequestParam);

        assertThat(s3RequestReturn).isNotSameAs(s3RequestParam);
        verify(_mockTokenProvider)
            .addToken(any(SdkHttpFullRequest.Builder.class));
    }



    private static BatchJob createTestBatchJob() {
        return createTestBatchJob(createTestMedia());
    }

    private static BatchJob createTestBatchJob(MediaImpl media) {
        return new BatchJobImpl(
            182,
            null,
            null,
            createTestPipeline(),
            0,
            null,
            null,
            List.of(media),
            Map.of(),
            Map.of());
    }

    private static MediaImpl createTestMedia() {
        return new MediaImpl(
            183,
            null,
            null,
            null,
            Map.of(),
            Map.of(),
            Set.of(),
            Set.of(),
            List.of(),
            null,
            null);
    }

    private static JobPipelineElements createTestPipeline() {
        var algo = new Algorithm(
                "ALGO1",
                null,
                ActionType.DETECTION,
                null,
                OptionalInt.empty(),
                null,
                null,
                true,
                false);
        var action = new Action("ACTION1", null, algo.name(), List.of());
        var task = new Task("TASK1", null, List.of(action.name()));
        var pipeline = new Pipeline("PIPELINE1", null, List.of(task.name()));

        return new JobPipelineElements(pipeline, List.of(task), List.of(action), List.of(algo));
    }
}
