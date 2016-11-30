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

package org.mitre.mpf.wfm.camelOps;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;
import org.apache.commons.lang3.mutable.MutableInt;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runner.notification.RunListener;
import org.mitre.mpf.wfm.camel.WfmProcessorInterface;
import org.mitre.mpf.wfm.camel.operations.detection.trackmerging.TrackMergingContext;
import org.mitre.mpf.wfm.camel.operations.detection.trackmerging.TrackMergingProcessor;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.*;

@ContextConfiguration(locations = {"classpath:applicationContext.xml"})
@RunWith(SpringJUnit4ClassRunner.class)
@RunListener.ThreadSafe
public class TestTrackMergingProcessor {
    private static final Logger log = LoggerFactory.getLogger(TestTrackMergingProcessor.class);
    private static final int MINUTES = 1000 * 60; // 1000 milliseconds/second & 60 seconds/minute.

    @Autowired
    private ApplicationContext context;

    @Autowired
    private CamelContext camelContext;

    @Autowired
    @Qualifier(TrackMergingProcessor.REF)
    private WfmProcessorInterface trackMergingProcessor;

    @Autowired
    private IoUtils ioUtils;

    @Autowired
    private JsonUtils jsonUtils;

    @Autowired
    private Redis redis;

    private static final MutableInt SEQUENCE = new MutableInt();

    public int next() {
        synchronized (SEQUENCE) {
            int next = SEQUENCE.getValue();
            SEQUENCE.increment();
            return next;
        }
    }

    @Test(timeout = 5 * MINUTES)
    public void testTrackMergingOn() throws Exception {
        final long jobId = 999999;
        final long mediaId = 123456;
        final int stageIndex = 0;
        final int priority = 5;
        Exchange exchange = new DefaultExchange(camelContext);
        TrackMergingContext mergeContext = new TrackMergingContext(jobId, stageIndex);
        exchange.getIn().setBody(jsonUtils.serialize(mergeContext));
        TransientPipeline trackMergePipeline = new TransientPipeline("trackMergePipeline", "trackMergeDescription");

        TransientStage trackMergeStageDet = new TransientStage("trackMergeDetection", "trackMergeDescription", ActionType.DETECTION);

        Map<String, String> mergeProp = new HashMap<>();
        mergeProp.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, "1");
        mergeProp.put(MpfConstants.MERGE_TRACKS_PROPERTY, "TRUE");
        TransientAction detectionAction = new TransientAction("detectionAction", "detectionDescription", "detectionAlgo");
        detectionAction.setProperties(mergeProp);
        trackMergeStageDet.getActions().add(detectionAction);

        trackMergePipeline.getStages().add(trackMergeStageDet);
        TransientJob trackMergeJob = new TransientJob(jobId, "999999", trackMergePipeline, stageIndex, priority, false, false);
        trackMergeJob.getMedia().add(new TransientMedia(mediaId,ioUtils.findFile("/samples/video_01.mp4").toString()));

        redis.persistJob(trackMergeJob);

        /*
        * Create overlapping tracks for testing
        */

        SortedSet<Track> tracks = new TreeSet<Track>();
        Track track1 = new Track(jobId, mediaId, 0, 0, 0, 199, "VIDEO");
        Detection detection1a = new Detection(10,10,52,60,18f,0,0,null);
        Detection detection1b = new Detection(10,10,52,60,18f,199,0,null);
        track1.getDetections().add(detection1a);
        track1.getDetections().add(detection1b);
        Track track2 = new Track(jobId, mediaId, 0, 0, 200, 399, "VIDEO");
        Detection detection2a = new Detection(10,10,52,60,18f,200,0,null);
        Detection detection2b = new Detection(10,10,52,60,18f,399,0,null);
        track2.getDetections().add(detection2a);
        track2.getDetections().add(detection2b);
        Track track3 = new Track(jobId, mediaId, 0, 0, 420, 599, "VIDEO");
        Detection detection3a = new Detection(10,10,52,60,18f,420,0,null);
        Detection detection3b = new Detection(10,10,52,60,18f,599,0,null);
        track3.getDetections().add(detection3a);
        track3.getDetections().add(detection3b);
        Track track4 = new Track(jobId, mediaId, 0, 0, 600, 610, "VIDEO");
        Detection detection4a = new Detection(10,10,89,300,18f,600,0,null);
        Detection detection4b = new Detection(10,10,84,291,18f,610,0,null);
        track4.getDetections().add(detection4a);
        track4.getDetections().add(detection4b);
        tracks.add(track1);
        tracks.add(track2);
        tracks.add(track3);
        tracks.add(track4);


