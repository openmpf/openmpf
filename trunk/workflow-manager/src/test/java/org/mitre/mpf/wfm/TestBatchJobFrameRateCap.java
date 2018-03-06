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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.PostConstruct;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mitre.mpf.interop.JsonJobRequest;
import org.mitre.mpf.interop.JsonMediaInputObject;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.wfm.businessrules.JobRequestBo;
import org.mitre.mpf.wfm.businessrules.impl.JobRequestBoImpl;
import org.mitre.mpf.wfm.camel.JobCompleteProcessor;
import org.mitre.mpf.wfm.camel.JobCompleteProcessorImpl;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.event.JobCompleteNotification;
import org.mitre.mpf.wfm.event.NotificationConsumer;
import org.mitre.mpf.wfm.service.MpfService;
import org.mitre.mpf.wfm.util.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration(locations = {"classpath:applicationContext.xml"})
@RunWith(SpringJUnit4ClassRunner.class)
public class TestBatchJobFrameRateCap {

    @Autowired
    @Qualifier(IoUtils.REF)
    protected IoUtils ioUtils;

    @Autowired
    @Qualifier(JobRequestBoImpl.REF)
    protected JobRequestBo jobRequestBo;

    @Autowired
    private MpfService mpfService;

    @Autowired
    @Qualifier(JobCompleteProcessorImpl.REF)
    private JobCompleteProcessor jobCompleteProcessor;


    protected static final Logger log = LoggerFactory.getLogger(TestBatchJobFrameRateCap.class);
    protected static final ObjectMapper objectMapper = new ObjectMapper();

    protected static boolean hasInitialized = false;
    protected static int testCtr = 0;
    protected static final int MINUTES = 1000 * 60; // 1000 milliseconds/second & 60 seconds/minute.
    protected static Object lock = new Object();
    protected static Set<Long> completedJobs = new HashSet<>();

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

    public boolean waitFor(long jobRequestId) {
        synchronized (lock) {
            while (!completedJobs.contains(jobRequestId)) {
                try {
                    lock.wait();
                } catch (InterruptedException ie) {
                    JobRequest jobRequest = mpfService.getJobRequest(jobRequestId);
                    log.warn("InterruptedException occurred while waiting. Job status is {} ", jobRequest.getStatus());
                    completedJobs.add(jobRequestId);
                    return jobRequest.getStatus() == BatchJobStatusType.COMPLETE;
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

    protected long runPipelineOnMedia(String pipelineName, List<JsonMediaInputObject> media, Map<String, Map> algorithmProperties, Map<String,String> jobProperties,
                                      boolean buildOutput, int priority) throws Exception {
        JsonJobRequest jsonJobRequest = jobRequestBo.createRequest(UUID.randomUUID().toString(), pipelineName, media,
            algorithmProperties, jobProperties, buildOutput, priority);
        long jobRequestId = mpfService.submitJob(jsonJobRequest);
        log.info("runPipelineOnMedia submitted jobId " + jobRequestId);
        // the waitFor method isn't working, for now just wait 1 minute for the job to complete (TODO: will fix this later).
//        Assert.assertTrue(waitFor(jobRequestId));
        JobRequest jobRequest = mpfService.getJobRequest(jobRequestId);
        log.info("runPipelineOnMedia initial status of jobId " + jobRequestId + ", status is " + jobRequest.getStatus() );
        int trialCount = 0;
        while ( jobRequest.getStatus() != BatchJobStatusType.COMPLETE && trialCount < 5 ) {
            Thread.sleep(60000);
            ++trialCount;
            jobRequest = mpfService.getJobRequest(jobRequestId);
            log.info("runPipelineOnMedia in loop check, trial #" + trialCount +": status of jobId " + jobRequestId + " is " + jobRequest.getStatus() );
        }
        log.info("runPipelineOnMedia returning for jobId " + jobRequestId + ", status is " + jobRequest.getStatus() );
        return jobRequestId;
    }

    protected List<JsonMediaInputObject> toMediaObjectList(URI... uris) {
        List<JsonMediaInputObject> media = new ArrayList<>(uris.length);
        for (URI uri : uris) {
            media.add(new JsonMediaInputObject(uri.toString()));
        }
        return media;
    }


    @Test(timeout = 2 * MINUTES)
    public void testBatchJobOverrideFrameRateCapAsJobProperty() throws Exception {
        testCtr++;
        log.info("Beginning test #{} OverrideFrameRateCapAsJobProperty()", testCtr);
        List<JsonMediaInputObject> media = toMediaObjectList(ioUtils.findFile("/samples/video_01.mp4"));

        HashMap jobProperties = new HashMap();
        jobProperties.put(MpfConstants.FRAME_RATE_CAP_PROPERTY, "10");

        long jobId = runPipelineOnMedia("OCV FACE DETECTION (WITH MARKUP) PIPELINE", media, Collections.emptyMap(), jobProperties, true, 5);
        log.info("Test #{} OverrideFrameRateCapAsJobProperty(), finished jobId = ", testCtr, jobId);

        JobRequest jobRequest = mpfService.getJobRequest(jobId);

        log.info("Test #{} OverrideFrameRateCapAsJobProperty(), output object path is = ", testCtr, jobRequest.getOutputObjectPath());

        Assert.assertTrue(jobRequest.getStatus() == BatchJobStatusType.COMPLETE);
        Assert.assertTrue(jobRequest.getOutputObjectPath() != null);
        Assert.assertTrue(new File(jobRequest.getOutputObjectPath()).exists());

        JsonOutputObject jsonOutputObject = objectMapper.readValue(new File(jobRequest.getOutputObjectPath()), JsonOutputObject.class);
        Assert.assertEquals(jsonOutputObject.getJobId(), jobId);
        String start = jsonOutputObject.getTimeStart(),
            stop = jsonOutputObject.getTimeStop();

        completedJobs.clear();

    }
}
