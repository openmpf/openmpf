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
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runner.notification.RunListener;
import org.mitre.mpf.wfm.camel.WfmProcessorInterface;
import org.mitre.mpf.wfm.camel.operations.detection.trackmerging.TrackMergingContext;
import org.mitre.mpf.wfm.camel.operations.detection.trackmerging.TrackMergingProcessor;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.*;

import static org.junit.Assert.assertEquals;

@ContextConfiguration(locations = {"classpath:applicationContext.xml"})
@RunWith(SpringJUnit4ClassRunner.class)
@RunListener.ThreadSafe
public class TestTrackMergingProcessor {
    private static final int MINUTES = 1000 * 60; // 1000 milliseconds/second & 60 seconds/minute.

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
    private InProgressBatchJobsService inProgressJobs;

    @Autowired
    private PropertiesUtil propertiesUtil;

    @Test(timeout = 5 * MINUTES)
    public void testTrackMergingOn() throws Exception {
        generateAndRunMerge("1", "TRUE", null, null, 4); // Merges tracks 1 & 2
    }

    @Test(timeout = 5 * MINUTES)
    public void testTrackMergingOnGap3() throws Exception {
        generateAndRunMerge("1", "TRUE", "3", null, 4); // Merges tracks 1 & 2; 3 frame gap still does not merge 3 & 4
    }

    @Test(timeout = 5 * MINUTES)
    public void testTrackMergingOnGap4() throws Exception {
        generateAndRunMerge("1", "TRUE", "4", null, 3); // Merges tracks 1 & 2; merges tracks 3 & 4
    }

    @Test(timeout = 5 * MINUTES)
    public void testTrackMergingOff() throws Exception {
        generateAndRunMerge("1", "FALSE", null, null, 5); // No merging
        generateAndRunMerge("1", "FALSE", "200", null, 5); // No merging even with gap set high
    }

    @Test(timeout = 5 * MINUTES)
    public void testMinTrackSizeNoMerge() throws Exception {
        generateAndRunMerge("1", "FALSE", null, "100", 3); // Drops tracks 3 & 5
        generateAndRunMerge("1", "FALSE", null, "200", 2); // Drops tracks 3, 4, & 5
        generateAndRunMerge("1", "FALSE", null, "201", 0); // Drops all tracks
    }

    @Test(timeout = 5 * MINUTES)
    public void testMinTrackSizeWithMerge() throws Exception {
        generateAndRunMerge("1", "TRUE", null, "100", 2); // Merges tracks 1 & 2, drops tracks 3 & 5
        generateAndRunMerge("1", "TRUE", null, "300", 1); // Merges tracks 1 & 2 (new track 400 frames), drops tracks 3, 4, & 5
        generateAndRunMerge("1", "TRUE", "3", "130", 1); // Merges tracks 1 & 2, drops tracks 3, 4, & 5
        generateAndRunMerge("1", "TRUE", "4", "130", 2); // Merges tracks 1 & 2, 3 & 4 (new track 130 frames) drops track 5
    }

    /**
     * This method tests merging under a variety of conditions defined by incoming property values.
     *
     * Five tracks are created and used for merging in 2 locations:
     * 1. Location 1, frames 0-199
     * 2. Location 1, frames 200-399
     * 3. Location 1, frames 470-477
     * 4. Location 1, frames 480-599
     * 5. Location 2, frames 600-610.
     *
     * Track 5 should never merge.  The other tracks may merge or be dropped based on properties.
     */
    private void generateAndRunMerge(String samplingInterval, String mergeTracks, String minGap, String minTrackSize, int expectedTracks) throws Exception {
        final long jobId = 999999;
        final long mediaId = 123456;
        final int stageIndex = 0;
        final int priority = 5;
        Exchange exchange = new DefaultExchange(camelContext);
        TrackMergingContext mergeContext = new TrackMergingContext(jobId, stageIndex);
        exchange.getIn().setBody(jsonUtils.serialize(mergeContext));

        Map<String, String> mergeProp = new HashMap<>();
        if (samplingInterval != null) {
            mergeProp.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, samplingInterval);
        }
        if (mergeTracks != null) {
            mergeProp.put(MpfConstants.MERGE_TRACKS_PROPERTY, mergeTracks);
        }
        if (minGap != null) {
            mergeProp.put(MpfConstants.MIN_GAP_BETWEEN_TRACKS, minGap);
        }
        if (minTrackSize != null) {
            mergeProp.put(MpfConstants.MIN_TRACK_LENGTH, minTrackSize);
        }
        TransientAction detectionAction = new TransientAction("detectionAction", "detectionDescription", "detectionAlgo", mergeProp);

