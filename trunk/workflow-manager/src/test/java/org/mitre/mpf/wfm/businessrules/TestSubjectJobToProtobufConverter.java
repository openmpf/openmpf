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

package org.mitre.mpf.wfm.businessrules;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.mitre.mpf.interop.JsonActionOutputObject;
import org.mitre.mpf.interop.JsonDetectionOutputObject;
import org.mitre.mpf.interop.JsonMediaOutputObject;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.interop.JsonTrackOutputObject;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.buffers.SubjectProtobuf;
import org.mitre.mpf.wfm.util.ThreadUtil;

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;

public class TestSubjectJobToProtobufConverter {

    @Test
    public void testCreateJob() {
        var videoOutputFuture = ThreadUtil.<JsonOutputObject>newFuture();
        var imageOutputFuture = ThreadUtil.<JsonOutputObject>newFuture();
        var audioOutputFuture = ThreadUtil.<JsonOutputObject>newFuture();
        var genericOutputFuture = ThreadUtil.<JsonOutputObject>newFuture();

        var pbJobFuture = new SubjectJobToProtobufConverter().createJob(
                140,
                List.of(videoOutputFuture, imageOutputFuture,
                        audioOutputFuture, genericOutputFuture),
                Map.of("JOB_PROP", "JOB_VALUE"));

        videoOutputFuture.complete(createVideoOutputObject());
        imageOutputFuture.complete(createImageOutputObject());
        audioOutputFuture.complete(createAudioOutputObject());
        TestUtil.assertNotDone(pbJobFuture);

        genericOutputFuture.complete(createGenericOutputObject());
        assertThat(pbJobFuture).succeedsWithin(TestUtil.FUTURE_DURATION)
                .isEqualTo(createExpectedProtobuf());
    }


    private static JsonOutputObject createVideoOutputObject() {

        var mediaOutput = new JsonMediaOutputObject(
                301,
                -1,
                "media1-path",
                "tiesDbSourceMediaPath",
                "VIDEO",
                "video/mp4",
                1000,
                "SHA",
                "status",
                null);
        mediaOutput.getMediaProperties().put("MEDIA_PROP1", "MEDIA_VALUE1");

        var actionOutput1 = new JsonActionOutputObject("ACTION1", "ALGO1");
        var detection1 = new JsonDetectionOutputObject(
                501,
                502,
                503,
                504,
                0.5f,
                ImmutableSortedMap.of("DETECTION1_PROP", "DETECTION1_VALUE"),
                100,
                2000L,
                "artifactExtractionStatus",
                "artifactPath");

        var track1OtherDetection = new JsonDetectionOutputObject(
                5011,
                5022,
                5033,
                5044,
                0.4f,
                ImmutableSortedMap.of("DETECTION_OTHER_PROP", "DETECTION_OTHER_VALUE"),
                1000,
                20000L,
                "artifactExtractionStatus",
                "artifactPath");

        var track1 = new JsonTrackOutputObject(
                1,
                "id1",
                100,
                104,
                2000L,
                2004L,
                "TRACK_TYPE1",
                0.5f,
                Map.of("TRACK1_PROP", "TRACK1_VALUE"),
                detection1,
                List.of(detection1, track1OtherDetection));

        actionOutput1.getTracks().add(track1);
        mediaOutput.getTrackTypes().put("TRACK_TYPE1", ImmutableSortedSet.of(actionOutput1));

        var actionOutput2 = new JsonActionOutputObject("ACTION2", "ALGO2");
        var detection2 = new JsonDetectionOutputObject(
                601,
                602,
                603,
                604,
                0.9f,
                ImmutableSortedMap.of("DETECTION2_PROP", "DETECTION2_VALUE"),
                200,
                4000L,
                "artifactExtractionStatus",
                "artifactPath");
        var track2 = new JsonTrackOutputObject(
                2,
                "id2",
                200,
                204,
                4000L,
                4004L,
                "TRACK_TYPE2",
                0.9f,
                Map.of("TRACK2_PROP", "TRACK2_VALUE"),
                detection2,
                List.of(detection2));
        actionOutput2.getTracks().add(track2);
        mediaOutput.getTrackTypes().put("TRACK_TYPE2", ImmutableSortedSet.of(actionOutput2));

        var videoJobOutput = new JsonOutputObject(
                "host-201", "objectId",
                null, // pipeline
                4,
                "siteId",
                "openmpfVersion",
                "externalJobId",
                Instant.now(),
                Instant.now(),
                "status",
                null); // timing
        videoJobOutput.getMedia().add(mediaOutput);
        videoJobOutput.getJobProperties().put("JOB1_PROP", "JOB1_VALUE");
        return videoJobOutput;
    }

