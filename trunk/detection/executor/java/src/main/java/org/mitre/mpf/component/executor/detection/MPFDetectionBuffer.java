/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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

import com.google.common.base.Joiner;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.commons.io.FilenameUtils;
import org.mitre.mpf.component.api.detection.*;
import org.mitre.mpf.wfm.buffers.AlgorithmPropertyProtocolBuffer.AlgorithmProperty;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionRequest;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.JMSException;
import javax.jms.Message;
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

    public Map<String, String> copyProperties(List<DetectionProtobuf.PropertyMap> prop_map) {
        Map<String, String> props = new HashMap<String, String>();
        for (int i = 0; i < prop_map.size(); i++) {
            props.put(prop_map.get(i).getKey(), prop_map.get(i).getValue());
        }
        return props;
    }

    public MPFMessageMetadata getMessageMetadata(Message message) throws JMSException {

        String dataUri = detectionRequest.getDataUri();
        MPFDataType dataType = translateProtobufDataType(detectionRequest.getDataType());
        long requestId = detectionRequest.getRequestId();

        Map<String, String> algorithmProperties = new HashMap<String, String>();
        for (int i = 0; i < detectionRequest.getAlgorithmPropertyList().size(); i++) {
            AlgorithmProperty algProp = detectionRequest.getAlgorithmProperty(i);
            algorithmProperties.put(algProp.getPropertyName(), algProp.getPropertyValue());
        }

        Map<String, String> mediaProperties = copyProperties(detectionRequest.getMediaMetadataList());

        try {
            String correlationId = message.getStringProperty("CorrelationId");
            String breadcrumbId = message.getStringProperty("breadcrumbId");
            int splitSize = message.getIntProperty("SplitSize");
            long jobId = message.getLongProperty("JobId");

            String jobName = "Job " + jobId + ":" + FilenameUtils.getName(dataUri);

            return new MPFMessageMetadata(dataUri, dataType,
                    detectionRequest.getMediaId(),
                    detectionRequest.getTaskName(), detectionRequest.getTaskIndex(),
                    detectionRequest.getActionName(), detectionRequest.getActionIndex(),
                    algorithmProperties, mediaProperties, requestId,
                    correlationId, breadcrumbId, splitSize, jobId, jobName);

        } catch (JMSException e) {
            LOG.error("Failed to get JMS message property.");
            throw e;
        }
    }

    public MPFDetectionAudioRequest getAudioRequest() {
        if (detectionRequest.getAudioRequest().hasFeedForwardTrack()) {
            DetectionProtobuf.AudioTrack track = detectionRequest.getAudioRequest().getFeedForwardTrack();
            // Copy the properties
            Map<String, String> trackProps = copyProperties(track.getDetectionPropertiesList());
            MPFAudioTrack new_track = new MPFAudioTrack(track.getStartTime(),
                                                        track.getStopTime(),
                                                        track.getConfidence(),
                                                        trackProps);
            return new MPFDetectionAudioRequest(
                           detectionRequest.getAudioRequest().getStartTime(),
                           detectionRequest.getAudioRequest().getStopTime(),
                           new_track);
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

            // Copy the frame locations map
            Map<Integer, MPFImageLocation> locations = new HashMap<Integer, MPFImageLocation>();
            for (int i = 0; i < track.getFrameLocationsList().size(); i++) {
                DetectionProtobuf.VideoTrack.FrameLocationMap loc_map = track.getFrameLocations(i);

                // Copy the detection properties for this location
                Map<String, String> locationProps =
                    copyProperties(loc_map.getImageLocation().getDetectionPropertiesList());
                // Create a new image location and put it into the new map
                MPFImageLocation loc = new MPFImageLocation(loc_map.getImageLocation().getXLeftUpper(),
                                                            loc_map.getImageLocation().getYLeftUpper(),
                                                            loc_map.getImageLocation().getWidth(),
                                                            loc_map.getImageLocation().getHeight(),
                                                            loc_map.getImageLocation().getConfidence(),
                                                            locationProps);
                locations.put(loc_map.getFrame(), loc);
            }
            // Copy the properties for the track itself
            Map<String, String> trackProps = copyProperties(track.getDetectionPropertiesList());

            MPFVideoTrack new_track = new MPFVideoTrack(track.getStartFrame(),
                                                        track.getStopFrame(),
                                                        locations,
                                                        track.getConfidence(),
                                                        trackProps);

            return new MPFDetectionVideoRequest(
                detectionRequest.getVideoRequest().getStartFrame(),
                detectionRequest.getVideoRequest().getStopFrame(),
                new_track);
        }
        else {
            return new MPFDetectionVideoRequest(
                detectionRequest.getVideoRequest().getStartFrame(),
                detectionRequest.getVideoRequest().getStopFrame());
        }
    }

    public MPFDetectionImageRequest getImageRequest() {
        if (detectionRequest.getImageRequest().hasFeedForwardLocation()) {
            DetectionProtobuf.ImageLocation tmp_loc =
                detectionRequest.getImageRequest().getFeedForwardLocation();

            Map<String, String> locationProperties =
                copyProperties(tmp_loc.getDetectionPropertiesList());

            MPFImageLocation loc = new MPFImageLocation(tmp_loc.getXLeftUpper(),
                                                        tmp_loc.getYLeftUpper(),
                                                        tmp_loc.getWidth(),
                                                        tmp_loc.getHeight(),
                                                        tmp_loc.getConfidence(),
                                                        locationProperties);
            return new MPFDetectionImageRequest(loc);
        }
        else {
            return new MPFDetectionImageRequest();
        }
    }


    public MPFDetectionGenericRequest getGenericRequest() {
        if (detectionRequest.getGenericRequest().hasFeedForwardTrack()) {
            DetectionProtobuf.GenericTrack track = detectionRequest.getGenericRequest().getFeedForwardTrack();
            // Copy the properties
            Map<String, String> trackProps = copyProperties(track.getDetectionPropertiesList());
            MPFGenericTrack new_track = new MPFGenericTrack(track.getConfidence(), trackProps);
            return new MPFDetectionGenericRequest(new_track);
        }
        else {
            return new MPFDetectionGenericRequest();
        }
    }


    private static DetectionResponse.Builder packCommonFields(
            final MPFMessageMetadata msgMetadata,
            final MPFDetectionError errorCode,
            final String errorMessage) {

        DetectionProtobuf.DetectionResponse.Builder detectionResponse = DetectionProtobuf.DetectionResponse.newBuilder();
        detectionResponse.setMediaId(msgMetadata.getMediaId());

        // TODO: share the same dataType enum between both the DetectionRequest and DetectionResponse protobuf
        detectionResponse.setDataType(translateMPFDetectionDataType(msgMetadata.getDataType()));

        detectionResponse.setTaskName(msgMetadata.getTaskName());
        detectionResponse.setTaskIndex(msgMetadata.getTaskIndex());
        detectionResponse.setActionName(msgMetadata.getActionName());
        detectionResponse.setActionIndex(msgMetadata.getActionIndex());
        detectionResponse.setError(translateMPFDetectionError(errorCode));
        detectionResponse.setErrorMessage(errorMessage);

        return detectionResponse;
    }

    public byte[] createAudioResponseMessage(final MPFMessageMetadata msgMetadata,
                                             final int startTime,
                                             final int stopTime,
                                             final String detectionType,
                                             final List<MPFAudioTrack> tracks,
                                             final MPFDetectionError errorCode,
                                             final String errorMessage) {
        byte[] responseContents = null;

        DetectionProtobuf.DetectionResponse.Builder detectionResponseBuilder
                = packCommonFields(msgMetadata, errorCode, errorMessage);

        DetectionProtobuf.DetectionResponse.AudioResponse.Builder audioResponseBuilder = detectionResponseBuilder.addAudioResponsesBuilder();
        audioResponseBuilder.setStartTime(startTime);
        audioResponseBuilder.setStopTime(stopTime);
        audioResponseBuilder.setDetectionType(detectionType);

        if (!tracks.isEmpty()) {
            LOG.debug("Number of audio tracks in detection response for job ID " +
                    msgMetadata.getJobId() + " = " + tracks.size());

            for (int i = 0; i < tracks.size(); i++) {

                DetectionProtobuf.AudioTrack audioTrack = audioResponseBuilder.addAudioTracksBuilder()
                        .setStartTime(tracks.get(i).getStartTime())
                        .setStopTime(tracks.get(i).getStopTime())
                        .setConfidence(tracks.get(i).getConfidence())
                        .addAllDetectionProperties(convertProperties(tracks.get(i).getDetectionProperties()))
                        .build();
            }
        }

        responseContents = detectionResponseBuilder
                .setRequestId(msgMetadata.getRequestId())
                .setDataType(translateMPFDetectionDataType(msgMetadata.getDataType()))
                .build()
                .toByteArray();

        return responseContents;
    }

    public byte[] createVideoResponseMessage(final MPFMessageMetadata msgMetadata,
                                             final int startFrame,
                                             final int stopFrame,
                                             final String detectionType,
                                             final List<MPFVideoTrack> tracks,
                                             final MPFDetectionError errorCode,
                                             final String errorMessage) {
        byte[] responseContents = null;

        DetectionProtobuf.DetectionResponse.Builder detectionResponseBuilder
                = packCommonFields(msgMetadata, errorCode, errorMessage);

        DetectionProtobuf.DetectionResponse.VideoResponse.Builder videoResponseBuilder =
                detectionResponseBuilder.addVideoResponsesBuilder();
        videoResponseBuilder.setStartFrame(startFrame);
        videoResponseBuilder.setStopFrame(stopFrame);
        videoResponseBuilder.setDetectionType(detectionType);

        if (!tracks.isEmpty()) {
            LOG.info("Number of video tracks in detection response for job ID " +
                    msgMetadata.getJobId() + " = " + tracks.size());

            for (int i = 0; i < tracks.size(); i++) {
                Set<DetectionProtobuf.VideoTrack.FrameLocationMap> frameLocationMapSet = new HashSet<>();
                for (Map.Entry<Integer,MPFImageLocation> entry : tracks.get(i).getFrameLocations().entrySet()) {
                    MPFImageLocation mpfImageLocation = entry.getValue();

                    DetectionProtobuf.VideoTrack.FrameLocationMap frameLocationMap = DetectionProtobuf.VideoTrack.FrameLocationMap.newBuilder()
                            .setFrame(entry.getKey())
                            .setImageLocation(DetectionProtobuf.ImageLocation.newBuilder()
                                    .setHeight(mpfImageLocation.getHeight())
                                    .setWidth(mpfImageLocation.getWidth())
                                    .setConfidence(mpfImageLocation.getConfidence())
                                    .setXLeftUpper(mpfImageLocation.getXLeftUpper())
                                    .setYLeftUpper(mpfImageLocation.getYLeftUpper())
                                    .addAllDetectionProperties(convertProperties(mpfImageLocation.getDetectionProperties()))).build();
                    frameLocationMapSet.add(frameLocationMap);

                }
                DetectionProtobuf.VideoTrack videoTrack = videoResponseBuilder.addVideoTracksBuilder()
                        .setStartFrame(tracks.get(i).getStartFrame())
                        .setStopFrame(tracks.get(i).getStopFrame())
                        .setConfidence(tracks.get(i).getConfidence())
                        .addAllDetectionProperties(convertProperties(tracks.get(i).getDetectionProperties()))
                        .addAllFrameLocations(frameLocationMapSet)
                        .build();

                LOG.info("Detection properties: {}", Joiner.on(";").withKeyValueSeparator("=").join(tracks.get(i).getDetectionProperties()));
            }
        }

        responseContents = detectionResponseBuilder
                .setRequestId(msgMetadata.getRequestId())
                .setDataType(translateMPFDetectionDataType(msgMetadata.getDataType()))
                .build()
                .toByteArray();

        return responseContents;
    }

    public byte[] createImageResponseMessage(final MPFMessageMetadata msgMetadata,
                                             final String detectionType,
                                             final List<MPFImageLocation> locations,
                                             final MPFDetectionError errorCode,
                                             final String errorMessage) {
        byte[] responseContents = null;

        DetectionProtobuf.DetectionResponse.Builder detectionResponseBuilder
                = packCommonFields(msgMetadata, errorCode, errorMessage);

        DetectionProtobuf.DetectionResponse.ImageResponse.Builder imageResponseBuilder = detectionResponseBuilder.addImageResponsesBuilder();
        imageResponseBuilder.setDetectionType(detectionType);

        if (!locations.isEmpty()) {
            LOG.debug("Number of image locations in detection response for job ID " +
                    msgMetadata.getJobId() + " = " + locations.size());

            for (int i = 0; i < locations.size(); i++) {
                imageResponseBuilder.addImageLocations(DetectionProtobuf.ImageLocation.newBuilder()
                        .setHeight(locations.get(i).getHeight())
                        .setWidth(locations.get(i).getWidth())
                        .setConfidence(locations.get(i).getConfidence())
                        .setXLeftUpper(locations.get(i).getXLeftUpper())
                        .setYLeftUpper(locations.get(i).getYLeftUpper())
                        .addAllDetectionProperties(convertProperties(locations.get(i).getDetectionProperties())));

            }

        }

        responseContents = detectionResponseBuilder
                .setRequestId(msgMetadata.getRequestId())
                .setDataType(translateMPFDetectionDataType(msgMetadata.getDataType()))
                .build()
                .toByteArray();

        return responseContents;
    }

    public byte[] createGenericResponseMessage(final MPFMessageMetadata msgMetadata,
                                               final String detectionType,
                                               final List<MPFGenericTrack> tracks,
                                               final MPFDetectionError errorCode,
                                               final String errorMessage) {
        byte[] responseContents = null;

        DetectionProtobuf.DetectionResponse.Builder detectionResponseBuilder
                = packCommonFields(msgMetadata, errorCode, errorMessage);

        DetectionProtobuf.DetectionResponse.GenericResponse.Builder genericResponseBuilder = detectionResponseBuilder.addGenericResponsesBuilder();
        genericResponseBuilder.setDetectionType(detectionType);

        if (!tracks.isEmpty()) {
            LOG.debug("Number of generic tracks in detection response for job ID " +
                    msgMetadata.getJobId() + " = " + tracks.size());

            for (int i = 0; i < tracks.size(); i++) {

                DetectionProtobuf.GenericTrack genericTrack = genericResponseBuilder.addGenericTracksBuilder()
                        .setConfidence(tracks.get(i).getConfidence())
                        .addAllDetectionProperties(convertProperties(tracks.get(i).getDetectionProperties()))
                        .build();
            }
        }

        responseContents = detectionResponseBuilder
                .setRequestId(msgMetadata.getRequestId())
                .setDataType(translateMPFDetectionDataType(msgMetadata.getDataType()))
                .build()
                .toByteArray();

        return responseContents;
    }

    public static MPFDataType translateProtobufDataType(final DetectionRequest.DataType dataType)  {

        switch (dataType) {
            case UNKNOWN:
                return MPFDataType.UNKNOWN;
            case VIDEO:
                return MPFDataType.VIDEO;
            case IMAGE:
                return MPFDataType.IMAGE;
            case AUDIO:
                return MPFDataType.AUDIO;
            default:
                return MPFDataType.UNKNOWN;
        }
    }

    public static DetectionResponse.DataType translateMPFDetectionDataType(final MPFDataType dataType) {

        switch (dataType) {
            case UNKNOWN:
                return DetectionResponse.DataType.UNKNOWN;
            case VIDEO:
                return DetectionResponse.DataType.VIDEO;
            case IMAGE:
                return DetectionResponse.DataType.IMAGE;
            case AUDIO:
                return DetectionResponse.DataType.AUDIO;
            default:
                return DetectionResponse.DataType.UNKNOWN;
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

    private List<DetectionProtobuf.PropertyMap> convertProperties(Map<String,String> propMap) {

        LinkedList<DetectionProtobuf.PropertyMap> output = new LinkedList<>();
        if (propMap!=null && !propMap.isEmpty()) {
            for (Map.Entry<String, String> entry : propMap.entrySet()) {
                output.add(DetectionProtobuf.PropertyMap.newBuilder()
                        .setKey(entry.getKey())
                        .setValue(entry.getValue()).build());
            }
        }
        return output;
    }
}
