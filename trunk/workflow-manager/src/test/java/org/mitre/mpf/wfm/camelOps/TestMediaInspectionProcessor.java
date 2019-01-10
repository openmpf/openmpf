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
import org.apache.camel.impl.DefaultExchange;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runner.notification.RunListener;
import org.mitre.mpf.test.MockRedisConfig;
import org.mitre.mpf.wfm.camel.WfmProcessorInterface;
import org.mitre.mpf.wfm.camel.operations.mediainspection.MediaInspectionProcessor;
import org.mitre.mpf.wfm.data.entities.transients.TransientMedia;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.concurrent.atomic.AtomicInteger;

@ContextConfiguration(classes = MockRedisConfig.class)
@RunWith(SpringJUnit4ClassRunner.class)
@RunListener.ThreadSafe
public class TestMediaInspectionProcessor {
	private static final Logger log = LoggerFactory.getLogger(TestMediaInspectionProcessor.class);
	private static final int MINUTES = 1000*60; // 1000 milliseconds/second & 60 seconds/minute.

	@Autowired
	private CamelContext camelContext;

	@Autowired
	@Qualifier(MediaInspectionProcessor.REF)
	private WfmProcessorInterface mediaInspectionProcessor;

    @Autowired
    private IoUtils ioUtils;

    @Autowired
    private JsonUtils jsonUtils;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    public int next() {
        return SEQUENCE.getAndIncrement();
    }


	@Test(timeout = 5 * MINUTES)
	public void testImageInspection() throws Exception {
		log.info("Starting image inspection test.");

		TransientMedia transientMedia = new TransientMedia(next(), ioUtils.findFile("/samples/meds1.jpg").toString());
		Exchange exchange = new DefaultExchange(camelContext);
		exchange.getIn().getHeaders().put(MpfHeaders.JOB_ID, next());
		exchange.getIn().setBody(jsonUtils.serialize(transientMedia));
		mediaInspectionProcessor.process(exchange);

		Object responseBody = exchange.getOut().getBody();
		Assert.assertTrue("A response body must be set.", responseBody != null);
		Assert.assertTrue(String.format("Response body must be a byte[]. Actual: %s.", responseBody.getClass()),  responseBody instanceof byte[]);

		// Implied assertion: Deserialization works.
		TransientMedia responseTransientMedia = jsonUtils.deserialize((byte[])responseBody, TransientMedia.class);

		Assert.assertTrue(String.format("The response entity must not fail. Actual: %s. Message: %s.",
				Boolean.toString(responseTransientMedia.isFailed()),
				responseTransientMedia.getMessage()),
				!responseTransientMedia.isFailed());

		String targetType = "image";
		Assert.assertTrue(String.format("The medium's type should begin with '%s'. Actual: %s.", targetType, responseTransientMedia.getType()),
				StringUtils.startsWithIgnoreCase(responseTransientMedia.getType(), targetType));

		int targetLength = 1;
		Assert.assertTrue(String.format("The medium's length should be %d. Actual: %d.", targetLength, responseTransientMedia.getLength()),
				responseTransientMedia.getLength() == targetLength);

		String targetHash = "c067e7eed23a0fe022140c30dbfa993ae720309d6567a803d111ecec739a6713";//`sha256sum meds1.jpg`
		Assert.assertTrue(String.format("The medium's hash should have matched '%s'. Actual: %s.", targetHash, responseTransientMedia.getSha256()),
				targetHash.equalsIgnoreCase(responseTransientMedia.getSha256()));

		log.info("Image inspection passed.");
	}

	/** Tests that the results from a video file are sane. */
	@Test(timeout = 5 * MINUTES)
	public void testVideoInspection() throws Exception {
		log.info("Starting video inspection test.");

		TransientMedia transientMedia = new TransientMedia(next(), ioUtils.findFile("/samples/video_01.mp4").toString());

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().getHeaders().put(MpfHeaders.JOB_ID, next());
        exchange.getIn().setBody(jsonUtils.serialize(transientMedia));
        mediaInspectionProcessor.process(exchange);

        Object responseBody = exchange.getOut().getBody();
        Assert.assertTrue("A response body must be set.", responseBody != null);
        Assert.assertTrue(String.format("Response body must be a byte[]. Actual: %s.", responseBody.getClass()),  responseBody instanceof byte[]);

        // Implied assertion: Deserialization works.
        TransientMedia responseTransientMedia = jsonUtils.deserialize((byte[])responseBody, TransientMedia.class);

        Assert.assertTrue(String.format("The response entity must not fail. Actual: %s. Message: %s.",
                        Boolean.toString(responseTransientMedia.isFailed()),
                        responseTransientMedia.getMessage()),
                !responseTransientMedia.isFailed());

        String targetType = "video";
        Assert.assertTrue(String.format("The medium's type should begin with '%s'. Actual: %s.", targetType, responseTransientMedia.getType()),
                StringUtils.startsWithIgnoreCase(responseTransientMedia.getType(), targetType));

        int targetLength = 90; //`ffprobe -show_packets video_01.mp4 | grep video | wc -l`
        Assert.assertTrue(String.format("The medium's length should be %d. Actual: %d.", targetLength, responseTransientMedia.getLength()),
                responseTransientMedia.getLength() == targetLength);

        String targetHash = "5eacf0a11d51413300ee0f4719b7ac7b52b47310a49320703c1d2639ebbc9fea"; //`sha256sum video_01.mp4`
        Assert.assertTrue(String.format("The medium's hash should have matched '%s'. Actual: %s.", targetHash, responseTransientMedia.getSha256()),
                targetHash.equalsIgnoreCase(responseTransientMedia.getSha256()));

        log.info("Video inspection passed.");
    }

