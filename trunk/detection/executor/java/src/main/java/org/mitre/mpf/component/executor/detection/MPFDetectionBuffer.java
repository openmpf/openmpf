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

package org.mitre.mpf.component.executor.detection;

import com.google.protobuf.InvalidProtocolBufferException;
import org.mitre.mpf.component.api.detection.*;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionRequest;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.JMSException;
import javax.jms.Message;

import static java.util.stream.Collectors.toMap;

import java.io.File;
import java.util.*;

public class MPFDetectionBuffer {

    private static final Logger LOG = LoggerFactory.getLogger(MPFDetectionBuffer.class);

    private DetectionRequest detectionRequest = null;

    public MPFDetectionBuffer(final byte[] requestContents) throws InvalidProtocolBufferException {
        try {
            detectionRequest = DetectionRequest.parseFrom(requestContents);
        } catch (InvalidProtocolBufferException e) {
            LOG.error("Failed to parse the request protocol buffer.");
            throw e;
        }
    }


    public MPFMessageMetadata getMessageMetadata(Message message) throws JMSException {

        String mediaPath = detectionRequest.getMediaPath();
        MPFDataType dataType = getProtobufDataType(detectionRequest);
        try {
            String correlationId = message.getStringProperty("CorrelationId");
            String breadcrumbId = message.getStringProperty("breadcrumbId");
            int splitSize = message.getIntProperty("SplitSize");
            long jobId = message.getLongProperty("JobId");

            String jobName = "Job " + jobId + ":" + (new File(mediaPath)).getName();

            return new MPFMessageMetadata(mediaPath, dataType,
                    detectionRequest.getMediaId(),
                    detectionRequest.getTaskIndex(),
                    detectionRequest.getActionIndex(),
                    detectionRequest.getAlgorithmPropertiesMap(),
                    detectionRequest.getMediaMetadataMap(),
                    correlationId, breadcrumbId, splitSize, jobId, jobName);

        } catch (JMSException e) {
            LOG.error("Failed to get JMS message property.");
            throw e;
        }
    }

    public MPFDetectionAudioRequest getAudioRequest() {
        if (detectionRequest.getAudioRequest().hasFeedForwardTrack()) {
            DetectionProtobuf.AudioTrack track = detectionRequest.getAudioRequest().getFeedForwardTrack();
            MPFAudioTrack newTrack = new MPFAudioTrack(track.getStartTime(),
                                                       track.getStopTime(),
                                                       track.getConfidence(),
                                                       track.getDetectionPropertiesMap());
            return new MPFDetectionAudioRequest(
                           detectionRequest.getAudioRequest().getStartTime(),
                           detectionRequest.getAudioRequest().getStopTime(),
                           newTrack);
        }
        else {
            return new MPFDetectionAudioRequest(
                           detectionRequest.getAudioRequest().getStartTime(),
                           detectionRequest.getAudioRequest().getStopTime());
        }
    }

    public MPFDetectionVideoRequest getVideoRequest() {
        if (detectionRequest.getVideoRequest().hasFeedForwardTrack()) {
            DetectionProtobuf.VideoTrack track = detectionRequest.getVideoRequest().getFeedForwardTrack();

            var locations = track.getFrameLocationsMap().entrySet()
                    .stream()
                    .collect(toMap(Map.Entry::getKey, e -> fromProtobuf(e.getValue())));

            MPFVideoTrack newTrack = new MPFVideoTrack(track.getStartFrame(),
                                                       track.getStopFrame(),
                                                       locations,
                                                       track.getConfidence(),
                                                       track.getDetectionPropertiesMap());

            return new MPFDetectionVideoRequest(
                detectionRequest.getVideoRequest().getStartFrame(),
                detectionRequest.getVideoRequest().getStopFrame(),
                newTrack);
        }
        else {
            return new MPFDetectionVideoRequest(
                detectionRequest.getVideoRequest().getStartFrame(),
                detectionRequest.getVideoRequest().getStopFrame());
        }
    }

