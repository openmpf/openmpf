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

package org.mitre.mpf.wfm;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.PostConstruct;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mitre.mpf.interop.JsonJobRequest;
import org.mitre.mpf.interop.JsonMediaInputObject;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.interop.JsonPipeline;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.businessrules.JobRequestBo;
import org.mitre.mpf.wfm.businessrules.impl.JobRequestBoImpl;
import org.mitre.mpf.wfm.camel.JobCompleteProcessor;
import org.mitre.mpf.wfm.camel.JobCompleteProcessorImpl;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.MpfEndpoints;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.event.JobCompleteNotification;
import org.mitre.mpf.wfm.event.NotificationConsumer;
import org.mitre.mpf.wfm.service.MpfService;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;


@ContextConfiguration(locations = {"classpath:applicationContext.xml"})
@RunWith(SpringJUnit4ClassRunner.class)
public class TestWfmEndToEnd {

	@Autowired
	private CamelContext camelContext;

	@Autowired
	@Qualifier(IoUtils.REF)
	protected IoUtils ioUtils;

	@Autowired
	protected PropertiesUtil propertiesUtil;

	@Autowired
	protected ApplicationContext context;

	@Autowired
	@Qualifier(JobRequestBoImpl.REF)
	protected JobRequestBo jobRequestBo;

	@Autowired
	private MpfService mpfService;

	@Autowired
	@Qualifier(JobCompleteProcessorImpl.REF)
	private JobCompleteProcessor jobCompleteProcessor;

	protected static final int MINUTES = 1000 * 60; // 1000 milliseconds/second & 60 seconds/minute.

	protected static final Logger log = LoggerFactory.getLogger(TestWfmEndToEnd.class);

	protected static final ObjectMapper objectMapper = new ObjectMapper();

	protected static boolean hasInitialized = false;
	protected static int testCtr = 0;
	protected static Set<Long> completedJobs = new HashSet<>();
	protected static Object lock = new Object();

	@PostConstruct
	private void init() throws Exception {
		synchronized (lock) {
			if (!hasInitialized) {
				completedJobs = new HashSet<Long>();
				jobCompleteProcessor.subscribe(new NotificationConsumer<JobCompleteNotification>() {
					@Override
					public void onNotification(Object source, JobCompleteNotification notification) {
						log.info("JobCompleteProcessorSubscriber: Source={}, Notification={}", source, notification);
						synchronized (lock) {
							completedJobs.add(notification.getJobId());
							lock.notifyAll();
						}
						log.info("JobCompleteProcessorSubscriber COMPLETE");
					}
				});

				log.info("Starting the tests from _setupContext");
				hasInitialized = true;
			}
		}
	}

	protected List<JsonMediaInputObject> toMediaObjectList(URI... uris) {
		List<JsonMediaInputObject> media = new ArrayList<>(uris.length);
		for (URI uri : uris) {
			media.add(new JsonMediaInputObject(uri.toString()));
		}
		return media;
	}

	protected long runPipelineOnMedia(JsonPipeline jsonPipeline, final List<JsonMediaInputObject> media, boolean buildOutput, int priority) throws Exception {
		JsonJobRequest jsonJobRequest = new JsonJobRequest(UUID.randomUUID().toString(), buildOutput, jsonPipeline, priority) {{
			getMedia().addAll(media);
		}};

		long jobRequestId = mpfService.submitJob(jsonJobRequest);
		Assert.assertTrue(waitFor(jobRequestId));
		return jobRequestId;
	}

	public boolean waitFor(long jobRequestId) {
		synchronized (lock) {
			while (!completedJobs.contains(jobRequestId)) {
				try {
					lock.wait();
				} catch (Exception exception) {
					log.warn("Exception occurred while waiting. Assuming that the job has completed (but failed)", exception);
					completedJobs.add(jobRequestId);
					return false;
				}
				log.info("Woken up. Checking to see if {} has completed", jobRequestId);
			}
			log.info("{} has completed!", jobRequestId);
			return true;
		}
	}

	// is this running on Jenkins and/or is output checking desired?
	private static boolean jenkins = false;

	static {
		String prop = System.getProperty("jenkins");
		if (prop != null) {
			jenkins = Boolean.valueOf(prop);
		}
	}

	protected long runPipelineOnMedia(String pipelineName, List<JsonMediaInputObject> media, Map<String,String> jobProperties, boolean buildOutput, int priority) throws Exception {
		JsonJobRequest jsonJobRequest = jobRequestBo.createRequest(UUID.randomUUID().toString(), pipelineName, media,
				Collections.emptyMap(), jobProperties, buildOutput, priority);
		long jobRequestId = mpfService.submitJob(jsonJobRequest);
		Assert.assertTrue(waitFor(jobRequestId));
		return jobRequestId;
	}