    private static JsonOutputObject createImageOutputObject() {
        var mediaOutput = new JsonMediaOutputObject(
                302,
                -1,
                "media2-path",
                "tiesDbSourceMediaPath",
                "IMAGE",
                "image/jpeg",
                1,
                "SHA2",
                "status",
                null);
        mediaOutput.getMediaProperties().put("MEDIA_PROP2", "MEDIA_VALUE2");

        var actionOutput = new JsonActionOutputObject("ACTION3", "ALGO3");
        var detection = new JsonDetectionOutputObject(
                701,
                702,
                703,
                704,
                0.7f,
                ImmutableSortedMap.of("DETECTION3_PROP", "DETECTION3_VALUE"),
                0,
                0L,
                "artifactExtractionStatus",
                "artifactPath");

        var track = new JsonTrackOutputObject(
                1,
                "id3",
                0,
                1,
                0,
                10L,
                "TRACK_TYPE3",
                0.7f,
                Map.of("TRACK3_PROP", "TRACK3_VALUE"),
                detection,
                List.of(detection));
        actionOutput.getTracks().add(track);
        mediaOutput.getTrackTypes().put("TRACK_TYPE3", ImmutableSortedSet.of(actionOutput));

        var imageJobOutput = new JsonOutputObject(
                "host-202", "objectId",
                null, // pipeline
                4,
                "siteId",
                "openmpfVersion",
                "externalJobId",
                Instant.now(),
                Instant.now(),
                "status",
                null); // timing
        imageJobOutput.getMedia().add(mediaOutput);
        imageJobOutput.getJobProperties().put("JOB2_PROP", "JOB2_VALUE");
        return imageJobOutput;
    }

    private static JsonOutputObject createAudioOutputObject() {
        var mediaOutput = new JsonMediaOutputObject(
                303,
                -1,
                "media3-path",
                "tiesDbSourceMediaPath",
                "AUDIO",
                "audio/mp3",
                1,
                "SHA3",
                "status",
                null);
        mediaOutput.getMediaProperties().put("MEDIA_PROP3", "MEDIA_VALUE3");

        var actionOutput = new JsonActionOutputObject("ACTION4", "ALGO4");
        var detection = new JsonDetectionOutputObject(
                801,
                802,
                803,
                804,
                0.7f,
                ImmutableSortedMap.of("DETECTION4_PROP", "DETECTION4_VALUE"),
                0,
                0L,
                "artifactExtractionStatus",
                "artifactPath");

        var track = new JsonTrackOutputObject(
                1,
                "id4",
                0,
                1,
                200,
                300,
                "TRACK_TYPE4",
                0.7f,
                Map.of("TRACK4_PROP", "TRACK4_VALUE"),
                detection,
                List.of(detection));
        actionOutput.getTracks().add(track);
        mediaOutput.getTrackTypes().put("TRACK_TYPE4", ImmutableSortedSet.of(actionOutput));
        var jobOutput = new JsonOutputObject(
                "host-203", "objectId",
                null,
                4,
                "siteId",
                "openmpfVersion",
                "externalJobId",
                Instant.now(),
                Instant.now(),
                "status",
                null);
        jobOutput.getMedia().add(mediaOutput);
        jobOutput.getJobProperties().put("JOB3_PROP", "JOB3_VALUE");
        return jobOutput;
    }


