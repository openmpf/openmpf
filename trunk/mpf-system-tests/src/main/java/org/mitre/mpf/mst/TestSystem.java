
/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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

package org.mitre.mpf.mst;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mitre.mpf.interop.JsonJobRequest;
import org.mitre.mpf.interop.JsonMediaInputObject;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.wfm.businessrules.JobRequestBo;
import org.mitre.mpf.wfm.businessrules.impl.JobRequestBoImpl;
import org.mitre.mpf.wfm.camel.JobCompleteProcessor;
import org.mitre.mpf.wfm.camel.JobCompleteProcessorImpl;
import org.mitre.mpf.wfm.event.JobCompleteNotification;
import org.mitre.mpf.wfm.event.NotificationConsumer;
import org.mitre.mpf.wfm.service.MpfService;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.*;

@ContextConfiguration(locations = {"classpath:applicationContext.xml"})
@ComponentScan({"org.mitre.mpf"})
@Configuration
@RunWith(SpringJUnit4ClassRunner.class)
@ActiveProfiles("jenkins")
public abstract class TestSystem {
    protected static final Logger log = LoggerFactory.getLogger(TestSystem.class);
    protected static int testCtr = 0;
	protected static Set<Long> completedJobs = new HashSet<>();
	protected static Object lock = new Object();


    @Autowired
    @Qualifier(IoUtils.REF)
    protected IoUtils ioUtils;

    @Autowired
    @Qualifier(PropertiesUtil.REF)
    protected PropertiesUtil propertiesUtil;

    @Autowired
    @Qualifier(OutputChecker.REF)
    protected OutputChecker outputChecker;

	@Autowired
	@Qualifier(JobRequestBoImpl.REF)
	protected JobRequestBo jobRequestBo;

    @Autowired
    private MpfService mpfService;

    @Autowired
	@Qualifier(JobCompleteProcessorImpl.REF)
	private JobCompleteProcessor jobCompleteProcessor;

	@Rule
	public TestName testName = new TestName();

    protected static boolean HAS_INITIALIZED = false;
	protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    protected static final int MINUTES = 1000*60; // 1000 milliseconds/second & 60 seconds/minute.

	// is this running on Jenkins and/or is output checking desired?
	protected static boolean jenkins = false;
	static {
		String prop = System.getProperty("jenkins");
		if (prop != null){
			jenkins = Boolean.valueOf(prop);
		}
	}

	@PostConstruct
	private void init() throws Exception {
		synchronized (lock) {
			if (!HAS_INITIALIZED) {
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
				HAS_INITIALIZED = true;
			}
		}
	}

    /*
     * This method simply checks that the number of media in the input matches the number of media in the output
     *
     * @param actualOutputPath
     * @param numInputMedia
     * @throws javax.xml.bind.JAXBException
     */
    public void checkOutput(URI actualOutputPath, int numInputMedia) throws IOException {
        log.debug("Deserializing actual output {}", actualOutputPath);

        JsonOutputObject actualOutput = OBJECT_MAPPER.readValue(actualOutputPath.toURL(), JsonOutputObject.class);
	    Assert.assertTrue(String.format("Actual output size=%d doesn't match number of input media=%d",
			    actualOutput.getMedia().size(), numInputMedia), actualOutput.getMedia().size() == numInputMedia);
    }

	protected List<JsonMediaInputObject> toMediaObjectList(URI... uris) {
		List<JsonMediaInputObject> media = new ArrayList<>(uris.length);
		for (URI uri : uris) {
			media.add(new JsonMediaInputObject(uri.toString()));
		}
		return media;
	}

	protected long runPipelineOnMedia(String pipelineName, List<JsonMediaInputObject> media, boolean buildOutput, int priority) throws Exception {
		JsonJobRequest jsonJobRequest = jobRequestBo.createRequest(UUID.randomUUID().toString(), pipelineName, media,
                buildOutput, priority);
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

	protected void runSystemTest(String pipelineName, String expectedOutputJsonPath, String... testMediaFiles) throws Exception {
		testCtr++;
		log.info("Beginning test #{} {}()", testCtr, testName.getMethodName());
		List<JsonMediaInputObject> mediaPaths = new LinkedList<>();
		for (String filePath : testMediaFiles) {
			mediaPaths.add(new JsonMediaInputObject(ioUtils.findFile(filePath).toString()));
		}

		long jobId = runPipelineOnMedia(pipelineName, mediaPaths, propertiesUtil.isOutputObjectsEnabled(),
				propertiesUtil.getJmsPriority());
		if (jenkins) {
			URL expectedOutputPath = getClass().getClassLoader().getResource(expectedOutputJsonPath);
			URI actualOutputPath = propertiesUtil.createDetectionOutputObjectFile(jobId).toURI();
			log.info("Deserializing expected output {} and actual output {}", expectedOutputPath, actualOutputPath);

			JsonOutputObject expectedOutputJson = OBJECT_MAPPER.readValue(expectedOutputPath, JsonOutputObject.class);
			JsonOutputObject actualOutputJson = OBJECT_MAPPER.readValue(actualOutputPath.toURL(), JsonOutputObject.class);

			outputChecker.compareOutputs(expectedOutputJson, actualOutputJson, pipelineName);
		}
		log.info("Finished test {}()", testName.getMethodName());
	}
}
