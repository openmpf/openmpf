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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mitre.mpf.test.MockRedisConfig;
import org.mitre.mpf.test.SpringTestWithMocks;
import org.mitre.mpf.wfm.camel.WfmProcessorInterface;
import org.mitre.mpf.wfm.camel.WfmSplitterInterface;
import org.mitre.mpf.wfm.camel.operations.mediaretrieval.RemoteMediaProcessor;
import org.mitre.mpf.wfm.camel.operations.mediaretrieval.RemoteMediaSplitter;
import org.mitre.mpf.wfm.data.entities.transients.TransientDetectionSystemProperties;
import org.mitre.mpf.wfm.data.entities.transients.TransientJob;
import org.mitre.mpf.wfm.data.entities.transients.TransientMedia;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@SpringTestWithMocks(MockRedisConfig.class)
@RunWith(SpringJUnit4ClassRunner.class)
public class TestRemoteMediaProcessor {
	private static final Logger log = LoggerFactory.getLogger(TestRemoteMediaProcessor.class);
	private static final int MINUTES = 1000*60; // 1000 milliseconds/second & 60 seconds/minute.
	private static final String EXT_IMG = "https://raw.githubusercontent.com/openmpf/openmpf/master/trunk/mpf-system-tests/src/test/resources/samples/face/meds-aa-S001-01.jpg";

	@Autowired
	private CamelContext camelContext;

	@Autowired
	@Qualifier(RemoteMediaProcessor.REF)
	private WfmProcessorInterface remoteMediaProcessor;

	@Autowired
	@Qualifier(RemoteMediaSplitter.REF)
	private WfmSplitterInterface remoteMediaSplitter;

	@Autowired
	private IoUtils ioUtils;

	@Autowired
	private JsonUtils jsonUtils;

	@Autowired
	private PropertiesUtil propertiesUtil;

	private TransientJob transientJob;

	private static final AtomicInteger SEQUENCE = new AtomicInteger();
	public int next() {
		return SEQUENCE.getAndIncrement();
	}

	@PostConstruct
	public void init() {
		setHttpProxies();

		// Capture a snapshot of the detection system property settings when the job is created.
		TransientDetectionSystemProperties transientDetectionSystemProperties = propertiesUtil.createDetectionSystemPropertiesSnapshot();

		transientJob = new TransientJob(next(), null, transientDetectionSystemProperties, null, 0, 0, false, false) {{
			getMedia().add(new TransientMedia(next(), ioUtils.findFile("/samples/meds1.jpg").toString()));
			getMedia().add(new TransientMedia(next(), EXT_IMG));
		}};
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
				System.setProperty(protocol + ".nonProxyHosts", noProxyHosts);
			}
		}
	}


	@Test(timeout = 5 * MINUTES)
	public void testValidRetrieveRequest() throws Exception {
		log.info("Starting valid image retrieval request.");

		TransientMedia transientMedia = new TransientMedia(next(), EXT_IMG);
		transientMedia.setLocalPath(ioUtils.createTemporaryFile().getAbsolutePath());

		Exchange exchange = new DefaultExchange(camelContext);
		exchange.getIn().getHeaders().put(MpfHeaders.JOB_ID, next());
		exchange.getIn().setBody(jsonUtils.serialize(transientMedia));
		remoteMediaProcessor.process(exchange);

		Object responseBody = exchange.getOut().getBody();
		Assert.assertTrue("A response body must be set.", responseBody != null);
		Assert.assertTrue(String.format("Response body must be a byte[]. Actual: %s.", responseBody.getClass()),  responseBody instanceof byte[]);

		// Implied assertion: Deserialization works.
		TransientMedia responseTransientMedia = jsonUtils.deserialize((byte[])responseBody, TransientMedia.class);

		Assert.assertTrue(String.format("The response entity must not fail. Actual: %s. Message: %s.",
						Boolean.toString(responseTransientMedia.isFailed()),
						responseTransientMedia.getMessage()),
				!responseTransientMedia.isFailed());

		log.info("Remote valid image retrieval request passed.");
	}

	@Test(timeout = 5 * MINUTES)
	public void testInvalidRetrieveRequest() throws Exception {
		log.info("Starting invalid image retrieval request.");

		TransientMedia transientMedia = new TransientMedia(next(), "https://www.mitre.org/"+UUID.randomUUID().toString());
		transientMedia.setLocalPath(ioUtils.createTemporaryFile().getAbsolutePath());

		Exchange exchange = new DefaultExchange(camelContext);
		exchange.getIn().getHeaders().put(MpfHeaders.JOB_ID, next());
		exchange.getIn().setBody(jsonUtils.serialize(transientMedia));
		remoteMediaProcessor.process(exchange);

		Object responseBody = exchange.getOut().getBody();
		Assert.assertTrue("A response body must be set.", responseBody != null);
		Assert.assertTrue(String.format("Response body must be a byte[]. Actual: %s.", responseBody.getClass()),  responseBody instanceof byte[]);

		// Implied assertion: Deserialization works.
		TransientMedia responseTransientMedia = jsonUtils.deserialize((byte[])responseBody, TransientMedia.class);

		Assert.assertTrue(String.format("The response entity must fail. Actual: %s. Message: %s.",
						Boolean.toString(responseTransientMedia.isFailed()),
						responseTransientMedia.getMessage()),
				responseTransientMedia.isFailed());

		log.info("Remote invalid image retrieval request passed.");
	}

	@Test(timeout = 5 * MINUTES)
	public void testSplitRequest() throws Exception {
		Exchange exchange = new DefaultExchange(camelContext);
		exchange.getIn().setHeader(MpfHeaders.JOB_ID, next());
		exchange.getIn().setBody(jsonUtils.serialize(transientJob));

		List<Message> messages = remoteMediaSplitter.split(exchange);

		int targetMessageCount = 1;
		Assert.assertTrue(String.format("The splitter must return %d message. Actual: %d.", targetMessageCount, messages.size()),
				targetMessageCount == messages.size());

		Object messageBody = messages.get(0).getBody();
		Assert.assertTrue("The splitter must assign a body value to the message it created.", messageBody != null);
		Assert.assertTrue("The request body for the message must be a byte[].", messageBody instanceof byte[]);

		TransientMedia transientMedia = jsonUtils.deserialize((byte[])(messageBody), TransientMedia.class);
		Assert.assertTrue("The local path must not begin with 'file:'.", !transientMedia.getLocalPath().startsWith("file:"));
		Assert.assertTrue("The transient file must not be marked as failed.", !transientMedia.isFailed());
	}
}
