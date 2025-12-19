package org.mitre.mpf.wfm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.message.BasicStatusLine;
import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.interop.JsonCallbackBody;
import org.mitre.mpf.interop.subject.CallbackMethod;
import org.mitre.mpf.mvc.security.OutgoingRequestTokenService;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.DbSubjectJob;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.AuditEventLogger;
import org.mitre.mpf.wfm.util.HttpClientUtils;
import org.mitre.mpf.wfm.util.ObjectMapperFactory;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.security.util.FieldUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TestJobCompleteCallbackService extends MockitoTest.Strict  {

    @Mock
    private HttpClientUtils _mockHttpClientUtils;

    private final ObjectMapper _objectMapper = ObjectMapperFactory.customObjectMapper();

    @Mock
    private PropertiesUtil _mockPropertiesUtil;

    @Mock
    private AggregateJobPropertiesUtil _mockAggregateJobPropertiesUtil;

    @Mock
    private OutgoingRequestTokenService _mockClientTokenProvider;

    @Mock
    private AuditEventLogger _mockAuditEventLogger;

    @Mock
    private AuditEventLogger.BuilderTagStage _mockBuilderTagStage;

    @Mock
    private AuditEventLogger.AuditEventBuilder _mockAuditEventBuilder;

    private JobCompleteCallbackService _jobCompleteCallbackService;

    
    @Before
    public void init() {
        lenient().when(_mockAuditEventLogger.createEvent())
                .thenReturn(_mockBuilderTagStage);
        lenient().when(_mockBuilderTagStage.withSecurityTag())
                .thenReturn(_mockAuditEventBuilder);
        lenient().when(_mockAuditEventBuilder.withEventId(anyInt()))
                .thenReturn(_mockAuditEventBuilder);
        
        _jobCompleteCallbackService = new JobCompleteCallbackService(
                _mockHttpClientUtils,
                _objectMapper,
                _mockPropertiesUtil,
                _mockAggregateJobPropertiesUtil,
                _mockClientTokenProvider,
                _mockAuditEventLogger);
    }


    @Test
    public void testSendDetectionCallback() throws IOException {
        when(_mockPropertiesUtil.getHttpCallbackRetryCount())
                .thenReturn(10);

        var job = mock(BatchJob.class);
        when(job.getId())
                .thenReturn(321L);
        var callbackUri = URI.create("http://localhost:4321/callback");
        when(job.getCallbackUrl())
                .thenReturn(Optional.of(callbackUri.toString()));
        when(job.getCallbackMethod())
                .thenReturn(Optional.empty());

        when(_mockPropertiesUtil.getExportedJobId(321))
                .thenReturn("host-321");

        var outputObjectUri = URI.create("http://localhost:1234/output-object");

        var requestCaptor = ArgumentCaptor.forClass(HttpPost.class);
        var httpRespFuture = ThreadUtil.<HttpResponse>newFuture();
        when(_mockHttpClientUtils.executeRequest(requestCaptor.capture(), eq(10)))
                .thenReturn(httpRespFuture);

        var resultFuture = _jobCompleteCallbackService.sendCallback(job, outputObjectUri);

        var request = requestCaptor.getValue();

        verify(_mockClientTokenProvider)
            .addTokenToJobCompleteCallback(same(job), same(request));

        assertThat(request.getURI()).isEqualTo(callbackUri);

        assertThat(request.getEntity().getContentType().getValue())
                .isEqualTo("application/json; charset=UTF-8");

        var actualCallbackBody = getBody(request);
        var expectedCallbackBody = new JsonCallbackBody("host-321", null, outputObjectUri.toString());

        assertThat(actualCallbackBody).usingRecursiveComparison()
                .isEqualTo(expectedCallbackBody);

        TestUtil.assertNotDone(resultFuture);

        var response = new BasicHttpResponse(
                new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"));
        httpRespFuture.complete(response);

        assertThat(resultFuture).succeedsWithin(TestUtil.FUTURE_DURATION);
    }


    @Test
    public void testSendSubjectJob() {
        var httpRespFuture = ThreadUtil.<HttpResponse>newFuture();
        var resultFuture = setupSubjectJob(httpRespFuture);

        var response = new BasicHttpResponse(
                new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"));
        httpRespFuture.complete(response);

        assertThat(resultFuture).succeedsWithin(TestUtil.FUTURE_DURATION);
    }


    @Test
    public void testSendSubjectJobWithOidc() {
        var httpRespFuture = ThreadUtil.<HttpResponse>newFuture();
        var resultFuture = setupSubjectJob(httpRespFuture);

        var response = new BasicHttpResponse(
                new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"));
        httpRespFuture.complete(response);

        assertThat(resultFuture).succeedsWithin(TestUtil.FUTURE_DURATION);

        verify(_mockClientTokenProvider)
                .addTokenToJobCompleteCallback(
                    any(DbSubjectJob.class),
                    any(HttpUriRequest.class));
    }


    @Test
    public void testErrorResponse() {
        var httpRespFuture = ThreadUtil.<HttpResponse>newFuture();
        var resultFuture = setupSubjectJob(httpRespFuture);

        var response = new BasicHttpResponse(
                new BasicStatusLine(HttpVersion.HTTP_1_1, 500, ""));
        httpRespFuture.complete(response);

        assertThatThrownBy(resultFuture::join)
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-200 status code");
    }


    private CompletableFuture<HttpResponse> setupSubjectJob(CompletableFuture<HttpResponse> httpRespFuture) {
        when(_mockPropertiesUtil.getHttpCallbackRetryCount())
                .thenReturn(10);

        var callbackUri = URI.create("http://localhost:4321/callback");
        var job = new DbSubjectJob(
            null,
            0,
            List.of(1L),
            Map.of(),
            callbackUri,
            CallbackMethod.GET,
            "EXT_ID");
        FieldUtils.setProtectedFieldValue("id", job, 456);
        var outputUri = URI.create("http://localhost:12345/subject-out");
        job.setOutputUri(outputUri);

        when(_mockPropertiesUtil.getExportedJobId(456))
                .thenReturn("host-456");

        var requestCaptor = ArgumentCaptor.forClass(HttpGet.class);
        when(_mockHttpClientUtils.executeRequest(requestCaptor.capture(), eq(10)))
                .thenReturn(httpRespFuture);

        var respFuture = _jobCompleteCallbackService.sendCallback(job);

        var request = requestCaptor.getValue();
        var requestUriComponents = URLEncodedUtils.parse(request.getURI(), StandardCharsets.UTF_8);
        assertThat(requestUriComponents).containsAll(List.of(
            new BasicNameValuePair("externalid", "EXT_ID"),
            new BasicNameValuePair("jobid", "host-456"),
            new BasicNameValuePair("outputobjecturi", outputUri.toString())));

        TestUtil.assertNotDone(respFuture);

        return respFuture;
    }

    private JsonCallbackBody getBody(HttpPost request) throws IOException {
        try (var inStream = request.getEntity().getContent()) {
            return _objectMapper.readValue(inStream, JsonCallbackBody.class);
        }
    }

    @Test
    public void throwsWhenNoCallbackUriInDetectionJob() {
        var job = mock(BatchJob.class);
        when(job.getCallbackUrl())
            .thenReturn(Optional.empty());

        var testUri = URI.create("http://localhost:1234");
        assertThatThrownBy(() -> _jobCompleteCallbackService.sendCallback(job, testUri))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Job did not have a callback URL.");
    }

    @Test
    public void throwsWhenNoCallbackUriInSubjectJob() {
        var job = mock(DbSubjectJob.class);
        when(job.getCallbackUri())
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> _jobCompleteCallbackService.sendCallback(job))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Job did not have a callback URL.");
    }
}
