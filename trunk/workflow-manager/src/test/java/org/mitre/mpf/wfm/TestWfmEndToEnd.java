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

package org.mitre.mpf.wfm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.rest.api.JobCreationMediaData;
import org.mitre.mpf.rest.api.JobCreationRequest;
import org.mitre.mpf.rest.api.MediaUri;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.businessrules.JobRequestService;
import org.mitre.mpf.wfm.camel.JobCompleteProcessor;
import org.mitre.mpf.wfm.camel.JobCompleteProcessorImpl;
import org.mitre.mpf.wfm.camel.routes.DetectionResponseRouteBuilder;
import org.mitre.mpf.wfm.data.access.JobRequestDao;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.MpfEndpoints;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.event.JobCompleteNotification;
import org.mitre.mpf.wfm.event.NotificationConsumer;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;


@WebAppConfiguration
@ContextConfiguration(locations = {"classpath:applicationContext.xml"})
@RunWith(SpringJUnit4ClassRunner.class)
@ActiveProfiles("jenkins")
public class TestWfmEndToEnd {

	@Autowired
	private CamelContext camelContext;

	@Autowired
	private IoUtils ioUtils;

	@Autowired
	private JobRequestService jobRequestService;

	@Autowired
	private JobRequestDao jobRequestDao;

	@Autowired
	private PropertiesUtil propertiesUtil;


	@Autowired
	@Qualifier(JobCompleteProcessorImpl.REF)
	private JobCompleteProcessor jobCompleteProcessor;

	@Autowired
	private ObjectMapper objectMapper;

	protected static final int MINUTES = 1000 * 60; // 1000 milliseconds/second & 60 seconds/minute.

	protected static final Logger log = LoggerFactory.getLogger(TestWfmEndToEnd.class);


	private static boolean hasInitialized = false;
	private static int testCtr = 0;
	private static Set<Long> completedJobs = new HashSet<>();
	private static final Object lock = new Object();

	@PostConstruct
	private void init() {
		synchronized (lock) {
			if (!hasInitialized) {
				completedJobs = new HashSet<>();
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

	private static List<JobCreationMediaData> toMediaObjectList(URI... uris) {
		List<JobCreationMediaData> media = new ArrayList<>(uris.length);
		for (URI uri : uris) {
			media.add(new JobCreationMediaData(
                    new MediaUri(uri),
                    Map.of(),
                    Map.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    Optional.empty()));
		}
		return media;
	}


	private static boolean waitFor(long jobRequestId) {
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


	private long runPipelineOnMedia(String pipelineName, List<JobCreationMediaData> media,
	                                Map<String, String> jobProperties, boolean buildOutput, int priority) {

		var jobRequest = new JobCreationRequest(
                List.copyOf(media),
                Map.copyOf(jobProperties),
                Map.of(),
                UUID.randomUUID().toString(),
                pipelineName,
                null,
                buildOutput,
                priority,
                null,
                null);

		long jobRequestId = jobRequestService.run(jobRequest).jobId();
		Assert.assertTrue(waitFor(jobRequestId));
		return jobRequestId;
	}


	@Test(timeout = 5 * MINUTES)
	public void testResubmission() throws Exception {
		testCtr++;
		log.info("Beginning test #{} testResubmission()", testCtr);
		List<JobCreationMediaData> media = toMediaObjectList(ioUtils.findFile("/samples/meds/aa/S001-01-t10_01.jpg"));

		long jobId = runPipelineOnMedia("OCV FACE DETECTION (WITH MARKUP) PIPELINE", media,
		                                Collections.emptyMap(), true, 5);

		JobRequest jobRequest = jobRequestDao.findById(jobId);

		Assert.assertEquals(BatchJobStatusType.COMPLETE, jobRequest.getStatus());
		Assert.assertNotNull(jobRequest.getOutputObjectPath());

		Path outputObjectPath = IoUtils.toLocalPath(jobRequest.getOutputObjectPath()).orElse(null);
		Assert.assertNotNull(outputObjectPath);
		Assert.assertTrue(Files.exists(outputObjectPath));

		JsonOutputObject jsonOutputObject = objectMapper.readValue(outputObjectPath.toFile(), JsonOutputObject.class);
		long internalJobId = propertiesUtil.getJobIdFromExportedId(jsonOutputObject.getJobId());
		Assert.assertEquals(internalJobId, jobId);
		String exportedJobId = propertiesUtil.getExportedJobId(jobId);
		Assert.assertEquals(exportedJobId, jsonOutputObject.getJobId());
		Instant start = jsonOutputObject.getTimeStart(),
				stop = jsonOutputObject.getTimeStop();

		completedJobs.clear();

		// Ensure that there is at least some pause between jobs so that the start and stop times can be meaningfully
		// compared to ensure that results are not erroneously being duplicated.
		Thread.sleep(2000);
		jobRequestService.resubmit(jobId, 5);
		Assert.assertTrue(waitFor(jobId));

		jobRequest = jobRequestDao.findById(jobId);

		Assert.assertEquals(BatchJobStatusType.COMPLETE, jobRequest.getStatus());
		Assert.assertNotNull(jobRequest.getOutputObjectPath());

		outputObjectPath = IoUtils.toLocalPath(jobRequest.getOutputObjectPath()).orElse(null);
		Assert.assertNotNull(outputObjectPath);
		Assert.assertTrue(Files.exists(outputObjectPath));

		jsonOutputObject = objectMapper.readValue(outputObjectPath.toFile(), JsonOutputObject.class);
		internalJobId = propertiesUtil.getJobIdFromExportedId(jsonOutputObject.getJobId());
		Assert.assertEquals(internalJobId, jobId);
		exportedJobId = propertiesUtil.getExportedJobId(jobId);
		Assert.assertEquals(exportedJobId, jsonOutputObject.getJobId());
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

		camelContext.createProducerTemplate().sendBodyAndHeader(
                DetectionResponseRouteBuilder.ENTRY_POINT,
                ExchangePattern.InOnly,
                targetResponse.toByteArray(),
                MpfHeaders.JOB_ID,
                jobId);
		Exchange exchange = camelContext.createConsumerTemplate().receive(MpfEndpoints.UNSOLICITED_MESSAGES + "?selector=" + MpfHeaders.JOB_ID + "%3D" + jobId, 10000);
		Assert.assertNotNull("The unsolicited response was not properly detected.", exchange);
		DetectionProtobuf.DetectionResponse receivedResponse = DetectionProtobuf.DetectionResponse.parseFrom(exchange.getIn().getBody(byte[].class));
		Assert.assertEquals(targetResponse.getMediaId(), receivedResponse.getMediaId());
		log.info("Finished testUnsolicitedResponse()");
	}

	private static DetectionProtobuf.DetectionResponse createUnsolicitedResponse(long id) {
		return DetectionProtobuf.DetectionResponse.newBuilder()
				.setTaskIndex(0)
				.setActionIndex(0)
				.setError(DetectionProtobuf.DetectionError.BAD_FRAME_SIZE)
				.setVideoResponse(DetectionProtobuf.DetectionResponse.VideoResponse.newBuilder()
						.setStartFrame(0)
						.setStopFrame(100))
				.setMediaId(id)
				.build();
	}
}
