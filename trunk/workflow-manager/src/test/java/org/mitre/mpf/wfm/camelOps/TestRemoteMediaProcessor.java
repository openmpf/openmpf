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
import static org.junit.Assert.assertTrue;
import static org.mitre.mpf.test.TestUtil.nonBlank;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultMessage;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.camel.operations.mediaretrieval.RemoteMediaProcessor;
import org.mitre.mpf.wfm.camel.operations.mediaretrieval.RemoteMediaSplitter;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.MediaImpl;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.service.WorkflowPropertyService;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;

import spark.Spark;

public class TestRemoteMediaProcessor extends MockitoTest.Lenient {
    private static final Logger LOG = LoggerFactory.getLogger(TestRemoteMediaProcessor.class);
    private static final int MINUTES = 1000*60; // 1000 milliseconds/second & 60 seconds/minute.
    private static final String EXT_IMG = "http://localhost:4587/test-image.jpg";

    private RemoteMediaProcessor _remoteMediaProcessor;

    @InjectMocks
    private RemoteMediaSplitter _remoteMediaSplitter;

    @Mock
    private InProgressBatchJobsService _mockInProgressJobs;

    @Mock
    private PropertiesUtil _mockPropertiesUtil;

    @Rule
    public TemporaryFolder _tempFolder = new TemporaryFolder();


    @BeforeClass
    public static void initClass() {
        setHttpProxies();
        startSpark();
    }

    @AfterClass
    public static void tearDownClass() {
        Spark.stop();
    }

    private static void setHttpProxies() {
        // When running the tests through Maven, the system properties set in the "JAVA_OPTS" environment variable
        // appear to be ignored.
        for (String protocol : new String[] { "http", "https" }) {
            boolean proxyAlreadySet = System.getProperty(protocol + ".proxyHost") != null;
            if (proxyAlreadySet) {
                continue;
            }
            String envHttpProxy = System.getenv(protocol + "_proxy");
            if (envHttpProxy != null) {
                URI proxyUri = URI.create(envHttpProxy);
                System.setProperty(protocol + ".proxyHost", proxyUri.getHost());
                System.setProperty(protocol + ".proxyPort", String.valueOf(proxyUri.getPort()));
            }

            String noProxyHosts = System.getenv("no_proxy");
            if (noProxyHosts != null) {
                String javaPropertyFormattedNoProxy = noProxyHosts.replace(',', '|');
                System.setProperty(protocol + ".nonProxyHosts", javaPropertyFormattedNoProxy);
            }
        }
    }

    @Before
    public void init() {
        when(_mockPropertiesUtil.getRemoteMediaDownloadRetries())
                .thenReturn(3);

        when(_mockPropertiesUtil.getRemoteMediaDownloadSleep())
                .thenReturn(200);

        _remoteMediaProcessor = new RemoteMediaProcessor(
                _mockInProgressJobs, null, _mockPropertiesUtil,
                new AggregateJobPropertiesUtil(_mockPropertiesUtil, mock(WorkflowPropertyService.class)));
    }


    @Test(timeout = 5 * MINUTES)
    public void testValidRetrieveRequest() throws Exception {
        LOG.info("Starting valid image retrieval request.");
        long jobId = 123;
        long mediaId = 456;

        MediaImpl media = new MediaImpl(
                mediaId, EXT_IMG, UriScheme.get(URI.create(EXT_IMG)), _tempFolder.newFile().toPath(),
                Map.of(), Map.of(), List.of(), List.of(), null);

        Exchange exchange = setupExchange(jobId, media);
        _remoteMediaProcessor.process(exchange);

        assertEquals("Media ID headers must be set.", mediaId, exchange.getOut().getHeader(MpfHeaders.MEDIA_ID));
        assertEquals("Job ID headers must be set.", jobId, exchange.getOut().getHeader(MpfHeaders.JOB_ID));

        Assert.assertFalse(String.format("The response entity must not fail. Actual: %s. Message: %s.",
                        Boolean.toString(media.isFailed()),
                                        media.getErrorMessage()),
                           media.isFailed());
        LOG.info("Remote valid image retrieval request passed.");
    }


    @Test(timeout = 5 * MINUTES)
    public void testInvalidRetrieveRequest() throws Exception {
        LOG.info("Starting invalid image retrieval request.");
        long jobId = 789;
        long mediaId = 321;

        MediaImpl media = new MediaImpl(
                mediaId, "https://www.mitre.org/" + UUID.randomUUID(), UriScheme.HTTPS,
                _tempFolder.newFile().toPath(), Map.of(), Map.of(), List.of(), List.of(), null);

        Exchange exchange = setupExchange(jobId, media);
        _remoteMediaProcessor.process(exchange);

        assertEquals("Media ID headers must be set.", mediaId, exchange.getOut().getHeader(MpfHeaders.MEDIA_ID));
        assertEquals("Job ID headers must be set.", jobId, exchange.getOut().getHeader(MpfHeaders.JOB_ID));
        assertTrue(media.isFailed());

        verify(_mockInProgressJobs)
                .addError(eq(jobId), eq(mediaId), eq(IssueCodes.REMOTE_STORAGE_DOWNLOAD), nonBlank());

        LOG.info("Remote invalid image retrieval request passed.");
    }



    @Test(timeout = 5 * MINUTES)
    public void testSplitRequest() throws Exception {
        long mediaId1 = 634;
        long mediaId2 = 458;
        ImmutableCollection<MediaImpl> media = ImmutableList.of(
                new MediaImpl(mediaId1, "/some/local/path.jpg", UriScheme.FILE,
                              Paths.get("/some/local/path.jpg"), Map.of(), Map.of(), List.of(),
                              List.of(), null),
                new MediaImpl(mediaId2, EXT_IMG, UriScheme.get(URI.create(EXT_IMG)),
                              _tempFolder.newFile().toPath(), Map.of(), Map.of(), List.of(),
                              List.of(), null));

        var job = mock(BatchJob.class);
        when(job.isCancelled())
                .thenReturn(false);
        when(job.getMedia())
                .thenAnswer(i -> media);

        long jobId = 4353;
        when(_mockInProgressJobs.getJob(jobId))
                .thenReturn(job);

        var context = new DefaultCamelContext();
        var inMessage = new DefaultMessage(context);
        inMessage.setHeader(MpfHeaders.JOB_ID, jobId);

        var exchange = new DefaultExchange(context);
        exchange.setIn(inMessage);

        List<Message> messages = _remoteMediaSplitter.split(exchange);

        int targetMessageCount = 1;
        assertEquals(String.format("The splitter must return %d message. Actual: %d.",
                                   targetMessageCount,
                                   messages.size()), targetMessageCount, messages.size());
        assertEquals(mediaId2, (long) messages.get(0).getHeader(MpfHeaders.MEDIA_ID, Long.class));
    }


    private Exchange setupExchange(long jobId, MediaImpl media) {
        return MediaTestUtil.setupExchange(jobId, media, _mockInProgressJobs);
    }


    private static void startSpark() {
        Spark.port(4587);
        Spark.get("/test-image.jpg", (req, resp) -> {
            var path = Paths.get(TestUtil.findFile("/samples/meds1.jpg"));
            try (var out = resp.raw().getOutputStream()) {
                Files.copy(path, out);
            }
            resp.raw().flushBuffer();
            return "";
        });
    }
}