    private static JsonOutputObject createGenericOutputObject() {
        var mediaOutput = new JsonMediaOutputObject(
                304,
                -1,
                "media4-path",
                "tiesDbSourceMediaPath",
                "UNKNOWN",
                "text/plain",
                1,
                "SHA3",
                "status",
                null);
        mediaOutput.getMediaProperties().put("MEDIA_PROP4", "MEDIA_VALUE4");

        var actionOutput = new JsonActionOutputObject("ACTION5", "ALGO5");
        var detection = new JsonDetectionOutputObject(
                901,
                902,
                903,
                904,
                0.9f,
                ImmutableSortedMap.of("DETECTION5_PROP", "DETECTION5_VALUE"),
                0,
                0L,
                "artifactExtractionStatus",
                "artifactPath");

        var track = new JsonTrackOutputObject(
                1,
                "id5",
                0,
                1,
                0,
                10L,
                "TRACK_TYPE5",
                0.9f,
                Map.of("TRACK5_PROP", "TRACK5_VALUE"),
                detection,
                List.of(detection));
        actionOutput.getTracks().add(track);
        mediaOutput.getTrackTypes().put("TRACK_TYPE5", ImmutableSortedSet.of(actionOutput));
        var jobOutput = new JsonOutputObject(
                "host-204", "objectId",
                null,
                4,
                "siteId",
                "openmpfVersion",
                "externalJobId",
                Instant.now(),
                Instant.now(),
                "status",
                null);
        jobOutput.getMedia().add(mediaOutput);
        jobOutput.getJobProperties().put("JOB4_PROP", "JOB4_VALUE");
        return jobOutput;
    }


    private static SubjectProtobuf.SubjectTrackingJob createExpectedProtobuf() {
        var jobBuilder = SubjectProtobuf.SubjectTrackingJob.newBuilder()
            .setJobId(140)
            .setJobName("Job 140")
            .putJobProperties("JOB_PROP", "JOB_VALUE")
            .addAllVideoJobResults(createExpectedVideoResults())
            .addImageJobResults(createExpectedImageResult())
            .addAudioJobResults(createExpectedAudioResult())
            .addGenericJobResults(createExpectedGenericResult());
        return jobBuilder.build();
    }


    private static List<SubjectProtobuf.VideoDetectionJobResults> createExpectedVideoResults() {
        var builder1 = SubjectProtobuf.VideoDetectionJobResults.newBuilder();
        var builder2 = SubjectProtobuf.VideoDetectionJobResults.newBuilder();

        builder1.getDetectionJobBuilder()
                .setDataUri("media1-path")
                .setMediaId("media1-path")
                .setAlgorithm("ALGO1")
                .setTrackType("TRACK_TYPE1")
                .putJobProperties("JOB1_PROP", "JOB1_VALUE")
                .putMediaProperties("MEDIA_PROP1", "MEDIA_VALUE1");


        builder2.getDetectionJobBuilder()
                .setDataUri("media1-path")
                .setMediaId("media1-path")
                .setAlgorithm("ALGO2")
                .setTrackType("TRACK_TYPE2")
                .putJobProperties("JOB1_PROP", "JOB1_VALUE")
                .putMediaProperties("MEDIA_PROP1", "MEDIA_VALUE1");

        var detection1 = DetectionProtobuf.ImageLocation.newBuilder()
                .setXLeftUpper(501)
                .setYLeftUpper(502)
                .setWidth(503)
                .setHeight(504)
                .setConfidence(0.5f)
                .putDetectionProperties("DETECTION1_PROP", "DETECTION1_VALUE")
                .build();

        var track1Other = DetectionProtobuf.ImageLocation.newBuilder()
                .setXLeftUpper(5011)
                .setYLeftUpper(5022)
                .setWidth(5033)
                .setHeight(5044)
                .setConfidence(0.4f)
                .putDetectionProperties("DETECTION_OTHER_PROP", "DETECTION_OTHER_VALUE")
                .build();

        var detection2 = DetectionProtobuf.ImageLocation.newBuilder()
                .setXLeftUpper(601)
                .setYLeftUpper(602)
                .setWidth(603)
                .setHeight(604)
                .setConfidence(0.9f)
                .putDetectionProperties("DETECTION2_PROP", "DETECTION2_VALUE")
                .build();


        var track1 = DetectionProtobuf.VideoTrack.newBuilder()
                .setStartFrame(100)
                .setStopFrame(104)
                .putFrameLocations(100, detection1)
                .putFrameLocations(1000, track1Other)
                .setConfidence(0.5f)
                .putDetectionProperties("TRACK1_PROP", "TRACK1_VALUE")
                .build();

        var track2 = DetectionProtobuf.VideoTrack.newBuilder()
                .setStartFrame(200)
                .setStopFrame(204)
                .putFrameLocations(200, detection2)
                .setConfidence(0.9f)
                .putDetectionProperties("TRACK2_PROP", "TRACK2_VALUE")
                .build();

        builder1.putResults("id1", track1);
        builder2.putResults("id2", track2);

        return List.of(builder1.build(), builder2.build());
    }