        redis.setTracks(jobId,mediaId,0,0,tracks);


        trackMergingProcessor.wfmProcess(exchange);

        Object responseBody = exchange.getOut().getBody();
        Assert.assertTrue("A response body must be set.", responseBody != null);
        Assert.assertTrue(String.format("Response body must be a byte[]. Actual: %s.", responseBody.getClass()),  responseBody instanceof byte[]);
        TrackMergingContext contextResponse = jsonUtils.deserialize((byte[])responseBody, TrackMergingContext.class);
        Assert.assertTrue(contextResponse.getStageIndex() == stageIndex);
        Assert.assertTrue(contextResponse.getJobId() == jobId);
        Assert.assertEquals(3,redis.getTracks(jobId,mediaId,0,0).size());
    }

    @Test(timeout = 5 * MINUTES)
    public void testTrackMergingOff() throws Exception {
        final long jobId = 999999;
        final long mediaId = 123456;
        final int stageIndex = 0;
        final int priority = 5;
        Exchange exchange = new DefaultExchange(camelContext);
        TrackMergingContext mergeContext = new TrackMergingContext(jobId, stageIndex);
        exchange.getIn().setBody(jsonUtils.serialize(mergeContext));
        TransientPipeline trackMergePipeline = new TransientPipeline("trackMergePipeline", "trackMergeDescription");

        TransientStage trackMergeStageDet = new TransientStage("trackMergeDetection", "trackMergeDescription", ActionType.DETECTION);

        Map<String, String> mergeProp = new HashMap<>();
        mergeProp.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, "1");
        mergeProp.put(MpfConstants.MERGE_TRACKS_PROPERTY, "FALSE");
        TransientAction detectionAction = new TransientAction("detectionAction", "detectionDescription", "detectionAlgo");
        detectionAction.setProperties(mergeProp);
        trackMergeStageDet.getActions().add(detectionAction);

        trackMergePipeline.getStages().add(trackMergeStageDet);
        TransientJob trackMergeJob = new TransientJob(jobId, "999999", trackMergePipeline, stageIndex, priority, false, false);
        trackMergeJob.getMedia().add(new TransientMedia(mediaId,ioUtils.findFile("/samples/video_01.mp4").toString()));

        redis.persistJob(trackMergeJob);

        /*
        * Create overlapping tracks for testing
        */

        SortedSet<Track> tracks = new TreeSet<Track>();
        Track track1 = new Track(jobId, mediaId, 0, 0, 0, 199, "VIDEO");
        Detection detection1a = new Detection(10,10,52,60,18f,0,0,null);
        Detection detection1b = new Detection(10,10,52,60,18f,199,0,null);
        track1.getDetections().add(detection1a);
        track1.getDetections().add(detection1b);
        Track track2 = new Track(jobId, mediaId, 0, 0, 200, 399, "VIDEO");
        Detection detection2a = new Detection(10,10,52,60,18f,200,0,null);
        Detection detection2b = new Detection(10,10,52,60,18f,399,0,null);
        track2.getDetections().add(detection2a);
        track2.getDetections().add(detection2b);
        Track track3 = new Track(jobId, mediaId, 0, 0, 420, 599, "VIDEO");
        Detection detection3a = new Detection(10,10,52,60,18f,420,0,null);
        Detection detection3b = new Detection(10,10,52,60,18f,599,0,null);
        track3.getDetections().add(detection3a);
        track3.getDetections().add(detection3b);
        Track track4 = new Track(jobId, mediaId, 0, 0, 600, 610, "VIDEO");
        Detection detection4a = new Detection(10,10,89,300,18f,600,0,null);
        Detection detection4b = new Detection(10,10,84,291,18f,610,0,null);
        track4.getDetections().add(detection4a);
        track4.getDetections().add(detection4b);
        tracks.add(track1);
        tracks.add(track2);
        tracks.add(track3);
        tracks.add(track4);


        redis.setTracks(jobId,mediaId,0,0,tracks);


        trackMergingProcessor.wfmProcess(exchange);

        Object responseBody = exchange.getOut().getBody();
        Assert.assertTrue("A response body must be set.", responseBody != null);
        Assert.assertTrue(String.format("Response body must be a byte[]. Actual: %s.", responseBody.getClass()),  responseBody instanceof byte[]);
        TrackMergingContext contextResponse = jsonUtils.deserialize((byte[])responseBody, TrackMergingContext.class);
        Assert.assertTrue(contextResponse.getStageIndex() == stageIndex);
        Assert.assertTrue(contextResponse.getJobId() == jobId);
        Assert.assertEquals(4,redis.getTracks(jobId,mediaId,0,0).size());
    }

    @Test(timeout = 5 * MINUTES)
    public void testTrackMergingNoTracks() throws Exception {
        final long jobId = 999999;
        final long mediaId = 123456;
        final int stageIndex = 0;
        final int priority = 5;
        Exchange exchange = new DefaultExchange(camelContext);
        TrackMergingContext mergeContext = new TrackMergingContext(jobId, stageIndex);
        exchange.getIn().setBody(jsonUtils.serialize(mergeContext));
        TransientPipeline trackMergePipeline = new TransientPipeline("trackMergePipeline", "trackMergeDescription");

        TransientStage trackMergeStageDet = new TransientStage("trackMergeDetection", "trackMergeDescription", ActionType.DETECTION);

        Map<String, String> mergeProp = new HashMap<>();
        mergeProp.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, "1");
        mergeProp.put(MpfConstants.MERGE_TRACKS_PROPERTY, "TRUE");
        TransientAction detectionAction = new TransientAction("detectionAction", "detectionDescription", "detectionAlgo");
        detectionAction.setProperties(mergeProp);
        trackMergeStageDet.getActions().add(detectionAction);

        trackMergePipeline.getStages().add(trackMergeStageDet);
        TransientJob trackMergeJob = new TransientJob(jobId, "999999", trackMergePipeline, stageIndex, priority, false, false);
        trackMergeJob.getMedia().add(new TransientMedia(mediaId,ioUtils.findFile("/samples/video_01.mp4").toString()));

        redis.persistJob(trackMergeJob);

        /*
        * Create overlapping tracks for testing
        */

        SortedSet<Track> tracks = new TreeSet<Track>();

        redis.setTracks(jobId, mediaId, 0, 0, tracks);


        trackMergingProcessor.wfmProcess(exchange);

        Object responseBody = exchange.getOut().getBody();
        Assert.assertTrue("A response body must be set.", responseBody != null);
        Assert.assertTrue(String.format("Response body must be a byte[]. Actual: %s.", responseBody.getClass()),  responseBody instanceof byte[]);
        TrackMergingContext contextResponse = jsonUtils.deserialize((byte[])responseBody, TrackMergingContext.class);
        Assert.assertTrue(contextResponse.getStageIndex() == stageIndex);
        Assert.assertTrue(contextResponse.getJobId() == jobId);
        Assert.assertEquals(0, redis.getTracks(jobId, mediaId, 0, 0).size());
    }

}