        TransientStage trackMergeStageDet = new TransientStage(
                "trackMergeDetection", "trackMergeDescription", ActionType.DETECTION,
                Collections.singletonList(detectionAction));

        TransientPipeline trackMergePipeline = new TransientPipeline(
                "trackMergePipeline", "trackMergeDescription",
                Collections.singletonList(trackMergeStageDet));

        // Capture a snapshot of the detection system property settings when the job is created.
        SystemPropertiesSnapshot systemPropertiesSnapshot = propertiesUtil.createSystemPropertiesSnapshot();

        inProgressJobs.addJob(
                jobId,
                "999999",
                systemPropertiesSnapshot,
                trackMergePipeline,
                priority,
                false,
                null,
                null,
                Collections.singletonList(new TransientMedia(mediaId,ioUtils.findFile("/samples/video_01.mp4").toString())),
                Collections.emptyMap(),
                Collections.emptyMap());

        /*
        * Create overlapping tracks for testing
        */
        SortedSet<Track> tracks = new TreeSet<Track>();
        Track track1 = new Track(jobId, mediaId, 0, 0, 0, 199, "VIDEO", 18f);
        Detection detection1a = new Detection(10,10,52,60,18f,0,0,null);
        Detection detection1b = new Detection(10,10,52,60,18f,199,0,null);
        track1.getDetections().add(detection1a);
        track1.getDetections().add(detection1b);
        Track track2 = new Track(jobId, mediaId, 0, 0, 200, 399, "VIDEO", 18f);
        Detection detection2a = new Detection(10,10,52,60,18f,200,0,null);
        Detection detection2b = new Detection(10,10,52,60,18f,399,0,null);
        track2.getDetections().add(detection2a);
        track2.getDetections().add(detection2b);
        Track track3 = new Track(jobId, mediaId, 0, 0, 470, 477, "VIDEO", 18f);
        Detection detection3a = new Detection(10,10,52,60,18f,420,0,null);
        Detection detection3b = new Detection(10,10,52,60,18f,599,0,null);
        track3.getDetections().add(detection3a);
        track3.getDetections().add(detection3b);
        Track track4 = new Track(jobId, mediaId, 0, 0, 480, 599, "VIDEO", 18f);
        Detection detection4a = new Detection(10,10,52,60,18f,480,0,null);
        Detection detection4b = new Detection(10,10,52,60,18f,599,0,null);
        track4.getDetections().add(detection4a);
        track4.getDetections().add(detection4b);
        Track track5 = new Track(jobId, mediaId, 0, 0, 600, 610, "VIDEO", 18f);
        Detection detection5a = new Detection(10,10,89,300,18f,600,0,null);
        Detection detection5b = new Detection(10,10,84,291,18f,610,0,null);
        track5.getDetections().add(detection5a);
        track5.getDetections().add(detection5b);
        tracks.add(track1);
        tracks.add(track2);
        tracks.add(track3);
        tracks.add(track4);
        tracks.add(track5);

        inProgressJobs.setTracks(jobId,mediaId,0,0,tracks);

        trackMergingProcessor.wfmProcess(exchange);