    public MPFDetectionImageRequest getImageRequest() {
        if (detectionRequest.getImageRequest().hasFeedForwardLocation()) {
            var pbLoc = detectionRequest.getImageRequest().getFeedForwardLocation();
            return new MPFDetectionImageRequest(fromProtobuf(pbLoc));
        }
        else {
            return new MPFDetectionImageRequest();
        }
    }


    public MPFDetectionGenericRequest getGenericRequest() {
        if (detectionRequest.getGenericRequest().hasFeedForwardTrack()) {
            DetectionProtobuf.GenericTrack track = detectionRequest.getGenericRequest().getFeedForwardTrack();
            MPFGenericTrack newTrack = new MPFGenericTrack(
                    track.getConfidence(), track.getDetectionPropertiesMap());
            return new MPFDetectionGenericRequest(newTrack);
        }
        else {
            return new MPFDetectionGenericRequest();
        }
    }


    private static DetectionResponse.Builder packCommonFields(
            final MPFMessageMetadata msgMetadata,
            final MPFDetectionError errorCode,
            final String errorMessage) {

        return DetectionProtobuf.DetectionResponse.newBuilder()
                .setMediaId(msgMetadata.getMediaId())
                .setTaskIndex(msgMetadata.getTaskIndex())
                .setActionIndex(msgMetadata.getActionIndex())
                .setError(translateMPFDetectionError(errorCode))
                .setErrorMessage(errorMessage);
    }

    public byte[] createAudioResponseMessage(final MPFMessageMetadata msgMetadata,
                                             final int startTime,
                                             final int stopTime,
                                             final List<MPFAudioTrack> tracks,
                                             final MPFDetectionError errorCode,
                                             final String errorMessage) {
        var detectionResponseBuilder = packCommonFields(msgMetadata, errorCode, errorMessage);
        var audioResponseBuilder = detectionResponseBuilder.getAudioResponseBuilder()
                    .setStartTime(startTime)
                    .setStopTime(stopTime);
        LOG.info(
            "Found {} tracks while processing job {}", tracks.size(), msgMetadata.getJobId());
        for (var track : tracks) {
            audioResponseBuilder.addAudioTracksBuilder()
                    .setStartTime(track.getStartTime())
                    .setStopTime(track.getStopTime())
                    .setConfidence(track.getConfidence())
                    .putAllDetectionProperties(track.getDetectionProperties());
        }
        return detectionResponseBuilder.build().toByteArray();
    }


    public byte[] createVideoResponseMessage(final MPFMessageMetadata msgMetadata,
                                             final int startFrame,
                                             final int stopFrame,
                                             final List<MPFVideoTrack> tracks,
                                             final MPFDetectionError errorCode,
                                             final String errorMessage) {
        var detectionResponseBuilder = packCommonFields(msgMetadata, errorCode, errorMessage);
        var videoResponseBuilder = detectionResponseBuilder.getVideoResponseBuilder()
                .setStartFrame(startFrame)
                .setStopFrame(stopFrame);

        LOG.info(
            "Found {} tracks while processing job {}", tracks.size(), msgMetadata.getJobId());

        for (var track : tracks) {
            var trackBuilder = videoResponseBuilder.addVideoTracksBuilder()
                    .setStartFrame(track.getStartFrame())
                    .setStopFrame(track.getStopFrame())
                    .setConfidence(track.getConfidence())
                    .putAllDetectionProperties(track.getDetectionProperties());
            track.getFrameLocations()
                .forEach((f, il) -> trackBuilder.putFrameLocations(f, toProtobuf(il)));
        }
        return detectionResponseBuilder.build().toByteArray();
    }

    public byte[] createImageResponseMessage(final MPFMessageMetadata msgMetadata,
                                             final List<MPFImageLocation> locations,
                                             final MPFDetectionError errorCode,
                                             final String errorMessage) {
        var detectionResponseBuilder = packCommonFields(msgMetadata, errorCode, errorMessage);
        var imageResponseBuilder = detectionResponseBuilder.getImageResponseBuilder();
        LOG.info(
            "Found {} image locations while processing job {}",
            locations.size(), msgMetadata.getJobId());

        locations.forEach(il -> imageResponseBuilder.addImageLocations(toProtobuf(il)));
        return detectionResponseBuilder.build().toByteArray();
    }

