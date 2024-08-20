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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.mitre.mpf.interop.JsonActionOutputObject;
import org.mitre.mpf.interop.JsonDetectionOutputObject;
import org.mitre.mpf.interop.JsonMediaOutputObject;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.interop.JsonTrackOutputObject;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.buffers.SubjectProtobuf;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.springframework.stereotype.Component;


@Component
public class SubjectJobToProtobufConverter {

    /**
     * Returns a new future that completes when all of the supplied outputObjectFutures complete
     * and are converted to a SubjectTrackingJob protobuf.
     */
    public CompletableFuture<SubjectProtobuf.SubjectTrackingJob> createJob(
            long jobId,
            Collection<CompletableFuture<JsonOutputObject>> outputObjectFutures,
            Map<String, String> jobProperties) {

        var jobBuilder = SubjectProtobuf.SubjectTrackingJob.newBuilder()
                .setJobId(jobId)
                .setJobName("Job " + jobId)
                .putAllJobProperties(jobProperties);

        var pbFutures = outputObjectFutures.stream()
                .map(f -> f.thenApplyAsync(SubjectJobToProtobufConverter::toProtobuf))
                .toList();

        return ThreadUtil.allOf(pbFutures)
            .thenApply(x -> {
                for (var f : pbFutures) {
                    ThreadUtil.join(f).addTo(jobBuilder);
                }
                return jobBuilder.build();
            });
    }


    private record ProtobufDetectionResults(
            Collection<SubjectProtobuf.VideoDetectionJobResults> video,
            Collection<SubjectProtobuf.ImageDetectionJobResults> image) {

        private void addTo(SubjectProtobuf.SubjectTrackingJob.Builder builder) {
            builder.addAllImageJobResults(image).addAllVideoJobResults(video);
        }
    }


    private static ProtobufDetectionResults toProtobuf(JsonOutputObject outputObject) {
        var videoJobResults = new ArrayList<SubjectProtobuf.VideoDetectionJobResults>();
        var imageJobResults = new ArrayList<SubjectProtobuf.ImageDetectionJobResults>();
        for (var media : outputObject.getMedia()) {
            var mediaType = MediaType.valueOf(media.getType());
            for (var entry : media.getTrackTypes().entrySet()) {
                var trackType = entry.getKey();
                var actions = entry.getValue();
                for (var action : actions) {
                    if (mediaType == MediaType.IMAGE) {
                        imageJobResults.add(createImageResults(
                                outputObject, media, trackType, action));
                    }
                    else if (mediaType == MediaType.VIDEO) {
                        videoJobResults.add(createVideoResults(
                                outputObject, media, trackType, action));
                    }
                }
            }
        }
        return new ProtobufDetectionResults(videoJobResults, imageJobResults);
    }


    private static SubjectProtobuf.ImageDetectionJobResults createImageResults(
            JsonOutputObject outputObject,
            JsonMediaOutputObject media,
            String trackType,
            JsonActionOutputObject action) {
        var imageJobResults = SubjectProtobuf.ImageDetectionJobResults.newBuilder();
        imageJobResults.setDetectionJob(
                createDetectionJob(outputObject, media, trackType, action.getAlgorithm()));
        for (var track : action.getTracks()) {
            var detection = track.getDetections().first();
            imageJobResults.putResults(track.getId(), convertToImageLocation(detection));
        }
        return imageJobResults.build();
    }

    private static SubjectProtobuf.VideoDetectionJobResults createVideoResults(
            JsonOutputObject outputObject,
            JsonMediaOutputObject media,
            String trackType,
            JsonActionOutputObject action) {
        var videoJobResults = SubjectProtobuf.VideoDetectionJobResults.newBuilder();
        videoJobResults.setDetectionJob(createDetectionJob(
                outputObject, media, trackType, action.getAlgorithm()));
        for (var track : action.getTracks()) {
            videoJobResults.putResults(track.getId(), convertToVideoTrack(track));
        }
        return videoJobResults.build();
    }


    private static SubjectProtobuf.DetectionJob createDetectionJob(
            JsonOutputObject outputObject,
            JsonMediaOutputObject media,
            String trackType,
            String algorithm) {
        return SubjectProtobuf.DetectionJob.newBuilder()
            // TODO: Download media and set path here
            .setDataUri(media.getPath())
            // TODO: use public path
            .setMediaId(media.getPath())
            .setAlgorithm(algorithm)
            .setTrackType(trackType)
            .putAllJobProperties(outputObject.getJobProperties())
            .putAllMediaProperties(media.getMediaProperties())
            .build();
    }

    private static DetectionProtobuf.ImageLocation convertToImageLocation(
            JsonDetectionOutputObject detection) {
        return DetectionProtobuf.ImageLocation.newBuilder()
                .setXLeftUpper(detection.getX())
                .setYLeftUpper(detection.getY())
                .setWidth(detection.getWidth())
                .setHeight(detection.getHeight())
                .setConfidence(detection.getConfidence())
                .putAllDetectionProperties(detection.getDetectionProperties())
                .build();
    }

    private static DetectionProtobuf.VideoTrack convertToVideoTrack(JsonTrackOutputObject track) {
        var videoTrack = DetectionProtobuf.VideoTrack.newBuilder()
                .setStartFrame(track.getStartOffsetFrame())
                .setStopFrame(track.getStopOffsetFrame())
                .setConfidence(track.getConfidence())
                .putAllDetectionProperties(track.getTrackProperties());
        for (var detection : track.getDetections()) {
            videoTrack.putFrameLocations(
                    detection.getOffsetFrame(), convertToImageLocation(detection));
        }
        return videoTrack.build();
    }
}