    private static SubjectProtobuf.ImageDetectionJobResults createExpectedImageResult() {
        var builder = SubjectProtobuf.ImageDetectionJobResults.newBuilder();
        builder.getDetectionJobBuilder()
                .setDataUri("media2-path")
                .setMediaId("media2-path")
                .setAlgorithm("ALGO3")
                .setTrackType("TRACK_TYPE3")
                .putJobProperties("JOB2_PROP", "JOB2_VALUE")
                .putMediaProperties("MEDIA_PROP2", "MEDIA_VALUE2");

        var detection = DetectionProtobuf.ImageLocation.newBuilder()
                .setXLeftUpper(701)
                .setYLeftUpper(702)
                .setWidth(703)
                .setHeight(704)
                .setConfidence(0.7f)
                .putDetectionProperties("DETECTION3_PROP", "DETECTION3_VALUE")
                .build();
        builder.putResults("id3", detection);
        return builder.build();
    }


    private static SubjectProtobuf.AudioDetectionJobResults createExpectedAudioResult() {
        var builder = SubjectProtobuf.AudioDetectionJobResults.newBuilder();
        builder.getDetectionJobBuilder()
                .setDataUri("media3-path")
                .setMediaId("media3-path")
                .setAlgorithm("ALGO4")
                .setTrackType("TRACK_TYPE4")
                .putJobProperties("JOB3_PROP", "JOB3_VALUE")
                .putMediaProperties("MEDIA_PROP3", "MEDIA_VALUE3");

        var track = DetectionProtobuf.AudioTrack.newBuilder()
                .setStartTime(200)
                .setStopTime(300)
                .setConfidence(0.7f)
                .putDetectionProperties("TRACK4_PROP", "TRACK4_VALUE")
                .build();
        builder.putResults("id4", track);
        return builder.build();
    }


    private static SubjectProtobuf.GenericDetectionJobResults createExpectedGenericResult() {
        var builder = SubjectProtobuf.GenericDetectionJobResults.newBuilder();
        builder.getDetectionJobBuilder()
                .setDataUri("media4-path")
                .setMediaId("media4-path")
                .setAlgorithm("ALGO5")
                .setTrackType("TRACK_TYPE5")
                .putJobProperties("JOB4_PROP", "JOB4_VALUE")
                .putMediaProperties("MEDIA_PROP4", "MEDIA_VALUE4");

        var track = DetectionProtobuf.GenericTrack.newBuilder()
                .setConfidence(0.9f)
                .putDetectionProperties("TRACK5_PROP", "TRACK5_VALUE")
                .build();

        builder.putResults("id5", track);
        return builder.build();
    }
}