    private static DetectionProtobuf.ImageLocation toProtobuf(MPFImageLocation imageLocation) {
        return DetectionProtobuf.ImageLocation.newBuilder()
                .setXLeftUpper(imageLocation.getXLeftUpper())
                .setYLeftUpper(imageLocation.getYLeftUpper())
                .setHeight(imageLocation.getHeight())
                .setWidth(imageLocation.getWidth())
                .setConfidence(imageLocation.getConfidence())
                .putAllDetectionProperties(imageLocation.getDetectionProperties())
                .build();
    }

    private static MPFImageLocation fromProtobuf(DetectionProtobuf.ImageLocation pbImgLoc) {
        return new MPFImageLocation(
            pbImgLoc.getXLeftUpper(),
            pbImgLoc.getYLeftUpper(),
            pbImgLoc.getWidth(),
            pbImgLoc.getHeight(),
            pbImgLoc.getConfidence(),
            pbImgLoc.getDetectionPropertiesMap());
    }

    public byte[] createGenericResponseMessage(final MPFMessageMetadata msgMetadata,
                                               final List<MPFGenericTrack> tracks,
                                               final MPFDetectionError errorCode,
                                               final String errorMessage) {
        var detectionResponseBuilder = packCommonFields(msgMetadata, errorCode, errorMessage);
        var genericResponseBuilder = detectionResponseBuilder.getGenericResponseBuilder();
        LOG.info(
            "Found {} tracks while processing job {}", tracks.size(), msgMetadata.getJobId());
        for (var track : tracks) {
            genericResponseBuilder.addGenericTracksBuilder()
                .setConfidence(track.getConfidence())
                .putAllDetectionProperties(track.getDetectionProperties());
        }
        return detectionResponseBuilder.build().toByteArray();
    }

    public static MPFDataType getProtobufDataType(DetectionRequest request)  {
        if (request.hasVideoRequest()) {
            return MPFDataType.VIDEO;
        }
        else if (request.hasImageRequest()) {
            return MPFDataType.IMAGE;
        }
        else if (request.hasAudioRequest()) {
            return MPFDataType.AUDIO;
        }
        else {
            return MPFDataType.UNKNOWN;
        }
    }

    public static org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError translateMPFDetectionError(final MPFDetectionError err) {

        switch (err) {

            case MPF_DETECTION_SUCCESS:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.NO_DETECTION_ERROR;
            case MPF_DETECTION_NOT_INITIALIZED:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.DETECTION_NOT_INITIALIZED;
            case MPF_UNSUPPORTED_DATA_TYPE:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.UNSUPPORTED_DATA_TYPE;
            case MPF_COULD_NOT_OPEN_DATAFILE:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.COULD_NOT_OPEN_DATAFILE;
            case MPF_COULD_NOT_READ_DATAFILE:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.COULD_NOT_READ_DATAFILE;
            case MPF_FILE_WRITE_ERROR:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.FILE_WRITE_ERROR;
            case MPF_BAD_FRAME_SIZE:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.BAD_FRAME_SIZE;
            case MPF_DETECTION_FAILED:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.DETECTION_FAILED;
            case MPF_INVALID_PROPERTY:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.INVALID_PROPERTY;
            case MPF_MISSING_PROPERTY:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.MISSING_PROPERTY;
            case MPF_MEMORY_ALLOCATION_FAILED:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.MEMORY_ALLOCATION_FAILED;
            case MPF_GPU_ERROR:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.GPU_ERROR;
            case MPF_NETWORK_ERROR:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.NETWORK_ERROR;
            case MPF_COULD_NOT_OPEN_MEDIA:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.COULD_NOT_OPEN_MEDIA;
            case MPF_COULD_NOT_READ_MEDIA:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.COULD_NOT_READ_MEDIA;
            default:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.UNRECOGNIZED_DETECTION_ERROR;
        }
    }
}