    /** Tests that the results from a video file are sane. */
    @Test(timeout = 5 * MINUTES)
    public void testVideoInspectionInvalid() throws Exception {
        log.info("Starting invalid video inspection test.");

        TransientMedia transientMedia = new TransientMedia(next(), ioUtils.findFile("/samples/video_01_invalid.mp4").toString());

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().getHeaders().put(MpfHeaders.JOB_ID, next());
        exchange.getIn().setBody(jsonUtils.serialize(transientMedia));
        mediaInspectionProcessor.process(exchange);

        Object responseBody = exchange.getOut().getBody();
        Assert.assertTrue("A response body must be set.", responseBody != null);
        Assert.assertTrue(String.format("Response body must be a byte[]. Actual: %s.", responseBody.getClass()),  responseBody instanceof byte[]);

        // Implied assertion: Deserialization works.
        TransientMedia responseTransientMedia = jsonUtils.deserialize((byte[])responseBody, TransientMedia.class);

        Assert.assertFalse(String.format("The response entity must fail. Actual: %s. Message: %s.",
                        Boolean.toString(responseTransientMedia.isFailed()),
                        responseTransientMedia.getMessage()),
                !responseTransientMedia.isFailed());

        log.info("Media Inspection correctly handled error on invalid video file.");

    }

	/** Tests that the results from an audio file are sane. */
	@Test(timeout = 5 * MINUTES)
	public void testAudioInspection() throws Exception {
		log.info("Starting audio inspection test.");

		// TODO: Implement test.
		TransientMedia transientMedia = new TransientMedia(next(), ioUtils.findFile("/samples/green.wav").toString());

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().getHeaders().put(MpfHeaders.JOB_ID, next());
        exchange.getIn().setBody(jsonUtils.serialize(transientMedia));
        mediaInspectionProcessor.process(exchange);

        Object responseBody = exchange.getOut().getBody();
        Assert.assertTrue("A response body must be set.", responseBody != null);
        Assert.assertTrue(String.format("Response body must be a byte[]. Actual: %s.", responseBody.getClass()),  responseBody instanceof byte[]);

        // Implied assertion: Deserialization works.
        TransientMedia responseTransientMedia = jsonUtils.deserialize((byte[])responseBody, TransientMedia.class);

        Assert.assertTrue(String.format("The response entity must not fail. Actual: %s. Message: %s.",
                        Boolean.toString(responseTransientMedia.isFailed()),
                        responseTransientMedia.getMessage()),
                !responseTransientMedia.isFailed());

        String targetType = "audio";
        Assert.assertTrue(String.format("The medium's type should begin with '%s'. Actual: %s.", targetType, responseTransientMedia.getType()),
                StringUtils.startsWithIgnoreCase(responseTransientMedia.getType(), targetType));

        int targetLength = -1; //`ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 green.wav` - actually produces 2.200000
        Assert.assertTrue(String.format("The medium's length should be %d. Actual: %d.", targetLength, responseTransientMedia.getLength()),
                responseTransientMedia.getLength() == targetLength);

        String targetHash = "237739f8d6ff3459d747f79d272d148d156a696bad93f3ddecc2350c4ee5d9e0"; //`sha256sum green.wav`
        Assert.assertTrue(String.format("The medium's hash should have matched '%s'. Actual: %s.", targetHash, responseTransientMedia.getSha256()),
                targetHash.equalsIgnoreCase(responseTransientMedia.getSha256()));

		log.info("Audio inspection passed.");
	}

	/** Tests that the results from a file which is not accessible is sane. */
	@Test(timeout = 5 * MINUTES)
	public void testInaccessibleFileInspection() throws Exception {
		log.info("Starting inaccessible file inspection test.");

		// TODO: Implement test.
		TransientMedia transientMedia = new TransientMedia(next(), "file:/asdfasfdasdf124124sadfasdfasdf.bin");
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().getHeaders().put(MpfHeaders.JOB_ID, next());
        exchange.getIn().setBody(jsonUtils.serialize(transientMedia));
        mediaInspectionProcessor.process(exchange);

        Object responseBody = exchange.getOut().getBody();
        TransientMedia responseTransientMedia = jsonUtils.deserialize((byte[])responseBody, TransientMedia.class);

        Assert.assertTrue(responseTransientMedia.isFailed());
        Assert.assertNotNull(responseTransientMedia.getMessage());  //failed processing but response handled and message body populated.

		log.info("Inaccessible file inspection passed.");
	}
}