        Object responseBody = exchange.getOut().getBody();
        Assert.assertTrue("A response body must be set.", responseBody != null);
        Assert.assertTrue(String.format("Response body must be a byte[]. Actual: %s.", responseBody.getClass()),  responseBody instanceof byte[]);
        TrackMergingContext contextResponse = jsonUtils.deserialize((byte[])responseBody, TrackMergingContext.class);
        Assert.assertTrue(contextResponse.getStageIndex() == stageIndex);
        Assert.assertTrue(contextResponse.getJobId() == jobId);
        Assert.assertEquals(expectedTracks, inProgressJobs.getTracks(jobId, mediaId, 0, 0).size());
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

        Map<String, String> mergeProp = new HashMap<>();
        mergeProp.put(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY, "1");
        mergeProp.put(MpfConstants.MERGE_TRACKS_PROPERTY, "TRUE");
        TransientAction detectionAction = new TransientAction(
                "detectionAction", "detectionDescription", "detectionAlgo", mergeProp);

        TransientStage trackMergeStageDet = new TransientStage(
                "trackMergeDetection",
                "trackMergeDescription", ActionType.DETECTION, Collections.singletonList(detectionAction));

        TransientPipeline trackMergePipeline = new TransientPipeline(
                "trackMergePipeline", "trackMergeDescription",
                Collections.singletonList(trackMergeStageDet));

        // Capture a snapshot of the detection system property settings when the job is created.
        SystemPropertiesSnapshot systemPropertiesSnapshot = propertiesUtil.createSystemPropertiesSnapshot();

        inProgressJobs.addJob(
                jobId,
                "999999",
                systemPropertiesSnapshot,
                trackMergePipeline,
                priority,
                false,
                null,
                null,
                Collections.singletonList(new TransientMedia(mediaId,ioUtils.findFile("/samples/video_01.mp4").toString())),
                Collections.emptyMap(),
                Collections.emptyMap());

        SortedSet<Track> tracks = new TreeSet<Track>();

        inProgressJobs.setTracks(jobId, mediaId, 0, 0, tracks);

        trackMergingProcessor.wfmProcess(exchange);

        Object responseBody = exchange.getOut().getBody();
        Assert.assertTrue("A response body must be set.", responseBody != null);
        Assert.assertTrue(String.format("Response body must be a byte[]. Actual: %s.", responseBody.getClass()),  responseBody instanceof byte[]);
        TrackMergingContext contextResponse = jsonUtils.deserialize((byte[])responseBody, TrackMergingContext.class);
        Assert.assertTrue(contextResponse.getStageIndex() == stageIndex);
        Assert.assertTrue(contextResponse.getJobId() == jobId);
        Assert.assertEquals(0, inProgressJobs.getTracks(jobId, mediaId, 0, 0).size());
    }


    @Test
    public void testTrackLevelInfoRetainedAfterMerge() {
        Track track1 = new Track(123, 1, 1, 1, 1, 1, "type", 0.25f);
        track1.getTrackProperties().put("track1_only_prop", "track1_only_val");
        track1.getTrackProperties().put("same_value_prop", "same_value_val");
        track1.getTrackProperties().put("diff_value_prop", "diff_value_val1");

        Track track2 = new Track(123, 1, 1, 1, 1, 1, "type", 0.75f);
        track2.getTrackProperties().put("track2_only_prop", "track2_only_val");
        track2.getTrackProperties().put("same_value_prop", "same_value_val");
        track2.getTrackProperties().put("diff_value_prop", "diff_value_val2");

        Track merged = TrackMergingProcessor.merge(track1, track2);
        assertEquals(merged.getConfidence(), 0.75, 0.01);

        SortedMap<String, String> mergedProps = merged.getTrackProperties();
        assertEquals("track1_only_val", mergedProps.get("track1_only_prop"));
        assertEquals("track2_only_val", mergedProps.get("track2_only_prop"));
        assertEquals("same_value_val", mergedProps.get("same_value_prop"));
        assertEquals("diff_value_val1; diff_value_val2", mergedProps.get("diff_value_prop"));
    }
}