	protected long runPipelineOnMediaAndCancelAfter(String pipelineName, List<JsonMediaInputObject> media, Map<String,String> jobProperties, boolean buildOutput, int priority, int timeout) throws Exception {
		JsonJobRequest jsonJobRequest = jobRequestBo.createRequest(UUID.randomUUID().toString(), pipelineName, media,
				Collections.emptyMap(), jobProperties, buildOutput, priority);
		long jobRequestId = mpfService.submitJob(jsonJobRequest);
		Thread.sleep(timeout);
		mpfService.cancel(jobRequestId);
		Assert.assertTrue(waitFor(jobRequestId));
		return jobRequestId;
	}

	@Test(timeout = 5 * MINUTES)
	public void testResubmission() throws Exception {
		testCtr++;
		log.info("Beginning test #{} testResubmission()", testCtr);
		List<JsonMediaInputObject> media = toMediaObjectList(ioUtils.findFile("/samples/meds/aa/S001-01-t10_01.jpg"));

		long jobId = runPipelineOnMedia("OCV FACE DETECTION (WITH MARKUP) PIPELINE", media, Collections.emptyMap(), true, 5);

		JobRequest jobRequest = mpfService.getJobRequest(jobId);

		Assert.assertTrue(jobRequest.getStatus() == BatchJobStatusType.COMPLETE);
		Assert.assertTrue(jobRequest.getOutputObjectPath() != null);
		Assert.assertTrue(new File(jobRequest.getOutputObjectPath()).exists());

		JsonOutputObject jsonOutputObject = objectMapper.readValue(new File(jobRequest.getOutputObjectPath()), JsonOutputObject.class);
		Assert.assertEquals(jsonOutputObject.getJobId(), jobId);
		String start = jsonOutputObject.getTimeStart(),
				stop = jsonOutputObject.getTimeStop();

		completedJobs.clear();

		// Ensure that there is at least some pause between jobs so that the start and stop times can be meaningfully
		// compared to ensure that results are not erroneously being duplicated.
		Thread.sleep(2000);
		mpfService.resubmitJob(jobId);
		Assert.assertTrue(waitFor(jobId));

		jobRequest = mpfService.getJobRequest(jobId);

		Assert.assertTrue(jobRequest.getStatus() == BatchJobStatusType.COMPLETE);
		Assert.assertTrue(jobRequest.getOutputObjectPath() != null);
		Assert.assertTrue(new File(jobRequest.getOutputObjectPath()).exists());

		jsonOutputObject = objectMapper.readValue(new File(jobRequest.getOutputObjectPath()), JsonOutputObject.class);
		Assert.assertEquals(jsonOutputObject.getJobId(), jobId);
		Assert.assertNotEquals(jsonOutputObject.getTimeStart(), start);
		Assert.assertNotEquals(jsonOutputObject.getTimeStop(), stop);

		log.info("Finished testResubmission()");
	}

	@Test(timeout = 5 * MINUTES)
	public void testUnsolicitedResponse() throws Exception {
		testCtr++;
		log.info("Beginning test #{} testUnsolicitedResponse()", testCtr);

		// Assumption: We never generate job ids less than 0.
		long jobId = -testCtr;
		DetectionProtobuf.DetectionResponse targetResponse = createUnsolicitedResponse(jobId);

		camelContext.createProducerTemplate().sendBodyAndHeader(MpfEndpoints.COMPLETED_DETECTIONS, ExchangePattern.InOnly, targetResponse.toByteArray(), MpfHeaders.JOB_ID, jobId);
		Exchange exchange = camelContext.createConsumerTemplate().receive(MpfEndpoints.UNSOLICITED_MESSAGES + "?selector=" + MpfHeaders.JOB_ID + "%3D" + jobId, 10000);
		Assert.assertTrue("The unsolicited response was not properly detected.", exchange != null);
		DetectionProtobuf.DetectionResponse receivedResponse = DetectionProtobuf.DetectionResponse.parseFrom(exchange.getIn().getBody(byte[].class));
		Assert.assertEquals(targetResponse.getMediaId(), receivedResponse.getMediaId());
		Assert.assertEquals(targetResponse.getRequestId(), receivedResponse.getRequestId());
		log.info("Finished testUnsolicitedResponse()");
	}

	private DetectionProtobuf.DetectionResponse createUnsolicitedResponse(long id) {
		return DetectionProtobuf.DetectionResponse.newBuilder()
				.setStageIndex(0)
				.setActionIndex(0)
				.setDataType(DetectionProtobuf.DetectionResponse.DataType.IMAGE)
				.setError(DetectionProtobuf.DetectionError.BAD_FRAME_SIZE)
				.setStartIndex(0)
				.setStopIndex(100)
				.setMediaId(id)
				.setRequestId(60)
				.build();
	}
}
